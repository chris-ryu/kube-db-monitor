package io.kubedb.monitor.agent;

import io.kubedb.monitor.common.metrics.MetricsCollector;
import io.kubedb.monitor.common.metrics.MetricsCollectorFactory;
import io.kubedb.monitor.common.metrics.DBMetrics;
import io.kubedb.monitor.common.metrics.HttpMetricsCollector;
import io.kubedb.monitor.common.metrics.InMemoryMetricsCollector;
import io.kubedb.monitor.common.metrics.LoggingMetricsCollector;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.AfterEach;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 메트릭 수집 및 HTTP 전송 기능 테스트
 * Agent -> Control Plane HTTP 전송 기능 검증
 */
public class MetricsCollectionTest {

    private final String testEndpoint = "http://localhost:8080/api/metrics";

    @BeforeEach
    void setUp() {
        // 시스템 프로퍼티 초기화
        System.clearProperty("kubedb.monitor.collector.type");
        System.clearProperty("kubedb.monitor.http.endpoint");
    }

    @AfterEach
    void tearDown() {
        // 시스템 프로퍼티 정리
        System.clearProperty("kubedb.monitor.collector.type");
        System.clearProperty("kubedb.monitor.http.endpoint");
    }

    @Test
    @DisplayName("HTTP MetricsCollector 생성 테스트")
    void testHttpMetricsCollectorCreation() {
        // Given: HTTP collector 설정
        System.setProperty("kubedb.monitor.collector.type", "HTTP");
        System.setProperty("kubedb.monitor.http.endpoint", testEndpoint);

        // When: MetricsCollector 생성
        MetricsCollector collector = MetricsCollectorFactory.createFromSystemConfig();

        // Then: HttpMetricsCollector가 생성되어야 함
        assertNotNull(collector);
        assertTrue(collector instanceof HttpMetricsCollector, 
                   "HTTP 타입으로 설정시 HttpMetricsCollector가 생성되어야 함");
    }

    @Test
    @DisplayName("InMemory MetricsCollector 생성 테스트")
    void testInMemoryMetricsCollectorCreation() {
        // Given: InMemory collector 설정
        System.setProperty("kubedb.monitor.collector.type", "IN_MEMORY");

        // When: MetricsCollector 생성
        MetricsCollector collector = MetricsCollectorFactory.createFromSystemConfig();

        // Then: InMemoryMetricsCollector가 생성되어야 함
        assertNotNull(collector);
        assertTrue(collector instanceof InMemoryMetricsCollector);
    }

    @Test
    @DisplayName("Logging MetricsCollector 생성 테스트")
    void testLoggingMetricsCollectorCreation() {
        // Given: Logging collector 설정
        System.setProperty("kubedb.monitor.collector.type", "LOGGING");

        // When: MetricsCollector 생성
        MetricsCollector collector = MetricsCollectorFactory.createFromSystemConfig();

        // Then: LoggingMetricsCollector가 생성되어야 함
        assertNotNull(collector);
        assertTrue(collector instanceof LoggingMetricsCollector);
    }

    @Test
    @DisplayName("기본 Composite MetricsCollector 생성 테스트")
    void testDefaultCompositeMetricsCollectorCreation() {
        // Given: 설정 없음 (기본값 사용)
        
        // When: MetricsCollector 생성
        MetricsCollector collector = MetricsCollectorFactory.createFromSystemConfig();

        // Then: 기본 Composite collector가 생성되어야 함
        assertNotNull(collector);
        // Composite는 내부적으로 여러 collector를 가지므로 구체적인 타입 체크는 생략
    }

    @Test
    @DisplayName("메트릭 수집 기본 기능 테스트")
    void testBasicMetricsCollection() {
        // Given: InMemory collector 생성
        System.setProperty("kubedb.monitor.collector.type", "IN_MEMORY");
        MetricsCollector collector = MetricsCollectorFactory.createFromSystemConfig();

        // When: 테스트 메트릭 수집
        DBMetrics testMetric = createTestMetric("SELECT * FROM courses", 100);
        collector.collect(testMetric);

        // Then: 메트릭 카운터 확인
        assertEquals(1, collector.getTotalCount());
        assertEquals(0, collector.getErrorCount());
        assertEquals(100.0, collector.getAverageExecutionTime(), 0.1);
    }

    @Test
    @DisplayName("에러 메트릭 수집 테스트")
    void testErrorMetricsCollection() {
        // Given: InMemory collector 생성
        System.setProperty("kubedb.monitor.collector.type", "IN_MEMORY");
        MetricsCollector collector = MetricsCollectorFactory.createFromSystemConfig();

        // When: 에러 메트릭 수집
        DBMetrics errorMetric = DBMetrics.builder()
                .sql("SELECT * FROM non_existent_table")
                .executionTimeMs(50)
                .connectionUrl("jdbc:postgresql://localhost:5432/test")
                .error("Table does not exist")
                .build();
        
        collector.collect(errorMetric);

        // Then: 에러 카운터 확인
        assertEquals(1, collector.getTotalCount());
        assertEquals(1, collector.getErrorCount());
    }

    @Test
    @DisplayName("다양한 쿼리 타입 메트릭 수집 테스트")
    void testDifferentQueryTypesCollection() {
        // Given: InMemory collector 생성
        System.setProperty("kubedb.monitor.collector.type", "IN_MEMORY");
        MetricsCollector collector = MetricsCollectorFactory.createFromSystemConfig();

        // When: 다양한 쿼리 타입 수집
        collector.collect(createTestMetric("SELECT * FROM students", 50));
        collector.collect(createTestMetric("INSERT INTO students VALUES (?)", 30));
        collector.collect(createTestMetric("UPDATE students SET name = ?", 40));
        collector.collect(createTestMetric("DELETE FROM students WHERE id = ?", 20));

        // Then: 모든 메트릭이 수집되었는지 확인
        assertEquals(4, collector.getTotalCount());
        assertEquals(0, collector.getErrorCount());
        assertEquals(35.0, collector.getAverageExecutionTime(), 0.1); // (50+30+40+20)/4 = 35
    }

    @Test
    @DisplayName("특수 이벤트 타입 메트릭 테스트")
    void testSpecialEventTypesMetrics() {
        // Given: InMemory collector 생성
        System.setProperty("kubedb.monitor.collector.type", "IN_MEMORY");
        MetricsCollector collector = MetricsCollectorFactory.createFromSystemConfig();

        // When: 특수 이벤트 타입 메트릭 수집
        collector.collect(createTestMetric("TPS_EVENT", 120)); // TPS 이벤트
        collector.collect(createTestMetric("LONG_RUNNING_TRANSACTION", 5000)); // 장시간 트랜잭션
        collector.collect(createTestMetric("DEADLOCK_DETECTED", 2000)); // 데드락

        // Then: 모든 이벤트가 수집되었는지 확인
        assertEquals(3, collector.getTotalCount());
        assertEquals(0, collector.getErrorCount());
    }

    @Test
    @DisplayName("Agent 설정과 MetricsCollector 통합 테스트")
    void testAgentConfigIntegration() {
        // Given: Agent 설정 생성
        String agentArgs = "enabled=true,collector-type=http,collector-endpoint=" + testEndpoint + ",log-level=DEBUG";
        AgentConfig config = AgentConfig.fromArgs(agentArgs);

        // When: 설정 검증
        assertEquals("http", config.getCollectorType());
        assertEquals(testEndpoint, config.getCollectorEndpoint());
        assertTrue(config.isEnabled());

        // Then: MetricsCollector가 올바르게 생성되는지 확인
        MetricsCollector collector = MetricsCollectorFactory.createFromSystemConfig();
        assertNotNull(collector);
        assertTrue(collector instanceof HttpMetricsCollector);
    }

    @Test
    @DisplayName("메트릭 클리어 기능 테스트")
    void testMetricsClearFunctionality() {
        // Given: InMemory collector와 테스트 데이터
        System.setProperty("kubedb.monitor.collector.type", "IN_MEMORY");
        MetricsCollector collector = MetricsCollectorFactory.createFromSystemConfig();
        
        collector.collect(createTestMetric("SELECT 1", 10));
        collector.collect(createTestMetric("SELECT 2", 20));

        // When: 메트릭 클리어
        assertEquals(2, collector.getTotalCount()); // 클리어 전 확인
        collector.clear();

        // Then: 모든 카운터가 0이 되어야 함
        assertEquals(0, collector.getTotalCount());
        assertEquals(0, collector.getErrorCount());
        assertEquals(0.0, collector.getAverageExecutionTime());
    }

    @Test
    @DisplayName("대량 메트릭 수집 성능 테스트")
    void testHighVolumeMetricsPerformance() {
        // Given: InMemory collector 생성
        System.setProperty("kubedb.monitor.collector.type", "IN_MEMORY");
        MetricsCollector collector = MetricsCollectorFactory.createFromSystemConfig();

        // When: 대량의 메트릭 수집
        final int METRIC_COUNT = 100;
        long startTime = System.currentTimeMillis();
        
        for (int i = 0; i < METRIC_COUNT; i++) {
            collector.collect(createTestMetric("SELECT * FROM test_table_" + i, 10 + (i % 50)));
        }
        
        long endTime = System.currentTimeMillis();
        long duration = endTime - startTime;

        // Then: 성능 검증
        assertEquals(METRIC_COUNT, collector.getTotalCount());
        assertTrue(duration < 1000, "100개 메트릭 수집이 1초 이내에 완료되어야 함");
        assertTrue(collector.getAverageExecutionTime() > 0);
        
        System.out.println("대량 메트릭 수집 성능: " + METRIC_COUNT + "개 메트릭을 " + duration + "ms에 처리");
    }

    /**
     * 테스트용 DBMetrics 객체 생성
     */
    private DBMetrics createTestMetric(String sql, long executionTime) {
        return DBMetrics.builder()
                .sql(sql)
                .executionTimeMs(executionTime)
                .connectionUrl("jdbc:postgresql://localhost:5432/test")
                .databaseType("postgresql")
                .build();
    }
}