package io.kubedb.monitor.controller.webhook;

import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST Controller for handling Kubernetes Admission Webhook requests
 */
@RestController
public class AdmissionWebhookController {
    
    private static final Logger logger = LoggerFactory.getLogger(AdmissionWebhookController.class);
    
    @Autowired
    private AdmissionWebhookService webhookService;
    
    /**
     * Handle mutating admission webhook requests
     */
    @PostMapping(value = "/mutate", 
                 consumes = MediaType.APPLICATION_JSON_VALUE,
                 produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<JsonNode> mutate(@RequestBody JsonNode admissionReview) {
        try {
            logger.info("Received mutating admission webhook request");
            logger.debug("Admission review: {}", admissionReview);
            
            JsonNode response = webhookService.processMutatingAdmission(admissionReview);
            
            logger.debug("Responding with: {}", response);
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            logger.error("Error processing mutating admission webhook", e);
            JsonNode errorResponse = webhookService.createErrorResponse(admissionReview, e.getMessage());
            return ResponseEntity.ok(errorResponse);
        }
    }
    
    /**
     * Handle validating admission webhook requests
     */
    @PostMapping(value = "/validate",
                 consumes = MediaType.APPLICATION_JSON_VALUE,
                 produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<JsonNode> validate(@RequestBody JsonNode admissionReview) {
        try {
            logger.info("Received validating admission webhook request");
            logger.debug("Admission review: {}", admissionReview);
            
            JsonNode response = webhookService.processValidatingAdmission(admissionReview);
            
            logger.debug("Responding with: {}", response);
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            logger.error("Error processing validating admission webhook", e);
            JsonNode errorResponse = webhookService.createValidationErrorResponse(admissionReview, e.getMessage());
            return ResponseEntity.ok(errorResponse);
        }
    }
}