package com.university.registration.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.List;

@Entity
@Table(name = "courses")
public class Course {

    @Id
    @Column(name = "course_id", length = 10)
    private String courseId; // 과목코드 (예: CSE101)

    @Column(name = "course_name", nullable = false, length = 100)
    private String courseName;

    @Column(nullable = false, length = 50)
    private String professor;

    @Column(nullable = false)
    private Integer credits; // 학점 (1-3)

    @Column(nullable = false)
    private Integer capacity; // 정원

    @Column(name = "enrolled_count")
    private Integer enrolledCount = 0; // 현재 신청 인원

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "department_id", nullable = false)
    private Department department;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "semester_id", nullable = false)
    private Semester semester;

    @Column(name = "day_time", length = 20)
    private String dayTime; // 요일-교시 (예: 월1,화3)

    @Column(length = 20)
    private String classroom; // 강의실

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "prerequisite_course_id")
    private Course prerequisiteCourse; // 선수과목

    @Column(name = "is_active")
    private Boolean isActive = true; // 활성화 여부

    @Enumerated(EnumType.STRING)
    @Column(name = "popularity_level")
    private PopularityLevel popularityLevel = PopularityLevel.MEDIUM;

    @Column(name = "created_at")
    private LocalDateTime createdAt = LocalDateTime.now();

    @OneToMany(mappedBy = "course", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private List<Cart> cartItems;

    @OneToMany(mappedBy = "course", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private List<Enrollment> enrollments;

    @OneToMany(mappedBy = "prerequisiteCourse", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private List<Course> dependentCourses;

    public enum PopularityLevel {
        HIGH,    // 인기과목 (정원 30명)
        MEDIUM,  // 보통과목 (정원 50명)
        LOW      // 비인기과목 (정원 80명)
    }

    // Constructors
    public Course() {}

    public Course(String courseId, String courseName, String professor, Integer credits, 
                  Integer capacity, Department department, Semester semester) {
        this.courseId = courseId;
        this.courseName = courseName;
        this.professor = professor;
        this.credits = credits;
        this.capacity = capacity;
        this.department = department;
        this.semester = semester;
    }

    // Business methods
    public boolean hasAvailableSlots() {
        return enrolledCount < capacity;
    }

    public int getAvailableSlots() {
        return capacity - enrolledCount;
    }

    public boolean hasTimeConflictWith(Course other) {
        if (this.dayTime == null || other.getDayTime() == null) {
            return false;
        }
        return this.dayTime.equals(other.getDayTime());
    }

    public double getEnrollmentRate() {
        return capacity == 0 ? 0.0 : (double) enrolledCount / capacity;
    }

    // Version for optimistic locking
    @Version
    private Long version;

    // Getters and Setters
    public String getCourseId() { return courseId; }
    public void setCourseId(String courseId) { this.courseId = courseId; }

    public String getCourseName() { return courseName; }
    public void setCourseName(String courseName) { this.courseName = courseName; }

    public String getProfessor() { return professor; }
    public void setProfessor(String professor) { this.professor = professor; }

    public Integer getCredits() { return credits; }
    public void setCredits(Integer credits) { this.credits = credits; }

    public Integer getCapacity() { return capacity; }
    public void setCapacity(Integer capacity) { this.capacity = capacity; }

    public Integer getEnrolledCount() { return enrolledCount; }
    public void setEnrolledCount(Integer enrolledCount) { this.enrolledCount = enrolledCount; }

    public Department getDepartment() { return department; }
    public void setDepartment(Department department) { this.department = department; }

    public Semester getSemester() { return semester; }
    public void setSemester(Semester semester) { this.semester = semester; }

    public String getDayTime() { return dayTime; }
    public void setDayTime(String dayTime) { this.dayTime = dayTime; }

    public String getClassroom() { return classroom; }
    public void setClassroom(String classroom) { this.classroom = classroom; }

    public Course getPrerequisiteCourse() { return prerequisiteCourse; }
    public void setPrerequisiteCourse(Course prerequisiteCourse) { this.prerequisiteCourse = prerequisiteCourse; }

    public Boolean getIsActive() { return isActive; }
    public void setIsActive(Boolean isActive) { this.isActive = isActive; }

    public PopularityLevel getPopularityLevel() { return popularityLevel; }
    public void setPopularityLevel(PopularityLevel popularityLevel) { this.popularityLevel = popularityLevel; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public List<Cart> getCartItems() { return cartItems; }
    public void setCartItems(List<Cart> cartItems) { this.cartItems = cartItems; }

    public List<Enrollment> getEnrollments() { return enrollments; }
    public void setEnrollments(List<Enrollment> enrollments) { this.enrollments = enrollments; }

    public List<Course> getDependentCourses() { return dependentCourses; }
    public void setDependentCourses(List<Course> dependentCourses) { this.dependentCourses = dependentCourses; }

    public Long getVersion() { return version; }
    public void setVersion(Long version) { this.version = version; }
}