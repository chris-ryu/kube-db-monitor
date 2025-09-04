package io.kubedb.monitor.agent;

import net.bytebuddy.asm.Advice;
import java.lang.reflect.Method;
import java.sql.PreparedStatement;
import java.util.logging.Logger;

/**
 * HikariCP 프록시 클래스 전용 Advice
 * HikariCP가 생성하는 동적 프록시 클래스들의 SQL 실행 메서드를 인터셉트합니다.
 */
public class HikariProxyAdvice {
    
    private static final Logger logger = Logger.getLogger(HikariProxyAdvice.class.getName());
    private static MetricsCollector metricsCollector;
    
    public static void setMetricsCollector(MetricsCollector collector) {
        metricsCollector = collector;
    }
    
    /**
     * HikariCP PreparedStatement execute 메서드 진입점
     */
    @Advice.OnMethodEnter
    public static long onEnter(@Advice.This Object target,
                               @Advice.Origin Method method,
                               @Advice.AllArguments Object[] args) {
        try {
            String methodName = method.getName();
            String className = target.getClass().getName();
            
            System.out.println("🎯 [HikariProxy] 메서드 진입: " + className + "." + methodName);
            
            // SQL 추출 시도
            String sql = extractSqlFromTarget(target, method, args);
            if (sql != null) {
                System.out.println("🔍 [HikariProxy] SQL 추출 성공: " + sql.substring(0, Math.min(sql.length(), 100)) + "...");
            }
            
            return System.nanoTime();
            
        } catch (Exception e) {
            logger.warning("[HikariProxy] OnMethodEnter 오류: " + e.getMessage());
            return System.nanoTime();
        }
    }
    
    /**
     * HikariCP PreparedStatement execute 메서드 종료점
     */
    @Advice.OnMethodExit(onThrowable = Throwable.class)
    public static void onExit(@Advice.This Object target,
                              @Advice.Origin Method method,
                              @Advice.AllArguments Object[] args,
                              @Advice.Enter long startTime,
                              @Advice.Return Object result,
                              @Advice.Thrown Throwable throwable) {
        try {
            long executionTime = System.nanoTime() - startTime;
            String methodName = method.getName();
            String className = target.getClass().getName();
            
            System.out.println("🏁 [HikariProxy] 메서드 종료: " + className + "." + methodName + 
                             " (실행시간: " + (executionTime / 1_000_000) + "ms)");
            
            // SQL 추출
            String sql = extractSqlFromTarget(target, method, args);
            if (sql == null) {
                System.out.println("⚠️ [HikariProxy] SQL 추출 실패");
                return;
            }
            
            // 연결 및 스레드 정보 수집
            String connectionId = extractConnectionId(target);
            String threadName = Thread.currentThread().getName();
            
            System.out.println("📊 [HikariProxy] 메트릭 수집: " + 
                             "SQL=" + sql.substring(0, Math.min(sql.length(), 50)) + "..., " +
                             "Connection=" + connectionId + ", " +
                             "Thread=" + threadName);
            
            if (metricsCollector != null) {
                // 성공/실패에 따른 메트릭 수집
                if (throwable == null) {
                    metricsCollector.recordQuery(sql, executionTime, connectionId, threadName);
                    
                    // Long running transaction 쿼리 정보 업데이트
                    metricsCollector.updateActiveTransactionQuery(sql, connectionId, threadName, executionTime / 1_000_000);
                    
                    System.out.println("✅ [HikariProxy] 메트릭 수집 완료");
                } else {
                    System.out.println("❌ [HikariProxy] SQL 실행 실패: " + throwable.getMessage());
                }
            } else {
                System.out.println("⚠️ [HikariProxy] MetricsCollector가 null입니다");
            }
            
        } catch (Exception e) {
            logger.warning("[HikariProxy] OnMethodExit 오류: " + e.getMessage());
        }
    }
    
    /**
     * 대상 객체에서 SQL 추출
     */
    public static String extractSqlFromTarget(Object target, Method method, Object[] args) {
        try {
            // PreparedStatement의 경우 SQL이 이미 준비되어 있음
            if (target instanceof PreparedStatement) {
                // Reflection을 통해 SQL 추출 시도
                return extractSqlViaReflection(target);
            }
            
            // 메서드 파라미터에서 SQL 추출 (Statement.execute(sql) 등)
            if (args != null && args.length > 0 && args[0] instanceof String) {
                return (String) args[0];
            }
            
            // HikariCP 프록시에서 SQL 추출 시도
            return extractSqlFromHikariProxy(target);
            
        } catch (Exception e) {
            System.out.println("⚠️ [HikariProxy] SQL 추출 중 오류: " + e.getMessage());
            return null;
        }
    }
    
    /**
     * Reflection을 통한 SQL 추출
     */
    public static String extractSqlViaReflection(Object target) {
        try {
            Class<?> targetClass = target.getClass();
            
            // HikariCP 프록시에서 실제 PreparedStatement 객체 얻기
            java.lang.reflect.Field delegateField = findFieldByType(targetClass, PreparedStatement.class);
            if (delegateField != null) {
                delegateField.setAccessible(true);
                Object delegate = delegateField.get(target);
                
                if (delegate != null) {
                    // PostgreSQL PreparedStatement에서 SQL 추출
                    java.lang.reflect.Field sqlField = findFieldContaining(delegate.getClass(), "sql", "query", "preparedSql");
                    if (sqlField != null) {
                        sqlField.setAccessible(true);
                        Object sqlValue = sqlField.get(delegate);
                        if (sqlValue instanceof String) {
                            return (String) sqlValue;
                        }
                    }
                }
            }
            
            // 직접 SQL 필드 찾기 시도
            java.lang.reflect.Field sqlField = findFieldContaining(targetClass, "sql", "query", "preparedSql");
            if (sqlField != null) {
                sqlField.setAccessible(true);
                Object sqlValue = sqlField.get(target);
                if (sqlValue instanceof String) {
                    return (String) sqlValue;
                }
            }
            
        } catch (Exception e) {
            System.out.println("⚠️ [HikariProxy] Reflection SQL 추출 실패: " + e.getMessage());
        }
        
        return null;
    }
    
    /**
     * HikariCP 프록시에서 SQL 추출
     */
    public static String extractSqlFromHikariProxy(Object target) {
        try {
            // toString() 메서드에서 SQL 정보 추출 시도
            String toString = target.toString();
            if (toString.contains("sql=")) {
                int start = toString.indexOf("sql=") + 4;
                int end = toString.indexOf(",", start);
                if (end == -1) end = toString.indexOf("}", start);
                if (end == -1) end = toString.length();
                
                String sql = toString.substring(start, end).trim();
                if (sql.startsWith("\"") && sql.endsWith("\"")) {
                    sql = sql.substring(1, sql.length() - 1);
                }
                return sql;
            }
            
        } catch (Exception e) {
            System.out.println("⚠️ [HikariProxy] toString SQL 추출 실패: " + e.getMessage());
        }
        
        return null;
    }
    
    /**
     * Connection ID 추출
     */
    public static String extractConnectionId(Object target) {
        try {
            String className = target.getClass().getName();
            String objectHash = Integer.toHexString(System.identityHashCode(target));
            return className + "@" + objectHash;
        } catch (Exception e) {
            return "hikari-proxy-" + System.currentTimeMillis();
        }
    }
    
    /**
     * 특정 타입의 필드 찾기
     */
    public static java.lang.reflect.Field findFieldByType(Class<?> clazz, Class<?> fieldType) {
        Class<?> currentClass = clazz;
        while (currentClass != null && currentClass != Object.class) {
            for (java.lang.reflect.Field field : currentClass.getDeclaredFields()) {
                if (fieldType.isAssignableFrom(field.getType())) {
                    return field;
                }
            }
            currentClass = currentClass.getSuperclass();
        }
        return null;
    }
    
    /**
     * 특정 이름을 포함하는 필드 찾기
     */
    public static java.lang.reflect.Field findFieldContaining(Class<?> clazz, String... keywords) {
        Class<?> currentClass = clazz;
        while (currentClass != null && currentClass != Object.class) {
            for (java.lang.reflect.Field field : currentClass.getDeclaredFields()) {
                String fieldName = field.getName().toLowerCase();
                for (String keyword : keywords) {
                    if (fieldName.contains(keyword.toLowerCase())) {
                        return field;
                    }
                }
            }
            currentClass = currentClass.getSuperclass();
        }
        return null;
    }
}