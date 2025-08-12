package io.kubedb.monitor.common.transaction;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

import java.util.Optional;

/**
 * TDD for TransactionRegistry
 */
class TransactionRegistryTest {
    
    private TransactionRegistry registry;
    
    @BeforeEach
    void setUp() {
        registry = new TransactionRegistry();
    }
    
    @Test
    void should_register_new_transaction() {
        // Given
        String connectionId = "conn-123";
        String transactionId = "tx-456";
        
        // When
        registry.registerTransaction(connectionId, transactionId);
        
        // Then
        Optional<String> result = registry.getTransactionId(connectionId);
        assertThat(result).isPresent();
        assertThat(result.get()).isEqualTo(transactionId);
    }
    
    @Test
    void should_return_empty_for_unknown_connection() {
        // Given
        String unknownConnectionId = "unknown-conn";
        
        // When
        Optional<String> result = registry.getTransactionId(unknownConnectionId);
        
        // Then
        assertThat(result).isEmpty();
    }
    
    @Test
    void should_create_transaction_context() {
        // Given
        String connectionId = "conn-123";
        String transactionId = "tx-456";
        
        // When
        registry.registerTransaction(connectionId, transactionId);
        TransactionContext context = registry.getOrCreateTransactionContext(transactionId);
        
        // Then
        assertThat(context).isNotNull();
        assertThat(context.getTransactionId()).isEqualTo(transactionId);
        assertThat(context.getStartTime()).isNotNull();
        assertThat(context.getStatus()).isEqualTo(TransactionStatus.ACTIVE);
    }
    
    @Test
    void should_add_query_to_transaction_context() {
        // Given
        String transactionId = "tx-456";
        TransactionContext context = registry.getOrCreateTransactionContext(transactionId);
        
        // When
        context.addQuery("query-1", "SELECT * FROM users", 100L);
        
        // Then
        assertThat(context.getQueries()).hasSize(1);
        QueryExecution query = context.getQueries().get(0);
        assertThat(query.getQueryId()).isEqualTo("query-1");
        assertThat(query.getSql()).isEqualTo("SELECT * FROM users");
        assertThat(query.getExecutionTimeMs()).isEqualTo(100L);
    }
    
    @Test
    void should_complete_transaction() {
        // Given
        String connectionId = "conn-123";
        String transactionId = "tx-456";
        registry.registerTransaction(connectionId, transactionId);
        
        // When
        registry.completeTransaction(connectionId, TransactionStatus.COMMITTED);
        
        // Then
        Optional<String> result = registry.getTransactionId(connectionId);
        assertThat(result).isEmpty(); // Transaction should be removed after completion
    }
    
    @Test
    void should_track_multiple_transactions() {
        // Given
        String conn1 = "conn-1";
        String conn2 = "conn-2";
        String tx1 = "tx-1";
        String tx2 = "tx-2";
        
        // When
        registry.registerTransaction(conn1, tx1);
        registry.registerTransaction(conn2, tx2);
        
        // Then
        assertThat(registry.getTransactionId(conn1)).contains(tx1);
        assertThat(registry.getTransactionId(conn2)).contains(tx2);
        assertThat(registry.getActiveTransactionCount()).isEqualTo(2);
    }
    
    @Test
    void should_generate_transaction_metrics() throws InterruptedException {
        // Given
        String transactionId = "tx-456";
        TransactionContext context = registry.getOrCreateTransactionContext(transactionId);
        context.addQuery("query-1", "SELECT * FROM users", 100L);
        context.addQuery("query-2", "UPDATE users SET name = ?", 50L);
        
        // Wait a bit to ensure duration > 0
        Thread.sleep(10);
        
        // When
        TransactionMetrics metrics = context.buildMetrics();
        
        // Then
        assertThat(metrics.getTransactionId()).isEqualTo(transactionId);
        assertThat(metrics.getQueryCount()).isEqualTo(2);
        assertThat(metrics.getTotalExecutionTimeMs()).isEqualTo(150L);
        assertThat(metrics.getDurationMs()).isGreaterThanOrEqualTo(10L);
    }
}