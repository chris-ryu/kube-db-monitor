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
    
    // 고급 모니터링 메트릭
    private final Integer peakActiveConnections;
    private final Double averageConnectionHoldTime;
    private final Long connectionRequestsPerSecond;
    private final Integer connectionPoolHealth; // 0-100 건강도 점수
    private final Long peakTimestamp; // Peak 값이 기록된 시간
    
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
        this.peakActiveConnections = builder.peakActiveConnections;
        this.averageConnectionHoldTime = builder.averageConnectionHoldTime;
        this.connectionRequestsPerSecond = builder.connectionRequestsPerSecond;
        this.connectionPoolHealth = builder.connectionPoolHealth != null ? builder.connectionPoolHealth : calculatePoolHealth();
        this.peakTimestamp = builder.peakTimestamp;
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
    
    /**
     * Connection Pool 건강도 점수 계산 (0-100)
     * 사용률, 대기 스레드, 응답시간 등을 종합하여 계산
     */
    private int calculatePoolHealth() {
        int healthScore = 100;
        
        // 1. 사용률 점검 (50% 이상 사용 시 감점)
        if (connectionUsageRatio > 0.8) {
            healthScore -= 30; // 80% 이상 사용 시 -30점
        } else if (connectionUsageRatio > 0.6) {
            healthScore -= 15; // 60% 이상 사용 시 -15점
        } else if (connectionUsageRatio > 0.5) {
            healthScore -= 5;  // 50% 이상 사용 시 -5점
        }
        
        // 2. 대기 스레드 점검
        if (waitingThreads != null && waitingThreads > 0) {
            healthScore -= Math.min(40, waitingThreads * 5); // 대기 스레드당 -5점, 최대 -40점
        }
        
        // 3. 평균 체크아웃 시간 점검 (100ms 이상일 때 감점)
        if (averageCheckoutTime != null && averageCheckoutTime > 100) {
            healthScore -= Math.min(20, (int)(averageCheckoutTime / 50)); // 50ms당 -1점, 최대 -20점
        }
        
        // 4. Peak 대비 현재 사용률 점검
        if (peakActiveConnections != null && peakActiveConnections > 0) {
            double peakRatio = (double) activeConnections / peakActiveConnections;
            if (peakRatio > 0.9) {
                healthScore -= 10; // Peak에 근접 시 -10점
            }
        }
        
        return Math.max(0, Math.min(100, healthScore));
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
    
    // 고급 모니터링 getters
    public Integer getPeakActiveConnections() { return peakActiveConnections; }
    public Double getAverageConnectionHoldTime() { return averageConnectionHoldTime; }
    public Long getConnectionRequestsPerSecond() { return connectionRequestsPerSecond; }
    public Integer getConnectionPoolHealth() { return connectionPoolHealth; }
    public Long getPeakTimestamp() { return peakTimestamp; }
    
    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append(String.format("PoolMetrics{type=%s, name=%s, active=%d, idle=%d, max=%d, usage=%.2f%%",
                               poolType.getDisplayName(), poolName, activeConnections, idleConnections, 
                               maxConnections, connectionUsageRatio * 100));
        
        if (peakActiveConnections != null) {
            sb.append(String.format(", peak=%d", peakActiveConnections));
        }
        if (connectionPoolHealth != null) {
            sb.append(String.format(", health=%d%%", connectionPoolHealth));
        }
        if (waitingThreads != null && waitingThreads > 0) {
            sb.append(String.format(", waiting=%d", waitingThreads));
        }
        
        sb.append("}");
        return sb.toString();
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
        
        // 고급 모니터링 필드
        private Integer peakActiveConnections;
        private Double averageConnectionHoldTime;
        private Long connectionRequestsPerSecond;
        private Integer connectionPoolHealth;
        private Long peakTimestamp;
        
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
        
        // 고급 모니터링 Builder 메서드들
        public Builder peakActiveConnections(Integer peakActiveConnections) {
            this.peakActiveConnections = peakActiveConnections;
            return this;
        }
        
        public Builder averageConnectionHoldTime(Double averageConnectionHoldTime) {
            this.averageConnectionHoldTime = averageConnectionHoldTime;
            return this;
        }
        
        public Builder connectionRequestsPerSecond(Long connectionRequestsPerSecond) {
            this.connectionRequestsPerSecond = connectionRequestsPerSecond;
            return this;
        }
        
        public Builder connectionPoolHealth(Integer connectionPoolHealth) {
            this.connectionPoolHealth = connectionPoolHealth;
            return this;
        }
        
        public Builder peakTimestamp(Long peakTimestamp) {
            this.peakTimestamp = peakTimestamp;
            return this;
        }
        
        public PoolMetrics build() {
            return new PoolMetrics(this);
        }
    }
}