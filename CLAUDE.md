수강 신청 앱 같은 java application의 db 모니터링 솔루션 개발 중

docs/agent-jdbc-compatibility-guide.md 파일 참조해서 수정 진행

falllback 코드 작성시 물어보고 진행 할 것.
mocking 코드 작성시 물어보고 진행 할 것.
**❌ "안전모드", "safe mode", "fallback code" 등의 코드를 사용자에게 알리지 않고 함부로 추가하는 것을 금지한다.**
**❌ 이전에 정상 작동하던 코드를 안전성을 이유로 임의 수정하는 것을 금지한다.**
한글로 답변
docker image 업데이트하면 실행 버전이 최신 버전인지 항상 확인

새로운 기능 구현시 TDD를 적극적으로 활용
  - 가능 한 테스트 케이스를 생성
  - 테스트 스위트 작동 여부를 검증하고, 필요없는 테스트스위트는 별도 보관. 

서버측 코드 작성시 simulation용 코드작업 물어보고 진행할 것 
디버깅 용도로 만들어진 시뮬레이션, 모킹 코드는 기능 구현 후 반드시 실제 환경으로 삭제, 복구

Agent -> Control Plane -> Dashboard 서비스 레이어를 넘어가는 부분에서 이벤트의 포맷이나 스키마가 변경되면 다음 레이어에서 호환 되는지 항상 확인

kubernetes 이미지 확인 시 localhost에 portforward하지 말고 public dns로 연결. 환경변수가 확실히 전달되도록

## 📊 **이벤트 처리 파이프라인 검증 필수**

**새로운 기능 구현 시 반드시 다음 검증 스크립트를 실행하여 모든 이벤트 타입이 정상 처리되는지 확인할 것:**

```bash
# 이벤트 처리 파이프라인 종합 검증 (기능 추가 시 필수 실행)
./scripts/event-pipeline-verification-test.sh
```

**지원하는 이벤트 타입 (총 5가지):**
1. **`query_execution`** - SQL 쿼리 실행 메트릭
2. **`transaction_event`** - 트랜잭션 커밋/롤백 이벤트
3. **`long_running_transaction`** - Long-running transaction 알림 ⭐ 
4. **`deadlock_event`** / **`deadlock_detected`** - 데드락 감지
5. **`system_metrics`** - Connection Pool 등 시스템 메트릭

**검증 항목:**
- ✅ Agent에서 이벤트 감지 및 생성
- ✅ Agent → Control Plane HTTP 전송
- ✅ Control Plane → Dashboard WebSocket 브로드캐스트
- ✅ 각 레이어에서 올바른 이벤트 타입 처리
- ✅ UI에서 실시간 표시

**성공률이 100%가 아닐 경우 배포 금지. 문제 해결 후 재검증 필수.** 

## Git Workflow
- Create feature branches for each task
- Use descriptive commit messages
- Squash commits before merging

# 📋 통합 배포 가이드 (2025-08-30 업데이트)

## 🚀 **주요 배포 방식**

### 1. Makefile 기반 통합 배포 (👍 추천)
```bash
# 🎯 전체 시스템 빌드+푸시+배포 (가장 편리)
make build-and-deploy-all

# 🎓 University App (API + UI + Agent) 통합 배포  
make build-and-deploy-university

# 📊 개별 컴포넌트 배포
make build-and-deploy-agent      # ByteBuddy Agent
make build-and-deploy-control-plane
make build-and-deploy-dashboard
```

### 2. 통합 YAML 파일 배포
```bash
# API + UI + Agent 모두 포함된 통합 배포
kubectl apply -f k8s/university-registration-with-ui.yaml

# Demo 전용 배포
kubectl apply -f k8s/university-registration-demo-complete.yaml
```

### 전체 테스트 실행
```bash
make test           # 빠른 테스트
make full-test      # 종합 테스트 
make comprehensive-test  # 완전한 회귀 테스트
```

## 📊 **모니터링 및 디버깅**

### 상태 확인
```bash
make status         # 배포 상태
make logs          # 전체 로그
make logs-agent    # Agent 로그만
make debug         # 디버깅 정보
```

### 데모 환경 
```bash
make demo          # 데모 환경 설정
make demo-reset    # 데모 데이터 리셋
make demo-deadlock # 데드락 시뮬레이션
```

## 🎯 **현재 배포 구조 (university-registration-with-ui.yaml)**

- **🤖 API 서버**: `university-registration-demo` (ByteBuddy Agent 포함)
- **🎨 UI 서버**: `university-registration-ui` (Next.js)  
- **🔗 통합 Ingress**: 
  - `/api/*` → API 서버
  - `/` → UI 서버  
  - SSL: https://university-registration.bitgaram.info

## ⚡ **ByteBuddy Agent 특징 (2025-08-30 완성)**

- ✅ **범용 JDBC 모니터링**: 모든 DB에서 바로 메트릭 수집
- ✅ **PostgreSQL 호환성 완전 해결**: "Unknown Types value" 오류 해결
- ✅ **투명한 모니터링**: 애플리케이션 코드 변경 불필요
- ✅ **Spring Boot 완전 지원**: Fat JAR 환경 안정적 동작

## 🧪 **KubeDB Monitor Agent 테스트 필수 실행 규정**

**⚠️ AGENT 수정 시 반드시 다음 테스트 스위트를 실행하여 regression을 방지해야 합니다:**

### 1. 필수 테스트 스크립트 실행
```bash
# Agent 종합 테스트 스위트 (Agent 수정 시 필수 실행)
./scripts/agent-comprehensive-test-suite.sh
```

### 2. 검증 항목
- ✅ **Transaction Integration**: 트랜잭션 감지 및 추적 기능
- ✅ **Long-running Transaction**: Long-running 트랜잭션 감지 (5초 임계값)
- ✅ **Transaction Start Detection**: BEGIN/START TRANSACTION/DML 문 감지
- ✅ **JDBC Interception**: PreparedStatement, Connection 메소드 인터셉션
- ✅ **Metrics Collection**: 메트릭 수집 및 Control Plane 전송
- ✅ **Agent JAR Build**: Agent JAR 파일 빌드 성공
- ✅ **Kubernetes Integration**: 실제 Pod 환경에서 Agent 동작 확인

### 3. 테스트 실패 시 대응
- ❌ **테스트 실패 시 배포 금지**
- 🔧 **근본 원인 해결 후 재테스트 필수**
- 📊 **성공률 100% 달성 시에만 배포 승인**

### 4. Agent 핵심 기능 목록 (테스트 대상)
1. **JDBC 인터셉션**: SQL 쿼리 실행 감지 및 메트릭 수집
2. **트랜잭션 추적**: `setAutoCommit(false)`, `commit()`, `rollback()` 감지
3. **Long-running 감지**: 5초 이상 실행되는 트랜잭션 알림
4. **Connection Pool 모니터링**: HikariCP 등 Connection Pool 메트릭
5. **PostgreSQL 호환성**: Unknown Types 오류 해결 및 안전 모드 지원

**📝 기존에 작동하던 기능이 Agent 수정 후 중단되는 사례가 빈발하므로, 이 테스트 규정을 엄격히 준수해야 합니다.**

## 🛠️ **Agent JAR 경로 및 배포 설정 (중요)**

**⚠️ Agent JAR 경로 관련 자주 발생하는 실수를 방지하기 위한 명확한 규칙:**

### 1. Agent JAR 파일 경로 고정 규칙
```bash
# ✅ 올바른 Agent JAR 경로 (JAVA_OPTS에서 사용)
/opt/kubedb-agent/kubedb-monitor-agent.jar

# ❌ 잘못된 경로들 (사용 금지)
/opt/shared-agent/kubedb-monitor-agent.jar  # initContainer에서 복사하는 임시 경로
/app/kubedb-monitor-agent.jar               # 잘못된 위치
```

### 2. Kubernetes 배포 YAML 설정 검증 포인트
```yaml
# ✅ 올바른 JAVA_OPTS 설정
env:
- name: JAVA_OPTS
  value: "-javaagent:/opt/kubedb-agent/kubedb-monitor-agent.jar=..."

# ✅ initContainer는 올바른 Agent 이미지 사용
initContainers:
- name: kubedb-agent-init
  image: registry.bitgaram.info/kubedb-monitor/agent:latest
  # Agent 이미지 내부에서 /opt/kubedb-agent/에 JAR 파일이 이미 위치함
```

### 3. Agent 초기화 검증 방법
```bash
# Agent 로드 확인 (정상 시 나타나야 할 로그)
🚀 KubeDB Monitor Agent starting...
🔧 ByteBuddy Agent Builder 설정 중...
🔍 Connection 클래스 발견: [실제 Connection 구현체 클래스명]
✅ ByteBuddy Agent Builder 설치 완료
```

**❌ initContainer에서 Agent JAR 복사 작업을 임의로 수정하지 말 것. Agent 이미지에서 이미 올바른 위치에 JAR 파일이 배치됨.**