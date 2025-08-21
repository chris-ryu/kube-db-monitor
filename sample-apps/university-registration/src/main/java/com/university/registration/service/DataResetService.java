package com.university.registration.service;

import com.university.registration.entity.*;
import com.university.registration.repository.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;

/**
 * 데이터 리셋 및 시나리오 생성 서비스
 * 테스트/데모 환경에서 일관된 데이터 관리를 위한 서비스
 */
@Service
public class DataResetService {

    private static final Logger logger = LoggerFactory.getLogger(DataResetService.class);

    @Autowired private DepartmentRepository departmentRepository;
    @Autowired private StudentRepository studentRepository;
    @Autowired private CourseRepository courseRepository;
    @Autowired private SemesterRepository semesterRepository;
    @Autowired private CartRepository cartRepository;
    @Autowired private EnrollmentRepository enrollmentRepository;

    private final Random random = new Random();

    @Transactional
    public void resetAllData(String mode) {
        logger.info("🔄 전체 데이터 리셋 시작 - Mode: {}", mode);
        
        // 1. 모든 데이터 삭제
        clearAllData();
        
        // 2. 모드별 데이터 재생성
        switch (mode.toLowerCase()) {
            case "test" -> createTestData();
            case "demo" -> createDemoData();
            case "dev" -> createDevData();
            case "clean" -> createMinimalData();
            default -> createDemoData();
        }
        
        logger.info("✅ 데이터 리셋 완료 - Mode: {}", mode);
    }

    @Transactional
    public void createScenarioData(String scenarioName) {
        switch (scenarioName.toLowerCase()) {
            case "deadlock" -> createDeadlockScenario();
            case "heavy-load" -> createHeavyLoadScenario();
            case "edge-case" -> createEdgeCaseScenario();
            case "enrollment-rush" -> createEnrollmentRushScenario();
            default -> throw new IllegalArgumentException("Unknown scenario: " + scenarioName);
        }
    }

    public Map<String, Object> getDataStatus() {
        Map<String, Object> status = new HashMap<>();
        status.put("timestamp", LocalDateTime.now());
        status.put("counts", Map.of(
            "semesters", semesterRepository.count(),
            "departments", departmentRepository.count(),
            "students", studentRepository.count(),
            "courses", courseRepository.count(),
            "enrollments", enrollmentRepository.count(),
            "carts", cartRepository.count()
        ));
        
        // 현재 학기 정보
        Optional<Semester> currentSemester = semesterRepository.findCurrentSemester();
        if (currentSemester.isPresent()) {
            Semester semester = currentSemester.get();
            status.put("currentSemester", Map.of(
                "year", semester.getYear(),
                "season", semester.getSeason(),
                "registrationPeriod", Map.of(
                    "start", semester.getRegistrationStart(),
                    "end", semester.getRegistrationEnd()
                )
            ));
        }
        
        return status;
    }

    private void clearAllData() {
        logger.info("🗑️ 모든 데이터 삭제 중...");
        cartRepository.deleteAll();
        enrollmentRepository.deleteAll();
        courseRepository.deleteAll();
        studentRepository.deleteAll();
        departmentRepository.deleteAll();
        semesterRepository.deleteAll();
        logger.info("✅ 모든 데이터 삭제 완료");
    }

    // === 데이터 생성 메서드들 ===

    private void createTestData() {
        logger.info("📝 테스트 데이터 생성");
        
        // 현재 학기
        Semester semester = createSemester(2024, Semester.Season.SPRING, true);
        
        // 테스트 학과
        Department dept = createDepartment("컴퓨터과학과", "공과대학");
        
        // 테스트 학생
        Student student = createStudent("2024001", "테스트학생", dept, 3);
        
        // 테스트 과목들
        for (int i = 1; i <= 5; i++) {
            createCourse("CS10" + i, "테스트과목" + i, dept, semester, "김교수", 30);
        }
        
        logger.info("✅ 테스트 데이터 생성 완료");
    }

    private void createDemoData() {
        logger.info("🎬 데모 데이터 생성");
        
        // 학기들
        Semester currentSemester = createSemester(2024, Semester.Season.SPRING, true);
        
        // 학과들
        Department[] departments = {
            createDepartment("컴퓨터과학과", "공과대학"),
            createDepartment("전자공학과", "공과대학"), 
            createDepartment("경영학과", "경영대학")
        };
        
        // 학생들 (30명)
        List<Student> students = new ArrayList<>();
        for (int i = 1; i <= 30; i++) {
            Department dept = departments[i % departments.length];
            students.add(createStudent(
                String.format("202400%02d", i),
                "데모학생" + i, 
                dept,
                1 + (i % 4)
            ));
        }
        
        // 과목들 (각 학과별 8개)
        List<Course> courses = new ArrayList<>();
        for (Department dept : departments) {
            for (int i = 1; i <= 8; i++) {
                Course course = createCourse(
                    dept.getCollege() + String.format("%03d", i),
                    dept.getDepartmentName().substring(0, 2) + "과목" + i,
                    dept,
                    currentSemester,
                    "교수" + (i % 3 + 1),
                    20 + i * 2
                );
                courses.add(course);
            }
        }
        
        // 수강신청 생성
        createRealisticEnrollments(students, courses, currentSemester);
        
        // 장바구니 데이터
        createCartData(students.subList(0, 10), courses);
        
        logger.info("✅ 데모 데이터 생성 완료");
    }

    private void createDevData() {
        logger.info("🔧 개발 데이터 생성");
        createDemoData(); // 데모 데이터 포함
        
        // 개발용 추가 데이터
        createEdgeCaseData();
        
        logger.info("✅ 개발 데이터 생성 완료");
    }

    private void createMinimalData() {
        logger.info("🏢 최소 데이터 생성");
        
        // 현재 학기만
        Semester semester = createSemester(2024, Semester.Season.SPRING, true);
        
        // 기본 학과
        Department dept = createDepartment("컴퓨터과학과", "공과대학");
        
        // 관리자 계정
        createStudent("admin", "관리자", dept, 4);
        
        logger.info("✅ 최소 데이터 생성 완료");
    }

    // === 시나리오별 데이터 생성 ===

    private void createDeadlockScenario() {
        logger.info("💀 데드락 시나리오 데이터 생성");
        
        if (semesterRepository.count() == 0) {
            createMinimalData();
        }
        
        Semester semester = semesterRepository.findCurrentSemester().orElseThrow();
        Department dept = departmentRepository.findAll().get(0);
        
        // 데드락 시뮬레이션용 학생들
        Student student1 = createStudent("DEADLOCK1", "데드락학생1", dept, 3);
        Student student2 = createStudent("DEADLOCK2", "데드락학생2", dept, 3);
        
        // 경합이 일어날 과목들
        Course popularCourse = createCourse("POPULAR01", "인기과목", dept, semester, "인기교수", 1); // 정원 1명
        Course limitedCourse = createCourse("LIMITED01", "제한과목", dept, semester, "제한교수", 2); // 정원 2명
        
        logger.info("💀 데드락 시나리오 데이터 생성 완료");
    }

    private void createHeavyLoadScenario() {
        logger.info("🔥 고부하 시나리오 데이터 생성");
        
        if (semesterRepository.count() == 0) {
            createMinimalData();
        }
        
        // 대량의 동시 접속을 시뮬레이션할 데이터 생성
        // 실제 구현 시 대량 데이터 생성 로직 추가
        
        logger.info("🔥 고부하 시나리오 데이터 생성 완료");
    }

    private void createEdgeCaseScenario() {
        logger.info("🧪 엣지케이스 시나리오 데이터 생성");
        createEdgeCaseData();
        logger.info("🧪 엣지케이스 시나리오 데이터 생성 완료");
    }

    private void createEnrollmentRushScenario() {
        logger.info("🏃 수강신청 러시 시나리오 데이터 생성");
        
        if (semesterRepository.count() == 0) {
            createMinimalData();
        }
        
        // 수강신청 러시 상황을 위한 데이터 생성
        // (많은 학생, 적은 정원의 인기 과목들)
        
        logger.info("🏃 수강신청 러시 시나리오 데이터 생성 완료");
    }

    // === 헬퍼 메서드들 ===

    private Semester createSemester(int year, Semester.Season season, boolean isCurrent) {
        Semester semester = new Semester();
        semester.setYear(year);
        semester.setSeason(season);
        semester.setRegistrationStart(LocalDateTime.now().minusDays(30));
        semester.setRegistrationEnd(LocalDateTime.now().plusDays(30));
        semester.setIsCurrent(isCurrent);
        return semesterRepository.save(semester);
    }

    private Department createDepartment(String name, String code) {
        Department dept = new Department();
        dept.setDepartmentName(name);
        dept.setCollege(code);
        return departmentRepository.save(dept);
    }

    private Student createStudent(String studentId, String name, Department dept, int grade) {
        Student student = new Student();
        student.setStudentId(studentId);
        student.setName(name);
        student.setEmail(studentId + "@university.edu");
        student.setGrade(grade);
        student.setDepartment(dept);
        student.setPassword("demo123");
        student.setTotalCredits(grade * 12);
        return studentRepository.save(student);
    }

    private Course createCourse(String courseId, String courseName, Department dept, 
                              Semester semester, String professor, int capacity) {
        Course course = new Course();
        course.setCourseId(courseId);
        course.setCourseName(courseName);
        course.setDepartment(dept);
        course.setCredits(3);
        course.setProfessor(professor);
        course.setCapacity(capacity);
        course.setEnrolledCount(0);
        course.setSemester(semester);
        course.setClassroom("강의실" + random.nextInt(100));
        course.setIsActive(true);
        return courseRepository.save(course);
    }

    private void createRealisticEnrollments(List<Student> students, List<Course> courses, Semester semester) {
        for (Student student : students) {
            int courseCount = 2 + random.nextInt(3); // 2-4과목
            Collections.shuffle(courses);
            
            for (int i = 0; i < Math.min(courseCount, courses.size()); i++) {
                Course course = courses.get(i);
                if (course.getEnrolledCount() < course.getCapacity()) {
                    createEnrollment(student, course, semester);
                    course.setEnrolledCount(course.getEnrolledCount() + 1);
                    courseRepository.save(course);
                }
            }
        }
    }

    private Enrollment createEnrollment(Student student, Course course, Semester semester) {
        Enrollment enrollment = new Enrollment();
        enrollment.setStudent(student);
        enrollment.setCourse(course);
        enrollment.setSemester(semester);
        enrollment.setStatus(Enrollment.Status.ENROLLED);
        return enrollmentRepository.save(enrollment);
    }

    private void createCartData(List<Student> students, List<Course> courses) {
        for (Student student : students) {
            if (random.nextBoolean() && !courses.isEmpty()) {
                Course course = courses.get(random.nextInt(courses.size()));
                Cart cart = new Cart();
                cart.setStudent(student);
                cart.setCourse(course);
                cartRepository.save(cart);
            }
        }
    }

    private void createEdgeCaseData() {
        // 엣지 케이스 데이터 생성
        if (departmentRepository.count() == 0) {
            createDepartment("테스트학과", "테스트대학");
        }
        
        Department dept = departmentRepository.findAll().get(0);
        
        // 특수 문자가 포함된 데이터
        createStudent("EDGE001", "특수문자!@#학생", dept, 1);
        
        // 매우 긴 이름
        createStudent("EDGE002", "아주아주아주아주긴이름을가진학생", dept, 2);
        
        // 빈 값에 가까운 데이터 등
        logger.info("엣지케이스 데이터 생성 완료");
    }
}