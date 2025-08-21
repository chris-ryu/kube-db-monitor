package com.university.registration.config;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * PostgreSQL TestContainers 설정
 * 실제 PostgreSQL 환경에서 JDBC 호환성 테스트를 위한 설정
 */
@TestConfiguration
public class PostgreSQLTestConfiguration {

    @Bean
    @ServiceConnection
    static PostgreSQLContainer<?> postgreSQLContainer() {
        return new PostgreSQLContainer<>("postgres:15-alpine")
                .withDatabaseName("university_test")
                .withUsername("test_user")
                .withPassword("test_password")
                .withInitScript("test-data.sql");
    }
}