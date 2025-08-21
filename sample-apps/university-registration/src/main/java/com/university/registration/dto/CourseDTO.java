package com.university.registration.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.university.registration.entity.Course;

import java.time.LocalDateTime;

public class CourseDTO {
    
    private String courseId;
    private String courseName;
    private String professor;
    private Integer credits;
    private Integer capacity;
    private Integer enrolledCount;
    private String departmentName;
    private Long departmentId;
    private String dayTime;
    private String classroom;
    private String prerequisiteCourseId;
    private String prerequisiteCourseName;
    private Boolean isActive;
    private String popularityLevel;
    private Boolean isAvailable;
    private Integer availableSlots;
    private Double enrollmentRate;
    
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createdAt;

    // Constructors
    public CourseDTO() {}

    public CourseDTO(Course course) {
        this.courseId = course.getCourseId();
        this.courseName = course.getCourseName();
        this.professor = course.getProfessor();
        this.credits = course.getCredits();
        this.capacity = course.getCapacity();
        this.enrolledCount = course.getEnrolledCount();
        this.departmentName = course.getDepartment() != null ? course.getDepartment().getDepartmentName() : null;
        this.departmentId = course.getDepartment() != null ? course.getDepartment().getId() : null;
        this.dayTime = course.getDayTime();
        this.classroom = course.getClassroom();
        this.prerequisiteCourseId = course.getPrerequisiteCourse() != null ? course.getPrerequisiteCourse().getCourseId() : null;
        this.prerequisiteCourseName = course.getPrerequisiteCourse() != null ? course.getPrerequisiteCourse().getCourseName() : null;
        this.isActive = course.getIsActive();
        this.popularityLevel = course.getPopularityLevel() != null ? course.getPopularityLevel().name() : null;
        this.isAvailable = course.hasAvailableSlots();
        this.availableSlots = course.getAvailableSlots();
        this.enrollmentRate = course.getEnrollmentRate();
        this.createdAt = course.getCreatedAt();
    }

    // Getters and Setters
    public String getCourseId() { return courseId; }
    public void setCourseId(String courseId) { this.courseId = courseId; }

    public String getCourseName() { return courseName; }
    public void setCourseName(String courseName) { this.courseName = courseName; }

    @JsonProperty("professorName")
    public String getProfessor() { return professor; }
    public void setProfessor(String professor) { this.professor = professor; }

    public Integer getCredits() { return credits; }
    public void setCredits(Integer credits) { this.credits = credits; }

    @JsonProperty("maxStudents")
    public Integer getCapacity() { return capacity; }
    public void setCapacity(Integer capacity) { this.capacity = capacity; }

    @JsonProperty("currentEnrollment")
    public Integer getEnrolledCount() { return enrolledCount; }
    public void setEnrolledCount(Integer enrolledCount) { this.enrolledCount = enrolledCount; }

    public String getDepartmentName() { return departmentName; }
    public void setDepartmentName(String departmentName) { this.departmentName = departmentName; }

    @JsonProperty("schedule")
    public String getDayTime() { return dayTime; }
    public void setDayTime(String dayTime) { this.dayTime = dayTime; }

    public String getClassroom() { return classroom; }
    public void setClassroom(String classroom) { this.classroom = classroom; }

    public String getPrerequisiteCourseId() { return prerequisiteCourseId; }
    public void setPrerequisiteCourseId(String prerequisiteCourseId) { this.prerequisiteCourseId = prerequisiteCourseId; }

    public String getPrerequisiteCourseName() { return prerequisiteCourseName; }
    public void setPrerequisiteCourseName(String prerequisiteCourseName) { this.prerequisiteCourseName = prerequisiteCourseName; }

    public Boolean getIsActive() { return isActive; }
    public void setIsActive(Boolean isActive) { this.isActive = isActive; }

    public String getPopularityLevel() { return popularityLevel; }
    public void setPopularityLevel(String popularityLevel) { this.popularityLevel = popularityLevel; }

    public Boolean getIsAvailable() { return isAvailable; }
    public void setIsAvailable(Boolean isAvailable) { this.isAvailable = isAvailable; }

    public Integer getAvailableSlots() { return availableSlots; }
    public void setAvailableSlots(Integer availableSlots) { this.availableSlots = availableSlots; }

    public Double getEnrollmentRate() { return enrollmentRate; }
    public void setEnrollmentRate(Double enrollmentRate) { this.enrollmentRate = enrollmentRate; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public Long getDepartmentId() { return departmentId; }
    public void setDepartmentId(Long departmentId) { this.departmentId = departmentId; }

    // 프론트엔드 호환성을 위한 중첩 객체
    @JsonProperty("department")
    public DepartmentDTO getDepartment() {
        return new DepartmentDTO(departmentId, departmentName);
    }

    // 테스트 호환성을 위한 편의 메소드들
    public Integer getMaxCapacity() { return capacity; }
    public Integer getCurrentEnrollment() { return enrolledCount; }
    public String getPrerequisites() { return prerequisiteCourseName; }
}