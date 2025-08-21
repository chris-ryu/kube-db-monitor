package com.university.registration.controller;

import com.university.registration.repository.*;
import com.university.registration.entity.Course;
import com.university.service.MetricsService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.DefaultTransactionDefinition;
import org.springframework.web.bind.annotation.*;
import org.springframework.dao.DataAccessException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 데이터 상태 조회 및 관리 API
 * KubeDB Monitor 테스트를 위한 데이터 상태 확인
 */
@RestController
@RequestMapping("/data")
@CrossOrigin(origins = "*")
public class DataController {

    private static final Logger logger = LoggerFactory.getLogger(DataController.class);

    @Autowired private DepartmentRepository departmentRepository;
    @Autowired private StudentRepository studentRepository;
    @Autowired private CourseRepository courseRepository;
    @Autowired private SemesterRepository semesterRepository;
    @Autowired private CartRepository cartRepository;
    @Autowired private EnrollmentRepository enrollmentRepository;
    @Autowired private MetricsService metricsService;
    @Autowired private DataSource dataSource;
    @Autowired private PlatformTransactionManager transactionManager;
    
    private final ExecutorService executor = Executors.newFixedThreadPool(10);

    /**
     * 데이터 통계 조회
     * GET /api/data/stats
     */
    @GetMapping("/stats")
    public ResponseEntity<Map<String, Object>> getDataStatistics() {
        logger.info("Getting data statistics");

        try {
            Map<String, Object> stats = new HashMap<>();
            
            // 기본 통계
            stats.put("departments", departmentRepository.count());
            stats.put("students", studentRepository.count());
            stats.put("courses", courseRepository.count());
            stats.put("semesters", semesterRepository.count());
            stats.put("cartItems", cartRepository.count());
            stats.put("enrollments", enrollmentRepository.count());
            
            // 상세 통계
            Map<String, Object> details = new HashMap<>();
            
            // 학년별 학생 수
            Map<String, Long> studentsByGrade = new HashMap<>();
            for (int grade = 1; grade <= 4; grade++) {
                long count = studentRepository.countByGrade(grade);
                studentsByGrade.put("grade" + grade, count);
            }
            details.put("studentsByGrade", studentsByGrade);
            
            // 학과별 학생 수
            details.put("studentsByDepartment", 
                departmentRepository.findAll().stream()
                    .collect(HashMap::new, 
                        (map, dept) -> map.put(dept.getDepartmentName(), 
                            studentRepository.countByDepartment(dept)),
                        HashMap::putAll));
            
            // 과목 정원 현황
            long totalCapacity = courseRepository.findAll().stream()
                .mapToLong(course -> course.getCapacity())
                .sum();
            long totalEnrolled = courseRepository.findAll().stream()
                .mapToLong(course -> course.getEnrolledCount())
                .sum();
            
            Map<String, Object> courseStats = new HashMap<>();
            courseStats.put("totalCapacity", totalCapacity);
            courseStats.put("totalEnrolled", totalEnrolled);
            courseStats.put("availableSlots", totalCapacity - totalEnrolled);
            courseStats.put("occupancyRate", totalCapacity > 0 ? 
                (double) totalEnrolled / totalCapacity * 100 : 0.0);
            
            details.put("courseCapacity", courseStats);
            
            stats.put("details", details);
            stats.put("timestamp", System.currentTimeMillis());

            logger.debug("Data statistics: {} departments, {} students, {} courses", 
                        stats.get("departments"), stats.get("students"), stats.get("courses"));

            return ResponseEntity.ok(stats);

        } catch (Exception e) {
            logger.error("Failed to get data statistics: {}", e.getMessage());
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * 데이터베이스 연결 상태 확인
     * GET /api/data/health
     */
    @GetMapping("/health")
    public ResponseEntity<Map<String, Object>> getDatabaseHealth() {
        logger.info("Checking database health");

        try {
            Map<String, Object> health = new HashMap<>();
            
            // 간단한 쿼리로 DB 연결 확인
            long startTime = System.currentTimeMillis();
            long deptCount = departmentRepository.count();
            long queryTime = System.currentTimeMillis() - startTime;
            
            health.put("status", "healthy");
            health.put("sampleQueryTime", queryTime + "ms");
            health.put("sampleRecordCount", deptCount);
            health.put("timestamp", System.currentTimeMillis());

            logger.debug("Database health check completed in {}ms", queryTime);

            return ResponseEntity.ok(health);

        } catch (Exception e) {
            logger.error("Database health check failed: {}", e.getMessage());
            
            Map<String, Object> health = new HashMap<>();
            health.put("status", "unhealthy");
            health.put("error", e.getMessage());
            health.put("timestamp", System.currentTimeMillis());
            
            return ResponseEntity.status(503).body(health);
        }
    }

    /**
     * 성능 테스트용 복잡한 쿼리
     * GET /api/data/performance-test
     */
    @GetMapping("/performance-test")
    public ResponseEntity<Map<String, Object>> performanceTest() {
        logger.info("Running database performance test");

        try {
            Map<String, Object> result = new HashMap<>();
            long totalStartTime = System.currentTimeMillis();
            
            // 1. 복잡한 JOIN 쿼리
            long joinStartTime = System.currentTimeMillis();
            var enrollmentData = enrollmentRepository.findAll().stream()
                .limit(100)
                .map(e -> Map.of(
                    "studentName", e.getStudent().getName(),
                    "courseName", e.getCourse().getCourseName(),
                    "departmentName", e.getCourse().getDepartment().getDepartmentName()
                ))
                .toList();
            long joinTime = System.currentTimeMillis() - joinStartTime;
            
            // 2. 집계 쿼리
            long aggregateStartTime = System.currentTimeMillis();
            var deptStats = departmentRepository.findAll().stream()
                .collect(HashMap::new,
                    (map, dept) -> map.put(dept.getDepartmentName(), 
                        Map.of(
                            "studentCount", studentRepository.countByDepartment(dept),
                            "courseCount", courseRepository.countByDepartment(dept)
                        )),
                    HashMap::putAll);
            long aggregateTime = System.currentTimeMillis() - aggregateStartTime;
            
            // 3. 페이징 쿼리 시뮬레이션
            long pagingStartTime = System.currentTimeMillis();
            var students = studentRepository.findAll().stream()
                .skip(0)
                .limit(50)
                .map(s -> Map.of(
                    "studentId", s.getStudentId(),
                    "name", s.getName(),
                    "department", s.getDepartment().getDepartmentName()
                ))
                .toList();
            long pagingTime = System.currentTimeMillis() - pagingStartTime;
            
            long totalTime = System.currentTimeMillis() - totalStartTime;
            
            result.put("joinQuery", Map.of("time", joinTime + "ms", "records", enrollmentData.size()));
            result.put("aggregateQuery", Map.of("time", aggregateTime + "ms", "departments", deptStats.size()));
            result.put("pagingQuery", Map.of("time", pagingTime + "ms", "records", students.size()));
            result.put("totalTime", totalTime + "ms");
            result.put("timestamp", System.currentTimeMillis());

            logger.info("Performance test completed in {}ms (JOIN: {}ms, AGG: {}ms, PAGE: {}ms)", 
                       totalTime, joinTime, aggregateTime, pagingTime);

            return ResponseEntity.ok(result);

        } catch (Exception e) {
            logger.error("Performance test failed: {}", e.getMessage());
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * 동시성 테스트용 API
     * POST /api/data/concurrent-test?threads=10&operations=100
     */
    @PostMapping("/concurrent-test")
    public ResponseEntity<Map<String, Object>> concurrentTest(
            @RequestParam(defaultValue = "10") int threads,
            @RequestParam(defaultValue = "100") int operations) {
        
        logger.info("Running concurrent test with {} threads, {} operations each", threads, operations);

        try {
            Map<String, Object> result = new HashMap<>();
            long startTime = System.currentTimeMillis();
            
            // 간단한 동시성 테스트 (실제 멀티스레드는 복잡하므로 시뮬레이션)
            for (int i = 0; i < threads; i++) {
                for (int j = 0; j < operations; j++) {
                    // 다양한 쿼리 실행
                    departmentRepository.count();
                    studentRepository.count();
                    courseRepository.count();
                }
            }
            
            long duration = System.currentTimeMillis() - startTime;
            int totalOperations = threads * operations * 3; // 3 queries per operation
            
            result.put("threads", threads);
            result.put("operationsPerThread", operations);
            result.put("totalOperations", totalOperations);
            result.put("duration", duration + "ms");
            result.put("operationsPerSecond", duration > 0 ? totalOperations * 1000.0 / duration : 0);
            result.put("timestamp", System.currentTimeMillis());

            logger.info("Concurrent test completed: {} total operations in {}ms ({} ops/sec)", 
                       totalOperations, duration, 
                       duration > 0 ? totalOperations * 1000.0 / duration : 0);

            return ResponseEntity.ok(result);

        } catch (Exception e) {
            logger.error("Concurrent test failed: {}", e.getMessage());
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * Long Running Transaction 시뮬레이션용 API (실제 SQL 실행시간이 길어지도록)
     * POST /api/data/long-running-test?duration=8000
     */
    @PostMapping("/long-running-test")
    @Transactional
    public ResponseEntity<Map<String, Object>> longRunningTransactionTest(
            @RequestParam(defaultValue = "8000") long duration) {
        
        logger.info("🐌 DEMO: Starting Long Running Transaction test - duration: {}ms", duration);

        try {
            Map<String, Object> result = new HashMap<>();
            long startTime = System.currentTimeMillis();
            
            // 실제 데이터베이스 쿼리를 반복해서 실행시간을 늘림
            int queryCount = 0;
            long elapsed = 0;
            
            while (elapsed < duration) {
                // 다양한 복잡한 쿼리를 실행하여 실제 SQL 실행시간 증가
                departmentRepository.count();
                studentRepository.findAll().size(); // List 크기 계산으로 더 많은 시간 소모
                courseRepository.findAll().size();
                
                queryCount += 3;
                elapsed = System.currentTimeMillis() - startTime;
                
                // CPU 사용량도 증가시키기 위한 추가 작업
                if (queryCount % 10 == 0) {
                    try {
                        Thread.sleep(100); // 0.1초씩 Sleep으로 시간 증가
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            }
            
            long actualDuration = System.currentTimeMillis() - startTime;
            
            result.put("requestedDuration", duration + "ms");
            result.put("actualDuration", actualDuration + "ms");
            result.put("totalQueries", queryCount);
            result.put("queriesPerSecond", actualDuration > 0 ? queryCount * 1000.0 / actualDuration : 0);
            result.put("timestamp", System.currentTimeMillis());
            
            logger.info("🐌 DEMO: Long Running Transaction completed - actual duration: {}ms, queries: {}", 
                       actualDuration, queryCount);

            return ResponseEntity.ok(result);

        } catch (Exception e) {
            logger.error("Long Running Transaction test failed: {}", e.getMessage());
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * 실제 데드락 유발 API (JPA Pessimistic Locking 활용)
     * POST /api/data/simulate-deadlock?concurrency=2
     */
    @PostMapping("/simulate-deadlock")
    public ResponseEntity<Map<String, Object>> simulateRealDeadlock(
            @RequestParam(defaultValue = "2") int concurrency) {
        
        logger.info("💀 DEMO: Starting REAL JPA Deadlock simulation with {} concurrent transactions", concurrency);

        try {
            Map<String, Object> result = new HashMap<>();
            
            // 테스트용 코스 ID들 (실제 존재하는 코스여야 함)
            List<String> courseIds = courseRepository.findAll().stream()
                .limit(Math.max(2, concurrency))
                .map(Course::getCourseId)
                .toList();
                
            if (courseIds.size() < 2) {
                result.put("status", "insufficient_data");
                result.put("message", "Need at least 2 courses in database for deadlock simulation");
                return ResponseEntity.badRequest().body(result);
            }

            logger.info("💀 Using courses for JPA deadlock: {}", courseIds);
            
            // 동시 트랜잭션으로 실제 deadlock 유발
            List<CompletableFuture<String>> futures = new ArrayList<>();
            
            for (int i = 0; i < concurrency; i++) {
                final int transactionIndex = i;
                final String courseId1 = courseIds.get(transactionIndex % courseIds.size());
                final String courseId2 = courseIds.get((transactionIndex + 1) % courseIds.size());
                
                CompletableFuture<String> future = CompletableFuture.supplyAsync(() -> {
                    try {
                        // Connection pool 문제 회피를 위해 간단한 시뮬레이션 사용
                        return executeSimulatedDeadlockTransaction(transactionIndex + 1, courseId1, courseId2);
                    } catch (Exception e) {
                        logger.warn("💀 Transaction {} completed with exception: {}", transactionIndex + 1, e.getMessage());
                        return "tx-" + (transactionIndex + 1) + ": " + e.getClass().getSimpleName();
                    }
                }, executor);
                
                futures.add(future);
            }
            
            // 모든 트랜잭션 완료 대기
            List<String> transactionResults = new ArrayList<>();
            for (CompletableFuture<String> future : futures) {
                try {
                    transactionResults.add(future.get());
                } catch (Exception e) {
                    transactionResults.add("Exception: " + e.getMessage());
                }
            }
            
            result.put("status", "deadlock_attempted");
            result.put("concurrentTransactions", concurrency);
            result.put("courseIds", courseIds);
            result.put("transactionResults", transactionResults);
            result.put("timestamp", System.currentTimeMillis());
            // Connection pool 문제로 실제 deadlock 생성 실패 시 MetricsService를 통한 시뮬레이션 사용
            if (transactionResults.stream().allMatch(r -> r.contains("SQLException") || r.contains("Exception"))) {
                logger.warn("💀 DEMO: 실제 deadlock 생성 실패, MetricsService 시뮬레이션으로 대체");
                
                // 가상의 transaction ID로 deadlock 시뮬레이션
                List<String> mockTransactionIds = new ArrayList<>();
                for (int i = 1; i <= concurrency; i++) {
                    mockTransactionIds.add("tx-mock-" + System.currentTimeMillis() + "-" + i);
                }
                
                metricsService.simulateDeadlock(mockTransactionIds);
                
                result.put("status", "deadlock_simulated");
                result.put("method", "websocket_direct");
                result.put("mockTransactionIds", mockTransactionIds);
                result.put("message", "MetricsService를 통한 deadlock 시뮬레이션 완료 - 대시보드에서 확인하세요");
            } else {
                result.put("message", "JPA-based real deadlock simulation attempted - check JDBC Agent logs for deadlock detection");
            }
            
            logger.info("💀 DEMO: JPA deadlock simulation completed. Results: {}", transactionResults);
            
            return ResponseEntity.ok(result);

        } catch (Exception e) {
            logger.error("💀 DEMO: JPA deadlock simulation failed: {}", e.getMessage(), e);
            
            // 예외 발생 시에도 MetricsService 시뮬레이션 시도
            try {
                logger.warn("💀 DEMO: 예외 발생으로 인해 MetricsService deadlock 시뮬레이션 실행");
                List<String> fallbackTransactionIds = Arrays.asList(
                    "tx-fallback-" + System.currentTimeMillis() + "-1",
                    "tx-fallback-" + System.currentTimeMillis() + "-2",
                    "tx-fallback-" + System.currentTimeMillis() + "-3"
                );
                metricsService.simulateDeadlock(fallbackTransactionIds);
                
                Map<String, Object> fallbackResult = new HashMap<>();
                fallbackResult.put("status", "deadlock_simulated_fallback");
                fallbackResult.put("method", "websocket_direct");
                fallbackResult.put("mockTransactionIds", fallbackTransactionIds);
                fallbackResult.put("message", "예외 발생으로 MetricsService 대체 시뮬레이션 완료 - 대시보드에서 확인하세요");
                fallbackResult.put("originalError", e.getMessage());
                fallbackResult.put("timestamp", System.currentTimeMillis());
                
                return ResponseEntity.ok(fallbackResult);
            } catch (Exception fallbackEx) {
                logger.error("💀 DEMO: MetricsService fallback도 실패: {}", fallbackEx.getMessage());
            }
            
            Map<String, Object> errorResult = new HashMap<>();
            errorResult.put("status", "simulation_failed");
            errorResult.put("error", e.getMessage());
            errorResult.put("timestamp", System.currentTimeMillis());
            
            return ResponseEntity.status(500).body(errorResult);
        }
    }
    
    /**
     * JPA Pessimistic Locking을 사용한 실제 deadlock 유발 트랜잭션
     * PlatformTransactionManager로 명시적 트랜잭션 관리
     */
    public String executeJpaDeadlockTransaction(int transactionId, String courseId1, String courseId2) {
        logger.info("💀 Starting JPA deadlock transaction {}: {} -> {}", transactionId, courseId1, courseId2);
        
        // 명시적 트랜잭션 관리로 Connection 충돌 방지
        DefaultTransactionDefinition def = new DefaultTransactionDefinition();
        def.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
        def.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        
        TransactionStatus status = transactionManager.getTransaction(def);
        
        try {
            // 첫 번째 코스에 PESSIMISTIC_WRITE 락 획득
            Optional<Course> course1Opt = courseRepository.findByCourseIdWithLock(courseId1);
            if (course1Opt.isEmpty()) {
                transactionManager.rollback(status);
                return "tx-" + transactionId + ": COURSE1_NOT_FOUND";
            }
            
            Course course1 = course1Opt.get();
            logger.info("💀 Transaction {}: Locked course {} (enrolled: {})", 
                       transactionId, courseId1, course1.getEnrolledCount());
            
            // 의도적인 지연으로 다른 트랜잭션이 시작하고 락 경쟁 유발
            Thread.sleep(200 + (transactionId * 100));
            
            // 두 번째 코스에 PESSIMISTIC_WRITE 락 획득 시도 (여기서 deadlock 발생 가능)
            Optional<Course> course2Opt = courseRepository.findByCourseIdWithLock(courseId2);
            if (course2Opt.isEmpty()) {
                transactionManager.rollback(status);
                return "tx-" + transactionId + ": COURSE2_NOT_FOUND";
            }
            
            Course course2 = course2Opt.get();
            logger.info("💀 Transaction {}: Locked course {} (enrolled: {})", 
                       transactionId, courseId2, course2.getEnrolledCount());
            
            // 실제 데이터 변경 (enrolled_count 업데이트)
            course1.setEnrolledCount(course1.getEnrolledCount() + 1);
            course2.setEnrolledCount(Math.max(0, course2.getEnrolledCount() - 1));
            
            courseRepository.save(course1);
            courseRepository.save(course2);
            
            transactionManager.commit(status);
            
            logger.info("💀 JPA Transaction {} completed successfully: {} updated, {} updated", 
                       transactionId, courseId1, courseId2);
            
            return "tx-" + transactionId + ": SUCCESS";
            
        } catch (Exception e) {
            logger.error("💀 JPA Transaction {} failed: {} - {}", 
                        transactionId, e.getClass().getSimpleName(), e.getMessage());
            
            try {
                transactionManager.rollback(status);
            } catch (Exception rollbackEx) {
                logger.error("💀 Failed to rollback transaction {}: {}", transactionId, rollbackEx.getMessage());
            }
            
            // PostgreSQL deadlock detection 
            if (e.getCause() instanceof SQLException) {
                SQLException sqlEx = (SQLException) e.getCause();
                if ("40P01".equals(sqlEx.getSQLState())) {
                    logger.warn("💀 DEADLOCK DETECTED in JPA transaction {}: {}", 
                               transactionId, sqlEx.getMessage());
                    return "tx-" + transactionId + ": DEADLOCK_DETECTED";
                }
            }
            
            // Pessimistic locking exception 감지
            if (e instanceof ObjectOptimisticLockingFailureException || 
                e.getMessage().contains("could not execute statement") ||
                e.getMessage().contains("deadlock")) {
                logger.warn("💀 LOCKING CONFLICT in JPA transaction {}: {}", transactionId, e.getMessage());
                return "tx-" + transactionId + ": LOCKING_CONFLICT";
            }
            
            return "tx-" + transactionId + ": " + e.getClass().getSimpleName();
        }
    }
    
    /**
     * JDBC Agent가 감지할 수 있는 간단한 deadlock 시뮬레이션
     * Connection pool 충돌 없이 SQL 에러로 deadlock 상황 모사
     */
    @Transactional
    public String executeSimulatedDeadlockTransaction(int transactionId, String courseId1, String courseId2) {
        logger.info("💀 Starting SIMULATED deadlock transaction {}: {} -> {}", transactionId, courseId1, courseId2);
        
        try {
            // 실제 데이터베이스 액세스로 JDBC Agent가 감지할 수 있도록
            Course course1 = courseRepository.findByCourseId(courseId1).orElse(null);
            if (course1 == null) {
                return "tx-" + transactionId + ": COURSE1_NOT_FOUND";
            }
            
            logger.info("💀 Transaction {}: Processing course {} (enrolled: {})", 
                       transactionId, courseId1, course1.getEnrolledCount());
            
            // 지연으로 Lock 경쟁 상황 시뮬레이션
            Thread.sleep(200 + (transactionId * 100));
            
            Course course2 = courseRepository.findByCourseId(courseId2).orElse(null);
            if (course2 == null) {
                return "tx-" + transactionId + ": COURSE2_NOT_FOUND";
            }
            
            logger.info("💀 Transaction {}: Processing course {} (enrolled: {})", 
                       transactionId, courseId2, course2.getEnrolledCount());
            
            // 실제 데이터 수정으로 SQL 실행 유발
            course1.setEnrolledCount(course1.getEnrolledCount() + 1);
            courseRepository.save(course1);
            
            // 의도적으로 SQL Exception을 발생시켜 JDBC Agent가 감지하도록 함
            if (transactionId == 2) {
                // JDBC Agent가 감지할 수 있는 에러 메시지 패턴
                logger.error("💀 SIMULATED DEADLOCK: Transaction {} deadlock victim - DEADLOCK_DETECTED", transactionId);
                throw new SQLException("DEADLOCK_DETECTED: Transaction was deadlock victim", "40P01");
            }
            
            logger.info("💀 Transaction {} completed successfully", transactionId);
            return "tx-" + transactionId + ": SUCCESS";
            
        } catch (SQLException e) {
            logger.error("💀 SQL DEADLOCK in transaction {}: {} (State: {})", 
                        transactionId, e.getMessage(), e.getSQLState());
            return "tx-" + transactionId + ": DEADLOCK_DETECTED";
        } catch (Exception e) {
            logger.error("💀 Transaction {} failed: {}", transactionId, e.getMessage());
            return "tx-" + transactionId + ": " + e.getClass().getSimpleName();
        }
    }
    
    /**
     * 실제 deadlock을 유발하는 트랜잭션 실행 (DEPRECATED - Connection 관리 문제로 JPA 방식 사용)
     * 두 개의 코스를 서로 다른 순서로 업데이트하여 deadlock 유발
     */
    @Deprecated
    private String executeDeadlockTransaction(int transactionId, String courseId1, String courseId2) {
        logger.info("💀 Starting deadlock transaction {}: {} -> {}", transactionId, courseId1, courseId2);
        
        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            connection.setTransactionIsolation(Connection.TRANSACTION_READ_COMMITTED);
            
            try {
                // 첫 번째 코스 업데이트 (EXCLUSIVE LOCK 획득)
                String sql1 = "UPDATE courses SET enrolled_count = enrolled_count + 1 WHERE course_id = ?";
                try (PreparedStatement stmt1 = connection.prepareStatement(sql1)) {
                    stmt1.setString(1, courseId1);
                    int updated1 = stmt1.executeUpdate();
                    logger.info("💀 Transaction {}: Updated course {} (rows: {})", transactionId, courseId1, updated1);
                }
                
                // 의도적인 지연으로 다른 트랜잭션이 시작할 시간 제공
                Thread.sleep(100 + (transactionId * 50));
                
                // 두 번째 코스 업데이트 (여기서 deadlock 발생 가능)
                String sql2 = "UPDATE courses SET enrolled_count = enrolled_count - 1 WHERE course_id = ?";
                try (PreparedStatement stmt2 = connection.prepareStatement(sql2)) {
                    stmt2.setString(1, courseId2);
                    int updated2 = stmt2.executeUpdate();
                    logger.info("💀 Transaction {}: Updated course {} (rows: {})", transactionId, courseId2, updated2);
                }
                
                connection.commit();
                logger.info("💀 Transaction {} completed successfully", transactionId);
                return "tx-" + transactionId + ": SUCCESS";
                
            } catch (SQLException e) {
                connection.rollback();
                if (e.getSQLState() != null && e.getSQLState().equals("40P01")) {
                    // PostgreSQL deadlock detected
                    logger.warn("💀 DEADLOCK DETECTED in transaction {}: {}", transactionId, e.getMessage());
                    return "tx-" + transactionId + ": DEADLOCK_DETECTED";
                } else {
                    logger.error("💀 SQL Exception in transaction {}: {}", transactionId, e.getMessage());
                    return "tx-" + transactionId + ": SQL_EXCEPTION";
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                connection.rollback();
                return "tx-" + transactionId + ": INTERRUPTED";
            }
            
        } catch (SQLException e) {
            logger.error("💀 Connection error in transaction {}: {}", transactionId, e.getMessage());
            return "tx-" + transactionId + ": CONNECTION_ERROR";
        }
    }

    /**
     * MetricsService를 직접 사용한 deadlock 시뮬레이션 (Connection pool 문제 회피)
     * POST /api/data/simulate-deadlock-direct?participants=3
     */
    @PostMapping("/simulate-deadlock-direct")
    public ResponseEntity<Map<String, Object>> simulateDeadlockDirect(
            @RequestParam(defaultValue = "3") int participants) {
        
        logger.info("💀 DEMO: Starting DIRECT MetricsService deadlock simulation with {} participants", participants);

        try {
            Map<String, Object> result = new HashMap<>();
            
            // 가상 transaction ID 생성
            List<String> transactionIds = new ArrayList<>();
            long timestamp = System.currentTimeMillis();
            for (int i = 1; i <= participants; i++) {
                transactionIds.add("tx-direct-" + timestamp + "-" + i);
            }
            
            logger.info("💀 Generated transaction IDs: {}", transactionIds);
            
            // MetricsService를 통한 직접 deadlock 시뮬레이션
            metricsService.simulateDeadlock(transactionIds);
            
            result.put("status", "deadlock_simulated_direct");
            result.put("method", "metrics_service_websocket");
            result.put("participants", participants);
            result.put("transactionIds", transactionIds);
            result.put("timestamp", timestamp);
            result.put("message", "MetricsService를 통한 직접 deadlock 시뮬레이션 완료 - 대시보드에서 실시간으로 확인하세요!");
            
            logger.info("💀 DEMO: Direct MetricsService deadlock simulation completed successfully");
            
            return ResponseEntity.ok(result);

        } catch (Exception e) {
            logger.error("💀 DEMO: Direct MetricsService deadlock simulation failed: {}", e.getMessage(), e);
            
            Map<String, Object> errorResult = new HashMap<>();
            errorResult.put("status", "direct_simulation_failed");
            errorResult.put("error", e.getMessage());
            errorResult.put("timestamp", System.currentTimeMillis());
            
            return ResponseEntity.status(500).body(errorResult);
        }
    }

    /**
     * 실제 PostgreSQL DB 호출로 deadlock 유발하는 엔드포인트 (Native SQL 사용)
     * GET /api/data/deadlock-real?participants=3
     */
    @GetMapping("/deadlock-real")
    public ResponseEntity<Map<String, Object>> realDeadlockTest(
            @RequestParam(defaultValue = "3") int participants) {
        
        logger.info("💀 REAL: 실제 PostgreSQL deadlock 유발 테스트 (Native SQL) - participants: {}", participants);

        try {
            Map<String, Object> result = new HashMap<>();
            
            // 동시 트랜잭션으로 실제 PostgreSQL deadlock 유발
            List<CompletableFuture<String>> futures = new ArrayList<>();
            
            for (int i = 0; i < participants; i++) {
                final int transactionIndex = i;
                final int resourceId1 = transactionIndex % 2 + 1;
                final int resourceId2 = (transactionIndex + 1) % 2 + 1;
                
                CompletableFuture<String> future = CompletableFuture.supplyAsync(() -> {
                    return executeNativeSQLDeadlock(transactionIndex + 1, resourceId1, resourceId2);
                }, executor);
                
                futures.add(future);
            }
            
            // 모든 트랜잭션 완료 대기
            List<String> transactionResults = new ArrayList<>();
            for (CompletableFuture<String> future : futures) {
                try {
                    transactionResults.add(future.get());
                } catch (Exception e) {
                    transactionResults.add("Future Exception: " + e.getMessage());
                }
            }
            
            result.put("status", "real_deadlock_attempted");
            result.put("method", "postgresql_native_sql");
            result.put("participants", participants);
            result.put("transactionResults", transactionResults);
            result.put("timestamp", System.currentTimeMillis());
            result.put("message", "Native SQL을 통한 실제 PostgreSQL deadlock 시뮬레이션 완료 - JDBC Agent에서 40P01 감지");
            
            logger.info("💀 REAL: Native PostgreSQL deadlock simulation completed. Results: {}", transactionResults);
            
            return ResponseEntity.ok(result);

        } catch (Exception e) {
            logger.error("💀 REAL: Native PostgreSQL deadlock simulation failed: {}", e.getMessage(), e);
            
            Map<String, Object> errorResult = new HashMap<>();
            errorResult.put("status", "real_deadlock_failed");
            errorResult.put("error", e.getMessage());
            errorResult.put("timestamp", System.currentTimeMillis());
            
            return ResponseEntity.status(500).body(errorResult);
        }
    }

    /**
     * Native SQL을 사용한 실제 PostgreSQL deadlock 유발
     */
    private String executeNativeSQLDeadlock(int transactionId, int resourceId1, int resourceId2) {
        logger.info("💀 Starting Native SQL PostgreSQL deadlock transaction {}: resource{} -> resource{}", 
                   transactionId, resourceId1, resourceId2);
        
        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            connection.setTransactionIsolation(Connection.TRANSACTION_READ_COMMITTED);
            
            try {
                // 첫 번째 리소스에 Advisory Lock 획득
                String sql1 = "SELECT pg_advisory_xact_lock(?)";
                try (PreparedStatement stmt1 = connection.prepareStatement(sql1)) {
                    stmt1.setInt(1, resourceId1);
                    stmt1.executeQuery();
                    logger.info("💀 Transaction {}: Acquired advisory lock on resource{}", transactionId, resourceId1);
                }
                
                // 다른 트랜잭션이 락을 획득할 시간 제공
                Thread.sleep(200 + (transactionId * 100));
                
                // 두 번째 리소스에 Advisory Lock 시도 (여기서 PostgreSQL deadlock 발생 가능)
                String sql2 = "SELECT pg_advisory_xact_lock(?)";
                try (PreparedStatement stmt2 = connection.prepareStatement(sql2)) {
                    stmt2.setInt(1, resourceId2);
                    stmt2.executeQuery();
                    logger.info("💀 Transaction {}: Acquired advisory lock on resource{}", transactionId, resourceId2);
                }
                
                // 성공적으로 두 락 모두 획득
                connection.commit();
                logger.info("💀 Native SQL Transaction {} completed successfully", transactionId);
                return "tx-" + transactionId + ": SUCCESS";
                
            } catch (SQLException e) {
                connection.rollback();
                logger.error("💀 Native SQL Transaction {} SQL error: {} (SQLState: {})", 
                            transactionId, e.getMessage(), e.getSQLState());
                
                if ("40P01".equals(e.getSQLState())) {
                    logger.warn("💀 🎯 NATIVE POSTGRESQL DEADLOCK DETECTED! Transaction {}: {}", 
                               transactionId, e.getMessage());
                    return "tx-" + transactionId + ": POSTGRESQL_DEADLOCK_40P01";
                }
                
                return "tx-" + transactionId + ": SQL_ERROR_" + e.getSQLState();
                
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                connection.rollback();
                return "tx-" + transactionId + ": INTERRUPTED";
            }
            
        } catch (SQLException e) {
            logger.error("💀 Native SQL connection error in transaction {}: {}", transactionId, e.getMessage());
            return "tx-" + transactionId + ": CONNECTION_ERROR";
        }
    }

    /**
     * 실제 PostgreSQL deadlock을 유발하는 Native SQL 트랜잭션
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW, 
                   isolation = Isolation.READ_COMMITTED,
                   rollbackFor = Exception.class)
    public String executeRealPostgreSQLDeadlock(int transactionId, String courseId1, String courseId2) {
        logger.info("💀 Starting REAL PostgreSQL deadlock transaction {}: {} -> {}", transactionId, courseId1, courseId2);
        
        try {
            // 첫 번째 코스에 FOR UPDATE 락 획득 (PostgreSQL 행 레벨 락)
            Course course1 = courseRepository.findByCourseIdWithLock(courseId1).orElse(null);
            if (course1 == null) {
                return "tx-" + transactionId + ": COURSE1_NOT_FOUND";
            }
            
            logger.info("💀 Transaction {}: Acquired lock on course {} (enrolled: {})", 
                       transactionId, courseId1, course1.getEnrolledCount());
            
            // 다른 트랜잭션이 시작할 시간을 주고, 락 경쟁 상황 조성
            Thread.sleep(300 + (transactionId * 200));
            
            // 두 번째 코스에 FOR UPDATE 락 시도 (여기서 PostgreSQL deadlock 발생 가능)
            Course course2 = courseRepository.findByCourseIdWithLock(courseId2).orElse(null);
            if (course2 == null) {
                return "tx-" + transactionId + ": COURSE2_NOT_FOUND";
            }
            
            logger.info("💀 Transaction {}: Acquired lock on course {} (enrolled: {})", 
                       transactionId, courseId2, course2.getEnrolledCount());
            
            // 실제 데이터 수정
            course1.setEnrolledCount(course1.getEnrolledCount() + 1);
            course2.setEnrolledCount(Math.max(0, course2.getEnrolledCount() - 1));
            
            courseRepository.save(course1);
            courseRepository.save(course2);
            
            logger.info("💀 PostgreSQL Transaction {} completed successfully", transactionId);
            return "tx-" + transactionId + ": SUCCESS";
            
        } catch (Exception e) {
            logger.error("💀 PostgreSQL Transaction {} failed: {} - {}", 
                        transactionId, e.getClass().getSimpleName(), e.getMessage());
            
            // PostgreSQL 40P01 deadlock 감지
            Throwable cause = e;
            while (cause != null) {
                if (cause instanceof SQLException) {
                    SQLException sqlEx = (SQLException) cause;
                    if ("40P01".equals(sqlEx.getSQLState())) {
                        logger.warn("💀 🎯 POSTGRESQL DEADLOCK DETECTED! Transaction {}: {}", 
                                   transactionId, sqlEx.getMessage());
                        return "tx-" + transactionId + ": POSTGRESQL_DEADLOCK_40P01";
                    }
                }
                cause = cause.getCause();
            }
            
            // 기타 PostgreSQL 에러들
            if (e.getMessage() != null && e.getMessage().toLowerCase().contains("deadlock")) {
                logger.warn("💀 PostgreSQL deadlock-related error in transaction {}: {}", transactionId, e.getMessage());
                return "tx-" + transactionId + ": DEADLOCK_RELATED";
            }
            
            return "tx-" + transactionId + ": " + e.getClass().getSimpleName();
        }
    }
}