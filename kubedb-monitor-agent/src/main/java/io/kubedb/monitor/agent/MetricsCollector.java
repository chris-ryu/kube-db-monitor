package io.kubedb.monitor.agent;

import io.kubedb.monitor.agent.pool.ConnectionPoolMonitor;
import io.kubedb.monitor.agent.pool.PoolMetrics;

import javax.sql.DataSource;
import java.sql.SQLException;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

import io.kubedb.monitor.agent.pool.ConnectionPoolMonitor;
import io.kubedb.monitor.agent.pool.PoolMetrics;
import java.util.Map;

/**
 * 프록시 기반 메트릭스 수집기
 * 
 * ASM 바이트코드 변환 없이 프록시를 통해 JDBC 메트릭스를 수집합니다.
 * PostgreSQL 드라이버 로딩 간섭 없이 안전한 모니터링을 제공합니다.
 */
public class MetricsCollector {
    private static final Logger logger = Logger.getLogger(MetricsCollector.class.getName());
    
    private final AgentConfig config;
    private final HttpMetricsTransmitter transmitter;
    private final ConnectionPoolMonitor poolMonitor;
    
    // 기본 메트릭스 카운터들
    private final AtomicLong queryCount = new AtomicLong(0);
    private final AtomicLong commitCount = new AtomicLong(0);
    private final AtomicLong rollbackCount = new AtomicLong(0);
    private final AtomicLong errorCount = new AtomicLong(0);
    private final AtomicLong connectionCloseCount = new AtomicLong(0);
    
    // 성능 메트릭스  
    private volatile long maxQueryTime = 0;
    
    // Long-running transaction 추적
    private final Map<String, TransactionInfo> activeTransactions = new ConcurrentHashMap<>();
    private final ScheduledExecutorService transactionMonitor = Executors.newSingleThreadScheduledExecutor(
        r -> {
            Thread t = new Thread(r, "KubeDB-Transaction-Monitor");
            t.setDaemon(true);
            return t;
        }
    );
    
    public MetricsCollector(AgentConfig config) {
        this.config = config;
        this.transmitter = new HttpMetricsTransmitter(config);
        this.poolMonitor = new ConnectionPoolMonitor(2); // 2초 간격으로 Pool 메트릭 수집 (개선)
        
        // Long-running transaction 모니터링 스케줄러 시작 (3초마다 체크 - 개선)
        transactionMonitor.scheduleAtFixedRate(this::checkLongRunningTransactions, 3, 3, TimeUnit.SECONDS);
        
        // Connection Pool 메트릭 주기적 전송 (5초마다 - 개선)
        transactionMonitor.scheduleAtFixedRate(this::sendConnectionPoolMetrics, 5, 5, TimeUnit.SECONDS);
        
        // Connection Pool 모니터링 시작
        poolMonitor.start();
        
        logger.info("[KubeDB] MetricsCollector 초기화됨 (Proxy Mode with HTTP transmission, Long-running transaction monitoring, and Connection Pool monitoring)");
    }
    
    /**
     * SQL 쿼리 실행 기록
     */
    public void recordQuery(String sql, long executionTimeNanos) {
        recordQuery(sql, executionTimeNanos, "unknown-connection", Thread.currentThread().getName());
    }
    
    /**
     * SQL 쿼리 실행 기록 (연결 정보 포함)
     */
    public void recordQuery(String sql, long executionTimeNanos, String connectionId, String threadName) {
        if (!config.isEnabled()) {
            return;
        }
        
        queryCount.incrementAndGet();
        
        long executionTimeMs = executionTimeNanos / 1_000_000;
        
        if (executionTimeMs > maxQueryTime) {
            maxQueryTime = executionTimeMs;
        }
        
        // Connection 요청 추적 (새로운 기능)
        if (poolMonitor != null) {
            poolMonitor.recordConnectionRequest();
        }
        
        // HTTP 전송 (Connection Pool 메트릭 포함)
        PoolMetrics poolMetrics = poolMonitor.getLatestMetrics();
        transmitter.transmitQueryMetric(sql, executionTimeMs, connectionId, threadName, poolMetrics);
        
        // 느린 쿼리 감지
        if (executionTimeMs > config.getSlowQueryThresholdMs()) {
            logger.info(String.format("[KubeDB] 느린 쿼리 감지 (%dms): %s", 
                       executionTimeMs, 
                       sql.length() > 100 ? sql.substring(0, 100) + "..." : sql));
        }
        
        logger.fine(String.format("[KubeDB] Query executed (%dms): %s", executionTimeMs, sql));
    }
    
    /**
     * 트랜잭션 시작 기록
     */
    public void recordTransactionBegin(String connectionId, String transactionId) {
        if (!config.isEnabled()) {
            return;
        }
        
        TransactionInfo txInfo = new TransactionInfo();
        txInfo.transactionId = transactionId;
        txInfo.connectionId = connectionId;
        txInfo.startTime = System.currentTimeMillis();
        txInfo.threadName = Thread.currentThread().getName();
        
        activeTransactions.put(transactionId, txInfo);
        logger.fine(String.format("[KubeDB] Transaction started: %s on %s", transactionId, connectionId));
    }
    
    /**
     * 트랜잭션 상태 변경 기록
     */
    public void recordTransactionStateChange(boolean autoCommit, long executionTimeNanos) {
        if (!config.isEnabled()) {
            return;
        }
        
        String connectionId = "conn-" + Thread.currentThread().getId();
        String transactionId = "tx-" + System.nanoTime();
        
        if (!autoCommit) {
            // 트랜잭션 시작
            recordTransactionBegin(connectionId, transactionId);
        } else {
            // 트랜잭션 자동 커밋 모드로 변경 - 활성 트랜잭션 종료
            activeTransactions.entrySet().removeIf(entry -> 
                connectionId.equals(entry.getValue().connectionId));
        }
        
        long executionTimeMs = executionTimeNanos / 1_000_000;
        logger.fine(String.format("[KubeDB] Transaction state change: autoCommit=%s (%dms)", 
                   autoCommit, executionTimeMs));
    }
    
    /**
     * 커밋 기록
     */
    public void recordCommit(long executionTimeNanos) {
        recordCommit(executionTimeNanos, "unknown-connection", "tx-" + System.nanoTime());
    }
    
    /**
     * 커밋 기록 (연결 정보 포함)
     */
    public void recordCommit(long executionTimeNanos, String connectionId, String transactionId) {
        if (!config.isEnabled()) {
            return;
        }
        
        commitCount.incrementAndGet();
        long executionTimeMs = executionTimeNanos / 1_000_000;
        
        // 활성 트랜잭션에서 제거
        activeTransactions.remove(transactionId);
        
        // HTTP 전송
        transmitter.transmitTransactionMetric("COMMIT", executionTimeMs, connectionId, transactionId);
        
        logger.fine(String.format("[KubeDB] Transaction committed (%dms)", executionTimeMs));
    }
    
    /**
     * 롤백 기록
     */
    public void recordRollback(long executionTimeNanos) {
        recordRollback(executionTimeNanos, "unknown-connection", "tx-" + System.nanoTime());
    }
    
    /**
     * 롤백 기록 (연결 정보 포함)
     */
    public void recordRollback(long executionTimeNanos, String connectionId, String transactionId) {
        if (!config.isEnabled()) {
            return;
        }
        
        rollbackCount.incrementAndGet();
        long executionTimeMs = executionTimeNanos / 1_000_000;
        
        // 활성 트랜잭션에서 제거
        activeTransactions.remove(transactionId);
        
        // HTTP 전송
        transmitter.transmitTransactionMetric("ROLLBACK", executionTimeMs, connectionId, transactionId);
        
        logger.fine(String.format("[KubeDB] Transaction rolled back (%dms)", executionTimeMs));
    }
    
    /**
     * 세이브포인트 롤백 기록
     */
    public void recordRollbackToSavepoint(long executionTimeNanos) {
        if (!config.isEnabled()) {
            return;
        }
        
        long executionTimeMs = executionTimeNanos / 1_000_000;
        logger.fine(String.format("[KubeDB] Rollback to savepoint (%dms)", executionTimeMs));
    }
    
    /**
     * 연결 종료 기록
     */
    public void recordConnectionClose() {
        recordConnectionClose(0, "unknown-connection");
    }
    
    /**
     * 연결 종료 기록 (시간 및 연결 정보 포함)
     */
    public void recordConnectionClose(long executionTimeNanos, String connectionId) {
        if (!config.isEnabled()) {
            return;
        }
        
        connectionCloseCount.incrementAndGet();
        long executionTimeMs = executionTimeNanos / 1_000_000;
        logger.fine(String.format("[KubeDB] Connection closed (%dms): %s", executionTimeMs, connectionId));
    }
    
    /**
     * 오류 기록
     */
    public void recordError(String operation, SQLException error) {
        if (!config.isEnabled()) {
            return;
        }
        
        errorCount.incrementAndGet();
        logger.warning(String.format("[KubeDB] SQL Error in %s: %s", operation, error.getMessage()));
    }
    
    /**
     * 오류 기록 (범용)
     */
    public void recordError(Exception error, long executionTimeNanos, String connectionId) {
        if (!config.isEnabled()) {
            return;
        }
        
        errorCount.incrementAndGet();
        long executionTimeMs = executionTimeNanos / 1_000_000;
        logger.warning(String.format("[KubeDB] Error (%dms) on %s: %s", 
                      executionTimeMs, connectionId, error.getMessage()));
    }
    
    /**
     * 현재 메트릭스 상태 반환
     */
    public MetricsSnapshot getSnapshot() {
        return new MetricsSnapshot(
            queryCount.get(),
            commitCount.get(), 
            rollbackCount.get(),
            errorCount.get(),
            connectionCloseCount.get(),
            maxQueryTime
        );
    }
    
    /**
     * 메트릭스 스냅샷 클래스
     */
    public static class MetricsSnapshot {
        public final long queryCount;
        public final long commitCount;
        public final long rollbackCount;
        public final long errorCount;
        public final long connectionCloseCount;
        public final long maxQueryTime;
        
        public MetricsSnapshot(long queryCount, long commitCount, long rollbackCount, 
                              long errorCount, long connectionCloseCount, 
                              long maxQueryTime) {
            this.queryCount = queryCount;
            this.commitCount = commitCount;
            this.rollbackCount = rollbackCount;
            this.errorCount = errorCount;
            this.connectionCloseCount = connectionCloseCount;
            this.maxQueryTime = maxQueryTime;
        }
        
        @Override
        public String toString() {
            return String.format("MetricsSnapshot{queries=%d, commits=%d, rollbacks=%d, errors=%d, " +
                               "connectionsClosed=%d, maxQueryTime=%dms}",
                               queryCount, commitCount, rollbackCount, errorCount, 
                               connectionCloseCount, maxQueryTime);
        }
    }
    
    /**
     * Long-running transaction 체크 및 알림
     */
    private void checkLongRunningTransactions() {
        if (!config.isEnabled()) {
            return;
        }
        
        long currentTime = System.currentTimeMillis();
        long thresholdMs = config.getLongRunningTransactionThresholdMs(); // 기본 4초
        
        for (TransactionInfo txInfo : activeTransactions.values()) {
            long durationMs = currentTime - txInfo.startTime;
            
            if (durationMs > thresholdMs && !txInfo.alreadyReported) {
                // Long-running transaction 감지
                txInfo.alreadyReported = true;
                
                logger.warning(String.format("[KubeDB] Long-running transaction detected: %s (%dms) on connection %s", 
                    txInfo.transactionId, durationMs, txInfo.connectionId));
                
                // HTTP 전송
                transmitter.transmitLongRunningTransactionAlert(
                    txInfo.transactionId, 
                    txInfo.connectionId, 
                    txInfo.threadName, 
                    durationMs, 
                    txInfo.startTime
                );
            }
        }
    }
    
    /**
     * 리소스 정리
     */
    public void shutdown() {
        if (transactionMonitor != null && !transactionMonitor.isShutdown()) {
            transactionMonitor.shutdown();
            try {
                if (!transactionMonitor.awaitTermination(5, TimeUnit.SECONDS)) {
                    transactionMonitor.shutdownNow();
                }
            } catch (InterruptedException e) {
                transactionMonitor.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
        
        if (transmitter != null) {
            transmitter.shutdown();
        }
        
        activeTransactions.clear();
        
        // Connection Pool 모니터링 중지
        if (poolMonitor != null) {
            poolMonitor.stop();
        }
        
        logger.info("[KubeDB] MetricsCollector shutdown completed");
    }
    
    /**
     * 새로운 DataSource 등록 (JDBC 인터셉터에서 발견 시 사용)
     */
    public void registerDataSource(DataSource dataSource) {
        if (poolMonitor != null) {
            poolMonitor.registerDataSource(dataSource);
        }
    }
    
    /**
     * Connection Pool 메트릭을 HTTP로 전송
     */
    private void sendConnectionPoolMetrics() {
        try {
            if (poolMonitor != null && transmitter != null) {
                PoolMetrics latestMetrics = poolMonitor.getLatestMetrics();
                if (latestMetrics != null && !latestMetrics.isEmpty()) {
                    transmitter.transmitSystemMetrics(latestMetrics);
                    
                    // 고급 메트릭 정보 포함한 상세 로깅
                    String detailedLog = String.format(
                        "[KubeDB] 📊 고급 Connection Pool 메트릭 전송: %s, 건강도: %d%%, 요청률: %d/sec", 
                        latestMetrics.toString(),
                        latestMetrics.getConnectionPoolHealth() != null ? latestMetrics.getConnectionPoolHealth() : 0,
                        latestMetrics.getConnectionRequestsPerSecond() != null ? latestMetrics.getConnectionRequestsPerSecond() : 0
                    );
                    
                    if (latestMetrics.getPeakActiveConnections() != null && latestMetrics.getPeakActiveConnections() > 0) {
                        detailedLog += String.format(", Peak: %d", latestMetrics.getPeakActiveConnections());
                    }
                    
                    logger.info(detailedLog);
                } else {
                    logger.fine("[KubeDB] Connection Pool 메트릭이 없거나 비어있음");
                }
            }
        } catch (Exception e) {
            logger.warning("[KubeDB] Connection Pool 메트릭 전송 실패: " + e.getMessage());
        }
    }
    
    /**
     * 트랜잭션 정보 클래스
     */
    private static class TransactionInfo {
        String transactionId;
        String connectionId;
        String threadName;
        long startTime;
        boolean alreadyReported = false;
    }
}