# KubeDB Monitor Dashboard를 위한 로그 데이터 변환 계획

## 📋 현재 상태 분석

### 현재 로그 형태
```
09:55:57.910 [http-nio-8080-exec-7] DEBUG o.s.jdbc.datasource.DataSourceUtils - Fetching JDBC Connection from DataSource
JDBC Method intercepted: org/springframework/jdbc/core/JdbcTemplate.execute
```

### 문제점
1. **비구조화 데이터**: 단순 텍스트 형태로 파싱 어려움
2. **정보 부족**: 쿼리 시간, SQL 내용, 성능 메트릭 부재
3. **컨텍스트 부족**: 요청 ID, 사용자 정보, 비즈니스 로직 연결 불가

## 🎯 Dashboard UI 요구사항

### 필수 기능들
- **실시간 쿼리 모니터링**: 현재 실행 중인 쿼리 현황
- **성능 분석**: 쿼리 실행 시간, 느린 쿼리 감지
- **트래픽 패턴**: 시간대별 DB 접근 패턴
- **에러 모니터링**: 실패한 쿼리 및 에러 통계
- **리소스 사용량**: 커넥션 풀, 메모리 사용량

## 🔧 로그 데이터 변환 전략

### 1단계: Agent 레벨에서 로그 포맷 개선

#### 현재 형태
```
JDBC Method intercepted: org/springframework/jdbc/core/JdbcTemplate.execute
```

#### 개선된 형태 (JSON)
```json
{
  "timestamp": "2025-08-09T09:55:57.910Z",
  "level": "INFO",
  "source": "kubedb-monitor-agent",
  "event_type": "jdbc_method_intercepted",
  "data": {
    "method": "org/springframework/jdbc/core/JdbcTemplate.execute",
    "execution_time_ms": 15,
    "database_type": "h2",
    "connection_id": "conn_001",
    "thread_name": "http-nio-8080-exec-7",
    "sql_hash": "abc123def456",
    "query_type": "SELECT|INSERT|UPDATE|DELETE",
    "table_name": "departments",
    "row_count": 1,
    "status": "success|error",
    "error_message": null
  },
  "trace": {
    "request_id": "req_789",
    "span_id": "span_001",
    "parent_span_id": null
  }
}
```

### 2단계: 로그 수집 및 파싱 시스템

#### Option A: Fluent Bit + Elasticsearch
```yaml
apiVersion: v1
kind: ConfigMap
metadata:
  name: fluent-bit-config
data:
  fluent-bit.conf: |
    [SERVICE]
        Flush         1
        Log_Level     info
        Daemon        off
        Parsers_File  parsers.conf

    [INPUT]
        Name              tail
        Path              /var/log/containers/university-registration-demo*.log
        Parser            kubedb-monitor
        Tag               kubedb.monitor
        Refresh_Interval  5

    [FILTER]
        Name                kubernetes
        Match               kubedb.monitor
        Kube_URL            https://kubernetes.default.svc:443
        Merge_Log           On

    [FILTER]
        Name          parser
        Match         kubedb.monitor
        Key_Name      log
        Parser        kubedb-json
        Reserve_Data  On

    [OUTPUT]
        Name  es
        Match kubedb.monitor
        Host  elasticsearch
        Port  9200
        Index kubedb-monitor
```

#### Option B: Promtail + Loki
```yaml
apiVersion: v1
kind: ConfigMap
metadata:
  name: promtail-config
data:
  promtail.yaml: |
    server:
      http_listen_port: 9080
      grpc_listen_port: 0

    clients:
      - url: http://loki:3100/loki/api/v1/push

    scrape_configs:
      - job_name: kubedb-monitor
        static_configs:
          - targets:
              - localhost
            labels:
              job: kubedb-monitor
              __path__: /var/log/pods/kubedb-monitor-test_university-registration-demo-*/*/*.log
        
        pipeline_stages:
          - match:
              selector: '{job="kubedb-monitor"}'
              stages:
                - regex:
                    expression: '^(?P<timestamp>\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}\\.\\d{3}Z) \\[(?P<thread>[^\\]]+)\\] (?P<level>\\w+) (?P<logger>[^ ]+) -- (?P<message>.+)$'
                - timestamp:
                    source: timestamp
                    format: RFC3339Nano
                - labels:
                    thread:
                    level:
                    logger:
```

### 3단계: 실시간 대시보드 구현

#### Grafana 대시보드 예시
```json
{
  "dashboard": {
    "title": "KubeDB Monitor Dashboard",
    "panels": [
      {
        "title": "Database Queries Per Second",
        "type": "stat",
        "targets": [
          {
            "expr": "rate(kubedb_queries_total[5m])",
            "legendFormat": "QPS"
          }
        ]
      },
      {
        "title": "Query Execution Time",
        "type": "histogram",
        "targets": [
          {
            "expr": "kubedb_query_duration_seconds",
            "legendFormat": "Execution Time"
          }
        ]
      },
      {
        "title": "Slow Queries",
        "type": "table",
        "targets": [
          {
            "expr": "kubedb_slow_queries",
            "format": "table"
          }
        ]
      }
    ]
  }
}
```

## 🚀 구현 로드맵

### Phase 1: 로그 포맷 개선 (2-3일)
- [ ] KubeDB Agent에 JSON 로그 포맷 추가
- [ ] 쿼리 실행 시간 측정 기능
- [ ] 에러 핸들링 및 상세 메타데이터 수집

### Phase 2: 로그 수집 파이프라인 (3-5일)
- [ ] Fluent Bit 또는 Promtail 설정
- [ ] Elasticsearch/Loki 클러스터 구성
- [ ] 로그 파싱 규칙 및 인덱싱 최적화

### Phase 3: 대시보드 구현 (5-7일)
- [ ] Grafana 대시보드 설계
- [ ] 실시간 메트릭 시각화
- [ ] 알림 및 임계값 설정
- [ ] 사용자 권한 및 접근 제어

### Phase 4: 고도화 기능 (추가)
- [ ] 쿼리 프로파일링 및 최적화 제안
- [ ] 머신러닝 기반 이상 탐지
- [ ] 성능 트렌드 분석
- [ ] 자동 스케일링 연동

## 📊 예상 결과물

### 1. 실시간 모니터링 대시보드
- 현재 DB 연결 상태
- 초당 쿼리 수 (QPS)
- 평균/최대 응답 시간
- 활성 쿼리 목록

### 2. 성능 분석 대시보드  
- 느린 쿼리 TOP 10
- 시간대별 성능 트렌드
- 테이블별 접근 패턴
- 인덱스 사용률 분석

### 3. 운영 대시보드
- 에러율 및 실패 쿼리
- 커넥션 풀 상태
- 메모리/CPU 사용률
- 알림 및 이벤트 로그

## 🔧 기술 스택 권장사항

### 로그 수집
- **Fluent Bit**: 경량화, 성능 우수
- **Vector**: 높은 처리량, 복잡한 변환 지원

### 저장소
- **Elasticsearch**: 복잡한 쿼리, 풀텍스트 검색
- **Loki**: 비용 효율적, 라벨 기반 쿼리

### 시각화
- **Grafana**: 업계 표준, 다양한 데이터 소스 지원
- **Kibana**: Elasticsearch와 완벽 통합

### 메트릭 수집 (추가)
- **Prometheus**: 시계열 데이터, 알림
- **StatsD**: 커스텀 메트릭 수집