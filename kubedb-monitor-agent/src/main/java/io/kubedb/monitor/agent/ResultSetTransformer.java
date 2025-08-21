package io.kubedb.monitor.agent;

import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * ResultSet 클래스 전용 변환기 (결과 집합 처리)
 */
public class ResultSetTransformer extends ClassVisitor {
    
    private static final Logger logger = LoggerFactory.getLogger(ResultSetTransformer.class);
    
    private final AgentConfig config;
    private final String className;
    private final PostgreSQLCompatibilityHelper postgresqlHelper;
    
    public ResultSetTransformer(ClassVisitor cv, AgentConfig config, String className) {
        super(Opcodes.ASM9, cv);
        this.config = config;
        this.className = className;
        this.postgresqlHelper = null;
        logger.debug("ResultSetTransformer initialized for: {}", className);
    }
    
    public ResultSetTransformer(ClassVisitor cv, AgentConfig config, String className,
                              PostgreSQLCompatibilityHelper postgresqlHelper) {
        super(Opcodes.ASM9, cv);
        this.config = config;
        this.className = className;
        this.postgresqlHelper = postgresqlHelper;
        logger.debug("ResultSetTransformer with PostgreSQL support initialized for: {}", className);
    }
    
    @Override
    public MethodVisitor visitMethod(int access, String name, String descriptor, 
                                   String signature, String[] exceptions) {
        
        MethodVisitor mv = super.visitMethod(access, name, descriptor, signature, exceptions);
        
        // ResultSet 탐색 관련 메서드들에 모니터링 추가
        if (isTargetMethod(name)) {
            logger.debug("Instrumenting ResultSet method: {}.{}({})", className, name, descriptor);
            return new ResultSetMethodVisitor(mv, name, config);
        }
        
        return mv;
    }
    
    private boolean isTargetMethod(String name) {
        return "next".equals(name) || 
               "close".equals(name) ||
               name.startsWith("get"); // getString, getInt, getLong 등
    }
    
    private static class ResultSetMethodVisitor extends MethodVisitor {
        private final String methodName;
        private final AgentConfig config;
        
        public ResultSetMethodVisitor(MethodVisitor mv, String methodName, AgentConfig config) {
            super(Opcodes.ASM9, mv);
            this.methodName = methodName;
            this.config = config;
        }
        
        @Override
        public void visitInsn(int opcode) {
            // 결과 집합 처리 카운터 증가 (next 메서드만)
            if (opcode >= Opcodes.IRETURN && opcode <= Opcodes.RETURN && 
                "next".equals(methodName)) {
                mv.visitLdcInsn("ResultSet.next");
                mv.visitMethodInsn(Opcodes.INVOKESTATIC, 
                                 "io/kubedb/monitor/agent/JDBCMethodInterceptor", 
                                 "recordResultSetAccess", 
                                 "(Ljava/lang/String;)V", false);
            }
            super.visitInsn(opcode);
        }
    }
}