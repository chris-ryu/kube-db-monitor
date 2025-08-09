package io.kubedb.monitor.agent.profiler;

import io.kubedb.monitor.agent.model.QueryMetrics;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.management.MBeanServer;
import javax.management.ObjectName;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.ThreadMXBean;
import java.lang.management.GarbageCollectorMXBean;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 데이터베이스 성능 프로파일링을 위한 클래스
 * 제니퍼 스타일의 상세한 성능 메트릭 수집
 */
@Slf4j
@Component
public class PerformanceProfiler {
    
    // 시스템 모니터링을 위한 MXBean들
    private final MemoryMXBean memoryMXBean = ManagementFactory.getMemoryMXBean();
    private final ThreadMXBean threadMXBean = ManagementFactory.getThreadMXBean();
    private final List<GarbageCollectorMXBean> gcMXBeans = ManagementFactory.getGarbageCollectorMXBeans();
    
    // SQL 패턴 추출을 위한 정규식
    private static final Pattern TABLE_PATTERN = Pattern.compile(
        "(?i)(?:FROM|JOIN|UPDATE|INTO)\\s+([\\w_]+)", Pattern.CASE_INSENSITIVE);
    private static final Pattern SQL_TYPE_PATTERN = Pattern.compile(
        "^\\s*(SELECT|INSERT|UPDATE|DELETE|CREATE|DROP|ALTER|TRUNCATE)", Pattern.CASE_INSENSITIVE);
    
    // 쿼리 복잡도 계산을 위한 패턴들
    private static final Map<Pattern, Integer> COMPLEXITY_PATTERNS = Map.of(
        Pattern.compile("(?i)\\bJOIN\\b"), 2,
        Pattern.compile("(?i)\\bSUBQUERY\\b|\\(\\s*SELECT"), 3,
        Pattern.compile("(?i)\\bUNION\\b"), 2,
        Pattern.compile("(?i)\\bGROUP\\s+BY\\b"), 2,
        Pattern.compile("(?i)\\bORDER\\s+BY\\b"), 1,
        Pattern.compile("(?i)\\bHAVING\\b"), 2
    );
    
    // 성능 통계를 위한 카운터들
    private final Map<String, AtomicLong> queryCounters = new ConcurrentHashMap<>();
    private final Map<String, AtomicLong> executionTimes = new ConcurrentHashMap<>();
    private final AtomicLong totalQueries = new AtomicLong(0);
    private final AtomicLong totalErrors = new AtomicLong(0);
    
    @Value("${kubedb.monitor.profiler.enabled:true}")
    private boolean profilerEnabled;
    
    @Value("${kubedb.monitor.profiler.detailed-metrics:true}")
    private boolean detailedMetrics;
    
    @Value("${kubedb.monitor.profiler.explain-plan:false}")
    private boolean collectExplainPlan;
    
    /**
     * 쿼리 실행을 프로파일링하고 상세한 메트릭을 수집
     */
    public QueryMetrics profileQuery(String sql, String connectionId, Supplier<Object> queryExecution) {
        if (!profilerEnabled) {
            queryExecution.get();
            return null;
        }
        
        String queryId = generateQueryId();
        long startTime = System.nanoTime();
        long startMemory = getCurrentMemoryUsage();
        long startCpuTime = getCurrentThreadCpuTime();
        
        QueryMetrics.QueryData.QueryDataBuilder dataBuilder = QueryMetrics.QueryData.builder()
            .queryId(queryId)
            .connectionId(connectionId)
            .threadName(Thread.currentThread().getName());
        
        try {
            // SQL 분석
            analyzeSql(sql, dataBuilder);
            
            // 쿼리 실행
            Object result = queryExecution.get();
            
            // 실행 후 메트릭 계산
            long executionTimeMs = (System.nanoTime() - startTime) / 1_000_000;
            long memoryUsed = getCurrentMemoryUsage() - startMemory;
            long cpuTimeUsed = getCurrentThreadCpuTime() - startCpuTime;
            
            // 결과 분석
            long rowsAffected = analyzeResult(result);
            
            // 메트릭 데이터 설정
            dataBuilder
                .executionTimeMs(executionTimeMs)
                .memoryUsedBytes(memoryUsed)
                .cpuTimeMs(cpuTimeUsed / 1_000_000) // 나노초를 밀리초로 변환
                .rowsAffected(rowsAffected)
                .status(QueryMetrics.ExecutionStatus.SUCCESS);
            
            // 상세 메트릭 수집
            if (detailedMetrics) {
                collectDetailedMetrics(dataBuilder, sql);
            }
            
            // 통계 업데이트
            updateStatistics(dataBuilder.build());
            
            return createQueryMetrics(queryId, dataBuilder);
            
        } catch (Exception e) {
            long executionTimeMs = (System.nanoTime() - startTime) / 1_000_000;
            
            dataBuilder
                .executionTimeMs(executionTimeMs)
                .status(QueryMetrics.ExecutionStatus.ERROR)
                .errorMessage(e.getMessage())
                .stackTrace(getStackTrace(e));
            
            totalErrors.incrementAndGet();
            
            return createErrorMetrics(queryId, dataBuilder, e);
        }
    }
    
    /**
     * 커넥션 풀 메트릭 수집
     */
    public QueryMetrics.SystemMetrics collectConnectionPoolMetrics(Connection connection) {
        try {
            QueryMetrics.SystemMetrics.SystemMetricsBuilder builder = 
                QueryMetrics.SystemMetrics.builder();
            
            // HikariCP 메트릭 수집 (JMX를 통해)
            MBeanServer mBeanServer = ManagementFactory.getPlatformMBeanServer();
            
            try {
                ObjectName poolName = new ObjectName("com.zaxxer.hikari:type=Pool (HikariPool-1)");
                
                Integer activeConnections = (Integer) mBeanServer.getAttribute(poolName, "ActiveConnections");
                Integer idleConnections = (Integer) mBeanServer.getAttribute(poolName, "IdleConnections");
                Integer totalConnections = (Integer) mBeanServer.getAttribute(poolName, "TotalConnections");
                Integer maxPoolSize = (Integer) mBeanServer.getAttribute(poolName, "MaximumPoolSize");
                
                builder
                    .connectionPoolActive(activeConnections)
                    .connectionPoolIdle(idleConnections)
                    .connectionPoolMax(maxPoolSize)
                    .connectionPoolUsageRatio((double) totalConnections / maxPoolSize);
                    
            } catch (Exception e) {
                log.debug("Could not collect HikariCP metrics via JMX: {}", e.getMessage());
            }
            
            // 시스템 메트릭 추가
            addSystemMetrics(builder);
            
            return builder.build();
            
        } catch (Exception e) {
            log.warn("Failed to collect connection pool metrics", e);
            return null;
        }
    }
    
    /**
     * SQL 분석 및 패턴 추출
     */
    private void analyzeSql(String sql, QueryMetrics.QueryData.QueryDataBuilder builder) {
        if (sql == null || sql.trim().isEmpty()) {
            return;
        }
        
        // SQL 해시 생성
        String sqlHash = generateSqlHash(sql);
        builder.sqlHash(sqlHash);
        
        // SQL 패턴 생성 (파라미터를 ?로 치환)
        String sqlPattern = generateSqlPattern(sql);
        builder.sqlPattern(sqlPattern);
        
        // SQL 타입 추출
        QueryMetrics.SqlType sqlType = extractSqlType(sql);
        builder.sqlType(sqlType);
        
        // 테이블명 추출
        List<String> tableNames = extractTableNames(sql);
        builder.tableNames(tableNames);
        
        // 복잡도 점수 계산
        Integer complexityScore = calculateComplexityScore(sql);
        builder.complexityScore(complexityScore);
    }
    
    /**
     * 상세 메트릭 수집
     */
    private void collectDetailedMetrics(QueryMetrics.QueryData.QueryDataBuilder builder, String sql) {
        try {
            // I/O 메트릭 (실제로는 추정값)
            builder
                .ioReadBytes(estimateIoRead(sql))
                .ioWriteBytes(estimateIoWrite(sql))
                .lockTimeMs(estimateLockTime(sql));
            
            // 캐시 히트율 계산 (모의 계산)
            builder.cacheHitRatio(calculateCacheHitRatio(sql));
            
            // 인덱스 사용 정보 (모의 데이터)
            QueryMetrics.IndexUsageInfo indexUsage = QueryMetrics.IndexUsageInfo.builder()
                .fullTableScan(isFullTableScan(sql))
                .indexEfficiencyScore(calculateIndexEfficiency(sql))
                .indexesUsed(getUsedIndexes(sql))
                .build();
            builder.indexUsage(indexUsage);
            
        } catch (Exception e) {
            log.debug("Failed to collect detailed metrics", e);
        }
    }
    
    /**
     * 시스템 메트릭 추가
     */
    private void addSystemMetrics(QueryMetrics.SystemMetrics.SystemMetricsBuilder builder) {
        // 메모리 메트릭
        long heapUsed = memoryMXBean.getHeapMemoryUsage().getUsed() / (1024 * 1024);
        long heapMax = memoryMXBean.getHeapMemoryUsage().getMax() / (1024 * 1024);
        long nonHeapUsed = memoryMXBean.getNonHeapMemoryUsage().getUsed() / (1024 * 1024);
        
        builder
            .heapUsedMb(heapUsed)
            .heapMaxMb(heapMax)
            .heapUsageRatio((double) heapUsed / heapMax)
            .nonHeapUsedMb(nonHeapUsed);
        
        // 스레드 메트릭
        builder
            .threadCount(threadMXBean.getThreadCount())
            .peakThreadCount(threadMXBean.getPeakThreadCount());
        
        // GC 메트릭
        long totalGcCount = 0;
        long totalGcTime = 0;
        for (GarbageCollectorMXBean gcBean : gcMXBeans) {
            totalGcCount += gcBean.getCollectionCount();
            totalGcTime += gcBean.getCollectionTime();
        }
        
        builder
            .gcCount(totalGcCount)
            .gcTimeMs(totalGcTime);
        
        // 클래스 로딩 메트릭
        builder
            .loadedClassCount(ManagementFactory.getClassLoadingMXBean().getLoadedClassCount())
            .unloadedClassCount(ManagementFactory.getClassLoadingMXBean().getUnloadedClassCount());
        
        // CPU 사용률 (프로세스 레벨)
        try {
            MBeanServer mBeanServer = ManagementFactory.getPlatformMBeanServer();
            ObjectName osName = new ObjectName("java.lang:type=OperatingSystem");
            Double processCpuLoad = (Double) mBeanServer.getAttribute(osName, "ProcessCpuLoad");
            if (processCpuLoad != null && processCpuLoad >= 0) {
                builder.cpuUsageRatio(processCpuLoad);
            }
        } catch (Exception e) {
            log.debug("Could not collect CPU metrics", e);
        }
    }
    
    /**
     * SQL 패턴 생성 (파라미터를 ?로 치환)
     */
    private String generateSqlPattern(String sql) {
        return sql.replaceAll("'[^']*'", "'?'")
                 .replaceAll("\\b\\d+\\b", "?")
                 .replaceAll("\\s+", " ")
                 .trim();
    }
    
    /**
     * SQL 해시 생성
     */
    private String generateSqlHash(String sql) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(sql.getBytes(StandardCharsets.UTF_8));
            StringBuilder hexString = new StringBuilder();
            
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) {
                    hexString.append('0');
                }
                hexString.append(hex);
            }
            
            return "sha256:" + hexString.toString();
        } catch (NoSuchAlgorithmException e) {
            return "hash-error:" + sql.hashCode();
        }
    }
    
    /**
     * SQL 타입 추출
     */
    private QueryMetrics.SqlType extractSqlType(String sql) {
        Matcher matcher = SQL_TYPE_PATTERN.matcher(sql);
        if (matcher.find()) {
            try {
                return QueryMetrics.SqlType.valueOf(matcher.group(1).toUpperCase());
            } catch (IllegalArgumentException e) {
                return QueryMetrics.SqlType.UNKNOWN;
            }
        }
        return QueryMetrics.SqlType.UNKNOWN;
    }
    
    /**
     * 테이블명 추출
     */
    private List<String> extractTableNames(String sql) {
        List<String> tableNames = new ArrayList<>();
        Matcher matcher = TABLE_PATTERN.matcher(sql);
        
        while (matcher.find()) {
            String tableName = matcher.group(1);
            if (!tableNames.contains(tableName)) {
                tableNames.add(tableName);
            }
        }
        
        return tableNames;
    }
    
    /**
     * 쿼리 복잡도 점수 계산
     */
    private Integer calculateComplexityScore(String sql) {
        int score = 1; // 기본 점수
        
        for (Map.Entry<Pattern, Integer> entry : COMPLEXITY_PATTERNS.entrySet()) {
            Matcher matcher = entry.getKey().matcher(sql);
            while (matcher.find()) {
                score += entry.getValue();
            }
        }
        
        return score;
    }
    
    // Utility methods
    private String generateQueryId() {
        return "q_" + System.currentTimeMillis() + "_" + Thread.currentThread().getId();
    }
    
    private long getCurrentMemoryUsage() {
        return memoryMXBean.getHeapMemoryUsage().getUsed();
    }
    
    private long getCurrentThreadCpuTime() {
        return threadMXBean.isCurrentThreadCpuTimeSupported() ? 
            threadMXBean.getCurrentThreadCpuTime() : 0;
    }
    
    private long analyzeResult(Object result) {
        if (result instanceof Number) {
            return ((Number) result).longValue();
        }
        if (result instanceof Collection) {
            return ((Collection<?>) result).size();
        }
        return 1L;
    }
    
    private void updateStatistics(QueryMetrics.QueryData data) {
        totalQueries.incrementAndGet();
        
        if (data.getSqlType() != null) {
            queryCounters.computeIfAbsent(data.getSqlType().name(), k -> new AtomicLong(0))
                          .incrementAndGet();
        }
        
        if (data.getExecutionTimeMs() != null) {
            String sqlHash = data.getSqlHash();
            executionTimes.computeIfAbsent(sqlHash, k -> new AtomicLong(0))
                          .addAndGet(data.getExecutionTimeMs());
        }
    }
    
    private QueryMetrics createQueryMetrics(String queryId, QueryMetrics.QueryData.QueryDataBuilder builder) {
        return QueryMetrics.builder()
            .timestamp(Instant.now())
            .eventType("query_execution")
            .data(builder.build())
            .metrics(collectConnectionPoolMetrics(null))
            .build();
    }
    
    private QueryMetrics createErrorMetrics(String queryId, QueryMetrics.QueryData.QueryDataBuilder builder, Exception e) {
        return QueryMetrics.builder()
            .timestamp(Instant.now())
            .eventType("query_error")
            .data(builder.build())
            .build();
    }
    
    private List<String> getStackTrace(Exception e) {
        return Arrays.stream(e.getStackTrace())
            .map(StackTraceElement::toString)
            .limit(10)
            .collect(java.util.stream.Collectors.toList());
    }
    
    // 추정 메서드들 (실제 구현에서는 더 정교한 로직 필요)
    private Long estimateIoRead(String sql) { return 1024L; }
    private Long estimateIoWrite(String sql) { return 512L; }
    private Long estimateLockTime(String sql) { return 1L; }
    private Double calculateCacheHitRatio(String sql) { return 0.85; }
    private Boolean isFullTableScan(String sql) { return !sql.toLowerCase().contains("where"); }
    private Double calculateIndexEfficiency(String sql) { return 0.9; }
    private List<String> getUsedIndexes(String sql) { return Arrays.asList("idx_primary"); }
    
    /**
     * 성능 통계 조회
     */
    public PerformanceStats getPerformanceStats() {
        return PerformanceStats.builder()
            .totalQueries(totalQueries.get())
            .totalErrors(totalErrors.get())
            .errorRate((double) totalErrors.get() / Math.max(totalQueries.get(), 1))
            .queryCounters(new HashMap<>(queryCounters.entrySet().stream()
                .collect(java.util.stream.Collectors.toMap(
                    Map.Entry::getKey,
                    e -> e.getValue().get()))))
            .build();
    }
    
    @lombok.Data
    @lombok.Builder
    public static class PerformanceStats {
        private long totalQueries;
        private long totalErrors;
        private double errorRate;
        private Map<String, Long> queryCounters;
    }
}