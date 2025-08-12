package io.kubedb.monitor.common.deadlock;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

import java.util.Optional;
import java.util.Set;

/**
 * TDD for DeadlockDetector - detecting circular wait conditions
 */
class DeadlockDetectorTest {
    
    private DeadlockDetector detector;
    
    @BeforeEach
    void setUp() {
        detector = new DeadlockDetector();
    }
    
    @Test
    void should_register_transaction_with_lock_request() {
        // Given
        String txId = "tx-1";
        String resourceId = "table-users-row-100";
        
        // When
        detector.registerLockRequest(txId, resourceId);
        
        // Then
        assertThat(detector.isTransactionActive(txId)).isTrue();
        Set<String> waitingFor = detector.getResourcesWaitingFor(txId);
        assertThat(waitingFor).contains(resourceId);
    }
    
    @Test
    void should_detect_no_deadlock_with_single_transaction() {
        // Given
        String txId = "tx-1";
        String resourceId = "table-users-row-100";
        
        // When
        detector.registerLockRequest(txId, resourceId);
        Optional<DeadlockEvent> deadlock = detector.checkForDeadlock();
        
        // Then
        assertThat(deadlock).isEmpty();
    }
    
    @Test
    void should_detect_simple_two_way_deadlock() {
        // Given
        String tx1 = "tx-1";
        String tx2 = "tx-2";
        String resource1 = "table-users-row-100";
        String resource2 = "table-orders-row-200";
        
        // When
        // tx1 holds resource1, wants resource2
        detector.registerLockAcquired(tx1, resource1);
        detector.registerLockRequest(tx1, resource2);
        
        // tx2 holds resource2, wants resource1  
        detector.registerLockAcquired(tx2, resource2);
        detector.registerLockRequest(tx2, resource1);
        
        Optional<DeadlockEvent> deadlock = detector.checkForDeadlock();
        
        // Then
        assertThat(deadlock).isPresent();
        DeadlockEvent event = deadlock.get();
        assertThat(event.getParticipants()).containsExactlyInAnyOrder(tx1, tx2);
        assertThat(event.getDeadlockType()).isEqualTo(DeadlockType.CIRCULAR_WAIT);
    }
    
    @Test
    void should_detect_three_way_deadlock() {
        // Given
        String tx1 = "tx-1";
        String tx2 = "tx-2";
        String tx3 = "tx-3";
        String resource1 = "table-users-row-1";
        String resource2 = "table-orders-row-2";
        String resource3 = "table-products-row-3";
        
        // When - Create circular dependency: tx1->tx2->tx3->tx1
        detector.registerLockAcquired(tx1, resource1);
        detector.registerLockRequest(tx1, resource2);
        
        detector.registerLockAcquired(tx2, resource2);
        detector.registerLockRequest(tx2, resource3);
        
        detector.registerLockAcquired(tx3, resource3);
        detector.registerLockRequest(tx3, resource1);
        
        Optional<DeadlockEvent> deadlock = detector.checkForDeadlock();
        
        // Then
        assertThat(deadlock).isPresent();
        DeadlockEvent event = deadlock.get();
        assertThat(event.getParticipants()).containsExactlyInAnyOrder(tx1, tx2, tx3);
    }
    
    @Test
    void should_resolve_deadlock_when_transaction_releases_lock() {
        // Given - Setup deadlock scenario
        String tx1 = "tx-1";
        String tx2 = "tx-2";
        String resource1 = "table-users-row-100";
        String resource2 = "table-orders-row-200";
        
        detector.registerLockAcquired(tx1, resource1);
        detector.registerLockRequest(tx1, resource2);
        detector.registerLockAcquired(tx2, resource2);
        detector.registerLockRequest(tx2, resource1);
        
        assertThat(detector.checkForDeadlock()).isPresent(); // Confirm deadlock
        
        // When - tx1 releases resource1 (maybe due to rollback)
        detector.registerLockReleased(tx1, resource1);
        
        // Then
        Optional<DeadlockEvent> deadlock = detector.checkForDeadlock();
        assertThat(deadlock).isEmpty();
    }
    
    @Test
    void should_choose_deadlock_victim_with_lowest_cost() {
        // Given
        String tx1 = "tx-1";
        String tx2 = "tx-2";
        String resource1 = "resource-1";
        String resource2 = "resource-2";
        
        // tx1 has done more work (higher cost to rollback)
        detector.registerTransactionCost(tx1, 1000L); // High cost
        detector.registerTransactionCost(tx2, 100L);  // Low cost
        
        detector.registerLockAcquired(tx1, resource1);
        detector.registerLockRequest(tx1, resource2);
        detector.registerLockAcquired(tx2, resource2);
        detector.registerLockRequest(tx2, resource1);
        
        // When
        Optional<DeadlockEvent> deadlock = detector.checkForDeadlock();
        
        // Then
        assertThat(deadlock).isPresent();
        DeadlockEvent event = deadlock.get();
        assertThat(event.getRecommendedVictim()).isEqualTo(tx2); // Lower cost transaction
    }
    
    @Test
    void should_clear_transaction_state_on_completion() {
        // Given
        String txId = "tx-1";
        String resourceId = "resource-1";
        
        detector.registerLockAcquired(txId, resourceId);
        assertThat(detector.isTransactionActive(txId)).isTrue();
        
        // When
        detector.onTransactionCompleted(txId);
        
        // Then
        assertThat(detector.isTransactionActive(txId)).isFalse();
        assertThat(detector.getResourcesHeldBy(txId)).isEmpty();
    }
}