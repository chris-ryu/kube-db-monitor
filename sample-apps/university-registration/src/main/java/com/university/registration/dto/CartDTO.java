package com.university.registration.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.university.registration.entity.Cart;

import java.time.LocalDateTime;
import java.util.List;

public class CartDTO {

    private Long cartId;
    private String studentId;
    private CourseDTO course;
    
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime addedAt;

    // Constructors
    public CartDTO() {}

    public CartDTO(Cart cart) {
        this.cartId = cart.getId();
        this.studentId = cart.getStudent().getStudentId();
        this.course = new CourseDTO(cart.getCourse());
        this.addedAt = cart.getAddedAt();
    }

    // Static factory methods for response DTOs
    public static class CartSummaryDTO {
        private List<CartDTO> cartItems;
        private Integer totalItems;
        private Integer totalCredits;
        private List<String> timeConflicts;
        private Boolean canEnrollAll;

        // Constructors
        public CartSummaryDTO() {}

        public CartSummaryDTO(List<CartDTO> cartItems, Integer totalCredits) {
            this.cartItems = cartItems;
            this.totalItems = cartItems.size();
            this.totalCredits = totalCredits;
        }

        // Getters and Setters
        public List<CartDTO> getCartItems() { return cartItems; }
        public void setCartItems(List<CartDTO> cartItems) { this.cartItems = cartItems; }

        public Integer getTotalItems() { return totalItems; }
        public void setTotalItems(Integer totalItems) { this.totalItems = totalItems; }

        public Integer getTotalCredits() { return totalCredits; }
        public void setTotalCredits(Integer totalCredits) { this.totalCredits = totalCredits; }

        public List<String> getTimeConflicts() { return timeConflicts; }
        public void setTimeConflicts(List<String> timeConflicts) { this.timeConflicts = timeConflicts; }

        public Boolean getCanEnrollAll() { return canEnrollAll; }
        public void setCanEnrollAll(Boolean canEnrollAll) { this.canEnrollAll = canEnrollAll; }
    }

    public static class AddToCartRequestDTO {
        private String courseId;

        public AddToCartRequestDTO() {}

        public AddToCartRequestDTO(String courseId) {
            this.courseId = courseId;
        }

        public String getCourseId() { return courseId; }
        public void setCourseId(String courseId) { this.courseId = courseId; }
    }

    // Getters and Setters
    public Long getCartId() { return cartId; }
    public void setCartId(Long cartId) { this.cartId = cartId; }

    public String getStudentId() { return studentId; }
    public void setStudentId(String studentId) { this.studentId = studentId; }

    public CourseDTO getCourse() { return course; }
    public void setCourse(CourseDTO course) { this.course = course; }

    public LocalDateTime getAddedAt() { return addedAt; }
    public void setAddedAt(LocalDateTime addedAt) { this.addedAt = addedAt; }
}