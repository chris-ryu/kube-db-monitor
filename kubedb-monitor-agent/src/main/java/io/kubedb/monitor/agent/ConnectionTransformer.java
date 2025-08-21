package io.kubedb.monitor.agent;

import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Connection 클래스 전용 변환기
 * 
 * 모니터링 대상 메서드:
 * - getConnection(): 연결 생성 모니터링
 * - setAutoCommit(): 트랜잭션 모드 변경 모니터링
 * - commit(): 커밋 모니터링
 * - rollback(): 롤백 모니터링
 * - close(): 연결 종료 모니터링
 */
public class ConnectionTransformer extends ClassVisitor {
    
    private static final Logger logger = LoggerFactory.getLogger(ConnectionTransformer.class);
    
    private final AgentConfig config;
    private final String className;
    private final PostgreSQLCompatibilityHelper postgresqlHelper;
    
    public ConnectionTransformer(ClassVisitor cv, AgentConfig config, String className) {
        super(Opcodes.ASM9, cv);
        this.config = config;
        this.className = className;
        this.postgresqlHelper = null;
        logger.debug("ConnectionTransformer initialized for: {}", className);
    }
    
    public ConnectionTransformer(ClassVisitor cv, AgentConfig config, String className,
                               PostgreSQLCompatibilityHelper postgresqlHelper) {
        super(Opcodes.ASM9, cv);
        this.config = config;
        this.className = className;
        this.postgresqlHelper = postgresqlHelper;
        logger.debug("ConnectionTransformer with PostgreSQL support initialized for: {}", className);
    }
    
    @Override
    public MethodVisitor visitMethod(int access, String name, String descriptor, 
                                   String signature, String[] exceptions) {
        
        MethodVisitor mv = super.visitMethod(access, name, descriptor, signature, exceptions);
        
        // Connection의 핵심 메서드들에 모니터링 추가
        if (isTargetMethod(name, descriptor)) {
            logger.debug("Instrumenting Connection method: {}.{}({})", className, name, descriptor);
            return new ConnectionMethodVisitor(mv, name, descriptor, config);
        }
        
        return mv;
    }
    
    /**
     * 모니터링 대상 메서드인지 판단
     */
    private boolean isTargetMethod(String name, String descriptor) {
        switch (name) {
            case "getConnection":
                return true;
            case "setAutoCommit":
                return descriptor.equals("(Z)V");
            case "commit":
                return descriptor.equals("()V");
            case "rollback":
                return descriptor.equals("()V") || descriptor.contains("Savepoint");
            case "close":
                return descriptor.equals("()V");
            case "createStatement":
            case "prepareStatement":
            case "prepareCall":
                return true;
            default:
                return false;
        }
    }
    
    /**
     * Connection 메서드 전용 MethodVisitor
     */
    private static class ConnectionMethodVisitor extends MethodVisitor {
        
        private final String methodName;
        private final String descriptor;
        private final AgentConfig config;
        
        public ConnectionMethodVisitor(MethodVisitor mv, String methodName, 
                                     String descriptor, AgentConfig config) {
            super(Opcodes.ASM9, mv);
            this.methodName = methodName;
            this.descriptor = descriptor;
            this.config = config;
        }
        
        @Override
        public void visitCode() {
            super.visitCode();
            
            // 메서드 시작 시 모니터링 코드 삽입
            insertMethodStartMonitoring();
        }
        
        @Override
        public void visitInsn(int opcode) {
            // return 전에 모니터링 코드 삽입
            if (opcode >= Opcodes.IRETURN && opcode <= Opcodes.RETURN) {
                insertMethodEndMonitoring();
            }
            super.visitInsn(opcode);
        }
        
        /**
         * 메서드 시작 시점 모니터링 코드 삽입
         */
        private void insertMethodStartMonitoring() {
            // System.currentTimeMillis() 호출하여 시작 시간 저장
            mv.visitMethodInsn(Opcodes.INVOKESTATIC, "java/lang/System", 
                             "currentTimeMillis", "()J", false);
            mv.visitVarInsn(Opcodes.LSTORE, getStartTimeVarIndex());
            
            // 로깅 코드 삽입 (DEBUG 레벨)
            if ("DEBUG".equals(config.getLogLevel())) {
                insertLoggingCode("Connection." + methodName + " 시작");
            }
        }
        
        /**
         * 메서드 종료 시점 모니터링 코드 삽입
         */
        private void insertMethodEndMonitoring() {
            // 실행 시간 계산
            mv.visitMethodInsn(Opcodes.INVOKESTATIC, "java/lang/System", 
                             "currentTimeMillis", "()J", false);
            mv.visitVarInsn(Opcodes.LLOAD, getStartTimeVarIndex());
            mv.visitInsn(Opcodes.LSUB);
            mv.visitVarInsn(Opcodes.LSTORE, getDurationVarIndex());
            
            // 성능 메트릭 수집 코드 삽입
            insertMetricsCollection();
            
            // 로깅 코드 삽입
            if ("DEBUG".equals(config.getLogLevel())) {
                insertLoggingCode("Connection." + methodName + " 완료");
            }
        }
        
        /**
         * 로깅 코드 삽입
         */
        private void insertLoggingCode(String message) {
            // Logger.debug() 호출을 위한 코드 생성
            mv.visitFieldInsn(Opcodes.GETSTATIC, 
                            "io/kubedb/monitor/agent/ConnectionTransformer", 
                            "logger", "Lorg/slf4j/Logger;");
            mv.visitLdcInsn(message);
            mv.visitMethodInsn(Opcodes.INVOKEINTERFACE, "org/slf4j/Logger", 
                             "debug", "(Ljava/lang/String;)V", true);
        }
        
        /**
         * 메트릭 수집 코드 삽입
         */
        private void insertMetricsCollection() {
            // JDBCMethodInterceptor.recordConnectionMetric() 호출
            mv.visitLdcInsn(methodName);
            mv.visitVarInsn(Opcodes.LLOAD, getDurationVarIndex());
            mv.visitMethodInsn(Opcodes.INVOKESTATIC, 
                             "io/kubedb/monitor/agent/JDBCMethodInterceptor", 
                             "recordConnectionMetric", 
                             "(Ljava/lang/String;J)V", false);
        }
        
        /**
         * 시작 시간 변수 인덱스
         */
        private int getStartTimeVarIndex() {
            return 10; // 로컬 변수 슬롯 10번 사용
        }
        
        /**
         * 실행 시간 변수 인덱스  
         */
        private int getDurationVarIndex() {
            return 12; // 로컬 변수 슬롯 12번 사용 (long이므로 2슬롯)
        }
    }
}