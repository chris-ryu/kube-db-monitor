package io.kubedb.monitor.agent.pool;

/**
 * Connection Pool 메트릭 데이터 클래스
 * 
 * 모든 Connection Pool 구현체에서 공통으로 수집할 수 있는 메트릭들
 */
public class PoolMetrics {
    private final int activeConnections;
    private final int idleConnections;
    private final int maxConnections;
    private final double connectionUsageRatio;
    private final PoolType poolType;
    private final String poolName;
    private final long timestamp;
    
    // 선택적 메트릭 (Pool에 따라 지원되지 않을 수 있음)
    private final Integer waitingThreads;
    private final Long averageCheckoutTime;
    private final Long totalConnectionsCreated;
    private final Long totalConnectionsClosed;
    
    private PoolMetrics(Builder builder) {
        this.activeConnections = builder.activeConnections;
        this.idleConnections = builder.idleConnections;
        this.maxConnections = builder.maxConnections;
        this.connectionUsageRatio = calculateUsageRatio(activeConnections, maxConnections);
        this.poolType = builder.poolType;
        this.poolName = builder.poolName;
        this.timestamp = System.currentTimeMillis();
        this.waitingThreads = builder.waitingThreads;
        this.averageCheckoutTime = builder.averageCheckoutTime;
        this.totalConnectionsCreated = builder.totalConnectionsCreated;
        this.totalConnectionsClosed = builder.totalConnectionsClosed;
    }
    
    /**
     * 기본값으로 초기화된 빈 메트릭 생성
     */
    public static PoolMetrics empty() {
        return new Builder(PoolType.UNKNOWN, "unknown-pool")
                .activeConnections(0)
                .idleConnections(0)
                .maxConnections(0)
                .build();
    }
    
    /**
     * 빈 메트릭인지 확인
     */
    public boolean isEmpty() {
        return poolType == PoolType.UNKNOWN || maxConnections == 0;
    }
    
    private double calculateUsageRatio(int active, int max) {
        if (max == 0) {
            return 0.0;
        }
        return (double) active / max;
    }
    
    // Getters
    public int getActiveConnections() { return activeConnections; }
    public int getIdleConnections() { return idleConnections; }
    public int getMaxConnections() { return maxConnections; }
    public int getTotalConnections() { return activeConnections + idleConnections; }
    public double getConnectionUsageRatio() { return connectionUsageRatio; }
    public PoolType getPoolType() { return poolType; }
    public String getPoolName() { return poolName; }
    public long getTimestamp() { return timestamp; }
    
    // Optional getters
    public Integer getWaitingThreads() { return waitingThreads; }
    public Long getAverageCheckoutTime() { return averageCheckoutTime; }
    public Long getTotalConnectionsCreated() { return totalConnectionsCreated; }
    public Long getTotalConnectionsClosed() { return totalConnectionsClosed; }
    
    @Override
    public String toString() {
        return String.format("PoolMetrics{type=%s, name=%s, active=%d, idle=%d, max=%d, usage=%.2f%%}",
                           poolType.getDisplayName(), poolName, activeConnections, idleConnections, 
                           maxConnections, connectionUsageRatio * 100);
    }
    
    /**
     * Builder 패턴으로 PoolMetrics 생성
     */
    public static class Builder {
        private final PoolType poolType;
        private final String poolName;
        private int activeConnections = 0;
        private int idleConnections = 0;
        private int maxConnections = 0;
        private Integer waitingThreads;
        private Long averageCheckoutTime;
        private Long totalConnectionsCreated;
        private Long totalConnectionsClosed;
        
        public Builder(PoolType poolType, String poolName) {
            this.poolType = poolType;
            this.poolName = poolName;
        }
        
        public Builder activeConnections(int activeConnections) {
            this.activeConnections = activeConnections;
            return this;
        }
        
        public Builder idleConnections(int idleConnections) {
            this.idleConnections = idleConnections;
            return this;
        }
        
        public Builder maxConnections(int maxConnections) {
            this.maxConnections = maxConnections;
            return this;
        }
        
        public Builder waitingThreads(Integer waitingThreads) {
            this.waitingThreads = waitingThreads;
            return this;
        }
        
        public Builder averageCheckoutTime(Long averageCheckoutTime) {
            this.averageCheckoutTime = averageCheckoutTime;
            return this;
        }
        
        public Builder totalConnectionsCreated(Long totalConnectionsCreated) {
            this.totalConnectionsCreated = totalConnectionsCreated;
            return this;
        }
        
        public Builder totalConnectionsClosed(Long totalConnectionsClosed) {
            this.totalConnectionsClosed = totalConnectionsClosed;
            return this;
        }
        
        public PoolMetrics build() {
            return new PoolMetrics(this);
        }
    }
}