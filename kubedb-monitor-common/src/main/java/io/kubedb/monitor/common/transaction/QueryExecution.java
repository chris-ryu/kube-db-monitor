package io.kubedb.monitor.common.transaction;

import java.time.Instant;

/**
 * Represents a single query execution within a transaction
 */
public class QueryExecution {
    private final String queryId;
    private final String sql;
    private final long executionTimeMs;
    private final Instant timestamp;
    private String status = "SUCCESS"; // Default status
    
    public QueryExecution(String queryId, String sql, long executionTimeMs) {
        this.queryId = queryId;
        this.sql = sql;
        this.executionTimeMs = executionTimeMs;
        this.timestamp = Instant.now();
    }
    
    public String getQueryId() {
        return queryId;
    }
    
    public String getSql() {
        return sql;
    }
    
    public String getSqlPattern() {
        // Create a simplified pattern by removing specific values
        return sql.replaceAll("'[^']*'", "?")
                  .replaceAll("\\b\\d+\\b", "?")
                  .replaceAll("\\s+", " ")
                  .trim();
    }
    
    public String getSqlType() {
        String normalizedSql = sql.trim().toUpperCase();
        if (normalizedSql.startsWith("SELECT")) return "SELECT";
        if (normalizedSql.startsWith("INSERT")) return "INSERT";
        if (normalizedSql.startsWith("UPDATE")) return "UPDATE";
        if (normalizedSql.startsWith("DELETE")) return "DELETE";
        if (normalizedSql.startsWith("CREATE")) return "CREATE";
        if (normalizedSql.startsWith("DROP")) return "DROP";
        if (normalizedSql.startsWith("ALTER")) return "ALTER";
        return "OTHER";
    }
    
    public long getExecutionTimeMs() {
        return executionTimeMs;
    }
    
    public Instant getTimestamp() {
        return timestamp;
    }
    
    public String getStatus() {
        return status;
    }
    
    public void setStatus(String status) {
        this.status = status;
    }
}