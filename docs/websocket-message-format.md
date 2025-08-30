# WebSocket 메시지 포맷 문서

## 개요

KubeDB Monitor 시스템에서 Control-plane과 Dashboard 간 실시간 통신을 위한 WebSocket 메시지 포맷을 정의합니다.

## 메시지 구조

모든 WebSocket 메시지는 다음과 같은 기본 구조를 가집니다:

```json
{
  "type": "메시지_타입",
  "data": { 메시지_데이터 },
  "timestamp": "2025-08-30T06:18:21Z"
}
```

## 메시지 타입별 상세 포맷

### 1. Query Execution 메시지 (`query_metrics`)

SQL 쿼리 실행 메트릭을 전송합니다.

```json
{
  "type": "query_metrics",
  "data": {
    "timestamp": "2025-08-30T06:18:21Z",
    "pod_name": "university-registration-demo-12345",
    "namespace": "kubedb-monitor-test",
    "event_type": "query_execution",
    "data": {
      "query_id": "query-1693456789123",
      "sql_type": "SELECT|INSERT|UPDATE|DELETE",
      "sql_pattern": "SELECT * FROM students WHERE id = ?",
      "execution_time_ms": 150,
      "connection_id": "conn-123456789",
      "thread_name": "http-nio-8080-exec-1",
      "status": "completed|error",
      "error_message": "에러 메시지 (옵션)"
    }
  },
  "timestamp": "2025-08-30T06:18:21Z"
}
```

### 2. Transaction Event 메시지 (`transaction_event`)

트랜잭션 관련 이벤트를 전송합니다.

```json
{
  "type": "transaction_event",
  "data": {
    "timestamp": "2025-08-30T06:18:21Z",
    "pod_name": "university-registration-demo-12345",
    "namespace": "kubedb-monitor-test",
    "event_type": "transaction_event",
    "data": {
      "query_id": "tx-1693456789123",
      "sql_type": "TRANSACTION",
      "sql_pattern": "COMMIT|ROLLBACK",
      "execution_time_ms": 50,
      "connection_id": "conn-123456789",
      "transaction_id": "tx-abc123def456",
      "status": "completed"
    }
  },
  "timestamp": "2025-08-30T06:18:21Z"
}
```

### 3. Long Running Transaction 메시지 (`long_running_transaction`)

장기 실행 트랜잭션을 감지했을 때 전송됩니다.

```json
{
  "type": "long_running_transaction",
  "data": {
    "timestamp": "2025-08-30T06:18:21Z",
    "pod_name": "university-registration-demo-12345",
    "namespace": "kubedb-monitor-test",
    "event_type": "long_running_transaction",
    "data": {
      "query_id": "long-tx-1693456789123",
      "sql_type": "TRANSACTION",
      "execution_time_ms": 8000,
      "connection_id": "conn-123456789",
      "transaction_id": "tx-abc123def456-long",
      "transaction_duration": 8000,
      "status": "long_running"
    }
  },
  "timestamp": "2025-08-30T06:18:21Z"
}
```

### 4. Deadlock Event 메시지 (`deadlock_event`)

데드락이 감지되었을 때 전송됩니다.

```json
{
  "type": "deadlock_event",
  "data": {
    "id": "deadlock-universityregistrationdemo-1693456789123",
    "participants": [
      {
        "id": "connection-1",
        "resource": "table_1",
        "lockType": "exclusive",
        "connection": "PgConnection@ac889df"
      },
      {
        "id": "connection-2",
        "resource": "table_2",
        "lockType": "shared",
        "connection": "PgConnection@139539a4"
      }
    ],
    "detectionTime": "2025-08-30T06:18:21Z",
    "recommendedVictim": "connection-1",
    "lockChain": [
      "connection-1 → connection-2 (table_1, exclusive)",
      "connection-2 → connection-1 (table_2, shared)"
    ],
    "severity": "critical",
    "status": "active",
    "pod_name": "university-registration-demo-12345",
    "namespace": "production",
    "cycleLength": 2,
    "duration_ms": 5000,
    "connections": "PgConnection@ac889df:PgConnection@139539a4"
  },
  "timestamp": "2025-08-30T06:18:21Z"
}
```

## Control-plane 메시지 변환 로직

### Query Execution → WebSocket

1. **입력**: Agent에서 HTTP POST로 전송된 메트릭
2. **변환**: `event_type`에 따라 메시지 타입 결정
3. **출력**: WebSocket으로 모든 연결된 클라이언트에 브로드캐스팅

```go
// Control-plane 변환 로직
var messageType string
switch metric.EventType {
case "query_execution":
    messageType = "query_metrics"
case "transaction_event":
    messageType = "transaction_event"
case "deadlock_event", "deadlock_detected":
    messageType = "deadlock_event"
    // 특별한 deadlock 구조로 변환
    deadlockMessage := createDeadlockMessage(metric)
    h.broadcast <- deadlockMessage
    return
case "long_running_transaction":
    messageType = "long_running_transaction"
default:
    messageType = "query_metrics" // 기본값
}
```

## Dashboard 메시지 파싱 로직

Dashboard에서는 다음과 같이 메시지를 처리해야 합니다:

```typescript
const processWebSocketMessage = (message: any) => {
  console.log('🔍 Processing WebSocket message type:', message.type)
  
  switch (message.type) {
    case 'query_metrics':
    case 'metric':
    case 'query_execution':
      processMetric(message.data)
      break
      
    case 'transaction_event':
      processTransactionEvent(message.data)
      break
      
    case 'deadlock_event':
      processDeadlockEvent(message.data)
      break
      
    case 'long_running_transaction':
      processLongRunningTransaction(message.data)
      break
      
    default:
      console.warn('❓ Unknown message type:', message.type, message)
  }
}
```

## 중요한 변환 규칙

### 1. Event Type 매핑
- Agent `event_type` → WebSocket `type`
- `query_execution` → `query_metrics`
- `transaction_event` → `transaction_event`
- `deadlock_detected` → `deadlock_event`
- `long_running_transaction` → `long_running_transaction`

### 2. 데드락 메시지 특수 처리
- Control-plane에서 `createDeadlockMessage()` 함수로 Dashboard 호환 구조로 변환
- 연결 정보를 participants 배열로 파싱
- Lock chain 생성 및 권장 victim 설정

### 3. 타임스탬프 형식
- ISO 8601 형식: `2025-08-30T06:18:21Z`
- 모든 메시지에 메시지 생성 시점의 타임스탬프 포함

## 디버깅 팁

### Control-plane 로그 확인
```bash
kubectl logs kubedb-monitor-control-plane-xxx -n kubedb-monitor --tail=20
```

### Dashboard 브라우저 콘솔 확인
- WebSocket 연결 상태: `🔗 WebSocket URL`
- 메시지 수신: `📨 Received WebSocket message`
- 메시지 파싱: `🔍 Processing WebSocket message type`

### WebSocket 연결 테스트
```javascript
const ws = new WebSocket('wss://kube-db-mon-controlplane.bitgaram.info/ws');
ws.onmessage = (event) => {
  console.log('수신된 메시지:', JSON.parse(event.data));
};
```

## 버전 히스토리

- **v1.0** (2025-08-30): 초기 WebSocket 메시지 포맷 정의
- 향후 버전에서 메시지 포맷 변경 시 하위 호환성 고려 필요