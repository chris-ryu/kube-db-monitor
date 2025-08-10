# Real-time Query Flow 데모 가이드

Real-time Query Flow 패널의 애니메이션 효과를 확인할 수 있는 데모용 샘플 데이터 스크립트입니다.

## 🚀 빠른 시작

### 1. 필요한 도구 설치

```bash
# macOS (Homebrew)
brew install bc websocat

# Ubuntu/Debian
sudo apt-get update
sudo apt-get install bc
npm install -g wscat

# 또는 curl 사용 (대부분 시스템에 기본 설치됨)
```

### 2. 백엔드 서버 실행

```bash
# control-plane 서버 실행 (포트 8080)
cd control-plane
go run main.go
```

### 3. 프론트엔드 대시보드 실행

```bash
# dashboard-frontend 실행 (포트 3000)
cd dashboard-frontend  
npm run dev
```

### 4. 데모 스크립트 실행

```bash
# 데모 스크립트 실행 (60초간)
./demo-query-flow.sh
```

브라우저에서 `http://localhost:3000`으로 접속하여 Real-time Query Flow 패널의 애니메이션을 확인하세요.

## 📊 데모 시나리오

스크립트는 15초마다 자동으로 다음 시나리오들을 순환합니다:

### NORMAL (정상 상태)
- **QPS**: 2 queries/sec
- **지연시간**: 정상
- **에러율**: 최소

### HIGH_LOAD (높은 부하)
- **QPS**: 6 queries/sec (3배 증가)
- **지연시간**: 1.5배 증가
- **에러율**: 1.2배 증가

### SLOW_QUERIES (느린 쿼리)
- **QPS**: 1.6 queries/sec (80% 수준)
- **지연시간**: 3배 증가
- **에러율**: 2배 증가

### ERROR_SPIKE (에러 급증)
- **QPS**: 2.4 queries/sec (1.2배 증가)
- **지연시간**: 정상
- **에러율**: 5배 증가

## 🎯 확인할 수 있는 애니메이션 효과

### 1. Flow Diagram
- App → Pool → DB → App 노드 간 연결선 애니메이션
- 활성 커넥션 수 실시간 업데이트

### 2. Query Particles
- 쿼리 실행 시간에 따른 파티클 색상 변화:
  - 🟢 **초록**: 10ms 이하 (빠른 쿼리)
  - 🟠 **주황**: 10-50ms (보통 쿼리)
  - 🟡 **노랑**: 50ms 이상 (느린 쿼리)
  - 🔴 **빨강**: 에러 발생

### 3. Real-time Query List
- 새로운 쿼리 슬라이드 인 애니메이션
- 실행 시간별 색상 구분
- 에러 쿼리 강조 표시

### 4. Connection Status
- WebSocket 연결 상태 실시간 표시
- 연결 중일 때 회전 애니메이션

## 🔧 스크립트 구성

### 쿼리 패턴
```sql
SELECT * FROM users WHERE id = ?                     # 빠른 단순 조회
SELECT u.*, p.* FROM users u JOIN profiles p...      # 조인 쿼리
UPDATE users SET last_login = NOW() WHERE id = ?     # 업데이트
INSERT INTO orders (user_id, product_id...)          # 삽입
SELECT COUNT(*) FROM orders WHERE created_at >= ?    # 집계 쿼리 (중간 속도)
SELECT o.*, u.username, p.name FROM orders o...      # 복잡한 조인 (느림)
DELETE FROM sessions WHERE expires_at < NOW()        # 삭제
```

### 메트릭 데이터
- **커넥션 풀**: 활성/유휴/최대 커넥션 수
- **시스템 메트릭**: 힙 메모리, CPU 사용률
- **쿼리 메트릭**: 실행 시간, 상태, 에러 메시지

## 🎮 사용법

```bash
# 기본 실행 (60초)
./demo-query-flow.sh

# 중단하려면
Ctrl+C
```

## 🔍 로그 확인

스크립트 실행 중 다음과 같은 로그가 출력됩니다:

```
🚀 Real-time Query Flow 데모 시작
📡 WebSocket URL: ws://localhost:8080/ws
⏱️  실행 시간: 60초
📊 기본 QPS: 2

🎬 데이터 생성 시작...

📊 생성된 쿼리: 10 | 시나리오: NORMAL | QPS: 2.00
🔄 시나리오 전환: HIGH_LOAD  
📊 생성된 쿼리: 20 | 시나리오: HIGH_LOAD | QPS: 6.00
🔄 시나리오 전환: SLOW_QUERIES
📊 생성된 쿼리: 30 | 시나리오: SLOW_QUERIES | QPS: 1.60
```

## 🐛 문제 해결

### WebSocket 연결 실패
```bash
# control-plane 서버가 실행 중인지 확인
curl -I http://localhost:8080/health

# 포트 사용 확인
lsof -i :8080
```

### 도구 누락 오류
```bash
# bc 설치 확인
which bc || echo "bc를 설치하세요"

# WebSocket 클라이언트 확인
which websocat || which wscat || which curl
```

### 권한 오류
```bash
# 스크립트 실행 권한 부여
chmod +x demo-query-flow.sh
```

## 📝 커스터마이징

스크립트 상단의 설정값을 수정하여 동작을 조정할 수 있습니다:

```bash
WS_URL="ws://localhost:8080/ws"    # WebSocket URL
DEMO_DURATION=60                   # 실행 시간 (초)
BASE_QPS=2                         # 기본 QPS
```

## 🎬 데모 시연 순서

1. **준비**: 백엔드 서버 + 프론트엔드 실행
2. **확인**: 브라우저에서 대시보드 접속
3. **실행**: `./demo-query-flow.sh` 실행
4. **관찰**: Real-time Query Flow 패널의 애니메이션 효과 확인
   - 파티클이 App → Pool → DB → App 경로로 이동
   - 쿼리 속도에 따른 색상 변화
   - 시나리오별 QPS 및 에러율 변화
   - 실시간 쿼리 목록 업데이트
5. **시나리오**: 15초마다 자동 전환되는 성능 시나리오 관찰