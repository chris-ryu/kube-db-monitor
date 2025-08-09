package com.university.registration.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.List;

public class EnrollmentRequestDTO {

    @NotEmpty(message = "수강신청할 과목 목록은 비어있을 수 없습니다")
    @Size(max = 10, message = "한 번에 최대 10개 과목까지 신청 가능합니다")
    private List<String> courseIds;

    // 장바구니에서 전체 신청 여부
    private boolean fromCart = false;

    public EnrollmentRequestDTO() {}

    public EnrollmentRequestDTO(List<String> courseIds) {
        this.courseIds = courseIds;
    }

    public EnrollmentRequestDTO(List<String> courseIds, boolean fromCart) {
        this.courseIds = courseIds;
        this.fromCart = fromCart;
    }

    // Getters and Setters
    public List<String> getCourseIds() { return courseIds; }
    public void setCourseIds(List<String> courseIds) { this.courseIds = courseIds; }

    public boolean isFromCart() { return fromCart; }
    public void setFromCart(boolean fromCart) { this.fromCart = fromCart; }
}