package com.university.registration.controller;

import com.university.registration.dto.CourseDTO;
import com.university.registration.service.CourseService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/courses")
@CrossOrigin(origins = "*")
public class CourseController {

    private static final Logger logger = LoggerFactory.getLogger(CourseController.class);

    @Autowired
    private CourseService courseService;

    /**
     * 과목 검색 (페이징 지원)
     * GET /api/courses?page=0&size=20&dept=1&keyword=데이터베이스
     */
    @GetMapping
    public ResponseEntity<Page<CourseDTO>> searchCourses(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) Long dept,
            @RequestParam(required = false) String keyword) {

        logger.info("Searching courses - page: {}, size: {}, dept: {}, keyword: {}", 
                   page, size, dept, keyword);

        try {
            Page<CourseDTO> courses = courseService.searchCourses(dept, keyword, page, size);
            
            logger.debug("Found {} courses on page {}", courses.getContent().size(), page);
            return ResponseEntity.ok(courses);

        } catch (Exception e) {
            logger.error("Failed to search courses: {}", e.getMessage());
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * 과목 상세 정보 조회
     * GET /api/courses/{courseId}
     */
    @GetMapping("/{courseId}")
    public ResponseEntity<CourseDTO> getCourse(@PathVariable String courseId) {
        logger.info("Getting course details for: {}", courseId);

        try {
            return courseService.getCourseById(courseId)
                    .map(course -> {
                        logger.debug("Found course: {}", course.getCourseName());
                        return ResponseEntity.ok(course);
                    })
                    .orElseGet(() -> {
                        logger.warn("Course not found: {}", courseId);
                        return ResponseEntity.notFound().build();
                    });

        } catch (Exception e) {
            logger.error("Failed to get course {}: {}", courseId, e.getMessage());
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * 과목 실시간 정원 정보 조회
     * GET /api/courses/{courseId}/availability
     */
    @GetMapping("/{courseId}/availability")
    public ResponseEntity<CourseDTO> getCourseAvailability(@PathVariable String courseId) {
        logger.info("Getting availability for course: {}", courseId);

        try {
            CourseDTO course = courseService.getCourseAvailability(courseId);
            return ResponseEntity.ok(course);

        } catch (RuntimeException e) {
            logger.warn("Course not found: {}", courseId);
            return ResponseEntity.notFound().build();

        } catch (Exception e) {
            logger.error("Failed to get availability for course {}: {}", courseId, e.getMessage());
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * 정원이 남은 과목들 조회
     * GET /api/courses/available
     */
    @GetMapping("/available")
    public ResponseEntity<List<CourseDTO>> getAvailableCourses() {
        logger.info("Getting available courses");

        try {
            List<CourseDTO> availableCourses = courseService.getAvailableCourses();
            
            logger.debug("Found {} available courses", availableCourses.size());
            return ResponseEntity.ok(availableCourses);

        } catch (Exception e) {
            logger.error("Failed to get available courses: {}", e.getMessage());
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * 인기 과목 조회
     * GET /api/courses/popular?threshold=0.8
     */
    @GetMapping("/popular")
    public ResponseEntity<List<CourseDTO>> getPopularCourses(
            @RequestParam(defaultValue = "0.8") Double threshold) {
        
        logger.info("Getting popular courses with threshold: {}", threshold);

        try {
            List<CourseDTO> popularCourses = courseService.getPopularCourses(threshold);
            
            logger.debug("Found {} popular courses", popularCourses.size());
            return ResponseEntity.ok(popularCourses);

        } catch (Exception e) {
            logger.error("Failed to get popular courses: {}", e.getMessage());
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * 학과별 과목 조회
     * GET /api/courses/department/{departmentId}
     */
    @GetMapping("/department/{departmentId}")
    public ResponseEntity<List<CourseDTO>> getCoursesByDepartment(@PathVariable Long departmentId) {
        logger.info("Getting courses by department: {}", departmentId);

        try {
            List<CourseDTO> courses = courseService.getCoursesByDepartment(departmentId);
            
            logger.debug("Found {} courses for department {}", courses.size(), departmentId);
            return ResponseEntity.ok(courses);

        } catch (RuntimeException e) {
            logger.warn("Department not found: {}", departmentId);
            return ResponseEntity.notFound().build();

        } catch (Exception e) {
            logger.error("Failed to get courses by department {}: {}", departmentId, e.getMessage());
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * 시간표 충돌 과목 조회
     * GET /api/courses/time-conflict?dayTime=월1,수3
     */
    @GetMapping("/time-conflict")
    public ResponseEntity<List<CourseDTO>> getTimeConflictCourses(@RequestParam String dayTime) {
        logger.info("Getting courses with time conflict: {}", dayTime);

        try {
            List<CourseDTO> conflictCourses = courseService.getTimeConflictCourses(dayTime);
            
            logger.debug("Found {} courses with time conflict", conflictCourses.size());
            return ResponseEntity.ok(conflictCourses);

        } catch (Exception e) {
            logger.error("Failed to get time conflict courses: {}", e.getMessage());
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * 선수과목이 있는 과목들 조회
     * GET /api/courses/prerequisites
     */
    @GetMapping("/prerequisites")
    public ResponseEntity<List<CourseDTO>> getCoursesWithPrerequisites() {
        logger.info("Getting courses with prerequisites");

        try {
            List<CourseDTO> courses = courseService.getCoursesWithPrerequisites();
            
            logger.debug("Found {} courses with prerequisites", courses.size());
            return ResponseEntity.ok(courses);

        } catch (Exception e) {
            logger.error("Failed to get courses with prerequisites: {}", e.getMessage());
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * 통계 API - 성능 테스트용
     */

    /**
     * 학과별 수강신청 통계
     * GET /api/courses/stats/department
     */
    @GetMapping("/stats/department")
    public ResponseEntity<List<Object[]>> getEnrollmentStatsByDepartment() {
        logger.info("Getting enrollment statistics by department");

        try {
            List<Object[]> stats = courseService.getEnrollmentStatsByDepartment();
            
            logger.debug("Retrieved department enrollment statistics");
            return ResponseEntity.ok(stats);

        } catch (Exception e) {
            logger.error("Failed to get department enrollment statistics: {}", e.getMessage());
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * 교수별 과목 수 통계
     * GET /api/courses/stats/professor
     */
    @GetMapping("/stats/professor")
    public ResponseEntity<List<Object[]>> getCourseStatsByProfessor() {
        logger.info("Getting course statistics by professor");

        try {
            List<Object[]> stats = courseService.getCourseStatsByProfessor();
            
            logger.debug("Retrieved professor course statistics");
            return ResponseEntity.ok(stats);

        } catch (Exception e) {
            logger.error("Failed to get professor course statistics: {}", e.getMessage());
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * 시간대별 과목 통계
     * GET /api/courses/stats/timeslot
     */
    @GetMapping("/stats/timeslot")
    public ResponseEntity<List<Object[]>> getTimeSlotStatistics() {
        logger.info("Getting time slot statistics");

        try {
            List<Object[]> stats = courseService.getTimeSlotStatistics();
            
            logger.debug("Retrieved time slot statistics");
            return ResponseEntity.ok(stats);

        } catch (Exception e) {
            logger.error("Failed to get time slot statistics: {}", e.getMessage());
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * 복잡한 JOIN 쿼리 테스트
     * GET /api/courses/enrollment-details
     */
    @GetMapping("/enrollment-details")
    public ResponseEntity<List<CourseDTO>> getCoursesWithEnrollmentDetails() {
        logger.info("Getting courses with enrollment details (complex JOIN query)");

        try {
            List<CourseDTO> courses = courseService.getCoursesWithEnrollmentDetails();
            
            logger.debug("Retrieved {} courses with enrollment details", courses.size());
            return ResponseEntity.ok(courses);

        } catch (Exception e) {
            logger.error("Failed to get courses with enrollment details: {}", e.getMessage());
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * 평균 수강신청 인원
     * GET /api/courses/stats/average-enrollment
     */
    @GetMapping("/stats/average-enrollment")
    public ResponseEntity<Double> getAverageEnrollment() {
        logger.info("Getting average enrollment");

        try {
            Double avgEnrollment = courseService.getAverageEnrollment();
            
            logger.debug("Average enrollment: {}", avgEnrollment);
            return ResponseEntity.ok(avgEnrollment);

        } catch (Exception e) {
            logger.error("Failed to get average enrollment: {}", e.getMessage());
            return ResponseEntity.internalServerError().build();
        }
    }
}