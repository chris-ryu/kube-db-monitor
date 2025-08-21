package com.university.registration.config;

import com.university.registration.entity.*;
import com.university.registration.repository.*;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;

/**
 * 통합 데이터 초기화 시스템
 * 
 * 환경별 데이터 초기화 전략:
 * - test: 최소한의 기본 데이터 (빠른 테스트)
 * - demo: 현실적인 데모 데이터 (시연용)
 * - dev: 개발용 다양한 시나리오 데이터
 * - prod: 초기 기본 데이터만 (실제 서비스용)
 */
@Component
@Profile({"test", "demo", "dev", "local"})
public class UniversalDataInitializer {

    private static final Logger logger = LoggerFactory.getLogger(UniversalDataInitializer.class);

    @Value("${app.registration.data-init-mode:basic}")
    private String dataInitMode;

    @Value("${app.registration.data-reset-enabled:false}")
    private boolean resetEnabled;

    @Value("${spring.profiles.active:default}")
    private String activeProfile;

    @Autowired private DepartmentRepository departmentRepository;
    @Autowired private StudentRepository studentRepository;
    @Autowired private CourseRepository courseRepository;
    @Autowired private SemesterRepository semesterRepository;
    @Autowired private CartRepository cartRepository;
    @Autowired private EnrollmentRepository enrollmentRepository;

    private final Random random = new Random();

    @PostConstruct
    @Transactional
    public void initializeData() {
        logger.info("🚀 Universal Data Initializer 시작 - Profile: {}, Mode: {}", activeProfile, dataInitMode);
        
        // 데이터 존재 확인
        boolean hasData = checkExistingData();
        
        if (hasData && !resetEnabled) {
            logger.info("기존 데이터가 존재하고 reset이 비활성화되어 있습니다. 초기화를 건너뜁니다.");
            return;
        }
        
        if (hasData && resetEnabled) {
            logger.warn("⚠️ 데이터 리셋 모드 활성화 - 기존 데이터를 삭제합니다.");
            clearAllData();
        }

        // 환경별 데이터 초기화
        switch (dataInitMode.toLowerCase()) {
            case "test" -> initializeTestData();
            case "demo" -> initializeDemoData();
            case "dev" -> initializeDevData();
            case "basic" -> initializeBasicData();
            case "full" -> initializeFullData();
            default -> {
                logger.warn("알 수 없는 데이터 초기화 모드: {}. 기본 모드로 실행합니다.", dataInitMode);
                initializeBasicData();
            }
        }

        logger.info("✅ 데이터 초기화 완료 - Mode: {}", dataInitMode);
        logDataSummary();
    }

    private boolean checkExistingData() {
        return semesterRepository.count() > 0 || departmentRepository.count() > 0;
    }

    private void clearAllData() {
        logger.info("🗑️ 전체 데이터 삭제 중...");
        cartRepository.deleteAll();
        enrollmentRepository.deleteAll();
        courseRepository.deleteAll();
        studentRepository.deleteAll();
        departmentRepository.deleteAll();
        semesterRepository.deleteAll();
        logger.info("✅ 전체 데이터 삭제 완료");
    }

    /**
     * 테스트 환경용 최소 데이터
     * - 빠른 테스트 실행을 위한 필수 데이터만 생성
     */
    private void initializeTestData() {
        logger.info("📝 테스트용 최소 데이터 초기화");
        
        // 현재 학기
        Semester semester = createSemester(2024, Semester.Season.SPRING, true);
        
        // 테스트 학과
        Department dept = createDepartment("컴퓨터과학과", "CS");
        
        // 테스트 학생
        Student student = createStudent("2024001", "테스트학생", dept, 3);
        
        // 테스트 과목들 (5개)
        for (int i = 1; i <= 5; i++) {
            createCourse("CS10" + i, "테스트과목" + i, dept, semester, "김교수", 30);
        }
    }

    /**
     * 데모 환경용 현실적인 데이터
     * - 시연에 적합한 양과 품질의 데이터
     */
    private void initializeDemoData() {
        logger.info("🎬 데모용 현실적인 데이터 초기화");
        
        // 현재 학기 + 이전 학기
        Semester currentSemester = createSemester(2024, Semester.Season.SPRING, true);
        Semester prevSemester = createSemester(2024, Semester.Season.WINTER, false);
        
        // 주요 학과들
        Department[] departments = {
            createDepartment("컴퓨터과학과", "CS"),
            createDepartment("전자공학과", "EE"),
            createDepartment("경영학과", "BM"),
            createDepartment("수학과", "MATH")
        };
        
        // 데모용 학생들 (50명)
        List<Student> students = new ArrayList<>();
        for (int i = 1; i <= 50; i++) {
            Department dept = departments[random.nextInt(departments.length)];
            int grade = 1 + (i % 4); // 1-4학년 균등 분배
            students.add(createStudent(
                String.format("202400%02d", i),
                "데모학생" + i,
                dept,
                grade
            ));
        }
        
        // 데모용 과목들 (각 학과별 10개씩)
        List<Course> courses = new ArrayList<>();
        for (Department dept : departments) {
            for (int i = 1; i <= 10; i++) {
                Course course = createCourse(
                    dept.getCollege() + String.format("%03d", i),
                    dept.getDepartmentName().substring(0, 2) + "과목" + i,
                    dept,
                    currentSemester,
                    "교수" + ((i % 3) + 1),
                    25 + (i * 2)
                );
                courses.add(course);
            }
        }
        
        // 현실적인 수강신청 패턴 생성
        createRealisticEnrollments(students, courses, currentSemester);
        
        // 일부 학생들의 장바구니 데이터
        createDemoCartData(students.subList(0, 15), courses);
    }

    /**
     * 개발 환경용 다양한 시나리오 데이터
     */
    private void initializeDevData() {
        logger.info("🔧 개발용 다양한 시나리오 데이터 초기화");
        initializeDemoData(); // 데모 데이터 기반
        
        // 개발용 추가 시나리오 데이터
        addEdgeCaseData();
        addPerformanceTestData();
    }

    /**
     * 기본 데이터 (프로덕션 초기 설정용)
     */
    private void initializeBasicData() {
        logger.info("🏢 기본 데이터 초기화 (프로덕션 초기 설정)");
        
        // 현재 학기만
        Semester semester = createSemester(2024, Semester.Season.SPRING, true);
        
        // 기본 학과
        createDepartment("컴퓨터과학과", "CS");
        
        // 관리자용 테스트 학생
        createStudent("admin001", "관리자", 
            departmentRepository.findAll().get(0), 4);
    }

    /**
     * 전체 데이터 (성능 테스트용)
     */
    private void initializeFullData() {
        logger.info("🚀 전체 데이터 초기화 (성능 테스트용)");
        initializeDemoData();
        
        // 대량 데이터 추가 (성능 테스트용)
        createBulkDataForPerformance();
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
        student.setPassword("demo123"); // 데모용 비밀번호
        student.setTotalCredits(grade * 12 + random.nextInt(10));
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
            int courseCount = 3 + random.nextInt(3); // 3-5과목
            List<Course> shuffledCourses = new ArrayList<>(courses);
            Collections.shuffle(shuffledCourses);
            
            for (int i = 0; i < Math.min(courseCount, courses.size()); i++) {
                Course course = shuffledCourses.get(i);
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
        enrollment.setEnrolledAt(LocalDateTime.now().minusDays(random.nextInt(30)));
        return enrollmentRepository.save(enrollment);
    }

    private void createDemoCartData(List<Student> students, List<Course> courses) {
        for (Student student : students) {
            // 각 학생마다 1-2개 과목을 장바구니에 추가
            List<Course> availableCourses = courses.stream()
                .filter(c -> c.getEnrolledCount() < c.getCapacity())
                .toList();
            
            if (!availableCourses.isEmpty()) {
                Course course = availableCourses.get(random.nextInt(availableCourses.size()));
                Cart cart = new Cart();
                cart.setStudent(student);
                cart.setCourse(course);
                cart.setAddedAt(LocalDateTime.now().minusHours(random.nextInt(24)));
                cartRepository.save(cart);
            }
        }
    }

    private void addEdgeCaseData() {
        // 엣지 케이스 데이터 (빈 값, 특수 문자, 긴 텍스트 등)
        Department specialDept = createDepartment("특수문자&테스트학과", "SPEC");
        Semester currentSemester = semesterRepository.findCurrentSemester().orElse(null);
        
        if (currentSemester != null) {
            createCourse("SPEC001", "특수문자!@#$%^&*()과목", specialDept, currentSemester, "특수교수", 1);
            createCourse("SPEC002", "아주아주아주아주긴과목명테스트용과목입니다", specialDept, currentSemester, "김교수", 999);
        }
    }

    private void addPerformanceTestData() {
        // 성능 테스트용 추가 데이터
        logger.info("성능 테스트용 추가 데이터 생성 중...");
        // 필요시 대량 데이터 생성 로직 추가
    }

    private void createBulkDataForPerformance() {
        // 대량 데이터 생성 (성능 테스트용)
        logger.info("대량 성능 테스트 데이터 생성 중...");
        // 필요시 구현
    }

    private void logDataSummary() {
        logger.info("📊 데이터 초기화 결과:");
        logger.info("  - 학기: {} 개", semesterRepository.count());
        logger.info("  - 학과: {} 개", departmentRepository.count());
        logger.info("  - 학생: {} 명", studentRepository.count());
        logger.info("  - 과목: {} 개", courseRepository.count());
        logger.info("  - 수강신청: {} 건", enrollmentRepository.count());
        logger.info("  - 장바구니: {} 건", cartRepository.count());
    }
}