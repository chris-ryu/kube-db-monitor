# Agent 수정 워크플로우 (SOP)

## 목적

ByteBuddy Agent 코드 수정 시 regression을 방지하고 모든 핵심 기능이 정상 작동함을 보장하기 위한 표준 절차입니다.

## 적용 대상

다음 파일 수정 시 반드시 이 SOP를 따라야 합니다:

- `kubedb-monitor-agent/src/main/java/io/kubedb/monitor/agent/` 하위의 모든 Java 파일
- 특히 다음 핵심 클래스 수정 시:
  - `KubeDBAgent.java`
  - `UniversalJDBCInterceptor.java`
  - `MetricsCollector.java`
  - `HttpMetricsTransmitter.java`
  - `SpringTransactionInterceptor.java`

## 필수 테스트 규정

**⚠️ AGENT 수정 시 반드시 다음 테스트 스위트를 실행하여 regression을 방지해야 합니다.**

### 1. 필수 테스트 스크립트 실행

```bash
# Agent 종합 테스트 스위트 (Agent 수정 시 필수 실행)
./scripts/agent-comprehensive-test-suite.sh
```

### 2. 검증 항목

이 테스트 스위트는 다음 항목을 자동으로 검증합니다:

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

## 워크플로우 단계

### 1단계: 코드 수정 전 현재 상태 확인

```bash
# 현재 브랜치 확인
git branch

# 최신 코드로 업데이트
git pull origin <branch-name>

# 수정 전 테스트 실행 (베이스라인 확립)
./scripts/agent-comprehensive-test-suite.sh
```

**예상 결과**: 모든 테스트 통과 (100% 성공률)

### 2단계: 코드 수정

수정 사항을 구현합니다. 다음 원칙을 준수하세요:

#### ❌ 금지 사항

1. **Fallback 코드 무단 추가 금지**
   - "안전모드", "safe mode", "fallback code" 등의 코드를 사용자에게 알리지 않고 함부로 추가하는 것을 금지합니다.
   - 기존에 정상 작동하던 코드를 안전성을 이유로 임의 수정하는 것을 금지합니다.

2. **Mocking 코드 무단 추가 금지**
   - 디버깅 용도의 시뮬레이션, 모킹 코드 추가 시 반드시 사용자에게 확인받아야 합니다.

3. **Agent JAR 경로 임의 변경 금지**
   - ✅ 올바른 경로: `/opt/kubedb-agent/kubedb-monitor-agent.jar`
   - ❌ 잘못된 경로: `/opt/shared-agent/`, `/app/` 등

#### ✅ 권장 사항

1. **TDD 접근 방식**
   - 가능한 경우 테스트 케이스를 먼저 작성
   - 테스트 주도로 코드 구현

2. **문서화**
   - 주요 변경 사항은 코드 주석으로 기록
   - 복잡한 로직은 JavaDoc 작성

### 3단계: 로컬 빌드 및 유닛 테스트

```bash
# Agent 디렉토리로 이동
cd kubedb-monitor-agent

# Maven 빌드 및 테스트
mvn clean test

# JAR 파일 생성 확인
mvn clean package
ls -lh target/kubedb-monitor-agent.jar
```

**예상 결과**: 빌드 성공, 모든 유닛 테스트 통과

### 4단계: Docker 이미지 빌드

```bash
# 프로젝트 루트로 이동
cd ..

# Docker 이미지 빌드 및 푸시
./scripts/build-images.sh --component agent --push
```

**확인 사항**:
- Docker 이미지 빌드 성공
- Registry에 이미지 푸시 성공
- 이미지 태그 확인 (`:latest` 또는 버전 태그)

### 5단계: Kubernetes 배포

```bash
# Agent가 포함된 애플리케이션 재배포
kubectl apply -f k8s/university-registration-with-ui.yaml

# Pod 재시작 확인
kubectl rollout status deployment/university-registration-demo -n kubedb-monitor-test

# Agent 로드 확인
kubectl logs deployment/university-registration-demo -n kubedb-monitor-test | grep "KubeDB Monitor Agent"
```

**예상 로그**:
```
🚀 KubeDB Monitor Agent starting...
🔧 ByteBuddy Agent Builder 설정 중...
🔍 Connection 클래스 발견: [실제 Connection 구현체 클래스명]
✅ ByteBuddy Agent Builder 설치 완료
```

### 6단계: 종합 테스트 스위트 실행

```bash
# Agent 종합 테스트 실행
./scripts/agent-comprehensive-test-suite.sh
```

**성공 기준**: 모든 테스트 100% 통과

**실패 시 조치**:
1. 실패한 테스트 항목 확인
2. Agent 로그에서 에러 메시지 확인
   ```bash
   kubectl logs deployment/university-registration-demo -n kubedb-monitor-test | grep -i error
   ```
3. 근본 원인 파악 및 수정
4. 2단계부터 재시작

### 7단계: 이벤트 파이프라인 검증

```bash
# 이벤트 처리 파이프라인 종합 검증
./scripts/event-pipeline-verification-test.sh
```

**성공 기준**: 5가지 이벤트 타입 모두 100% 전송 성공

**검증 항목**:
- ✅ `query_execution`
- ✅ `transaction_event`
- ✅ `long_running_transaction`
- ✅ `deadlock_event`
- ✅ `system_metrics`

### 8단계: 실제 애플리케이션 동작 확인

```bash
# 테스트 트래픽 생성
curl https://university-registration.bitgaram.info/api/courses

# Dashboard에서 실시간 메트릭 확인
# https://kube-db-mon-dashboard.bitgaram.info
```

**확인 사항**:
1. Dashboard에 쿼리 실행 이벤트 표시되는지
2. Long-running transaction 감지 정상 작동하는지
3. Connection Pool 메트릭 정상 수집되는지

### 9단계: 코드 커밋 및 문서화

```bash
# 변경 사항 커밋
git add .
git commit -m "feat: [수정 내용 요약]

- 상세 변경 내역 1
- 상세 변경 내역 2

🧪 Tested with:
- ./scripts/agent-comprehensive-test-suite.sh (100% pass)
- ./scripts/event-pipeline-verification-test.sh (100% pass)

🤖 Generated with Claude Code
Co-Authored-By: Claude <noreply@anthropic.com>"

# 원격 저장소에 푸시
git push origin <branch-name>
```

**커밋 메시지 필수 포함 사항**:
- 변경 내용 요약
- 테스트 스위트 실행 결과
- 모든 테스트 통과 확인

### 10단계: 사후 모니터링

배포 후 최소 30분간 다음 사항을 모니터링:

```bash
# Agent 로그 모니터링 (에러 확인)
kubectl logs -f deployment/university-registration-demo -n kubedb-monitor-test | grep -i error

# Control Plane 로그 모니터링
kubectl logs -f deployment/kubedb-monitor-control-plane -n kubedb-monitor-test

# Pod 상태 확인
kubectl get pods -n kubedb-monitor-test -w
```

**문제 발생 시**:
- 즉시 이전 버전으로 롤백
- 문제 원인 파악 후 수정
- 1단계부터 재시작

## 자주 발생하는 실수 및 해결 방법

### 실수 1: Agent JAR 경로 오류

**증상**: Pod 시작 시 "Agent JAR not found" 오류

**원인**: `JAVA_OPTS`에 잘못된 Agent JAR 경로 지정

**해결**:
```yaml
# ✅ 올바른 설정
env:
- name: JAVA_OPTS
  value: "-javaagent:/opt/kubedb-agent/kubedb-monitor-agent.jar=..."

# ❌ 잘못된 설정
env:
- name: JAVA_OPTS
  value: "-javaagent:/opt/shared-agent/kubedb-monitor-agent.jar=..."
```

### 실수 2: 이벤트 타입 불일치

**증상**: Control Plane에서 "Invalid eventType" 오류

**원인**: Agent에서 잘못된 `eventType` 값 전송

**해결**: 5가지 표준 이벤트 타입 중 하나 사용
- `query_execution`
- `transaction_event`
- `long_running_transaction`
- `deadlock_event`
- `system_metrics`

### 실수 3: ByteBuddy 인터셉션 실패

**증상**: SQL 쿼리가 실행되지만 메트릭이 수집되지 않음

**원인**: ByteBuddy 타입 매칭 조건 오류

**해결**: `KubeDBAgent.java`에서 타입 매칭 조건 확인
```java
// ✅ 올바른 타입 매칭
.type(nameMatches(".*PreparedStatement.*"))
.method(named("execute").or(named("executeQuery")).or(named("executeUpdate")))
```

### 실수 4: 지연 초기화 로직으로 인한 인터셉션 차단 ⭐ CRITICAL

**증상**:
- ByteBuddy Agent는 정상 로드되지만 SQL 쿼리 메트릭이 전혀 수집되지 않음
- Control Plane에서 `query_execution` 이벤트를 전혀 수신하지 못함
- Agent 로그에 "🚀 인터셉트 진입" 메시지가 나타나지 않음
- Pod 시작 후 일정 시간 동안 모든 JDBC 호출이 무시됨

**원인**:
2025-09-05 커밋(`77306249`)에서 추가된 지연 초기화 로직이 문제:
```java
// ❌ 문제가 되는 코드
private static volatile boolean databaseInitializationComplete = false;
private static final long INITIALIZATION_WAIT_TIME_MS = 30000; // 30초 대기

if (!isDatabaseInitializationReady()) {
    return callable.call(); // 인터셉션 무시
}
```

이 로직은 HikariCP와 PostgreSQL 초기화를 기다리기 위해 추가되었지만, 실제로는:
- ByteBuddy는 클래스 로딩 시점에 이미 인터셉션을 설정함
- 별도의 대기 시간이 필요 없음
- 오히려 초기 30초간 모든 메트릭 수집을 막아버림

**해결**:
1. `UniversalJDBCInterceptor.java`에서 지연 초기화 로직 제거:
```java
// ✅ 올바른 설정
private static volatile boolean databaseInitializationComplete = true; // 즉시 활성화
private static final long INITIALIZATION_WAIT_TIME_MS = 0; // 대기 시간 제거

// 지연 체크 로직 제거
// if (!isDatabaseInitializationReady()) {
//     return callable.call();
// }
```

2. 검증 방법:
```bash
# 1. Agent 재배포 후 즉시 API 호출
curl https://university-registration.bitgaram.info/api/courses

# 2. Control Plane 로그 확인 (즉시 메트릭 수신되어야 함)
kubectl logs -n kubedb-monitor kubedb-monitor-control-plane-xxx --tail=20 | grep "query_execution"

# 예상 출력:
# 📊 Received real JDBC metric: query_execution - SELECT
```

**교훈**:
- ❌ "안전을 위한" 지연 로직이 오히려 기능을 완전히 차단할 수 있음
- ✅ ByteBuddy Agent는 premain()에서 이미 인터셉션 준비 완료
- ✅ 문제 발생 시 git log로 언제부터 동작하지 않았는지 확인하는 것이 중요

**관련 커밋**:
- 문제 추가: `77306249` (2025-09-05)
- 문제 해결: 이 커밋

## 체크리스트

수정 완료 후 다음 체크리스트를 확인하세요:

- [ ] 로컬 빌드 및 유닛 테스트 통과
- [ ] Docker 이미지 빌드 성공
- [ ] Kubernetes 배포 성공
- [ ] Agent 로드 확인 (로그에 "✅ ByteBuddy Agent Builder 설치 완료" 표시)
- [ ] `agent-comprehensive-test-suite.sh` 100% 통과
- [ ] `event-pipeline-verification-test.sh` 100% 통과
- [ ] Dashboard에서 실시간 메트릭 확인
- [ ] 코드 커밋 및 푸시 완료
- [ ] 30분 사후 모니터링 완료 (에러 없음)

## 관련 문서

- [Architecture Overview](../system/architecture-overview.md)
- [Event Pipeline](../system/event-pipeline.md)
- [Agent JDBC Compatibility Guide](../../docs/agent-jdbc-compatibility-guide.md)
- [Agent Comprehensive Test Suite Script](../../scripts/agent-comprehensive-test-suite.sh)
- [Event Pipeline Verification Script](../../scripts/event-pipeline-verification-test.sh)

## 버전 히스토리

- **v1.0** (2025-10-08): 초기 SOP 작성
- **v1.1** (2025-11-05): 지연 초기화 로직 문제 트러블슈팅 추가 (실수 4)
