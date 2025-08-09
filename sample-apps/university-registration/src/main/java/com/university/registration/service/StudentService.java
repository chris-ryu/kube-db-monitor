package com.university.registration.service;

import com.university.registration.dto.StudentDTO;
import com.university.registration.entity.Student;
import com.university.registration.entity.Semester;
import com.university.registration.repository.StudentRepository;
import com.university.registration.repository.EnrollmentRepository;
import com.university.registration.repository.SemesterRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Service
@Transactional
public class StudentService {

    private static final Logger logger = LoggerFactory.getLogger(StudentService.class);

    @Autowired private StudentRepository studentRepository;
    @Autowired private EnrollmentRepository enrollmentRepository;
    @Autowired private SemesterRepository semesterRepository;
    @Autowired private PasswordEncoder passwordEncoder;

    /**
     * 학생 인증 (로그인)
     */
    @Transactional(readOnly = true)
    public Optional<Student> authenticateStudent(String studentId, String password) {
        logger.debug("Authenticating student: {}", studentId);

        // 학생 정보 조회 - SELECT with JOIN (Department)
        Optional<Student> studentOpt = studentRepository.findByStudentId(studentId);
        
        if (studentOpt.isPresent()) {
            Student student = studentOpt.get();
            
            // 비밀번호 검증
            if (passwordEncoder.matches(password, student.getPassword())) {
                logger.info("Student {} authenticated successfully", studentId);
                return Optional.of(student);
            } else {
                logger.warn("Password mismatch for student {}", studentId);
            }
        } else {
            logger.warn("Student {} not found", studentId);
        }

        return Optional.empty();
    }

    /**
     * 학생 프로필 조회 (현재 학기 수강신청 정보 포함)
     */
    @Transactional(readOnly = true)
    public StudentDTO.StudentProfileDTO getStudentProfile(String studentId) {
        logger.debug("Getting profile for student: {}", studentId);

        // 학생 정보 조회 - SELECT with JOIN
        Student student = studentRepository.findByStudentId(studentId)
                .orElseThrow(() -> new RuntimeException("학생을 찾을 수 없습니다: " + studentId));

        StudentDTO.StudentProfileDTO profile = new StudentDTO.StudentProfileDTO(student);

        // 현재 학기 수강신청 학점 계산 - Aggregate SELECT
        Semester currentSemester = getCurrentSemester();
        Integer currentSemesterCredits = enrollmentRepository
                .getTotalCreditsByStudentAndSemester(student, currentSemester);
        
        profile.setCurrentSemesterCredits(currentSemesterCredits != null ? currentSemesterCredits : 0);

        return profile;
    }

    /**
     * 학생 정보 업데이트
     */
    public boolean updateStudentInfo(String studentId, StudentDTO studentDTO) {
        logger.debug("Updating info for student: {}", studentId);

        try {
            // 학생 조회 - SELECT
            Student student = studentRepository.findByStudentId(studentId)
                    .orElseThrow(() -> new RuntimeException("학생을 찾을 수 없습니다: " + studentId));

            // 업데이트 가능한 필드들
            if (studentDTO.getName() != null) {
                student.setName(studentDTO.getName());
            }
            
            if (studentDTO.getMaxCredits() != null) {
                student.setMaxCredits(studentDTO.getMaxCredits());
            }

            // 학생 정보 저장 - UPDATE
            studentRepository.save(student);
            
            logger.info("Updated info for student: {}", studentId);
            return true;

        } catch (Exception e) {
            logger.error("Failed to update info for student {}: {}", studentId, e.getMessage());
            return false;
        }
    }

    /**
     * 비밀번호 변경
     */
    public boolean changePassword(String studentId, String currentPassword, String newPassword) {
        logger.debug("Changing password for student: {}", studentId);

        try {
            // 학생 조회 - SELECT
            Student student = studentRepository.findByStudentId(studentId)
                    .orElseThrow(() -> new RuntimeException("학생을 찾을 수 없습니다: " + studentId));

            // 현재 비밀번호 확인
            if (!passwordEncoder.matches(currentPassword, student.getPassword())) {
                logger.warn("Current password mismatch for student: {}", studentId);
                return false;
            }

            // 새 비밀번호 암호화 및 저장
            String encodedPassword = passwordEncoder.encode(newPassword);
            student.setPassword(encodedPassword);
            
            // 비밀번호 업데이트 - UPDATE
            studentRepository.save(student);
            
            logger.info("Password changed for student: {}", studentId);
            return true;

        } catch (Exception e) {
            logger.error("Failed to change password for student {}: {}", studentId, e.getMessage());
            return false;
        }
    }

    /**
     * 학생의 학점 정보 업데이트
     * - 수강신청/취소 시 호출되는 메서드
     */
    @Transactional
    public void updateStudentCredits(String studentId) {
        logger.debug("Updating credits for student: {}", studentId);

        try {
            Student student = studentRepository.findByStudentId(studentId)
                    .orElseThrow(() -> new RuntimeException("학생을 찾을 수 없습니다: " + studentId));

            // 전체 이수 학점 재계산 - Complex aggregate query
            // TODO: 실제로는 성적 처리 시스템과 연동하여 계산해야 함
            // 현재는 현재 학기 신청 학점만 업데이트

            Semester currentSemester = getCurrentSemester();
            Integer currentCredits = enrollmentRepository
                    .getTotalCreditsByStudentAndSemester(student, currentSemester);

            // 학점 업데이트 - UPDATE
            int updatedRows = studentRepository.updateTotalCredits(studentId, 
                    currentCredits != null ? currentCredits : 0);

            if (updatedRows > 0) {
                logger.debug("Updated credits for student {}: {}", studentId, currentCredits);
            }

        } catch (Exception e) {
            logger.error("Failed to update credits for student {}: {}", studentId, e.getMessage());
        }
    }

    /**
     * 학생 존재 여부 확인
     */
    @Transactional(readOnly = true)
    public boolean studentExists(String studentId) {
        return studentRepository.existsById(studentId);
    }

    /**
     * 학생 상세 정보 조회 (관리자용)
     */
    @Transactional(readOnly = true)
    public Optional<StudentDTO> getStudentDetails(String studentId) {
        logger.debug("Getting detailed info for student: {}", studentId);

        return studentRepository.findByStudentId(studentId)
                .map(StudentDTO::new);
    }

    // Helper method
    private Semester getCurrentSemester() {
        return semesterRepository.findCurrentSemester()
                .orElseThrow(() -> new RuntimeException("현재 학기 정보를 찾을 수 없습니다."));
    }
}