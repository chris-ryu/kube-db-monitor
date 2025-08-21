package com.university.registration.dto;

import com.university.registration.entity.Enrollment;
import java.time.LocalDateTime;

public class EnrollmentDTO {
    private Long id;
    private String studentId;
    private String studentName;
    private CourseDTO course;
    private LocalDateTime enrolledAt;
    private String status;

    // Constructors
    public EnrollmentDTO() {}

    public EnrollmentDTO(Enrollment enrollment) {
        this.id = enrollment.getId();
        this.studentId = enrollment.getStudent().getStudentId();
        this.studentName = enrollment.getStudent().getName();
        this.enrolledAt = enrollment.getEnrolledAt();
        this.status = "ACTIVE"; // 기본값
        
        // Course 정보를 CourseDTO로 변환
        if (enrollment.getCourse() != null) {
            this.course = new CourseDTO(enrollment.getCourse());
        }
    }

    // Getters and Setters
    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getStudentId() {
        return studentId;
    }

    public void setStudentId(String studentId) {
        this.studentId = studentId;
    }

    public String getStudentName() {
        return studentName;
    }

    public void setStudentName(String studentName) {
        this.studentName = studentName;
    }

    public CourseDTO getCourse() {
        return course;
    }

    public void setCourse(CourseDTO course) {
        this.course = course;
    }

    public LocalDateTime getEnrolledAt() {
        return enrolledAt;
    }

    public void setEnrolledAt(LocalDateTime enrolledAt) {
        this.enrolledAt = enrolledAt;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }
}