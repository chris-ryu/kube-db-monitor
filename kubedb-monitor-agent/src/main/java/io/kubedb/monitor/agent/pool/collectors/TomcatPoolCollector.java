package io.kubedb.monitor.agent.pool.collectors;

import io.kubedb.monitor.agent.pool.PoolMetrics;
import io.kubedb.monitor.agent.pool.PoolType;

import javax.sql.DataSource;
import java.lang.reflect.Method;
import java.util.logging.Logger;

/**
 * Tomcat JDBC Pool 메트릭 수집기
 * 
 * Apache Tomcat JDBC Connection Pool의 메트릭을 수집합니다.
 */
public class TomcatPoolCollector implements PoolMetricsCollector {
    private static final Logger logger = Logger.getLogger(TomcatPoolCollector.class.getName());
    
    @Override
    public boolean supports(DataSource dataSource) {
        String className = dataSource.getClass().getName();
        return className.contains("tomcat") && className.contains("jdbc");
    }
    
    @Override
    public PoolMetrics collect(DataSource dataSource) {
        try {
            return collectTomcatMetrics(dataSource);
        } catch (Exception e) {
            logger.warning(String.format("[KubeDB] Tomcat JDBC Pool 메트릭 수집 실패: %s", e.getMessage()));
            return PoolMetrics.empty();
        }
    }
    
    /**
     * Tomcat JDBC Pool의 메트릭 수집
     */
    private PoolMetrics collectTomcatMetrics(DataSource dataSource) throws Exception {
        // Tomcat JDBC Pool의 메트릭 메서드들
        int activeConnections = getIntValue(dataSource, "getActive");
        int idleConnections = getIntValue(dataSource, "getIdle");
        int maxActive = getIntValue(dataSource, "getMaxActive");
        
        // Pool 이름 추출 (있는 경우)
        String poolName = getStringValue(dataSource, "getPoolName");
        if (poolName == null || poolName.isEmpty()) {
            poolName = "TomcatPool";
        }
        
        logger.fine(String.format("[KubeDB] Tomcat JDBC Pool 메트릭 수집: active=%d, idle=%d, max=%d", 
                   activeConnections, idleConnections, maxActive));
        
        return new PoolMetrics.Builder(PoolType.TOMCAT, poolName)
                .activeConnections(activeConnections)
                .idleConnections(idleConnections)
                .maxConnections(maxActive)
                .build();
    }
    
    /**
     * DataSource에서 int 값 추출
     */
    private int getIntValue(DataSource dataSource, String methodName) {
        try {
            Method method = dataSource.getClass().getMethod(methodName);
            Object result = method.invoke(dataSource);
            return result != null ? (Integer) result : 0;
        } catch (Exception e) {
            logger.fine(String.format("[KubeDB] Tomcat Pool %s 호출 실패: %s", methodName, e.getMessage()));
            return 0;
        }
    }
    
    /**
     * DataSource에서 String 값 추출
     */
    private String getStringValue(DataSource dataSource, String methodName) {
        try {
            Method method = dataSource.getClass().getMethod(methodName);
            Object result = method.invoke(dataSource);
            return result != null ? (String) result : null;
        } catch (Exception e) {
            logger.fine(String.format("[KubeDB] Tomcat Pool %s 호출 실패: %s", methodName, e.getMessage()));
            return null;
        }
    }
}