package io.kubedb.monitor.common.metrics;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.LongAdder;

/**
 * MetricsCollector that outputs metrics to log files
 * Suitable for debugging and simple monitoring scenarios
 */
public class LoggingMetricsCollector implements MetricsCollector {
    private static final Logger logger = LoggerFactory.getLogger(LoggingMetricsCollector.class);
    private static final Logger metricsLogger = LoggerFactory.getLogger("io.kubedb.monitor.metrics");
    
    private final AtomicLong totalCount = new AtomicLong(0);
    private final AtomicLong errorCount = new AtomicLong(0);
    private final LongAdder totalExecutionTime = new LongAdder();
    
    @Override
    public void collect(DBMetrics metric) {
        if (metric == null) {
            logger.warn("Received null metric, ignoring");
            return;
        }
        
        totalCount.incrementAndGet();
        
        if (metric.isError()) {
            errorCount.incrementAndGet();
            logErrorMetric(metric);
        } else {
            totalExecutionTime.add(metric.getExecutionTimeMs());
            logSuccessMetric(metric);
        }
        
        // Log summary statistics periodically
        long count = totalCount.get();
        if (count % 100 == 0) {
            logSummary();
        }
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
        totalCount.set(0);
        errorCount.set(0);
        totalExecutionTime.reset();
        logger.info("Cleared all metrics");
    }
    
    private void logSuccessMetric(DBMetrics metric) {
        metricsLogger.info(
            "DB_QUERY_SUCCESS|sql={}|execution_time_ms={}|db_type={}|connection_url={}|timestamp={}",
            metric.getSql(),
            metric.getExecutionTimeMs(),
            metric.getDatabaseType(),
            metric.getConnectionUrl(),
            metric.getTimestamp()
        );
        
        // Log slow queries with WARN level
        if (metric.getExecutionTimeMs() > 1000) {
            metricsLogger.warn(
                "SLOW_QUERY_DETECTED|sql={}|execution_time_ms={}|db_type={}",
                metric.getSql(),
                metric.getExecutionTimeMs(),
                metric.getDatabaseType()
            );
        }
    }
    
    private void logErrorMetric(DBMetrics metric) {
        metricsLogger.error(
            "DB_QUERY_ERROR|sql={}|error={}|db_type={}|connection_url={}|timestamp={}",
            metric.getSql(),
            metric.getErrorMessage(),
            metric.getDatabaseType(),
            metric.getConnectionUrl(),
            metric.getTimestamp()
        );
    }
    
    private void logSummary() {
        long total = totalCount.get();
        long errors = errorCount.get();
        double avgTime = getAverageExecutionTime();
        double errorRate = total > 0 ? (double) errors / total * 100 : 0.0;
        
        metricsLogger.info(
            "DB_METRICS_SUMMARY|total_queries={}|error_count={}|error_rate={:.2f}%|avg_execution_time_ms={:.2f}",
            total,
            errors,
            errorRate,
            avgTime
        );
    }
}