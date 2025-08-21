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
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;

/**
 * 샘플 데이터 초기화 컴포넌트
 * KubeDB Monitor 테스트를 위한 현실적인 대학교 수강신청 시스템 데이터 생성
 */
@Component
@Profile("!test")  // test 프로파일에서만 비활성화, development는 활성화
public class DataInitializer {

    private static final Logger logger = LoggerFactory.getLogger(DataInitializer.class);

    @Value("${app.registration.initialize-sample-data:true}")
    private boolean initializeSampleData;

    @Value("${app.registration.data-init-batch-size:100}")
    private int batchSize;

    @Autowired private DepartmentRepository departmentRepository;
    @Autowired private StudentRepository studentRepository;
    @Autowired private CourseRepository courseRepository;
    @Autowired private SemesterRepository semesterRepository;
    @Autowired private CartRepository cartRepository;
    @Autowired private EnrollmentRepository enrollmentRepository;

    private final Random random = new Random();
    private final List<Department> departments = new ArrayList<>();
    private final List<Student> students = new ArrayList<>();
    private final List<Course> courses = new ArrayList<>();
    private final List<Semester> semesters = new ArrayList<>();

    @PostConstruct
    @Transactional
    public void initializeData() {
        if (!initializeSampleData) {
            logger.info("Sample data initialization is disabled (app.registration.initialize-sample-data=false)");
            return;
        }
        
        // Check if data already exists
        if (departmentRepository.count() > 0) {
            logger.info("Sample data already exists, skipping initialization");
            return;
        }
        
        logger.info("Starting data initialization for KubeDB Monitor testing...");
        logger.info("Configuration: batch-size={}", batchSize);
        
        long startTime = System.currentTimeMillis();
        
        try {
            initializeSemesters();
            initializeDepartments();
            initializeCourses();
            initializeStudents(); // Cart API 테스트를 위해 학생 데이터 추가
            
            long duration = System.currentTimeMillis() - startTime;
            logger.info("Data initialization completed successfully in {} ms", duration);
            logger.info("Created: {} departments, {} courses, {} students", 
                       departments.size(), courses.size(), students.size());
            
        } catch (Exception e) {
            logger.error("Failed to initialize data: {}", e.getMessage(), e);
            throw new RuntimeException("Data initialization failed", e);
        }
    }

    /**
     * 학기 정보 초기화
     */
    private void initializeSemesters() {
        logger.info("Initializing semesters...");
        
        // 현재 학기
        Semester currentSemester = new Semester();
        currentSemester.setYear(2024);
        currentSemester.setSeason(Semester.Season.SPRING);
        currentSemester.setRegistrationStart(LocalDateTime.now().minusDays(30));
        currentSemester.setRegistrationEnd(LocalDateTime.now().plusDays(30));
        currentSemester.setIsCurrent(true);
        semesters.add(semesterRepository.save(currentSemester));
        
        // 이전 학기들
        for (int year = 2020; year < 2024; year++) {
            for (Semester.Season season : Semester.Season.values()) {
                Semester semester = new Semester();
                semester.setYear(year);
                semester.setSeason(season);
                semester.setRegistrationStart(LocalDateTime.of(year, 2, 1, 9, 0));
                semester.setRegistrationEnd(LocalDateTime.of(year, 2, 15, 18, 0));
                semester.setIsCurrent(false);
                semesters.add(semesterRepository.save(semester));
            }
        }
    }

    /**
     * 학과 정보 초기화 (10개 학과)
     */
    private void initializeDepartments() {
        logger.info("Initializing departments...");
        
        String[][] deptData = {
            {"컴퓨터과학과", "공과대학"},
            {"전자공학과", "공과대학"}, 
            {"기계공학과", "공과대학"},
            {"화학공학과", "공과대학"},
            {"경영학과", "경영대학"},
            {"경제학과", "경영대학"},
            {"수학과", "자연과학대학"},
            {"물리학과", "자연과학대학"},
            {"영어영문학과", "인문대학"},
            {"심리학과", "사회과학대학"}
        };
        
        for (String[] data : deptData) {
            Department dept = new Department();
            dept.setDepartmentName(data[0]);
            dept.setCollege(data[1]);
            departments.add(departmentRepository.save(dept));
        }
    }

    /**
     * 학생 정보 초기화 (테스트용 5명만 생성)
     */
    private void initializeStudents() {
        logger.info("Initializing test students...");
        
        String[] firstNames = {"민준", "서준", "지민", "서윤", "하준"};
        String[] lastNames = {"김", "이", "박", "최", "정"};
        
        // 테스트용 학생 5명 생성
        for (int i = 0; i < 5; i++) {
            Student student = new Student();
            String studentId = String.format("2024%03d", i + 1); // 2024001, 2024002, ...
            String name = lastNames[i] + firstNames[i];
            
            student.setStudentId(studentId);
            student.setName(name);
            student.setEmail(studentId + "@university.edu");
            student.setGrade(2); // 모두 2학년으로 설정
            student.setDepartment(departments.get(i % departments.size()));
            student.setPassword("password123");
            student.setTotalCredits(30 + random.nextInt(10)); // 2학년 적정 학점
            
            students.add(studentRepository.save(student));
            logger.debug("Created test student: {} ({})", studentId, name);
        }
    }

    /**
     * 과목 정보 초기화 (200개 과목)
     */
    private void initializeCourses() {
        logger.info("Initializing 200 courses...");
        
        String[] courseNames = {
            "데이터구조", "알고리즘", "데이터베이스", "운영체제", "컴퓨터네트워크",
            "소프트웨어공학", "인공지능", "머신러닝", "웹프로그래밍", "모바일프로그래밍",
            "회로이론", "전자회로", "디지털논리회로", "마이크로프로세서", "신호및시스템",
            "경영학원론", "회계학원론", "마케팅원론", "재무관리", "인적자원관리",
            "미적분학", "선형대수", "통계학", "확률론", "수치해석",
            "일반물리학", "양자역학", "전자기학", "열역학", "광학",
            "영문법", "영어회화", "영문학개론", "미국문학", "영국문학",
            "심리학개론", "사회심리학", "인지심리학", "발달심리학", "임상심리학"
        };
        
        String[] professors = {"김교수", "이교수", "박교수", "최교수", "정교수", "강교수", 
                              "조교수", "윤교수", "장교수", "임교수"};
        
        String[] timeSlots = {"월1", "월2", "화1", "화2", "수1", "수2", 
                             "목1", "목2", "금1", "금2"};
        
        Semester currentSemester = semesters.get(0);
        int courseCounter = 1;
        
        for (int i = 0; i < 10; i++) { // UI 테스트용으로 10개만 생성
            Course course = new Course();
            course.setCourseId(String.format("CS%03d", courseCounter++));
            course.setCourseName(courseNames[random.nextInt(courseNames.length)]);
            course.setDepartment(departments.get(random.nextInt(departments.size())));
            course.setCredits(3); // 기본 3학점
            course.setProfessor(professors[random.nextInt(professors.length)]);
            course.setCapacity(30 + random.nextInt(41)); // 30-70명
            course.setEnrolledCount(0);
            course.setSemester(currentSemester);
            course.setClassroom("강의실" + (100 + random.nextInt(200)));
            
            // 모든 과목에 시간 정보 설정 (일부는 복수 시간으로 설정)
            if (random.nextDouble() < 0.3) {
                // 30% 확률로 복수 시간 설정 (예: "월1,수2")
                String[] twoSlots = {timeSlots[random.nextInt(timeSlots.length)], 
                                   timeSlots[random.nextInt(timeSlots.length)]};
                course.setDayTime(String.join(",", twoSlots));
            } else {
                // 70% 확률로 단일 시간 설정
                course.setDayTime(timeSlots[random.nextInt(timeSlots.length)]);
            }
            
            courses.add(courseRepository.save(course));
        }
    }

    /**
     * 수강신청 데이터 초기화 (현실적인 패턴)
     */
    private void initializeEnrollments() {
        logger.info("Initializing enrollments with realistic patterns...");
        
        Semester currentSemester = semesters.get(0);
        int totalEnrollments = 0;
        
        // 각 학생에게 3-6개 과목 할당
        for (Student student : students) {
            int maxCourses = 3 + random.nextInt(4); // 3-6 과목
            List<Course> shuffledCourses = new ArrayList<>(courses);
            Collections.shuffle(shuffledCourses);
            
            int enrolledCount = 0;
            for (Course course : shuffledCourses) {
                if (enrolledCount >= maxCourses) break;
                if (course.getEnrolledCount() >= course.getCapacity()) continue;
                
                // 70% 확률로 수강신청
                if (random.nextDouble() < 0.7) {
                    Enrollment enrollment = new Enrollment();
                    enrollment.setStudent(student);
                    enrollment.setCourse(course);
                    enrollment.setSemester(currentSemester);
                    enrollment.setStatus(Enrollment.Status.ENROLLED);
                    
                    enrollmentRepository.save(enrollment);
                    
                    // 과목 수강인원 증가
                    course.setEnrolledCount(course.getEnrolledCount() + 1);
                    courseRepository.save(course);
                    
                    totalEnrollments++;
                    enrolledCount++;
                }
            }
            
            if (totalEnrollments % 500 == 0) {
                logger.debug("Created {} enrollments", totalEnrollments);
            }
        }
        
        logger.info("Created {} total enrollments", totalEnrollments);
    }

    /**
     * 장바구니 데이터 초기화 (수강신청 대기 과목들)
     */
    private void initializeCarts() {
        logger.info("Initializing cart data...");
        
        int totalCartItems = 0;
        
        // 전체 학생의 30%가 장바구니에 1-2개 과목 보관
        List<Student> studentsWithCarts = new ArrayList<>(students);
        Collections.shuffle(studentsWithCarts);
        int numStudentsWithCarts = (int) (students.size() * 0.3);
        
        for (int i = 0; i < numStudentsWithCarts; i++) {
            Student student = studentsWithCarts.get(i);
            int numCartItems = 1 + random.nextInt(2); // 1-2개
            
            // 수강하지 않은 과목 중에서 선택
            List<Course> availableCourses = courses.stream()
                .filter(c -> c.getEnrolledCount() < c.getCapacity())
                .filter(c -> !isStudentEnrolledInCourse(student, c))
                .collect(Collectors.toList());
            
            if (availableCourses.isEmpty()) continue;
            
            Collections.shuffle(availableCourses);
            
            for (int j = 0; j < Math.min(numCartItems, availableCourses.size()); j++) {
                Course course = availableCourses.get(j);
                
                Cart cart = new Cart();
                cart.setStudent(student);
                cart.setCourse(course);
                
                cartRepository.save(cart);
                totalCartItems++;
            }
        }
        
        logger.info("Created {} cart items for {} students", totalCartItems, numStudentsWithCarts);
    }

    // Helper methods
    
    private boolean isStudentEnrolledInCourse(Student student, Course course) {
        return enrollmentRepository.existsByStudentAndCourse(student, course);
    }
}