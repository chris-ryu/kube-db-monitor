package io.kubedb.monitor.agent;

import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 안전 모드 ASM 변환 Visitor
 * PostgreSQL JDBC 호환성 문제를 해결하기 위한 최소한의 변환만 수행
 * 
 * 안전 모드 특징:
 * - PreparedStatement 메서드는 변환하지 않음
 * - Connection의 commit/rollback/autoCommit 메서드는 건드리지 않음
 * - 단순한 Connection 라이프사이클 모니터링만 수행
 */
public class SafeTransformationVisitor extends ClassVisitor {
    
    private static final Logger logger = LoggerFactory.getLogger(SafeTransformationVisitor.class);
    
    private final AgentConfig config;
    private final PostgreSQLCompatibilityHelper postgresqlHelper;
    private String className;
    
    public SafeTransformationVisitor(int api, ClassVisitor classVisitor, AgentConfig config) {
        super(api, classVisitor);
        this.config = config;
        this.postgresqlHelper = null;
    }
    
    public SafeTransformationVisitor(int api, ClassVisitor classVisitor, AgentConfig config,
                                   PostgreSQLCompatibilityHelper postgresqlHelper) {
        super(api, classVisitor);
        this.config = config;
        this.postgresqlHelper = postgresqlHelper;
    }
    
    @Override
    public void visit(int version, int access, String name, String signature, String superName, String[] interfaces) {
        this.className = name;
        logger.debug("안전 모드 변환 시작: {}", name);
        super.visit(version, access, name, signature, superName, interfaces);
    }
    
    @Override
    public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
        MethodVisitor mv = super.visitMethod(access, name, descriptor, signature, exceptions);
        
        // PostgreSQL 호환성을 위해 특정 메서드들은 변환하지 않음
        if (shouldSkipMethod(name, descriptor)) {
            logger.debug("안전 모드: 메서드 변환 건너뜀 - {}.{}({})", className, name, descriptor);
            return mv;
        }
        
        // Connection 생성/해제만 모니터링
        if (isConnectionLifecycleMethod(name)) {
            logger.debug("안전 모드: Connection 라이프사이클 모니터링 추가 - {}.{}", className, name);
            return new SafeConnectionMonitoringVisitor(api, mv, className, name, config, postgresqlHelper);
        }
        
        return mv;
    }
    
    private boolean shouldSkipMethod(String methodName, String descriptor) {
        // PostgreSQL PreparedStatement 문제 메서드들 제외
        if (config.isExcludePreparedStatementTransformation()) {
            if (methodName.equals("setNull") || 
                methodName.equals("setObject") ||
                methodName.equals("setString") ||
                methodName.equals("setInt") ||
                methodName.equals("executeQuery") ||
                methodName.equals("executeUpdate") ||
                methodName.equals("executeBatch")) {
                return true;
            }
        }
        
        // PostgreSQL Connection autoCommit 관련 메서드들 제외 - 더 강화된 체크
        if (config.isAvoidAutocommitStateChange()) {
            // autoCommit 관련 모든 메서드를 완전히 제외
            if (methodName.equals("commit") ||
                methodName.equals("rollback") ||
                methodName.equals("setAutoCommit") ||
                methodName.equals("getAutoCommit") ||
                // 트랜잭션 관련 메서드들도 제외
                methodName.equals("beginTransaction") ||
                methodName.equals("endTransaction") ||
                methodName.equals("commitTransaction") ||
                methodName.equals("rollbackTransaction") ||
                // Spring 관련 트랜잭션 메서드들
                methodName.contains("Transaction") ||
                methodName.contains("Commit") ||
                methodName.contains("Rollback") ||
                methodName.contains("AutoCommit")) {
                
                logger.debug("avoidAutocommitStateChange=true: 메서드 변환 건너뜀 - {}", methodName);
                return true;
            }
        }
        
        // 트랜잭션 경계 메서드들 제외
        if (config.isPreserveTransactionBoundaries()) {
            if (methodName.equals("begin") ||
                methodName.equals("start") ||
                methodName.equals("end") ||
                methodName.contains("Transaction")) {
                return true;
            }
        }
        
        return false;
    }
    
    private boolean isConnectionLifecycleMethod(String methodName) {
        return methodName.equals("getConnection") ||
               methodName.equals("close") ||
               methodName.equals("<init>") ||
               methodName.equals("connect");
    }
    
    /**
     * 안전한 Connection 모니터링을 위한 메서드 Visitor
     */
    private static class SafeConnectionMonitoringVisitor extends MethodVisitor {
        
        private final String className;
        private final String methodName;
        private final AgentConfig config;
        private final PostgreSQLCompatibilityHelper postgresqlHelper;
        
        public SafeConnectionMonitoringVisitor(int api, MethodVisitor methodVisitor, 
                                              String className, String methodName, 
                                              AgentConfig config,
                                              PostgreSQLCompatibilityHelper postgresqlHelper) {
            super(api, methodVisitor);
            this.className = className;
            this.methodName = methodName;
            this.config = config;
            this.postgresqlHelper = postgresqlHelper;
        }
        
        @Override
        public void visitCode() {
            super.visitCode();
            
            // 메서드 시작 시 간단한 로깅만 추가 (성능에 미치는 영향 최소화)
            if ("DEBUG".equals(config.getLogLevel())) {
                insertSimpleLogging();
            }
        }
        
        @Override
        public void visitInsn(int opcode) {
            // Return 명령어 전에 Connection 모니터링 정보 수집
            if (opcode >= Opcodes.IRETURN && opcode <= Opcodes.RETURN) {
                if (shouldCollectMetrics()) {
                    insertMetricsCollection();
                }
            }
            
            super.visitInsn(opcode);
        }
        
        private void insertSimpleLogging() {
            // 간단한 로그만 삽입 (성능 오버헤드 최소화)
            // getstatic System.out
            super.visitFieldInsn(Opcodes.GETSTATIC, "java/lang/System", "out", "Ljava/io/PrintStream;");
            
            // PostgreSQL 호환성 여부에 따라 메시지 분류
            String message;
            if (postgresqlHelper != null && isPostgreSQLMethod()) {
                message = "KubeDB Monitor [Safe+PostgreSQL]: " + className + "." + methodName + " called";
            } else {
                message = "KubeDB Monitor [Safe]: " + className + "." + methodName + " called";
            }
            
            super.visitLdcInsn(message);
            
            // println 호출
            super.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/io/PrintStream", "println", 
                                 "(Ljava/lang/String;)V", false);
        }
        
        private boolean isPostgreSQLMethod() {
            return className != null && 
                   (className.contains("postgresql") || className.contains("Connection"));
        }
        
        private boolean shouldCollectMetrics() {
            // 샘플링 레이트에 따라 메트릭 수집 여부 결정
            double samplingRate = config.getSamplingRate();
            return samplingRate >= 1.0 || Math.random() < samplingRate;
        }
        
        private void insertMetricsCollection() {
            // 매우 간단한 메트릭 수집만 수행
            // 실제로는 MetricsCollector를 통해 수집하지만, 안전 모드에서는 최소화
            
            // 현재 시간 기록
            super.visitMethodInsn(Opcodes.INVOKESTATIC, "java/lang/System", "currentTimeMillis", 
                                 "()J", false);
            super.visitInsn(Opcodes.POP2); // 결과값 제거 (실제로는 저장해야 함)
        }
    }
}