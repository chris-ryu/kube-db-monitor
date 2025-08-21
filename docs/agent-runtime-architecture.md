# KubeDB Monitor Agent 런타임 아키텍처 가이드

## 개요

KubeDB Monitor Agent는 **Zero-Code-Change 모니터링**을 제공하는 Java Agent입니다. 애플리케이션의 런타임을 교체하지 않고, 투명한 모니터링 레이어를 추가하여 데이터베이스 성능 모니터링, 호환성 개선, 메트릭 수집 기능을 제공합니다.

## 핵심 개념: 런타임 교체 vs 모니터링 레이어

### ❌ Agent가 하지 않는 일 (런타임 교체 아님)

KubeDB Monitor Agent는 다음을 **교체하거나 변경하지 않습니다**:

1. **JDBC 드라이버 교체**: PostgreSQL, MySQL 등 기존 JDBC 드라이버 그대로 사용
2. **데이터베이스 변경**: 기존 데이터베이스 시스템 그대로 사용
3. **애플리케이션 로직 변경**: 비즈니스 코드를 전혀 건드리지 않음
4. **Connection Pool 교체**: HikariCP, Tomcat JDBC 등 그대로 사용
5. **ORM Framework 교체**: Hibernate, JPA, MyBatis 등 그대로 사용

### ✅ Agent가 하는 일 (모니터링 레이어 추가)

KubeDB Monitor Agent는 다음과 같은 **투명한 모니터링 기능**을 추가합니다:

1. **JDBC 호출 Instrumentation**: 메서드 호출을 감시하고 모니터링 코드 삽입
2. **성능 메트릭 수집**: 쿼리 실행 시간, TPS, 응답시간 측정
3. **Transaction 모니터링**: 트랜잭션 라이프사이클 추적
4. **데드락 검출**: Lock 요청 패턴 분석으로 데드락 감지
5. **호환성 개선**: JDBC 드라이버별 호환성 문제 자동 해결
6. **메트릭 전송**: Control Plane으로 실시간 데이터 전송

## Agent 동작 아키텍처

### 1. JVM 레벨 통합

```
┌─────────────────────────────────────┐
│          Java Application           │
│      (수강신청 앱, 기타 앱)        │
└─────────────────────────────────────┘
                    │ JDBC 호출
┌─────────────────────────────────────┐
│         KubeDB Monitor Agent        │ ← Java Agent로 JVM에 Attach
│    (투명한 모니터링 레이어 추가)    │
└─────────────────────────────────────┘
                    │ 원본 호출 전달
┌─────────────────────────────────────┐
│        실제 JDBC 드라이버           │
│    (PostgreSQL, MySQL, Oracle)     │ ← 그대로 유지
└─────────────────────────────────────┘
                    │
┌─────────────────────────────────────┐
│           데이터베이스              │
│    (PostgreSQL, MySQL, Oracle)     │ ← 그대로 유지
└─────────────────────────────────────┘
```

### 2. Connection 프록시 패턴

```java
// Agent가 자동으로 수행하는 프록시 래핑
Connection realConnection = DriverManager.getConnection("jdbc:postgresql://...");
Connection proxiedConnection = new PostgreSQLConnectionProxy(realConnection, config);

// 애플리케이션은 여전히 표준 JDBC API 사용
PreparedStatement stmt = connection.prepareStatement(sql);  // ← 코드 변경 없음
stmt.executeQuery();  // ← Agent가 내부적으로 모니터링 수행
```

### 3. 모니터링 데이터 플로우

```
┌─────────────┐    ┌─────────────┐    ┌─────────────┐    ┌─────────────┐
│ Application │───▶│    Agent    │───▶│Control Plane│───▶│  Dashboard  │
│             │    │             │    │             │    │             │
│ JDBC 호출   │    │ 메트릭 수집 │    │ 데이터 처리 │    │ 시각화      │
│ (변경없음)  │    │ 호환성 개선 │    │ 알림 처리   │    │ 알람        │
└─────────────┘    └─────────────┘    └─────────────┘    └─────────────┘
```

## 실제 University Registration 앱에서의 동작

### Before: Agent 없이

```java
// 수강신청 앱의 원본 코드 (CourseService.java)
@Service
public class CourseService {
    public List<Course> searchCourses(Long departmentId, String searchTerm) {
        String sql = """
            SELECT c.course_id, c.course_name, c.professor 
            FROM courses c 
            WHERE (? IS NULL OR c.department_id = ?) 
              AND (? IS NULL OR LOWER(c.course_name) LIKE LOWER(?))
            """;
        
        PreparedStatement stmt = connection.prepareStatement(sql);
        stmt.setObject(1, departmentId);  // ← "Unknown Types value" 에러 발생!
        stmt.setObject(2, departmentId);
        stmt.setObject(3, searchTerm);
        stmt.setObject(4, "%" + searchTerm + "%");
        
        return stmt.executeQuery();  // ← SQLException 발생으로 앱 중단
    }
}
```

**문제점:**
- PostgreSQL "Unknown Types value" 에러로 앱 실행 불가
- 성능 모니터링 불가능
- 데드락, Long-running transaction 감지 불가능

### After: Agent 적용 후

```java
// 수강신청 앱 코드는 전혀 변경되지 않음!
@Service
public class CourseService {
    public List<Course> searchCourses(Long departmentId, String searchTerm) {
        String sql = """
            SELECT c.course_id, c.course_name, c.professor 
            FROM courses c 
            WHERE (? IS NULL OR c.department_id = ?) 
              AND (? IS NULL OR LOWER(c.course_name) LIKE LOWER(?))
            """;
        
        PreparedStatement stmt = connection.prepareStatement(sql);
        stmt.setObject(1, departmentId);  // ← Agent가 setNull(1, Types.BIGINT)로 변환
        stmt.setObject(2, departmentId);
        stmt.setObject(3, searchTerm);    // ← Agent가 setNull(3, Types.VARCHAR)로 변환
        stmt.setObject(4, "%" + searchTerm + "%");
        
        return stmt.executeQuery();      // ← Agent가 실행시간 측정, 모니터링 수행
    }
}
```

**개선사항:**
- ✅ PostgreSQL 호환성 문제 자동 해결
- ✅ 실시간 쿼리 성능 모니터링
- ✅ Transaction 추적 및 데드락 감지
- ✅ Dashboard에서 실시간 모니터링 가능
- ✅ **코드 변경 없음 (Zero-Code-Change)**

## Agent의 핵심 구성 요소

### 1. JDBC Instrumentation Layer

```java
@Component
public class JDBCInterceptor implements ClassFileTransformer {
    @Override
    public byte[] transform(ClassLoader loader, String className, ...) {
        // JDBC 관련 클래스만 타겟팅
        if (isJDBCClass(className)) {
            // 모니터링 코드 삽입 (런타임 교체 아님)
            return addMonitoringCode(classfileBuffer);
        }
        return null; // 다른 클래스는 건드리지 않음
    }
}
```

### 2. Connection Proxy Pattern

```java
public class PostgreSQLConnectionProxy implements Connection {
    private final Connection delegate;  // ← 실제 PostgreSQL Connection
    private final TransactionAwareJDBCInterceptor interceptor;
    
    @Override
    public PreparedStatement prepareStatement(String sql) throws SQLException {
        PreparedStatement realStmt = delegate.prepareStatement(sql);
        // 프록시로 래핑하여 모니터링 추가
        return new PostgreSQLPreparedStatementProxy(realStmt, sql, interceptor);
    }
    
    @Override
    public void commit() throws SQLException {
        // Transaction 모니터링
        interceptor.onTransactionCommit(delegate);
        delegate.commit();  // ← 실제 commit은 원본 Connection에서
    }
}
```

### 3. PostgreSQL 호환성 개선

```java
public class PostgreSQLCompatibilityHelper {
    public void setParameterSafely(PreparedStatement stmt, int index, Object value) {
        if (value == null) {
            // PostgreSQL "Unknown Types value" 에러 방지
            stmt.setNull(index, inferSQLType(index));  // 자동 타입 추론
        } else {
            stmt.setObject(index, value);  // 원본 그대로
        }
    }
}
```

### 4. 메트릭 수집 및 전송

```java
public class MetricsCollector {
    public void collectQueryMetrics(String sql, long executionTime) {
        DBMetrics metrics = DBMetrics.builder()
            .sql(sql)
            .executionTimeMs(executionTime)
            .timestamp(System.currentTimeMillis())
            .build();
            
        // Control Plane으로 전송
        httpClient.post("/api/metrics", metrics);
    }
}
```

## 런타임 영향 분석

### 🔍 성능 영향

| 측면 | 영향 | 설명 |
|------|------|------|
| **CPU 사용량** | +2-5% | 프록시 오버헤드 및 메트릭 수집 |
| **메모리 사용량** | +10-20MB | 프록시 객체 및 메트릭 버퍼 |
| **쿼리 응답시간** | +0.1-1ms | 프록시 호출 오버헤드 |
| **처리량(TPS)** | -1-3% | 모니터링 코드 실행 시간 |

### 📊 장점 vs 비용

**장점:**
- ✅ Zero-Code-Change 모니터링
- ✅ 실시간 성능 가시성
- ✅ 자동 데드락/Long-running transaction 감지
- ✅ JDBC 호환성 문제 자동 해결
- ✅ 프로덕션 환경에서 즉시 적용 가능

**비용:**
- 🔶 약간의 성능 오버헤드 (일반적으로 무시 가능)
- 🔶 추가 메모리 사용량 (현대 서버에서는 미미)
- 🔶 Agent 설정 및 운영 복잡성

## 배포 및 적용 방법

### 1. Java Agent 설정

```yaml
# Kubernetes Deployment
apiVersion: apps/v1
kind: Deployment
spec:
  template:
    spec:
      containers:
      - name: university-registration
        image: university-registration:latest
        env:
        - name: JAVA_OPTS
          value: "-javaagent:/app/lib/kubedb-monitor-agent.jar=config.properties"
        # 애플리케이션 코드는 전혀 변경되지 않음
```

### 2. Agent 설정

```properties
# config.properties
agent.enabled=true
postgresql.strict-compatibility=true
postgresql.fix-unknown-types-value=true
metrics.collection.enabled=true
control-plane.endpoint=http://kubedb-monitor-control-plane:8080
```

### 3. 점진적 적용 전략

```yaml
# Phase 1: 개발 환경에서 테스트
env:
- name: AGENT_ENABLED
  value: "true"
- name: AGENT_SAMPLING_RATE  
  value: "0.1"  # 10% 샘플링

# Phase 2: 스테이징 환경에서 전체 적용
env:
- name: AGENT_SAMPLING_RATE
  value: "1.0"   # 100% 샘플링

# Phase 3: 프로덕션 환경에 점진적 배포
# Canary 배포나 Blue-Green 배포 활용
```

## 모니터링 결과 확인

### 1. Dashboard에서 확인

- **실시간 TPS**: 초당 처리 쿼리 수
- **평균 응답시간**: 쿼리 실행 시간 분포
- **에러율**: SQLException 발생 비율
- **Long-running Transaction**: 임계값 초과 트랜잭션
- **데드락 이벤트**: 감지된 데드락 상황

### 2. 로그에서 확인

```log
[INFO] [KubeDB] PostgreSQL Connection 프록시 활성화 (Transaction 모니터링 포함) - PgConnection
[INFO] [KubeDB] PostgreSQL Query 완료: duration=45ms, sql=SELECT * FROM courses WHERE...
[WARN] [KubeDB] Long Running Transaction detected: transactionId=tx-abc123, duration=5200ms
[ERROR] [KubeDB] Potential deadlock detected for transaction tx-def456
```

## 문제 해결 및 트러블슈팅

### 1. Agent 비활성화 (긴급 상황)

```yaml
# Agent를 일시적으로 비활성화
env:
- name: JAVA_OPTS
  value: "-Xmx512m -Xms256m"  # Agent 설정 제거
```

### 2. Safe Mode 활성화

```yaml
# 최소한의 모니터링만 수행
env:
- name: JAVA_OPTS
  value: "-javaagent:agent.jar=safe-mode=true,sampling-rate=0.01"
```

### 3. 호환성 문제 해결

```properties
# 특정 JDBC 드라이버와 호환성 문제 시
exclude-classes=com.problematic.jdbc.Driver
safe-transformation-mode=true
avoid-autocommit-state-change=true
```

## 결론

KubeDB Monitor Agent는 **런타임을 교체하는 것이 아니라, 투명한 모니터링 레이어를 추가**하는 방식으로 동작합니다:

### 핵심 특징

1. **Non-Intrusive**: 애플리케이션 코드를 전혀 변경하지 않음
2. **Runtime Preservation**: 기존 JDBC 드라이버, 데이터베이스, ORM 그대로 사용
3. **Transparent Monitoring**: 보이지 않는 모니터링 레이어 추가
4. **Compatibility Enhancement**: JDBC 드라이버 호환성 문제 자동 해결
5. **Production Ready**: 프로덕션 환경에서 즉시 적용 가능

### 실제 효과

- **University Registration 앱**: PostgreSQL "Unknown Types value" 에러 완전 해결
- **실시간 모니터링**: Dashboard를 통한 성능 가시성 확보
- **자동 감지**: 데드락, Long-running transaction 자동 탐지
- **운영 개선**: 문제 발생 전 사전 알림 및 대응

이러한 아키텍처를 통해 KubeDB Monitor는 **Zero-Code-Change 데이터베이스 모니터링**의 진정한 가치를 제공하며, 기존 애플리케이션의 안정성을 유지하면서도 강력한 모니터링 기능을 추가할 수 있습니다.

---

**업데이트 이력:**
- 2025-08-20: 초기 문서 작성
  - Agent 런타임 아키텍처 상세 설명
  - University Registration 앱 실제 적용 사례 분석
  - Connection 프록시 패턴 동작 원리 문서화
  - 성능 영향 및 운영 가이드 제공
  - Zero-Code-Change 모니터링 개념 정립