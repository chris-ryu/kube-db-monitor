package com.university.registration.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.List;

@Entity
@Table(name = "semesters")
public class Semester {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "semester_id")
    private Long id;

    @Column(nullable = false)
    private Integer year;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private Season season;

    @Column(name = "registration_start", nullable = false)
    private LocalDateTime registrationStart;

    @Column(name = "registration_end", nullable = false)
    private LocalDateTime registrationEnd;

    @Column(name = "is_current")
    private Boolean isCurrent = false;

    @OneToMany(mappedBy = "semester", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private List<Course> courses;

    @OneToMany(mappedBy = "semester", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private List<Enrollment> enrollments;

    public enum Season {
        SPRING, FALL, SUMMER, WINTER
    }

    // Constructors
    public Semester() {}

    public Semester(Integer year, Season season, LocalDateTime registrationStart, LocalDateTime registrationEnd) {
        this.year = year;
        this.season = season;
        this.registrationStart = registrationStart;
        this.registrationEnd = registrationEnd;
    }

    // Business methods
    public boolean isRegistrationOpen() {
        LocalDateTime now = LocalDateTime.now();
        return now.isAfter(registrationStart) && now.isBefore(registrationEnd);
    }

    public String getDisplayName() {
        return year + " " + season.name();
    }

    // Getters and Setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Integer getYear() { return year; }
    public void setYear(Integer year) { this.year = year; }

    public Season getSeason() { return season; }
    public void setSeason(Season season) { this.season = season; }

    public LocalDateTime getRegistrationStart() { return registrationStart; }
    public void setRegistrationStart(LocalDateTime registrationStart) { this.registrationStart = registrationStart; }

    public LocalDateTime getRegistrationEnd() { return registrationEnd; }
    public void setRegistrationEnd(LocalDateTime registrationEnd) { this.registrationEnd = registrationEnd; }

    public Boolean getIsCurrent() { return isCurrent; }
    public void setIsCurrent(Boolean isCurrent) { this.isCurrent = isCurrent; }

    public List<Course> getCourses() { return courses; }
    public void setCourses(List<Course> courses) { this.courses = courses; }

    public List<Enrollment> getEnrollments() { return enrollments; }
    public void setEnrollments(List<Enrollment> enrollments) { this.enrollments = enrollments; }
}