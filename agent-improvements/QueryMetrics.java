package io.kubedb.monitor.agent.model;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Data;
import lombok.extern.jackson.Jacksonized;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * 데이터베이스 쿼리 실행 메트릭을 담는 모델 클래스
 * 제니퍼 스타일 대시보드를 위한 풍부한 메트릭 정보 포함
 */
@Data
@Builder
@Jacksonized
@JsonInclude(JsonInclude.Include.NON_NULL)
public class QueryMetrics {
    
    // 기본 정보
    @JsonProperty("timestamp")
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss.SSSXXX", timezone = "UTC")
    private Instant timestamp;
    
    @JsonProperty("pod_name")
    private String podName;
    
    @JsonProperty("namespace")
    private String namespace;
    
    @JsonProperty("event_type")
    private String eventType;
    
    // 쿼리 상세 정보
    @JsonProperty("data")
    private QueryData data;
    
    // 실행 컨텍스트 정보
    @JsonProperty("context")
    private ExecutionContext context;
    
    // 시스템 메트릭
    @JsonProperty("metrics")
    private SystemMetrics metrics;
    
    /**
     * 쿼리 실행 데이터
     */
    @Data
    @Builder
    @Jacksonized
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class QueryData {
        @JsonProperty("query_id")
        private String queryId;
        
        @JsonProperty("sql_hash")
        private String sqlHash;
        
        @JsonProperty("sql_pattern")
        private String sqlPattern;
        
        @JsonProperty("sql_type")
        private SqlType sqlType;
        
        @JsonProperty("table_names")
        private List<String> tableNames;
        
        @JsonProperty("execution_time_ms")
        private Long executionTimeMs;
        
        @JsonProperty("rows_affected")
        private Long rowsAffected;
        
        @JsonProperty("connection_id")
        private String connectionId;
        
        @JsonProperty("thread_name")
        private String threadName;
        
        @JsonProperty("memory_used_bytes")
        private Long memoryUsedBytes;
        
        @JsonProperty("cpu_time_ms")
        private Long cpuTimeMs;
        
        @JsonProperty("io_read_bytes")
        private Long ioReadBytes;
        
        @JsonProperty("io_write_bytes")
        private Long ioWriteBytes;
        
        @JsonProperty("lock_time_ms")
        private Long lockTimeMs;
        
        @JsonProperty("status")
        private ExecutionStatus status;
        
        @JsonProperty("error_code")
        private String errorCode;
        
        @JsonProperty("error_message")
        private String errorMessage;
        
        @JsonProperty("explain_plan")
        private Map<String, Object> explainPlan;
        
        @JsonProperty("stack_trace")
        private List<String> stackTrace;
        
        // 쿼리 복잡도 메트릭
        @JsonProperty("complexity_score")
        private Integer complexityScore;
        
        @JsonProperty("index_usage")
        private IndexUsageInfo indexUsage;
        
        @JsonProperty("cache_hit_ratio")
        private Double cacheHitRatio;
    }
    
    /**
     * 실행 컨텍스트 정보
     */
    @Data
    @Builder
    @Jacksonized
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class ExecutionContext {
        @JsonProperty("request_id")
        private String requestId;
        
        @JsonProperty("user_session")
        private String userSession;
        
        @JsonProperty("api_endpoint")
        private String apiEndpoint;
        
        @JsonProperty("business_operation")
        private String businessOperation;
        
        @JsonProperty("user_id")
        private String userId;
        
        @JsonProperty("client_ip")
        private String clientIp;
        
        @JsonProperty("user_agent")
        private String userAgent;
        
        @JsonProperty("trace_id")
        private String traceId;
        
        @JsonProperty("span_id")
        private String spanId;
        
        @JsonProperty("parent_span_id")
        private String parentSpanId;
    }
    
    /**
     * 시스템 메트릭
     */
    @Data
    @Builder
    @Jacksonized
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class SystemMetrics {
        // 커넥션 풀 정보
        @JsonProperty("connection_pool_active")
        private Integer connectionPoolActive;
        
        @JsonProperty("connection_pool_idle")
        private Integer connectionPoolIdle;
        
        @JsonProperty("connection_pool_max")
        private Integer connectionPoolMax;
        
        @JsonProperty("connection_pool_usage_ratio")
        private Double connectionPoolUsageRatio;
        
        // 메모리 정보
        @JsonProperty("heap_used_mb")
        private Long heapUsedMb;
        
        @JsonProperty("heap_max_mb")
        private Long heapMaxMb;
        
        @JsonProperty("heap_usage_ratio")
        private Double heapUsageRatio;
        
        @JsonProperty("non_heap_used_mb")
        private Long nonHeapUsedMb;
        
        // GC 정보
        @JsonProperty("gc_count")
        private Long gcCount;
        
        @JsonProperty("gc_time_ms")
        private Long gcTimeMs;
        
        @JsonProperty("gc_frequency")
        private Double gcFrequency;
        
        // CPU 정보
        @JsonProperty("cpu_usage_ratio")
        private Double cpuUsageRatio;
        
        @JsonProperty("process_cpu_time_ms")
        private Long processCpuTimeMs;
        
        // 스레드 정보
        @JsonProperty("thread_count")
        private Integer threadCount;
        
        @JsonProperty("peak_thread_count")
        private Integer peakThreadCount;
        
        // 클래스 로딩 정보
        @JsonProperty("loaded_class_count")
        private Integer loadedClassCount;
        
        @JsonProperty("unloaded_class_count")
        private Long unloadedClassCount;
    }
    
    /**
     * 인덱스 사용 정보
     */
    @Data
    @Builder
    @Jacksonized
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class IndexUsageInfo {
        @JsonProperty("indexes_used")
        private List<String> indexesUsed;
        
        @JsonProperty("full_table_scan")
        private Boolean fullTableScan;
        
        @JsonProperty("index_efficiency_score")
        private Double indexEfficiencyScore;
        
        @JsonProperty("missing_index_suggestions")
        private List<String> missingIndexSuggestions;
    }
    
    /**
     * SQL 쿼리 타입
     */
    public enum SqlType {
        SELECT, INSERT, UPDATE, DELETE, CREATE, DROP, ALTER, TRUNCATE, UNKNOWN
    }
    
    /**
     * 실행 상태
     */
    public enum ExecutionStatus {
        SUCCESS, ERROR, TIMEOUT, CANCELLED
    }
    
    /**
     * 빠른 에러 생성을 위한 팩토리 메서드
     */
    public static QueryMetrics error(Exception exception, String queryId, String sqlPattern) {
        return QueryMetrics.builder()
            .timestamp(Instant.now())
            .eventType("query_execution_error")
            .data(QueryData.builder()
                .queryId(queryId)
                .sqlPattern(sqlPattern)
                .status(ExecutionStatus.ERROR)
                .errorMessage(exception.getMessage())
                .stackTrace(getStackTrace(exception))
                .build())
            .build();
    }
    
    /**
     * 성공적인 쿼리 실행을 위한 팩토리 메서드
     */
    public static QueryMetrics success(String queryId, String sqlPattern, long executionTimeMs) {
        return QueryMetrics.builder()
            .timestamp(Instant.now())
            .eventType("query_execution")
            .data(QueryData.builder()
                .queryId(queryId)
                .sqlPattern(sqlPattern)
                .executionTimeMs(executionTimeMs)
                .status(ExecutionStatus.SUCCESS)
                .build())
            .build();
    }
    
    /**
     * 느린 쿼리인지 판단
     */
    public boolean isSlowQuery(long thresholdMs) {
        return data != null && 
               data.getExecutionTimeMs() != null && 
               data.getExecutionTimeMs() > thresholdMs;
    }
    
    /**
     * 에러 쿼리인지 판단
     */
    public boolean isErrorQuery() {
        return data != null && ExecutionStatus.ERROR.equals(data.getStatus());
    }
    
    /**
     * 복잡한 쿼리인지 판단 (여러 테이블 조인, 서브쿼리 등)
     */
    public boolean isComplexQuery() {
        return data != null && 
               data.getComplexityScore() != null && 
               data.getComplexityScore() > 5;
    }
    
    private static List<String> getStackTrace(Exception exception) {
        // 스택 트레이스를 문자열 리스트로 변환
        return java.util.Arrays.stream(exception.getStackTrace())
            .map(StackTraceElement::toString)
            .limit(10) // 상위 10개만 포함
            .collect(java.util.stream.Collectors.toList());
    }
}