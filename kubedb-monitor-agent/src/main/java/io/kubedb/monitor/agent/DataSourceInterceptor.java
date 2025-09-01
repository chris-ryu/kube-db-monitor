package io.kubedb.monitor.agent;

import net.bytebuddy.implementation.bind.annotation.AllArguments;
import net.bytebuddy.implementation.bind.annotation.Origin;
import net.bytebuddy.implementation.bind.annotation.RuntimeType;
import net.bytebuddy.implementation.bind.annotation.SuperCall;
import net.bytebuddy.implementation.bind.annotation.This;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sql.DataSource;
import java.lang.reflect.Method;
import java.sql.Connection;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Set;

/**
 * DataSource 인터셉터 - Connection Pool 감지 및 등록
 * 
 * DataSource의 getConnection() 메서드를 인터셉트하여
 * Connection Pool 모니터링 시스템에 DataSource를 등록합니다.
 */
public class DataSourceInterceptor {
    private static final Logger logger = LoggerFactory.getLogger(DataSourceInterceptor.class);
    
    // 이미 등록된 DataSource를 추적하여 중복 등록 방지
    private static final Set<DataSource> registeredDataSources = ConcurrentHashMap.newKeySet();
    
    @RuntimeType
    public static Object intercept(@This Object dataSourceInstance,
                                  @Origin Method method,
                                  @SuperCall Callable<?> original,
                                  @AllArguments Object[] args) throws Exception {
        
        // 디버그 로그: 모든 DataSource 메서드 호출 추적
        System.out.println("🔍 DataSource 메서드 호출됨: " + method.getName() + " on " + dataSourceInstance.getClass().getSimpleName());
        
        // getConnection 메서드만 처리
        if (!"getConnection".equals(method.getName())) {
            return original.call();
        }
        
        System.out.println("🔗 DataSource.getConnection() 인터셉트 - " + dataSourceInstance.getClass().getSimpleName());
        
        // DataSource 등록 처리
        if (dataSourceInstance instanceof DataSource) {
            DataSource dataSource = (DataSource) dataSourceInstance;
            
            // 중복 등록 방지
            if (!registeredDataSources.contains(dataSource)) {
                try {
                    // 글로벌 MetricsCollector 가져오기
                    MetricsCollector metricsCollector = KubeDBAgent.getGlobalMetricsCollector();
                    if (metricsCollector != null) {
                        
                        // DataSource 등록
                        metricsCollector.registerDataSource(dataSource);
                        registeredDataSources.add(dataSource);
                        
                        System.out.println("✅ DataSource가 Connection Pool 모니터링에 등록됨: " + 
                                         dataSource.getClass().getSimpleName() + 
                                         " @" + System.identityHashCode(dataSource));
                        
                        logger.info("[KubeDB] DataSource registered for Connection Pool monitoring: {}", 
                                  dataSource.getClass().getName());
                    }
                } catch (Exception e) {
                    logger.warn("[KubeDB] Failed to register DataSource for monitoring: {}", e.getMessage());
                    // 등록 실패해도 원래 기능은 계속 동작하도록 함
                }
            }
        }
        
        // 원래 getConnection() 메서드 호출
        try {
            Object result = original.call();
            
            if (result instanceof Connection && logger.isDebugEnabled()) {
                logger.debug("[KubeDB] Connection obtained from DataSource: {}", 
                           dataSourceInstance.getClass().getSimpleName());
            }
            
            return result;
        } catch (Exception e) {
            logger.warn("[KubeDB] Error in DataSource getConnection(): {}", e.getMessage());
            throw e;
        }
    }
}