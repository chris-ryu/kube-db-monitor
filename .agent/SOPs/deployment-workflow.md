# 배포 워크플로우 (SOP)

## 목적

KubeDB Monitor 시스템의 안전하고 일관된 배포를 보장하기 위한 표준 절차입니다.

## 배포 방식 개요

### 1. Makefile 기반 통합 배포 (👍 추천)

가장 편리하고 안전한 배포 방식입니다.

```bash
# 🎯 전체 시스템 빌드+푸시+배포
make build-and-deploy-all

# 🎓 University App (API + UI + Agent) 통합 배포
make build-and-deploy-university

# 📊 개별 컴포넌트 배포
make build-and-deploy-agent           # ByteBuddy Agent
make build-and-deploy-control-plane   # Control Plane
make build-and-deploy-dashboard       # Dashboard
```

### 2. 수동 배포

세밀한 제어가 필요한 경우 사용합니다.

## 전체 시스템 배포 워크플로우

### 1단계: 사전 확인

```bash
# Git 상태 확인
git status

# 현재 브랜치 확인
git branch

# 미커밋 변경 사항 확인
git diff
```

**확인 사항**:
- 모든 변경 사항이 커밋되었는지
- 올바른 브랜치에 있는지 (feature 브랜치 또는 main)
- `.gitignore`에 의해 무시되는 파일이 있는지

### 2단계: 로컬 테스트 실행

```bash
# 빠른 테스트
make test

# 종합 테스트
make full-test

# 완전한 회귀 테스트
make comprehensive-test
```

**성공 기준**: 모든 테스트 100% 통과

### 3단계: Docker 이미지 빌드

#### Makefile 사용 (권장)

```bash
# 전체 시스템 빌드
make build-and-deploy-all
```

이 명령은 다음을 자동으로 수행합니다:
1. Agent JAR 빌드
2. Docker 이미지 빌드 (Agent, Control Plane, Dashboard, University App)
3. Registry에 푸시
4. Kubernetes 배포 업데이트
5. 배포 상태 확인

#### 수동 빌드 (세밀한 제어 필요 시)

```bash
# Agent 빌드
cd kubedb-monitor-agent
mvn clean package
cd ..

# 모든 이미지 빌드 및 푸시
./scripts/build-images.sh --push

# 또는 개별 컴포넌트만 빌드
./scripts/build-images.sh --component agent --push
./scripts/build-images.sh --component control-plane --push
./scripts/build-images.sh --component dashboard --push
```

**확인 사항**:
- 빌드 로그에 에러가 없는지
- Registry에 이미지가 정상 푸시되었는지
- 이미지 태그가 올바른지 (`:latest` 또는 버전 태그)

### 4단계: Kubernetes 배포

#### 통합 YAML 파일 배포 (권장)

```bash
# API + UI + Agent 모두 포함된 통합 배포
kubectl apply -f k8s/university-registration-with-ui.yaml

# Control Plane + Dashboard 배포
kubectl apply -f k8s/kubedb-monitor-deployment.yaml
```

#### 개별 컴포넌트 배포

```bash
# Agent가 포함된 애플리케이션만 재배포
kubectl rollout restart deployment/university-registration-demo -n kubedb-monitor-test

# Control Plane만 재배포
kubectl rollout restart deployment/kubedb-monitor-control-plane -n kubedb-monitor-test

# Dashboard만 재배포
kubectl rollout restart deployment/kubedb-monitor-dashboard -n kubedb-monitor-test
```

### 5단계: 배포 상태 확인

```bash
# 배포 상태 확인
make status

# 또는 수동 확인
kubectl get pods -n kubedb-monitor-test

# 롤아웃 상태 확인
kubectl rollout status deployment/university-registration-demo -n kubedb-monitor-test
kubectl rollout status deployment/kubedb-monitor-control-plane -n kubedb-monitor-test
kubectl rollout status deployment/kubedb-monitor-dashboard -n kubedb-monitor-test
```

**예상 결과**: 모든 Pod가 `Running` 상태이고 `READY` 상태

### 6단계: Agent 로드 확인

```bash
# Agent 로드 로그 확인
kubectl logs deployment/university-registration-demo -n kubedb-monitor-test | grep "KubeDB Monitor Agent"
```

**예상 로그**:
```
🚀 KubeDB Monitor Agent starting...
🔧 ByteBuddy Agent Builder 설정 중...
🔍 Connection 클래스 발견: [실제 Connection 구현체 클래스명]
✅ ByteBuddy Agent Builder 설치 완료
```

**로그가 보이지 않는 경우**:
1. `JAVA_OPTS` 환경 변수 확인
   ```bash
   kubectl get deployment university-registration-demo -n kubedb-monitor-test -o yaml | grep JAVA_OPTS
   ```
2. Agent JAR 경로 확인 (`/opt/kubedb-agent/kubedb-monitor-agent.jar`)
3. Pod 재시작
   ```bash
   kubectl rollout restart deployment/university-registration-demo -n kubedb-monitor-test
   ```

### 7단계: 환경 변수 전달 확인

**중요**: Kubernetes 이미지 확인 시 localhost에 portforward하지 말고 public DNS로 연결하여 환경 변수가 확실히 전달되도록 합니다.

```bash
# 환경 변수 확인 (Agent Pod)
kubectl exec deployment/university-registration-demo -n kubedb-monitor-test -- env | grep KUBEDB_MONITOR

# 확인해야 할 환경 변수
# - KUBEDB_MONITOR_ENABLED=true
# - KUBEDB_MONITOR_LONG_RUNNING_TX_THRESHOLD_MS=5000
# - KUBEDB_MONITOR_COLLECTOR_ENDPOINT=http://kubedb-monitor-control-plane:8080/api/metrics
```

**환경 변수가 설정되지 않은 경우**:
1. `k8s/university-registration-with-ui.yaml` 파일에서 `env` 섹션 확인
2. 배포 YAML 재적용
3. Pod 재시작

### 8단계: 이벤트 파이프라인 검증

```bash
# 이벤트 처리 파이프라인 종합 검증
./scripts/event-pipeline-verification-test.sh
```

**성공 기준**: 5가지 이벤트 타입 모두 100% 전송 성공

**실패 시 조치**:
1. Agent 로그 확인
   ```bash
   kubectl logs deployment/university-registration-demo -n kubedb-monitor-test | tail -100
   ```
2. Control Plane 로그 확인
   ```bash
   kubectl logs deployment/kubedb-monitor-control-plane -n kubedb-monitor-test | tail -100
   ```
3. 네트워크 연결 확인 (Agent → Control Plane)
   ```bash
   kubectl exec deployment/university-registration-demo -n kubedb-monitor-test -- curl -v http://kubedb-monitor-control-plane:8080/health
   ```

### 9단계: 엔드포인트 동작 확인

#### Public DNS로 확인 (권장)

```bash
# Application API 확인
curl https://university-registration.bitgaram.info/api/courses

# Dashboard 확인
curl https://kube-db-mon-dashboard.bitgaram.info/

# Control Plane Health Check
curl https://kube-db-mon-controlplane.bitgaram.info/health
```

#### Dashboard에서 실시간 메트릭 확인

1. 브라우저에서 Dashboard 접속: https://kube-db-mon-dashboard.bitgaram.info
2. WebSocket 연결 상태 확인 (브라우저 개발자 도구 → Network → WS)
3. 테스트 트래픽 생성:
   ```bash
   curl https://university-registration.bitgaram.info/api/courses
   ```
4. Dashboard에 쿼리 실행 이벤트가 실시간으로 표시되는지 확인

### 10단계: 로그 모니터링

```bash
# 전체 로그 모니터링
make logs

# 또는 개별 컴포넌트 로그
make logs-agent            # Agent 로그
kubectl logs -f deployment/kubedb-monitor-control-plane -n kubedb-monitor-test
kubectl logs -f deployment/kubedb-monitor-dashboard -n kubedb-monitor-test
```

**모니터링 기간**: 최소 30분

**확인 사항**:
- 에러 로그가 없는지
- 메모리 누수 징후가 없는지
- CPU 사용률이 정상 범위인지

## 개별 컴포넌트 배포 워크플로우

### Agent만 업데이트

```bash
# 1. Agent JAR 빌드
cd kubedb-monitor-agent
mvn clean package
cd ..

# 2. Agent 이미지 빌드 및 푸시
make build-and-deploy-agent

# 3. 배포 확인
kubectl rollout status deployment/university-registration-demo -n kubedb-monitor-test

# 4. 테스트 실행
./scripts/agent-comprehensive-test-suite.sh
```

### Control Plane만 업데이트

```bash
# 1. Control Plane 빌드 및 배포
make build-and-deploy-control-plane

# 2. 배포 확인
kubectl rollout status deployment/kubedb-monitor-control-plane -n kubedb-monitor-test

# 3. Health Check
curl https://kube-db-mon-controlplane.bitgaram.info/health
```

### Dashboard만 업데이트

```bash
# 1. Dashboard 빌드 및 배포
make build-and-deploy-dashboard

# 2. 배포 확인
kubectl rollout status deployment/kubedb-monitor-dashboard -n kubedb-monitor-test

# 3. 브라우저에서 확인
open https://kube-db-mon-dashboard.bitgaram.info
```

## 롤백 절차

배포 후 문제가 발생한 경우 즉시 롤백합니다.

### Kubernetes 롤백

```bash
# 이전 버전으로 롤백
kubectl rollout undo deployment/university-registration-demo -n kubedb-monitor-test
kubectl rollout undo deployment/kubedb-monitor-control-plane -n kubedb-monitor-test
kubectl rollout undo deployment/kubedb-monitor-dashboard -n kubedb-monitor-test

# 롤백 상태 확인
kubectl rollout status deployment/university-registration-demo -n kubedb-monitor-test
```

### Docker 이미지 롤백

특정 버전으로 롤백이 필요한 경우:

```bash
# 이전 이미지 태그로 변경
kubectl set image deployment/university-registration-demo \
  university-registration-demo=registry.bitgaram.info/university-registration/api:<previous-tag> \
  -n kubedb-monitor-test
```

## 배포 체크리스트

- [ ] Git 상태 확인 (모든 변경 사항 커밋)
- [ ] 로컬 테스트 통과 (`make test` 또는 `make full-test`)
- [ ] Docker 이미지 빌드 성공
- [ ] Registry에 이미지 푸시 성공
- [ ] Kubernetes 배포 성공 (모든 Pod `Running` 상태)
- [ ] Agent 로드 확인 (로그에 "✅ ByteBuddy Agent Builder 설치 완료" 표시)
- [ ] 환경 변수 전달 확인 (`KUBEDB_MONITOR_*` 환경 변수)
- [ ] 이벤트 파이프라인 검증 통과 (`event-pipeline-verification-test.sh` 100%)
- [ ] Public DNS 엔드포인트 동작 확인
- [ ] Dashboard 실시간 메트릭 확인
- [ ] 30분 로그 모니터링 완료 (에러 없음)

## 자주 발생하는 문제 및 해결 방법

### 문제 1: Pod ImagePullBackOff 상태

**증상**: Pod가 `ImagePullBackOff` 또는 `ErrImagePull` 상태

**원인**:
- Registry에 이미지가 없음
- Registry 인증 실패
- 이미지 태그 오타

**해결**:
```bash
# 이미지가 Registry에 존재하는지 확인
docker pull registry.bitgaram.info/kubedb-monitor/agent:latest

# Registry Secret 확인
kubectl get secret registry-secret -n kubedb-monitor-test

# Registry Secret 재생성 (필요 시)
kubectl delete secret registry-secret -n kubedb-monitor-test
kubectl create secret docker-registry registry-secret \
  --docker-server=registry.bitgaram.info \
  --docker-username=<username> \
  --docker-password=<password> \
  -n kubedb-monitor-test
```

### 문제 2: Agent가 로드되지 않음

**증상**: Agent 로드 로그가 보이지 않음

**해결**:
1. `JAVA_OPTS` 환경 변수 확인
2. Agent JAR 경로 확인 (`/opt/kubedb-agent/kubedb-monitor-agent.jar`)
3. initContainer 로그 확인
   ```bash
   kubectl logs <pod-name> -c kubedb-agent-init -n kubedb-monitor-test
   ```

### 문제 3: WebSocket 연결 실패

**증상**: Dashboard에서 "WebSocket 연결 실패" 메시지

**해결**:
1. Control Plane Pod 상태 확인
2. Ingress 설정 확인 (WebSocket 지원 여부)
3. CORS 설정 확인
4. 브라우저 개발자 도구에서 WebSocket 연결 로그 확인

## 관련 문서

- [Architecture Overview](../system/architecture-overview.md)
- [Project Structure](../system/project-structure.md)
- [Event Pipeline](../system/event-pipeline.md)
- [Agent Modification Workflow](./agent-modification-workflow.md)

## 버전 히스토리

- **v1.0** (2025-10-08): 초기 SOP 작성
