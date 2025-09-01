package io.kubedb.monitor.agent;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sql.DataSource;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Spring Boot 환경에서 DataSource를 감지하고 등록하는 클래스
 * ApplicationContext를 통해 DataSource Bean을 찾아서 Connection Pool 모니터링에 등록합니다.
 */
public class SpringBootDataSourceDetector {
    private static final Logger logger = LoggerFactory.getLogger(SpringBootDataSourceDetector.class);
    
    private static final Set<DataSource> detectedDataSources = ConcurrentHashMap.newKeySet();
    private static volatile boolean initialized = false;
    
    /**
     * Spring Boot ApplicationContext에서 DataSource를 찾아서 등록
     */
    public static void detectAndRegisterDataSources() {
        if (initialized) {
            return;
        }
        
        try {
            logger.info("[KubeDB] Spring Boot DataSource 감지 시작...");
            
            // Spring Boot ApplicationContext 찾기
            Object applicationContext = findSpringApplicationContext();
            if (applicationContext == null) {
                logger.warn("[KubeDB] Spring ApplicationContext를 찾을 수 없음");
                return;
            }
            
            logger.info("[KubeDB] Spring ApplicationContext 발견: {}", applicationContext.getClass().getName());
            
            // DataSource Bean 찾기
            DataSource dataSource = getDataSourceBean(applicationContext);
            if (dataSource != null && !detectedDataSources.contains(dataSource)) {
                
                logger.info("[KubeDB] DataSource Bean 발견: {}", dataSource.getClass().getName());
                System.out.println("🎯 Spring Boot DataSource 발견: " + dataSource.getClass().getSimpleName());
                
                // Global MetricsCollector에 등록
                MetricsCollector globalCollector = KubeDBAgent.getGlobalMetricsCollector();
                if (globalCollector != null) {
                    globalCollector.registerDataSource(dataSource);
                    detectedDataSources.add(dataSource);
                    
                    // HikariCP인 경우 MXBean 강제 등록
                    if (dataSource.getClass().getName().contains("Hikari")) {
                        enableHikariCPMXBeans(dataSource);
                    }
                    
                    System.out.println("✅ Spring Boot DataSource가 Connection Pool 모니터링에 등록됨: " + dataSource.getClass().getSimpleName());
                    logger.info("[KubeDB] DataSource 등록 완료: {}", dataSource.getClass().getName());
                } else {
                    logger.warn("[KubeDB] Global MetricsCollector가 null입니다");
                }
            }
            
            initialized = true;
            
        } catch (Exception e) {
            logger.error("[KubeDB] Spring Boot DataSource 감지 중 오류 발생", e);
        }
    }
    
    /**
     * Spring ApplicationContext 찾기
     */
    private static Object findSpringApplicationContext() {
        try {
            // Spring Boot의 SpringApplication.run 결과로 생성된 ApplicationContext 찾기
            Class<?> springApplicationClass = Class.forName("org.springframework.boot.SpringApplication");
            
            // Static 필드에서 현재 ApplicationContext 찾기
            Field[] fields = springApplicationClass.getDeclaredFields();
            for (Field field : fields) {
                field.setAccessible(true);
                if (java.lang.reflect.Modifier.isStatic(field.getModifiers())) {
                    Object value = field.get(null);
                    if (value != null && value.getClass().getName().contains("ApplicationContext")) {
                        return value;
                    }
                }
            }
            
            // 다른 방법: Spring Context Holder를 통한 접근 시도
            try {
                Class<?> contextHolderClass = Class.forName("org.springframework.context.ApplicationContextHolder");
                Method getContextMethod = contextHolderClass.getMethod("getApplicationContext");
                Object context = getContextMethod.invoke(null);
                if (context != null) {
                    return context;
                }
            } catch (Exception ignored) {
                // ApplicationContextHolder가 없는 경우 무시
            }
            
            return null;
            
        } catch (Exception e) {
            logger.debug("[KubeDB] ApplicationContext 찾기 실패: {}", e.getMessage());
            return null;
        }
    }
    
    /**
     * ApplicationContext에서 DataSource Bean 가져오기
     */
    private static DataSource getDataSourceBean(Object applicationContext) {
        try {
            // getBean(Class.class) 메서드 사용
            Method getBeanMethod = applicationContext.getClass().getMethod("getBean", Class.class);
            Object dataSourceBean = getBeanMethod.invoke(applicationContext, DataSource.class);
            
            if (dataSourceBean instanceof DataSource) {
                return (DataSource) dataSourceBean;
            }
            
        } catch (Exception e) {
            logger.debug("[KubeDB] DataSource Bean 가져오기 실패: {}", e.getMessage());
            
            // 대안: getBeanNamesForType 사용
            try {
                Method getBeanNamesForTypeMethod = applicationContext.getClass().getMethod("getBeanNamesForType", Class.class);
                String[] beanNames = (String[]) getBeanNamesForTypeMethod.invoke(applicationContext, DataSource.class);
                
                if (beanNames != null && beanNames.length > 0) {
                    Method getBeanByNameMethod = applicationContext.getClass().getMethod("getBean", String.class);
                    Object bean = getBeanByNameMethod.invoke(applicationContext, beanNames[0]);
                    
                    if (bean instanceof DataSource) {
                        return (DataSource) bean;
                    }
                }
            } catch (Exception e2) {
                logger.debug("[KubeDB] getBeanNamesForType 방법도 실패: {}", e2.getMessage());
            }
        }
        
        return null;
    }
    
    /**
     * 감지된 DataSource 개수 반환
     */
    public static int getDetectedDataSourceCount() {
        return detectedDataSources.size();
    }
    
    /**
     * HikariCP MXBean을 강제로 활성화
     */
    private static void enableHikariCPMXBeans(DataSource dataSource) {
        try {
            // HikariDataSource에서 register-mbeans를 true로 설정
            Method setRegisterMBeans = dataSource.getClass().getMethod("setRegisterMbeans", boolean.class);
            setRegisterMBeans.invoke(dataSource, true);
            
            System.out.println("🔧 HikariCP MXBean 등록 강제 활성화: " + dataSource.getClass().getSimpleName());
            logger.info("[KubeDB] HikariCP MXBean 등록 강제 활성화: {}", dataSource.getClass().getName());
            
        } catch (Exception e) {
            logger.debug("[KubeDB] HikariCP MXBean 활성화 실패 (이미 활성화되었거나 지원하지 않음): {}", e.getMessage());
            
            // 대안: reflection을 통해 HikariPool에서 직접 MXBean 등록 시도
            try {
                Field poolField = dataSource.getClass().getDeclaredField("pool");
                poolField.setAccessible(true);
                Object pool = poolField.get(dataSource);
                
                if (pool != null) {
                    Method registerMBean = pool.getClass().getMethod("setRegisterMbeans", boolean.class);
                    registerMBean.invoke(pool, true);
                    
                    System.out.println("🔧 HikariPool MXBean 직접 등록 성공");
                    logger.info("[KubeDB] HikariPool MXBean 직접 등록 성공");
                }
            } catch (Exception e2) {
                logger.debug("[KubeDB] HikariPool MXBean 직접 등록도 실패: {}", e2.getMessage());
            }
        }
    }
    
    /**
     * 초기화 상태 확인
     */
    public static boolean isInitialized() {
        return initialized;
    }
}