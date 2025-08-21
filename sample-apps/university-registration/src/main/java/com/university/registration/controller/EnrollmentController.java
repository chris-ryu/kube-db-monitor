package com.university.registration.controller;

import com.university.registration.dto.EnrollmentRequestDTO;
import com.university.registration.dto.EnrollmentResponseDTO;
import com.university.registration.dto.EnrollmentDTO;
import com.university.registration.entity.Enrollment;
import com.university.registration.service.EnrollmentService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;
import java.util.List;

@RestController
@RequestMapping("/enrollments")
@CrossOrigin(origins = "*")
public class EnrollmentController {

    private static final Logger logger = LoggerFactory.getLogger(EnrollmentController.class);

    @Autowired
    private EnrollmentService enrollmentService;

    /**
     * 장바구니에서 수강신청 (핵심 API)
     * POST /api/enrollments/from-cart
     * Body: {"studentId": "2021001", "courseIds": ["CSE301", "CSE302"], "fromCart": true}
     * 
     * 이 API는 KubeDB Monitor 테스트의 핵심 포인트:
     * - 높은 동시성 상황에서의 복잡한 트랜잭션
     * - 여러 테이블에 걸친 SELECT, INSERT, UPDATE, DELETE 쿼리
     * - 비관적/낙관적 락을 활용한 동시성 제어
     */
    @PostMapping("/from-cart")
    public ResponseEntity<EnrollmentResponseDTO> enrollFromCart(
            @Valid @RequestBody EnrollFromCartRequest request) {

        logger.info("Processing enrollment from cart for student: {}, courses: {}", 
                   request.getStudentId(), request.getCourseIds());

        try {
            // EnrollmentRequestDTO 생성
            EnrollmentRequestDTO enrollmentRequest = new EnrollmentRequestDTO();
            enrollmentRequest.setCourseIds(request.getCourseIds());
            enrollmentRequest.setFromCart(true);

            // 수강신청 처리 (복잡한 트랜잭션)
            EnrollmentResponseDTO response = enrollmentService.enrollFromCart(
                request.getStudentId(), enrollmentRequest);

            logger.info("Enrollment completed for student {}. Success: {}, Failed: {}", 
                       request.getStudentId(), response.getSuccessCount(), response.getFailCount());

            return ResponseEntity.ok(response);

        } catch (RuntimeException e) {
            logger.error("Enrollment failed for student {}: {}", request.getStudentId(), e.getMessage());
            
            EnrollmentResponseDTO errorResponse = new EnrollmentResponseDTO(false, e.getMessage());
            return ResponseEntity.badRequest().body(errorResponse);

        } catch (Exception e) {
            logger.error("Unexpected error during enrollment for student {}: {}", 
                        request.getStudentId(), e.getMessage());
            
            EnrollmentResponseDTO errorResponse = new EnrollmentResponseDTO(false, 
                "수강신청 처리 중 시스템 오류가 발생했습니다.");
            return ResponseEntity.internalServerError().body(errorResponse);
        }
    }

    /**
     * 개별 과목 수강신청
     * POST /api/enrollments/{courseId}
     */
    @PostMapping("/{courseId}")
    public ResponseEntity<EnrollmentResponseDTO> enrollCourse(
            @PathVariable String courseId,
            @RequestParam String studentId) {

        logger.info("Processing individual enrollment for student: {}, course: {}", 
                   studentId, courseId);

        try {
            // 개별 과목 수강신청을 위한 요청 생성
            EnrollmentRequestDTO request = new EnrollmentRequestDTO();
            request.setCourseIds(List.of(courseId));
            request.setFromCart(false);

            EnrollmentResponseDTO response = enrollmentService.enrollFromCart(studentId, request);

            logger.info("Individual enrollment completed for student {}, course {}: {}", 
                       studentId, courseId, response.isSuccess() ? "SUCCESS" : "FAILED");

            return ResponseEntity.ok(response);

        } catch (RuntimeException e) {
            logger.error("Individual enrollment failed for student {}, course {}: {}", 
                        studentId, courseId, e.getMessage());
            
            EnrollmentResponseDTO errorResponse = new EnrollmentResponseDTO(false, e.getMessage());
            return ResponseEntity.badRequest().body(errorResponse);

        } catch (Exception e) {
            logger.error("Unexpected error during individual enrollment: {}", e.getMessage());
            
            EnrollmentResponseDTO errorResponse = new EnrollmentResponseDTO(false, 
                "수강신청 처리 중 시스템 오류가 발생했습니다.");
            return ResponseEntity.internalServerError().body(errorResponse);
        }
    }

    /**
     * 수강신청 취소
     * DELETE /api/enrollments/{courseId}?studentId=2021001
     */
    @DeleteMapping("/{courseId}")
    public ResponseEntity<String> cancelEnrollment(
            @PathVariable String courseId,
            @RequestParam String studentId) {

        logger.info("Cancelling enrollment for student: {}, course: {}", studentId, courseId);

        try {
            boolean success = enrollmentService.cancelEnrollment(studentId, courseId);

            if (success) {
                logger.info("Successfully cancelled enrollment for student {}, course {}", 
                           studentId, courseId);
                return ResponseEntity.ok("수강신청이 취소되었습니다.");
            } else {
                logger.warn("Enrollment not found for student {}, course {}", studentId, courseId);
                return ResponseEntity.notFound().build();
            }

        } catch (RuntimeException e) {
            logger.error("Failed to cancel enrollment: {}", e.getMessage());
            return ResponseEntity.badRequest().body(e.getMessage());

        } catch (Exception e) {
            logger.error("Unexpected error during enrollment cancellation: {}", e.getMessage());
            return ResponseEntity.internalServerError().body("수강신청 취소 중 오류가 발생했습니다.");
        }
    }

    /**
     * 학생별 수강신청 현황 조회
     * GET /api/enrollments/me?studentId=2021001
     */
    @GetMapping("/me")
    public ResponseEntity<List<EnrollmentDTO>> getMyEnrollments(@RequestParam String studentId) {
        logger.info("Getting enrollments for student: {}", studentId);

        try {
            List<Enrollment> enrollments = enrollmentService.getStudentEnrollments(studentId);
            
            // Entity를 DTO로 변환하여 순환 참조 방지
            List<EnrollmentDTO> enrollmentDTOs = enrollments.stream()
                    .map(EnrollmentDTO::new)
                    .toList();

            logger.debug("Found {} enrollments for student {}", enrollmentDTOs.size(), studentId);
            return ResponseEntity.ok(enrollmentDTOs);

        } catch (RuntimeException e) {
            logger.warn("Student not found: {}", studentId);
            return ResponseEntity.notFound().build();

        } catch (Exception e) {
            logger.error("Failed to get enrollments for student {}: {}", studentId, e.getMessage());
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * 대량 수강신청 테스트 API (성능 테스트용)
     * POST /api/enrollments/bulk-test
     * Body: {"studentIds": ["2021001", "2021002", ...], "courseId": "CSE301"}
     * 
     * 동시성 테스트를 위한 API - 여러 학생이 동시에 같은 과목을 신청
     */
    @PostMapping("/bulk-test")
    public ResponseEntity<BulkTestResultDTO> bulkEnrollmentTest(
            @RequestBody BulkTestRequest request) {

        logger.info("Processing bulk enrollment test for {} students on course {}", 
                   request.getStudentIds().size(), request.getCourseId());

        BulkTestResultDTO result = new BulkTestResultDTO();
        result.setTotalStudents(request.getStudentIds().size());

        // 각 학생에 대해 개별 수강신청 시도
        for (String studentId : request.getStudentIds()) {
            try {
                EnrollmentRequestDTO enrollmentRequest = new EnrollmentRequestDTO();
                enrollmentRequest.setCourseIds(List.of(request.getCourseId()));
                enrollmentRequest.setFromCart(false);

                EnrollmentResponseDTO response = enrollmentService.enrollFromCart(studentId, enrollmentRequest);

                if (response.isSuccess()) {
                    result.incrementSuccess();
                } else {
                    result.incrementFailed();
                    result.addFailedStudent(studentId, response.getMessage());
                }

            } catch (Exception e) {
                result.incrementFailed();
                result.addFailedStudent(studentId, e.getMessage());
            }
        }

        logger.info("Bulk enrollment test completed. Success: {}, Failed: {}", 
                   result.getSuccessCount(), result.getFailedCount());

        return ResponseEntity.ok(result);
    }

    // Request DTOs
    public static class EnrollFromCartRequest {
        private String studentId;
        private List<String> courseIds;

        // Getters and Setters
        public String getStudentId() { return studentId; }
        public void setStudentId(String studentId) { this.studentId = studentId; }

        public List<String> getCourseIds() { return courseIds; }
        public void setCourseIds(List<String> courseIds) { this.courseIds = courseIds; }
    }

    public static class BulkTestRequest {
        private List<String> studentIds;
        private String courseId;

        // Getters and Setters
        public List<String> getStudentIds() { return studentIds; }
        public void setStudentIds(List<String> studentIds) { this.studentIds = studentIds; }

        public String getCourseId() { return courseId; }
        public void setCourseId(String courseId) { this.courseId = courseId; }
    }

    // Response DTO
    public static class BulkTestResultDTO {
        private int totalStudents;
        private int successCount = 0;
        private int failedCount = 0;
        private List<String> failedStudents = new java.util.ArrayList<>();
        private List<String> failedReasons = new java.util.ArrayList<>();

        public void incrementSuccess() { successCount++; }
        public void incrementFailed() { failedCount++; }

        public void addFailedStudent(String studentId, String reason) {
            failedStudents.add(studentId);
            failedReasons.add(reason);
        }

        // Getters and Setters
        public int getTotalStudents() { return totalStudents; }
        public void setTotalStudents(int totalStudents) { this.totalStudents = totalStudents; }

        public int getSuccessCount() { return successCount; }
        public void setSuccessCount(int successCount) { this.successCount = successCount; }

        public int getFailedCount() { return failedCount; }
        public void setFailedCount(int failedCount) { this.failedCount = failedCount; }

        public List<String> getFailedStudents() { return failedStudents; }
        public void setFailedStudents(List<String> failedStudents) { this.failedStudents = failedStudents; }

        public List<String> getFailedReasons() { return failedReasons; }
        public void setFailedReasons(List<String> failedReasons) { this.failedReasons = failedReasons; }
    }
}