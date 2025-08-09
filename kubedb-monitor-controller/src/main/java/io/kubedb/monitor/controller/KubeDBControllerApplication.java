package io.kubedb.monitor.controller;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Main application class for KubeDB Monitor Controller
 * This is the Kubernetes Admission Controller that injects monitoring agents
 */
@SpringBootApplication
public class KubeDBControllerApplication {

    public static void main(String[] args) {
        SpringApplication.run(KubeDBControllerApplication.class, args);
    }
}