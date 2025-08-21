package io.kubedb.monitor.agent;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 실제 프로덕션에서 발생하는 PostgreSQL Unknown Types value 에러 시나리오 테스트
 * 
 * 실제 University Registration 앱에서 발생한 에러:
 * SQL: select c1_0.course_id,c1_0.capacity,... from courses c1_0 where c1_0.semester_id=? and c1_0.is_active=true 
 *      and (? is null or c1_0.department_id=?) and (? is null or lower(c1_0.course_name) like lower(('%'||?||'%'))...
 * Error: org.postgresql.util.PSQLException: Unknown Types value.
 */
@Testcontainers
public class ProductionScenarioTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:13")
            .withDatabaseName("university_test")
            .withUsername("test")
            .withPassword("test");

    @Test
    @DisplayName("실제 프로덕션 시나리오: 선택적 검색 조건에서 NULL 파라미터 바인딩 - Unknown Types value 에러")
    void testProductionNullParameterBinding() throws SQLException {
        // 실제 University Registration에서 발생한 테이블 구조 재현
        try (Connection conn = DriverManager.getConnection(
                postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())) {
            
            // 테이블 생성
            conn.createStatement().execute("""
                CREATE TABLE courses (
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

            // 샘플 데이터 생성
            conn.createStatement().execute("""
                INSERT INTO courses (course_name, professor, credits, capacity, semester_id, department_id, is_active) 
                VALUES 
                ('프로그래밍 기초', '김교수', 3, 30, 1, 1, true),
                ('자료구조', '박교수', 3, 25, 1, 1, true),
                ('미적분학', '이교수', 3, 40, 1, 2, true)
            """);

            // 실제 Hibernate가 생성한 SQL 쿼리 재현
            String sql = """
                select c1_0.course_id,c1_0.capacity,c1_0.classroom,c1_0.course_name,c1_0.created_at,c1_0.credits,
                       c1_0.day_time,c1_0.department_id,c1_0.enrolled_count,c1_0.is_active,c1_0.popularity_level,
                       c1_0.prerequisite_course_id,c1_0.professor,c1_0.semester_id,c1_0.version 
                from courses c1_0 
                where c1_0.semester_id=? 
                  and c1_0.is_active=true 
                  and (? is null or c1_0.department_id=?) 
                  and (? is null or lower(c1_0.course_name) like lower(('%'||?||'%')) 
                       or lower(c1_0.professor) like lower(('%'||?||'%'))) 
                order by c1_0.course_id 
                offset ? rows fetch first ? rows only
                """;

            // 테스트 케이스 1: NULL 검색 조건으로 "Unknown Types value" 에러 재현
            try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setLong(1, 1L);           // semester_id = 1
                stmt.setObject(2, null);       // department filter - NULL (이부분에서 에러 발생)
                stmt.setObject(3, null);       // department_id - NULL 
                stmt.setObject(4, null);       // search keyword filter - NULL (이부분에서도 에러 발생)
                stmt.setObject(5, null);       // search keyword for course_name - NULL
                stmt.setObject(6, null);       // search keyword for professor - NULL
                stmt.setInt(7, 0);             // offset
                stmt.setInt(8, 10);            // limit

                // 이 실행에서 "Unknown Types value" 에러가 발생함
                assertThrows(SQLException.class, () -> {
                    try (ResultSet rs = stmt.executeQuery()) {
                        // 결과 처리
                    }
                }, "setObject(null) 호출시 PostgreSQL Unknown Types value 에러가 발생해야 함");
            }
        }
    }

    @Test
    @DisplayName("PostgreSQL 호환성 해결방안: setNull with explicit types")
    void testPostgreSQLCompatibleNullBinding() throws SQLException {
        try (Connection conn = DriverManager.getConnection(
                postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())) {
            
            // 동일한 테이블과 데이터 준비 (위와 같음)
            conn.createStatement().execute("""
                CREATE TABLE courses2 (
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

            conn.createStatement().execute("""
                INSERT INTO courses2 (course_name, professor, credits, capacity, semester_id, department_id, is_active) 
                VALUES 
                ('프로그래밍 기초', '김교수', 3, 30, 1, 1, true),
                ('자료구조', '박교수', 3, 25, 1, 1, true),
                ('미적분학', '이교수', 3, 40, 1, 2, true)
            """);

            String sql = """
                select c1_0.course_id,c1_0.capacity,c1_0.classroom,c1_0.course_name,c1_0.created_at,c1_0.credits,
                       c1_0.day_time,c1_0.department_id,c1_0.enrolled_count,c1_0.is_active,c1_0.popularity_level,
                       c1_0.prerequisite_course_id,c1_0.professor,c1_0.semester_id,c1_0.version 
                from courses2 c1_0 
                where c1_0.semester_id=? 
                  and c1_0.is_active=true 
                  and (? is null or c1_0.department_id=?) 
                  and (? is null or lower(c1_0.course_name) like lower(('%'||?||'%')) 
                       or lower(c1_0.professor) like lower(('%'||?||'%'))) 
                order by c1_0.course_id 
                offset ? rows fetch first ? rows only
                """;

            // 해결방안: explicit type으로 setNull 사용
            try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setLong(1, 1L);                    // semester_id = 1
                stmt.setNull(2, java.sql.Types.BIGINT); // department filter - 명시적 타입
                stmt.setNull(3, java.sql.Types.BIGINT); // department_id - 명시적 타입
                stmt.setNull(4, java.sql.Types.VARCHAR);// search keyword filter - 명시적 타입
                stmt.setNull(5, java.sql.Types.VARCHAR);// search keyword for course_name - 명시적 타입
                stmt.setNull(6, java.sql.Types.VARCHAR);// search keyword for professor - 명시적 타입
                stmt.setInt(7, 0);                      // offset
                stmt.setInt(8, 10);                     // limit

                // 이번에는 정상적으로 실행되어야 함
                try (ResultSet rs = stmt.executeQuery()) {
                    assertTrue(rs.next(), "쿼리 결과가 있어야 함");
                    
                    int resultCount = 0;
                    do {
                        assertNotNull(rs.getString("course_name"));
                        resultCount++;
                    } while (rs.next());
                    
                    assertEquals(3, resultCount, "모든 강의가 조회되어야 함");
                }
            }
        }
    }

    @Test 
    @DisplayName("Agent가 해결해야 하는 시나리오: PostgreSQLCompatibilityHelper를 통한 자동 변환")
    void testAgentShouldHandleScenario() throws SQLException {
        try (Connection conn = DriverManager.getConnection(
                postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())) {
            
            // PostgreSQL 호환성 헬퍼 인스턴스 생성
            PostgreSQLCompatibilityHelper helper = new PostgreSQLCompatibilityHelper(new AgentConfig.Builder().build());
            
            // 테이블 준비
            conn.createStatement().execute("""
                CREATE TABLE courses3 (
                    course_id BIGSERIAL PRIMARY KEY,
                    course_name VARCHAR(200) NOT NULL,
                    professor VARCHAR(100),
                    semester_id BIGINT,
                    department_id BIGINT,
                    is_active BOOLEAN DEFAULT true
                )
            """);

            conn.createStatement().execute("""
                INSERT INTO courses3 (course_name, professor, semester_id, department_id, is_active) 
                VALUES 
                ('프로그래밍 기초', '김교수', 1, 1, true),
                ('자료구조', '박교수', 1, 1, true)
            """);

            // Agent가 변환해야 하는 시나리오
            String sql = "SELECT * FROM courses3 WHERE semester_id = ? AND (? IS NULL OR department_id = ?) AND (? IS NULL OR course_name LIKE ?)";
            
            try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                // PostgreSQLCompatibilityHelper를 사용한 안전한 파라미터 바인딩
                helper.setParameterSafely(stmt, 1, 1L);      // semester_id
                helper.setParameterSafely(stmt, 2, null);    // department filter - Agent가 자동으로 적절한 타입으로 변환
                helper.setParameterSafely(stmt, 3, null);    // department_id  
                helper.setParameterSafely(stmt, 4, null);    // search filter
                helper.setParameterSafely(stmt, 5, null);    // search keyword

                // 정상 실행되어야 함
                try (ResultSet rs = stmt.executeQuery()) {
                    assertTrue(rs.next(), "Agent 변환으로 쿼리가 성공해야 함");
                    assertEquals("프로그래밍 기초", rs.getString("course_name"));
                }
            }
        }
    }

    @Test
    @DisplayName("현장 재현: Spring Data JPA Repository 메서드에서 발생하는 NULL 바인딩 패턴")
    void testSpringDataRepositoryPattern() throws SQLException {
        try (Connection conn = DriverManager.getConnection(
                postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())) {
            
            // University Registration의 CourseService.searchCourses() 메서드와 동일한 패턴
            conn.createStatement().execute("""
                CREATE TABLE courses4 (
                    course_id BIGSERIAL PRIMARY KEY,
                    course_name VARCHAR(200) NOT NULL,
                    professor VARCHAR(100),
                    semester_id BIGINT,
                    department_id BIGINT,
                    is_active BOOLEAN DEFAULT true
                )
            """);

            conn.createStatement().execute("""
                INSERT INTO courses4 (course_name, professor, semester_id, department_id, is_active) 
                VALUES 
                ('컴퓨터과학개론', '홍교수', 1, 1, true),
                ('데이터베이스설계', '김교수', 1, 1, true),
                ('선형대수', '박교수', 1, 2, true)
            """);

            // Spring Data JPA가 생성하는 동적 검색 쿼리 패턴
            String dynamicSearchSql = """
                SELECT c.course_id, c.course_name, c.professor, c.department_id
                FROM courses4 c 
                WHERE c.semester_id = ?
                  AND c.is_active = true
                  AND (?1 IS NULL OR c.department_id = ?1)  -- 부서 필터 (선택적)
                  AND (?2 IS NULL OR 
                       LOWER(c.course_name) LIKE LOWER(CONCAT('%', ?2, '%')) OR 
                       LOWER(c.professor) LIKE LOWER(CONCAT('%', ?2, '%')))  -- 검색어 필터 (선택적)
                ORDER BY c.course_id
                LIMIT ? OFFSET ?
                """;

            // 현장에서 흔한 시나리오들
            
            // 시나리오 1: 모든 필터가 NULL (전체 조회)
            String sql1 = "SELECT * FROM courses4 WHERE semester_id = ? AND (? IS NULL OR department_id = ?) AND (? IS NULL OR course_name ILIKE ?) LIMIT ? OFFSET ?";
            try (PreparedStatement stmt = conn.prepareStatement(sql1)) {
                stmt.setLong(1, 1L);       // semester_id
                stmt.setObject(2, null);   // NULL 검사를 위한 파라미터 - 여기서 에러 발생
                stmt.setObject(3, null);   // department_id 
                stmt.setObject(4, null);   // search keyword 검사
                stmt.setObject(5, null);   // search keyword 값
                stmt.setInt(6, 10);        // limit
                stmt.setInt(7, 0);         // offset

                // Unknown Types value 에러 발생 예상
                SQLException exception = assertThrows(SQLException.class, stmt::executeQuery);
                assertTrue(exception.getMessage().contains("Unknown Types value") || 
                          exception.getMessage().contains("can't infer the SQL type"),
                          "PostgreSQL 타입 추론 실패 에러가 발생해야 함");
            }

            // 시나리오 2: PostgreSQLCompatibilityHelper 적용 후 해결
            PostgreSQLCompatibilityHelper helper = new PostgreSQLCompatibilityHelper(new AgentConfig.Builder().build());
            try (PreparedStatement stmt = conn.prepareStatement(sql1)) {
                helper.setParameterSafely(stmt, 1, 1L);      // semester_id
                helper.setParameterSafely(stmt, 2, null);    // NULL 검사 - 자동 타입 결정
                helper.setParameterSafely(stmt, 3, null);    // department_id - BIGINT로 자동 변환
                helper.setParameterSafely(stmt, 4, null);    // search keyword 검사 - VARCHAR로 자동 변환
                helper.setParameterSafely(stmt, 5, null);    // search keyword 값 - VARCHAR로 자동 변환
                stmt.setInt(6, 10);                          // limit
                stmt.setInt(7, 0);                           // offset

                try (ResultSet rs = stmt.executeQuery()) {
                    assertTrue(rs.next(), "PostgreSQL 호환성 헬퍼로 성공적으로 조회되어야 함");
                    
                    int count = 0;
                    do {
                        count++;
                        assertNotNull(rs.getString("course_name"));
                    } while (rs.next());
                    
                    assertEquals(3, count, "모든 강의가 조회되어야 함");
                }
            }
        }
    }
}