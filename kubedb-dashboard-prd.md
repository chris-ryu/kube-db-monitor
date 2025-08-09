# KubeDB Monitor 실시간 대시보드 PRD

## 🎯 비전 및 목표

### 비전
**제니퍼(Jennifer) APM과 같은 수준의 아름답고 직관적인 데이터베이스 성능 모니터링 대시보드 구현**

### 핵심 목표
- 🎨 **시각적 우수성**: 제니퍼 수준의 아름다운 UI/UX
- ⚡ **실시간 모니터링**: 밀리초 단위 실시간 데이터 시각화
- 🎭 **애니메이션 효과**: 자연스럽고 직관적인 데이터 플로우 표현
- 🔧 **제로 의존성**: 외부 서비스 의존성 최소화
- 🚀 **고성능**: 대용량 데이터 처리 및 실시간 렌더링

---

## 🏗️ 시스템 아키텍처

### 전체 구성도
```
┌─────────────────────────────────────────────────────────┐
│                   Web Dashboard                         │
│  ┌─────────────┐ ┌─────────────┐ ┌─────────────┐       │
│  │ Real-time   │ │ Analytics   │ │ Alert       │       │
│  │ Monitoring  │ │ Dashboard   │ │ Center      │       │
│  └─────────────┘ └─────────────┘ └─────────────┘       │
└─────────────────┬───────────────────────────────────────┘
                  │ WebSocket/SSE
┌─────────────────┴───────────────────────────────────────┐
│            KubeDB Monitor Control Plane                │
│  ┌─────────────┐ ┌─────────────┐ ┌─────────────┐       │
│  │ Data        │ │ Metrics     │ │ Alert       │       │
│  │ Collector   │ │ Processor   │ │ Engine      │       │
│  └─────────────┘ └─────────────┘ └─────────────┘       │
└─────────────────┬───────────────────────────────────────┘
                  │ gRPC/HTTP
┌─────────────────┴───────────────────────────────────────┐
│                   Agent Layer                          │
│  ┌─────────────┐ ┌─────────────┐ ┌─────────────┐       │
│  │ JDBC        │ │ Connection  │ │ Performance │       │
│  │ Interceptor │ │ Monitor     │ │ Profiler    │       │
│  └─────────────┘ └─────────────┘ └─────────────┘       │
└─────────────────────────────────────────────────────────┘
```

### 계층별 상세 설계

#### 1️⃣ **Agent Layer** (KubeDB Monitor Agent 개선)
**목표**: 풍부한 메트릭 데이터 수집 및 실시간 전송

```java
// 개선된 Agent 구조
public class KubeDBMonitorAgent {
    - JDBCInterceptor: SQL 쿼리 인터셉션
    - ConnectionPoolMonitor: 커넥션 풀 모니터링
    - PerformanceProfiler: 실행 계획 분석
    - MetricsCollector: 통계 데이터 수집
    - StreamingReporter: 실시간 데이터 전송
}
```

**JSON 로그 포맷 개선**:
```json
{
  "timestamp": "2025-08-09T10:30:45.123Z",
  "pod_name": "university-registration-demo-abc123",
  "namespace": "kubedb-monitor-test",
  "event_type": "query_execution",
  "data": {
    "query_id": "q_001_20250809103045",
    "sql_hash": "sha256:abc123...",
    "sql_pattern": "SELECT * FROM ? WHERE ? = ?",
    "sql_type": "SELECT",
    "table_names": ["students", "departments"],
    "execution_time_ms": 45,
    "rows_affected": 23,
    "connection_id": "conn_pool_001",
    "thread_name": "http-nio-8080-exec-7",
    "memory_used_bytes": 2048576,
    "cpu_time_ms": 12,
    "io_read_bytes": 4096,
    "io_write_bytes": 1024,
    "lock_time_ms": 2,
    "status": "success",
    "error_code": null,
    "error_message": null,
    "explain_plan": {...},
    "stack_trace": [...]
  },
  "context": {
    "request_id": "req_user_registration_001",
    "user_session": "session_abc123",
    "api_endpoint": "/api/students",
    "business_operation": "student_enrollment"
  },
  "metrics": {
    "connection_pool_active": 5,
    "connection_pool_idle": 3,
    "connection_pool_max": 10,
    "heap_used_mb": 128,
    "heap_max_mb": 512,
    "gc_count": 2,
    "gc_time_ms": 15
  }
}
```

#### 2️⃣ **Control Plane** (새로 구현)
**목표**: 고성능 데이터 처리 및 실시간 스트리밍

**기술 스택**:
- **언어**: Go (고성능, 경량화)
- **데이터 저장**: ClickHouse (시계열 데이터 최적화)
- **스트리밍**: WebSocket + Server-Sent Events
- **캐싱**: Redis (실시간 메트릭 캐싱)

**주요 컴포넌트**:
```go
// Control Plane 구조
type ControlPlane struct {
    DataCollector   *collector.Service    // Agent로부터 데이터 수집
    MetricsProcessor *processor.Service   // 실시간 메트릭 계산
    AlertEngine     *alert.Engine        // 임계값 기반 알림
    WebSocketHub    *websocket.Hub       // 실시간 데이터 전송
    TimeSeriesDB    *clickhouse.Client   // 시계열 데이터 저장
    MetricsCache    *redis.Client        // 실시간 캐시
}
```

#### 3️⃣ **Web Dashboard** (React/Next.js)
**목표**: 제니퍼 수준의 아름다운 실시간 대시보드

**기술 스택**:
- **프론트엔드**: React 18 + Next.js 14
- **상태 관리**: Zustand + React Query
- **시각화**: D3.js + Canvas/WebGL
- **애니메이션**: Framer Motion + GSAP
- **스타일링**: Tailwind CSS + Styled Components

---

## 🎨 UI/UX 설계

### 메인 대시보드 구성

#### 1. **실시간 쿼리 플로우 시각화**
```
┌─────────────────────────────────────────────────────────┐
│                Real-time Query Flow                    │
│                                                         │
│  [App] ──SQL──> [Pool] ──Query──> [DB] ──Result──> [App] │
│    🔄           💾 5/10           🗄️            📊      │
│                                                         │
│  ╭─ 실행중인 쿼리 애니메이션 ─────────────────────────╮  │
│  │ SELECT * FROM students WHERE...    ⏱️ 45ms      │  │
│  │ INSERT INTO departments...         ⏱️ 12ms      │  │
│  ╰─────────────────────────────────────────────────────╯  │
└─────────────────────────────────────────────────────────┘
```

#### 2. **성능 메트릭 카드**
```
┌─────────────┐ ┌─────────────┐ ┌─────────────┐ ┌─────────────┐
│   QPS       │ │ Avg Latency │ │ Active Conn │ │ Error Rate  │
│   🔥 1,234  │ │   ⚡ 23ms   │ │   🔗 15/50  │ │   ❌ 0.1%  │
│   ↗️ +12%   │ │   ↘️ -5ms   │ │   📈 Graph  │ │   ↘️ -0.2% │
└─────────────┘ └─────────────┘ └─────────────┘ └─────────────┘
```

#### 3. **쿼리 히트맵**
```
┌─────────────────────────────────────────────────────────┐
│              Query Heatmap (Last 24h)                  │
│                                                         │
│ 00:00 ████████████████████████████████████████ 23:59   │
│ Slow  🔴🔴🔴⚪⚪⚪⚪🟡🟡🟡🔴🔴🔴⚪⚪⚪⚪🟢🟢🟢 Fast │
│                                                         │
│ 📊 Peak: 14:30 (2,456 QPS) | Slow: 09:15 (145ms avg)  │
└─────────────────────────────────────────────────────────┘
```

### 상세 분석 대시보드

#### 4. **SQL 분석 차트**
```
┌─────────────────┐ ┌─────────────────┐ ┌─────────────────┐
│ Query Types     │ │ Table Access    │ │ Execution Time  │
│                 │ │                 │ │                 │
│ 🔍 SELECT  68%  │ │ students    45% │ │ 🟢 <10ms   72% │
│ 📝 INSERT  18%  │ │ courses     28% │ │ 🟡 10-50ms  23% │
│ ✏️ UPDATE  12%  │ │ enrollments 15% │ │ 🔴 >50ms     5% │
│ 🗑️ DELETE   2%  │ │ others      12% │ │                 │
└─────────────────┘ └─────────────────┘ └─────────────────┘
```

### 실시간 애니메이션 효과

#### 1. **쿼리 흐름 애니메이션**
- SQL 쿼리가 실행될 때마다 파티클 효과
- 커넥션 풀에서 DB로 흐르는 데이터 스트림
- 응답 시간에 따른 색상 변화 (빠름:녹색 → 보통:노랑 → 느림:빨강)

#### 2. **메트릭 카운터 애니메이션**
- 숫자 증가/감소 시 부드러운 트랜지션
- 임계값 초과 시 펄스 효과
- 트렌드 화살표 회전 애니메이션

#### 3. **차트 업데이트 애니메이션**
- 실시간 데이터 추가 시 smooth transition
- 라인 차트의 그라데이션 효과
- 도넛 차트의 회전 애니메이션

---

## 🔧 기술 구현 상세

### Phase 1: Agent 개선 (1-2주)

#### 1.1 JSON 로그 포맷 구현
```java
@Component
public class StructuredLogger {
    private static final ObjectMapper mapper = new ObjectMapper();
    
    public void logQueryExecution(QueryMetrics metrics) {
        LogEvent event = LogEvent.builder()
            .timestamp(Instant.now())
            .eventType("query_execution")
            .data(metrics)
            .context(getCurrentContext())
            .metrics(getSystemMetrics())
            .build();
            
        // 구조화된 JSON 로그 출력
        System.out.println("KUBEDB_METRICS: " + mapper.writeValueAsString(event));
    }
}
```

#### 1.2 성능 메트릭 수집 개선
```java
@Component
public class PerformanceProfiler {
    public QueryMetrics profileQuery(String sql, Supplier<Object> queryExecution) {
        long startTime = System.nanoTime();
        long startMemory = getUsedMemory();
        
        try {
            Object result = queryExecution.get();
            long executionTime = (System.nanoTime() - startTime) / 1_000_000;
            
            return QueryMetrics.builder()
                .executionTimeMs(executionTime)
                .memoryUsedBytes(getUsedMemory() - startMemory)
                .sqlHash(generateSqlHash(sql))
                .sqlPattern(extractSqlPattern(sql))
                .tableNames(extractTableNames(sql))
                .queryType(detectQueryType(sql))
                .build();
                
        } catch (Exception e) {
            // 에러 메트릭 수집
            return QueryMetrics.error(e);
        }
    }
}
```

### Phase 2: Control Plane 구현 (3-4주)

#### 2.1 데이터 수집 서비스
```go
// main.go
func main() {
    hub := websocket.NewHub()
    collector := collector.NewService(hub)
    processor := processor.NewService(hub)
    
    // Agent 로그 수집
    go collector.CollectLogs("/var/log/kubedb-monitor")
    
    // 메트릭 처리
    go processor.ProcessMetrics()
    
    // WebSocket 서버
    http.HandleFunc("/ws", hub.HandleWebSocket)
    
    // REST API
    http.HandleFunc("/api/metrics", handleMetrics)
    
    log.Fatal(http.ListenAndServe(":8080", nil))
}
```

#### 2.2 실시간 데이터 스트리밍
```go
// websocket/hub.go
type Hub struct {
    clients    map[*Client]bool
    broadcast  chan []byte
    register   chan *Client
    unregister chan *Client
}

func (h *Hub) BroadcastMetrics(metrics *QueryMetrics) {
    data, _ := json.Marshal(WebSocketMessage{
        Type: "query_execution",
        Data: metrics,
        Timestamp: time.Now(),
    })
    
    select {
    case h.broadcast <- data:
    default:
        close(h.broadcast)
    }
}
```

### Phase 3: 웹 대시보드 구현 (4-5주)

#### 3.1 실시간 데이터 연결
```typescript
// hooks/useRealTimeMetrics.ts
export function useRealTimeMetrics() {
  const [metrics, setMetrics] = useState<QueryMetrics[]>([]);
  
  useEffect(() => {
    const ws = new WebSocket('ws://localhost:8080/ws');
    
    ws.onmessage = (event) => {
      const message = JSON.parse(event.data);
      
      if (message.type === 'query_execution') {
        setMetrics(prev => [
          message.data,
          ...prev.slice(0, 999) // 최근 1000개만 유지
        ]);
      }
    };
    
    return () => ws.close();
  }, []);
  
  return { metrics, isConnected: ws.readyState === WebSocket.OPEN };
}
```

#### 3.2 애니메이션 컴포넌트
```typescript
// components/QueryFlowAnimation.tsx
export function QueryFlowAnimation() {
  const { metrics } = useRealTimeMetrics();
  const controls = useAnimation();
  
  useEffect(() => {
    if (metrics.length > 0) {
      const latestQuery = metrics[0];
      
      // 쿼리 실행 애니메이션
      controls.start({
        x: [0, 200, 400],
        opacity: [0, 1, 0],
        transition: {
          duration: latestQuery.executionTimeMs / 1000,
          ease: "easeInOut"
        }
      });
    }
  }, [metrics]);
  
  return (
    <motion.div
      animate={controls}
      className="query-particle"
    >
      💫
    </motion.div>
  );
}
```

#### 3.3 실시간 차트
```typescript
// components/RealTimeChart.tsx
export function RealTimeChart() {
  const { metrics } = useRealTimeMetrics();
  const chartData = useMemo(() => 
    processMetricsForChart(metrics), [metrics]
  );
  
  return (
    <ResponsiveContainer width="100%" height={300}>
      <LineChart data={chartData}>
        <Line 
          type="monotone" 
          dataKey="qps" 
          stroke="#00ff88"
          strokeWidth={2}
          dot={false}
          animationDuration={300}
        />
        <XAxis dataKey="timestamp" />
        <YAxis />
        <Tooltip 
          content={<CustomTooltip />}
          animationDuration={100}
        />
      </LineChart>
    </ResponsiveContainer>
  );
}
```

---

## 🎨 Design System

### 색상 팔레트 (제니퍼 스타일)
```css
:root {
  /* Primary Colors */
  --color-primary: #00ff88;      /* 네온 그린 */
  --color-secondary: #0099ff;    /* 사이버 블루 */
  --color-accent: #ff6b35;       /* 경고 오렌지 */
  
  /* Status Colors */
  --color-success: #00ff88;
  --color-warning: #ffab00;
  --color-error: #ff5252;
  --color-info: #2196f3;
  
  /* Background */
  --bg-dark: #0a0a0a;           /* 다크 배경 */
  --bg-surface: #1a1a1a;        /* 카드 배경 */
  --bg-elevated: #2a2a2a;       /* 높은 배경 */
  
  /* Text */
  --text-primary: #ffffff;
  --text-secondary: #b0b0b0;
  --text-muted: #666666;
}
```

### 애니메이션 라이브러리
```typescript
// animations/queryFlow.ts
export const queryFlowAnimations = {
  queryExecution: {
    initial: { x: -100, opacity: 0, scale: 0.8 },
    animate: { 
      x: 0, 
      opacity: 1, 
      scale: 1,
      transition: { duration: 0.5, ease: "easeOut" }
    },
    exit: { 
      x: 100, 
      opacity: 0, 
      scale: 0.8,
      transition: { duration: 0.3 }
    }
  },
  
  metricUpdate: {
    initial: { scale: 1 },
    animate: { 
      scale: [1, 1.1, 1],
      transition: { duration: 0.2 }
    }
  },
  
  alertPulse: {
    animate: {
      scale: [1, 1.05, 1],
      opacity: [1, 0.8, 1],
      transition: { 
        duration: 2,
        repeat: Infinity,
        ease: "easeInOut"
      }
    }
  }
};
```

---

## 📊 핵심 메트릭 정의

### 1. 성능 메트릭
- **QPS** (Queries Per Second): 초당 쿼리 수
- **평균 응답시간**: 쿼리 실행 평균 시간
- **95th Percentile**: 95% 쿼리의 응답시간
- **느린 쿼리 비율**: 임계값 초과 쿼리 비율

### 2. 리소스 메트릭
- **커넥션 풀 사용률**: Active/Total 커넥션 비율
- **메모리 사용량**: Heap 메모리 사용률
- **CPU 사용률**: 데이터베이스 연산 CPU 사용률
- **I/O 처리량**: 읽기/쓰기 바이트 수

### 3. 비즈니스 메트릭
- **테이블별 접근 빈도**: 각 테이블의 쿼리 수
- **오퍼레이션별 분포**: SELECT/INSERT/UPDATE/DELETE 비율
- **사용자 세션별 활동**: 동시 접속 사용자 수
- **에러율**: 실패한 쿼리 비율

---

## 🚀 구현 로드맵

### Phase 1: Foundation (2주)
- [ ] Agent JSON 로그 포맷 구현
- [ ] 성능 메트릭 수집 개선
- [ ] Control Plane 기본 구조 설계

### Phase 2: Data Pipeline (3주)
- [ ] 실시간 로그 수집 시스템
- [ ] 메트릭 처리 엔진 구현
- [ ] WebSocket 스트리밍 서버

### Phase 3: Dashboard Core (4주)
- [ ] React 대시보드 기본 구조
- [ ] 실시간 데이터 연결
- [ ] 기본 차트 컴포넌트 구현

### Phase 4: Advanced UI (3주)
- [ ] 쿼리 플로우 애니메이션
- [ ] 메트릭 카드 애니메이션
- [ ] 인터랙티브 차트 구현

### Phase 5: Polish & Optimization (2주)
- [ ] 성능 최적화
- [ ] 애니메이션 polish
- [ ] 반응형 디자인
- [ ] 다크모드 완성

---

## 💡 성공 기준

### 기능적 요구사항
- ✅ 실시간 쿼리 모니터링 (< 100ms 지연)
- ✅ 1000+ QPS 처리 능력
- ✅ 24시간 연속 운영 안정성
- ✅ 반응형 웹 대시보드

### 비기능적 요구사항
- 🎨 제니퍼 수준의 UI/UX 품질
- ⚡ 60fps 부드러운 애니메이션
- 📱 모바일/태블릿 완벽 지원
- 🔧 외부 의존성 최소화

### 사용자 경험
- 👀 직관적인 데이터 시각화
- 🎭 매력적인 애니메이션 효과
- 🚨 즉각적인 이상 상황 알림
- 📊 풍부한 분석 정보 제공

이 PRD를 바탕으로 제니퍼와 같은 수준의 아름다운 실시간 DB 모니터링 대시보드를 구현할 수 있을 것입니다!