# 이벤트 처리 파이프라인

## 개요

KubeDB Monitor의 이벤트 처리 파이프라인은 Agent → Control Plane → Dashboard로 이어지는 3단계 아키텍처입니다.

## 지원 이벤트 타입 (총 5가지)

1. **`query_execution`**: SQL 쿼리 실행 메트릭
2. **`transaction_event`**: 트랜잭션 커밋/롤백 이벤트
3. **`long_running_transaction`**: Long-running transaction 알림 (임계값: 5초)
4. **`deadlock_event`** / **`deadlock_detected`**: 데드락 감지
5. **`system_metrics`**: Connection Pool 등 시스템 메트릭

## 파이프라인 검증 필수 규정

**⚠️ 새로운 기능 구현 시 반드시 다음 검증 스크립트를 실행하여 모든 이벤트 타입이 정상 처리되는지 확인할 것:**

```bash
./scripts/event-pipeline-verification-test.sh
```

**검증 항목:**
- ✅ Agent에서 이벤트 감지 및 생성
- ✅ Agent → Control Plane HTTP 전송
- ✅ Control Plane → Dashboard WebSocket 브로드캐스트
- ✅ 각 레이어에서 올바른 이벤트 타입 처리
- ✅ UI에서 실시간 표시

**성공률이 100%가 아닐 경우 배포 금지. 문제 해결 후 재검증 필수.**

## 이벤트 흐름 상세

### 1. Agent Layer: 이벤트 생성

**위치**: `kubedb-monitor-agent/src/main/java/io/kubedb/monitor/agent/`

#### 이벤트 타입별 생성 로직

##### 1.1 query_execution

**생성 시점**: JDBC `execute*()` 메서드 호출 시

**생성 클래스**: `UniversalJDBCInterceptor.java`

```java
// UniversalJDBCInterceptor.java - afterExecute() 메서드
Map<String, Object> event = new HashMap<>();
event.put("eventType", "query_execution");
event.put("sql", sql);
event.put("executionTime", executionTime);
event.put("success", success);
event.put("timestamp", System.currentTimeMillis());

metricsCollector.recordQueryExecution(event);
```

**전송**: `MetricsCollector` → `HttpMetricsTransmitter`

##### 1.2 transaction_event

**생성 시점**: `commit()` 또는 `rollback()` 호출 시

**생성 클래스**: `UniversalJDBCInterceptor.java`

```java
// commit() 또는 rollback() 인터셉션
Map<String, Object> event = new HashMap<>();
event.put("eventType", "transaction_event");
event.put("action", "commit" or "rollback");
event.put("transactionId", txId);
event.put("duration", duration);
event.put("timestamp", System.currentTimeMillis());

metricsCollector.recordTransactionEvent(event);
```

##### 1.3 long_running_transaction

**생성 시점**: 트랜잭션 커밋 시 총 실행 시간이 임계값(5초) 초과

**생성 클래스**: `UniversalJDBCInterceptor.java`

```java
// commit() 인터셉션 - 트랜잭션 시간 검사
long transactionDuration = System.currentTimeMillis() - txStartTime;
if (transactionDuration > LONG_RUNNING_TX_THRESHOLD) {
    Map<String, Object> event = new HashMap<>();
    event.put("eventType", "long_running_transaction");
    event.put("transactionId", txId);
    event.put("duration", transactionDuration);
    event.put("sqlStatements", sqlList);
    event.put("timestamp", System.currentTimeMillis());

    metricsCollector.recordLongRunningTransaction(event);
}
```

**임계값 설정**: 환경 변수 `KUBEDB_MONITOR_LONG_RUNNING_TX_THRESHOLD_MS` (기본값: 5000ms)

##### 1.4 deadlock_event

**생성 시점**: JDBC 예외 메시지에서 데드락 키워드 감지

**생성 클래스**: `UniversalJDBCInterceptor.java`

```java
// 예외 처리 로직
if (exception.getMessage().contains("deadlock") ||
    exception.getMessage().contains("Deadlock")) {
    Map<String, Object> event = new HashMap<>();
    event.put("eventType", "deadlock_event");
    event.put("errorMessage", exception.getMessage());
    event.put("sql", sql);
    event.put("timestamp", System.currentTimeMillis());

    metricsCollector.recordDeadlock(event);
}
```

##### 1.5 system_metrics

**생성 시점**: Connection Pool 메트릭 수집 (주기적)

**생성 클래스**: `ConnectionPoolMonitor.java`, `HikariPoolCollector.java`

```java
// ConnectionPoolMonitor.java
PoolMetrics metrics = hikariPoolCollector.collect();

Map<String, Object> event = new HashMap<>();
event.put("eventType", "system_metrics");
event.put("poolType", "HikariCP");
event.put("activeConnections", metrics.getActiveConnections());
event.put("idleConnections", metrics.getIdleConnections());
event.put("totalConnections", metrics.getTotalConnections());
event.put("timestamp", System.currentTimeMillis());

metricsCollector.recordSystemMetrics(event);
```

#### HTTP 전송

**전송 클래스**: `HttpMetricsTransmitter.java`

```java
// HttpMetricsTransmitter.java - transmit() 메서드
POST /api/metrics
Content-Type: application/json

{
  "eventType": "...",
  "timestamp": 1234567890,
  "podName": "university-registration-demo-xxx",
  "namespace": "kubedb-monitor-test",
  ...
}
```

**엔드포인트**: `KUBEDB_MONITOR_COLLECTOR_ENDPOINT` 환경 변수로 설정

### 2. Control Plane Layer: 이벤트 수신 및 중계

**위치**: `control-plane/main.go`

#### HTTP 수신

```go
// main.go - metricsHandler()
func metricsHandler(w http.ResponseWriter, r *http.Request) {
    var event map[string]interface{}
    json.NewDecoder(r.Body).Decode(&event)

    // 이벤트 타입 검증
    eventType, ok := event["eventType"].(string)
    if !ok {
        http.Error(w, "Invalid eventType", http.StatusBadRequest)
        return
    }

    // WebSocket Hub로 브로드캐스트
    hub.broadcast <- event
}
```

#### WebSocket 브로드캐스트

```go
// main.go - Hub.run()
func (h *Hub) run() {
    for {
        select {
        case message := <-h.broadcast:
            // 모든 연결된 클라이언트에 전송
            for client := range h.clients {
                select {
                case client.send <- message:
                default:
                    close(client.send)
                    delete(h.clients, client)
                }
            }
        }
    }
}
```

### 3. Dashboard Layer: 이벤트 수신 및 표시

**위치**: `dashboard-frontend/src/app/page.tsx`

#### WebSocket 연결

```javascript
// page.tsx - useEffect()
const ws = new WebSocket(WS_URL);

ws.onmessage = (event) => {
    const data = JSON.parse(event.data);

    switch(data.eventType) {
        case 'query_execution':
            handleQueryExecution(data);
            break;
        case 'transaction_event':
            handleTransactionEvent(data);
            break;
        case 'long_running_transaction':
            handleLongRunningTransaction(data);
            break;
        case 'deadlock_event':
            handleDeadlock(data);
            break;
        case 'system_metrics':
            handleSystemMetrics(data);
            break;
    }
};
```

#### UI 표시

각 이벤트 타입은 대시보드의 해당 패널에 실시간 표시됩니다:

1. **Query Execution Panel**: 테이블 형태로 최근 쿼리 목록 표시
2. **Transaction Panel**: 커밋/롤백 이벤트 타임라인
3. **Long-running Transaction Panel**: 알림 배지 + 상세 정보
4. **Deadlock Panel**: 데드락 이벤트 알림 (빨간색 경고)
5. **Connection Pool Panel**: Recharts로 메트릭 차트 표시

## 이벤트 스키마 레퍼런스

자세한 이벤트 스키마는 다음 문서 참조:
- [agent-event-data-structures.md](../../docs/agent-event-data-structures.md)
- [websocket-message-format.md](../../docs/websocket-message-format.md)

## 호환성 검증 체크리스트

이벤트 스키마나 포맷을 변경할 때 다음 사항을 확인:

- [ ] Agent에서 올바른 필드명으로 이벤트 생성
- [ ] Control Plane에서 이벤트 타입 필드 검증
- [ ] Dashboard에서 모든 필드 파싱 가능
- [ ] 이전 버전 이벤트와 하위 호환성 유지
- [ ] `event-pipeline-verification-test.sh` 실행하여 100% 성공 확인

## 트러블슈팅

### 이벤트가 Dashboard에 표시되지 않을 때

1. **Agent 로그 확인**
   ```bash
   kubectl logs <pod-name> -n kubedb-monitor-test | grep "KubeDB Monitor"
   ```
   - Agent가 정상 로드되었는지 확인
   - HTTP 전송 성공 로그 확인

2. **Control Plane 로그 확인**
   ```bash
   kubectl logs deployment/kubedb-monitor-control-plane -n kubedb-monitor-test
   ```
   - 이벤트 수신 로그 확인
   - WebSocket 연결 상태 확인

3. **Dashboard 브라우저 콘솔 확인**
   - WebSocket 연결 상태
   - 수신된 메시지 형식 확인

### 이벤트 타입 불일치 오류

**증상**: Control Plane에서 "Invalid eventType" 오류

**원인**: Agent에서 잘못된 eventType 값 전송

**해결**:
1. Agent 코드에서 eventType 필드값 확인
2. 5가지 표준 이벤트 타입 중 하나인지 검증
3. 대소문자 일치 여부 확인 (snake_case 사용)

## 참고 문서

- [Architecture Overview](./architecture-overview.md)
- [Project Structure](./project-structure.md)
- [Agent JDBC Compatibility Guide](../../docs/agent-jdbc-compatibility-guide.md)
