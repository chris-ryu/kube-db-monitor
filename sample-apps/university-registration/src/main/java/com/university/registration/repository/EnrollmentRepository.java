package com.university.registration.repository;

import com.university.registration.entity.Enrollment;
import com.university.registration.entity.Student;
import com.university.registration.entity.Course;
import com.university.registration.entity.Semester;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface EnrollmentRepository extends JpaRepository<Enrollment, Long> {

    // 기본 조회
    List<Enrollment> findByStudent(Student student);

    List<Enrollment> findByCourse(Course course);

    List<Enrollment> findBySemester(Semester semester);

    Optional<Enrollment> findByStudentAndCourseAndSemester(Student student, Course course, Semester semester);

    boolean existsByStudentAndCourse(Student student, Course course);

    // 활성 수강신청만 조회
    List<Enrollment> findByStudentAndStatus(Student student, Enrollment.Status status);

    @Query("""
        SELECT e FROM Enrollment e 
        JOIN FETCH e.course c 
        JOIN FETCH c.department 
        WHERE e.student = :student 
        AND e.status = 'ENROLLED' 
        ORDER BY e.enrolledAt DESC
        """)
    List<Enrollment> findActiveEnrollmentsByStudent(@Param("student") Student student);

    // 학기별 학생 수강신청 조회
    @Query("""
        SELECT e FROM Enrollment e 
        JOIN FETCH e.course c 
        WHERE e.student = :student 
        AND e.semester = :semester 
        AND e.status = 'ENROLLED'
        ORDER BY c.dayTime
        """)
    List<Enrollment> findByStudentAndSemesterWithCourse(@Param("student") Student student, 
                                                        @Param("semester") Semester semester);

    // 과목별 수강신청자 수
    @Query("SELECT COUNT(e) FROM Enrollment e WHERE e.course = :course AND e.status = 'ENROLLED'")
    long countActiveByCourse(@Param("course") Course course);

    // 시간표 충돌 체크
    @Query("""
        SELECT e FROM Enrollment e 
        WHERE e.student = :student 
        AND e.semester = :semester 
        AND e.course.dayTime = :dayTime 
        AND e.status = 'ENROLLED'
        """)
    List<Enrollment> findTimeConflictEnrollments(@Param("student") Student student, 
                                                @Param("semester") Semester semester,
                                                @Param("dayTime") String dayTime);

    // 학생별 총 수강 학점
    @Query("""
        SELECT COALESCE(SUM(e.course.credits), 0) 
        FROM Enrollment e 
        WHERE e.student = :student 
        AND e.semester = :semester 
        AND e.status = 'ENROLLED'
        """)
    Integer getTotalCreditsByStudentAndSemester(@Param("student") Student student, 
                                               @Param("semester") Semester semester);

    // 수강신청 취소
    @Modifying
    @Query("""
        UPDATE Enrollment e 
        SET e.status = 'CANCELLED' 
        WHERE e.student = :student 
        AND e.course = :course 
        AND e.semester = :semester
        """)
    int cancelEnrollment(@Param("student") Student student, 
                        @Param("course") Course course, 
                        @Param("semester") Semester semester);

    // 통계 쿼리들 (성능 테스트용)
    @Query("""
        SELECT e.course.department.departmentName, COUNT(e), AVG(e.course.credits)
        FROM Enrollment e 
        WHERE e.semester = :semester 
        AND e.status = 'ENROLLED'
        GROUP BY e.course.department.departmentName 
        ORDER BY COUNT(e) DESC
        """)
    List<Object[]> getEnrollmentStatsByDepartment(@Param("semester") Semester semester);

    // 학년별 평균 수강 학점
    @Query("""
        SELECT 
            e.student.grade,
            COUNT(DISTINCT e.student),
            AVG(subquery.totalCredits)
        FROM Enrollment e
        JOIN (
            SELECT e2.student.studentId as studentId, SUM(e2.course.credits) as totalCredits
            FROM Enrollment e2 
            WHERE e2.semester = :semester AND e2.status = 'ENROLLED'
            GROUP BY e2.student.studentId
        ) subquery ON e.student.studentId = subquery.studentId
        WHERE e.semester = :semester AND e.status = 'ENROLLED'
        GROUP BY e.student.grade
        ORDER BY e.student.grade
        """)
    List<Object[]> getAverageCreditsStatsByGrade(@Param("semester") Semester semester);

    // 시간대별 인기도 분석
    @Query("""
        SELECT 
            e.course.dayTime,
            COUNT(e) as enrollmentCount,
            COUNT(DISTINCT e.course) as courseCount,
            AVG(e.course.capacity) as avgCapacity
        FROM Enrollment e 
        WHERE e.semester = :semester 
        AND e.status = 'ENROLLED'
        AND e.course.dayTime IS NOT NULL
        GROUP BY e.course.dayTime 
        ORDER BY enrollmentCount DESC
        """)
    List<Object[]> getTimeSlotPopularity(@Param("semester") Semester semester);

    // 교수별 수강생 수
    @Query("""
        SELECT 
            e.course.professor,
            COUNT(e) as totalEnrollments,
            COUNT(DISTINCT e.course) as courseCount,
            AVG(e.course.credits) as avgCredits
        FROM Enrollment e 
        WHERE e.semester = :semester AND e.status = 'ENROLLED'
        GROUP BY e.course.professor 
        ORDER BY totalEnrollments DESC
        """)
    List<Object[]> getProfessorEnrollmentStats(@Param("semester") Semester semester);

    // 최근 수강신청 내역 (실시간 모니터링용)
    @Query("""
        SELECT e FROM Enrollment e 
        JOIN FETCH e.student s 
        JOIN FETCH e.course c 
        WHERE e.enrolledAt >= :since 
        ORDER BY e.enrolledAt DESC
        """)
    List<Enrollment> findRecentEnrollments(@Param("since") LocalDateTime since);

    // 페이징된 수강신청 내역
    Page<Enrollment> findByStudentAndSemester(Student student, Semester semester, Pageable pageable);

    // 복잡한 통계 쿼리 (성능 테스트용)
    @Query("""
        SELECT 
            DATE(e.enrolledAt) as enrollmentDate,
            COUNT(e) as dailyCount,
            COUNT(DISTINCT e.student) as uniqueStudents,
            COUNT(DISTINCT e.course) as uniqueCourses,
            AVG(e.course.credits) as avgCredits
        FROM Enrollment e 
        WHERE e.semester = :semester 
        AND e.status = 'ENROLLED'
        AND e.enrolledAt >= :startDate
        GROUP BY DATE(e.enrolledAt) 
        ORDER BY enrollmentDate DESC
        """)
    List<Object[]> getDailyEnrollmentStats(@Param("semester") Semester semester,
                                          @Param("startDate") LocalDateTime startDate);

    // 선수과목 이수 여부 체크
    @Query("""
        SELECT e FROM Enrollment e 
        WHERE e.student = :student 
        AND e.course = :prerequisiteCourse 
        AND e.status = 'ENROLLED'
        """)
    Optional<Enrollment> findPrerequisiteEnrollment(@Param("student") Student student,
                                                   @Param("prerequisiteCourse") Course prerequisiteCourse);
}