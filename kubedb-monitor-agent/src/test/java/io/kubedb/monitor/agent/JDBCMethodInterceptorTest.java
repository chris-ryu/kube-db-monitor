package io.kubedb.monitor.agent;

import io.kubedb.monitor.common.metrics.DBMetrics;
import io.kubedb.monitor.common.metrics.InMemoryMetricsCollector;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JDBCMethodInterceptorTest {

    @BeforeEach
    void setUp() {
        JDBCMethodInterceptor.clearMetrics();
    }
    
    @AfterEach
    void tearDown() {
        JDBCMethodInterceptor.clearMetrics();
    }

    @Test
    void shouldInterceptStatementExecution() {
        // Given
        String sql = "SELECT * FROM users";
        String connectionUrl = "jdbc:h2:mem:test";
        String databaseType = "h2";
        Object mockStatement = new Object();

        // When
        boolean result = JDBCMethodInterceptor.executeStatement(mockStatement, sql, connectionUrl, databaseType);

        // Then
        assertThat(result).isTrue();
        
        InMemoryMetricsCollector collector = (InMemoryMetricsCollector) JDBCMethodInterceptor.getMetricsCollector();
        assertThat(collector.getTotalCount()).isEqualTo(1);
        assertThat(collector.getErrorCount()).isEqualTo(0);
        
        DBMetrics metric = collector.getMetrics().peek();
        assertThat(metric).isNotNull();
        assertThat(metric.getSql()).isEqualTo(sql);
        assertThat(metric.getDatabaseType()).isEqualTo(databaseType);
        assertThat(metric.getConnectionUrl()).isEqualTo(connectionUrl);
        assertThat(metric.getExecutionTimeMs()).isGreaterThanOrEqualTo(0);
    }

    @Test
    void shouldInterceptPreparedStatementExecution() {
        // Given
        String sql = "SELECT * FROM users WHERE id = ?";
        String connectionUrl = "jdbc:mysql://localhost/test";
        String databaseType = "mysql";
        Object mockPreparedStatement = new Object();

        // When
        boolean result = JDBCMethodInterceptor.executePreparedStatement(mockPreparedStatement, sql, connectionUrl, databaseType);

        // Then
        assertThat(result).isTrue();
        
        InMemoryMetricsCollector collector = (InMemoryMetricsCollector) JDBCMethodInterceptor.getMetricsCollector();
        assertThat(collector.getTotalCount()).isEqualTo(1);
        assertThat(collector.getErrorCount()).isEqualTo(0);
    }

    @Test
    void shouldMaskSqlParameters() {
        // Given - SQL with string and numeric parameters
        String sql = "SELECT * FROM users WHERE name = 'John' AND id = 123";
        String connectionUrl = "jdbc:h2:mem:test";
        String databaseType = "h2";
        Object mockStatement = new Object();

        // When
        JDBCMethodInterceptor.executeStatement(mockStatement, sql, connectionUrl, databaseType);

        // Then
        InMemoryMetricsCollector collector = (InMemoryMetricsCollector) JDBCMethodInterceptor.getMetricsCollector();
        DBMetrics metric = collector.getMetrics().peek();
        
        assertThat(metric).isNotNull();
        assertThat(metric.getSql()).contains("?");
        assertThat(metric.getSql()).doesNotContain("John");
        assertThat(metric.getSql()).doesNotContain("123");
    }

    @Test
    void shouldHandleNullSql() {
        // Given
        String sql = null;
        String connectionUrl = "jdbc:h2:mem:test";
        String databaseType = "h2";
        Object mockStatement = new Object();

        // When
        boolean result = JDBCMethodInterceptor.executeStatement(mockStatement, sql, connectionUrl, databaseType);

        // Then
        assertThat(result).isTrue();
        
        InMemoryMetricsCollector collector = (InMemoryMetricsCollector) JDBCMethodInterceptor.getMetricsCollector();
        assertThat(collector.getTotalCount()).isEqualTo(1);
        
        DBMetrics metric = collector.getMetrics().peek();
        assertThat(metric.getSql()).isNull();
    }

    @Test
    void shouldCollectMultipleMetrics() {
        // Given
        Object mockStatement = new Object();

        // When - Execute multiple statements
        JDBCMethodInterceptor.executeStatement(mockStatement, "SELECT 1", "jdbc:h2:mem:test", "h2");
        JDBCMethodInterceptor.executeStatement(mockStatement, "SELECT 2", "jdbc:h2:mem:test", "h2");
        JDBCMethodInterceptor.executePreparedStatement(mockStatement, "SELECT ?", "jdbc:h2:mem:test", "h2");

        // Then
        InMemoryMetricsCollector collector = (InMemoryMetricsCollector) JDBCMethodInterceptor.getMetricsCollector();
        assertThat(collector.getTotalCount()).isEqualTo(3);
        assertThat(collector.getErrorCount()).isEqualTo(0);
        assertThat(collector.getAverageExecutionTime()).isGreaterThan(0);
    }

    @Test
    void shouldRecordExecutionTime() {
        // Given
        String sql = "SELECT SLEEP(50)"; // Simulate slow query
        String connectionUrl = "jdbc:h2:mem:test";
        String databaseType = "h2";
        Object mockStatement = new Object();

        // When
        JDBCMethodInterceptor.executeStatement(mockStatement, sql, connectionUrl, databaseType);

        // Then
        InMemoryMetricsCollector collector = (InMemoryMetricsCollector) JDBCMethodInterceptor.getMetricsCollector();
        DBMetrics metric = collector.getMetrics().peek();
        
        assertThat(metric).isNotNull();
        assertThat(metric.getExecutionTimeMs()).isGreaterThan(5); // Should take at least 5ms due to Thread.sleep
    }

    @Test
    void shouldClearMetrics() {
        // Given
        Object mockStatement = new Object();
        JDBCMethodInterceptor.executeStatement(mockStatement, "SELECT 1", "jdbc:h2:mem:test", "h2");
        
        InMemoryMetricsCollector collector = (InMemoryMetricsCollector) JDBCMethodInterceptor.getMetricsCollector();
        assertThat(collector.getTotalCount()).isEqualTo(1);

        // When
        JDBCMethodInterceptor.clearMetrics();

        // Then
        assertThat(collector.getTotalCount()).isEqualTo(0);
        assertThat(collector.getErrorCount()).isEqualTo(0);
        assertThat(collector.getMetrics()).isEmpty();
    }
}