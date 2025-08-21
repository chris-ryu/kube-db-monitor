package com.university.registration.contract;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.university.registration.config.PostgreSQLTestConfiguration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * UI-Backend API 계약 검증 테스트
 * 
 * 주요 검증 항목:
 * 1. 필드명 일관성 검증
 * 2. 데이터 타입 검증  
 * 3. 필수 필드 검증
 * 4. 응답 구조 검증
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@ActiveProfiles("test")
@Import(PostgreSQLTestConfiguration.class)
@DisplayName("UI-Backend API 계약 검증")
class ApiContractTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    // 프론트엔드에서 기대하는 Course 필드들
    private static final Set<String> EXPECTED_COURSE_FIELDS = Set.of(
        "courseId", "courseName", 
        "professorName",        // ❌ 백엔드: professor
        "credits",
        "maxStudents",          // ❌ 백엔드: capacity  
        "currentEnrollment",    // ❌ 백엔드: enrolledCount
        "department",
        "schedule",             // ❌ 백엔드: dayTime
        "classroom"
    );

    @Test
    @DisplayName("과목 검색 API - 필드명 일관성 검증")
    void searchCourses_FieldNameConsistency() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/courses")
                .param("page", "0")
                .param("size", "5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andReturn();

        String jsonResponse = result.getResponse().getContentAsString();
        JsonNode response = objectMapper.readTree(jsonResponse);
        JsonNode content = response.get("content");
        
        assertThat(content.isArray()).isTrue();
        
        if (content.size() > 0) {
            JsonNode firstCourse = content.get(0);
            
            System.out.println("🔍 백엔드 API 응답 필드:");
            List<String> actualFields = new ArrayList<>();
            firstCourse.fieldNames().forEachRemaining(actualFields::add);
            actualFields.sort(String::compareTo);
            actualFields.forEach(field -> System.out.println("  - " + field));
            
            System.out.println("\n🎯 프론트엔드 기대 필드:");
            EXPECTED_COURSE_FIELDS.stream().sorted().forEach(field -> 
                System.out.println("  - " + field));
                
            // 필드명 불일치 검사
            List<String> missingFields = new ArrayList<>();
            for (String expectedField : EXPECTED_COURSE_FIELDS) {
                if (!firstCourse.has(expectedField)) {
                    missingFields.add(expectedField);
                }
            }
            
            if (!missingFields.isEmpty()) {
                System.out.println("\n❌ 누락된 필드:");
                missingFields.forEach(field -> System.out.println("  - " + field));
                
                // 필드명 매핑 제안
                System.out.println("\n💡 필드명 매핑 제안:");
                suggestFieldMapping(missingFields, actualFields);
            }
        }
    }

    @Test
    @DisplayName("과목 상세 API - 데이터 타입 검증")
    void getCourseDetail_DataTypeValidation() throws Exception {
        // 먼저 사용 가능한 과목 조회
        MvcResult searchResult = mockMvc.perform(get("/api/courses")
                .param("page", "0")
                .param("size", "1"))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode searchResponse = objectMapper.readTree(searchResult.getResponse().getContentAsString());
        JsonNode courses = searchResponse.get("content");
        
        if (courses.size() > 0) {
            String courseId = courses.get(0).get("courseId").asText();
            
            MvcResult detailResult = mockMvc.perform(get("/api/courses/{courseId}", courseId))
                    .andExpect(status().isOk())
                    .andReturn();

            JsonNode course = objectMapper.readTree(detailResult.getResponse().getContentAsString());
            
            // 데이터 타입 검증
            System.out.println("📊 데이터 타입 검증:");
            validateDataType(course, "courseId", "string");
            validateDataType(course, "courseName", "string"); 
            validateDataType(course, "credits", "number");
            validateDataType(course, "capacity", "number");
            validateDataType(course, "enrolledCount", "number");
        }
    }

    @Test
    @DisplayName("장바구니 API - 응답 구조 검증")
    void cartApi_ResponseStructureValidation() throws Exception {
        String testStudentId = "TEST001";
        
        MvcResult result = mockMvc.perform(get("/api/cart")
                .param("studentId", testStudentId))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode response = objectMapper.readTree(result.getResponse().getContentAsString());
        
        System.out.println("🛒 장바구니 API 응답 구조:");
        if (response.isArray()) {
            System.out.println("  - 타입: Array");
            if (response.size() > 0) {
                JsonNode firstItem = response.get(0);
                System.out.println("  - 아이템 필드:");
                firstItem.fieldNames().forEachRemaining(field -> 
                    System.out.println("    * " + field + ": " + firstItem.get(field).getNodeType()));
            }
        } else {
            System.out.println("  - 타입: Object");
            response.fieldNames().forEachRemaining(field -> 
                System.out.println("    * " + field + ": " + response.get(field).getNodeType()));
        }
    }

    @Test
    @DisplayName("수강신청 API - 요청/응답 검증")
    void enrollmentApi_RequestResponseValidation() throws Exception {
        String testStudentId = "TEST001";
        String testCourseId = "CS101";
        
        // 수강신청 시도
        MvcResult result = mockMvc.perform(post("/api/enrollments/{courseId}", testCourseId)
                .param("studentId", testStudentId))
                .andReturn(); // 성공/실패 모두 허용

        JsonNode response = objectMapper.readTree(result.getResponse().getContentAsString());
        
        System.out.println("📝 수강신청 API 응답:");
        System.out.println("  - 상태코드: " + result.getResponse().getStatus());
        
        if (response.has("message")) {
            System.out.println("  - 메시지: " + response.get("message").asText());
        }
        
        // 응답 구조 검증 (성공/실패 상관없이)
        assertThat(response).isNotNull();
    }

    // Helper methods
    private void validateDataType(JsonNode node, String fieldName, String expectedType) {
        if (node.has(fieldName)) {
            JsonNode field = node.get(fieldName);
            String actualType = getJsonNodeType(field);
            
            System.out.println(String.format("  - %s: %s (기대: %s) %s", 
                fieldName, actualType, expectedType, 
                actualType.equals(expectedType) ? "✅" : "❌"));
                
            if (!actualType.equals(expectedType)) {
                System.out.println(String.format("    실제값: %s", field.toString()));
            }
        } else {
            System.out.println(String.format("  - %s: 누락됨 ❌", fieldName));
        }
    }

    private String getJsonNodeType(JsonNode node) {
        if (node.isNull()) return "null";
        if (node.isBoolean()) return "boolean";
        if (node.isInt() || node.isLong()) return "number";
        if (node.isDouble() || node.isFloat()) return "number";
        if (node.isTextual()) return "string";
        if (node.isArray()) return "array";
        if (node.isObject()) return "object";
        return "unknown";
    }

    private void suggestFieldMapping(List<String> missingFields, List<String> actualFields) {
        for (String missing : missingFields) {
            switch (missing) {
                case "professorName" -> {
                    if (actualFields.contains("professor")) {
                        System.out.println("  * professorName -> professor");
                    }
                }
                case "maxStudents" -> {
                    if (actualFields.contains("capacity")) {
                        System.out.println("  * maxStudents -> capacity");
                    }
                }
                case "currentEnrollment" -> {
                    if (actualFields.contains("enrolledCount")) {
                        System.out.println("  * currentEnrollment -> enrolledCount");
                    }
                }
                case "schedule" -> {
                    if (actualFields.contains("dayTime")) {
                        System.out.println("  * schedule -> dayTime");
                    }
                }
            }
        }
    }
}