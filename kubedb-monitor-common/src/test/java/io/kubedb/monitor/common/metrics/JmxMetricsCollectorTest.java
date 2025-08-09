package io.kubedb.monitor.common.metrics;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.management.MBeanServer;
import javax.management.ObjectName;
import java.lang.management.ManagementFactory;

import static org.assertj.core.api.Assertions.assertThat;

class JmxMetricsCollectorTest {

    private JmxMetricsCollector collector;
    private MBeanServer mBeanServer;
    private ObjectName objectName;

    @BeforeEach
    void setUp() throws Exception {
        collector = new JmxMetricsCollector();
        mBeanServer = ManagementFactory.getPlatformMBeanServer();
        objectName = collector.getObjectName();
    }

    @AfterEach
    void tearDown() {
        collector.unregister();
    }

    @Test
    void shouldStartWithZeroMetrics() {
        // Then
        assertThat(collector.getTotalCount()).isEqualTo(0);
        assertThat(collector.getErrorCount()).isEqualTo(0);
        assertThat(collector.getSuccessCount()).isEqualTo(0);
        assertThat(collector.getAverageExecutionTime()).isEqualTo(0.0);
        assertThat(collector.getMaxExecutionTime()).isEqualTo(0);
        assertThat(collector.getMinExecutionTime()).isEqualTo(0);
        assertThat(collector.getErrorRate()).isEqualTo(0.0);
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
        assertThat(collector.getSuccessCount()).isEqualTo(1);
        assertThat(collector.getAverageExecutionTime()).isEqualTo(100.0);
        assertThat(collector.getMaxExecutionTime()).isEqualTo(100);
        assertThat(collector.getMinExecutionTime()).isEqualTo(100);
        assertThat(collector.getErrorRate()).isEqualTo(0.0);
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
        DBMetrics metric3 = DBMetrics.builder()
                .sql("SELECT 3")
                .executionTimeMs(50L)
                .build();

        // When
        collector.collect(metric1);
        collector.collect(metric2);
        collector.collect(metric3);

        // Then
        assertThat(collector.getTotalCount()).isEqualTo(3);
        assertThat(collector.getErrorCount()).isEqualTo(0);
        assertThat(collector.getSuccessCount()).isEqualTo(3);
        assertThat(collector.getAverageExecutionTime()).isEqualTo(116.67, org.assertj.core.data.Offset.offset(0.01)); // (100+200+50)/3
        assertThat(collector.getMaxExecutionTime()).isEqualTo(200);
        assertThat(collector.getMinExecutionTime()).isEqualTo(50);
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
        assertThat(collector.getSuccessCount()).isEqualTo(1);
        assertThat(collector.getAverageExecutionTime()).isEqualTo(100.0); // Only success metrics counted
        assertThat(collector.getErrorRate()).isEqualTo(50.0); // 1 error out of 2 total = 50%
    }

    @Test
    void shouldBeAccessibleViajMx() throws Exception {
        // Given
        DBMetrics metric = DBMetrics.builder()
                .sql("SELECT 1")
                .executionTimeMs(150L)
                .build();

        // When
        collector.collect(metric);

        // Then - Access via JMX
        assertThat(mBeanServer.isRegistered(objectName)).isTrue();
        assertThat(mBeanServer.getAttribute(objectName, "TotalCount")).isEqualTo(1L);
        assertThat(mBeanServer.getAttribute(objectName, "ErrorCount")).isEqualTo(0L);
        assertThat(mBeanServer.getAttribute(objectName, "SuccessCount")).isEqualTo(1L);
        assertThat(mBeanServer.getAttribute(objectName, "AverageExecutionTime")).isEqualTo(150.0);
        assertThat(mBeanServer.getAttribute(objectName, "MaxExecutionTime")).isEqualTo(150L);
        assertThat(mBeanServer.getAttribute(objectName, "MinExecutionTime")).isEqualTo(150L);
        assertThat(mBeanServer.getAttribute(objectName, "ErrorRate")).isEqualTo(0.0);
    }

    @Test
    void shouldClearAllMetrics() throws Exception {
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
        assertThat(collector.getSuccessCount()).isEqualTo(0);
        assertThat(collector.getAverageExecutionTime()).isEqualTo(0.0);
        
        // Also verify via JMX
        assertThat(mBeanServer.getAttribute(objectName, "TotalCount")).isEqualTo(0L);
    }

    @Test
    void shouldUnregisterMBean() throws Exception {
        // Given
        assertThat(mBeanServer.isRegistered(objectName)).isTrue();

        // When
        collector.unregister();

        // Then
        assertThat(mBeanServer.isRegistered(objectName)).isFalse();
    }
}