# KubeDB Monitor 아키텍처 개요

## 시스템 구조

KubeDB Monitor는 3계층 아키텍처로 구성된 Kubernetes 기반 DB 모니터링 솔루션입니다.

### 핵심 컴포넌트

```
┌─────────────────────────────────────────────────────────────────┐
│                    Kubernetes Cluster                            │
│                                                                   │
│  ┌────────────────┐         ┌──────────────────┐                │
│  │  Application   │         │  ByteBuddy Agent │                │
│  │  Pod           │────────▶│  (Java Agent)    │                │
│  │  (Spring Boot) │         │  - JDBC 인터셉션  │                │
│  └────────────────┘         │  - 메트릭 수집    │                │
│                              └──────────────────┘                │
│                                      │                            │
│                                      │ HTTP POST                  │
│                                      ▼                            │
│                              ┌──────────────────┐                │
│                              │  Control Plane   │                │
│                              │  (Go)            │                │
│                              │  - 이벤트 수신    │                │
│                              │  - 이벤트 중계    │                │
│                              └──────────────────┘                │
│                                      │                            │
│                                      │ WebSocket                  │
│                                      ▼                            │
│                              ┌──────────────────┐                │
│                              │  Dashboard       │                │
│                              │  (Next.js)       │                │
│                              │  - 실시간 UI     │                │
│                              └──────────────────┘                │
└─────────────────────────────────────────────────────────────────┘
```

## 1. Agent Layer (kubedb-monitor-agent/)

**기술 스택**: Java 17+, ByteBuddy, Maven

**역할**: 애플리케이션 JVM에 주입되어 JDBC 호출을 투명하게 모니터링

### 핵심 클래스

- `KubeDBAgent.java`: ByteBuddy Agent 진입점
- `UniversalJDBCInterceptor.java`: 범용 JDBC 메서드 인터셉션
- `MetricsCollector.java`: 메트릭 데이터 수집 및 집계
- `HttpMetricsTransmitter.java`: Control Plane으로 HTTP 전송
- `SpringTransactionInterceptor.java`: Spring 트랜잭션 감지

### 지원 기능

1. **SQL 쿼리 실행 모니터링** (`query_execution`)
2. **트랜잭션 추적** (`transaction_event`)
3. **Long-running Transaction 감지** (`long_running_transaction`) - 임계값: 5초
4. **데드락 감지** (`deadlock_event`)
5. **Connection Pool 메트릭** (`system_metrics`)

### Agent 배포 방식

```yaml
# JAVA_OPTS에 Agent JAR 경로 지정
env:
- name: JAVA_OPTS
  value: "-javaagent:/opt/kubedb-agent/kubedb-monitor-agent.jar=collector-endpoint=http://kubedb-monitor-control-plane:8080/api/metrics"
```

## 2. Control Plane Layer (control-plane/)

**기술 스택**: Go, Gorilla WebSocket

**역할**: Agent에서 수신한 이벤트를 Dashboard로 WebSocket 브로드캐스트

### 핵심 파일

- `main.go`: HTTP 서버, WebSocket Hub, 이벤트 라우팅

### API 엔드포인트

```
POST /api/metrics              # Agent에서 메트릭 수신
GET  /ws                        # Dashboard WebSocket 연결
GET  /health                    # 헬스 체크
```

### 이벤트 처리 흐름

```
Agent HTTP POST → Control Plane 수신 → WebSocket Hub → 연결된 모든 클라이언트에 브로드캐스트
```

## 3. Dashboard Layer (dashboard-frontend/)

**기술 스택**: Next.js 14+, React, Recharts, Tailwind CSS

**역할**: 실시간 메트릭 시각화 및 알림 표시

### 핵심 컴포넌트

- `Dashboard.jsx`: 메인 대시보드 UI
- WebSocket 클라이언트: Control Plane 실시간 연결

### 지원 패널

1. **Query Execution Panel**: SQL 쿼리 실행 내역
2. **Transaction Panel**: 트랜잭션 커밋/롤백 이벤트
3. **Long-running Transaction Panel**: 장기 실행 트랜잭션 알림
4. **Deadlock Panel**: 데드락 이벤트 알림
5. **Connection Pool Panel**: Connection Pool 메트릭 차트

## 이벤트 스키마

### 공통 필드

```json
{
  "eventType": "query_execution | transaction_event | long_running_transaction | deadlock_event | system_metrics",
  "timestamp": "2025-10-08T10:30:00Z",
  "podName": "university-registration-demo-xxx",
  "namespace": "kubedb-monitor-test"
}
```

### 이벤트별 상세 스키마

자세한 내용은 [agent-event-data-structures.md](../../docs/agent-event-data-structures.md) 참조

## 배포 구조

### Kubernetes 리소스

```
namespace: kubedb-monitor-test

Deployments:
- kubedb-monitor-control-plane  (Go)
- kubedb-monitor-dashboard      (Next.js)
- university-registration-demo   (Spring Boot + Agent)

Services:
- kubedb-monitor-control-plane (ClusterIP :8080)
- kubedb-monitor-dashboard     (ClusterIP :3000)
- university-registration-ui    (ClusterIP :3000)

Ingress:
- university-registration.bitgaram.info (Application)
- kube-db-mon-dashboard.bitgaram.info (Dashboard)
- kube-db-mon-controlplane.bitgaram.info (Control Plane)
```

## 데이터 흐름 예시

### Long-running Transaction 감지 예시

```
1. Application에서 트랜잭션 시작
   └─ Agent: setAutoCommit(false) 인터셉션 → 트랜잭션 시작 시간 기록

2. SQL 쿼리 실행 (5초 이상 소요)
   └─ Agent: execute() 인터셉션 → 실행 시간 측정

3. 트랜잭션 커밋
   └─ Agent: commit() 인터셉션 → 총 트랜잭션 시간 계산
   └─ Agent: 5초 초과 감지 → long_running_transaction 이벤트 생성
   └─ Agent → Control Plane: HTTP POST /api/metrics

4. Control Plane: 이벤트 수신 → WebSocket 브로드캐스트

5. Dashboard: WebSocket 수신 → Long-running Panel에 알림 표시
```

## 핵심 설정 값

### Agent 환경 변수

```bash
KUBEDB_MONITOR_ENABLED=true                          # 모니터링 활성화
KUBEDB_MONITOR_LONG_RUNNING_TX_THRESHOLD_MS=5000     # Long-running 임계값 (ms)
KUBEDB_MONITOR_SLOW_QUERY_THRESHOLD_MS=1000          # Slow Query 임계값 (ms)
KUBEDB_MONITOR_COLLECTOR_ENDPOINT=http://...         # Control Plane URL
KUBEDB_MONITOR_LOG_LEVEL=WARN                        # 로그 레벨
```

### Control Plane 환경 변수

```bash
PORT=8080                                            # HTTP 서버 포트
```

### Dashboard 환경 변수

```bash
NEXT_PUBLIC_WS_URL=ws://...                          # WebSocket URL
```

## 참고 문서

- [JDBC 호환성 가이드](../../docs/agent-jdbc-compatibility-guide.md)
- [이벤트 데이터 구조](../../docs/agent-event-data-structures.md)
- [WebSocket 메시지 포맷](../../docs/websocket-message-format.md)
- [ByteBuddy Agent 구현 보고서](../../docs/bytebuddy-agent-implementation-report.md)
