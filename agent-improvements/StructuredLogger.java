package io.kubedb.monitor.agent.logging;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.kubedb.monitor.agent.model.QueryMetrics;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.time.Instant;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

/**
 * 구조화된 JSON 로그를 출력하는 로거
 * 제니퍼 스타일 대시보드를 위한 실시간 메트릭 로깅
 */
@Slf4j
@Component
public class StructuredLogger {
    
    private static final String KUBEDB_METRICS_PREFIX = "KUBEDB_METRICS: ";
    private static final ObjectMapper objectMapper = new ObjectMapper();
    
    // 비동기 로깅을 위한 큐와 스레드 풀
    private final BlockingQueue<QueryMetrics> logQueue = new LinkedBlockingQueue<>(10000);
    private final ExecutorService logExecutor = Executors.newSingleThreadExecutor(
        r -> new Thread(r, "kubedb-structured-logger"));
    
    @Value("${kubedb.monitor.pod-name:unknown}")
    private String podName;
    
    @Value("${kubedb.monitor.namespace:default}")
    private String namespace;
    
    @Value("${kubedb.monitor.logging.async:true}")
    private boolean asyncLogging;
    
    @Value("${kubedb.monitor.logging.slow-query-threshold-ms:50}")
    private long slowQueryThresholdMs;
    
    @PostConstruct
    public void init() {
        // ObjectMapper 설정
        objectMapper.registerModule(new JavaTimeModule());
        objectMapper.configure(com.fasterxml.jackson.databind.SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false);
        
        // 비동기 로깅이 활성화된 경우 백그라운드 스레드 시작
        if (asyncLogging) {
            startAsyncLogger();
        }
        
        log.info("StructuredLogger initialized - Pod: {}, Namespace: {}, Async: {}", 
            podName, namespace, asyncLogging);
    }
    
    /**
     * 쿼리 실행 메트릭을 구조화된 JSON으로 로깅
     */
    public void logQueryExecution(QueryMetrics metrics) {
        if (metrics == null) {
            return;
        }
        
        // 기본 정보 설정
        metrics = enrichMetrics(metrics);
        
        if (asyncLogging) {
            // 비동기 로깅
            if (!logQueue.offer(metrics)) {
                log.warn("Log queue is full, dropping metrics for query: {}", 
                    metrics.getData() != null ? metrics.getData().getQueryId() : "unknown");
            }
        } else {
            // 동기 로깅
            writeLog(metrics);
        }
    }
    
    /**
     * 쿼리 시작 이벤트 로깅
     */
    public void logQueryStart(String queryId, String sqlPattern, String connectionId) {
        QueryMetrics metrics = QueryMetrics.builder()
            .timestamp(Instant.now())
            .eventType("query_start")
            .data(QueryMetrics.QueryData.builder()
                .queryId(queryId)
                .sqlPattern(sqlPattern)
                .connectionId(connectionId)
                .status(QueryMetrics.ExecutionStatus.SUCCESS)
                .build())
            .build();
            
        logQueryExecution(metrics);
    }
    
    /**
     * 쿼리 완료 이벤트 로깅
     */
    public void logQueryComplete(QueryMetrics metrics) {
        if (metrics != null && metrics.getData() != null) {
            metrics.setEventType("query_complete");
            
            // 느린 쿼리인 경우 특별한 이벤트 타입 설정
            if (metrics.isSlowQuery(slowQueryThresholdMs)) {
                metrics.setEventType("slow_query");
                log.warn("Slow query detected: {} ms - Query ID: {}", 
                    metrics.getData().getExecutionTimeMs(),
                    metrics.getData().getQueryId());
            }
        }
        
        logQueryExecution(metrics);
    }
    
    /**
     * 에러 이벤트 로깅
     */
    public void logQueryError(String queryId, String sqlPattern, Exception exception) {
        QueryMetrics errorMetrics = QueryMetrics.error(exception, queryId, sqlPattern);
        errorMetrics = enrichMetrics(errorMetrics);
        errorMetrics.setEventType("query_error");
        
        logQueryExecution(errorMetrics);
        
        log.error("Query execution error - Query ID: {}, Error: {}", 
            queryId, exception.getMessage(), exception);
    }
    
    /**
     * 커넥션 풀 상태 로깅
     */
    public void logConnectionPoolStatus(QueryMetrics.SystemMetrics systemMetrics) {
        QueryMetrics metrics = QueryMetrics.builder()
            .timestamp(Instant.now())
            .eventType("connection_pool_status")
            .metrics(systemMetrics)
            .build();
            
        metrics = enrichMetrics(metrics);
        logQueryExecution(metrics);
    }
    
    /**
     * 시스템 메트릭 로깅
     */
    public void logSystemMetrics(QueryMetrics.SystemMetrics systemMetrics) {
        QueryMetrics metrics = QueryMetrics.builder()
            .timestamp(Instant.now())
            .eventType("system_metrics")
            .metrics(systemMetrics)
            .build();
            
        metrics = enrichMetrics(metrics);
        logQueryExecution(metrics);
    }
    
    /**
     * 사용자 세션 이벤트 로깅
     */
    public void logUserSession(String sessionId, String userId, String action) {
        QueryMetrics metrics = QueryMetrics.builder()
            .timestamp(Instant.now())
            .eventType("user_session")
            .context(QueryMetrics.ExecutionContext.builder()
                .userSession(sessionId)
                .userId(userId)
                .businessOperation(action)
                .build())
            .build();
            
        metrics = enrichMetrics(metrics);
        logQueryExecution(metrics);
    }
    
    /**
     * 대시보드 접근 로깅
     */
    public void logDashboardAccess(String userId, String endpoint, String clientIp) {
        QueryMetrics metrics = QueryMetrics.builder()
            .timestamp(Instant.now())
            .eventType("dashboard_access")
            .context(QueryMetrics.ExecutionContext.builder()
                .userId(userId)
                .apiEndpoint(endpoint)
                .clientIp(clientIp)
                .businessOperation("dashboard_access")
                .build())
            .build();
            
        metrics = enrichMetrics(metrics);
        logQueryExecution(metrics);
    }
    
    /**
     * 메트릭에 기본 정보 추가
     */
    private QueryMetrics enrichMetrics(QueryMetrics metrics) {
        return metrics.toBuilder()
            .podName(podName)
            .namespace(namespace)
            .timestamp(metrics.getTimestamp() != null ? metrics.getTimestamp() : Instant.now())
            .build();
    }
    
    /**
     * 실제 로그 출력
     */
    private void writeLog(QueryMetrics metrics) {
        try {
            String jsonLog = objectMapper.writeValueAsString(metrics);
            
            // 구조화된 로그를 표준 출력으로 출력 (Kubernetes 로그 수집을 위함)
            System.out.println(KUBEDB_METRICS_PREFIX + jsonLog);
            
            // 또한 일반 로그로도 출력 (디버깅 및 모니터링 용)
            if (log.isDebugEnabled()) {
                log.debug("KubeDB Metrics: Event={}, QueryId={}, ExecutionTime={}ms", 
                    metrics.getEventType(),
                    metrics.getData() != null ? metrics.getData().getQueryId() : "N/A",
                    metrics.getData() != null ? metrics.getData().getExecutionTimeMs() : "N/A");
            }
            
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize QueryMetrics to JSON", e);
        }
    }
    
    /**
     * 비동기 로거 시작
     */
    private void startAsyncLogger() {
        logExecutor.submit(() -> {
            while (!Thread.currentThread().isInterrupted()) {
                try {
                    QueryMetrics metrics = logQueue.poll(1, TimeUnit.SECONDS);
                    if (metrics != null) {
                        writeLog(metrics);
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    log.info("Async logger interrupted, stopping...");
                    break;
                } catch (Exception e) {
                    log.error("Error in async logger", e);
                }
            }
        });
    }
    
    /**
     * 애플리케이션 종료 시 정리 작업
     */
    public void shutdown() {
        log.info("Shutting down StructuredLogger...");
        
        // 큐에 남은 로그들을 모두 출력
        QueryMetrics remaining;
        while ((remaining = logQueue.poll()) != null) {
            writeLog(remaining);
        }
        
        logExecutor.shutdown();
        try {
            if (!logExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                logExecutor.shutdownNow();
            }
        } catch (InterruptedException e) {
            logExecutor.shutdownNow();
            Thread.currentThread().interrupt();
        }
        
        log.info("StructuredLogger shutdown complete");
    }
    
    /**
     * 현재 로그 큐 크기 반환 (모니터링 용)
     */
    public int getQueueSize() {
        return logQueue.size();
    }
    
    /**
     * 로깅 통계 정보 반환
     */
    public LoggingStats getLoggingStats() {
        return LoggingStats.builder()
            .queueSize(logQueue.size())
            .queueCapacity(10000)
            .asyncLogging(asyncLogging)
            .slowQueryThreshold(slowQueryThresholdMs)
            .build();
    }
    
    @lombok.Data
    @lombok.Builder
    public static class LoggingStats {
        private int queueSize;
        private int queueCapacity;
        private boolean asyncLogging;
        private long slowQueryThreshold;
        private double queueUsageRatio;
        
        public double getQueueUsageRatio() {
            return (double) queueSize / queueCapacity;
        }
    }
}