package io.kubedb.monitor.common.metrics;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class MetricsCollectorFactoryTest {

    @Test
    void shouldCreateInMemoryCollector() {
        // When
        MetricsCollector collector = MetricsCollectorFactory.create(MetricsCollectorFactory.CollectorType.IN_MEMORY);

        // Then
        assertThat(collector).isInstanceOf(InMemoryMetricsCollector.class);
    }

    @Test
    void shouldCreateLoggingCollector() {
        // When
        MetricsCollector collector = MetricsCollectorFactory.create(MetricsCollectorFactory.CollectorType.LOGGING);

        // Then
        assertThat(collector).isInstanceOf(LoggingMetricsCollector.class);
    }

    @Test
    void shouldCreateJmxCollector() {
        // When
        MetricsCollector collector = MetricsCollectorFactory.create(MetricsCollectorFactory.CollectorType.JMX);

        // Then
        assertThat(collector).isInstanceOf(JmxMetricsCollector.class);
        
        // Clean up
        if (collector instanceof JmxMetricsCollector) {
            ((JmxMetricsCollector) collector).unregister();
        }
    }

    @Test
    void shouldCreateCompositeCollector() {
        // When
        MetricsCollector collector = MetricsCollectorFactory.create(MetricsCollectorFactory.CollectorType.COMPOSITE);

        // Then
        assertThat(collector).isInstanceOf(CompositeMetricsCollector.class);
    }

    @Test
    void shouldCreateCompositeWithSpecificTypes() {
        // When
        MetricsCollector collector = MetricsCollectorFactory.createComposite(
                MetricsCollectorFactory.CollectorType.IN_MEMORY,
                MetricsCollectorFactory.CollectorType.LOGGING
        );

        // Then
        assertThat(collector).isInstanceOf(CompositeMetricsCollector.class);
        
        // Test that it works with actual metrics
        DBMetrics metric = DBMetrics.builder()
                .sql("SELECT 1")
                .executionTimeMs(100L)
                .build();
        
        collector.collect(metric);
        
        assertThat(collector.getTotalCount()).isEqualTo(1);
    }

    @Test
    void shouldCreateFromSystemConfigWithDefault() {
        // Given - no system properties set
        
        // When
        MetricsCollector collector = MetricsCollectorFactory.createFromSystemConfig();

        // Then
        assertThat(collector).isInstanceOf(CompositeMetricsCollector.class);
    }

    @Test
    void shouldCreateFromSystemProperty() {
        // Given
        System.setProperty("kubedb.monitor.collector.type", "LOGGING");

        try {
            // When
            MetricsCollector collector = MetricsCollectorFactory.createFromSystemConfig();

            // Then
            assertThat(collector).isInstanceOf(LoggingMetricsCollector.class);
        } finally {
            // Clean up
            System.clearProperty("kubedb.monitor.collector.type");
        }
    }

    @Test
    void shouldFallbackToDefaultForInvalidSystemProperty() {
        // Given
        System.setProperty("kubedb.monitor.collector.type", "INVALID_TYPE");

        try {
            // When
            MetricsCollector collector = MetricsCollectorFactory.createFromSystemConfig();

            // Then
            assertThat(collector).isInstanceOf(CompositeMetricsCollector.class);
        } finally {
            // Clean up
            System.clearProperty("kubedb.monitor.collector.type");
        }
    }

    @Test
    void compositeCollectorShouldDelegateToAllCollectors() {
        // Given
        MetricsCollector composite = MetricsCollectorFactory.createComposite(
                MetricsCollectorFactory.CollectorType.IN_MEMORY,
                MetricsCollectorFactory.CollectorType.LOGGING
        );
        
        DBMetrics metric = DBMetrics.builder()
                .sql("SELECT 1")
                .executionTimeMs(100L)
                .build();

        // When
        composite.collect(metric);

        // Then
        assertThat(composite.getTotalCount()).isEqualTo(1);
        assertThat(composite.getErrorCount()).isEqualTo(0);
        assertThat(composite.getAverageExecutionTime()).isEqualTo(100.0);
    }

    @Test
    void compositeCollectorShouldHandleClear() {
        // Given
        MetricsCollector composite = MetricsCollectorFactory.createComposite(
                MetricsCollectorFactory.CollectorType.IN_MEMORY,
                MetricsCollectorFactory.CollectorType.LOGGING
        );
        
        DBMetrics metric = DBMetrics.builder()
                .sql("SELECT 1")
                .executionTimeMs(100L)
                .build();
        
        composite.collect(metric);
        assertThat(composite.getTotalCount()).isEqualTo(1);

        // When
        composite.clear();

        // Then
        assertThat(composite.getTotalCount()).isEqualTo(0);
        assertThat(composite.getErrorCount()).isEqualTo(0);
        assertThat(composite.getAverageExecutionTime()).isEqualTo(0.0);
    }
}