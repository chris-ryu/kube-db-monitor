package io.kubedb.monitor.agent;

import io.kubedb.monitor.common.metrics.DBMetrics;
import io.kubedb.monitor.common.metrics.MetricsCollector;
import io.kubedb.monitor.common.metrics.MetricsCollectorFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.ConcurrentHashMap;
import java.time.Instant;
import java.util.Map;

/**
 * Production-Safe JDBC Interceptor
 * 
 * 핵심 원칙:
 * 1. 실제 SQL 실행은 전혀 방해하지 않음
 * 2. 오직 메트릭 수집만 수행
 * 3. 무한 재귀 완전 방지
 * 4. 실제 SQL 결과에 영향 없음
 */
public class ProductionSafeJDBCInterceptor {
    private static final Logger logger = LoggerFactory.getLogger(ProductionSafeJDBCInterceptor.class);
    private static volatile MetricsCollector collector;
    
    // Long Running Transaction 임계값 (5초)
    private static final long LONG_RUNNING_THRESHOLD_MS = 5000;
    
    // TPS 계산용
    private static final AtomicLong totalQueries = new AtomicLong(0);
    private static final Map<String, Instant> queryTimestamps = new ConcurrentHashMap<>();
    
    // ThreadLocal을 사용해서 재귀 방지
    private static final ThreadLocal<Boolean> isInterceptorActive = new ThreadLocal<Boolean>() {
        @Override
        protected Boolean initialValue() {
            return Boolean.FALSE;
        }
    };
    
    /**
     * 메트릭 수집기 초기화 (lazy initialization)
     */
    private static MetricsCollector getCollector() {
        if (collector == null) {
            synchronized (ProductionSafeJDBCInterceptor.class) {
                if (collector == null) {
                    collector = MetricsCollectorFactory.createFromSystemConfig();
                    logger.info("Production-Safe MetricsCollector initialized: {}", collector.getClass().getSimpleName());
                }
            }
        }
        return collector;
    }
    
    /**
     * PRODUCTION-SAFE 메트릭 수집
     * 
     * 이 메서드는:
     * - 실제 SQL 실행을 방해하지 않음
     * - 순수하게 모니터링만 수행
     * - 무한 재귀 방지
     * - 예외 발생 시에도 원본 SQL 실행에 영향 없음
     */
    public static void collectMetricsOnly(Object statement, String sql, String connectionUrl, String databaseType, long executionTimeMs) {
        // 재귀 방지: 이미 인터셉터가 활성 상태라면 무시
        if (isInterceptorActive.get()) {
            return;
        }
        
        try {
            isInterceptorActive.set(true);
            
            // SQL 추출
            String actualSql = extractSqlSafely(statement, sql);
            if (actualSql == null || actualSql.isEmpty()) {
                actualSql = "UNKNOWN_SQL";
            }
            
            logger.debug("📊 Production-Safe: Collecting metrics for SQL: {}", actualSql);
            
            // Connection 추출 (안전하게)
            Connection connection = extractConnectionSafely(statement);
            String connectionId = getConnectionIdSafely(connection);
            
            // Long Running Transaction 체크
            if (executionTimeMs >= LONG_RUNNING_THRESHOLD_MS) {
                logger.warn("🐌 Production-Safe: LONG RUNNING TRANSACTION DETECTED! Duration: {}ms, SQL: {}", executionTimeMs, actualSql);
                
                // 대시보드로 Long Running Transaction 이벤트 전송
                try {
                    DBMetrics longRunningMetric = DBMetrics.builder()
                        .sql("LONG_RUNNING_TRANSACTION")
                        .executionTimeMs(executionTimeMs)
                        .databaseType("TRANSACTION_METRIC")
                        .connectionUrl("long-tx://" + connectionId)
                        .build();
                    getCollector().collect(longRunningMetric);
                    logger.info("✅ Production-Safe: LONG_RUNNING_TRANSACTION event sent to dashboard");
                } catch (Exception e) {
                    logger.warn("❌ Production-Safe: Failed to send Long Running Transaction event: {}", e.getMessage());
                }
            }
            
            // 기본 쿼리 메트릭 수집
            try {
                DBMetrics metric = DBMetrics.builder()
                    .sql(maskSqlParameters(actualSql))
                    .executionTimeMs(executionTimeMs)
                    .databaseType(databaseType)
                    .connectionUrl(connectionUrl)
                    .build();
                getCollector().collect(metric);
                
                // TPS 메트릭 추적
                trackTpsMetrics(actualSql, executionTimeMs);
                
            } catch (Exception e) {
                logger.warn("❌ Production-Safe: Failed to collect basic metrics: {}", e.getMessage());
            }
            
        } catch (Exception e) {
            // 어떤 예외가 발생해도 원본 SQL 실행에는 영향 없음
            logger.warn("❌ Production-Safe: Metric collection failed, but original SQL execution continues: {}", e.getMessage());
        } finally {
            isInterceptorActive.set(false);
        }
    }
    
    /**
     * SQL 추출 (안전하게)
     */
    private static String extractSqlSafely(Object statement, String sql) {
        try {
            if (sql != null && !sql.isEmpty() && 
                !"INTERCEPTED_SQL_QUERY".equals(sql) && 
                !"SQL_PLACEHOLDER".equals(sql) &&
                !"ASM_INTERCEPTED_SQL".equals(sql)) {
                return sql;
            }
            
            // statement 객체에서 SQL 추출 시도 (reflection 사용)
            if (statement != null) {
                try {
                    java.lang.reflect.Field sqlField = null;
                    Class<?> currentClass = statement.getClass();
                    
                    while (currentClass != null && sqlField == null) {
                        try {
                            sqlField = currentClass.getDeclaredField("sql");
                            break;
                        } catch (NoSuchFieldException e) {
                            // 다른 필드명들도 시도
                            String[] fieldNames = {"query", "sqlString", "originalSql", "preparedSql"};
                            for (String fieldName : fieldNames) {
                                try {
                                    sqlField = currentClass.getDeclaredField(fieldName);
                                    break;
                                } catch (NoSuchFieldException ignored) {}
                            }
                        }
                        currentClass = currentClass.getSuperclass();
                    }
                    
                    if (sqlField != null) {
                        sqlField.setAccessible(true);
                        Object sqlValue = sqlField.get(statement);
                        if (sqlValue instanceof String) {
                            return (String) sqlValue;
                        }
                    }
                } catch (Exception e) {
                    logger.debug("SQL extraction from statement failed: {}", e.getMessage());
                }
            }
            
            return "EXTRACTED_SQL_FAILED";
        } catch (Exception e) {
            return "SQL_EXTRACTION_ERROR";
        }
    }
    
    /**
     * Connection 추출 (안전하게)
     */
    private static Connection extractConnectionSafely(Object statement) {
        try {
            if (statement == null) {
                return null;
            }
            
            // getConnection 메서드 시도
            try {
                java.lang.reflect.Method getConnectionMethod = statement.getClass().getMethod("getConnection");
                Object connectionObj = getConnectionMethod.invoke(statement);
                if (connectionObj instanceof Connection) {
                    return (Connection) connectionObj;
                }
            } catch (Exception e) {
                logger.debug("getConnection() method failed: {}", e.getMessage());
            }
            
            return null;
        } catch (Exception e) {
            logger.debug("Connection extraction failed: {}", e.getMessage());
            return null;
        }
    }
    
    /**
     * Connection ID 생성 (안전하게)
     */
    private static String getConnectionIdSafely(Connection connection) {
        try {
            if (connection == null) {
                return "unknown-connection";
            }
            return connection.getClass().getSimpleName() + "@" + Integer.toHexString(connection.hashCode());
        } catch (Exception e) {
            return "connection-id-error-" + System.currentTimeMillis();
        }
    }
    
    /**
     * SQL 파라미터 마스킹
     */
    private static String maskSqlParameters(String sql) {
        if (sql == null) {
            return null;
        }
        
        try {
            // 간단한 파라미터 마스킹
            return sql.replaceAll("'[^']*'", "?")
                      .replaceAll("\\b\\d+\\b", "?");
        } catch (Exception e) {
            return sql;
        }
    }
    
    /**
     * TPS 메트릭 추적
     */
    private static void trackTpsMetrics(String sql, long executionTime) {
        try {
            totalQueries.incrementAndGet();
            String currentSecond = String.valueOf(System.currentTimeMillis() / 1000);
            queryTimestamps.put(currentSecond, Instant.now());
            
            // TPS 계산 및 전송 (1초마다)
            if (totalQueries.get() % 10 == 0) { // 10개 쿼리마다 TPS 체크
                long currentTime = System.currentTimeMillis();
                long queriesInLastSecond = queryTimestamps.entrySet().stream()
                    .filter(entry -> currentTime - entry.getValue().toEpochMilli() <= 1000)
                    .count();
                
                if (queriesInLastSecond > 50) { // High TPS threshold
                    try {
                        DBMetrics tpsMetric = DBMetrics.builder()
                            .sql("TPS_EVENT")
                            .executionTimeMs(queriesInLastSecond)
                            .databaseType("TPS_METRIC")
                            .connectionUrl("tps://" + String.format("%.2f", (double) queriesInLastSecond))
                            .build();
                        getCollector().collect(tpsMetric);
                        logger.info("🚀 Production-Safe: HIGH TPS EVENT: {} TPS detected", queriesInLastSecond);
                    } catch (Exception e) {
                        logger.warn("TPS metric collection failed: {}", e.getMessage());
                    }
                }
            }
        } catch (Exception e) {
            logger.debug("TPS tracking failed: {}", e.getMessage());
        }
    }
}