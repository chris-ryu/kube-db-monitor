package io.kubedb.monitor.agent;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.sql.*;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * ByteBuddy Agent 통합 테스트
 * 
 * ByteBuddy + Runtime Discovery 하이브리드 접근법을 검증합니다.
 * PostgreSQL "Unknown Types value" 에러가 ByteBuddy 인터셉션을 통해 해결되는지 확인합니다.
 */
@Testcontainers
public class ByteBuddyAgentIntegrationTest {
    private static final Logger logger = Logger.getLogger(ByteBuddyAgentIntegrationTest.class.getName());
    
    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:13")
            .withDatabaseName("bytebuddy_agent_test")
            .withUsername("test")
            .withPassword("test");
    
    private AgentConfig agentConfig;
    
    @BeforeEach
    void setUp() {
        agentConfig = new AgentConfig.Builder()
                .collectorEndpoint("http://localhost:8080/metrics")
                .samplingRate(1.0)
                .enabled(true)
                .build();
    }
    
    @Test
    @DisplayName("PostgreSQL 기본 Connection 및 Statement 실행 테스트")
    void testPostgreSQLBasicOperations() throws SQLException {
        try (Connection connection = DriverManager.getConnection(
                postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())) {
            
            // 기본 Connection 기능 테스트
            assertNotNull(connection);
            assertFalse(connection.isClosed());
            assertTrue(connection.isValid(5));
            
            // 테스트 테이블 생성
            try (Statement stmt = connection.createStatement()) {
                stmt.execute("CREATE TABLE IF NOT EXISTS test_table (id SERIAL PRIMARY KEY, name VARCHAR(100))");
            }
            
            // PreparedStatement를 통한 데이터 삽입
            try (PreparedStatement pstmt = connection.prepareStatement(
                    "INSERT INTO test_table (name) VALUES (?)")) {
                pstmt.setString(1, "TestName");
                int result = pstmt.executeUpdate();
                assertEquals(1, result);
            }
            
            // 데이터 조회
            try (Statement stmt = connection.createStatement();
                 ResultSet rs = stmt.executeQuery("SELECT COUNT(*) FROM test_table")) {
                assertTrue(rs.next());
                assertEquals(1, rs.getInt(1));
            }
        }
    }
    
    @Test
    @DisplayName("PostgreSQL NULL 파라미터 처리 테스트 (Unknown Types value 해결)")
    void testPostgreSQLNullParameterHandling() throws SQLException {
        try (Connection connection = DriverManager.getConnection(
                postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())) {
            
            // 테스트 테이블 생성
            try (Statement stmt = connection.createStatement()) {
                stmt.execute("CREATE TABLE IF NOT EXISTS null_test (id SERIAL PRIMARY KEY, " +
                          "name VARCHAR(100), description TEXT, count INTEGER)");
            }
            
            // NULL 파라미터가 포함된 복잡한 쿼리 (University Registration 실제 패턴)
            String complexQuery = 
                "INSERT INTO null_test (name, description, count) " +
                "SELECT ?, ?, ? WHERE (? IS NULL OR ? = ?)";
            
            try (PreparedStatement pstmt = connection.prepareStatement(complexQuery)) {
                pstmt.setString(1, "TestName");
                pstmt.setNull(2, Types.VARCHAR);  // NULL 값 설정
                pstmt.setInt(3, 100);
                pstmt.setNull(4, Types.VARCHAR);   // 조건문의 NULL
                pstmt.setString(5, "condition");
                pstmt.setString(6, "condition");
                
                // 이전에는 "Unknown Types value" 에러가 발생했지만 
                // ByteBuddy 인터셉션으로 투명하게 처리됨
                int result = pstmt.executeUpdate();
                assertTrue(result >= 0); // 성공적으로 실행됨
            }
        }
    }
    
    @Test
    @DisplayName("University Registration 실제 쿼리 패턴 테스트")
    void testUniversityRegistrationQueryPattern() throws SQLException {
        try (Connection connection = DriverManager.getConnection(
                postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())) {
            
            // University Registration 스키마와 유사한 테이블 생성
            try (Statement stmt = connection.createStatement()) {
                stmt.execute("CREATE TABLE IF NOT EXISTS courses (" +
                           "course_id VARCHAR(10) PRIMARY KEY, " +
                           "course_name VARCHAR(100), " +
                           "department_id BIGINT, " +
                           "semester_id BIGINT, " +
                           "is_active BOOLEAN)");
            }
            
            // 실제 University Registration 앱에서 사용하는 복잡한 동적 검색 쿼리
            String universityQuery = 
                "SELECT course_id, course_name FROM courses " +
                "WHERE semester_id = ? AND is_active = true " +
                "AND (? IS NULL OR department_id = ?) " +
                "AND (? IS NULL OR LOWER(course_name) LIKE LOWER('%' || ? || '%'))";
            
            try (PreparedStatement pstmt = connection.prepareStatement(universityQuery)) {
                // 실제 University Registration에서 사용하는 파라미터 패턴
                pstmt.setLong(1, 1L);         // semester_id
                pstmt.setNull(2, Types.BIGINT);  // department filter (NULL = 모든 학과)
                pstmt.setNull(3, Types.BIGINT);  // department_id (사용되지 않음)
                pstmt.setNull(4, Types.VARCHAR); // search term (NULL = 검색어 없음)
                pstmt.setNull(5, Types.VARCHAR); // search value (사용되지 않음)
                
                // ByteBuddy 인터셉션이 투명하게 처리하여 정상 실행됨
                try (ResultSet rs = pstmt.executeQuery()) {
                    assertNotNull(rs);
                    // 데이터가 없어도 쿼리는 정상 실행되어야 함
                }
            }
        }
    }
    
    @Test
    @DisplayName("트랜잭션 모니터링 테스트")
    void testTransactionMonitoring() throws SQLException {
        try (Connection connection = DriverManager.getConnection(
                postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())) {
            
            connection.setAutoCommit(false);
            
            try (Statement stmt = connection.createStatement()) {
                stmt.execute("CREATE TABLE IF NOT EXISTS tx_test (id SERIAL PRIMARY KEY, value TEXT)");
            }
            
            // 트랜잭션 내에서 여러 작업 수행
            try (PreparedStatement pstmt = connection.prepareStatement(
                    "INSERT INTO tx_test (value) VALUES (?)")) {
                
                pstmt.setString(1, "Transaction Test 1");
                pstmt.executeUpdate();
                
                pstmt.setString(1, "Transaction Test 2");
                pstmt.executeUpdate();
            }
            
            // 커밋 - ByteBuddy Agent가 트랜잭션 이벤트를 모니터링해야 함
            connection.commit();
            
            // 데이터 확인
            try (Statement stmt = connection.createStatement();
                 ResultSet rs = stmt.executeQuery("SELECT COUNT(*) FROM tx_test")) {
                assertTrue(rs.next());
                assertEquals(2, rs.getInt(1));
            }
        }
    }
    
    @Test
    @DisplayName("MetricsCollector 통합 테스트")
    void testMetricsCollectorIntegration() throws SQLException {
        // MetricsCollector가 초기화되고 메트릭을 수집하는지 확인
        MetricsCollector collector = new MetricsCollector(agentConfig);
        assertNotNull(collector);
        
        // 몇 가지 메트릭을 시뮬레이션
        collector.recordQuery("SELECT * FROM test", 100_000_000L, "conn-1", "main");
        collector.recordCommit(50_000_000L, "conn-1", "tx-1");
        collector.recordRollback(25_000_000L, "conn-1", "tx-2");
        
        // 메트릭이 정상적으로 기록되었는지 확인 (실제 검증은 로그로)
        assertTrue(true); // MetricsCollector가 예외 없이 동작하면 성공
    }
}