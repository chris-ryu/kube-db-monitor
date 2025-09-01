수강 신청 앱 같은 java application의 db 모니터링 솔루션 개발 중

docs/agent-jdbc-compatibility-guide.md 파일 참조해서 수정 진행

falllback 코드 작성시 물어보고 진행 할 것.
mocking 코드 작성시 물어보고 진행 할 것.
한글로 답변
docker image 업데이트하면 실행 버전이 최신 버전인지 항상 확인
TDD를 적극적으로 활용
  - 테스트 케이스를 통해 기능 검증
  - 테스트 케이스 유지보수, 필요없는 테스트케이스는 별도 보관. 

서버측 코드 작성시 simulation용 코드작업 물어보고 진행할 것 
디버깅 용도로 만들어진 시뮬레이션, 모킹 코드는 기능 구현 후 반드시 실제 환경으로 삭제, 복구

Agent -> Control Plane -> Dashboard 서비스 레이어를 넘어가는 부분에서 이벤트의 포맷이나 스키마가 변경되면 다음 레이어에서 호환 되는지 항상 확인

kubernetes 이미지 확인 시 localhost에 portforward하지 말고 public dns로 연결. 환경변수가 확실히 전달되도록 

agent, control plane, dashboard의 빌드는 script/build-image.sh, 배포는 k8s/kubedb-monitor-deployment.yaml
업데이트시 배포 삭제후 재배포

postgres-system 네임스페이스 postgres-cluster-1 팟으로 연결하면 psql연결 가능

직접 YAML 수정하지 말고 make build-and-deploy 로 다시 빌드하고 재배포해야 합니다.

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

### 3. build-images.sh 스크립트 (기존 방식)
```bash
# 전체 빌드+배포
./scripts/build-images.sh all

# Agent만
./scripts/build-images.sh agent
```

## 🔧 **개발 시 빠른 작업 흐름**

### Agent 수정 후
```bash
make build-and-deploy-agent
# 또는
./scripts/build-images.sh agent
```

### UI 수정 후  
```bash
make build-and-deploy-university
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

kubernetes