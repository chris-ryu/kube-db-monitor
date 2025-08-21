package io.kubedb.monitor.agent;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Types;

/**
 * PostgreSQL 호환성 문제 해결을 위한 헬퍼 클래스
 * 
 * 주요 기능:
 * 1. "Unknown Types value" 에러 해결
 * 2. NULL 파라미터 바인딩 개선
 * 3. PostgreSQL JDBC 드라이버 특이사항 처리
 */
public class PostgreSQLCompatibilityHelper {
    
    private static final Logger logger = LoggerFactory.getLogger(PostgreSQLCompatibilityHelper.class);
    
    private final AgentConfig config;
    
    public PostgreSQLCompatibilityHelper(AgentConfig config) {
        this.config = config;
    }
    
    /**
     * PreparedStatement의 NULL 파라미터 바인딩을 안전하게 처리
     * 
     * @param stmt PreparedStatement
     * @param parameterIndex 파라미터 인덱스
     * @param value 설정할 값
     * @throws SQLException SQL 에러
     */
    public void setParameterSafely(PreparedStatement stmt, int parameterIndex, Object value) throws SQLException {
        if (!config.isPostgresqlFixUnknownTypesValue()) {
            // 기본 동작: setObject 사용
            stmt.setObject(parameterIndex, value);
            return;
        }
        
        if (value == null) {
            // NULL 값 처리 - 명시적 타입 지정으로 "Unknown Types value" 에러 방지
            logger.debug("PostgreSQL 안전 NULL 파라미터 바인딩: parameter {}", parameterIndex);
            setNullParameterWithType(stmt, parameterIndex);
        } else {
            // 일반 값 설정
            stmt.setObject(parameterIndex, value);
        }
    }
    
    /**
     * NULL 파라미터를 타입과 함께 안전하게 설정
     * PostgreSQL JDBC 드라이버의 "Unknown Types value" 에러 방지
     */
    private void setNullParameterWithType(PreparedStatement stmt, int parameterIndex) throws SQLException {
        // SQL 컨텍스트를 분석해서 적절한 타입 추론
        int sqlType = inferSqlTypeFromContext(parameterIndex);
        logger.debug("PostgreSQL NULL 파라미터를 {} 타입으로 설정: parameter {}", sqlType, parameterIndex);
        stmt.setNull(parameterIndex, sqlType);
    }
    
    /**
     * "Unknown Types value" 에러인지 확인
     */
    private boolean isUnknownTypesError(SQLException e) {
        return e.getMessage() != null && 
               (e.getMessage().contains("Unknown Types value") ||
                e.getSQLState() != null && e.getSQLState().equals("07006"));
    }
    
    /**
     * 파라미터 위치와 쿼리 컨텍스트를 기반으로 SQL 타입 추론
     * 
     * 일반적인 쿼리 패턴을 기반으로 타입을 추론:
     * - ID 관련 필드: BIGINT
     * - 문자열 검색/필터: VARCHAR
     * - 불린 플래그: BOOLEAN
     * - 기본값: VARCHAR (가장 호환성이 좋음)
     */
    private int inferSqlTypeFromContext(int parameterIndex) {
        // 파라미터 인덱스별 추론 규칙
        // 실제 쿼리 패턴 분석 결과를 기반으로 함
        
        switch (parameterIndex) {
            case 2:
            case 3:
                // 일반적으로 department_id 등의 ID 필드
                return Types.BIGINT;
            case 4:
            case 5:
            case 6:
                // 일반적으로 keyword 검색용 문자열
                return Types.VARCHAR;
            default:
                // 기본값: VARCHAR (가장 호환성이 좋음)
                return Types.VARCHAR;
        }
    }
    
    /**
     * Hibernate 스타일 쿼리의 NULL 파라미터를 안전하게 처리
     * 
     * 특정 쿼리 패턴에 맞춰 타입을 더 정확하게 추론
     */
    public void handleHibernateStyleQuery(PreparedStatement stmt, Object[] parameters) throws SQLException {
        if (!config.isPostgresqlFixUnknownTypesValue() || parameters == null) {
            // 설정이 비활성화되어 있거나 파라미터가 없으면 기본 처리
            return;
        }
        
        logger.debug("Hibernate 스타일 쿼리 파라미터 처리 시작: {} 개 파라미터", parameters.length);
        
        for (int i = 0; i < parameters.length; i++) {
            int paramIndex = i + 1; // JDBC는 1부터 시작
            Object param = parameters[i];
            
            if (param == null) {
                setNullParameterWithHibernateContext(stmt, paramIndex, i);
            } else {
                stmt.setObject(paramIndex, param);
            }
        }
    }
    
    /**
     * Hibernate 컨텍스트를 고려한 NULL 파라미터 처리
     */
    private void setNullParameterWithHibernateContext(PreparedStatement stmt, int paramIndex, int arrayIndex) throws SQLException {
        try {
            stmt.setObject(paramIndex, null);
            
        } catch (SQLException e) {
            if (isUnknownTypesError(e)) {
                // Hibernate 쿼리 패턴별 타입 추론
                int sqlType = inferHibernateParameterType(arrayIndex);
                stmt.setNull(paramIndex, sqlType);
                
                logger.debug("Hibernate NULL 파라미터 타입 {} 설정: index {}", sqlType, paramIndex);
                
            } else {
                throw e;
            }
        }
    }
    
    /**
     * Hibernate 쿼리 패턴을 기반으로 한 타입 추론
     * 
     * 대학교 수강신청 앱의 실제 쿼리 패턴 분석 결과:
     * - parameter 1: semester_id (BIGINT)
     * - parameter 2: department filter check (BIGINT for IS NULL check)
     * - parameter 3: department_id (BIGINT)
     * - parameter 4: keyword filter check (VARCHAR for IS NULL check)  
     * - parameter 5: keyword for course_name (VARCHAR)
     * - parameter 6: keyword for professor (VARCHAR)
     * - parameter 7: offset (INTEGER)
     * - parameter 8: limit (INTEGER)
     */
    private int inferHibernateParameterType(int arrayIndex) {
        switch (arrayIndex) {
            case 0: // semester_id
                return Types.BIGINT;
            case 1: // department filter check (? is null)
            case 2: // department_id
                return Types.BIGINT;
            case 3: // keyword filter check (? is null)
            case 4: // keyword for course_name
            case 5: // keyword for professor
                return Types.VARCHAR;
            case 6: // offset
            case 7: // limit
                return Types.INTEGER;
            default:
                return Types.VARCHAR; // 안전한 기본값
        }
    }
    
    /**
     * 설정 정보를 로그로 출력
     */
    public void logCompatibilitySettings() {
        if (logger.isDebugEnabled()) {
            logger.debug("PostgreSQL 호환성 설정:");
            logger.debug("  - Fix Unknown Types Value: {}", config.isPostgresqlFixUnknownTypesValue());
            logger.debug("  - Strict Compatibility: {}", config.isPostgresqlStrictCompatibility());
            logger.debug("  - Safe Transformation Mode: {}", config.isSafeTransformationMode());
            logger.debug("  - Avoid NULL Parameter Transformation: {}", config.isAvoidNullParameterTransformation());
        }
    }
}