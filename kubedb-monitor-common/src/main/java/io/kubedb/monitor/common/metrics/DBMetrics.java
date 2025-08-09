package io.kubedb.monitor.common.metrics;

import java.time.Instant;

/**
 * Represents database performance metrics collected by the agent
 */
public class DBMetrics {
    private final String sql;
    private final long executionTimeMs;
    private final String databaseType;
    private final String connectionUrl;
    private final Instant timestamp;
    private final boolean isError;
    private final String errorMessage;

    private DBMetrics(Builder builder) {
        this.sql = builder.sql;
        this.executionTimeMs = builder.executionTimeMs;
        this.databaseType = builder.databaseType;
        this.connectionUrl = builder.connectionUrl;
        this.timestamp = builder.timestamp;
        this.isError = builder.isError;
        this.errorMessage = builder.errorMessage;
    }

    public static Builder builder() {
        return new Builder();
    }

    // Getters
    public String getSql() { return sql; }
    public long getExecutionTimeMs() { return executionTimeMs; }
    public String getDatabaseType() { return databaseType; }
    public String getConnectionUrl() { return connectionUrl; }
    public Instant getTimestamp() { return timestamp; }
    public boolean isError() { return isError; }
    public String getErrorMessage() { return errorMessage; }

    public static class Builder {
        private String sql;
        private long executionTimeMs;
        private String databaseType;
        private String connectionUrl;
        private Instant timestamp = Instant.now();
        private boolean isError = false;
        private String errorMessage;

        public Builder sql(String sql) {
            this.sql = sql;
            return this;
        }

        public Builder executionTimeMs(long executionTimeMs) {
            this.executionTimeMs = executionTimeMs;
            return this;
        }

        public Builder databaseType(String databaseType) {
            this.databaseType = databaseType;
            return this;
        }

        public Builder connectionUrl(String connectionUrl) {
            this.connectionUrl = connectionUrl;
            return this;
        }

        public Builder timestamp(Instant timestamp) {
            this.timestamp = timestamp;
            return this;
        }

        public Builder error(String errorMessage) {
            this.isError = true;
            this.errorMessage = errorMessage;
            return this;
        }

        public DBMetrics build() {
            return new DBMetrics(this);
        }
    }

    @Override
    public String toString() {
        return "DBMetrics{" +
                "sql='" + sql + '\'' +
                ", executionTimeMs=" + executionTimeMs +
                ", databaseType='" + databaseType + '\'' +
                ", connectionUrl='" + connectionUrl + '\'' +
                ", timestamp=" + timestamp +
                ", isError=" + isError +
                ", errorMessage='" + errorMessage + '\'' +
                '}';
    }
}