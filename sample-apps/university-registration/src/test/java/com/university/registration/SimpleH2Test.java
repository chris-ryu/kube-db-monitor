package com.university.registration;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * H2 인메모리 DB 기본 연결 테스트
 */
@SpringBootTest
@ActiveProfiles("test")
@DisplayName("H2 인메모리 DB 연결 테스트")
class SimpleH2Test {

    @Autowired
    private DataSource dataSource;

    @Test
    @DisplayName("데이터베이스 연결 확인")
    void testDatabaseConnection() throws SQLException {
        try (Connection connection = dataSource.getConnection()) {
            assertTrue(connection.isValid(1));
            
            try (Statement statement = connection.createStatement()) {
                // 간단한 쿼리로 연결 확인
                ResultSet rs = statement.executeQuery("SELECT 1");
                assertTrue(rs.next());
                int result = rs.getInt(1);
                System.out.println("🔍 데이터베이스 연결 성공: " + result);
                assertTrue(result == 1);
                
                // 데이터베이스 정보 확인
                String dbProductName = connection.getMetaData().getDatabaseProductName();
                String dbProductVersion = connection.getMetaData().getDatabaseProductVersion();
                String jdbcUrl = connection.getMetaData().getURL();
                
                System.out.println("📊 DB 제품: " + dbProductName);
                System.out.println("📈 DB 버전: " + dbProductVersion);  
                System.out.println("🔗 JDBC URL: " + jdbcUrl);
            }
        }
    }
}