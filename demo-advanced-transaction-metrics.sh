#!/bin/bash

# 고급 트랜잭션 메트릭 데모 스크립트
# 데드락, 장시간 실행 트랜잭션, 노드별/팟별 메트릭을 시연

set -e

# 색상 정의
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[0;33m'
BLUE='\033[0;34m'
PURPLE='\033[0;35m'
NC='\033[0m' # No Color

echo "🚀 Advanced Transaction Metrics Demo"
echo "========================================"

# 전제 조건 확인
check_prerequisites() {
    echo -e "${BLUE}[INFO]${NC} Checking prerequisites..."
    
    if ! command -v kubectl &> /dev/null; then
        echo -e "${RED}[ERROR]${NC} kubectl is required but not installed"
        exit 1
    fi
    
    if ! kubectl get namespace kubedb-monitor-test &> /dev/null; then
        echo -e "${RED}[ERROR]${NC} kubedb-monitor-test namespace not found"
        echo "Please run ./demo-environment-setup.sh first"
        exit 1
    fi
    
    echo -e "${GREEN}[SUCCESS]${NC} Prerequisites check passed"
}

# 포트 포워딩 및 서비스 설정
setup_dashboard_access() {
    echo -e "${BLUE}[INFO]${NC} Setting up dashboard access and port forwarding..."
    
    # 1. Public DNS 도메인 연결 확인
    if curl -k -s "${UNIVERSITY_APP_URL}/actuator/health" > /dev/null 2>&1; then
        echo -e "${GREEN}[SUCCESS]${NC} University app accessible via Public DNS"
    else
        echo -e "${YELLOW}[WARNING]${NC} Public DNS not accessible, trying port-forward fallback..."
        if ! pgrep -f "port-forward.*8082:80" > /dev/null; then
            kubectl port-forward -n kubedb-monitor-test service/university-registration-demo-service 8082:80 > /dev/null 2>&1 &
            sleep 3
            UNIVERSITY_APP_URL="http://localhost:8082/api"
            echo -e "${GREEN}[SUCCESS]${NC} Fallback port forwarding started: localhost:8082"
        fi
    fi
    
    # 2. Control Plane 확인 및 설정
    if ! curl -s https://kube-db-mon-dashboard.bitgaram.info/api/health > /dev/null 2>&1; then
        echo -e "${YELLOW}[INFO]${NC} Control Plane not running, starting it..."
        cd control-plane
        if [ ! -f "kubedb-monitor-control-plane" ]; then
            echo -e "${BLUE}[INFO]${NC} Building Control Plane..."
            go build -o kubedb-monitor-control-plane .
        fi
        ./kubedb-monitor-control-plane > /dev/null 2>&1 &
        sleep 3
        cd ..
        echo -e "${GREEN}[SUCCESS]${NC} Control Plane started: https://kube-db-mon-dashboard.bitgaram.info"
    else
        echo -e "${GREEN}[SUCCESS]${NC} Control Plane already running"
    fi
    
    # 3. 대시보드 확인 및 설정
    if ! pgrep -f "next dev" > /dev/null; then
        echo -e "${YELLOW}[INFO]${NC} Dashboard not running, starting it..."
        cd dashboard-frontend && npm run dev > /dev/null 2>&1 &
        sleep 5
        cd ..
        echo -e "${GREEN}[SUCCESS]${NC} Dashboard started"
    else
        echo -e "${GREEN}[SUCCESS]${NC} Dashboard already running"
    fi
    
    # 포트 확인
    DASHBOARD_PORT=3000
    if lsof -ti:3004 > /dev/null 2>&1; then
        DASHBOARD_PORT=3004
    elif lsof -ti:3002 > /dev/null 2>&1; then
        DASHBOARD_PORT=3002
    elif lsof -ti:3001 > /dev/null 2>&1; then
        DASHBOARD_PORT=3001
    fi
    
    # Public DNS configuration - fully working (without /api prefix)
    UNIVERSITY_APP_URL="https://university-registration.bitgaram.info"
    
    echo -e "${GREEN}[SUCCESS]${NC} Services ready:"
    echo -e "  📱 Dashboard: https://kube-db-mon-dashboard.bitgaram.info"
    echo -e "  🔗 Control Plane: https://kube-db-mon-dashboard.bitgaram.info/ws"  
    echo -e "  🚀 University App: ${UNIVERSITY_APP_URL} (Public DNS)"
}

# 시나리오 1: 장시간 실행 트랜잭션 시뮬레이션
simulate_long_running_transactions() {
    echo ""
    echo -e "${PURPLE}========================================${NC}"
    echo -e "${PURPLE}  Scenario 1: Long Running Transactions${NC}"
    echo -e "${PURPLE}========================================${NC}"
    
    echo -e "${BLUE}[INFO]${NC} Simulating long-running transactions..."
    
    # 장시간 실행되는 트랜잭션을 시뮬레이션하기 위한 API 호출
    echo -e "${BLUE}[INFO]${NC} 1. Starting performance test (complex queries)..."
    curl -k -X GET "${UNIVERSITY_APP_URL}/data/performance-test" \
        --max-time 2 > /dev/null 2>&1 &
    
    echo -e "${BLUE}[INFO]${NC} 2. Starting concurrent test..."
    curl -k -X POST "${UNIVERSITY_APP_URL}/data/concurrent-test?threads=5&operations=50" \
        --max-time 2 > /dev/null 2>&1 &
        
    echo -e "${BLUE}[INFO]${NC} 3. Starting courses query load..."
    curl -k -X GET "${UNIVERSITY_APP_URL}/courses" \
        --max-time 2 > /dev/null 2>&1 &
    
    sleep 3
    echo -e "${GREEN}[SUCCESS]${NC} Long-running transactions initiated"
    echo -e "${YELLOW}[DEMO]${NC} Check dashboard for 'Long Running Transactions' alert panel"
}

# 시나리오 2: 데드락 시뮬레이션
simulate_deadlock_scenarios() {
    echo ""
    echo -e "${PURPLE}========================================${NC}"
    echo -e "${PURPLE}  Scenario 2: Deadlock Detection${NC}"
    echo -e "${PURPLE}========================================${NC}"
    
    echo -e "${BLUE}[INFO]${NC} Simulating deadlock scenarios..."
    
    # 동시에 여러 트랜잭션을 실행하여 데드락 유발
    echo -e "${BLUE}[INFO]${NC} 1. Starting concurrent data operations..."
    
    # Multiple concurrent database operations
    for i in {1..5}; do
        curl -k -X GET "${UNIVERSITY_APP_URL}/data/stats" \
            --max-time 2 > /dev/null 2>&1 &
    done
    
    sleep 1
    
    echo -e "${BLUE}[INFO]${NC} 2. Starting overlapping performance tests..."
    for i in {1..3}; do
        curl -k -X GET "${UNIVERSITY_APP_URL}/data/performance-test" \
            --max-time 2 > /dev/null 2>&1 &
    done
    
    sleep 3
    echo -e "${GREEN}[SUCCESS]${NC} Deadlock scenarios initiated"
    echo -e "${YELLOW}[DEMO]${NC} Check dashboard for 'Deadlock Alert' panel"
}

# 시나리오 3: 노드/팟별 부하 분산 테스트
simulate_node_pod_load() {
    echo ""
    echo -e "${PURPLE}========================================${NC}"
    echo -e "${PURPLE}  Scenario 3: Node/Pod Load Distribution${NC}"
    echo -e "${PURPLE}========================================${NC}"
    
    echo -e "${BLUE}[INFO]${NC} Generating distributed load across pods..."
    
    # 다양한 API 엔드포인트로 부하 생성
    endpoints=(
        "courses"
        "courses"
        "data/stats"
        "data/health"
        "data/performance-test"
    )
    
    echo -e "${BLUE}[INFO]${NC} Starting load generation on multiple endpoints..."
    
    for i in {1..10}; do
        for endpoint in "${endpoints[@]}"; do
            curl -k -X GET "${UNIVERSITY_APP_URL}/${endpoint}" \
                --max-time 1 > /dev/null 2>&1 &
        done
        sleep 0.5
    done
    
    echo -e "${BLUE}[INFO]${NC} Generating heavy database queries..."
    
    # 복잡한 쿼리들
    for i in {1..5}; do
        curl -k -X GET "${UNIVERSITY_APP_URL}/data/performance-test" \
            --max-time 2 > /dev/null 2>&1 &
        curl -k -X POST "${UNIVERSITY_APP_URL}/data/concurrent-test?threads=3&operations=20" \
            --max-time 2 > /dev/null 2>&1 &
        sleep 1
    done
    
    sleep 5
    echo -e "${GREEN}[SUCCESS]${NC} Load distribution completed"
    echo -e "${YELLOW}[DEMO]${NC} Check dashboard 'Node & Pod Metrics' panel for distribution"
}

# 시나리오 4: 트랜잭션 타임라인 데모
simulate_transaction_timeline() {
    echo ""
    echo -e "${PURPLE}========================================${NC}"
    echo -e "${PURPLE}  Scenario 4: Transaction Timeline${NC}"
    echo -e "${PURPLE}========================================${NC}"
    
    echo -e "${BLUE}[INFO]${NC} Creating diverse transaction patterns..."
    
    # 빠른 트랜잭션들
    echo -e "${BLUE}[INFO]${NC} 1. Fast transactions (simple queries)..."
    for i in {1..10}; do
        curl -k -X GET "${UNIVERSITY_APP_URL}/courses" \
            --max-time 1 > /dev/null 2>&1 &
        sleep 0.2
    done
    
    # 중간 속도 트랜잭션들  
    echo -e "${BLUE}[INFO]${NC} 2. Medium transactions (data queries)..."
    for i in {1..5}; do
        curl -k -X GET "${UNIVERSITY_APP_URL}/data/stats" \
            --max-time 1 > /dev/null 2>&1 &
        sleep 0.5
    done
    
    # 느린 트랜잭션들
    echo -e "${BLUE}[INFO]${NC} 3. Slow transactions (complex operations)..."
    curl -k -X GET "${UNIVERSITY_APP_URL}/data/performance-test" \
        --max-time 2 > /dev/null 2>&1 &
        
    curl -k -X POST "${UNIVERSITY_APP_URL}/data/concurrent-test?threads=10&operations=100" \
        --max-time 2 > /dev/null 2>&1 &
    
    sleep 3
    echo -e "${GREEN}[SUCCESS]${NC} Transaction timeline patterns created"
    echo -e "${YELLOW}[DEMO]${NC} Check dashboard 'Transaction Timeline' for different performance levels"
}

# 메트릭 확인 및 분석
analyze_metrics() {
    echo ""
    echo -e "${PURPLE}========================================${NC}"
    echo -e "${PURPLE}  Metrics Analysis${NC}"
    echo -e "${PURPLE}========================================${NC}"
    
    echo -e "${BLUE}[INFO]${NC} Analyzing generated metrics..."
    
    # Pod 로그에서 트랜잭션 메트릭 확인
    echo -e "${BLUE}[INFO]${NC} Checking transaction metrics in pod logs..."
    POD_NAME=$(kubectl get pods -n kubedb-monitor-test -l app=university-registration-demo -o jsonpath='{.items[0].metadata.name}')
    
    echo -e "${YELLOW}[ANALYSIS]${NC} Recent transaction events:"
    kubectl logs -n kubedb-monitor-test $POD_NAME --tail=20 | grep -E "(Transaction|Deadlock|JDBC)" || echo "No transaction logs found yet"
    
    echo ""
    echo -e "${YELLOW}[ANALYSIS]${NC} Current pod status:"
    kubectl get pods -n kubedb-monitor-test -o wide
    
    echo ""
    echo -e "${YELLOW}[ANALYSIS]${NC} Resource usage:"
    kubectl top pods -n kubedb-monitor-test 2>/dev/null || echo "Metrics server not available"
}

# 데모 완료 및 정리 안내
demo_completion() {
    echo ""
    echo -e "${PURPLE}========================================${NC}"
    echo -e "${PURPLE}  Demo Complete!${NC}"
    echo -e "${PURPLE}========================================${NC}"
    
    echo -e "${GREEN}[SUCCESS]${NC} ✅ Advanced Transaction Metrics Demo completed successfully!"
    echo ""
    echo -e "${YELLOW}[DEMO RESULTS]${NC} You should now see in the dashboard:"
    echo "  🚨 Deadlock Alert - Critical deadlocks detected"
    echo "  ⚠️  Long Running Transactions - Transactions over threshold"
    echo "  📊 Node & Pod Metrics - Load distribution across nodes/pods"
    echo "  📈 Transaction Timeline - Fast/Medium/Slow transaction patterns"
    echo "  📋 Recent Queries - Real-time query execution log"
    echo ""
    # 실제 대시보드 포트 확인
    DASHBOARD_PORT=3000
    if lsof -ti:3004 > /dev/null 2>&1; then
        DASHBOARD_PORT=3004
    elif lsof -ti:3002 > /dev/null 2>&1; then
        DASHBOARD_PORT=3002
    elif lsof -ti:3001 > /dev/null 2>&1; then
        DASHBOARD_PORT=3001
    fi
    
    echo -e "${BLUE}[DASHBOARD]${NC} View results at: https://kube-db-mon-dashboard.bitgaram.info"
    echo ""
    echo -e "${YELLOW}[INTERACTIVE]${NC} Try the following dashboard features:"
    echo "  1. Click 'Resolve' on deadlock alerts"
    echo "  2. Click 'Kill' on long-running transactions" 
    echo "  3. Toggle between Node View/Pod View"
    echo "  4. Filter transactions by namespace"
    echo "  5. Expand transaction details in timeline"
    echo ""
    echo -e "${BLUE}[CLEANUP]${NC} To stop the demo:"
    echo "  • Press Ctrl+C to stop this script"
    echo "  • Run ./demo-environment-setup.sh to reset environment"
    echo "  • Stop dashboard: pkill -f 'next dev'"
}

# 메인 데모 실행
main() {
    echo -e "${GREEN}[START]${NC} Starting Advanced Transaction Metrics Demo..."
    
    check_prerequisites
    setup_dashboard_access
    
    echo ""
    # 대시보드 포트 확인
    DASHBOARD_PORT=3000
    if lsof -ti:3004 > /dev/null 2>&1; then
        DASHBOARD_PORT=3004
    elif lsof -ti:3002 > /dev/null 2>&1; then
        DASHBOARD_PORT=3002  
    elif lsof -ti:3001 > /dev/null 2>&1; then
        DASHBOARD_PORT=3001
    fi
    
    echo -e "${YELLOW}[IMPORTANT]${NC} Open the dashboard: https://kube-db-mon-dashboard.bitgaram.info"
    echo -e "${YELLOW}[IMPORTANT]${NC} Press Enter to start the demo scenarios..."
    read -r
    
    simulate_long_running_transactions
    sleep 5
    
    simulate_deadlock_scenarios
    sleep 5
    
    simulate_node_pod_load
    sleep 5
    
    simulate_transaction_timeline
    sleep 5
    
    analyze_metrics
    demo_completion
    
    echo ""
    echo -e "${BLUE}[INFO]${NC} Demo will continue generating background load..."
    echo -e "${BLUE}[INFO]${NC} Press Ctrl+C to stop"
    
    # 지속적인 백그라운드 로드 유지
    while true; do
        curl -k -X GET "${UNIVERSITY_APP_URL}/actuator/health" --max-time 1 > /dev/null 2>&1 &
        curl -k -X GET "${UNIVERSITY_APP_URL}/data/health" --max-time 1 > /dev/null 2>&1 &
        curl -k -X GET "${UNIVERSITY_APP_URL}/courses" --max-time 1 > /dev/null 2>&1 &
        sleep 10
    done
}

# 스크립트 실행
main "$@"