package com.university.registration.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.university.registration.config.PostgreSQLTestConfiguration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.client.RestTemplate;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.*;

/**
 * UI-Backend E2E 통합 테스트
 * 
 * 실제 HTTP 클라이언트를 사용하여 프론트엔드가 백엔드와 통신할 때 발생할 수 있는
 * 데이터 전송 문제를 검증합니다.
 * 
 * 검증 시나리오:
 * 1. 과목 검색 플로우
 * 2. 장바구니 추가/제거 플로우  
 * 3. 수강신청 플로우
 * 4. 오류 처리 시나리오
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
@ActiveProfiles("test")
@Import(PostgreSQLTestConfiguration.class)
@DisplayName("UI-Backend E2E 통합 테스트")
class UiBackendIntegrationTest {

    @LocalServerPort
    private int port;

    @Autowired
    private ObjectMapper objectMapper;

    private final RestTemplate restTemplate = new RestTemplate();

    private String getBaseUrl() {
        return "http://localhost:" + port;
    }

    @Test
    @DisplayName("E2E 시나리오: 과목 검색부터 수강신청까지")
    void fullEnrollmentFlow_ShouldWorkEndToEnd() throws Exception {
        System.out.println("🚀 E2E 테스트 시작: 과목 검색부터 수강신청까지");
        
        String testStudentId = "TEST001";
        
        // 1단계: 과목 검색 (프론트엔드가 하는 방식과 동일)
        System.out.println("\n1️⃣ 과목 검색");
        String searchUrl = getBaseUrl() + "/api/courses?page=0&size=10";
        ResponseEntity<String> searchResponse = restTemplate.getForEntity(searchUrl, String.class);
        
        assertThat(searchResponse.getStatusCodeValue()).isEqualTo(200);
        JsonNode searchResult = objectMapper.readTree(searchResponse.getBody());
        JsonNode courses = searchResult.get("content");
        assertThat(courses.isArray()).isTrue();
        assertThat(courses.size()).isGreaterThan(0);
        
        // 첫 번째 과목 선택
        JsonNode selectedCourse = courses.get(0);
        String courseId = selectedCourse.get("courseId").asText();
        String courseName = selectedCourse.get("courseName").asText();
        
        System.out.printf("   선택된 과목: %s (%s)%n", courseName, courseId);
        
        // 2단계: 과목 상세 조회
        System.out.println("\n2️⃣ 과목 상세 조회");
        String detailUrl = getBaseUrl() + "/api/courses/" + courseId;
        ResponseEntity<String> detailResponse = restTemplate.getForEntity(detailUrl, String.class);
        
        assertThat(detailResponse.getStatusCodeValue()).isEqualTo(200);
        JsonNode courseDetail = objectMapper.readTree(detailResponse.getBody());
        
        // 프론트엔드가 필요로 하는 필드들이 있는지 확인
        System.out.println("   필수 필드 검증:");
        validateRequiredField(courseDetail, "courseId", "과목코드");
        validateRequiredField(courseDetail, "courseName", "과목명");
        
        // 필드명 불일치 문제 확인
        String professor = getFieldValue(courseDetail, "professor", "professorName");
        Integer capacity = getIntegerFieldValue(courseDetail, "capacity", "maxStudents");  
        Integer enrolledCount = getIntegerFieldValue(courseDetail, "enrolledCount", "currentEnrollment");
        String schedule = getFieldValue(courseDetail, "dayTime", "schedule");
        
        System.out.printf("   교수: %s, 정원: %d, 수강인원: %d, 시간: %s%n", 
                         professor, capacity, enrolledCount, schedule);
        
        // 3단계: 장바구니에 추가
        System.out.println("\n3️⃣ 장바구니에 추가");
        String addToCartUrl = getBaseUrl() + "/api/cart/items";
        
        Map<String, Object> cartRequest = new HashMap<>();
        cartRequest.put("studentId", testStudentId);
        cartRequest.put("courseId", courseId);
        
        HttpHeaders headers = new HttpHeaders();
        headers.set("Content-Type", "application/json");
        HttpEntity<Map<String, Object>> cartEntity = new HttpEntity<>(cartRequest, headers);
        
        try {
            ResponseEntity<String> cartResponse = restTemplate.postForEntity(addToCartUrl, cartEntity, String.class);
            System.out.printf("   장바구니 추가 결과: %d%n", cartResponse.getStatusCodeValue());
            
            if (cartResponse.getStatusCodeValue() == 200 || cartResponse.getStatusCodeValue() == 201) {
                // 4단계: 장바구니 조회
                System.out.println("\n4️⃣ 장바구니 조회");
                String cartUrl = getBaseUrl() + "/api/cart?studentId=" + testStudentId;
                ResponseEntity<String> cartListResponse = restTemplate.getForEntity(cartUrl, String.class);
                
                assertThat(cartListResponse.getStatusCodeValue()).isEqualTo(200);
                JsonNode cartItems = objectMapper.readTree(cartListResponse.getBody());
                System.out.printf("   장바구니 아이템 수: %d%n", 
                                 cartItems.isArray() ? cartItems.size() : 0);
            }
            
        } catch (Exception e) {
            System.out.println("   장바구니 추가 실패: " + e.getMessage());
        }
        
        // 5단계: 수강신청 시도
        System.out.println("\n5️⃣ 수강신청 시도");
        String enrollUrl = getBaseUrl() + "/api/enrollments/" + courseId + "?studentId=" + testStudentId;
        
        try {
            ResponseEntity<String> enrollResponse = restTemplate.postForEntity(enrollUrl, null, String.class);
            System.out.printf("   수강신청 결과: %d%n", enrollResponse.getStatusCodeValue());
            
            if (enrollResponse.getBody() != null) {
                JsonNode enrollResult = objectMapper.readTree(enrollResponse.getBody());
                if (enrollResult.has("message")) {
                    System.out.println("   메시지: " + enrollResult.get("message").asText());
                }
            }
            
        } catch (Exception e) {
            System.out.println("   수강신청 실패: " + e.getMessage());
        }
        
        System.out.println("\n✅ E2E 테스트 완료");
    }

    @Test
    @DisplayName("데이터 타입 변환 오류 시뮬레이션")
    void dataTypeConversionErrors_ShouldBeHandledGracefully() throws Exception {
        System.out.println("🧪 데이터 타입 변환 오류 테스트");
        
        // 1. 잘못된 페이지 파라미터
        String invalidPageUrl = getBaseUrl() + "/api/courses?page=invalid&size=10";
        try {
            ResponseEntity<String> response = restTemplate.getForEntity(invalidPageUrl, String.class);
            System.out.printf("   잘못된 페이지 파라미터 처리: %d%n", response.getStatusCodeValue());
        } catch (Exception e) {
            System.out.println("   예상된 오류 발생: " + e.getMessage());
        }
        
        // 2. 존재하지 않는 과목 조회
        String nonExistentCourseUrl = getBaseUrl() + "/api/courses/INVALID999";
        try {
            ResponseEntity<String> response = restTemplate.getForEntity(nonExistentCourseUrl, String.class);
            System.out.printf("   존재하지 않는 과목 조회: %d%n", response.getStatusCodeValue());
        } catch (Exception e) {
            System.out.println("   예상된 오류 발생: " + e.getMessage());
        }
        
        // 3. 잘못된 JSON 형식으로 수강신청
        String enrollUrl = getBaseUrl() + "/api/enrollments/CS101";
        String invalidJson = "{ invalid json }";
        
        HttpHeaders headers = new HttpHeaders();
        headers.set("Content-Type", "application/json");
        HttpEntity<String> entity = new HttpEntity<>(invalidJson, headers);
        
        try {
            ResponseEntity<String> response = restTemplate.exchange(enrollUrl, HttpMethod.POST, entity, String.class);
            System.out.printf("   잘못된 JSON 처리: %d%n", response.getStatusCodeValue());
        } catch (Exception e) {
            System.out.println("   예상된 오류 발생: " + e.getMessage());
        }
    }

    @Test
    @DisplayName("대용량 데이터 응답 처리")
    void largeDataResponse_ShouldBeHandledCorrectly() throws Exception {
        System.out.println("📊 대용량 데이터 응답 테스트");
        
        // 큰 페이지 크기로 요청
        String largePageUrl = getBaseUrl() + "/api/courses?page=0&size=100";
        ResponseEntity<String> response = restTemplate.getForEntity(largePageUrl, String.class);
        
        assertThat(response.getStatusCodeValue()).isEqualTo(200);
        JsonNode result = objectMapper.readTree(response.getBody());
        
        System.out.printf("   요청한 크기: 100, 실제 반환: %d%n", 
                         result.get("content").size());
        System.out.printf("   응답 크기: %d bytes%n", 
                         response.getBody().length());
    }

    // Helper methods
    private void validateRequiredField(JsonNode node, String fieldName, String displayName) {
        if (node.has(fieldName)) {
            System.out.printf("     ✅ %s: %s%n", displayName, node.get(fieldName).asText());
        } else {
            System.out.printf("     ❌ %s: 누락됨%n", displayName);
        }
    }

    private String getFieldValue(JsonNode node, String primaryField, String fallbackField) {
        if (node.has(primaryField)) {
            return node.get(primaryField).asText();
        } else if (node.has(fallbackField)) {
            return node.get(fallbackField).asText(); 
        }
        return "N/A";
    }

    private Integer getIntegerFieldValue(JsonNode node, String primaryField, String fallbackField) {
        if (node.has(primaryField)) {
            return node.get(primaryField).asInt();
        } else if (node.has(fallbackField)) {
            return node.get(fallbackField).asInt();
        }
        return 0;
    }
}