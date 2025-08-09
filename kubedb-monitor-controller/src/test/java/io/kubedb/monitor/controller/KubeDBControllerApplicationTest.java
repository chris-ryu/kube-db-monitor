package io.kubedb.monitor.controller;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

@SpringBootTest
@TestPropertySource(properties = {
    "server.ssl.enabled=false",
    "server.port=0"
})
class KubeDBControllerApplicationTest {

    @Test
    void contextLoads() {
        // This test ensures that the Spring Boot application context loads successfully
        // If this test passes, it means all beans are properly configured and the application starts
    }
}