package io.kubedb.monitor.controller.webhook;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Kubernetes Mutating Admission Controller
 * Intercepts Pod creation and modifies them to inject monitoring agents
 */
@RestController
@RequestMapping("/admission")
public class MutatingAdmissionController {
    
    private static final Logger logger = LoggerFactory.getLogger(MutatingAdmissionController.class);
    
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final AdmissionWebhookService webhookService;
    
    public MutatingAdmissionController(AdmissionWebhookService webhookService) {
        this.webhookService = webhookService;
    }
    
    @PostMapping("/mutate")
    public ResponseEntity<JsonNode> mutate(@RequestBody JsonNode admissionReview) {
        logger.info("Received admission review request");
        
        try {
            JsonNode response = webhookService.processMutatingAdmission(admissionReview);
            logger.info("Successfully processed admission review");
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            logger.error("Error processing admission review", e);
            
            // Return a response that allows the pod to be created without modification
            JsonNode errorResponse = webhookService.createErrorResponse(admissionReview, e.getMessage());
            return ResponseEntity.ok(errorResponse);
        }
    }
    
    @PostMapping("/validate")
    public ResponseEntity<JsonNode> validate(@RequestBody JsonNode admissionReview) {
        logger.info("Received validation admission review request");
        
        try {
            JsonNode response = webhookService.processValidatingAdmission(admissionReview);
            logger.info("Successfully processed validation admission review");
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            logger.error("Error processing validation admission review", e);
            
            // Return a response that allows the pod to be created
            JsonNode errorResponse = webhookService.createValidationErrorResponse(admissionReview, e.getMessage());
            return ResponseEntity.ok(errorResponse);
        }
    }
}