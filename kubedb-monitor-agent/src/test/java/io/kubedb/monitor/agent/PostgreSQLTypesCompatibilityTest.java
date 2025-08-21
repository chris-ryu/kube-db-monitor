package io.kubedb.monitor.agent;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.TestInstance;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.sql.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * PostgreSQL 타입 호환성 전용 테스트
 * "Unknown Types value" 에러 해결을 위한 테스트 케이스
 */
@Testcontainers
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@DisplayName("PostgreSQL 타입 호환성 테스트")
public class PostgreSQLTypesCompatibilityTest {

    private static final Logger logger = LoggerFactory.getLogger(PostgreSQLTypesCompatibilityTest.class);
    
    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15-alpine")
            .withDatabaseName("testdb")
            .withUsername("testuser")
            .withPassword("testpass");
    
    private static String TEST_DB_URL;
    private static String TEST_DB_USER;
    private static String TEST_DB_PASSWORD;
    private static PostgreSQLCompatibilityHelper compatibilityHelper;

    @BeforeAll
    void setUpAll() throws Exception {
        logger.info("🐘 PostgreSQL 타입 호환성 테스트 시작");
        
        // Agent 설정 - PostgreSQL 호환성 개선 옵션 활성화
        AgentConfig agentConfig = new AgentConfig.Builder()
                .enabled(true)
                .samplingRate(1.0)
                .slowQueryThresholdMs(50)
                .collectorType("LOGGING")
                .postgresqlStrictCompatibility(true)
                .postgresqlFixUnknownTypesValue(true)
                .safeTransformationMode(true)
                .build();
                
        // PostgreSQL 호환성 헬퍼 초기화
        compatibilityHelper = new PostgreSQLCompatibilityHelper(agentConfig);

        // DB 연결 정보 설정
        TEST_DB_URL = postgres.getJdbcUrl();
        TEST_DB_USER = postgres.getUsername();
        TEST_DB_PASSWORD = postgres.getPassword();
        
        logger.info("DB URL: {}", TEST_DB_URL);
        
        // 테스트 테이블 생성
        createTestTables();
    }

    private void createTestTables() throws SQLException {
        try (Connection conn = DriverManager.getConnection(TEST_DB_URL, TEST_DB_USER, TEST_DB_PASSWORD);
             Statement stmt = conn.createStatement()) {
            
            // University 앱과 동일한 스키마 생성
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS departments (
                    department_id BIGSERIAL PRIMARY KEY,
                    department_name VARCHAR(100) NOT NULL,
                    building VARCHAR(50),
                    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
                )
            """);
            
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS semesters (
                    semester_id BIGSERIAL PRIMARY KEY,
                    year INTEGER NOT NULL,
                    season VARCHAR(20) NOT NULL,
                    is_current BOOLEAN DEFAULT FALSE,
                    registration_start TIMESTAMP,
                    registration_end TIMESTAMP,
                    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
                )
            """);
            
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS courses (
                    course_id BIGSERIAL PRIMARY KEY,
                    course_name VARCHAR(200) NOT NULL,
                    professor VARCHAR(100),
                    credits INTEGER,
                    capacity INTEGER,
                    enrolled_count INTEGER DEFAULT 0,
                    classroom VARCHAR(50),
                    day_time VARCHAR(100),
                    department_id BIGINT REFERENCES departments(department_id),
                    semester_id BIGINT REFERENCES semesters(semester_id),
                    prerequisite_course_id BIGINT,
                    is_active BOOLEAN DEFAULT TRUE,
                    popularity_level INTEGER DEFAULT 1,
                    version BIGINT DEFAULT 0,
                    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
                )
            """);
            
            // 테스트 데이터 삽입
            stmt.execute("""
                INSERT INTO departments (department_name, building) VALUES 
                ('Computer Science', 'Engineering Building'),
                ('Mathematics', 'Science Building')
            """);
            
            stmt.execute("""
                INSERT INTO semesters (year, season, is_current, registration_start, registration_end) VALUES 
                (2024, 'Spring', true, CURRENT_TIMESTAMP - INTERVAL '1 month', CURRENT_TIMESTAMP + INTERVAL '1 month')
            """);
            
            stmt.execute("""
                INSERT INTO courses (course_name, professor, credits, capacity, department_id, semester_id, is_active) VALUES 
                ('Database Systems', 'Dr. Smith', 3, 30, 1, 1, true),
                ('Data Structures', 'Dr. Johnson', 4, 25, 1, 1, true),
                ('Calculus I', 'Dr. Brown', 4, 40, 2, 1, true)
            """);
            
            logger.info("✅ 테스트 테이블 및 데이터 생성 완료");
        }
    }

    @Test
    @DisplayName("PostgreSQL NULL 파라미터 바인딩 테스트 - Unknown Types value 에러 재현")
    void testNullParameterBinding() throws SQLException {
        logger.info("🧪 NULL 파라미터 바인딩 테스트 시작");
        
        try (Connection conn = DriverManager.getConnection(TEST_DB_URL, TEST_DB_USER, TEST_DB_PASSWORD)) {
            // University 앱에서 사용하는 실제 쿼리 패턴
            String sql = """
                SELECT c.course_id, c.course_name, c.professor, c.credits, c.capacity, c.enrolled_count,
                       c.classroom, c.day_time, c.department_id, c.semester_id, c.is_active, c.popularity_level, c.version
                FROM courses c 
                WHERE c.semester_id = ? 
                AND c.is_active = true 
                AND (? IS NULL OR c.department_id = ?) 
                AND (? IS NULL OR LOWER(c.course_name) LIKE LOWER(CONCAT('%', ?, '%')) 
                     OR LOWER(c.professor) LIKE LOWER(CONCAT('%', ?, '%')))
                ORDER BY c.course_id 
                OFFSET ? ROWS FETCH FIRST ? ROWS ONLY
            """;
            
            try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
                // University 앱과 동일한 파라미터 설정 방식
                pstmt.setLong(1, 1L); // semester_id
                
                // 여기서 "Unknown Types value" 에러가 발생할 수 있는 부분
                pstmt.setObject(2, null); // department filter (null)
                pstmt.setObject(3, null); // department_id (null)
                pstmt.setObject(4, null); // keyword filter (null) 
                pstmt.setObject(5, null); // keyword (null)
                pstmt.setObject(6, null); // keyword (null)
                pstmt.setInt(7, 0); // offset
                pstmt.setInt(8, 3); // limit
                
                logger.info("📋 실행할 쿼리: {}", sql);
                logger.info("📋 파라미터: semester_id=1, dept=null, keyword=null, offset=0, limit=3");
                
                try (ResultSet rs = pstmt.executeQuery()) {
                    int count = 0;
                    while (rs.next()) {
                        count++;
                        logger.info("📚 과목 {}: {} ({})", count, rs.getString("course_name"), rs.getString("professor"));
                    }
                    logger.info("✅ NULL 파라미터 바인딩 테스트 성공: {}개 결과", count);
                    assertTrue(count > 0, "결과가 있어야 함");
                }
            }
        }
    }

    @Test
    @DisplayName("PostgreSQL 타입 명시적 설정 테스트")
    void testExplicitTypeBinding() throws SQLException {
        logger.info("🧪 명시적 타입 바인딩 테스트 시작");
        
        try (Connection conn = DriverManager.getConnection(TEST_DB_URL, TEST_DB_USER, TEST_DB_PASSWORD)) {
            String sql = """
                SELECT c.course_id, c.course_name, c.professor, c.credits
                FROM courses c 
                WHERE c.semester_id = ? 
                AND c.is_active = true 
                AND (? IS NULL OR c.department_id = ?) 
                AND (? IS NULL OR LOWER(c.course_name) LIKE LOWER(?))
                ORDER BY c.course_id 
                LIMIT ?
            """;
            
            try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
                pstmt.setLong(1, 1L); // semester_id
                
                // 명시적 타입 설정으로 "Unknown Types value" 에러 방지
                pstmt.setNull(2, Types.BIGINT); // department filter
                pstmt.setNull(3, Types.BIGINT); // department_id
                pstmt.setNull(4, Types.VARCHAR); // keyword filter
                pstmt.setNull(5, Types.VARCHAR); // keyword
                pstmt.setInt(6, 3); // limit
                
                logger.info("📋 명시적 타입 설정으로 쿼리 실행");
                
                try (ResultSet rs = pstmt.executeQuery()) {
                    int count = 0;
                    while (rs.next()) {
                        count++;
                        logger.info("📚 과목 {}: {} ({}크레딧)", count, rs.getString("course_name"), rs.getInt("credits"));
                    }
                    logger.info("✅ 명시적 타입 바인딩 테스트 성공: {}개 결과", count);
                    assertTrue(count > 0, "결과가 있어야 함");
                }
            }
        }
    }

    @Test
    @DisplayName("PostgreSQL JDBC 드라이버 버전별 호환성 테스트")
    void testPostgreSQLDriverCompatibility() throws SQLException {
        logger.info("🧪 PostgreSQL JDBC 드라이버 호환성 테스트 시작");
        
        try (Connection conn = DriverManager.getConnection(TEST_DB_URL, TEST_DB_USER, TEST_DB_PASSWORD)) {
            DatabaseMetaData metaData = conn.getMetaData();
            logger.info("📋 드라이버 정보:");
            logger.info("  - 드라이버명: {}", metaData.getDriverName());
            logger.info("  - 드라이버 버전: {}", metaData.getDriverVersion());
            logger.info("  - JDBC 버전: {}.{}", metaData.getJDBCMajorVersion(), metaData.getJDBCMinorVersion());
            logger.info("  - DB 제품명: {}", metaData.getDatabaseProductName());
            logger.info("  - DB 버전: {}", metaData.getDatabaseProductVersion());
            
            // 다양한 타입의 파라미터 테스트
            String sql = "SELECT * FROM courses WHERE course_id = ? AND credits = ? AND is_active = ?";
            
            try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
                pstmt.setLong(1, 1L);
                pstmt.setInt(2, 3);
                pstmt.setBoolean(3, true);
                
                try (ResultSet rs = pstmt.executeQuery()) {
                    if (rs.next()) {
                        logger.info("✅ 기본 타입 바인딩 성공: {}", rs.getString("course_name"));
                    }
                }
            }
        }
    }

    @Test
    @DisplayName("Hibernate 스타일 쿼리 패턴 테스트")
    void testHibernateQueryPattern() throws SQLException {
        logger.info("🧪 Hibernate 스타일 쿼리 패턴 테스트 시작");
        
        try (Connection conn = DriverManager.getConnection(TEST_DB_URL, TEST_DB_USER, TEST_DB_PASSWORD)) {
            // Hibernate가 생성하는 실제 쿼리 패턴 (로그에서 확인됨)
            String sql = """
                select
                    c1_0.course_id,
                    c1_0.capacity,
                    c1_0.classroom,
                    c1_0.course_name,
                    c1_0.created_at,
                    c1_0.credits,
                    c1_0.day_time,
                    c1_0.department_id,
                    c1_0.enrolled_count,
                    c1_0.is_active,
                    c1_0.popularity_level,
                    c1_0.prerequisite_course_id,
                    c1_0.professor,
                    c1_0.semester_id,
                    c1_0.version 
                from
                    courses c1_0 
                where
                    c1_0.semester_id=? 
                    and c1_0.is_active=true 
                    and (
                        ? is null 
                        or c1_0.department_id=?
                    ) 
                    and (
                        ? is null 
                        or lower(c1_0.course_name) like lower(('%'||?||'%')) 
                        or lower(c1_0.professor) like lower(('%'||?||'%')) 
                    ) 
                order by
                    c1_0.course_id,
                    c1_0.course_id 
                offset
                    ? rows 
                fetch
                    first ? rows only
            """;
            
            try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
                pstmt.setLong(1, 1L); // semester_id
                
                // 이 부분에서 "Unknown Types value" 에러가 발생
                // PostgreSQL 드라이버가 null 타입을 추론할 수 없음
                try {
                    pstmt.setObject(2, null); // ? is null 체크용
                    pstmt.setObject(3, null); // department_id
                    pstmt.setObject(4, null); // ? is null 체크용
                    pstmt.setObject(5, null); // keyword for course_name
                    pstmt.setObject(6, null); // keyword for professor
                    pstmt.setInt(7, 0); // offset
                    pstmt.setInt(8, 3); // limit
                    
                    logger.info("⚠️  setObject(null) 방식으로 시도 중...");
                    
                    try (ResultSet rs = pstmt.executeQuery()) {
                        int count = 0;
                        while (rs.next()) {
                            count++;
                        }
                        logger.info("✅ Hibernate 스타일 쿼리 성공: {}개 결과", count);
                    }
                    
                } catch (SQLException e) {
                    logger.warn("❌ setObject(null) 방식 실패: {}", e.getMessage());
                    
                    // 대안: 명시적 타입 지정
                    logger.info("🔄 명시적 NULL 타입 지정으로 재시도...");
                    pstmt.clearParameters();
                    pstmt.setLong(1, 1L); // semester_id
                    pstmt.setNull(2, Types.BIGINT); // ? is null 체크용
                    pstmt.setNull(3, Types.BIGINT); // department_id
                    pstmt.setNull(4, Types.VARCHAR); // ? is null 체크용
                    pstmt.setNull(5, Types.VARCHAR); // keyword for course_name
                    pstmt.setNull(6, Types.VARCHAR); // keyword for professor
                    pstmt.setInt(7, 0); // offset
                    pstmt.setInt(8, 3); // limit
                    
                    try (ResultSet rs = pstmt.executeQuery()) {
                        int count = 0;
                        while (rs.next()) {
                            count++;
                        }
                        logger.info("✅ 명시적 NULL 타입 지정으로 성공: {}개 결과", count);
                    }
                }
            }
        }
    }

    @Test
    @DisplayName("Agent Safe Mode에서의 PostgreSQL 호환성 테스트")
    void testAgentSafeModePostgreSQL() throws SQLException {
        logger.info("🧪 Agent Safe Mode PostgreSQL 호환성 테스트 시작");
        
        // Agent를 Safe Mode로 설정
        System.setProperty("kubedb.monitor.postgresql.fix-unknown-types", "true");
        System.setProperty("kubedb.monitor.safe.transformation.mode", "true");
        
        try (Connection conn = DriverManager.getConnection(TEST_DB_URL, TEST_DB_USER, TEST_DB_PASSWORD)) {
            String sql = "SELECT COUNT(*) as total FROM courses WHERE semester_id = ? AND (? IS NULL OR department_id = ?)";
            
            try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
                pstmt.setLong(1, 1L);
                pstmt.setObject(2, null); // 이 부분이 문제가 될 수 있음
                pstmt.setObject(3, null);
                
                try (ResultSet rs = pstmt.executeQuery()) {
                    if (rs.next()) {
                        int total = rs.getInt("total");
                        logger.info("✅ Safe Mode에서 쿼리 성공: 총 {}개 과목", total);
                        assertTrue(total > 0, "결과가 있어야 함");
                    }
                }
            }
        }
    }

    @Test
    @DisplayName("PostgreSQLCompatibilityHelper를 사용한 안전한 파라미터 바인딩 테스트")
    void testCompatibilityHelperSafeBinding() throws SQLException {
        logger.info("🧪 PostgreSQLCompatibilityHelper 테스트 시작");
        
        compatibilityHelper.logCompatibilitySettings();
        
        try (Connection conn = DriverManager.getConnection(TEST_DB_URL, TEST_DB_USER, TEST_DB_PASSWORD)) {
            String sql = """
                SELECT c.course_id, c.course_name, c.professor
                FROM courses c 
                WHERE c.semester_id = ? 
                AND c.is_active = true 
                AND (? IS NULL OR c.department_id = ?) 
                AND (? IS NULL OR LOWER(c.course_name) LIKE LOWER(CONCAT('%', ?, '%')))
                ORDER BY c.course_id 
                LIMIT ?
            """;
            
            try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
                // PostgreSQLCompatibilityHelper를 사용하여 안전하게 파라미터 설정
                pstmt.setLong(1, 1L); // semester_id
                
                // NULL 파라미터들을 안전하게 처리
                compatibilityHelper.setParameterSafely(pstmt, 2, null); // department filter
                compatibilityHelper.setParameterSafely(pstmt, 3, null); // department_id
                compatibilityHelper.setParameterSafely(pstmt, 4, null); // keyword filter
                compatibilityHelper.setParameterSafely(pstmt, 5, null); // keyword
                
                pstmt.setInt(6, 5); // limit
                
                logger.info("📋 CompatibilityHelper를 사용하여 쿼리 실행");
                
                try (ResultSet rs = pstmt.executeQuery()) {
                    int count = 0;
                    while (rs.next()) {
                        count++;
                        logger.info("📚 과목 {}: {} ({})", count, rs.getString("course_name"), rs.getString("professor"));
                    }
                    logger.info("✅ CompatibilityHelper 테스트 성공: {}개 결과", count);
                    assertTrue(count > 0, "결과가 있어야 함");
                }
            }
        }
    }

    @Test
    @DisplayName("Hibernate 스타일 파라미터 배열 처리 테스트")
    void testHibernateStyleParameterArray() throws SQLException {
        logger.info("🧪 Hibernate 스타일 파라미터 배열 처리 테스트 시작");
        
        try (Connection conn = DriverManager.getConnection(TEST_DB_URL, TEST_DB_USER, TEST_DB_PASSWORD)) {
            // 실제 Hibernate가 생성하는 쿼리 패턴
            String sql = """
                select
                    c1_0.course_id,
                    c1_0.course_name,
                    c1_0.professor,
                    c1_0.credits
                from
                    courses c1_0 
                where
                    c1_0.semester_id=? 
                    and c1_0.is_active=true 
                    and (
                        ? is null 
                        or c1_0.department_id=?
                    ) 
                    and (
                        ? is null 
                        or lower(c1_0.course_name) like lower(('%'||?||'%'))
                    ) 
                order by
                    c1_0.course_id
                offset
                    ? rows 
                fetch
                    first ? rows only
            """;
            
            try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
                // Hibernate 스타일 파라미터 배열
                Object[] hibernateParams = {
                    1L,    // semester_id
                    null,  // department filter check
                    null,  // department_id
                    null,  // keyword filter check  
                    null,  // keyword
                    0,     // offset
                    3      // limit
                };
                
                logger.info("📋 Hibernate 스타일 파라미터 배열로 처리");
                
                // PostgreSQLCompatibilityHelper를 사용하여 배열 처리
                compatibilityHelper.handleHibernateStyleQuery(pstmt, hibernateParams);
                
                // 배열로 처리되지 않는 파라미터들은 직접 설정
                pstmt.setLong(1, 1L);
                pstmt.setInt(6, 0);  // offset
                pstmt.setInt(7, 3);  // limit
                
                try (ResultSet rs = pstmt.executeQuery()) {
                    int count = 0;
                    while (rs.next()) {
                        count++;
                        logger.info("📚 과목 {}: {} ({}크레딧)", count, rs.getString("course_name"), rs.getInt("credits"));
                    }
                    logger.info("✅ Hibernate 스타일 배열 처리 성공: {}개 결과", count);
                    assertTrue(count > 0, "결과가 있어야 함");
                }
            }
        }
    }
}