package com.university.registration.dto;

import com.university.registration.entity.Department;

/**
 * 학과 정보 DTO
 * 프론트엔드와의 호환성을 위해 생성
 */
public class DepartmentDTO {
    
    private Long id;
    private String name;
    private String code;

    // Constructors
    public DepartmentDTO() {}

    public DepartmentDTO(Long id, String name) {
        this.id = id;
        this.name = name;
    }

    public DepartmentDTO(Long id, String name, String code) {
        this.id = id;
        this.name = name;
        this.code = code;
    }

    public DepartmentDTO(Department department) {
        if (department != null) {
            this.id = department.getId();
            this.name = department.getDepartmentName();
            this.code = department.getCollege();
        }
    }

    // Getters and Setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }
}