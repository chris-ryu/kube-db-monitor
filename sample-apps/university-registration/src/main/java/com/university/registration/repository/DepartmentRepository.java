package com.university.registration.repository;

import com.university.registration.entity.Department;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface DepartmentRepository extends JpaRepository<Department, Long> {

    Optional<Department> findByDepartmentName(String departmentName);

    List<Department> findByCollege(String college);

    @Query("SELECT d FROM Department d WHERE d.college = :college ORDER BY d.departmentName")
    List<Department> findByCollegeOrderByName(String college);

    boolean existsByDepartmentName(String departmentName);
}