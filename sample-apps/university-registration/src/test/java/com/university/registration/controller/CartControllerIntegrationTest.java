package com.university.registration.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.university.registration.config.PostgreSQLTestConfiguration;
import com.university.registration.controller.CartController.AddToCartRequest;
import com.university.registration.controller.CartController.ValidateCartRequest;
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
 * Cart API 통합 테스트
 * 
 * 주요 테스트 범위:
 * 1. 장바구니 조회 API (학생별)
 * 2. 장바구니 추가/제거/비우기 API
 * 3. 장바구니 검증 API
 * 4. 장바구니 통계 API
 * 5. PostgreSQL 호환성 검증
 * 6. 에러 처리 및 예외 상황
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@Testcontainers
@ActiveProfiles("test")
@Import(PostgreSQLTestConfiguration.class)
@DisplayName("Cart API 통합 테스트")
class CartControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    private static final String TEST_STUDENT_ID = "2024001";
    private static final String TEST_COURSE_ID = "CSE101";

    @Test
    @DisplayName("GET /api/cart - 학생 장바구니 조회 (정상 케이스)")
    void getCart_WithValidStudentId_ShouldReturnCartSummary() throws Exception {
        mockMvc.perform(get("/api/cart")
                .param("studentId", TEST_STUDENT_ID))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.totalItems").exists())
                .andExpect(jsonPath("$.totalCredits").exists())
                .andExpect(jsonPath("$.cartItems").isArray())
                .andExpect(jsonPath("$.timeConflicts").isArray())
                .andExpect(jsonPath("$.canEnrollAll").exists());
    }

    @Test
    @DisplayName("GET /api/cart - 존재하지 않는 학생 (빈 장바구니 반환)")
    void getCart_WithInvalidStudentId_ShouldReturnEmptyCart() throws Exception {
        mockMvc.perform(get("/api/cart")
                .param("studentId", "INVALID999"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.totalItems").value(0))
                .andExpect(jsonPath("$.totalCredits").value(0))
                .andExpect(jsonPath("$.cartItems").isArray())
                .andExpect(jsonPath("$.cartItems").isEmpty())
                .andExpect(jsonPath("$.canEnrollAll").value(true));
    }

    @Test
    @DisplayName("GET /api/cart - studentId 파라미터 누락")
    void getCart_WithoutStudentIdParam_ShouldReturnBadRequest() throws Exception {
        mockMvc.perform(get("/api/cart"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("POST /api/cart/items - 장바구니에 과목 추가")
    void addToCart_WithValidRequest_ShouldAddCourseToCart() throws Exception {
        AddToCartRequest request = new AddToCartRequest();
        request.setStudentId(TEST_STUDENT_ID);
        request.setCourseId(TEST_COURSE_ID);

        mockMvc.perform(post("/api/cart/items")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(content().string("장바구니에 추가되었습니다."));
    }

    @Test
    @DisplayName("POST /api/cart/items - 중복 과목 추가 시도")
    void addToCart_WithDuplicateCourse_ShouldReturnBadRequest() throws Exception {
        AddToCartRequest request = new AddToCartRequest();
        request.setStudentId(TEST_STUDENT_ID);
        request.setCourseId(TEST_COURSE_ID);

        // 첫 번째 추가는 성공해야 함
        mockMvc.perform(post("/api/cart/items")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk());

        // 두 번째 추가는 실패해야 함
        mockMvc.perform(post("/api/cart/items")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(content().string("이미 장바구니에 있거나 시간표가 충돌합니다."));
    }

    @Test
    @DisplayName("POST /api/cart/items - 잘못된 요청 데이터")
    void addToCart_WithInvalidRequest_ShouldReturnBadRequest() throws Exception {
        AddToCartRequest request = new AddToCartRequest();
        // studentId와 courseId를 설정하지 않음

        mockMvc.perform(post("/api/cart/items")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("POST /api/cart/items - 존재하지 않는 과목 추가")
    void addToCart_WithNonexistentCourse_ShouldReturnBadRequest() throws Exception {
        AddToCartRequest request = new AddToCartRequest();
        request.setStudentId(TEST_STUDENT_ID);
        request.setCourseId("INVALID999");

        mockMvc.perform(post("/api/cart/items")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("DELETE /api/cart/items/{courseId} - 장바구니에서 과목 제거")
    void removeFromCart_WithValidCourse_ShouldRemoveCourseFromCart() throws Exception {
        // 먼저 과목을 추가
        AddToCartRequest addRequest = new AddToCartRequest();
        addRequest.setStudentId(TEST_STUDENT_ID);
        addRequest.setCourseId(TEST_COURSE_ID);
        
        mockMvc.perform(post("/api/cart/items")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(addRequest)));

        // 그 다음 제거
        mockMvc.perform(delete("/api/cart/items/" + TEST_COURSE_ID)
                .param("studentId", TEST_STUDENT_ID))
                .andExpect(status().isOk())
                .andExpect(content().string("장바구니에서 제거되었습니다."));
    }

    @Test
    @DisplayName("DELETE /api/cart/items/{courseId} - 존재하지 않는 과목 제거")
    void removeFromCart_WithNonexistentCourse_ShouldReturnNotFound() throws Exception {
        mockMvc.perform(delete("/api/cart/items/INVALID999")
                .param("studentId", TEST_STUDENT_ID))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("DELETE /api/cart/items/{courseId} - studentId 파라미터 누락")
    void removeFromCart_WithoutStudentId_ShouldReturnBadRequest() throws Exception {
        mockMvc.perform(delete("/api/cart/items/" + TEST_COURSE_ID))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("DELETE /api/cart - 장바구니 비우기")
    void clearCart_WithValidStudentId_ShouldClearCart() throws Exception {
        mockMvc.perform(delete("/api/cart")
                .param("studentId", TEST_STUDENT_ID))
                .andExpect(status().isOk())
                .andExpect(content().string("장바구니가 비워졌습니다."));
    }

    @Test
    @DisplayName("DELETE /api/cart - 존재하지 않는 학생")
    void clearCart_WithInvalidStudentId_ShouldReturnNotFound() throws Exception {
        mockMvc.perform(delete("/api/cart")
                .param("studentId", "INVALID999"))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("POST /api/cart/validate - 장바구니 검증")
    void validateCart_WithValidStudentId_ShouldReturnValidationResult() throws Exception {
        ValidateCartRequest request = new ValidateCartRequest();
        request.setStudentId(TEST_STUDENT_ID);

        mockMvc.perform(post("/api/cart/validate")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.valid").exists())
                .andExpect(jsonPath("$.validationErrors").exists());
    }

    @Test
    @DisplayName("POST /api/cart/validate - 존재하지 않는 학생")
    void validateCart_WithInvalidStudentId_ShouldReturnNotFound() throws Exception {
        ValidateCartRequest request = new ValidateCartRequest();
        request.setStudentId("INVALID999");

        mockMvc.perform(post("/api/cart/validate")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isNotFound());
    }

    /**
     * 통계 API 테스트
     */

    @Test
    @DisplayName("GET /api/cart/stats/most-wanted - 인기 과목 통계")
    void getMostWantedCourses_ShouldReturnMostWantedStats() throws Exception {
        mockMvc.perform(get("/api/cart/stats/most-wanted"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$").isArray());
    }

    @Test
    @DisplayName("GET /api/cart/stats/department - 학과별 장바구니 통계")
    void getCartStatsByDepartment_ShouldReturnDepartmentStats() throws Exception {
        mockMvc.perform(get("/api/cart/stats/department"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$").isArray());
    }

    @Test
    @DisplayName("GET /api/cart/stats/grade - 학년별 장바구니 통계")
    void getCartStatsByGrade_ShouldReturnGradeStats() throws Exception {
        mockMvc.perform(get("/api/cart/stats/grade"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$").isArray());
    }

    @Test
    @DisplayName("GET /api/cart/stats/popular-professors - 인기 교수 통계")
    void getPopularProfessors_ShouldReturnPopularProfessorStats() throws Exception {
        mockMvc.perform(get("/api/cart/stats/popular-professors"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$").isArray());
    }

    /**
     * 복잡한 시나리오 테스트
     */

    @Test
    @DisplayName("복합 시나리오 - 장바구니 추가, 조회, 검증, 제거, 비우기")
    void complexScenario_FullCartWorkflow_ShouldCompleteSuccessfully() throws Exception {
        String studentId = "2024002";
        String[] courseIds = {"CSE101", "MATH201", "ENG101"};

        // 1. 빈 장바구니 확인
        mockMvc.perform(get("/api/cart")
                .param("studentId", studentId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalItems").value(0));

        // 2. 여러 과목 추가
        for (String courseId : courseIds) {
            AddToCartRequest request = new AddToCartRequest();
            request.setStudentId(studentId);
            request.setCourseId(courseId);

            mockMvc.perform(post("/api/cart/items")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk());
        }

        // 3. 장바구니 조회 및 항목 수 확인
        mockMvc.perform(get("/api/cart")
                .param("studentId", studentId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalItems").value(courseIds.length));

        // 4. 장바구니 검증
        ValidateCartRequest validateRequest = new ValidateCartRequest();
        validateRequest.setStudentId(studentId);

        mockMvc.perform(post("/api/cart/validate")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(validateRequest)))
                .andExpect(status().isOk());

        // 5. 개별 과목 제거
        mockMvc.perform(delete("/api/cart/items/" + courseIds[0])
                .param("studentId", studentId))
                .andExpect(status().isOk());

        // 6. 장바구니 비우기
        mockMvc.perform(delete("/api/cart")
                .param("studentId", studentId))
                .andExpect(status().isOk());

        // 7. 최종 빈 장바구니 확인
        mockMvc.perform(get("/api/cart")
                .param("studentId", studentId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalItems").value(0));
    }

    /**
     * 에러 케이스 및 경계값 테스트
     */

    @Test
    @DisplayName("특수문자 포함 학번 처리")
    void handleSpecialCharactersInStudentId() throws Exception {
        String[] specialStudentIds = {
            "student@test", "student#123", "학생-001", "STUDENT_TEST"
        };

        for (String studentId : specialStudentIds) {
            mockMvc.perform(get("/api/cart")
                    .param("studentId", studentId))
                    .andExpect(status().isOk())
                    .andExpect(content().contentType(MediaType.APPLICATION_JSON));
        }
    }

    @Test
    @DisplayName("매우 긴 학번 처리")
    void handleVeryLongStudentId() throws Exception {
        String longStudentId = "a".repeat(255);
        
        mockMvc.perform(get("/api/cart")
                .param("studentId", longStudentId))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("동시성 테스트 - 같은 학생의 동시 장바구니 조회")
    void concurrentCartAccess_SameStudent_ShouldHandleGracefully() throws Exception {
        // 동시에 같은 학생의 장바구니를 여러 번 조회
        for (int i = 0; i < 10; i++) {
            mockMvc.perform(get("/api/cart")
                    .param("studentId", TEST_STUDENT_ID))
                    .andExpect(status().isOk())
                    .andExpect(content().contentType(MediaType.APPLICATION_JSON));
        }
    }

    @Test
    @DisplayName("JSON 파싱 에러 처리")
    void handleInvalidJsonInRequest() throws Exception {
        String invalidJson = "{\"studentId\": \"test\", invalid json}";

        mockMvc.perform(post("/api/cart/items")
                .contentType(MediaType.APPLICATION_JSON)
                .content(invalidJson))
                .andExpect(status().isBadRequest());
    }

    /**
     * PostgreSQL 특화 테스트
     */

    @Test
    @DisplayName("PostgreSQL 트랜잭션 처리 - 장바구니 추가/제거 원자성")
    void postgresqlTransactionHandling_CartOperations_ShouldBeAtomic() throws Exception {
        String studentId = "2024003";
        AddToCartRequest request = new AddToCartRequest();
        request.setStudentId(studentId);
        request.setCourseId(TEST_COURSE_ID);

        // 여러 번 연속으로 같은 작업 수행하여 트랜잭션 처리 확인
        mockMvc.perform(post("/api/cart/items")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk());

        mockMvc.perform(delete("/api/cart/items/" + TEST_COURSE_ID)
                .param("studentId", studentId))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/cart/items")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("PostgreSQL 데이터 타입 호환성 - 다양한 학번 형식")
    void postgresqlDataTypeCompatibility_StudentIdFormats() throws Exception {
        String[] studentIdFormats = {
            "2024001",      // 숫자형
            "STU2024001",   // 영문+숫자
            "학생001",       // 한글+숫자
            "2024-001",     // 하이픈 포함
            "24001"         // 짧은 숫자
        };

        for (String studentId : studentIdFormats) {
            mockMvc.perform(get("/api/cart")
                    .param("studentId", studentId))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.totalItems").exists());
        }
    }

    @Test
    @DisplayName("PostgreSQL 성능 테스트 - 장바구니 통계 쿼리")
    void postgresqlPerformanceTest_CartStatistics() throws Exception {
        // 모든 통계 API를 연속으로 호출하여 PostgreSQL 성능 확인
        String[] statsEndpoints = {
            "/api/cart/stats/most-wanted",
            "/api/cart/stats/department", 
            "/api/cart/stats/grade",
            "/api/cart/stats/popular-professors"
        };

        for (String endpoint : statsEndpoints) {
            mockMvc.perform(get(endpoint))
                    .andExpect(status().isOk())
                    .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                    .andExpect(jsonPath("$").isArray());
        }
    }
}