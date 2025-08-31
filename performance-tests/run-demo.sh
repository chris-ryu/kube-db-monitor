#!/bin/bash

# University Registration Performance Test Demo Runner
# KubeDB Monitor 데모용 자동화 스크립트

set -e

# 색상 정의
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# 로고 출력
print_logo() {
    echo -e "${BLUE}"
    echo "╔══════════════════════════════════════════════════════════════╗"
    echo "║              🎓 University Registration Load Test             ║"
    echo "║                     KubeDB Monitor Demo                      ║"
    echo "╚══════════════════════════════════════════════════════════════╝"
    echo -e "${NC}"
}

# 단계별 메시지 출력
print_step() {
    echo -e "${GREEN}[STEP] $1${NC}"
}

print_info() {
    echo -e "${BLUE}[INFO] $1${NC}"
}

print_warning() {
    echo -e "${YELLOW}[WARN] $1${NC}"
}

print_error() {
    echo -e "${RED}[ERROR] $1${NC}"
}

# 환경 확인
check_environment() {
    print_step "환경 확인 중..."
    
    # JMeter 설치 확인
    if ! command -v jmeter &> /dev/null; then
        print_error "JMeter가 설치되어 있지 않습니다."
        print_info "설치 방법: brew install jmeter (macOS) 또는 https://jmeter.apache.org/download_jmeter.cgi"
        exit 1
    fi
    
    # 필요한 디렉토리 생성
    mkdir -p results logs
    
    # 테스트 대상 서비스 상태 확인
    print_info "수강신청 시스템 연결 확인 중..."
    if curl -s --max-time 5 https://university-registration.bitgaram.info/api/courses > /dev/null; then
        print_info "✅ 수강신청 시스템 연결 성공"
    else
        print_error "❌ 수강신청 시스템에 연결할 수 없습니다."
        exit 1
    fi
    
    # KubeDB Monitor 대시보드 확인
    print_info "KubeDB Monitor 대시보드 확인 중..."
    if curl -s --max-time 5 https://kube-db-mon-dashboard.bitgaram.info > /dev/null; then
        print_info "✅ KubeDB Monitor 대시보드 연결 성공"
    else
        print_warning "⚠️  KubeDB Monitor 대시보드에 연결할 수 없습니다."
    fi
    
    echo ""
}

# 데모 시나리오 실행
run_scenario() {
    local scenario=$1
    local users=$2
    local duration=$3
    local rampup=$4
    local description=$5
    
    print_step "시나리오 실행: $scenario - $description"
    print_info "동시 사용자: ${users}명, 지속시간: ${duration}초, 램프업: ${rampup}초"
    
    # 결과 파일명 생성
    local timestamp=$(date '+%Y%m%d_%H%M%S')
    local result_file="results/${scenario}_${timestamp}.jtl"
    local log_file="logs/${scenario}_${timestamp}.log"
    
    # JMeter 실행
    jmeter -n -t university-load-test.jmx \
        -Jusers=$users \
        -Jduration=$duration \
        -Jrampup=$rampup \
        -Jscenario=$scenario \
        -JbaseURL=https://university-registration.bitgaram.info \
        -l "$result_file" \
        -j "$log_file" \
        -e -o "results/${scenario}_${timestamp}_report"
    
    if [ $? -eq 0 ]; then
        print_info "✅ $scenario 시나리오 완료 - 결과: $result_file"
        
        # 간단한 결과 요약 출력
        if [ -f "$result_file" ]; then
            local total_requests=$(tail -n +2 "$result_file" | wc -l)
            local error_count=$(tail -n +2 "$result_file" | awk -F',' '$8=="false" {count++} END {print count+0}')
            local success_rate=$(echo "scale=2; ($total_requests - $error_count) * 100 / $total_requests" | bc -l 2>/dev/null || echo "계산불가")
            
            echo "  📊 총 요청: $total_requests"
            echo "  ❌ 오류: $error_count"  
            echo "  ✅ 성공률: ${success_rate}%"
        fi
    else
        print_error "❌ $scenario 시나리오 실행 실패"
        return 1
    fi
    
    echo ""
}

# 데모 대시보드 안내
show_monitoring_info() {
    print_step "실시간 모니터링 대시보드"
    echo ""
    echo "🖥️  다음 URL에서 실시간 성능을 확인하세요:"
    echo ""
    echo "   📊 KubeDB Monitor Dashboard:"
    echo "      https://kube-db-mon-dashboard.bitgaram.info"
    echo ""
    echo "   🎯 주요 관찰 포인트:"
    echo "      - DB Connection Pool 사용률"
    echo "      - Query Response Time"
    echo "      - Transaction 경쟁 상황"
    echo "      - Deadlock 감지"
    echo "      - Slow Query 탐지"
    echo ""
}

# 데모 실행 대기
wait_for_demo_start() {
    print_step "데모 시작 준비"
    echo ""
    print_info "🎬 데모를 시작하기 전에 대시보드를 열어두세요!"
    echo ""
    
    while true; do
        read -p "대시보드 준비가 완료되었나요? (y/N): " yn
        case $yn in
            [Yy]* ) break;;
            [Nn]* ) print_info "대시보드를 먼저 열어주세요."; continue;;
            * ) print_warning "y 또는 n을 입력하세요.";;
        esac
    done
    
    echo ""
}

# 결과 보고서 생성
generate_report() {
    print_step "결과 보고서 생성 중..."
    
    local report_file="results/demo_summary_$(date '+%Y%m%d_%H%M%S').txt"
    
    cat > "$report_file" << EOF
🎓 University Registration Performance Test Summary
================================================

📅 테스트 일시: $(date '+%Y-%m-%d %H:%M:%S')
🎯 테스트 목적: KubeDB Monitor 실시간 모니터링 데모

📊 실행된 시나리오:
1. Warmup (50 users, 2분) - 시스템 안정성 확인
2. Peak (200 users, 5분) - 수강신청 러시 상황  
3. Stress (500 users, 3분) - 극한 부하 테스트

📁 결과 파일:
$(ls -la results/ | grep -E '\.(jtl|html)$' | tail -10)

🔗 관련 링크:
- KubeDB Monitor: https://kube-db-mon-dashboard.bitgaram.info
- 테스트 대상: https://university-registration.bitgaram.info
- GitHub: https://github.com/kubedb-monitor/kubedb-monitor

💡 데모 포인트:
✅ 실시간 DB 모니터링 및 알림
✅ 쿼리 성능 분석 및 최적화 제안
✅ 동시성 제어 및 데드락 감지
✅ 자동화된 성능 임계값 관리

EOF

    print_info "✅ 보고서 생성 완료: $report_file"
}

# 메인 실행 함수
main() {
    print_logo
    
    # 환경 확인
    check_environment
    
    # 모니터링 정보 표시
    show_monitoring_info
    
    # 데모 시작 대기
    wait_for_demo_start
    
    # Phase 1: Warm-up
    print_step "🔥 Phase 1: Warm-up (평상시)"
    print_info "목적: 시스템 정상 동작 확인"
    run_scenario "warmup" 50 120 30 "평상시 - 시스템 안정성 확인"
    
    # 잠시 대기
    print_info "다음 단계 준비 중..."
    sleep 10
    
    # Phase 2: Peak Time  
    print_step "⚡ Phase 2: Peak Time (수강신청 러시)"
    print_info "목적: 실제 수강신청 상황 재현"
    run_scenario "peak" 200 300 60 "수강신청 러시 - 실제 상황 시뮬레이션"
    
    # 잠시 대기
    print_info "다음 단계 준비 중..."
    sleep 10
    
    # Phase 3: Stress Test
    print_step "🚨 Phase 3: Stress Test (극한 부하)"
    print_info "목적: 시스템 한계점 및 장애 상황 확인"
    run_scenario "stress" 500 180 30 "극한 부하 - 시스템 한계 테스트"
    
    # 결과 보고서 생성
    generate_report
    
    # 완료 메시지
    print_step "🎉 데모 완료!"
    echo ""
    print_info "모든 시나리오가 성공적으로 완료되었습니다."
    print_info "결과는 results/ 디렉토리에서 확인할 수 있습니다."
    echo ""
    print_info "📊 KubeDB Monitor 대시보드에서 실시간 성능 분석 결과를 확인해보세요!"
    echo ""
}

# 스크립트 실행
if [[ "${BASH_SOURCE[0]}" == "${0}" ]]; then
    # 인자에 따른 개별 시나리오 실행
    case ${1:-"full"} in
        "warmup")
            check_environment
            run_scenario "warmup" 50 120 30 "평상시 - 시스템 안정성 확인"
            ;;
        "peak")
            check_environment  
            run_scenario "peak" 200 300 60 "수강신청 러시 - 실제 상황 시뮬레이션"
            ;;
        "stress")
            check_environment
            run_scenario "stress" 500 180 30 "극한 부하 - 시스템 한계 테스트"
            ;;
        "full"|*)
            main
            ;;
    esac
fi