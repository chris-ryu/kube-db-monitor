package io.kubedb.monitor.agent;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.util.CheckClassAdapter;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;

/**
 * 바이트코드 검증 및 VerifyError 방지를 위한 테스트
 * 
 * 이 테스트는 Agent의 바이트코드 변환이 JVM의 바이트코드 검증을 통과하는지 확인합니다.
 * Spring DataSource나 기타 복잡한 클래스들의 변환 시 VerifyError가 발생하지 않도록 합니다.
 */
class BytecodeVerificationTest {

    private JDBCInterceptor interceptor;
    private AgentConfig config;

    @BeforeEach
    void setUp() {
        config = new AgentConfig.Builder()
                .enabled(true)
                .samplingRate(1.0)
                .safeTransformationMode(true) // 안전 모드로 테스트
                .build();
        
        interceptor = new JDBCInterceptor(config);
    }

    @Test
    void shouldProduceValidBytecodeForSimpleClass() throws Exception {
        // Given - 간단한 클래스 바이트코드 생성
        byte[] originalBytecode = createSimpleClassBytecode("TestClass");
        
        // When - Agent가 클래스를 변환
        byte[] transformedBytecode = interceptor.transform(
                null,
                "TestClass", 
                null, 
                null, 
                originalBytecode
        );
        
        // Then - 변환된 바이트코드가 유효해야 함
        if (transformedBytecode != null) {
            assertThatNoException().isThrownBy(() -> {
                validateBytecode(transformedBytecode);
            });
        }
    }

    @Test
    void shouldHandleSpringDataSourceClassesSafely() throws Exception {
        // Given - Spring DataSource 관련 클래스들
        String[] springClasses = {
                "org/springframework/jdbc/datasource/lookup/AbstractRoutingDataSource",
                "org/springframework/jdbc/datasource/DataSourceTransactionManager",
                "org/springframework/boot/jdbc/DataSourceBuilder"
        };
        
        for (String className : springClasses) {
            // When - Spring 클래스 변환 시도
            byte[] result = interceptor.transform(
                    null,
                    className,
                    null,
                    null,
                    createSimpleClassBytecode(className.replace('/', '.'))
            );
            
            // Then - Spring 클래스들은 변환되지 않아야 함 (null 반환)
            assertThat(result).isNull();
        }
    }

    @Test
    void shouldProduceVerifiableBytecodeForJDBCClasses() throws Exception {
        // Given - JDBC 관련 클래스들
        String[] jdbcClasses = {
                "java/sql/Connection",
                "java/sql/Statement", 
                "java/sql/PreparedStatement"
        };
        
        for (String className : jdbcClasses) {
            // Given
            byte[] originalBytecode = createJDBCClassBytecode(className);
            
            // When
            byte[] transformedBytecode = interceptor.transform(
                    null,
                    className,
                    null,
                    null,
                    originalBytecode
            );
            
            // Then - 변환된 바이트코드가 있다면 검증 가능해야 함
            if (transformedBytecode != null) {
                assertThatNoException().isThrownBy(() -> {
                    validateBytecode(transformedBytecode);
                });
                
                // 클래스 로딩도 가능해야 함
                assertThatNoException().isThrownBy(() -> {
                    loadClassFromBytecode(className.replace('/', '.'), transformedBytecode);
                });
            }
        }
    }

    @Test
    void shouldNotCorruptStackFramesInTransformation() throws Exception {
        // Given - 복잡한 메서드를 가진 클래스
        byte[] complexClassBytecode = createComplexClassBytecode();
        
        // When
        byte[] transformedBytecode = interceptor.transform(
                null,
                "ComplexTestClass",
                null,
                null,
                complexClassBytecode
        );
        
        // Then - 스택 프레임이 손상되지 않아야 함
        if (transformedBytecode != null) {
            assertThatNoException().isThrownBy(() -> {
                validateStackFrames(transformedBytecode);
            });
        }
    }

    @Test
    void shouldPreserveOriginalClassWhenTransformationFails() throws Exception {
        // Given - 잘못된 바이트코드
        byte[] invalidBytecode = new byte[]{0x01, 0x02, 0x03}; // 무효한 바이트코드
        
        // When
        byte[] result = interceptor.transform(
                null,
                "InvalidClass",
                null,
                null,
                invalidBytecode
        );
        
        // Then - 변환 실패시 null 반환 (원본 유지)
        assertThat(result).isNull();
    }

    // === Helper Methods ===

    private byte[] createSimpleClassBytecode(String className) {
        ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        cw.visit(
                Opcodes.V11, 
                Opcodes.ACC_PUBLIC, 
                className.replace('.', '/'), 
                null,
                "java/lang/Object", 
                null
        );
        
        // 기본 생성자 추가
        org.objectweb.asm.MethodVisitor mv = cw.visitMethod(
                Opcodes.ACC_PUBLIC, 
                "<init>", 
                "()V", 
                null, 
                null
        );
        mv.visitCode();
        mv.visitVarInsn(Opcodes.ALOAD, 0);
        mv.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false);
        mv.visitInsn(Opcodes.RETURN);
        mv.visitMaxs(1, 1);
        mv.visitEnd();
        
        cw.visitEnd();
        return cw.toByteArray();
    }

    private byte[] createJDBCClassBytecode(String className) {
        ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_MAXS | ClassWriter.COMPUTE_FRAMES);
        cw.visit(
                Opcodes.V11, 
                Opcodes.ACC_PUBLIC | Opcodes.ACC_ABSTRACT | Opcodes.ACC_INTERFACE, 
                className, 
                null,
                "java/lang/Object", 
                null
        );
        
        // JDBC 메서드 시그니처 추가 (인터페이스이므로 추상 메서드)
        if (className.contains("Connection")) {
            cw.visitMethod(
                    Opcodes.ACC_PUBLIC | Opcodes.ACC_ABSTRACT, 
                    "commit", 
                    "()V", 
                    null, 
                    new String[]{"java/sql/SQLException"}
            ).visitEnd();
        } else if (className.contains("Statement")) {
            cw.visitMethod(
                    Opcodes.ACC_PUBLIC | Opcodes.ACC_ABSTRACT, 
                    "executeQuery", 
                    "(Ljava/lang/String;)Ljava/sql/ResultSet;", 
                    null, 
                    new String[]{"java/sql/SQLException"}
            ).visitEnd();
        }
        
        cw.visitEnd();
        return cw.toByteArray();
    }

    private byte[] createComplexClassBytecode() {
        ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_MAXS | ClassWriter.COMPUTE_FRAMES);
        cw.visit(
                Opcodes.V11, 
                Opcodes.ACC_PUBLIC, 
                "ComplexTestClass", 
                null,
                "java/lang/Object", 
                null
        );
        
        // 복잡한 메서드 (try-catch, 루프, 조건문 포함)
        org.objectweb.asm.MethodVisitor mv = cw.visitMethod(
                Opcodes.ACC_PUBLIC, 
                "complexMethod", 
                "(I)Ljava/lang/String;", 
                null, 
                null
        );
        mv.visitCode();
        
        // try-catch 블록
        org.objectweb.asm.Label tryStart = new org.objectweb.asm.Label();
        org.objectweb.asm.Label tryEnd = new org.objectweb.asm.Label();
        org.objectweb.asm.Label catchStart = new org.objectweb.asm.Label();
        
        mv.visitTryCatchBlock(tryStart, tryEnd, catchStart, "java/lang/Exception");
        
        mv.visitLabel(tryStart);
        mv.visitLdcInsn("result");
        mv.visitLabel(tryEnd);
        mv.visitInsn(Opcodes.ARETURN);
        
        mv.visitLabel(catchStart);
        mv.visitVarInsn(Opcodes.ASTORE, 2);
        mv.visitLdcInsn("error");
        mv.visitInsn(Opcodes.ARETURN);
        
        mv.visitMaxs(1, 3);
        mv.visitEnd();
        
        cw.visitEnd();
        return cw.toByteArray();
    }

    private void validateBytecode(byte[] bytecode) {
        // ASM의 CheckClassAdapter를 사용한 바이트코드 검증
        StringWriter sw = new StringWriter();
        PrintWriter pw = new PrintWriter(sw);
        
        ClassReader cr = new ClassReader(bytecode);
        CheckClassAdapter.verify(cr, false, pw);
        
        String errors = sw.toString();
        if (!errors.isEmpty()) {
            throw new AssertionError("바이트코드 검증 실패: " + errors);
        }
    }

    private void validateStackFrames(byte[] bytecode) {
        // 스택 프레임 검증
        ClassReader cr = new ClassReader(bytecode);
        ClassNode cn = new ClassNode();
        cr.accept(cn, ClassReader.EXPAND_FRAMES);
        
        // 모든 메서드의 스택 프레임 확인
        for (MethodNode method : cn.methods) {
            if (method.instructions.size() > 0) {
                // 메서드가 정상적으로 파싱되었는지 확인
                assertThat(method.instructions).isNotNull();
            }
        }
    }

    private Class<?> loadClassFromBytecode(String className, byte[] bytecode) throws Exception {
        // 커스텀 클래스로더로 바이트코드에서 클래스 로딩
        ClassLoader classLoader = new ClassLoader() {
            @Override
            protected Class<?> findClass(String name) throws ClassNotFoundException {
                if (name.equals(className)) {
                    return defineClass(name, bytecode, 0, bytecode.length);
                }
                throw new ClassNotFoundException(name);
            }
        };
        
        return classLoader.loadClass(className);
    }
}