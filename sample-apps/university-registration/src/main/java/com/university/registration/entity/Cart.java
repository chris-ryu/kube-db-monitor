package com.university.registration.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "cart", 
       uniqueConstraints = @UniqueConstraint(columnNames = {"student_id", "course_id"}))
public class Cart {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "cart_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "student_id", nullable = false)
    private Student student;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "course_id", nullable = false)
    private Course course;

    @Column(name = "added_at")
    private LocalDateTime addedAt = LocalDateTime.now();

    // Constructors
    public Cart() {}

    public Cart(Student student, Course course) {
        this.student = student;
        this.course = course;
    }

    // Business methods
    public boolean isTimeConflictWith(Cart other) {
        return this.course.hasTimeConflictWith(other.getCourse());
    }

    // Getters and Setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Student getStudent() { return student; }
    public void setStudent(Student student) { this.student = student; }

    public Course getCourse() { return course; }
    public void setCourse(Course course) { this.course = course; }

    public LocalDateTime getAddedAt() { return addedAt; }
    public void setAddedAt(LocalDateTime addedAt) { this.addedAt = addedAt; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Cart)) return false;
        Cart cart = (Cart) o;
        return student.getStudentId().equals(cart.student.getStudentId()) &&
               course.getCourseId().equals(cart.course.getCourseId());
    }

    @Override
    public int hashCode() {
        return student.getStudentId().hashCode() + course.getCourseId().hashCode();
    }
}