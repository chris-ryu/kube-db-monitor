package io.kubedb.monitor.common.metrics;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class DBMetricsTest {

    @Test
    void shouldBuildMetricsWithAllFields() {
        // Given
        String sql = "SELECT * FROM users WHERE id = ?";
        long executionTime = 150L;
        String databaseType = "mysql";
        String connectionUrl = "jdbc:mysql://localhost:3306/test";
        Instant timestamp = Instant.now();

        // When
        DBMetrics metrics = DBMetrics.builder()
                .sql(sql)
                .executionTimeMs(executionTime)
                .databaseType(databaseType)
                .connectionUrl(connectionUrl)
                .timestamp(timestamp)
                .build();

        // Then
        assertThat(metrics.getSql()).isEqualTo(sql);
        assertThat(metrics.getExecutionTimeMs()).isEqualTo(executionTime);
        assertThat(metrics.getDatabaseType()).isEqualTo(databaseType);
        assertThat(metrics.getConnectionUrl()).isEqualTo(connectionUrl);
        assertThat(metrics.getTimestamp()).isEqualTo(timestamp);
        assertThat(metrics.isError()).isFalse();
        assertThat(metrics.getErrorMessage()).isNull();
    }

    @Test
    void shouldBuildErrorMetrics() {
        // Given
        String sql = "INVALID SQL";
        String errorMessage = "Syntax error";

        // When
        DBMetrics metrics = DBMetrics.builder()
                .sql(sql)
                .error(errorMessage)
                .build();

        // Then
        assertThat(metrics.getSql()).isEqualTo(sql);
        assertThat(metrics.isError()).isTrue();
        assertThat(metrics.getErrorMessage()).isEqualTo(errorMessage);
        assertThat(metrics.getExecutionTimeMs()).isEqualTo(0L);
    }

    @Test
    void shouldHaveDefaultTimestamp() {
        // Given/When
        DBMetrics metrics = DBMetrics.builder()
                .sql("SELECT 1")
                .build();

        // Then
        assertThat(metrics.getTimestamp()).isNotNull();
        assertThat(metrics.getTimestamp()).isBeforeOrEqualTo(Instant.now());
    }

    @Test
    void shouldProvideStringRepresentation() {
        // Given
        DBMetrics metrics = DBMetrics.builder()
                .sql("SELECT 1")
                .executionTimeMs(100L)
                .databaseType("h2")
                .build();

        // When
        String result = metrics.toString();

        // Then
        assertThat(result).contains("SELECT 1");
        assertThat(result).contains("100");
        assertThat(result).contains("h2");
        assertThat(result).contains("isError=false");
    }
}