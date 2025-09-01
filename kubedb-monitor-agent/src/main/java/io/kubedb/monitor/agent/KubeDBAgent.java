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
    private static MetricsCollector globalMetricsCollector;
    
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
            // 글로벌 MetricsCollector 초기화
            globalMetricsCollector = new MetricsCollector(config);
            
            // ByteBuddy 기반 인터셉션 설정
            initializeByteBuddyInterception(inst);
            
            // 런타임 DataSource 발견 시작
            initializeRuntimeDiscovery(inst);
            
            // Spring Boot DataSource 감지 (5초 후)
            java.util.concurrent.ScheduledExecutorService springDetector = 
                java.util.concurrent.Executors.newSingleThreadScheduledExecutor();
            springDetector.schedule(() -> {
                try {
                    SpringBootDataSourceDetector.detectAndRegisterDataSources();
                    System.out.println("🌟 Spring Boot DataSource 감지 완료: " + 
                                     SpringBootDataSourceDetector.getDetectedDataSourceCount() + "개 발견");
                } catch (Exception e) {
                    System.out.println("❌ Spring Boot DataSource 감지 실패: " + e.getMessage());
                }
            }, 5, java.util.concurrent.TimeUnit.SECONDS);
            
            // 직접 DataSource 스캐너 시작 (더 공격적인 방법)
            DirectDataSourceFinder.startPeriodicScanning();
            
            // HikariCP MXBean 강제 등록 시도 (지연 실행)
            java.util.concurrent.ScheduledExecutorService mxbeanForcer = 
                java.util.concurrent.Executors.newSingleThreadScheduledExecutor();
            mxbeanForcer.schedule(() -> {
                try {
                    forceHikariCPMXBeanRegistration();
                } catch (Exception e) {
                    System.out.println("❌ HikariCP MXBean 강제 등록 실패: " + e.getMessage());
                }
            }, 75, java.util.concurrent.TimeUnit.SECONDS);
            
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
                
                // 모든 Connection 구현체 인터셉트 (HikariCP Proxy 포함)
                .type(ElementMatchers.isSubTypeOf(java.sql.Connection.class)
                     .and(ElementMatchers.not(ElementMatchers.isInterface()))
                     .or(ElementMatchers.nameContains("HikariProxy"))
                     .or(ElementMatchers.nameContains("PgConnection")))
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
                
                // 모든 PreparedStatement 구현체 인터셉트 (HikariCP Proxy 포함)
                .type(ElementMatchers.isSubTypeOf(java.sql.PreparedStatement.class)
                     .and(ElementMatchers.not(ElementMatchers.isInterface()))
                     .or(ElementMatchers.nameContains("HikariProxy"))
                     .or(ElementMatchers.nameContains("PgPreparedStatement")))
                .transform((builder, type, classLoader, module, protectionDomain) -> {
                    System.out.println("🔍 PreparedStatement 클래스 발견: " + type.getName());
                    return builder
                        .method(ElementMatchers.named("execute")
                               .or(ElementMatchers.named("executeQuery"))
                               .or(ElementMatchers.named("executeUpdate"))
                               .or(ElementMatchers.named("executeBatch")))
                        .intercept(MethodDelegation.to(UniversalJDBCInterceptor.class));
                })
                
                // 모든 Statement 구현체 인터셉트 (HikariCP Proxy 포함)
                .type(ElementMatchers.isSubTypeOf(java.sql.Statement.class)
                     .and(ElementMatchers.not(ElementMatchers.isInterface()))
                     .and(ElementMatchers.not(ElementMatchers.isSubTypeOf(java.sql.PreparedStatement.class)))
                     .or(ElementMatchers.nameContains("HikariProxy"))
                     .or(ElementMatchers.nameContains("PgStatement")))
                .transform((builder, type, classLoader, module, protectionDomain) -> {
                    System.out.println("🔍 Statement 클래스 발견: " + type.getName());
                    return builder
                        .method(ElementMatchers.named("execute")
                               .or(ElementMatchers.named("executeQuery"))
                               .or(ElementMatchers.named("executeUpdate")))
                        .intercept(MethodDelegation.to(UniversalJDBCInterceptor.class));
                })
                
                // 모든 DataSource 구현체 인터셉트 (Connection Pool 감지)
                .type(ElementMatchers.isSubTypeOf(javax.sql.DataSource.class)
                     .and(ElementMatchers.not(ElementMatchers.isInterface()))
                     .and(ElementMatchers.not(ElementMatchers.nameContains("Proxy"))))
                .transform((builder, type, classLoader, module, protectionDomain) -> {
                    System.out.println("🔗 DataSource 클래스 발견: " + type.getName());
                    return builder
                        .method(ElementMatchers.named("getConnection"))
                        .intercept(MethodDelegation.to(DataSourceInterceptor.class));
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
     * Get the global metrics collector
     */
    public static MetricsCollector getGlobalMetricsCollector() {
        return globalMetricsCollector;
    }
    
    /**
     * HikariCP MXBean 강제 등록 시도
     */
    private static void forceHikariCPMXBeanRegistration() {
        System.out.println("🔧 HikariCP MXBean 강제 등록 시도 시작...");
        
        try {
            // 모든 로드된 클래스에서 HikariDataSource 찾기
            Class<?>[] loadedClasses = instrumentation.getAllLoadedClasses();
            
            for (Class<?> clazz : loadedClasses) {
                if (clazz.getName().equals("com.zaxxer.hikari.HikariDataSource")) {
                    System.out.println("✅ HikariDataSource 클래스 발견: " + clazz.getName());
                    
                    // Static 필드나 인스턴스에서 HikariDataSource 객체 찾기
                    findAndRegisterHikariInstances(clazz);
                    break;
                }
            }
            
            // Spring ApplicationContext를 통한 접근 시도
            trySpringApplicationContextAccess();
            
        } catch (Exception e) {
            System.out.println("❌ HikariCP MXBean 강제 등록 중 오류: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    /**
     * Spring ApplicationContext를 통해 HikariDataSource 찾기 및 MXBean 등록
     */
    private static void trySpringApplicationContextAccess() {
        try {
            Class<?> contextHolderClass = Class.forName("org.springframework.context.ApplicationContextHolder");
            java.lang.reflect.Method getContextMethod = contextHolderClass.getMethod("getApplicationContext");
            Object applicationContext = getContextMethod.invoke(null);
            
            if (applicationContext != null) {
                System.out.println("✅ Spring ApplicationContext 발견");
                
                // DataSource Bean 가져오기
                java.lang.reflect.Method getBeanMethod = applicationContext.getClass().getMethod("getBean", Class.class);
                Object dataSourceBean = getBeanMethod.invoke(applicationContext, javax.sql.DataSource.class);
                
                if (dataSourceBean != null && dataSourceBean.getClass().getName().contains("Hikari")) {
                    System.out.println("🎯 HikariDataSource Bean 발견: " + dataSourceBean.getClass().getSimpleName());
                    
                    // MXBean 등록 강제 시도
                    forceEnableMXBean(dataSourceBean);
                    
                    // MetricsCollector에도 등록
                    if (globalMetricsCollector != null) {
                        globalMetricsCollector.registerDataSource((javax.sql.DataSource) dataSourceBean);
                        System.out.println("✅ DataSource를 MetricsCollector에 등록 완료");
                    }
                }
            }
        } catch (Exception e) {
            System.out.println("🔍 Spring ApplicationContext 접근 실패 (정상일 수 있음): " + e.getMessage());
        }
    }
    
    /**
     * HikariDataSource 인스턴스 찾기 및 등록
     */
    private static void findAndRegisterHikariInstances(Class<?> hikariClass) {
        try {
            // 이 방법은 복잡하므로 Spring 방식을 우선 시도
            System.out.println("🔍 HikariDataSource 인스턴스 검색 중...");
        } catch (Exception e) {
            System.out.println("🔍 HikariDataSource 인스턴스 검색 실패: " + e.getMessage());
        }
    }
    
    /**
     * HikariDataSource에서 MXBean 등록 강제 활성화
     */
    private static void forceEnableMXBean(Object dataSource) {
        try {
            // setRegisterMbeans 메서드 호출 시도
            java.lang.reflect.Method setRegisterMBeans = dataSource.getClass().getMethod("setRegisterMbeans", boolean.class);
            setRegisterMBeans.invoke(dataSource, true);
            
            System.out.println("🔧 HikariCP setRegisterMbeans(true) 호출 성공");
            
            // HikariPool에서 직접 MXBean 등록 시도
            try {
                java.lang.reflect.Field poolField = dataSource.getClass().getDeclaredField("pool");
                poolField.setAccessible(true);
                Object pool = poolField.get(dataSource);
                
                if (pool != null) {
                    System.out.println("🎯 HikariPool 인스턴스 접근 성공");
                    
                    // Pool의 MXBean 등록 상태 확인
                    try {
                        java.lang.reflect.Method registerMBeanMethod = pool.getClass().getMethod("setRegisterMbeans", boolean.class);
                        registerMBeanMethod.invoke(pool, true);
                        System.out.println("✅ HikariPool MXBean 등록 강제 활성화 성공");
                    } catch (Exception e) {
                        System.out.println("🔍 HikariPool MXBean 등록 방법을 찾을 수 없음: " + e.getMessage());
                    }
                }
                
            } catch (Exception e) {
                System.out.println("🔍 HikariPool 접근 실패: " + e.getMessage());
            }
            
        } catch (Exception e) {
            System.out.println("❌ HikariCP MXBean 강제 등록 실패: " + e.getMessage());
        }
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