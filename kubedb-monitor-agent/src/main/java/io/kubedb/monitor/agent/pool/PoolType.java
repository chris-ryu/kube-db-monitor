package io.kubedb.monitor.agent.pool;

/**
 * Connection Pool 타입 열거형
 * 
 * 다양한 WAS 환경에서 사용되는 Connection Pool 구현체들을 식별하기 위한 타입
 */
public enum PoolType {
    HIKARI("HikariCP", "com.zaxxer.hikari.HikariDataSource"),
    TOMCAT("Tomcat JDBC Pool", "org.apache.tomcat.jdbc.pool.DataSource"),
    DBCP2("Apache Commons DBCP2", "org.apache.commons.dbcp2.BasicDataSource"),
    C3P0("C3P0", "com.mchange.v2.c3p0.ComboPooledDataSource"),
    DRUID("Alibaba Druid", "com.alibaba.druid.pool.DruidDataSource"),
    WEBLOGIC("Oracle WebLogic", "weblogic.jdbc"),
    WEBSPHERE("IBM WebSphere", "com.ibm.ws.rsadapter"),
    JBOSS("JBoss/WildFly", "org.jboss.jca"),
    UNKNOWN("Unknown", "");
    
    private final String displayName;
    private final String classPattern;
    
    PoolType(String displayName, String classPattern) {
        this.displayName = displayName;
        this.classPattern = classPattern;
    }
    
    public String getDisplayName() {
        return displayName;
    }
    
    public String getClassPattern() {
        return classPattern;
    }
    
    /**
     * DataSource 클래스명으로부터 Pool 타입을 감지
     */
    public static PoolType detectFromClassName(String className) {
        if (className == null || className.isEmpty()) {
            return UNKNOWN;
        }
        
        String lowerClassName = className.toLowerCase();
        
        // 명확한 클래스명 매치
        for (PoolType type : values()) {
            if (type != UNKNOWN && !type.getClassPattern().isEmpty()) {
                if (lowerClassName.contains(type.getClassPattern().toLowerCase())) {
                    return type;
                }
            }
        }
        
        // 패턴 매치
        if (lowerClassName.contains("hikari")) {
            return HIKARI;
        } else if (lowerClassName.contains("tomcat")) {
            return TOMCAT;
        } else if (lowerClassName.contains("dbcp")) {
            return DBCP2;
        } else if (lowerClassName.contains("c3p0")) {
            return C3P0;
        } else if (lowerClassName.contains("druid")) {
            return DRUID;
        } else if (lowerClassName.contains("weblogic")) {
            return WEBLOGIC;
        } else if (lowerClassName.contains("websphere") || lowerClassName.contains("ibm")) {
            return WEBSPHERE;
        } else if (lowerClassName.contains("jboss") || lowerClassName.contains("wildfly")) {
            return JBOSS;
        }
        
        return UNKNOWN;
    }
}