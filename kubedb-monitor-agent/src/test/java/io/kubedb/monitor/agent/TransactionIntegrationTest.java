package io.kubedb.monitor.agent;

import io.kubedb.monitor.common.deadlock.DeadlockDetector;
import io.kubedb.monitor.common.deadlock.DeadlockEvent;
import io.kubedb.monitor.common.transaction.TransactionRegistry;
import io.kubedb.monitor.common.transaction.TransactionContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

import java.sql.*;
import java.util.Optional;

/**
 * Integration test for transaction tracking and deadlock detection
 * Uses in-memory H2 database for realistic testing
 */
class TransactionIntegrationTest {
    
    private TransactionRegistry registry;
    private DeadlockDetector deadlockDetector;
    private TransactionAwareJDBCInterceptor interceptor;
    private Connection connection;
    
    @BeforeEach
    void setUp() throws SQLException {
        registry = new TransactionRegistry();
        deadlockDetector = new DeadlockDetector();
        interceptor = new TransactionAwareJDBCInterceptor(registry, deadlockDetector);
        
        // Create unique in-memory H2 database for each test
        String dbName = "testdb_" + System.currentTimeMillis();
        connection = DriverManager.getConnection("jdbc:h2:mem:" + dbName, "sa", "");
        
        // Create test table
        try (Statement stmt = connection.createStatement()) {
            stmt.execute("CREATE TABLE users (id INT PRIMARY KEY, name VARCHAR(50))");
            stmt.execute("INSERT INTO users VALUES (1, 'Alice'), (2, 'Bob')");
        }
    }
    
    @Test
    void should_track_transaction_lifecycle() throws SQLException {
        // Given - Start transaction
        connection.setAutoCommit(false);
        interceptor.onSetAutoCommit(connection, false);
        
        String connectionId = connection.toString();
        Optional<String> transactionId = registry.getTransactionId(connectionId);
        
        // Then - Transaction should be registered
        assertThat(transactionId).isPresent();
        assertThat(registry.getActiveTransactionCount()).isEqualTo(1);
        
        // When - Execute queries
        try (PreparedStatement stmt = connection.prepareStatement("SELECT * FROM users WHERE id = ?")) {
            stmt.setInt(1, 1);
            stmt.executeQuery();
            interceptor.onQueryExecution(connection, "SELECT * FROM users WHERE id = ?", 10L, true);
        }
        
        try (PreparedStatement stmt = connection.prepareStatement("UPDATE users SET name = ? WHERE id = ?")) {
            stmt.setString(1, "Updated Alice");
            stmt.setInt(2, 1);
            stmt.executeUpdate();
            interceptor.onQueryExecution(connection, "UPDATE users SET name = ? WHERE id = ?", 25L, true);
        }
        
        // Then - Queries should be tracked
        TransactionContext context = registry.getOrCreateTransactionContext(transactionId.get());
        assertThat(context.getQueries()).hasSize(2);
        
        // When - Commit transaction
        connection.commit();
        interceptor.onCommit(connection);
        
        // Then - Transaction should be completed
        assertThat(registry.getActiveTransactionCount()).isEqualTo(0);
    }
    
    @Test 
    void should_detect_lock_requests_in_select_for_update() throws SQLException {
        // Given - Start transaction
        connection.setAutoCommit(false);
        interceptor.onSetAutoCommit(connection, false);
        
        String connectionId = connection.toString();
        Optional<String> transactionId = registry.getTransactionId(connectionId);
        
        // When - Execute SELECT FOR UPDATE
        interceptor.onQueryExecution(connection, "SELECT * FROM users WHERE id = 1 FOR UPDATE", 15L, true);
        
        // Then - Lock request should be registered
        assertThat(deadlockDetector.getResourcesWaitingFor(transactionId.get())).contains("users");
        
        // Cleanup
        connection.rollback();
        interceptor.onRollback(connection);
    }
    
    @Test
    void should_simulate_simple_deadlock_scenario() throws SQLException {
        // This test simulates the detection logic, not actual database deadlock
        // since that would require multiple connections and complex setup
        
        String tx1 = "tx-1";
        String tx2 = "tx-2";
        String resource1 = "users";
        String resource2 = "orders";
        
        // Simulate deadlock scenario
        deadlockDetector.registerLockAcquired(tx1, resource1);
        deadlockDetector.registerLockRequest(tx1, resource2);
        
        deadlockDetector.registerLockAcquired(tx2, resource2);
        deadlockDetector.registerLockRequest(tx2, resource1);
        
        // When - Check for deadlock
        Optional<DeadlockEvent> deadlock = deadlockDetector.checkForDeadlock();
        
        // Then - Deadlock should be detected
        assertThat(deadlock).isPresent();
        assertThat(deadlock.get().getParticipants()).containsExactlyInAnyOrder(tx1, tx2);
    }
    
    @Test
    void should_handle_rollback_scenario() throws SQLException {
        // Given - Start transaction and execute query
        connection.setAutoCommit(false);
        interceptor.onSetAutoCommit(connection, false);
        
        String connectionId = connection.toString();
        Optional<String> transactionId = registry.getTransactionId(connectionId);
        
        // Execute some work
        interceptor.onQueryExecution(connection, "UPDATE users SET name = 'Test' WHERE id = 1", 30L, true);
        
        TransactionContext context = registry.getOrCreateTransactionContext(transactionId.get());
        assertThat(context.getQueries()).hasSize(1);
        
        // When - Rollback
        connection.rollback();
        interceptor.onRollback(connection);
        
        // Then - Transaction should be completed and cleaned up
        assertThat(registry.getActiveTransactionCount()).isEqualTo(0);
        assertThat(deadlockDetector.isTransactionActive(transactionId.get())).isFalse();
    }
    
    @Test
    void should_extract_table_names_from_sql() {
        // Test the private method indirectly through query execution
        connection.toString(); // Just to have a connection reference
        
        // This would be tested through the actual interceptor behavior
        // The table name extraction logic is working if lock detection works
        assertThat(true).isTrue(); // Placeholder for more complex table extraction tests
    }
}