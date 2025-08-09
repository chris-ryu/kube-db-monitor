package io.kubedb.monitor.agent;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Configuration for KubeDB Monitor Agent
 */
public class AgentConfig {
    private static final Logger logger = LoggerFactory.getLogger(AgentConfig.class);
    
    // Default configuration values
    private static final boolean DEFAULT_ENABLED = true;
    private static final double DEFAULT_SAMPLING_RATE = 1.0;
    private static final List<String> DEFAULT_SUPPORTED_DATABASES = Arrays.asList("mysql", "postgresql", "h2");
    private static final boolean DEFAULT_MASK_SQL_PARAMS = true;
    private static final long DEFAULT_SLOW_QUERY_THRESHOLD_MS = 1000;
    
    private final boolean enabled;
    private final double samplingRate;
    private final List<String> supportedDatabases;
    private final boolean maskSqlParams;
    private final long slowQueryThresholdMs;
    
    private AgentConfig(Builder builder) {
        this.enabled = builder.enabled;
        this.samplingRate = builder.samplingRate;
        this.supportedDatabases = builder.supportedDatabases;
        this.maskSqlParams = builder.maskSqlParams;
        this.slowQueryThresholdMs = builder.slowQueryThresholdMs;
    }
    
    /**
     * Parse agent configuration from command line arguments
     */
    public static AgentConfig fromArgs(String agentArgs) {
        Builder builder = new Builder();
        
        if (agentArgs == null || agentArgs.trim().isEmpty()) {
            logger.info("No agent arguments provided, using defaults");
            return builder.build();
        }
        
        Map<String, String> args = parseArgs(agentArgs);
        
        // Parse enabled flag
        if (args.containsKey("enabled")) {
            builder.enabled(Boolean.parseBoolean(args.get("enabled")));
        }
        
        // Parse sampling rate
        if (args.containsKey("sampling-rate")) {
            try {
                double rate = Double.parseDouble(args.get("sampling-rate"));
                if (rate < 0.0 || rate > 1.0) {
                    logger.warn("Invalid sampling rate: {}, using default: {}", rate, DEFAULT_SAMPLING_RATE);
                } else {
                    builder.samplingRate(rate);
                }
            } catch (NumberFormatException e) {
                logger.warn("Invalid sampling rate format: {}, using default: {}", args.get("sampling-rate"), DEFAULT_SAMPLING_RATE);
            }
        }
        
        // Parse supported databases
        if (args.containsKey("db-types")) {
            List<String> dbTypes = Arrays.stream(args.get("db-types").split(","))
                    .map(String::trim)
                    .filter(s -> !s.isEmpty())
                    .collect(Collectors.toList());
            builder.supportedDatabases(dbTypes);
        }
        
        // Parse mask SQL params
        if (args.containsKey("mask-sql-params")) {
            builder.maskSqlParams(Boolean.parseBoolean(args.get("mask-sql-params")));
        }
        
        // Parse slow query threshold
        if (args.containsKey("slow-query-threshold")) {
            try {
                long threshold = Long.parseLong(args.get("slow-query-threshold"));
                builder.slowQueryThresholdMs(threshold);
            } catch (NumberFormatException e) {
                logger.warn("Invalid slow query threshold: {}, using default: {}", args.get("slow-query-threshold"), DEFAULT_SLOW_QUERY_THRESHOLD_MS);
            }
        }
        
        return builder.build();
    }
    
    private static Map<String, String> parseArgs(String agentArgs) {
        Map<String, String> args = new HashMap<>();
        
        for (String arg : agentArgs.split(",")) {
            String[] parts = arg.split("=", 2);
            if (parts.length == 2) {
                args.put(parts[0].trim(), parts[1].trim());
            }
        }
        
        return args;
    }
    
    // Getters
    public boolean isEnabled() { return enabled; }
    public double getSamplingRate() { return samplingRate; }
    public List<String> getSupportedDatabases() { return supportedDatabases; }
    public boolean isMaskSqlParams() { return maskSqlParams; }
    public long getSlowQueryThresholdMs() { return slowQueryThresholdMs; }
    
    public static class Builder {
        private boolean enabled = DEFAULT_ENABLED;
        private double samplingRate = DEFAULT_SAMPLING_RATE;
        private List<String> supportedDatabases = DEFAULT_SUPPORTED_DATABASES;
        private boolean maskSqlParams = DEFAULT_MASK_SQL_PARAMS;
        private long slowQueryThresholdMs = DEFAULT_SLOW_QUERY_THRESHOLD_MS;
        
        public Builder enabled(boolean enabled) {
            this.enabled = enabled;
            return this;
        }
        
        public Builder samplingRate(double samplingRate) {
            this.samplingRate = samplingRate;
            return this;
        }
        
        public Builder supportedDatabases(List<String> supportedDatabases) {
            this.supportedDatabases = supportedDatabases;
            return this;
        }
        
        public Builder maskSqlParams(boolean maskSqlParams) {
            this.maskSqlParams = maskSqlParams;
            return this;
        }
        
        public Builder slowQueryThresholdMs(long slowQueryThresholdMs) {
            this.slowQueryThresholdMs = slowQueryThresholdMs;
            return this;
        }
        
        public AgentConfig build() {
            return new AgentConfig(this);
        }
    }
    
    @Override
    public String toString() {
        return "AgentConfig{" +
                "enabled=" + enabled +
                ", samplingRate=" + samplingRate +
                ", supportedDatabases=" + supportedDatabases +
                ", maskSqlParams=" + maskSqlParams +
                ", slowQueryThresholdMs=" + slowQueryThresholdMs +
                '}';
    }
}