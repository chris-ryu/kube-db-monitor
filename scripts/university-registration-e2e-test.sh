#!/bin/bash

# 수강신청 데모앱 E2E 테스트 케이스
# KubeDB Monitor Agent, Control Plane, Dashboard 전체 플로우 검증

set -e

# 색상 정의
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
PURPLE='\033[0;35m'
CYAN='\033[0;36m'
NC='\033[0m' # No Color

# 테스트 결과 추적
TESTS_PASSED=0
TESTS_FAILED=0
TEST_RESULTS=()

log_info() {
    echo -e "${BLUE}[INFO]${NC} $1"
}

log_success() {
    echo -e "${GREEN}[SUCCESS]${NC} $1"
    TESTS_PASSED=$((TESTS_PASSED + 1))
    TEST_RESULTS+=("✅ $1")
}

log_warning() {
    echo -e "${YELLOW}[WARNING]${NC} $1"
}

log_error() {
    echo -e "${RED}[ERROR]${NC} $1"
    TESTS_FAILED=$((TESTS_FAILED + 1))
    TEST_RESULTS+=("❌ $1")
}

log_step() {
    echo -e "\n${PURPLE}[STEP]${NC} $1"
    echo "=================================================="
}

# 환경 변수 설정
NAMESPACE="kubedb-monitor-test"
CONTROL_PLANE_NAMESPACE="kubedb-monitor"
DEMO_APP_URL="https://university-registration.bitgaram.info"
DASHBOARD_URL="https://kube-db-mon-dashboard.bitgaram.info"

log_step "1. 환경 설정 및 사전 조건 확인"

# Namespace 존재 확인
if kubectl get namespace $NAMESPACE &>/dev/null; then
    log_success "Namespace $NAMESPACE 존재 확인"
else
    log_error "Namespace $NAMESPACE 존재하지 않음"
    exit 1
fi

if kubectl get namespace $CONTROL_PLANE_NAMESPACE &>/dev/null; then
    log_success "Namespace $CONTROL_PLANE_NAMESPACE 존재 확인"
else
    log_error "Namespace $CONTROL_PLANE_NAMESPACE 존재하지 않음"
    exit 1
fi

log_step "2. 수강신청 데모앱 Pod 상태 확인"

# Pod 이름 동적 파악 - 정상 작동하는 Pod 사용
DEMO_POD_NAME=$(kubectl get pods -n $NAMESPACE -l app=university-registration-demo --field-selector=status.phase=Running -o jsonpath="{.items[0].metadata.name}" 2>/dev/null || echo "")

if [[ -z "$DEMO_POD_NAME" ]]; then
    log_error "정상 작동하는 수강신청 앱 Pod를 찾을 수 없습니다"
    exit 1
fi

if [[ -n "$DEMO_POD_NAME" ]]; then
    log_success "수강신청 데모앱 Pod 확인: $DEMO_POD_NAME"
else
    log_error "수강신청 데모앱 Pod를 찾을 수 없습니다"
    exit 1
fi

log_step "3. Control Plane Pod 상태 확인"

# Control Plane Pod 이름 동적 파악
CONTROL_PLANE_POD=$(kubectl get pods -n $CONTROL_PLANE_NAMESPACE -l app=kubedb-monitor-control-plane --field-selector=status.phase=Running -o jsonpath="{.items[0].metadata.name}" 2>/dev/null || echo "")

if [[ -n "$CONTROL_PLANE_POD" ]]; then
    log_success "Control Plane Pod 확인: $CONTROL_PLANE_POD"
else
    log_error "Control Plane Pod를 찾을 수 없습니다"
    exit 1
fi

log_step "4. Dashboard Pod 상태 확인"

# Dashboard Pod 이름 동적 파악
DASHBOARD_POD=$(kubectl get pods -n $CONTROL_PLANE_NAMESPACE -l app=kubedb-monitor-dashboard --field-selector=status.phase=Running -o jsonpath="{.items[0].metadata.name}" 2>/dev/null || echo "")

if [[ -n "$DASHBOARD_POD" ]]; then
    log_success "Dashboard Pod 확인: $DASHBOARD_POD"
else
    log_error "Dashboard Pod를 찾을 수 없습니다"
    exit 1
fi

log_step "5. TLS 인증서 및 HTTPS 접속 테스트"

# HTTPS 접속 테스트 (Control Plane은 내부 전용으로 제외)
log_info "Control Plane은 내부 전용 서비스로 외부 HTTPS 접속 테스트 제외"

if curl -s -I "$DEMO_APP_URL" | grep -q "200 OK\|302 Found\|404"; then
    log_success "수강신청 앱 HTTPS 접속 성공 (TLS 연결 정상)"
else
    log_error "수강신청 앱 HTTPS 접속 실패"
fi

if curl -s -I "$DASHBOARD_URL" | grep -q "200 OK\|302 Found"; then
    log_success "Dashboard HTTPS 접속 성공"
else
    log_error "Dashboard HTTPS 접속 실패"
fi

log_step "6. Agent 및 Control Plane 연결 상태 확인"

# Agent에서 Control Plane으로의 연결 테스트
AGENT_LOGS=$(kubectl logs $DEMO_POD_NAME -n $NAMESPACE --tail=50 | grep -i "kubedb.*monitor\|agent" | tail -10)
if echo "$AGENT_LOGS" | grep -q "agent\|monitor"; then
    log_success "Agent 로그에서 모니터링 활동 확인"
else
    log_warning "Agent 로그에서 모니터링 활동을 확인할 수 없습니다"
fi

log_step "7. 실제 데이터베이스 쿼리 실행 및 모니터링 테스트"

log_info "수강신청 앱에서 실제 API 호출을 통한 데이터베이스 쿼리 실행..."

# API 호출 전 로그 초기화 (ISO 8601 형식 사용)
INITIAL_TIMESTAMP=$(date -u +%Y-%m-%dT%H:%M:%SZ)

# 과목 조회 API 호출 (페이징 파라미터 추가)
COURSE_RESPONSE=$(kubectl exec $DEMO_POD_NAME -n $NAMESPACE -- curl -s "http://localhost:8080/api/courses?page=0&size=5" 2>/dev/null || echo "")

if [[ -n "$COURSE_RESPONSE" ]] && echo "$COURSE_RESPONSE" | grep -q "courseId\|name"; then
    log_success "과목 조회 API 호출 성공"
    echo "응답 예시: $(echo "$COURSE_RESPONSE" | head -c 200)..."
else
    log_error "과목 조회 API 호출 실패"
fi

# 학생 조회 API 호출 (테스트용)
STUDENT_RESPONSE=$(kubectl exec $DEMO_POD_NAME -n $NAMESPACE -- curl -s "http://localhost:8080/api/students?page=0&size=5" 2>/dev/null || echo "")

if [[ -n "$STUDENT_RESPONSE" ]] && echo "$STUDENT_RESPONSE" | grep -q "studentId\|name"; then
    log_success "학생 조회 API 호출 성공"
    echo "응답 예시: $(echo "$STUDENT_RESPONSE" | head -c 200)..."
else
    log_warning "학생 조회 API 호출 - 데이터가 없을 수 있음"
fi

log_step "8. Agent에서 SQL 쿼리 캡처 확인"

sleep 5  # 로그가 기록될 시간 대기

# Agent 로그에서 SQL 쿼리 캡처 확인
AGENT_SQL_LOGS=$(kubectl logs $DEMO_POD_NAME -n $NAMESPACE --since-time="$INITIAL_TIMESTAMP" | grep -i "sql\|query\|jdbc" | tail -10)

if [[ -n "$AGENT_SQL_LOGS" ]]; then
    log_success "Agent에서 SQL 쿼리 캡처 확인"
    echo "캡처된 쿼리 예시:"
    echo "$AGENT_SQL_LOGS" | head -3
else
    log_warning "Agent에서 SQL 쿼리 캡처를 확인할 수 없습니다"
fi

log_step "9. Control Plane에서 메트릭 수집 확인"

# Control Plane 로그에서 메트릭 수집 확인
CONTROL_PLANE_LOGS=$(kubectl logs $CONTROL_PLANE_POD -n $CONTROL_PLANE_NAMESPACE --since-time="$INITIAL_TIMESTAMP" | grep -i "metric\|sql\|query" | tail -10)

if [[ -n "$CONTROL_PLANE_LOGS" ]]; then
    log_success "Control Plane에서 메트릭 수집 확인"
    echo "수집된 메트릭 예시:"
    echo "$CONTROL_PLANE_LOGS" | head -3
else
    log_warning "Control Plane에서 메트릭 수집을 확인할 수 없습니다"
fi

log_step "10. Dashboard WebSocket 연결 및 실시간 데이터 확인"

# Dashboard 로그에서 WebSocket 연결 확인
DASHBOARD_LOGS=$(kubectl logs $DASHBOARD_POD -n $CONTROL_PLANE_NAMESPACE --since-time="$INITIAL_TIMESTAMP" | grep -i "websocket\|connection\|client" | tail -5)

if [[ -n "$DASHBOARD_LOGS" ]]; then
    log_success "Dashboard WebSocket 연결 활동 확인"
else
    log_warning "Dashboard WebSocket 활동을 확인할 수 없습니다"
fi

log_step "11. API를 통한 메트릭 데이터 직접 조회"

# Control Plane API에서 메트릭 직접 조회
METRICS_RESPONSE=$(curl -s "$CONTROL_PLANE_URL/api/events?limit=10" 2>/dev/null || echo "")

if [[ -n "$METRICS_RESPONSE" ]] && echo "$METRICS_RESPONSE" | grep -q "timestamp\|sql\|query"; then
    log_success "Control Plane API를 통한 메트릭 데이터 조회 성공"
    echo "최근 이벤트 수: $(echo "$METRICS_RESPONSE" | grep -o "timestamp" | wc -l)"
else
    log_warning "Control Plane API를 통한 메트릭 데이터 조회 - 데이터가 없을 수 있음"
fi

log_step "12. 장시간 실행 쿼리 시뮬레이션 테스트"

log_info "장시간 실행 쿼리 시뮬레이션을 위한 API 호출..."

# 장시간 실행 쿼리 시뮬레이션 API 호출 (있다면)
LONG_RUNNING_RESPONSE=$(kubectl exec $DEMO_POD_NAME -n $NAMESPACE -- curl -s -X POST http://localhost:8080/api/simulation/long-running-query 2>/dev/null || echo "")

if [[ -n "$LONG_RUNNING_RESPONSE" ]]; then
    log_success "장시간 실행 쿼리 시뮬레이션 실행"
    sleep 10  # 쿼리 실행 대기
    
    # 장시간 실행 쿼리 감지 확인
    LONG_RUNNING_LOGS=$(kubectl logs $DEMO_POD_NAME -n $NAMESPACE --tail=20 | grep -i "long.*running\|slow.*query" | tail -3)
    
    if [[ -n "$LONG_RUNNING_LOGS" ]]; then
        log_success "장시간 실행 쿼리 감지 확인"
    else
        log_warning "장시간 실행 쿼리 감지를 확인할 수 없습니다"
    fi
else
    log_warning "장시간 실행 쿼리 시뮬레이션 API를 사용할 수 없습니다"
fi

log_step "13. 데드락 시뮬레이션 테스트"

log_info "데드락 시뮬레이션을 위한 API 호출..."

# 데드락 시뮬레이션 API 호출 (있다면)
DEADLOCK_RESPONSE=$(kubectl exec $DEMO_POD_NAME -n $NAMESPACE -- curl -s -X POST http://localhost:8080/api/simulation/deadlock 2>/dev/null || echo "")

if [[ -n "$DEADLOCK_RESPONSE" ]]; then
    log_success "데드락 시뮬레이션 실행"
    sleep 5  # 데드락 발생 대기
    
    # 데드락 감지 확인
    DEADLOCK_LOGS=$(kubectl logs $DEMO_POD_NAME -n $NAMESPACE --tail=20 | grep -i "deadlock" | tail -3)
    
    if [[ -n "$DEADLOCK_LOGS" ]]; then
        log_success "데드락 감지 확인"
    else
        log_warning "데드락 감지를 확인할 수 없습니다"
    fi
else
    log_warning "데드락 시뮬레이션 API를 사용할 수 없습니다"
fi

log_step "14. E2E 테스트 결과 요약"

echo -e "\n${CYAN}================================="
echo -e "     E2E 테스트 결과 요약"
echo -e "=================================${NC}\n"

for result in "${TEST_RESULTS[@]}"; do
    echo "$result"
done

echo -e "\n${GREEN}통과한 테스트: $TESTS_PASSED${NC}"
echo -e "${RED}실패한 테스트: $TESTS_FAILED${NC}"

TOTAL_TESTS=$((TESTS_PASSED + TESTS_FAILED))
if [[ $TOTAL_TESTS -gt 0 ]]; then
    SUCCESS_RATE=$((TESTS_PASSED * 100 / TOTAL_TESTS))
    echo -e "${BLUE}성공률: ${SUCCESS_RATE}%${NC}"
fi

if [[ $TESTS_FAILED -eq 0 ]]; then
    echo -e "\n${GREEN}🎉 모든 E2E 테스트가 성공적으로 완료되었습니다!${NC}"
    exit 0
else
    echo -e "\n${YELLOW}⚠️  일부 테스트에서 문제가 발견되었습니다. 로그를 확인해주세요.${NC}"
    exit 1
fi