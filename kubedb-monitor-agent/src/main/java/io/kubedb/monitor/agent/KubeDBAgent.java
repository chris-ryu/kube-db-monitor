package io.kubedb.monitor.agent;

import net.bytebuddy.agent.builder.AgentBuilder;
import net.bytebuddy.implementation.MethodDelegation;
import net.bytebuddy.matcher.ElementMatchers;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.instrument.Instrumentation;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * ByteBuddy 기반 범용 JDBC 모니터링 Agent
 * 모든 주요 데이터베이스(PostgreSQL, Oracle, MySQL, SQL Server 등)를 지원합니다.
 */
public class KubeDBAgent {
    private static final Logger logger = LoggerFactory.getLogger(KubeDBAgent.class);
    
    private static Instrumentation instrumentation;
    private static AgentConfig config;
    private static RuntimeDataSourceDiscovery dataSourceDiscovery;
    private static ScheduledExecutorService scheduler;
    
    /**
     * JVM hook to statically load the javaagent at startup.
     * Called by the JVM when the agent is loaded via -javaagent
     */
    public static void premain(String agentArgs, Instrumentation inst) {
        System.out.println("🚀 KubeDB Monitor Agent (ByteBuddy Edition) starting with args: " + agentArgs);
        System.out.println("🔥🔥🔥 [BYTEBUDDY-AGENT] 새로운 ByteBuddy 기반 Agent가 실행되고 있음 🔥🔥🔥");
        
        logger.info("KubeDB Monitor Agent (ByteBuddy) starting...");
        
        instrumentation = inst;
        config = AgentConfig.fromArgs(agentArgs);
        
        System.out.println("📊 Agent config loaded - enabled: " + config.isEnabled());
        
        if (!config.isEnabled()) {
            System.out.println("❌ KubeDB Monitor Agent is disabled");
            logger.info("KubeDB Monitor Agent is disabled");
            return;
        }
        
        try {
            // ByteBuddy 기반 인터셉션 설정
            initializeByteBuddyInterception(inst);
            
            // 런타임 DataSource 발견 시작
            initializeRuntimeDiscovery(inst);
            
            // HTTP 전송 테스트
            testHttpTransmission(config);
            
            System.out.println("✅ ByteBuddy Agent started successfully");
            System.out.println("📋 Monitoring databases: " + config.getSupportedDatabases());
            logger.info("ByteBuddy Agent started successfully");
            logger.info("Monitoring databases: {}", config.getSupportedDatabases());
            
        } catch (Exception e) {
            System.out.println("❌ ByteBuddy Agent initialization failed: " + e.getMessage());
            logger.error("Failed to start ByteBuddy Agent", e);
            e.printStackTrace();
        }
    }
    
    /**
     * JVM hook to dynamically load javaagent at runtime.
     */
    public static void agentmain(String agentArgs, Instrumentation inst) {
        logger.info("KubeDB Monitor Agent attaching at runtime...");
        premain(agentArgs, inst);
    }
    
    /**
     * ByteBuddy 기반 JDBC 인터셉션 초기화
     */
    private static void initializeByteBuddyInterception(Instrumentation inst) {
        System.out.println("🔧 ByteBuddy Agent Builder 설정 중...");
        
        try {
            new AgentBuilder.Default()
                .with(AgentBuilder.RedefinitionStrategy.RETRANSFORMATION)
                .with(AgentBuilder.TypeStrategy.Default.REDEFINE)
                .ignore(ElementMatchers.nameStartsWith("net.bytebuddy.")
                       .or(ElementMatchers.nameStartsWith("io.kubedb.monitor.agent.")))
                
                // 모든 Connection 구현체 인터셉트
                .type(ElementMatchers.isSubTypeOf(java.sql.Connection.class)
                     .and(ElementMatchers.not(ElementMatchers.isInterface())))
                .transform((builder, type, classLoader, module, protectionDomain) -> {
                    System.out.println("🔍 Connection 클래스 발견: " + type.getName());
                    return builder
                        .method(ElementMatchers.named("prepareStatement")
                               .or(ElementMatchers.named("createStatement"))
                               .or(ElementMatchers.named("commit"))
                               .or(ElementMatchers.named("rollback"))
                               .or(ElementMatchers.named("setAutoCommit"))
                               .or(ElementMatchers.named("close")))
                        .intercept(MethodDelegation.to(UniversalJDBCInterceptor.class));
                })
                
                // 모든 PreparedStatement 구현체 인터셉트
                .type(ElementMatchers.isSubTypeOf(java.sql.PreparedStatement.class)
                     .and(ElementMatchers.not(ElementMatchers.isInterface())))
                .transform((builder, type, classLoader, module, protectionDomain) -> {
                    System.out.println("🔍 PreparedStatement 클래스 발견: " + type.getName());
                    return builder
                        .method(ElementMatchers.named("execute")
                               .or(ElementMatchers.named("executeQuery"))
                               .or(ElementMatchers.named("executeUpdate"))
                               .or(ElementMatchers.named("executeBatch")))
                        .intercept(MethodDelegation.to(UniversalJDBCInterceptor.class));
                })
                
                // 모든 Statement 구현체 인터셉트
                .type(ElementMatchers.isSubTypeOf(java.sql.Statement.class)
                     .and(ElementMatchers.not(ElementMatchers.isInterface()))
                     .and(ElementMatchers.not(ElementMatchers.isSubTypeOf(java.sql.PreparedStatement.class))))
                .transform((builder, type, classLoader, module, protectionDomain) -> {
                    System.out.println("🔍 Statement 클래스 발견: " + type.getName());
                    return builder
                        .method(ElementMatchers.named("execute")
                               .or(ElementMatchers.named("executeQuery"))
                               .or(ElementMatchers.named("executeUpdate")))
                        .intercept(MethodDelegation.to(UniversalJDBCInterceptor.class));
                })
                
                .installOn(inst);
                
            System.out.println("✅ ByteBuddy Agent Builder 설치 완료");
            
        } catch (Exception e) {
            System.out.println("❌ ByteBuddy 설정 실패: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    /**
     * 런타임 DataSource 발견 초기화
     */
    private static void initializeRuntimeDiscovery(Instrumentation inst) {
        System.out.println("🔍 Runtime DataSource Discovery 시작...");
        
        dataSourceDiscovery = new RuntimeDataSourceDiscovery(inst, config);
        scheduler = Executors.newScheduledThreadPool(2);
        
        // DataSource 발견 작업을 주기적으로 실행 (5초마다)
        scheduler.scheduleAtFixedRate(() -> {
            try {
                dataSourceDiscovery.discoverAndWrapDataSources();
            } catch (Exception e) {
                // 조용히 실패 (정상적일 수 있음)
                logger.debug("DataSource discovery error (may be normal): {}", e.getMessage());
            }
        }, 5, 5, TimeUnit.SECONDS);
        
        System.out.println("✅ Runtime DataSource Discovery 시작됨");
    }
    
    /**
     * HTTP 전송 테스트
     */
    private static void testHttpTransmission(AgentConfig config) {
        try {
            System.out.println("🧪 HTTP 전송 테스트 시작...");
            HttpMetricsTransmitter transmitter = new HttpMetricsTransmitter(config);
            
            // 테스트 메트릭 전송
            transmitter.transmitQueryMetric(
                "SELECT * FROM bytebuddy_test WHERE agent = 'new'", 
                123, // 123ms
                "bytebuddy-test-connection", 
                "bytebuddy-test-thread"
            );
            
            System.out.println("✅ HTTP 전송 테스트 완료");
        } catch (Exception e) {
            System.out.println("❌ HTTP 전송 테스트 실패: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    /**
     * Get the instrumentation instance
     */
    public static Instrumentation getInstrumentation() {
        return instrumentation;
    }
    
    /**
     * Get the agent configuration
     */
    public static AgentConfig getConfig() {
        return config;
    }
    
    /**
     * Shutdown cleanup
     */
    public static void shutdown() {
        if (scheduler != null && !scheduler.isShutdown()) {
            scheduler.shutdown();
            try {
                if (!scheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                    scheduler.shutdownNow();
                }
            } catch (InterruptedException e) {
                scheduler.shutdownNow();
            }
        }
        System.out.println("🔄 KubeDB Monitor Agent shutdown complete");
    }
}