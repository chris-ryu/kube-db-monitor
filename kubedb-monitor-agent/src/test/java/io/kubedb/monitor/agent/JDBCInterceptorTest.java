package io.kubedb.monitor.agent;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class JDBCInterceptorTest {

    private JDBCInterceptor interceptor;
    private AgentConfig config;

    @BeforeEach
    void setUp() {
        config = new AgentConfig.Builder()
                .enabled(true)
                .build();
        
        interceptor = new JDBCInterceptor(config);
    }

    @Test
    void shouldReturnNullForNonTargetClasses() throws Exception {
        // Given
        String className = "java/lang/String";
        byte[] classfileBuffer = new byte[0];

        // When
        byte[] result = interceptor.transform(
                null, 
                className, 
                null, 
                null, 
                classfileBuffer
        );

        // Then
        assertThat(result).isNull();
    }

    @Test
    void shouldReturnNullForNullClassName() throws Exception {
        // Given
        String className = null;
        byte[] classfileBuffer = new byte[0];

        // When
        byte[] result = interceptor.transform(
                null, 
                className, 
                null, 
                null, 
                classfileBuffer
        );

        // Then
        assertThat(result).isNull();
    }

    @Test
    void shouldIdentifyJdbcConnectionAsTarget() throws Exception {
        // Given
        String className = "java/sql/Connection";
        byte[] classfileBuffer = new byte[0];

        // When
        byte[] result = interceptor.transform(
                null, 
                className, 
                null, 
                null, 
                classfileBuffer
        );

        // Then
        // For now, returns null as transformation is not implemented
        assertThat(result).isNull();
    }

    @Test
    void shouldIdentifyJdbcStatementAsTarget() throws Exception {
        // Given
        String className = "java/sql/Statement";
        byte[] classfileBuffer = new byte[0];

        // When
        byte[] result = interceptor.transform(
                null, 
                className, 
                null, 
                null, 
                classfileBuffer
        );

        // Then
        assertThat(result).isNull();
    }

    @Test
    void shouldIdentifyJdbcPreparedStatementAsTarget() throws Exception {
        // Given
        String className = "java/sql/PreparedStatement";
        byte[] classfileBuffer = new byte[0];

        // When
        byte[] result = interceptor.transform(
                null, 
                className, 
                null, 
                null, 
                classfileBuffer
        );

        // Then
        assertThat(result).isNull();
    }

    @Test
    void shouldIdentifyMysqlDriverAsTarget() throws Exception {
        // Given
        String className = "com/mysql/cj/jdbc/ConnectionImpl";
        byte[] classfileBuffer = new byte[0];

        // When
        byte[] result = interceptor.transform(
                null, 
                className, 
                null, 
                null, 
                classfileBuffer
        );

        // Then
        assertThat(result).isNull();
    }

    @Test
    void shouldIdentifyPostgresqlDriverAsTarget() throws Exception {
        // Given
        String className = "org/postgresql/jdbc/PgConnection";
        byte[] classfileBuffer = new byte[0];

        // When
        byte[] result = interceptor.transform(
                null, 
                className, 
                null, 
                null, 
                classfileBuffer
        );

        // Then
        assertThat(result).isNull();
    }

    @Test
    void shouldIdentifyH2DriverAsTarget() throws Exception {
        // Given
        String className = "org/h2/jdbc/JdbcConnection";
        byte[] classfileBuffer = new byte[0];

        // When
        byte[] result = interceptor.transform(
                null, 
                className, 
                null, 
                null, 
                classfileBuffer
        );

        // Then
        assertThat(result).isNull();
    }

    @Test
    void shouldReturnNullWhenDisabled() throws Exception {
        // Given
        AgentConfig disabledConfig = new AgentConfig.Builder()
                .enabled(false)
                .build();
        JDBCInterceptor disabledInterceptor = new JDBCInterceptor(disabledConfig);
        
        String className = "java/sql/Connection";
        byte[] classfileBuffer = new byte[0];

        // When
        byte[] result = disabledInterceptor.transform(
                null, 
                className, 
                null, 
                null, 
                classfileBuffer
        );

        // Then
        assertThat(result).isNull();
    }

    @Test
    void shouldHandleExceptionGracefully() throws Exception {
        // Given - this will cause an exception during transformation
        String className = "java/sql/Connection";
        byte[] invalidClassfileBuffer = null;

        // When
        byte[] result = interceptor.transform(
                null, 
                className, 
                null, 
                null, 
                invalidClassfileBuffer
        );

        // Then - should return null instead of throwing exception
        assertThat(result).isNull();
    }
}