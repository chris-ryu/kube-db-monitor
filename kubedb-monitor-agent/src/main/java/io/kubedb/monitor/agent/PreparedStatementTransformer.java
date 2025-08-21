package io.kubedb.monitor.agent;

import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * PreparedStatement 클래스 전용 변환기
 * 
 * 모니터링 대상 메서드:
 * - executeQuery(): 조회 쿼리 실행 모니터링
 * - executeUpdate(): 업데이트 쿼리 실행 모니터링  
 * - execute(): 일반 쿼리 실행 모니터링
 * - executeBatch(): 배치 쿼리 실행 모니터링
 * - setXXX(): 파라미터 바인딩 모니터링
 */
public class PreparedStatementTransformer extends ClassVisitor {
    
    private static final Logger logger = LoggerFactory.getLogger(PreparedStatementTransformer.class);
    
    private final AgentConfig config;
    private final String className;
    private final PostgreSQLCompatibilityHelper postgresqlHelper;
    
    public PreparedStatementTransformer(ClassVisitor cv, AgentConfig config, String className) {
        super(Opcodes.ASM9, cv);
        this.config = config;
        this.className = className;
        this.postgresqlHelper = null;
        logger.debug("PreparedStatementTransformer initialized for: {}", className);
    }
    
    public PreparedStatementTransformer(ClassVisitor cv, AgentConfig config, String className,
                                      PostgreSQLCompatibilityHelper postgresqlHelper) {
        super(Opcodes.ASM9, cv);
        this.config = config;
        this.className = className;
        this.postgresqlHelper = postgresqlHelper;
        logger.debug("PreparedStatementTransformer with PostgreSQL support initialized for: {}", className);
    }
    
    @Override
    public MethodVisitor visitMethod(int access, String name, String descriptor, 
                                   String signature, String[] exceptions) {
        
        MethodVisitor mv = super.visitMethod(access, name, descriptor, signature, exceptions);
        
        // PreparedStatement의 핵심 메서드들에 모니터링 추가
        if (isTargetMethod(name, descriptor)) {
            logger.debug("Instrumenting PreparedStatement method: {}.{}({})", className, name, descriptor);
            
            // PostgreSQL 호환성 모드에서 setObject 메서드 특별 처리
            if (postgresqlHelper != null && "setObject".equals(name)) {
                logger.info("PostgreSQL 호환성: setObject 메서드에 타입 에러 해결 로직 적용 - {}", className);
                return new PostgreSQLCompatibleMethodVisitor(mv, name, descriptor, config, postgresqlHelper);
            }
            
            return new PreparedStatementMethodVisitor(mv, name, descriptor, config, postgresqlHelper);
        }
        
        return mv;
    }
    
    /**
     * 모니터링 대상 메서드인지 판단
     */
    private boolean isTargetMethod(String name, String descriptor) {
        // 쿼리 실행 메서드들
        if ("executeQuery".equals(name) && descriptor.equals("()Ljava/sql/ResultSet;")) {
            return true;
        }
        if ("executeUpdate".equals(name) && descriptor.equals("()I")) {
            return true;
        }
        if ("execute".equals(name) && descriptor.equals("()Z")) {
            return true;
        }
        if ("executeBatch".equals(name) && descriptor.equals("()[I")) {
            return true;
        }
        
        // 파라미터 바인딩 메서드들 (setXXX 계열)
        if (name.startsWith("set") && name.length() > 3) {
            char c = name.charAt(3);
            if (Character.isUpperCase(c)) {
                // setString, setInt, setLong, setObject 등
                return true;
            }
        }
        
        return false;
    }
    
    /**
     * PreparedStatement 메서드 전용 MethodVisitor
     */
    private static class PreparedStatementMethodVisitor extends MethodVisitor {
        
        private final String methodName;
        private final String descriptor;
        private final AgentConfig config;
        private final PostgreSQLCompatibilityHelper postgresqlHelper;
        
        public PreparedStatementMethodVisitor(MethodVisitor mv, String methodName, 
                                            String descriptor, AgentConfig config,
                                            PostgreSQLCompatibilityHelper postgresqlHelper) {
            super(Opcodes.ASM9, mv);
            this.methodName = methodName;
            this.descriptor = descriptor;
            this.config = config;
            this.postgresqlHelper = postgresqlHelper;
        }
        
        @Override
        public void visitCode() {
            super.visitCode();
            
            // 쿼리 실행 메서드의 경우만 시작 모니터링
            if (isExecuteMethod()) {
                insertMethodStartMonitoring();
            }
        }
        
        @Override
        public void visitInsn(int opcode) {
            // return 전에 모니터링 코드 삽입
            if (opcode >= Opcodes.IRETURN && opcode <= Opcodes.RETURN) {
                if (isExecuteMethod()) {
                    insertMethodEndMonitoring();
                } else if (isParameterMethod()) {
                    insertParameterMonitoring();
                }
            }
            super.visitInsn(opcode);
        }
        
        @Override
        public void visitMaxs(int maxStack, int maxLocals) {
            // 간소화된 모니터링 코드로 최소한만 증가
            super.visitMaxs(Math.max(maxStack, 3), Math.max(maxLocals, maxLocals + 1));
        }
        
        /**
         * 쿼리 실행 메서드인지 확인
         */
        private boolean isExecuteMethod() {
            return methodName.startsWith("execute");
        }
        
        /**
         * 파라미터 설정 메서드인지 확인
         */
        private boolean isParameterMethod() {
            return methodName.startsWith("set");
        }
        
        /**
         * 메서드 시작 시점 모니터링 코드 삽입 - 간소화된 버전
         */
        private void insertMethodStartMonitoring() {
            // 매우 간단한 로깅만
            if ("DEBUG".equals(config.getLogLevel())) {
                mv.visitFieldInsn(Opcodes.GETSTATIC, "java/lang/System", "out", "Ljava/io/PrintStream;");
                mv.visitLdcInsn("[KubeDB] PreparedStatement." + methodName + " called");
                mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/io/PrintStream", "println", 
                                 "(Ljava/lang/String;)V", false);
            }
        }
        
        /**
         * 메서드 종료 시점 모니터링 코드 삽입 - 간소화된 버전
         */
        private void insertMethodEndMonitoring() {
            // 매우 간단한 완료 로깅만
            if ("DEBUG".equals(config.getLogLevel())) {
                mv.visitFieldInsn(Opcodes.GETSTATIC, "java/lang/System", "out", "Ljava/io/PrintStream;");
                mv.visitLdcInsn("[KubeDB] PreparedStatement." + methodName + " completed");
                mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/io/PrintStream", "println", 
                                 "(Ljava/lang/String;)V", false);
            }
        }
        
        /**
         * 파라미터 모니터링 코드 삽입 - 간소화된 버전
         */
        private void insertParameterMonitoring() {
            // 매우 간단한 파라미터 로깅만
            if ("DEBUG".equals(config.getLogLevel())) {
                mv.visitFieldInsn(Opcodes.GETSTATIC, "java/lang/System", "out", "Ljava/io/PrintStream;");
                mv.visitLdcInsn("[KubeDB] Parameter " + methodName + " set");
                mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/io/PrintStream", "println", 
                                 "(Ljava/lang/String;)V", false);
            }
        }
        
    }
    
    /**
     * PostgreSQL 호환성을 위한 특별한 MethodVisitor
     * setObject(index, null) 호출을 setNull(index, Types.XXX) 호출로 대체
     */
    private static class PostgreSQLCompatibleMethodVisitor extends MethodVisitor {
        
        private final String methodName;
        private final String descriptor;
        private final AgentConfig config;
        private final PostgreSQLCompatibilityHelper postgresqlHelper;
        
        public PostgreSQLCompatibleMethodVisitor(MethodVisitor mv, String methodName, 
                                               String descriptor, AgentConfig config,
                                               PostgreSQLCompatibilityHelper postgresqlHelper) {
            super(Opcodes.ASM9, mv);
            this.methodName = methodName;
            this.descriptor = descriptor;
            this.config = config;
            this.postgresqlHelper = postgresqlHelper;
        }
        
        @Override
        public void visitMethodInsn(int opcode, String owner, String name, String descriptor, boolean isInterface) {
            // setObject 메서드 호출을 가로채서 PostgreSQL 호환성 적용
            if ("setObject".equals(name) && owner.contains("PreparedStatement")) {
                logger.debug("PostgreSQL 호환성: setObject 호출 감지 - 호환성 헬퍼로 대체");
                
                // 기존 setObject 호출을 PostgreSQLCompatibilityHelper.setParameterSafely로 대체
                super.visitMethodInsn(Opcodes.INVOKEVIRTUAL, 
                                    "io/kubedb/monitor/agent/PostgreSQLCompatibilityHelper", 
                                    "setParameterSafely", 
                                    "(Ljava/sql/PreparedStatement;ILjava/lang/Object;)V", 
                                    false);
                return;
            }
            
            // 기타 메서드 호출은 그대로 전달
            super.visitMethodInsn(opcode, owner, name, descriptor, isInterface);
        }
        
        @Override
        public void visitCode() {
            super.visitCode();
            
            // 메서드 시작 시 로깅
            if ("DEBUG".equals(config.getLogLevel())) {
                mv.visitFieldInsn(Opcodes.GETSTATIC, "java/lang/System", "out", "Ljava/io/PrintStream;");
                mv.visitLdcInsn("KubeDB Monitor PostgreSQL: " + methodName + " called with compatibility mode");
                mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/io/PrintStream", "println", 
                                 "(Ljava/lang/String;)V", false);
            }
        }
    }
}