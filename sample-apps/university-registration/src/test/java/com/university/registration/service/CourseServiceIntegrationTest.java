package com.university.registration.service;

import com.university.registration.config.PostgreSQLTestConfiguration;
import com.university.registration.dto.CourseDTO;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;

/**
 * CourseService 통합 테스트
 * 
 * 주요 테스트 범위:
 * 1. 과목 검색 서비스 로직
 * 2. 과목 정보 조회 서비스
 * 3. 통계 및 집계 서비스
 * 4. PostgreSQL 호환성 검증
 * 5. 비즈니스 로직 검증
 */
@SpringBootTest
@Testcontainers
@ActiveProfiles("test")
@Import(PostgreSQLTestConfiguration.class)
@DisplayName("CourseService 통합 테스트")
class CourseServiceIntegrationTest {

    @Autowired
    private CourseService courseService;

    @Test
    @DisplayName("검색 서비스 - 기본 페이징")
    void searchCourses_BasicPaging_ShouldReturnPagedResults() {
        // Given
        Long departmentId = null;
        String keyword = null;
        int page = 0;
        int size = 10;

        // When
        Page<CourseDTO> result = courseService.searchCourses(departmentId, keyword, page, size);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getContent()).isNotEmpty();
        assertThat(result.getSize()).isEqualTo(size);
        assertThat(result.getNumber()).isEqualTo(page);
        assertThat(result.getTotalElements()).isGreaterThan(0);
        assertThat(result.getTotalPages()).isGreaterThan(0);
    }

    @Test
    @DisplayName("검색 서비스 - 키워드 검색")
    void searchCourses_WithKeyword_ShouldFilterByKeyword() {
        // Given
        String keyword = "Database";
        int page = 0;
        int size = 20;

        // When
        Page<CourseDTO> result = courseService.searchCourses(null, keyword, page, size);

        // Then
        assertThat(result).isNotNull();
        
        if (!result.getContent().isEmpty()) {
            // 검색된 결과에 키워드가 포함되어 있는지 확인
            boolean keywordFound = result.getContent().stream()
                .anyMatch(course -> 
                    course.getCourseName().toLowerCase().contains(keyword.toLowerCase()) ||
                    course.getProfessor().toLowerCase().contains(keyword.toLowerCase()));
            assertThat(keywordFound).isTrue();
        }
    }

    @Test
    @DisplayName("검색 서비스 - 한글 키워드 검색")
    void searchCourses_WithKoreanKeyword_ShouldHandleKoreanText() {
        // Given
        String koreanKeyword = "데이터베이스";
        int page = 0;
        int size = 20;

        // When
        Page<CourseDTO> result = courseService.searchCourses(null, koreanKeyword, page, size);

        // Then
        assertThat(result).isNotNull();
        
        if (!result.getContent().isEmpty()) {
            boolean keywordFound = result.getContent().stream()
                .anyMatch(course -> 
                    course.getCourseName().contains(koreanKeyword) ||
                    course.getProfessor().contains(koreanKeyword));
            assertThat(keywordFound).isTrue();
        }
    }

    @Test
    @DisplayName("검색 서비스 - 학과별 필터링")
    void searchCourses_WithDepartmentFilter_ShouldFilterByDepartment() {
        // Given
        Long departmentId = 1L;
        int page = 0;
        int size = 20;

        // When
        Page<CourseDTO> result = courseService.searchCourses(departmentId, null, page, size);

        // Then
        assertThat(result).isNotNull();
        
        if (!result.getContent().isEmpty()) {
            result.getContent().forEach(course -> {
                assertThat(course.getDepartmentId()).isEqualTo(departmentId);
            });
        }
    }

    @Test
    @DisplayName("검색 서비스 - 복합 필터링")
    void searchCourses_WithDepartmentAndKeyword_ShouldApplyBothFilters() {
        // Given
        Long departmentId = 1L;
        String keyword = "Database";
        int page = 0;
        int size = 20;

        // When
        Page<CourseDTO> result = courseService.searchCourses(departmentId, keyword, page, size);

        // Then
        assertThat(result).isNotNull();
        
        result.getContent().forEach(course -> {
            // 학과 필터링 확인
            assertThat(course.getDepartmentId()).isEqualTo(departmentId);
            
            // 키워드 필터링 확인
            boolean keywordMatch = course.getCourseName().toLowerCase().contains(keyword.toLowerCase()) ||
                                 course.getProfessor().toLowerCase().contains(keyword.toLowerCase());
            assertThat(keywordMatch).isTrue();
        });
    }

    @Test
    @DisplayName("검색 서비스 - 빈 키워드 처리")
    void searchCourses_WithEmptyKeyword_ShouldReturnAllCourses() {
        // Given
        String emptyKeyword = "";
        int page = 0;
        int size = 10;

        // When
        Page<CourseDTO> result = courseService.searchCourses(null, emptyKeyword, page, size);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getContent()).isNotEmpty();
    }

    @Test
    @DisplayName("과목 조회 서비스 - 유효한 과목 ID")
    void getCourseById_WithValidCourseId_ShouldReturnCourse() {
        // Given
        String courseId = "CSE101";

        // When
        Optional<CourseDTO> result = courseService.getCourseById(courseId);

        // Then
        if (result.isPresent()) {
            CourseDTO course = result.get();
            assertThat(course.getCourseId()).isEqualTo(courseId);
            assertThat(course.getCourseName()).isNotNull();
            assertThat(course.getProfessor()).isNotNull();
            assertThat(course.getCredits()).isGreaterThan(0);
        }
    }

    @Test
    @DisplayName("과목 조회 서비스 - 존재하지 않는 과목 ID")
    void getCourseById_WithInvalidCourseId_ShouldReturnEmpty() {
        // Given
        String invalidCourseId = "INVALID999";

        // When
        Optional<CourseDTO> result = courseService.getCourseById(invalidCourseId);

        // Then
        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("과목 가용성 서비스 - 정원 정보 확인")
    void getCourseAvailability_WithValidCourseId_ShouldReturnAvailabilityInfo() {
        // Given
        String courseId = "CSE101";

        // When & Then
        assertThatNoException().isThrownBy(() -> {
            CourseDTO course = courseService.getCourseAvailability(courseId);
            assertThat(course).isNotNull();
            assertThat(course.getMaxCapacity()).isGreaterThan(0);
            assertThat(course.getCurrentEnrollment()).isGreaterThanOrEqualTo(0);
            assertThat(course.getCurrentEnrollment()).isLessThanOrEqualTo(course.getMaxCapacity());
        });
    }

    @Test
    @DisplayName("수강 가능한 과목 서비스 - 정원이 남은 과목들")
    void getAvailableCourses_ShouldReturnCoursesWithAvailability() {
        // When
        List<CourseDTO> result = courseService.getAvailableCourses();

        // Then
        assertThat(result).isNotNull();
        
        result.forEach(course -> {
            assertThat(course.getCurrentEnrollment()).isLessThan(course.getMaxCapacity());
            assertThat(course.getIsActive()).isTrue();
        });
    }

    @Test
    @DisplayName("인기 과목 서비스 - 기본 임계값")
    void getPopularCourses_WithDefaultThreshold_ShouldReturnPopularCourses() {
        // Given
        Double threshold = 0.8;

        // When
        List<CourseDTO> result = courseService.getPopularCourses(threshold);

        // Then
        assertThat(result).isNotNull();
        
        result.forEach(course -> {
            double enrollmentRatio = (double) course.getCurrentEnrollment() / course.getMaxCapacity();
            assertThat(enrollmentRatio).isGreaterThanOrEqualTo(threshold);
        });
    }

    @Test
    @DisplayName("인기 과목 서비스 - 다양한 임계값")
    void getPopularCourses_WithVariousThresholds_ShouldFilterCorrectly() {
        // Given
        Double[] thresholds = {0.5, 0.7, 0.8, 0.9, 1.0};

        for (Double threshold : thresholds) {
            // When
            List<CourseDTO> result = courseService.getPopularCourses(threshold);

            // Then
            assertThat(result).isNotNull();
            
            result.forEach(course -> {
                double enrollmentRatio = (double) course.getCurrentEnrollment() / course.getMaxCapacity();
                assertThat(enrollmentRatio).isGreaterThanOrEqualTo(threshold);
            });
        }
    }

    @Test
    @DisplayName("학과별 과목 서비스")
    void getCoursesByDepartment_WithValidDepartmentId_ShouldReturnDepartmentCourses() {
        // Given
        Long departmentId = 1L;

        // When & Then
        assertThatNoException().isThrownBy(() -> {
            List<CourseDTO> result = courseService.getCoursesByDepartment(departmentId);
            assertThat(result).isNotNull();
            
            result.forEach(course -> {
                assertThat(course.getDepartmentId()).isEqualTo(departmentId);
            });
        });
    }

    @Test
    @DisplayName("시간표 충돌 과목 서비스")
    void getTimeConflictCourses_WithValidTimeSlot_ShouldReturnConflictingCourses() {
        // Given
        String dayTime = "월1,수3";

        // When
        List<CourseDTO> result = courseService.getTimeConflictCourses(dayTime);

        // Then
        assertThat(result).isNotNull();
        
        result.forEach(course -> {
            assertThat(course.getDayTime()).contains("월1");
        });
    }

    @Test
    @DisplayName("선수과목 있는 과목 서비스")
    void getCoursesWithPrerequisites_ShouldReturnCoursesWithPrerequisites() {
        // When
        List<CourseDTO> result = courseService.getCoursesWithPrerequisites();

        // Then
        assertThat(result).isNotNull();
        
        result.forEach(course -> {
            assertThat(course.getPrerequisites()).isNotNull();
            assertThat(course.getPrerequisites()).isNotEmpty();
        });
    }

    /**
     * 통계 서비스 테스트
     */

    @Test
    @DisplayName("학과별 수강신청 통계 서비스")
    void getEnrollmentStatsByDepartment_ShouldReturnDepartmentStatistics() {
        // When
        List<Object[]> result = courseService.getEnrollmentStatsByDepartment();

        // Then
        assertThat(result).isNotNull();
        
        result.forEach(stat -> {
            assertThat(stat).hasSize(3); // [부서명, 과목수, 총수강인원] 형태 예상
            assertThat(stat[0]).isNotNull(); // 부서명
            assertThat(stat[1]).isInstanceOf(Number.class); // 과목수
            assertThat(stat[2]).isInstanceOf(Number.class); // 총수강인원
        });
    }

    @Test
    @DisplayName("교수별 과목 수 통계 서비스")
    void getCourseStatsByProfessor_ShouldReturnProfessorStatistics() {
        // When
        List<Object[]> result = courseService.getCourseStatsByProfessor();

        // Then
        assertThat(result).isNotNull();
        
        result.forEach(stat -> {
            assertThat(stat).hasSizeGreaterThanOrEqualTo(2); // [교수명, 과목수] 최소 형태
            assertThat(stat[0]).isNotNull(); // 교수명
            assertThat(stat[1]).isInstanceOf(Number.class); // 과목수
        });
    }

    @Test
    @DisplayName("시간대별 과목 통계 서비스")
    void getTimeSlotStatistics_ShouldReturnTimeSlotStatistics() {
        // When
        List<Object[]> result = courseService.getTimeSlotStatistics();

        // Then
        assertThat(result).isNotNull();
        
        result.forEach(stat -> {
            assertThat(stat).hasSizeGreaterThanOrEqualTo(2); // [시간대, 과목수] 최소 형태
            assertThat(stat[0]).isNotNull(); // 시간대
            assertThat(stat[1]).isInstanceOf(Number.class); // 과목수
        });
    }

    @Test
    @DisplayName("평균 수강신청 인원 서비스")
    void getAverageEnrollment_ShouldReturnAverageValue() {
        // When
        Double result = courseService.getAverageEnrollment();

        // Then
        assertThat(result).isNotNull();
        assertThat(result).isGreaterThanOrEqualTo(0.0);
    }

    @Test
    @DisplayName("복잡한 JOIN 쿼리 서비스")
    void getCoursesWithEnrollmentDetails_ShouldReturnDetailedCourses() {
        // When
        List<CourseDTO> result = courseService.getCoursesWithEnrollmentDetails();

        // Then
        assertThat(result).isNotNull();
        
        result.forEach(course -> {
            assertThat(course.getCourseId()).isNotNull();
            assertThat(course.getCourseName()).isNotNull();
            assertThat(course.getCurrentEnrollment()).isGreaterThanOrEqualTo(0);
            assertThat(course.getMaxCapacity()).isGreaterThan(0);
        });
    }

    /**
     * 경계값 및 예외 처리 테스트
     */

    @Test
    @DisplayName("검색 서비스 - 음수 페이지 번호 처리")
    void searchCourses_WithNegativePage_ShouldHandleGracefully() {
        // Given
        int negativePage = -1;
        int size = 10;

        // When & Then
        assertThatNoException().isThrownBy(() -> {
            Page<CourseDTO> result = courseService.searchCourses(null, null, negativePage, size);
            assertThat(result).isNotNull();
        });
    }

    @Test
    @DisplayName("검색 서비스 - 큰 페이지 크기 처리")
    void searchCourses_WithLargePageSize_ShouldHandleGracefully() {
        // Given
        int page = 0;
        int largeSize = 10000;

        // When & Then
        assertThatNoException().isThrownBy(() -> {
            Page<CourseDTO> result = courseService.searchCourses(null, null, page, largeSize);
            assertThat(result).isNotNull();
        });
    }

    @Test
    @DisplayName("검색 서비스 - 매우 긴 키워드 처리")
    void searchCourses_WithVeryLongKeyword_ShouldHandleGracefully() {
        // Given
        String longKeyword = "a".repeat(1000);
        int page = 0;
        int size = 10;

        // When & Then
        assertThatNoException().isThrownBy(() -> {
            Page<CourseDTO> result = courseService.searchCourses(null, longKeyword, page, size);
            assertThat(result).isNotNull();
        });
    }

    @Test
    @DisplayName("존재하지 않는 학과로 과목 검색")
    void getCoursesByDepartment_WithInvalidDepartmentId_ShouldThrowException() {
        // Given
        Long invalidDepartmentId = 999L;

        // When & Then
        assertThatThrownBy(() -> {
            courseService.getCoursesByDepartment(invalidDepartmentId);
        }).isInstanceOf(RuntimeException.class);
    }

    @Test
    @DisplayName("존재하지 않는 과목의 가용성 조회")
    void getCourseAvailability_WithInvalidCourseId_ShouldThrowException() {
        // Given
        String invalidCourseId = "INVALID999";

        // When & Then
        assertThatThrownBy(() -> {
            courseService.getCourseAvailability(invalidCourseId);
        }).isInstanceOf(RuntimeException.class);
    }

    /**
     * PostgreSQL 특화 테스트
     */

    @Test
    @DisplayName("PostgreSQL 특수문자 키워드 처리")
    void postgresqlSpecialCharacterHandling() {
        // Given
        String[] specialKeywords = {
            "AI/ML", "C++", "C#", ".NET", "Node.js", "AI & ML"
        };

        for (String keyword : specialKeywords) {
            // When & Then
            assertThatNoException().isThrownBy(() -> {
                Page<CourseDTO> result = courseService.searchCourses(null, keyword, 0, 10);
                assertThat(result).isNotNull();
            });
        }
    }

    @Test
    @DisplayName("PostgreSQL 유니코드 문자 처리")
    void postgresqlUnicodeCharacterHandling() {
        // Given
        String[] unicodeKeywords = {
            "데이터베이스", "알고리즘", "컴퓨터과학", "소프트웨어공학",
            "머신러닝", "인공지능", "네트워크보안", "운영체제"
        };

        for (String keyword : unicodeKeywords) {
            // When & Then
            assertThatNoException().isThrownBy(() -> {
                Page<CourseDTO> result = courseService.searchCourses(null, keyword, 0, 10);
                assertThat(result).isNotNull();
            });
        }
    }

    @Test
    @DisplayName("PostgreSQL 성능 테스트 - 대용량 통계 쿼리")
    void postgresqlPerformanceTest_LargeStatisticalQueries() {
        // When & Then
        assertThatNoException().isThrownBy(() -> {
            // 여러 통계 쿼리를 연속으로 실행
            List<Object[]> departmentStats = courseService.getEnrollmentStatsByDepartment();
            assertThat(departmentStats).isNotNull();
            
            List<Object[]> professorStats = courseService.getCourseStatsByProfessor();
            assertThat(professorStats).isNotNull();
            
            List<Object[]> timeSlotStats = courseService.getTimeSlotStatistics();
            assertThat(timeSlotStats).isNotNull();
            
            Double avgEnrollment = courseService.getAverageEnrollment();
            assertThat(avgEnrollment).isNotNull();
            
            List<CourseDTO> enrollmentDetails = courseService.getCoursesWithEnrollmentDetails();
            assertThat(enrollmentDetails).isNotNull();
        });
    }
}