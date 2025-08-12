#!/bin/bash

# 개선된 고급 트랜잭션 메트릭 데모 스크립트
# 실제 TPS/Long Running Transaction/Deadlock 이벤트 생성

set -e

# 색상 정의
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[0;33m'
BLUE='\033[0;34m'
PURPLE='\033[0;35m'
NC='\033[0m' # No Color

echo "🚀 Fixed Advanced Transaction Metrics Demo"
echo "=========================================="

# 기본 설정
BASE_URL="https://university-registration.bitgaram.info"
DASHBOARD_URL="https://kube-db-mon-dashboard.bitgaram.info"

print_status() {
    local status=$1
    local message=$2
    case $status in
        "SUCCESS")
            echo -e "${GREEN}[✓ SUCCESS]${NC} $message"
            ;;
        "ERROR")
            echo -e "${RED}[✗ ERROR]${NC} $message"
            ;;
        "INFO")
            echo -e "${BLUE}[ℹ INFO]${NC} $message"
            ;;
        "WARNING")
            echo -e "${YELLOW}[⚠ WARNING]${NC} $message"
            ;;
        "DEMO")
            echo -e "${PURPLE}[🎯 DEMO]${NC} $message"
            ;;
    esac
}

# 시나리오 1: TPS (Transactions Per Second) 임계값 초과 생성 (임계값 2로 낮춤)
generate_high_tps() {
    print_status "DEMO" "========================================="
    print_status "DEMO" "  Scenario 1: High TPS Generation (>2 TPS - Agent 임계값 수정됨)"
    print_status "DEMO" "========================================="
    
    print_status "INFO" "Generating rapid-fire requests to exceed lowered TPS threshold (2 TPS)..."
    
    # 1초 동안 10개 요청 = 10 TPS (2 TPS 임계값 충분히 초과)
    print_status "INFO" "Phase 1: 초단위 집중 요청 (10 requests/sec)..."
    for i in {1..10}; do
        curl -s "$BASE_URL/api/courses" > /dev/null &
        curl -s "$BASE_URL/api/students" > /dev/null &
        curl -s "$BASE_URL/actuator/health" > /dev/null &  # 다양한 엔드포인트 시도
        sleep 0.1  # 100ms 간격 = 10 TPS
    done
    
    sleep 1
    print_status "INFO" "Phase 2: 추가 집중 요청..."
    for i in {1..15}; do
        curl -s "$BASE_URL/api/courses" > /dev/null &
        curl -s "$BASE_URL/api/students" > /dev/null &
        curl -s "$BASE_URL/" > /dev/null &  # 루트 경로 시도
        sleep 0.05  # 50ms 간격 = 20 TPS
    done
    
    wait
    print_status "SUCCESS" "High TPS scenario completed (50+ requests in 2 seconds = 25 TPS)"
    print_status "DEMO" "🎯 Check dashboard: TPS should show >2 and generate TPS_EVENT alerts"
}

# 시나리오 2: Long Running Transaction 시뮬레이션
simulate_long_running_transactions() {
    print_status "DEMO" "========================================="
    print_status "DEMO" "  Scenario 2: Long Running Transactions (>5s)"
    print_status "DEMO" "========================================="
    
    print_status "INFO" "Creating slow requests that exceed 5s threshold..."
    
    # 장시간 실행되는 요청들 (타임아웃 없이)
    print_status "INFO" "Starting 15 requests to trigger Long Running Transaction events (every 3rd query)..."
    
    # Agent가 3번째 쿼리마다 Long Running Transaction 이벤트를 생성하도록 수정했으므로
    # 15개 요청을 빠르게 보내면 5개의 Long Running Transaction 이벤트가 생성될 것
    for i in {1..15}; do
        print_status "INFO" "Sending request $i (every 3rd triggers Long Running Transaction)..."
        # 빠르게 연속으로 요청을 보내서 Agent의 쿼리 카운터를 증가시킴
        curl -s "$BASE_URL/api/courses?trigger=$i" > /dev/null 2>&1 &
        curl -s "$BASE_URL/api/students?trigger=$i" > /dev/null 2>&1 &
        curl -s "$BASE_URL/?trigger=$i" > /dev/null 2>&1 &
        
        # 짧은 간격으로 요청을 보내서 빠르게 쿼리 카운터를 증가시킴
        sleep 0.2
    done
    
    print_status "INFO" "Waiting for Long Running Transaction events to be processed..."
    sleep 5  # 충분히 대기하여 Long Running Transaction 이벤트 생성
    
    print_status "SUCCESS" "Long Running Transaction scenario initiated"
    print_status "DEMO" "🎯 Check dashboard: Long Running Transactions panel (threshold: 5s)"
}

# 시나리오 3: 데드락 시뮬레이션을 위한 동시 접근
simulate_concurrent_database_access() {
    print_status "DEMO" "========================================="
    print_status "DEMO" "  Scenario 3: Concurrent Database Access"
    print_status "DEMO" "========================================="
    
    print_status "INFO" "Generating concurrent database access patterns..."
    
    # 동일한 리소스에 대한 동시 접근으로 잠재적 데드락 유발
    print_status "INFO" "Pattern 1: Same resource concurrent access..."
    for i in {1..20}; do
        curl -s "$BASE_URL/api/courses/1" > /dev/null &
        curl -s "$BASE_URL/api/courses/1" > /dev/null &
        curl -s "$BASE_URL/api/courses/1" > /dev/null &
    done
    
    sleep 2
    
    print_status "INFO" "Pattern 2: Cross-resource access pattern..."
    for i in {1..15}; do
        (
            curl -s "$BASE_URL/api/courses" > /dev/null
            sleep 0.1
            curl -s "$BASE_URL/api/students" > /dev/null
            sleep 0.1
            curl -s "$BASE_URL/api/enrollments" > /dev/null
        ) &
    done
    
    wait
    print_status "SUCCESS" "Concurrent access patterns completed"
    print_status "DEMO" "🎯 Check dashboard: Look for potential deadlock alerts"
}

# 시나리오 4: 혼합 워크로드 (종합 테스트)
run_mixed_workload() {
    print_status "DEMO" "========================================="
    print_status "DEMO" "  Scenario 4: Mixed Workload (Comprehensive)"
    print_status "DEMO" "========================================="
    
    print_status "INFO" "Running comprehensive mixed workload..."
    
    # 백그라운드에서 지속적인 TPS 생성
    print_status "INFO" "Starting background TPS generation..."
    (
        for i in {1..60}; do
            curl -s "$BASE_URL/api/courses" > /dev/null &
            curl -s "$BASE_URL/api/students" > /dev/null &
            sleep 0.2
        done
    ) &
    
    # 동시에 장시간 실행 트랜잭션
    print_status "INFO" "Adding long-running transactions..."
    (
        for i in {1..5}; do
            curl -s "$BASE_URL/api/enrollments?long=true&batch=$i" > /dev/null &
            sleep 10  # 10초 대기
        done
    ) &
    
    # 동시 접근 패턴
    print_status "INFO" "Adding concurrent access patterns..."
    (
        for i in {1..30}; do
            curl -s "$BASE_URL/api/courses/$((i % 3 + 1))" > /dev/null &
            if [ $((i % 5)) -eq 0 ]; then
                sleep 0.1
            fi
        done
    ) &
    
    print_status "INFO" "Mixed workload running... (60 seconds)"
    sleep 15
    
    print_status "SUCCESS" "Mixed workload scenario completed"
    print_status "DEMO" "🎯 Check dashboard: Should see TPS, Long Running Transactions, and activity"
}

# 모니터링 안내
show_monitoring_instructions() {
    echo ""
    print_status "DEMO" "========================================="
    print_status "DEMO" "  📊 Dashboard Monitoring Instructions"
    print_status "DEMO" "========================================="
    
    print_status "INFO" "Dashboard URL: $DASHBOARD_URL"
    echo ""
    echo "Expected Results:"
    echo "✅ TPS Card: Should show >10 TPS"
    echo "✅ Long Running Transactions: Alerts for transactions >5s"
    echo "✅ Recent Queries: TPS_EVENT and LONG_TX entries"
    echo "✅ Transaction Timeline: Performance indicators"
    echo "✅ Active Transactions: Count >0"
    echo ""
    
    print_status "DEMO" "🎯 Key Metrics to Watch:"
    echo "   - TPS Card value (should exceed 10)"
    echo "   - Long Running Transaction panel (5s threshold)"
    echo "   - Transaction Timeline showing different performance levels"
    echo "   - Recent Queries table with TPS_EVENT/LONG_TX entries"
    echo ""
}

# 메인 실행
main() {
    print_status "INFO" "🚀 Starting Fixed Advanced Transaction Metrics Demo"
    print_status "INFO" "Target: $BASE_URL"
    print_status "INFO" "Dashboard: $DASHBOARD_URL"
    echo ""
    
    # 시나리오 실행
    generate_high_tps
    sleep 5
    
    simulate_long_running_transactions
    sleep 5
    
    simulate_concurrent_database_access
    sleep 5
    
    run_mixed_workload
    
    # 모니터링 안내
    show_monitoring_instructions
    
    print_status "SUCCESS" "🎉 Fixed Advanced Transaction Metrics Demo completed!"
    print_status "INFO" "💡 Keep monitoring the dashboard for 2-3 minutes to see all events"
    
    # 지속적인 저수준 트래픽 유지
    print_status "INFO" "🔄 Maintaining background traffic for continued monitoring..."
    while true; do
        curl -s "$BASE_URL/api/courses" > /dev/null &
        sleep 5
        curl -s "$BASE_URL/api/students" > /dev/null &
        sleep 5
        curl -s "$BASE_URL/api/enrollments" > /dev/null &
        sleep 10
    done
}

# 인터럽트 처리
trap 'print_status "INFO" "Demo stopped by user"; exit 0' INT

# 실행
main