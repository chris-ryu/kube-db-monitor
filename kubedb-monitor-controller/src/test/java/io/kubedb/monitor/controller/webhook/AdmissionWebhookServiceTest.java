package io.kubedb.monitor.controller.webhook;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AdmissionWebhookServiceTest {

    private AdmissionWebhookService webhookService;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        webhookService = new AdmissionWebhookService();
        objectMapper = new ObjectMapper();
    }

    @Test
    void shouldAllowPodWithoutMonitoringAnnotation() {
        // Given
        JsonNode admissionReview = createAdmissionReview(createPodWithoutAnnotations());

        // When
        JsonNode response = webhookService.processMutatingAdmission(admissionReview);

        // Then
        assertThat(response.get("response").get("allowed").asBoolean()).isTrue();
        assertThat(response.get("response").has("patch")).isFalse();
    }

    @Test
    void shouldAllowPodWithMonitoringDisabled() {
        // Given
        JsonNode pod = createPodWithAnnotations("kubedb.monitor/enable", "false");
        JsonNode admissionReview = createAdmissionReview(pod);

        // When
        JsonNode response = webhookService.processMutatingAdmission(admissionReview);

        // Then
        assertThat(response.get("response").get("allowed").asBoolean()).isTrue();
        assertThat(response.get("response").has("patch")).isFalse();
    }

    @Test
    void shouldModifyPodWithMonitoringEnabled() {
        // Given
        JsonNode pod = createPodWithAnnotations("kubedb.monitor/enable", "true");
        JsonNode admissionReview = createAdmissionReview(pod);

        // When
        JsonNode response = webhookService.processMutatingAdmission(admissionReview);

        // Then
        assertThat(response.get("response").get("allowed").asBoolean()).isTrue();
        assertThat(response.get("response").has("patch")).isTrue();
        assertThat(response.get("response").get("patchType").asText()).isEqualTo("JSONPatch");
    }

    @Test
    void shouldModifyPodWithDbTypesAnnotation() {
        // Given
        ObjectNode pod = createBasicPod();
        ObjectNode annotations = objectMapper.createObjectNode();
        annotations.put("kubedb.monitor/enable", "true");
        annotations.put("kubedb.monitor/db-types", "mysql,postgresql");
        ((ObjectNode)pod.get("metadata")).set("annotations", annotations);
        
        JsonNode admissionReview = createAdmissionReview(pod);

        // When
        JsonNode response = webhookService.processMutatingAdmission(admissionReview);

        // Then
        assertThat(response.get("response").get("allowed").asBoolean()).isTrue();
        assertThat(response.get("response").has("patch")).isTrue();
    }

    @Test
    void shouldProcessValidatingAdmissionSuccessfully() {
        // Given
        JsonNode pod = createPodWithAnnotations("kubedb.monitor/enable", "true");
        JsonNode admissionReview = createAdmissionReview(pod);

        // When
        JsonNode response = webhookService.processValidatingAdmission(admissionReview);

        // Then
        assertThat(response.get("response").get("allowed").asBoolean()).isTrue();
    }

    @Test
    void shouldAllowPodWithoutContainers() {
        // Given
        ObjectNode pod = createBasicPod();
        ((ObjectNode)pod.get("spec")).remove("containers");
        
        JsonNode admissionReview = createAdmissionReview(pod);

        // When
        JsonNode response = webhookService.processValidatingAdmission(admissionReview);

        // Then
        assertThat(response.get("response").get("allowed").asBoolean()).isFalse();
        assertThat(response.get("response").get("status").get("message").asText())
                .contains("Pod must have at least one container");
    }

    @Test
    void shouldCreateErrorResponseGracefully() {
        // Given
        JsonNode admissionReview = createAdmissionReview(createBasicPod());
        String errorMessage = "Test error";

        // When
        JsonNode response = webhookService.createErrorResponse(admissionReview, errorMessage);

        // Then
        assertThat(response.get("response").get("allowed").asBoolean()).isTrue();
        assertThat(response.get("apiVersion").asText()).isEqualTo("admission.k8s.io/v1");
        assertThat(response.get("kind").asText()).isEqualTo("AdmissionReview");
    }

    @Test
    void shouldCreateValidationErrorResponseGracefully() {
        // Given
        JsonNode admissionReview = createAdmissionReview(createBasicPod());
        String errorMessage = "Test validation error";

        // When
        JsonNode response = webhookService.createValidationErrorResponse(admissionReview, errorMessage);

        // Then
        assertThat(response.get("response").get("allowed").asBoolean()).isTrue();
    }

    @Test
    void shouldHandleMultipleAnnotations() {
        // Given
        ObjectNode pod = createBasicPod();
        ObjectNode annotations = objectMapper.createObjectNode();
        annotations.put("kubedb.monitor/enable", "true");
        annotations.put("kubedb.monitor/db-types", "mysql,postgresql");
        annotations.put("kubedb.monitor/sampling-rate", "0.5");
        annotations.put("kubedb.monitor/slow-query-threshold", "2000");
        ((ObjectNode)pod.get("metadata")).set("annotations", annotations);
        
        JsonNode admissionReview = createAdmissionReview(pod);

        // When
        JsonNode response = webhookService.processMutatingAdmission(admissionReview);

        // Then
        assertThat(response.get("response").get("allowed").asBoolean()).isTrue();
        assertThat(response.get("response").has("patch")).isTrue();
    }

    @Test
    void shouldDetectJavaContainer() {
        // Given - Pod with Java-related image
        ObjectNode pod = createBasicPod();
        ArrayNode containers = (ArrayNode) pod.get("spec").get("containers");
        ObjectNode container = (ObjectNode) containers.get(0);
        container.put("image", "openjdk:17-jre");
        
        ObjectNode annotations = objectMapper.createObjectNode();
        annotations.put("kubedb.monitor/enable", "true");
        ((ObjectNode)pod.get("metadata")).set("annotations", annotations);
        
        JsonNode admissionReview = createAdmissionReview(pod);

        // When
        JsonNode response = webhookService.processValidatingAdmission(admissionReview);

        // Then
        assertThat(response.get("response").get("allowed").asBoolean()).isTrue();
    }

    @Test
    void shouldDetectJavaContainerByEnvironmentVariable() {
        // Given - Pod with JAVA_HOME environment variable
        ObjectNode pod = createBasicPod();
        ArrayNode containers = (ArrayNode) pod.get("spec").get("containers");
        ObjectNode container = (ObjectNode) containers.get(0);
        
        ArrayNode env = objectMapper.createArrayNode();
        ObjectNode javaHomeEnv = objectMapper.createObjectNode();
        javaHomeEnv.put("name", "JAVA_HOME");
        javaHomeEnv.put("value", "/opt/java");
        env.add(javaHomeEnv);
        container.set("env", env);
        
        ObjectNode annotations = objectMapper.createObjectNode();
        annotations.put("kubedb.monitor/enable", "true");
        ((ObjectNode)pod.get("metadata")).set("annotations", annotations);
        
        JsonNode admissionReview = createAdmissionReview(pod);

        // When
        JsonNode response = webhookService.processValidatingAdmission(admissionReview);

        // Then
        assertThat(response.get("response").get("allowed").asBoolean()).isTrue();
    }

    private ObjectNode createBasicPod() {
        ObjectNode pod = objectMapper.createObjectNode();
        pod.put("apiVersion", "v1");
        pod.put("kind", "Pod");
        
        // Metadata
        ObjectNode metadata = objectMapper.createObjectNode();
        metadata.put("name", "test-pod");
        metadata.put("namespace", "default");
        pod.set("metadata", metadata);
        
        // Spec
        ObjectNode spec = objectMapper.createObjectNode();
        ArrayNode containers = objectMapper.createArrayNode();
        
        ObjectNode container = objectMapper.createObjectNode();
        container.put("name", "app");
        container.put("image", "nginx:latest");
        containers.add(container);
        
        spec.set("containers", containers);
        pod.set("spec", spec);
        
        return pod;
    }

    private ObjectNode createPodWithoutAnnotations() {
        return createBasicPod();
    }

    private ObjectNode createPodWithAnnotations(String key, String value) {
        ObjectNode pod = createBasicPod();
        ObjectNode annotations = objectMapper.createObjectNode();
        annotations.put(key, value);
        ((ObjectNode)pod.get("metadata")).set("annotations", annotations);
        return pod;
    }

    private JsonNode createAdmissionReview(JsonNode pod) {
        ObjectNode admissionReview = objectMapper.createObjectNode();
        admissionReview.put("apiVersion", "admission.k8s.io/v1");
        admissionReview.put("kind", "AdmissionReview");
        
        ObjectNode request = objectMapper.createObjectNode();
        request.put("uid", "test-uid-123");
        request.set("kind", objectMapper.createObjectNode().put("group", "").put("version", "v1").put("kind", "Pod"));
        request.set("resource", objectMapper.createObjectNode().put("group", "").put("version", "v1").put("resource", "pods"));
        request.put("operation", "CREATE");
        request.set("object", pod);
        
        admissionReview.set("request", request);
        return admissionReview;
    }
}