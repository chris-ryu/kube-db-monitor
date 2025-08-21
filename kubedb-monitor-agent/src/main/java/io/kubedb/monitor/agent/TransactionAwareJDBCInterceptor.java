package io.kubedb.monitor.agent;

import io.kubedb.monitor.common.deadlock.DeadlockDetector;
import io.kubedb.monitor.common.deadlock.DeadlockEvent;
import io.kubedb.monitor.common.transaction.TransactionRegistry;
import io.kubedb.monitor.common.transaction.TransactionStatus;
import io.kubedb.monitor.common.transaction.TransactionContext;
import io.kubedb.monitor.common.metrics.DBMetrics;
import io.kubedb.monitor.common.metrics.MetricsCollector;
import io.kubedb.monitor.common.metrics.MetricsCollectorFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * JDBC Interceptor with transaction tracking and deadlock detection
 */
public class TransactionAwareJDBCInterceptor {
    private static final Logger logger = LoggerFactory.getLogger(TransactionAwareJDBCInterceptor.class);
    
    private final TransactionRegistry transactionRegistry;
    private final DeadlockDetector deadlockDetector;
    private volatile MetricsCollector metricsCollector;
    
    // Long running transaction threshold (configurable)
    private static final long LONG_RUNNING_THRESHOLD_MS = 5000; // 5 seconds
    
    // SQL patterns for different types of operations
    private static final Pattern SELECT_FOR_UPDATE_PATTERN = 
        Pattern.compile(".*SELECT.*FOR\\s+UPDATE.*", Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
    private static final Pattern UPDATE_PATTERN = 
        Pattern.compile(".*UPDATE\\s+([\\w_]+).*", Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
    private static final Pattern DELETE_PATTERN = 
        Pattern.compile(".*DELETE\\s+FROM\\s+([\\w_]+).*", Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
    
    public TransactionAwareJDBCInterceptor(TransactionRegistry transactionRegistry, 
                                          DeadlockDetector deadlockDetector) {
        this.transactionRegistry = transactionRegistry;
        this.deadlockDetector = deadlockDetector;
    }
    
    /**
     * Get or create the metrics collector (lazy initialization)
     */
    private MetricsCollector getMetricsCollector() {
        if (metricsCollector == null) {
            synchronized (this) {
                if (metricsCollector == null) {
                    logger.debug("Initializing MetricsCollector for TransactionAwareJDBCInterceptor");
                    metricsCollector = MetricsCollectorFactory.createFromSystemConfig();
                    logger.info("MetricsCollector initialized in TransactionAwareJDBCInterceptor: {}", 
                               metricsCollector.getClass().getSimpleName());
                }
            }
        }
        return metricsCollector;
    }
    
    /**
     * Called when setAutoCommit is invoked
     */
    public void onSetAutoCommit(Connection connection, boolean autoCommit) throws SQLException {
        String connectionId = getConnectionId(connection);
        
        if (!autoCommit) {
            // Transaction starting
            String transactionId = generateTransactionId();
            transactionRegistry.registerTransaction(connectionId, transactionId);
            logger.debug("Started transaction {} for connection {}", transactionId, connectionId);
        } else {
            // Auto-commit mode - any existing transaction should be completed
            Optional<String> transactionId = transactionRegistry.getTransactionId(connectionId);
            if (transactionId.isPresent()) {
                transactionRegistry.completeTransaction(connectionId, TransactionStatus.COMMITTED);
                deadlockDetector.onTransactionCompleted(transactionId.get());
                logger.debug("Auto-completed transaction {} for connection {}", transactionId.get(), connectionId);
            }
        }
    }
    
    /**
     * Called when a query is executed successfully
     */
    public void onQueryExecution(Connection connection, String sql, long executionTimeMs, boolean success) {
        String connectionId = getConnectionId(connection);
        Optional<String> transactionIdOpt = transactionRegistry.getTransactionId(connectionId);
        
        if (transactionIdOpt.isPresent()) {
            String transactionId = transactionIdOpt.get();
            
            // Add query to transaction context
            TransactionContext context = transactionRegistry.getOrCreateTransactionContext(transactionId);
            String queryId = generateQueryId();
            context.addQuery(queryId, sql, executionTimeMs);
            
            // Detect lock requests and registrations
            analyzeLockBehavior(transactionId, sql);
            
            // Check for long running transaction
            checkForLongRunningTransaction(transactionId, context);
            
            logger.debug("Added query {} to transaction {} ({}ms)", queryId, transactionId, executionTimeMs);
        }
    }
    
    /**
     * Check for long running transactions and emit metrics
     */
    private void checkForLongRunningTransaction(String transactionId, TransactionContext context) {
        try {
            // Calculate transaction duration from start time to now
            java.time.Instant now = java.time.Instant.now();
            long transactionDuration = java.time.Duration.between(context.getStartTime(), now).toMillis();
            
            if (transactionDuration > LONG_RUNNING_THRESHOLD_MS) {
                logger.warn("🐌 Long Running Transaction detected: transactionId={}, duration={}ms (threshold: {}ms)", 
                           transactionId, transactionDuration, LONG_RUNNING_THRESHOLD_MS);
                
                // Generate long running transaction event
                generateLongRunningTransactionEvent(transactionId, transactionDuration);
            }
        } catch (Exception e) {
            logger.debug("Error checking for long running transaction", e);
        }
    }
    
    /**
     * Generate long running transaction event and send to metrics collector
     */
    private void generateLongRunningTransactionEvent(String transactionId, long durationMs) {
        logger.warn("🐌 LONG RUNNING TRANSACTION EVENT: {} duration={}ms", transactionId, durationMs);
        
        try {
            logger.info("🔧 DEBUG: About to send LONG_RUNNING_TRANSACTION event to collector");
            
            DBMetrics longTxMetric = DBMetrics.builder()
                .sql("LONG_RUNNING_TRANSACTION")
                .executionTimeMs(durationMs)
                .databaseType("TRANSACTION_METRIC")
                .connectionUrl("long-tx://" + transactionId)
                .build();
                
            getMetricsCollector().collect(longTxMetric);
            logger.info("✅ DEBUG: Successfully sent LONG_RUNNING_TRANSACTION event to collector");
            
        } catch (Exception e) {
            logger.error("❌ Failed to send long running transaction event", e);
        }
    }
    
    /**
     * Called when a query fails with an exception
     */
    public void onQueryError(Connection connection, String sql, SQLException exception) {
        String connectionId = getConnectionId(connection);
        Optional<String> transactionIdOpt = transactionRegistry.getTransactionId(connectionId);
        
        if (transactionIdOpt.isPresent()) {
            String transactionId = transactionIdOpt.get();
            
            // Check if this might be a deadlock-related error
            if (isDeadlockRelatedError(exception)) {
                logger.warn("Potential deadlock detected for transaction {}: {}", 
                           transactionId, exception.getMessage());
                
                Optional<DeadlockEvent> deadlockEvent = deadlockDetector.checkForDeadlock();
                if (deadlockEvent.isPresent()) {
                    handleDeadlockDetected(deadlockEvent.get());
                }
            }
            
            // Add failed query to transaction context
            TransactionContext context = transactionRegistry.getOrCreateTransactionContext(transactionId);
            String queryId = generateQueryId();
            context.addQuery(queryId, sql, 0L); // Failed query has 0 execution time
        }
    }
    
    /**
     * Called when commit() is invoked
     */
    public void onCommit(Connection connection) throws SQLException {
        String connectionId = getConnectionId(connection);
        Optional<String> transactionIdOpt = transactionRegistry.getTransactionId(connectionId);
        
        if (transactionIdOpt.isPresent()) {
            String transactionId = transactionIdOpt.get();
            transactionRegistry.completeTransaction(connectionId, TransactionStatus.COMMITTED);
            deadlockDetector.onTransactionCompleted(transactionId);
            logger.debug("Committed transaction {}", transactionId);
        }
    }
    
    /**
     * Called when rollback() is invoked  
     */
    public void onRollback(Connection connection) throws SQLException {
        String connectionId = getConnectionId(connection);
        Optional<String> transactionIdOpt = transactionRegistry.getTransactionId(connectionId);
        
        if (transactionIdOpt.isPresent()) {
            String transactionId = transactionIdOpt.get();
            transactionRegistry.completeTransaction(connectionId, TransactionStatus.ROLLED_BACK);
            deadlockDetector.onTransactionCompleted(transactionId);
            logger.debug("Rolled back transaction {}", transactionId);
        }
    }
    
    /**
     * Called when transaction commit is completed (proxy에서 사용)
     */
    public void onTransactionCommit(Connection connection) throws SQLException {
        onCommit(connection);
    }
    
    /**
     * Called when transaction rollback is completed (proxy에서 사용)  
     */
    public void onTransactionRollback(Connection connection) throws SQLException {
        onRollback(connection);
    }
    
    /**
     * Analyze SQL to detect lock requests and acquisitions
     */
    private void analyzeLockBehavior(String transactionId, String sql) {
        if (SELECT_FOR_UPDATE_PATTERN.matcher(sql).matches()) {
            // SELECT FOR UPDATE requests exclusive locks
            String resourceId = extractTableName(sql);
            if (resourceId != null) {
                deadlockDetector.registerLockRequest(transactionId, resourceId);
            }
        } else if (UPDATE_PATTERN.matcher(sql).matches() || DELETE_PATTERN.matcher(sql).matches()) {
            // UPDATE/DELETE operations acquire locks
            String resourceId = extractTableName(sql);
            if (resourceId != null) {
                // Assume immediate lock acquisition for UPDATE/DELETE
                deadlockDetector.registerLockAcquired(transactionId, resourceId);
            }
        }
    }
    
    /**
     * Extract table name from SQL for resource identification
     */
    private String extractTableName(String sql) {
        try {
            // Simple regex-based table name extraction
            String upperSql = sql.toUpperCase();
            
            if (upperSql.contains("FROM")) {
                String[] parts = upperSql.split("FROM\\s+");
                if (parts.length > 1) {
                    String afterFrom = parts[1].trim();
                    String[] tokens = afterFrom.split("\\s+");
                    return tokens[0].toLowerCase();
                }
            } else if (upperSql.contains("UPDATE")) {
                String[] parts = upperSql.split("UPDATE\\s+");
                if (parts.length > 1) {
                    String afterUpdate = parts[1].trim();
                    String[] tokens = afterUpdate.split("\\s+");
                    return tokens[0].toLowerCase();
                }
            }
        } catch (Exception e) {
            logger.debug("Failed to extract table name from SQL: {}", sql, e);
        }
        
        return null;
    }
    
    /**
     * Check if exception indicates a deadlock-related error
     */
    private boolean isDeadlockRelatedError(SQLException exception) {
        String sqlState = exception.getSQLState();
        int errorCode = exception.getErrorCode();
        String message = exception.getMessage().toLowerCase();
        
        // MySQL deadlock indicators
        if ("40001".equals(sqlState) || errorCode == 1213) {
            return true;
        }
        
        // Lock timeout indicators
        if (errorCode == 1205 || message.contains("lock wait timeout")) {
            return true;
        }
        
        // PostgreSQL deadlock indicators
        if ("40P01".equals(sqlState) || message.contains("deadlock detected")) {
            return true;
        }
        
        return false;
    }
    
    /**
     * Handle detected deadlock event
     */
    private void handleDeadlockDetected(DeadlockEvent event) {
        logger.error("DEADLOCK DETECTED: {} participants, recommended victim: {}", 
                    event.getParticipants().size(), event.getRecommendedVictim());
        
        // Emit the deadlock event to the monitoring system
        try {
            logger.info("🔧 DEBUG: About to send DEADLOCK_EVENT to collector");
            
            DBMetrics deadlockMetric = DBMetrics.builder()
                .sql("DEADLOCK_EVENT")
                .executionTimeMs(System.currentTimeMillis())
                .databaseType("DEADLOCK_METRIC")
                .connectionUrl("deadlock://" + event.getParticipants().size() + "-participants")
                .build();
                
            getMetricsCollector().collect(deadlockMetric);
            logger.info("✅ DEBUG: Successfully sent DEADLOCK_EVENT to collector");
            
        } catch (Exception e) {
            logger.error("❌ Failed to send deadlock event", e);
        }
        
        logger.error("Deadlock details: {}", event);
    }
    
    /**
     * Get connection identifier
     */
    private String getConnectionId(Connection connection) {
        return connection.toString();
    }
    
    /**
     * Generate unique transaction ID
     */
    private String generateTransactionId() {
        return "tx-" + UUID.randomUUID().toString().substring(0, 8);
    }
    
    /**
     * Generate unique query ID
     */
    private String generateQueryId() {
        return "q-" + UUID.randomUUID().toString().substring(0, 8);
    }
}