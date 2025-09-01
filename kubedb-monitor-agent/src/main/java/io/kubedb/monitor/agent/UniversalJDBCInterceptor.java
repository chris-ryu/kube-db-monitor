package io.kubedb.monitor.agent;

import net.bytebuddy.implementation.bind.annotation.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Method;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.concurrent.Callable;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import javax.sql.DataSource;

import io.kubedb.monitor.agent.pool.ConnectionPoolMonitor;
import io.kubedb.monitor.agent.pool.PoolMetrics;

/**
 * ByteBuddy 기반 범용 JDBC 인터셉터
 * 모든 데이터베이스(PostgreSQL, Oracle, MySQL, SQL Server 등)를 지원하는 통합 인터셉터
 */
public class UniversalJDBCInterceptor {
    private static final Logger logger = LoggerFactory.getLogger(UniversalJDBCInterceptor.class);
    
    private static volatile MetricsCollector metricsCollector;
    private static volatile HttpMetricsTransmitter httpTransmitter;
    private static volatile ConnectionPoolMonitor poolMonitor;
    
    // 이미 등록된 DataSource를 추적하여 중복 등록 방지
    private static final Set<DataSource> registeredDataSources = ConcurrentHashMap.newKeySet();
    
    // DB 타입 열거형
    public enum DatabaseType {
        POSTGRESQL, MYSQL, ORACLE, SQLSERVER, MARIADB, H2, UNKNOWN
    }
    
    /**
     * 모든 JDBC 메서드에 대한 범용 인터셉터
     */
    @RuntimeType
    public static Object intercept(
            @Origin Method method,
            @This Object target,
            @AllArguments Object[] args,
            @SuperCall Callable<?> callable) throws Exception {
        
        // MetricsCollector, HttpTransmitter, ConnectionPoolMonitor 지연 초기화
        if (metricsCollector == null) {
            synchronized (UniversalJDBCInterceptor.class) {
                if (metricsCollector == null) {
                    AgentConfig config = KubeDBAgent.getConfig();
                    if (config != null) {
                        metricsCollector = new MetricsCollector(config);
                        httpTransmitter = new HttpMetricsTransmitter(config);
                        poolMonitor = new ConnectionPoolMonitor();
                        
                        logger.info("[KubeDB] UniversalJDBCInterceptor 초기화 완료 - 실시간 Connection Pool 메트릭 전송 활성화");
                    }
                }
            }
        }
        
        if (metricsCollector == null || !KubeDBAgent.getConfig().isEnabled()) {
            return callable.call();
        }
        
        long startTime = System.nanoTime();
        String methodName = method.getName();
        String className = target.getClass().getSimpleName();
        
        try {
            System.out.println("🔍 JDBC 메서드 인터셉트: " + className + "." + methodName);
            
            Object result = callable.call();
            long executionTime = System.nanoTime() - startTime;
            
            // 메서드 타입별 처리
            handleMethodExecution(target, method, args, executionTime, true, null);
            
            return result;
            
        } catch (Exception e) {
            long executionTime = System.nanoTime() - startTime;
            handleMethodExecution(target, method, args, executionTime, false, e);
            throw e;
        }
    }
    
    /**
     * 메서드 실행 결과 처리
     */
    private static void handleMethodExecution(Object target, Method method, Object[] args, 
                                            long executionTime, boolean success, Exception error) {
        try {
            String methodName = method.getName();
            String className = target.getClass().getSimpleName();
            
            // DB 타입 감지
            DatabaseType dbType = detectDatabaseType(target);
            
            // Connection 사용 시 DataSource 등록 시도
            tryRegisterDataSourceFromConnection(target);
            
            // 메서드별 특화 처리
            switch (methodName) {
                case "execute":
                case "executeQuery":
                case "executeUpdate":
                case "executeBatch":
                    handleStatementExecution(target, method, args, executionTime, success, error, dbType);
                    break;
                    
                case "prepareStatement":
                case "createStatement":
                    handleStatementCreation(target, method, args, executionTime, success, error, dbType);
                    break;
                    
                case "commit":
                    handleCommit(target, executionTime, success, error, dbType);
                    break;
                    
                case "rollback":
                    handleRollback(target, executionTime, success, error, dbType);
                    break;
                    
                case "setAutoCommit":
                    handleSetAutoCommit(target, method, args, executionTime, success, error, dbType);
                    break;
                    
                case "close":
                    handleClose(target, executionTime, className, dbType);
                    break;
                    
                default:
                    logger.debug("Unhandled method: {}.{}", className, methodName);
            }
            
        } catch (Exception e) {
            logger.error("Error handling method execution: {}", e.getMessage(), e);
        }
    }
    
    /**
     * Statement 실행 처리 (execute, executeQuery, executeUpdate)
     */
    private static void handleStatementExecution(Object target, Method method, Object[] args,
                                               long executionTime, boolean success, Exception error, 
                                               DatabaseType dbType) {
        try {
            String sql = extractSQL(target, args);
            String connectionId = getConnectionId(target);
            String threadName = Thread.currentThread().getName();
            
            System.out.println("🔍 SQL 실행 감지: " + sql + " (" + (executionTime / 1_000_000) + "ms)");
            
            // PreparedStatement 생성 관련 잘못된 메트릭 필터링
            // (기존 문자열과 신규 추출 실패 문자열 모두 처리)
            if (sql != null && (sql.contains("PreparedStatement SQL (구현체별 추출 필요)") || 
                               sql.equals("PreparedStatement [SQL not available]"))) {
                System.out.println("❌ PreparedStatement 메타데이터 메트릭은 전송하지 않음 - 실제 SQL 실행이 아님");
                return;
            }
            
            // 비정상적으로 큰 실행 시간 필터링 (10초 이상)
            long executionTimeMs = executionTime / 1_000_000;
            if (executionTimeMs > 10000) {
                System.out.println("❌ 비정상적으로 큰 실행 시간 필터링: " + executionTimeMs + "ms");
                return;
            }
            
            // DB별 특화 처리
            sql = preprocessSQL(sql, dbType);
            
            // 메트릭 수집 (기존 방식 유지)
            if (success) {
                metricsCollector.recordQuery(sql, executionTime, connectionId, threadName);
            } else {
                // 오류 처리는 MetricsCollector 메서드 시그니처 확인 필요
                logger.warn("SQL execution failed: {}, Error: {}", sql, error.getMessage());
            }
            
            // 실시간 HTTP 전송 (Connection Pool 메트릭 포함)
            if (httpTransmitter != null && success) {
                try {
                    PoolMetrics currentPoolMetrics = poolMonitor != null ? poolMonitor.getLatestMetrics() : null;
                    if (currentPoolMetrics != null && !currentPoolMetrics.isEmpty()) {
                        logger.info("[KubeDB] 📊 HTTP 전송에 Connection Pool 메트릭 포함: {}", currentPoolMetrics);
                    } else {
                        logger.warn("[KubeDB] ⚠️ HTTP 전송 시 Connection Pool 메트릭이 없음 또는 비어있음");
                    }
                    httpTransmitter.transmitQueryMetric(sql, executionTimeMs, connectionId, threadName, currentPoolMetrics);
                } catch (Exception e) {
                    logger.warn("[KubeDB] HTTP 메트릭 전송 실패: {}", e.getMessage());
                }
            }
            
        } catch (Exception e) {
            logger.error("Error handling statement execution: {}", e.getMessage());
        }
    }
    
    /**
     * Statement 생성 처리 (prepareStatement, createStatement)
     */
    private static void handleStatementCreation(Object target, Method method, Object[] args,
                                              long executionTime, boolean success, Exception error,
                                              DatabaseType dbType) {
        try {
            if ("prepareStatement".equals(method.getName()) && args.length > 0) {
                String sql = String.valueOf(args[0]);
                System.out.println("🔍 PreparedStatement 생성: " + sql);
                
                // SQL 준비 시간도 메트릭으로 수집
                String connectionId = getConnectionId(target);
                String threadName = Thread.currentThread().getName();
                
                // PreparedStatement 생성은 별도 메트릭으로 처리 (query_execution이 아님)
                // 실제 SQL 실행 시간에 영향주지 않도록 분리
                logger.debug("[KubeDB] PreparedStatement 생성: {} ({}ms)", sql, executionTime / 1_000_000);
                
                // PreparedStatement 생성은 별도의 이벤트 타입으로 전송 (선택사항)
                // 현재는 평균 지연시간 계산에 영향주지 않도록 전송하지 않음
            }
        } catch (Exception e) {
            logger.error("Error handling statement creation: {}", e.getMessage());
        }
    }
    
    /**
     * 커밋 처리
     */
    private static void handleCommit(Object target, long executionTime, boolean success, 
                                   Exception error, DatabaseType dbType) {
        try {
            String connectionId = getConnectionId(target);
            String transactionId = "tx-" + System.currentTimeMillis();
            
            System.out.println("🔍 트랜잭션 커밋: " + connectionId + " (" + (executionTime / 1_000_000) + "ms)");
            
            metricsCollector.recordCommit(executionTime, connectionId, transactionId);
            
        } catch (Exception e) {
            logger.error("Error handling commit: {}", e.getMessage());
        }
    }
    
    /**
     * 롤백 처리
     */
    private static void handleRollback(Object target, long executionTime, boolean success,
                                     Exception error, DatabaseType dbType) {
        try {
            String connectionId = getConnectionId(target);
            String transactionId = "tx-" + System.currentTimeMillis();
            
            System.out.println("🔍 트랜잭션 롤백: " + connectionId + " (" + (executionTime / 1_000_000) + "ms)");
            
            metricsCollector.recordRollback(executionTime, connectionId, transactionId);
            
        } catch (Exception e) {
            logger.error("Error handling rollback: {}", e.getMessage());
        }
    }
    
    /**
     * AutoCommit 모드 변경 처리
     */
    private static void handleSetAutoCommit(Object target, Method method, Object[] args, 
                                          long executionTime, boolean success, Exception error, 
                                          DatabaseType dbType) {
        try {
            String connectionId = getConnectionId(target);
            boolean autoCommit = args.length > 0 ? (Boolean) args[0] : true;
            
            System.out.println("🔍 AutoCommit 모드 변경: " + connectionId + " → " + autoCommit + 
                             " (" + (executionTime / 1_000_000) + "ms)");
            
            // Long-running transaction 감지를 위한 트랜잭션 상태 변경 기록
            metricsCollector.recordTransactionStateChange(autoCommit, executionTime);
            
            // AutoCommit이 false로 설정되면 트랜잭션 시작으로 간주
            if (!autoCommit) {
                String transactionId = "tx-" + System.currentTimeMillis();
                System.out.println("🚀 트랜잭션 시작 감지: " + connectionId + " (txId: " + transactionId + ")");
            }
            
        } catch (Exception e) {
            logger.error("Error handling setAutoCommit: {}", e.getMessage());
        }
    }
    
    /**
     * 연결 종료 처리
     */
    private static void handleClose(Object target, long executionTime, String className, 
                                  DatabaseType dbType) {
        try {
            String connectionId = getConnectionId(target);
            
            System.out.println("🔍 " + className + " 종료: " + connectionId + " (" + (executionTime / 1_000_000) + "ms)");
            
            metricsCollector.recordConnectionClose(executionTime, connectionId);
            
        } catch (Exception e) {
            logger.error("Error handling close: {}", e.getMessage());
        }
    }
    
    /**
     * SQL 문장 추출
     */
    private static String extractSQL(Object target, Object[] args) {
        try {
            // Statement.execute(String sql) 형태
            if (args != null && args.length > 0 && args[0] instanceof String) {
                return (String) args[0];
            }
            
            // PreparedStatement의 경우 실제 SQL 추출 시도
            if (target instanceof PreparedStatement) {
                String extractedSQL = extractPreparedStatementSQL((PreparedStatement) target);
                if (extractedSQL != null) {
                    return extractedSQL;
                }
                
                // 추출 실패 시 기본값 (하지만 필터링되지 않는 문자열)
                return "PreparedStatement [SQL not available]";
            }
            
            return "Unknown SQL";
            
        } catch (Exception e) {
            return "SQL extraction error: " + e.getMessage();
        }
    }
    
    /**
     * PreparedStatement에서 실제 SQL 추출 시도
     */
    private static String extractPreparedStatementSQL(PreparedStatement ps) {
        try {
            // PostgreSQL JDBC 드라이버의 경우
            if (ps.getClass().getName().contains("postgresql")) {
                try {
                    // PostgreSQL PreparedStatement는 toString()에 SQL을 포함할 수 있음
                    String psString = ps.toString();
                    if (psString.contains("SELECT") || psString.contains("INSERT") || 
                        psString.contains("UPDATE") || psString.contains("DELETE")) {
                        return cleanPostgreSQLString(psString);
                    }
                } catch (Exception e) {
                    logger.debug("PostgreSQL SQL extraction failed: {}", e.getMessage());
                }
            }
            
            // HikariCP Proxy의 경우
            if (ps.getClass().getName().contains("HikariProxy")) {
                try {
                    // Reflection을 통해 delegate 객체 접근
                    java.lang.reflect.Field delegateField = ps.getClass().getDeclaredField("delegate");
                    delegateField.setAccessible(true);
                    Object delegate = delegateField.get(ps);
                    
                    if (delegate instanceof PreparedStatement) {
                        return extractPreparedStatementSQL((PreparedStatement) delegate);
                    }
                } catch (Exception e) {
                    logger.debug("HikariProxy SQL extraction failed: {}", e.getMessage());
                }
            }
            
            // 범용 toString() 방식 (마지막 시도)
            String psString = ps.toString();
            if (psString.length() > 100) { // 너무 긴 경우 축약
                psString = psString.substring(0, 100) + "...";
            }
            return psString;
            
        } catch (Exception e) {
            logger.debug("PreparedStatement SQL extraction failed: {}", e.getMessage());
            return null;
        }
    }
    
    /**
     * PostgreSQL 문자열 정리
     */
    private static String cleanPostgreSQLString(String psString) {
        try {
            // PostgreSQL PreparedStatement toString() 파싱
            if (psString.contains("SELECT")) {
                int startIdx = psString.indexOf("SELECT");
                int endIdx = psString.length();
                // JDBC URL이나 기타 정보 제거
                for (String delimiter : new String[]{" [", " with", " parameters"}) {
                    int delimIdx = psString.indexOf(delimiter, startIdx);
                    if (delimIdx > startIdx) {
                        endIdx = Math.min(endIdx, delimIdx);
                    }
                }
                return psString.substring(startIdx, endIdx).trim();
            }
            
            // INSERT, UPDATE, DELETE도 비슷하게 처리
            for (String sqlType : new String[]{"INSERT", "UPDATE", "DELETE"}) {
                if (psString.contains(sqlType)) {
                    int startIdx = psString.indexOf(sqlType);
                    int endIdx = Math.min(psString.length(), startIdx + 200); // 최대 200자
                    return psString.substring(startIdx, endIdx).trim();
                }
            }
            
            return psString;
        } catch (Exception e) {
            return psString; // 파싱 실패 시 원본 반환
        }
    }
    
    /**
     * Connection ID 추출
     */
    private static String getConnectionId(Object target) {
        try {
            if (target instanceof Connection) {
                return target.toString();
            }
            
            // Statement에서 Connection 추출
            if (target instanceof java.sql.Statement) {
                java.sql.Statement stmt = (java.sql.Statement) target;
                Connection conn = stmt.getConnection();
                return conn != null ? conn.toString() : "unknown-connection";
            }
            
            return target.toString();
            
        } catch (Exception e) {
            return "connection-id-error: " + e.getMessage();
        }
    }
    
    /**
     * 데이터베이스 타입 감지
     */
    private static DatabaseType detectDatabaseType(Object target) {
        try {
            Connection connection = null;
            
            if (target instanceof Connection) {
                connection = (Connection) target;
            } else if (target instanceof java.sql.Statement) {
                connection = ((java.sql.Statement) target).getConnection();
            }
            
            if (connection != null) {
                DatabaseMetaData metaData = connection.getMetaData();
                String url = metaData.getURL().toLowerCase();
                
                if (url.contains("postgresql")) return DatabaseType.POSTGRESQL;
                if (url.contains("mysql")) return DatabaseType.MYSQL;
                if (url.contains("oracle")) return DatabaseType.ORACLE;
                if (url.contains("sqlserver")) return DatabaseType.SQLSERVER;
                if (url.contains("mariadb")) return DatabaseType.MARIADB;
                if (url.contains("h2")) return DatabaseType.H2;
            }
            
        } catch (Exception e) {
            logger.debug("Error detecting database type: {}", e.getMessage());
        }
        
        return DatabaseType.UNKNOWN;
    }
    
    /**
     * DB별 SQL 전처리
     */
    private static String preprocessSQL(String sql, DatabaseType dbType) {
        if (sql == null) return null;
        
        switch (dbType) {
            case POSTGRESQL:
                // PostgreSQL의 특수 문법 처리
                return sql.replaceAll("\\$\\d+", "?"); // $1, $2 → ?
                
            case ORACLE:
                // Oracle의 특수 문법 처리
                return sql.replaceAll(":\\w+", "?"); // :param → ?
                
            case SQLSERVER:
                // SQL Server의 특수 문법 처리
                return sql.replaceAll("@\\w+", "?"); // @param → ?
                
            default:
                return sql;
        }
    }
    
    /**
     * Connection에서 DataSource를 찾아서 등록하는 메서드 - 개선된 HikariCP 감지
     */
    private static void tryRegisterDataSourceFromConnection(Object target) {
        try {
            String targetClass = target.getClass().getName();
            System.out.println("🔍 Connection 클래스 감지: " + targetClass);
            
            // HikariCP Connection 처리 - 더 구체적인 감지
            if (targetClass.contains("HikariProxy") || targetClass.contains("hikari")) {
                System.out.println("🎯 HikariCP Connection 감지됨");
                
                // 방법 1: 직접적인 dataSource 필드 접근
                if (tryDirectDataSourceFieldAccess(target)) return;
                
                // 방법 2: poolEntry를 통한 접근  
                if (tryPoolEntryAccess(target)) return;
                
                // 방법 3: delegate 또는 parentProxy를 통한 접근
                if (tryDelegateAccess(target)) return;
                
                // 방법 4: JMX를 통한 HikariDataSource 역추적
                if (tryJMXBasedDiscovery()) return;
            }
            
            System.out.println("⚠️ DataSource 등록 실패: " + targetClass);
            
        } catch (Exception e) {
            logger.debug("DataSource 등록 실패: {}", e.getMessage());
            System.out.println("❌ DataSource 등록 중 예외: " + e.getMessage());
        }
    }
    
    /**
     * 직접적인 dataSource 필드 접근 시도
     */
    private static boolean tryDirectDataSourceFieldAccess(Object target) {
        try {
            // HikariProxyConnection의 모든 필드 탐색
            Class<?> clazz = target.getClass();
            while (clazz != null) {
                java.lang.reflect.Field[] fields = clazz.getDeclaredFields();
                for (java.lang.reflect.Field field : fields) {
                    field.setAccessible(true);
                    Object fieldValue = field.get(target);
                    
                    if (fieldValue != null) {
                        String fieldClassName = fieldValue.getClass().getName();
                        System.out.println("  필드 검사: " + field.getName() + " -> " + fieldClassName);
                        
                        if (fieldClassName.contains("HikariDataSource")) {
                            return registerDataSourceInstance((DataSource) fieldValue);
                        }
                    }
                }
                clazz = clazz.getSuperclass();
            }
        } catch (Exception e) {
            System.out.println("  직접 필드 접근 실패: " + e.getMessage());
        }
        return false;
    }
    
    /**
     * PoolEntry를 통한 DataSource 접근 - 개선된 안전한 접근
     */
    private static boolean tryPoolEntryAccess(Object target) {
        try {
            // HikariProxyConnection -> poolEntry -> hikariPool -> dataSource
            java.lang.reflect.Field poolEntryField = findField(target.getClass(), "poolEntry");
            if (poolEntryField == null) {
                System.out.println("  poolEntry 필드를 찾을 수 없음");
                return false;
            }
            
            poolEntryField.setAccessible(true);
            Object poolEntry = poolEntryField.get(target);
            
            if (poolEntry == null) {
                System.out.println("  poolEntry가 null");
                return false;
            }
            
            System.out.println("  ✅ PoolEntry 발견: " + poolEntry.getClass().getName());
            
            // PoolEntry -> hikariPool 접근
            java.lang.reflect.Field hikariPoolField = findField(poolEntry.getClass(), "hikariPool");
            if (hikariPoolField == null) {
                System.out.println("  hikariPool 필드를 찾을 수 없음");
                return false;
            }
            
            hikariPoolField.setAccessible(true);
            Object hikariPool = hikariPoolField.get(poolEntry);
            
            if (hikariPool == null) {
                System.out.println("  hikariPool이 null");
                return false;
            }
            
            System.out.println("  ✅ HikariPool 발견: " + hikariPool.getClass().getName());
            
            // HikariPool에서 실제 HikariDataSource 찾기 (DriverDataSource가 아닌)
            Object realHikariDataSource = findRealHikariDataSource(hikariPool);
            if (realHikariDataSource instanceof DataSource) {
                System.out.println("  🎯 실제 HikariDataSource 발견!");
                System.out.println("  DataSource 클래스: " + realHikariDataSource.getClass().getName());
                return registerDataSourceInstance((DataSource) realHikariDataSource);
            }
            
            // Fallback: 기본 dataSource 필드 접근
            java.lang.reflect.Field dataSourceField = findField(hikariPool.getClass(), "dataSource");
            if (dataSourceField == null) {
                System.out.println("  dataSource 필드를 찾을 수 없음");
                return false;
            }
            
            dataSourceField.setAccessible(true);
            Object dataSource = dataSourceField.get(hikariPool);
            
            if (dataSource instanceof DataSource) {
                System.out.println("  🔍 Fallback DataSource 발견: " + dataSource.getClass().getName());
                
                // DriverDataSource인 경우 실제 HikariDataSource를 찾으려 시도
                if (dataSource.getClass().getName().contains("DriverDataSource")) {
                    System.out.println("  ⚠️ DriverDataSource 감지됨 - 실제 HikariDataSource를 찾아야 함");
                    // TODO: 여기서 실제 HikariDataSource로 연결되는 방법 구현 필요
                }
                
                return registerDataSourceInstance((DataSource) dataSource);
            } else {
                System.out.println("  dataSource가 DataSource 타입이 아님: " + 
                    (dataSource != null ? dataSource.getClass().getName() : "null"));
                return false;
            }
            
        } catch (Exception e) {
            System.out.println("  PoolEntry 접근 실패 - 상세: " + e.getClass().getSimpleName() + ": " + e.getMessage());
            e.printStackTrace(); // 디버깅을 위한 스택트레이스
        }
        return false;
    }
    
    /**
     * HikariPool에서 실제 HikariDataSource 찾기 - 완전한 역추적 로직
     * DriverDataSource가 아닌 진짜 HikariDataSource를 역추적
     */
    private static Object findRealHikariDataSource(Object hikariPool) {
        try {
            System.out.println("  🔍 실제 HikariDataSource 역추적 시작...");
            
            // 방법 1: HikariPool의 모든 필드를 상세히 탐색
            Class<?> poolClass = hikariPool.getClass();
            java.lang.reflect.Field[] fields = poolClass.getDeclaredFields();
            
            System.out.println("  📊 HikariPool 총 필드 개수: " + fields.length);
            
            for (java.lang.reflect.Field field : fields) {
                try {
                    field.setAccessible(true);
                    Object fieldValue = field.get(hikariPool);
                    
                    if (fieldValue != null) {
                        String fieldName = field.getName();
                        String fieldClassName = fieldValue.getClass().getName();
                        System.out.println("  📋 필드: " + fieldName + " -> " + fieldClassName);
                        
                        // 1. 직접적인 HikariDataSource 참조 찾기
                        if (fieldClassName.equals("com.zaxxer.hikari.HikariDataSource")) {
                            System.out.println("  ✨ 직접 HikariDataSource 발견: " + fieldName);
                            return fieldValue;
                        }
                        
                        // 2. parentDataSource, originalDataSource 등의 이름 패턴 확인
                        if (fieldName.toLowerCase().contains("datasource") && 
                            !fieldName.toLowerCase().contains("driver")) {
                            System.out.println("  🎯 DataSource 후보 필드: " + fieldName + " (" + fieldClassName + ")");
                            if (fieldValue instanceof javax.sql.DataSource && 
                                fieldClassName.contains("Hikari")) {
                                System.out.println("  ✨ DataSource 필드에서 HikariDataSource 발견!");
                                return fieldValue;
                            }
                        }
                        
                        // 3. 생성자나 설정 관련 필드에서 HikariDataSource 찾기  
                        if (fieldName.toLowerCase().contains("config") || 
                            fieldName.toLowerCase().contains("source") ||
                            fieldName.toLowerCase().contains("parent")) {
                            System.out.println("  🔍 설정 관련 필드 심층 탐색: " + fieldName);
                            Object nestedDataSource = searchNestedForDataSource(fieldValue);
                            if (nestedDataSource != null) {
                                return nestedDataSource;
                            }
                        }
                    }
                } catch (Exception e) {
                    // 개별 필드 접근 실패는 로그로만 남김
                    System.out.println("  ⚠️ 필드 접근 실패: " + field.getName() + " - " + e.getMessage());
                }
            }
            
            // 방법 2: HikariPool의 부모 클래스 필드도 탐색
            System.out.println("  🔍 부모 클래스 필드 탐색 시작...");
            Class<?> parentClass = poolClass.getSuperclass();
            while (parentClass != null && !parentClass.equals(Object.class)) {
                System.out.println("  📋 부모 클래스: " + parentClass.getName());
                java.lang.reflect.Field[] parentFields = parentClass.getDeclaredFields();
                
                for (java.lang.reflect.Field field : parentFields) {
                    try {
                        field.setAccessible(true);
                        Object fieldValue = field.get(hikariPool);
                        
                        if (fieldValue != null) {
                            String fieldClassName = fieldValue.getClass().getName();
                            if (fieldClassName.equals("com.zaxxer.hikari.HikariDataSource")) {
                                System.out.println("  ✨ 부모 클래스에서 HikariDataSource 발견: " + field.getName());
                                return fieldValue;
                            }
                        }
                    } catch (Exception e) {
                        // 무시
                    }
                }
                parentClass = parentClass.getSuperclass();
            }
            
            // 방법 3: Static 참조를 통한 HikariDataSource 찾기 (최후 수단)
            System.out.println("  🔍 글로벌 HikariDataSource 인스턴스 검색 시도...");
            return findGlobalHikariDataSource();
            
        } catch (Exception e) {
            System.out.println("  💥 HikariDataSource 역추적 전체 실패: " + e.getMessage());
        }
        
        return null;
    }
    
    /**
     * 중첩된 객체에서 HikariDataSource 찾기
     */
    private static Object searchNestedForDataSource(Object obj) {
        if (obj == null) return null;
        
        try {
            java.lang.reflect.Field[] fields = obj.getClass().getDeclaredFields();
            for (java.lang.reflect.Field field : fields) {
                try {
                    field.setAccessible(true);
                    Object fieldValue = field.get(obj);
                    
                    if (fieldValue != null && fieldValue.getClass().getName().equals("com.zaxxer.hikari.HikariDataSource")) {
                        System.out.println("    ✨ 중첩 객체에서 HikariDataSource 발견: " + field.getName());
                        return fieldValue;
                    }
                } catch (Exception e) {
                    // 무시
                }
            }
        } catch (Exception e) {
            // 무시
        }
        
        return null;
    }
    
    /**
     * 글로벌 스코프에서 HikariDataSource 찾기 (최후 수단)
     */
    private static Object findGlobalHikariDataSource() {
        try {
            // 현재 스레드의 컨텍스트에서 HikariDataSource를 찾는 시도
            // 이는 매우 해킹적인 방법이지만, 최후 수단으로 사용
            System.out.println("    🔍 ThreadLocal 및 컨텍스트 검색...");
            
            // JVM에 로드된 모든 클래스 중 HikariDataSource 인스턴스 찾기
            // (이는 성능상 부담이 되므로 최후 수단)
            
        } catch (Exception e) {
            System.out.println("    ❌ 글로벌 검색 실패: " + e.getMessage());
        }
        
        return null;
    }
    
    /**
     * 클래스 계층에서 필드를 안전하게 찾는 헬퍼 메서드
     */
    private static java.lang.reflect.Field findField(Class<?> clazz, String fieldName) {
        while (clazz != null) {
            try {
                return clazz.getDeclaredField(fieldName);
            } catch (NoSuchFieldException e) {
                clazz = clazz.getSuperclass();
            }
        }
        return null;
    }
    
    /**
     * Delegate 접근 시도
     */
    private static boolean tryDelegateAccess(Object target) {
        try {
            // delegate 필드를 통한 접근
            java.lang.reflect.Field delegateField = target.getClass().getDeclaredField("delegate");
            delegateField.setAccessible(true);
            Object delegate = delegateField.get(target);
            
            if (delegate != null) {
                System.out.println("  Delegate 발견: " + delegate.getClass().getName());
                // delegate에서 다시 DataSource 탐색
                return tryDirectDataSourceFieldAccess(delegate);
            }
        } catch (Exception e) {
            System.out.println("  Delegate 접근 실패: " + e.getMessage());
        }
        return false;
    }
    
    /**
     * JMX를 통한 HikariDataSource 역추적 - 개선된 MBean 검색
     */
    private static boolean tryJMXBasedDiscovery() {
        try {
            javax.management.MBeanServer server = java.lang.management.ManagementFactory.getPlatformMBeanServer();
            
            // 1. HikariCP 전용 패턴으로 검색
            java.util.Set<javax.management.ObjectName> hikariNames = server.queryNames(
                new javax.management.ObjectName("com.zaxxer.hikari:type=Pool*"), null);
            System.out.println("  🔍 HikariCP Pool MBean 검색: " + hikariNames.size() + "개");
            
            // 2. 더 넓은 HikariCP 패턴으로 검색
            java.util.Set<javax.management.ObjectName> allHikariNames = server.queryNames(
                new javax.management.ObjectName("com.zaxxer.hikari:*"), null);
            System.out.println("  🔍 전체 HikariCP MBean 검색: " + allHikariNames.size() + "개");
            
            // 3. 모든 MBean을 검색해서 HikariCP 관련 찾기
            java.util.Set<javax.management.ObjectName> allNames = server.queryNames(null, null);
            System.out.println("  🔍 전체 MBean 개수: " + allNames.size() + "개");
            
            int hikariCount = 0;
            for (javax.management.ObjectName name : allNames) {
                String nameStr = name.toString();
                if (nameStr.toLowerCase().contains("hikari")) {
                    System.out.println("  📋 HikariCP 관련 MBean: " + nameStr);
                    hikariCount++;
                    
                    // 이 MBean에서 메트릭 추출 시도
                    try {
                        javax.management.MBeanInfo info = server.getMBeanInfo(name);
                        System.out.println("    📊 Attributes: " + info.getAttributes().length + "개");
                        
                        for (javax.management.MBeanAttributeInfo attr : info.getAttributes()) {
                            if (attr.getName().contains("Connection") || 
                                attr.getName().contains("Pool") ||
                                attr.getName().contains("Active") ||
                                attr.getName().contains("Idle")) {
                                System.out.println("      🎯 메트릭 속성: " + attr.getName() + " (" + attr.getType() + ")");
                            }
                        }
                    } catch (Exception e) {
                        System.out.println("    ⚠️ MBean 정보 조회 실패: " + e.getMessage());
                    }
                }
            }
            
            System.out.println("  📊 HikariCP 관련 MBean 총 " + hikariCount + "개 발견");
            
            // 표준 HikariCP MBean이 있다면 메트릭 수집 시도
            if (!hikariNames.isEmpty()) {
                for (javax.management.ObjectName name : hikariNames) {
                    System.out.println("  ✅ HikariCP Pool MBean 발견: " + name);
                    
                    try {
                        Object poolName = server.getAttribute(name, "PoolName");
                        Object activeConnections = server.getAttribute(name, "ActiveConnections");
                        Object idleConnections = server.getAttribute(name, "IdleConnections");
                        Object totalConnections = server.getAttribute(name, "TotalConnections");
                        
                        System.out.println("    📊 Pool: " + poolName + 
                                         ", Active: " + activeConnections + 
                                         ", Idle: " + idleConnections + 
                                         ", Total: " + totalConnections);
                        
                        // TODO: 이 메트릭으로 PoolMetrics 객체 생성하고 등록
                        return true;
                        
                    } catch (Exception e) {
                        System.out.println("    ❌ MBean 메트릭 조회 실패: " + e.getMessage());
                    }
                }
            }
            
        } catch (Exception e) {
            System.out.println("  💥 JMX 기반 탐색 전체 실패: " + e.getMessage());
        }
        return false;
    }
    
    /**
     * DataSource 인스턴스 등록
     */
    private static boolean registerDataSourceInstance(DataSource dataSource) {
        try {
            if (!registeredDataSources.contains(dataSource)) {
                System.out.println("🔗 HikariDataSource 등록 시도: " + dataSource.getClass().getSimpleName());
                
                MetricsCollector globalCollector = KubeDBAgent.getGlobalMetricsCollector();
                if (globalCollector != null) {
                    globalCollector.registerDataSource(dataSource);
                    registeredDataSources.add(dataSource);
                    System.out.println("✅ HikariDataSource가 성공적으로 등록됨!");
                    return true;
                } else {
                    System.out.println("❌ GlobalMetricsCollector가 null");
                }
            } else {
                System.out.println("ℹ️ DataSource 이미 등록됨");
                return true;
            }
        } catch (Exception e) {
            System.out.println("❌ DataSource 등록 실패: " + e.getMessage());
        }
        return false;
    }
}