package io.kubedb.monitor.common.metrics;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicLong;

/**
 * HTTP-based metrics collector that sends metrics to a remote endpoint
 */
public class HttpMetricsCollector implements MetricsCollector {
    private static final Logger logger = LoggerFactory.getLogger(HttpMetricsCollector.class);
    
    private final String endpoint;
    private final HttpClient httpClient;
    private final AtomicLong totalCount = new AtomicLong(0);
    private final AtomicLong errorCount = new AtomicLong(0);
    private final AtomicLong totalExecutionTime = new AtomicLong(0);
    
    public HttpMetricsCollector(String endpoint) {
        this.endpoint = endpoint;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
        
        logger.info("HTTP metrics collector initialized with endpoint: {}", endpoint);
    }
    
    @Override
    public void collect(DBMetrics metric) {
        if (metric == null) {
            return;
        }
        
        totalCount.incrementAndGet();
        logger.info("📊 HttpMetricsCollector.collect called, totalCount now: " + totalCount.get());
        if (metric.isError()) {
            errorCount.incrementAndGet();
        }
        totalExecutionTime.addAndGet(metric.getExecutionTimeMs());
        
        // Convert to HTTP payload format
        HttpMetricPayload payload = convertToPayload(metric);
        
        // Send asynchronously to avoid blocking
        httpClient.sendAsync(
            createHttpRequest(payload),
            HttpResponse.BodyHandlers.ofString()
        ).thenApply(HttpResponse::body)
         .thenAccept(responseBody -> {
             logger.debug("Successfully sent metric to HTTP endpoint: {}", responseBody);
         })
         .exceptionally(throwable -> {
             logger.error("Failed to send metric to HTTP endpoint: {}", endpoint, throwable);
             return null;
         });
    }
    
    @Override
    public long getTotalCount() {
        return totalCount.get();
    }
    
    @Override
    public long getErrorCount() {
        return errorCount.get();
    }
    
    @Override
    public double getAverageExecutionTime() {
        long total = totalCount.get();
        return total > 0 ? (double) totalExecutionTime.get() / total : 0.0;
    }
    
    @Override
    public void clear() {
        totalCount.set(0);
        errorCount.set(0);
        totalExecutionTime.set(0);
    }
    
    private HttpMetricPayload convertToPayload(DBMetrics metric) {
        HttpMetricPayload payload = new HttpMetricPayload();
        payload.timestamp = Instant.now().toString();
        payload.podName = System.getenv("HOSTNAME"); // Kubernetes pod name
        
        // Detect special event types based on SQL pattern and connection URL
        String sql = metric.getSql();
        String connectionUrl = metric.getConnectionUrl();
        
        if ("TPS_EVENT".equals(sql)) {
            payload.eventType = "tps_event";
        } else if ("LONG_RUNNING_TRANSACTION".equals(sql)) {
            payload.eventType = "long_running_transaction";
        } else {
            payload.eventType = "query_execution";
        }
        
        // Data section
        payload.data = new HttpMetricPayload.QueryData();
        payload.data.queryId = "agent-" + System.currentTimeMillis() + "-" + hashCode();
        payload.data.sqlPattern = metric.getSql();
        payload.data.sqlType = extractSqlType(metric.getSql());
        payload.data.tableNames = extractTableNames(metric.getSql());
        payload.data.executionTimeMs = metric.getExecutionTimeMs();
        payload.data.status = metric.isError() ? "ERROR" : "SUCCESS";
        payload.data.errorMessage = metric.getErrorMessage();
        
        // Add special handling for TPS and Long Running Transaction events
        if ("tps_event".equals(payload.eventType)) {
            payload.data.tpsValue = (double) metric.getExecutionTimeMs(); // TPS value stored in executionTimeMs
            payload.data.sqlPattern = "High TPS detected: " + payload.data.tpsValue + " queries/second";
            logger.info("🚀 TPS EVENT: Sending TPS event with value {} to Control Plane", payload.data.tpsValue);
        } else if ("long_running_transaction".equals(payload.eventType)) {
            payload.data.transactionDuration = metric.getExecutionTimeMs();
            payload.data.transactionId = extractTransactionIdFromUrl(connectionUrl);
            payload.data.sqlPattern = "Long running transaction detected: " + payload.data.transactionDuration + "ms";
            logger.warn("🚨 LONG RUNNING TRANSACTION EVENT: Sending long running transaction event ({}ms) to Control Plane", payload.data.transactionDuration);
        }
        
        // Metrics section
        payload.metrics = new HttpMetricPayload.SystemMetrics();
        payload.metrics.connectionPoolActive = 5; // TODO: Get real values
        payload.metrics.connectionPoolIdle = 3;
        payload.metrics.connectionPoolMax = 10;
        payload.metrics.connectionPoolUsageRatio = 0.5;
        payload.metrics.heapUsedMb = (int) (Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory()) / (1024 * 1024);
        payload.metrics.heapMaxMb = (int) Runtime.getRuntime().maxMemory() / (1024 * 1024);
        payload.metrics.heapUsageRatio = (double) payload.metrics.heapUsedMb / payload.metrics.heapMaxMb;
        payload.metrics.cpuUsageRatio = 0.2; // TODO: Get real CPU usage
        
        return payload;
    }
    
    private HttpRequest createHttpRequest(HttpMetricPayload payload) {
        try {
            String jsonBody = payloadToJson(payload);
            
            return HttpRequest.newBuilder()
                    .uri(URI.create(endpoint))
                    .timeout(Duration.ofSeconds(10))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
                    .build();
                    
        } catch (Exception e) {
            logger.error("Failed to create HTTP request", e);
            throw new RuntimeException(e);
        }
    }
    
    private String payloadToJson(HttpMetricPayload payload) {
        StringBuilder json = new StringBuilder();
        json.append("{");
        json.append("\"timestamp\":\"").append(payload.timestamp).append("\",");
        json.append("\"pod_name\":\"").append(payload.podName != null ? payload.podName : "unknown").append("\",");
        json.append("\"event_type\":\"").append(payload.eventType).append("\",");
        
        // Data section
        json.append("\"data\":{");
        json.append("\"query_id\":\"").append(payload.data.queryId).append("\",");
        json.append("\"sql_pattern\":\"").append(escapeSql(payload.data.sqlPattern)).append("\",");
        json.append("\"sql_type\":\"").append(payload.data.sqlType).append("\",");
        json.append("\"table_names\":[");
        if (payload.data.tableNames != null) {
            for (int i = 0; i < payload.data.tableNames.length; i++) {
                json.append("\"").append(payload.data.tableNames[i]).append("\"");
                if (i < payload.data.tableNames.length - 1) json.append(",");
            }
        }
        json.append("],");
        json.append("\"execution_time_ms\":").append(payload.data.executionTimeMs).append(",");
        json.append("\"status\":\"").append(payload.data.status).append("\"");
        if (payload.data.errorMessage != null) {
            json.append(",\"error_message\":\"").append(escapeJson(payload.data.errorMessage)).append("\"");
        }
        
        // Add advanced event fields
        if (payload.data.tpsValue != null) {
            json.append(",\"tps_value\":").append(payload.data.tpsValue);
        }
        if (payload.data.transactionDuration != null) {
            json.append(",\"transaction_duration\":").append(payload.data.transactionDuration);
        }
        if (payload.data.transactionId != null) {
            json.append(",\"transaction_id\":\"").append(payload.data.transactionId).append("\"");
        }
        
        json.append("},");
        
        // Metrics section
        json.append("\"metrics\":{");
        json.append("\"connection_pool_active\":").append(payload.metrics.connectionPoolActive).append(",");
        json.append("\"connection_pool_idle\":").append(payload.metrics.connectionPoolIdle).append(",");
        json.append("\"connection_pool_max\":").append(payload.metrics.connectionPoolMax).append(",");
        json.append("\"connection_pool_usage_ratio\":").append(payload.metrics.connectionPoolUsageRatio).append(",");
        json.append("\"heap_used_mb\":").append(payload.metrics.heapUsedMb).append(",");
        json.append("\"heap_max_mb\":").append(payload.metrics.heapMaxMb).append(",");
        json.append("\"heap_usage_ratio\":").append(payload.metrics.heapUsageRatio).append(",");
        json.append("\"cpu_usage_ratio\":").append(payload.metrics.cpuUsageRatio);
        json.append("}");
        
        json.append("}");
        return json.toString();
    }
    
    private String escapeJson(String str) {
        if (str == null) return "";
        return str.replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\\r");
    }
    
    private String escapeSql(String sql) {
        if (sql == null) return "";
        // Escape quotes and truncate if too long
        String escaped = sql.replace("\"", "\\\"").replace("\n", " ").replace("\r", " ");
        return escaped.length() > 200 ? escaped.substring(0, 200) + "..." : escaped;
    }
    
    private String extractSqlType(String sql) {
        if (sql == null || sql.trim().isEmpty()) {
            return "UNKNOWN";
        }
        
        String upperSql = sql.trim().toUpperCase();
        if (upperSql.startsWith("SELECT")) return "SELECT";
        if (upperSql.startsWith("INSERT")) return "INSERT";
        if (upperSql.startsWith("UPDATE")) return "UPDATE";
        if (upperSql.startsWith("DELETE")) return "DELETE";
        if (upperSql.startsWith("CREATE")) return "CREATE";
        if (upperSql.startsWith("DROP")) return "DROP";
        if (upperSql.startsWith("ALTER")) return "ALTER";
        
        return "OTHER";
    }
    
    private String[] extractTableNames(String sql) {
        // Simple table extraction - in production this would be more sophisticated
        if (sql == null) {
            return new String[0];
        }
        
        // This is a very basic implementation
        String upperSql = sql.toUpperCase();
        if (upperSql.contains(" FROM ")) {
            // Try to extract table name after FROM
            String[] parts = upperSql.split(" FROM ");
            if (parts.length > 1) {
                String tablePart = parts[1].split(" ")[0];
                return new String[]{tablePart.toLowerCase()};
            }
        }
        
        return new String[]{"unknown"};
    }
    
    /**
     * Extract transaction ID from connection URL (for long running transaction events)
     */
    private String extractTransactionIdFromUrl(String url) {
        if (url != null && url.startsWith("long-tx://")) {
            return url.substring("long-tx://".length());
        }
        return null;
    }
    
    /**
     * HTTP payload structure matching the control plane API
     */
    public static class HttpMetricPayload {
        public String timestamp;
        public String podName;
        public String eventType;
        public QueryData data;
        public SystemMetrics metrics;
        
        public static class QueryData {
            public String queryId;
            public String sqlPattern;
            public String sqlType;
            public String[] tableNames;
            public long executionTimeMs;
            public String status;
            public String errorMessage;
            
            // Additional fields for advanced events
            public Double tpsValue; // For TPS events
            public Long transactionDuration; // For long running transaction events
            public String transactionId; // For transaction events
        }
        
        public static class SystemMetrics {
            public Integer connectionPoolActive;
            public Integer connectionPoolIdle;
            public Integer connectionPoolMax;
            public Double connectionPoolUsageRatio;
            public Integer heapUsedMb;
            public Integer heapMaxMb;
            public Double heapUsageRatio;
            public Double cpuUsageRatio;
        }
    }
}