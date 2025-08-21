package com.university.registration;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 간단한 TestContainers PostgreSQL 연결 테스트
 */
@SpringBootTest
@Testcontainers 
@ActiveProfiles("test")
@DisplayName("간단한 PostgreSQL TestContainers 테스트")
class SimplePostgreSQLTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15-alpine")
            .withDatabaseName("test_db")
            .withUsername("test")
            .withPassword("test");

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
        registry.add("spring.jpa.database-platform", () -> "org.hibernate.dialect.PostgreSQLDialect");
    }

    @Autowired
    private DataSource dataSource;

    @Test
    @DisplayName("PostgreSQL 컨테이너 정상 시작 확인")
    void testPostgreSQLContainer() {
        assertTrue(postgres.isRunning());
        assertTrue(postgres.isCreated());
        System.out.println("🐘 PostgreSQL 컨테이너 정상 동작: " + postgres.getJdbcUrl());
    }

    @Test
    @DisplayName("PostgreSQL JDBC 연결 및 쿼리 테스트")
    void testPostgreSQLConnection() throws SQLException {
        try (Connection connection = dataSource.getConnection()) {
            assertTrue(connection.isValid(1));
            
            try (Statement statement = connection.createStatement()) {
                // 간단한 PostgreSQL 쿼리 테스트
                ResultSet rs = statement.executeQuery("SELECT version()");
                assertTrue(rs.next());
                String version = rs.getString(1);
                System.out.println("🔍 PostgreSQL 버전: " + version);
                assertTrue(version.contains("PostgreSQL"));
            }
        }
    }
}