package io.kubedb.monitor.agent;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.Opcodes;

import static org.assertj.core.api.Assertions.*;

/**
 * JDBCTransformerFactory 테스트
 * 
 * 클래스별 적절한 변환기가 생성되는지 검증
 */
@DisplayName("JDBC 변환기 팩토리 테스트")
class JDBCTransformerFactoryTest {
    
    private final AgentConfig testConfig = createTestConfig();
    
    @Test
    @DisplayName("Connection 클래스에 대해 ConnectionTransformer 생성")
    void shouldCreateConnectionTransformerForConnectionClasses() {
        // Given
        String[] connectionClasses = {
            "java/sql/Connection",
            "org/postgresql/jdbc/PgConnection",
            "com/mysql/cj/jdbc/ConnectionImpl",
            "oracle/jdbc/driver/OracleConnection"
        };
        
        ClassVisitor mockCV = createMockClassVisitor();
        
        // When & Then
        for (String className : connectionClasses) {
            ClassVisitor transformer = JDBCTransformerFactory.createTransformer(className, mockCV, testConfig);
            
            assertThat(transformer)
                .withFailMessage("ConnectionTransformer should be created for: " + className)
                .isInstanceOf(ConnectionTransformer.class);
        }
    }
    
    @Test
    @DisplayName("PreparedStatement 클래스에 대해 PreparedStatementTransformer 생성")
    void shouldCreatePreparedStatementTransformerForPreparedStatementClasses() {
        // Given
        String[] preparedStatementClasses = {
            "java/sql/PreparedStatement",
            "org/postgresql/jdbc/PgPreparedStatement",
            "com/mysql/cj/jdbc/ClientPreparedStatement"
        };
        
        ClassVisitor mockCV = createMockClassVisitor();
        
        // When & Then
        for (String className : preparedStatementClasses) {
            ClassVisitor transformer = JDBCTransformerFactory.createTransformer(className, mockCV, testConfig);
            
            assertThat(transformer)
                .withFailMessage("PreparedStatementTransformer should be created for: " + className)
                .isInstanceOf(PreparedStatementTransformer.class);
        }
    }
    
    @Test
    @DisplayName("Statement 클래스에 대해 StatementTransformer 생성 (PreparedStatement 제외)")
    void shouldCreateStatementTransformerForStatementClasses() {
        // Given
        String[] statementClasses = {
            "java/sql/Statement",
            "org/postgresql/jdbc/PgStatement",
            "com/mysql/cj/jdbc/StatementImpl"
        };
        
        ClassVisitor mockCV = createMockClassVisitor();
        
        // When & Then
        for (String className : statementClasses) {
            ClassVisitor transformer = JDBCTransformerFactory.createTransformer(className, mockCV, testConfig);
            
            assertThat(transformer)
                .withFailMessage("StatementTransformer should be created for: " + className)
                .isInstanceOf(StatementTransformer.class);
        }
    }
    
    @Test
    @DisplayName("CallableStatement 클래스에 대해 CallableStatementTransformer 생성")
    void shouldCreateCallableStatementTransformerForCallableStatementClasses() {
        // Given
        String[] callableStatementClasses = {
            "java/sql/CallableStatement",
            "org/postgresql/jdbc/PgCallableStatement",
            "com/mysql/cj/jdbc/CallableStatementWrapper"
        };
        
        ClassVisitor mockCV = createMockClassVisitor();
        
        // When & Then
        for (String className : callableStatementClasses) {
            ClassVisitor transformer = JDBCTransformerFactory.createTransformer(className, mockCV, testConfig);
            
            assertThat(transformer)
                .withFailMessage("CallableStatementTransformer should be created for: " + className)
                .isInstanceOf(CallableStatementTransformer.class);
        }
    }
    
    @Test
    @DisplayName("ResultSet 클래스에 대해 ResultSetTransformer 생성")
    void shouldCreateResultSetTransformerForResultSetClasses() {
        // Given
        String[] resultSetClasses = {
            "java/sql/ResultSet",
            "org/postgresql/jdbc/PgResultSet",
            "com/mysql/cj/jdbc/result/ResultSetImpl"
        };
        
        ClassVisitor mockCV = createMockClassVisitor();
        
        // When & Then
        for (String className : resultSetClasses) {
            ClassVisitor transformer = JDBCTransformerFactory.createTransformer(className, mockCV, testConfig);
            
            assertThat(transformer)
                .withFailMessage("ResultSetTransformer should be created for: " + className)
                .isInstanceOf(ResultSetTransformer.class);
        }
    }
    
    @Test
    @DisplayName("기타 클래스에 대해 StandardTransformationVisitor 생성")
    void shouldCreateStandardTransformerForOtherClasses() {
        // Given
        String[] otherClasses = {
            "org/springframework/jdbc/core/JdbcTemplate",
            "javax/sql/DataSource",
            "org/apache/commons/dbcp2/BasicDataSource"
        };
        
        ClassVisitor mockCV = createMockClassVisitor();
        
        // When & Then
        for (String className : otherClasses) {
            ClassVisitor transformer = JDBCTransformerFactory.createTransformer(className, mockCV, testConfig);
            
            assertThat(transformer)
                .withFailMessage("StandardTransformationVisitor should be created for: " + className)
                .isInstanceOf(StandardTransformationVisitor.class);
        }
    }
    
    @Test
    @DisplayName("클래스 우선순위 검증: CallableStatement > PreparedStatement > Statement")
    void shouldRespectClassHierarchyPriority() {
        ClassVisitor mockCV = createMockClassVisitor();
        
        // CallableStatement는 PreparedStatement를 확장하지만 CallableStatementTransformer가 우선
        ClassVisitor transformer1 = JDBCTransformerFactory.createTransformer(
            "org/postgresql/jdbc/PgCallableStatement", mockCV, testConfig);
        assertThat(transformer1).isInstanceOf(CallableStatementTransformer.class);
        
        // PreparedStatement는 Statement를 확장하지만 PreparedStatementTransformer가 우선
        ClassVisitor transformer2 = JDBCTransformerFactory.createTransformer(
            "org/postgresql/jdbc/PgPreparedStatement", mockCV, testConfig);
        assertThat(transformer2).isInstanceOf(PreparedStatementTransformer.class);
    }
    
    /**
     * 테스트용 AgentConfig 생성
     */
    private AgentConfig createTestConfig() {
        return new AgentConfig.Builder()
            .enabled(true)
            .samplingRate(1.0)
            .supportedDatabases(java.util.Arrays.asList("postgresql", "mysql"))
            .maskSqlParams(true)
            .slowQueryThresholdMs(1000L)
            .collectorType("COMPOSITE")
            .postgresqlStrictCompatibility(true)
            .preserveTransactionBoundaries(true)
            .logLevel("DEBUG")
            .build();
    }
    
    /**
     * 테스트용 ClassVisitor 생성
     */
    private ClassVisitor createMockClassVisitor() {
        return new ClassVisitor(Opcodes.ASM9) {
            // 기본 구현만 제공
        };
    }
}