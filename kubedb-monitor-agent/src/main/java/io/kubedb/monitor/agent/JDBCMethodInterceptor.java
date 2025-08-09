package io.kubedb.monitor.agent;

import io.kubedb.monitor.common.metrics.DBMetrics;
import io.kubedb.monitor.common.metrics.MetricsCollector;
import io.kubedb.monitor.common.metrics.MetricsCollectorFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.SQLException;

/**
 * Runtime interceptor for JDBC method calls
 * This class contains the actual interception logic that gets injected into JDBC classes
 */
public class JDBCMethodInterceptor {
    private static final Logger logger = LoggerFactory.getLogger(JDBCMethodInterceptor.class);
    private static final MetricsCollector collector = MetricsCollectorFactory.createFromSystemConfig();
    
    /**
     * Intercepts Statement.execute() method calls
     */
    public static boolean executeStatement(Object statement, String sql, String connectionUrl, String databaseType) {
        long startTime = System.currentTimeMillis();
        boolean result = false;
        
        try {
            logger.debug("Intercepting statement execution: {}", sql);
            
            // Call original method through reflection or direct call
            // In real implementation, this would be the original method call
            result = executeOriginalStatement(statement, sql);
            
            long executionTime = System.currentTimeMillis() - startTime;
            
            // Collect metrics
            DBMetrics metric = DBMetrics.builder()
                    .sql(maskSqlParameters(sql))
                    .executionTimeMs(executionTime)
                    .databaseType(databaseType)
                    .connectionUrl(connectionUrl)
                    .build();
            
            collector.collect(metric);
            
        } catch (SQLException e) {
            long executionTime = System.currentTimeMillis() - startTime;
            
            // Collect error metric
            DBMetrics metric = DBMetrics.builder()
                    .sql(maskSqlParameters(sql))
                    .executionTimeMs(executionTime)
                    .databaseType(databaseType)
                    .connectionUrl(connectionUrl)
                    .error(e.getMessage())
                    .build();
            
            collector.collect(metric);
            
            // Re-throw the exception
            throw new RuntimeException(e);
        }
        
        return result;
    }
    
    /**
     * Intercepts PreparedStatement.execute() method calls
     */
    public static boolean executePreparedStatement(Object preparedStatement, String sql, String connectionUrl, String databaseType) {
        long startTime = System.currentTimeMillis();
        boolean result = false;
        
        try {
            logger.debug("Intercepting prepared statement execution: {}", sql);
            
            // Call original method
            result = executeOriginalPreparedStatement(preparedStatement);
            
            long executionTime = System.currentTimeMillis() - startTime;
            
            // Collect metrics
            DBMetrics metric = DBMetrics.builder()
                    .sql(maskSqlParameters(sql))
                    .executionTimeMs(executionTime)
                    .databaseType(databaseType)
                    .connectionUrl(connectionUrl)
                    .build();
            
            collector.collect(metric);
            
        } catch (SQLException e) {
            long executionTime = System.currentTimeMillis() - startTime;
            
            // Collect error metric
            DBMetrics metric = DBMetrics.builder()
                    .sql(maskSqlParameters(sql))
                    .executionTimeMs(executionTime)
                    .databaseType(databaseType)
                    .connectionUrl(connectionUrl)
                    .error(e.getMessage())
                    .build();
            
            collector.collect(metric);
            
            // Re-throw the exception
            throw new RuntimeException(e);
        }
        
        return result;
    }
    
    /**
     * Get the metrics collector (for testing)
     */
    public static MetricsCollector getMetricsCollector() {
        return collector;
    }
    
    /**
     * Clear collected metrics (for testing)
     */
    public static void clearMetrics() {
        collector.clear();
    }
    
    private static String maskSqlParameters(String sql) {
        if (sql == null) {
            return null;
        }
        
        // Simple parameter masking - replace values with ?
        // In production, this would be more sophisticated
        return sql.replaceAll("'[^']*'", "?")
                  .replaceAll("\\b\\d+\\b", "?");
    }
    
    private static boolean executeOriginalStatement(Object statement, String sql) throws SQLException {
        // This is a placeholder for the original method call
        // In real bytecode instrumentation, this would be replaced with the actual original method
        logger.debug("Executing original statement: {}", sql);
        
        // Simulate execution
        try {
            Thread.sleep(10); // Simulate DB execution time
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        
        return true;
    }
    
    private static boolean executeOriginalPreparedStatement(Object preparedStatement) throws SQLException {
        // This is a placeholder for the original method call
        logger.debug("Executing original prepared statement");
        
        // Simulate execution
        try {
            Thread.sleep(5); // Simulate DB execution time
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        
        return true;
    }
}