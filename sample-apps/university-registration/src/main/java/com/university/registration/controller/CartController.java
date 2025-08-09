package com.university.registration.controller;

import com.university.registration.dto.CartDTO;
import com.university.registration.service.CartService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/cart")
@CrossOrigin(origins = "*")
public class CartController {

    private static final Logger logger = LoggerFactory.getLogger(CartController.class);

    @Autowired
    private CartService cartService;

    /**
     * 학생의 장바구니 조회
     * GET /api/cart?studentId=2021001
     */
    @GetMapping
    public ResponseEntity<CartDTO.CartSummaryDTO> getCart(@RequestParam String studentId) {
        logger.info("Getting cart for student: {}", studentId);

        try {
            CartDTO.CartSummaryDTO cartSummary = cartService.getCartSummary(studentId);
            
            logger.debug("Retrieved cart with {} items for student {}", 
                        cartSummary.getTotalItems(), studentId);
            return ResponseEntity.ok(cartSummary);

        } catch (RuntimeException e) {
            logger.warn("Student not found: {}", studentId);
            return ResponseEntity.notFound().build();

        } catch (Exception e) {
            logger.error("Failed to get cart for student {}: {}", studentId, e.getMessage());
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * 장바구니에 과목 추가
     * POST /api/cart/items
     * Body: {"studentId": "2021001", "courseId": "CSE301"}
     */
    @PostMapping("/items")
    public ResponseEntity<String> addToCart(@RequestBody AddToCartRequest request) {
        logger.info("Adding course {} to cart for student {}", 
                   request.getCourseId(), request.getStudentId());

        try {
            boolean success = cartService.addToCart(request.getStudentId(), request.getCourseId());
            
            if (success) {
                logger.info("Successfully added course {} to cart for student {}", 
                           request.getCourseId(), request.getStudentId());
                return ResponseEntity.ok("장바구니에 추가되었습니다.");
            } else {
                logger.warn("Failed to add course {} to cart for student {} - already exists or time conflict", 
                           request.getCourseId(), request.getStudentId());
                return ResponseEntity.badRequest().body("이미 장바구니에 있거나 시간표가 충돌합니다.");
            }

        } catch (RuntimeException e) {
            logger.warn("Invalid request: {}", e.getMessage());
            return ResponseEntity.badRequest().body(e.getMessage());

        } catch (Exception e) {
            logger.error("Failed to add to cart: {}", e.getMessage());
            return ResponseEntity.internalServerError().body("장바구니 추가 중 오류가 발생했습니다.");
        }
    }

    /**
     * 장바구니에서 과목 제거
     * DELETE /api/cart/items/{courseId}?studentId=2021001
     */
    @DeleteMapping("/items/{courseId}")
    public ResponseEntity<String> removeFromCart(
            @PathVariable String courseId, 
            @RequestParam String studentId) {
        
        logger.info("Removing course {} from cart for student {}", courseId, studentId);

        try {
            boolean success = cartService.removeFromCart(studentId, courseId);
            
            if (success) {
                logger.info("Successfully removed course {} from cart for student {}", 
                           courseId, studentId);
                return ResponseEntity.ok("장바구니에서 제거되었습니다.");
            } else {
                logger.warn("Course {} not found in cart for student {}", courseId, studentId);
                return ResponseEntity.notFound().build();
            }

        } catch (RuntimeException e) {
            logger.warn("Invalid request: {}", e.getMessage());
            return ResponseEntity.badRequest().body(e.getMessage());

        } catch (Exception e) {
            logger.error("Failed to remove from cart: {}", e.getMessage());
            return ResponseEntity.internalServerError().body("장바구니 제거 중 오류가 발생했습니다.");
        }
    }

    /**
     * 장바구니 비우기
     * DELETE /api/cart?studentId=2021001
     */
    @DeleteMapping
    public ResponseEntity<String> clearCart(@RequestParam String studentId) {
        logger.info("Clearing cart for student: {}", studentId);

        try {
            boolean success = cartService.clearCart(studentId);
            
            if (success) {
                logger.info("Successfully cleared cart for student {}", studentId);
                return ResponseEntity.ok("장바구니가 비워졌습니다.");
            } else {
                logger.warn("Failed to clear cart for student {}", studentId);
                return ResponseEntity.internalServerError().body("장바구니 비우기에 실패했습니다.");
            }

        } catch (RuntimeException e) {
            logger.warn("Student not found: {}", studentId);
            return ResponseEntity.notFound().build();

        } catch (Exception e) {
            logger.error("Failed to clear cart for student {}: {}", studentId, e.getMessage());
            return ResponseEntity.internalServerError().body("장바구니 비우기 중 오류가 발생했습니다.");
        }
    }

    /**
     * 장바구니 검증 (수강신청 전 최종 체크)
     * POST /api/cart/validate
     * Body: {"studentId": "2021001"}
     */
    @PostMapping("/validate")
    public ResponseEntity<CartService.CartValidationResult> validateCart(
            @RequestBody ValidateCartRequest request) {
        
        logger.info("Validating cart for student: {}", request.getStudentId());

        try {
            CartService.CartValidationResult result = cartService.validateCart(request.getStudentId());
            
            logger.debug("Cart validation for student {}: valid={}", 
                        request.getStudentId(), result.isValid());
            return ResponseEntity.ok(result);

        } catch (RuntimeException e) {
            logger.warn("Student not found: {}", request.getStudentId());
            return ResponseEntity.notFound().build();

        } catch (Exception e) {
            logger.error("Failed to validate cart for student {}: {}", 
                        request.getStudentId(), e.getMessage());
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * 통계 API - 성능 테스트용
     */

    /**
     * 인기 과목 (장바구니 기준)
     * GET /api/cart/stats/most-wanted
     */
    @GetMapping("/stats/most-wanted")
    public ResponseEntity<List<Object[]>> getMostWantedCourses() {
        logger.info("Getting most wanted courses from cart data");

        try {
            List<Object[]> stats = cartService.getMostWantedCourses();
            
            logger.debug("Retrieved most wanted courses statistics");
            return ResponseEntity.ok(stats);

        } catch (Exception e) {
            logger.error("Failed to get most wanted courses: {}", e.getMessage());
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * 학과별 장바구니 통계
     * GET /api/cart/stats/department
     */
    @GetMapping("/stats/department")
    public ResponseEntity<List<Object[]>> getCartStatsByDepartment() {
        logger.info("Getting cart statistics by department");

        try {
            List<Object[]> stats = cartService.getCartStatsByDepartment();
            
            logger.debug("Retrieved cart statistics by department");
            return ResponseEntity.ok(stats);

        } catch (Exception e) {
            logger.error("Failed to get cart stats by department: {}", e.getMessage());
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * 학년별 장바구니 통계
     * GET /api/cart/stats/grade
     */
    @GetMapping("/stats/grade")
    public ResponseEntity<List<Object[]>> getCartStatsByGrade() {
        logger.info("Getting cart statistics by grade");

        try {
            List<Object[]> stats = cartService.getCartStatsByGrade();
            
            logger.debug("Retrieved cart statistics by grade");
            return ResponseEntity.ok(stats);

        } catch (Exception e) {
            logger.error("Failed to get cart stats by grade: {}", e.getMessage());
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * 교수별 인기도 (장바구니 기준)
     * GET /api/cart/stats/popular-professors
     */
    @GetMapping("/stats/popular-professors")
    public ResponseEntity<List<Object[]>> getPopularProfessors() {
        logger.info("Getting popular professors based on cart data");

        try {
            List<Object[]> stats = cartService.getPopularProfessors();
            
            logger.debug("Retrieved popular professors statistics");
            return ResponseEntity.ok(stats);

        } catch (Exception e) {
            logger.error("Failed to get popular professors: {}", e.getMessage());
            return ResponseEntity.internalServerError().build();
        }
    }

    // Request DTOs
    public static class AddToCartRequest {
        private String studentId;
        private String courseId;

        // Getters and Setters
        public String getStudentId() { return studentId; }
        public void setStudentId(String studentId) { this.studentId = studentId; }

        public String getCourseId() { return courseId; }
        public void setCourseId(String courseId) { this.courseId = courseId; }
    }

    public static class ValidateCartRequest {
        private String studentId;

        // Getters and Setters
        public String getStudentId() { return studentId; }
        public void setStudentId(String studentId) { this.studentId = studentId; }
    }
}