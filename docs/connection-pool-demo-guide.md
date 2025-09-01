# 📊 Connection Pool 모니터링 데모 가이드

## 🏊‍♂️ Connection Pool이란?

### 💡 **쉬운 예시: 택시 승강장**

Connection Pool을 **택시 승강장**으로 생각해보세요!

```
🚖🚖🚖🚖🚖🚖🚖🚖🚖🚖  ← 택시 승강장 (최대 10대)
```

- **택시 승강장**: Connection Pool
- **각 택시**: Database Connection
- **승객**: Application의 요청들

### 📈 **상태별 설명**

#### 1️⃣ **평상시 (낮은 부하)**
```
🚖🚖💤💤💤💤💤💤💤💤  
사용중: 2대    대기중: 0대    총: 2대    최대: 10대
```
- **Active Connections**: 2 (승객을 태우고 운행 중)
- **Idle Connections**: 0 (대기 중인 택시 없음)
- **Total Connections**: 2 (현재 승강장에 있는 택시)
- **Max Connections**: 10 (승강장 최대 수용 가능)

#### 2️⃣ **보통 시간대**
```
🚖💤💤💤💤💤💤💤💤💤  
사용중: 1대    대기중: 4대    총: 5대    최대: 10대
```
- 승객 감소 → 일부 택시는 대기 상태

#### 3️⃣ **러시아워 (높은 부하)**
```
🚖🚖🚖🚖🚖🚖🚖💤💤💤  
사용중: 7대    대기중: 1대    총: 8대    최대: 10대
```
- 승객 증가 → 더 많은 택시가 운행

#### 4️⃣ **최대 부하**
```
🚖🚖🚖🚖🚖🚖🚖🚖🚖🚖  
사용중: 10대   대기중: 0대    총: 10대   최대: 10대
```
- 모든 택시 운행 중 → 새로운 승객은 대기해야 함

---

## 🎯 **실제 시스템에서의 의미**

### ✅ **좋은 상태**
```
Active: 2, Idle: 3, Max: 10, Usage: 20%
```
- 충분한 여유 용량
- 빠른 응답 시간
- 안정적인 서비스

### ⚠️ **주의 상태**  
```
Active: 7, Idle: 1, Max: 10, Usage: 70%
```
- 부하 증가 중
- 모니터링 필요
- 스케일링 준비

### 🚨 **위험 상태**
```
Active: 10, Idle: 0, Max: 10, Usage: 100%
```
- 모든 연결 사용 중
- 새로운 요청은 대기
- **즉시 대응 필요!**

---

## 🔍 **KubeDB Monitor Dashboard에서 보는 방법**

### 📊 **Connection Pool Status 섹션**

1. **Active Connections**: 현재 데이터베이스 작업 수행 중인 연결
2. **Idle Connections**: 대기 중인 연결 (재사용 가능)
3. **Max Connections**: 최대 허용 연결 수
4. **Pool Usage**: 사용률 퍼센테이지

### 🎨 **컬러 코딩**
- 🟢 **녹색 (0-50%)**: 안전한 상태
- 🟡 **노란색 (50-80%)**: 주의 필요
- 🔴 **빨간색 (80-100%)**: 위험 상태

---

## 🚀 **데모 시나리오**

### 📋 **시나리오 1: 평상시 모니터링**
```
"현재 HikariCP Connection Pool 상태를 보시면..."
Active: 0-1개, Idle: 1-2개, Max: 10개, Usage: 0-10%
"매우 안정적인 상태입니다."
```

### 📋 **시나리오 2: API 부하 테스트**
```bash
# 동시에 여러 API 호출
for i in {1..5}; do
  curl https://university-registration.bitgaram.info/api/courses &
done
```
```
"부하 테스트를 실행하면..."
Active: 3-5개, Idle: 1-2개, Max: 10개, Usage: 30-50%
"연결 수가 증가하는 것을 실시간으로 볼 수 있습니다."
```

### 📋 **시나리오 3: 장기 실행 트랜잭션**
```
"데이터베이스에서 오래 걸리는 작업이 시작되면..."
Active: 7-8개, Idle: 0-1개, Max: 10개, Usage: 70-80%
"연결이 오래 점유되어 Usage가 증가합니다."
```

---

## 🎪 **데모 발표 스크립트**

### 🎤 **오프닝**
> "안녕하세요! 오늘은 실시간 데이터베이스 Connection Pool 모니터링을 시연해드리겠습니다. Connection Pool을 택시 승강장에 비유해서 쉽게 설명드릴게요."

### 🎤 **메인 데모**
> "현재 화면을 보시면, HikariCP Connection Pool 상태가 실시간으로 표시되고 있습니다. 
> - Active Connections는 현재 운행 중인 택시
> - Idle Connections는 대기 중인 택시
> - 10초마다 자동으로 업데이트됩니다!"

### 🎤 **실시간 테스트**
> "지금 API를 여러 번 호출해보겠습니다. 화면에서 Active Connections 수치가 실시간으로 변하는 것을 확인하세요!"

### 🎤 **마무리**
> "이처럼 KubeDB Monitor를 통해 데이터베이스 Connection Pool 상태를 실시간으로 모니터링할 수 있습니다. 시스템 부하를 미리 감지하고 대응할 수 있는 강력한 도구입니다!"

---

## 🔧 **기술적 세부사항 (참고용)**

### 📐 **수식**
- `Total Connections = Active + Idle`
- `Usage Rate = (Active + Idle) / Max × 100%`
- `Available Connections = Max - (Active + Idle)`

### ⏱️ **업데이트 주기**
- **메트릭 수집**: 5초마다
- **전송 및 표시**: 10초마다
- **실시간 반영**: WebSocket 스트리밍

### 🏗️ **아키텍처**
```
HikariCP → ConnectionPoolMonitor → MetricsCollector 
    ↓
HttpMetricsTransmitter → Control Plane → Dashboard
```

---

## 🎯 **핵심 메시지**

1. **직관적**: 택시 승강장처럼 쉽게 이해
2. **실시간**: 10초마다 자동 업데이트  
3. **예측 가능**: Usage 70% 넘으면 주의
4. **즉시 대응**: 100% 도달 전 스케일링

> **"Connection Pool 모니터링으로 데이터베이스 성능 문제를 미리 예방하세요!"** 🚀