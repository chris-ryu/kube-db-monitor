package io.kubedb.monitor.agent;

import java.sql.SQLException;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Logger;

/**
 * 프록시 기반 메트릭스 수집기
 * 
 * ASM 바이트코드 변환 없이 프록시를 통해 JDBC 메트릭스를 수집합니다.
 * PostgreSQL 드라이버 로딩 간섭 없이 안전한 모니터링을 제공합니다.
 */
public class MetricsCollector {
    private static final Logger logger = Logger.getLogger(MetricsCollector.class.getName());
    
    private final AgentConfig config;
    
    // 기본 메트릭스 카운터들
    private final AtomicLong queryCount = new AtomicLong(0);
    private final AtomicLong commitCount = new AtomicLong(0);
    private final AtomicLong rollbackCount = new AtomicLong(0);
    private final AtomicLong errorCount = new AtomicLong(0);
    private final AtomicLong connectionCloseCount = new AtomicLong(0);
    
    // 성능 메트릭스
    private volatile long totalQueryTime = 0;
    private volatile long maxQueryTime = 0;
    
    public MetricsCollector(AgentConfig config) {
        this.config = config;
        logger.info("[KubeDB] MetricsCollector 초기화됨 (Proxy Mode)");
    }
    
    /**
     * SQL 쿼리 실행 기록
     */
    public void recordQuery(String sql, long executionTimeNanos) {
        if (!config.isEnabled()) {
            return;
        }
        
        queryCount.incrementAndGet();
        
        long executionTimeMs = executionTimeNanos / 1_000_000;
        totalQueryTime += executionTimeMs;
        
        if (executionTimeMs > maxQueryTime) {
            maxQueryTime = executionTimeMs;
        }
        
        // 느린 쿼리 감지
        if (executionTimeMs > config.getSlowQueryThresholdMs()) {
            logger.info(String.format("[KubeDB] 느린 쿼리 감지 (%dms): %s", 
                       executionTimeMs, 
                       sql.length() > 100 ? sql.substring(0, 100) + "..." : sql));
        }
        
        logger.fine(String.format("[KubeDB] Query executed (%dms): %s", executionTimeMs, sql));
    }
    
    /**
     * 트랜잭션 상태 변경 기록
     */
    public void recordTransactionStateChange(boolean autoCommit, long executionTimeNanos) {
        if (!config.isEnabled()) {
            return;
        }
        
        long executionTimeMs = executionTimeNanos / 1_000_000;
        logger.fine(String.format("[KubeDB] Transaction state change: autoCommit=%s (%dms)", 
                   autoCommit, executionTimeMs));
    }
    
    /**
     * 커밋 기록
     */
    public void recordCommit(long executionTimeNanos) {
        if (!config.isEnabled()) {
            return;
        }
        
        commitCount.incrementAndGet();
        long executionTimeMs = executionTimeNanos / 1_000_000;
        logger.fine(String.format("[KubeDB] Transaction committed (%dms)", executionTimeMs));
    }
    
    /**
     * 롤백 기록
     */
    public void recordRollback(long executionTimeNanos) {
        if (!config.isEnabled()) {
            return;
        }
        
        rollbackCount.incrementAndGet();
        long executionTimeMs = executionTimeNanos / 1_000_000;
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
        if (!config.isEnabled()) {
            return;
        }
        
        connectionCloseCount.incrementAndGet();
        logger.fine("[KubeDB] Connection closed");
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
     * 현재 메트릭스 상태 반환
     */
    public MetricsSnapshot getSnapshot() {
        return new MetricsSnapshot(
            queryCount.get(),
            commitCount.get(), 
            rollbackCount.get(),
            errorCount.get(),
            connectionCloseCount.get(),
            totalQueryTime,
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
        public final long totalQueryTime;
        public final long maxQueryTime;
        
        public MetricsSnapshot(long queryCount, long commitCount, long rollbackCount, 
                              long errorCount, long connectionCloseCount, 
                              long totalQueryTime, long maxQueryTime) {
            this.queryCount = queryCount;
            this.commitCount = commitCount;
            this.rollbackCount = rollbackCount;
            this.errorCount = errorCount;
            this.connectionCloseCount = connectionCloseCount;
            this.totalQueryTime = totalQueryTime;
            this.maxQueryTime = maxQueryTime;
        }
        
        @Override
        public String toString() {
            return String.format("MetricsSnapshot{queries=%d, commits=%d, rollbacks=%d, errors=%d, " +
                               "connectionsClosed=%d, totalQueryTime=%dms, maxQueryTime=%dms}",
                               queryCount, commitCount, rollbackCount, errorCount, 
                               connectionCloseCount, totalQueryTime, maxQueryTime);
        }
    }
}