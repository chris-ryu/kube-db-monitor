package io.kubedb.monitor.agent;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sql.DataSource;
import java.lang.instrument.Instrumentation;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 런타임에 JVM을 스캔하여 DataSource 인스턴스를 발견하고 프록시로 감싸는 클래스
 * Spring Boot, Hibernate, HikariCP 등 모든 환경을 지원합니다.
 */
public class RuntimeDataSourceDiscovery {
    private static final Logger logger = LoggerFactory.getLogger(RuntimeDataSourceDiscovery.class);
    
    private final Instrumentation instrumentation;
    private final AgentConfig config;
    private final MetricsCollector metricsCollector;
    
    // 이미 처리된 DataSource를 추적하여 중복 처리 방지
    private final Set<Object> processedDataSources = ConcurrentHashMap.newKeySet();
    
    // 지원하는 DataSource 클래스 이름들
    private static final Set<String> DATASOURCE_CLASSES = Set.of(
        "com.zaxxer.hikari.HikariDataSource",
        "org.apache.tomcat.jdbc.pool.DataSource", 
        "org.apache.commons.dbcp2.BasicDataSource",
        "oracle.jdbc.pool.OracleDataSource",
        "com.microsoft.sqlserver.jdbc.SQLServerDataSource",
        "org.postgresql.ds.PGSimpleDataSource",
        "org.postgresql.ds.PGPoolingDataSource",
        "org.postgresql.ds.PGConnectionPoolDataSource",
        "com.mysql.cj.jdbc.MysqlDataSource",
        "org.mariadb.jdbc.MariaDbDataSource",
        "com.mchange.v2.c3p0.ComboPooledDataSource"
    );
    
    public RuntimeDataSourceDiscovery(Instrumentation instrumentation, AgentConfig config) {
        this.instrumentation = instrumentation;
        this.config = config;
        this.metricsCollector = new MetricsCollector(config);
    }
    
    /**
     * JVM을 스캔하여 DataSource를 찾고 프록시로 감싸기
     */
    public void discoverAndWrapDataSources() {
        try {
            Class<?>[] loadedClasses = instrumentation.getAllLoadedClasses();
            
            for (Class<?> clazz : loadedClasses) {
                if (isDataSourceClass(clazz)) {
                    System.out.println("🔍 DataSource 클래스 발견: " + clazz.getName());
                    scanForDataSourceInstances(clazz);
                }
            }
        } catch (Exception e) {
            logger.debug("DataSource discovery error: {}", e.getMessage());
        }
    }
    
    /**
     * 클래스가 DataSource인지 확인
     */
    private boolean isDataSourceClass(Class<?> clazz) {
        if (clazz == null) return false;
        
        // 정확한 클래스 이름 매치
        if (DATASOURCE_CLASSES.contains(clazz.getName())) {
            return true;
        }
        
        // DataSource 인터페이스 구현체인지 확인
        try {
            return DataSource.class.isAssignableFrom(clazz) && !clazz.isInterface();
        } catch (Exception e) {
            return false;
        }
    }
    
    /**
     * 특정 클래스의 인스턴스를 스캔하여 DataSource 찾기
     */
    private void scanForDataSourceInstances(Class<?> clazz) {
        try {
            // Static 필드에서 DataSource 인스턴스 찾기
            scanStaticFields(clazz);
            
            // Spring Boot 환경에서는 ApplicationContext에서 Bean 찾기
            scanSpringBeans();
            
            // 기타 싱글톤 패턴 인스턴스 찾기
            scanSingletonInstances(clazz);
            
        } catch (Exception e) {
            logger.debug("Error scanning DataSource instances for {}: {}", clazz.getName(), e.getMessage());
        }
    }
    
    /**
     * Static 필드에서 DataSource 인스턴스 스캔
     */
    private void scanStaticFields(Class<?> clazz) {
        try {
            Field[] fields = clazz.getDeclaredFields();
            for (Field field : fields) {
                if (java.lang.reflect.Modifier.isStatic(field.getModifiers())) {
                    field.setAccessible(true);
                    Object instance = field.get(null);
                    
                    if (instance instanceof DataSource && !processedDataSources.contains(instance)) {
                        System.out.println("✅ Static DataSource 발견: " + field.getName());
                        wrapDataSource((DataSource) instance, field, null);
                    }
                }
            }
        } catch (Exception e) {
            logger.debug("Error scanning static fields: {}", e.getMessage());
        }
    }
    
    /**
     * Spring Bean에서 DataSource 찾기
     */
    private void scanSpringBeans() {
        try {
            // Spring ApplicationContext가 로드된 경우 Bean 스캔
            Class<?>[] classes = instrumentation.getAllLoadedClasses();
            for (Class<?> clazz : classes) {
                if (clazz.getName().contains("ApplicationContext")) {
                    scanSpringApplicationContext(clazz);
                }
            }
        } catch (Exception e) {
            logger.debug("Error scanning Spring beans: {}", e.getMessage());
        }
    }
    
    /**
     * Spring ApplicationContext에서 DataSource Bean 찾기
     */
    private void scanSpringApplicationContext(Class<?> contextClass) {
        try {
            // Static 인스턴스나 싱글톤 인스턴스 찾기
            Field[] fields = contextClass.getDeclaredFields();
            for (Field field : fields) {
                field.setAccessible(true);
                Object contextInstance = null;
                
                if (java.lang.reflect.Modifier.isStatic(field.getModifiers())) {
                    contextInstance = field.get(null);
                }
                
                if (contextInstance != null) {
                    // getBean 메서드로 DataSource 타입 Bean 찾기
                    try {
                        Method getBeanMethod = contextClass.getMethod("getBean", Class.class);
                        Object dataSourceBean = getBeanMethod.invoke(contextInstance, DataSource.class);
                        
                        if (dataSourceBean instanceof DataSource && !processedDataSources.contains(dataSourceBean)) {
                            System.out.println("✅ Spring DataSource Bean 발견");
                            wrapDataSource((DataSource) dataSourceBean, null, contextInstance);
                        }
                    } catch (Exception e) {
                        // Bean이 없거나 메서드가 없는 경우 무시
                    }
                }
            }
        } catch (Exception e) {
            logger.debug("Error scanning ApplicationContext: {}", e.getMessage());
        }
    }
    
    /**
     * 싱글톤 패턴 인스턴스 스캔
     */
    private void scanSingletonInstances(Class<?> clazz) {
        try {
            // getInstance() 메서드가 있는 경우
            Method getInstanceMethod = clazz.getMethod("getInstance");
            if (getInstanceMethod != null && java.lang.reflect.Modifier.isStatic(getInstanceMethod.getModifiers())) {
                Object instance = getInstanceMethod.invoke(null);
                if (instance instanceof DataSource && !processedDataSources.contains(instance)) {
                    System.out.println("✅ Singleton DataSource 발견: " + clazz.getSimpleName());
                    wrapDataSource((DataSource) instance, null, null);
                }
            }
        } catch (Exception e) {
            // getInstance 메서드가 없는 경우 무시
        }
    }
    
    /**
     * DataSource를 메트릭 수집 프록시로 감싸기
     */
    private void wrapDataSource(DataSource originalDataSource, Field field, Object container) {
        try {
            System.out.println("🔧 DataSource 프록시 생성 중: " + originalDataSource.getClass().getSimpleName());
            
            // 이미 처리된 DataSource인지 확인
            if (processedDataSources.contains(originalDataSource)) {
                return;
            }
            
            // 프록시 DataSource 생성
            DataSource proxyDataSource = (DataSource) Proxy.newProxyInstance(
                originalDataSource.getClass().getClassLoader(),
                new Class<?>[]{DataSource.class},
                (proxy, method, args) -> {
                    if ("getConnection".equals(method.getName())) {
                        // Connection 획득을 감지하고 메트릭 프록시로 감싸기
                        Connection originalConnection = (Connection) method.invoke(originalDataSource, args);
                        return wrapConnection(originalConnection);
                    } else {
                        return method.invoke(originalDataSource, args);
                    }
                }
            );
            
            // 원래 위치에 프록시 DataSource로 교체
            if (field != null) {
                field.set(container, proxyDataSource);
                System.out.println("✅ DataSource 프록시로 교체 완료: " + field.getName());
            }
            
            // Connection Pool 모니터링을 위해 원본 DataSource를 등록
            metricsCollector.registerDataSource(originalDataSource);
            System.out.println("🔗 DataSource가 Connection Pool 모니터링에 등록됨: " + originalDataSource.getClass().getSimpleName());
            
            processedDataSources.add(originalDataSource);
            
        } catch (Exception e) {
            logger.error("Error wrapping DataSource: {}", e.getMessage(), e);
        }
    }
    
    /**
     * Connection을 메트릭 수집 프록시로 감싸기
     */
    private Connection wrapConnection(Connection originalConnection) {
        // 모든 DB에 대해 범용 프록시 사용 (UniversalJDBCInterceptor 활용)
        
        // 폴백: 기존 JDK Dynamic Proxy 사용
        return (Connection) Proxy.newProxyInstance(
            originalConnection.getClass().getClassLoader(),
            new Class<?>[]{Connection.class},
            (proxy, method, args) -> {
                long startTime = System.nanoTime();
                
                try {
                    Object result = method.invoke(originalConnection, args);
                    
                    // 메서드별 메트릭 수집
                    long executionTime = System.nanoTime() - startTime;
                    String methodName = method.getName();
                    
                    if ("prepareStatement".equals(methodName)) {
                        // PreparedStatement 생성 감지
                        String sql = args.length > 0 ? String.valueOf(args[0]) : "unknown";
                        System.out.println("🔍 SQL 준비됨: " + sql);
                        metricsCollector.recordQuery(sql, executionTime, 
                                                   originalConnection.toString(), 
                                                   Thread.currentThread().getName());
                    } else if ("commit".equals(methodName)) {
                        metricsCollector.recordCommit(executionTime, 
                                                    originalConnection.toString(), 
                                                    "tx-" + System.currentTimeMillis());
                    } else if ("rollback".equals(methodName)) {
                        metricsCollector.recordRollback(executionTime, 
                                                       originalConnection.toString(), 
                                                       "tx-" + System.currentTimeMillis());
                    } else if ("setAutoCommit".equals(methodName)) {
                        // Transaction state change 감지
                        boolean autoCommit = args.length > 0 ? (Boolean) args[0] : true;
                        metricsCollector.recordTransactionStateChange(autoCommit, executionTime);
                    }
                    
                    return result;
                } catch (Exception e) {
                    long executionTime = System.nanoTime() - startTime;
                    metricsCollector.recordError(e, executionTime, originalConnection.toString());
                    throw e;
                }
            }
        );
    }
}