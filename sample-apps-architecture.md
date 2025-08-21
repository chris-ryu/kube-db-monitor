# KubeDB Monitor Agent 호환성 테스트용 확장 가능한 샘플 애플리케이션 아키텍처

## 1. 전체 구조 개요

```
sample-apps/
├── common-core/                    # 공통 비즈니스 로직 (DB 중립적)
│   ├── src/main/java/
│   │   └── com/university/registration/
│   │       ├── dto/               # 데이터 전송 객체
│   │       ├── service/           # 비즈니스 서비스 (인터페이스)
│   │       ├── entity/            # JPA Entity (추상화)
│   │       └── controller/        # REST Controller
│   └── pom.xml                    # 공통 의존성 정의
│
├── postgresql-app/                # PostgreSQL 전용 구현
│   ├── src/main/java/
│   │   └── com/university/registration/postgresql/
│   │       ├── config/           # PostgreSQL 특화 설정
│   │       ├── repository/       # PostgreSQL 최적화 Repository
│   │       └── service/          # PostgreSQL 특화 서비스 구현
│   ├── src/main/resources/
│   │   ├── application-postgresql.yml
│   │   └── db/migration/         # Flyway 마이그레이션 (PostgreSQL)
│   └── pom.xml
│
├── mysql-app/                     # MySQL 전용 구현  
│   ├── src/main/java/
│   │   └── com/university/registration/mysql/
│   │       ├── config/           # MySQL 특화 설정
│   │       ├── repository/       # MySQL 최적화 Repository
│   │       └── service/          # MySQL 특화 서비스 구현
│   ├── src/main/resources/
│   │   ├── application-mysql.yml
│   │   └── db/migration/         # Flyway 마이그레이션 (MySQL)
│   └── pom.xml
│
├── mariadb-app/                   # MariaDB 전용 구현
│   ├── src/main/java/
│   │   └── com/university/registration/mariadb/
│   │       ├── config/           # MariaDB 특화 설정
│   │       ├── repository/       # MariaDB 최적화 Repository
│   │       └── service/          # MariaDB 특화 서비스 구현
│   └── pom.xml
│
├── oracle-app/                    # Oracle 전용 구현
│   ├── src/main/java/
│   │   └── com/university/registration/oracle/
│   │       ├── config/           # Oracle 특화 설정 (CDB/PDB)
│   │       ├── repository/       # Oracle 최적화 Repository
│   │       └── service/          # Oracle 특화 서비스 구현
│   └── pom.xml
│
├── sqlserver-app/                 # SQL Server 전용 구현
│   ├── src/main/java/
│   │   └── com/university/registration/sqlserver/
│   │       ├── config/           # SQL Server 특화 설정
│   │       ├── repository/       # SQL Server 최적화 Repository
│   │       └── service/          # SQL Server 특화 서비스 구현
│   └── pom.xml
│
└── integration-tests/             # 통합 테스트 (모든 DB 대상)
    ├── src/test/java/
    │   └── com/university/registration/
    │       ├── compatibility/     # DB별 호환성 테스트
    │       ├── performance/       # 성능 테스트
    │       └── agent/            # Agent 특화 테스트
    └── pom.xml
```

## 2. 공통 모듈 (common-core) 설계

### 2.1 추상화된 Entity 구조

```java
// common-core/src/main/java/com/university/registration/entity/

@MappedSuperclass
public abstract class BaseEntity {
    @Column(name = "created_at")
    protected LocalDateTime createdAt = LocalDateTime.now();
    
    @Version
    protected Long version = 0L;
}

@Entity
@Table(name = "departments")
public abstract class Department extends BaseEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    protected Long departmentId;
    
    @Column(nullable = false, length = 100)
    protected String name;
    
    @Column(nullable = false, length = 10, unique = true)
    protected String code;
    
    // 추상 메서드로 DB별 특화 구현 위임
    public abstract void applyDatabaseSpecificOptimizations();
}
```

### 2.2 서비스 인터페이스

```java
// common-core/src/main/java/com/university/registration/service/

public interface CourseService {
    List<CourseDto> getAllCourses();
    CourseDto getCourse(String courseId);
    CourseDto createCourse(CreateCourseDto dto);
    void enrollStudent(String studentId, String courseId);
    
    // DB별 최적화 쿼리를 위한 추상 메서드
    List<CourseDto> getCoursesWithDatabaseOptimization(CourseSearchCriteria criteria);
    void executeDatabaseSpecificMaintenance();
}

public interface StudentService {
    List<StudentDto> getAllStudents();
    StudentDto createStudent(CreateStudentDto dto);
    
    // 각 DB의 특화 기능 활용
    List<StudentDto> searchStudentsWithFullText(String searchTerm);
    void optimizeStudentQueries();
}
```

### 2.3 공통 REST Controller

```java
@RestController
@RequestMapping("/api")
@CrossOrigin(origins = "*")
public class UniversityController {
    
    private final CourseService courseService;
    private final StudentService studentService;
    
    @GetMapping("/courses")
    public ResponseEntity<List<CourseDto>> getCourses(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        return ResponseEntity.ok(courseService.getAllCourses());
    }
    
    @PostMapping("/students/{studentId}/enroll/{courseId}")
    public ResponseEntity<String> enrollStudent(
            @PathVariable String studentId,
            @PathVariable String courseId) {
        courseService.enrollStudent(studentId, courseId);
        return ResponseEntity.ok("Enrollment successful");
    }
    
    // DB별 최적화 엔드포인트
    @GetMapping("/courses/optimized")
    public ResponseEntity<List<CourseDto>> getOptimizedCourses(
            @RequestParam Map<String, String> criteria) {
        CourseSearchCriteria searchCriteria = new CourseSearchCriteria(criteria);
        return ResponseEntity.ok(courseService.getCoursesWithDatabaseOptimization(searchCriteria));
    }
}
```

## 3. DB별 전용 구현체 예시

### 3.1 PostgreSQL 전용 구현

```java
// postgresql-app/src/main/java/com/university/registration/postgresql/

@Entity
@Table(name = "courses")
public class PostgreSQLCourse extends Course {
    
    // PostgreSQL 특화 기능: JSON 컬럼
    @Column(columnDefinition = "jsonb")
    @Convert(converter = JsonConverter.class)
    private Map<String, Object> metadata;
    
    // PostgreSQL 특화 기능: Array 타입
    @Column(columnDefinition = "text[]")
    private String[] tags;
    
    @Override
    public void applyDatabaseSpecificOptimizations() {
        // PostgreSQL VACUUM, ANALYZE 등
    }
}

@Service
@Profile("postgresql")
public class PostgreSQLCourseService implements CourseService {
    
    @Override
    public List<CourseDto> getCoursesWithDatabaseOptimization(CourseSearchCriteria criteria) {
        // PostgreSQL 특화: CTE, Window Function 활용
        return courseRepository.findCoursesWithCTE(criteria);
    }
    
    // PostgreSQL 특화 전문 검색
    public List<CourseDto> searchCoursesWithFullText(String searchTerm) {
        return courseRepository.findByFullTextSearch(searchTerm);
    }
}
```

### 3.2 MySQL 전용 구현

```java
// mysql-app/src/main/java/com/university/registration/mysql/

@Entity
@Table(name = "courses")
public class MySQLCourse extends Course {
    
    // MySQL 특화: Virtual Column
    @Formula("CHAR_LENGTH(course_name)")
    private Integer nameLength;
    
    @Override
    public void applyDatabaseSpecificOptimizations() {
        // MySQL InnoDB 최적화, Query Cache 활용
    }
}

@Service
@Profile("mysql")
public class MySQLCourseService implements CourseService {
    
    @Override
    public List<CourseDto> getCoursesWithDatabaseOptimization(CourseSearchCriteria criteria) {
        // MySQL 특화: InnoDB 최적화 쿼리
        return courseRepository.findCoursesWithInnoDBOptimization(criteria);
    }
}
```

### 3.3 Oracle 전용 구현

```java
// oracle-app/src/main/java/com/university/registration/oracle/

@Entity
@Table(name = "courses")
public class OracleCourse extends Course {
    
    // Oracle 특화: Sequence 사용
    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "course_seq")
    @SequenceGenerator(name = "course_seq", sequenceName = "course_seq", allocationSize = 1)
    private Long courseId;
    
    @Override
    public void applyDatabaseSpecificOptimizations() {
        // Oracle 통계 수집, 힌트 활용
    }
}

@Service
@Profile("oracle")
public class OracleCourseService implements CourseService {
    
    @Override
    public List<CourseDto> getCoursesWithDatabaseOptimization(CourseSearchCriteria criteria) {
        // Oracle 특화: 파티셔닝, 인덱스 힌트 활용
        return courseRepository.findCoursesWithOracleHints(criteria);
    }
}
```

## 4. 구성 전략

### 4.1 빌드 및 배포 전략

```xml
<!-- 부모 POM (common-core) -->
<parent>
    <groupId>com.university</groupId>
    <artifactId>registration-common</artifactId>
    <version>1.0.0</version>
</parent>

<modules>
    <module>common-core</module>
    <module>postgresql-app</module>
    <module>mysql-app</module>
    <module>mariadb-app</module>
    <module>oracle-app</module>
    <module>sqlserver-app</module>
    <module>integration-tests</module>
</modules>
```

### 4.2 Profile 기반 설정

```yaml
# application-postgresql.yml
spring:
  profiles:
    active: postgresql
  datasource:
    driver-class-name: org.postgresql.Driver
    url: jdbc:postgresql://postgres-cluster-rw.postgres-system:5432/university
  jpa:
    database-platform: org.hibernate.dialect.PostgreSQLDialect
    properties:
      hibernate:
        dialect: org.hibernate.dialect.PostgreSQLDialect

# application-mysql.yml
spring:
  profiles:
    active: mysql
  datasource:
    driver-class-name: com.mysql.cj.jdbc.Driver
    url: jdbc:mysql://mysql-cluster-rw.mysql-system:3306/university
  jpa:
    database-platform: org.hibernate.dialect.MySQLDialect
```

## 5. Agent 호환성 테스트 전략

### 5.1 각 DB별 Agent 설정 차이점

| 데이터베이스 | 드라이버 | 특화 설정 | 모니터링 포인트 |
|-------------|---------|----------|---------------|
| PostgreSQL  | postgresql | prepared-statement-cache | Connection Pool, Transaction |
| MySQL       | mysql-connector-j | innodb-monitoring | InnoDB Buffer Pool, Query Cache |
| MariaDB     | mariadb-java-client | query-response-time | Galera Cluster, Query Performance |
| Oracle      | ojdbc11 | cdb-pdb-monitoring | CDB/PDB, PL/SQL, Cursor |
| SQL Server  | mssql-jdbc | always-on-monitoring | T-SQL, Index Usage, Wait Stats |

### 5.2 통합 테스트 시나리오

```java
@TestMethodOrder(OrderAnnotation.class)
public class AgentCompatibilityTest {
    
    @ParameterizedTest
    @ValueSource(strings = {"postgresql", "mysql", "mariadb", "oracle", "sqlserver"})
    void testAgentBasicCompatibility(String database) {
        // 각 DB에서 Agent 기본 로딩 테스트
    }
    
    @ParameterizedTest
    @ValueSource(strings = {"conservative", "balanced", "aggressive"})
    void testAgentProfilePerformance(String profile) {
        // 각 Agent 프로파일별 성능 영향도 측정
    }
    
    @Test
    void testCrossDatabaseCompatibility() {
        // 여러 DB를 동시에 사용하는 환경에서의 Agent 안정성
    }
}
```

## 6. 확장 가능성

### 6.1 새로운 데이터베이스 추가 절차

1. `{database}-app/` 디렉토리 생성
2. DB별 Entity 구현체 작성
3. DB별 Service 구현체 작성
4. DB별 설정 파일 추가
5. Kubernetes 배포 설정 추가
6. Agent 호환성 테스트 추가

### 6.2 새로운 커넥션 풀 지원 절차

1. 커넥션 풀별 설정 프로파일 추가
2. HikariCP 외 DBCP2, C3P0 등 설정
3. Agent 호환성 매트릭스 업데이트
4. 성능 비교 테스트 추가

이러한 구조로 PostgreSQL을 기반으로 시작해서 체계적으로 다른 데이터베이스와 커넥션 풀로 확장할 수 있는 견고하고 유지보수 가능한 아키텍처를 구축할 수 있습니다.