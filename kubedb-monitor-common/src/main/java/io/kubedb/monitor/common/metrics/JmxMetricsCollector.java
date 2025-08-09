package io.kubedb.monitor.common.metrics;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.management.MBeanServer;
import javax.management.ObjectName;
import java.lang.management.ManagementFactory;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.LongAdder;

/**
 * MetricsCollector that exposes metrics through JMX
 * Allows monitoring tools to collect metrics via JMX
 */
public class JmxMetricsCollector implements MetricsCollector, JmxMetricsCollectorMBean {
    private static final Logger logger = LoggerFactory.getLogger(JmxMetricsCollector.class);
    
    private final AtomicLong totalCount = new AtomicLong(0);
    private final AtomicLong errorCount = new AtomicLong(0);
    private final LongAdder totalExecutionTime = new LongAdder();
    private final AtomicLong maxExecutionTime = new AtomicLong(0);
    private final AtomicLong minExecutionTime = new AtomicLong(Long.MAX_VALUE);
    
    private volatile boolean registered = false;
    private ObjectName objectName;
    
    public JmxMetricsCollector() {
        registerMBean();
    }
    
    @Override
    public void collect(DBMetrics metric) {
        if (metric == null) {
            logger.warn("Received null metric, ignoring");
            return;
        }
        
        totalCount.incrementAndGet();
        
        if (metric.isError()) {
            errorCount.incrementAndGet();
        } else {
            long executionTime = metric.getExecutionTimeMs();
            totalExecutionTime.add(executionTime);
            
            // Update min/max execution times
            updateMinExecutionTime(executionTime);
            updateMaxExecutionTime(executionTime);
        }
        
        logger.debug("Collected metric via JMX: {}", metric);
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
    public long getMaxExecutionTime() {
        long max = maxExecutionTime.get();
        return max == 0 ? 0 : max;
    }
    
    @Override
    public long getMinExecutionTime() {
        long min = minExecutionTime.get();
        return min == Long.MAX_VALUE ? 0 : min;
    }
    
    @Override
    public double getErrorRate() {
        long total = totalCount.get();
        if (total == 0) {
            return 0.0;
        }
        return (double) errorCount.get() / total * 100.0;
    }
    
    @Override
    public long getSuccessCount() {
        return totalCount.get() - errorCount.get();
    }
    
    @Override
    public void clear() {
        totalCount.set(0);
        errorCount.set(0);
        totalExecutionTime.reset();
        maxExecutionTime.set(0);
        minExecutionTime.set(Long.MAX_VALUE);
        logger.info("Cleared all JMX metrics");
    }
    
    private void updateMaxExecutionTime(long executionTime) {
        long currentMax;
        do {
            currentMax = maxExecutionTime.get();
            if (executionTime <= currentMax) {
                break;
            }
        } while (!maxExecutionTime.compareAndSet(currentMax, executionTime));
    }
    
    private void updateMinExecutionTime(long executionTime) {
        long currentMin;
        do {
            currentMin = minExecutionTime.get();
            if (executionTime >= currentMin) {
                break;
            }
        } while (!minExecutionTime.compareAndSet(currentMin, executionTime));
    }
    
    private void registerMBean() {
        try {
            MBeanServer server = ManagementFactory.getPlatformMBeanServer();
            this.objectName = new ObjectName("io.kubedb.monitor:type=DBMetrics,instance=" + System.identityHashCode(this));
            
            if (!server.isRegistered(this.objectName)) {
                server.registerMBean(this, this.objectName);
                registered = true;
                logger.info("Registered JMX MBean: {}", this.objectName);
            }
        } catch (Exception e) {
            logger.error("Failed to register JMX MBean", e);
        }
    }
    
    public void unregister() {
        if (!registered || objectName == null) {
            return;
        }
        
        try {
            MBeanServer server = ManagementFactory.getPlatformMBeanServer();
            
            if (server.isRegistered(this.objectName)) {
                server.unregisterMBean(this.objectName);
                registered = false;
                logger.info("Unregistered JMX MBean: {}", this.objectName);
            }
        } catch (Exception e) {
            logger.error("Failed to unregister JMX MBean", e);
        }
    }
    
    public ObjectName getObjectName() {
        return objectName;
    }
}

/**
 * JMX MBean interface for DB metrics
 */
interface JmxMetricsCollectorMBean {
    long getTotalCount();
    long getErrorCount();
    long getSuccessCount();
    double getAverageExecutionTime();
    long getMaxExecutionTime();
    long getMinExecutionTime();
    double getErrorRate();
    void clear();
}