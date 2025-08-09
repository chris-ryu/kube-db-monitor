package com.university.registration.repository;

import com.university.registration.entity.Cart;
import com.university.registration.entity.Student;
import com.university.registration.entity.Course;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface CartRepository extends JpaRepository<Cart, Long> {

    // 학생별 장바구니 조회
    List<Cart> findByStudent(Student student);

    @Query("""
        SELECT c FROM Cart c 
        JOIN FETCH c.course co 
        JOIN FETCH co.department 
        WHERE c.student = :student 
        ORDER BY c.addedAt DESC
        """)
    List<Cart> findByStudentWithCourseDetails(@Param("student") Student student);

    // 특정 학생의 특정 과목 장바구니 아이템
    Optional<Cart> findByStudentAndCourse(Student student, Course course);

    boolean existsByStudentAndCourse(Student student, Course course);

    // 학생별 장바구니 과목 수
    @Query("SELECT COUNT(c) FROM Cart c WHERE c.student = :student")
    long countByStudent(@Param("student") Student student);

    // 학생별 장바구니 총 학점
    @Query("SELECT SUM(c.course.credits) FROM Cart c WHERE c.student = :student")
    Integer getTotalCreditsByStudent(@Param("student") Student student);

    // 시간표 충돌 체크
    @Query("""
        SELECT c FROM Cart c 
        WHERE c.student = :student 
        AND c.course.dayTime = :dayTime
        """)
    List<Cart> findTimeConflictItems(@Param("student") Student student, 
                                    @Param("dayTime") String dayTime);

    // 과목별 장바구니 담은 학생 수 (인기도 측정)
    @Query("SELECT COUNT(c) FROM Cart c WHERE c.course = :course")
    long countByCourse(@Param("course") Course course);

    // 학생의 장바구니 전체 삭제
    @Modifying
    @Query("DELETE FROM Cart c WHERE c.student = :student")
    int deleteByStudent(@Param("student") Student student);

    // 특정 과목 장바구니에서 삭제
    @Modifying
    @Query("DELETE FROM Cart c WHERE c.student = :student AND c.course = :course")
    int deleteByStudentAndCourse(@Param("student") Student student, @Param("course") Course course);

    // 인기 과목 (장바구니에 많이 담긴 과목) - 성능 테스트용
    @Query("""
        SELECT c.course, COUNT(c) as cartCount 
        FROM Cart c 
        GROUP BY c.course 
        ORDER BY cartCount DESC
        """)
    List<Object[]> findMostWantedCourses();

    // 학과별 장바구니 통계
    @Query("""
        SELECT c.course.department.departmentName, COUNT(c), AVG(c.course.credits)
        FROM Cart c 
        GROUP BY c.course.department.departmentName 
        ORDER BY COUNT(c) DESC
        """)
    List<Object[]> getCartStatsByDepartment();

    // 복잡한 집계 쿼리 (성능 테스트용)
    @Query("""
        SELECT 
            c.student.grade,
            COUNT(DISTINCT c.student),
            COUNT(c),
            AVG(c.course.credits),
            SUM(c.course.credits)
        FROM Cart c 
        GROUP BY c.student.grade 
        ORDER BY c.student.grade
        """)
    List<Object[]> getCartStatsByGrade();

    // 교수별 인기도 (장바구니 기준)
    @Query("""
        SELECT c.course.professor, COUNT(c) as popularity
        FROM Cart c 
        GROUP BY c.course.professor 
        HAVING COUNT(c) > :minCount
        ORDER BY popularity DESC
        """)
    List<Object[]> getPopularProfessors(@Param("minCount") Long minCount);
}