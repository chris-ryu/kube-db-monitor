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

/**
 * ByteBuddy 기반 범용 JDBC 인터셉터
 * 모든 데이터베이스(PostgreSQL, Oracle, MySQL, SQL Server 등)를 지원하는 통합 인터셉터
 */
public class UniversalJDBCInterceptor {
    private static final Logger logger = LoggerFactory.getLogger(UniversalJDBCInterceptor.class);
    
    private static volatile MetricsCollector metricsCollector;
    
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
        
        // MetricsCollector 지연 초기화
        if (metricsCollector == null) {
            synchronized (UniversalJDBCInterceptor.class) {
                if (metricsCollector == null) {
                    AgentConfig config = KubeDBAgent.getConfig();
                    if (config != null) {
                        metricsCollector = new MetricsCollector(config);
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
            
            // DB별 특화 처리
            sql = preprocessSQL(sql, dbType);
            
            // 메트릭 수집
            if (success) {
                metricsCollector.recordQuery(sql, executionTime, connectionId, threadName);
            } else {
                // 오류 처리는 MetricsCollector 메서드 시그니처 확인 필요
                logger.warn("SQL execution failed: {}, Error: {}", sql, error.getMessage());
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
                
                // SQL 준비 메트릭
                metricsCollector.recordQuery("PREPARE: " + sql, executionTime, connectionId, threadName);
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
            
            // PreparedStatement의 경우 미리 준비된 SQL이 있을 수 있음
            if (target instanceof PreparedStatement) {
                // PreparedStatement에서 SQL을 추출하는 것은 구현체마다 다름
                return "PreparedStatement SQL (구현체별 추출 필요)";
            }
            
            return "Unknown SQL";
            
        } catch (Exception e) {
            return "SQL extraction error: " + e.getMessage();
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
}