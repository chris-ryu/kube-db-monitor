package io.kubedb.monitor.agent;

import io.kubedb.monitor.common.deadlock.DeadlockDetector;
import io.kubedb.monitor.common.metrics.DBMetrics;
import io.kubedb.monitor.common.metrics.MetricsCollector;
import io.kubedb.monitor.common.metrics.MetricsCollectorFactory;
import io.kubedb.monitor.common.transaction.TransactionRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.ConcurrentHashMap;
import java.time.Instant;
import java.util.Map;

/**
 * Runtime interceptor for JDBC method calls with transaction tracking
 * This class contains the actual interception logic that gets injected into JDBC classes
 */
public class JDBCMethodInterceptor {
    private static final Logger logger = LoggerFactory.getLogger(JDBCMethodInterceptor.class);
    private static volatile MetricsCollector collector;
    private static volatile TransactionAwareJDBCInterceptor transactionInterceptor;
    
    // TPS calculation - track query count per time window
    private static final AtomicLong totalQueries = new AtomicLong(0);
    private static final Map<String, Instant> queryTimestamps = new ConcurrentHashMap<>();
    private static final long TPS_WINDOW_MS = 1000; // 1 second window
    
    // Long running transaction threshold (configurable)  
    private static final long LONG_RUNNING_THRESHOLD_MS = 5000; // 5 seconds
    
    // Transaction tracking - connection ID -> transaction start time
    private static final Map<String, Instant> transactionStartTimes = new ConcurrentHashMap<>();
    
    /**
     * Get or create the metrics collector (lazy initialization)
     */
    private static MetricsCollector getCollector() {
        if (collector == null) {
            synchronized (JDBCMethodInterceptor.class) {
                if (collector == null) {
                    logger.debug("Initializing MetricsCollector from system config");
                    collector = MetricsCollectorFactory.createFromSystemConfig();
                    logger.info("MetricsCollector initialized: {}", collector.getClass().getSimpleName());
                }
            }
        }
        return collector;
    }
    
    /**
     * Get or create the transaction-aware interceptor (lazy initialization)
     */
    private static TransactionAwareJDBCInterceptor getTransactionInterceptor() {
        if (transactionInterceptor == null) {
            synchronized (JDBCMethodInterceptor.class) {
                if (transactionInterceptor == null) {
                    TransactionRegistry registry = new TransactionRegistry();
                    DeadlockDetector detector = new DeadlockDetector();
                    transactionInterceptor = new TransactionAwareJDBCInterceptor(registry, detector);
                    logger.info("TransactionAwareJDBCInterceptor initialized");
                }
            }
        }
        return transactionInterceptor;
    }
    
    /**
     * Intercepts Statement.execute() method calls
     */
    public static boolean executeStatement(Object statement, String sql, String connectionUrl, String databaseType) {
        long startTime = System.currentTimeMillis();
        boolean result = false;
        Connection connection = null;
        
        try {
            // Add debug output to System.out to ensure this method is called
            System.out.println("🔍 JDBCMethodInterceptor.executeStatement called: " + sql);
            System.out.println("🔧 MetricsCollector about to be initialized...");
            logger.debug("Intercepting statement execution: {}", sql);
            
            // Extract connection from statement if available
            connection = extractConnection(statement);
            
            // Call original method through reflection or direct call
            // In real implementation, this would be the original method call
            result = executeOriginalStatement(statement, sql);
            
            long executionTime = System.currentTimeMillis() - startTime;
            
            // Track TPS metrics
            trackTpsMetrics(sql, executionTime);
            
            // Track transaction-aware metrics if connection available
            if (connection != null) {
                getTransactionInterceptor().onQueryExecution(connection, sql, executionTime, true);
                checkForLongRunningTransaction(connection);
            }
            
            // Collect basic metrics
            DBMetrics metric = DBMetrics.builder()
                    .sql(maskSqlParameters(sql))
                    .executionTimeMs(executionTime)
                    .databaseType(databaseType)
                    .connectionUrl(connectionUrl)
                    .build();
            
            getCollector().collect(metric);
            
        } catch (SQLException e) {
            long executionTime = System.currentTimeMillis() - startTime;
            
            // Handle transaction-aware error tracking
            if (connection != null) {
                getTransactionInterceptor().onQueryError(connection, sql, e);
            }
            
            // Collect error metric
            DBMetrics metric = DBMetrics.builder()
                    .sql(maskSqlParameters(sql))
                    .executionTimeMs(executionTime)
                    .databaseType(databaseType)
                    .connectionUrl(connectionUrl)
                    .error(e.getMessage())
                    .build();
            
            getCollector().collect(metric);
            
            // Re-throw the exception
            throw new RuntimeException(e);
        }
        
        return result;
    }
    
    /**
     * Intercepts PreparedStatement.execute() method calls
     */
    public static boolean executePreparedStatement(Object preparedStatement, String sql, String connectionUrl, String databaseType) {
        long startTime = System.currentTimeMillis();
        boolean result = false;
        Connection connection = null;
        
        try {
            logger.debug("Intercepting prepared statement execution: {}", sql);
            
            // Extract connection from prepared statement if available
            connection = extractConnection(preparedStatement);
            
            // Call original method
            result = executeOriginalPreparedStatement(preparedStatement);
            
            long executionTime = System.currentTimeMillis() - startTime;
            
            // Track TPS metrics
            trackTpsMetrics(sql, executionTime);
            
            // Track transaction-aware metrics if connection available
            if (connection != null) {
                getTransactionInterceptor().onQueryExecution(connection, sql, executionTime, true);
                checkForLongRunningTransaction(connection);
            }
            
            // Collect basic metrics
            DBMetrics metric = DBMetrics.builder()
                    .sql(maskSqlParameters(sql))
                    .executionTimeMs(executionTime)
                    .databaseType(databaseType)
                    .connectionUrl(connectionUrl)
                    .build();
            
            getCollector().collect(metric);
            
        } catch (SQLException e) {
            long executionTime = System.currentTimeMillis() - startTime;
            
            // Handle transaction-aware error tracking
            if (connection != null) {
                getTransactionInterceptor().onQueryError(connection, sql, e);
            }
            
            // Collect error metric
            DBMetrics metric = DBMetrics.builder()
                    .sql(maskSqlParameters(sql))
                    .executionTimeMs(executionTime)
                    .databaseType(databaseType)
                    .connectionUrl(connectionUrl)
                    .error(e.getMessage())
                    .build();
            
            getCollector().collect(metric);
            
            // Re-throw the exception
            throw new RuntimeException(e);
        }
        
        return result;
    }
    
    /**
     * Get the metrics collector (for testing)
     */
    public static MetricsCollector getMetricsCollector() {
        return getCollector();
    }
    
    /**
     * Clear collected metrics (for testing)
     */
    public static void clearMetrics() {
        getCollector().clear();
    }
    
    private static String maskSqlParameters(String sql) {
        if (sql == null) {
            return null;
        }
        
        // Simple parameter masking - replace values with ?
        // In production, this would be more sophisticated
        return sql.replaceAll("'[^']*'", "?")
                  .replaceAll("\\b\\d+\\b", "?");
    }
    
    private static boolean executeOriginalStatement(Object statement, String sql) throws SQLException {
        // This is a placeholder for the original method call
        // In real bytecode instrumentation, this would be replaced with the actual original method
        logger.debug("Executing original statement: {}", sql);
        
        // Simulate execution
        try {
            Thread.sleep(10); // Simulate DB execution time
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        
        return true;
    }
    
    private static boolean executeOriginalPreparedStatement(Object preparedStatement) throws SQLException {
        // This is a placeholder for the original method call
        logger.debug("Executing original prepared statement");
        
        // Simulate execution
        try {
            Thread.sleep(5); // Simulate DB execution time
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        
        return true;
    }
    
    /**
     * Track TPS (Transactions Per Second) metrics
     */
    private static void trackTpsMetrics(String sql, long executionTimeMs) {
        long currentQueries = totalQueries.incrementAndGet();
        Instant now = Instant.now();
        String queryId = "q-" + currentQueries;
        
        queryTimestamps.put(queryId, now);
        
        // Calculate TPS for the last window - remove old entries first
        long windowStart = now.minusMillis(TPS_WINDOW_MS).toEpochMilli();
        queryTimestamps.entrySet().removeIf(entry -> 
            entry.getValue().toEpochMilli() < windowStart);
        
        long queriesInWindow = queryTimestamps.size();
        double currentTps = (double) queriesInWindow / (TPS_WINDOW_MS / 1000.0);
        
        logger.info("📊 TPS Metrics: Current={}, Total Queries={}, Execution Time={}ms", 
                   String.format("%.2f", currentTps), currentQueries, executionTimeMs);
        
        // Generate TPS event if threshold exceeded (e.g., > 10 TPS for demo purposes)
        if (currentTps > 10) {
            generateTpsEvent(currentTps, currentQueries);
        }
        
        // For demo purposes: simulate long running transaction detection every 10th query
        if (currentQueries % 10 == 0) {
            logger.info("🐌 DEMO: Simulating Long Running Transaction for demo purposes");
            generateLongRunningTransactionEvent("demo-connection-" + currentQueries, LONG_RUNNING_THRESHOLD_MS + 2000); // 7초 duration
        }
    }
    
    /**
     * Check for long running transactions
     */
    private static void checkForLongRunningTransaction(Connection connection) {
        try {
            String connectionId = connection.toString();
            Instant now = Instant.now();
            
            // Check if connection has autoCommit disabled (indicating active transaction)
            if (!connection.getAutoCommit()) {
                // Transaction is active - track start time if not already tracked
                transactionStartTimes.putIfAbsent(connectionId, now);
                
                Instant transactionStart = transactionStartTimes.get(connectionId);
                if (transactionStart != null) {
                    long duration = now.toEpochMilli() - transactionStart.toEpochMilli();
                    if (duration > LONG_RUNNING_THRESHOLD_MS) {
                        logger.warn("⚠️ Long Running Transaction detected: connection={}, duration={}ms (threshold: {}ms)", 
                                   connectionId, duration, LONG_RUNNING_THRESHOLD_MS);
                        generateLongRunningTransactionEvent(connectionId, duration);
                    }
                }
            } else {
                // AutoCommit is enabled - remove any tracked transaction
                transactionStartTimes.remove(connectionId);
            }
        } catch (SQLException e) {
            logger.debug("Could not check transaction status", e);
        }
    }
    
    /**
     * Generate TPS event for high transaction rates
     */
    private static void generateTpsEvent(double tps, long totalQueries) {
        logger.info("🚀 HIGH TPS EVENT: {} TPS detected (Total: {} queries)", 
                   String.format("%.2f", tps), totalQueries);
        
        // Here you would send this to the metrics collector as a special event
        try {
            getCollector().collect(DBMetrics.builder()
                .sql("TPS_EVENT")
                .executionTimeMs((long) tps)
                .databaseType("TPS_METRIC")
                .connectionUrl("tps://" + String.format("%.2f", tps))
                .build());
        } catch (Exception e) {
            logger.error("Failed to send TPS event", e);
        }
    }
    
    /**
     * Generate long running transaction event
     */
    private static void generateLongRunningTransactionEvent(String connectionId, long durationMs) {
        logger.warn("🐌 LONG RUNNING TRANSACTION EVENT: {} duration={}ms", connectionId, durationMs);
        
        // Here you would send this to the metrics collector as a special event
        try {
            getCollector().collect(DBMetrics.builder()
                .sql("LONG_RUNNING_TRANSACTION")
                .executionTimeMs(durationMs)
                .databaseType("TRANSACTION_METRIC")
                .connectionUrl("long-tx://" + connectionId)
                .build());
        } catch (Exception e) {
            logger.error("Failed to send long running transaction event", e);
        }
    }
    
    /**
     * Extract Connection object from statement (reflection-based)
     */
    private static Connection extractConnection(Object statement) {
        try {
            // Try to get connection via getConnection() method
            java.lang.reflect.Method getConnectionMethod = statement.getClass().getMethod("getConnection");
            return (Connection) getConnectionMethod.invoke(statement);
        } catch (Exception e) {
            logger.debug("Could not extract connection from statement", e);
            return null;
        }
    }
}