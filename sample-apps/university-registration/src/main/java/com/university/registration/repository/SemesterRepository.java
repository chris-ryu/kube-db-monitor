package com.university.registration.repository;

import com.university.registration.entity.Semester;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface SemesterRepository extends JpaRepository<Semester, Long> {

    Optional<Semester> findByIsCurrent(Boolean isCurrent);

    @Query("SELECT s FROM Semester s WHERE s.isCurrent = true")
    Optional<Semester> findCurrentSemester();

    List<Semester> findByYear(Integer year);

    List<Semester> findByYearAndSeason(Integer year, Semester.Season season);

    @Query("SELECT s FROM Semester s WHERE s.registrationStart <= :now AND s.registrationEnd >= :now")
    List<Semester> findActiveRegistrationSemesters(@Param("now") LocalDateTime now);

    @Query("SELECT s FROM Semester s ORDER BY s.year DESC, s.season DESC")
    List<Semester> findAllOrderByYearAndSeasonDesc();

    // 수강신청 가능한 학기 조회
    @Query("""
        SELECT s FROM Semester s 
        WHERE s.registrationStart <= CURRENT_TIMESTAMP 
        AND s.registrationEnd >= CURRENT_TIMESTAMP 
        AND s.isCurrent = true
        """)
    Optional<Semester> findActiveRegistrationSemester();

    boolean existsByYearAndSeason(Integer year, Semester.Season season);
}