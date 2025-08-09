package com.university.registration.dto;

import java.util.List;
import java.util.ArrayList;

public class EnrollmentResponseDTO {

    private boolean success;
    private String message;
    private List<EnrollmentResultDTO> results;
    private Integer totalCredits;
    private Integer successCount;
    private Integer failCount;

    public static class EnrollmentResultDTO {
        private String courseId;
        private String courseName;
        private String status;
        private String message;

        public EnrollmentResultDTO() {}

        public EnrollmentResultDTO(String courseId, String courseName, String status, String message) {
            this.courseId = courseId;
            this.courseName = courseName;
            this.status = status;
            this.message = message;
        }

        // Getters and Setters
        public String getCourseId() { return courseId; }
        public void setCourseId(String courseId) { this.courseId = courseId; }

        public String getCourseName() { return courseName; }
        public void setCourseName(String courseName) { this.courseName = courseName; }

        public String getStatus() { return status; }
        public void setStatus(String status) { this.status = status; }

        public String getMessage() { return message; }
        public void setMessage(String message) { this.message = message; }
    }

    // Constructors
    public EnrollmentResponseDTO() {
        this.results = new ArrayList<>();
    }

    public EnrollmentResponseDTO(boolean success, String message) {
        this.success = success;
        this.message = message;
        this.results = new ArrayList<>();
    }

    // Business methods
    public void addResult(String courseId, String courseName, String status, String message) {
        this.results.add(new EnrollmentResultDTO(courseId, courseName, status, message));
    }

    public void calculateSummary() {
        this.successCount = (int) results.stream().filter(r -> "SUCCESS".equals(r.getStatus())).count();
        this.failCount = results.size() - successCount;
        this.success = successCount > 0;
    }

    // Getters and Setters
    public boolean isSuccess() { return success; }
    public void setSuccess(boolean success) { this.success = success; }

    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }

    public List<EnrollmentResultDTO> getResults() { return results; }
    public void setResults(List<EnrollmentResultDTO> results) { this.results = results; }

    public Integer getTotalCredits() { return totalCredits; }
    public void setTotalCredits(Integer totalCredits) { this.totalCredits = totalCredits; }

    public Integer getSuccessCount() { return successCount; }
    public void setSuccessCount(Integer successCount) { this.successCount = successCount; }

    public Integer getFailCount() { return failCount; }
    public void setFailCount(Integer failCount) { this.failCount = failCount; }
}