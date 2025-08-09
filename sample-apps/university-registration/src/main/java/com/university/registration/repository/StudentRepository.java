package com.university.registration.repository;

import com.university.registration.entity.Student;
import com.university.registration.entity.Department;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface StudentRepository extends JpaRepository<Student, String> {

    Optional<Student> findByStudentId(String studentId);

    List<Student> findByDepartment(Department department);

    List<Student> findByGrade(Integer grade);

    Page<Student> findByDepartment(Department department, Pageable pageable);

    @Query("SELECT s FROM Student s WHERE s.department.id = :departmentId AND s.grade = :grade")
    List<Student> findByDepartmentAndGrade(@Param("departmentId") Long departmentId, @Param("grade") Integer grade);

    @Query("SELECT COUNT(s) FROM Student s WHERE s.department = :department")
    long countByDepartment(@Param("department") Department department);

    long countByGrade(Integer grade);

    @Query("SELECT s FROM Student s WHERE s.name LIKE %:name%")
    List<Student> findByNameContaining(@Param("name") String name);

    // 학점 업데이트를 위한 메서드
    @Modifying
    @Query("UPDATE Student s SET s.totalCredits = :totalCredits WHERE s.studentId = :studentId")
    int updateTotalCredits(@Param("studentId") String studentId, @Param("totalCredits") Integer totalCredits);

    // 통계 쿼리
    @Query("SELECT AVG(s.totalCredits) FROM Student s WHERE s.department = :department")
    Double getAverageCredits(@Param("department") Department department);

    // 성능 테스트를 위한 복잡한 쿼리
    @Query("""
        SELECT s FROM Student s 
        JOIN FETCH s.department d 
        LEFT JOIN FETCH s.enrollments e 
        LEFT JOIN FETCH e.course c 
        WHERE s.grade = :grade 
        ORDER BY s.totalCredits DESC
        """)
    List<Student> findStudentsWithEnrollmentsByGrade(@Param("grade") Integer grade);
}