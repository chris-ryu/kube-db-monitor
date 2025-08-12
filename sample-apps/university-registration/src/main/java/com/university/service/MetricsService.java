package com.university.service;

import com.university.websocket.MetricsWebSocketHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.stereotype.Service;

import javax.sql.DataSource;
import java.sql.Connection;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

@Service
@EnableAsync
public class MetricsService {
    
    private static final Logger logger = LoggerFactory.getLogger(MetricsService.class);
    private final MetricsWebSocketHandler webSocketHandler;
    private final DataSource dataSource;
    
    // Mock transaction registry
    private final Map<String, TransactionInfo> activeTransactions = new HashMap<>();
    private final List<DeadlockInfo> deadlocks = new ArrayList<>();
    
    public MetricsService(MetricsWebSocketHandler webSocketHandler, DataSource dataSource) {
        this.webSocketHandler = webSocketHandler;
        this.dataSource = dataSource;
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
        
        Map<String, Object> message = Map.of(
            "type", "query_metrics",
            "timestamp", Instant.now().toString(),
            "pod_name", podName != null ? podName : "university-registration-demo",
            "namespace", "kubedb-monitor-test",
            "event_type", "query_execution",
            "data", queryData,
            "metrics", getCurrentJVMMetrics()
        );
        
        webSocketHandler.broadcastMessage(message);
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
        
        Map<String, Object> message = Map.of(
            "type", "transaction_event",
            "timestamp", Instant.now().toString(),
            "data", transactionEvent
        );
        
        webSocketHandler.broadcastMessage(message);
        logger.debug("Sent transaction event: {} - {}", transactionId, eventType);
        
        // Simulate long-running transaction detection
        if (durationMs > 300000) { // 5 minutes
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
        
        Map<String, Object> message = Map.of(
            "type", "deadlock_event",
            "timestamp", Instant.now().toString(),
            "data", deadlockEvent
        );
        
        webSocketHandler.broadcastMessage(message);
        logger.warn("Simulated deadlock detected: {} participants", transactionIds.size());
    }
    
    private void simulateLongRunningTransactionAlert(TransactionInfo txInfo, long durationMs) {
        // This would be called by the actual transaction monitoring system
        logger.warn("Long-running transaction detected: {} running for {}ms", 
                   txInfo.transactionId, durationMs);
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