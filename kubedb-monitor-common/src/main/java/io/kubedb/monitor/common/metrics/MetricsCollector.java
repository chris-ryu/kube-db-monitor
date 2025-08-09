package io.kubedb.monitor.common.metrics;

/**
 * Interface for collecting and reporting database metrics
 */
public interface MetricsCollector {
    
    /**
     * Collect a database metric
     * @param metric the metric to collect
     */
    void collect(DBMetrics metric);
    
    /**
     * Get the total number of metrics collected
     * @return total count
     */
    long getTotalCount();
    
    /**
     * Get the number of error metrics collected
     * @return error count
     */
    long getErrorCount();
    
    /**
     * Get the average execution time in milliseconds
     * @return average execution time
     */
    double getAverageExecutionTime();
    
    /**
     * Clear all collected metrics
     */
    void clear();
}