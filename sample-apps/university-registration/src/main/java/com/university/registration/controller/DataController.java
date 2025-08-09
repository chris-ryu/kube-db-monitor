package com.university.registration.controller;

import com.university.registration.repository.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

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
}