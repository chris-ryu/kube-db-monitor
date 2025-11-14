# 새로운 이벤트 타입 구현 (SOP)

## 목적

새로운 이벤트 타입을 추가할 때 Agent → Control Plane → Dashboard 전체 파이프라인에서 일관성을 유지하기 위한 표준 절차입니다.

## 언제 사용하는가

- 새로운 모니터링 기능을 추가할 때 (예: `connection_leak_detected`, `slow_query_alert` 등)
- 기존 이벤트 타입의 스키마를 변경할 때
- 새로운 메트릭 수집 기능을 추가할 때

## 이벤트 타입 추가 워크플로우

### 1단계: 이벤트 타입 설계

#### 1.1 이벤트 타입 네이밍

**규칙**:
- snake_case 사용 (예: `connection_leak_detected`)
- 동사_명사 또는 명사_상태 형태 권장
- 명확하고 설명적인 이름 사용

**예시**:
- ✅ `long_running_transaction`
- ✅ `deadlock_detected`
- ✅ `connection_leak_detected`
- ❌ `lrt` (약어 사용 금지)
- ❌ `ConnectionLeakDetected` (camelCase 금지)

#### 1.2 이벤트 스키마 설계

**공통 필드** (모든 이벤트에 포함):
```json
{
  "eventType": "new_event_type",
  "timestamp": 1234567890,
  "podName": "app-xxx",
  "namespace": "default"
}
```

**추가 필드** 설계:
- 이벤트 고유 정보만 포함
- 필드명은 camelCase 사용
- 중복 정보 최소화

**예시** (Connection Leak 이벤트):
```json
{
  "eventType": "connection_leak_detected",
  "timestamp": 1234567890,
  "podName": "app-xxx",
  "namespace": "default",
  "leakedConnections": 5,
  "poolName": "HikariPool-1",
  "stackTrace": "...",
  "severity": "WARNING"
}
```

### 2단계: Agent 레이어 구현

#### 2.1 이벤트 생성 로직 추가

**위치**: `kubedb-monitor-agent/src/main/java/io/kubedb/monitor/agent/`

**해당 클래스 선택**:
- JDBC 관련: `UniversalJDBCInterceptor.java`
- Connection Pool 관련: `ConnectionPoolMonitor.java`
- 트랜잭션 관련: `UniversalJDBCInterceptor.java` (트랜잭션 메서드 인터셉션)
- 새로운 기능: 별도 클래스 생성 (예: `ConnectionLeakDetector.java`)

**예시 코드** (Connection Leak 감지):
```java
// ConnectionLeakDetector.java
public class ConnectionLeakDetector {
    private final MetricsCollector metricsCollector;

    public void detectLeaks() {
        // 누수 감지 로직
        if (leakedConnectionsCount > 0) {
            Map<String, Object> event = new HashMap<>();
            event.put("eventType", "connection_leak_detected");
            event.put("timestamp", System.currentTimeMillis());
            event.put("leakedConnections", leakedConnectionsCount);
            event.put("poolName", poolName);
            event.put("stackTrace", getStackTrace());
            event.put("severity", "WARNING");

            metricsCollector.recordConnectionLeak(event);
        }
    }
}
```

#### 2.2 MetricsCollector에 메서드 추가

**위치**: `kubedb-monitor-agent/src/main/java/io/kubedb/monitor/agent/MetricsCollector.java`

```java
// MetricsCollector.java
public class MetricsCollector {
    // 기존 메서드들...

    public void recordConnectionLeak(Map<String, Object> event) {
        // Pod 및 Namespace 정보 추가
        event.put("podName", System.getenv("HOSTNAME"));
        event.put("namespace", System.getenv("POD_NAMESPACE"));

        // HTTP 전송
        transmitter.transmit(event);
    }
}
```

#### 2.3 유닛 테스트 작성 (TDD)

**위치**: `kubedb-monitor-agent/src/test/java/io/kubedb/monitor/agent/`

```java
// ConnectionLeakDetectorTest.java
@Test
public void testConnectionLeakDetection() {
    // Given
    ConnectionLeakDetector detector = new ConnectionLeakDetector(metricsCollector);

    // When
    detector.detectLeaks();

    // Then
    verify(metricsCollector).recordConnectionLeak(argThat(event ->
        "connection_leak_detected".equals(event.get("eventType"))
    ));
}
```

#### 2.4 Agent 빌드 및 테스트

```bash
# Agent 디렉토리로 이동
cd kubedb-monitor-agent

# 빌드 및 테스트
mvn clean test

# JAR 파일 생성
mvn clean package
```

### 3단계: Control Plane 레이어 구현

#### 3.1 이벤트 타입 검증 추가 (선택 사항)

**위치**: `control-plane/main.go`

Control Plane은 모든 이벤트 타입을 투명하게 전달하므로 일반적으로 수정이 필요 없습니다.

단, 이벤트 타입 화이트리스트를 관리하는 경우:

```go
// main.go - metricsHandler()
var validEventTypes = map[string]bool{
    "query_execution":          true,
    "transaction_event":        true,
    "long_running_transaction": true,
    "deadlock_event":           true,
    "system_metrics":           true,
    "connection_leak_detected": true, // 새로운 이벤트 타입 추가
}

func metricsHandler(w http.ResponseWriter, r *http.Request) {
    var event map[string]interface{}
    json.NewDecoder(r.Body).Decode(&event)

    eventType, ok := event["eventType"].(string)
    if !ok || !validEventTypes[eventType] {
        http.Error(w, "Invalid eventType", http.StatusBadRequest)
        return
    }

    hub.broadcast <- event
}
```

#### 3.2 Control Plane 빌드 및 테스트

```bash
# Control Plane 디렉토리로 이동
cd control-plane

# 빌드
go build -o kubedb-monitor-control-plane main.go

# 로컬 테스트 (선택 사항)
./kubedb-monitor-control-plane
```

### 4단계: Dashboard 레이어 구현

#### 4.1 이벤트 핸들러 추가

**위치**: `dashboard-frontend/src/app/page.tsx`

```typescript
// page.tsx - WebSocket 메시지 수신 핸들러
ws.onmessage = (event) => {
    const data = JSON.parse(event.data);

    switch(data.eventType) {
        // 기존 케이스들...
        case 'connection_leak_detected':
            handleConnectionLeak(data);
            break;
        default:
            console.warn('Unknown event type:', data.eventType);
    }
};
```

#### 4.2 State 관리 추가

```typescript
// page.tsx - State 추가
const [connectionLeaks, setConnectionLeaks] = useState([]);

const handleConnectionLeak = (data) => {
    setConnectionLeaks(prev => [data, ...prev].slice(0, 100)); // 최근 100개만 유지
};
```

#### 4.3 UI 컴포넌트 추가

```tsx
// page.tsx - 새로운 패널 컴포넌트
<div className="connection-leak-panel">
  <h2>Connection Leak Alerts</h2>
  {connectionLeaks.length > 0 ? (
    <table>
      <thead>
        <tr>
          <th>Timestamp</th>
          <th>Leaked Connections</th>
          <th>Pool Name</th>
          <th>Severity</th>
        </tr>
      </thead>
      <tbody>
        {connectionLeaks.map((leak, index) => (
          <tr key={index}>
            <td>{new Date(leak.timestamp).toLocaleString()}</td>
            <td>{leak.leakedConnections}</td>
            <td>{leak.poolName}</td>
            <td className={`severity-${leak.severity.toLowerCase()}`}>
              {leak.severity}
            </td>
          </tr>
        ))}
      </tbody>
    </table>
  ) : (
    <p>No connection leaks detected</p>
  )}
</div>
```

#### 4.4 Dashboard 로컬 테스트

```bash
# Dashboard 디렉토리로 이동
cd dashboard-frontend

# 의존성 설치 (필요 시)
npm install

# 로컬 개발 서버 실행
npm run dev

# 브라우저에서 확인
open http://localhost:3000
```

### 5단계: 통합 테스트

#### 5.1 이벤트 파이프라인 검증 스크립트 업데이트

**위치**: `scripts/event-pipeline-verification-test.sh`

```bash
# 새로운 이벤트 타입 테스트 추가
echo "Testing connection_leak_detected event..."

# 테스트 이벤트 생성 (시뮬레이션)
# ... (구현 내용은 이벤트 타입에 따라 다름)

# 검증
if [ "$connection_leak_detected_count" -gt 0 ]; then
    echo "✅ connection_leak_detected event: PASS"
else
    echo "❌ connection_leak_detected event: FAIL"
fi
```

#### 5.2 전체 파이프라인 테스트 실행

```bash
# 전체 시스템 배포
make build-and-deploy-all

# 이벤트 파이프라인 검증
./scripts/event-pipeline-verification-test.sh
```

**성공 기준**: 새로운 이벤트 타입 포함 모든 이벤트 100% 전송 성공

### 6단계: 문서화

#### 6.1 이벤트 데이터 구조 문서 업데이트

**위치**: `docs/agent-event-data-structures.md`

새로운 이벤트 타입의 스키마를 추가:

```markdown
### connection_leak_detected

**설명**: Connection Pool에서 누수된 연결이 감지되었을 때 발생

**필드**:
- `eventType`: "connection_leak_detected"
- `timestamp`: Unix timestamp (milliseconds)
- `podName`: Pod 이름
- `namespace`: Namespace
- `leakedConnections`: 누수된 연결 수 (integer)
- `poolName`: Connection Pool 이름 (string)
- `stackTrace`: 스택 트레이스 (string, optional)
- `severity`: 심각도 (string: "INFO", "WARNING", "ERROR")

**예시**:
\```json
{
  "eventType": "connection_leak_detected",
  "timestamp": 1234567890,
  "podName": "app-xxx",
  "namespace": "default",
  "leakedConnections": 5,
  "poolName": "HikariPool-1",
  "stackTrace": "...",
  "severity": "WARNING"
}
\```
```

#### 6.2 Event Pipeline 문서 업데이트

**위치**: `.agent/system/event-pipeline.md`

지원 이벤트 타입 목록에 추가:

```markdown
## 지원 이벤트 타입 (총 6가지)

1. **`query_execution`**: SQL 쿼리 실행 메트릭
2. **`transaction_event`**: 트랜잭션 커밋/롤백 이벤트
3. **`long_running_transaction`**: Long-running transaction 알림
4. **`deadlock_event`**: 데드락 감지
5. **`system_metrics`**: Connection Pool 등 시스템 메트릭
6. **`connection_leak_detected`**: Connection Leak 감지 (신규)
```

#### 6.3 README.md 업데이트

프로젝트 루트의 README.md에 새로운 기능 추가를 기록합니다.

### 7단계: 배포 및 모니터링

#### 7.1 배포

```bash
# 전체 시스템 배포
make build-and-deploy-all

# 배포 상태 확인
make status
```

#### 7.2 실제 이벤트 발생 테스트

```bash
# 테스트 시나리오 실행 (예: Connection Leak 유발)
# ... (이벤트 타입에 따라 다름)

# Dashboard에서 실시간 확인
open https://kube-db-mon-dashboard.bitgaram.info
```

#### 7.3 로그 모니터링

```bash
# Agent 로그 확인 (새로운 이벤트 생성 확인)
kubectl logs deployment/university-registration-demo -n kubedb-monitor-test | grep "connection_leak_detected"

# Control Plane 로그 확인 (이벤트 수신 확인)
kubectl logs deployment/kubedb-monitor-control-plane -n kubedb-monitor-test | grep "connection_leak_detected"
```

## 체크리스트

- [ ] 이벤트 타입 네이밍 규칙 준수 (snake_case)
- [ ] 이벤트 스키마 설계 완료 (공통 필드 + 고유 필드)
- [ ] Agent 레이어: 이벤트 생성 로직 구현
- [ ] Agent 레이어: MetricsCollector 메서드 추가
- [ ] Agent 레이어: 유닛 테스트 작성 및 통과
- [ ] Control Plane 레이어: 이벤트 타입 검증 추가 (필요 시)
- [ ] Dashboard 레이어: 이벤트 핸들러 추가
- [ ] Dashboard 레이어: State 관리 추가
- [ ] Dashboard 레이어: UI 컴포넌트 구현
- [ ] 통합 테스트: event-pipeline-verification-test.sh 업데이트
- [ ] 통합 테스트: 전체 파이프라인 테스트 100% 통과
- [ ] 문서화: agent-event-data-structures.md 업데이트
- [ ] 문서화: event-pipeline.md 업데이트
- [ ] 문서화: README.md 업데이트
- [ ] 배포 및 실제 환경 테스트 완료
- [ ] 30분 로그 모니터링 완료 (에러 없음)

## 호환성 주의 사항

### Agent → Control Plane 호환성

- **eventType 필드는 필수**: Control Plane에서 이벤트 타입을 기반으로 라우팅
- **타입 불일치 방지**: Agent에서 보내는 eventType 값과 Dashboard에서 처리하는 값이 정확히 일치해야 함

### Control Plane → Dashboard 호환성

- **모든 필드가 JSON 직렬화 가능해야 함**
- **WebSocket 메시지 크기 제한**: 과도하게 큰 데이터 전송 금지 (stackTrace 등은 truncate)

### 하위 호환성 유지

- **기존 이벤트 타입 스키마 변경 금지**: 새로운 필드 추가는 가능하지만, 기존 필드 제거나 타입 변경은 불가
- **선택적 필드 사용**: 새로운 필드는 optional로 설계하여 이전 버전과 호환성 유지

## 관련 문서

- [Architecture Overview](../system/architecture-overview.md)
- [Event Pipeline](../system/event-pipeline.md)
- [Agent Modification Workflow](./agent-modification-workflow.md)
- [Deployment Workflow](./deployment-workflow.md)
- [Agent Event Data Structures](../../docs/agent-event-data-structures.md)

## 버전 히스토리

- **v1.0** (2025-10-08): 초기 SOP 작성
