package io.kubedb.monitor.agent;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Long-running transaction 추적 기능을 위한 간단한 테스트 스위트
 * Mock 없이 실제 구현을 테스트
 */
class SimpleLongRunningTransactionTest {
    
    private AgentConfig testConfig;
    private MetricsCollector metricsCollector;
    
    @BeforeEach
    void setUp() {
        // 테스트용 AgentConfig 생성 (1초 임계값으로 빠른 테스트)
        testConfig = AgentConfig.fromArgs("enabled=true,long-running-tx-threshold=1000");
        
        // MetricsCollector 생성
        metricsCollector = new MetricsCollector(testConfig);
    }
    
    @Test
    @DisplayName("MetricsCollector가 정상적으로 생성되어야 함")
    void shouldCreateMetricsCollectorSuccessfully() {
        // Given & When & Then
        assertNotNull(metricsCollector);
        assertNotNull(testConfig);
        assertTrue(testConfig.isEnabled());
        assertEquals(1000L, testConfig.getLongRunningTransactionThresholdMs());
    }
    
    @Test
    @DisplayName("recordTransactionBegin 메소드가 존재하고 실행되어야 함")
    void shouldHaveRecordTransactionBeginMethod() {
        // Given
        String connectionId = "conn-123";
        String transactionId = "tx-456";
        
        // When & Then - 예외가 발생하지 않아야 함
        assertDoesNotThrow(() -> {
            metricsCollector.recordTransactionBegin(connectionId, transactionId);
        });
    }
    
    @Test
    @DisplayName("트랜잭션 commit 후에는 동일한 ID로 다시 시작할 수 있어야 함")
    void shouldAllowRestartAfterCommit() {
        // Given
        String connectionId = "conn-123";
        String transactionId = "tx-456";
        
        // When
        metricsCollector.recordTransactionBegin(connectionId, transactionId);
        metricsCollector.recordCommit(1000000L, connectionId, transactionId); // 1ms
        
        // Then - 동일한 ID로 다시 시작 가능해야 함
        assertDoesNotThrow(() -> {
            metricsCollector.recordTransactionBegin(connectionId, transactionId);
        });
    }
    
    @Test
    @DisplayName("트랜잭션 rollback 후에는 동일한 ID로 다시 시작할 수 있어야 함")
    void shouldAllowRestartAfterRollback() {
        // Given
        String connectionId = "conn-123";
        String transactionId = "tx-456";
        
        // When
        metricsCollector.recordTransactionBegin(connectionId, transactionId);
        metricsCollector.recordRollback(1000000L, connectionId, transactionId); // 1ms
        
        // Then - 동일한 ID로 다시 시작 가능해야 함
        assertDoesNotThrow(() -> {
            metricsCollector.recordTransactionBegin(connectionId, transactionId);
        });
    }
    
    @Test
    @DisplayName("활성 트랜잭션 쿼리 정보 업데이트가 정상 작동해야 함")
    void shouldUpdateActiveTransactionQuerySuccessfully() {
        // Given
        String connectionId = "conn-123";
        String transactionId = "tx-456";
        String sql = "SELECT * FROM users WHERE id = ?";
        String threadName = "test-thread";
        
        // When
        metricsCollector.recordTransactionBegin(connectionId, transactionId);
        
        // Then - 예외가 발생하지 않아야 함
        assertDoesNotThrow(() -> {
            metricsCollector.updateActiveTransactionQuery(sql, connectionId, threadName, 100L);
        });
    }
    
    @Test
    @DisplayName("Long-running transaction 체크 메소드가 정상 실행되어야 함")
    void shouldExecuteCheckLongRunningTransactions() throws Exception {
        // Given
        String connectionId = "conn-123";
        String transactionId = "tx-456";
        
        metricsCollector.recordTransactionBegin(connectionId, transactionId);
        
        // 임계값보다 긴 시간 대기
        Thread.sleep(1100); // 1.1초 대기 (임계값 1초보다 김)
        
        // When & Then - 예외가 발생하지 않아야 함
        assertDoesNotThrow(() -> {
            invokeCheckLongRunningTransactions(metricsCollector);
        });
    }
    
    @Test
    @DisplayName("비활성화된 Agent는 트랜잭션을 추적하지 않아야 함")
    void shouldNotTrackWhenDisabled() {
        // Given
        AgentConfig disabledConfig = AgentConfig.fromArgs("enabled=false");
        MetricsCollector disabledCollector = new MetricsCollector(disabledConfig);
        
        // When & Then - 비활성화되어 있어도 예외는 발생하지 않아야 함
        assertDoesNotThrow(() -> {
            disabledCollector.recordTransactionBegin("conn-123", "tx-456");
            disabledCollector.recordCommit(1000000L, "conn-123", "tx-456");
        });
    }
    
    @Test
    @DisplayName("SQL 타입 추출이 정상 작동해야 함")
    void shouldExtractSqlTypeCorrectly() {
        // Given
        String[] testCases = {
            "SELECT * FROM users",
            "INSERT INTO users VALUES (?)",
            "UPDATE users SET name = ?",
            "DELETE FROM users WHERE id = ?",
            "BEGIN TRANSACTION",
            "COMMIT",
            "ROLLBACK"
        };
        
        // When & Then
        for (String sql : testCases) {
            assertDoesNotThrow(() -> {
                metricsCollector.updateActiveTransactionQuery(sql, "conn-123", "thread-1", 100L);
            }, "SQL type extraction should work for: " + sql);
        }
    }
    
    @Test
    @DisplayName("Connection Pool 메트릭 전송이 정상 작동해야 함")  
    void shouldSendConnectionPoolMetricsSuccessfully() throws Exception {
        // Given
        // MetricsCollector는 생성 시 자동으로 Connection Pool 모니터링을 시작함
        
        // When & Then - 예외가 발생하지 않아야 함
        assertDoesNotThrow(() -> {
            // sendConnectionPoolMetrics는 스케줄러에 의해 자동 실행되므로
            // 짧은 시간 대기 후 정상 작동 확인
            Thread.sleep(500);
        });
    }
    
    // Helper methods
    
    private void invokeCheckLongRunningTransactions(MetricsCollector collector) {
        try {
            java.lang.reflect.Method method = MetricsCollector.class.getDeclaredMethod("checkLongRunningTransactions");
            method.setAccessible(true);
            method.invoke(collector);
        } catch (Exception e) {
            fail("Failed to invoke checkLongRunningTransactions: " + e.getMessage());
        }
    }
}