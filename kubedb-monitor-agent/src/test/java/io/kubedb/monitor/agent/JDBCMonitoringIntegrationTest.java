package io.kubedb.monitor.agent;

import io.kubedb.monitor.common.metrics.DBMetrics;
import io.kubedb.monitor.common.metrics.InMemoryMetricsCollector;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration test for JDBC monitoring without actual bytecode instrumentation.
 * This test simulates what the agent would collect when monitoring real JDBC calls.
 */
class JDBCMonitoringIntegrationTest {

    private Connection connection;
    private InMemoryMetricsCollector collector;

    @BeforeEach
    void setUp() throws SQLException {
        // Setup in-memory H2 database
        connection = DriverManager.getConnection("jdbc:h2:mem:testdb", "sa", "");
        
        // Create test table
        try (Statement stmt = connection.createStatement()) {
            stmt.execute("CREATE TABLE users (id INT PRIMARY KEY, name VARCHAR(255))");
            stmt.execute("INSERT INTO users VALUES (1, 'John'), (2, 'Jane')");
        }
        
        collector = new InMemoryMetricsCollector();
    }

    @AfterEach
    void tearDown() throws SQLException {
        if (connection != null && !connection.isClosed()) {
            connection.close();
        }
    }

    @Test
    void shouldSimulateMonitoringSimpleSelect() throws SQLException {
        // Given
        String sql = "SELECT * FROM users";

        // When - Simulate what the agent would do
        long startTime = System.currentTimeMillis();
        
        try (Statement stmt = connection.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            
            // Consume the result set
            while (rs.next()) {
                rs.getInt("id");
                rs.getString("name");
            }
        }
        
        long executionTime = System.currentTimeMillis() - startTime;

        // Simulate agent collecting the metric
        DBMetrics metric = DBMetrics.builder()
                .sql(sql)
                .executionTimeMs(executionTime)
                .databaseType("h2")
                .connectionUrl("jdbc:h2:mem:testdb")
                .build();

        collector.collect(metric);

        // Then
        assertThat(collector.getTotalCount()).isEqualTo(1);
        assertThat(collector.getErrorCount()).isEqualTo(0);
        assertThat(collector.getAverageExecutionTime()).isGreaterThanOrEqualTo(0);
        
        DBMetrics collectedMetric = collector.getMetrics().peek();
        assertThat(collectedMetric).isNotNull();
        assertThat(collectedMetric.getSql()).isEqualTo(sql);
        assertThat(collectedMetric.getDatabaseType()).isEqualTo("h2");
    }

    @Test
    void shouldSimulateMonitoringPreparedStatement() throws SQLException {
        // Given
        String sql = "SELECT * FROM users WHERE id = ?";
        int userId = 1;

        // When - Simulate monitoring prepared statement execution
        long startTime = System.currentTimeMillis();
        
        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setInt(1, userId);
            
            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    rs.getInt("id");
                    rs.getString("name");
                }
            }
        }
        
        long executionTime = System.currentTimeMillis() - startTime;

        // Simulate agent collecting the metric (with parameter masking)
        String maskedSql = sql; // In real implementation, parameters would be masked
        DBMetrics metric = DBMetrics.builder()
                .sql(maskedSql)
                .executionTimeMs(executionTime)
                .databaseType("h2")
                .connectionUrl("jdbc:h2:mem:testdb")
                .build();

        collector.collect(metric);

        // Then
        assertThat(collector.getTotalCount()).isEqualTo(1);
        assertThat(collector.getErrorCount()).isEqualTo(0);
        
        DBMetrics collectedMetric = collector.getMetrics().peek();
        assertThat(collectedMetric).isNotNull();
        assertThat(collectedMetric.getSql()).contains("WHERE id = ?");
    }

    @Test
    void shouldSimulateMonitoringSlowQuery() throws SQLException {
        // Given - Simulate a slow query with sleep
        String sql = "SELECT SLEEP(100)"; // H2 doesn't have SLEEP, but this simulates the concept

        // When
        long startTime = System.currentTimeMillis();
        
        try (Statement stmt = connection.createStatement()) {
            // Simulate slow execution
            Thread.sleep(50); // Simulate 50ms execution time
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        
        long executionTime = System.currentTimeMillis() - startTime;

        // Simulate agent detecting slow query
        DBMetrics metric = DBMetrics.builder()
                .sql(sql)
                .executionTimeMs(executionTime)
                .databaseType("h2")
                .connectionUrl("jdbc:h2:mem:testdb")
                .build();

        collector.collect(metric);

        // Then
        assertThat(collector.getTotalCount()).isEqualTo(1);
        assertThat(collector.getAverageExecutionTime()).isGreaterThan(30); // Should be > 30ms
        
        DBMetrics collectedMetric = collector.getMetrics().peek();
        assertThat(collectedMetric).isNotNull();
        assertThat(collectedMetric.getExecutionTimeMs()).isGreaterThan(30);
    }

    @Test
    void shouldSimulateMonitoringSqlError() throws SQLException {
        // Given
        String invalidSql = "SELECT * FROM nonexistent_table";

        // When - Execute invalid SQL and capture error
        try (Statement stmt = connection.createStatement()) {
            stmt.executeQuery(invalidSql);
        } catch (SQLException e) {
            // Simulate agent capturing the error
            DBMetrics metric = DBMetrics.builder()
                    .sql(invalidSql)
                    .databaseType("h2")
                    .connectionUrl("jdbc:h2:mem:testdb")
                    .error(e.getMessage())
                    .build();

            collector.collect(metric);
        }

        // Then
        assertThat(collector.getTotalCount()).isEqualTo(1);
        assertThat(collector.getErrorCount()).isEqualTo(1);
        assertThat(collector.getAverageExecutionTime()).isEqualTo(0.0); // No successful executions
        
        DBMetrics collectedMetric = collector.getMetrics().peek();
        assertThat(collectedMetric).isNotNull();
        assertThat(collectedMetric.isError()).isTrue();
        assertThat(collectedMetric.getErrorMessage()).isNotEmpty();
    }

    @Test
    void shouldSimulateMonitoringMultipleOperations() throws SQLException {
        // Given - Multiple different operations

        // Operation 1: SELECT
        simulateOperation("SELECT COUNT(*) FROM users", false);
        
        // Operation 2: INSERT
        simulateOperation("INSERT INTO users VALUES (3, 'Bob')", false);
        
        // Operation 3: UPDATE
        simulateOperation("UPDATE users SET name = 'Bobby' WHERE id = 3", false);
        
        // Operation 4: ERROR
        simulateOperation("SELECT * FROM invalid_table", true);

        // Then
        assertThat(collector.getTotalCount()).isEqualTo(4);
        assertThat(collector.getErrorCount()).isEqualTo(1);
        assertThat(collector.getAverageExecutionTime()).isGreaterThanOrEqualTo(0);
    }

    private void simulateOperation(String sql, boolean shouldError) {
        long startTime = System.currentTimeMillis();
        
        try (Statement stmt = connection.createStatement()) {
            if (sql.startsWith("SELECT")) {
                stmt.executeQuery(sql).close();
            } else {
                stmt.executeUpdate(sql);
            }
            
            long executionTime = System.currentTimeMillis() - startTime;
            
            DBMetrics metric = DBMetrics.builder()
                    .sql(sql)
                    .executionTimeMs(executionTime)
                    .databaseType("h2")
                    .connectionUrl("jdbc:h2:mem:testdb")
                    .build();
            
            collector.collect(metric);
            
        } catch (SQLException e) {
            if (shouldError) {
                DBMetrics metric = DBMetrics.builder()
                        .sql(sql)
                        .databaseType("h2")
                        .connectionUrl("jdbc:h2:mem:testdb")
                        .error(e.getMessage())
                        .build();
                
                collector.collect(metric);
            } else {
                throw new RuntimeException("Unexpected SQL error", e);
            }
        }
    }
}