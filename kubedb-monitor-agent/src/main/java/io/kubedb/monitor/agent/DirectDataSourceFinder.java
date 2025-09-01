package io.kubedb.monitor.agent;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sql.DataSource;
import java.lang.instrument.Instrumentation;
import java.lang.reflect.Field;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * JVM을 스캔하여 직접적으로 HikariDataSource 인스턴스를 찾는 클래스
 * 모든 로드된 클래스의 static 필드와 인스턴스를 검색합니다.
 */
public class DirectDataSourceFinder {
    private static final Logger logger = LoggerFactory.getLogger(DirectDataSourceFinder.class);
    
    private static final Set<DataSource> foundDataSources = ConcurrentHashMap.newKeySet();
    private static ScheduledExecutorService scannerExecutor;
    
    /**
     * 주기적으로 DataSource를 찾아서 등록하는 스캐너 시작
     */
    public static void startPeriodicScanning() {
        if (scannerExecutor != null && !scannerExecutor.isShutdown()) {
            return;
        }
        
        scannerExecutor = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "DataSource-Scanner");
            t.setDaemon(true);
            return t;
        });
        
        // 10초마다 스캔
        scannerExecutor.scheduleAtFixedRate(() -> {
            try {
                scanForDataSources();
            } catch (Exception e) {
                logger.warn("[KubeDB] DataSource 스캔 중 오류", e);
            }
        }, 10, 10, TimeUnit.SECONDS);
        
        logger.info("[KubeDB] DirectDataSourceFinder 주기적 스캔 시작");
        System.out.println("🔍 DirectDataSourceFinder 주기적 스캔 시작 (10초마다)");
    }
    
    /**
     * JVM을 스캔하여 DataSource 인스턴스 찾기
     */
    private static void scanForDataSources() {
        try {
            Instrumentation instrumentation = KubeDBAgent.getInstrumentation();
            if (instrumentation == null) {
                return;
            }
            
            Class<?>[] loadedClasses = instrumentation.getAllLoadedClasses();
            
            for (Class<?> clazz : loadedClasses) {
                // HikariDataSource 클래스인지 확인
                if (clazz.getName().equals("com.zaxxer.hikari.HikariDataSource")) {
                    scanHikariDataSourceInstances(clazz);
                }
                
                // Spring Boot의 DataSourceConfiguration 클래스 확인
                if (clazz.getName().contains("DataSourceConfiguration") || 
                    clazz.getName().contains("DataSourceAutoConfiguration")) {
                    scanSpringDataSourceConfiguration(clazz);
                }
            }
            
        } catch (Exception e) {
            logger.debug("[KubeDB] DataSource 스캔 실패", e);
        }
    }
    
    /**
     * HikariDataSource 클래스에서 인스턴스 찾기
     */
    private static void scanHikariDataSourceInstances(Class<?> hikariClass) {
        try {
            // Static 필드에서 인스턴스 찾기
            Field[] fields = hikariClass.getDeclaredFields();
            for (Field field : fields) {
                if (java.lang.reflect.Modifier.isStatic(field.getModifiers()) &&
                    DataSource.class.isAssignableFrom(field.getType())) {
                    
                    field.setAccessible(true);
                    Object instance = field.get(null);
                    
                    if (instance instanceof DataSource) {
                        registerDataSource((DataSource) instance, "HikariDataSource static field: " + field.getName());
                    }
                }
            }
            
            // JVM Heap에서 인스턴스 찾기 (위험할 수 있으므로 제한적으로 사용)
            // 실제로는 Spring Boot에서 Bean Registry를 통해 찾는 것이 더 안전
            
        } catch (Exception e) {
            logger.debug("[KubeDB] HikariDataSource 인스턴스 스캔 실패", e);
        }
    }
    
    /**
     * Spring DataSource Configuration에서 Bean 찾기
     */
    private static void scanSpringDataSourceConfiguration(Class<?> configClass) {
        try {
            // Configuration 클래스의 @Bean 메서드로 생성된 DataSource 찾기
            Field[] fields = configClass.getDeclaredFields();
            for (Field field : fields) {
                if (DataSource.class.isAssignableFrom(field.getType())) {
                    
                    if (java.lang.reflect.Modifier.isStatic(field.getModifiers())) {
                        field.setAccessible(true);
                        Object instance = field.get(null);
                        
                        if (instance instanceof DataSource) {
                            registerDataSource((DataSource) instance, "Spring Configuration: " + configClass.getSimpleName());
                        }
                    }
                }
            }
            
        } catch (Exception e) {
            logger.debug("[KubeDB] Spring Configuration 스캔 실패", e);
        }
    }
    
    /**
     * Thread Local Storage에서 DataSource 찾기
     */
    public static void scanThreadLocalDataSources() {
        try {
            // 현재 스레드와 관련된 DataSource 인스턴스 찾기
            Thread currentThread = Thread.currentThread();
            ThreadGroup group = currentThread.getThreadGroup();
            
            // ThreadGroup에서 활성 스레드들을 확인
            Thread[] threads = new Thread[group.activeCount()];
            int count = group.enumerate(threads);
            
            for (int i = 0; i < count; i++) {
                if (threads[i] != null) {
                    // 스레드의 ContextClassLoader를 통해 Spring Context 접근 시도
                    ClassLoader classLoader = threads[i].getContextClassLoader();
                    if (classLoader != null) {
                        tryFindDataSourceInClassLoader(classLoader);
                    }
                }
            }
            
        } catch (Exception e) {
            logger.debug("[KubeDB] ThreadLocal DataSource 스캔 실패", e);
        }
    }
    
    /**
     * ClassLoader를 통해 DataSource 찾기
     */
    private static void tryFindDataSourceInClassLoader(ClassLoader classLoader) {
        try {
            // Spring Boot의 경우 ApplicationContext가 ClassLoader와 연결되어 있을 수 있음
            Class<?> holderClass = classLoader.loadClass("org.springframework.context.ApplicationContextHolder");
            java.lang.reflect.Method getContextMethod = holderClass.getMethod("getApplicationContext");
            Object applicationContext = getContextMethod.invoke(null);
            
            if (applicationContext != null) {
                java.lang.reflect.Method getBeanMethod = applicationContext.getClass().getMethod("getBean", Class.class);
                Object dataSourceBean = getBeanMethod.invoke(applicationContext, DataSource.class);
                
                if (dataSourceBean instanceof DataSource) {
                    registerDataSource((DataSource) dataSourceBean, "ApplicationContextHolder");
                }
            }
            
        } catch (Exception e) {
            // 실패해도 무시 (정상적일 수 있음)
        }
    }
    
    /**
     * DataSource를 등록하고 중복 체크
     */
    private static void registerDataSource(DataSource dataSource, String source) {
        if (foundDataSources.contains(dataSource)) {
            return;
        }
        
        foundDataSources.add(dataSource);
        
        // Global MetricsCollector에 등록
        MetricsCollector globalCollector = KubeDBAgent.getGlobalMetricsCollector();
        if (globalCollector != null) {
            globalCollector.registerDataSource(dataSource);
            
            System.out.println("🎯 DataSource 발견 및 등록: " + dataSource.getClass().getSimpleName() + 
                             " (출처: " + source + ")");
            logger.info("[KubeDB] DataSource 등록 완료: {} (출처: {})", 
                       dataSource.getClass().getName(), source);
        }
    }
    
    /**
     * 발견된 DataSource 수 반환
     */
    public static int getFoundDataSourceCount() {
        return foundDataSources.size();
    }
    
    /**
     * 스캐너 중지
     */
    public static void stop() {
        if (scannerExecutor != null && !scannerExecutor.isShutdown()) {
            scannerExecutor.shutdown();
            logger.info("[KubeDB] DirectDataSourceFinder 스캔 중지");
        }
    }
}