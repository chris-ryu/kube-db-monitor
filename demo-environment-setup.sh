#!/bin/bash

# KubeDB Monitor 데모 환경 초기화 및 설정 스크립트
# 매번 깨끗한 데모 환경을 준비하는 스크립트

set -e

# 색상 정의
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
PURPLE='\033[0;35m'
NC='\033[0m' # No Color

# 함수 정의
print_header() {
    echo -e "${PURPLE}========================================${NC}"
    echo -e "${PURPLE}  $1${NC}"
    echo -e "${PURPLE}========================================${NC}"
}

print_status() {
    echo -e "${BLUE}[정보]${NC} $1"
}

print_success() {
    echo -e "${GREEN}[성공]${NC} $1"
}

print_warning() {
    echo -e "${YELLOW}[경고]${NC} $1"
}

print_error() {
    echo -e "${RED}[오류]${NC} $1"
}

# 스크립트 시작
echo "🚀 KubeDB Monitor 데모 환경 초기화 시작"
print_header "Step 1: 기존 환경 정리"

# 네임스페이스 확인
if ! kubectl get namespace kubedb-monitor-test > /dev/null 2>&1; then
    print_status "kubedb-monitor-test 네임스페이스 생성 중..."
    kubectl create namespace kubedb-monitor-test
    print_success "네임스페이스 생성 완료"
else
    print_success "kubedb-monitor-test 네임스페이스 존재 확인"
fi

# 기존 배포들 정리 (KubeDB Controller는 유지)
print_status "기존 데모 배포들 정리 중..."

# 기존 university-registration 관련 배포 모두 삭제
kubectl delete deployment -n kubedb-monitor-test university-registration-demo --ignore-not-found=true || true
kubectl delete deployment -n kubedb-monitor-test university-registration-basic --ignore-not-found=true || true
kubectl delete deployment -n kubedb-monitor-test university-registration-final --ignore-not-found=true || true
kubectl delete deployment -n kubedb-monitor-test university-registration --ignore-not-found=true || true
kubectl delete service -n kubedb-monitor-test university-registration-demo-service --ignore-not-found=true || true
kubectl delete service -n kubedb-monitor-test university-registration-basic-service --ignore-not-found=true || true
kubectl delete service -n kubedb-monitor-test university-registration-service --ignore-not-found=true || true
kubectl delete ingress -n kubedb-monitor-test --all --ignore-not-found=true || true

print_success "기존 배포 정리 완료"

# 포트 포워딩 정리
print_status "기존 포트 포워딩 정리 중..."
pkill -f "kubectl port-forward" 2>/dev/null || true
print_success "포트 포워딩 정리 완료"

# 잠시 대기 (리소스 정리 시간)
print_status "리소스 정리 대기 중... (10초)"
sleep 10

print_header "Step 2: 데모 환경 배포"

# 최신 데모 환경 배포
print_status "KubeDB Monitor 데모 환경 배포 중..."
kubectl apply -f k8s/university-registration-demo-complete.yaml

print_success "데모 환경 배포 완료"

print_header "Step 3: 배포 상태 확인"

# 배포 상태 대기
print_status "Pod 시작 대기 중..."
kubectl wait --for=condition=ready pod -l app=university-registration-demo -n kubedb-monitor-test --timeout=300s

# 최종 상태 확인
print_status "배포 상태 확인 중..."
echo ""
kubectl get pods -n kubedb-monitor-test
echo ""

# Health Check 확인
POD_NAME=$(kubectl get pods -n kubedb-monitor-test -l app=university-registration-demo --no-headers | awk '{print $1}' | head -1)

if [ -n "$POD_NAME" ]; then
    print_status "애플리케이션 Health Check 확인 중..."
    
    # 포트 포워딩 설정
    kubectl port-forward -n kubedb-monitor-test pod/"$POD_NAME" 8080:8080 > /dev/null 2>&1 &
    PORT_FORWARD_PID=$!
    sleep 5
    
    # Health Check 테스트
    if curl -s http://localhost:8080/api/actuator/health > /dev/null 2>&1; then
        print_success "애플리케이션이 정상적으로 시작되었습니다"
        
        # KubeDB Agent 확인
        AGENT_LOGS=$(kubectl logs -n kubedb-monitor-test "$POD_NAME" | grep -c "KubeDB Monitor Agent started successfully" || echo "0")
        if [ "$AGENT_LOGS" -gt 0 ]; then
            print_success "KubeDB Monitor Agent 정상 작동 중"
        else
            print_warning "KubeDB Monitor Agent 상태 확인 필요"
        fi
        
    else
        print_warning "애플리케이션이 아직 준비되지 않았습니다"
    fi
    
    # 포트 포워딩 정리
    kill $PORT_FORWARD_PID 2>/dev/null || true
    
else
    print_error "데모 Pod를 찾을 수 없습니다"
    exit 1
fi

print_header "Step 4: 데모 준비 완료"

echo ""
print_success "✅ KubeDB Monitor 데모 환경이 성공적으로 초기화되었습니다!"
echo ""

echo "📋 현재 배포된 리소스:"
echo "  - KubeDB Monitor Controller: kubedb-monitor-test-*"
echo "  - Demo Application: university-registration-demo-*"
echo "  - Service: university-registration-demo-service"
echo "  - Ingress: university-registration-demo-ingress"
echo ""

echo "🎯 데모 실행 준비:"
echo "  1. 포트 포워딩: kubectl port-forward -n kubedb-monitor-test pod/$POD_NAME 8080:8080"
echo "  2. 데모 검증: ./demo-complete-validation.sh"
echo "  3. 실시간 모니터링: kubectl logs -n kubedb-monitor-test $POD_NAME -f | grep 'JDBC Method intercepted'"
echo ""

echo "🔗 웹 접속:"
echo "  - Health Check: http://localhost:8080/api/actuator/health"
echo "  - Metrics: http://localhost:8080/api/actuator/metrics"
echo "  - 외부 접속: https://university-registration.bitgaram.info (Ingress 설정됨)"
echo ""

echo "⚠️  참고사항:"
echo "  - 데모 실행 전 포트 포워딩을 설정해주세요"
echo "  - 매 데모마다 이 스크립트를 실행하여 깨끗한 환경을 만들 수 있습니다"
echo "  - 데이터는 H2 인메모리 DB를 사용하므로 Pod 재시작 시 초기화됩니다"
echo ""

print_success "🎉 데모 환경 초기화 완료!"