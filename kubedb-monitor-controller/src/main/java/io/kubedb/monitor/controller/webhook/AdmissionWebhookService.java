package io.kubedb.monitor.controller.webhook;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Base64;

/**
 * Service for processing Kubernetes Admission Webhook requests
 * Handles both mutating and validating admission webhooks
 */
@Service
public class AdmissionWebhookService {
    
    private static final Logger logger = LoggerFactory.getLogger(AdmissionWebhookService.class);
    
    // Annotation keys
    private static final String ENABLE_ANNOTATION = "kubedb.monitor/enable";
    private static final String DB_TYPES_ANNOTATION = "kubedb.monitor/db-types";
    private static final String SAMPLING_RATE_ANNOTATION = "kubedb.monitor/sampling-rate";
    private static final String SLOW_QUERY_THRESHOLD_ANNOTATION = "kubedb.monitor/slow-query-threshold";
    
    // Agent configuration
    private static final String AGENT_IMAGE = "kubedb-monitor/agent:latest";
    private static final String AGENT_VOLUME_NAME = "kubedb-agent";
    private static final String AGENT_PATH = "/opt/kubedb-agent";
    
    private final ObjectMapper objectMapper = new ObjectMapper();
    
    /**
     * Process mutating admission webhook request
     */
    public JsonNode processMutatingAdmission(JsonNode admissionReview) {
        logger.debug("Processing mutating admission request");
        
        JsonNode request = admissionReview.get("request");
        JsonNode pod = request.get("object");
        
        // Check if monitoring should be enabled for this pod
        if (!shouldEnableMonitoring(pod)) {
            logger.debug("Monitoring not enabled for pod: {}", getPodName(pod));
            return createAllowedResponse(request, null);
        }
        
        logger.info("Enabling monitoring for pod: {}", getPodName(pod));
        
        // Create JSON patch to modify the pod
        ArrayNode patches = createMonitoringPatches(pod);
        
        return createAllowedResponse(request, patches);
    }
    
    /**
     * Process validating admission webhook request
     */
    public JsonNode processValidatingAdmission(JsonNode admissionReview) {
        logger.debug("Processing validating admission request");
        
        JsonNode request = admissionReview.get("request");
        JsonNode pod = request.get("object");
        
        // Perform validation checks
        String validationError = validatePodForMonitoring(pod);
        
        if (validationError != null) {
            logger.warn("Validation failed for pod {}: {}", getPodName(pod), validationError);
            return createDeniedResponse(request, validationError);
        }
        
        logger.debug("Validation passed for pod: {}", getPodName(pod));
        return createAllowedResponse(request, null);
    }
    
    /**
     * Create error response for mutating admission
     */
    public JsonNode createErrorResponse(JsonNode admissionReview, String errorMessage) {
        JsonNode request = admissionReview.get("request");
        logger.error("Creating error response: {}", errorMessage);
        
        // Allow the pod to be created without modification on error
        return createAllowedResponse(request, null);
    }
    
    /**
     * Create error response for validating admission
     */
    public JsonNode createValidationErrorResponse(JsonNode admissionReview, String errorMessage) {
        JsonNode request = admissionReview.get("request");
        logger.error("Creating validation error response: {}", errorMessage);
        
        // Allow the pod to be created on validation error
        return createAllowedResponse(request, null);
    }
    
    private boolean shouldEnableMonitoring(JsonNode pod) {
        JsonNode annotations = getAnnotations(pod);
        if (annotations == null) {
            return false;
        }
        
        JsonNode enableAnnotation = annotations.get(ENABLE_ANNOTATION);
        if (enableAnnotation == null) {
            return false;
        }
        
        return "true".equalsIgnoreCase(enableAnnotation.asText());
    }
    
    private JsonNode getAnnotations(JsonNode pod) {
        JsonNode metadata = pod.get("metadata");
        if (metadata == null) {
            return null;
        }
        
        return metadata.get("annotations");
    }
    
    private String getPodName(JsonNode pod) {
        JsonNode metadata = pod.get("metadata");
        if (metadata == null) {
            return "unknown";
        }
        
        JsonNode name = metadata.get("name");
        return name != null ? name.asText() : "unknown";
    }
    
    private ArrayNode createMonitoringPatches(JsonNode pod) {
        ArrayNode patches = objectMapper.createArrayNode();
        
        // Add init container for agent
        patches.add(createInitContainerPatch());
        
        // Add volume for agent
        patches.add(createVolumePatch());
        
        // Add environment variables to main containers
        patches.addAll(createEnvironmentVariablePatches(pod));
        
        return patches;
    }
    
    private ObjectNode createInitContainerPatch() {
        ObjectNode patch = objectMapper.createObjectNode();
        patch.put("op", "add");
        patch.put("path", "/spec/initContainers");
        
        ArrayNode initContainers = objectMapper.createArrayNode();
        ObjectNode initContainer = objectMapper.createObjectNode();
        
        initContainer.put("name", "kubedb-agent-init");
        initContainer.put("image", AGENT_IMAGE);
        
        ArrayNode command = objectMapper.createArrayNode();
        command.add("sh");
        command.add("-c");
        command.add("cp /opt/kubedb-monitor-agent.jar " + AGENT_PATH + "/");
        initContainer.set("command", command);
        
        ArrayNode volumeMounts = objectMapper.createArrayNode();
        ObjectNode volumeMount = objectMapper.createObjectNode();
        volumeMount.put("name", AGENT_VOLUME_NAME);
        volumeMount.put("mountPath", AGENT_PATH);
        volumeMounts.add(volumeMount);
        initContainer.set("volumeMounts", volumeMounts);
        
        initContainers.add(initContainer);
        patch.set("value", initContainers);
        
        return patch;
    }
    
    private ObjectNode createVolumePatch() {
        ObjectNode patch = objectMapper.createObjectNode();
        patch.put("op", "add");
        patch.put("path", "/spec/volumes");
        
        ArrayNode volumes = objectMapper.createArrayNode();
        ObjectNode volume = objectMapper.createObjectNode();
        
        volume.put("name", AGENT_VOLUME_NAME);
        ObjectNode emptyDir = objectMapper.createObjectNode();
        volume.set("emptyDir", emptyDir);
        
        volumes.add(volume);
        patch.set("value", volumes);
        
        return patch;
    }
    
    private ArrayNode createEnvironmentVariablePatches(JsonNode pod) {
        ArrayNode patches = objectMapper.createArrayNode();
        
        // For each container, add JAVA_OPTS environment variable
        JsonNode containers = pod.get("spec").get("containers");
        if (containers != null && containers.isArray()) {
            for (int i = 0; i < containers.size(); i++) {
                ObjectNode envPatch = createJavaOptsEnvironmentPatch(i, pod);
                if (envPatch != null) {
                    patches.add(envPatch);
                }
            }
        }
        
        return patches;
    }
    
    private ObjectNode createJavaOptsEnvironmentPatch(int containerIndex, JsonNode pod) {
        ObjectNode patch = objectMapper.createObjectNode();
        patch.put("op", "add");
        patch.put("path", "/spec/containers/" + containerIndex + "/env");
        
        ArrayNode env = objectMapper.createArrayNode();
        
        // Add JAVA_OPTS environment variable
        ObjectNode javaOpts = objectMapper.createObjectNode();
        javaOpts.put("name", "JAVA_OPTS");
        
        String agentArgs = buildAgentArgs(pod);
        String javaOptsValue = "-javaagent:" + AGENT_PATH + "/kubedb-monitor-agent.jar=" + agentArgs;
        javaOpts.put("value", javaOptsValue);
        
        env.add(javaOpts);
        patch.set("value", env);
        
        return patch;
    }
    
    private String buildAgentArgs(JsonNode pod) {
        StringBuilder args = new StringBuilder();
        
        JsonNode annotations = getAnnotations(pod);
        if (annotations == null) {
            return "";
        }
        
        // Add db-types if specified
        JsonNode dbTypes = annotations.get(DB_TYPES_ANNOTATION);
        if (dbTypes != null) {
            args.append("db-types=").append(dbTypes.asText()).append(",");
        }
        
        // Add sampling-rate if specified
        JsonNode samplingRate = annotations.get(SAMPLING_RATE_ANNOTATION);
        if (samplingRate != null) {
            args.append("sampling-rate=").append(samplingRate.asText()).append(",");
        }
        
        // Add slow-query-threshold if specified
        JsonNode slowQueryThreshold = annotations.get(SLOW_QUERY_THRESHOLD_ANNOTATION);
        if (slowQueryThreshold != null) {
            args.append("slow-query-threshold=").append(slowQueryThreshold.asText()).append(",");
        }
        
        // Remove trailing comma
        if (args.length() > 0 && args.charAt(args.length() - 1) == ',') {
            args.setLength(args.length() - 1);
        }
        
        return args.toString();
    }
    
    private String validatePodForMonitoring(JsonNode pod) {
        // Validate that the pod has the necessary requirements for monitoring
        
        JsonNode containers = pod.get("spec").get("containers");
        if (containers == null || !containers.isArray() || containers.size() == 0) {
            return "Pod must have at least one container";
        }
        
        // Check if any container appears to be a Java application
        boolean hasJavaContainer = false;
        for (JsonNode container : containers) {
            if (isJavaContainer(container)) {
                hasJavaContainer = true;
                break;
            }
        }
        
        if (!hasJavaContainer) {
            logger.warn("Pod {} does not appear to contain Java applications, but monitoring is enabled", getPodName(pod));
        }
        
        return null; // No validation errors
    }
    
    private boolean isJavaContainer(JsonNode container) {
        JsonNode image = container.get("image");
        if (image != null) {
            String imageStr = image.asText().toLowerCase();
            if (imageStr.contains("java") || imageStr.contains("openjdk") || imageStr.contains("spring") || imageStr.contains("tomcat")) {
                return true;
            }
        }
        
        // Check environment variables for Java-related settings
        JsonNode env = container.get("env");
        if (env != null && env.isArray()) {
            for (JsonNode envVar : env) {
                JsonNode name = envVar.get("name");
                if (name != null) {
                    String nameStr = name.asText().toUpperCase();
                    if (nameStr.contains("JAVA") || nameStr.equals("JVM_OPTS") || nameStr.equals("CATALINA_OPTS")) {
                        return true;
                    }
                }
            }
        }
        
        return false;
    }
    
    private ObjectNode createAllowedResponse(JsonNode request, ArrayNode patches) {
        ObjectNode response = objectMapper.createObjectNode();
        response.put("apiVersion", "admission.k8s.io/v1");
        response.put("kind", "AdmissionReview");
        
        ObjectNode responseObj = objectMapper.createObjectNode();
        responseObj.put("uid", request.get("uid").asText());
        responseObj.put("allowed", true);
        
        if (patches != null && patches.size() > 0) {
            String patchBase64 = Base64.getEncoder().encodeToString(patches.toString().getBytes());
            responseObj.put("patch", patchBase64);
            responseObj.put("patchType", "JSONPatch");
        }
        
        response.set("response", responseObj);
        return response;
    }
    
    private ObjectNode createDeniedResponse(JsonNode request, String reason) {
        ObjectNode response = objectMapper.createObjectNode();
        response.put("apiVersion", "admission.k8s.io/v1");
        response.put("kind", "AdmissionReview");
        
        ObjectNode responseObj = objectMapper.createObjectNode();
        responseObj.put("uid", request.get("uid").asText());
        responseObj.put("allowed", false);
        
        ObjectNode status = objectMapper.createObjectNode();
        status.put("code", 400);
        status.put("message", reason);
        responseObj.set("status", status);
        
        response.set("response", responseObj);
        return response;
    }
}