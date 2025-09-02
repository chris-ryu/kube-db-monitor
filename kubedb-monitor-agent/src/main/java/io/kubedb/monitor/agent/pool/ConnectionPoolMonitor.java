package io.kubedb.monitor.agent.pool;

import io.kubedb.monitor.agent.pool.collectors.HikariPoolCollector;
import io.kubedb.monitor.agent.pool.collectors.PoolMetricsCollector;
import io.kubedb.monitor.agent.pool.collectors.TomcatPoolCollector;

import javax.sql.DataSource;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

/**
 * Connection Pool 모니터링 통합 관리자
 * 
 * 다양한 Connection Pool 구현체들을 감지하고 주기적으로 메트릭을 수집합니다.
 */
public class ConnectionPoolMonitor {
    private static final Logger logger = Logger.getLogger(ConnectionPoolMonitor.class.getName());
    
    private final WASConnectionPoolDetector detector;
    private final List<PoolMetricsCollector> collectors;
    private final ScheduledExecutorService scheduler;
    private final long metricsIntervalSeconds;
    
    private volatile boolean started = false;
    private volatile PoolMetrics lastCollectedMetrics = PoolMetrics.empty();
    
    // Peak 값 추적을 위한 필드들
    private volatile int peakActiveConnections = 0;
    private volatile long peakTimestamp = System.currentTimeMillis();
    private volatile long connectionRequestCount = 0;
    private volatile long lastConnectionRequestCount = 0;
    private volatile long lastMetricsTime = System.currentTimeMillis();
    
    public ConnectionPoolMonitor(long metricsIntervalSeconds) {
        this.metricsIntervalSeconds = metricsIntervalSeconds;
        this.detector = new WASConnectionPoolDetector();
        this.collectors = new ArrayList<>();
        this.scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "KubeDB-ConnectionPool-Monitor");
            t.setDaemon(true);
            return t;
        });
        
        // 지원하는 Connection Pool 수집기들 등록
        registerCollectors();
    }
    
    /**
     * 기본 30초 간격으로 모니터링하는 생성자
     */
    public ConnectionPoolMonitor() {
        this(30);
    }
    
    /**
     * 지원하는 Connection Pool 수집기들을 등록
     */
    private void registerCollectors() {
        collectors.add(new HikariPoolCollector());
        collectors.add(new TomcatPoolCollector());
        
        logger.info(String.format("[KubeDB] Connection Pool 수집기 등록 완료: %d개", collectors.size()));
    }
    
    /**
     * Connection Pool 모니터링 시작
     */
    public synchronized void start() {
        if (started) {
            logger.warning("[KubeDB] Connection Pool 모니터링이 이미 시작됨");
            return;
        }
        
        logger.info("[KubeDB] Connection Pool 모니터링 시작");
        
        // Connection Pool 감지
        detector.detectConnectionPools();
        
        // 주기적 메트릭 수집 스케줄링
        scheduler.scheduleAtFixedRate(this::collectAllPoolMetrics, 
                                    5, // 5초 후 시작
                                    metricsIntervalSeconds, 
                                    TimeUnit.SECONDS);
        
        started = true;
        
        // 초기 메트릭 수집
        collectAllPoolMetrics();
    }
    
    /**
     * 모든 감지된 Connection Pool에서 메트릭 수집
     */
    private void collectAllPoolMetrics() {
        try {
            List<DataSource> dataSources = detector.getDetectedDataSources();
            
            if (dataSources.isEmpty()) {
                logger.fine("[KubeDB] 감지된 Connection Pool이 없음");
                return;
            }
            
            for (DataSource dataSource : dataSources) {
                PoolMetrics rawMetrics = collectPoolMetrics(dataSource);
                if (rawMetrics != null && rawMetrics.getPoolType() != PoolType.UNKNOWN) {
                    // Peak 값 추적 및 고급 메트릭 계산
                    PoolMetrics enhancedMetrics = enhanceWithAdvancedMetrics(rawMetrics);
                    lastCollectedMetrics = enhancedMetrics;
                    logger.fine(String.format("[KubeDB] Pool 메트릭 수집 (고급): %s", enhancedMetrics));
                }
            }
            
        } catch (Exception e) {
            logger.warning(String.format("[KubeDB] Connection Pool 메트릭 수집 중 오류: %s", e.getMessage()));
        }
    }
    
    /**
     * 개별 DataSource에서 메트릭 수집
     */
    private PoolMetrics collectPoolMetrics(DataSource dataSource) {
        for (PoolMetricsCollector collector : collectors) {
            if (collector.supports(dataSource)) {
                try {
                    return collector.collect(dataSource);
                } catch (Exception e) {
                    logger.warning(String.format("[KubeDB] %s 메트릭 수집 실패: %s", 
                                 collector.getClass().getSimpleName(), e.getMessage()));
                }
            }
        }
        
        // 지원되지 않는 Pool 타입
        PoolType poolType = PoolType.detectFromClassName(dataSource.getClass().getName());
        logger.fine(String.format("[KubeDB] 지원되지 않는 Connection Pool: %s (%s)", 
                   poolType.getDisplayName(), dataSource.getClass().getName()));
        
        return PoolMetrics.empty();
    }
    
    /**
     * 기본 메트릭에 고급 모니터링 메트릭을 추가
     */
    private PoolMetrics enhanceWithAdvancedMetrics(PoolMetrics rawMetrics) {
        long currentTime = System.currentTimeMillis();
        int currentActive = rawMetrics.getActiveConnections();
        
        // 1. Peak Active Connections 추적
        if (currentActive > peakActiveConnections) {
            peakActiveConnections = currentActive;
            peakTimestamp = currentTime;
            logger.info(String.format("[KubeDB] 새로운 Peak Active Connections: %d (이전: %d)", 
                       currentActive, peakActiveConnections));
        }
        
        // 2. Connection Requests Per Second 계산
        long timeDelta = currentTime - lastMetricsTime;
        long connectionRequestsPerSecond = 0;
        
        if (timeDelta > 0) {
            long requestDelta = connectionRequestCount - lastConnectionRequestCount;
            connectionRequestsPerSecond = (requestDelta * 1000) / timeDelta;
            lastConnectionRequestCount = connectionRequestCount;
            lastMetricsTime = currentTime;
        }
        
        // 3. 평균 Connection Hold Time 계산 (단순화된 추정)
        double avgHoldTime = estimateAverageConnectionHoldTime(rawMetrics);
        
        // 4. 고급 메트릭이 포함된 새로운 PoolMetrics 생성
        return new PoolMetrics.Builder(rawMetrics.getPoolType(), rawMetrics.getPoolName())
                .activeConnections(rawMetrics.getActiveConnections())
                .idleConnections(rawMetrics.getIdleConnections())
                .maxConnections(rawMetrics.getMaxConnections())
                .waitingThreads(rawMetrics.getWaitingThreads())
                .averageCheckoutTime(rawMetrics.getAverageCheckoutTime())
                .totalConnectionsCreated(rawMetrics.getTotalConnectionsCreated())
                .totalConnectionsClosed(rawMetrics.getTotalConnectionsClosed())
                // 고급 메트릭 추가
                .peakActiveConnections(peakActiveConnections)
                .peakTimestamp(peakTimestamp)
                .connectionRequestsPerSecond(connectionRequestsPerSecond)
                .averageConnectionHoldTime(avgHoldTime)
                .build();
    }
    
    /**
     * 평균 Connection Hold Time 추정
     * (실제 구현에서는 Connection Proxy에서 더 정확한 측정이 필요)
     */
    private double estimateAverageConnectionHoldTime(PoolMetrics metrics) {
        // 단순화된 추정: 활성 Connection이 많을수록 Hold Time이 길 것으로 가정
        int active = metrics.getActiveConnections();
        int max = metrics.getMaxConnections();
        
        if (max == 0) {
            return 0.0;
        }
        
        // 기본 50ms에서 사용률에 따라 증가
        double baseTime = 50.0; // 50ms
        double usageRatio = (double) active / max;
        
        return baseTime * (1 + usageRatio * 2); // 최대 150ms까지
    }
    
    /**
     * Connection 요청 카운트 증가 (외부에서 호출)
     */
    public void recordConnectionRequest() {
        connectionRequestCount++;
    }
    
    /**
     * 가장 최근에 수집된 Pool 메트릭 반환
     */
    public PoolMetrics getLatestMetrics() {
        return lastCollectedMetrics;
    }
    
    /**
     * DataSource를 수동으로 등록 (JDBC 인터셉터에서 발견된 경우)
     */
    public void registerDataSource(DataSource dataSource) {
        detector.registerDataSource(dataSource);
        
        // 새로 등록된 DataSource의 메트릭을 즉시 수집
        if (started) {
            PoolMetrics metrics = collectPoolMetrics(dataSource);
            if (metrics != null && metrics.getPoolType() != PoolType.UNKNOWN) {
                lastCollectedMetrics = metrics;
                logger.info(String.format("[KubeDB] 새로 등록된 DataSource 메트릭 수집: %s", metrics));
            }
        }
    }
    
    /**
     * Connection Pool이 감지되었는지 확인
     */
    public boolean hasDetectedPools() {
        return detector.hasDetectedPools();
    }
    
    /**
     * 모니터링 중지
     */
    public synchronized void stop() {
        if (!started) {
            return;
        }
        
        logger.info("[KubeDB] Connection Pool 모니터링 중지");
        
        if (scheduler != null && !scheduler.isShutdown()) {
            scheduler.shutdown();
            try {
                if (!scheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                    scheduler.shutdownNow();
                }
            } catch (InterruptedException e) {
                scheduler.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
        
        started = false;
    }
    
    /**
     * 모니터링 시작 여부 확인
     */
    public boolean isStarted() {
        return started;
    }
}