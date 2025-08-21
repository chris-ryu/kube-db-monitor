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
 * Connection 프록시 패턴 통합 테스트
 * 
 * ASM 바이트코드 변환의 한계를 극복하기 위한 Connection 프록시 패턴을 검증합니다.
 * PostgreSQL "Unknown Types value" 에러가 Connection 프록시를 통해 해결되는지 확인합니다.
 */
@Testcontainers
public class ConnectionProxyIntegrationTest {
    private static final Logger logger = Logger.getLogger(ConnectionProxyIntegrationTest.class.getName());
    
    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:13")
            .withDatabaseName("connection_proxy_test")
            .withUsername("test")
            .withPassword("test");
    
    private AgentConfig agentConfig;
    
    @BeforeEach
    void setUp() {
        agentConfig = new AgentConfig.Builder()
                .collectorEndpoint("http://localhost:8080/metrics")
                .samplingRate(1.0)
                .postgresqlFixUnknownTypesValue(true)  // PostgreSQL 호환성 활성화
                .build();
    }
    
    @Test
    @DisplayName("PostgreSQL Connection 프록시 생성 및 기본 기능 테스트")
    void testPostgreSQLConnectionProxyCreation() throws SQLException {
        try (Connection directConnection = DriverManager.getConnection(
                postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())) {
            
            // DatabaseProxyFactory를 통해 프록시 생성
            Connection proxiedConnection = DatabaseProxyFactory.createConnectionProxy(
                    directConnection, postgres.getJdbcUrl(), agentConfig);
            
            // 프록시가 올바르게 생성되었는지 확인
            assertNotNull(proxiedConnection);
            assertTrue(proxiedConnection instanceof PostgreSQLConnectionProxy);
            
            // 기본 Connection 기능 테스트
            assertFalse(proxiedConnection.isClosed());
            assertTrue(proxiedConnection.isValid(5));
            
            // DatabaseMetaData 접근 테스트
            DatabaseMetaData metaData = proxiedConnection.getMetaData();
            assertEquals("PostgreSQL", metaData.getDatabaseProductName());
        }
    }
    
    @Test
    @DisplayName("PostgreSQL PreparedStatement 프록시를 통한 Unknown Types value 에러 해결")
    void testPostgreSQLPreparedStatementProxyNullHandling() throws SQLException {
        try (Connection directConnection = DriverManager.getConnection(
                postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())) {
            
            // 테스트 테이블 생성
            directConnection.createStatement().execute("""
                CREATE TABLE IF NOT EXISTS connection_proxy_test (
                    id BIGSERIAL PRIMARY KEY,
                    name VARCHAR(100),
                    department_id BIGINT,
                    active BOOLEAN DEFAULT true
                )
            """);
            
            // 샘플 데이터 삽입
            directConnection.createStatement().execute("""
                INSERT INTO connection_proxy_test (name, department_id, active) VALUES
                ('테스트1', 1, true),
                ('테스트2', 2, true),
                ('테스트3', 1, false)
            """);
            
            // Connection 프록시 생성
            Connection proxiedConnection = DatabaseProxyFactory.createConnectionProxy(
                    directConnection, postgres.getJdbcUrl(), agentConfig);
            
            // 문제가 되는 쿼리 패턴 - NULL 파라미터가 포함된 동적 검색 쿼리
            String problematicSql = """
                SELECT id, name, department_id, active 
                FROM connection_proxy_test 
                WHERE active = true
                  AND (? IS NULL OR department_id = ?)
                  AND (? IS NULL OR name LIKE ('%' || ? || '%'))
                ORDER BY id
            """;
            
            try (PreparedStatement stmt = proxiedConnection.prepareStatement(problematicSql)) {
                // 프록시된 PreparedStatement인지 확인
                assertTrue(stmt instanceof PostgreSQLPreparedStatementProxy);
                
                // NULL 파라미터 바인딩 - 이전에는 "Unknown Types value" 에러 발생
                stmt.setObject(1, null);    // department filter check
                stmt.setObject(2, null);    // department_id value  
                stmt.setObject(3, null);    // name filter check
                stmt.setObject(4, null);    // name search value
                
                // 쿼리 실행이 성공해야 함 (에러가 발생하지 않아야 함)
                try (ResultSet rs = stmt.executeQuery()) {
                    assertTrue(rs.next(), "결과가 있어야 함");
                    
                    int count = 0;
                    do {
                        count++;
                        assertNotNull(rs.getString("name"));
                        logger.info("검색된 결과: " + rs.getString("name"));
                    } while (rs.next());
                    
                    assertEquals(2, count, "active=true인 레코드 2개가 조회되어야 함");
                }
            }
        }
    }
    
    @Test
    @DisplayName("실제 University Registration 앱 쿼리 패턴으로 프록시 테스트")
    void testRealUniversityQueryPatternWithProxy() throws SQLException {
        try (Connection directConnection = DriverManager.getConnection(
                postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())) {
            
            // University Registration과 동일한 테이블 구조 생성
            directConnection.createStatement().execute("""
                CREATE TABLE IF NOT EXISTS courses_proxy_test (
                    course_id BIGSERIAL PRIMARY KEY,
                    course_name VARCHAR(200) NOT NULL,
                    professor VARCHAR(100),
                    credits INTEGER NOT NULL,
                    capacity INTEGER NOT NULL,
                    enrolled_count INTEGER DEFAULT 0,
                    semester_id BIGINT,
                    department_id BIGINT,
                    is_active BOOLEAN DEFAULT true,
                    classroom VARCHAR(50),
                    day_time VARCHAR(50),
                    popularity_level INTEGER DEFAULT 0,
                    prerequisite_course_id BIGINT,
                    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                    version BIGINT DEFAULT 0
                )
            """);
            
            // 샘플 데이터 삽입
            directConnection.createStatement().execute("""
                INSERT INTO courses_proxy_test (course_name, professor, credits, capacity, semester_id, department_id, is_active) VALUES
                ('프로그래밍 기초', '김교수', 3, 30, 1, 1, true),
                ('자료구조', '박교수', 3, 25, 1, 1, true),
                ('미적분학', '이교수', 3, 40, 1, 2, true),
                ('통계학', '최교수', 3, 35, 1, 2, true)
            """);
            
            // Connection 프록시 생성
            Connection proxiedConnection = DatabaseProxyFactory.createConnectionProxy(
                    directConnection, postgres.getJdbcUrl(), agentConfig);
            
            // 실제 Hibernate가 생성하는 복잡한 쿼리 패턴
            String hibernateGeneratedSql = """
                select c1_0.course_id,c1_0.capacity,c1_0.classroom,c1_0.course_name,c1_0.created_at,c1_0.credits,
                       c1_0.day_time,c1_0.department_id,c1_0.enrolled_count,c1_0.is_active,c1_0.popularity_level,
                       c1_0.prerequisite_course_id,c1_0.professor,c1_0.semester_id,c1_0.version 
                from courses_proxy_test c1_0 
                where c1_0.semester_id=? 
                  and c1_0.is_active=true 
                  and (? is null or c1_0.department_id=?) 
                  and (? is null or lower(c1_0.course_name) like lower(('%'||?||'%')) 
                       or lower(c1_0.professor) like lower(('%'||?||'%'))) 
                order by c1_0.course_id 
                offset ? rows fetch first ? rows only
            """;
            
            try (PreparedStatement stmt = proxiedConnection.prepareStatement(hibernateGeneratedSql)) {
                // 프록시된 PreparedStatement인지 확인
                assertTrue(stmt instanceof PostgreSQLPreparedStatementProxy);
                
                // 시나리오 1: 모든 검색 필터가 NULL (전체 조회)
                stmt.setLong(1, 1L);           // semester_id = 1
                stmt.setObject(2, null);       // department filter check - 프록시가 안전하게 처리
                stmt.setObject(3, null);       // department_id value
                stmt.setObject(4, null);       // search keyword filter check - 프록시가 안전하게 처리  
                stmt.setObject(5, null);       // search keyword for course_name
                stmt.setObject(6, null);       // search keyword for professor
                stmt.setInt(7, 0);             // offset
                stmt.setInt(8, 10);            // limit
                
                try (ResultSet rs = stmt.executeQuery()) {
                    assertTrue(rs.next(), "결과가 있어야 함");
                    
                    int count = 0;
                    do {
                        count++;
                        String courseName = rs.getString("course_name");
                        String professor = rs.getString("professor");
                        assertNotNull(courseName);
                        logger.info(String.format("강의: %s (교수: %s)", courseName, professor));
                    } while (rs.next());
                    
                    assertEquals(4, count, "모든 강의가 조회되어야 함");
                }
                
                // 시나리오 2: 부서 필터만 적용
                stmt.setLong(1, 1L);           // semester_id = 1
                stmt.setObject(2, 1L);         // department filter check - NOT NULL
                stmt.setLong(3, 1L);           // department_id = 1 (컴퓨터과학과)
                stmt.setObject(4, null);       // search keyword filter check - NULL
                stmt.setObject(5, null);       // search keyword for course_name
                stmt.setObject(6, null);       // search keyword for professor
                stmt.setInt(7, 0);             // offset
                stmt.setInt(8, 10);            // limit
                
                try (ResultSet rs = stmt.executeQuery()) {
                    assertTrue(rs.next(), "결과가 있어야 함");
                    
                    int count = 0;
                    do {
                        count++;
                        assertEquals(1L, rs.getLong("department_id"), "부서 ID가 1이어야 함");
                    } while (rs.next());
                    
                    assertEquals(2, count, "컴퓨터과학과 강의 2개가 조회되어야 함");
                }
            }
        }
    }
    
    @Test
    @DisplayName("CallableStatement 프록시 테스트")
    void testPostgreSQLCallableStatementProxy() throws SQLException {
        try (Connection directConnection = DriverManager.getConnection(
                postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())) {
            
            // 간단한 PostgreSQL 함수 생성 (CallableStatement 테스트용)
            directConnection.createStatement().execute("""
                CREATE OR REPLACE FUNCTION test_function(input_text TEXT DEFAULT NULL) 
                RETURNS TEXT AS $$
                BEGIN
                    IF input_text IS NULL THEN
                        RETURN 'NULL 입력값 처리됨';
                    ELSE
                        RETURN ' 입력값: ' || input_text;
                    END IF;
                END;
                $$ LANGUAGE plpgsql;
            """);
            
            // Connection 프록시 생성
            Connection proxiedConnection = DatabaseProxyFactory.createConnectionProxy(
                    directConnection, postgres.getJdbcUrl(), agentConfig);
            
            try (CallableStatement stmt = proxiedConnection.prepareCall("{? = call test_function(?)}")) {
                // 프록시된 CallableStatement인지 확인
                assertTrue(stmt instanceof PostgreSQLCallableStatementProxy);
                
                // OUT 파라미터 등록
                stmt.registerOutParameter(1, Types.VARCHAR);
                
                // NULL 파라미터 테스트 - 프록시가 안전하게 처리해야 함
                stmt.setObject(2, null);
                
                stmt.execute();
                
                String result = stmt.getString(1);
                assertNotNull(result);
                assertEquals("NULL 입력값 처리됨", result);
                
                logger.info("CallableStatement 결과: " + result);
            }
        }
    }
    
    @Test
    @DisplayName("DatabaseProxyFactory 타입 감지 테스트")
    void testDatabaseProxyFactoryTypeDetection() {
        // PostgreSQL URL 감지 테스트
        assertTrue(DatabaseProxyFactory.isProxySupported(postgres.getJdbcUrl()));
        
        // 다른 데이터베이스 URL 테스트
        assertFalse(DatabaseProxyFactory.isProxySupported("jdbc:mysql://localhost/test"));
        assertFalse(DatabaseProxyFactory.isProxySupported("jdbc:oracle:thin:@localhost:1521:xe"));
        assertFalse(DatabaseProxyFactory.isProxySupported("jdbc:h2:mem:test"));
        
        // 데이터베이스 타입별 호환성 정보 확인
        String pgInfo = DatabaseProxyFactory.getCompatibilityInfo(
                DatabaseProxyFactory.DatabaseType.POSTGRESQL);
        assertTrue(pgInfo.contains("setObject(null)"));
        assertTrue(pgInfo.contains("autoCommit"));
    }
}