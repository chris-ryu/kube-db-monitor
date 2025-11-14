# KubeDB Monitor 프로젝트 구조

## 디렉토리 구조

```
kube-db-monitor/
├── .agent/                              # AI 에이전트용 문서 시스템 (신규)
│   ├── system/                          # 시스템 아키텍처 문서
│   ├── SOPs/                            # 표준 운영 절차
│   ├── task/                            # 구현 계획 아카이브
│   └── readme.md                        # 문서 중앙 인덱스
│
├── kubedb-monitor-agent/                # Java Agent (ByteBuddy)
│   ├── src/main/java/                   # Agent 소스 코드
│   │   └── io/kubedb/monitor/agent/
│   │       ├── KubeDBAgent.java         # Agent 진입점
│   │       ├── UniversalJDBCInterceptor.java  # JDBC 인터셉션
│   │       ├── MetricsCollector.java    # 메트릭 수집
│   │       ├── HttpMetricsTransmitter.java    # HTTP 전송
│   │       ├── SpringTransactionInterceptor.java  # Spring 트랜잭션
│   │       └── pool/                    # Connection Pool 모니터링
│   │           ├── ConnectionPoolMonitor.java
│   │           ├── PoolMetrics.java
│   │           └── collectors/
│   │               ├── HikariPoolCollector.java
│   │               └── TomcatPoolCollector.java
│   ├── src/test/java/                   # 테스트 코드
│   ├── pom.xml                          # Maven 빌드 설정
│   └── Dockerfile                       # Agent 이미지 빌드
│
├── control-plane/                       # Control Plane (Go)
│   ├── main.go                          # HTTP 서버 + WebSocket Hub
│   ├── go.mod                           # Go 모듈 정의
│   ├── go.sum                           # 의존성 체크섬
│   └── Dockerfile                       # Control Plane 이미지 빌드
│
├── dashboard-frontend/                  # Dashboard (Next.js)
│   ├── src/
│   │   ├── app/
│   │   │   ├── page.tsx                 # 메인 페이지
│   │   │   ├── layout.tsx               # 레이아웃
│   │   │   ├── sessions/                # Session 녹화 기능 (신규)
│   │   │   └── settings/                # 설정 페이지 (신규)
│   │   └── components/
│   │       ├── Navigation.tsx           # 네비게이션 (신규)
│   │       └── RecordingControls.tsx    # 녹화 컨트롤 (신규)
│   ├── package.json                     # NPM 의존성
│   ├── next.config.js                   # Next.js 설정
│   └── Dockerfile                       # Dashboard 이미지 빌드
│
├── k8s/                                 # Kubernetes 매니페스트
│   ├── kubedb-monitor-deployment.yaml   # Control Plane + Dashboard 배포
│   ├── university-registration-with-ui.yaml  # 통합 배포 (API + UI + Agent)
│   ├── archived/                        # 레거시 매니페스트 보관
│   └── ...
│
├── scripts/                             # 빌드 및 테스트 스크립트
│   ├── build-images.sh                  # Docker 이미지 빌드 스크립트
│   ├── agent-comprehensive-test-suite.sh     # Agent 종합 테스트
│   ├── event-pipeline-verification-test.sh   # 이벤트 파이프라인 검증
│   └── ...
│
├── docs/                                # 기술 문서
│   ├── agent-jdbc-compatibility-guide.md     # JDBC 호환성 가이드
│   ├── agent-event-data-structures.md        # 이벤트 데이터 구조
│   ├── websocket-message-format.md           # WebSocket 메시지 포맷
│   └── bytebuddy-agent-implementation-report.md  # Agent 구현 보고서
│
├── Makefile                             # 통합 빌드 및 배포 명령어
├── CLAUDE.md                            # AI 에이전트 메모리 파일
├── README.md                            # 프로젝트 README
└── ...
```

## 주요 파일 설명

### Agent (Java)

| 파일 경로 | 역할 | 주요 기능 |
|----------|------|----------|
| `KubeDBAgent.java` | Agent 진입점 | ByteBuddy 초기화, 인터셉터 등록 |
| `UniversalJDBCInterceptor.java` | JDBC 인터셉션 | `execute*()`, `setAutoCommit()`, `commit()`, `rollback()` 등 메서드 인터셉션 |
| `MetricsCollector.java` | 메트릭 수집 | 메트릭 데이터 집계, 이벤트 생성 |
| `HttpMetricsTransmitter.java` | HTTP 전송 | Control Plane으로 메트릭 POST |
| `SpringTransactionInterceptor.java` | Spring 트랜잭션 | `@Transactional` 애노테이션 감지 (신규 구현) |
| `AgentConfig.java` | 설정 관리 | 환경 변수 파싱, 기본값 설정 |

### Control Plane (Go)

| 파일 경로 | 역할 | 주요 기능 |
|----------|------|----------|
| `main.go` | HTTP 서버 + WebSocket | `/api/metrics` 엔드포인트, WebSocket Hub, 브로드캐스트 |

### Dashboard (Next.js)

| 파일 경로 | 역할 | 주요 기능 |
|----------|------|----------|
| `src/app/page.tsx` | 메인 대시보드 | WebSocket 연결, 실시간 메트릭 표시 |
| `src/app/sessions/` | Session 녹화 (신규) | 메트릭 이벤트 녹화 및 재생 |
| `src/app/settings/` | 설정 페이지 (신규) | 계정 및 보안 설정 |

### Kubernetes

| 파일 경로 | 역할 | 주요 내용 |
|----------|------|----------|
| `k8s/kubedb-monitor-deployment.yaml` | Control Plane + Dashboard | Deployment, Service, Ingress |
| `k8s/university-registration-with-ui.yaml` | 통합 배포 | API + UI + Agent 포함 |

## 빌드 산출물

### Docker 이미지

```
registry.bitgaram.info/kubedb-monitor/agent:latest
registry.bitgaram.info/kubedb-monitor/control-plane:latest
registry.bitgaram.info/kubedb-monitor/dashboard:latest
registry.bitgaram.info/university-registration/api:latest
registry.bitgaram.info/university-registration/ui:latest
```

### Agent JAR 파일

```
kubedb-monitor-agent/target/kubedb-monitor-agent.jar
```

**배포 경로**: `/opt/kubedb-agent/kubedb-monitor-agent.jar`

## 테스트 디렉토리

```
kubedb-monitor-agent/src/test/java/
├── io/kubedb/monitor/agent/
│   ├── AgentConfigTest.java
│   ├── ByteBuddyAgentIntegrationTest.java
│   ├── JDBCCompatibilityTestSuite.java
│   ├── LongRunningTransactionIntegrationTest.java
│   ├── TransactionIntegrationTest.java
│   └── ...
└── java-backup/old-asm-tests/           # 레거시 ASM 테스트 (백업)
```

## 설정 파일

### Maven (Agent)
- `kubedb-monitor-agent/pom.xml`

### Go Modules (Control Plane)
- `control-plane/go.mod`
- `control-plane/go.sum`

### NPM (Dashboard)
- `dashboard-frontend/package.json`
- `dashboard-frontend/package-lock.json`

### Makefile
- `/Makefile`: 통합 빌드 및 배포 명령어

## 문서 파일

### 루트 디렉토리
- `README.md`: 프로젝트 개요
- `CLAUDE.md`: AI 에이전트 메모리 파일
- `DEPLOYMENT_SUMMARY.md`: 배포 요약
- `DEMO_GUIDE.md`: 데모 가이드
- `DEMO_SCENARIO_GUIDE.md`: 데모 시나리오 가이드

### docs/ 디렉토리
- `agent-jdbc-compatibility-guide.md`: JDBC 호환성 가이드
- `agent-event-data-structures.md`: 이벤트 데이터 구조
- `websocket-message-format.md`: WebSocket 메시지 포맷
- `bytebuddy-agent-implementation-report.md`: ByteBuddy Agent 구현 보고서

### .agent/ 디렉토리 (신규)
- `system/`: 시스템 아키텍처 문서
- `SOPs/`: 표준 운영 절차
- `task/`: 구현 계획 아카이브
- `readme.md`: 문서 중앙 인덱스

## Git 브랜치 전략

### 주요 브랜치
- `session-recording` (현재): Session 녹화 기능 개발 브랜치

### 최근 커밋
- `81306ccb`: Settings 페이지 추가 (계정 및 보안 관리)
- `20436f19`: Agent 종합 테스트 및 이벤트 파이프라인 검증 추가
- `77306249`: Connection Pool 모니터링 컴포넌트 구현
- `b14c51ee`: HikariCP 프록시 Advice 구현

## 의존성

### Agent (Java)
- ByteBuddy 1.14.x
- Apache HttpClient 5.x
- Jackson 2.x

### Control Plane (Go)
- Gorilla WebSocket
- Go 1.21+

### Dashboard (Next.js)
- Next.js 14+
- React 18+
- Recharts (차트)
- Tailwind CSS
