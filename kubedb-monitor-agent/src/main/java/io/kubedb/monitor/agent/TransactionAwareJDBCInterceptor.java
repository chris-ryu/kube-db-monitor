package io.kubedb.monitor.agent;

import io.kubedb.monitor.common.deadlock.DeadlockDetector;
import io.kubedb.monitor.common.deadlock.DeadlockEvent;
import io.kubedb.monitor.common.transaction.TransactionRegistry;
import io.kubedb.monitor.common.transaction.TransactionStatus;
import io.kubedb.monitor.common.transaction.TransactionContext;
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
            
            logger.debug("Added query {} to transaction {} ({}ms)", queryId, transactionId, executionTimeMs);
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
        
        // Here you would emit the deadlock event to your monitoring system
        // For now, just log it
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