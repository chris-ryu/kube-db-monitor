#!/bin/bash

# Local Long Running Transaction Test Script
# 실제 Agent 환경에서 Long Running Transaction 검증

set -e

RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[0;33m'
BLUE='\033[0;34m'
NC='\033[0m'

print_status() {
    local status=$1
    local message=$2
    case $status in
        "SUCCESS") echo -e "${GREEN}[✓ SUCCESS]${NC} $message" ;;
        "ERROR") echo -e "${RED}[✗ ERROR]${NC} $message" ;;
        "INFO") echo -e "${BLUE}[ℹ INFO]${NC} $message" ;;
        "WARNING") echo -e "${YELLOW}[⚠ WARNING]${NC} $message" ;;
    esac
}

echo "🧪 Local Long Running Transaction Test"
echo "======================================"

# 로컬 Pod URL 확인
LOCAL_URL="http://localhost:8090"
DASHBOARD_URL="http://localhost:3000"

print_status "INFO" "Testing connectivity..."
if ! curl -s --max-time 5 "$LOCAL_URL/actuator/health" > /dev/null; then
    print_status "ERROR" "Cannot connect to $LOCAL_URL"
    print_status "INFO" "Make sure port-forward is running:"
    print_status "INFO" "kubectl port-forward -n kubedb-monitor-test university-registration-demo-xxx 8090:8080"
    exit 1
fi

print_status "SUCCESS" "Connected to university demo app at $LOCAL_URL"

# 단계 1: 기본 데이터베이스 쿼리로 Agent 동작 확인
test_basic_queries() {
    print_status "INFO" "Step 1: Testing basic database queries (to confirm Agent is working)..."
    
    # 여러 엔드포인트를 빠르게 호출하여 Agent의 JDBC 인터셉션 확인
    for i in {1..5}; do
        print_status "INFO" "Basic query $i..."
        curl -s "$LOCAL_URL/courses" > /dev/null 2>&1 &
        curl -s "$LOCAL_URL/actuator/health" > /dev/null 2>&1 &
        sleep 0.5
    done
    
    wait
    print_status "SUCCESS" "Basic queries completed - check Agent logs for JDBC interception"
}

# 단계 2: Transaction 생성을 위한 반복적인 데이터베이스 접근
generate_transaction_load() {
    print_status "INFO" "Step 2: Generating transaction load to trigger Long Running Transaction detection..."
    
    # Agent는 TransactionAwareJDBCInterceptor에서 5초 이상의 트랜잭션을 감지
    # 따라서 연속적으로 쿼리를 실행하여 트랜잭션이 5초 이상 지속되도록 함
    
    print_status "INFO" "Creating sustained database activity (10 second duration)..."
    
    # 10초 동안 연속으로 데이터베이스 쿼리 실행
    start_time=$(date +%s)
    counter=1
    
    while [ $(($(date +%s) - start_time)) -lt 10 ]; do
        print_status "INFO" "Transaction query batch $counter..."
        
        # 각 배치에서 여러 쿼리를 빠르게 실행하여 트랜잭션 지속시간 증가
        curl -s "$LOCAL_URL/courses" > /dev/null 2>&1 &
        curl -s "$LOCAL_URL/courses/available" > /dev/null 2>&1 &
        curl -s "$LOCAL_URL/courses/popular" > /dev/null 2>&1 &
        curl -s "$LOCAL_URL/courses/stats/department" > /dev/null 2>&1 &
        
        # 짧은 간격으로 쿼리를 계속 실행하여 트랜잭션이 지속되도록 함
        sleep 0.3
        counter=$((counter + 1))
    done
    
    wait
    print_status "SUCCESS" "Transaction load generation completed (10+ seconds of sustained DB activity)"
}

# 단계 3: Agent 로그 확인
check_agent_logs() {
    print_status "INFO" "Step 3: Checking Agent logs for Long Running Transaction detection..."
    
    # Pod 이름 자동 검색
    POD_NAME=$(kubectl get pods -n kubedb-monitor-test -l app=university-registration-demo --no-headers -o custom-columns=":metadata.name" | head -1)
    
    if [ -z "$POD_NAME" ]; then
        print_status "ERROR" "Cannot find university demo pod"
        return 1
    fi
    
    print_status "INFO" "Checking logs from pod: $POD_NAME"
    
    # Long Running Transaction 관련 로그 검색
    echo ""
    print_status "INFO" "🔍 Searching for Long Running Transaction events in Agent logs..."
    
    if kubectl logs -n kubedb-monitor-test "$POD_NAME" --tail=50 | grep -i "Long Running Transaction\|LONG_RUNNING_TRANSACTION"; then
        print_status "SUCCESS" "✅ Found Long Running Transaction events in Agent logs!"
    else
        print_status "WARNING" "⚠️  No Long Running Transaction events found in Agent logs"
        print_status "INFO" "This could mean:"
        print_status "INFO" "  - Transactions completed in <5 seconds (threshold not met)"
        print_status "INFO" "  - Agent transaction detection needs more time"
        print_status "INFO" "  - Database queries are not creating proper transactions"
    fi
    
    echo ""
    print_status "INFO" "🔍 Recent Agent activity (last 20 lines):"
    kubectl logs -n kubedb-monitor-test "$POD_NAME" --tail=20
}

# 단계 4: Dashboard 확인 안내
check_dashboard() {
    print_status "INFO" "Step 4: Dashboard verification instructions..."
    
    echo ""
    print_status "INFO" "📊 Check Dashboard at: $DASHBOARD_URL"
    print_status "INFO" "Expected to see:"
    print_status "INFO" "  - Long Running Transaction Alert panel with active alerts"
    print_status "INFO" "  - Transaction Timeline showing activity"
    print_status "INFO" "  - Recent Queries table with LONG_TX entries"
    
    echo ""
    print_status "INFO" "If no Long Running Transactions appear:"
    print_status "INFO" "  1. Wait 30-60 seconds for events to propagate"
    print_status "INFO" "  2. Check that transactions exceed 5-second threshold"
    print_status "INFO" "  3. Verify Agent is properly instrumenting JDBC calls"
}

# 단계 5: 더 강력한 Long Running Transaction 시뮬레이션
simulate_extended_transactions() {
    print_status "INFO" "Step 5: Extended transaction simulation (30 second sustained load)..."
    
    # 더 긴 시간 동안 지속적인 데이터베이스 활동
    print_status "INFO" "Creating 30-second sustained transaction load..."
    
    start_time=$(date +%s)
    batch_num=1
    
    while [ $(($(date +%s) - start_time)) -lt 30 ]; do
        print_status "INFO" "Extended batch $batch_num ($(date +%s - start_time)s elapsed)..."
        
        # 각 배치에서 더 많은 다양한 쿼리 실행
        curl -s "$LOCAL_URL/courses" > /dev/null 2>&1 &
        curl -s "$LOCAL_URL/courses/available" > /dev/null 2>&1 &
        curl -s "$LOCAL_URL/courses/popular?threshold=0.5" > /dev/null 2>&1 &
        curl -s "$LOCAL_URL/courses/stats/department" > /dev/null 2>&1 &
        curl -s "$LOCAL_URL/courses/stats/professor" > /dev/null 2>&1 &
        curl -s "$LOCAL_URL/courses/enrollment-details" > /dev/null 2>&1 &
        
        sleep 1  # 1초 간격으로 배치 실행
        batch_num=$((batch_num + 1))
    done
    
    wait
    print_status "SUCCESS" "✅ Extended transaction simulation completed (30+ seconds)"
    print_status "INFO" "This should definitely trigger Long Running Transaction detection!"
}

# 메인 실행
main() {
    print_status "INFO" "🚀 Starting Local Long Running Transaction Test"
    print_status "INFO" "Target: $LOCAL_URL"
    print_status "INFO" "Dashboard: $DASHBOARD_URL"
    echo ""
    
    # 테스트 실행
    test_basic_queries
    sleep 2
    
    generate_transaction_load
    sleep 3
    
    check_agent_logs
    sleep 2
    
    simulate_extended_transactions
    sleep 3
    
    print_status "SUCCESS" "🎉 Long Running Transaction test completed!"
    print_status "INFO" "💡 Final check - looking for Long Running Transaction events..."
    
    # 최종 로그 체크
    POD_NAME=$(kubectl get pods -n kubedb-monitor-test -l app=university-registration-demo --no-headers -o custom-columns=":metadata.name" | head -1)
    if kubectl logs -n kubedb-monitor-test "$POD_NAME" --tail=30 | grep -i "Long Running Transaction\|LONG_RUNNING_TRANSACTION\|🐌"; then
        print_status "SUCCESS" "🎯 CONFIRMED: Long Running Transaction events detected!"
    else
        print_status "WARNING" "⚠️  Still no Long Running Transaction events detected"
        print_status "INFO" "💡 Recommendation: Check Agent transaction detection logic"
    fi
    
    check_dashboard
}

# 실행
main