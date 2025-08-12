package io.kubedb.monitor.common.transaction;

import java.time.Instant;

/**
 * Transaction metrics for reporting
 */
public class TransactionMetrics {
    private final String transactionId;
    private final int queryCount;
    private final long totalExecutionTimeMs;
    private final long durationMs;
    private final TransactionStatus status;
    private final Instant startTime;
    private final Instant endTime;
    
    public TransactionMetrics(String transactionId, int queryCount, long totalExecutionTimeMs, 
                             long durationMs, TransactionStatus status, Instant startTime, Instant endTime) {
        this.transactionId = transactionId;
        this.queryCount = queryCount;
        this.totalExecutionTimeMs = totalExecutionTimeMs;
        this.durationMs = durationMs;
        this.status = status;
        this.startTime = startTime;
        this.endTime = endTime;
    }
    
    public String getTransactionId() {
        return transactionId;
    }
    
    public int getQueryCount() {
        return queryCount;
    }
    
    public long getTotalExecutionTimeMs() {
        return totalExecutionTimeMs;
    }
    
    public long getDurationMs() {
        return durationMs;
    }
    
    public TransactionStatus getStatus() {
        return status;
    }
    
    public Instant getStartTime() {
        return startTime;
    }
    
    public Instant getEndTime() {
        return endTime;
    }
}