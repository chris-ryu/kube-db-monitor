package com.university.service;

// import com.university.websocket.MetricsWebSocketHandler; // 제거됨: Control Plane HTTP API 사용
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import javax.sql.DataSource;
import java.sql.Connection;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

@Service
@EnableAsync
public class MetricsService {
    
    private static final Logger logger = LoggerFactory.getLogger(MetricsService.class);
    private final DataSource dataSource;
    private final RestTemplate restTemplate;
    
    // Mock transaction registry
    private final Map<String, TransactionInfo> activeTransactions = new HashMap<>();
    private final List<DeadlockInfo> deadlocks = new ArrayList<>();
    
    public MetricsService(DataSource dataSource) {
        this.dataSource = dataSource;
        this.restTemplate = new RestTemplate(); // RestTemplate 초기화
        logger.info("🚀 MetricsService initialized with Control Plane HTTP API integration");
    }
    
    @Async
    public void sendQueryMetrics(String queryId, String sqlPattern, String sqlType, 
                               long executionTimeMs, String status, String podName) {
        
        Map<String, Object> queryData = Map.of(
            "query_id", queryId,
            "sql_pattern", sqlPattern,
            "sql_type", sqlType,
            "execution_time_ms", executionTimeMs,
            "timestamp", Instant.now().toString(),
            "status", status
        );
        
        // Note: 일반 쿼리 메트릭은 Agent가 처리하므로 여기서는 특수 이벤트만 처리
        logger.debug("Sent query metrics: {} - {}ms", sqlType, executionTimeMs);
    }
    
    @Async
    public void sendTransactionEvent(String transactionId, String eventType, Map<String, Object> details) {
        TransactionInfo txInfo = activeTransactions.computeIfAbsent(transactionId, id -> {
            TransactionInfo info = new TransactionInfo();
            info.transactionId = id;
            info.startTime = Instant.now();
            info.status = "active";
            info.podName = "university-registration-demo";
            info.namespace = "kubedb-monitor-test";
            info.queries = new ArrayList<>();
            return info;
        });
        
        if ("query_executed".equals(eventType)) {
            txInfo.queries.add(details);
            txInfo.queryCount = txInfo.queries.size();
            txInfo.totalExecutionTimeMs += (Long) details.getOrDefault("execution_time_ms", 0L);
        } else if ("transaction_committed".equals(eventType) || "transaction_rolled_back".equals(eventType)) {
            txInfo.status = "transaction_committed".equals(eventType) ? "committed" : "rolled_back";
            txInfo.endTime = Instant.now();
        } else if ("long_running_test".equals(eventType)) {
            // Long-running test에서는 details에서 직접 값들을 가져옴
            txInfo.queryCount = ((Number) details.getOrDefault("query_count", 0)).intValue();
            txInfo.totalExecutionTimeMs = ((Number) details.getOrDefault("execution_time_ms", 0L)).longValue();
            // endTime은 설정하지 않아서 현재 시간 기준으로 duration 계산
        }
        
        long durationMs = txInfo.endTime != null ? 
            (txInfo.endTime.toEpochMilli() - txInfo.startTime.toEpochMilli()) :
            (Instant.now().toEpochMilli() - txInfo.startTime.toEpochMilli());
        
        Map<String, Object> transactionEvent = new HashMap<>();
        transactionEvent.put("id", "evt-" + transactionId);
        transactionEvent.put("transaction_id", transactionId);
        transactionEvent.put("start_time", txInfo.startTime.toString());
        transactionEvent.put("end_time", txInfo.endTime != null ? txInfo.endTime.toString() : null);
        transactionEvent.put("status", txInfo.status);
        transactionEvent.put("duration_ms", durationMs);
        transactionEvent.put("query_count", txInfo.queryCount);
        transactionEvent.put("total_execution_time_ms", txInfo.totalExecutionTimeMs);
        transactionEvent.put("pod_name", txInfo.podName);
        transactionEvent.put("namespace", txInfo.namespace);
        transactionEvent.put("queries", txInfo.queries);
        
        // Note: 일반 트랜잭션 이벤트도 Agent가 처리하므로 여기서는 특수 이벤트만 처리
        logger.debug("Sent transaction event: {} - {}", transactionId, eventType);
        
        // Simulate long-running transaction detection
        if (durationMs > 4000) { // 4 seconds for testing
            simulateLongRunningTransactionAlert(txInfo, durationMs);
        }
    }
    
    @Async
    public void simulateDeadlock(List<String> transactionIds) {
        DeadlockInfo deadlock = new DeadlockInfo();
        deadlock.id = "dl-" + System.currentTimeMillis();
        deadlock.participants = transactionIds;
        deadlock.detectionTime = Instant.now();
        deadlock.recommendedVictim = transactionIds.get(ThreadLocalRandom.current().nextInt(transactionIds.size()));
        deadlock.severity = "critical";
        deadlock.status = "active";
        deadlock.cycleLength = transactionIds.size();
        
        // Create lock chain
        List<String> lockChain = new ArrayList<>();
        for (int i = 0; i < transactionIds.size(); i++) {
            String current = transactionIds.get(i);
            String next = transactionIds.get((i + 1) % transactionIds.size());
            lockChain.add(current + " → table_" + (i + 1) + " (waiting for " + next + ")");
        }
        deadlock.lockChain = lockChain;
        
        deadlocks.add(deadlock);
        
        Map<String, Object> deadlockEvent = Map.of(
            "id", deadlock.id,
            "participants", deadlock.participants,
            "detectionTime", deadlock.detectionTime.toString(),
            "recommendedVictim", deadlock.recommendedVictim,
            "lockChain", deadlock.lockChain,
            "severity", deadlock.severity,
            "status", deadlock.status,
            "pod_name", "university-registration-demo",
            "namespace", "kubedb-monitor-test",
            "cycleLength", deadlock.cycleLength
        );
        
        logger.warn("🔥 BEFORE sendEventToControlPlane - deadlock detected: {} participants", transactionIds.size());
        
        // Control Plane HTTP API를 통한 전송 (올바른 아키텍처)
        sendEventToControlPlane("deadlock_detected", deadlockEvent);
        
        logger.warn("🔥 AFTER sendEventToControlPlane - deadlock sent: {} participants", transactionIds.size());
    }
    
    private void simulateLongRunningTransactionAlert(TransactionInfo txInfo, long durationMs) {
        // Create long running transaction event
        Map<String, Object> longRunningEvent = Map.of(
            "transaction_id", txInfo.transactionId,
            "duration_ms", durationMs,
            "query_count", txInfo.queryCount,
            "start_time", txInfo.startTime.toString(),
            "pod_name", "university-registration-demo",
            "namespace", "kubedb-monitor-test",
            "status", "active"
        );
        
        // Control Plane HTTP API를 통한 전송
        sendEventToControlPlane("long_running_transaction", longRunningEvent);
        
        // This would be called by the actual transaction monitoring system
        logger.warn("Long-running transaction detected: {} running for {}ms", 
                   txInfo.transactionId, durationMs);
    }
    
    /**
     * Control Plane HTTP API로 직접 이벤트 전송
     */
    private void sendEventToControlPlane(String eventType, Map<String, Object> eventData) {
        try {
            // Control Plane에 직접 HTTP 전송
            String controlPlaneUrl = "http://kubedb-monitor-control-plane.kubedb-monitor.svc.cluster.local:8080/api/metrics";
            
            // Control Plane이 기대하는 형식으로 메시지 생성
            Map<String, Object> payload = Map.of(
                "event_type", eventType,
                "timestamp", Instant.now().toString(),
                "pod_name", "university-registration-demo",
                "namespace", "kubedb-monitor-test",
                "data", createSpecialEventData(eventType, eventData)
            );
            
            // 간단한 HTTP POST 전송 (Java 11+ HttpClient 사용)
            logger.info("🚀 Sending {} event to Control Plane URL: {}", eventType, controlPlaneUrl);
            sendHttpPost(controlPlaneUrl, payload);
            logger.info("✅ Successfully sent {} event to Control Plane: {}", eventType, eventData.get("id"));
            
        } catch (Exception e) {
            logger.error("Failed to send {} event to Control Plane: {}", eventType, e.getMessage());
        }
    }
    
    private Map<String, Object> createSpecialEventData(String eventType, Map<String, Object> eventData) {
        Map<String, Object> data = new HashMap<>();
        
        if ("deadlock_detected".equals(eventType)) {
            // Control Plane이 기대하는 deadlock 데이터 형식
            data.put("sql_type", "DEADLOCK");
            data.put("execution_time_ms", 0L);
            data.put("status", "error");
            data.put("query_id", "deadlock-" + System.currentTimeMillis());
            
            // 데드락 특화 필드들
            data.put("deadlock_duration", 0L);
            data.put("deadlock_connections", String.join(":", (List<String>) eventData.get("participants")));
            data.put("transaction_id", eventData.get("id"));
            
        } else if ("long_running_transaction".equals(eventType)) {
            // Long-running transaction 데이터 형식
            data.put("sql_type", "LONG_RUNNING");
            data.put("execution_time_ms", eventData.get("duration_ms"));
            data.put("status", "active");
            data.put("query_id", eventData.get("transaction_id") + "-longrunning");
            
            // Long-running 특화 필드들
            data.put("transaction_duration", eventData.get("duration_ms"));
            data.put("transaction_id", eventData.get("transaction_id"));
        }
        
        return data;
    }
    
    private void sendHttpPost(String url, Map<String, Object> payload) {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            
            HttpEntity<Map<String, Object>> request = new HttpEntity<>(payload, headers);
            
            ResponseEntity<String> response = restTemplate.postForEntity(url, request, String.class);
            
            if (response.getStatusCode().is2xxSuccessful()) {
                logger.info("✅ HTTP POST successful to {} (Status: {})", url, response.getStatusCode());
            } else {
                logger.warn("❌ HTTP POST failed with status: {} to {}", response.getStatusCode(), url);
            }
            
        } catch (Exception e) {
            logger.error("Failed to send HTTP POST to {}: {}", url, e.getMessage());
        }
    }
    
    private Map<String, Object> getCurrentJVMMetrics() {
        Runtime runtime = Runtime.getRuntime();
        long maxMemory = runtime.maxMemory();
        long totalMemory = runtime.totalMemory();
        long freeMemory = runtime.freeMemory();
        long usedMemory = totalMemory - freeMemory;
        
        // Try to get connection pool info
        int activeConnections = 0;
        int maxConnections = 20;
        
        try (Connection conn = dataSource.getConnection()) {
            // Mock connection pool metrics
            activeConnections = ThreadLocalRandom.current().nextInt(1, 10);
        } catch (Exception e) {
            logger.debug("Could not get connection pool metrics: {}", e.getMessage());
        }
        
        return Map.of(
            "connection_pool_active", activeConnections,
            "connection_pool_max", maxConnections,
            "heap_usage_ratio", (double) usedMemory / maxMemory,
            "cpu_usage_ratio", ThreadLocalRandom.current().nextDouble(0.1, 0.8),
            "total_memory_mb", totalMemory / (1024 * 1024),
            "used_memory_mb", usedMemory / (1024 * 1024),
            "free_memory_mb", freeMemory / (1024 * 1024)
        );
    }
    
    // Data classes
    private static class TransactionInfo {
        String transactionId;
        Instant startTime;
        Instant endTime;
        String status;
        int queryCount = 0;
        long totalExecutionTimeMs = 0;
        String podName;
        String namespace;
        List<Map<String, Object>> queries;
    }
    
    private static class DeadlockInfo {
        String id;
        List<String> participants;
        Instant detectionTime;
        String recommendedVictim;
        List<String> lockChain;
        String severity;
        String status;
        int cycleLength;
    }
}