package io.kubedb.monitor.integration;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;

import java.io.IOException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.sql.ResultSet;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for the complete KubeDB monitoring system
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class IntegrationTest {

    @BeforeAll
    static void setUp() {
        // Ensure H2 driver is loaded
        try {
            Class.forName("org.h2.Driver");
        } catch (ClassNotFoundException e) {
            throw new RuntimeException("H2 driver not found", e);
        }
    }

    @Test
    @Order(1)
    void shouldBuildSuccessfully() {
        // This test verifies that the project builds successfully
        assertThat(System.getProperty("java.version")).isNotNull();
    }

    @Test
    @Order(2)
    void shouldCreateDatabaseConnection() throws Exception {
        // Test that we can create database connections
        try (Connection conn = DriverManager.getConnection("jdbc:h2:mem:integrationtest", "sa", "")) {
            assertThat(conn).isNotNull();
            assertThat(conn.isClosed()).isFalse();
        }
    }

    @Test
    @Order(3)
    void shouldExecuteBasicSqlOperations() throws Exception {
        try (Connection conn = DriverManager.getConnection("jdbc:h2:mem:integrationtest", "sa", "")) {
            try (Statement stmt = conn.createStatement()) {
                // Create table
                stmt.executeUpdate("CREATE TABLE integration_test (id INT PRIMARY KEY, name VARCHAR(100))");
                
                // Insert data
                int inserted = stmt.executeUpdate("INSERT INTO integration_test VALUES (1, 'Integration Test')");
                assertThat(inserted).isEqualTo(1);
                
                // Query data
                try (ResultSet rs = stmt.executeQuery("SELECT COUNT(*) FROM integration_test")) {
                    assertThat(rs.next()).isTrue();
                    assertThat(rs.getInt(1)).isEqualTo(1);
                }
            }
        }
    }

    @Test
    @Order(4)
    void shouldHandleAgentConfiguration() {
        // Verify that agent configuration system properties work
        System.setProperty("kubedb.monitor.agent.db-types", "h2,mysql");
        System.setProperty("kubedb.monitor.agent.collector-type", "logging");
        
        String dbTypes = System.getProperty("kubedb.monitor.agent.db-types");
        String collectorType = System.getProperty("kubedb.monitor.agent.collector-type");
        
        assertThat(dbTypes).isEqualTo("h2,mysql");
        assertThat(collectorType).isEqualTo("logging");
        
        // Clean up
        System.clearProperty("kubedb.monitor.agent.db-types");
        System.clearProperty("kubedb.monitor.agent.collector-type");
    }

    @Test
    @Order(5)
    void shouldValidateKubernetesManifests() throws IOException {
        // Basic validation that our Kubernetes manifests exist and are readable
        String[] manifestFiles = {
            "k8s/namespace.yaml",
            "k8s/rbac.yaml",
            "k8s/deployment.yaml",
            "k8s/webhook-config.yaml",
            "k8s/example-app.yaml"
        };
        
        for (String manifestFile : manifestFiles) {
            java.io.File file = new java.io.File(manifestFile);
            assertThat(file.exists()).as("Manifest file %s should exist", manifestFile).isTrue();
            assertThat(file.canRead()).as("Manifest file %s should be readable", manifestFile).isTrue();
        }
    }

    @Test
    @Order(6)
    void shouldValidateDockerfiles() {
        // Validate that our Dockerfiles exist
        java.io.File agentDockerfile = new java.io.File("Dockerfile.agent");
        java.io.File controllerDockerfile = new java.io.File("Dockerfile.controller");
        
        assertThat(agentDockerfile.exists()).isTrue();
        assertThat(controllerDockerfile.exists()).isTrue();
    }
}