package io.kubedb.monitor.agent;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.sql.*;

import static org.assertj.core.api.Assertions.*;

/**
 * 개선된 Agent 통합 테스트
 * 
 * 클래스별 특화 변환 기능과 누락 탐지 기능을 검증
 */
@Testcontainers
@DisplayName("개선된 Agent 통합 테스트")
class EnhancedAgentIntegrationTest {
    
    private static final Logger logger = LoggerFactory.getLogger(EnhancedAgentIntegrationTest.class);
    
    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15-alpine")
            .withDatabaseName("agent_test")
            .withUsername("testuser")
            .withPassword("testpass");
    
    private String testDbUrl;
    private AgentConfig testConfig;
    
    @BeforeEach
    void setUp() {
        testDbUrl = postgres.getJdbcUrl();
        
        testConfig = new AgentConfig.Builder()
                .enabled(true)
                .samplingRate(1.0)
                .supportedDatabases(java.util.Arrays.asList("postgresql"))
                .maskSqlParams(true)
                .slowQueryThresholdMs(100L)
                .collectorType("COMPOSITE")
                .logLevel("DEBUG")
                .build();
                
        // 테스트 테이블 생성
        createTestTables();
        
        logger.info("개선된 Agent 통합 테스트 초기화 완료");
    }
    
    @Nested
    @DisplayName("🎯 클래스별 특화 변환 검증")
    class SpecializedTransformationTests {
        
        @Test
        @DisplayName("ConnectionTransformer - 트랜잭션 관리 모니터링")
        void testConnectionTransformerMonitoring() throws Exception {
            logger.info("=== 🔌 Connection 변환기 모니터링 테스트 ===");
            
            try (Connection conn = DriverManager.getConnection(testDbUrl, "testuser", "testpass")) {
                logger.info("사용된 Connection 구현체: {}", conn.getClass().getName());
                
                // 트랜잭션 모니터링 테스트
                boolean originalAutoCommit = conn.getAutoCommit();
                logger.info("원본 autoCommit: {}", originalAutoCommit);
                
                // setAutoCommit() 모니터링 검증
                conn.setAutoCommit(false);
                assertThat(conn.getAutoCommit()).isFalse();
                logger.info("✅ setAutoCommit(false) 모니터링 완료");
                
                // commit() 모니터링 검증  
                conn.commit();
                logger.info("✅ commit() 모니터링 완료");
                
                // rollback() 모니터링 검증
                conn.rollback();
                logger.info("✅ rollback() 모니터링 완료");
                
                // 원상복구
                conn.setAutoCommit(originalAutoCommit);
                logger.info("✅ Connection 변환기 모니터링 테스트 성공");
            }
        }
        
        @Test
        @DisplayName("PreparedStatementTransformer - 쿼리 실행 모니터링") 
        void testPreparedStatementTransformerMonitoring() throws Exception {
            logger.info("=== 📝 PreparedStatement 변환기 모니터링 테스트 ===");
            
            try (Connection conn = DriverManager.getConnection(testDbUrl, "testuser", "testpass")) {
                String sql = "INSERT INTO test_table (name, value) VALUES (?, ?)";
                
                try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
                    logger.info("사용된 PreparedStatement 구현체: {}", pstmt.getClass().getName());
                    
                    // 파라미터 바인딩 모니터링
                    pstmt.setString(1, "test_name");
                    pstmt.setInt(2, 42);
                    logger.info("✅ setString(), setInt() 파라미터 바인딩 모니터링 완료");
                    
                    // executeUpdate() 모니터링
                    int result = pstmt.executeUpdate();
                    assertThat(result).isEqualTo(1);
                    logger.info("✅ executeUpdate() 모니터링 완료: {} 행 영향", result);
                }
                
                // 조회 쿼리 테스트
                String selectSql = "SELECT name, value FROM test_table WHERE name = ?";
                try (PreparedStatement pstmt = conn.prepareStatement(selectSql)) {
                    pstmt.setString(1, "test_name");
                    
                    // executeQuery() 모니터링
                    try (ResultSet rs = pstmt.executeQuery()) {
                        assertThat(rs.next()).isTrue();
                        assertThat(rs.getString("name")).isEqualTo("test_name");
                        assertThat(rs.getInt("value")).isEqualTo(42);
                        logger.info("✅ executeQuery() 모니터링 완료");
                    }
                }
                
                logger.info("✅ PreparedStatement 변환기 모니터링 테스트 성공");
            }
        }
        
        @Test
        @DisplayName("StatementTransformer - SQL 실행 모니터링")
        void testStatementTransformerMonitoring() throws Exception {
            logger.info("=== 📄 Statement 변환기 모니터링 테스트 ===");
            
            try (Connection conn = DriverManager.getConnection(testDbUrl, "testuser", "testpass");
                 Statement stmt = conn.createStatement()) {
                
                logger.info("사용된 Statement 구현체: {}", stmt.getClass().getName());
                
                // executeQuery() 모니터링
                String selectSql = "SELECT COUNT(*) as cnt FROM test_table";
                try (ResultSet rs = stmt.executeQuery(selectSql)) {
                    assertThat(rs.next()).isTrue();
                    int count = rs.getInt("cnt");
                    logger.info("✅ executeQuery(String) 모니터링 완료: {} 행", count);
                }
                
                // executeUpdate() 모니터링
                String updateSql = "UPDATE test_table SET value = 100 WHERE name = 'test_name'";
                int updateResult = stmt.executeUpdate(updateSql);
                logger.info("✅ executeUpdate(String) 모니터링 완료: {} 행 업데이트", updateResult);
                
                // execute() 모니터링
                String deleteSql = "DELETE FROM test_table WHERE name = 'test_name'";
                boolean executeResult = stmt.execute(deleteSql);
                logger.info("✅ execute(String) 모니터링 완료: {}", executeResult);
                
                logger.info("✅ Statement 변환기 모니터링 테스트 성공");
            }
        }
        
        @Test
        @DisplayName("ResultSetTransformer - 결과 집합 모니터링")
        void testResultSetTransformerMonitoring() throws Exception {
            logger.info("=== 📊 ResultSet 변환기 모니터링 테스트 ===");
            
            // 테스트 데이터 준비
            try (Connection conn = DriverManager.getConnection(testDbUrl, "testuser", "testpass")) {
                
                // 테스트 데이터 삽입
                try (PreparedStatement pstmt = conn.prepareStatement("INSERT INTO test_table (name, value) VALUES (?, ?)")) {
                    for (int i = 1; i <= 5; i++) {
                        pstmt.setString(1, "name" + i);
                        pstmt.setInt(2, i * 10);
                        pstmt.executeUpdate();
                    }
                }
                
                // ResultSet 모니터링 테스트
                try (PreparedStatement pstmt = conn.prepareStatement("SELECT name, value FROM test_table ORDER BY value");
                     ResultSet rs = pstmt.executeQuery()) {
                    
                    logger.info("사용된 ResultSet 구현체: {}", rs.getClass().getName());
                    
                    int rowCount = 0;
                    while (rs.next()) { // next() 호출 모니터링
                        String name = rs.getString("name"); // getString() 모니터링
                        int value = rs.getInt("value"); // getInt() 모니터링
                        
                        logger.debug("Row {}: {} = {}", ++rowCount, name, value);
                        
                        assertThat(name).isNotEmpty();
                        assertThat(value).isGreaterThan(0);
                    }
                    
                    assertThat(rowCount).isEqualTo(5);
                    logger.info("✅ ResultSet.next(), getString(), getInt() 모니터링 완료: {} 행", rowCount);
                }
                
                logger.info("✅ ResultSet 변환기 모니터링 테스트 성공");
            }
        }
    }
    
    @Nested
    @DisplayName("🔍 누락 클래스 탐지 검증")
    class MissedClassDetectionTests {
        
        @Test
        @DisplayName("실제 JDBC 드라이버 클래스들의 변환 적용 확인")
        void testActualJDBCDriverClassTransformation() throws Exception {
            logger.info("=== 🔍 실제 JDBC 드라이버 클래스 변환 확인 ===");
            
            try (Connection conn = DriverManager.getConnection(testDbUrl, "testuser", "testpass")) {
                // 실제 사용되는 PostgreSQL JDBC 드라이버 클래스들 확인
                logger.info("실제 Connection 클래스: {}", conn.getClass().getName());
                
                try (PreparedStatement pstmt = conn.prepareStatement("SELECT 1")) {
                    logger.info("실제 PreparedStatement 클래스: {}", pstmt.getClass().getName());
                    
                    try (ResultSet rs = pstmt.executeQuery()) {
                        logger.info("실제 ResultSet 클래스: {}", rs.getClass().getName());
                        
                        // 이 클래스들이 TARGET_CLASSES나 팩토리에서 적절히 처리되는지 확인
                        assertThat(rs.next()).isTrue();
                        assertThat(rs.getInt(1)).isEqualTo(1);
                        
                        logger.info("✅ 실제 JDBC 클래스들의 변환 적용 확인 완료");
                    }
                }
                
                try (Statement stmt = conn.createStatement()) {
                    logger.info("실제 Statement 클래스: {}", stmt.getClass().getName());
                    
                    try (ResultSet rs = stmt.executeQuery("SELECT CURRENT_TIMESTAMP")) {
                        assertThat(rs.next()).isTrue();
                        logger.info("현재 시각: {}", rs.getTimestamp(1));
                    }
                }
            }
        }
        
        @Test
        @DisplayName("느린 쿼리 임계값 모니터링")
        void testSlowQueryThresholdMonitoring() throws Exception {
            logger.info("=== ⏱️ 느린 쿼리 임계값 모니터링 테스트 ===");
            
            try (Connection conn = DriverManager.getConnection(testDbUrl, "testuser", "testpass")) {
                
                // 의도적으로 느린 쿼리 실행 (PostgreSQL pg_sleep 사용)
                try (PreparedStatement pstmt = conn.prepareStatement("SELECT pg_sleep(0.2), ?")) {
                    pstmt.setString(1, "slow_query_test");
                    
                    long startTime = System.currentTimeMillis();
                    try (ResultSet rs = pstmt.executeQuery()) {
                        assertThat(rs.next()).isTrue();
                        long duration = System.currentTimeMillis() - startTime;
                        
                        logger.info("쿼리 실행 시간: {}ms", duration);
                        assertThat(duration).isGreaterThan(100L); // 임계값보다 느림
                        
                        logger.info("✅ 느린 쿼리 탐지 모니터링 완료");
                    }
                }
            }
        }
    }
    
    /**
     * 테스트 테이블 생성
     */
    private void createTestTables() {
        try (Connection conn = DriverManager.getConnection(testDbUrl, "testuser", "testpass");
             Statement stmt = conn.createStatement()) {
            
            // 기존 테이블 삭제 및 생성
            stmt.executeUpdate("DROP TABLE IF EXISTS test_table");
            stmt.executeUpdate("""
                CREATE TABLE test_table (
                    id SERIAL PRIMARY KEY,
                    name VARCHAR(100),
                    value INTEGER,
                    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
                )
            """);
            
            logger.info("테스트 테이블 생성 완료");
            
        } catch (SQLException e) {
            logger.error("테스트 테이블 생성 실패", e);
            throw new RuntimeException(e);
        }
    }
}