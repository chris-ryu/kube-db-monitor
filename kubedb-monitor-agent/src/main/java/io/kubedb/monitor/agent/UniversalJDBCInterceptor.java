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
import java.util.*;
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
    
    // 🚨 지연 인터셉션: HikariCP와 PostgreSQL 초기화 완료까지 대기
    private static volatile boolean databaseInitializationComplete = false;
    private static final long INITIALIZATION_WAIT_TIME_MS = 30000; // 30초 대기
    private static long agentStartTime = System.currentTimeMillis();
    
    // 이미 등록된 DataSource를 추적하여 중복 등록 방지
    private static final Set<DataSource> registeredDataSources = ConcurrentHashMap.newKeySet();
    
    // 트랜잭션별 쿼리 히스토리 저장 (connection_id -> 쿼리 리스트)
    private static final Map<String, List<QueryHistoryEntry>> transactionQueryHistory = new ConcurrentHashMap<>();
    
    // 실행 중인 쿼리 정보 저장 (thread_id -> 현재 실행 중인 SQL 정보)
    private static final Map<String, ActiveQueryInfo> activeQueries = new ConcurrentHashMap<>();
    
    // Connection ID 기반 실행 중인 쿼리 매핑 (connection_id -> ActiveQueryInfo)
    private static final Map<String, ActiveQueryInfo> activeQueriesByConnection = new ConcurrentHashMap<>();
    
    // 실행 중인 쿼리 정보 클래스
    private static class ActiveQueryInfo {
        final String sql;
        final String connectionId;
        final long startTime;
        final String queryType;
        
        ActiveQueryInfo(String sql, String connectionId, String queryType) {
            this.sql = sql;
            this.connectionId = connectionId;
            this.startTime = System.currentTimeMillis();
            this.queryType = queryType;
        }
    }
    
    // 쿼리 히스토리 엔트리
    private static class QueryHistoryEntry {
        final String sql;
        final long timestamp;
        final String queryType;
        final long executionTime;
        
        QueryHistoryEntry(String sql, String queryType, long executionTime) {
            this.sql = sql;
            this.timestamp = System.currentTimeMillis();
            this.queryType = queryType;
            this.executionTime = executionTime;
        }
    }
    
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
        
        // PostgreSQL 시스템 메서드 제외 필터 (우선 처리)
        if (isSystemMethod(method.getName())) {
            return callable.call();
        }
        
        // 🚨 지연 인터셉션: 데이터베이스 초기화 완료 대기
        if (!isDatabaseInitializationReady()) {
            return callable.call();
        }
        
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
        
        // 🚀 Long Running Transaction SQL 추적 로직 복원 (PostgreSQL 호환성 유지)
        String connectionId = null;
        String threadId = null;
        if (methodName.contains("execute") && !methodName.contains("Batch")) {
            try {
                connectionId = getConnectionId(target);
                threadId = Thread.currentThread().getName() + "-" + Thread.currentThread().getId();
                String sql = extractSQL(target, args);
                
                // PostgreSQL 호환성을 위한 안전한 SQL 추출
                if (sql != null && !sql.contains("[SQL extraction failed]") && !sql.trim().isEmpty() 
                    && !isSystemQuerySQL(sql)) {
                    ActiveQueryInfo queryInfo = new ActiveQueryInfo(sql, connectionId, extractSQLType(sql));
                    activeQueries.put(threadId, queryInfo);
                    activeQueriesByConnection.put(connectionId, queryInfo);
                    logger.debug("[KubeDB] SQL 실행 시작 저장: {} on {}", threadId, connectionId);
                }
            } catch (Exception e) {
                // SQL 실행 시작 시점 저장 실패해도 메인 로직에 영향 없음
                logger.warn("[KubeDB] SQL 실행 시작 시점 정보 저장 실패: {}", e.getMessage());
            }
        }
        
        try {
            // SQL 실행 관련 메서드만 로깅
            if (methodName.contains("execute") || methodName.contains("prepare") || methodName.contains("commit") || methodName.contains("rollback")) {
                System.out.println("🔍 JDBC 메서드 인터셉트: " + className + "." + methodName + " (args: " + (args != null ? args.length : 0) + ")");
                if (args != null && args.length > 0 && args[0] instanceof String) {
                    System.out.println("   SQL: " + args[0]);
                }
            }
            
            Object result = callable.call();
            long executionTime = System.nanoTime() - startTime;
            
            // 메서드 타입별 처리
            handleMethodExecution(target, method, args, executionTime, true, null);
            
            return result;
            
        } catch (Exception e) {
            long executionTime = System.nanoTime() - startTime;
            
            // 🚨 Long Running Transaction 관련 코드 제거됨
            
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
            String className = target.getClass().getName();
            boolean isHikariProxy = className.contains("HikariProxy");
            
            logger.debug("[KubeDB] ⚡ handleStatementExecution 호출됨! Method: {}, Target: {}, HikariCP: {}", 
                        method.getName(), target.getClass().getSimpleName(), isHikariProxy);
            
            System.out.println("🎯 SQL 실행 감지 (HikariCP: " + isHikariProxy + "): " + className + "." + method.getName());
            
            String sql = extractSQL(target, args);
            
            // HikariCP 프록시의 경우 추가 SQL 추출 시도
            if ((sql == null || sql.trim().isEmpty()) && isHikariProxy) {
                sql = extractSqlFromHikariProxy(target);
                System.out.println("🔄 HikariCP 전용 SQL 추출 시도: " + (sql != null ? "성공" : "실패"));
            }
            
            String connectionId = getConnectionId(target);
            String threadName = Thread.currentThread().getName();
            
            System.out.println("🔍 SQL 실행 감지: " + sql + " (" + (executionTime / 1_000_000) + "ms)");
            System.out.println("   Connection ID: " + connectionId + ", Thread: " + threadName);
            logger.debug("[KubeDB] SQL 실행 감지: {} ({}ms)", sql, executionTime / 1_000_000);
            
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
            
            // 🔥 SQL 실행 완료시 ActiveQueryInfo 관리 (Long Running Transaction에서 사용)
            if (sql != null && !sql.contains("[SQL extraction failed]")) {
                String threadId = Thread.currentThread().getName() + "-" + Thread.currentThread().getId();
                
                // 실행 완료시에 ActiveQueryInfo 제거 (실행 시작은 별도 위치에서 처리)
                if (executionTimeMs > 0) {
                    // 실행 완료시 Thread ID와 Connection ID 둘 다 제거
                    activeQueries.remove(threadId);
                    activeQueriesByConnection.remove(connectionId);
                    System.out.println("🏁 실행 완료 SQL 제거 (Thread + Connection): " + threadId + " & " + connectionId + " (" + executionTimeMs + "ms)");
                }
            }
            
            // 쿼리 히스토리에 기록 (성공한 SQL만)
            if (sql != null && !sql.contains("[SQL extraction failed]") && success) {
                recordQueryInHistory(connectionId, sql, extractSQLType(sql), executionTimeMs);
            }
            
            // 암시적 트랜잭션 감지 - Connection의 AutoCommit 상태 확인
            checkAndDetectImplicitTransaction(target, connectionId, threadName);
            
            // 트랜잭션 시작 감지 및 기록
            handleTransactionBeginIfNeeded(sql, connectionId, threadName);
            
            // 메트릭 수집 (기존 방식 유지)
            if (success) {
                System.out.println("✅ SQL 메트릭 수집 중: " + sql);
                metricsCollector.recordQuery(sql, executionTime, connectionId, threadName);
                
                // Long running transaction에 현재 실행 중인 쿼리 정보 업데이트
                System.out.println("🔄 Active transaction에 쿼리 정보 업데이트 중...");
                metricsCollector.updateActiveTransactionQuery(sql, connectionId, threadName, executionTime / 1_000_000);
                
                System.out.println("✅ SQL 메트릭 수집 완료");
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
            
            // 트랜잭션 커밋시 쿼리 히스토리 정리
            clearTransactionHistory(connectionId);
            
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
            
            // 트랜잭션 롤백시 쿼리 히스토리 정리
            clearTransactionHistory(connectionId);
            
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
            String threadName = Thread.currentThread().getName();
            
            System.out.println("🔍 setAutoCommit 인터셉트 감지: " + connectionId + " → " + autoCommit + 
                             " (" + (executionTime / 1_000_000) + "ms) [Thread: " + threadName + "]");
            logger.debug("[KubeDB] setAutoCommit called: connectionId={}, autoCommit={}, thread={}", 
                        connectionId, autoCommit, threadName);
            
            // Long-running transaction 감지를 위한 트랜잭션 상태 변경 기록
            metricsCollector.recordTransactionStateChange(autoCommit, executionTime, connectionId);
            
            // AutoCommit이 false로 설정되면 트랜잭션 시작으로 간주
            if (!autoCommit) {
                String transactionId = generateTransactionId(connectionId, threadName);
                boolean transactionStarted = metricsCollector.recordTransactionBegin(connectionId, transactionId);
                
                if (transactionStarted) {
                    System.out.println("🚀 트랜잭션 시작 감지 (setAutoCommit): " + connectionId + " (txId: " + transactionId + ")");
                    logger.info("[KubeDB] Transaction started via setAutoCommit: {} on connection {}", 
                               transactionId, connectionId);
                } else {
                    System.out.println("ℹ️ 트랜잭션 이미 활성화됨: " + connectionId);
                    logger.debug("[KubeDB] Transaction already active on connection {}", connectionId);
                }
            } else {
                System.out.println("🔚 AutoCommit 활성화 - 트랜잭션 종료: " + connectionId);
                logger.debug("[KubeDB] AutoCommit enabled, ending transaction on connection {}", connectionId);
            }
            
        } catch (Exception e) {
            System.out.println("❌ setAutoCommit 처리 중 오류: " + e.getMessage());
            logger.error("Error handling setAutoCommit: {}", e.getMessage(), e);
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
            
            // HikariCP 프록시 객체 특별 처리 (최우선)
            String className = target.getClass().getName();
            if (className.contains("HikariProxy")) {
                System.out.println("🎯 HikariCP 프록시에서 SQL 추출 시도: " + className);
                String hikariSQL = extractSqlFromHikariProxy(target);
                if (hikariSQL != null && !hikariSQL.trim().isEmpty() && !hikariSQL.equals("null")) {
                    System.out.println("✅ HikariCP SQL 추출 성공: " + hikariSQL.substring(0, Math.min(50, hikariSQL.length())) + "...");
                    return hikariSQL;
                } else {
                    System.out.println("❌ HikariCP SQL 추출 실패, 대체 방법 시도");
                    // 실패시에도 toString에서 SQL을 찾아서 반환
                    String toString = target.toString();
                    if (toString.contains("wrapping ") && toString.toLowerCase().matches(".*\\b(select|insert|update|delete|create|drop|alter)\\b.*")) {
                        int start = toString.indexOf("wrapping ") + 9;
                        String fallbackSQL = toString.substring(start).trim();
                        System.out.println("🔄 대체 방법으로 SQL 추출: " + fallbackSQL.substring(0, Math.min(60, fallbackSQL.length())));
                        return fallbackSQL;
                    }
                    // 최후 수단: 기본 정보 제공
                    return "[SQL extraction failed] " + className + " - " + toString.substring(0, Math.min(80, toString.length()));
                }
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
     * Connection ID 추출 (HikariCP underlying connection 기반)
     */
    private static String getConnectionId(Object target) {
        try {
            Connection connection = null;
            
            if (target instanceof Connection) {
                connection = (Connection) target;
            } else if (target instanceof java.sql.Statement) {
                java.sql.Statement stmt = (java.sql.Statement) target;
                connection = stmt.getConnection();
            }
            
            if (connection != null) {
                // HikariCP 프록시인 경우 실제 underlying connection 추출
                if (connection.getClass().getName().contains("HikariProxy")) {
                    try {
                        // Method 1: delegate 필드를 통한 underlying connection 접근
                        java.lang.reflect.Field delegateField = connection.getClass().getDeclaredField("delegate");
                        delegateField.setAccessible(true);
                        Object delegate = delegateField.get(connection);
                        
                        if (delegate instanceof Connection) {
                            Connection underlyingConn = (Connection) delegate;
                            // PostgreSQL connection의 고유 정보 추출
                            String connString = underlyingConn.toString();
                            
                            // PostgreSQL connection에서 더 안정적인 식별자 추출
                            if (connString.contains("PgConnection")) {
                                // PgConnection의 hashCode나 고유 정보 사용
                                return "pg-conn-" + Integer.toHexString(underlyingConn.hashCode());
                            }
                            
                            return "underlying-" + Integer.toHexString(underlyingConn.hashCode());
                        }
                    } catch (Exception e) {
                        // Method 2: URL 기반 연결 정보 + 스레드 정보로 고유 ID 생성
                        try {
                            DatabaseMetaData metaData = connection.getMetaData();
                            String url = metaData.getURL();
                            String user = metaData.getUserName();
                            
                            // URL과 사용자명을 기반으로 안정적인 connection ID 생성
                            String baseId = url + "-" + user;
                            return "stable-conn-" + Integer.toHexString(baseId.hashCode());
                        } catch (Exception metaEx) {
                            // Fallback: HikariCP proxy의 hashCode 사용
                            return "hikari-proxy-" + Integer.toHexString(connection.hashCode());
                        }
                    }
                }
                
                // 일반 Connection인 경우
                return "conn-" + Integer.toHexString(connection.hashCode());
            }
            
            return "target-" + Integer.toHexString(target.hashCode());
            
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
    
    /**
     * 암시적 트랜잭션 감지 - Connection의 AutoCommit 상태 확인
     */
    private static void checkAndDetectImplicitTransaction(Object target, String connectionId, String threadName) {
        if (metricsCollector == null) {
            return;
        }
        
        try {
            // Connection 객체 추출
            java.sql.Connection connection = null;
            
            if (target instanceof java.sql.Connection) {
                connection = (java.sql.Connection) target;
            } else if (target instanceof java.sql.Statement) {
                connection = ((java.sql.Statement) target).getConnection();
            }
            
            if (connection != null) {
                boolean autoCommit = connection.getAutoCommit();
                
                System.out.println("🔍 Connection AutoCommit 상태 확인: " + connectionId + " → " + autoCommit);
                logger.debug("[KubeDB] Checking connection autoCommit: connectionId={}, autoCommit={}", 
                           connectionId, autoCommit);
                
                // AutoCommit이 false이고 아직 트랜잭션이 기록되지 않은 경우 암시적 트랜잭션으로 간주
                if (!autoCommit && !metricsCollector.hasActiveTransaction(connectionId)) {
                    String transactionId = generateTransactionId(connectionId, threadName);
                    boolean transactionStarted = metricsCollector.recordTransactionBegin(connectionId, transactionId);
                    
                    if (transactionStarted) {
                        System.out.println("🚀 암시적 트랜잭션 시작 감지: " + connectionId + " (txId: " + transactionId + ")");
                        logger.info("[KubeDB] Implicit transaction started: {} on connection {}", 
                                   transactionId, connectionId);
                    }
                }
            }
            
        } catch (Exception e) {
            System.out.println("❌ 암시적 트랜잭션 감지 중 오류: " + e.getMessage());
            logger.debug("Error detecting implicit transaction: {}", e.getMessage());
        }
    }
    
    /**
     * SQL에서 트랜잭션 시작 감지 및 기록
     */
    private static void handleTransactionBeginIfNeeded(String sql, String connectionId, String threadName) {
        if (sql == null || metricsCollector == null) {
            return;
        }
        
        String trimmedSql = sql.trim().toUpperCase();
        
        // 명시적 트랜잭션 시작 감지
        if (trimmedSql.startsWith("BEGIN") || 
            trimmedSql.startsWith("START TRANSACTION") || 
            trimmedSql.contains("BEGIN TRANSACTION")) {
            
            String transactionId = generateTransactionId(connectionId, threadName);
            boolean transactionStarted = metricsCollector.recordTransactionBegin(connectionId, transactionId);
            
            if (transactionStarted) {
                logger.info("[KubeDB] 명시적 트랜잭션 시작 감지: {} on connection {}", transactionId, connectionId);
            }
        } 
        // 🎯 Spring @Transactional 환경에서 암시적 트랜잭션 감지 강화
        // 첫 번째 SQL 실행 시 항상 트랜잭션 시작으로 간주 (Spring Boot 환경 특성)
        else {
            // Connection별 트랜잭션 상태 확인 및 시작
            String transactionId = generateTransactionId(connectionId, threadName);
            boolean transactionStarted = metricsCollector.recordTransactionBegin(connectionId, transactionId);
            
            if (transactionStarted) {
                logger.debug("[KubeDB] 🎯 Spring @Transactional 암시적 트랜잭션 시작: {} on connection {} (SQL: {})", 
                           transactionId, connectionId, truncateSQL(sql));
            }
        }
    }
    
    /**
     * 쿼리 히스토리에 기록
     */
    private static void recordQueryInHistory(String connectionId, String sql, String queryType, long executionTimeMs) {
        if (connectionId == null || sql == null) return;
        
        transactionQueryHistory.computeIfAbsent(connectionId, k -> new ArrayList<>())
                .add(new QueryHistoryEntry(sql, queryType, executionTimeMs));
        
        // 히스토리가 너무 길면 오래된 것 제거 (최대 20개 유지)
        List<QueryHistoryEntry> history = transactionQueryHistory.get(connectionId);
        if (history.size() > 20) {
            history.subList(0, history.size() - 20).clear();
        }
        
        System.out.println("📝 쿼리 히스토리 기록: " + connectionId + " -> " + sql.substring(0, Math.min(50, sql.length())));
    }
    
    /**
     * 트랜잭션 쿼리 히스토리 조회
     */
    public static List<QueryHistoryEntry> getTransactionHistory(String connectionId) {
        if (connectionId == null) return new ArrayList<>();
        return new ArrayList<>(transactionQueryHistory.getOrDefault(connectionId, new ArrayList<>()));
    }
    
    /**
     * 현재 실행 중인 쿼리 정보 조회 (Long Running Transaction에서 사용)
     */
    public static ActiveQueryInfo getActiveQueryByThread(String threadName) {
        // threadName으로 시작하는 키를 찾아서 반환
        for (Map.Entry<String, ActiveQueryInfo> entry : activeQueries.entrySet()) {
            if (entry.getKey().startsWith(threadName + "-")) {
                return entry.getValue();
            }
        }
        return null;
    }
    
    /**
     * 현재 실행 중인 쿼리 정보 조회 (Connection ID 기반)
     */
    public static ActiveQueryInfo getActiveQueryByConnection(String connectionId) {
        if (connectionId == null) return null;
        
        // 직접 Connection ID 기반 맵에서 조회 (빠름)
        ActiveQueryInfo result = activeQueriesByConnection.get(connectionId);
        if (result != null) {
            return result;
        }
        
        // 백업: 기존 방식으로 Thread 기반 맵에서 Connection ID로 검색
        for (ActiveQueryInfo queryInfo : activeQueries.values()) {
            if (connectionId.equals(queryInfo.connectionId)) {
                return queryInfo;
            }
        }
        return null;
    }
    
    /**
     * 현재 실행 중인 쿼리 정보 조회 (Thread ID 기반 - HttpMetricsTransmitter에서 사용)
     */
    public static Object getActiveQueryInfo(String threadId) {
        if (threadId == null) return null;
        return activeQueries.get(threadId);
    }
    
    /**
     * 현재 활성화된 쿼리 중 아무거나 하나 반환 (Connection ID 매핑 문제 해결용)
     */
    public static Object getAnyActiveQueryInfo() {
        // Connection ID 기반 맵에서 먼저 찾기
        if (!activeQueriesByConnection.isEmpty()) {
            return activeQueriesByConnection.values().iterator().next();
        }
        
        // Thread ID 기반 맵에서 찾기
        if (!activeQueries.isEmpty()) {
            return activeQueries.values().iterator().next();
        }
        
        return null;
    }
    
    /**
     * 트랜잭션 종료시 히스토리 정리
     */
    private static void clearTransactionHistory(String connectionId) {
        if (connectionId != null) {
            transactionQueryHistory.remove(connectionId);
            System.out.println("🗑️ 트랜잭션 히스토리 정리: " + connectionId);
        }
    }
    
    /**
     * SQL 타입 추출
     */
    private static String extractSQLType(String sql) {
        if (sql == null) return "UNKNOWN";
        
        String upperSQL = sql.trim().toUpperCase();
        if (upperSQL.startsWith("SELECT")) return "SELECT";
        if (upperSQL.startsWith("INSERT")) return "INSERT";
        if (upperSQL.startsWith("UPDATE")) return "UPDATE";
        if (upperSQL.startsWith("DELETE")) return "DELETE";
        if (upperSQL.startsWith("CREATE")) return "CREATE";
        if (upperSQL.startsWith("DROP")) return "DROP";
        if (upperSQL.startsWith("ALTER")) return "ALTER";
        return "OTHER";
    }
    
    /**
     * DML 문인지 확인
     */
    private static boolean isDMLStatement(String sql) {
        return sql.startsWith("INSERT") || 
               sql.startsWith("UPDATE") || 
               sql.startsWith("DELETE") ||
               sql.startsWith("MERGE") ||
               sql.startsWith("UPSERT");
    }
    
    /**
     * SQL 문자열을 로깅용으로 축약
     */
    private static String truncateSQL(String sql) {
        if (sql == null) return "null";
        if (sql.length() <= 50) return sql;
        return sql.substring(0, 47) + "...";
    }
    
    /**
     * 트랜잭션 ID 생성
     */
    private static String generateTransactionId(String connectionId, String threadName) {
        return String.format("tx-%s-%s-%d", 
                           connectionId != null ? connectionId.replaceAll("[^a-zA-Z0-9]", "") : "unknown",
                           threadName != null ? threadName.replaceAll("[^a-zA-Z0-9]", "") : "unknown", 
                           System.nanoTime());
    }
    
    /**
     * HikariCP 프록시에서 SQL 추출
     */
    private static String extractSqlFromHikariProxy(Object target) {
        try {
            // 1. toString() 메서드에서 SQL 정보 추출 시도
            String toString = target.toString();
            System.out.println("🔍 HikariCP toString: " + toString);
            
            // toString에서 실제 SQL 찾기
            if (toString.contains("wrapping ")) {
                int start = toString.indexOf("wrapping ") + 9;
                int end = toString.length();
                String potentialSQL = toString.substring(start, end).trim();
                if (potentialSQL.toLowerCase().matches(".*\\b(select|insert|update|delete|create|drop|alter)\\b.*")) {
                    System.out.println("✅ toString에서 SQL 발견: " + potentialSQL.substring(0, Math.min(60, potentialSQL.length())));
                    return potentialSQL;
                }
            }
            
            if (toString.contains("sql=")) {
                int start = toString.indexOf("sql=") + 4;
                int end = toString.indexOf(",", start);
                if (end == -1) end = toString.indexOf("}", start);
                if (end == -1) end = toString.length();
                
                String sql = toString.substring(start, end).trim();
                if (sql.startsWith("\"") && sql.endsWith("\"")) {
                    sql = sql.substring(1, sql.length() - 1);
                }
                if (!sql.isEmpty() && !sql.equals("null")) {
                    return sql;
                }
            }
            
            // 2. Reflection을 통한 SQL 추출 (안전하게)
            try {
                return extractSqlViaReflection(target);
            } catch (Exception reflectionError) {
                System.out.println("🔍 Reflection 실패, 대체 방법 시도: " + reflectionError.getMessage());
            }
            
            // 3. 최후 수단: toString에서 SQL 패턴 매칭
            if (toString.toLowerCase().matches(".*\\b(select|insert|update|delete|create|drop|alter)\\b.*")) {
                return "SQL found in toString: " + toString.substring(0, Math.min(100, toString.length()));
            }
            
            return null;
            
        } catch (Exception e) {
            System.out.println("⚠️ HikariCP SQL 추출 실패: " + e.getMessage());
            return null;
        }
    }
    
    /**
     * Reflection을 통한 SQL 추출
     */
    private static String extractSqlViaReflection(Object target) {
        try {
            Class<?> targetClass = target.getClass();
            
            // HikariCP 프록시에서 실제 PreparedStatement 객체 얻기
            java.lang.reflect.Field delegateField = findFieldByType(targetClass, java.sql.PreparedStatement.class);
            if (delegateField != null) {
                delegateField.setAccessible(true);
                Object delegate = delegateField.get(target);
                
                if (delegate != null) {
                    // PostgreSQL PreparedStatement에서 SQL 추출
                    java.lang.reflect.Field sqlField = findFieldContaining(delegate.getClass(), "sql", "query", "preparedSql");
                    if (sqlField != null) {
                        sqlField.setAccessible(true);
                        Object sqlValue = sqlField.get(delegate);
                        if (sqlValue instanceof String) {
                            return (String) sqlValue;
                        }
                    }
                }
            }
            
            // 직접 SQL 필드 찾기 시도
            java.lang.reflect.Field sqlField = findFieldContaining(targetClass, "sql", "query", "preparedSql");
            if (sqlField != null) {
                sqlField.setAccessible(true);
                Object sqlValue = sqlField.get(target);
                if (sqlValue instanceof String) {
                    return (String) sqlValue;
                }
            }
            
        } catch (Exception e) {
            System.out.println("⚠️ Reflection SQL 추출 실패: " + e.getMessage());
        }
        
        return null;
    }
    
    /**
     * 특정 타입의 필드 찾기
     */
    private static java.lang.reflect.Field findFieldByType(Class<?> clazz, Class<?> fieldType) {
        Class<?> currentClass = clazz;
        while (currentClass != null && currentClass != Object.class) {
            for (java.lang.reflect.Field field : currentClass.getDeclaredFields()) {
                if (fieldType.isAssignableFrom(field.getType())) {
                    return field;
                }
            }
            currentClass = currentClass.getSuperclass();
        }
        return null;
    }
    
    /**
     * 특정 이름을 포함하는 필드 찾기
     */
    private static java.lang.reflect.Field findFieldContaining(Class<?> clazz, String... keywords) {
        Class<?> currentClass = clazz;
        while (currentClass != null && currentClass != Object.class) {
            for (java.lang.reflect.Field field : currentClass.getDeclaredFields()) {
                String fieldName = field.getName().toLowerCase();
                for (String keyword : keywords) {
                    if (fieldName.contains(keyword.toLowerCase())) {
                        return field;
                    }
                }
            }
            currentClass = currentClass.getSuperclass();
        }
        return null;
    }
    
    /**
     * PostgreSQL 시스템 쿼리 필터링 (Long-running transaction 추적용)
     */
    private static boolean isSystemQuerySQL(String sql) {
        if (sql == null) return false;
        
        String upperSQL = sql.trim().toUpperCase();
        
        // PostgreSQL 시스템 쿼리 패턴들 (Long-running transaction 추적에서 제외)
        return upperSQL.startsWith("SELECT VERSION()") ||
               upperSQL.contains("INFORMATION_SCHEMA") ||
               upperSQL.contains("PG_CATALOG") ||
               upperSQL.contains("SYSTEM_CATALOG") ||
               upperSQL.startsWith("SHOW ") ||
               upperSQL.startsWith("DESCRIBE ") ||
               upperSQL.startsWith("EXPLAIN ") ||
               upperSQL.contains("METADATA") ||
               (upperSQL.contains("SELECT") && upperSQL.contains("DUAL")) ||
               upperSQL.equals("SELECT 1") ||
               upperSQL.equals("SELECT NOW()");
    }

    /**
     * PostgreSQL 및 기타 DB의 시스템 메서드 필터링 (강화된 버전)
     * 이런 메서드들은 Agent가 가로채면 안됨 (연결 설정, 메타데이터 조회 등)
     * HikariCP 연결 풀 초기화 시 "No results were returned by the query" 오류 방지
     */
    private static boolean isSystemMethod(String methodName) {
        // 🚨 1. PostgreSQL 핵심 시스템 메서드들 (HikariCP 호환성 필수)
        if (methodName.equals("getTransactionIsolation") ||
            methodName.equals("setTransactionIsolation") ||
            methodName.equals("getAutoCommit") ||
            methodName.equals("setAutoCommit") ||
            methodName.equals("isReadOnly") ||
            methodName.equals("setReadOnly") ||
            methodName.equals("getCatalog") ||
            methodName.equals("setCatalog") ||
            methodName.equals("getSchema") ||
            methodName.equals("setSchema") ||
            methodName.equals("getMetaData") ||
            methodName.equals("getDatabaseMetaData") ||
            methodName.equals("getClientInfo") ||
            methodName.equals("setClientInfo") ||
            methodName.equals("isValid") ||
            methodName.equals("getNetworkTimeout") ||
            methodName.equals("setNetworkTimeout") ||
            methodName.equals("getHoldability") ||
            methodName.equals("setHoldability") ||
            methodName.equals("getWarnings") ||
            methodName.equals("clearWarnings") ||
            methodName.equals("getTypeMap") ||
            methodName.equals("setTypeMap")) {
            return true;
        }
        
        // 🚨 2. HikariCP 연결 검증 및 초기화 메서드들 (중요!)
        if (methodName.equals("testConnection") ||
            methodName.equals("validateConnection") ||
            methodName.equals("ping") ||
            methodName.equals("checkConnection") ||
            methodName.equals("isConnectionAlive") ||
            methodName.equals("validate") ||
            methodName.equals("init") ||
            methodName.equals("initialize") ||
            methodName.equals("reset") ||
            methodName.equals("resetConnection")) {
            return true;
        }
        
        // 🚨 3. PostgreSQL JDBC 드라이버 초기화 메서드들
        if (methodName.equals("getURL") ||
            methodName.equals("acceptsURL") ||
            methodName.equals("getPropertyInfo") ||
            methodName.equals("getDriverVersion") ||
            methodName.equals("getDriverName") ||
            methodName.equals("getMajorVersion") ||
            methodName.equals("getMinorVersion") ||
            methodName.equals("jdbcCompliant") ||
            methodName.equals("getParentLogger")) {
            return true;
        }
        
        // 4. JDBC 표준 메타데이터 및 Object 메서드들
        if (methodName.equals("isClosed") ||
            methodName.equals("toString") ||
            methodName.equals("hashCode") ||
            methodName.equals("equals") ||
            methodName.equals("getClass") ||
            methodName.equals("notify") ||
            methodName.equals("notifyAll") ||
            methodName.equals("wait") ||
            methodName.equals("finalize")) {
            return true;
        }
        
        // 🚨 5. Connection 상태 확인 메서드들 (HikariCP가 자주 사용)
        if (methodName.equals("isClosed") ||
            methodName.equals("isValid") ||
            methodName.equals("isWrapperFor") ||
            methodName.equals("unwrap") ||
            methodName.equals("abort") ||
            methodName.equals("getConnectionId") ||
            methodName.equals("getConnectionInfo") ||
            methodName.equals("getServerInfo")) {
            return true;
        }
        
        // 6. PreparedStatement/ResultSet 메타데이터 메서드들 (PostgreSQL 호환성 중요)
        if (methodName.equals("getMetaData") ||
            methodName.equals("getParameterMetaData") ||
            methodName.equals("getResultSetMetaData") ||
            methodName.equals("getResultSetType") ||
            methodName.equals("getResultSetConcurrency") ||
            methodName.equals("getResultSetHoldability") ||
            methodName.equals("getFetchDirection") ||
            methodName.equals("setFetchDirection") ||
            methodName.equals("getFetchSize") ||
            methodName.equals("setFetchSize") ||
            methodName.equals("getMaxRows") ||
            methodName.equals("setMaxRows") ||
            methodName.equals("getMaxFieldSize") ||
            methodName.equals("setMaxFieldSize") ||
            methodName.equals("getQueryTimeout") ||
            methodName.equals("setQueryTimeout")) {
            return true;
        }
        
        // 🚨 7. PostgreSQL 내부 시스템 쿼리 관련 (절대 인터셉트 금지)
        if (methodName.startsWith("pg") || // PostgreSQL 전용 메서드들 (pgXXX)
            methodName.startsWith("postgres") || // PostgreSQL 전용
            methodName.contains("Version") || // 버전 관련
            methodName.contains("Catalog") || // 카탈로그 관련
            methodName.contains("Information") || // 정보 스키마 관련
            methodName.contains("Metadata") || // 메타데이터 관련
            methodName.contains("SystemTable")) { // 시스템 테이블 관련
            return true;
        }
        
        // 8. HikariCP 내부 메서드들 (더 정확한 필터링)
        if (methodName.startsWith("get") && (
               methodName.contains("Pool") ||
               methodName.contains("Hikari") ||
               methodName.contains("Config") ||
               (methodName.contains("Connection") && !methodName.equals("getConnection")))) {
            return true;
        }
        
        // 🚨 9. 데이터 타입 및 변환 관련 메서드들 (PostgreSQL 특수 타입 처리)
        if (methodName.contains("Binary") || // Binary 관련
            methodName.contains("Array") ||  // Array 타입 관련
            methodName.contains("Blob") ||   // Blob 관련
            methodName.contains("Clob") ||   // Clob 관련
            methodName.contains("JSON") ||   // JSON 타입 관련
            methodName.contains("UUID") ||   // UUID 타입 관련
            methodName.contains("Geometry") || // 공간 데이터 타입
            methodName.contains("Timestamp")) { // 타임스탬프 관련
            return true;
        }
        
        // 6. Hibernate/JPA ORM 관련 시스템 메서드들
        if (methodName.startsWith("hibernate") ||
            methodName.startsWith("org_hibernate") ||
            methodName.contains("LazyInit") ||
            methodName.contains("Proxy") ||
            methodName.contains("Session")) {
            return true;
        }
        
        return false;
    }
    
    /**
     * 데이터베이스 초기화 준비 상태 확인
     * HikariCP와 PostgreSQL 초기화가 완료될 때까지 대기
     */
    private static boolean isDatabaseInitializationReady() {
        // 이미 초기화가 완료되었다면 true 반환
        if (databaseInitializationComplete) {
            return true;
        }
        
        long currentTime = System.currentTimeMillis();
        long elapsedTime = currentTime - agentStartTime;
        
        // 30초가 지났으면 초기화가 완료된 것으로 간주
        if (elapsedTime >= INITIALIZATION_WAIT_TIME_MS) {
            System.out.println("🚀 [KubeDB] 데이터베이스 초기화 대기 시간 완료 - Agent 인터셉션 활성화");
            databaseInitializationComplete = true;
            return true;
        }
        
        // Spring Boot ApplicationContext 확인을 통한 초기화 완료 감지
        try {
            // Spring Boot가 완전히 시작되었는지 확인
            if (isSpringBootFullyStarted()) {
                System.out.println("🚀 [KubeDB] Spring Boot 초기화 완료 감지 - Agent 인터셉션 활성화 (경과시간: " + 
                                 (elapsedTime / 1000) + "초)");
                databaseInitializationComplete = true;
                return true;
            }
        } catch (Exception e) {
            // Spring Boot 확인 실패는 정상적일 수 있음
        }
        
        // 아직 초기화가 완료되지 않음 - 인터셉션 대기
        if (elapsedTime % 10000 == 0) { // 10초마다 한 번씩 로그 출력 (너무 자주 출력 방지)
            System.out.println("⏳ [KubeDB] 데이터베이스 초기화 대기 중... (경과: " + 
                             (elapsedTime / 1000) + "초/" + (INITIALIZATION_WAIT_TIME_MS / 1000) + "초)");
        }
        return false;
    }
    
    /**
     * Spring Boot 완전 시작 상태 확인
     */
    private static boolean isSpringBootFullyStarted() {
        try {
            // ApplicationContext 클래스가 로드되어 있는지 확인
            Class<?> applicationContextHolderClass = Class.forName("org.springframework.context.ApplicationContextHolder");
            Object applicationContext = applicationContextHolderClass.getMethod("getApplicationContext").invoke(null);
            
            if (applicationContext != null) {
                // ApplicationContext가 존재하면 DataSource Bean 확인
                try {
                    Object dataSourceBean = applicationContext.getClass()
                        .getMethod("getBean", Class.class)
                        .invoke(applicationContext, javax.sql.DataSource.class);
                    
                    if (dataSourceBean != null) {
                        // DataSource Bean이 존재하면 HikariCP 상태 확인
                        return isHikariCPReady(dataSourceBean);
                    }
                } catch (Exception e) {
                    // DataSource Bean이 없을 수 있음
                }
            }
        } catch (Exception e) {
            // Spring Boot이 아닐 수도 있음
        }
        
        return false;
    }
    
    /**
     * HikariCP 준비 상태 확인
     */
    private static boolean isHikariCPReady(Object dataSource) {
        try {
            if (dataSource.getClass().getName().contains("HikariDataSource")) {
                // HikariDataSource의 isRunning() 또는 isClosed() 메서드 확인
                try {
                    Boolean isClosed = (Boolean) dataSource.getClass().getMethod("isClosed").invoke(dataSource);
                    return !isClosed;
                } catch (Exception e) {
                    // isClosed 메서드가 없을 수 있음 - 기본적으로 준비됨으로 간주
                    return true;
                }
            }
        } catch (Exception e) {
            // HikariDataSource가 아닐 수 있음
        }
        
        return true; // 다른 DataSource의 경우 준비됨으로 간주
    }
}