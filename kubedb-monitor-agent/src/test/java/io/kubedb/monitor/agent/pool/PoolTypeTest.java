package io.kubedb.monitor.agent.pool;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 * PoolType 감지 로직 테스트
 */
public class PoolTypeTest {
    
    @Test
    public void testDetectHikariCP() {
        assertEquals(PoolType.HIKARI, 
                    PoolType.detectFromClassName("com.zaxxer.hikari.HikariDataSource"));
        assertEquals(PoolType.HIKARI, 
                    PoolType.detectFromClassName("com.zaxxer.HikariDataSource"));
    }
    
    @Test
    public void testDetectTomcatPool() {
        assertEquals(PoolType.TOMCAT, 
                    PoolType.detectFromClassName("org.apache.tomcat.jdbc.pool.DataSource"));
        assertEquals(PoolType.TOMCAT, 
                    PoolType.detectFromClassName("org.apache.tomcat.jdbc.DataSource"));
    }
    
    @Test
    public void testDetectDBCP2() {
        assertEquals(PoolType.DBCP2, 
                    PoolType.detectFromClassName("org.apache.commons.dbcp2.BasicDataSource"));
        assertEquals(PoolType.DBCP2, 
                    PoolType.detectFromClassName("org.apache.commons.dbcp.BasicDataSource"));
    }
    
    @Test
    public void testDetectDruid() {
        assertEquals(PoolType.DRUID, 
                    PoolType.detectFromClassName("com.alibaba.druid.pool.DruidDataSource"));
    }
    
    @Test
    public void testDetectC3P0() {
        assertEquals(PoolType.C3P0, 
                    PoolType.detectFromClassName("com.mchange.v2.c3p0.ComboPooledDataSource"));
    }
    
    @Test
    public void testDetectWebLogic() {
        assertEquals(PoolType.WEBLOGIC, 
                    PoolType.detectFromClassName("weblogic.jdbc.wrapper.DataSource"));
    }
    
    @Test
    public void testDetectWebSphere() {
        assertEquals(PoolType.WEBSPHERE, 
                    PoolType.detectFromClassName("com.ibm.ws.rsadapter.jdbc.WSJdbcDataSource"));
        assertEquals(PoolType.WEBSPHERE, 
                    PoolType.detectFromClassName("com.ibm.websphere.rsadapter.WSDataSource"));
    }
    
    @Test
    public void testDetectJBoss() {
        assertEquals(PoolType.JBOSS, 
                    PoolType.detectFromClassName("org.jboss.jca.adapters.jdbc.WrapperDataSource"));
        assertEquals(PoolType.JBOSS, 
                    PoolType.detectFromClassName("org.wildfly.extension.datasources.WildFlyDataSource"));
    }
    
    @Test
    public void testDetectUnknown() {
        assertEquals(PoolType.UNKNOWN, 
                    PoolType.detectFromClassName("com.example.custom.CustomDataSource"));
        assertEquals(PoolType.UNKNOWN, 
                    PoolType.detectFromClassName(""));
        assertEquals(PoolType.UNKNOWN, 
                    PoolType.detectFromClassName(null));
    }
    
    @Test
    public void testCaseInsensitiveDetection() {
        assertEquals(PoolType.HIKARI, 
                    PoolType.detectFromClassName("COM.ZAXXER.HIKARI.HIKARIDATASOURCE"));
        assertEquals(PoolType.TOMCAT, 
                    PoolType.detectFromClassName("ORG.APACHE.TOMCAT.JDBC.POOL.DATASOURCE"));
    }
}