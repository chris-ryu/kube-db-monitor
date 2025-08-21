package io.kubedb.monitor.agent;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.instrument.ClassFileTransformer;
import java.lang.instrument.IllegalClassFormatException;
import java.security.ProtectionDomain;

/**
 * JDBC ClassFileTransformer that intercepts JDBC calls for monitoring
 */
public class JDBCInterceptor implements ClassFileTransformer {
    private static final Logger logger = LoggerFactory.getLogger(JDBCInterceptor.class);
    
    private final AgentConfig config;
    private final PostgreSQLCompatibilityHelper postgresqlHelper;
    
    // Target classes to instrument
    private static final String[] TARGET_CLASSES = {
        "java/sql/Connection",
        "java/sql/Statement", 
        "java/sql/PreparedStatement",
        "java/sql/CallableStatement",
        // PostgreSQL JDBC 구현체들
        "org/postgresql/jdbc/PgConnection",
        "org/postgresql/jdbc/PgStatement", 
        "org/postgresql/jdbc/PgPreparedStatement",
        "org/postgresql/jdbc/PgCallableStatement"
    };
    
    public JDBCInterceptor(AgentConfig config) {
        this.config = config;
        this.postgresqlHelper = new PostgreSQLCompatibilityHelper(config);
        logger.info("JDBCInterceptor initialized with config: {}", config);
        
        // PostgreSQL 호환성 설정 로그 출력
        if (config.isPostgresqlFixUnknownTypesValue() || config.isPostgresqlStrictCompatibility()) {
            postgresqlHelper.logCompatibilitySettings();
        }
    }
    
    @Override
    public byte[] transform(
            ClassLoader loader,
            String className,
            Class<?> classBeingRedefined,
            ProtectionDomain protectionDomain,
            byte[] classfileBuffer) throws IllegalClassFormatException {
        
        // Skip if monitoring is disabled
        if (!config.isEnabled()) {
            return null;
        }
        
        // PostgreSQL 드라이버 클래스들은 절대 변환하지 않음
        if (isPostgreSQLDriverClass(className)) {
            logger.debug("📝 PostgreSQL 드라이버 클래스 변환 제외: {}", className);
            return null;
        }
        
        // Check if this is a target class
        if (!isTargetClass(className)) {
            return null;
        }
        
        try {
            logger.debug("Instrumenting class: {}", className);
            return transformClass(className, classfileBuffer);
            
        } catch (Exception e) {
            logger.error("Failed to transform class: {}", className, e);
            // Return null to avoid breaking the application
            return null;
        }
    }
    
    /**
     * PostgreSQL 드라이버 관련 클래스들을 식별하여 변환에서 제외
     * 이 클래스들을 변환하면 드라이버 로딩이 실패할 수 있음
     */
    private boolean isPostgreSQLDriverClass(String className) {
        if (className == null) {
            return false;
        }
        
        // PostgreSQL 드라이버 패키지
        if (className.startsWith("org/postgresql/")) {
            return true;
        }
        
        // JDBC 드라이버 관리 클래스들
        if (className.equals("java/sql/DriverManager") || 
            className.equals("java/sql/Driver") ||
            className.startsWith("java/sql/")) {
            return true;
        }
        
        // PostgreSQL 관련 키워드가 포함된 클래스들
        if (className.contains("postgresql") || 
            className.contains("PostgreSQL") ||
            className.contains("Postgres")) {
            return true;
        }
        
        return false;
    }
    
    private byte[] handlePostgreSQLCompatibility(String className, byte[] classfileBuffer) {
        logger.debug("PostgreSQL 호환성 모드로 클래스 처리: {}", className);
        
        // PostgreSQL PreparedStatement 클래스들은 변환하지 않음 (ASM 호환성 문제로 인해)
        if (isPreparedStatementClass(className) || isCallableStatementClass(className)) {
            logger.warn("PostgreSQL 호환성: PreparedStatement 클래스 변환 건너뜀 (ASM 호환성 문제) - {}", className);
            return null; // 변환하지 않음
        }
        
        // 기타 문제 클래스들은 변환하지 않음
        if (shouldExcludeFromTransformation(className)) {
            logger.info("PostgreSQL 호환성을 위해 변환 제외: {}", className);
            return null; // 변환하지 않음
        }
        
        // 안전 모드가 활성화된 경우 제한적 변환
        if (config.isSafeTransformationMode()) {
            return applySafeTransformationWithPostgreSQLHelper(className, classfileBuffer);
        }
        
        // 일반 변환 로직 적용
        return applyStandardTransformationWithPostgreSQLHelper(className, classfileBuffer);
    }
    
    private boolean isPreparedStatementClass(String className) {
        return className != null && 
               (className.equals("java/sql/PreparedStatement") ||
                className.equals("org/postgresql/jdbc/PgPreparedStatement") ||
                className.contains("PreparedStatement"));
    }
    
    private boolean isCallableStatementClass(String className) {
        return className != null && 
               (className.equals("java/sql/CallableStatement") ||
                className.equals("org/postgresql/jdbc/PgCallableStatement") ||
                className.contains("CallableStatement"));
    }
    
    private byte[] applyPostgreSQLSpecificTransformation(String className, byte[] classfileBuffer) {
        logger.info("PostgreSQL 타입 에러 해결을 위한 특수 변환 적용: {}", className);
        
        try {
            org.objectweb.asm.ClassReader classReader = new org.objectweb.asm.ClassReader(classfileBuffer);
            org.objectweb.asm.ClassWriter classWriter = new org.objectweb.asm.ClassWriter(
                    classReader,
                    org.objectweb.asm.ClassWriter.COMPUTE_MAXS  // COMPUTE_FRAMES 대신 COMPUTE_MAXS 사용
            );
            
            // PostgreSQL PreparedStatement 전용 변환기 사용
            PreparedStatementTransformer transformer = new PreparedStatementTransformer(
                classWriter, config, className, postgresqlHelper);
            
            classReader.accept(transformer, org.objectweb.asm.ClassReader.SKIP_FRAMES);  // 프레임 무시
            byte[] result = classWriter.toByteArray();
            
            logger.info("✅ PostgreSQL 특수 변환 성공: {}", className);
            return result;
            
        } catch (Exception e) {
            logger.warn("❌ PostgreSQL 특수 변환 실패: {}, 표준 변환으로 fallback (원인: {})", 
                       className, e.getMessage());
            
            // 실패시 표준 변환으로 fallback
            return applyStandardTransformationWithPostgreSQLHelper(className, classfileBuffer);
        }
    }
    
    private boolean shouldExcludeFromTransformation(String className) {
        // Agent 설정에 따른 제외 로직
        if (config.isExcludePreparedStatementTransformation() && 
            className.contains("PreparedStatement")) {
            return true;
        }
        
        if (config.isExcludeConnectionManagement() && 
            className.contains("Connection")) {
            return true;
        }
        
        // Spring DataSource 문제 클래스들 (PostgreSQL JDBC 구현체는 변환해야 함)
        String[] problematicClasses = {
            "org/postgresql/util/PSQLException",
            // Spring DataSource 관련 문제 클래스들
            "org/springframework/jdbc/datasource/lookup/AbstractRoutingDataSource",
            "org/springframework/jdbc/datasource/lookup/DataSourceLookup",
            "org/postgresql/core/QueryExecutor"
        };
        
        for (String problematicClass : problematicClasses) {
            if (className.equals(problematicClass)) {
                return true;
            }
        }
        
        return false;
    }
    
    private byte[] applySafeTransformation(String className, byte[] classfileBuffer) {
        logger.debug("안전 모드 변환 적용: {}", className);
        
        // 안전 모드에서는 최소한의 모니터링만 수행
        try {
            org.objectweb.asm.ClassReader classReader = new org.objectweb.asm.ClassReader(classfileBuffer);
            org.objectweb.asm.ClassWriter classWriter = new org.objectweb.asm.ClassWriter(
                    classReader, 
                    org.objectweb.asm.ClassWriter.COMPUTE_MAXS
            );
            
            // 최소한의 계측만 추가 (connection 라이프사이클만)
            SafeTransformationVisitor visitor = new SafeTransformationVisitor(
                    org.objectweb.asm.Opcodes.ASM9, 
                    classWriter,
                    config
            );
            
            classReader.accept(visitor, 0);
            return classWriter.toByteArray();
            
        } catch (Exception e) {
            logger.warn("안전 모드 변환 실패: {}, 원본 클래스 사용", className, e);
            return null;
        }
    }
    
    private byte[] applySafeTransformationWithPostgreSQLHelper(String className, byte[] classfileBuffer) {
        logger.debug("PostgreSQL 호환성이 포함된 안전 모드 변환: {}", className);
        
        try {
            org.objectweb.asm.ClassReader classReader = new org.objectweb.asm.ClassReader(classfileBuffer);
            org.objectweb.asm.ClassWriter classWriter = new org.objectweb.asm.ClassWriter(
                    classReader, 
                    org.objectweb.asm.ClassWriter.COMPUTE_MAXS
            );
            
            // PostgreSQL 호환성이 포함된 안전 변환 방문자
            SafeTransformationVisitor visitor = new SafeTransformationVisitor(
                    org.objectweb.asm.Opcodes.ASM9, 
                    classWriter,
                    config,
                    postgresqlHelper
            );
            
            classReader.accept(visitor, 0);
            byte[] result = classWriter.toByteArray();
            
            logger.debug("PostgreSQL 호환성 안전 변환 완료: {}", className);
            return result;
            
        } catch (Exception e) {
            logger.warn("PostgreSQL 호환성 안전 변환 실패: {}, 원본 클래스 사용 (원인: {})", 
                       className, e.getMessage());
            return null;
        }
    }
    
    private byte[] applyStandardTransformation(String className, byte[] classfileBuffer) {
        logger.debug("클래스별 특화 변환 적용: {}", className);
        
        try {
            org.objectweb.asm.ClassReader classReader = new org.objectweb.asm.ClassReader(classfileBuffer);
            org.objectweb.asm.ClassWriter classWriter = new org.objectweb.asm.ClassWriter(
                    classReader,
                    org.objectweb.asm.ClassWriter.COMPUTE_FRAMES
            );
            
            // 🎯 클래스별 특화된 변환기 생성 및 적용
            org.objectweb.asm.ClassVisitor transformerVisitor = 
                JDBCTransformerFactory.createTransformer(className, classWriter, config);
            
            classReader.accept(transformerVisitor, org.objectweb.asm.ClassReader.EXPAND_FRAMES);
            byte[] result = classWriter.toByteArray();
            
            logger.info("✅ 클래스별 특화 변환 성공: {}", className);
            return result;
            
        } catch (Exception e) {
            logger.warn("❌ 클래스별 특화 변환 실패: {}, 원본 클래스 사용 (원인: {})", 
                       className, e.getMessage());
            return null;
        }
    }
    
    private byte[] applyStandardTransformationWithPostgreSQLHelper(String className, byte[] classfileBuffer) {
        logger.debug("PostgreSQL 호환성이 포함된 표준 변환: {}", className);
        
        try {
            org.objectweb.asm.ClassReader classReader = new org.objectweb.asm.ClassReader(classfileBuffer);
            org.objectweb.asm.ClassWriter classWriter = new org.objectweb.asm.ClassWriter(
                    classReader,
                    org.objectweb.asm.ClassWriter.COMPUTE_FRAMES
            );
            
            // PostgreSQL 호환성이 포함된 변환기 생성
            org.objectweb.asm.ClassVisitor transformerVisitor = 
                JDBCTransformerFactory.createTransformerWithPostgreSQLHelper(
                    className, classWriter, config, postgresqlHelper);
            
            if (transformerVisitor == null) {
                // Factory에서 지원하지 않는 클래스인 경우 기본 변환기 사용
                transformerVisitor = new StandardTransformationVisitor(
                    org.objectweb.asm.Opcodes.ASM9, 
                    classWriter, 
                    config,
                    postgresqlHelper
                );
            }
            
            classReader.accept(transformerVisitor, org.objectweb.asm.ClassReader.EXPAND_FRAMES);
            byte[] result = classWriter.toByteArray();
            
            logger.info("✅ PostgreSQL 호환성 표준 변환 성공: {}", className);
            return result;
            
        } catch (Exception e) {
            logger.warn("❌ PostgreSQL 호환성 표준 변환 실패: {}, 원본 클래스 사용 (원인: {})", 
                       className, e.getMessage());
            return null;
        }
    }
    
    private boolean isTargetClass(String className) {
        if (className == null) {
            return false;
        }
        
        for (String targetClass : TARGET_CLASSES) {
            if (className.equals(targetClass)) {
                return true;
            }
        }
        
        // Also check for database driver classes
        return isDriverClass(className);
    }
    
    private boolean isDriverClass(String className) {
        // Spring DataSource 관련 클래스들 제외 (ASM 호환성 문제로 인해)
        if (className.startsWith("org/springframework/jdbc/datasource/")) {
            return false;
        }
        
        // Spring 전체 패키지 제외 (JDBC 관련만 예외적으로 허용)
        if (className.startsWith("org/springframework/") && 
            !className.contains("jdbc/core")) {
            return false;
        }
        
        // Check for common JDBC driver classes and their Statement implementations
        return className.contains("mysql") ||
               className.contains("postgresql") ||
               className.contains("oracle") ||
               className.contains("h2") ||
               className.contains("jdbc") ||
               // PostgreSQL 구체적 클래스들
               (className.startsWith("org/postgresql/jdbc/") && className.contains("Statement")) ||
               // MySQL 구체적 클래스들  
               (className.startsWith("com/mysql/cj/jdbc/") && className.contains("Statement")) ||
               // 기타 JDBC 드라이버 Statement 구현체들
               className.endsWith("PreparedStatement") ||
               className.endsWith("Statement") && className.contains("jdbc");
    }
    
    private boolean isConnectionClass(String className) {
        // Connection 관련 클래스들 - autoCommit 메서드를 가지고 있는 클래스들
        // 더 강화된 HikariCP 및 Connection Pool 클래스 제외
        
        // 1. 표준 Connection 인터페이스 및 구현체들
        if (className.equals("java/sql/Connection") ||
            className.startsWith("org/postgresql/jdbc/PgConnection") ||
            className.startsWith("com/mysql/cj/jdbc/ConnectionImpl")) {
            return true;
        }
        
        // 2. HikariCP의 모든 Connection 관련 클래스들 완전 제외
        if (className.startsWith("com/zaxxer/hikari/")) {
            return true; // HikariCP 전체 패키지 제외
        }
        
        // 3. 동적 프록시 클래스들 제외 (HikariCP가 런타임에 생성)
        if (className.contains("HikariProxy") ||
            className.contains("ProxyConnection") ||
            className.contains("$Proxy") && className.contains("Connection")) {
            return true;
        }
        
        // 4. 일반적인 Connection 포함 클래스들 (넓은 범위)
        if (className.contains("Connection") ||
            (className.contains("jdbc") && className.contains("Connection"))) {
            return true;
        }
        
        return false;
    }
    
    /**
     * JDBC 관련 클래스인지 잠재적으로 판단
     */
    private boolean isPotentialJDBCClass(String className) {
        if (className == null) return false;
        
        // Spring 내부 클래스 제외 (너무 많은 노이즈 발생)
        if (className.startsWith("org/springframework/") && 
            !className.contains("jdbc/core")) {
            return false;
        }
        
        // JVM 내부 클래스 제외
        if (className.startsWith("java/lang/") || 
            className.startsWith("java/util/") ||
            className.startsWith("sun/") ||
            className.startsWith("jdk/")) {
            return false;
        }
        
        // JDBC 관련 패턴 매칭
        return className.contains("jdbc") || 
               className.contains("Statement") || 
               className.contains("Connection") ||
               className.contains("DataSource") ||
               className.contains("ResultSet") ||
               // DB 드라이버 패키지들
               className.startsWith("org/postgresql/") ||
               className.startsWith("com/mysql/") ||
               className.startsWith("oracle/jdbc/") ||
               className.startsWith("org/h2/");
    }
    
    /**
     * 클래스에서 JDBC 관련 메서드들을 추출
     */
    private String getJDBCMethods(byte[] classfileBuffer) {
        try {
            org.objectweb.asm.ClassReader classReader = new org.objectweb.asm.ClassReader(classfileBuffer);
            JDBCMethodCollector collector = new JDBCMethodCollector();
            classReader.accept(collector, org.objectweb.asm.ClassReader.SKIP_CODE);
            return collector.getJDBCMethods();
        } catch (Exception e) {
            logger.debug("Failed to analyze class methods: {}", e.getMessage());
            return "";
        }
    }
    
    /**
     * JDBC 관련 메서드를 수집하는 ClassVisitor
     */
    private static class JDBCMethodCollector extends org.objectweb.asm.ClassVisitor {
        private final java.util.Set<String> jdbcMethods = new java.util.HashSet<>();
        
        // JDBC 핵심 메서드들
        private static final java.util.Set<String> JDBC_METHOD_NAMES = java.util.Set.of(
            "executeQuery", "executeUpdate", "execute", "executeBatch",
            "getConnection", "setAutoCommit", "commit", "rollback",
            "prepareStatement", "prepareCall", "createStatement",
            "setString", "setInt", "setLong", "setObject",
            "next", "getString", "getInt", "getLong", "getObject"
        );
        
        public JDBCMethodCollector() {
            super(org.objectweb.asm.Opcodes.ASM9);
        }
        
        @Override
        public org.objectweb.asm.MethodVisitor visitMethod(int access, String name, String descriptor, 
                                                          String signature, String[] exceptions) {
            if (JDBC_METHOD_NAMES.contains(name)) {
                jdbcMethods.add(name + descriptor);
            }
            return null; // 메서드 바디는 분석하지 않음
        }
        
        public String getJDBCMethods() {
            return String.join(", ", jdbcMethods);
        }
    }
    
    private byte[] transformClass(String className, byte[] classfileBuffer) {
        try {
            // PostgreSQL 특화 호환성 체크 - 완전한 처리
            if (isPostgreSQLDriverClass(className) && config.isPostgresqlStrictCompatibility()) {
                return handlePostgreSQLCompatibility(className, classfileBuffer);
            }
            
            // avoidAutocommitStateChange가 true이면 Connection 관련 클래스는 변환하지 않음
            if (config.isAvoidAutocommitStateChange() && isConnectionClass(className)) {
                logger.debug("avoidAutocommitStateChange=true: Connection 클래스 변환 건너뜀 - {}", className);
                return null; // 변환하지 않음
            }
            
            // 일반적인 변환 로직
            org.objectweb.asm.ClassReader classReader = new org.objectweb.asm.ClassReader(classfileBuffer);
            org.objectweb.asm.ClassWriter classWriter = new org.objectweb.asm.ClassWriter(
                    classReader, 
                    org.objectweb.asm.ClassWriter.COMPUTE_MAXS | org.objectweb.asm.ClassWriter.COMPUTE_FRAMES
            );
            
            // Safe Transformation Mode 또는 Standard Mode 선택 (PostgreSQL 호환성 포함)
            org.objectweb.asm.ClassVisitor visitor;
            if (config.isSafeTransformationMode()) {
                visitor = new SafeTransformationVisitor(
                    org.objectweb.asm.Opcodes.ASM9, 
                    classWriter, 
                    config,
                    postgresqlHelper
                );
                logger.debug("Safe Transformation Mode with PostgreSQL compatibility: {}", className);
            } else {
                visitor = new StandardTransformationVisitor(
                    org.objectweb.asm.Opcodes.ASM9, 
                    classWriter, 
                    config,
                    postgresqlHelper
                );
                logger.debug("Standard Transformation Mode with PostgreSQL compatibility: {}", className);
            }
            
            classReader.accept(visitor, org.objectweb.asm.ClassReader.EXPAND_FRAMES);
            
            byte[] transformedClass = classWriter.toByteArray();
            
            logger.info("Successfully transformed class: {}", className);
            return transformedClass;
            
        } catch (Exception e) {
            logger.error("Failed to transform class: {}", className, e);
            // Return null to indicate no transformation should be applied
            return null;
        }
    }
}