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
import java.util.Set;
import java.util.HashSet;
import java.util.Collections;
import java.util.concurrent.locks.ReentrantReadWriteLock;

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
    
    // Deadlock 감지 임계값 (10초)
    private static final long DEADLOCK_DETECTION_THRESHOLD_MS = 10000;
    
    // TPS 계산용
    private static final AtomicLong totalQueries = new AtomicLong(0);
    private static final Map<String, Instant> queryTimestamps = new ConcurrentHashMap<>();
    
    // Deadlock 감지용 - Connection lock tracking
    private static final Map<String, Set<String>> connectionLocks = new ConcurrentHashMap<>();
    private static final Map<String, Instant> lockAcquisitionTimes = new ConcurrentHashMap<>();
    private static final Map<String, String> connectionTransactions = new ConcurrentHashMap<>();
    private static final ReentrantReadWriteLock deadlockDetectionLock = new ReentrantReadWriteLock();
    
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
                
                // Deadlock 감지
                trackDeadlockDetection(connection, connectionId, actualSql, executionTimeMs);
                
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
     * TPS 메트릭 추적 (개선된 버전 - 실시간 TPS 업데이트)
     */
    private static void trackTpsMetrics(String sql, long executionTime) {
        try {
            long currentQueryCount = totalQueries.incrementAndGet();
            String currentSecond = String.valueOf(System.currentTimeMillis() / 1000);
            queryTimestamps.put(currentSecond, Instant.now());
            
            // 더 자주 TPS 계산 및 전송 (5개 쿼리마다)
            if (currentQueryCount % 5 == 0) {
                long currentTime = System.currentTimeMillis();
                long queriesInLastSecond = queryTimestamps.entrySet().stream()
                    .filter(entry -> currentTime - entry.getValue().toEpochMilli() <= 1000)
                    .count();
                
                // 일반 TPS 메트릭 항상 전송 (대시보드 실시간 업데이트용)
                try {
                    DBMetrics tpsMetric = DBMetrics.builder()
                        .sql("TPS_UPDATE")
                        .executionTimeMs(queriesInLastSecond)
                        .databaseType("TPS_METRIC")
                        .connectionUrl("tps://" + String.format("%.2f", (double) queriesInLastSecond))
                        .build();
                    getCollector().collect(tpsMetric);
                    logger.debug("📈 Production-Safe: TPS UPDATE: {} TPS (total queries: {})", 
                               queriesInLastSecond, currentQueryCount);
                } catch (Exception e) {
                    logger.debug("TPS metric collection failed: {}", e.getMessage());
                }
                
                // High TPS 이벤트 (임계값을 10으로 낮춤)
                if (queriesInLastSecond >= 10) {
                    try {
                        DBMetrics highTpsMetric = DBMetrics.builder()
                            .sql("HIGH_TPS_EVENT")
                            .executionTimeMs(queriesInLastSecond)
                            .databaseType("TPS_ALERT")
                            .connectionUrl("high-tps://" + String.format("%.2f", (double) queriesInLastSecond))
                            .build();
                        getCollector().collect(highTpsMetric);
                        logger.info("🚀 Production-Safe: HIGH TPS EVENT: {} TPS detected (total: {})", 
                                   queriesInLastSecond, currentQueryCount);
                    } catch (Exception e) {
                        logger.warn("High TPS metric collection failed: {}", e.getMessage());
                    }
                }
            }
            
            // 오래된 타임스탬프 정리 (메모리 누수 방지)
            if (currentQueryCount % 100 == 0) {
                long cutoffTime = System.currentTimeMillis() - 5000; // 5초 이전 데이터 제거
                queryTimestamps.entrySet().removeIf(entry -> entry.getValue().toEpochMilli() < cutoffTime);
            }
            
        } catch (Exception e) {
            logger.debug("TPS tracking failed: {}", e.getMessage());
        }
    }
    
    /**
     * Deadlock 감지 추적 (Production-Safe) - 실제 트랜잭션 상태 반영
     */
    private static void trackDeadlockDetection(Connection connection, String connectionId, String sql, long executionTimeMs) {
        try {
            if (connection == null || connectionId == null) {
                return;
            }
            
            // Production-Safe: Connection 상태 확인 (실제 SQL 실행에 방해 없음)
            boolean isInTransaction = false;
            boolean isLongRunning = executionTimeMs >= LONG_RUNNING_THRESHOLD_MS;
            
            try {
                // autoCommit이 false이면 트랜잭션 진행 중
                isInTransaction = !connection.getAutoCommit();
                
                // 추가 트랜잭션 상태 확인
                if (isInTransaction) {
                    // Connection이 closed되지 않았는지 확인
                    if (connection.isClosed()) {
                        cleanupConnectionLocks(connectionId);
                        return;
                    }
                    
                    // READ_UNCOMMITTED나 기타 격리 수준에서의 트랜잭션 감지
                    int isolationLevel = connection.getTransactionIsolation();
                    logger.debug("Production-Safe: Connection {} transaction isolation level: {}", 
                               connectionId, isolationLevel);
                }
                
            } catch (SQLException e) {
                logger.debug("Production-Safe: Failed to check connection status: {}", e.getMessage());
                // Connection 상태를 확인할 수 없으면 정리
                cleanupConnectionLocks(connectionId);
                return;
            }
            
            if (!isInTransaction) {
                // 트랜잭션이 아니거나 autoCommit=true인 경우 lock tracking에서 제거
                cleanupConnectionLocks(connectionId);
                logger.debug("Production-Safe: No active transaction for connection {}, cleaned up locks", connectionId);
                return;
            }
            
            // 트랜잭션 진행 중이고 실제 실행 시간이 있는 경우에만 추적
            if (isInTransaction && executionTimeMs > 0) {
                updateConnectionLockTracking(connectionId, sql, executionTimeMs);
                
                // Long Running Transaction이면서 트랜잭션인 경우 Deadlock 가능성 증가
                if (isLongRunning) {
                    logger.debug("Production-Safe: Long running transaction detected in connection {}: {}ms", 
                               connectionId, executionTimeMs);
                }
                
                // Deadlock 감지 실행 (임계값: 5초로 낮춰서 더 민감하게 감지)
                if (executionTimeMs >= 5000) {
                    detectPotentialDeadlock(connectionId, sql, executionTimeMs);
                }
            }
            
        } catch (Exception e) {
            logger.debug("Production-Safe: Deadlock tracking failed: {}", e.getMessage());
        }
    }
    
    /**
     * Connection lock tracking 업데이트
     */
    private static void updateConnectionLockTracking(String connectionId, String sql, long executionTimeMs) {
        try {
            deadlockDetectionLock.writeLock().lock();
            
            // 현재 connection의 locks 추적
            Set<String> locks = connectionLocks.computeIfAbsent(connectionId, k -> Collections.synchronizedSet(new HashSet<>()));
            
            // SQL에서 테이블/리소스 추출 (간단한 패턴 매칭)
            String resourceId = extractResourceFromSql(sql);
            if (resourceId != null) {
                locks.add(resourceId);
                lockAcquisitionTimes.put(connectionId + ":" + resourceId, Instant.now());
            }
            
            // 현재 트랜잭션 정보 저장
            connectionTransactions.put(connectionId, sql);
            
            logger.debug("📊 Production-Safe: Updated lock tracking for connection {}, resource: {}, execution time: {}ms", 
                        connectionId, resourceId, executionTimeMs);
            
        } finally {
            deadlockDetectionLock.writeLock().unlock();
        }
    }
    
    /**
     * 잠재적 Deadlock 감지
     */
    private static void detectPotentialDeadlock(String connectionId, String sql, long executionTimeMs) {
        try {
            deadlockDetectionLock.readLock().lock();
            
            // 현재 connection이 대기 중인 리소스들 확인
            Set<String> currentLocks = connectionLocks.get(connectionId);
            if (currentLocks == null || currentLocks.isEmpty()) {
                return;
            }
            
            // 다른 connection들이 보유한 locks 확인하여 circular dependency 검사
            for (Map.Entry<String, Set<String>> entry : connectionLocks.entrySet()) {
                String otherConnectionId = entry.getKey();
                Set<String> otherLocks = entry.getValue();
                
                if (!otherConnectionId.equals(connectionId) && otherLocks != null) {
                    // Deadlock 패턴 체크: A가 B의 리소스를 기다리고, B가 A의 리소스를 기다리는 상황
                    if (hasLockConflict(currentLocks, otherLocks)) {
                        logger.warn("💀 Production-Safe: POTENTIAL DEADLOCK DETECTED! Connection {} ({}ms) conflicts with connection {}", 
                                   connectionId, executionTimeMs, otherConnectionId);
                        
                        // Dashboard로 Deadlock 이벤트 전송
                        sendDeadlockEvent(connectionId, otherConnectionId, sql, executionTimeMs);
                        break; // 첫 번째 감지된 deadlock만 보고
                    }
                }
            }
            
        } finally {
            deadlockDetectionLock.readLock().unlock();
        }
    }
    
    /**
     * Lock 충돌 검사
     */
    private static boolean hasLockConflict(Set<String> locks1, Set<String> locks2) {
        if (locks1 == null || locks2 == null) {
            return false;
        }
        
        // 두 set이 공통 리소스를 가지고 있는지 확인 (간단한 버전)
        for (String resource : locks1) {
            if (locks2.contains(resource)) {
                return true;
            }
        }
        return false;
    }
    
    /**
     * SQL에서 리소스(테이블명) 추출
     */
    private static String extractResourceFromSql(String sql) {
        if (sql == null || sql.isEmpty()) {
            return null;
        }
        
        try {
            // 간단한 패턴 매칭으로 테이블명 추출
            String upperSql = sql.toUpperCase();
            
            // SELECT, UPDATE, DELETE, INSERT에서 테이블명 추출
            if (upperSql.contains("FROM ")) {
                int fromIndex = upperSql.indexOf("FROM ") + 5;
                int endIndex = upperSql.indexOf(" ", fromIndex);
                if (endIndex == -1) endIndex = sql.length();
                return sql.substring(fromIndex, endIndex).trim();
            } else if (upperSql.contains("UPDATE ")) {
                int updateIndex = upperSql.indexOf("UPDATE ") + 7;
                int endIndex = upperSql.indexOf(" ", updateIndex);
                if (endIndex == -1) endIndex = sql.length();
                return sql.substring(updateIndex, endIndex).trim();
            } else if (upperSql.contains("INSERT INTO ")) {
                int insertIndex = upperSql.indexOf("INSERT INTO ") + 12;
                int endIndex = upperSql.indexOf(" ", insertIndex);
                if (endIndex == -1) endIndex = sql.length();
                return sql.substring(insertIndex, endIndex).trim();
            }
            
            // 기본값: SQL 해시
            return "resource_" + Math.abs(sql.hashCode() % 1000);
        } catch (Exception e) {
            return "unknown_resource";
        }
    }
    
    /**
     * Connection locks 정리
     */
    private static void cleanupConnectionLocks(String connectionId) {
        try {
            deadlockDetectionLock.writeLock().lock();
            
            connectionLocks.remove(connectionId);
            connectionTransactions.remove(connectionId);
            
            // lockAcquisitionTimes에서 해당 connection 관련 항목들 제거
            lockAcquisitionTimes.entrySet().removeIf(entry -> entry.getKey().startsWith(connectionId + ":"));
            
            logger.debug("📊 Production-Safe: Cleaned up locks for connection: {}", connectionId);
        } finally {
            deadlockDetectionLock.writeLock().unlock();
        }
    }
    
    /**
     * Deadlock 이벤트를 Dashboard로 전송
     */
    private static void sendDeadlockEvent(String connectionId1, String connectionId2, String sql, long executionTimeMs) {
        try {
            DBMetrics deadlockMetric = DBMetrics.builder()
                .sql("DEADLOCK_DETECTED")
                .executionTimeMs(executionTimeMs)
                .databaseType("DEADLOCK_METRIC")
                .connectionUrl("deadlock://" + connectionId1 + ":" + connectionId2)
                .build();
                
            getCollector().collect(deadlockMetric);
            logger.warn("💀 Production-Safe: DEADLOCK event sent to dashboard - Connection {} conflicts with {}, Duration: {}ms", 
                       connectionId1, connectionId2, executionTimeMs);
            
        } catch (Exception e) {
            logger.warn("❌ Production-Safe: Failed to send Deadlock event: {}", e.getMessage());
        }
    }
}