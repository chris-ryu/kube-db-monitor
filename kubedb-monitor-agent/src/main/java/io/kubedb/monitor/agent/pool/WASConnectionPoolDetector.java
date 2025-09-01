package io.kubedb.monitor.agent.pool;

import javax.sql.DataSource;
import javax.naming.Context;
import javax.naming.InitialContext;
import javax.naming.NamingEnumeration;
import javax.naming.NameClassPair;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;
import java.util.logging.Level;

/**
 * WAS 환경에서 Connection Pool을 자동으로 감지하는 클래스
 * 
 * 다양한 WAS 환경에서 사용되는 Connection Pool들을 JNDI, 클래스 분석 등을 통해 감지합니다.
 */
public class WASConnectionPoolDetector {
    private static final Logger logger = Logger.getLogger(WASConnectionPoolDetector.class.getName());
    
    private static final String[] COMMON_JNDI_PREFIXES = {
        "java:comp/env/jdbc/",
        "java:comp/env/",
        "java:/",
        "jdbc/"
    };
    
    private final List<DataSource> detectedDataSources = new ArrayList<>();
    private volatile boolean initialized = false;
    
    /**
     * WAS 환경에서 사용 가능한 모든 DataSource를 감지
     */
    public synchronized void detectConnectionPools() {
        if (initialized) {
            return;
        }
        
        logger.info("[KubeDB] WAS Connection Pool 감지 시작");
        
        // 1. JNDI를 통한 DataSource 검색
        detectFromJNDI();
        
        // 2. Spring Bean Context에서 DataSource 검색 (가능한 경우)
        detectFromSpringContext();
        
        // 3. 알려진 DataSource 인스턴스 추적
        detectFromRegisteredDataSources();
        
        initialized = true;
        
        logger.info(String.format("[KubeDB] Connection Pool 감지 완료: %d개 발견", detectedDataSources.size()));
        for (DataSource ds : detectedDataSources) {
            PoolType poolType = PoolType.detectFromClassName(ds.getClass().getName());
            logger.info(String.format("[KubeDB] 감지된 Pool: %s (%s)", 
                       poolType.getDisplayName(), ds.getClass().getName()));
        }
    }
    
    /**
     * JNDI를 통한 DataSource 검색
     */
    private void detectFromJNDI() {
        try {
            Context ctx = new InitialContext();
            
            for (String prefix : COMMON_JNDI_PREFIXES) {
                try {
                    scanJNDIContext(ctx, prefix);
                } catch (Exception e) {
                    logger.fine(String.format("[KubeDB] JNDI prefix '%s' 스캔 실패: %s", prefix, e.getMessage()));
                }
            }
            
        } catch (Exception e) {
            logger.warning(String.format("[KubeDB] JNDI Context 초기화 실패: %s", e.getMessage()));
        }
    }
    
    /**
     * JNDI Context를 재귀적으로 스캔하여 DataSource 검색
     */
    private void scanJNDIContext(Context ctx, String path) {
        try {
            NamingEnumeration<NameClassPair> list = ctx.list(path);
            
            while (list.hasMore()) {
                NameClassPair nc = list.next();
                String name = nc.getName();
                String className = nc.getClassName();
                String fullPath = path + name;
                
                logger.fine(String.format("[KubeDB] JNDI 검사: %s (%s)", fullPath, className));
                
                try {
                    Object obj = ctx.lookup(fullPath);
                    
                    if (obj instanceof DataSource) {
                        DataSource ds = (DataSource) obj;
                        detectedDataSources.add(ds);
                        logger.info(String.format("[KubeDB] JNDI DataSource 발견: %s -> %s", 
                                   fullPath, ds.getClass().getName()));
                    } else if (className != null && className.contains("Context")) {
                        // 하위 Context가 있다면 재귀적으로 검색
                        scanJNDIContext(ctx, fullPath + "/");
                    }
                    
                } catch (Exception e) {
                    logger.fine(String.format("[KubeDB] JNDI lookup 실패 %s: %s", fullPath, e.getMessage()));
                }
            }
            
        } catch (Exception e) {
            logger.fine(String.format("[KubeDB] JNDI context 스캔 실패 %s: %s", path, e.getMessage()));
        }
    }
    
    /**
     * Spring Bean Context에서 DataSource 검색 (Reflection 사용)
     */
    private void detectFromSpringContext() {
        try {
            // Spring ApplicationContext가 존재하는 경우에만 시도
            Class<?> contextClass = Class.forName("org.springframework.context.ApplicationContext");
            Class<?> holderClass = Class.forName("org.springframework.context.ApplicationContextAware");
            
            // Spring Bean에서 DataSource 타입의 Bean들 검색
            logger.fine("[KubeDB] Spring Context에서 DataSource 검색 시도");
            
            // 실제 구현은 Spring 의존성이 있는 경우에만 활성화
            // 현재는 로그만 남기고 추후 확장 가능하도록 구조만 준비
            
        } catch (ClassNotFoundException e) {
            logger.fine("[KubeDB] Spring Framework가 감지되지 않음");
        } catch (Exception e) {
            logger.fine(String.format("[KubeDB] Spring Context 스캔 실패: %s", e.getMessage()));
        }
    }
    
    /**
     * 등록된 DataSource 인스턴스들을 추적
     * (Agent의 다른 부분에서 DataSource를 발견했을 때 등록할 수 있도록)
     */
    private void detectFromRegisteredDataSources() {
        // DataSource 등록 메커니즘은 추후 JDBC 인터셉터와 연동하여 구현
        logger.fine("[KubeDB] 등록된 DataSource 인스턴스 검사");
    }
    
    /**
     * 감지된 모든 DataSource 반환
     */
    public List<DataSource> getDetectedDataSources() {
        if (!initialized) {
            detectConnectionPools();
        }
        return new ArrayList<>(detectedDataSources);
    }
    
    /**
     * DataSource를 수동으로 등록 (JDBC 인터셉터에서 새로운 DataSource 발견 시 사용)
     */
    public void registerDataSource(DataSource dataSource) {
        if (dataSource != null && !detectedDataSources.contains(dataSource)) {
            detectedDataSources.add(dataSource);
            PoolType poolType = PoolType.detectFromClassName(dataSource.getClass().getName());
            logger.info(String.format("[KubeDB] 새로운 DataSource 등록: %s (%s)", 
                       poolType.getDisplayName(), dataSource.getClass().getName()));
        }
    }
    
    /**
     * 감지된 Connection Pool이 있는지 확인
     */
    public boolean hasDetectedPools() {
        return !getDetectedDataSources().isEmpty();
    }
    
    /**
     * 감지 상태 재설정 (테스트용)
     */
    public synchronized void reset() {
        detectedDataSources.clear();
        initialized = false;
        logger.fine("[KubeDB] Connection Pool 감지 상태 재설정");
    }
}