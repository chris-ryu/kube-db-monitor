package io.kubedb.monitor.agent;

import io.kubedb.monitor.common.deadlock.DeadlockDetector;
import io.kubedb.monitor.common.deadlock.DeadlockEvent;
import io.kubedb.monitor.common.transaction.TransactionRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.Optional;

import static org.mockito.Mockito.*;

/**
 * TDD for Transaction-aware JDBC interceptor with deadlock detection
 */
class TransactionAwareJDBCInterceptorTest {
    
    private TransactionAwareJDBCInterceptor interceptor;
    
    @Mock
    private Connection mockConnection;
    
    @Mock 
    private TransactionRegistry mockRegistry;
    
    @Mock
    private DeadlockDetector mockDeadlockDetector;
    
    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        interceptor = new TransactionAwareJDBCInterceptor(mockRegistry, mockDeadlockDetector);
    }
    
    @Test
    void should_register_transaction_on_setAutoCommit_false() throws SQLException {
        // Given
        String connectionId = "conn-123";
        when(mockConnection.toString()).thenReturn(connectionId);
        
        // When
        interceptor.onSetAutoCommit(mockConnection, false);
        
        // Then
        verify(mockRegistry).registerTransaction(eq(connectionId), any(String.class));
    }
    
    @Test
    void should_not_register_transaction_on_setAutoCommit_true() throws SQLException {
        // Given
        String connectionId = "conn-123";
        when(mockConnection.toString()).thenReturn(connectionId);
        
        // When
        interceptor.onSetAutoCommit(mockConnection, true);
        
        // Then
        verify(mockRegistry, never()).registerTransaction(any(), any());
    }
    
    @Test
    void should_track_query_in_transaction_context() throws SQLException {
        // Given
        String connectionId = "conn-123";
        String transactionId = "tx-456";
        String sql = "SELECT * FROM users WHERE id = ?";
        
        when(mockConnection.toString()).thenReturn(connectionId);
        when(mockRegistry.getTransactionId(connectionId)).thenReturn(Optional.of(transactionId));
        
        // When
        interceptor.onQueryExecution(mockConnection, sql, 100L, true);
        
        // Then
        verify(mockRegistry).getOrCreateTransactionContext(transactionId);
    }
    
    @Test
    void should_detect_lock_request_for_select_for_update() throws SQLException {
        // Given
        String connectionId = "conn-123";
        String transactionId = "tx-456";
        String sql = "SELECT * FROM users WHERE id = ? FOR UPDATE";
        
        when(mockConnection.toString()).thenReturn(connectionId);
        when(mockRegistry.getTransactionId(connectionId)).thenReturn(Optional.of(transactionId));
        
        // When
        interceptor.onQueryExecution(mockConnection, sql, 50L, true);
        
        // Then
        verify(mockDeadlockDetector).registerLockRequest(eq(transactionId), contains("users"));
    }
    
    @Test
    void should_detect_deadlock_on_lock_timeout() throws SQLException {
        // Given
        String connectionId = "conn-123";
        String transactionId = "tx-456";
        String sql = "UPDATE users SET name = ? WHERE id = ?";
        
        when(mockConnection.toString()).thenReturn(connectionId);
        when(mockRegistry.getTransactionId(connectionId)).thenReturn(Optional.of(transactionId));
        
        // Simulate lock timeout exception
        SQLException lockTimeoutException = new SQLException("Lock wait timeout exceeded", "HY000", 1205);
        
        // When
        interceptor.onQueryError(mockConnection, sql, lockTimeoutException);
        
        // Then
        verify(mockDeadlockDetector).checkForDeadlock();
    }
    
    @Test
    void should_complete_transaction_on_commit() throws SQLException {
        // Given
        String connectionId = "conn-123";
        when(mockConnection.toString()).thenReturn(connectionId);
        
        // When
        interceptor.onCommit(mockConnection);
        
        // Then
        verify(mockRegistry).completeTransaction(connectionId, any());
        verify(mockDeadlockDetector).onTransactionCompleted(any());
    }
    
    @Test
    void should_complete_transaction_on_rollback() throws SQLException {
        // Given
        String connectionId = "conn-123";
        when(mockConnection.toString()).thenReturn(connectionId);
        
        // When
        interceptor.onRollback(mockConnection);
        
        // Then
        verify(mockRegistry).completeTransaction(connectionId, any());
        verify(mockDeadlockDetector).onTransactionCompleted(any());
    }
    
    @Test
    void should_emit_deadlock_event_when_detected() throws SQLException {
        // Given
        String connectionId = "conn-123";
        String sql = "UPDATE orders SET status = ?";
        
        when(mockConnection.toString()).thenReturn(connectionId);
        when(mockRegistry.getTransactionId(connectionId)).thenReturn(Optional.of("tx-1"));
        
        DeadlockEvent mockDeadlockEvent = mock(DeadlockEvent.class);
        when(mockDeadlockDetector.checkForDeadlock()).thenReturn(Optional.of(mockDeadlockEvent));
        
        // When
        interceptor.onQueryError(mockConnection, sql, new SQLException("Deadlock", "40001"));
        
        // Then
        // Should emit deadlock event to metrics collectors
        verify(mockDeadlockDetector).checkForDeadlock();
    }
}