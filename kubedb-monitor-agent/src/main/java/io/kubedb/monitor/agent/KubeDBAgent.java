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
        logger.info("KubeDB Monitor Agent starting...");
        
        instrumentation = inst;
        config = AgentConfig.fromArgs(agentArgs);
        
        if (!config.isEnabled()) {
            logger.info("KubeDB Monitor Agent is disabled");
            return;
        }
        
        try {
            // Initialize the JDBC interceptor
            JDBCInterceptor interceptor = new JDBCInterceptor(config);
            
            // Add transformer for JDBC classes
            instrumentation.addTransformer(interceptor, true);
            
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
}