#!/bin/bash

# KubeDB Monitor Demo Validation Script
# 데모 시나리오의 주요 기능들을 빠르게 검증하는 스크립트

set -e

echo "🚀 KubeDB Monitor Demo Validation Script 시작"
echo "============================================"

# 색상 정의
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# 함수 정의
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

# Step 1: 환경 확인
print_status "Step 1: 환경 확인 중..."

# Kubernetes 연결 확인
if kubectl cluster-info > /dev/null 2>&1; then
    print_success "Kubernetes 클러스터 연결 정상"
else
    print_error "Kubernetes 클러스터 연결 실패"
    exit 1
fi

# 네임스페이스 확인
if kubectl get namespace kubedb-monitor-test > /dev/null 2>&1; then
    print_success "kubedb-monitor-test 네임스페이스 존재"
else
    print_error "kubedb-monitor-test 네임스페이스가 존재하지 않습니다"
    exit 1
fi

# Step 2: 애플리케이션 상태 확인
print_status "Step 2: 애플리케이션 상태 확인 중..."

# Pod 상태 확인
POD_STATUS=$(kubectl get pods -n kubedb-monitor-test -l app=university-registration-monitored --no-headers | awk '{print $3}' | head -1)
if [ "$POD_STATUS" = "Running" ]; then
    print_success "수강신청 앱이 정상 실행 중입니다"
else
    print_warning "수강신청 앱 상태: $POD_STATUS"
    print_status "Pod 세부 정보:"
    kubectl get pods -n kubedb-monitor-test -l app=university-registration-monitored
fi

# Step 3: KubeDB Agent 주입 확인
print_status "Step 3: KubeDB Agent 주입 확인 중..."

# Init Container 확인
INIT_CONTAINER_COUNT=$(kubectl get pods -n kubedb-monitor-test -l app=university-registration-monitored -o jsonpath='{.items[0].spec.initContainers[*].name}' | grep -c "kubedb-agent-init" || echo "0")
if [ "$INIT_CONTAINER_COUNT" -gt 0 ]; then
    print_success "KubeDB Agent Init Container가 정상 주입됨"
else
    print_warning "KubeDB Agent Init Container를 찾을 수 없습니다"
fi

# Agent 로그 확인
AGENT_LOGS=$(kubectl logs -n kubedb-monitor-test -l app=university-registration-monitored 2>/dev/null | grep -c "KubeDB Monitor Agent started successfully" || echo "0")
if [ "$AGENT_LOGS" -gt 0 ]; then
    print_success "KubeDB Monitor Agent가 정상 시작됨"
else
    print_warning "KubeDB Monitor Agent 시작 로그를 찾을 수 없습니다"
fi

# Step 4: 데이터베이스 모니터링 확인
print_status "Step 4: 데이터베이스 모니터링 확인 중..."

# 클래스 변환 로그 확인
TRANSFORMED_CLASSES=$(kubectl logs -n kubedb-monitor-test -l app=university-registration-monitored 2>/dev/null | grep -c "Successfully transformed class" || echo "0")
if [ "$TRANSFORMED_CLASSES" -gt 0 ]; then
    print_success "데이터베이스 클래스 변환 완료 (변환된 클래스: $TRANSFORMED_CLASSES개)"
else
    print_warning "데이터베이스 클래스 변환 로그를 찾을 수 없습니다"
fi

# Step 5: Port Forward 설정 및 API 테스트
print_status "Step 5: API 연결 테스트 중..."

# 기존 Port Forward 정리
pkill -f "kubectl port-forward" 2>/dev/null || true
sleep 2

# Port Forward 설정 (백그라운드)
kubectl port-forward -n kubedb-monitor-test deployment/university-registration-monitored 8080:8080 > /dev/null 2>&1 &
PORT_FORWARD_PID=$!
sleep 5

# Health Check API 테스트
if curl -s http://localhost:8080/api/actuator/health > /dev/null 2>&1; then
    print_success "애플리케이션 Health Check API 정상 응답"
    
    # 간단한 기능 테스트
    print_status "기본 API 테스트 중..."
    
    # 학생 목록 조회 (빈 응답이라도 200 OK면 정상)
    if curl -s -o /dev/null -w "%{http_code}" http://localhost:8080/api/students | grep -q "200"; then
        print_success "학생 목록 API 정상 응답"
    else
        print_warning "학생 목록 API 응답 이상"
    fi
    
    # 과목 목록 조회
    if curl -s -o /dev/null -w "%{http_code}" http://localhost:8080/api/courses | grep -q "200"; then
        print_success "과목 목록 API 정상 응답"
    else
        print_warning "과목 목록 API 응답 이상"
    fi
    
else
    print_error "애플리케이션 Health Check API 응답 실패"
fi

# Port Forward 정리
kill $PORT_FORWARD_PID 2>/dev/null || true

# Step 6: 모니터링 데이터 확인
print_status "Step 6: 모니터링 데이터 확인 중..."

# KUBEDB-MONITOR 로그 확인
MONITOR_LOGS=$(kubectl logs -n kubedb-monitor-test -l app=university-registration-monitored --since=5m 2>/dev/null | grep -c "KUBEDB-MONITOR" || echo "0")
if [ "$MONITOR_LOGS" -gt 0 ]; then
    print_success "데이터베이스 모니터링 로그 발견 ($MONITOR_LOGS개)"
    print_status "최근 모니터링 로그 샘플:"
    kubectl logs -n kubedb-monitor-test -l app=university-registration-monitored --since=5m 2>/dev/null | grep "KUBEDB-MONITOR" | tail -3
else
    print_warning "데이터베이스 모니터링 로그를 찾을 수 없습니다 (아직 DB 활동이 없을 수 있음)"
fi

# Step 7: 최종 상태 요약
echo ""
print_status "=== 최종 검증 결과 요약 ==="
echo ""

echo "📊 시스템 상태:"
echo "  - Kubernetes 클러스터: ✅ 연결됨"
echo "  - 수강신청 앱: $([ "$POD_STATUS" = "Running" ] && echo "✅ 실행 중" || echo "⚠️ $POD_STATUS")"
echo "  - KubeDB Agent: $([ "$AGENT_LOGS" -gt 0 ] && echo "✅ 정상 작동" || echo "⚠️ 확인 필요")"
echo "  - 데이터베이스 모니터링: $([ "$TRANSFORMED_CLASSES" -gt 0 ] && echo "✅ 활성화" || echo "⚠️ 확인 필요")"

echo ""
echo "🎯 데모 준비 상태:"
if [ "$POD_STATUS" = "Running" ] && [ "$AGENT_LOGS" -gt 0 ] && [ "$TRANSFORMED_CLASSES" -gt 0 ]; then
    print_success "✅ 모든 시스템이 정상입니다. 데모를 시작할 수 있습니다!"
else
    print_warning "⚠️ 일부 시스템에 이슈가 있습니다. 로그를 확인해주세요."
fi

echo ""
echo "📝 데모 시작 방법:"
echo "  1. 포트 포워딩: kubectl port-forward -n kubedb-monitor-test deployment/university-registration-monitored 8080:8080"
echo "  2. 로그 모니터링: kubectl logs -n kubedb-monitor-test -l app=university-registration-monitored -f | grep 'KUBEDB-MONITOR'"
echo "  3. API 테스트: curl http://localhost:8080/api/students"
echo ""
echo "🔗 관련 문서:"
echo "  - 상세 데모 가이드: DEMO_SCENARIO_GUIDE.md"
echo "  - 기술 문서: README.md"
echo ""
echo "============================================"
echo "🎉 KubeDB Monitor Demo Validation 완료"
