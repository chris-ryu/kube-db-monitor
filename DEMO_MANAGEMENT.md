# KubeDB Monitor 데모 환경 관리 가이드

## 📋 개요

KubeDB Monitor 데모를 위한 환경 관리 및 초기화 방법을 설명합니다.

## 🎯 데모 환경 구성

### 현재 배포된 구성 요소

| 리소스 | 이름 | 상태 | 역할 |
|--------|------|------|------|
| **Controller** | `kubedb-monitor-test-*` | Running | KubeDB Monitor 컨트롤러 |
| **Demo App** | `university-registration-demo-*` | Running | 수강신청 데모 애플리케이션 |
| **Service** | `university-registration-demo-service` | Active | 클러스터 내부 서비스 |
| **Ingress** | `university-registration-demo-ingress` | Active | 외부 접근용 |

## 🛠️ 데모 환경 관리 스크립트

### 1. 완전한 환경 초기화 (권장)
```bash
# 모든 데모 리소스를 정리하고 새로 배포
./demo-environment-setup.sh
```

**사용 시기:**
- 첫 데모 환경 구축 시
- 환경이 크게 망가졌을 때
- 완전히 깨끗한 상태에서 시작하고 싶을 때

**소요 시간:** 약 2-3분

### 2. 빠른 데모 리셋
```bash
# 기존 Pod만 재시작 (H2 DB 초기화)
./demo-quick-reset.sh
```

**사용 시기:**
- 데모 중간에 데이터를 초기화하고 싶을 때
- Pod는 정상이지만 DB 상태만 리셋하고 싶을 때

**소요 시간:** 약 30초

### 3. 데모 검증 및 실행
```bash
# 전체 데모 시나리오 실행 및 검증
./demo-complete-validation.sh
```

**기능:**
- 환경 상태 자동 확인
- 데이터 초기화 (학과, 학생, 과목, 수강신청)
- KubeDB 모니터링 상태 검증
- 실시간 통계 분석 시뮬레이션

## 🔧 문제 해결

### 일반적인 문제들

#### 1. Pod가 CrashLoopBackOff 상태일 때
```bash
# 문제 Pod 확인
kubectl get pods -n kubedb-monitor-test

# 로그 확인
kubectl logs -n kubedb-monitor-test <POD_NAME> --previous

# 해결: 완전 환경 재구축
./demo-environment-setup.sh
```

#### 2. 포트 포워딩이 작동하지 않을 때
```bash
# 기존 포트 포워딩 정리
pkill -f "kubectl port-forward"

# 새로 설정
kubectl port-forward -n kubedb-monitor-test pod/<POD_NAME> 8080:8080
```

#### 3. 데이터가 보이지 않을 때
```bash
# 빠른 리셋으로 H2 DB 초기화
./demo-quick-reset.sh

# 데모 재실행
./demo-complete-validation.sh
```

## 📊 데모 시나리오 흐름

### 표준 데모 진행 순서

1. **환경 준비**
   ```bash
   ./demo-environment-setup.sh
   ```

2. **포트 포워딩 설정**
   ```bash
   kubectl port-forward -n kubedb-monitor-test pod/<POD_NAME> 8080:8080
   ```

3. **실시간 모니터링 시작**
   ```bash
   kubectl logs -n kubedb-monitor-test <POD_NAME> -f | grep 'KUBEDB-MONITOR'
   ```

4. **데모 실행**
   ```bash
   ./demo-complete-validation.sh
   ```

5. **추가 시연** (수동)
   - 학생 추가: `curl -X POST http://localhost:8080/api/students -H "Content-Type: application/json" -d '{...}'`
   - 통계 조회: `curl http://localhost:8080/api/reports/enrollment-status`

## 🎭 데모 환경별 설정

### 개발 환경
- 모든 로그 출력 활성화
- H2 Console 접근 가능 (포트 8080)
- 상세한 디버그 정보 제공

### 운영 데모 환경
- 핵심 모니터링 로그만 출력
- 성능 최적화된 설정
- 외부 접근용 Ingress 설정

## 🔍 모니터링 확인 방법

### KubeDB Agent 상태 확인
```bash
kubectl logs -n kubedb-monitor-test <POD_NAME> | grep "KubeDB Monitor Agent"
```

### 실시간 JDBC 인터셉션 확인
```bash
kubectl logs -n kubedb-monitor-test <POD_NAME> -f | grep "JDBC Method intercepted"
```

### 데이터베이스 쿼리 모니터링
```bash
kubectl logs -n kubedb-monitor-test <POD_NAME> -f | grep "KUBEDB-MONITOR"
```

## 📝 데모 후 정리

### 리소스 정리 (선택사항)
```bash
# 데모 애플리케이션만 삭제 (Controller는 유지)
kubectl delete deployment,service,ingress -n kubedb-monitor-test -l app=university-registration-demo

# 전체 네임스페이스 삭제
kubectl delete namespace kubedb-monitor-test
```

## 🚨 주의사항

1. **데이터 지속성**: H2 인메모리 DB 사용으로 Pod 재시작 시 모든 데이터가 초기화됩니다.
2. **포트 충돌**: 8080 포트가 사용 중인 경우 다른 포트 사용 필요
3. **리소스 제한**: 데모 환경은 최소 512MB 메모리, 0.2 CPU 필요
4. **네트워크**: Ingress 사용 시 도메인 설정 확인 필요

## 📞 문의사항

데모 환경 구축 중 문제가 발생하면:
1. 로그 확인: `kubectl logs -n kubedb-monitor-test <POD_NAME>`
2. 상태 확인: `kubectl describe pod -n kubedb-monitor-test <POD_NAME>`
3. 환경 재구축: `./demo-environment-setup.sh`