package io.kubedb.monitor.common.transaction;

import io.kubedb.monitor.common.metrics.MetricsClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Registry for tracking active database transactions and their contexts
 */
public class TransactionRegistry {
    private static final Logger logger = LoggerFactory.getLogger(TransactionRegistry.class);
    
    // Connection ID -> Transaction ID mapping
    private final Map<String, String> connectionToTransaction = new ConcurrentHashMap<>();
    
    // Transaction ID -> Transaction Context mapping
    private final Map<String, TransactionContext> activeTransactions = new ConcurrentHashMap<>();
    
    // Optional metrics client for sending data to control plane
    private MetricsClient metricsClient;
    
    /**
     * Register a new transaction for a connection
     */
    public void registerTransaction(String connectionId, String transactionId) {
        logger.debug("Registering transaction {} for connection {}", transactionId, connectionId);
        
        connectionToTransaction.put(connectionId, transactionId);
        activeTransactions.putIfAbsent(transactionId, new TransactionContext(transactionId));
    }
    
    /**
     * Get transaction ID for a connection
     */
    public Optional<String> getTransactionId(String connectionId) {
        return Optional.ofNullable(connectionToTransaction.get(connectionId));
    }
    
    /**
     * Get or create transaction context
     */
    public TransactionContext getOrCreateTransactionContext(String transactionId) {
        return activeTransactions.computeIfAbsent(transactionId, TransactionContext::new);
    }
    
    /**
     * Complete a transaction and clean up resources
     */
    public void completeTransaction(String connectionId, TransactionStatus finalStatus) {
        String transactionId = connectionToTransaction.remove(connectionId);
        
        if (transactionId != null) {
            TransactionContext context = activeTransactions.remove(transactionId);
            if (context != null) {
                context.complete(finalStatus);
                logger.debug("Completed transaction {} with status {}", transactionId, finalStatus);
                
                // Here you could emit metrics or send to collectors
                TransactionMetrics metrics = context.buildMetrics();
                onTransactionCompleted(metrics);
            }
        }
    }
    
    /**
     * Generate a unique transaction ID
     */
    public String generateTransactionId() {
        return "tx-" + UUID.randomUUID().toString().substring(0, 8);
    }
    
    /**
     * Get count of active transactions
     */
    public int getActiveTransactionCount() {
        return activeTransactions.size();
    }
    
    /**
     * Clean up orphaned transactions (for housekeeping)
     */
    public void cleanupOrphanedTransactions(long maxAgeMs) {
        long now = System.currentTimeMillis();
        
        activeTransactions.entrySet().removeIf(entry -> {
            TransactionContext context = entry.getValue();
            long age = now - context.getStartTime().toEpochMilli();
            
            if (age > maxAgeMs) {
                logger.warn("Cleaning up orphaned transaction: {} (age: {}ms)", 
                           entry.getKey(), age);
                return true;
            }
            return false;
        });
    }
    
    /**
     * Set metrics client for sending data to control plane
     */
    public void setMetricsClient(MetricsClient metricsClient) {
        this.metricsClient = metricsClient;
    }
    
    /**
     * Record a query execution for a transaction
     */
    public void recordQueryExecution(String connectionId, QueryExecution query) {
        String transactionId = connectionToTransaction.get(connectionId);
        if (transactionId != null) {
            TransactionContext context = activeTransactions.get(transactionId);
            if (context != null) {
                context.addQuery(query);
                
                // Send query metrics if client is configured
                if (metricsClient != null) {
                    String threadName = Thread.currentThread().getName();
                    metricsClient.sendQueryMetrics(query, connectionId, threadName);
                }
            }
        }
    }
    
    /**
     * Hook for transaction completion events
     */
    protected void onTransactionCompleted(TransactionMetrics metrics) {
        logger.info("Transaction completed: {} queries in {}ms", 
                   metrics.getQueryCount(), metrics.getDurationMs());
        
        // Send transaction metrics if client is configured
        if (metricsClient != null) {
            metricsClient.sendTransactionMetrics(metrics);
        }
    }
}