package com.university.registration.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.jdbc.DataSourceBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;

import jakarta.persistence.EntityManagerFactory;
import javax.sql.DataSource;
import java.util.Properties;

@Configuration
@EnableTransactionManagement
@EnableJpaRepositories(
    basePackages = "com.university.registration.repository",
    entityManagerFactoryRef = "entityManagerFactory",
    transactionManagerRef = "transactionManager"
)
@org.springframework.context.annotation.Profile("!test")
public class DatabaseConfig {

    @Primary
    @Bean
    public DataSource dataSource() {
        HikariConfig config = new HikariConfig();
        
        // 환경변수에서 설정값 가져오기 - 이전에 작동했던 방식
        String jdbcUrl = System.getenv("SPRING_DATASOURCE_JDBC_URL");
        if (jdbcUrl == null) {
            jdbcUrl = System.getenv("SPRING_DATASOURCE_URL");
        }
        config.setJdbcUrl(jdbcUrl);
        config.setUsername(System.getenv("SPRING_DATASOURCE_USERNAME"));
        config.setPassword(System.getenv("SPRING_DATASOURCE_PASSWORD"));
        config.setDriverClassName(System.getenv().getOrDefault("SPRING_DATASOURCE_DRIVER_CLASS_NAME", "org.postgresql.Driver"));
            
        // HikariCP 설정
        config.setMaximumPoolSize(Integer.parseInt(
            System.getenv().getOrDefault("SPRING_DATASOURCE_HIKARI_MAXIMUM_POOL_SIZE", "10")));
        config.setMinimumIdle(Integer.parseInt(
            System.getenv().getOrDefault("SPRING_DATASOURCE_HIKARI_MINIMUM_IDLE", "2")));
        config.setConnectionTimeout(Long.parseLong(
            System.getenv().getOrDefault("SPRING_DATASOURCE_HIKARI_CONNECTION_TIMEOUT", "30000")));
        config.setIdleTimeout(Long.parseLong(
            System.getenv().getOrDefault("SPRING_DATASOURCE_HIKARI_IDLE_TIMEOUT", "600000")));
        config.setMaxLifetime(Long.parseLong(
            System.getenv().getOrDefault("SPRING_DATASOURCE_HIKARI_MAX_LIFETIME", "1800000")));
            
        // autoCommit 설정
        boolean autoCommit = Boolean.parseBoolean(
            System.getenv().getOrDefault("SPRING_DATASOURCE_HIKARI_AUTO_COMMIT", "false"));
        config.setAutoCommit(autoCommit);
        
        System.out.println("🔧 DatabaseConfig - 연결 정보:");
        System.out.println("  JDBC URL: " + jdbcUrl);
        System.out.println("  Username: " + System.getenv("SPRING_DATASOURCE_USERNAME"));
        System.out.println("  AutoCommit: " + autoCommit);
        
        return new HikariDataSource(config);
    }

    @Bean
    public LocalContainerEntityManagerFactoryBean entityManagerFactory() {
        LocalContainerEntityManagerFactoryBean em = new LocalContainerEntityManagerFactoryBean();
        em.setDataSource(dataSource());
        em.setPackagesToScan("com.university.registration.entity");
        
        HibernateJpaVendorAdapter vendorAdapter = new HibernateJpaVendorAdapter();
        em.setJpaVendorAdapter(vendorAdapter);
        
        Properties props = new Properties();
        props.put("hibernate.dialect", System.getenv().getOrDefault("SPRING_JPA_DATABASE_PLATFORM", "org.hibernate.dialect.PostgreSQLDialect"));
        props.put("hibernate.hbm2ddl.auto", System.getenv().getOrDefault("SPRING_JPA_HIBERNATE_DDL_AUTO", "validate"));
        props.put("hibernate.show_sql", System.getenv().getOrDefault("SPRING_JPA_SHOW_SQL", "false"));
        props.put("hibernate.format_sql", "true");
        props.put("hibernate.generate_statistics", "true");
        props.put("hibernate.jdbc.batch_size", "20");
        props.put("hibernate.order_inserts", "true");
        props.put("hibernate.order_updates", "true");
        props.put("hibernate.connection.provider_disables_autocommit", "false");
        
        em.setJpaProperties(props);
        
        return em;
    }

    @Bean
    public PlatformTransactionManager transactionManager(EntityManagerFactory entityManagerFactory) {
        JpaTransactionManager transactionManager = new JpaTransactionManager();
        transactionManager.setEntityManagerFactory(entityManagerFactory);
        return transactionManager;
    }
}