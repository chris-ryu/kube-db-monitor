package io.kubedb.monitor.agent;

import org.objectweb.asm.ClassVisitor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * JDBC 클래스별 특화된 변환기를 생성하는 팩토리
 * 
 * 각 JDBC 클래스 타입에 따라 적절한 변환 로직을 적용:
 * - Connection: 트랜잭션 모니터링
 * - Statement: 쿼리 실행 모니터링  
 * - PreparedStatement: 파라미터 바인딩 + 쿼리 실행 모니터링
 * - CallableStatement: 저장 프로시저 모니터링
 * - ResultSet: 결과 집합 처리 모니터링
 */
public class JDBCTransformerFactory {
    
    private static final Logger logger = LoggerFactory.getLogger(JDBCTransformerFactory.class);
    
    /**
     * 클래스 이름에 따라 적절한 변환기를 생성
     */
    public static ClassVisitor createTransformer(String className, ClassVisitor cv, AgentConfig config) {
        logger.debug("Creating transformer for class: {}", className);
        
        // Connection 관련 클래스들
        if (isConnectionClass(className)) {
            logger.debug("Using ConnectionTransformer for: {}", className);
            return new ConnectionTransformer(cv, config, className);
        }
        
        // CallableStatement (PreparedStatement보다 먼저 확인)
        if (isCallableStatementClass(className)) {
            logger.debug("Using CallableStatementTransformer for: {}", className);
            return new CallableStatementTransformer(cv, config, className);
        }
        
        // PreparedStatement (Statement보다 먼저 확인)  
        if (isPreparedStatementClass(className)) {
            logger.debug("Using PreparedStatementTransformer for: {}", className);
            return new PreparedStatementTransformer(cv, config, className);
        }
        
        // Statement
        if (isStatementClass(className)) {
            logger.debug("Using StatementTransformer for: {}", className);
            return new StatementTransformer(cv, config, className);
        }
        
        // ResultSet
        if (isResultSetClass(className)) {
            logger.debug("Using ResultSetTransformer for: {}", className);
            return new ResultSetTransformer(cv, config, className);
        }
        
        // 기본 변환기 (기타 JDBC 관련 클래스들)
        logger.debug("Using StandardTransformationVisitor for: {}", className);
        return new StandardTransformationVisitor(org.objectweb.asm.Opcodes.ASM9, cv, config);
    }
    
    /**
     * PostgreSQL 호환성 헬퍼가 포함된 변환기를 생성
     */
    public static ClassVisitor createTransformerWithPostgreSQLHelper(
            String className, ClassVisitor cv, AgentConfig config, PostgreSQLCompatibilityHelper postgresqlHelper) {
        logger.debug("Creating PostgreSQL compatible transformer for class: {}", className);
        
        // Connection 관련 클래스들
        if (isConnectionClass(className)) {
            logger.debug("Using ConnectionTransformer with PostgreSQL support for: {}", className);
            return new ConnectionTransformer(cv, config, className, postgresqlHelper);
        }
        
        // CallableStatement (PreparedStatement보다 먼저 확인)
        if (isCallableStatementClass(className)) {
            logger.debug("Using CallableStatementTransformer with PostgreSQL support for: {}", className);
            return new CallableStatementTransformer(cv, config, className, postgresqlHelper);
        }
        
        // PreparedStatement (Statement보다 먼저 확인)  
        if (isPreparedStatementClass(className)) {
            logger.debug("Using PreparedStatementTransformer with PostgreSQL support for: {}", className);
            return new PreparedStatementTransformer(cv, config, className, postgresqlHelper);
        }
        
        // Statement
        if (isStatementClass(className)) {
            logger.debug("Using StatementTransformer with PostgreSQL support for: {}", className);
            return new StatementTransformer(cv, config, className, postgresqlHelper);
        }
        
        // ResultSet
        if (isResultSetClass(className)) {
            logger.debug("Using ResultSetTransformer with PostgreSQL support for: {}", className);
            return new ResultSetTransformer(cv, config, className, postgresqlHelper);
        }
        
        // 기본 변환기 (PostgreSQL 호환성 포함)
        logger.debug("Using StandardTransformationVisitor with PostgreSQL support for: {}", className);
        return new StandardTransformationVisitor(org.objectweb.asm.Opcodes.ASM9, cv, config, postgresqlHelper);
    }
    
    /**
     * Connection 관련 클래스인지 판단
     */
    private static boolean isConnectionClass(String className) {
        return className.equals("java/sql/Connection") ||
               className.endsWith("Connection") ||
               className.contains("Connection") && (
                   className.startsWith("org/postgresql/jdbc/") ||
                   className.startsWith("com/mysql/cj/jdbc/") ||
                   className.startsWith("oracle/jdbc/") ||
                   className.startsWith("org/h2/jdbc/")
               );
    }
    
    /**
     * Statement 관련 클래스인지 판단 (PreparedStatement, CallableStatement 제외)
     */
    private static boolean isStatementClass(String className) {
        if (isPreparedStatementClass(className) || isCallableStatementClass(className)) {
            return false;
        }
        
        return className.equals("java/sql/Statement") ||
               className.endsWith("Statement") ||
               className.contains("Statement") && (
                   className.startsWith("org/postgresql/jdbc/") ||
                   className.startsWith("com/mysql/cj/jdbc/") ||
                   className.startsWith("oracle/jdbc/") ||
                   className.startsWith("org/h2/jdbc/")
               );
    }
    
    /**
     * PreparedStatement 관련 클래스인지 판단
     */
    private static boolean isPreparedStatementClass(String className) {
        return className.equals("java/sql/PreparedStatement") ||
               className.endsWith("PreparedStatement") ||
               className.contains("PreparedStatement");
    }
    
    /**
     * CallableStatement 관련 클래스인지 판단
     */
    private static boolean isCallableStatementClass(String className) {
        return className.equals("java/sql/CallableStatement") ||
               className.endsWith("CallableStatement") ||
               className.contains("CallableStatement");
    }
    
    /**
     * ResultSet 관련 클래스인지 판단
     */
    private static boolean isResultSetClass(String className) {
        return className.equals("java/sql/ResultSet") ||
               className.endsWith("ResultSet") ||
               className.contains("ResultSet") && (
                   className.startsWith("org/postgresql/jdbc/") ||
                   className.startsWith("com/mysql/cj/jdbc/") ||
                   className.startsWith("oracle/jdbc/") ||
                   className.startsWith("org/h2/jdbc/")
               );
    }
}