package io.kubedb.monitor.controller.webhook;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

class MutatingAdmissionControllerTest {

    @Mock
    private AdmissionWebhookService webhookService;

    private MutatingAdmissionController controller;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        controller = new MutatingAdmissionController(webhookService);
        objectMapper = new ObjectMapper();
    }

    @Test
    void shouldHandleMutatingAdmissionSuccessfully() {
        // Given
        JsonNode admissionReview = createTestAdmissionReview();
        JsonNode expectedResponse = createTestResponse(true);
        
        when(webhookService.processMutatingAdmission(any(JsonNode.class)))
                .thenReturn(expectedResponse);

        // When
        ResponseEntity<JsonNode> response = controller.mutate(admissionReview);

        // Then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().get("response").get("allowed").asBoolean()).isTrue();
    }

    @Test
    void shouldHandleMutatingAdmissionError() {
        // Given
        JsonNode admissionReview = createTestAdmissionReview();
        JsonNode errorResponse = createTestResponse(true); // Allow on error
        
        when(webhookService.processMutatingAdmission(any(JsonNode.class)))
                .thenThrow(new RuntimeException("Test error"));
        when(webhookService.createErrorResponse(any(JsonNode.class), any(String.class)))
                .thenReturn(errorResponse);

        // When
        ResponseEntity<JsonNode> response = controller.mutate(admissionReview);

        // Then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().get("response").get("allowed").asBoolean()).isTrue();
    }

    @Test
    void shouldHandleValidatingAdmissionSuccessfully() {
        // Given
        JsonNode admissionReview = createTestAdmissionReview();
        JsonNode expectedResponse = createTestResponse(true);
        
        when(webhookService.processValidatingAdmission(any(JsonNode.class)))
                .thenReturn(expectedResponse);

        // When
        ResponseEntity<JsonNode> response = controller.validate(admissionReview);

        // Then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().get("response").get("allowed").asBoolean()).isTrue();
    }

    @Test
    void shouldHandleValidatingAdmissionError() {
        // Given
        JsonNode admissionReview = createTestAdmissionReview();
        JsonNode errorResponse = createTestResponse(true); // Allow on error
        
        when(webhookService.processValidatingAdmission(any(JsonNode.class)))
                .thenThrow(new RuntimeException("Validation error"));
        when(webhookService.createValidationErrorResponse(any(JsonNode.class), any(String.class)))
                .thenReturn(errorResponse);

        // When
        ResponseEntity<JsonNode> response = controller.validate(admissionReview);

        // Then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().get("response").get("allowed").asBoolean()).isTrue();
    }

    @Test
    void shouldReturnProperJsonStructure() {
        // Given
        JsonNode admissionReview = createTestAdmissionReview();
        JsonNode expectedResponse = createTestResponse(true);
        
        when(webhookService.processMutatingAdmission(any(JsonNode.class)))
                .thenReturn(expectedResponse);

        // When
        ResponseEntity<JsonNode> response = controller.mutate(admissionReview);

        // Then
        JsonNode body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.has("apiVersion")).isTrue();
        assertThat(body.has("kind")).isTrue();
        assertThat(body.has("response")).isTrue();
        assertThat(body.get("apiVersion").asText()).isEqualTo("admission.k8s.io/v1");
        assertThat(body.get("kind").asText()).isEqualTo("AdmissionReview");
    }

    private JsonNode createTestAdmissionReview() {
        ObjectNode admissionReview = objectMapper.createObjectNode();
        admissionReview.put("apiVersion", "admission.k8s.io/v1");
        admissionReview.put("kind", "AdmissionReview");
        
        ObjectNode request = objectMapper.createObjectNode();
        request.put("uid", "test-uid-123");
        
        ObjectNode kind = objectMapper.createObjectNode();
        kind.put("group", "");
        kind.put("version", "v1");
        kind.put("kind", "Pod");
        request.set("kind", kind);
        
        ObjectNode resource = objectMapper.createObjectNode();
        resource.put("group", "");
        resource.put("version", "v1");
        resource.put("resource", "pods");
        request.set("resource", resource);
        
        request.put("operation", "CREATE");
        
        // Create a simple pod object
        ObjectNode pod = objectMapper.createObjectNode();
        pod.put("apiVersion", "v1");
        pod.put("kind", "Pod");
        
        ObjectNode metadata = objectMapper.createObjectNode();
        metadata.put("name", "test-pod");
        pod.set("metadata", metadata);
        
        request.set("object", pod);
        admissionReview.set("request", request);
        
        return admissionReview;
    }

    private JsonNode createTestResponse(boolean allowed) {
        ObjectNode response = objectMapper.createObjectNode();
        response.put("apiVersion", "admission.k8s.io/v1");
        response.put("kind", "AdmissionReview");
        
        ObjectNode responseObj = objectMapper.createObjectNode();
        responseObj.put("uid", "test-uid-123");
        responseObj.put("allowed", allowed);
        
        response.set("response", responseObj);
        return response;
    }
}