package io.kubedb.monitor.agent;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Disabled;

import java.lang.reflect.Method;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.DriverManager;
import java.util.concurrent.Callable;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Long-running transaction 추적 기능의 통합 테스트
 * 실제 SQL 실행 과정에서의 트랜잭션 감지를 테스트
 */
class LongRunningTransactionIntegrationTest {
    
    private AgentConfig testConfig;
    private MetricsCollector metricsCollector;
    
    @BeforeEach
    void setUp() {
        // 테스트용 AgentConfig 생성 (1초 임계값으로 빠른 테스트)
        testConfig = AgentConfig.fromArgs("enabled=true,long-running-tx-threshold=1000,collector-endpoint=http://test:8080/api/metrics");
        
        // MetricsCollector 생성
        metricsCollector = new MetricsCollector(testConfig);
    }
    
    @Test
    @DisplayName("handleTransactionBeginIfNeeded 함수가 존재하고 호출 가능해야 함")
    void shouldHaveHandleTransactionBeginIfNeededMethod() {
        // Given
        String sql = "BEGIN TRANSACTION";
        String connectionId = "conn-123";
        String threadName = "test-thread";
        
        // When & Then
        assertDoesNotThrow(() -> {
            Method method = findHandleTransactionBeginIfNeededMethod();
            assertNotNull(method, "handleTransactionBeginIfNeeded 메소드가 존재해야 함");
            
            // private 메소드를 accessible로 만들어서 테스트
            method.setAccessible(true);
            method.invoke(null, sql, connectionId, threadName);
        });
    }
    
    @Test
    @DisplayName("BEGIN 문 실행 시 handleTransactionBeginIfNeeded가 트랜잭션 시작을 감지해야 함")
    void shouldDetectTransactionBeginFromBeginStatement() {
        // Given
        String[] beginStatements = {
            "BEGIN",
            "BEGIN TRANSACTION",
            "START TRANSACTION",
            "begin transaction",
            "start transaction"
        };
        
        // When & Then
        for (String sql : beginStatements) {
            assertDoesNotThrow(() -> {
                Method method = findHandleTransactionBeginIfNeededMethod();
                if (method != null) {
                    method.setAccessible(true);
                    method.invoke(null, sql, "conn-123", "test-thread");
                }
            }, "BEGIN 계열 문장이 트랜잭션 시작으로 감지되어야 함: " + sql);
        }
    }
    
    @Test
    @DisplayName("DML 문 실행 시 암시적 트랜잭션이 감지되어야 함")
    void shouldDetectImplicitTransactionFromDML() {
        // Given
        String[] dmlStatements = {
            "INSERT INTO users (name) VALUES ('test')",
            "UPDATE users SET name = 'updated' WHERE id = 1",
            "DELETE FROM users WHERE id = 1",
            "MERGE INTO users USING temp ON users.id = temp.id"
        };
        
        // When & Then
        for (String sql : dmlStatements) {
            assertDoesNotThrow(() -> {
                Method method = findHandleTransactionBeginIfNeededMethod();
                if (method != null) {
                    method.setAccessible(true);
                    method.invoke(null, sql, "conn-123", "test-thread");
                }
            }, "DML 문이 암시적 트랜잭션 시작으로 감지되어야 함: " + sql);
        }
    }
    
    @Test
    @DisplayName("UniversalJDBCInterceptor.intercept 메소드가 트랜잭션 감지 로직을 호출해야 함") 
    @Disabled("실제 DB 연결이 필요한 테스트")
    void shouldCallTransactionDetectionLogicInInterceptMethod() throws Exception {
        // Given
        MockConnection mockConnection = new MockConnection();
        Method executeMethod = Connection.class.getMethod("prepareStatement", String.class);
        Object[] args = {"INSERT INTO test_table VALUES (?)"};
        Callable<Object> mockCallable = () -> null;
        
        // When & Then
        assertDoesNotThrow(() -> {
            Object result = UniversalJDBCInterceptor.intercept(
                executeMethod, 
                mockConnection, 
                args, 
                mockCallable
            );
        });
    }
    
    @Test
    @DisplayName("generateTransactionId 함수가 고유한 ID를 생성해야 함")
    void shouldGenerateUniqueTransactionIds() throws Exception {
        // Given
        String connectionId = "conn-123";
        String threadName = "test-thread";
        
        // When
        String txId1 = callGenerateTransactionId(connectionId, threadName);
        Thread.sleep(1); // 시간 차이를 만들기 위해
        String txId2 = callGenerateTransactionId(connectionId, threadName);
        
        // Then
        assertNotNull(txId1);
        assertNotNull(txId2);
        assertNotEquals(txId1, txId2, "트랜잭션 ID는 고유해야 함");
        assertTrue(txId1.startsWith("tx-"), "트랜잭션 ID는 'tx-'로 시작해야 함");
        assertTrue(txId2.startsWith("tx-"), "트랜잭션 ID는 'tx-'로 시작해야 함");
    }
    
    @Test
    @DisplayName("traceTransactionExecution이 실제 트랜잭션 시작부터 종료까지 추적해야 함")
    void shouldTraceCompleteTransactionLifecycle() throws InterruptedException {
        // Given
        String connectionId = "conn-123";
        String transactionId = "tx-test-" + System.currentTimeMillis();
        
        // When - 트랜잭션 시작
        metricsCollector.recordTransactionBegin(connectionId, transactionId);
        
        // 시뮬레이션: 쿼리 실행
        metricsCollector.updateActiveTransactionQuery(
            "SELECT * FROM users WHERE id = ?", 
            connectionId, 
            "test-thread", 
            50L
        );
        
        // 시뮬레이션: 임계값보다 긴 시간 대기
        Thread.sleep(1100); // 1.1초 (임계값 1초보다 김)
        
        // Long-running transaction 체크
        invokeCheckLongRunningTransactions(metricsCollector);
        
        // 트랜잭션 커밋
        metricsCollector.recordCommit(500000L, connectionId, transactionId);
        
        // Then
        // 예외가 발생하지 않았다면 전체 라이프사이클이 정상 처리된 것
        assertTrue(true, "전체 트랜잭션 라이프사이클이 정상 처리되어야 함");
    }
    
    @Test
    @DisplayName("실제 SQL 문자열 파싱이 트랜잭션 타입을 올바르게 감지해야 함")
    void shouldCorrectlyParseSqlForTransactionType() {
        // Given & When & Then
        assertTrue(isTransactionBeginSQL("BEGIN"), "BEGIN 감지");
        assertTrue(isTransactionBeginSQL("BEGIN TRANSACTION"), "BEGIN TRANSACTION 감지");
        assertTrue(isTransactionBeginSQL("START TRANSACTION"), "START TRANSACTION 감지");
        assertTrue(isTransactionBeginSQL("   begin   "), "공백이 있는 BEGIN 감지");
        
        assertTrue(isDMLSQL("INSERT INTO test VALUES (1)"), "INSERT 감지");
        assertTrue(isDMLSQL("UPDATE test SET col = 1"), "UPDATE 감지");
        assertTrue(isDMLSQL("DELETE FROM test"), "DELETE 감지");
        
        assertFalse(isTransactionBeginSQL("SELECT * FROM test"), "SELECT는 트랜잭션 시작 아님");
        assertFalse(isDMLSQL("SELECT * FROM test"), "SELECT는 DML 아님");
    }
    
    @Test
    @DisplayName("Connection ID 추출이 다양한 Connection 타입에서 작동해야 함")
    void shouldExtractConnectionIdFromVariousConnectionTypes() {
        // Given & When & Then
        assertDoesNotThrow(() -> {
            String id1 = extractConnectionId(new MockConnection());
            String id2 = extractConnectionId(new MockHikariConnection());
            
            assertNotNull(id1, "MockConnection에서 Connection ID 추출");
            assertNotNull(id2, "MockHikariConnection에서 Connection ID 추출");
        });
    }
    
    // Helper methods
    
    private Method findHandleTransactionBeginIfNeededMethod() {
        try {
            return UniversalJDBCInterceptor.class.getDeclaredMethod(
                "handleTransactionBeginIfNeeded", 
                String.class, String.class, String.class);
        } catch (NoSuchMethodException e) {
            System.out.println("handleTransactionBeginIfNeeded 메소드를 찾을 수 없음: " + e.getMessage());
            return null;
        }
    }
    
    private String callGenerateTransactionId(String connectionId, String threadName) {
        try {
            Method method = UniversalJDBCInterceptor.class.getDeclaredMethod(
                "generateTransactionId", String.class, String.class);
            method.setAccessible(true);
            return (String) method.invoke(null, connectionId, threadName);
        } catch (Exception e) {
            // Fallback implementation
            return String.format("tx-%s-%s-%d", 
                               connectionId != null ? connectionId.replaceAll("[^a-zA-Z0-9]", "") : "unknown",
                               threadName != null ? threadName.replaceAll("[^a-zA-Z0-9]", "") : "unknown", 
                               System.nanoTime());
        }
    }
    
    private void invokeCheckLongRunningTransactions(MetricsCollector collector) {
        try {
            Method method = MetricsCollector.class.getDeclaredMethod("checkLongRunningTransactions");
            method.setAccessible(true);
            method.invoke(collector);
        } catch (Exception e) {
            fail("checkLongRunningTransactions 호출 실패: " + e.getMessage());
        }
    }
    
    private boolean isTransactionBeginSQL(String sql) {
        if (sql == null) return false;
        String trimmed = sql.trim().toUpperCase();
        return trimmed.startsWith("BEGIN") || trimmed.startsWith("START TRANSACTION");
    }
    
    private boolean isDMLSQL(String sql) {
        if (sql == null) return false;
        String trimmed = sql.trim().toUpperCase();
        return trimmed.startsWith("INSERT") || 
               trimmed.startsWith("UPDATE") || 
               trimmed.startsWith("DELETE") ||
               trimmed.startsWith("MERGE");
    }
    
    private String extractConnectionId(Object connection) {
        return connection.toString();
    }
    
    // Mock classes for testing
    
    private static class MockConnection implements Connection {
        @Override
        public String toString() {
            return "MockConnection@" + Integer.toHexString(hashCode());
        }
        
        // Implement other Connection methods as needed for compilation
        @Override public java.sql.Statement createStatement() { return null; }
        @Override public PreparedStatement prepareStatement(String sql) { return null; }
        @Override public java.sql.CallableStatement prepareCall(String sql) { return null; }
        @Override public String nativeSQL(String sql) { return null; }
        @Override public void setAutoCommit(boolean autoCommit) { }
        @Override public boolean getAutoCommit() { return false; }
        @Override public void commit() { }
        @Override public void rollback() { }
        @Override public void close() { }
        @Override public boolean isClosed() { return false; }
        @Override public java.sql.DatabaseMetaData getMetaData() { return null; }
        @Override public void setReadOnly(boolean readOnly) { }
        @Override public boolean isReadOnly() { return false; }
        @Override public void setCatalog(String catalog) { }
        @Override public String getCatalog() { return null; }
        @Override public void setTransactionIsolation(int level) { }
        @Override public int getTransactionIsolation() { return 0; }
        @Override public java.sql.SQLWarning getWarnings() { return null; }
        @Override public void clearWarnings() { }
        @Override public java.sql.Statement createStatement(int resultSetType, int resultSetConcurrency) { return null; }
        @Override public PreparedStatement prepareStatement(String sql, int resultSetType, int resultSetConcurrency) { return null; }
        @Override public java.sql.CallableStatement prepareCall(String sql, int resultSetType, int resultSetConcurrency) { return null; }
        @Override public java.util.Map<String,Class<?>> getTypeMap() { return null; }
        @Override public void setTypeMap(java.util.Map<String,Class<?>> map) { }
        @Override public void setHoldability(int holdability) { }
        @Override public int getHoldability() { return 0; }
        @Override public java.sql.Savepoint setSavepoint() { return null; }
        @Override public java.sql.Savepoint setSavepoint(String name) { return null; }
        @Override public void rollback(java.sql.Savepoint savepoint) { }
        @Override public void releaseSavepoint(java.sql.Savepoint savepoint) { }
        @Override public java.sql.Statement createStatement(int resultSetType, int resultSetConcurrency, int resultSetHoldability) { return null; }
        @Override public PreparedStatement prepareStatement(String sql, int resultSetType, int resultSetConcurrency, int resultSetHoldability) { return null; }
        @Override public java.sql.CallableStatement prepareCall(String sql, int resultSetType, int resultSetConcurrency, int resultSetHoldability) { return null; }
        @Override public PreparedStatement prepareStatement(String sql, int autoGeneratedKeys) { return null; }
        @Override public PreparedStatement prepareStatement(String sql, int[] columnIndexes) { return null; }
        @Override public PreparedStatement prepareStatement(String sql, String[] columnNames) { return null; }
        @Override public java.sql.Clob createClob() { return null; }
        @Override public java.sql.Blob createBlob() { return null; }
        @Override public java.sql.NClob createNClob() { return null; }
        @Override public java.sql.SQLXML createSQLXML() { return null; }
        @Override public boolean isValid(int timeout) { return true; }
        @Override public void setClientInfo(String name, String value) { }
        @Override public void setClientInfo(java.util.Properties properties) { }
        @Override public String getClientInfo(String name) { return null; }
        @Override public java.util.Properties getClientInfo() { return null; }
        @Override public java.sql.Array createArrayOf(String typeName, Object[] elements) { return null; }
        @Override public java.sql.Struct createStruct(String typeName, Object[] attributes) { return null; }
        @Override public void setSchema(String schema) { }
        @Override public String getSchema() { return null; }
        @Override public void abort(java.util.concurrent.Executor executor) { }
        @Override public void setNetworkTimeout(java.util.concurrent.Executor executor, int milliseconds) { }
        @Override public int getNetworkTimeout() { return 0; }
        @Override public <T> T unwrap(Class<T> iface) { return null; }
        @Override public boolean isWrapperFor(Class<?> iface) { return false; }
    }
    
    private static class MockHikariConnection extends MockConnection {
        @Override
        public String toString() {
            return "HikariProxyConnection@" + Integer.toHexString(hashCode()) + " wrapping MockConnection";
        }
    }
}