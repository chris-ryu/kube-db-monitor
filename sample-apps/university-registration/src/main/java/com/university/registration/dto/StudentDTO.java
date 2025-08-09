package com.university.registration.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.university.registration.entity.Student;

import java.time.LocalDateTime;

public class StudentDTO {

    private String studentId;
    private String name;
    private String departmentName;
    private Integer grade;
    private Integer totalCredits;
    private Integer maxCredits;
    private Integer currentSemesterCredits;
    
    @JsonIgnore  // 비밀번호는 응답에 포함하지 않음
    private String password;
    
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createdAt;

    // 인증용 DTO
    public static class LoginRequestDTO {
        private String studentId;
        private String password;

        public LoginRequestDTO() {}

        public LoginRequestDTO(String studentId, String password) {
            this.studentId = studentId;
            this.password = password;
        }

        public String getStudentId() { return studentId; }
        public void setStudentId(String studentId) { this.studentId = studentId; }

        public String getPassword() { return password; }
        public void setPassword(String password) { this.password = password; }
    }

    // 프로필 응답용 DTO
    public static class StudentProfileDTO {
        private String studentId;
        private String name;
        private String departmentName;
        private Integer grade;
        private Integer totalCredits;
        private Integer maxCredits;
        private Integer currentSemesterCredits;
        private Integer remainingCredits;
        
        public StudentProfileDTO() {}

        public StudentProfileDTO(Student student) {
            this.studentId = student.getStudentId();
            this.name = student.getName();
            this.departmentName = student.getDepartment() != null ? 
                student.getDepartment().getDepartmentName() : null;
            this.grade = student.getGrade();
            this.totalCredits = student.getTotalCredits();
            this.maxCredits = student.getMaxCredits();
            this.remainingCredits = maxCredits - (currentSemesterCredits != null ? currentSemesterCredits : 0);
        }

        // Getters and Setters
        public String getStudentId() { return studentId; }
        public void setStudentId(String studentId) { this.studentId = studentId; }

        public String getName() { return name; }
        public void setName(String name) { this.name = name; }

        public String getDepartmentName() { return departmentName; }
        public void setDepartmentName(String departmentName) { this.departmentName = departmentName; }

        public Integer getGrade() { return grade; }
        public void setGrade(Integer grade) { this.grade = grade; }

        public Integer getTotalCredits() { return totalCredits; }
        public void setTotalCredits(Integer totalCredits) { this.totalCredits = totalCredits; }

        public Integer getMaxCredits() { return maxCredits; }
        public void setMaxCredits(Integer maxCredits) { this.maxCredits = maxCredits; }

        public Integer getCurrentSemesterCredits() { return currentSemesterCredits; }
        public void setCurrentSemesterCredits(Integer currentSemesterCredits) { 
            this.currentSemesterCredits = currentSemesterCredits;
            this.remainingCredits = maxCredits - (currentSemesterCredits != null ? currentSemesterCredits : 0);
        }

        public Integer getRemainingCredits() { return remainingCredits; }
        public void setRemainingCredits(Integer remainingCredits) { this.remainingCredits = remainingCredits; }
    }

    // Constructors
    public StudentDTO() {}

    public StudentDTO(Student student) {
        this.studentId = student.getStudentId();
        this.name = student.getName();
        this.departmentName = student.getDepartment() != null ? 
            student.getDepartment().getDepartmentName() : null;
        this.grade = student.getGrade();
        this.totalCredits = student.getTotalCredits();
        this.maxCredits = student.getMaxCredits();
        this.createdAt = student.getCreatedAt();
    }

    // Getters and Setters
    public String getStudentId() { return studentId; }
    public void setStudentId(String studentId) { this.studentId = studentId; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getDepartmentName() { return departmentName; }
    public void setDepartmentName(String departmentName) { this.departmentName = departmentName; }

    public Integer getGrade() { return grade; }
    public void setGrade(Integer grade) { this.grade = grade; }

    public Integer getTotalCredits() { return totalCredits; }
    public void setTotalCredits(Integer totalCredits) { this.totalCredits = totalCredits; }

    public Integer getMaxCredits() { return maxCredits; }
    public void setMaxCredits(Integer maxCredits) { this.maxCredits = maxCredits; }

    public Integer getCurrentSemesterCredits() { return currentSemesterCredits; }
    public void setCurrentSemesterCredits(Integer currentSemesterCredits) { this.currentSemesterCredits = currentSemesterCredits; }

    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}