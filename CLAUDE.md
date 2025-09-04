수강 신청 앱 같은 java application의 db 모니터링 솔루션 개발 중

docs/agent-jdbc-compatibility-guide.md 파일 참조해서 수정 진행

falllback 코드 작성시 물어보고 진행 할 것.
mocking 코드 작성시 물어보고 진행 할 것.
한글로 답변
docker image 업데이트하면 실행 버전이 최신 버전인지 항상 확인

새로운 기능 구현시 TDD를 적극적으로 활용
  - 가능 한 테스트 케이스를 생성
  - 테스트 스위트 작동 여부를 검증하고, 필요없는 테스트스위트는 별도 보관. 

서버측 코드 작성시 simulation용 코드작업 물어보고 진행할 것 
디버깅 용도로 만들어진 시뮬레이션, 모킹 코드는 기능 구현 후 반드시 실제 환경으로 삭제, 복구

Agent -> Control Plane -> Dashboard 서비스 레이어를 넘어가는 부분에서 이벤트의 포맷이나 스키마가 변경되면 다음 레이어에서 호환 되는지 항상 확인

kubernetes 이미지 확인 시 localhost에 portforward하지 말고 public dns로 연결. 환경변수가 확실히 전달되도록 

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