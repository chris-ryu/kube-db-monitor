package io.kubedb.monitor.agent;

import net.bytebuddy.implementation.bind.annotation.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Method;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;

/**
 * Spring Transaction Manager 인터셉터
 * Spring의 PlatformTransactionManager와 JPA EntityManager의 트랜잭션을 감지하고 추적
 */
public class SpringTransactionInterceptor {
    private static final Logger logger = LoggerFactory.getLogger(SpringTransactionInterceptor.class);
    
    // 트랜잭션 시작 시간 추적 (transaction_object -> start_time)
    private static final Map<Object, Long> transactionStartTimes = new ConcurrentHashMap<>();
    
    // 트랜잭션 상태 추적 (thread_id -> transaction_info)
    private static final Map<String, SpringTransactionInfo> activeTransactions = new ConcurrentHashMap<>();
    
    // Long-running threshold (5초)
    private static final long LONG_RUNNING_THRESHOLD_MS = 5000;
    
    // 트랜잭션 정보 클래스
    private static class SpringTransactionInfo {
        final long startTime;
        final String threadName;
        final String transactionId;
        
        SpringTransactionInfo(String transactionId, String threadName) {
            this.startTime = System.currentTimeMillis();
            this.threadName = threadName;
            this.transactionId = transactionId;
        }
        
        long getDuration() {
            return System.currentTimeMillis() - startTime;
        }
    }
    
    /**
     * Spring Transaction Manager 메서드 인터셉터
     */
    @RuntimeType
    public static Object interceptSpringTransaction(
            @Origin Method method,
            @This Object target,
            @AllArguments Object[] args,
            @SuperCall Callable<?> callable) throws Exception {
        
        String methodName = method.getName();
        String threadName = Thread.currentThread().getName();
        long startTime = System.currentTimeMillis();
        
        try {
            // 메서드 실행 전 처리
            if ("getTransaction".equals(methodName)) {
                handleTransactionStart(target, threadName);
            }
            
            // 실제 메서드 실행
            Object result = callable.call();
            
            // 메서드 실행 후 처리
            long executionTime = System.currentTimeMillis() - startTime;
            
            if ("commit".equals(methodName)) {
                handleTransactionCommit(target, threadName, executionTime);
            } else if ("rollback".equals(methodName)) {
                handleTransactionRollback(target, threadName, executionTime);
            }
            
            return result;
            
        } catch (Exception e) {
            logger.error("Error intercepting Spring transaction method {}: {}", methodName, e.getMessage());
            throw e;
        }
    }
    
    /**
     * 트랜잭션 시작 처리
     */
    private static void handleTransactionStart(Object transactionManager, String threadName) {
        try {
            String transactionId = generateTransactionId(transactionManager, threadName);
            SpringTransactionInfo txInfo = new SpringTransactionInfo(transactionId, threadName);
            
            activeTransactions.put(threadName, txInfo);
            
            System.out.println("🚀 Spring 트랜잭션 시작 감지: " + transactionId + " (Thread: " + threadName + ")");
            logger.info("[KubeDB] Spring transaction started: {} on thread {}", transactionId, threadName);
            
            // MetricsCollector에 트랜잭션 시작 알림
            MetricsCollector metricsCollector = getMetricsCollector();
            if (metricsCollector != null) {
                metricsCollector.recordTransactionBegin("spring-tx-" + threadName, transactionId);
            }
            
        } catch (Exception e) {
            logger.error("Error handling Spring transaction start: {}", e.getMessage(), e);
        }
    }
    
    /**
     * 트랜잭션 커밋 처리
     */
    private static void handleTransactionCommit(Object transactionManager, String threadName, long executionTime) {
        try {
            SpringTransactionInfo txInfo = activeTransactions.remove(threadName);
            if (txInfo != null) {
                long totalDuration = txInfo.getDuration();
                
                System.out.println("✅ Spring 트랜잭션 커밋: " + txInfo.transactionId + 
                                 " (지속시간: " + totalDuration + "ms)");
                logger.info("[KubeDB] Spring transaction committed: {} (duration: {}ms)", 
                           txInfo.transactionId, totalDuration);
                
                // Long-running 감지
                if (totalDuration > LONG_RUNNING_THRESHOLD_MS) {
                    System.out.println("⏰ Long-running Spring 트랜잭션 감지: " + txInfo.transactionId + 
                                     " (지속시간: " + totalDuration + "ms, 임계값: " + LONG_RUNNING_THRESHOLD_MS + "ms)");
                    logger.warn("[KubeDB] Long-running Spring transaction detected: {} (duration: {}ms)", 
                               txInfo.transactionId, totalDuration);
                    
                    // Long-running 이벤트 전송
                    sendLongRunningTransactionEvent(txInfo, totalDuration);
                }
                
                // MetricsCollector에 커밋 알림
                MetricsCollector metricsCollector = getMetricsCollector();
                if (metricsCollector != null) {
                    metricsCollector.recordCommit(executionTime, "spring-tx-" + threadName, txInfo.transactionId);
                }
            }
            
        } catch (Exception e) {
            logger.error("Error handling Spring transaction commit: {}", e.getMessage(), e);
        }
    }
    
    /**
     * 트랜잭션 롤백 처리
     */
    private static void handleTransactionRollback(Object transactionManager, String threadName, long executionTime) {
        try {
            SpringTransactionInfo txInfo = activeTransactions.remove(threadName);
            if (txInfo != null) {
                long totalDuration = txInfo.getDuration();
                
                System.out.println("🔄 Spring 트랜잭션 롤백: " + txInfo.transactionId + 
                                 " (지속시간: " + totalDuration + "ms)");
                logger.info("[KubeDB] Spring transaction rolled back: {} (duration: {}ms)", 
                           txInfo.transactionId, totalDuration);
                
                // MetricsCollector에 롤백 알림
                MetricsCollector metricsCollector = getMetricsCollector();
                if (metricsCollector != null) {
                    metricsCollector.recordRollback(executionTime, "spring-tx-" + threadName, txInfo.transactionId);
                }
            }
            
        } catch (Exception e) {
            logger.error("Error handling Spring transaction rollback: {}", e.getMessage(), e);
        }
    }
    
    /**
     * Long-running 트랜잭션 이벤트 전송
     */
    private static void sendLongRunningTransactionEvent(SpringTransactionInfo txInfo, long duration) {
        try {
            MetricsCollector metricsCollector = getMetricsCollector();
            if (metricsCollector != null) {
                // Long-running transaction 이벤트 생성 및 전송
                metricsCollector.recordLongRunningTransaction(
                    txInfo.transactionId,
                    "spring-tx-" + txInfo.threadName,
                    duration,
                    "Spring @Transactional"
                );
            }
        } catch (Exception e) {
            logger.error("Error sending long-running transaction event: {}", e.getMessage(), e);
        }
    }
    
    /**
     * 트랜잭션 ID 생성
     */
    private static String generateTransactionId(Object transactionManager, String threadName) {
        return "spring-tx-" + System.currentTimeMillis() + "-" + 
               threadName.replaceAll("[^a-zA-Z0-9]", "") + "-" + 
               Math.abs(transactionManager.hashCode());
    }
    
    /**
     * MetricsCollector 인스턴스 가져오기 (reflection 사용)
     */
    private static MetricsCollector getMetricsCollector() {
        try {
            // UniversalJDBCInterceptor에서 MetricsCollector 참조 가져오기
            Class<?> interceptorClass = SpringTransactionInterceptor.class.getClassLoader()
                .loadClass("io.kubedb.monitor.agent.UniversalJDBCInterceptor");
            
            java.lang.reflect.Field field = interceptorClass.getDeclaredField("metricsCollector");
            field.setAccessible(true);
            return (MetricsCollector) field.get(null);
            
        } catch (Exception e) {
            logger.debug("Could not get MetricsCollector instance: {}", e.getMessage());
            return null;
        }
    }
    
    /**
     * 현재 활성 Spring 트랜잭션 수 조회
     */
    public static int getActiveTransactionCount() {
        return activeTransactions.size();
    }
    
    /**
     * 정리 작업 (필요시 호출)
     */
    public static void cleanup() {
        transactionStartTimes.clear();
        activeTransactions.clear();
    }
}