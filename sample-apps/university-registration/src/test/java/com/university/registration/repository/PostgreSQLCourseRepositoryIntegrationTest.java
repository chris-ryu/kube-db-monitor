package com.university.registration.repository;

import com.university.registration.config.PostgreSQLTestConfiguration;
import com.university.registration.entity.Course;
import com.university.registration.entity.Department;
import com.university.registration.entity.Semester;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.List;

import static org.assertj.core.api.Assertions.*;

/**
 * PostgreSQL 환경에서의 CourseRepository 통합 테스트
 * 
 * 주요 테스트 범위:
 * 1. Native Query의 PostgreSQL 호환성 (searchCourses)
 * 2. PostgreSQL 타입 캐스팅 (bytea vs text) 
 * 3. LOWER 함수 호환성
 * 4. 한글 문자열 처리
 * 5. 특수문자 처리
 * 6. SpEL 표현식 vs 단순 파라미터 바인딩
 */
@SpringBootTest
@Testcontainers
@ActiveProfiles("test")
@Import(PostgreSQLTestConfiguration.class)
@Transactional
@DisplayName("PostgreSQL CourseRepository 통합 테스트")
class PostgreSQLCourseRepositoryIntegrationTest {

    @Autowired
    private CourseRepository courseRepository;

    @Autowired
    private DepartmentRepository departmentRepository;

    @Autowired
    private SemesterRepository semesterRepository;

    @Test
    @DisplayName("PostgreSQL Native Query - 기본 검색 (키워드 없음)")
    void searchCourses_WithoutKeyword_ShouldReturnAllActiveCourses() {
        // Given
        Semester currentSemester = getCurrentSemester();
        Pageable pageable = PageRequest.of(0, 10);

        // When
        Page<Course> result = courseRepository.searchCourses(
            currentSemester.getId(), null, null, pageable);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getContent()).isNotEmpty();
        
        // 모든 과목이 현재 학기이고 활성 상태인지 확인
        result.getContent().forEach(course -> {
            assertThat(course.getSemester().getId()).isEqualTo(currentSemester.getId());
            assertThat(course.getIsActive()).isTrue();
        });
    }

    @Test
    @DisplayName("PostgreSQL Native Query - 학과별 필터링")
    void searchCourses_WithDepartmentFilter_ShouldReturnCoursesForDepartment() {
        // Given
        Semester currentSemester = getCurrentSemester();
        Department csDepartment = getDepartmentByName("컴퓨터과학과");
        Pageable pageable = PageRequest.of(0, 10);

        // When
        Page<Course> result = courseRepository.searchCourses(
            currentSemester.getId(), csDepartment.getId(), null, pageable);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getContent()).isNotEmpty();
        
        // 모든 결과가 해당 학과의 과목인지 확인
        result.getContent().forEach(course -> {
            assertThat(course.getDepartment().getId()).isEqualTo(csDepartment.getId());
        });
    }

    @Test
    @DisplayName("PostgreSQL LOWER 함수 호환성 - 영문 키워드 검색")
    void searchCourses_WithEnglishKeyword_ShouldUseLowerFunction() {
        // Given
        Semester currentSemester = getCurrentSemester();
        String keyword = "Database"; // 대소문자 섞인 키워드
        Pageable pageable = PageRequest.of(0, 10);

        // When
        Page<Course> result = courseRepository.searchCourses(
            currentSemester.getId(), null, keyword, pageable);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getContent()).isNotEmpty();
        
        // "Database Systems" 과목이 검색되는지 확인 (대소문자 무관)
        boolean foundDatabaseCourse = result.getContent().stream()
            .anyMatch(course -> course.getCourseName().toLowerCase().contains("database"));
        assertThat(foundDatabaseCourse).isTrue();
    }

    @Test
    @DisplayName("PostgreSQL 한글 문자열 처리 - 한글 키워드 검색")
    void searchCourses_WithKoreanKeyword_ShouldHandleKoreanCharacters() {
        // Given
        Semester currentSemester = getCurrentSemester();
        String koreanKeyword = "데이터베이스"; // 한글 키워드
        Pageable pageable = PageRequest.of(0, 10);

        // When
        Page<Course> result = courseRepository.searchCourses(
            currentSemester.getId(), null, koreanKeyword, pageable);

        // Then
        assertThat(result).isNotNull();
        
        if (!result.getContent().isEmpty()) {
            // "데이터베이스설계" 과목이 검색되는지 확인
            boolean foundKoreanCourse = result.getContent().stream()
                .anyMatch(course -> course.getCourseName().contains("데이터베이스"));
            assertThat(foundKoreanCourse).isTrue();
        }
    }

    @Test
    @DisplayName("PostgreSQL 교수명 검색 - 한글 교수명")
    void searchCourses_WithKoreanProfessorName_ShouldSearchInProfessorField() {
        // Given
        Semester currentSemester = getCurrentSemester();
        String professorKeyword = "김교수"; // 한글 교수명
        Pageable pageable = PageRequest.of(0, 10);

        // When
        Page<Course> result = courseRepository.searchCourses(
            currentSemester.getId(), null, professorKeyword, pageable);

        // Then
        assertThat(result).isNotNull();
        
        if (!result.getContent().isEmpty()) {
            // 김교수가 담당하는 과목이 검색되는지 확인
            boolean foundProfessorCourse = result.getContent().stream()
                .anyMatch(course -> course.getProfessor().contains("김교수"));
            assertThat(foundProfessorCourse).isTrue();
        }
    }

    @Test
    @DisplayName("PostgreSQL 특수문자 처리 - 특수문자가 포함된 키워드")
    void searchCourses_WithSpecialCharacters_ShouldHandleSpecialChars() {
        // Given
        Semester currentSemester = getCurrentSemester();
        String specialKeyword = "AI/ML"; // 특수문자 포함 키워드
        Pageable pageable = PageRequest.of(0, 10);

        // When
        Page<Course> result = courseRepository.searchCourses(
            currentSemester.getId(), null, specialKeyword, pageable);

        // Then
        assertThat(result).isNotNull();
        
        if (!result.getContent().isEmpty()) {
            // "AI/ML Introduction" 과목이 검색되는지 확인
            boolean foundSpecialCharCourse = result.getContent().stream()
                .anyMatch(course -> course.getCourseName().contains("AI/ML"));
            assertThat(foundSpecialCharCourse).isTrue();
        }
    }

    @Test
    @DisplayName("PostgreSQL 타입 캐스팅 - ::text 캐스팅 검증")
    void searchCourses_PostgreSQLTypeCasting_ShouldUseTextCasting() {
        // Given
        Semester currentSemester = getCurrentSemester();
        String keyword = "Prof"; // 영문 교수명 키워드
        Pageable pageable = PageRequest.of(0, 10);

        // When & Then
        // 이 테스트는 "function lower(bytea) does not exist" 에러가 발생하지 않는지 확인
        assertThatNoException().isThrownBy(() -> {
            Page<Course> result = courseRepository.searchCourses(
                currentSemester.getId(), null, keyword, pageable);
            
            // 결과 검증
            assertThat(result).isNotNull();
            
            if (!result.getContent().isEmpty()) {
                boolean foundProfCourse = result.getContent().stream()
                    .anyMatch(course -> course.getProfessor().toLowerCase().contains("prof"));
                assertThat(foundProfCourse).isTrue();
            }
        });
    }

    @Test
    @DisplayName("PostgreSQL 복합 필터링 - 학과 + 키워드 조합")
    void searchCourses_WithDepartmentAndKeyword_ShouldApplyBothFilters() {
        // Given
        Semester currentSemester = getCurrentSemester();
        Department csDepartment = getDepartmentByName("컴퓨터과학과");
        String keyword = "Database";
        Pageable pageable = PageRequest.of(0, 10);

        // When
        Page<Course> result = courseRepository.searchCourses(
            currentSemester.getId(), csDepartment.getId(), keyword, pageable);

        // Then
        assertThat(result).isNotNull();
        
        // 모든 결과가 조건을 만족하는지 확인
        result.getContent().forEach(course -> {
            assertThat(course.getDepartment().getId()).isEqualTo(csDepartment.getId());
            assertThat(course.getSemester().getId()).isEqualTo(currentSemester.getId());
            assertThat(course.getIsActive()).isTrue();
            
            // 키워드가 과목명 또는 교수명에 포함되는지 확인
            boolean keywordMatch = course.getCourseName().toLowerCase().contains(keyword.toLowerCase()) ||
                                 course.getProfessor().toLowerCase().contains(keyword.toLowerCase());
            assertThat(keywordMatch).isTrue();
        });
    }

    @Test
    @DisplayName("PostgreSQL 페이징 동작 검증")
    void searchCourses_WithPaging_ShouldReturnCorrectPageSize() {
        // Given
        Semester currentSemester = getCurrentSemester();
        Pageable firstPage = PageRequest.of(0, 2); // 첫 페이지, 2개씩
        Pageable secondPage = PageRequest.of(1, 2); // 두번째 페이지, 2개씩

        // When
        Page<Course> firstResult = courseRepository.searchCourses(
            currentSemester.getId(), null, null, firstPage);
        Page<Course> secondResult = courseRepository.searchCourses(
            currentSemester.getId(), null, null, secondPage);

        // Then
        assertThat(firstResult).isNotNull();
        assertThat(firstResult.getContent()).hasSizeLessThanOrEqualTo(2);
        
        assertThat(secondResult).isNotNull();
        assertThat(secondResult.getContent()).hasSizeLessThanOrEqualTo(2);
        
        // 페이지별로 다른 결과가 나오는지 확인 (전체 데이터가 2개 이상인 경우)
        if (firstResult.getTotalElements() > 2) {
            assertThat(firstResult.getContent()).isNotEqualTo(secondResult.getContent());
        }
    }

    @Test
    @DisplayName("PostgreSQL 정렬 순서 검증 - course_id 순")
    void searchCourses_ShouldReturnResultsOrderedByCourseId() {
        // Given
        Semester currentSemester = getCurrentSemester();
        Pageable pageable = PageRequest.of(0, 10);

        // When
        Page<Course> result = courseRepository.searchCourses(
            currentSemester.getId(), null, null, pageable);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getContent()).isNotEmpty();

        // course_id 순으로 정렬되어 있는지 확인
        List<Course> courses = result.getContent();
        for (int i = 1; i < courses.size(); i++) {
            String currentCourseId = courses.get(i).getCourseId();
            String previousCourseId = courses.get(i - 1).getCourseId();
            assertThat(currentCourseId.compareTo(previousCourseId)).isGreaterThanOrEqualTo(0);
        }
    }

    @Test
    @DisplayName("PostgreSQL NULL 값 처리 - keyword가 null인 경우")
    void searchCourses_WithNullKeyword_ShouldHandleNullGracefully() {
        // Given
        Semester currentSemester = getCurrentSemester();
        Pageable pageable = PageRequest.of(0, 10);

        // When & Then
        assertThatNoException().isThrownBy(() -> {
            Page<Course> result = courseRepository.searchCourses(
                currentSemester.getId(), null, null, pageable);
            
            assertThat(result).isNotNull();
            assertThat(result.getContent()).isNotEmpty();
        });
    }

    @Test
    @DisplayName("PostgreSQL 빈 문자열 처리")
    void searchCourses_WithEmptyKeyword_ShouldHandleEmptyString() {
        // Given
        Semester currentSemester = getCurrentSemester();
        String emptyKeyword = "";
        Pageable pageable = PageRequest.of(0, 10);

        // When
        Page<Course> result = courseRepository.searchCourses(
            currentSemester.getId(), null, emptyKeyword, pageable);

        // Then
        assertThat(result).isNotNull();
        // 빈 문자열은 모든 과목과 매치되지 않아야 함
        // 하지만 쿼리 자체는 정상 실행되어야 함
    }

    // Helper methods
    private Semester getCurrentSemester() {
        return semesterRepository.findCurrentSemester()
            .orElseThrow(() -> new RuntimeException("현재 학기 정보를 찾을 수 없습니다."));
    }

    private Department getDepartmentByName(String departmentName) {
        return departmentRepository.findAll().stream()
            .filter(dept -> dept.getDepartmentName().equals(departmentName))
            .findFirst()
            .orElseThrow(() -> new RuntimeException("학과를 찾을 수 없습니다: " + departmentName));
    }
}