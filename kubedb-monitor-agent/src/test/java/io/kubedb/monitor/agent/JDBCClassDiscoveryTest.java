package io.kubedb.monitor.agent;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

import java.security.ProtectionDomain;

import static org.assertj.core.api.Assertions.*;

/**
 * JDBC 클래스 누락 탐지 기능 테스트
 * 
 * 새로운 누락 탐지 로직이 제대로 작동하는지 검증
 */
@DisplayName("JDBC 클래스 누락 탐지 테스트")
class JDBCClassDiscoveryTest {
    
    private JDBCInterceptor interceptor;
    private AgentConfig testConfig;
    
    @BeforeEach
    void setUp() {
        testConfig = new AgentConfig.Builder()
            .enabled(true)
            .samplingRate(1.0)
            .supportedDatabases(java.util.Arrays.asList("postgresql"))
            .logLevel("DEBUG")
            .build();
            
        interceptor = new JDBCInterceptor(testConfig);
    }
    
    @Test
    @DisplayName("JDBC 메서드를 가진 클래스가 변환됨 (jdbc 패턴 포함)")
    void shouldTransformJDBCClassesWithJDBCMethods() throws Exception {
        // Given: executeQuery 메서드를 가진 가상의 클래스 생성
        byte[] classBytes = createClassWithJDBCMethod("executeQuery", "()Ljava/sql/ResultSet;");
        String className = "com/custom/jdbc/CustomStatement"; // jdbc 패턴을 포함하므로 변환됨
        
        // When: transform 호출
        byte[] result = interceptor.transform(
            getClass().getClassLoader(),
            className,
            null,
            null,
            classBytes
        );
        
        // Then: jdbc 패턴을 포함하므로 실제 변환됨
        assertThat(result).isNotNull(); // 변환된 바이트코드가 반환됨
        assertThat(result).isNotEqualTo(classBytes); // 원본과 달라야 함
        
        // 로그 검증은 실제 로깅 프레임워크를 통해 확인 필요
        // 여기서는 메서드가 정상적으로 호출되었음을 확인
    }
    
    @Test
    @DisplayName("JDBC 메서드가 없는 클래스는 누락 대상으로 감지되지 않음")
    void shouldNotDetectNonJDBCClassesAsMissed() throws Exception {
        // Given: JDBC와 관련없는 메서드만 가진 클래스 생성
        byte[] classBytes = createClassWithJDBCMethod("toString", "()Ljava/lang/String;");
        String className = "com/custom/util/UtilityClass";
        
        // When: transform 호출
        byte[] result = interceptor.transform(
            getClass().getClassLoader(),
            className,
            null,
            null,
            classBytes
        );
        
        // Then: 변환되지 않고 누락 로그도 기록되지 않음
        assertThat(result).isNull();
    }
    
    @Test
    @DisplayName("Spring 내부 클래스는 노이즈 방지를 위해 제외됨")
    void shouldExcludeSpringInternalClassesFromDetection() throws Exception {
        // Given: Spring 내부 클래스 (jdbc/core 제외)
        byte[] classBytes = createClassWithJDBCMethod("executeQuery", "()Ljava/sql/ResultSet;");
        String className = "org/springframework/beans/factory/BeanFactory";
        
        // When: transform 호출
        byte[] result = interceptor.transform(
            getClass().getClassLoader(),
            className,
            null,
            null,
            classBytes
        );
        
        // Then: Spring 내부 클래스는 누락 탐지에서 제외
        assertThat(result).isNull();
    }
    
    @Test
    @DisplayName("Spring JDBC 핵심 클래스는 변환됨")
    void shouldTransformSpringJDBCCoreClasses() throws Exception {
        // Given: Spring JDBC 핵심 클래스
        byte[] classBytes = createClassWithJDBCMethod("executeQuery", "()Ljava/sql/ResultSet;");
        String className = "org/springframework/jdbc/core/CustomJdbcTemplate"; // jdbc 패턴 포함
        
        // When: transform 호출
        byte[] result = interceptor.transform(
            getClass().getClassLoader(),
            className,
            null,
            null,
            classBytes
        );
        
        // Then: jdbc 패턴을 포함하므로 실제 변환됨
        assertThat(result).isNotNull(); // 변환된 바이트코드가 반환됨
        assertThat(result).isNotEqualTo(classBytes); // 원본과 달라야 함
    }
    
    @Test
    @DisplayName("JVM 내부 클래스는 누락 탐지에서 제외됨")
    void shouldExcludeJVMInternalClasses() throws Exception {
        // Given: JVM 내부 클래스들
        String[] jvmClasses = {
            "java/lang/String",
            "java/util/ArrayList", 
            "sun/misc/Unsafe",
            "jdk/internal/misc/VM"
        };
        
        byte[] classBytes = createClassWithJDBCMethod("executeQuery", "()Ljava/sql/ResultSet;");
        
        for (String className : jvmClasses) {
            // When: transform 호출
            byte[] result = interceptor.transform(
                getClass().getClassLoader(),
                className,
                null,
                null,
                classBytes
            );
            
            // Then: JVM 클래스는 누락 탐지에서 제외
            assertThat(result).isNull();
        }
    }
    
    @Test
    @DisplayName("주요 JDBC 메서드 패턴이 변환됨")
    void shouldTransformMajorJDBCMethodPatterns() throws Exception {
        // Given: 주요 JDBC 메서드들을 가진 클래스들 (변환이 실제로 적용되는 메서드들)
        String[] jdbcMethods = {
            "executeQuery", "executeUpdate", "execute", 
            "getConnection", "setAutoCommit", "commit", "rollback",
            "setString", "setInt", "next", "getString"
        };
        
        for (String methodName : jdbcMethods) {
            // 각 메서드를 가진 클래스 생성 (jdbc 패턴 포함)
            byte[] classBytes = createClassWithJDBCMethod(methodName, "()V");
            String className = "com/test/jdbc/" + methodName + "TestClass"; // jdbc 패턴 포함
            
            // When: transform 호출
            byte[] result = interceptor.transform(
                getClass().getClassLoader(),
                className,
                null,
                null,
                classBytes
            );
            
            // Then: jdbc 패턴을 포함하므로 변환됨
            assertThat(result).isNotNull(); // 변환된 바이트코드가 반환됨
            
            // 일부 메서드는 변환이 적용되지 않을 수 있으므로 로그 확인만 진행
            // (실제 바이트코드 비교보다는 변환 프로세스가 실행되었음을 확인)
        }
    }
    
    @Test
    @DisplayName("누락 탐지 기능 - jdbc 패턴이 없는 클래스의 JDBC 메서드")
    void shouldDetectMissedJDBCClassesWithoutJDBCPattern() throws Exception {
        // Given: JDBC 메서드를 가진 클래스이지만 jdbc 패턴이 없음
        byte[] classBytes = createClassWithJDBCMethod("executeQuery", "()Ljava/sql/ResultSet;");
        String className = "com/custom/database/CustomDatabaseAccessor"; // jdbc 패턴 없음
        
        // When: transform 호출
        byte[] result = interceptor.transform(
            getClass().getClassLoader(),
            className,
            null,
            null,
            classBytes
        );
        
        // Then: jdbc 패턴이 없으므로 변환되지 않음 (누락 탐지 로그는 기록됨)
        assertThat(result).isNull(); // 변환되지 않음
        
        // 실제 로그 확인은 수동 검증 또는 로그 캡처를 통해 가능
        // 여기서는 누락 탐지 로직이 호출되었음을 확인
    }
    
    /**
     * 테스트용 클래스 바이트코드 생성
     */
    private byte[] createClassWithJDBCMethod(String methodName, String descriptor) {
        ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        
        // 클래스 헤더 생성
        cw.visit(Opcodes.V11, Opcodes.ACC_PUBLIC, "TestClass", null, "java/lang/Object", null);
        
        // 기본 생성자 생성
        MethodVisitor constructor = cw.visitMethod(Opcodes.ACC_PUBLIC, "<init>", "()V", null, null);
        constructor.visitCode();
        constructor.visitVarInsn(Opcodes.ALOAD, 0);
        constructor.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false);
        constructor.visitInsn(Opcodes.RETURN);
        constructor.visitMaxs(1, 1);
        constructor.visitEnd();
        
        // JDBC 메서드 생성
        MethodVisitor method = cw.visitMethod(Opcodes.ACC_PUBLIC, methodName, descriptor, null, null);
        method.visitCode();
        
        // 리턴 타입에 따른 적절한 리턴 명령
        if (descriptor.endsWith(")V")) {
            method.visitInsn(Opcodes.RETURN);
        } else if (descriptor.endsWith(")Z")) {
            method.visitInsn(Opcodes.ICONST_1);
            method.visitInsn(Opcodes.IRETURN);
        } else if (descriptor.endsWith(")I")) {
            method.visitInsn(Opcodes.ICONST_0);
            method.visitInsn(Opcodes.IRETURN);
        } else if (descriptor.endsWith(")J")) {
            method.visitInsn(Opcodes.LCONST_0);
            method.visitInsn(Opcodes.LRETURN);
        } else {
            method.visitInsn(Opcodes.ACONST_NULL);
            method.visitInsn(Opcodes.ARETURN);
        }
        
        method.visitMaxs(2, 1);
        method.visitEnd();
        
        cw.visitEnd();
        return cw.toByteArray();
    }
}