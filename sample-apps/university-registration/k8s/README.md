# KubeDB Monitor 통합 테스트

이 디렉토리는 KubeDB Monitor와 대학교 수강신청 시스템 샘플 애플리케이션의 통합 테스트를 위한 Kubernetes 매니페스트와 스크립트를 포함합니다.

## 개요

KubeDB Monitor는 Kubernetes 환경에서 실행되는 애플리케이션의 데이터베이스 성능을 모니터링하는 솔루션입니다. 이 통합 테스트는 다음을 검증합니다:

- Java Agent의 JDBC 인터셉션 기능
- Kubernetes Admission Controller의 자동 Agent 주입
- 메트릭 수집 및 출력 기능
- 대용량 동시 트래픽 상황에서의 성능

## 구성 요소

### 1. 샘플 애플리케이션
- **university-registration**: 대학교 수강신청 시스템
- **데이터베이스**: H2 인메모리 데이터베이스 (MySQL 모드)
- **기능**: 학생, 과목, 수강신청, 장바구니 관리
- **테스트 데이터**: 1000명 학생, 200개 과목, 현실적인 수강신청 패턴

### 2. KubeDB Monitor 구성
- **Java Agent**: JDBC 호출 인터셉트 및 메트릭 수집
- **Admission Controller**: Pod 생성 시 자동 Agent 주입
- **메트릭 출력**: 로깅, JMX, Prometheus 형식 지원

## 사전 요구사항

- Kubernetes 클러스터 (minikube, kind, 또는 실제 클러스터)
- kubectl 명령어 도구
- Docker 및 레지스트리 접근 권한
- Maven 3.6+
- Java 17+

## 디렉토리 구조

```
k8s/
├── README.md                 # 이 파일
├── integration-test.sh       # 통합 테스트 실행 스크립트
├── namespace.yaml           # 테스트 네임스페이스 및 리소스 제한
├── deployment.yaml          # 샘플 애플리케이션 배포 매니페스트
└── monitoring/              # 모니터링 관련 설정 (향후 추가)
```

## 사용법

### 1. 기본 통합 테스트 실행

```bash
# 모든 단계를 포함한 전체 테스트
./integration-test.sh

# 부하 테스트까지 포함
./integration-test.sh --load-test

# 테스트 후 리소스 정리
./integration-test.sh --cleanup
```

### 2. 단계별 실행

```bash
# 빌드 건너뛰기 (이미 빌드된 이미지 사용)
./integration-test.sh --skip-build

# Docker 푸시 건너뛰기 (로컬 이미지 사용)
./integration-test.sh --skip-push

# 조합 사용
./integration-test.sh --skip-build --skip-push --load-test
```

### 3. 수동 테스트

```bash
# 네임스페이스 생성
kubectl apply -f namespace.yaml

# 애플리케이션 배포
kubectl apply -f deployment.yaml

# 포트 포워딩으로 접근
kubectl port-forward svc/university-registration-service 8080:80 -n kubedb-monitor-test

# API 테스트
curl http://localhost:8080/api/data/stats
curl http://localhost:8080/api/data/health
curl http://localhost:8080/api/courses?page=0&size=20
```

## 테스트 시나리오

### 1. 기본 기능 테스트
- 애플리케이션 배포 및 상태 확인
- 데이터 초기화 검증
- API 엔드포인트 정상 동작 확인

### 2. KubeDB Monitor 기능 테스트
- Java Agent 자동 주입 확인
- JDBC 호출 인터셉션 동작 확인
- 메트릭 수집 및 출력 검증

### 3. 성능 테스트
- 동시 다중 사용자 수강신청 시뮬레이션
- 복잡한 JOIN 쿼리 성능 측정
- 대량 데이터 처리 성능 확인

### 4. 모니터링 검증
- Prometheus 메트릭 노출 확인
- 로그 기반 메트릭 출력 검증
- 슬로우 쿼리 탐지 기능 확인

## KubeDB Monitor 어노테이션

배포 매니페스트에 사용되는 주요 어노테이션:

```yaml
annotations:
  # 기본 설정
  kubedb.monitor/enable: "true"
  kubedb.monitor/db-types: "h2"
  kubedb.monitor/sampling-rate: "1.0"
  kubedb.monitor/slow-query-threshold: "500"
  kubedb.monitor/collector-type: "logging"
  
  # 고급 설정
  kubedb.monitor/batch-size: "50"
  kubedb.monitor/flush-interval: "10s"
  kubedb.monitor/enable-stack-trace: "true"
  kubedb.monitor/log-level: "INFO"
```

## 예상 결과

성공적인 통합 테스트 후 다음을 확인할 수 있습니다:

1. **애플리케이션 로그**에서 KubeDB Monitor 메트릭 출력
2. **Prometheus 엔드포인트**에서 데이터베이스 메트릭 확인
3. **부하 테스트** 중 실시간 성능 모니터링
4. **슬로우 쿼리** 탐지 및 로깅

## 모니터링 데이터 예시

```json
{
  "timestamp": "2024-01-15T10:30:45.123Z",
  "query": "SELECT s FROM Student s WHERE s.department = ?",
  "executionTime": 45,
  "database": "h2",
  "success": true,
  "rowsAffected": 150
}
```

## 문제 해결

### 일반적인 문제

1. **이미지 풀 실패**
   ```bash
   # 레지스트리 시크릿 확인
   kubectl get secret registry-secret -n kubedb-monitor-test
   ```

2. **Pod 시작 실패**
   ```bash
   # Pod 상태 확인
   kubectl describe pod -l app=university-registration -n kubedb-monitor-test
   ```

3. **데이터 초기화 실패**
   ```bash
   # 애플리케이션 로그 확인
   kubectl logs -l app=university-registration -n kubedb-monitor-test
   ```

### 로그 확인

```bash
# KubeDB Monitor 관련 로그 필터링
kubectl logs -l app=university-registration -n kubedb-monitor-test | grep -i "kubedb\|monitor\|agent"

# 실시간 로그 모니터링
kubectl logs -f deployment/university-registration -n kubedb-monitor-test
```

## 정리

테스트 완료 후 리소스 정리:

```bash
# 네임스페이스 전체 삭제
kubectl delete namespace kubedb-monitor-test

# 또는 스크립트 사용
./integration-test.sh --cleanup
```

## 추가 정보

- [KubeDB Monitor 메인 문서](../../../README.md)
- [Java Agent 상세 가이드](../../../kubedb-monitor-agent/README.md)
- [Admission Controller 설정](../../../kubedb-monitor-controller/README.md)