package io.kubedb.monitor.agent;

import java.sql.*;
import java.util.logging.Logger;

/**
 * PostgreSQL Statement 프록시
 * 
 * ASM 변환 없이 Statement 레벨에서 SQL 실행을 모니터링합니다.
 */
public class PostgreSQLStatementProxy implements Statement {
    private static final Logger logger = Logger.getLogger(PostgreSQLStatementProxy.class.getName());
    
    private final Statement delegate;
    private final AgentConfig config;
    private final MetricsCollector metricsCollector;
    
    public PostgreSQLStatementProxy(Statement delegate, AgentConfig config, MetricsCollector metricsCollector) {
        this.delegate = delegate;
        this.config = config;
        this.metricsCollector = metricsCollector;
    }
    
    @Override
    public ResultSet executeQuery(String sql) throws SQLException {
        long startTime = System.nanoTime();
        try {
            ResultSet result = delegate.executeQuery(sql);
            metricsCollector.recordQuery(sql, System.nanoTime() - startTime);
            return result;
        } catch (SQLException e) {
            metricsCollector.recordError("executeQuery", e);
            throw e;
        }
    }
    
    @Override
    public int executeUpdate(String sql) throws SQLException {
        long startTime = System.nanoTime();
        try {
            int result = delegate.executeUpdate(sql);
            metricsCollector.recordQuery(sql, System.nanoTime() - startTime);
            return result;
        } catch (SQLException e) {
            metricsCollector.recordError("executeUpdate", e);
            throw e;
        }
    }
    
    @Override
    public boolean execute(String sql) throws SQLException {
        long startTime = System.nanoTime();
        try {
            boolean result = delegate.execute(sql);
            metricsCollector.recordQuery(sql, System.nanoTime() - startTime);
            return result;
        } catch (SQLException e) {
            metricsCollector.recordError("execute", e);
            throw e;
        }
    }
    
    // 나머지 메서드들은 단순 위임
    @Override
    public void close() throws SQLException {
        delegate.close();
    }
    
    @Override
    public int getMaxFieldSize() throws SQLException {
        return delegate.getMaxFieldSize();
    }
    
    @Override
    public void setMaxFieldSize(int max) throws SQLException {
        delegate.setMaxFieldSize(max);
    }
    
    @Override
    public int getMaxRows() throws SQLException {
        return delegate.getMaxRows();
    }
    
    @Override
    public void setMaxRows(int max) throws SQLException {
        delegate.setMaxRows(max);
    }
    
    @Override
    public void setEscapeProcessing(boolean enable) throws SQLException {
        delegate.setEscapeProcessing(enable);
    }
    
    @Override
    public int getQueryTimeout() throws SQLException {
        return delegate.getQueryTimeout();
    }
    
    @Override
    public void setQueryTimeout(int seconds) throws SQLException {
        delegate.setQueryTimeout(seconds);
    }
    
    @Override
    public void cancel() throws SQLException {
        delegate.cancel();
    }
    
    @Override
    public SQLWarning getWarnings() throws SQLException {
        return delegate.getWarnings();
    }
    
    @Override
    public void clearWarnings() throws SQLException {
        delegate.clearWarnings();
    }
    
    @Override
    public void setCursorName(String name) throws SQLException {
        delegate.setCursorName(name);
    }
    
    @Override
    public ResultSet getResultSet() throws SQLException {
        return delegate.getResultSet();
    }
    
    @Override
    public int getUpdateCount() throws SQLException {
        return delegate.getUpdateCount();
    }
    
    @Override
    public boolean getMoreResults() throws SQLException {
        return delegate.getMoreResults();
    }
    
    @Override
    public void setFetchDirection(int direction) throws SQLException {
        delegate.setFetchDirection(direction);
    }
    
    @Override
    public int getFetchDirection() throws SQLException {
        return delegate.getFetchDirection();
    }
    
    @Override
    public void setFetchSize(int rows) throws SQLException {
        delegate.setFetchSize(rows);
    }
    
    @Override
    public int getFetchSize() throws SQLException {
        return delegate.getFetchSize();
    }
    
    @Override
    public int getResultSetConcurrency() throws SQLException {
        return delegate.getResultSetConcurrency();
    }
    
    @Override
    public int getResultSetType() throws SQLException {
        return delegate.getResultSetType();
    }
    
    @Override
    public void addBatch(String sql) throws SQLException {
        delegate.addBatch(sql);
    }
    
    @Override
    public void clearBatch() throws SQLException {
        delegate.clearBatch();
    }
    
    @Override
    public int[] executeBatch() throws SQLException {
        long startTime = System.nanoTime();
        try {
            int[] result = delegate.executeBatch();
            metricsCollector.recordQuery("BATCH", System.nanoTime() - startTime);
            return result;
        } catch (SQLException e) {
            metricsCollector.recordError("executeBatch", e);
            throw e;
        }
    }
    
    @Override
    public Connection getConnection() throws SQLException {
        return delegate.getConnection();
    }
    
    @Override
    public boolean getMoreResults(int current) throws SQLException {
        return delegate.getMoreResults(current);
    }
    
    @Override
    public ResultSet getGeneratedKeys() throws SQLException {
        return delegate.getGeneratedKeys();
    }
    
    @Override
    public int executeUpdate(String sql, int autoGeneratedKeys) throws SQLException {
        long startTime = System.nanoTime();
        try {
            int result = delegate.executeUpdate(sql, autoGeneratedKeys);
            metricsCollector.recordQuery(sql, System.nanoTime() - startTime);
            return result;
        } catch (SQLException e) {
            metricsCollector.recordError("executeUpdate", e);
            throw e;
        }
    }
    
    @Override
    public int executeUpdate(String sql, int[] columnIndexes) throws SQLException {
        long startTime = System.nanoTime();
        try {
            int result = delegate.executeUpdate(sql, columnIndexes);
            metricsCollector.recordQuery(sql, System.nanoTime() - startTime);
            return result;
        } catch (SQLException e) {
            metricsCollector.recordError("executeUpdate", e);
            throw e;
        }
    }
    
    @Override
    public int executeUpdate(String sql, String[] columnNames) throws SQLException {
        long startTime = System.nanoTime();
        try {
            int result = delegate.executeUpdate(sql, columnNames);
            metricsCollector.recordQuery(sql, System.nanoTime() - startTime);
            return result;
        } catch (SQLException e) {
            metricsCollector.recordError("executeUpdate", e);
            throw e;
        }
    }
    
    @Override
    public boolean execute(String sql, int autoGeneratedKeys) throws SQLException {
        long startTime = System.nanoTime();
        try {
            boolean result = delegate.execute(sql, autoGeneratedKeys);
            metricsCollector.recordQuery(sql, System.nanoTime() - startTime);
            return result;
        } catch (SQLException e) {
            metricsCollector.recordError("execute", e);
            throw e;
        }
    }
    
    @Override
    public boolean execute(String sql, int[] columnIndexes) throws SQLException {
        long startTime = System.nanoTime();
        try {
            boolean result = delegate.execute(sql, columnIndexes);
            metricsCollector.recordQuery(sql, System.nanoTime() - startTime);
            return result;
        } catch (SQLException e) {
            metricsCollector.recordError("execute", e);
            throw e;
        }
    }
    
    @Override
    public boolean execute(String sql, String[] columnNames) throws SQLException {
        long startTime = System.nanoTime();
        try {
            boolean result = delegate.execute(sql, columnNames);
            metricsCollector.recordQuery(sql, System.nanoTime() - startTime);
            return result;
        } catch (SQLException e) {
            metricsCollector.recordError("execute", e);
            throw e;
        }
    }
    
    @Override
    public int getResultSetHoldability() throws SQLException {
        return delegate.getResultSetHoldability();
    }
    
    @Override
    public boolean isClosed() throws SQLException {
        return delegate.isClosed();
    }
    
    @Override
    public void setPoolable(boolean poolable) throws SQLException {
        delegate.setPoolable(poolable);
    }
    
    @Override
    public boolean isPoolable() throws SQLException {
        return delegate.isPoolable();
    }
    
    @Override
    public void closeOnCompletion() throws SQLException {
        delegate.closeOnCompletion();
    }
    
    @Override
    public boolean isCloseOnCompletion() throws SQLException {
        return delegate.isCloseOnCompletion();
    }
    
    @Override
    public <T> T unwrap(Class<T> iface) throws SQLException {
        return delegate.unwrap(iface);
    }
    
    @Override
    public boolean isWrapperFor(Class<?> iface) throws SQLException {
        return delegate.isWrapperFor(iface);
    }
}