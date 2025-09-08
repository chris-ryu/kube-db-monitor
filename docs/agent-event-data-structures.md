# Agent Event Data Structures

Agent에서 Control Plane으로 전송하는 실제 이벤트 데이터 구조를 문서화합니다.

## 1. Query Execution Event (`query_execution`)

일반적인 SQL 쿼리 실행 메트릭입니다.

### Agent → Control Plane HTTP Payload
```json
{
  "timestamp": "2025-08-30T06:18:21Z",
  "podName": "university-registration-demo-12345",
  "namespace": "kubedb-monitor-test",
  "eventType": "query_execution",
  "data": {
    "queryId": "agent-1693456789123-987654321",
    "sqlPattern": "SELECT * FROM students WHERE id = ?",
    "sqlType": "SELECT",
    "tableNames": ["students"],
    "executionTimeMs": 150,
    "status": "SUCCESS",
    "errorMessage": null
  },
  "metrics": {
    "connectionPoolActive": 5,
    "connectionPoolIdle": 3,
    "connectionPoolMax": 10
  }
}
```

### Control Plane → Dashboard WebSocket
```json
{
  "type": "query_metrics",
  "data": {
    "timestamp": "2025-08-30T06:18:21Z",
    "pod_name": "university-registration-demo-12345",
    "namespace": "kubedb-monitor-test",
    "event_type": "query_execution",
    "data": {
      "query_id": "agent-1693456789123-987654321",
      "sql_type": "SELECT",
      "sql_pattern": "SELECT * FROM students WHERE id = ?",
      "execution_time_ms": 150,
      "status": "SUCCESS"
    }
  },
  "timestamp": "2025-08-30T06:18:21Z"
}
```

## 2. Long-Running Transaction Event (`long_running_transaction`)

5초 이상 실행되는 트랜잭션을 감지했을 때 전송됩니다.

### Agent → Control Plane HTTP Payload
```json
{
  "timestamp": "2025-08-30T06:18:21Z",
  "podName": "university-registration-demo-12345",
  "namespace": "kubedb-monitor-test",
  "eventType": "long_running_transaction",
  "data": {
    "queryId": "agent-1693456789123-987654321",
    "sqlPattern": "Long running transaction detected: 8000ms",
    "sqlType": "LONG_RUNNING",
    "executionTimeMs": 8000,
    "status": "SUCCESS",
    "transactionDuration": 8000,
    "transactionId": "tx-abc123def456-long"
  },
  "metrics": {
    "connectionPoolActive": 8,
    "connectionPoolIdle": 1,
    "connectionPoolMax": 10
  }
}
```

### Control Plane → Dashboard WebSocket
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

## 3. Deadlock Detection Event (`deadlock_detected`)

데드락이 감지되었을 때 전송됩니다.

### Agent → Control Plane HTTP Payload
```json
{
  "timestamp": "2025-08-30T06:18:21Z",
  "podName": "university-registration-demo-12345",
  "namespace": "kubedb-monitor-test",
  "eventType": "deadlock_detected",
  "data": {
    "queryId": "agent-1693456789123-987654321",
    "sqlPattern": "Deadlock detected: 5000ms duration",
    "sqlType": "DEADLOCK",
    "executionTimeMs": 5000,
    "status": "SUCCESS",
    "deadlockDuration": 5000,
    "deadlockConnections": "PgConnection@ac889df:PgConnection@139539a4",
    "transactionId": "deadlock-tx-123"
  }
}
```

### Control Plane → Dashboard WebSocket (특수 변환)
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

## 4. TPS (Transactions Per Second) Event (`tps_event`)

높은 TPS가 감지되었을 때 전송됩니다.

### Agent → Control Plane HTTP Payload
```json
{
  "timestamp": "2025-08-30T06:18:21Z",
  "podName": "university-registration-demo-12345",
  "namespace": "kubedb-monitor-test", 
  "eventType": "tps_event",
  "data": {
    "queryId": "agent-1693456789123-987654321",
    "sqlPattern": "High TPS detected: 150.5 queries/second",
    "sqlType": "TPS_EVENT",
    "executionTimeMs": 150.5,
    "status": "SUCCESS",
    "tpsValue": 150.5
  },
  "metrics": {
    "connectionPoolActive": 9,
    "connectionPoolIdle": 1,
    "connectionPoolMax": 10
  }
}
```

## 5. System Metrics Event (`system_metrics`)

시스템 메트릭 (Connection Pool 상태 등)을 전송합니다.

### Agent → Control Plane HTTP Payload
```json
{
  "timestamp": "2025-08-30T06:18:21Z",
  "podName": "university-registration-demo-12345",
  "namespace": "kubedb-monitor-test",
  "eventType": "system_metrics",
  "data": {
    "queryId": "system-metrics-1693456789123",
    "sqlPattern": "System metrics collection",
    "sqlType": "SYSTEM",
    "executionTimeMs": 0,
    "status": "SUCCESS"
  },
  "metrics": {
    "connectionPoolActive": 5,
    "connectionPoolIdle": 3,
    "connectionPoolMax": 10,
    "connectionPoolUsageRatio": 0.8,
    "peakActiveConnections": 8,
    "waitingThreads": 0
  }
}
```

## 필드 매핑 정리

### Agent HttpMetricPayload.QueryData 실제 필드들
```java
public static class QueryData {
    public String queryId;                // 쿼리 고유 ID
    public String sqlPattern;             // SQL 패턴/내용
    public String sqlType;                // SQL 타입 (SELECT, INSERT, etc.)
    public String[] tableNames;           // 관련 테이블들
    public long executionTimeMs;          // 실행 시간 (ms)
    public String status;                 // SUCCESS/ERROR
    public String errorMessage;           // 에러 메시지 (있는 경우)
    
    // 특수 이벤트용 추가 필드들
    public Double tpsValue;               // TPS 이벤트용
    public Long transactionDuration;      // Long-running 트랜잭션용
    public String transactionId;          // 트랜잭션 ID
    public Long deadlockDuration;         // 데드락 이벤트용
    public String deadlockConnections;    // 데드락 연결 정보
}
```

### Dashboard TypeScript 타입 매핑

#### TransactionEvent (Long-running용)
```typescript
interface TransactionEvent {
  // 기본 필드들
  id: string
  transaction_id: string
  status: 'active' | 'committed' | 'rolled_back' | 'timeout'
  
  // Agent에서 실제로 보내는 필드들
  transaction_duration?: number    // Agent: transactionDuration
  sql_pattern?: string            // Agent: sqlPattern
  execution_time_ms?: number      // Agent: executionTimeMs
  
  // Fallback 필드들
  duration_ms?: number
  query?: string
  pod_name?: string
  database_name?: string
}
```

#### DeadlockEvent (Deadlock용)
```typescript  
interface DeadlockEvent {
  // 기본 필드들
  id: string
  participants: string[]
  detectionTime: string
  severity: 'critical' | 'warning' | 'info'
  status: 'active' | 'resolved' | 'ignored'
  
  // Agent/Control Plane에서 실제로 제공하는 필드들
  duration_ms?: number            // Control Plane: duration_ms
  duration?: number               // Control Plane: duration_ms
  pod_name?: string              // Agent: podName
  transaction_id?: string        // Agent: transactionId
  
  // Fallback 필드들
  query?: string
  description?: string
  database_name?: string
}
```

## 이벤트 처리 플로우

1. **Agent**: `HttpMetricsCollector`에서 메트릭 수집 및 HTTP POST로 전송
2. **Control Plane**: 이벤트 타입에 따라 WebSocket 메시지 변환
3. **Dashboard**: WebSocket 메시지 수신 및 UI 컴포넌트 업데이트

### 중요한 변환 규칙
- `deadlock_detected` → `deadlock_event` (특수 구조 변환)
- `long_running_transaction` → `long_running_transaction` (직통)
- `query_execution` → `query_metrics` (이름만 변환)

### 데이터 우선순위
1. **Agent 원본 필드** (예: `transactionDuration`, `sqlPattern`)
2. **Control Plane 변환 필드** (예: `transaction_duration`, `duration_ms`)
3. **UI 호환 Fallback** (예: `query`, `description`)