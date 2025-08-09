#!/bin/bash

# KubeDB Monitor 빠른 데모 리셋 스크립트
# 기존 Pod만 재시작하여 빠르게 초기화

set -e

echo "🔄 KubeDB Monitor 빠른 데모 리셋 시작"

# 색상 정의
GREEN='\033[0;32m'
BLUE='\033[0;34m'
NC='\033[0m'

print_status() {
    echo -e "${BLUE}[정보]${NC} $1"
}

print_success() {
    echo -e "${GREEN}[성공]${NC} $1"
}

# 포트 포워딩 정리
print_status "포트 포워딩 정리 중..."
pkill -f "kubectl port-forward" 2>/dev/null || true

# 데모 Pod 재시작 (H2 인메모리 DB 초기화됨)
print_status "데모 애플리케이션 재시작 중..."
kubectl rollout restart deployment/university-registration-demo -n kubedb-monitor-test

# 재시작 완료 대기
print_status "재시작 완료 대기 중..."
kubectl rollout status deployment/university-registration-demo -n kubedb-monitor-test --timeout=120s

# 새로운 Pod 이름 확인
NEW_POD=$(kubectl get pods -n kubedb-monitor-test -l app=university-registration-demo --no-headers | awk '{print $1}')

print_success "✅ 빠른 리셋 완료!"
echo ""
echo "🎯 새로운 Pod: $NEW_POD"
echo "💡 사용법:"
echo "  1. 포트 포워딩: kubectl port-forward -n kubedb-monitor-test pod/$NEW_POD 8080:8080"
echo "  2. 데모 실행: ./demo-complete-validation.sh"
echo ""