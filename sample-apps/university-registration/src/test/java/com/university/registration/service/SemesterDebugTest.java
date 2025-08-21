package com.university.registration.service;

import com.university.registration.config.PostgreSQLTestConfiguration;
import com.university.registration.entity.Semester;
import com.university.registration.repository.SemesterRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;

/**
 * 현재 학기 조회 디버깅 테스트
 */
@SpringBootTest
@Testcontainers
@ActiveProfiles("test")
@Import(PostgreSQLTestConfiguration.class)
@DisplayName("현재 학기 조회 디버깅 테스트")
class SemesterDebugTest {

    @Autowired
    private SemesterRepository semesterRepository;

    @Test
    @DisplayName("모든 학기 데이터 조회")
    void getAllSemesters() {
        List<Semester> semesters = semesterRepository.findAll();
        System.out.println("📊 전체 학기 수: " + semesters.size());
        
        for (Semester semester : semesters) {
            System.out.println("🎓 학기 정보:");
            System.out.println("  - ID: " + semester.getId());
            System.out.println("  - Year: " + semester.getYear());
            System.out.println("  - Season: " + semester.getSeason());
            System.out.println("  - IsCurrent: " + semester.getIsCurrent());
            System.out.println("  - Registration Start: " + semester.getRegistrationStart());
            System.out.println("  - Registration End: " + semester.getRegistrationEnd());
            System.out.println();
        }
        
        assertThat(semesters).isNotEmpty();
    }

    @Test
    @DisplayName("현재 학기 조회 - findCurrentSemester")
    void findCurrentSemester() {
        Optional<Semester> currentSemester = semesterRepository.findCurrentSemester();
        
        System.out.println("🔍 findCurrentSemester() 결과: " + 
            (currentSemester.isPresent() ? "발견됨" : "없음"));
        
        if (currentSemester.isPresent()) {
            Semester semester = currentSemester.get();
            System.out.println("  - Year: " + semester.getYear());
            System.out.println("  - Season: " + semester.getSeason());
            System.out.println("  - IsCurrent: " + semester.getIsCurrent());
        }
        
        assertThat(currentSemester).isPresent();
    }

    @Test
    @DisplayName("현재 학기 조회 - findByIsCurrent(true)")
    void findByIsCurrent() {
        Optional<Semester> currentSemester = semesterRepository.findByIsCurrent(true);
        
        System.out.println("🔍 findByIsCurrent(true) 결과: " + 
            (currentSemester.isPresent() ? "발견됨" : "없음"));
            
        if (currentSemester.isPresent()) {
            Semester semester = currentSemester.get();
            System.out.println("  - Year: " + semester.getYear());
            System.out.println("  - Season: " + semester.getSeason());
            System.out.println("  - IsCurrent: " + semester.getIsCurrent());
        }
        
        assertThat(currentSemester).isPresent();
    }
}