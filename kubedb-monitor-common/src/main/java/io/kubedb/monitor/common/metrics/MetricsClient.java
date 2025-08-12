package io.kubedb.monitor.common.metrics;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.kubedb.monitor.common.transaction.QueryExecution;
import io.kubedb.monitor.common.transaction.TransactionContext;
import io.kubedb.monitor.common.transaction.TransactionMetrics;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Client for sending metrics to the KubeDB Monitor Control Plane
 */
public class MetricsClient {
    private static final Logger logger = LoggerFactory.getLogger(MetricsClient.class);
    
    private final String controlPlaneUrl;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final ExecutorService executorService;
    private final String podName;
    private final String namespace;
    
    public MetricsClient(String controlPlaneUrl, String podName, String namespace) {
        this.controlPlaneUrl = controlPlaneUrl.endsWith("/") ? controlPlaneUrl : controlPlaneUrl + "/";
        this.podName = podName;
        this.namespace = namespace;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .build();
        this.objectMapper = new ObjectMapper();
        this.executorService = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "metrics-sender");
            t.setDaemon(true);
            return t;
        });
    }
    
    /**
     * Send query execution metrics asynchronously
     */
    public void sendQueryMetrics(QueryExecution query, String connectionId, String threadName) {
        CompletableFuture.runAsync(() -> {
            try {
                Map<String, Object> queryData = new HashMap<>();
                queryData.put("query_id", query.getQueryId());
                queryData.put("sql_pattern", query.getSqlPattern());
                queryData.put("sql_type", query.getSqlType());
                queryData.put("execution_time_ms", query.getExecutionTimeMs());
                queryData.put("timestamp", query.getTimestamp().toString());
                queryData.put("status", query.getStatus());
                queryData.put("connection_id", connectionId);
                queryData.put("thread_name", threadName);
                
                Map<String, Object> systemMetrics = getCurrentSystemMetrics();
                
                Map<String, Object> payload = new HashMap<>();
                payload.put("timestamp", Instant.now().toString());
                payload.put("pod_name", podName);
                payload.put("namespace", namespace);
                payload.put("event_type", "query_execution");
                payload.put("data", queryData);
                payload.put("metrics", systemMetrics);
                
                sendMetricsPayload(payload);
                logger.debug("Sent query metrics: {} - {}ms", query.getSqlType(), query.getExecutionTimeMs());
                
            } catch (Exception e) {
                logger.error("Failed to send query metrics: ", e);
            }
        }, executorService);
    }
    
    /**
     * Send transaction metrics asynchronously
     */
    public void sendTransactionMetrics(TransactionMetrics txMetrics) {
        CompletableFuture.runAsync(() -> {
            try {
                Map<String, Object> transactionData = new HashMap<>();
                transactionData.put("transaction_id", txMetrics.getTransactionId());
                transactionData.put("start_time", txMetrics.getStartTime().toString());
                transactionData.put("end_time", txMetrics.getEndTime() != null ? txMetrics.getEndTime().toString() : null);
                transactionData.put("status", txMetrics.getStatus().name().toLowerCase());
                transactionData.put("duration_ms", txMetrics.getDurationMs());
                transactionData.put("query_count", txMetrics.getQueryCount());
                transactionData.put("total_execution_time_ms", txMetrics.getTotalExecutionTimeMs());
                
                Map<String, Object> payload = new HashMap<>();
                payload.put("timestamp", Instant.now().toString());
                payload.put("pod_name", podName);
                payload.put("namespace", namespace);
                payload.put("event_type", "transaction_event");
                payload.put("data", transactionData);
                payload.put("metrics", getCurrentSystemMetrics());
                
                sendMetricsPayload(payload);
                logger.debug("Sent transaction metrics: {} - {}ms", txMetrics.getTransactionId(), txMetrics.getDurationMs());
                
            } catch (Exception e) {
                logger.error("Failed to send transaction metrics: ", e);
            }
        }, executorService);
    }
    
    /**
     * Send deadlock event
     */
    public void sendDeadlockEvent(String transactionId1, String transactionId2, String lockChain) {
        CompletableFuture.runAsync(() -> {
            try {
                Map<String, Object> deadlockData = new HashMap<>();
                deadlockData.put("id", "dl-" + System.currentTimeMillis());
                deadlockData.put("participants", new String[]{transactionId1, transactionId2});
                deadlockData.put("detectionTime", Instant.now().toString());
                deadlockData.put("recommendedVictim", transactionId2); // Simple victim selection
                deadlockData.put("lockChain", new String[]{lockChain});
                deadlockData.put("severity", "critical");
                deadlockData.put("status", "active");
                deadlockData.put("cycleLength", 2);
                
                Map<String, Object> payload = new HashMap<>();
                payload.put("timestamp", Instant.now().toString());
                payload.put("pod_name", podName);
                payload.put("namespace", namespace);
                payload.put("event_type", "deadlock_event");
                payload.put("data", deadlockData);
                payload.put("metrics", getCurrentSystemMetrics());
                
                sendMetricsPayload(payload);
                logger.warn("Sent deadlock event: {} vs {}", transactionId1, transactionId2);
                
            } catch (Exception e) {
                logger.error("Failed to send deadlock metrics: ", e);
            }
        }, executorService);
    }
    
    private void sendMetricsPayload(Map<String, Object> payload) throws IOException, InterruptedException {
        String json = objectMapper.writeValueAsString(payload);
        
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(controlPlaneUrl + "api/metrics"))
                .header("Content-Type", "application/json")
                .timeout(Duration.ofSeconds(10))
                .POST(HttpRequest.BodyPublishers.ofString(json))
                .build();
        
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        
        if (response.statusCode() != 200) {
            logger.warn("Failed to send metrics, status: {}, response: {}", 
                       response.statusCode(), response.body());
        }
    }
    
    private Map<String, Object> getCurrentSystemMetrics() {
        Runtime runtime = Runtime.getRuntime();
        long maxMemory = runtime.maxMemory();
        long totalMemory = runtime.totalMemory();
        long freeMemory = runtime.freeMemory();
        long usedMemory = totalMemory - freeMemory;
        
        Map<String, Object> metrics = new HashMap<>();
        metrics.put("heap_used_mb", usedMemory / (1024 * 1024));
        metrics.put("heap_max_mb", maxMemory / (1024 * 1024));
        metrics.put("heap_usage_ratio", (double) usedMemory / maxMemory);
        metrics.put("cpu_usage_ratio", getCurrentCpuUsage());
        metrics.put("connection_pool_active", 5); // Mock - would get from actual pool
        metrics.put("connection_pool_max", 20);   // Mock - would get from actual pool
        metrics.put("connection_pool_usage_ratio", 0.25);
        
        return metrics;
    }
    
    private double getCurrentCpuUsage() {
        // Simple approximation - in real implementation would use JMX
        return Math.min(0.8, Math.random() * 0.6 + 0.1);
    }
    
    public void shutdown() {
        executorService.shutdown();
    }
}