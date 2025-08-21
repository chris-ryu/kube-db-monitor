package io.kubedb.monitor.agent;

import java.io.InputStream;
import java.io.Reader;
import java.math.BigDecimal;
import java.net.URL;
import java.sql.*;
import java.util.Calendar;
import java.util.logging.Logger;

/**
 * PostgreSQL PreparedStatement 프록시
 * 
 * "Unknown Types value" 에러를 방지하기 위해 setObject(index, null) 호출을
 * 안전한 setNull(index, sqlType) 호출로 변환합니다.
 * 
 * 동시에 쿼리 실행 메트릭을 수집하고 Transaction/Deadlock 모니터링을 수행합니다.
 */
public class PostgreSQLPreparedStatementProxy implements PreparedStatement {
    private static final Logger logger = Logger.getLogger(PostgreSQLPreparedStatementProxy.class.getName());
    
    private final PreparedStatement delegate;
    private final String sql;
    private final PostgreSQLCompatibilityHelper compatibilityHelper;
    private final TransactionAwareJDBCInterceptor transactionInterceptor;
    private long startTime;
    
    public PostgreSQLPreparedStatementProxy(PreparedStatement delegate, String sql, 
                                          PostgreSQLCompatibilityHelper compatibilityHelper,
                                          TransactionAwareJDBCInterceptor transactionInterceptor) {
        this.delegate = delegate;
        this.sql = sql;
        this.compatibilityHelper = compatibilityHelper;
        this.transactionInterceptor = transactionInterceptor;
    }
    
    // PostgreSQL 호환성을 위한 핵심 메서드 - setObject 오버라이드
    @Override
    public void setObject(int parameterIndex, Object x) throws SQLException {
        try {
            // PostgreSQL 호환성 헬퍼를 통한 안전한 파라미터 바인딩
            compatibilityHelper.setParameterSafely(delegate, parameterIndex, x);
            
            logger.fine(String.format("[KubeDB] PostgreSQL 안전 파라미터 바인딩: index=%d, value=%s, type=%s", 
                       parameterIndex, 
                       x == null ? "NULL" : x.toString(), 
                       x == null ? "NULL" : x.getClass().getSimpleName()));
        } catch (SQLException e) {
            logger.warning(String.format("[KubeDB] PostgreSQL 파라미터 바인딩 실패: index=%d, error=%s", 
                          parameterIndex, e.getMessage()));
            throw e;
        }
    }
    
    @Override
    public void setObject(int parameterIndex, Object x, int targetSqlType) throws SQLException {
        // 타입이 명시된 경우는 안전하므로 그대로 위임
        delegate.setObject(parameterIndex, x, targetSqlType);
    }
    
    @Override
    public void setObject(int parameterIndex, Object x, int targetSqlType, int scaleOrLength) throws SQLException {
        // 타입이 명시된 경우는 안전하므로 그대로 위임
        delegate.setObject(parameterIndex, x, targetSqlType, scaleOrLength);
    }
    
    // 쿼리 실행 모니터링
    @Override
    public ResultSet executeQuery() throws SQLException {
        startTime = System.currentTimeMillis();
        try {
            logger.fine("[KubeDB] PostgreSQL Query 실행 시작: " + 
                       (sql.length() > 100 ? sql.substring(0, 100) + "..." : sql));
            
            ResultSet result = delegate.executeQuery();
            
            long duration = System.currentTimeMillis() - startTime;
            logger.info(String.format("[KubeDB] PostgreSQL Query 완료: duration=%dms, sql=%s", 
                       duration, sql.length() > 50 ? sql.substring(0, 50) + "..." : sql));
            
            // Transaction 모니터링: 성공한 쿼리 실행 기록
            if (transactionInterceptor != null) {
                try {
                    transactionInterceptor.onQueryExecution(delegate.getConnection(), sql, duration, true);
                } catch (Exception e) {
                    logger.fine("[KubeDB] Transaction 모니터링 중 오류 (executeQuery): " + e.getMessage());
                }
            }
            
            return result;
        } catch (SQLException e) {
            long duration = System.currentTimeMillis() - startTime;
            logger.warning(String.format("[KubeDB] PostgreSQL Query 실패: duration=%dms, error=%s, sql=%s", 
                          duration, e.getMessage(), 
                          sql.length() > 50 ? sql.substring(0, 50) + "..." : sql));
            
            // Transaction 모니터링: 실패한 쿼리 오류 기록
            if (transactionInterceptor != null) {
                try {
                    transactionInterceptor.onQueryError(delegate.getConnection(), sql, e);
                } catch (Exception te) {
                    logger.fine("[KubeDB] Transaction 오류 모니터링 중 오류: " + te.getMessage());
                }
            }
            
            throw e;
        }
    }
    
    @Override
    public int executeUpdate() throws SQLException {
        startTime = System.currentTimeMillis();
        try {
            logger.fine("[KubeDB] PostgreSQL Update 실행 시작: " + 
                       (sql.length() > 100 ? sql.substring(0, 100) + "..." : sql));
            
            int result = delegate.executeUpdate();
            
            long duration = System.currentTimeMillis() - startTime;
            logger.info(String.format("[KubeDB] PostgreSQL Update 완료: duration=%dms, affected=%d, sql=%s", 
                       duration, result, sql.length() > 50 ? sql.substring(0, 50) + "..." : sql));
            
            // Transaction 모니터링: 성공한 업데이트 실행 기록
            if (transactionInterceptor != null) {
                try {
                    transactionInterceptor.onQueryExecution(delegate.getConnection(), sql, duration, true);
                } catch (Exception e) {
                    logger.fine("[KubeDB] Transaction 모니터링 중 오류 (executeUpdate): " + e.getMessage());
                }
            }
            
            return result;
        } catch (SQLException e) {
            long duration = System.currentTimeMillis() - startTime;
            logger.warning(String.format("[KubeDB] PostgreSQL Update 실패: duration=%dms, error=%s, sql=%s", 
                          duration, e.getMessage(), 
                          sql.length() > 50 ? sql.substring(0, 50) + "..." : sql));
            
            // Transaction 모니터링: 실패한 업데이트 오류 기록
            if (transactionInterceptor != null) {
                try {
                    transactionInterceptor.onQueryError(delegate.getConnection(), sql, e);
                } catch (Exception te) {
                    logger.fine("[KubeDB] Transaction 오류 모니터링 중 오류: " + te.getMessage());
                }
            }
            
            throw e;
        }
    }
    
    @Override
    public boolean execute() throws SQLException {
        startTime = System.currentTimeMillis();
        try {
            logger.fine("[KubeDB] PostgreSQL Execute 실행 시작: " + 
                       (sql.length() > 100 ? sql.substring(0, 100) + "..." : sql));
            
            boolean result = delegate.execute();
            
            long duration = System.currentTimeMillis() - startTime;
            logger.info(String.format("[KubeDB] PostgreSQL Execute 완료: duration=%dms, hasResultSet=%s, sql=%s", 
                       duration, result, sql.length() > 50 ? sql.substring(0, 50) + "..." : sql));
            
            // Transaction 모니터링: 성공한 실행 기록
            if (transactionInterceptor != null) {
                try {
                    transactionInterceptor.onQueryExecution(delegate.getConnection(), sql, duration, true);
                } catch (Exception e) {
                    logger.fine("[KubeDB] Transaction 모니터링 중 오류 (execute): " + e.getMessage());
                }
            }
            
            return result;
        } catch (SQLException e) {
            long duration = System.currentTimeMillis() - startTime;
            logger.warning(String.format("[KubeDB] PostgreSQL Execute 실패: duration=%dms, error=%s, sql=%s", 
                          duration, e.getMessage(), 
                          sql.length() > 50 ? sql.substring(0, 50) + "..." : sql));
            
            // Transaction 모니터링: 실패한 실행 오류 기록
            if (transactionInterceptor != null) {
                try {
                    transactionInterceptor.onQueryError(delegate.getConnection(), sql, e);
                } catch (Exception te) {
                    logger.fine("[KubeDB] Transaction 오류 모니터링 중 오류: " + te.getMessage());
                }
            }
            
            throw e;
        }
    }
    
    // 나머지 setXXX 메서드들은 delegate에 안전하게 위임
    @Override
    public void setNull(int parameterIndex, int sqlType) throws SQLException {
        delegate.setNull(parameterIndex, sqlType);
    }
    
    @Override
    public void setBoolean(int parameterIndex, boolean x) throws SQLException {
        delegate.setBoolean(parameterIndex, x);
    }
    
    @Override
    public void setByte(int parameterIndex, byte x) throws SQLException {
        delegate.setByte(parameterIndex, x);
    }
    
    @Override
    public void setShort(int parameterIndex, short x) throws SQLException {
        delegate.setShort(parameterIndex, x);
    }
    
    @Override
    public void setInt(int parameterIndex, int x) throws SQLException {
        delegate.setInt(parameterIndex, x);
    }
    
    @Override
    public void setLong(int parameterIndex, long x) throws SQLException {
        delegate.setLong(parameterIndex, x);
    }
    
    @Override
    public void setFloat(int parameterIndex, float x) throws SQLException {
        delegate.setFloat(parameterIndex, x);
    }
    
    @Override
    public void setDouble(int parameterIndex, double x) throws SQLException {
        delegate.setDouble(parameterIndex, x);
    }
    
    @Override
    public void setBigDecimal(int parameterIndex, BigDecimal x) throws SQLException {
        delegate.setBigDecimal(parameterIndex, x);
    }
    
    @Override
    public void setString(int parameterIndex, String x) throws SQLException {
        delegate.setString(parameterIndex, x);
    }
    
    @Override
    public void setBytes(int parameterIndex, byte[] x) throws SQLException {
        delegate.setBytes(parameterIndex, x);
    }
    
    @Override
    public void setDate(int parameterIndex, Date x) throws SQLException {
        delegate.setDate(parameterIndex, x);
    }
    
    @Override
    public void setTime(int parameterIndex, Time x) throws SQLException {
        delegate.setTime(parameterIndex, x);
    }
    
    @Override
    public void setTimestamp(int parameterIndex, Timestamp x) throws SQLException {
        delegate.setTimestamp(parameterIndex, x);
    }
    
    @Override
    public void setAsciiStream(int parameterIndex, InputStream x, int length) throws SQLException {
        delegate.setAsciiStream(parameterIndex, x, length);
    }
    
    @Override
    @Deprecated
    public void setUnicodeStream(int parameterIndex, InputStream x, int length) throws SQLException {
        delegate.setUnicodeStream(parameterIndex, x, length);
    }
    
    @Override
    public void setBinaryStream(int parameterIndex, InputStream x, int length) throws SQLException {
        delegate.setBinaryStream(parameterIndex, x, length);
    }
    
    @Override
    public void clearParameters() throws SQLException {
        delegate.clearParameters();
    }
    
    @Override
    public void setRef(int parameterIndex, Ref x) throws SQLException {
        delegate.setRef(parameterIndex, x);
    }
    
    @Override
    public void setBlob(int parameterIndex, Blob x) throws SQLException {
        delegate.setBlob(parameterIndex, x);
    }
    
    @Override
    public void setClob(int parameterIndex, Clob x) throws SQLException {
        delegate.setClob(parameterIndex, x);
    }
    
    @Override
    public void setArray(int parameterIndex, Array x) throws SQLException {
        delegate.setArray(parameterIndex, x);
    }
    
    @Override
    public ResultSetMetaData getMetaData() throws SQLException {
        return delegate.getMetaData();
    }
    
    @Override
    public void setDate(int parameterIndex, Date x, Calendar cal) throws SQLException {
        delegate.setDate(parameterIndex, x, cal);
    }
    
    @Override
    public void setTime(int parameterIndex, Time x, Calendar cal) throws SQLException {
        delegate.setTime(parameterIndex, x, cal);
    }
    
    @Override
    public void setTimestamp(int parameterIndex, Timestamp x, Calendar cal) throws SQLException {
        delegate.setTimestamp(parameterIndex, x, cal);
    }
    
    @Override
    public void setNull(int parameterIndex, int sqlType, String typeName) throws SQLException {
        delegate.setNull(parameterIndex, sqlType, typeName);
    }
    
    @Override
    public void setURL(int parameterIndex, URL x) throws SQLException {
        delegate.setURL(parameterIndex, x);
    }
    
    @Override
    public ParameterMetaData getParameterMetaData() throws SQLException {
        return delegate.getParameterMetaData();
    }
    
    @Override
    public void setRowId(int parameterIndex, RowId x) throws SQLException {
        delegate.setRowId(parameterIndex, x);
    }
    
    @Override
    public void setNString(int parameterIndex, String value) throws SQLException {
        delegate.setNString(parameterIndex, value);
    }
    
    @Override
    public void setNCharacterStream(int parameterIndex, Reader value, long length) throws SQLException {
        delegate.setNCharacterStream(parameterIndex, value, length);
    }
    
    @Override
    public void setNClob(int parameterIndex, NClob value) throws SQLException {
        delegate.setNClob(parameterIndex, value);
    }
    
    @Override
    public void setClob(int parameterIndex, Reader reader, long length) throws SQLException {
        delegate.setClob(parameterIndex, reader, length);
    }
    
    @Override
    public void setBlob(int parameterIndex, InputStream inputStream, long length) throws SQLException {
        delegate.setBlob(parameterIndex, inputStream, length);
    }
    
    @Override
    public void setNClob(int parameterIndex, Reader reader, long length) throws SQLException {
        delegate.setNClob(parameterIndex, reader, length);
    }
    
    @Override
    public void setSQLXML(int parameterIndex, SQLXML xmlObject) throws SQLException {
        delegate.setSQLXML(parameterIndex, xmlObject);
    }
    
    @Override
    public void setAsciiStream(int parameterIndex, InputStream x, long length) throws SQLException {
        delegate.setAsciiStream(parameterIndex, x, length);
    }
    
    @Override
    public void setBinaryStream(int parameterIndex, InputStream x, long length) throws SQLException {
        delegate.setBinaryStream(parameterIndex, x, length);
    }
    
    @Override
    public void setCharacterStream(int parameterIndex, Reader reader, long length) throws SQLException {
        delegate.setCharacterStream(parameterIndex, reader, length);
    }
    
    @Override
    public void setAsciiStream(int parameterIndex, InputStream x) throws SQLException {
        delegate.setAsciiStream(parameterIndex, x);
    }
    
    @Override
    public void setBinaryStream(int parameterIndex, InputStream x) throws SQLException {
        delegate.setBinaryStream(parameterIndex, x);
    }
    
    @Override
    public void setCharacterStream(int parameterIndex, Reader reader) throws SQLException {
        delegate.setCharacterStream(parameterIndex, reader);
    }
    
    @Override
    public void setNCharacterStream(int parameterIndex, Reader value) throws SQLException {
        delegate.setNCharacterStream(parameterIndex, value);
    }
    
    @Override
    public void setClob(int parameterIndex, Reader reader) throws SQLException {
        delegate.setClob(parameterIndex, reader);
    }
    
    @Override
    public void setBlob(int parameterIndex, InputStream inputStream) throws SQLException {
        delegate.setBlob(parameterIndex, inputStream);
    }
    
    @Override
    public void setNClob(int parameterIndex, Reader reader) throws SQLException {
        delegate.setNClob(parameterIndex, reader);
    }
    
    // Statement 인터페이스 메서드들도 delegate에 위임
    @Override
    public void addBatch() throws SQLException {
        delegate.addBatch();
    }
    
    @Override
    public void setCharacterStream(int parameterIndex, Reader reader, int length) throws SQLException {
        delegate.setCharacterStream(parameterIndex, reader, length);
    }
    
    // Statement 메서드들 위임
    @Override
    public ResultSet executeQuery(String sql) throws SQLException {
        return delegate.executeQuery(sql);
    }
    
    @Override
    public int executeUpdate(String sql) throws SQLException {
        return delegate.executeUpdate(sql);
    }
    
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
    public boolean execute(String sql) throws SQLException {
        return delegate.execute(sql);
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
        return delegate.executeBatch();
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
        return delegate.executeUpdate(sql, autoGeneratedKeys);
    }
    
    @Override
    public int executeUpdate(String sql, int[] columnIndexes) throws SQLException {
        return delegate.executeUpdate(sql, columnIndexes);
    }
    
    @Override
    public int executeUpdate(String sql, String[] columnNames) throws SQLException {
        return delegate.executeUpdate(sql, columnNames);
    }
    
    @Override
    public boolean execute(String sql, int autoGeneratedKeys) throws SQLException {
        return delegate.execute(sql, autoGeneratedKeys);
    }
    
    @Override
    public boolean execute(String sql, int[] columnIndexes) throws SQLException {
        return delegate.execute(sql, columnIndexes);
    }
    
    @Override
    public boolean execute(String sql, String[] columnNames) throws SQLException {
        return delegate.execute(sql, columnNames);
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
        if (iface.isAssignableFrom(getClass())) {
            return iface.cast(this);
        }
        return delegate.unwrap(iface);
    }
    
    @Override
    public boolean isWrapperFor(Class<?> iface) throws SQLException {
        if (iface.isAssignableFrom(getClass())) {
            return true;
        }
        return delegate.isWrapperFor(iface);
    }
}