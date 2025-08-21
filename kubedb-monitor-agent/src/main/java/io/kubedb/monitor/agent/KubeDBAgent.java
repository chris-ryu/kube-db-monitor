package io.kubedb.monitor.agent;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.instrument.Instrumentation;

/**
 * Main Java Agent class for KubeDB Monitor
 * This agent intercepts JDBC calls and collects performance metrics
 */
public class KubeDBAgent {
    private static final Logger logger = LoggerFactory.getLogger(KubeDBAgent.class);
    
    private static Instrumentation instrumentation;
    private static AgentConfig config;
    
    /**
     * JVM hook to statically load the javaagent at startup.
     * Called by the JVM when the agent is loaded via -javaagent
     */
    public static void premain(String agentArgs, Instrumentation inst) {
        // 명시적 System.out 로그 추가 (로깅 시스템이 초기화되지 않을 경우를 대비)
        System.out.println("🚀 KubeDB Monitor Agent starting with args: " + agentArgs);
        System.out.println("🔥🔥🔥 [UNIQUE-TEST-MESSAGE] 이 메시지가 보이면 새 Agent 코드가 실행되고 있음 🔥🔥🔥");
        
        logger.info("KubeDB Monitor Agent starting...");
        
        instrumentation = inst;
        config = AgentConfig.fromArgs(agentArgs);
        
        System.out.println("📊 Agent config loaded - enabled: " + config.isEnabled());
        
        if (!config.isEnabled()) {
            System.out.println("❌ KubeDB Monitor Agent is disabled");
            logger.info("KubeDB Monitor Agent is disabled");
            return;
        }
        
        try {
            System.out.println("🔍 [DEBUG] initializeHybridMonitoring 호출 직전");
            // 하이브리드 방식: PostgreSQL 드라이버 제외한 ASM 변환 + 프록시 방식 함께 사용
            initializeHybridMonitoring(inst, config);
            System.out.println("🔍 [DEBUG] initializeHybridMonitoring 호출 완료");
            
            System.out.println("✅ KubeDB Monitor Agent started successfully");
            System.out.println("📋 Monitoring databases: " + config.getSupportedDatabases());
            logger.info("KubeDB Monitor Agent started successfully");
            logger.info("Monitoring databases: {}", config.getSupportedDatabases());
            
        } catch (Exception e) {
            logger.error("Failed to start KubeDB Monitor Agent", e);
            throw new RuntimeException("Agent initialization failed", e);
        }
    }
    
    /**
     * JVM hook to dynamically load javaagent at runtime.
     * Called when agent is loaded via Attach API
     */
    public static void agentmain(String agentArgs, Instrumentation inst) {
        logger.info("KubeDB Monitor Agent attaching at runtime...");
        premain(agentArgs, inst);
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
     * 하이브리드 모니터링 초기화
     * PostgreSQL 드라이버 제외한 ASM 변환 + DriverManager 프록시 등록을 통한 안전한 모니터링
     */
    private static void initializeHybridMonitoring(Instrumentation inst, AgentConfig config) throws Exception {
        System.out.println("🔄 순수 Proxy 모니터링 초기화 시작...");
        
        // ASM 변환 완전 제거 - 문서에서 검증된 바와 같이 Proxy 방식이 우수함
        // Connection 프록시 패턴이 ASM 바이트코드 변환보다 우수한 이유:
        // 1. 안전성: 바이트코드 검증 오류 없음
        // 2. 호환성: 모든 Spring/JDBC 환경과 완벽 호환  
        // 3. 문제 해결: PostgreSQL "Unknown Types value" 완벽 해결
        System.out.println("⚠️ ASM 바이트코드 변환 제거됨 - 순수 Proxy 방식 사용");
        
        // PostgreSQL 지원이 설정되어 있는 경우 드라이버 프록시 등록
        if (config.getSupportedDatabases().contains("postgresql")) {
            registerPostgreSQLDriverProxy(config);
        }
        
        // MySQL 등 다른 데이터베이스 지원 시 추가
        if (config.getSupportedDatabases().contains("mysql")) {
            // TODO: MySQL 드라이버 프록시 등록
            System.out.println("📝 MySQL 드라이버 프록시 지원 예정");
        }
        
        System.out.println("✅ 순수 Proxy 모니터링 초기화 완료");
    }
    
    /**
     * PostgreSQL 드라이버 프록시 등록
     * DriverManager에 우리의 프록시 드라이버를 등록하여 모든 연결을 가로채기
     */
    private static void registerPostgreSQLDriverProxy(AgentConfig config) throws Exception {
        try {
            System.out.println("🔗 PostgreSQL 드라이버 프록시 등록 중...");
            
            // 원본 PostgreSQL 드라이버 로드
            Class<?> originalDriverClass = Class.forName("org.postgresql.Driver");
            java.sql.Driver originalDriver = (java.sql.Driver) originalDriverClass.newInstance();
            
            // 프록시 드라이버 생성
            PostgreSQLDriverProxy proxyDriver = new PostgreSQLDriverProxy(originalDriver, config);
            
            // DriverManager에 프록시 드라이버 등록
            java.sql.DriverManager.registerDriver(proxyDriver);
            
            System.out.println("✅ PostgreSQL 드라이버 프록시 등록 성공");
            logger.info("PostgreSQL Driver Proxy registered successfully");
            
        } catch (ClassNotFoundException e) {
            System.out.println("⚠️  PostgreSQL 드라이버를 찾을 수 없음 (정상 - PostgreSQL 미사용시)");
            logger.info("PostgreSQL driver not found in classpath (expected if PostgreSQL not used)");
        } catch (Exception e) {
            System.out.println("❌ PostgreSQL 드라이버 프록시 등록 실패: " + e.getMessage());
            logger.error("Failed to register PostgreSQL driver proxy", e);
            throw e;
        }
    }
}