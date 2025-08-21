package io.kubedb.monitor.agent;

import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * CallableStatement 클래스 전용 변환기 (저장 프로시저 호출)
 */
public class CallableStatementTransformer extends ClassVisitor {
    
    private static final Logger logger = LoggerFactory.getLogger(CallableStatementTransformer.class);
    
    private final AgentConfig config;
    private final String className;
    private final PostgreSQLCompatibilityHelper postgresqlHelper;
    
    public CallableStatementTransformer(ClassVisitor cv, AgentConfig config, String className) {
        super(Opcodes.ASM9, cv);
        this.config = config;
        this.className = className;
        this.postgresqlHelper = null;
        logger.debug("CallableStatementTransformer initialized for: {}", className);
    }
    
    public CallableStatementTransformer(ClassVisitor cv, AgentConfig config, String className,
                                      PostgreSQLCompatibilityHelper postgresqlHelper) {
        super(Opcodes.ASM9, cv);
        this.config = config;
        this.className = className;
        this.postgresqlHelper = postgresqlHelper;
        logger.debug("CallableStatementTransformer with PostgreSQL support initialized for: {}", className);
    }
    
    @Override
    public MethodVisitor visitMethod(int access, String name, String descriptor, 
                                   String signature, String[] exceptions) {
        
        MethodVisitor mv = super.visitMethod(access, name, descriptor, signature, exceptions);
        
        // 저장 프로시저 관련 메서드들에 모니터링 추가
        if (isTargetMethod(name)) {
            logger.debug("Instrumenting CallableStatement method: {}.{}({})", className, name, descriptor);
            return new CallableStatementMethodVisitor(mv, name, config);
        }
        
        return mv;
    }
    
    private boolean isTargetMethod(String name) {
        return name.startsWith("execute") || 
               name.startsWith("registerOutParameter") ||
               name.startsWith("getObject") ||
               name.startsWith("getString");
    }
    
    private static class CallableStatementMethodVisitor extends MethodVisitor {
        private final String methodName;
        private final AgentConfig config;
        
        public CallableStatementMethodVisitor(MethodVisitor mv, String methodName, AgentConfig config) {
            super(Opcodes.ASM9, mv);
            this.methodName = methodName;
            this.config = config;
        }
        
        @Override
        public void visitCode() {
            super.visitCode();
            if (methodName.startsWith("execute")) {
                // 저장 프로시저 실행 시작 모니터링
                mv.visitMethodInsn(Opcodes.INVOKESTATIC, "java/lang/System", 
                                 "currentTimeMillis", "()J", false);
                mv.visitVarInsn(Opcodes.LSTORE, 40);
            }
        }
        
        @Override
        public void visitInsn(int opcode) {
            if (opcode >= Opcodes.IRETURN && opcode <= Opcodes.RETURN && 
                methodName.startsWith("execute")) {
                // 실행 시간 계산 및 메트릭 수집
                mv.visitMethodInsn(Opcodes.INVOKESTATIC, "java/lang/System", 
                                 "currentTimeMillis", "()J", false);
                mv.visitVarInsn(Opcodes.LLOAD, 40);
                mv.visitInsn(Opcodes.LSUB);
                
                mv.visitLdcInsn("StoredProcedure." + methodName);
                mv.visitInsn(Opcodes.DUP2);
                mv.visitMethodInsn(Opcodes.INVOKESTATIC, 
                                 "io/kubedb/monitor/agent/JDBCMethodInterceptor", 
                                 "recordCallableStatementMetric", 
                                 "(Ljava/lang/String;J)V", false);
                mv.visitInsn(Opcodes.POP2);
            }
            super.visitInsn(opcode);
        }
    }
}