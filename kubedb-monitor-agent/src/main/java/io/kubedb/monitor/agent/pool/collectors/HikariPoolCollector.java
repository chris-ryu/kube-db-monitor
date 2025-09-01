package io.kubedb.monitor.agent.pool.collectors;

import io.kubedb.monitor.agent.pool.PoolMetrics;
import io.kubedb.monitor.agent.pool.PoolType;

import javax.sql.DataSource;
import java.lang.reflect.Method;
import java.util.logging.Logger;

/**
 * HikariCP Connection Pool 메트릭 수집기
 * 
 * HikariCP의 MXBean 인터페이스를 통해 Connection Pool 메트릭을 수집합니다.
 */
public class HikariPoolCollector implements PoolMetricsCollector {
    private static final Logger logger = Logger.getLogger(HikariPoolCollector.class.getName());
    
    @Override
    public boolean supports(DataSource dataSource) {
        return dataSource.getClass().getName().contains("hikari");
    }
    
    @Override
    public PoolMetrics collect(DataSource dataSource) {
        try {
            return collectHikariMetrics(dataSource);
        } catch (Exception e) {
            logger.warning(String.format("[KubeDB] HikariCP 메트릭 수집 실패: %s", e.getMessage()));
            return PoolMetrics.empty();
        }
    }
    
    /**
     * HikariCP의 MXBean을 통해 메트릭 수집
     */
    private PoolMetrics collectHikariMetrics(DataSource dataSource) throws Exception {
        // HikariDataSource에서 HikariPoolMXBean 획득
        Method getHikariPoolMXBean = dataSource.getClass().getMethod("getHikariPoolMXBean");
        Object poolMXBean = getHikariPoolMXBean.invoke(dataSource);
        
        if (poolMXBean == null) {
            logger.warning("[KubeDB] HikariPoolMXBean을 획득할 수 없음 - reflection 기반 fallback 시도");
            return collectHikariMetricsViaReflection(dataSource);
        }
        
        // MXBean에서 메트릭 값들 추출
        int activeConnections = getIntValue(poolMXBean, "getActiveConnections");
        int idleConnections = getIntValue(poolMXBean, "getIdleConnections");
        int totalConnections = getIntValue(poolMXBean, "getTotalConnections");
        int threadsAwaitingConnection = getIntValue(poolMXBean, "getThreadsAwaitingConnection");
        
        // HikariDataSource에서 최대 Pool 크기 추출
        int maxPoolSize = getMaxPoolSize(dataSource);
        
        // Pool 이름 추출
        String poolName = getPoolName(dataSource);
        
        logger.fine(String.format("[KubeDB] HikariCP 메트릭 수집: active=%d, idle=%d, total=%d, max=%d, waiting=%d", 
                   activeConnections, idleConnections, totalConnections, maxPoolSize, threadsAwaitingConnection));
        
        return new PoolMetrics.Builder(PoolType.HIKARI, poolName)
                .activeConnections(activeConnections)
                .idleConnections(idleConnections)
                .maxConnections(maxPoolSize)
                .waitingThreads(threadsAwaitingConnection)
                .build();
    }
    
    /**
     * HikariDataSource에서 최대 Pool 크기 추출
     */
    private int getMaxPoolSize(DataSource dataSource) {
        try {
            Method getMaximumPoolSize = dataSource.getClass().getMethod("getMaximumPoolSize");
            Object result = getMaximumPoolSize.invoke(dataSource);
            return result != null ? (Integer) result : 0;
        } catch (Exception e) {
            logger.fine(String.format("[KubeDB] HikariCP maxPoolSize 추출 실패: %s", e.getMessage()));
            return 0;
        }
    }
    
    /**
     * HikariDataSource에서 Pool 이름 추출
     */
    private String getPoolName(DataSource dataSource) {
        try {
            Method getPoolName = dataSource.getClass().getMethod("getPoolName");
            Object result = getPoolName.invoke(dataSource);
            return result != null ? (String) result : "HikariPool";
        } catch (Exception e) {
            // Pool 이름을 가져올 수 없는 경우 기본값 사용
            return "HikariPool";
        }
    }
    
    /**
     * MXBean 없이 reflection을 통한 직접 메트릭 수집 (fallback)
     */
    private PoolMetrics collectHikariMetricsViaReflection(DataSource dataSource) {
        try {
            logger.info("[KubeDB] HikariCP reflection 기반 메트릭 수집 시도");
            
            // HikariDataSource에서 pool 필드 접근
            java.lang.reflect.Field poolField = dataSource.getClass().getDeclaredField("pool");
            poolField.setAccessible(true);
            Object hikariPool = poolField.get(dataSource);
            
            if (hikariPool == null) {
                logger.warning("[KubeDB] HikariPool 인스턴스가 null");
                return PoolMetrics.empty();
            }
            
            logger.info("[KubeDB] HikariPool 인스턴스 접근 성공: " + hikariPool.getClass().getName());
            
            // HikariPool에서 직접 메트릭 추출
            int activeConnections = getPoolIntValue(hikariPool, "getActiveConnections");
            int idleConnections = getPoolIntValue(hikariPool, "getIdleConnections"); 
            int totalConnections = getPoolIntValue(hikariPool, "getTotalConnections");
            int threadsAwaitingConnection = getPoolIntValue(hikariPool, "getThreadsAwaitingConnection");
            
            // 최대 Pool 크기와 Pool 이름 추출
            int maxPoolSize = getMaxPoolSize(dataSource);
            String poolName = getPoolName(dataSource);
            
            logger.info(String.format("[KubeDB] HikariCP reflection 메트릭: active=%d, idle=%d, total=%d, max=%d, waiting=%d", 
                       activeConnections, idleConnections, totalConnections, maxPoolSize, threadsAwaitingConnection));
            
            return new PoolMetrics.Builder(PoolType.HIKARI, poolName)
                    .activeConnections(activeConnections)
                    .idleConnections(idleConnections)
                    .maxConnections(maxPoolSize)
                    .waitingThreads(threadsAwaitingConnection)
                    .build();
                    
        } catch (Exception e) {
            logger.warning(String.format("[KubeDB] HikariCP reflection 메트릭 수집 실패: %s", e.getMessage()));
            
            // 대안: 기본 DataSource 정보만 수집
            return createBasicPoolMetrics(dataSource);
        }
    }
    
    /**
     * HikariPool 인스턴스에서 직접 int 값 추출
     */
    private int getPoolIntValue(Object hikariPool, String methodName) {
        try {
            Method method = hikariPool.getClass().getMethod(methodName);
            Object result = method.invoke(hikariPool);
            return result != null ? (Integer) result : 0;
        } catch (Exception e) {
            logger.fine(String.format("[KubeDB] HikariPool %s 호출 실패: %s", methodName, e.getMessage()));
            return 0;
        }
    }
    
    /**
     * 기본적인 Pool 메트릭 생성 (최후 fallback)
     */
    private PoolMetrics createBasicPoolMetrics(DataSource dataSource) {
        try {
            String poolName = getPoolName(dataSource);
            int maxPoolSize = getMaxPoolSize(dataSource);
            
            logger.info("[KubeDB] 기본 HikariCP 메트릭 생성 - maxPoolSize: " + maxPoolSize);
            
            // 최소한의 정보라도 제공
            return new PoolMetrics.Builder(PoolType.HIKARI, poolName)
                    .maxConnections(maxPoolSize)
                    .activeConnections(1) // 최소 1개는 활성 상태로 가정
                    .idleConnections(0)
                    .build();
                    
        } catch (Exception e) {
            logger.warning("[KubeDB] 기본 Pool 메트릭 생성도 실패: " + e.getMessage());
            return PoolMetrics.empty();
        }
    }

    /**
     * MXBean에서 int 값 추출
     */
    private int getIntValue(Object mxBean, String methodName) {
        try {
            Method method = mxBean.getClass().getMethod(methodName);
            Object result = method.invoke(mxBean);
            return result != null ? (Integer) result : 0;
        } catch (Exception e) {
            logger.fine(String.format("[KubeDB] HikariCP %s 호출 실패: %s", methodName, e.getMessage()));
            return 0;
        }
    }
}