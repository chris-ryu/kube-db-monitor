package com.university.registration.service;

import com.university.registration.dto.EnrollmentRequestDTO;
import com.university.registration.dto.EnrollmentResponseDTO;
import com.university.registration.entity.*;
import com.university.registration.repository.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Isolation;

import jakarta.persistence.OptimisticLockException;
import java.util.List;
import java.util.Optional;

@Service
@Transactional
public class EnrollmentService {

    private static final Logger logger = LoggerFactory.getLogger(EnrollmentService.class);

    @Autowired private EnrollmentRepository enrollmentRepository;
    @Autowired private StudentRepository studentRepository;
    @Autowired private CourseRepository courseRepository;
    @Autowired private SemesterRepository semesterRepository;
    @Autowired private CartRepository cartRepository;

    /**
     * 장바구니에서 수강신청 (핵심 비즈니스 로직)
     * - 높은 동시성 상황에서의 정원 관리
     * - 복잡한 트랜잭션 처리
     * - KubeDB Monitor 테스트의 핵심 포인트
     */
    @Transactional(isolation = Isolation.READ_COMMITTED, timeout = 30)
    public EnrollmentResponseDTO enrollFromCart(String studentId, EnrollmentRequestDTO request) {
        logger.info("Starting enrollment from cart for student: {}", studentId);
        
        EnrollmentResponseDTO response = new EnrollmentResponseDTO();
        
        try {
            // 1. 학생 정보 조회 - SELECT
            Student student = findStudentById(studentId);
            
            // 2. 현재 학기 조회 - SELECT
            Semester currentSemester = getCurrentSemester();
            
            // 3. 학생의 장바구니에서 요청된 과목들 조회 - SELECT with JOIN
            List<String> courseIds = request.getCourseIds();
            
            for (String courseId : courseIds) {
                try {
                    processIndividualEnrollment(student, courseId, currentSemester, response);
                } catch (Exception e) {
                    logger.error("Failed to enroll course {} for student {}: {}", 
                               courseId, studentId, e.getMessage());
                    response.addResult(courseId, courseId, "FAILED", e.getMessage());
                }
            }
            
            // 4. 응답 요약 계산
            response.calculateSummary();
            
            // 5. 성공한 항목들의 장바구니에서 삭제
            if (request.isFromCart()) {
                removeSuccessfulItemsFromCart(student, response);
            }
            
            logger.info("Enrollment completed for student: {}. Success: {}, Failed: {}", 
                       studentId, response.getSuccessCount(), response.getFailCount());
            
        } catch (Exception e) {
            logger.error("Enrollment process failed for student {}: {}", studentId, e.getMessage());
            response.setSuccess(false);
            response.setMessage("수강신청 처리 중 오류가 발생했습니다: " + e.getMessage());
        }
        
        return response;
    }

    /**
     * 개별 과목 수강신청 처리
     * - 정원 체크 및 업데이트 (PESSIMISTIC_WRITE 락)
     * - 시간표 충돌 검증
     * - 선수과목 이수 여부 확인
     */
    private void processIndividualEnrollment(Student student, String courseId, 
                                           Semester semester, EnrollmentResponseDTO response) {
        
        // 1. 과목 정보 조회 (비관적 락 적용) - SELECT FOR UPDATE
        Optional<Course> courseOpt = courseRepository.findByCourseIdWithLock(courseId);
        if (courseOpt.isEmpty()) {
            response.addResult(courseId, courseId, "FAILED", "존재하지 않는 과목입니다.");
            return;
        }
        
        Course course = courseOpt.get();
        
        // 2. 기본 검증
        if (!validateBasicEnrollmentRequirements(student, course, semester, response)) {
            return;
        }
        
        // 3. 정원 체크 및 예약
        if (!reserveSlot(course, response)) {
            return;
        }
        
        try {
            // 4. 수강신청 레코드 생성 - INSERT
            Enrollment enrollment = new Enrollment(student, course, semester);
            enrollmentRepository.save(enrollment);
            
            // 5. 과목 수강신청 인원 증가 - UPDATE
            course.setEnrolledCount(course.getEnrolledCount() + 1);
            courseRepository.save(course);
            
            logger.debug("Successfully enrolled student {} in course {}", 
                        student.getStudentId(), course.getCourseId());
            
            response.addResult(course.getCourseId(), course.getCourseName(), 
                             "SUCCESS", "수강신청이 완료되었습니다.");
                             
        } catch (OptimisticLockException e) {
            logger.warn("Optimistic lock exception during enrollment: {}", e.getMessage());
            response.addResult(course.getCourseId(), course.getCourseName(), 
                             "FAILED", "동시 접속으로 인한 처리 실패. 다시 시도해주세요.");
        }
    }

    /**
     * 기본 수강신청 요구사항 검증
     */
    private boolean validateBasicEnrollmentRequirements(Student student, Course course, 
                                                       Semester semester, EnrollmentResponseDTO response) {
        
        // 1. 이미 신청한 과목인지 확인 - SELECT
        if (enrollmentRepository.findByStudentAndCourseAndSemester(student, course, semester).isPresent()) {
            response.addResult(course.getCourseId(), course.getCourseName(), 
                             "FAILED", "이미 신청한 과목입니다.");
            return false;
        }
        
        // 2. 시간표 충돌 체크 - SELECT with JOIN
        List<Enrollment> conflictingEnrollments = 
            enrollmentRepository.findTimeConflictEnrollments(student, semester, course.getDayTime());
        
        if (!conflictingEnrollments.isEmpty()) {
            response.addResult(course.getCourseId(), course.getCourseName(), 
                             "FAILED", "시간표가 충돌합니다.");
            return false;
        }
        
        // 3. 현재 학기 총 신청 학점 확인 - Aggregate SELECT
        Integer currentCredits = 
            enrollmentRepository.getTotalCreditsByStudentAndSemester(student, semester);
        
        if (currentCredits != null && currentCredits + course.getCredits() > student.getMaxCredits()) {
            response.addResult(course.getCourseId(), course.getCourseName(), 
                             "FAILED", "최대 신청 학점을 초과합니다.");
            return false;
        }
        
        // 4. 선수과목 이수 여부 확인
        if (course.getPrerequisiteCourse() != null) {
            Optional<Enrollment> prerequisiteEnrollment = 
                enrollmentRepository.findPrerequisiteEnrollment(student, course.getPrerequisiteCourse());
            
            if (prerequisiteEnrollment.isEmpty()) {
                response.addResult(course.getCourseId(), course.getCourseName(), 
                                 "FAILED", "선수과목을 이수하지 않았습니다: " + 
                                 course.getPrerequisiteCourse().getCourseName());
                return false;
            }
        }
        
        return true;
    }

    /**
     * 과목 정원 예약 (동시성 제어의 핵심)
     */
    private boolean reserveSlot(Course course, EnrollmentResponseDTO response) {
        if (course.getEnrolledCount() >= course.getCapacity()) {
            response.addResult(course.getCourseId(), course.getCourseName(), 
                             "FAILED", "정원이 초과되었습니다.");
            return false;
        }
        return true;
    }

    /**
     * 수강신청 취소
     */
    @Transactional
    public boolean cancelEnrollment(String studentId, String courseId) {
        try {
            Student student = findStudentById(studentId);
            Semester currentSemester = getCurrentSemester();
            
            Optional<Course> courseOpt = courseRepository.findByCourseIdWithLock(courseId);
            if (courseOpt.isEmpty()) {
                return false;
            }
            
            Course course = courseOpt.get();
            
            // 수강신청 취소 - UPDATE
            int cancelledCount = enrollmentRepository.cancelEnrollment(student, course, currentSemester);
            
            if (cancelledCount > 0) {
                // 과목 수강신청 인원 감소 - UPDATE
                course.setEnrolledCount(Math.max(0, course.getEnrolledCount() - 1));
                courseRepository.save(course);
                
                logger.info("Cancelled enrollment for student {} in course {}", studentId, courseId);
                return true;
            }
            
        } catch (Exception e) {
            logger.error("Failed to cancel enrollment: {}", e.getMessage());
        }
        
        return false;
    }

    /**
     * 학생별 수강신청 현황 조회
     */
    @Transactional(readOnly = true)
    public List<Enrollment> getStudentEnrollments(String studentId) {
        Student student = findStudentById(studentId);
        Semester currentSemester = getCurrentSemester();
        
        return enrollmentRepository.findByStudentAndSemesterWithCourse(student, currentSemester);
    }

    // Helper methods
    private Student findStudentById(String studentId) {
        return studentRepository.findById(studentId)
            .orElseThrow(() -> new RuntimeException("학생을 찾을 수 없습니다: " + studentId));
    }

    private Semester getCurrentSemester() {
        return semesterRepository.findCurrentSemester()
            .orElseThrow(() -> new RuntimeException("현재 학기 정보를 찾을 수 없습니다."));
    }

    private void removeSuccessfulItemsFromCart(Student student, EnrollmentResponseDTO response) {
        for (EnrollmentResponseDTO.EnrollmentResultDTO result : response.getResults()) {
            if ("SUCCESS".equals(result.getStatus())) {
                try {
                    Course course = courseRepository.findById(result.getCourseId()).orElse(null);
                    if (course != null) {
                        cartRepository.deleteByStudentAndCourse(student, course);
                    }
                } catch (Exception e) {
                    logger.warn("Failed to remove course {} from cart for student {}: {}", 
                              result.getCourseId(), student.getStudentId(), e.getMessage());
                }
            }
        }
    }
}