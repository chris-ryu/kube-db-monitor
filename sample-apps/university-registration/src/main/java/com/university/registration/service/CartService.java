package com.university.registration.service;

import com.university.registration.dto.CartDTO;
import com.university.registration.entity.Cart;
import com.university.registration.entity.Course;
import com.university.registration.entity.Student;
import com.university.registration.repository.CartRepository;
import com.university.registration.repository.CourseRepository;
import com.university.registration.repository.StudentRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.ArrayList;
import java.util.stream.Collectors;

@Service
@Transactional
public class CartService {

    private static final Logger logger = LoggerFactory.getLogger(CartService.class);

    @Autowired private CartRepository cartRepository;
    @Autowired private StudentRepository studentRepository;
    @Autowired private CourseRepository courseRepository;

    /**
     * 장바구니에 과목 추가
     * - 중복 체크 로직
     * - 시간표 충돌 검증
     */
    public boolean addToCart(String studentId, String courseId) {
        logger.debug("Adding course {} to cart for student {}", courseId, studentId);

        try {
            // 1. 학생 정보 조회 - SELECT
            Student student = findStudentById(studentId);
            
            // 2. 과목 정보 조회 - SELECT
            Course course = findCourseById(courseId);

            // 3. 이미 장바구니에 있는지 확인 - SELECT
            if (cartRepository.existsByStudentAndCourse(student, course)) {
                logger.warn("Course {} already in cart for student {}", courseId, studentId);
                return false;
            }

            // 4. 시간표 충돌 체크 - SELECT with WHERE
            List<Cart> conflictItems = cartRepository.findTimeConflictItems(student, course.getDayTime());
            if (!conflictItems.isEmpty()) {
                logger.warn("Time conflict detected for course {} and student {}", courseId, studentId);
                return false;
            }

            // 5. 장바구니에 추가 - INSERT
            Cart cartItem = new Cart(student, course);
            cartRepository.save(cartItem);

            logger.info("Successfully added course {} to cart for student {}", courseId, studentId);
            return true;

        } catch (DataIntegrityViolationException e) {
            logger.warn("Duplicate cart item detected: {}", e.getMessage());
            return false;
        } catch (Exception e) {
            logger.error("Failed to add course {} to cart for student {}: {}", courseId, studentId, e.getMessage());
            return false;
        }
    }

    /**
     * 장바구니에서 과목 제거
     */
    public boolean removeFromCart(String studentId, String courseId) {
        logger.debug("Removing course {} from cart for student {}", courseId, studentId);

        try {
            Student student = findStudentById(studentId);
            Course course = findCourseById(courseId);

            // 장바구니에서 삭제 - DELETE
            int deletedCount = cartRepository.deleteByStudentAndCourse(student, course);
            
            if (deletedCount > 0) {
                logger.info("Successfully removed course {} from cart for student {}", courseId, studentId);
                return true;
            } else {
                logger.warn("Course {} not found in cart for student {}", courseId, studentId);
                return false;
            }

        } catch (Exception e) {
            logger.error("Failed to remove course {} from cart for student {}: {}", courseId, studentId, e.getMessage());
            return false;
        }
    }

    /**
     * 학생의 장바구니 조회
     * - JOIN을 포함한 복합 쿼리
     */
    @Transactional(readOnly = true)
    public CartDTO.CartSummaryDTO getCartSummary(String studentId) {
        logger.debug("Getting cart summary for student {}", studentId);

        Student student = findStudentById(studentId);

        // 장바구니 아이템들 조회 - JOIN FETCH로 N+1 문제 방지
        List<Cart> cartItems = cartRepository.findByStudentWithCourseDetails(student);

        // Cart Entity를 CartDTO로 변환
        List<CartDTO> cartDTOs = cartItems.stream()
                .map(CartDTO::new)
                .collect(Collectors.toList());

        // 총 학점 계산 - Aggregate function
        Integer totalCredits = cartRepository.getTotalCreditsByStudent(student);
        if (totalCredits == null) totalCredits = 0;

        CartDTO.CartSummaryDTO summary = new CartDTO.CartSummaryDTO(cartDTOs, totalCredits);

        // 시간표 충돌 체크
        List<String> timeConflicts = findTimeConflicts(cartItems);
        summary.setTimeConflicts(timeConflicts);

        // 전체 수강신청 가능 여부
        summary.setCanEnrollAll(timeConflicts.isEmpty() && totalCredits <= 21);

        return summary;
    }

    /**
     * 장바구니 비우기
     */
    public boolean clearCart(String studentId) {
        logger.debug("Clearing cart for student {}", studentId);

        try {
            Student student = findStudentById(studentId);
            
            // 장바구니 전체 삭제 - DELETE with WHERE
            int deletedCount = cartRepository.deleteByStudent(student);
            
            logger.info("Cleared {} items from cart for student {}", deletedCount, studentId);
            return true;

        } catch (Exception e) {
            logger.error("Failed to clear cart for student {}: {}", studentId, e.getMessage());
            return false;
        }
    }

    /**
     * 장바구니 검증 (수강신청 전 최종 체크)
     */
    @Transactional(readOnly = true)
    public CartValidationResult validateCart(String studentId) {
        logger.debug("Validating cart for student {}", studentId);

        Student student = findStudentById(studentId);
        List<Cart> cartItems = cartRepository.findByStudentWithCourseDetails(student);

        CartValidationResult result = new CartValidationResult();

        // 1. 시간표 충돌 체크
        List<String> timeConflicts = findTimeConflicts(cartItems);
        result.setTimeConflicts(timeConflicts);

        // 2. 총 학점 체크
        int totalCredits = cartItems.stream()
                .mapToInt(cart -> cart.getCourse().getCredits())
                .sum();
        result.setTotalCredits(totalCredits);
        result.setCreditLimitExceeded(totalCredits > student.getMaxCredits());

        // 3. 정원 초과 과목 체크
        List<String> capacityExceededCourses = new ArrayList<>();
        for (Cart cartItem : cartItems) {
            Course course = cartItem.getCourse();
            // 실시간 정원 체크 - SELECT
            Course freshCourse = courseRepository.findByCourseId(course.getCourseId()).orElse(course);
            if (!freshCourse.hasAvailableSlots()) {
                capacityExceededCourses.add(course.getCourseId());
            }
        }
        result.setCapacityExceededCourses(capacityExceededCourses);

        // 4. 전체 검증 결과
        result.setValid(timeConflicts.isEmpty() && 
                       !result.isCreditLimitExceeded() && 
                       capacityExceededCourses.isEmpty());

        return result;
    }

    /**
     * 인기 과목 조회 (장바구니 기준)
     * - 성능 테스트용 복잡한 집계 쿼리
     */
    @Transactional(readOnly = true)
    public List<Object[]> getMostWantedCourses() {
        logger.debug("Getting most wanted courses from cart data");

        // 장바구니에 많이 담긴 과목들 - GROUP BY with COUNT
        return cartRepository.findMostWantedCourses();
    }

    /**
     * 학과별 장바구니 통계
     */
    @Transactional(readOnly = true)
    public List<Object[]> getCartStatsByDepartment() {
        logger.debug("Getting cart statistics by department");

        // 학과별 장바구니 통계 - GROUP BY with aggregations
        return cartRepository.getCartStatsByDepartment();
    }

    /**
     * 학년별 장바구니 통계
     */
    @Transactional(readOnly = true)
    public List<Object[]> getCartStatsByGrade() {
        logger.debug("Getting cart statistics by grade");

        // 학년별 복잡한 집계 쿼리
        return cartRepository.getCartStatsByGrade();
    }

    /**
     * 교수별 인기도 (장바구니 기준)
     */
    @Transactional(readOnly = true)
    public List<Object[]> getPopularProfessors() {
        logger.debug("Getting popular professors based on cart data");

        // 교수별 인기도 - GROUP BY with HAVING
        return cartRepository.getPopularProfessors(5L); // 최소 5개 이상
    }

    // Helper methods
    private Student findStudentById(String studentId) {
        return studentRepository.findById(studentId)
                .orElseThrow(() -> new RuntimeException("학생을 찾을 수 없습니다: " + studentId));
    }

    private Course findCourseById(String courseId) {
        return courseRepository.findById(courseId)
                .orElseThrow(() -> new RuntimeException("과목을 찾을 수 없습니다: " + courseId));
    }

    private List<String> findTimeConflicts(List<Cart> cartItems) {
        List<String> conflicts = new ArrayList<>();
        
        for (int i = 0; i < cartItems.size(); i++) {
            for (int j = i + 1; j < cartItems.size(); j++) {
                Cart item1 = cartItems.get(i);
                Cart item2 = cartItems.get(j);
                
                if (item1.isTimeConflictWith(item2)) {
                    String conflict = String.format("%s와 %s 시간표 충돌", 
                                    item1.getCourse().getCourseName(),
                                    item2.getCourse().getCourseName());
                    conflicts.add(conflict);
                }
            }
        }
        
        return conflicts;
    }

    // Inner class for validation result
    public static class CartValidationResult {
        private boolean valid;
        private List<String> timeConflicts = new ArrayList<>();
        private int totalCredits;
        private boolean creditLimitExceeded;
        private List<String> capacityExceededCourses = new ArrayList<>();

        // Getters and Setters
        public boolean isValid() { return valid; }
        public void setValid(boolean valid) { this.valid = valid; }

        public List<String> getTimeConflicts() { return timeConflicts; }
        public void setTimeConflicts(List<String> timeConflicts) { this.timeConflicts = timeConflicts; }

        public int getTotalCredits() { return totalCredits; }
        public void setTotalCredits(int totalCredits) { this.totalCredits = totalCredits; }

        public boolean isCreditLimitExceeded() { return creditLimitExceeded; }
        public void setCreditLimitExceeded(boolean creditLimitExceeded) { this.creditLimitExceeded = creditLimitExceeded; }

        public List<String> getCapacityExceededCourses() { return capacityExceededCourses; }
        public void setCapacityExceededCourses(List<String> capacityExceededCourses) { this.capacityExceededCourses = capacityExceededCourses; }
    }
}