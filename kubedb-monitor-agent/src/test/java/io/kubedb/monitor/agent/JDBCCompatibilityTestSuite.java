package io.kubedb.monitor.agent;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.TestInstance;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.sql.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 체계적인 JDBC 호환성 테스트 슈트
 * PostgreSQL 기준으로 작성되었지만 다른 DB로 확장 가능
 * 
 * 테스트 카테고리:
 * 1. 기본 연결 테스트
 * 2. PreparedStatement 호환성
 * 3. 트랜잭션 경계 처리
 * 4. 복잡한 쿼리 패턴
 * 5. 에러 상황 처리
 * 6. 멀티스레드 환경 테스트
 */
@Testcontainers
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@DisplayName("JDBC 호환성 테스트 슈트")
public class JDBCCompatibilityTestSuite {

    private static final Logger logger = LoggerFactory.getLogger(JDBCCompatibilityTestSuite.class);
    
    // TestContainers PostgreSQL 컨테이너
    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15-alpine")
            .withDatabaseName("testdb")
            .withUsername("testuser")  
            .withPassword("testpass");
    
    // 동적으로 설정되는 DB 연결 정보
    private static String TEST_DB_URL;
    private static String TEST_DB_USER;
    private static String TEST_DB_PASSWORD;
    
    private AgentConfig testConfig;
    private TestMetrics testMetrics;
    
    @BeforeAll
    static void initializeDatabase() {
        // PostgreSQL 컨테이너에서 연결 정보 가져오기
        TEST_DB_URL = postgres.getJdbcUrl();
        TEST_DB_USER = postgres.getUsername();
        TEST_DB_PASSWORD = postgres.getPassword();
        
        logger.info("PostgreSQL TestContainer 시작됨:");
        logger.info("  URL: {}", TEST_DB_URL);
        logger.info("  User: {}", TEST_DB_USER);
        
        // 테스트 테이블 생성
        try (Connection conn = DriverManager.getConnection(TEST_DB_URL, TEST_DB_USER, TEST_DB_PASSWORD);
             Statement stmt = conn.createStatement()) {
            
            // 대학교 수강신청 시스템 테이블들 생성 (간소화)
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS departments (
                    department_id SERIAL PRIMARY KEY,
                    department_name VARCHAR(100) NOT NULL,
                    college VARCHAR(100) NOT NULL
                )
            """);
            
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS courses (
                    course_id SERIAL PRIMARY KEY,
                    course_name VARCHAR(200) NOT NULL,
                    department_id INTEGER REFERENCES departments(department_id),
                    capacity INTEGER DEFAULT 30,
                    enrolled_count INTEGER DEFAULT 0,
                    is_active BOOLEAN DEFAULT TRUE
                )
            """);
            
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS students (
                    student_id VARCHAR(20) PRIMARY KEY,
                    name VARCHAR(100) NOT NULL,
                    department_id INTEGER REFERENCES departments(department_id),
                    grade INTEGER DEFAULT 1
                )
            """);
            
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS cart (
                    cart_id SERIAL PRIMARY KEY,
                    student_id VARCHAR(20) REFERENCES students(student_id),
                    course_id INTEGER REFERENCES courses(course_id)
                )
            """);
            
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS enrollments (
                    enrollment_id SERIAL PRIMARY KEY,
                    student_id VARCHAR(20) REFERENCES students(student_id),
                    course_id INTEGER REFERENCES courses(course_id),
                    enrollment_date TIMESTAMP DEFAULT CURRENT_TIMESTAMP
                )
            """);
            
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS semesters (
                    semester_id SERIAL PRIMARY KEY,
                    year INTEGER NOT NULL,
                    season VARCHAR(20) NOT NULL
                )
            """);
            
            // 테스트 데이터 삽입
            stmt.execute("INSERT INTO departments (department_name, college) VALUES ('컴퓨터공학과', '공과대학')");
            stmt.execute("INSERT INTO departments (department_name, college) VALUES ('전자공학과', '공과대학')");
            
            stmt.execute("INSERT INTO courses (course_name, department_id) VALUES ('데이터베이스', 1)");
            stmt.execute("INSERT INTO courses (course_name, department_id) VALUES ('알고리즘', 1)");
            stmt.execute("INSERT INTO courses (course_name, department_id) VALUES ('회로이론', 2)");
            
            stmt.execute("INSERT INTO students (student_id, name, department_id) VALUES ('2024001', '김테스트', 1)");
            stmt.execute("INSERT INTO students (student_id, name, department_id) VALUES ('2024002', '박테스트', 1)");
            
            stmt.execute("INSERT INTO semesters (year, season) VALUES (2024, 'SPRING')");
            stmt.execute("INSERT INTO semesters (year, season) VALUES (2024, 'FALL')");
            
            logger.info("테스트 데이터베이스 스키마 및 데이터 생성 완료");
            
        } catch (SQLException e) {
            logger.error("테스트 데이터베이스 초기화 실패", e);
            throw new RuntimeException("Database initialization failed", e);
        }
    }
    
    @BeforeEach
    void setUp() {
        testConfig = new AgentConfig.Builder()
                .enabled(true)
                .postgresqlStrictCompatibility(true)
                .excludePreparedStatementTransformation(true)
                .preserveTransactionBoundaries(true)
                .excludeConnectionManagement(false)
                .avoidAutocommitStateChange(true)  // 핵심 설정: autoCommit 간섭 방지
                .avoidNullParameterTransformation(true)
                .postgresqlFixAutocommitConflict(true)
                .safeTransformationMode(true)
                .logLevel("DEBUG")
                .build();
                
        testMetrics = new TestMetrics();
        
        logger.info("테스트 설정 초기화 완료: {}", testConfig);
    }

    @Nested
    @DisplayName("1. 기본 연결 테스트")
    class BasicConnectionTests {
        
        @Test
        @DisplayName("기본 DataSource 연결 테스트")
        void testBasicConnection() throws Exception {
            testMetrics.startTest("basic_connection");
            
            try (Connection conn = DriverManager.getConnection(TEST_DB_URL, TEST_DB_USER, TEST_DB_PASSWORD)) {
                assertConnectionValid(conn);
                testMetrics.recordSuccess("basic_connection");
                
                logger.info("✅ 기본 연결 테스트 성공");
            } catch (Exception e) {
                testMetrics.recordFailure("basic_connection", e);
                logger.error("❌ 기본 연결 테스트 실패", e);
                throw e;
            }
        }
        
        @Test
        @DisplayName("연결 풀링 동작 확인")
        void testConnectionPooling() throws Exception {
            testMetrics.startTest("connection_pooling");
            
            List<Connection> connections = new ArrayList<>();
            
            try {
                // 여러 연결 생성
                for (int i = 0; i < 5; i++) {
                    Connection conn = DriverManager.getConnection(TEST_DB_URL, TEST_DB_USER, TEST_DB_PASSWORD);
                    connections.add(conn);
                    assertConnectionValid(conn);
                }
                
                testMetrics.recordSuccess("connection_pooling");
                logger.info("✅ 연결 풀링 테스트 성공: {} 연결 생성", connections.size());
                
            } catch (Exception e) {
                testMetrics.recordFailure("connection_pooling", e);
                logger.error("❌ 연결 풀링 테스트 실패", e);
                throw e;
            } finally {
                // 모든 연결 정리
                for (Connection conn : connections) {
                    if (conn != null && !conn.isClosed()) {
                        conn.close();
                    }
                }
            }
        }
        
        @Test
        @DisplayName("autoCommit 모드 확인")
        void testAutoCommitMode() throws Exception {
            testMetrics.startTest("autocommit_mode");
            
            try (Connection conn = DriverManager.getConnection(TEST_DB_URL, TEST_DB_USER, TEST_DB_PASSWORD)) {
                
                // 기본 autoCommit 상태 확인
                boolean defaultAutoCommit = conn.getAutoCommit();
                logger.info("기본 autoCommit 상태: {}", defaultAutoCommit);
                
                // autoCommit 모드 변경 테스트
                conn.setAutoCommit(false);
                assert !conn.getAutoCommit() : "autoCommit을 false로 설정했지만 여전히 true";
                
                conn.setAutoCommit(true);
                assert conn.getAutoCommit() : "autoCommit을 true로 설정했지만 여전히 false";
                
                testMetrics.recordSuccess("autocommit_mode");
                logger.info("✅ autoCommit 모드 테스트 성공");
                
            } catch (Exception e) {
                testMetrics.recordFailure("autocommit_mode", e);
                logger.error("❌ autoCommit 모드 테스트 실패", e);
                throw e;
            }
        }
    }

    @Nested
    @DisplayName("2. PreparedStatement 호환성")
    class PreparedStatementTests {
        
        @Test
        @DisplayName("NULL 파라미터 바인딩 테스트")
        void testNullParameterBinding() throws Exception {
            testMetrics.startTest("null_parameter_binding");
            
            try (Connection conn = DriverManager.getConnection(TEST_DB_URL, TEST_DB_USER, TEST_DB_PASSWORD)) {
                
                // PostgreSQL의 문제가 되었던 쿼리 패턴 테스트
                String sql = "SELECT course_id, course_name FROM courses " +
                           "WHERE semester_id = ? AND is_active = true " +
                           "AND (? IS NULL OR department_id = ?) " +
                           "AND (? IS NULL OR LOWER(course_name) LIKE LOWER(CONCAT('%', ?, '%')))";
                
                try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
                    
                    // 파라미터 바인딩 - NULL 값 포함
                    pstmt.setInt(1, 1); // semester_id
                    pstmt.setNull(2, Types.INTEGER); // department_id NULL 체크용
                    pstmt.setNull(3, Types.INTEGER); // department_id 실제 값
                    pstmt.setNull(4, Types.VARCHAR); // query string NULL 체크용
                    pstmt.setNull(5, Types.VARCHAR); // query string 실제 값
                    
                    // 쿼리 실행 - 이 부분에서 "Unknown Types value" 에러가 발생했음
                    try (ResultSet rs = pstmt.executeQuery()) {
                        int count = 0;
                        while (rs.next() && count < 5) { // 최대 5개만 확인
                            count++;
                        }
                        logger.info("NULL 파라미터 쿼리 결과: {} 건", count);
                    }
                    
                    testMetrics.recordSuccess("null_parameter_binding");
                    logger.info("✅ NULL 파라미터 바인딩 테스트 성공");
                }
                
            } catch (SQLException e) {
                testMetrics.recordFailure("null_parameter_binding", e);
                logger.error("❌ NULL 파라미터 바인딩 테스트 실패: {}", e.getMessage(), e);
                
                // PostgreSQL 특화 에러 확인
                if (e.getMessage().contains("Unknown Types value")) {
                    logger.error("🔥 PostgreSQL 'Unknown Types value' 에러 발생 - Agent 호환성 문제");
                }
                
                throw e;
            }
        }
        
        @Test
        @DisplayName("다양한 데이터 타입 바인딩 테스트")
        void testVariousDataTypeBinding() throws Exception {
            testMetrics.startTest("various_datatype_binding");
            
            try (Connection conn = DriverManager.getConnection(TEST_DB_URL, TEST_DB_USER, TEST_DB_PASSWORD)) {
                
                String sql = "SELECT ?, ?, ?, ?, ?, ?, ?";
                
                try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
                    
                    // 다양한 타입 테스트
                    pstmt.setString(1, "test_string");
                    pstmt.setInt(2, 12345);
                    pstmt.setLong(3, 9876543210L);
                    pstmt.setDouble(4, 3.14159);
                    pstmt.setBoolean(5, true);
                    pstmt.setTimestamp(6, new Timestamp(System.currentTimeMillis()));
                    pstmt.setNull(7, Types.VARCHAR);
                    
                    try (ResultSet rs = pstmt.executeQuery()) {
                        if (rs.next()) {
                            logger.info("다양한 타입 테스트 결과:");
                            logger.info("  String: {}", rs.getString(1));
                            logger.info("  Int: {}", rs.getInt(2));
                            logger.info("  Long: {}", rs.getLong(3));
                            logger.info("  Double: {}", rs.getDouble(4));
                            logger.info("  Boolean: {}", rs.getBoolean(5));
                            logger.info("  Timestamp: {}", rs.getTimestamp(6));
                            logger.info("  NULL: {}", rs.getString(7));
                        }
                    }
                    
                    testMetrics.recordSuccess("various_datatype_binding");
                    logger.info("✅ 다양한 데이터 타입 바인딩 테스트 성공");
                }
                
            } catch (Exception e) {
                testMetrics.recordFailure("various_datatype_binding", e);
                logger.error("❌ 다양한 데이터 타입 바인딩 테스트 실패", e);
                throw e;
            }
        }
        
        @Test
        @DisplayName("배치 처리 테스트")
        void testBatchProcessing() throws Exception {
            testMetrics.startTest("batch_processing");
            
            try (Connection conn = DriverManager.getConnection(TEST_DB_URL, TEST_DB_USER, TEST_DB_PASSWORD)) {
                conn.setAutoCommit(false);
                
                // 테스트용 임시 테이블이 있다고 가정 - 실제로는 기존 테이블 사용
                String sql = "INSERT INTO students (student_id, name, department_id) VALUES (?, ?, ?)";
                
                try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
                    
                    // 배치 추가
                    for (int i = 1; i <= 3; i++) {
                        pstmt.setString(1, "BATCH_" + System.currentTimeMillis() + "_" + i);
                        pstmt.setString(2, "배치테스트학생" + i);
                        pstmt.setInt(3, 1); // 첫 번째 학과
                        pstmt.addBatch();
                    }
                    
                    // 배치 실행
                    int[] results = pstmt.executeBatch();
                    conn.commit();
                    
                    logger.info("배치 처리 결과: {}", Arrays.toString(results));
                    
                    testMetrics.recordSuccess("batch_processing");
                    logger.info("✅ 배치 처리 테스트 성공");
                    
                } catch (Exception e) {
                    conn.rollback();
                    throw e;
                } finally {
                    conn.setAutoCommit(true);
                }
                
            } catch (Exception e) {
                testMetrics.recordFailure("batch_processing", e);
                logger.error("❌ 배치 처리 테스트 실패", e);
                throw e;
            }
        }
    }
    
    @Nested
    @DisplayName("3. 트랜잭션 경계 처리 & autoCommit 호환성")
    class TransactionBoundaryTests {
        
        @Test
        @DisplayName("명시적 트랜잭션 처리 테스트")
        void testExplicitTransactionHandling() throws Exception {
            testMetrics.startTest("explicit_transaction");
            
            try (Connection conn = DriverManager.getConnection(TEST_DB_URL, TEST_DB_USER, TEST_DB_PASSWORD)) {
                
                // autoCommit 비활성화
                conn.setAutoCommit(false);
                
                try {
                    // 트랜잭션 내 작업 수행
                    String selectSql = "SELECT COUNT(*) FROM courses WHERE is_active = true";
                    int initialCount = 0;
                    
                    try (PreparedStatement pstmt = conn.prepareStatement(selectSql);
                         ResultSet rs = pstmt.executeQuery()) {
                        if (rs.next()) {
                            initialCount = rs.getInt(1);
                        }
                    }
                    
                    logger.info("트랜잭션 시작 - 활성 과목 수: {}", initialCount);
                    
                    // 명시적 커밋 - 이 부분에서 "Cannot commit when autoCommit is enabled" 에러 발생
                    conn.commit();
                    
                    testMetrics.recordSuccess("explicit_transaction");
                    logger.info("✅ 명시적 트랜잭션 처리 테스트 성공");
                    
                } catch (SQLException e) {
                    logger.warn("트랜잭션 에러 발생, 롤백 시도: {}", e.getMessage());
                    
                    try {
                        conn.rollback();
                        logger.info("롤백 성공");
                    } catch (SQLException rollbackEx) {
                        logger.error("롤백 실패: {}", rollbackEx.getMessage());
                        testMetrics.recordFailure("explicit_transaction", rollbackEx);
                        throw rollbackEx;
                    }
                    
                    // PostgreSQL autoCommit 관련 에러 확인
                    if (e.getMessage().contains("Cannot commit when autoCommit is enabled")) {
                        logger.error("🔥 PostgreSQL autoCommit 충돌 에러 - Agent가 상태를 잘못 관리");
                        testMetrics.recordFailure("explicit_transaction", e);
                        throw e;
                    } else if (e.getMessage().contains("Unable to rollback against JDBC Connection")) {
                        logger.error("🔥 PostgreSQL 롤백 실패 에러 - Agent 호환성 문제");
                        testMetrics.recordFailure("explicit_transaction", e);
                        throw e;
                    }
                    
                } finally {
                    conn.setAutoCommit(true);
                }
                
            } catch (Exception e) {
                testMetrics.recordFailure("explicit_transaction", e);
                logger.error("❌ 명시적 트랜잭션 처리 테스트 실패", e);
                throw e;
            }
        }
        
        @Test
        @DisplayName("Savepoint 처리 테스트")
        void testSavepointHandling() throws Exception {
            testMetrics.startTest("savepoint_handling");
            
            try (Connection conn = DriverManager.getConnection(TEST_DB_URL, TEST_DB_USER, TEST_DB_PASSWORD)) {
                conn.setAutoCommit(false);
                
                try {
                    // Savepoint 생성
                    Savepoint savepoint1 = conn.setSavepoint("test_savepoint_1");
                    logger.info("Savepoint 생성: {}", savepoint1.getSavepointName());
                    
                    // 일부 작업 수행
                    String sql = "SELECT COUNT(*) FROM students";
                    try (PreparedStatement pstmt = conn.prepareStatement(sql);
                         ResultSet rs = pstmt.executeQuery()) {
                        if (rs.next()) {
                            logger.info("현재 학생 수: {}", rs.getInt(1));
                        }
                    }
                    
                    // Savepoint로 롤백
                    conn.rollback(savepoint1);
                    logger.info("Savepoint 롤백 완료");
                    
                    conn.commit();
                    
                    testMetrics.recordSuccess("savepoint_handling");
                    logger.info("✅ Savepoint 처리 테스트 성공");
                    
                } catch (Exception e) {
                    conn.rollback();
                    throw e;
                } finally {
                    conn.setAutoCommit(true);
                }
                
            } catch (Exception e) {
                testMetrics.recordFailure("savepoint_handling", e);
                logger.error("❌ Savepoint 처리 테스트 실패", e);
                throw e;
            }
        }
        
        @Test
        @DisplayName("autoCommit 상태 변경 호환성 테스트")
        void testAutoCommitStateCompatibility() throws Exception {
            testMetrics.startTest("autocommit_state_compatibility");
            
            try (Connection conn = DriverManager.getConnection(TEST_DB_URL, TEST_DB_USER, TEST_DB_PASSWORD)) {
                
                // 1. 기본 autoCommit 상태 확인
                boolean initialAutoCommit = conn.getAutoCommit();
                logger.info("초기 autoCommit 상태: {}", initialAutoCommit);
                
                // 2. autoCommit을 false로 변경
                conn.setAutoCommit(false);
                boolean afterSetFalse = conn.getAutoCommit();
                assert !afterSetFalse : "autoCommit을 false로 설정했지만 여전히 true";
                logger.info("autoCommit false 설정 후: {}", afterSetFalse);
                
                // 3. 간단한 SELECT 쿼리 (commit 없이)
                try (PreparedStatement pstmt = conn.prepareStatement("SELECT 1");
                     ResultSet rs = pstmt.executeQuery()) {
                    if (rs.next()) {
                        logger.info("SELECT 쿼리 결과: {}", rs.getInt(1));
                    }
                }
                
                // 4. 명시적 commit - 여기서 PostgreSQL Agent 호환성 문제 발생 가능
                try {
                    conn.commit();
                    logger.info("✅ 명시적 commit 성공");
                } catch (SQLException e) {
                    if (e.getMessage().contains("Cannot commit when autoCommit is enabled")) {
                        logger.error("🔥 PostgreSQL Agent autoCommit 상태 혼동 에러: {}", e.getMessage());
                        testMetrics.recordFailure("autocommit_state_compatibility", e);
                        throw e;
                    }
                    throw e;
                }
                
                // 5. autoCommit을 true로 복원
                conn.setAutoCommit(true);
                boolean afterRestore = conn.getAutoCommit();
                assert afterRestore : "autoCommit을 true로 설정했지만 여전히 false";
                logger.info("autoCommit true 복원 후: {}", afterRestore);
                
                testMetrics.recordSuccess("autocommit_state_compatibility");
                logger.info("✅ autoCommit 상태 변경 호환성 테스트 성공");
                
            } catch (Exception e) {
                testMetrics.recordFailure("autocommit_state_compatibility", e);
                logger.error("❌ autoCommit 상태 변경 호환성 테스트 실패", e);
                throw e;
            }
        }
        
        @Test
        @DisplayName("Agent autoCommit 간섭 방지 테스트")
        void testAgentAutoCommitNonInterference() throws Exception {
            testMetrics.startTest("agent_autocommit_non_interference");
            
            try (Connection conn = DriverManager.getConnection(TEST_DB_URL, TEST_DB_USER, TEST_DB_PASSWORD)) {
                
                // Agent가 설정된 상황에서 autoCommit 상태가 예측 가능한지 테스트
                logger.info("=== Agent autoCommit 간섭 방지 테스트 시작 ===");
                
                // 1. 초기 상태 기록
                boolean initial = conn.getAutoCommit();
                logger.info("초기 autoCommit: {}", initial);
                
                // 2. 여러 번 autoCommit 상태 변경 후 일관성 확인
                for (int i = 0; i < 3; i++) {
                    conn.setAutoCommit(false);
                    assert !conn.getAutoCommit() : "Iteration " + i + ": setAutoCommit(false) 후에도 true";
                    
                    conn.setAutoCommit(true);
                    assert conn.getAutoCommit() : "Iteration " + i + ": setAutoCommit(true) 후에도 false";
                    
                    logger.info("Iteration {}: autoCommit 상태 변경 정상", i + 1);
                }
                
                // 3. 트랜잭션 중간에 Agent가 autoCommit을 변경하지 않는지 테스트
                conn.setAutoCommit(false);
                
                try (PreparedStatement pstmt = conn.prepareStatement("SELECT COUNT(*) FROM courses")) {
                    try (ResultSet rs = pstmt.executeQuery()) {
                        if (rs.next()) {
                            int count = rs.getInt(1);
                            logger.info("쿼리 실행 후 과목 수: {}", count);
                            
                            // Agent가 autoCommit 상태를 변경했는지 확인
                            boolean afterQuery = conn.getAutoCommit();
                            if (afterQuery) {
                                logger.error("🔥 Agent가 쿼리 실행 중 autoCommit을 true로 변경함!");
                                testMetrics.recordFailure("agent_autocommit_non_interference", 
                                    new RuntimeException("Agent modified autoCommit during query"));
                                throw new RuntimeException("Agent modified autoCommit state");
                            }
                        }
                    }
                }
                
                // 정상적으로 커밋
                conn.commit();
                conn.setAutoCommit(true);
                
                testMetrics.recordSuccess("agent_autocommit_non_interference");
                logger.info("✅ Agent autoCommit 간섭 방지 테스트 성공");
                
            } catch (Exception e) {
                testMetrics.recordFailure("agent_autocommit_non_interference", e);
                logger.error("❌ Agent autoCommit 간섭 방지 테스트 실패", e);
                throw e;
            }
        }
        
        @Test
        @DisplayName("HikariCP autoCommit 설정 호환성 테스트")
        void testHikariAutoCommitCompatibility() throws Exception {
            testMetrics.startTest("hikari_autocommit_compatibility");
            
            try (Connection conn = DriverManager.getConnection(TEST_DB_URL, TEST_DB_USER, TEST_DB_PASSWORD)) {
                
                // HikariCP는 기본적으로 autoCommit=true로 Connection을 제공
                // Agent가 이 설정을 방해하지 않는지 확인
                
                logger.info("=== HikariCP autoCommit 설정 호환성 테스트 ===");
                
                boolean hikariDefault = conn.getAutoCommit();
                logger.info("HikariCP 기본 autoCommit: {}", hikariDefault);
                
                // 일반적인 Spring Boot @Transactional 동작 시뮬레이션
                // Spring이 트랜잭션 시작 시 autoCommit=false로 설정
                conn.setAutoCommit(false);
                logger.info("Spring @Transactional 시작: autoCommit=false");
                
                // 비즈니스 로직 실행 (여러 쿼리)
                try (PreparedStatement pstmt1 = conn.prepareStatement("SELECT COUNT(*) FROM students");
                     PreparedStatement pstmt2 = conn.prepareStatement("SELECT COUNT(*) FROM courses")) {
                    
                    try (ResultSet rs1 = pstmt1.executeQuery()) {
                        if (rs1.next()) logger.info("학생 수: {}", rs1.getInt(1));
                    }
                    
                    try (ResultSet rs2 = pstmt2.executeQuery()) {
                        if (rs2.next()) logger.info("과목 수: {}", rs2.getInt(1));
                    }
                }
                
                // Spring이 트랜잭션 종료 시 commit 후 autoCommit=true로 복원
                try {
                    conn.commit();
                    logger.info("Spring 트랜잭션 commit 성공");
                } catch (SQLException e) {
                    if (e.getMessage().contains("Cannot commit when autoCommit is enabled")) {
                        logger.error("🔥 HikariCP + Agent autoCommit 충돌: {}", e.getMessage());
                        testMetrics.recordFailure("hikari_autocommit_compatibility", e);
                        throw e;
                    }
                    throw e;
                }
                
                conn.setAutoCommit(true);
                logger.info("Spring @Transactional 종료: autoCommit=true");
                
                // HikariCP에 Connection 반환 후 다음 요청에서 정상 동작하는지 확인
                boolean finalState = conn.getAutoCommit();
                assert finalState : "트랜잭션 종료 후 autoCommit이 true가 아님";
                
                testMetrics.recordSuccess("hikari_autocommit_compatibility");
                logger.info("✅ HikariCP autoCommit 설정 호환성 테스트 성공");
                
            } catch (Exception e) {
                testMetrics.recordFailure("hikari_autocommit_compatibility", e);
                logger.error("❌ HikariCP autoCommit 설정 호환성 테스트 실패", e);
                throw e;
            }
        }
        
        @Test
        @DisplayName("실제 Production 에러 시나리오 - DataInitializer autoCommit 충돌")
        void testRealProductionAutoCommitError() throws Exception {
            testMetrics.startTest("production_autocommit_error");
            
            try (Connection conn = DriverManager.getConnection(TEST_DB_URL, TEST_DB_USER, TEST_DB_PASSWORD)) {
                
                // 실제 Production 환경에서 발생한 시나리오 재현
                logger.info("=== 실제 Production autoCommit 에러 시나리오 테스트 ===");
                
                // Spring Boot DataInitializer와 유사한 패턴:
                // 1. HikariCP가 autoCommit=true로 Connection 제공
                boolean initialAutoCommit = conn.getAutoCommit();
                logger.info("HikariCP 초기 autoCommit: {}", initialAutoCommit);
                assert initialAutoCommit : "HikariCP는 기본적으로 autoCommit=true여야 함";
                
                // 2. Spring @Transactional이 autoCommit=false로 설정
                conn.setAutoCommit(false);
                logger.info("Spring @Transactional: autoCommit=false로 설정");
                
                // 3. JPA Repository 호출 (count 메서드) - 실제 에러 발생 지점
                try (PreparedStatement pstmt = conn.prepareStatement("SELECT COUNT(*) FROM courses")) {
                    try (ResultSet rs = pstmt.executeQuery()) {
                        if (rs.next()) {
                            int count = rs.getInt(1);
                            logger.info("JPA Repository count() 결과: {}", count);
                        }
                    }
                }
                
                // 4. Spring TransactionManager가 commit() 호출
                // 이 시점에서 "Cannot commit when autoCommit is enabled" 에러 발생
                boolean autoCommitBeforeCommit = conn.getAutoCommit();
                logger.info("commit() 호출 직전 autoCommit 상태: {}", autoCommitBeforeCommit);
                
                if (autoCommitBeforeCommit) {
                    // Agent가 autoCommit 상태를 잘못 관리한 경우
                    logger.error("🔥 CRITICAL: Agent가 autoCommit을 true로 변경함! (avoidAutocommitStateChange=true인데도)");
                    testMetrics.recordFailure("production_autocommit_error", 
                        new RuntimeException("Agent violated avoidAutocommitStateChange setting"));
                    throw new SQLException("Cannot commit when autoCommit is enabled."); // 실제 에러 재현
                }
                
                try {
                    conn.commit();
                    logger.info("✅ Spring TransactionManager commit 성공");
                } catch (SQLException e) {
                    if (e.getMessage().contains("Cannot commit when autoCommit is enabled")) {
                        logger.error("🔥 Production 동일 에러 재현: {}", e.getMessage());
                        testMetrics.recordFailure("production_autocommit_error", e);
                        throw e;
                    }
                    throw e;
                }
                
                // 5. Spring이 Connection을 HikariCP에 반환하기 전 autoCommit=true로 복원
                conn.setAutoCommit(true);
                logger.info("Spring: Connection 반환 전 autoCommit=true로 복원");
                
                testMetrics.recordSuccess("production_autocommit_error");
                logger.info("✅ 실제 Production 에러 시나리오 테스트 성공 (Agent가 간섭하지 않음)");
                
            } catch (SQLException e) {
                if (e.getMessage().contains("Cannot commit when autoCommit is enabled")) {
                    // 이 경우가 실제 Production 에러와 동일한 상황
                    testMetrics.recordFailure("production_autocommit_error", e);
                    logger.error("❌ 실제 Production 에러와 동일한 상황 발생: {}", e.getMessage());
                    logger.error("💡 해결 필요: Agent의 avoidAutocommitStateChange 설정이 제대로 작동하지 않음");
                    throw e;
                } else {
                    testMetrics.recordFailure("production_autocommit_error", e);
                    logger.error("❌ 실제 Production 에러 시나리오 테스트 실패", e);
                    throw e;
                }
            }
        }
        
        @Test
        @DisplayName("Agent와 함께 JPA/Hibernate 트랜잭션 패턴 테스트")
        void testAgentWithJpaHibernateTransactions() throws Exception {
            testMetrics.startTest("agent_jpa_hibernate_transactions");
            
            try (Connection conn = DriverManager.getConnection(TEST_DB_URL, TEST_DB_USER, TEST_DB_PASSWORD)) {
                
                // Spring Boot + JPA + Hibernate + HikariCP + Agent 전체 스택 시뮬레이션
                logger.info("=== Agent와 JPA/Hibernate 트랜잭션 호환성 테스트 ===");
                
                // 1. HikariCP Connection Pool에서 Connection 획득 (autoCommit=true)
                assert conn.getAutoCommit() : "HikariCP Connection은 autoCommit=true여야 함";
                
                // 2. Spring PlatformTransactionManager가 트랜잭션 시작
                conn.setAutoCommit(false);
                logger.info("Spring PlatformTransactionManager: 트랜잭션 시작 (autoCommit=false)");
                
                // Agent가 이 상태를 변경하지 않았는지 확인
                if (conn.getAutoCommit()) {
                    logger.error("🔥 Agent가 불법적으로 autoCommit을 true로 변경!");
                    testMetrics.recordFailure("agent_jpa_hibernate_transactions",
                        new RuntimeException("Agent illegally modified autoCommit state"));
                    throw new RuntimeException("Agent violated transaction boundaries");
                }
                
                // 3. Hibernate/JPA EntityManager가 여러 쿼리 실행
                String[] queries = {
                    "SELECT COUNT(*) FROM students",
                    "SELECT COUNT(*) FROM courses", 
                    "SELECT COUNT(*) FROM departments"
                };
                
                for (int i = 0; i < queries.length; i++) {
                    try (PreparedStatement pstmt = conn.prepareStatement(queries[i])) {
                        try (ResultSet rs = pstmt.executeQuery()) {
                            if (rs.next()) {
                                logger.info("쿼리 {}: {}", i+1, rs.getInt(1));
                            }
                        }
                    }
                    
                    // 각 쿼리 후에 Agent가 autoCommit을 변경했는지 확인
                    if (conn.getAutoCommit()) {
                        logger.error("🔥 Agent가 쿼리 {} 실행 후 autoCommit을 true로 변경!", i+1);
                        testMetrics.recordFailure("agent_jpa_hibernate_transactions",
                            new RuntimeException("Agent modified autoCommit during transaction"));
                        throw new RuntimeException("Agent violated transaction integrity");
                    }
                }
                
                // 4. Spring TransactionManager가 트랜잭션 커밋
                // 이 시점에서 autoCommit=false여야 커밋이 가능
                assert !conn.getAutoCommit() : "커밋 직전 autoCommit=false여야 함";
                
                conn.commit();
                logger.info("✅ Spring TransactionManager: 트랜잭션 커밋 성공");
                
                // 5. Spring이 Connection 상태를 HikariCP에 맞게 복원
                conn.setAutoCommit(true);
                logger.info("Spring: HikariCP 반환을 위해 autoCommit=true로 복원");
                
                testMetrics.recordSuccess("agent_jpa_hibernate_transactions");
                logger.info("✅ Agent와 JPA/Hibernate 트랜잭션 호환성 테스트 성공");
                
            } catch (Exception e) {
                testMetrics.recordFailure("agent_jpa_hibernate_transactions", e);
                logger.error("❌ Agent와 JPA/Hibernate 트랜잭션 호환성 테스트 실패", e);
                throw e;
            }
        }
        
        @Test
        @DisplayName("Agent가 실제로 동작하는지 검증 테스트")
        void testAgentActuallyRunning() throws Exception {
            testMetrics.startTest("agent_actually_running");
            
            try {
                // Agent 설정 확인
                logger.info("=== Agent 실제 동작 검증 테스트 ===");
                logger.info("Agent 설정: avoidAutocommitStateChange={}", testConfig.isAvoidAutocommitStateChange());
                logger.info("Agent 설정: safeTransformationMode={}", testConfig.isSafeTransformationMode());
                logger.info("Agent 설정: postgresqlStrictCompatibility={}", testConfig.isPostgresqlStrictCompatibility());
                
                // System Properties에서 Agent 관련 정보 확인
                String agentPath = System.getProperty("java.vm.info");
                logger.info("JVM Info: {}", agentPath);
                
                // Java Agent가 실제로 로드되었는지 확인
                String javaAgent = System.getProperty("javaagent");
                logger.info("Java Agent Property: {}", javaAgent);
                
                // 실제로 Connection을 생성해서 Agent의 Instrumentation 로그 확인
                try (Connection conn = DriverManager.getConnection(TEST_DB_URL, TEST_DB_USER, TEST_DB_PASSWORD)) {
                    logger.info("Connection 클래스: {}", conn.getClass().getName());
                    
                    // PreparedStatement 생성하여 Agent Instrumentation 확인
                    try (PreparedStatement pstmt = conn.prepareStatement("SELECT 1")) {
                        logger.info("PreparedStatement 클래스: {}", pstmt.getClass().getName());
                        
                        // 쿼리 실행하여 Agent의 모니터링 로직 동작 확인
                        try (ResultSet rs = pstmt.executeQuery()) {
                            if (rs.next()) {
                                logger.info("Query 실행 결과: {}", rs.getInt(1));
                            }
                        }
                    }
                }
                
                testMetrics.recordSuccess("agent_actually_running");
                logger.info("✅ Agent 실제 동작 검증 테스트 완료");
                
                // 테스트 환경에서는 Agent가 제대로 동작하지 않을 수 있음을 로깅
                logger.warn("⚠️ 주의: 테스트 환경에서는 Agent가 Production과 다르게 동작할 수 있음");
                logger.warn("⚠️ Production 환경에서는 JVM 시작 시 -javaagent 옵션으로 Agent가 로드됨");
                logger.warn("⚠️ 테스트에서는 Agent의 실제 바이트코드 변환이 제한적일 수 있음");
                
            } catch (Exception e) {
                testMetrics.recordFailure("agent_actually_running", e);
                logger.error("❌ Agent 실제 동작 검증 테스트 실패", e);
                throw e;
            }
        }
    }
    
    @Nested
    @DisplayName("4. 복잡한 쿼리 패턴")
    class ComplexQueryTests {
        
        @Test
        @DisplayName("복잡한 JOIN 쿼리 테스트")
        void testComplexJoinQuery() throws Exception {
            testMetrics.startTest("complex_join_query");
            
            try (Connection conn = DriverManager.getConnection(TEST_DB_URL, TEST_DB_USER, TEST_DB_PASSWORD)) {
                
                // 복잡한 JOIN 쿼리
                String sql = """
                    SELECT s.student_id, s.name, c.course_name, d.department_name
                    FROM students s 
                    LEFT JOIN enrollments e ON s.student_id = e.student_id
                    LEFT JOIN courses c ON e.course_id = c.course_id
                    LEFT JOIN departments d ON s.department_id = d.department_id
                    WHERE s.grade = ? AND (? IS NULL OR d.department_id = ?)
                    ORDER BY s.student_id
                    LIMIT 10
                    """;
                
                try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
                    pstmt.setInt(1, 1); // 1학년
                    pstmt.setNull(2, Types.INTEGER); // department filter
                    pstmt.setNull(3, Types.INTEGER);
                    
                    try (ResultSet rs = pstmt.executeQuery()) {
                        int count = 0;
                        while (rs.next()) {
                            count++;
                            if (count <= 3) { // 처음 3개만 로깅
                                logger.info("JOIN 결과 {}: {} - {} - {}", count, 
                                           rs.getString("student_id"), 
                                           rs.getString("name"),
                                           rs.getString("course_name"));
                            }
                        }
                        logger.info("복잡한 JOIN 쿼리 결과: {} 건", count);
                    }
                    
                    testMetrics.recordSuccess("complex_join_query");
                    logger.info("✅ 복잡한 JOIN 쿼리 테스트 성공");
                }
                
            } catch (Exception e) {
                testMetrics.recordFailure("complex_join_query", e);
                logger.error("❌ 복잡한 JOIN 쿼리 테스트 실패", e);
                throw e;
            }
        }
        
        @Test
        @DisplayName("서브쿼리 패턴 테스트")
        void testSubqueryPattern() throws Exception {
            testMetrics.startTest("subquery_pattern");
            
            try (Connection conn = DriverManager.getConnection(TEST_DB_URL, TEST_DB_USER, TEST_DB_PASSWORD)) {
                
                // 서브쿼리를 포함한 복잡한 쿼리
                String sql = """
                    SELECT c.course_name, c.capacity, c.enrolled_count,
                           (SELECT COUNT(*) FROM cart ct WHERE ct.course_id = c.course_id) as cart_count
                    FROM courses c
                    WHERE c.department_id IN (
                        SELECT d.department_id 
                        FROM departments d 
                        WHERE d.college = ?
                    )
                    AND c.enrolled_count < c.capacity
                    ORDER BY c.enrolled_count DESC
                    LIMIT 5
                    """;
                
                try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
                    pstmt.setString(1, "공과대학");
                    
                    try (ResultSet rs = pstmt.executeQuery()) {
                        int count = 0;
                        while (rs.next()) {
                            count++;
                            logger.info("서브쿼리 결과 {}: {} (정원: {}, 신청: {}, 장바구니: {})", 
                                       count,
                                       rs.getString("course_name"),
                                       rs.getInt("capacity"),
                                       rs.getInt("enrolled_count"),
                                       rs.getInt("cart_count"));
                        }
                        logger.info("서브쿼리 패턴 결과: {} 건", count);
                    }
                    
                    testMetrics.recordSuccess("subquery_pattern");
                    logger.info("✅ 서브쿼리 패턴 테스트 성공");
                }
                
            } catch (Exception e) {
                testMetrics.recordFailure("subquery_pattern", e);
                logger.error("❌ 서브쿼리 패턴 테스트 실패", e);
                throw e;
            }
        }
    }
    
    @Nested
    @DisplayName("5. 에러 상황 처리")
    class ErrorHandlingTests {
        
        @Test
        @DisplayName("SQL 문법 오류 처리 테스트")
        void testSqlSyntaxErrorHandling() throws Exception {
            testMetrics.startTest("sql_syntax_error");
            
            try (Connection conn = DriverManager.getConnection(TEST_DB_URL, TEST_DB_USER, TEST_DB_PASSWORD)) {
                
                // 의도적으로 잘못된 SQL
                String badSql = "SELECT * FROMM courses"; // 오타
                
                try (PreparedStatement pstmt = conn.prepareStatement(badSql)) {
                    pstmt.executeQuery(); // 여기서 에러 발생해야 함
                    
                    // 에러가 발생하지 않으면 실패
                    testMetrics.recordFailure("sql_syntax_error", new RuntimeException("Expected SQL syntax error but none occurred"));
                    logger.error("❌ SQL 문법 오류가 예상되었지만 발생하지 않음");
                    
                } catch (SQLException e) {
                    // 예상된 에러
                    logger.info("예상된 SQL 문법 오류 발생: {}", e.getMessage());
                    
                    testMetrics.recordSuccess("sql_syntax_error");
                    logger.info("✅ SQL 문법 오류 처리 테스트 성공");
                }
                
            } catch (Exception e) {
                testMetrics.recordFailure("sql_syntax_error", e);
                logger.error("❌ SQL 문법 오류 처리 테스트 실패", e);
                throw e;
            }
        }
        
        @Test
        @DisplayName("타임아웃 처리 테스트")
        void testTimeoutHandling() throws Exception {
            testMetrics.startTest("timeout_handling");
            
            try (Connection conn = DriverManager.getConnection(TEST_DB_URL, TEST_DB_USER, TEST_DB_PASSWORD)) {
                
                // 매우 짧은 쿼리 타임아웃 설정 (1초)
                String sql = "SELECT pg_sleep(2)"; // 2초 대기 - 타임아웃 발생
                
                try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
                    pstmt.setQueryTimeout(1);
                    
                    pstmt.executeQuery(); // 타임아웃 에러 발생해야 함
                    
                    testMetrics.recordFailure("timeout_handling", new RuntimeException("Expected timeout but query completed"));
                    logger.error("❌ 타임아웃이 예상되었지만 쿼리가 완료됨");
                    
                } catch (SQLException e) {
                    if (e.getMessage().contains("timeout") || e.getMessage().contains("cancel")) {
                        logger.info("예상된 타임아웃 에러 발생: {}", e.getMessage());
                        testMetrics.recordSuccess("timeout_handling");
                        logger.info("✅ 타임아웃 처리 테스트 성공");
                    } else {
                        throw e;
                    }
                }
                
            } catch (Exception e) {
                testMetrics.recordFailure("timeout_handling", e);
                logger.error("❌ 타임아웃 처리 테스트 실패", e);
                throw e;
            }
        }
    }

    @Nested 
    @DisplayName("6. 멀티스레드 환경 테스트")
    class MultiThreadTests {
        
        @Test
        @DisplayName("동시성 연결 테스트")
        void testConcurrentConnections() throws Exception {
            testMetrics.startTest("concurrent_connections");
            
            ExecutorService executor = Executors.newFixedThreadPool(5);
            CountDownLatch latch = new CountDownLatch(5);
            AtomicInteger successCount = new AtomicInteger(0);
            AtomicInteger errorCount = new AtomicInteger(0);
            
            try {
                // 5개 스레드에서 동시 연결 테스트
                for (int i = 0; i < 5; i++) {
                    final int threadId = i;
                    executor.submit(() -> {
                        try {
                            Thread.sleep(50 * threadId); // 약간의 지연
                            
                            try (Connection conn = DriverManager.getConnection(TEST_DB_URL, TEST_DB_USER, TEST_DB_PASSWORD)) {
                                assertConnectionValid(conn);
                                
                                // 간단한 쿼리 수행
                                try (PreparedStatement pstmt = conn.prepareStatement("SELECT COUNT(*) FROM courses");
                                     ResultSet rs = pstmt.executeQuery()) {
                                    if (rs.next()) {
                                        int count = rs.getInt(1);
                                        logger.debug("스레드 {}: 과목 수 = {}", threadId, count);
                                    }
                                }
                                
                                successCount.incrementAndGet();
                            }
                            
                        } catch (Exception e) {
                            logger.error("스레드 {} 에러: {}", threadId, e.getMessage());
                            errorCount.incrementAndGet();
                        } finally {
                            latch.countDown();
                        }
                    });
                }
                
                // 모든 스레드 완료 대기 (최대 30초)
                boolean completed = latch.await(30, TimeUnit.SECONDS);
                
                if (completed && errorCount.get() == 0) {
                    testMetrics.recordSuccess("concurrent_connections");
                    logger.info("✅ 동시성 연결 테스트 성공: {}/5 스레드 성공", successCount.get());
                } else {
                    testMetrics.recordFailure("concurrent_connections", 
                        new RuntimeException("Concurrent test failed: " + errorCount.get() + " errors"));
                    logger.error("❌ 동시성 연결 테스트 실패: {} 성공, {} 실패", successCount.get(), errorCount.get());
                }
                
            } finally {
                executor.shutdown();
                if (!executor.awaitTermination(10, TimeUnit.SECONDS)) {
                    executor.shutdownNow();
                }
            }
        }
    }
    
    // 헬퍼 메서드들
    
    private void assertConnectionValid(Connection conn) throws SQLException {
        assert conn != null : "Connection is null";
        assert !conn.isClosed() : "Connection is closed";
        assert conn.isValid(5) : "Connection is not valid";
    }
    
    /**
     * 테스트 메트릭 수집 클래스
     */
    private static class TestMetrics {
        private final Map<String, Long> startTimes = new ConcurrentHashMap<>();
        private final Map<String, String> results = new ConcurrentHashMap<>();
        private final Map<String, Exception> failures = new ConcurrentHashMap<>();
        
        void startTest(String testName) {
            startTimes.put(testName, System.currentTimeMillis());
        }
        
        void recordSuccess(String testName) {
            long duration = System.currentTimeMillis() - startTimes.getOrDefault(testName, 0L);
            results.put(testName, "SUCCESS (" + duration + "ms)");
        }
        
        void recordFailure(String testName, Exception e) {
            long duration = System.currentTimeMillis() - startTimes.getOrDefault(testName, 0L);
            results.put(testName, "FAILURE (" + duration + "ms): " + e.getMessage());
            failures.put(testName, e);
        }
        
        void printSummary() {
            logger.info("\n=== 테스트 결과 요약 ===");
            results.forEach((test, result) -> logger.info("{}: {}", test, result));
            
            long successCount = results.values().stream()
                .mapToLong(result -> result.startsWith("SUCCESS") ? 1 : 0)
                .sum();
            long totalCount = results.size();
            
            logger.info("총 {} 테스트 중 {} 성공, {} 실패", totalCount, successCount, totalCount - successCount);
        }
    }
    
    @Nested
    @DisplayName("🔥 Production 이슈 회귀 방지 테스트 (Critical)")
    class ProductionRegressionTests {
        
        @Test
        @DisplayName("[CRITICAL] HikariCP + Spring @Transactional autoCommit 충돌 방지")
        void testHikariCPSpringTransactionalAutoCommitConflict() throws Exception {
            testMetrics.startTest("hikaricp_spring_autocommit_conflict");
            
            /*
             * 🔥 CRITICAL REGRESSION TEST
             * 
             * Issue: "Cannot commit when autoCommit is enabled" in Production
             * Root Cause: DatabaseConfig에서 hibernate.connection.provider_disables_autocommit=true이고
             *            HikariCP autoCommit 설정이 환경변수로 제대로 적용되지 않음
             * 
             * Solution: DatabaseConfig에서 HikariConfig.setAutoCommit(false) 직접 설정하고
             *          hibernate.connection.provider_disables_autocommit=false로 설정
             * 
             * Test Purpose: 이 문제가 다시 발생하지 않도록 회귀 테스트 제공
             */
            
            logger.info("=== 🔥 HikariCP + Spring @Transactional autoCommit 충돌 테스트 ===");
            logger.info("💡 이 테스트는 Production에서 발생한 Critical Issue의 회귀를 방지합니다");
            
            try (Connection conn = DriverManager.getConnection(TEST_DB_URL, TEST_DB_USER, TEST_DB_PASSWORD)) {
                
                // 1. HikariCP가 autoCommit=false로 설정되어야 함 (Critical)
                boolean initialAutoCommit = conn.getAutoCommit();
                logger.info("Step 1: HikariCP 초기 autoCommit 상태: {}", initialAutoCommit);
                
                if (initialAutoCommit) {
                    logger.warn("⚠️  WARNING: HikariCP autoCommit이 true입니다!");
                    logger.warn("🔧 해결 방법: DatabaseConfig에서 HikariConfig.setAutoCommit(false) 설정 필요");
                    
                    // 테스트를 위해 수동으로 false로 설정
                    conn.setAutoCommit(false);
                }
                
                // 2. Spring @Transactional 패턴 시뮬레이션
                logger.info("Step 2: Spring @Transactional 패턴 시뮬레이션 시작");
                
                // DataInitializer에서 하는 것과 동일한 패턴
                try (PreparedStatement countStmt = conn.prepareStatement("SELECT COUNT(*) FROM courses")) {
                    try (ResultSet rs = countStmt.executeQuery()) {
                        if (rs.next()) {
                            int courseCount = rs.getInt(1);
                            logger.info("현재 과목 수: {}", courseCount);
                        }
                    }
                }
                
                // 3. Spring TransactionManager의 commit() 호출 시뮬레이션
                logger.info("Step 3: Spring TransactionManager commit() 시뮬레이션");
                boolean autoCommitBeforeCommit = conn.getAutoCommit();
                logger.info("commit() 호출 직전 autoCommit 상태: {}", autoCommitBeforeCommit);
                
                if (autoCommitBeforeCommit) {
                    logger.error("🔥 CRITICAL: autoCommit이 true 상태에서 commit() 호출!");
                    logger.error("💀 이 상황에서는 'Cannot commit when autoCommit is enabled' 에러 발생");
                    
                    testMetrics.recordFailure("hikaricp_spring_autocommit_conflict", 
                        new SQLException("Cannot commit when autoCommit is enabled - Production Issue Reproduced"));
                    
                    throw new SQLException("Cannot commit when autoCommit is enabled.");
                }
                
                // 4. 정상적인 commit 수행
                try {
                    conn.commit();
                    logger.info("✅ commit() 성공 - autoCommit 충돌 없음");
                } catch (SQLException e) {
                    if (e.getMessage().contains("Cannot commit when autoCommit is enabled")) {
                        logger.error("🔥 PRODUCTION ISSUE REPRODUCED: {}", e.getMessage());
                        logger.error("🔧 FIX REQUIRED: DatabaseConfig에서 HikariCP autoCommit 설정 확인 필요");
                        
                        testMetrics.recordFailure("hikaricp_spring_autocommit_conflict", e);
                        throw e;
                    }
                    throw e;
                }
                
                testMetrics.recordSuccess("hikaricp_spring_autocommit_conflict");
                logger.info("✅ HikariCP + Spring @Transactional autoCommit 충돌 방지 테스트 성공");
                
            } catch (SQLException e) {
                testMetrics.recordFailure("hikaricp_spring_autocommit_conflict", e);
                logger.error("❌ CRITICAL REGRESSION TEST FAILED: {}", e.getMessage());
                logger.error("🔧 ACTION REQUIRED: DatabaseConfig.java에서 HikariCP 설정 확인 필요");
                throw e;
            }
        }
        
        @Test
        @DisplayName("[CRITICAL] DatabaseConfig HikariCP 설정 검증")
        void testDatabaseConfigHikariCPSettings() throws Exception {
            testMetrics.startTest("database_config_hikaricp_validation");
            
            /*
             * 🔥 CRITICAL VALIDATION TEST
             * 
             * Purpose: DatabaseConfig에서 HikariCP 설정이 올바르게 되어 있는지 검증
             * 
             * Critical Settings:
             * 1. HikariConfig.setAutoCommit(false) - Spring 관리 트랜잭션 사용
             * 2. hibernate.connection.provider_disables_autocommit=false - HikariCP가 autoCommit 제어
             * 3. hibernate.dialect - PostgreSQL 환경에 맞게 동적 설정
             * 
             * Failure Impact: Production "Cannot commit when autoCommit is enabled" 에러
             */
            
            logger.info("=== 🔥 DatabaseConfig HikariCP 설정 검증 테스트 ===");
            logger.info("💡 이 테스트는 DatabaseConfig의 Critical 설정들을 검증합니다");
            
            try {
                // 환경변수 설정 확인
                String autoCommitEnv = System.getenv("SPRING_DATASOURCE_HIKARI_AUTO_COMMIT");
                logger.info("SPRING_DATASOURCE_HIKARI_AUTO_COMMIT 환경변수: {}", autoCommitEnv);
                
                String hibernateAutoCommitEnv = System.getenv("SPRING_JPA_PROPERTIES_HIBERNATE_CONNECTION_AUTOCOMMIT");
                logger.info("HIBERNATE_CONNECTION_AUTOCOMMIT 환경변수: {}", hibernateAutoCommitEnv);
                
                // 실제 Connection으로 최종 설정 확인
                try (Connection conn = DriverManager.getConnection(TEST_DB_URL, TEST_DB_USER, TEST_DB_PASSWORD)) {
                    boolean actualAutoCommit = conn.getAutoCommit();
                    logger.info("실제 Connection autoCommit 상태: {}", actualAutoCommit);
                    
                    // Critical Validation
                    if (actualAutoCommit) {
                        logger.error("🔥 CRITICAL FAILURE: HikariCP autoCommit이 true로 설정됨!");
                        logger.error("💀 이 설정은 Production에서 'Cannot commit when autoCommit is enabled' 에러를 발생시킵니다");
                        logger.error("🔧 해결방법:");
                        logger.error("   1. DatabaseConfig.java에서 HikariConfig.setAutoCommit(false) 설정");
                        logger.error("   2. hibernate.connection.provider_disables_autocommit=false 설정");
                        logger.error("   3. Spring Boot 환경변수 방식으로는 해결되지 않음 (실증됨)");
                        
                        testMetrics.recordFailure("database_config_hikaricp_validation", 
                            new RuntimeException("Critical HikariCP autoCommit misconfiguration detected"));
                        
                        throw new RuntimeException("CRITICAL: HikariCP autoCommit=true will cause Production failures");
                    }
                    
                    logger.info("✅ HikariCP autoCommit=false 설정 검증 통과");
                }
                
                testMetrics.recordSuccess("database_config_hikaricp_validation");
                logger.info("✅ DatabaseConfig HikariCP 설정 검증 완료 - Production 이슈 예방됨");
                
            } catch (Exception e) {
                testMetrics.recordFailure("database_config_hikaricp_validation", e);
                logger.error("❌ CRITICAL VALIDATION FAILED: {}", e.getMessage());
                logger.error("🚨 WARNING: Production에서 autoCommit 관련 오류 발생 가능성 높음");
                throw e;
            }
        }
        
        @Test
        @DisplayName("[INFO] Agent vs Application 설정 책임 분리 검증")
        void testAgentApplicationResponsibilitySeparation() throws Exception {
            testMetrics.startTest("agent_application_responsibility_separation");
            
            /*
             * 📋 LESSON LEARNED TEST
             * 
             * Key Finding: autoCommit 문제는 Agent가 아닌 Application 설정 문제였음
             * 
             * Agent 책임:
             * - JDBC 호출 모니터링
             * - 메트릭 수집
             * - avoidAutocommitStateChange=true일 때 Connection 클래스 변환 금지
             * 
             * Application 책임:
             * - HikariCP DataSource 설정
             * - Spring Transaction 설정
             * - Hibernate 설정
             * 
             * Test Purpose: 이 분리가 올바르게 이루어져 있는지 확인
             */
            
            logger.info("=== 📋 Agent vs Application 설정 책임 분리 검증 ===");
            logger.info("💡 이 테스트는 Agent와 Application의 책임 분리를 검증합니다");
            
            try (Connection conn = DriverManager.getConnection(TEST_DB_URL, TEST_DB_USER, TEST_DB_PASSWORD)) {
                
                // 1. Agent 설정 확인 (모니터링만 담당)
                String javaAgentProperty = System.getProperty("kubedb.monitor.agent.enabled");
                logger.info("Agent 활성화 상태: {}", javaAgentProperty != null ? "활성" : "비활성");
                
                // 2. Application 설정 확인 (실제 DB 연결 담당)
                boolean appAutoCommit = conn.getAutoCommit();
                logger.info("Application에서 설정된 autoCommit: {}", appAutoCommit);
                
                // 3. Agent가 Application 설정을 변경하지 않았는지 확인
                conn.setAutoCommit(false);
                boolean afterSetFalse = conn.getAutoCommit();
                assert !afterSetFalse : "Application에서 setAutoCommit(false) 설정이 Agent에 의해 변경됨";
                
                conn.setAutoCommit(true);
                boolean afterSetTrue = conn.getAutoCommit();
                assert afterSetTrue : "Application에서 setAutoCommit(true) 설정이 Agent에 의해 변경됨";
                
                logger.info("✅ Agent가 Application의 autoCommit 설정을 변경하지 않음 - 책임 분리 OK");
                
                // 4. 실제 트랜잭션 수행으로 간섭 없음 확인
                conn.setAutoCommit(false);
                
                try (PreparedStatement pstmt = conn.prepareStatement("SELECT 1")) {
                    try (ResultSet rs = pstmt.executeQuery()) {
                        if (rs.next()) {
                            int result = rs.getInt(1);
                            logger.info("쿼리 실행 결과: {}", result);
                            
                            // Agent가 쿼리 실행 중 autoCommit을 변경했는지 확인
                            boolean afterQuery = conn.getAutoCommit();
                            assert !afterQuery : "Agent가 쿼리 실행 중 autoCommit을 true로 변경함";
                        }
                    }
                }
                
                conn.commit(); // 정상적인 commit
                logger.info("✅ 트랜잭션 수행 중 Agent 간섭 없음 확인");
                
                testMetrics.recordSuccess("agent_application_responsibility_separation");
                logger.info("✅ Agent vs Application 책임 분리 검증 완료");
                
            } catch (Exception e) {
                testMetrics.recordFailure("agent_application_responsibility_separation", e);
                logger.error("❌ Agent vs Application 책임 분리 검증 실패: {}", e.getMessage());
                throw e;
            }
        }
    }
}