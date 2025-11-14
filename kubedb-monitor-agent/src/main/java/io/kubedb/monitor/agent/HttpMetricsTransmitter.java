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
     * 장기 실행 트랜잭션 알림 전송 (기존 버전)
     */
    public void transmitLongRunningTransactionAlert(String transactionId, String connectionId, String threadName, long durationMs, long startTime) {
        transmitLongRunningTransactionAlert(transactionId, connectionId, threadName, durationMs, startTime, null, null, null);
    }
    
    /**
     * 장기 실행 트랜잭션 알림 전송 (쿼리 정보 포함)
     */
    public void transmitLongRunningTransactionAlert(String transactionId, String connectionId, String threadName, 
                                                  long durationMs, long startTime, String currentQuery, 
                                                  String storedProcedureName, java.util.List<?> queryHistory) {
        if (!shouldTransmit()) {
            return;
        }
        
        executor.submit(() -> {
            try {
                String json = createLongRunningTransactionAlertJson(transactionId, connectionId, threadName, 
                                                                   durationMs, startTime, currentQuery, storedProcedureName, queryHistory);
                                                                   
                // 디버깅: 실제 전송되는 JSON 및 쿼리 정보 로그 출력
                logger.info("[KubeDB] 🔍 Long-running transaction alert JSON: currentQuery={}, storedProcedure={}, historySize={}", 
                           currentQuery != null ? currentQuery.substring(0, Math.min(50, currentQuery.length())) : "null",
                           storedProcedureName,
                           queryHistory != null ? queryHistory.size() : "null");
                logger.debug("[KubeDB] 🔍 Full long-running transaction JSON: {}", json);
                           
                sendHttpPost(json);
                logger.info("[KubeDB] Long-running transaction alert transmitted: {} ms (with query info)", durationMs);
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
                // 디버깅: 전송하는 JSON 구조 로그 출력
                logger.info("[KubeDB] 🔍 Transmitting system metrics JSON: {}", json);
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
     * 장기 실행 트랜잭션 알림 JSON 생성 (기존 버전)
     */
    private String createLongRunningTransactionAlertJson(String transactionId, String connectionId, String threadName, long durationMs, long startTime) {
        return createLongRunningTransactionAlertJson(transactionId, connectionId, threadName, durationMs, startTime, null, null, null);
    }
    
    /**
     * 장기 실행 트랜잭션 알림 JSON 생성 (쿼리 정보 포함)
     */
    private String createLongRunningTransactionAlertJson(String transactionId, String connectionId, String threadName, 
                                                       long durationMs, long startTime, String currentQuery, 
                                                       String storedProcedureName, java.util.List<?> queryHistory) {
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME) + "Z";
        
        // 쿼리 정보 추가 필드 생성
        String queryInfoFields = "";
        if (currentQuery != null || storedProcedureName != null || (queryHistory != null && !queryHistory.isEmpty())) {
            StringBuilder queryFields = new StringBuilder();
            
            if (currentQuery != null) {
                String maskedCurrentQuery = config.isMaskSqlParams() ? maskSqlParameters(currentQuery) : currentQuery;
                queryFields.append(",\"current_query\": \"")
                           .append(maskedCurrentQuery.replace("\"", "\\\"").replace("\n", "\\n"))
                           .append("\"");
            }
            
            if (storedProcedureName != null) {
                queryFields.append(",\"stored_procedure\": \"").append(storedProcedureName).append("\"");
            }
            
            // 실행 중인 활성 쿼리 정보 조회 (Connection ID 기반으로 개선)
            try {
                // 방법 1: Connection ID로 ActiveQueryInfo 조회 (가장 정확)
                Object activeQueryInfo = UniversalJDBCInterceptor.getActiveQueryByConnection(connectionId);
                
                // 방법 1-2: Connection ID가 "conn-" 형태인 경우 모든 활성 쿼리에서 매칭 시도
                if (activeQueryInfo == null && connectionId.startsWith("conn-")) {
                    // Thread ID 기반으로 생성된 Connection ID인 경우, 실제 Connection과 매핑
                    activeQueryInfo = findActiveQueryByAnyConnection();
                    if (activeQueryInfo != null) {
                        logger.debug("[KubeDB] Thread 기반 Connection ID {}에서 활성 쿼리 대체 매칭 성공", connectionId);
                    }
                }
                
                if (activeQueryInfo != null) {
                    // ActiveQueryInfo에서 SQL 정보 추출
                    String activeSql = extractSqlFromActiveQueryInfo(activeQueryInfo);
                    if (activeSql != null && !activeSql.isEmpty() && !activeSql.equals("SQL data collection in progress...")) {
                        String maskedActiveSql = config.isMaskSqlParams() ? maskSqlParameters(activeSql) : activeSql;
                        queryFields.append(",\"current_active_query\": \"")
                                   .append(maskedActiveSql.replace("\"", "\\\"").replace("\n", "\\n"))
                                   .append("\"");
                        
                        // SQL 타입도 추가
                        String sqlType = extractSqlType(activeSql);
                        queryFields.append(",\"active_query_type\": \"").append(sqlType).append("\"");
                        
                        logger.info("[KubeDB] 🔍 Connection ID로 활성 쿼리 발견: {} (Connection: {})", 
                                   activeSql.substring(0, Math.min(100, activeSql.length())), connectionId);
                    } else {
                        logger.debug("[KubeDB] ⚠️ Connection ID로 SQL 추출 실패, Thread ID 방식 시도");
                        // 방법 2: Thread Name으로 ActiveQueryInfo 조회 (백업)
                        Object threadBasedQuery = UniversalJDBCInterceptor.getActiveQueryByThread(threadName);
                        if (threadBasedQuery != null) {
                            String threadSql = extractSqlFromActiveQueryInfo(threadBasedQuery);
                            if (threadSql != null && !threadSql.isEmpty() && !threadSql.equals("SQL data collection in progress...")) {
                                String maskedThreadSql = config.isMaskSqlParams() ? maskSqlParameters(threadSql) : threadSql;
                                queryFields.append(",\"current_active_query\": \"")
                                           .append(maskedThreadSql.replace("\"", "\\\"").replace("\n", "\\n"))
                                           .append("\"");
                                
                                String sqlType = extractSqlType(threadSql);
                                queryFields.append(",\"active_query_type\": \"").append(sqlType).append("\"");
                                
                                logger.info("[KubeDB] 🔍 Thread Name으로 활성 쿼리 발견: {} (Thread: {})", 
                                           threadSql.substring(0, Math.min(100, threadSql.length())), threadName);
                            }
                        }
                    }
                } else {
                    logger.debug("[KubeDB] ⚠️ Connection ID {}로 ActiveQuery 조회 결과 없음", connectionId);
                    
                    // 방법 2: Thread Name으로 ActiveQueryInfo 조회 (백업)
                    Object threadBasedQuery = UniversalJDBCInterceptor.getActiveQueryByThread(threadName);
                    if (threadBasedQuery != null) {
                        String threadSql = extractSqlFromActiveQueryInfo(threadBasedQuery);
                        if (threadSql != null && !threadSql.isEmpty() && !threadSql.equals("SQL data collection in progress...")) {
                            String maskedThreadSql = config.isMaskSqlParams() ? maskSqlParameters(threadSql) : threadSql;
                            queryFields.append(",\"current_active_query\": \"")
                                       .append(maskedThreadSql.replace("\"", "\\\"").replace("\n", "\\n"))
                                       .append("\"");
                            
                            String sqlType = extractSqlType(threadSql);
                            queryFields.append(",\"active_query_type\": \"").append(sqlType).append("\"");
                            
                            logger.info("[KubeDB] 🔍 Thread Name으로 활성 쿼리 발견 (백업 방식): {} (Thread: {})", 
                                       threadSql.substring(0, Math.min(100, threadSql.length())), threadName);
                        }
                    } else {
                        logger.warn("[KubeDB] ❌ Connection ID와 Thread Name 모두로 ActiveQuery 조회 실패: conn={}, thread={}", 
                                   connectionId, threadName);
                    }
                }
                
                // 백업으로 쿼리 히스토리도 시도
                java.util.List<?> realHistory = UniversalJDBCInterceptor.getTransactionHistory(connectionId);
                if (realHistory != null && !realHistory.isEmpty()) {
                    queryFields.append(",\"query_history\": [");
                    for (int i = 0; i < Math.min(realHistory.size(), 5); i++) { // 최대 5개로 줄임
                        if (i > 0) queryFields.append(",");
                        Object entry = realHistory.get(i);
                        String historySql = extractSqlFromHistoryEntry(entry);
                        String maskedHistorySql = config.isMaskSqlParams() ? maskSqlParameters(historySql) : historySql;
                        queryFields.append("{\"query\": \"").append(maskedHistorySql.replace("\"", "\\\"")).append("\"}");
                    }
                    queryFields.append("]");
                }
            } catch (Exception e) {
                logger.warn("[KubeDB] 활성 쿼리 정보 조회 실패: {}", e.getMessage());
                // 실패시 기존 방식으로 fallback
                if (queryHistory != null && !queryHistory.isEmpty()) {
                    queryFields.append(",\"query_history\": [");
                    for (int i = 0; i < Math.min(queryHistory.size(), 3); i++) {
                        if (i > 0) queryFields.append(",");
                        queryFields.append("{\"query\": \"history_query_").append(i).append("\"}");
                    }
                    queryFields.append("]");
                }
            }
            
            queryInfoFields = queryFields.toString();
        }
        
        // 실제 SQL 쿼리 추출 로직 개선
        String actualSqlPattern = "Long running transaction";
        
        // 1순위: 매개변수로 전달된 currentQuery 사용
        if (currentQuery != null && !currentQuery.trim().isEmpty()) {
            actualSqlPattern = currentQuery.replace("\"", "\\\"").replace("\n", "\\n");
        } 
        // 2순위: Connection ID 기반으로 활성 쿼리 조회
        else {
            try {
                Object activeQueryInfo = UniversalJDBCInterceptor.getActiveQueryByConnection(connectionId);
                if (activeQueryInfo != null) {
                    String activeSql = extractSqlFromActiveQueryInfo(activeQueryInfo);
                    if (activeSql != null && !activeSql.trim().isEmpty() && !activeSql.equals("SQL data collection in progress...")) {
                        actualSqlPattern = activeSql.replace("\"", "\\\"").replace("\n", "\\n");
                        logger.info("[KubeDB] 🔍 Connection ID {}에서 실제 SQL 패턴 추출: {}", 
                                   connectionId, activeSql.substring(0, Math.min(50, activeSql.length())));
                    }
                }
                
                // 3순위: Thread Name 기반으로 활성 쿼리 조회 (백업)
                if (actualSqlPattern.equals("Long running transaction")) {
                    Object threadBasedQuery = UniversalJDBCInterceptor.getActiveQueryByThread(threadName);
                    if (threadBasedQuery != null) {
                        String threadSql = extractSqlFromActiveQueryInfo(threadBasedQuery);
                        if (threadSql != null && !threadSql.trim().isEmpty() && !threadSql.equals("SQL data collection in progress...")) {
                            actualSqlPattern = threadSql.replace("\"", "\\\"").replace("\n", "\\n");
                            logger.info("[KubeDB] 🔍 Thread {}에서 실제 SQL 패턴 추출: {}", 
                                       threadName, threadSql.substring(0, Math.min(50, threadSql.length())));
                        }
                    }
                }
            } catch (Exception e) {
                logger.warn("[KubeDB] SQL 패턴 추출 실패, 기본값 사용: {}", e.getMessage());
            }
        }

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
                "\"status\": \"active\"," +
                "\"sql_pattern\": \"%s\"" +
                "%s" + // 쿼리 정보 필드
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
            startTime,
            actualSqlPattern,
            queryInfoFields
        );
    }
    
    /**
     * 시스템 메트릭 전용 JSON 생성 (고급 Connection Pool 메트릭 포함)
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
                "\"total_connections\": %d," +
                // 고급 Connection Pool 메트릭 추가
                "\"connection_pool_peak_active\": %d," +
                "\"connection_pool_peak_timestamp\": %d," +
                "\"connection_pool_requests_per_second\": %d," +
                "\"connection_pool_health_score\": %d," +
                "\"connection_pool_average_hold_time\": %.2f," +
                "\"connection_pool_waiting_threads\": %d" +
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
            poolMetrics.getTotalConnections(),
            // 고급 메트릭 값 추가 (null-safe)
            poolMetrics.getPeakActiveConnections() != null ? poolMetrics.getPeakActiveConnections() : 0,
            poolMetrics.getPeakTimestamp() != null ? poolMetrics.getPeakTimestamp() : 0,
            poolMetrics.getConnectionRequestsPerSecond() != null ? poolMetrics.getConnectionRequestsPerSecond() : 0,
            poolMetrics.getConnectionPoolHealth() != null ? poolMetrics.getConnectionPoolHealth() : 0,
            poolMetrics.getAverageConnectionHoldTime() != null ? poolMetrics.getAverageConnectionHoldTime() : 0.0,
            poolMetrics.getWaitingThreads() != null ? poolMetrics.getWaitingThreads() : 0
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
    
    /**
     * QueryHistoryEntry 객체에서 SQL 추출
     */
    private String extractSqlFromHistoryEntry(Object entry) {
        try {
            // Reflection으로 sql 필드 접근
            java.lang.reflect.Field sqlField = entry.getClass().getDeclaredField("sql");
            sqlField.setAccessible(true);
            String sql = (String) sqlField.get(entry);
            return sql != null ? sql : "Unknown SQL";
        } catch (Exception e) {
            return "SQL extraction failed: " + e.getMessage();
        }
    }
    
    /**
     * ActiveQueryInfo 객체에서 SQL 추출
     */
    private String extractSqlFromActiveQueryInfo(Object activeQueryInfo) {
        try {
            // Reflection으로 sql 필드 접근
            java.lang.reflect.Field sqlField = activeQueryInfo.getClass().getDeclaredField("sql");
            sqlField.setAccessible(true);
            String sql = (String) sqlField.get(activeQueryInfo);
            return sql != null ? sql : "No SQL data available";
        } catch (Exception e) {
            logger.debug("[KubeDB] ActiveQueryInfo SQL 추출 실패: {}", e.getMessage());
            return "SQL extraction failed";
        }
    }
    
    /**
     * 현재 활성화된 쿼리 중 아무거나 하나 반환 (Connection ID 매핑 문제 해결용)
     */
    private Object findActiveQueryByAnyConnection() {
        try {
            // UniversalJDBCInterceptor에서 활성 쿼리 목록 가져오기
            return UniversalJDBCInterceptor.getAnyActiveQueryInfo();
        } catch (Exception e) {
            logger.debug("[KubeDB] 활성 쿼리 대체 매칭 실패: {}", e.getMessage());
            return null;
        }
    }
}