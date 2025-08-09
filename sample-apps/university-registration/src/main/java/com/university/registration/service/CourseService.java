package com.university.registration.service;

import com.university.registration.dto.CourseDTO;
import com.university.registration.entity.Course;
import com.university.registration.entity.Department;
import com.university.registration.entity.Semester;
import com.university.registration.repository.CourseRepository;
import com.university.registration.repository.DepartmentRepository;
import com.university.registration.repository.SemesterRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@Transactional(readOnly = true)
public class CourseService {

    private static final Logger logger = LoggerFactory.getLogger(CourseService.class);

    @Autowired private CourseRepository courseRepository;
    @Autowired private DepartmentRepository departmentRepository;
    @Autowired private SemesterRepository semesterRepository;

    /**
     * 과목 검색 (페이징 지원)
     * - 복잡한 WHERE 조건과 JOIN을 포함한 쿼리
     * - KubeDB Monitor의 SELECT 성능 테스트 포인트
     */
    @Cacheable(value = "courseSearch", key = "#departmentId + '_' + #keyword + '_' + #page + '_' + #size")
    public Page<CourseDTO> searchCourses(Long departmentId, String keyword, int page, int size) {
        logger.debug("Searching courses with department: {}, keyword: {}, page: {}, size: {}", 
                    departmentId, keyword, page, size);

        Semester currentSemester = getCurrentSemester();
        Pageable pageable = PageRequest.of(page, size, Sort.by("courseId"));

        // 복잡한 검색 쿼리 실행 - JOIN with WHERE conditions
        Page<Course> coursePage = courseRepository.searchCourses(
            currentSemester, departmentId, keyword, pageable);

        // Entity를 DTO로 변환
        return coursePage.map(CourseDTO::new);
    }

    /**
     * 과목 상세 정보 조회
     */
    @Cacheable(value = "courseDetails", key = "#courseId")
    public Optional<CourseDTO> getCourseById(String courseId) {
        logger.debug("Getting course details for: {}", courseId);

        return courseRepository.findByCourseId(courseId)
                .map(CourseDTO::new);
    }

    /**
     * 인기 과목 조회
     * - 정원 대비 신청률이 높은 과목들
     * - 복잡한 계산이 포함된 쿼리
     */
    @Cacheable(value = "popularCourses", key = "#threshold")
    public List<CourseDTO> getPopularCourses(Double threshold) {
        logger.debug("Getting popular courses with threshold: {}", threshold);

        Semester currentSemester = getCurrentSemester();
        
        // 인기 과목 조회 - 계산식이 포함된 복잡한 쿼리
        List<Course> popularCourses = courseRepository.findPopularCourses(currentSemester, threshold);

        return popularCourses.stream()
                .map(CourseDTO::new)
                .collect(Collectors.toList());
    }

    /**
     * 정원이 남은 과목 조회
     */
    @Cacheable(value = "availableCourses")
    public List<CourseDTO> getAvailableCourses() {
        logger.debug("Getting available courses");

        List<Course> availableCourses = courseRepository.findAvailableCourses();
        
        return availableCourses.stream()
                .map(CourseDTO::new)
                .collect(Collectors.toList());
    }

    /**
     * 학과별 과목 조회
     */
    public List<CourseDTO> getCoursesByDepartment(Long departmentId) {
        logger.debug("Getting courses by department: {}", departmentId);

        Department department = departmentRepository.findById(departmentId)
                .orElseThrow(() -> new RuntimeException("존재하지 않는 학과입니다: " + departmentId));

        Semester currentSemester = getCurrentSemester();
        List<Course> courses = courseRepository.findBySemesterAndDepartment(currentSemester, department);

        return courses.stream()
                .map(CourseDTO::new)
                .collect(Collectors.toList());
    }

    /**
     * 시간표 충돌 과목 조회
     */
    public List<CourseDTO> getTimeConflictCourses(String dayTime) {
        logger.debug("Getting courses with day/time: {}", dayTime);

        Semester currentSemester = getCurrentSemester();
        List<Course> conflictCourses = courseRepository.findByDayTimeAndSemester(dayTime, currentSemester);

        return conflictCourses.stream()
                .map(CourseDTO::new)
                .collect(Collectors.toList());
    }

    /**
     * 선수과목이 있는 과목들 조회
     */
    public List<CourseDTO> getCoursesWithPrerequisites() {
        logger.debug("Getting courses with prerequisites");

        List<Course> coursesWithPrerequisites = courseRepository.findCoursesWithPrerequisites();

        return coursesWithPrerequisites.stream()
                .map(CourseDTO::new)
                .collect(Collectors.toList());
    }

    /**
     * 과목별 수강신청 현황 조회
     * - 실시간 정원 정보 확인용
     */
    public CourseDTO getCourseAvailability(String courseId) {
        logger.debug("Getting availability for course: {}", courseId);

        Course course = courseRepository.findByCourseId(courseId)
                .orElseThrow(() -> new RuntimeException("존재하지 않는 과목입니다: " + courseId));

        return new CourseDTO(course);
    }

    /**
     * 통계 조회 메서드들 (성능 테스트용)
     */
    
    /**
     * 학과별 수강신청 통계
     */
    public List<Object[]> getEnrollmentStatsByDepartment() {
        logger.debug("Getting enrollment statistics by department");

        Semester currentSemester = getCurrentSemester();
        
        // 집계 함수를 사용한 통계 쿼리
        return courseRepository.getEnrollmentStatsByDepartment(currentSemester);
    }

    /**
     * 교수별 과목 수 통계
     */
    public List<Object[]> getCourseStatsByProfessor() {
        logger.debug("Getting course statistics by professor");

        Semester currentSemester = getCurrentSemester();
        
        // GROUP BY를 사용한 통계 쿼리
        return courseRepository.countCoursesByProfessor(currentSemester);
    }

    /**
     * 시간대별 과목 통계
     */
    public List<Object[]> getTimeSlotStatistics() {
        logger.debug("Getting time slot statistics");

        Semester currentSemester = getCurrentSemester();
        
        // 복잡한 집계 함수들을 사용한 통계 쿼리
        return courseRepository.getTimeSlotStatistics(currentSemester);
    }

    /**
     * 수강신청 인원이 많은 과목들과 상세 정보 조회
     * - 복잡한 JOIN과 FETCH를 포함한 성능 테스트용 쿼리
     */
    public List<CourseDTO> getCoursesWithEnrollmentDetails() {
        logger.debug("Getting courses with enrollment details (complex JOIN query)");

        Semester currentSemester = getCurrentSemester();
        
        // 여러 테이블을 JOIN하고 FETCH하는 복잡한 쿼리
        List<Course> coursesWithDetails = courseRepository.findCoursesWithEnrollmentDetails(currentSemester);

        return coursesWithDetails.stream()
                .map(CourseDTO::new)
                .collect(Collectors.toList());
    }

    /**
     * 전체 과목 평균 수강신청 인원
     */
    public Double getAverageEnrollment() {
        logger.debug("Getting average enrollment");

        Semester currentSemester = getCurrentSemester();
        return courseRepository.getAverageEnrollment(currentSemester);
    }

    // Helper methods
    private Semester getCurrentSemester() {
        return semesterRepository.findCurrentSemester()
                .orElseThrow(() -> new RuntimeException("현재 학기 정보를 찾을 수 없습니다."));
    }
}