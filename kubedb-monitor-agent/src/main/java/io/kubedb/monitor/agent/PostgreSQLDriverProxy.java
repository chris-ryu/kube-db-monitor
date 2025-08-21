package io.kubedb.monitor.agent;

import java.sql.*;
import java.util.Properties;
import java.util.logging.Logger;

/**
 * PostgreSQL Driver 프록시
 * 
 * DriverManager.getConnection() 호출을 가로채서 PostgreSQL Connection 프록시를 반환합니다.
 * 이를 통해 ASM 바이트코드 변환 없이도 안전한 PostgreSQL 모니터링을 제공합니다.
 */
public class PostgreSQLDriverProxy implements Driver {
    private static final Logger logger = Logger.getLogger(PostgreSQLDriverProxy.class.getName());
    
    private final Driver delegate;
    private final AgentConfig config;
    
    public PostgreSQLDriverProxy(Driver delegate, AgentConfig config) {
        this.delegate = delegate;
        this.config = config;
        
        logger.info("[KubeDB] PostgreSQL Driver 프록시 활성화 - " + 
                   delegate.getClass().getName());
    }
    
    @Override
    public Connection connect(String url, Properties info) throws SQLException {
        logger.info("[KubeDB] PostgreSQL Connection 요청: " + url);
        
        try {
            Connection connection = delegate.connect(url, info);
            
            if (connection != null) {
                logger.info("[KubeDB] PostgreSQL Connection 성공, 프록시 적용 중...");
                return new PostgreSQLConnectionProxy(connection, config);
            }
            
            return null;
        } catch (SQLException e) {
            logger.warning("[KubeDB] PostgreSQL Connection 실패: " + e.getMessage());
            throw e;
        }
    }
    
    @Override
    public boolean acceptsURL(String url) throws SQLException {
        return delegate.acceptsURL(url);
    }
    
    @Override
    public DriverPropertyInfo[] getPropertyInfo(String url, Properties info) throws SQLException {
        return delegate.getPropertyInfo(url, info);
    }
    
    @Override
    public int getMajorVersion() {
        return delegate.getMajorVersion();
    }
    
    @Override
    public int getMinorVersion() {
        return delegate.getMinorVersion();
    }
    
    @Override
    public boolean jdbcCompliant() {
        return delegate.jdbcCompliant();
    }
    
    @Override
    public Logger getParentLogger() throws SQLFeatureNotSupportedException {
        return delegate.getParentLogger();
    }
}