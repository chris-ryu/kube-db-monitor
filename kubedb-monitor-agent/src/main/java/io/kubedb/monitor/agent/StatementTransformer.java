package io.kubedb.monitor.agent;

import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Statement 클래스 전용 변환기
 * 
 * 모니터링 대상 메서드:
 * - executeQuery(String sql): SQL 조회 실행 모니터링
 * - executeUpdate(String sql): SQL 업데이트 실행 모니터링
 * - execute(String sql): 일반 SQL 실행 모니터링
 * - executeBatch(): 배치 실행 모니터링
 */
public class StatementTransformer extends ClassVisitor {
    
    private static final Logger logger = LoggerFactory.getLogger(StatementTransformer.class);
    
    private final AgentConfig config;
    private final String className;
    private final PostgreSQLCompatibilityHelper postgresqlHelper;
    
    public StatementTransformer(ClassVisitor cv, AgentConfig config, String className) {
        super(Opcodes.ASM9, cv);
        this.config = config;
        this.className = className;
        this.postgresqlHelper = null;
        logger.debug("StatementTransformer initialized for: {}", className);
    }
    
    public StatementTransformer(ClassVisitor cv, AgentConfig config, String className,
                              PostgreSQLCompatibilityHelper postgresqlHelper) {
        super(Opcodes.ASM9, cv);
        this.config = config;
        this.className = className;
        this.postgresqlHelper = postgresqlHelper;
        logger.debug("StatementTransformer with PostgreSQL support initialized for: {}", className);
    }
    
    @Override
    public MethodVisitor visitMethod(int access, String name, String descriptor, 
                                   String signature, String[] exceptions) {
        
        MethodVisitor mv = super.visitMethod(access, name, descriptor, signature, exceptions);
        
        // Statement의 핵심 메서드들에 모니터링 추가
        if (isTargetMethod(name, descriptor)) {
            logger.debug("Instrumenting Statement method: {}.{}({})", className, name, descriptor);
            return new StatementMethodVisitor(mv, name, descriptor, config);
        }
        
        return mv;
    }
    
    /**
     * 모니터링 대상 메서드인지 판단
     */
    private boolean isTargetMethod(String name, String descriptor) {
        switch (name) {
            case "executeQuery":
                return descriptor.equals("(Ljava/lang/String;)Ljava/sql/ResultSet;");
            case "executeUpdate":
                return descriptor.equals("(Ljava/lang/String;)I");
            case "execute":
                return descriptor.equals("(Ljava/lang/String;)Z");
            case "executeBatch":
                return descriptor.equals("()[I");
            default:
                return false;
        }
    }
    
    /**
     * Statement 메서드 전용 MethodVisitor
     */
    private static class StatementMethodVisitor extends MethodVisitor {
        
        private final String methodName;
        private final String descriptor;
        private final AgentConfig config;
        
        public StatementMethodVisitor(MethodVisitor mv, String methodName, 
                                    String descriptor, AgentConfig config) {
            super(Opcodes.ASM9, mv);
            this.methodName = methodName;
            this.descriptor = descriptor;
            this.config = config;
        }
        
        @Override
        public void visitCode() {
            super.visitCode();
            insertMethodStartMonitoring();
        }
        
        @Override
        public void visitInsn(int opcode) {
            if (opcode >= Opcodes.IRETURN && opcode <= Opcodes.RETURN) {
                insertMethodEndMonitoring();
            }
            super.visitInsn(opcode);
        }
        
        private void insertMethodStartMonitoring() {
            // 시작 시간 저장
            mv.visitMethodInsn(Opcodes.INVOKESTATIC, "java/lang/System", 
                             "currentTimeMillis", "()J", false);
            mv.visitVarInsn(Opcodes.LSTORE, 30);
            
            // SQL 파라미터 로깅 (첫 번째 파라미터가 SQL 문자열)
            if ("DEBUG".equals(config.getLogLevel()) && hasSqlParameter()) {
                mv.visitFieldInsn(Opcodes.GETSTATIC, 
                                "io/kubedb/monitor/agent/StatementTransformer", 
                                "logger", "Lorg/slf4j/Logger;");
                mv.visitLdcInsn("Statement." + methodName + " 시작");
                mv.visitMethodInsn(Opcodes.INVOKEINTERFACE, "org/slf4j/Logger", 
                                 "debug", "(Ljava/lang/String;)V", true);
            }
        }
        
        private void insertMethodEndMonitoring() {
            // 실행 시간 계산
            mv.visitMethodInsn(Opcodes.INVOKESTATIC, "java/lang/System", 
                             "currentTimeMillis", "()J", false);
            mv.visitVarInsn(Opcodes.LLOAD, 30);
            mv.visitInsn(Opcodes.LSUB);
            
            // 메트릭 수집
            mv.visitLdcInsn(methodName);
            mv.visitInsn(Opcodes.DUP2);
            mv.visitMethodInsn(Opcodes.INVOKESTATIC, 
                             "io/kubedb/monitor/agent/JDBCMethodInterceptor", 
                             "recordStatementMetric", 
                             "(Ljava/lang/String;J)V", false);
            
            mv.visitInsn(Opcodes.POP2); // 스택 정리
        }
        
        /**
         * SQL 파라미터를 받는 메서드인지 확인
         */
        private boolean hasSqlParameter() {
            return descriptor.contains("(Ljava/lang/String;)");
        }
    }
}