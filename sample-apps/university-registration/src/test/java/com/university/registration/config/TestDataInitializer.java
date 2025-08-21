package com.university.registration.config;

import com.university.registration.entity.*;
import com.university.registration.repository.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestComponent;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * 테스트용 기본 데이터 초기화
 * 최소한의 데이터만 생성하여 테스트가 정상적으로 실행되도록 지원
 */
@TestComponent
public class TestDataInitializer {

    @Autowired private DepartmentRepository departmentRepository;
    @Autowired private StudentRepository studentRepository;
    @Autowired private CourseRepository courseRepository;
    @Autowired private SemesterRepository semesterRepository;

    @Transactional
    public void initializeTestData() {
        // 이미 데이터가 있으면 스킵
        if (semesterRepository.count() > 0) {
            return;
        }

        // 1. 현재 학기 생성
        Semester currentSemester = new Semester();
        currentSemester.setYear(2024);
        currentSemester.setSeason(Semester.Season.SPRING);
        currentSemester.setRegistrationStart(LocalDateTime.now().minusDays(30));
        currentSemester.setRegistrationEnd(LocalDateTime.now().plusDays(30));
        currentSemester.setIsCurrent(true);
        semesterRepository.save(currentSemester);

        // 2. 테스트용 학과 생성
        Department testDept = new Department();
        testDept.setDepartmentName("컴퓨터과학과");
        testDept.setCollege("공과대학");
        departmentRepository.save(testDept);

        // 3. 테스트용 학생 생성
        Student testStudent = new Student();
        testStudent.setStudentId("2024001");
        testStudent.setName("테스트학생");
        testStudent.setEmail("test@university.edu");
        testStudent.setGrade(3);
        testStudent.setDepartment(testDept);
        testStudent.setPassword("password123");
        testStudent.setTotalCredits(45);
        studentRepository.save(testStudent);

        // 4. 테스트용 과목 생성
        Course testCourse = new Course();
        testCourse.setCourseId("CS101");
        testCourse.setCourseName("데이터구조");
        testCourse.setDepartment(testDept);
        testCourse.setCredits(3);
        testCourse.setProfessor("김교수");
        testCourse.setCapacity(30);
        testCourse.setEnrolledCount(0);
        testCourse.setSemester(currentSemester);
        testCourse.setClassroom("컴퓨터실1");
        testCourse.setIsActive(true);
        courseRepository.save(testCourse);

        // 5. 추가 테스트 과목들
        for (int i = 2; i <= 5; i++) {
            Course course = new Course();
            course.setCourseId("CS10" + i);
            course.setCourseName("테스트과목" + i);
            course.setDepartment(testDept);
            course.setCredits(3);
            course.setProfessor("이교수");
            course.setCapacity(25 + (i * 5));
            course.setEnrolledCount(0);
            course.setSemester(currentSemester);
            course.setClassroom("강의실" + i);
            course.setIsActive(true);
            courseRepository.save(course);
        }
    }

    @Transactional
    public void clearTestData() {
        courseRepository.deleteAll();
        studentRepository.deleteAll();
        departmentRepository.deleteAll();
        semesterRepository.deleteAll();
    }
}