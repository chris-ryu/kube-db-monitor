package io.kubedb.monitor.agent;

import io.kubedb.monitor.agent.pool.PoolMetrics;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicLong;

/**
 * HTTP를 통해 메트릭을 Control Plane으로 전송하는 클래스
 */
public class HttpMetricsTransmitter {
    private static final Logger logger = LoggerFactory.getLogger(HttpMetricsTransmitter.class);
    
    private final AgentConfig config;
    private final String collectorEndpoint;
    private final ExecutorService executor;
    private final AtomicLong transmissionCounter = new AtomicLong(0);
    
    public HttpMetricsTransmitter(AgentConfig config) {
        this.config = config;
        this.collectorEndpoint = config.getCollectorEndpoint();
        this.executor = Executors.newFixedThreadPool(2); // 2개 스레드로 비동기 전송
        
        logger.info("[KubeDB] HttpMetricsTransmitter 초기화됨 - endpoint: {}", collectorEndpoint);
        
        if (collectorEndpoint == null || collectorEndpoint.trim().isEmpty()) {
            logger.warn("[KubeDB] Collector endpoint가 설정되지 않았습니다. 메트릭 전송이 비활성화됩니다.");
        }
    }
    
    /**
     * 쿼리 메트릭을 비동기로 전송
     */
    public void transmitQueryMetric(String sql, long executionTimeMs, String connectionId, String threadName) {
        transmitQueryMetric(sql, executionTimeMs, connectionId, threadName, null);
    }
    
    /**
     * 쿼리 메트릭을 시스템 메트릭과 함께 비동기로 전송
     */
    public void transmitQueryMetric(String sql, long executionTimeMs, String connectionId, String threadName, PoolMetrics poolMetrics) {
        if (!shouldTransmit()) {
            return;
        }
        
        executor.submit(() -> {
            try {
                String json = createQueryMetricJson(sql, executionTimeMs, connectionId, threadName, poolMetrics);
                sendHttpPost(json);
                logger.debug("[KubeDB] Query metric transmitted: {} ms", executionTimeMs);
            } catch (Exception e) {
                logger.warn("[KubeDB] Failed to transmit query metric: {}", e.getMessage());
            }
        });
    }
    
    /**
     * 트랜잭션 메트릭을 비동기로 전송
     */
    public void transmitTransactionMetric(String operation, long executionTimeMs, String connectionId, String transactionId) {
        if (!shouldTransmit()) {
            return;
        }
        
        executor.submit(() -> {
            try {
                String json = createTransactionMetricJson(operation, executionTimeMs, connectionId, transactionId);
                sendHttpPost(json);
                logger.debug("[KubeDB] Transaction metric transmitted: {} - {} ms", operation, executionTimeMs);
            } catch (Exception e) {
                logger.warn("[KubeDB] Failed to transmit transaction metric: {}", e.getMessage());
            }
        });
    }
    
    /**
     * 장기 실행 트랜잭션 알림 전송
     */
    public void transmitLongRunningTransactionAlert(String transactionId, String connectionId, String threadName, long durationMs, long startTime) {
        if (!shouldTransmit()) {
            return;
        }
        
        executor.submit(() -> {
            try {
                String json = createLongRunningTransactionAlertJson(transactionId, connectionId, threadName, durationMs, startTime);
                sendHttpPost(json);
                logger.info("[KubeDB] Long-running transaction alert transmitted: {} ms", durationMs);
            } catch (Exception e) {
                logger.warn("[KubeDB] Failed to transmit long-running transaction alert: {}", e.getMessage());
            }
        });
    }
    
    /**
     * 시스템 메트릭만 별도로 전송 (주기적 전송용)
     */
    public void transmitSystemMetrics(PoolMetrics poolMetrics) {
        if (!shouldTransmit() || poolMetrics == null) {
            return;
        }
        
        executor.submit(() -> {
            try {
                String json = createSystemMetricJson(poolMetrics);
                sendHttpPost(json);
                logger.debug("[KubeDB] System metrics transmitted: {}", poolMetrics.getPoolType().getDisplayName());
            } catch (Exception e) {
                logger.warn("[KubeDB] Failed to transmit system metrics: {}", e.getMessage());
            }
        });
    }
    
    /**
     * 전송할지 여부를 샘플링 레이트에 따라 결정
     */
    private boolean shouldTransmit() {
        if (collectorEndpoint == null || collectorEndpoint.trim().isEmpty()) {
            return false;
        }
        
        if (!config.isEnabled()) {
            return false;
        }
        
        // 샘플링 레이트 적용
        double samplingRate = config.getSamplingRate();
        if (samplingRate <= 0.0) {
            return false;
        }
        if (samplingRate >= 1.0) {
            return true;
        }
        
        // 간단한 카운터 기반 샘플링
        long count = transmissionCounter.incrementAndGet();
        return (count % Math.round(1.0 / samplingRate)) == 0;
    }
    
    /**
     * 쿼리 메트릭 JSON 생성
     */
    private String createQueryMetricJson(String sql, long executionTimeMs, String connectionId, String threadName) {
        return createQueryMetricJson(sql, executionTimeMs, connectionId, threadName, null);
    }
    
    /**
     * 쿼리 메트릭 JSON 생성 (시스템 메트릭 포함)
     */
    private String createQueryMetricJson(String sql, long executionTimeMs, String connectionId, String threadName, PoolMetrics poolMetrics) {
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME) + "Z";
        String sqlType = extractSqlType(sql);
        String queryId = "query-" + System.nanoTime();
        
        // SQL에서 민감한 정보 마스킹
        String maskedSql = config.isMaskSqlParams() ? maskSqlParameters(sql) : sql;
        
        // 시스템 메트릭 JSON 부분 생성
        String metricsJson = "";
        if (poolMetrics != null && poolMetrics.getPoolType().name() != "UNKNOWN") {
            metricsJson = String.format(
                ",\"metrics\": {" +
                    "\"connection_pool_active\": %d," +
                    "\"connection_pool_idle\": %d," +
                    "\"connection_pool_max\": %d," +
                    "\"connection_pool_usage_ratio\": %.3f," +
                    "\"pool_type\": \"%s\"," +
                    "\"pool_name\": \"%s\"" +
                "}",
                poolMetrics.getActiveConnections(),
                poolMetrics.getIdleConnections(),
                poolMetrics.getMaxConnections(),
                poolMetrics.getConnectionUsageRatio(),
                poolMetrics.getPoolType().getDisplayName(),
                poolMetrics.getPoolName()
            );
        }
        
        return String.format(
            "{" +
            "\"timestamp\": \"%s\"," +
            "\"event_type\": \"query_execution\"," +
            "\"pod_name\": \"%s\"," +
            "\"namespace\": \"%s\"," +
            "\"data\": {" +
                "\"query_id\": \"%s\"," +
                "\"sql_type\": \"%s\"," +
                "\"sql_pattern\": \"%s\"," +
                "\"execution_time_ms\": %d," +
                "\"connection_id\": \"%s\"," +
                "\"thread_name\": \"%s\"," +
                "\"status\": \"completed\"" +
            "}" +
            "%s" + // 시스템 메트릭 부분
            "}",
            timestamp,
            getPodName(),
            getNamespace(),
            queryId,
            sqlType,
            maskedSql.replace("\"", "\\\"").replace("\n", "\\n"),
            executionTimeMs,
            connectionId,
            threadName,
            metricsJson
        );
    }
    
    /**
     * 트랜잭션 메트릭 JSON 생성
     */
    private String createTransactionMetricJson(String operation, long executionTimeMs, String connectionId, String transactionId) {
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME) + "Z";
        
        return String.format(
            "{" +
            "\"timestamp\": \"%s\"," +
            "\"event_type\": \"transaction_event\"," +
            "\"pod_name\": \"%s\"," +
            "\"namespace\": \"%s\"," +
            "\"data\": {" +
                "\"query_id\": \"tx-%s\"," +
                "\"sql_type\": \"TRANSACTION\"," +
                "\"sql_pattern\": \"%s\"," +
                "\"execution_time_ms\": %d," +
                "\"connection_id\": \"%s\"," +
                "\"transaction_id\": \"%s\"," +
                "\"status\": \"completed\"" +
            "}" +
            "}",
            timestamp,
            getPodName(),
            getNamespace(),
            System.nanoTime(),
            operation.toUpperCase(),
            executionTimeMs,
            connectionId,
            transactionId
        );
    }
    
    /**
     * 장기 실행 트랜잭션 알림 JSON 생성
     */
    private String createLongRunningTransactionAlertJson(String transactionId, String connectionId, String threadName, long durationMs, long startTime) {
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME) + "Z";
        
        return String.format(
            "{" +
            "\"timestamp\": \"%s\"," +
            "\"event_type\": \"long_running_transaction\"," +
            "\"pod_name\": \"%s\"," +
            "\"namespace\": \"%s\"," +
            "\"data\": {" +
                "\"query_id\": \"long-tx-%s\"," +
                "\"sql_type\": \"LONG_RUNNING\"," +
                "\"execution_time_ms\": %d," +
                "\"connection_id\": \"%s\"," +
                "\"transaction_id\": \"%s\"," +
                "\"transaction_duration\": %d," +
                "\"thread_name\": \"%s\"," +
                "\"start_time\": %d," +
                "\"status\": \"active\"" +
            "}" +
            "}",
            timestamp,
            getPodName(),
            getNamespace(),
            System.nanoTime(),
            durationMs,
            connectionId,
            transactionId,
            durationMs,
            threadName,
            startTime
        );
    }
    
    /**
     * 시스템 메트릭 전용 JSON 생성
     */
    private String createSystemMetricJson(PoolMetrics poolMetrics) {
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME) + "Z";
        
        return String.format(
            "{" +
            "\"timestamp\": \"%s\"," +
            "\"event_type\": \"system_metrics\"," +
            "\"pod_name\": \"%s\"," +
            "\"namespace\": \"%s\"," +
            "\"metrics\": {" +
                "\"connection_pool_active\": %d," +
                "\"connection_pool_idle\": %d," +
                "\"connection_pool_max\": %d," +
                "\"connection_pool_usage_ratio\": %.3f," +
                "\"pool_type\": \"%s\"," +
                "\"pool_name\": \"%s\"," +
                "\"total_connections\": %d" +
            "}" +
            "}",
            timestamp,
            getPodName(),
            getNamespace(),
            poolMetrics.getActiveConnections(),
            poolMetrics.getIdleConnections(),
            poolMetrics.getMaxConnections(),
            poolMetrics.getConnectionUsageRatio(),
            poolMetrics.getPoolType().getDisplayName(),
            poolMetrics.getPoolName(),
            poolMetrics.getTotalConnections()
        );
    }
    
    /**
     * HTTP POST 요청 전송
     */
    private void sendHttpPost(String jsonPayload) throws IOException {
        URL url = new URL(collectorEndpoint);
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        
        try {
            connection.setRequestMethod("POST");
            connection.setRequestProperty("Content-Type", "application/json");
            connection.setRequestProperty("User-Agent", "KubeDB-Monitor-Agent/1.0");
            connection.setDoOutput(true);
            connection.setConnectTimeout(5000); // 5초 타임아웃
            connection.setReadTimeout(10000); // 10초 읽기 타임아웃
            
            // JSON 데이터 전송
            try (OutputStream os = connection.getOutputStream()) {
                byte[] input = jsonPayload.getBytes(StandardCharsets.UTF_8);
                os.write(input, 0, input.length);
            }
            
            // 응답 확인
            int responseCode = connection.getResponseCode();
            if (responseCode == HttpURLConnection.HTTP_OK) {
                logger.debug("[KubeDB] Metric successfully transmitted to {}", collectorEndpoint);
            } else {
                logger.warn("[KubeDB] HTTP transmission failed with code: {}", responseCode);
            }
            
        } finally {
            connection.disconnect();
        }
    }
    
    /**
     * SQL에서 타입 추출
     */
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
    
    /**
     * SQL 파라미터 마스킹
     */
    private String maskSqlParameters(String sql) {
        if (sql == null) return null;
        
        // 간단한 파라미터 마스킹 (?, 'value', "value" 등을 ? 또는 *** 로 변경)
        return sql.replaceAll("'[^']*'", "'***'")
                  .replaceAll("\"[^\"]*\"", "\"***\"")
                  .replaceAll("\\b\\d+\\b", "***");
    }
    
    /**
     * Pod 이름 가져오기
     */
    private String getPodName() {
        String hostname = System.getenv("HOSTNAME");
        return hostname != null ? hostname : "unknown-pod";
    }
    
    /**
     * Namespace 가져오기  
     */
    private String getNamespace() {
        String namespace = System.getenv("NAMESPACE");
        return namespace != null ? namespace : "kubedb-monitor-test";
    }
    
    /**
     * 리소스 정리
     */
    public void shutdown() {
        if (executor != null) {
            executor.shutdown();
            logger.info("[KubeDB] HttpMetricsTransmitter shutdown completed");
        }
    }
}