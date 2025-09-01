package io.kubedb.monitor.agent.pool.collectors;

import io.kubedb.monitor.agent.pool.PoolMetrics;

import javax.sql.DataSource;

/**
 * Connection Pool 메트릭 수집기 인터페이스
 * 
 * 다양한 Connection Pool 구현체에 대한 메트릭 수집을 위한 공통 인터페이스
 */
public interface PoolMetricsCollector {
    
    /**
     * 주어진 DataSource가 이 수집기에서 지원하는지 확인
     * 
     * @param dataSource 확인할 DataSource
     * @return 지원 여부
     */
    boolean supports(DataSource dataSource);
    
    /**
     * DataSource에서 Connection Pool 메트릭을 수집
     * 
     * @param dataSource 메트릭을 수집할 DataSource
     * @return 수집된 Pool 메트릭
     */
    PoolMetrics collect(DataSource dataSource);
}