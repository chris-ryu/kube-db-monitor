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
    private final String collectorType;
    private final String collectorEndpoint;
    
    // PostgreSQL 특화 호환성 설정들
    private final boolean postgresqlStrictCompatibility;
    private final boolean excludePreparedStatementTransformation;
    private final boolean preserveTransactionBoundaries;
    private final boolean excludeConnectionManagement;
    private final boolean avoidNullParameterTransformation;
    private final boolean avoidAutocommitStateChange;
    private final boolean postgresqlFixUnknownTypesValue;
    private final boolean postgresqlFixAutocommitConflict;
    private final boolean safeTransformationMode;
    private final String logLevel;
    
    private AgentConfig(Builder builder) {
        this.enabled = builder.enabled;
        this.samplingRate = builder.samplingRate;
        this.supportedDatabases = builder.supportedDatabases;
        this.maskSqlParams = builder.maskSqlParams;
        this.slowQueryThresholdMs = builder.slowQueryThresholdMs;
        this.collectorType = builder.collectorType;
        this.collectorEndpoint = builder.collectorEndpoint;
        
        // PostgreSQL 특화 설정 초기화
        this.postgresqlStrictCompatibility = builder.postgresqlStrictCompatibility;
        this.excludePreparedStatementTransformation = builder.excludePreparedStatementTransformation;
        this.preserveTransactionBoundaries = builder.preserveTransactionBoundaries;
        this.excludeConnectionManagement = builder.excludeConnectionManagement;
        this.avoidNullParameterTransformation = builder.avoidNullParameterTransformation;
        this.avoidAutocommitStateChange = builder.avoidAutocommitStateChange;
        this.postgresqlFixUnknownTypesValue = builder.postgresqlFixUnknownTypesValue;
        this.postgresqlFixAutocommitConflict = builder.postgresqlFixAutocommitConflict;
        this.safeTransformationMode = builder.safeTransformationMode;
        this.logLevel = builder.logLevel;
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
        
        // Parse collector type
        if (args.containsKey("collector-type")) {
            builder.collectorType(args.get("collector-type"));
        }
        
        // Parse collector endpoint
        if (args.containsKey("collector-endpoint")) {
            builder.collectorEndpoint(args.get("collector-endpoint"));
        }
        
        // Parse PostgreSQL 특화 호환성 설정들
        if (args.containsKey("postgresql-strict-compatibility")) {
            builder.postgresqlStrictCompatibility(Boolean.parseBoolean(args.get("postgresql-strict-compatibility")));
        }
        if (args.containsKey("exclude-prepared-statement-transformation")) {
            builder.excludePreparedStatementTransformation(Boolean.parseBoolean(args.get("exclude-prepared-statement-transformation")));
        }
        if (args.containsKey("preserve-transaction-boundaries")) {
            builder.preserveTransactionBoundaries(Boolean.parseBoolean(args.get("preserve-transaction-boundaries")));
        }
        if (args.containsKey("exclude-connection-management")) {
            builder.excludeConnectionManagement(Boolean.parseBoolean(args.get("exclude-connection-management")));
        }
        if (args.containsKey("avoid-null-parameter-transformation")) {
            builder.avoidNullParameterTransformation(Boolean.parseBoolean(args.get("avoid-null-parameter-transformation")));
        }
        if (args.containsKey("avoid-autocommit-state-change")) {
            builder.avoidAutocommitStateChange(Boolean.parseBoolean(args.get("avoid-autocommit-state-change")));
        }
        if (args.containsKey("postgresql-fix-unknown-types-value")) {
            builder.postgresqlFixUnknownTypesValue(Boolean.parseBoolean(args.get("postgresql-fix-unknown-types-value")));
        }
        if (args.containsKey("postgresql-fix-autocommit-conflict")) {
            builder.postgresqlFixAutocommitConflict(Boolean.parseBoolean(args.get("postgresql-fix-autocommit-conflict")));
        }
        if (args.containsKey("safe-transformation-mode")) {
            builder.safeTransformationMode(Boolean.parseBoolean(args.get("safe-transformation-mode")));
        }
        if (args.containsKey("log-level")) {
            builder.logLevel(args.get("log-level"));
        }
        
        AgentConfig config = builder.build();
        
        // Set system properties for metrics collector
        if (config.getCollectorType() != null) {
            System.setProperty("kubedb.monitor.collector.type", config.getCollectorType().toUpperCase());
        }
        if (config.getCollectorEndpoint() != null) {
            System.setProperty("kubedb.monitor.http.endpoint", config.getCollectorEndpoint());
        }
        
        logger.info("Agent configuration: {}", config);
        
        return config;
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
    public String getCollectorType() { return collectorType; }
    public String getCollectorEndpoint() { return collectorEndpoint; }
    
    // PostgreSQL 특화 호환성 설정 getters
    public boolean isPostgresqlStrictCompatibility() { return postgresqlStrictCompatibility; }
    public boolean isExcludePreparedStatementTransformation() { return excludePreparedStatementTransformation; }
    public boolean isPreserveTransactionBoundaries() { return preserveTransactionBoundaries; }
    public boolean isExcludeConnectionManagement() { return excludeConnectionManagement; }
    public boolean isAvoidNullParameterTransformation() { return avoidNullParameterTransformation; }
    public boolean isAvoidAutocommitStateChange() { return avoidAutocommitStateChange; }
    public boolean isPostgresqlFixUnknownTypesValue() { return postgresqlFixUnknownTypesValue; }
    public boolean isPostgresqlFixAutocommitConflict() { return postgresqlFixAutocommitConflict; }
    public boolean isSafeTransformationMode() { return safeTransformationMode; }
    public String getLogLevel() { return logLevel; }
    
    public static class Builder {
        private boolean enabled = DEFAULT_ENABLED;
        private double samplingRate = DEFAULT_SAMPLING_RATE;
        private List<String> supportedDatabases = DEFAULT_SUPPORTED_DATABASES;
        private boolean maskSqlParams = DEFAULT_MASK_SQL_PARAMS;
        private long slowQueryThresholdMs = DEFAULT_SLOW_QUERY_THRESHOLD_MS;
        private String collectorType = "COMPOSITE"; // Default collector type
        private String collectorEndpoint;
        
        // PostgreSQL 특화 호환성 설정들 (기본값: 안전 모드)
        private boolean postgresqlStrictCompatibility = false;
        private boolean excludePreparedStatementTransformation = false;
        private boolean preserveTransactionBoundaries = false;
        private boolean excludeConnectionManagement = false;
        private boolean avoidNullParameterTransformation = false;
        private boolean avoidAutocommitStateChange = false;
        private boolean postgresqlFixUnknownTypesValue = false;
        private boolean postgresqlFixAutocommitConflict = false;
        private boolean safeTransformationMode = false;
        private String logLevel = "INFO";
        
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
        
        public Builder collectorType(String collectorType) {
            this.collectorType = collectorType;
            return this;
        }
        
        public Builder collectorEndpoint(String collectorEndpoint) {
            this.collectorEndpoint = collectorEndpoint;
            return this;
        }
        
        // PostgreSQL 특화 호환성 설정 빌더 메서드들
        public Builder postgresqlStrictCompatibility(boolean postgresqlStrictCompatibility) {
            this.postgresqlStrictCompatibility = postgresqlStrictCompatibility;
            return this;
        }
        
        public Builder excludePreparedStatementTransformation(boolean excludePreparedStatementTransformation) {
            this.excludePreparedStatementTransformation = excludePreparedStatementTransformation;
            return this;
        }
        
        public Builder preserveTransactionBoundaries(boolean preserveTransactionBoundaries) {
            this.preserveTransactionBoundaries = preserveTransactionBoundaries;
            return this;
        }
        
        public Builder excludeConnectionManagement(boolean excludeConnectionManagement) {
            this.excludeConnectionManagement = excludeConnectionManagement;
            return this;
        }
        
        public Builder avoidNullParameterTransformation(boolean avoidNullParameterTransformation) {
            this.avoidNullParameterTransformation = avoidNullParameterTransformation;
            return this;
        }
        
        public Builder avoidAutocommitStateChange(boolean avoidAutocommitStateChange) {
            this.avoidAutocommitStateChange = avoidAutocommitStateChange;
            return this;
        }
        
        public Builder postgresqlFixUnknownTypesValue(boolean postgresqlFixUnknownTypesValue) {
            this.postgresqlFixUnknownTypesValue = postgresqlFixUnknownTypesValue;
            return this;
        }
        
        public Builder postgresqlFixAutocommitConflict(boolean postgresqlFixAutocommitConflict) {
            this.postgresqlFixAutocommitConflict = postgresqlFixAutocommitConflict;
            return this;
        }
        
        public Builder safeTransformationMode(boolean safeTransformationMode) {
            this.safeTransformationMode = safeTransformationMode;
            return this;
        }
        
        public Builder logLevel(String logLevel) {
            this.logLevel = logLevel;
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
                ", collectorType='" + collectorType + '\'' +
                ", collectorEndpoint='" + collectorEndpoint + '\'' +
                ", postgresqlStrictCompatibility=" + postgresqlStrictCompatibility +
                ", excludePreparedStatementTransformation=" + excludePreparedStatementTransformation +
                ", preserveTransactionBoundaries=" + preserveTransactionBoundaries +
                ", excludeConnectionManagement=" + excludeConnectionManagement +
                ", avoidNullParameterTransformation=" + avoidNullParameterTransformation +
                ", avoidAutocommitStateChange=" + avoidAutocommitStateChange +
                ", postgresqlFixUnknownTypesValue=" + postgresqlFixUnknownTypesValue +
                ", postgresqlFixAutocommitConflict=" + postgresqlFixAutocommitConflict +
                ", safeTransformationMode=" + safeTransformationMode +
                ", logLevel='" + logLevel + '\'' +
                '}';
    }
}