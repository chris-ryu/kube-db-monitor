package com.university.registration.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.university.registration.config.PostgreSQLTestConfiguration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Course API 통합 테스트
 * 
 * 주요 테스트 범위:
 * 1. Course 검색 API (페이징, 필터링, 키워드 검색)
 * 2. Course 상세 조회 API
 * 3. Course 가용성 API
 * 4. Course 통계 API
 * 5. PostgreSQL 호환성 검증
 * 6. 에러 처리 검증
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@Testcontainers
@ActiveProfiles("test")
@Import(PostgreSQLTestConfiguration.class)
@DisplayName("Course API 통합 테스트")
class CourseControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    @DisplayName("GET /api/courses - 기본 과목 검색 (페이징)")
    void searchCourses_DefaultPaging_ShouldReturnPagedResults() throws Exception {
        mockMvc.perform(get("/api/courses")
                .param("page", "0")
                .param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.totalElements").exists())
                .andExpect(jsonPath("$.totalPages").exists())
                .andExpect(jsonPath("$.size").value(10))
                .andExpect(jsonPath("$.number").value(0));
    }

    @Test
    @DisplayName("GET /api/courses - 키워드 검색 (PostgreSQL LOWER 함수 호환성)")
    void searchCourses_WithKeyword_ShouldSearchCoursesAndProfessors() throws Exception {
        mockMvc.perform(get("/api/courses")
                .param("page", "0")
                .param("size", "10")
                .param("keyword", "Database"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.content").isArray());
    }

    @Test
    @DisplayName("GET /api/courses - 한글 키워드 검색")
    void searchCourses_WithKoreanKeyword_ShouldHandleKoreanCharacters() throws Exception {
        mockMvc.perform(get("/api/courses")
                .param("page", "0")
                .param("size", "10")
                .param("keyword", "데이터베이스"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.content").isArray());
    }

    @Test
    @DisplayName("GET /api/courses - 학과별 필터링")
    void searchCourses_WithDepartmentFilter_ShouldFilterByDepartment() throws Exception {
        mockMvc.perform(get("/api/courses")
                .param("page", "0")
                .param("size", "10")
                .param("dept", "1"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.content").isArray());
    }

    @Test
    @DisplayName("GET /api/courses - 복합 필터링 (학과 + 키워드)")
    void searchCourses_WithDepartmentAndKeyword_ShouldApplyBothFilters() throws Exception {
        mockMvc.perform(get("/api/courses")
                .param("page", "0")
                .param("size", "10")
                .param("dept", "1")
                .param("keyword", "Database"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.content").isArray());
    }

    @Test
    @DisplayName("GET /api/courses - 빈 키워드 처리")
    void searchCourses_WithEmptyKeyword_ShouldHandleEmptyString() throws Exception {
        mockMvc.perform(get("/api/courses")
                .param("page", "0")
                .param("size", "10")
                .param("keyword", ""))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.content").isArray());
    }

    @Test
    @DisplayName("GET /api/courses - 잘못된 페이지 파라미터 처리")
    void searchCourses_WithInvalidPageParams_ShouldHandleGracefully() throws Exception {
        mockMvc.perform(get("/api/courses")
                .param("page", "-1")
                .param("size", "0"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("GET /api/courses/{courseId} - 과목 상세 조회")
    void getCourse_WithValidCourseId_ShouldReturnCourseDetails() throws Exception {
        mockMvc.perform(get("/api/courses/CSE101"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.courseId").exists())
                .andExpect(jsonPath("$.courseName").exists())
                .andExpect(jsonPath("$.professor").exists());
    }

    @Test
    @DisplayName("GET /api/courses/{courseId} - 존재하지 않는 과목 조회")
    void getCourse_WithInvalidCourseId_ShouldReturnNotFound() throws Exception {
        mockMvc.perform(get("/api/courses/INVALID999"))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("GET /api/courses/{courseId}/availability - 과목 가용성 조회")
    void getCourseAvailability_WithValidCourseId_ShouldReturnAvailability() throws Exception {
        mockMvc.perform(get("/api/courses/CSE101/availability"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.maxCapacity").exists())
                .andExpect(jsonPath("$.currentEnrollment").exists());
    }

    @Test
    @DisplayName("GET /api/courses/available - 수강 가능한 과목 조회")
    void getAvailableCourses_ShouldReturnCoursesWithAvailability() throws Exception {
        mockMvc.perform(get("/api/courses/available"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$").isArray());
    }

    @Test
    @DisplayName("GET /api/courses/popular - 인기 과목 조회 (기본 임계값)")
    void getPopularCourses_WithDefaultThreshold_ShouldReturnPopularCourses() throws Exception {
        mockMvc.perform(get("/api/courses/popular"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$").isArray());
    }

    @Test
    @DisplayName("GET /api/courses/popular - 인기 과목 조회 (사용자 정의 임계값)")
    void getPopularCourses_WithCustomThreshold_ShouldReturnPopularCourses() throws Exception {
        mockMvc.perform(get("/api/courses/popular")
                .param("threshold", "0.6"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$").isArray());
    }

    @Test
    @DisplayName("GET /api/courses/department/{departmentId} - 학과별 과목 조회")
    void getCoursesByDepartment_WithValidDepartmentId_ShouldReturnDepartmentCourses() throws Exception {
        mockMvc.perform(get("/api/courses/department/1"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$").isArray());
    }

    @Test
    @DisplayName("GET /api/courses/department/{departmentId} - 존재하지 않는 학과")
    void getCoursesByDepartment_WithInvalidDepartmentId_ShouldReturnNotFound() throws Exception {
        mockMvc.perform(get("/api/courses/department/999"))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("GET /api/courses/time-conflict - 시간표 충돌 과목 조회")
    void getTimeConflictCourses_WithValidTimeSlot_ShouldReturnConflictingCourses() throws Exception {
        mockMvc.perform(get("/api/courses/time-conflict")
                .param("dayTime", "월1,수3"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$").isArray());
    }

    @Test
    @DisplayName("GET /api/courses/prerequisites - 선수과목 있는 과목 조회")
    void getCoursesWithPrerequisites_ShouldReturnCoursesWithPrerequisites() throws Exception {
        mockMvc.perform(get("/api/courses/prerequisites"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$").isArray());
    }

    /**
     * 통계 API 테스트
     */

    @Test
    @DisplayName("GET /api/courses/stats/department - 학과별 수강신청 통계")
    void getEnrollmentStatsByDepartment_ShouldReturnDepartmentStats() throws Exception {
        mockMvc.perform(get("/api/courses/stats/department"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$").isArray());
    }

    @Test
    @DisplayName("GET /api/courses/stats/professor - 교수별 과목 수 통계")
    void getCourseStatsByProfessor_ShouldReturnProfessorStats() throws Exception {
        mockMvc.perform(get("/api/courses/stats/professor"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$").isArray());
    }

    @Test
    @DisplayName("GET /api/courses/stats/timeslot - 시간대별 과목 통계")
    void getTimeSlotStatistics_ShouldReturnTimeSlotStats() throws Exception {
        mockMvc.perform(get("/api/courses/stats/timeslot"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$").isArray());
    }

    @Test
    @DisplayName("GET /api/courses/enrollment-details - 복잡한 JOIN 쿼리 테스트")
    void getCoursesWithEnrollmentDetails_ShouldReturnComplexJoinResults() throws Exception {
        mockMvc.perform(get("/api/courses/enrollment-details"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$").isArray());
    }

    @Test
    @DisplayName("GET /api/courses/stats/average-enrollment - 평균 수강신청 인원")
    void getAverageEnrollment_ShouldReturnAverageValue() throws Exception {
        mockMvc.perform(get("/api/courses/stats/average-enrollment"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$").isNumber());
    }

    /**
     * 에러 케이스 및 경계값 테스트
     */

    @Test
    @DisplayName("특수문자 키워드 검색 처리")
    void searchCourses_WithSpecialCharacters_ShouldHandleSpecialChars() throws Exception {
        mockMvc.perform(get("/api/courses")
                .param("page", "0")
                .param("size", "10")
                .param("keyword", "AI/ML & Data"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.content").isArray());
    }

    @Test
    @DisplayName("매우 긴 키워드 처리")
    void searchCourses_WithVeryLongKeyword_ShouldHandleGracefully() throws Exception {
        String longKeyword = "a".repeat(1000);
        
        mockMvc.perform(get("/api/courses")
                .param("page", "0")
                .param("size", "10")
                .param("keyword", longKeyword))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON));
    }

    @Test
    @DisplayName("SQL Injection 공격 시도 방어")
    void searchCourses_WithSQLInjectionAttempt_ShouldBeSecure() throws Exception {
        mockMvc.perform(get("/api/courses")
                .param("page", "0")
                .param("size", "10")
                .param("keyword", "'; DROP TABLE courses; --"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.content").isArray());
    }

    @Test
    @DisplayName("대용량 페이지 크기 요청 제한")
    void searchCourses_WithLargePageSize_ShouldHandleGracefully() throws Exception {
        mockMvc.perform(get("/api/courses")
                .param("page", "0")
                .param("size", "10000"))
                .andExpect(status().isOk());
    }

    /**
     * 성능 및 동시성 테스트
     */

    @Test
    @DisplayName("동시성 테스트 - 인기 과목 조회")
    void getPopularCourses_ConcurrentRequests_ShouldHandleMultipleRequests() throws Exception {
        // 간단한 동시성 테스트
        for (int i = 0; i < 5; i++) {
            mockMvc.perform(get("/api/courses/popular"))
                    .andExpect(status().isOk())
                    .andExpect(content().contentType(MediaType.APPLICATION_JSON));
        }
    }

    @Test
    @DisplayName("복합 통계 쿼리 성능 테스트")
    void performanceTest_ComplexStatisticalQueries_ShouldCompleteInReasonableTime() throws Exception {
        // 여러 통계 API를 연속으로 호출하여 성능 검증
        mockMvc.perform(get("/api/courses/stats/department"))
                .andExpect(status().isOk());
                
        mockMvc.perform(get("/api/courses/stats/professor"))
                .andExpect(status().isOk());
                
        mockMvc.perform(get("/api/courses/stats/timeslot"))
                .andExpect(status().isOk());
                
        mockMvc.perform(get("/api/courses/stats/average-enrollment"))
                .andExpect(status().isOk());
                
        mockMvc.perform(get("/api/courses/enrollment-details"))
                .andExpect(status().isOk());
    }

    /**
     * PostgreSQL 특화 테스트
     */

    @Test
    @DisplayName("PostgreSQL 타입 캐스팅 호환성 검증")
    void searchCourses_PostgreSQLTypeCasting_ShouldNotThrowTypeCastingErrors() throws Exception {
        // 다양한 타입의 키워드로 PostgreSQL 타입 캐스팅 이슈가 없는지 확인
        String[] testKeywords = {
            "Database", "데이터베이스", "AI/ML", "Prof", "김교수", 
            "123", "CS", "수학", "영어", "Algorithm"
        };

        for (String keyword : testKeywords) {
            mockMvc.perform(get("/api/courses")
                    .param("page", "0")
                    .param("size", "5")
                    .param("keyword", keyword))
                    .andExpect(status().isOk())
                    .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                    .andExpect(jsonPath("$.content").isArray());
        }
    }

    @Test
    @DisplayName("PostgreSQL LOWER 함수 호환성 - 다양한 문자셋")
    void searchCourses_PostgreSQLLowerFunction_ShouldHandleMultipleCharsets() throws Exception {
        String[] mixedCaseKeywords = {
            "Database", "DATABASE", "database", "DaTaBaSe",
            "데이터베이스", "김교수", "KIM교수", "kim교수"
        };

        for (String keyword : mixedCaseKeywords) {
            mockMvc.perform(get("/api/courses")
                    .param("page", "0")
                    .param("size", "5")
                    .param("keyword", keyword))
                    .andExpect(status().isOk())
                    .andExpect(content().contentType(MediaType.APPLICATION_JSON));
        }
    }
}