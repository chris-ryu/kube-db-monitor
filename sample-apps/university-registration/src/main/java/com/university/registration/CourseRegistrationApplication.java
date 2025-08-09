package com.university.registration;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;

@SpringBootApplication
@EnableCaching
public class CourseRegistrationApplication {

    public static void main(String[] args) {
        SpringApplication.run(CourseRegistrationApplication.class, args);
    }
}