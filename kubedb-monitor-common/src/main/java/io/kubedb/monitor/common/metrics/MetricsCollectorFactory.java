package io.kubedb.monitor.common.metrics;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

/**
 * Factory for creating MetricsCollector instances based on configuration
 */
public class MetricsCollectorFactory {
    private static final Logger logger = LoggerFactory.getLogger(MetricsCollectorFactory.class);
    
    public enum CollectorType {
        IN_MEMORY,
        LOGGING,
        JMX,
        COMPOSITE
    }
    
    /**
     * Create a metrics collector based on type
     */
    public static MetricsCollector create(CollectorType type) {
        switch (type) {
            case IN_MEMORY:
                logger.info("Creating InMemoryMetricsCollector");
                return new InMemoryMetricsCollector();
                
            case LOGGING:
                logger.info("Creating LoggingMetricsCollector");
                return new LoggingMetricsCollector();
                
            case JMX:
                logger.info("Creating JmxMetricsCollector");
                return new JmxMetricsCollector();
                
            case COMPOSITE:
                logger.info("Creating CompositeMetricsCollector with logging and JMX");
                return createComposite(CollectorType.LOGGING, CollectorType.JMX);
                
            default:
                logger.warn("Unknown collector type: {}, falling back to InMemoryMetricsCollector", type);
                return new InMemoryMetricsCollector();
        }
    }
    
    /**
     * Create a composite collector with multiple backends
     */
    public static MetricsCollector createComposite(CollectorType... types) {
        List<MetricsCollector> collectors = new ArrayList<>();
        
        for (CollectorType type : types) {
            if (type != CollectorType.COMPOSITE) { // Avoid infinite recursion
                collectors.add(create(type));
            }
        }
        
        return new CompositeMetricsCollector(collectors);
    }
    
    /**
     * Create collector based on system property or environment variable
     */
    public static MetricsCollector createFromSystemConfig() {
        String configValue = System.getProperty("kubedb.monitor.collector.type");
        if (configValue == null) {
            configValue = System.getenv("KUBEDB_MONITOR_COLLECTOR_TYPE");
        }
        
        if (configValue == null || configValue.trim().isEmpty()) {
            logger.info("No collector type specified, using default COMPOSITE collector");
            return create(CollectorType.COMPOSITE);
        }
        
        try {
            CollectorType type = CollectorType.valueOf(configValue.toUpperCase());
            return create(type);
        } catch (IllegalArgumentException e) {
            logger.warn("Invalid collector type: {}, using default COMPOSITE collector", configValue);
            return create(CollectorType.COMPOSITE);
        }
    }
}

/**
 * Composite collector that delegates to multiple collectors
 */
class CompositeMetricsCollector implements MetricsCollector {
    private static final Logger logger = LoggerFactory.getLogger(CompositeMetricsCollector.class);
    
    private final List<MetricsCollector> collectors;
    
    public CompositeMetricsCollector(List<MetricsCollector> collectors) {
        this.collectors = new ArrayList<>(collectors);
        logger.info("Created CompositeMetricsCollector with {} collectors", collectors.size());
    }
    
    @Override
    public void collect(DBMetrics metric) {
        for (MetricsCollector collector : collectors) {
            try {
                collector.collect(metric);
            } catch (Exception e) {
                logger.error("Error collecting metric with collector: {}", collector.getClass().getSimpleName(), e);
            }
        }
    }
    
    @Override
    public long getTotalCount() {
        // Return count from the first collector
        return collectors.isEmpty() ? 0 : collectors.get(0).getTotalCount();
    }
    
    @Override
    public long getErrorCount() {
        // Return count from the first collector
        return collectors.isEmpty() ? 0 : collectors.get(0).getErrorCount();
    }
    
    @Override
    public double getAverageExecutionTime() {
        // Return average from the first collector
        return collectors.isEmpty() ? 0.0 : collectors.get(0).getAverageExecutionTime();
    }
    
    @Override
    public void clear() {
        for (MetricsCollector collector : collectors) {
            try {
                collector.clear();
            } catch (Exception e) {
                logger.error("Error clearing metrics from collector: {}", collector.getClass().getSimpleName(), e);
            }
        }
    }
}