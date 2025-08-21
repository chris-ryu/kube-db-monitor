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

import static org.assertj.core.api.Assertions.*;

/**
 * 간단한 CourseService 테스트
 */
@SpringBootTest
@Testcontainers
@ActiveProfiles("test")
@Import(PostgreSQLTestConfiguration.class)
@DisplayName("간단한 CourseService 테스트")
class SimpleCourseServiceTest {

    @Autowired
    private CourseService courseService;

    @Test
    @DisplayName("기본 과목 검색 - 데이터 존재 여부 확인")
    void searchCourses_Basic_ShouldNotBeEmpty() {
        try {
            Page<CourseDTO> result = courseService.searchCourses(null, null, 0, 10);
            
            System.out.println("✅ 검색 성공:");
            System.out.println("  - 총 과목 수: " + result.getTotalElements());
            System.out.println("  - 현재 페이지 과목 수: " + result.getContent().size());
            
            result.getContent().forEach(course -> {
                System.out.println("  - " + course.getCourseId() + ": " + course.getCourseName());
            });
            
            assertThat(result).isNotNull();
            assertThat(result.getContent()).isNotEmpty();
            
        } catch (Exception e) {
            System.out.println("❌ 검색 실패:");
            System.out.println("  - 오류: " + e.getMessage());
            e.printStackTrace();
            throw e;
        }
    }
}