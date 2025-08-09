package io.kubedb.monitor.common.metrics;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.LongAdder;

/**
 * In-memory implementation of MetricsCollector for testing and development
 */
public class InMemoryMetricsCollector implements MetricsCollector {
    private static final Logger logger = LoggerFactory.getLogger(InMemoryMetricsCollector.class);
    
    private final ConcurrentLinkedQueue<DBMetrics> metrics = new ConcurrentLinkedQueue<>();
    private final AtomicLong totalCount = new AtomicLong(0);
    private final AtomicLong errorCount = new AtomicLong(0);
    private final LongAdder totalExecutionTime = new LongAdder();
    
    @Override
    public void collect(DBMetrics metric) {
        if (metric == null) {
            logger.warn("Received null metric, ignoring");
            return;
        }
        
        metrics.offer(metric);
        totalCount.incrementAndGet();
        
        if (metric.isError()) {
            errorCount.incrementAndGet();
        } else {
            totalExecutionTime.add(metric.getExecutionTimeMs());
        }
        
        logger.debug("Collected metric: {}", metric);
    }
    
    @Override
    public long getTotalCount() {
        return totalCount.get();
    }
    
    @Override
    public long getErrorCount() {
        return errorCount.get();
    }
    
    @Override
    public double getAverageExecutionTime() {
        long total = totalCount.get();
        long errors = errorCount.get();
        long successCount = total - errors;
        
        if (successCount == 0) {
            return 0.0;
        }
        
        return (double) totalExecutionTime.sum() / successCount;
    }
    
    @Override
    public void clear() {
        metrics.clear();
        totalCount.set(0);
        errorCount.set(0);
        totalExecutionTime.reset();
        logger.debug("Cleared all metrics");
    }
    
    /**
     * Get all collected metrics (for testing purposes)
     */
    public ConcurrentLinkedQueue<DBMetrics> getMetrics() {
        return metrics;
    }
}