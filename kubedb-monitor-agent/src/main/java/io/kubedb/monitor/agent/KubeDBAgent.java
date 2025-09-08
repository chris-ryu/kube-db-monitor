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
 * KubeDB Monitor Agent - ByteBuddy 기반 JDBC 모니터링 에이전트
 */
public class KubeDBAgent {
    private static final Logger logger = LoggerFactory.getLogger(KubeDBAgent.class);
    
    private static AgentConfig config;
    private static RuntimeDataSourceDiscovery dataSourceDiscovery;
    private static ScheduledExecutorService scheduler;
    private static Instrumentation instrumentation;
    private static MetricsCollector globalMetricsCollector;
    
    /**
     * JVM hook to statically load the javaagent at startup.
     */
    public static void premain(String agentArgs, Instrumentation inst) {
        System.out.println("🚀 KubeDB Monitor Agent starting...");
        logger.info("KubeDB Monitor Agent starting with args: {}", agentArgs);
        
        // Instrumentation 저장
        instrumentation = inst;
        
        // Agent 설정 로드 (인자 파싱 포함)
        config = AgentConfig.fromArgs(agentArgs);
        System.out.println("📊 Agent config loaded - enabled: " + config.isEnabled());
        
        // MetricsCollector 초기화
        globalMetricsCollector = new MetricsCollector(config);
        
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
        logger.info("Initializing ByteBuddy JDBC interception");
        
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
        try {
            dataSourceDiscovery = new RuntimeDataSourceDiscovery(inst, config);
            
            System.out.println("✅ Runtime DataSource Discovery 시작됨");
            
        } catch (Exception e) {
            System.out.println("⚠️ Runtime DataSource Discovery 초기화 실패: " + e.getMessage());
            logger.warn("Failed to initialize runtime DataSource discovery", e);
        }
    }
    
    /**
     * HTTP 전송 테스트
     */
    private static void testHttpTransmission(AgentConfig config) {
        try {
            HttpMetricsTransmitter transmitter = new HttpMetricsTransmitter(config);
            
            System.out.println("✅ HTTP 전송 준비 완료: " + config.getControlPlaneEndpoint());
            
        } catch (Exception e) {
            System.out.println("❌ HTTP 전송 테스트 중 예외: " + e.getMessage());
        }
    }
    
    /**
     * Agent 설정 반환
     */
    public static AgentConfig getConfig() {
        return config;
    }
    
    /**
     * Global MetricsCollector 반환
     */
    public static MetricsCollector getGlobalMetricsCollector() {
        return globalMetricsCollector;
    }
    
    /**
     * Instrumentation 반환
     */
    public static Instrumentation getInstrumentation() {
        return instrumentation;
    }
    
    /**
     * 에이전트 종료 시 리소스 정리
     */
    public static void shutdown() {
        try {
            if (scheduler != null && !scheduler.isShutdown()) {
                scheduler.shutdown();
                scheduler.awaitTermination(5, TimeUnit.SECONDS);
            }
            
            logger.info("KubeDB Monitor Agent shutdown completed");
            
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}