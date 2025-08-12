#!/bin/bash

# 강제로 Long Running Transaction과 Deadlock 이벤트를 생성하는 데모 스크립트
BASE_URL="https://university-registration.bitgaram.info"

echo "🚀 Force Event Generation Demo"
echo "=============================="
echo "목표: Agent의 쿼리 카운터를 증가시켜 Long Running Transaction 이벤트 생성"
echo "현재 Agent 설정:"
echo "  - TPS 임계값: >2 TPS"
echo "  - Long Running Transaction: 2번째 쿼리마다 생성"
echo ""

# 함수: 상태 출력
print_status() {
    local status=$1
    local message=$2
    case $status in
        "SUCCESS") echo -e "\033[0;32m[✓ SUCCESS]\033[0m $message" ;;
        "ERROR") echo -e "\033[0;31m[✗ ERROR]\033[0m $message" ;;
        "INFO") echo -e "\033[0;34m[ℹ INFO]\033[0m $message" ;;
        "WARNING") echo -e "\033[0;33m[⚠ WARNING]\033[0m $message" ;;
        "DEMO") echo -e "\033[0;35m[🎯 DEMO]\033[0m $message" ;;
    esac
}

# Phase 1: 쿼리 카운터 증가를 위한 집중적 단일 요청
print_status "DEMO" "Phase 1: 쿼리 카운터 증가 (2번째마다 Long Running Transaction 생성)"
print_status "INFO" "20개 개별 요청을 1초 간격으로 전송..."

for i in {1..20}; do
    print_status "INFO" "Request $i: 쿼리 카운터 증가 목적"
    curl -s "$BASE_URL/api/courses?counter=$i" > /dev/null
    sleep 0.5  # 0.5초 간격으로 개별 요청
done

print_status "SUCCESS" "Phase 1 완료: 20개 요청으로 Agent 쿼리 카운터 증가"

# Phase 2: 더 많은 요청으로 확실한 이벤트 생성
print_status "DEMO" "Phase 2: 추가 집중 요청 (Long Running Transaction 확실 생성)"

for i in {21..40}; do
    print_status "INFO" "Request $i: Long Running Transaction 트리거 목적"
    curl -s "$BASE_URL/?force=$i" > /dev/null
    sleep 0.3  # 더 빠른 간격
done

print_status "SUCCESS" "Phase 2 완료: 추가 20개 요청"

# Phase 3: 최종 확인용 burst 요청
print_status "DEMO" "Phase 3: 최종 burst 요청 (이벤트 생성 보장)"

print_status "INFO" "10개 동시 요청 burst..."
for i in {41..50}; do
    curl -s "$BASE_URL/api/students?final=$i" > /dev/null &
done
wait

sleep 2

print_status "SUCCESS" "🎉 Force Event Generation 완료!"
print_status "INFO" "총 50개 요청 전송 - Agent 쿼리 카운터 충분히 증가"

echo ""
print_status "DEMO" "🎯 예상 결과:"
echo "   ✅ Agent 로그에 '🐌 DEMO: Simulating Long Running Transaction' 메시지"
echo "   ✅ Control Plane에 'long_running_transaction' 이벤트 수신"
echo "   ✅ Dashboard Transaction Timeline에 트랜잭션 표시"
echo "   ✅ Long Running Transactions 패널에 5s 초과 트랜잭션 표시"

echo ""
print_status "INFO" "📊 확인 명령어들:"
echo "Agent 로그: kubectl logs university-registration-demo-7465ffd546-vf4xz -n kubedb-monitor-test --tail=20"
echo "Control Plane 로그: kubectl logs -l app=kubedb-monitor-control-plane -n kubedb-monitor --tail=10"
echo "Dashboard: https://kube-db-mon-dashboard.bitgaram.info"

# 지속적인 배경 트래픽으로 이벤트 유지
print_status "INFO" "🔄 배경 트래픽 시작 (이벤트 지속 생성)..."

while true; do
    # 매 5초마다 2개 요청으로 지속적 이벤트 생성
    curl -s "$BASE_URL/?bg=1" > /dev/null &
    sleep 2
    curl -s "$BASE_URL/?bg=2" > /dev/null &
    sleep 3
done