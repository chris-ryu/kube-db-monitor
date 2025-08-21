package io.kubedb.monitor.agent;

import java.sql.Connection;
import java.sql.Driver;
import java.sql.SQLException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

/**
 * 데이터베이스별 프록시 팩토리
 * 
 * 각 데이터베이스 드라이버에 맞는 프록시 객체를 생성하고 관리합니다.
 * PostgreSQL, MySQL, Oracle 등 다양한 데이터베이스를 지원할 수 있도록 확장 가능한 구조입니다.
 */
public class DatabaseProxyFactory {
    private static final Logger logger = Logger.getLogger(DatabaseProxyFactory.class.getName());
    
    // 캐시된 드라이버 프록시들
    private static final ConcurrentHashMap<String, Driver> driverProxyCache = new ConcurrentHashMap<>();
    
    public enum DatabaseType {
        POSTGRESQL("org.postgresql.Driver", "postgresql"),
        MYSQL("com.mysql.cj.jdbc.Driver", "mysql"),
        MARIADB("org.mariadb.jdbc.Driver", "mariadb"),
        ORACLE("oracle.jdbc.driver.OracleDriver", "oracle"),
        SQL_SERVER("com.microsoft.sqlserver.jdbc.SQLServerDriver", "sqlserver"),
        H2("org.h2.Driver", "h2"),
        UNKNOWN("", "");
        
        private final String driverClassName;
        private final String urlPrefix;
        
        DatabaseType(String driverClassName, String urlPrefix) {
            this.driverClassName = driverClassName;
            this.urlPrefix = urlPrefix;
        }
        
        public String getDriverClassName() {
            return driverClassName;
        }
        
        public String getUrlPrefix() {
            return urlPrefix;
        }
        
        public static DatabaseType fromUrl(String url) {
            if (url == null) return UNKNOWN;
            
            String lowerUrl = url.toLowerCase();
            for (DatabaseType type : values()) {
                if (type != UNKNOWN && lowerUrl.contains("jdbc:" + type.urlPrefix)) {
                    return type;
                }
            }
            return UNKNOWN;
        }
        
        public static DatabaseType fromDriverClass(String className) {
            if (className == null) return UNKNOWN;
            
            for (DatabaseType type : values()) {
                if (type.driverClassName.equals(className)) {
                    return type;
                }
            }
            return UNKNOWN;
        }
    }
    
    /**
     * Connection에 적합한 프록시를 생성합니다.
     */
    public static Connection createConnectionProxy(Connection connection, String url, AgentConfig config) {
        if (connection == null) {
            return null;
        }
        
        DatabaseType dbType = DatabaseType.fromUrl(url);
        
        switch (dbType) {
            case POSTGRESQL:
                logger.info("[KubeDB] PostgreSQL Connection 프록시 생성");
                return new PostgreSQLConnectionProxy(connection, config);
                
            case MYSQL:
            case MARIADB:
                logger.info("[KubeDB] MySQL/MariaDB Connection - 향후 지원 예정, 기본 모니터링 적용");
                return connection; // 향후 MySQLConnectionProxy 구현
                
            case ORACLE:
                logger.info("[KubeDB] Oracle Connection - 향후 지원 예정, 기본 모니터링 적용");
                return connection; // 향후 OracleConnectionProxy 구현
                
            case SQL_SERVER:
                logger.info("[KubeDB] SQL Server Connection - 향후 지원 예정, 기본 모니터링 적용");
                return connection; // 향후 SQLServerConnectionProxy 구현
                
            case H2:
                logger.info("[KubeDB] H2 Database Connection - 기본 모니터링 적용");
                return connection; // H2는 테스트용이므로 프록시 불필요
                
            default:
                logger.warning("[KubeDB] 알 수 없는 데이터베이스 타입: " + url + " - 기본 모니터링 적용");
                return connection;
        }
    }
    
    /**
     * Driver에 적합한 프록시를 생성합니다.
     */
    public static Driver createDriverProxy(Driver driver, AgentConfig config) {
        if (driver == null) {
            return null;
        }
        
        String driverClassName = driver.getClass().getName();
        DatabaseType dbType = DatabaseType.fromDriverClass(driverClassName);
        
        // 캐시 확인
        String cacheKey = driverClassName + "_" + System.identityHashCode(driver);
        Driver cachedProxy = driverProxyCache.get(cacheKey);
        if (cachedProxy != null) {
            logger.fine("[KubeDB] 캐시된 Driver 프록시 사용: " + driverClassName);
            return cachedProxy;
        }
        
        Driver proxyDriver;
        
        switch (dbType) {
            case POSTGRESQL:
                logger.info("[KubeDB] PostgreSQL Driver 프록시 생성: " + driverClassName);
                proxyDriver = new PostgreSQLDriverProxy(driver, config);
                break;
                
            case MYSQL:
            case MARIADB:
                logger.info("[KubeDB] MySQL/MariaDB Driver - 향후 지원 예정: " + driverClassName);
                proxyDriver = driver; // 향후 MySQLDriverProxy 구현
                break;
                
            case ORACLE:
                logger.info("[KubeDB] Oracle Driver - 향후 지원 예정: " + driverClassName);
                proxyDriver = driver; // 향후 OracleDriverProxy 구현
                break;
                
            case SQL_SERVER:
                logger.info("[KubeDB] SQL Server Driver - 향후 지원 예정: " + driverClassName);
                proxyDriver = driver; // 향후 SQLServerDriverProxy 구현
                break;
                
            case H2:
                logger.fine("[KubeDB] H2 Driver - 기본 사용: " + driverClassName);
                proxyDriver = driver; // H2는 테스트용이므로 프록시 불필요
                break;
                
            default:
                logger.warning("[KubeDB] 알 수 없는 Driver 타입: " + driverClassName);
                proxyDriver = driver;
                break;
        }
        
        // 캐시에 저장
        driverProxyCache.put(cacheKey, proxyDriver);
        return proxyDriver;
    }
    
    /**
     * 특정 데이터베이스 타입이 프록시를 지원하는지 확인합니다.
     */
    public static boolean isProxySupported(DatabaseType dbType) {
        switch (dbType) {
            case POSTGRESQL:
                return true;
            case MYSQL:
            case MARIADB:
            case ORACLE:
            case SQL_SERVER:
                return false; // 향후 지원 예정
            default:
                return false;
        }
    }
    
    /**
     * 특정 URL에 대해 프록시가 지원되는지 확인합니다.
     */
    public static boolean isProxySupported(String url) {
        DatabaseType dbType = DatabaseType.fromUrl(url);
        return isProxySupported(dbType);
    }
    
    /**
     * 데이터베이스별 호환성 이슈 정보를 반환합니다.
     */
    public static String getCompatibilityInfo(DatabaseType dbType) {
        switch (dbType) {
            case POSTGRESQL:
                return "Known issues: setObject(null) -> setNull(type) 변환, autoCommit 트랜잭션 관리";
            case MYSQL:
                return "Known issues: Timezone handling, SSL certificate validation (지원 예정)";
            case ORACLE:
                return "Known issues: PL/SQL CURSOR, CDB/PDB connection management (지원 예정)";
            case SQL_SERVER:
                return "Known issues: T-SQL syntax, Always On availability groups (지원 예정)";
            case H2:
                return "Test database - No known compatibility issues";
            default:
                return "Unknown database - Compatibility not verified";
        }
    }
    
    /**
     * 프록시 캐시를 정리합니다. (테스트 용도)
     */
    public static void clearProxyCache() {
        driverProxyCache.clear();
        logger.info("[KubeDB] Driver 프록시 캐시 정리됨");
    }
    
    /**
     * 현재 캐시된 프록시 개수를 반환합니다.
     */
    public static int getCachedProxyCount() {
        return driverProxyCache.size();
    }
}