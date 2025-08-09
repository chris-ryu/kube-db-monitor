package com.university.registration.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "enrollments",
       uniqueConstraints = @UniqueConstraint(columnNames = {"student_id", "course_id", "semester_id"}))
public class Enrollment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "enrollment_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "student_id", nullable = false)
    private Student student;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "course_id", nullable = false)
    private Course course;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "semester_id", nullable = false)
    private Semester semester;

    @Column(name = "enrolled_at")
    private LocalDateTime enrolledAt = LocalDateTime.now();

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private Status status = Status.ENROLLED;

    public enum Status {
        ENROLLED,    // 수강신청 완료
        CANCELLED,   // 취소됨
        WAITLISTED   // 대기열 (확장 기능)
    }

    // Constructors
    public Enrollment() {}

    public Enrollment(Student student, Course course, Semester semester) {
        this.student = student;
        this.course = course;
        this.semester = semester;
    }

    // Business methods
    public boolean isActive() {
        return status == Status.ENROLLED;
    }

    public void cancel() {
        this.status = Status.CANCELLED;
    }

    // Getters and Setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Student getStudent() { return student; }
    public void setStudent(Student student) { this.student = student; }

    public Course getCourse() { return course; }
    public void setCourse(Course course) { this.course = course; }

    public Semester getSemester() { return semester; }
    public void setSemester(Semester semester) { this.semester = semester; }

    public LocalDateTime getEnrolledAt() { return enrolledAt; }
    public void setEnrolledAt(LocalDateTime enrolledAt) { this.enrolledAt = enrolledAt; }

    public Status getStatus() { return status; }
    public void setStatus(Status status) { this.status = status; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Enrollment)) return false;
        Enrollment that = (Enrollment) o;
        return student.getStudentId().equals(that.student.getStudentId()) &&
               course.getCourseId().equals(that.course.getCourseId()) &&
               semester.getId().equals(that.semester.getId());
    }

    @Override
    public int hashCode() {
        return student.getStudentId().hashCode() + 
               course.getCourseId().hashCode() + 
               semester.getId().hashCode();
    }
}