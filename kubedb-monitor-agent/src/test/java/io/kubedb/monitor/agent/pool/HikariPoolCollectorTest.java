package io.kubedb.monitor.agent.pool;

import io.kubedb.monitor.agent.pool.collectors.HikariPoolCollector;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;

import javax.sql.DataSource;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * HikariPoolCollector 테스트
 */
public class HikariPoolCollectorTest {
    
    private HikariPoolCollector collector;
    
    @BeforeEach
    public void setUp() {
        collector = new HikariPoolCollector();
    }
    
    @Test
    public void testSupportsHikariDataSource() {
        // Mock HikariDataSource
        DataSource hikariDS = mock(DataSource.class);
        when(hikariDS.getClass().getName()).thenReturn("com.zaxxer.hikari.HikariDataSource");
        
        assertTrue(collector.supports(hikariDS));
    }
    
    @Test
    public void testDoesNotSupportOtherDataSource() {
        // Mock non-HikariDataSource
        DataSource otherDS = mock(DataSource.class);
        when(otherDS.getClass().getName()).thenReturn("org.apache.commons.dbcp2.BasicDataSource");
        
        assertFalse(collector.supports(otherDS));
    }
    
    @Test
    public void testCollectReturnsEmptyOnFailure() {
        // Mock DataSource that will cause an exception
        DataSource brokenDS = mock(DataSource.class);
        when(brokenDS.getClass().getName()).thenReturn("com.zaxxer.hikari.HikariDataSource");
        
        // This will fail because we can't actually invoke getHikariPoolMXBean on a mock
        PoolMetrics metrics = collector.collect(brokenDS);
        
        // Should return empty metrics on failure
        assertEquals(PoolType.UNKNOWN, metrics.getPoolType());
        assertEquals(0, metrics.getActiveConnections());
    }
}