package io.kubedb.monitor.common.metrics;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class LoggingMetricsCollectorTest {

    private LoggingMetricsCollector collector;

    @BeforeEach
    void setUp() {
        collector = new LoggingMetricsCollector();
    }

    @Test
    void shouldStartWithZeroMetrics() {
        // Then
        assertThat(collector.getTotalCount()).isEqualTo(0);
        assertThat(collector.getErrorCount()).isEqualTo(0);
        assertThat(collector.getAverageExecutionTime()).isEqualTo(0.0);
    }

    @Test
    void shouldCollectSingleMetric() {
        // Given
        DBMetrics metric = DBMetrics.builder()
                .sql("SELECT 1")
                .executionTimeMs(100L)
                .databaseType("h2")
                .build();

        // When
        collector.collect(metric);

        // Then
        assertThat(collector.getTotalCount()).isEqualTo(1);
        assertThat(collector.getErrorCount()).isEqualTo(0);
        assertThat(collector.getAverageExecutionTime()).isEqualTo(100.0);
    }

    @Test
    void shouldCollectMultipleMetrics() {
        // Given
        DBMetrics metric1 = DBMetrics.builder()
                .sql("SELECT 1")
                .executionTimeMs(100L)
                .build();
        DBMetrics metric2 = DBMetrics.builder()
                .sql("SELECT 2")
                .executionTimeMs(200L)
                .build();

        // When
        collector.collect(metric1);
        collector.collect(metric2);

        // Then
        assertThat(collector.getTotalCount()).isEqualTo(2);
        assertThat(collector.getErrorCount()).isEqualTo(0);
        assertThat(collector.getAverageExecutionTime()).isEqualTo(150.0); // (100+200)/2
    }

    @Test
    void shouldCountErrorMetrics() {
        // Given
        DBMetrics successMetric = DBMetrics.builder()
                .sql("SELECT 1")
                .executionTimeMs(100L)
                .build();
        DBMetrics errorMetric = DBMetrics.builder()
                .sql("INVALID SQL")
                .error("Syntax error")
                .build();

        // When
        collector.collect(successMetric);
        collector.collect(errorMetric);

        // Then
        assertThat(collector.getTotalCount()).isEqualTo(2);
        assertThat(collector.getErrorCount()).isEqualTo(1);
        assertThat(collector.getAverageExecutionTime()).isEqualTo(100.0); // Only success metrics counted
    }

    @Test
    void shouldIgnoreNullMetrics() {
        // When
        collector.collect(null);

        // Then
        assertThat(collector.getTotalCount()).isEqualTo(0);
        assertThat(collector.getErrorCount()).isEqualTo(0);
        assertThat(collector.getAverageExecutionTime()).isEqualTo(0.0);
    }

    @Test
    void shouldClearAllMetrics() {
        // Given
        DBMetrics metric = DBMetrics.builder()
                .sql("SELECT 1")
                .executionTimeMs(100L)
                .build();
        collector.collect(metric);

        // When
        collector.clear();

        // Then
        assertThat(collector.getTotalCount()).isEqualTo(0);
        assertThat(collector.getErrorCount()).isEqualTo(0);
        assertThat(collector.getAverageExecutionTime()).isEqualTo(0.0);
    }
}