package io.kubedb.monitor.controller.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Configuration properties for KubeDB Monitor Controller
 */
@Component
@ConfigurationProperties(prefix = "kubedb.monitor.controller")
public class ControllerConfig {
    
    private String agentImage = "kubedb-monitor/agent:latest";
    private String agentVolumePath = "/opt/kubedb-agent";
    private WebhookConfig webhook = new WebhookConfig();
    
    public String getAgentImage() {
        return agentImage;
    }
    
    public void setAgentImage(String agentImage) {
        this.agentImage = agentImage;
    }
    
    public String getAgentVolumePath() {
        return agentVolumePath;
    }
    
    public void setAgentVolumePath(String agentVolumePath) {
        this.agentVolumePath = agentVolumePath;
    }
    
    public WebhookConfig getWebhook() {
        return webhook;
    }
    
    public void setWebhook(WebhookConfig webhook) {
        this.webhook = webhook;
    }
    
    public static class WebhookConfig {
        private int timeoutSeconds = 10;
        private String failurePolicy = "Fail";
        
        public int getTimeoutSeconds() {
            return timeoutSeconds;
        }
        
        public void setTimeoutSeconds(int timeoutSeconds) {
            this.timeoutSeconds = timeoutSeconds;
        }
        
        public String getFailurePolicy() {
            return failurePolicy;
        }
        
        public void setFailurePolicy(String failurePolicy) {
            this.failurePolicy = failurePolicy;
        }
    }
}