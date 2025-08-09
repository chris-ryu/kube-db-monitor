package com.university.registration.repository;

import com.university.registration.entity.Course;
import com.university.registration.entity.Department;
import com.university.registration.entity.Semester;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;

@Repository
public interface CourseRepository extends JpaRepository<Course, String> {

    // 기본 조회
    Optional<Course> findByCourseId(String courseId);

    List<Course> findBySemester(Semester semester);

    List<Course> findByDepartment(Department department);

    long countByDepartment(Department department);

    List<Course> findBySemesterAndDepartment(Semester semester, Department department);

    // 검색 기능
    @Query("""
        SELECT c FROM Course c 
        WHERE c.semester = :semester 
        AND c.isActive = true 
        AND (:departmentId IS NULL OR c.department.id = :departmentId)
        AND (:keyword IS NULL OR 
             LOWER(c.courseName) LIKE LOWER(CONCAT('%', :keyword, '%')) OR 
             LOWER(c.professor) LIKE LOWER(CONCAT('%', :keyword, '%')))
        ORDER BY c.courseId
        """)
    Page<Course> searchCourses(@Param("semester") Semester semester,
                              @Param("departmentId") Long departmentId,
                              @Param("keyword") String keyword,
                              Pageable pageable);

    // 시간표 충돌 체크
    @Query("""
        SELECT c FROM Course c 
        WHERE c.semester = :semester 
        AND c.dayTime = :dayTime 
        AND c.isActive = true
        """)
    List<Course> findByDayTimeAndSemester(@Param("dayTime") String dayTime, 
                                         @Param("semester") Semester semester);

    // 인기도별 조회
    List<Course> findByPopularityLevel(Course.PopularityLevel popularityLevel);

    @Query("SELECT c FROM Course c WHERE c.popularityLevel = :level AND c.semester = :semester")
    List<Course> findByPopularityLevelAndSemester(@Param("level") Course.PopularityLevel level,
                                                 @Param("semester") Semester semester);

    // 정원 관련 쿼리 (동시성 제어를 위한 락)
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT c FROM Course c WHERE c.courseId = :courseId")
    Optional<Course> findByCourseIdWithLock(@Param("courseId") String courseId);

    // 수강신청 인원 업데이트
    @Modifying
    @Query("UPDATE Course c SET c.enrolledCount = c.enrolledCount + :delta WHERE c.courseId = :courseId")
    int updateEnrolledCount(@Param("courseId") String courseId, @Param("delta") Integer delta);

    // 정원이 남은 과목 조회
    @Query("SELECT c FROM Course c WHERE c.enrolledCount < c.capacity AND c.isActive = true")
    List<Course> findAvailableCourses();

    // 인기 과목 (정원 대비 신청률이 높은 과목)
    @Query("""
        SELECT c FROM Course c 
        WHERE c.semester = :semester 
        AND c.isActive = true 
        AND (c.enrolledCount * 1.0 / c.capacity) > :threshold
        ORDER BY (c.enrolledCount * 1.0 / c.capacity) DESC
        """)
    List<Course> findPopularCourses(@Param("semester") Semester semester, 
                                   @Param("threshold") Double threshold);

    // 통계 쿼리들 (성능 테스트용)
    @Query("SELECT AVG(c.enrolledCount) FROM Course c WHERE c.semester = :semester")
    Double getAverageEnrollment(@Param("semester") Semester semester);

    @Query("""
        SELECT c.department.departmentName, COUNT(c), AVG(c.enrolledCount) 
        FROM Course c 
        WHERE c.semester = :semester 
        GROUP BY c.department.departmentName 
        ORDER BY COUNT(c) DESC
        """)
    List<Object[]> getEnrollmentStatsByDepartment(@Param("semester") Semester semester);

    // 복잡한 JOIN 쿼리 (성능 테스트용)
    @Query("""
        SELECT c FROM Course c
        JOIN FETCH c.department d
        JOIN FETCH c.semester s
        LEFT JOIN FETCH c.enrollments e
        LEFT JOIN FETCH e.student st
        WHERE c.semester = :semester
        AND c.isActive = true
        ORDER BY c.enrolledCount DESC
        """)
    List<Course> findCoursesWithEnrollmentDetails(@Param("semester") Semester semester);

    // 선수과목이 있는 과목들
    @Query("SELECT c FROM Course c WHERE c.prerequisiteCourse IS NOT NULL")
    List<Course> findCoursesWithPrerequisites();

    // 특정 과목을 선수과목으로 하는 과목들
    List<Course> findByPrerequisiteCourse(Course prerequisiteCourse);

    // 교수별 과목 수
    @Query("SELECT c.professor, COUNT(c) FROM Course c WHERE c.semester = :semester GROUP BY c.professor")
    List<Object[]> countCoursesByProfessor(@Param("semester") Semester semester);

    // 시간대별 과목 수 (성능 테스트용)
    @Query("""
        SELECT c.dayTime, COUNT(c), SUM(c.enrolledCount), AVG(c.capacity) 
        FROM Course c 
        WHERE c.semester = :semester AND c.dayTime IS NOT NULL 
        GROUP BY c.dayTime 
        ORDER BY COUNT(c) DESC
        """)
    List<Object[]> getTimeSlotStatistics(@Param("semester") Semester semester);
}