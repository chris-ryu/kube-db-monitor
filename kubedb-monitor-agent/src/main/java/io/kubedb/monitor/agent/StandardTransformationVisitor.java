package io.kubedb.monitor.agent;

import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 표준 ASM 변환 Visitor
 * 포괄적인 JDBC 모니터링을 위한 변환 수행
 * 
 * 표준 모드 특징:
 * - 모든 JDBC 메서드에 대한 모니터링
 * - 성능 메트릭 수집
 * - 상세한 쿼리 분석
 * - 트랜잭션 추적
 */
public class StandardTransformationVisitor extends ClassVisitor {
    
    private static final Logger logger = LoggerFactory.getLogger(StandardTransformationVisitor.class);
    
    private final AgentConfig config;
    private final PostgreSQLCompatibilityHelper postgresqlHelper;
    private String className;
    
    public StandardTransformationVisitor(int api, ClassVisitor classVisitor, AgentConfig config) {
        super(api, classVisitor);
        this.config = config;
        this.postgresqlHelper = null;
    }
    
    public StandardTransformationVisitor(int api, ClassVisitor classVisitor, AgentConfig config, 
                                       PostgreSQLCompatibilityHelper postgresqlHelper) {
        super(api, classVisitor);
        this.config = config;
        this.postgresqlHelper = postgresqlHelper;
    }
    
    @Override
    public void visit(int version, int access, String name, String signature, String superName, String[] interfaces) {
        this.className = name;
        logger.debug("표준 모드 변환 시작: {}", name);
        super.visit(version, access, name, signature, superName, interfaces);
    }
    
    @Override
    public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
        MethodVisitor mv = super.visitMethod(access, name, descriptor, signature, exceptions);
        
        // JDBC 모니터링이 필요한 메서드인지 확인
        if (isJdbcMonitoringTarget(name, descriptor)) {
            logger.debug("표준 모드: JDBC 모니터링 추가 - {}.{}({})", className, name, descriptor);
            return new StandardJdbcMonitoringVisitor(api, mv, className, name, descriptor, config, postgresqlHelper);
        }
        
        return mv;
    }
    
    private boolean isJdbcMonitoringTarget(String methodName, String descriptor) {
        // Connection 관련 메서드들
        if (methodName.equals("getConnection") || 
            methodName.equals("close") ||
            methodName.equals("commit") ||
            methodName.equals("rollback") ||
            methodName.equals("setAutoCommit")) {
            return true;
        }
        
        // Statement 실행 메서드들
        if (methodName.equals("execute") ||
            methodName.equals("executeQuery") ||
            methodName.equals("executeUpdate") ||
            methodName.equals("executeBatch")) {
            return true;
        }
        
        // PreparedStatement 관련 메서드들 (PostgreSQL 호환성 고려)
        if (!config.isExcludePreparedStatementTransformation()) {
            if (methodName.startsWith("set") || // setInt, setString, setNull 등
                methodName.equals("addBatch") ||
                methodName.equals("clearParameters")) {
                return true;
            }
        }
        
        return false;
    }
    
    /**
     * 표준 JDBC 모니터링을 위한 메서드 Visitor
     */
    private static class StandardJdbcMonitoringVisitor extends MethodVisitor {
        
        private final String className;
        private final String methodName;
        private final String descriptor;
        private final AgentConfig config;
        private final PostgreSQLCompatibilityHelper postgresqlHelper;
        
        public StandardJdbcMonitoringVisitor(int api, MethodVisitor methodVisitor, 
                                           String className, String methodName, 
                                           String descriptor, AgentConfig config,
                                           PostgreSQLCompatibilityHelper postgresqlHelper) {
            super(api, methodVisitor);
            this.className = className;
            this.methodName = methodName;
            this.descriptor = descriptor;
            this.config = config;
            this.postgresqlHelper = postgresqlHelper;
        }
        
        @Override
        public void visitCode() {
            super.visitCode();
            
            // 메서드 시작 시 모니터링 시작
            insertMonitoringStart();
        }
        
        @Override
        public void visitInsn(int opcode) {
            // Return 명령어 전에 모니터링 종료
            if (opcode >= Opcodes.IRETURN && opcode <= Opcodes.RETURN) {
                insertMonitoringEnd();
            }
            
            super.visitInsn(opcode);
        }
        
        @Override
        public void visitMaxs(int maxStack, int maxLocals) {
            // 스택 크기 조정 (모니터링 코드로 인한 증가 고려)
            super.visitMaxs(maxStack + 4, maxLocals + 2);
        }
        
        private void insertMonitoringStart() {
            // 시작 시간 기록
            super.visitMethodInsn(Opcodes.INVOKESTATIC, "java/lang/System", "nanoTime", 
                                 "()J", false);
            super.visitVarInsn(Opcodes.LSTORE, getStartTimeLocalVar());
            
            // 메트릭 수집 시작
            if (shouldCollectDetailedMetrics()) {
                insertDetailedMetricsStart();
            }
        }
        
        private void insertMonitoringEnd() {
            // 종료 시간 기록
            super.visitMethodInsn(Opcodes.INVOKESTATIC, "java/lang/System", "nanoTime", 
                                 "()J", false);
            
            // 시작 시간 로드
            super.visitVarInsn(Opcodes.LLOAD, getStartTimeLocalVar());
            
            // 실행 시간 계산 (endTime - startTime)
            super.visitInsn(Opcodes.LSUB);
            
            // 메트릭 수집
            insertMetricsCollection();
        }
        
        private void insertDetailedMetricsStart() {
            // 상세한 메트릭 수집 (쿼리 내용, 파라미터 등)
            // PostgreSQL 호환성 확인
            if (postgresqlHelper != null && isPostgreSQLRelated()) {
                super.visitFieldInsn(Opcodes.GETSTATIC, "java/lang/System", "out", "Ljava/io/PrintStream;");
                super.visitLdcInsn("KubeDB Monitor [PostgreSQL Compatible]: " + className + "." + methodName + " started");
                super.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/io/PrintStream", "println", 
                                     "(Ljava/lang/String;)V", false);
            } else {
                // 일반 모니터링
                super.visitFieldInsn(Opcodes.GETSTATIC, "java/lang/System", "out", "Ljava/io/PrintStream;");
                super.visitLdcInsn("KubeDB Monitor: " + className + "." + methodName + " started");
                super.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/io/PrintStream", "println", 
                                     "(Ljava/lang/String;)V", false);
            }
        }
        
        private boolean isPostgreSQLRelated() {
            return className != null && 
                   (className.contains("postgresql") || className.contains("PreparedStatement"));
        }
        
        private void insertMetricsCollection() {
            // 실행 시간을 스택에서 가져와서 메트릭으로 전송
            // 실제로는 MetricsCollector 호출
            
            // 임시로 콘솔 출력
            super.visitInsn(Opcodes.DUP2); // 실행 시간 복사
            
            super.visitFieldInsn(Opcodes.GETSTATIC, "java/lang/System", "out", "Ljava/io/PrintStream;");
            super.visitInsn(Opcodes.DUP_X2);
            super.visitInsn(Opcodes.POP);
            
            super.visitLdcInsn("KubeDB Monitor: " + className + "." + methodName + " took ");
            super.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/io/PrintStream", "print", 
                                 "(Ljava/lang/String;)V", false);
            
            super.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/io/PrintStream", "print", 
                                 "(J)V", false);
            
            super.visitLdcInsn(" ns");
            super.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/io/PrintStream", "println", 
                                 "(Ljava/lang/String;)V", false);
            
            super.visitInsn(Opcodes.POP2); // 실행 시간 제거
        }
        
        private boolean shouldCollectDetailedMetrics() {
            // 샘플링 레이트와 설정에 따라 결정
            double samplingRate = config.getSamplingRate();
            
            if (samplingRate >= 1.0) {
                return true;
            }
            
            return Math.random() < samplingRate;
        }
        
        private int getStartTimeLocalVar() {
            // 로컬 변수 슬롯 계산 (실제로는 더 정교한 계산 필요)
            return 10; // 임시 값
        }
    }
}