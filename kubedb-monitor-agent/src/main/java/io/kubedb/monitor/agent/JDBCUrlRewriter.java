package io.kubedb.monitor.agent;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.sql.*;
import java.util.Enumeration;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Fallback 메커니즘: JDBC URL Rewriting 및 DriverManager 후킹
 * ByteBuddy와 Runtime Discovery가 실패할 경우 사용되는 최후의 수단
 */
public class JDBCUrlRewriter {
    private static final Logger logger = LoggerFactory.getLogger(JDBCUrlRewriter.class);
    
    private static volatile boolean initialized = false;
    private static final ConcurrentHashMap<Driver, Driver> originalDrivers = new ConcurrentHashMap<>();
    
    /**
     * DriverManager 후킹 초기화
     */
    public static synchronized void initializeDriverManagerHook(AgentConfig config) {
        if (initialized) {
            return;
        }
        
        try {
            System.out.println("🔧 DriverManager 후킹 시작...");
            
            // 모든 등록된 드라이버를 우리의 프록시 드라이버로 교체
            Enumeration<Driver> drivers = DriverManager.getDrivers();
            
            while (drivers.hasMoreElements()) {
                Driver originalDriver = drivers.nextElement();
                
                // 이미 우리의 프록시 드라이버인지 확인
                if (originalDriver instanceof MonitoringProxyDriver) {
                    continue;
                }
                
                System.out.println("🔍 드라이버 발견: " + originalDriver.getClass().getName());
                
                // 원본 드라이버 제거
                DriverManager.deregisterDriver(originalDriver);
                
                // 우리의 프록시 드라이버로 등록
                MonitoringProxyDriver proxyDriver = new MonitoringProxyDriver(originalDriver, config);
                DriverManager.registerDriver(proxyDriver);
                
                originalDrivers.put(proxyDriver, originalDriver);
                
                System.out.println("✅ 프록시 드라이버로 교체: " + originalDriver.getClass().getName());
            }
            
            // 범용 프록시 드라이버도 최우선으로 등록
            UniversalProxyDriver universalDriver = new UniversalProxyDriver(config);
            DriverManager.registerDriver(universalDriver);
            
            initialized = true;
            System.out.println("✅ DriverManager 후킹 완료");
            
        } catch (Exception e) {
            logger.error("DriverManager hook initialization failed: {}", e.getMessage(), e);
        }
    }
    
    /**
     * 모니터링 프록시 드라이버
     */
    public static class MonitoringProxyDriver implements Driver {
        private final Driver originalDriver;
        private final AgentConfig config;
        private final MetricsCollector metricsCollector;
        
        public MonitoringProxyDriver(Driver originalDriver, AgentConfig config) {
            this.originalDriver = originalDriver;
            this.config = config;
            this.metricsCollector = new MetricsCollector(config);
        }
        
        @Override
        public Connection connect(String url, Properties info) throws SQLException {
            System.out.println("🔍 JDBC 연결 요청: " + url);
            
            Connection originalConnection = originalDriver.connect(url, info);
            if (originalConnection == null) {
                return null;
            }
            
            // Connection을 모니터링 프록시로 감싸기
            return createMonitoringConnection(originalConnection);
        }
        
        @Override
        public boolean acceptsURL(String url) throws SQLException {
            return originalDriver.acceptsURL(url);
        }
        
        @Override
        public DriverPropertyInfo[] getPropertyInfo(String url, Properties info) throws SQLException {
            return originalDriver.getPropertyInfo(url, info);
        }
        
        @Override
        public int getMajorVersion() {
            return originalDriver.getMajorVersion();
        }
        
        @Override
        public int getMinorVersion() {
            return originalDriver.getMinorVersion();
        }
        
        @Override
        public boolean jdbcCompliant() {
            return originalDriver.jdbcCompliant();
        }
        
        @Override
        public java.util.logging.Logger getParentLogger() throws SQLFeatureNotSupportedException {
            return originalDriver.getParentLogger();
        }
        
        /**
         * Connection을 모니터링 프록시로 감싸기
         */
        private Connection createMonitoringConnection(Connection originalConnection) {
            return (Connection) Proxy.newProxyInstance(
                originalConnection.getClass().getClassLoader(),
                new Class<?>[]{Connection.class},
                new ConnectionInvocationHandler(originalConnection, metricsCollector)
            );
        }
    }
    
    /**
     * 범용 프록시 드라이버 (모든 JDBC URL 가로채기)
     */
    public static class UniversalProxyDriver implements Driver {
        private final AgentConfig config;
        private final MetricsCollector metricsCollector;
        
        public UniversalProxyDriver(AgentConfig config) {
            this.config = config;
            this.metricsCollector = new MetricsCollector(config);
        }
        
        @Override
        public Connection connect(String url, Properties info) throws SQLException {
            // 모든 JDBC URL 처리
            if (!acceptsURL(url)) {
                return null;
            }
            
            System.out.println("🔍 Universal 프록시 드라이버가 연결 처리: " + url);
            
            // 실제 드라이버 찾기
            Driver actualDriver = findActualDriver(url, info);
            if (actualDriver == null) {
                throw new SQLException("No suitable driver found for: " + url);
            }
            
            Connection originalConnection = actualDriver.connect(url, info);
            if (originalConnection == null) {
                return null;
            }
            
            // 모니터링 프록시로 감싸기
            return (Connection) Proxy.newProxyInstance(
                originalConnection.getClass().getClassLoader(),
                new Class<?>[]{Connection.class},
                new ConnectionInvocationHandler(originalConnection, metricsCollector)
            );
        }
        
        @Override
        public boolean acceptsURL(String url) throws SQLException {
            // 모든 JDBC URL 허용 (단, 우리의 프록시 URL은 제외)
            return url != null && 
                   url.startsWith("jdbc:") && 
                   !url.contains("kubedb-proxy");
        }
        
        @Override
        public DriverPropertyInfo[] getPropertyInfo(String url, Properties info) throws SQLException {
            Driver actualDriver = findActualDriver(url, info);
            return actualDriver != null ? actualDriver.getPropertyInfo(url, info) : new DriverPropertyInfo[0];
        }
        
        @Override
        public int getMajorVersion() {
            return 1;
        }
        
        @Override
        public int getMinorVersion() {
            return 0;
        }
        
        @Override
        public boolean jdbcCompliant() {
            return true;
        }
        
        @Override
        public java.util.logging.Logger getParentLogger() throws SQLFeatureNotSupportedException {
            throw new SQLFeatureNotSupportedException();
        }
        
        /**
         * URL에 맞는 실제 드라이버 찾기
         */
        private Driver findActualDriver(String url, Properties info) throws SQLException {
            Enumeration<Driver> drivers = DriverManager.getDrivers();
            
            while (drivers.hasMoreElements()) {
                Driver driver = drivers.nextElement();
                
                // 자기 자신은 제외
                if (driver == this || driver instanceof UniversalProxyDriver) {
                    continue;
                }
                
                // 프록시 드라이버인 경우 원본 드라이버 사용
                if (driver instanceof MonitoringProxyDriver) {
                    Driver original = originalDrivers.get(driver);
                    if (original != null && original.acceptsURL(url)) {
                        return original;
                    }
                } else if (driver.acceptsURL(url)) {
                    return driver;
                }
            }
            
            return null;
        }
    }
    
    /**
     * Connection InvocationHandler
     */
    public static class ConnectionInvocationHandler implements InvocationHandler {
        private final Connection originalConnection;
        private final MetricsCollector metricsCollector;
        
        public ConnectionInvocationHandler(Connection originalConnection, MetricsCollector metricsCollector) {
            this.originalConnection = originalConnection;
            this.metricsCollector = metricsCollector;
        }
        
        @Override
        public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
            long startTime = System.nanoTime();
            String methodName = method.getName();
            
            try {
                Object result = method.invoke(originalConnection, args);
                long executionTime = System.nanoTime() - startTime;
                
                // 메서드별 메트릭 수집
                switch (methodName) {
                    case "prepareStatement":
                        if (args.length > 0 && args[0] instanceof String) {
                            String sql = (String) args[0];
                            System.out.println("🔍 SQL 준비: " + sql);
                            metricsCollector.recordQuery(sql, executionTime, 
                                                       originalConnection.toString(), 
                                                       Thread.currentThread().getName());
                        }
                        break;
                        
                    case "commit":
                        System.out.println("🔍 트랜잭션 커밋");
                        metricsCollector.recordCommit(executionTime, 
                                                    originalConnection.toString(), 
                                                    "tx-" + System.currentTimeMillis());
                        break;
                        
                    case "rollback":
                        System.out.println("🔍 트랜잭션 롤백");
                        metricsCollector.recordRollback(executionTime, 
                                                       originalConnection.toString(), 
                                                       "tx-" + System.currentTimeMillis());
                        break;
                }
                
                return result;
                
            } catch (Exception e) {
                long executionTime = System.nanoTime() - startTime;
                logger.error("Connection method error: {}.{}", methodName, e.getMessage());
                throw e;
            }
        }
    }
    
    /**
     * 정리 작업
     */
    public static synchronized void cleanup() {
        if (!initialized) {
            return;
        }
        
        try {
            System.out.println("🔄 DriverManager 후킹 정리 중...");
            
            // 프록시 드라이버들을 원본 드라이버로 복원
            for (java.util.Map.Entry<Driver, Driver> entry : originalDrivers.entrySet()) {
                Driver proxyDriver = entry.getKey();
                Driver originalDriver = entry.getValue();
                
                DriverManager.deregisterDriver(proxyDriver);
                DriverManager.registerDriver(originalDriver);
            }
            
            originalDrivers.clear();
            initialized = false;
            
            System.out.println("✅ DriverManager 후킹 정리 완료");
            
        } catch (Exception e) {
            logger.error("DriverManager hook cleanup failed: {}", e.getMessage(), e);
        }
    }
}