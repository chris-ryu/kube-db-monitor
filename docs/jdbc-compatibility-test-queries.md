# JDBC 호환성 테스트 쿼리 가이드

## 개요

이 문서는 KubeDB Monitor Agent의 JDBC 호환성을 테스트하기 위한 표준화된 쿼리 세트를 제공합니다. PostgreSQL을 기준으로 작성되었지만, 다른 데이터베이스로 확장할 때 재사용할 수 있도록 구조화되어 있습니다.

## 테스트 카테고리별 쿼리

### 1. 기본 연결 및 상태 확인 쿼리

#### 1.1 연결 상태 확인
```sql
-- PostgreSQL
SELECT version();

-- MySQL
SELECT version();

-- Oracle
SELECT * FROM v$version WHERE ROWNUM = 1;

-- SQL Server
SELECT @@version;
```

#### 1.2 현재 데이터베이스 정보
```sql
-- PostgreSQL
SELECT current_database(), current_user, current_schema();

-- MySQL  
SELECT database(), user(), schema();

-- Oracle
SELECT sys_context('USERENV', 'DB_NAME') as db_name,
       sys_context('USERENV', 'SESSION_USER') as user_name,
       sys_context('USERENV', 'CURRENT_SCHEMA') as schema_name
FROM dual;

-- SQL Server
SELECT db_name() as database_name, 
       user_name() as user_name,
       schema_name() as schema_name;
```

### 2. PreparedStatement 호환성 테스트 쿼리

#### 2.1 NULL 파라미터 바인딩 (핵심 문제 쿼리)
```sql
-- PostgreSQL - Agent에서 "Unknown Types value" 에러 발생
SELECT course_id, course_name FROM courses 
WHERE semester_id = ? AND is_active = true 
  AND (? IS NULL OR department_id = ?) 
  AND (? IS NULL OR LOWER(course_name) LIKE LOWER(CONCAT('%', ?, '%')));

-- MySQL - 유사한 패턴
SELECT course_id, course_name FROM courses 
WHERE semester_id = ? AND is_active = 1
  AND (? IS NULL OR department_id = ?) 
  AND (? IS NULL OR LOWER(course_name) LIKE LOWER(CONCAT('%', ?, '%')));

-- Oracle - 유사한 패턴 (NVL 함수 사용)
SELECT course_id, course_name FROM courses 
WHERE semester_id = ? AND is_active = 1
  AND (? IS NULL OR department_id = ?) 
  AND (? IS NULL OR LOWER(course_name) LIKE LOWER('%' || NVL(?, '') || '%'));

-- SQL Server - 유사한 패턴 (ISNULL 함수 사용)
SELECT course_id, course_name FROM courses 
WHERE semester_id = ? AND is_active = 1
  AND (? IS NULL OR department_id = ?) 
  AND (? IS NULL OR LOWER(course_name) LIKE LOWER('%' + ISNULL(?, '') + '%'));
```

**파라미터 바인딩 패턴:**
- Parameter 1: `setInt(1, 1)` - semester_id
- Parameter 2: `setNull(2, Types.INTEGER)` - department_id NULL 체크용
- Parameter 3: `setNull(3, Types.INTEGER)` - department_id 실제 값
- Parameter 4: `setNull(4, Types.VARCHAR)` - query string NULL 체크용  
- Parameter 5: `setNull(5, Types.VARCHAR)` - query string 실제 값

#### 2.2 다양한 데이터 타입 테스트
```sql
-- 모든 DB 공통 (리터럴 반환 쿼리)
SELECT ?, ?, ?, ?, ?, ?, ? as test_query;
```

**파라미터 바인딩 세트:**
- `setString(1, "test_string")`
- `setInt(2, 12345)`
- `setLong(3, 9876543210L)`
- `setDouble(4, 3.14159)`
- `setBoolean(5, true)`
- `setTimestamp(6, new Timestamp(현재시간))`
- `setNull(7, Types.VARCHAR)`

#### 2.3 배치 처리 테스트 쿼리
```sql
-- 공통 패턴 (테이블 구조에 맞게 조정)
INSERT INTO test_batch_table (id, name, value) VALUES (?, ?, ?);
```

### 3. 트랜잭션 경계 처리 테스트 쿼리

#### 3.1 AutoCommit 충돌 테스트 (PostgreSQL 문제)
```sql
-- PostgreSQL에서 "Cannot commit when autoCommit is enabled" 에러 발생
-- 트랜잭션 내에서 실행할 쿼리
SELECT COUNT(*) FROM courses WHERE is_active = true;

-- 이후 conn.commit() 호출 시 에러 발생
```

#### 3.2 롤백 충돌 테스트
```sql  
-- 에러 상황 시뮬레이션 쿼리 (존재하지 않는 테이블)
SELECT * FROM non_existent_table;

-- 이후 conn.rollback() 호출 시 "Unable to rollback against JDBC Connection" 에러
```

#### 3.3 Savepoint 테스트
```sql
-- 모든 DB 공통
SELECT COUNT(*) as row_count FROM any_existing_table;

-- Savepoint 생성 후 롤백 테스트
```

### 4. 복잡한 쿼리 패턴

#### 4.1 복잡한 JOIN 쿼리
```sql
-- PostgreSQL 기준 (수강신청 시스템)
SELECT s.student_id, s.name, c.course_name, d.department_name
FROM students s 
LEFT JOIN enrollments e ON s.student_id = e.student_id
LEFT JOIN courses c ON e.course_id = c.course_id
LEFT JOIN departments d ON s.department_id = d.department_id
WHERE s.grade = ? AND (? IS NULL OR d.department_id = ?)
ORDER BY s.student_id
LIMIT 10;

-- MySQL - 동일한 구조
-- Oracle - ROWNUM 사용
SELECT * FROM (
  SELECT s.student_id, s.name, c.course_name, d.department_name
  FROM students s 
  LEFT JOIN enrollments e ON s.student_id = e.student_id
  LEFT JOIN courses c ON e.course_id = c.course_id
  LEFT JOIN departments d ON s.department_id = d.department_id
  WHERE s.grade = ? AND (? IS NULL OR d.department_id = ?)
  ORDER BY s.student_id
) WHERE ROWNUM <= 10;

-- SQL Server - TOP 사용
SELECT TOP 10 s.student_id, s.name, c.course_name, d.department_name
FROM students s 
LEFT JOIN enrollments e ON s.student_id = e.student_id
LEFT JOIN courses c ON e.course_id = c.course_id
LEFT JOIN departments d ON s.department_id = d.department_id
WHERE s.grade = ? AND (? IS NULL OR d.department_id = ?)
ORDER BY s.student_id;
```

#### 4.2 서브쿼리 패턴
```sql
-- PostgreSQL 기준
SELECT c.course_name, c.capacity, c.enrolled_count,
       (SELECT COUNT(*) FROM cart ct WHERE ct.course_id = c.course_id) as cart_count
FROM courses c
WHERE c.department_id IN (
    SELECT d.department_id 
    FROM departments d 
    WHERE d.college = ?
)
AND c.enrolled_count < c.capacity
ORDER BY c.enrolled_count DESC
LIMIT 5;

-- 다른 DB들도 거의 동일하나 LIMIT 구문만 차이
```

#### 4.3 윈도우 함수 테스트 (PostgreSQL 9.0+, MySQL 8.0+, Oracle 11g+, SQL Server 2005+)
```sql
-- PostgreSQL/MySQL 8.0+
SELECT 
  student_id, 
  name, 
  grade,
  ROW_NUMBER() OVER (PARTITION BY grade ORDER BY student_id) as rn,
  COUNT(*) OVER (PARTITION BY grade) as grade_count
FROM students 
WHERE department_id = ?
ORDER BY grade, student_id;

-- Oracle/SQL Server - 거의 동일
```

### 5. 에러 상황 처리 테스트 쿼리

#### 5.1 문법 오류 테스트
```sql
-- 의도적 문법 오류 (모든 DB 공통)
SELECT * FROMM courses;  -- FROM에 오타
SELECTT * FROM courses;  -- SELECT에 오타
```

#### 5.2 타임아웃 테스트 쿼리
```sql
-- PostgreSQL
SELECT pg_sleep(5);  -- 5초 대기

-- MySQL  
SELECT SLEEP(5);     -- 5초 대기

-- Oracle
BEGIN 
  DBMS_LOCK.SLEEP(5); -- 5초 대기
END;

-- SQL Server
WAITFOR DELAY '00:00:05'; -- 5초 대기
```

#### 5.3 제약조건 위반 테스트
```sql
-- 기본키 중복 삽입 시도 (모든 DB 공통 패턴)
INSERT INTO students (student_id, name, department_id) 
VALUES (?, ?, ?);  -- 이미 존재하는 student_id로 실행
```

### 6. DB별 특화 테스트 쿼리

#### 6.1 PostgreSQL 특화
```sql
-- JSONB 타입 테스트
SELECT jsonb_build_object('key', 'value') as json_test;

-- ARRAY 타입 테스트  
SELECT ARRAY[1,2,3,4] as array_test;

-- LISTEN/NOTIFY 테스트
LISTEN test_channel;
NOTIFY test_channel, 'test message';
```

#### 6.2 MySQL 특화
```sql
-- JSON 타입 테스트 (MySQL 5.7+)
SELECT JSON_OBJECT('key', 'value') as json_test;

-- 시간대 처리 테스트
SELECT CONVERT_TZ(NOW(), 'UTC', 'Asia/Seoul') as tz_test;

-- 엔진별 테스트 (InnoDB vs MyISAM)
SHOW TABLE STATUS LIKE 'courses';
```

#### 6.3 Oracle 특화
```sql
-- CURSOR 테스트
DECLARE
  CURSOR c1 IS SELECT * FROM courses;
BEGIN
  FOR rec IN c1 LOOP
    NULL;
  END LOOP;
END;

-- CLOB/BLOB 테스트
SELECT EMPTY_CLOB() as clob_test FROM dual;
```

#### 6.4 SQL Server 특화
```sql
-- T-SQL 구문 테스트
DECLARE @var INT = 1;
SELECT @var as variable_test;

-- 임시 테이블 테스트
CREATE TABLE #temp_table (id INT, name VARCHAR(50));
INSERT INTO #temp_table VALUES (1, 'test');
SELECT * FROM #temp_table;
DROP TABLE #temp_table;
```

## 테스트 실행 가이드라인

### 테스트 순서
1. **기본 연결 테스트** - Agent 없이 실행하여 기준선 확립
2. **Agent 적용 테스트** - 동일한 쿼리를 Agent와 함께 실행
3. **비교 분석** - 결과, 성능, 에러 패턴 비교
4. **호환성 개선** - 발견된 문제점에 대한 Agent 설정 조정
5. **회귀 테스트** - 개선 후 전체 테스트 재실행

### 성능 측정 포인트
- **연결 시간**: DataSource.getConnection() 소요 시간
- **쿼리 실행 시간**: PreparedStatement.executeQuery() 소요 시간
- **메모리 사용량**: Agent 적용 전후 메모리 사용 패턴
- **CPU 오버헤드**: ASM 변환으로 인한 CPU 추가 사용량

### 에러 패턴 분석
- **Agent 특화 에러**: ASM 변환 과정에서 발생하는 에러
- **JDBC 드라이버 호환성**: 각 DB별 드라이버 특성으로 인한 에러
- **트랜잭션 관리**: autoCommit, 명시적 트랜잭션 관련 에러
- **타입 호환성**: NULL 처리, 데이터 타입 변환 관련 에러

## 확장 가이드

### 새로운 DB 추가 시 체크리스트
1. **기본 연결 쿼리 작성** - version(), current_database() 등
2. **데이터 타입 매핑** - 각 DB의 고유 타입들
3. **문법 차이 대응** - LIMIT vs TOP vs ROWNUM
4. **특화 기능 쿼리** - DB별 고유 기능들
5. **알려진 문제 사례** - 각 DB별 일반적인 호환성 이슈

### 테스트 자동화 구조
```java
// 테스트 인터페이스 예시
public interface DatabaseCompatibilityTest {
    void testBasicConnection();
    void testNullParameterBinding();
    void testTransactionHandling();
    void testComplexQueries();
    void testErrorHandling();
    void testDatabaseSpecificFeatures();
}

// DB별 구현체
public class PostgreSQLCompatibilityTest implements DatabaseCompatibilityTest {
    // PostgreSQL 특화 구현
}

public class MySQLCompatibilityTest implements DatabaseCompatibilityTest {
    // MySQL 특화 구현  
}
```

## 문제 해결 참조

### 자주 발생하는 문제와 해결책

1. **"Unknown Types value" (PostgreSQL)**
   - 원인: PreparedStatement.setNull() 호출 시 Agent의 ASM 변환 오류
   - 해결: `exclude-prepared-statement-transformation=true` 설정

2. **"Cannot commit when autoCommit is enabled" (PostgreSQL)**
   - 원인: Agent가 Connection의 autoCommit 상태를 잘못 관리
   - 해결: `avoid-autocommit-state-change=true` 설정

3. **"Unable to rollback against JDBC Connection" (PostgreSQL)**
   - 원인: HikariCP 프록시와 Agent 간의 트랜잭션 관리 충돌
   - 해결: `exclude-connection-management=true` 설정

## 결론

이 문서의 쿼리들을 사용하여 체계적으로 JDBC 호환성을 테스트함으로써:
- Agent의 데이터베이스별 호환성 문제를 사전에 발견
- 프로덕션 환경에서 발생할 수 있는 문제 예방
- 새로운 DB 지원 시 빠른 호환성 검증 가능
- 일관된 품질의 Agent 개발 및 유지보수

각 데이터베이스별로 이 쿼리들을 실행하고 결과를 비교 분석하여 Agent의 호환성을 지속적으로 개선해나가시기 바랍니다.