package io.kubedb.monitor.common.transaction;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Context for tracking a single transaction's lifecycle and queries
 */
public class TransactionContext {
    private final String transactionId;
    private final Instant startTime;
    private final List<QueryExecution> queries;
    private final AtomicInteger querySequence;
    private TransactionStatus status;
    private Instant endTime;
    
    public TransactionContext(String transactionId) {
        this.transactionId = transactionId;
        this.startTime = Instant.now();
        this.queries = new ArrayList<>();
        this.querySequence = new AtomicInteger(0);
        this.status = TransactionStatus.ACTIVE;
    }
    
    public synchronized void addQuery(String queryId, String sql, long executionTimeMs) {
        QueryExecution query = new QueryExecution(queryId, sql, executionTimeMs);
        queries.add(query);
        querySequence.incrementAndGet();
    }
    
    public synchronized void addQuery(QueryExecution query) {
        queries.add(query);
        querySequence.incrementAndGet();
    }
    
    public synchronized TransactionMetrics buildMetrics() {
        long totalExecutionTime = queries.stream()
            .mapToLong(QueryExecution::getExecutionTimeMs)
            .sum();
            
        long durationMs = endTime != null ? 
            Duration.between(startTime, endTime).toMillis() :
            Duration.between(startTime, Instant.now()).toMillis();
            
        return new TransactionMetrics(
            transactionId,
            queries.size(),
            totalExecutionTime,
            durationMs,
            status,
            startTime,
            endTime
        );
    }
    
    public synchronized void complete(TransactionStatus finalStatus) {
        this.status = finalStatus;
        this.endTime = Instant.now();
    }
    
    // Getters
    public String getTransactionId() {
        return transactionId;
    }
    
    public Instant getStartTime() {
        return startTime;
    }
    
    public List<QueryExecution> getQueries() {
        return new ArrayList<>(queries); // Return copy for thread safety
    }
    
    public TransactionStatus getStatus() {
        return status;
    }
    
    public int getCurrentQuerySequence() {
        return querySequence.get();
    }
}