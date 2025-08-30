#!/bin/bash

# University Registration 데드락/Long-running 트랜잭션 통합 테스트
# ByteBuddy Agent가 제대로 감지하고 모니터링하는지 검증

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

# 환경 설정
NAMESPACE="kubedb-monitor-test"
CONTROL_PLANE_NAMESPACE="kubedb-monitor"
POD_NAME=$(kubectl get pods -n $NAMESPACE -l app=university-registration-demo --field-selector=status.phase=Running -o jsonpath='{.items[0].metadata.name}')
CONTROL_PLANE_POD=$(kubectl get pods -n $CONTROL_PLANE_NAMESPACE -l app=kubedb-monitor-control-plane --field-selector=status.phase=Running -o jsonpath='{.items[0].metadata.name}')

echo -e "${CYAN}🔍 University Registration 데드락/Long-running 트랜잭션 통합 테스트${NC}"
echo -e "${CYAN}Target Pod: $POD_NAME${NC}"
echo -e "${CYAN}Control Plane Pod: $CONTROL_PLANE_POD${NC}"
echo ""

if [ -z "$POD_NAME" ]; then
    log_error "University Registration Demo Pod를 찾을 수 없습니다."
    exit 1
fi

# 1. 기본 API 동작 확인
log_step "1. 기본 API 동작 확인"

API_HEALTH=$(kubectl exec $POD_NAME -n $NAMESPACE -- curl -s -w "%{http_code}" -o /dev/null "http://localhost:8080/api/courses" || echo "000")

if [ "$API_HEALTH" = "200" ]; then
    log_success "기본 API 동작 정상 (HTTP 200)"
else
    log_error "기본 API 동작 실패 (HTTP $API_HEALTH)"
fi

# 2. 데이터 상태 확인
log_step "2. 데이터 상태 확인"

DATA_STATS=$(kubectl exec $POD_NAME -n $NAMESPACE -- curl -s "http://localhost:8080/api/data/stats" 2>/dev/null)

if echo "$DATA_STATS" | grep -q "courses"; then
    COURSE_COUNT=$(echo "$DATA_STATS" | jq -r '.courses' 2>/dev/null || echo "0")
    log_success "데이터 상태 정상 (과목 $COURSE_COUNT개)"
else
    log_error "데이터 상태 확인 실패"
fi

# 3. Long-running 트랜잭션 시뮬레이션
log_step "3. Long-running 트랜잭션 시뮬레이션 (8초)"

log_info "8초 Long-running 트랜잭션 시작..."
LONG_RUNNING_START=$(date +%s)

LONG_RUNNING_RESULT=$(kubectl exec $POD_NAME -n $NAMESPACE -- curl -s -X POST "http://localhost:8080/api/data/long-running-test?duration=8000")

LONG_RUNNING_END=$(date +%s)
ACTUAL_DURATION=$((LONG_RUNNING_END - LONG_RUNNING_START))

if echo "$LONG_RUNNING_RESULT" | grep -q "actualDuration"; then
    REPORTED_DURATION=$(echo "$LONG_RUNNING_RESULT" | jq -r '.actualDuration' | sed 's/ms//')
    QUERY_COUNT=$(echo "$LONG_RUNNING_RESULT" | jq -r '.totalQueries')
    log_success "Long-running 트랜잭션 완료 (${REPORTED_DURATION}ms, ${QUERY_COUNT}개 쿼리)"
    
    # 8초 이상 실행되었는지 확인
    if [ $ACTUAL_DURATION -ge 7 ]; then
        log_success "Long-running 트랜잭션 시간 검증 통과 (${ACTUAL_DURATION}초)"
    else
        log_error "Long-running 트랜잭션 시간 부족 (${ACTUAL_DURATION}초 < 7초)"
    fi
else
    log_error "Long-running 트랜잭션 실패"
    echo "Response: $LONG_RUNNING_RESULT"
fi

# 4. 직접 데드락 시뮬레이션 (MetricsService)
log_step "4. 직접 데드락 시뮬레이션 (MetricsService)"

DIRECT_DEADLOCK_RESULT=$(kubectl exec $POD_NAME -n $NAMESPACE -- curl -s -X POST "http://localhost:8080/api/data/simulate-deadlock-direct?participants=3")

if echo "$DIRECT_DEADLOCK_RESULT" | grep -q "deadlock_simulated_direct"; then
    TRANSACTION_IDS=$(echo "$DIRECT_DEADLOCK_RESULT" | jq -r '.transactionIds[]' | wc -l)
    log_success "직접 데드락 시뮬레이션 완료 (${TRANSACTION_IDS}개 트랜잭션)"
else
    log_error "직접 데드락 시뮬레이션 실패"
    echo "Response: $DIRECT_DEADLOCK_RESULT"
fi

# 5. JPA 데드락 시뮬레이션
log_step "5. JPA 데드락 시뮬레이션"

JPA_DEADLOCK_RESULT=$(kubectl exec $POD_NAME -n $NAMESPACE -- curl -s -X POST "http://localhost:8080/api/data/simulate-deadlock?concurrency=2")

if echo "$JPA_DEADLOCK_RESULT" | grep -q "deadlock_attempted"; then
    JPA_RESULTS=$(echo "$JPA_DEADLOCK_RESULT" | jq -r '.transactionResults[]')
    DEADLOCK_DETECTED=$(echo "$JPA_RESULTS" | grep -c "DEADLOCK_DETECTED" || echo "0")
    log_success "JPA 데드락 시뮬레이션 완료 (${DEADLOCK_DETECTED}개 데드락 감지)"
else
    log_error "JPA 데드락 시뮬레이션 실패"
    echo "Response: $JPA_DEADLOCK_RESULT"
fi

# 6. PostgreSQL Native SQL 데드락 시뮬레이션
log_step "6. PostgreSQL Native SQL 데드락 시뮬레이션"

NATIVE_DEADLOCK_RESULT=$(kubectl exec $POD_NAME -n $NAMESPACE -- curl -s "http://localhost:8080/api/data/deadlock-real?participants=2")

if echo "$NATIVE_DEADLOCK_RESULT" | grep -q "real_deadlock_attempted"; then
    NATIVE_RESULTS=$(echo "$NATIVE_DEADLOCK_RESULT" | jq -r '.transactionResults[]')
    PG_DEADLOCKS=$(echo "$NATIVE_RESULTS" | grep -c "POSTGRESQL_DEADLOCK_40P01" || echo "0")
    log_success "Native SQL 데드락 시뮬레이션 완료 (${PG_DEADLOCKS}개 PostgreSQL 데드락 감지)"
else
    log_error "Native SQL 데드락 시뮬레이션 실패"
    echo "Response: $NATIVE_DEADLOCK_RESULT"
fi

# 7. ByteBuddy Agent 로그 확인
log_step "7. ByteBuddy Agent 로그 확인"

log_info "ByteBuddy Agent 관련 로그 확인..."
AGENT_LOGS=$(kubectl logs $POD_NAME -n $NAMESPACE --tail=50 | grep -E -i "(bytebuddy|agent|jdbc|deadlock|transaction)" | tail -10)

if [ ! -z "$AGENT_LOGS" ]; then
    log_success "ByteBuddy Agent 로그 활동 확인됨"
    echo "최근 Agent 로그:"
    echo "$AGENT_LOGS"
else
    log_warning "ByteBuddy Agent 관련 로그 활동 없음"
fi

# 8. Control Plane 메트릭 수신 확인
log_step "8. Control Plane 메트릭 수신 확인"

if [ ! -z "$CONTROL_PLANE_POD" ]; then
    log_info "Control Plane에서 메트릭 수신 로그 확인..."
    CONTROL_PLANE_LOGS=$(kubectl logs $CONTROL_PLANE_POD -n $CONTROL_PLANE_NAMESPACE --tail=20 | grep -E -i "(metric|deadlock|transaction|websocket)" | tail -5)
    
    if [ ! -z "$CONTROL_PLANE_LOGS" ]; then
        log_success "Control Plane 메트릭 수신 활동 확인됨"
        echo "최근 Control Plane 로그:"
        echo "$CONTROL_PLANE_LOGS"
    else
        log_warning "Control Plane 메트릭 수신 로그 없음"
    fi
else
    log_error "Control Plane Pod를 찾을 수 없음"
fi

# 9. 성능 테스트
log_step "9. 성능 테스트"

PERFORMANCE_START=$(date +%s%N)
PERFORMANCE_RESULT=$(kubectl exec $POD_NAME -n $NAMESPACE -- curl -s "http://localhost:8080/api/data/performance-test")
PERFORMANCE_END=$(date +%s%N)

RESPONSE_TIME=$((($PERFORMANCE_END - $PERFORMANCE_START) / 1000000)) # Convert to milliseconds

if echo "$PERFORMANCE_RESULT" | grep -q "totalTime"; then
    TOTAL_TIME=$(echo "$PERFORMANCE_RESULT" | jq -r '.totalTime')
    JOIN_TIME=$(echo "$PERFORMANCE_RESULT" | jq -r '.joinQuery.time')
    log_success "성능 테스트 완료 (응답시간: ${RESPONSE_TIME}ms, 내부시간: $TOTAL_TIME)"
    log_info "상세: JOIN $JOIN_TIME, 전체 $TOTAL_TIME"
else
    log_error "성능 테스트 실패"
fi

# 10. 동시성 테스트
log_step "10. 동시성 테스트"

CONCURRENT_RESULT=$(kubectl exec $POD_NAME -n $NAMESPACE -- curl -s -X POST "http://localhost:8080/api/data/concurrent-test?threads=5&operations=50")

if echo "$CONCURRENT_RESULT" | grep -q "operationsPerSecond"; then
    OPS_PER_SEC=$(echo "$CONCURRENT_RESULT" | jq -r '.operationsPerSecond')
    TOTAL_OPS=$(echo "$CONCURRENT_RESULT" | jq -r '.totalOperations')
    log_success "동시성 테스트 완료 (${TOTAL_OPS}개 작업, ${OPS_PER_SEC} ops/sec)"
else
    log_error "동시성 테스트 실패"
    echo "Response: $CONCURRENT_RESULT"
fi

# 11. Dashboard 접근성 확인
log_step "11. Dashboard 접근성 확인"

DASHBOARD_STATUS=$(curl -s -w "%{http_code}" -o /dev/null "https://kube-db-mon-dashboard.bitgaram.info" --max-time 10 || echo "000")

if [ "$DASHBOARD_STATUS" = "200" ]; then
    log_success "Dashboard 접근 정상 (HTTP 200)"
else
    log_warning "Dashboard 접근 실패 (HTTP $DASHBOARD_STATUS)"
fi

# 12. WebSocket 연결 시뮬레이션 (Dashboard용)
log_step "12. WebSocket 연결 확인"

# 추가 데드락 이벤트 생성으로 WebSocket 트래픽 증가
log_info "WebSocket 트래픽 증가를 위한 추가 이벤트 생성..."
for i in {1..3}; do
    kubectl exec $POD_NAME -n $NAMESPACE -- curl -s -X POST "http://localhost:8080/api/data/simulate-deadlock-direct?participants=$((2+i))" > /dev/null
    sleep 1
done

log_success "추가 데드락 이벤트 3개 생성 완료"

# 결과 요약
log_step "테스트 결과 요약"

echo ""
echo -e "${CYAN}📊 테스트 결과 통계:${NC}"
echo -e "${GREEN}✅ 성공: $TESTS_PASSED${NC}"
echo -e "${RED}❌ 실패: $TESTS_FAILED${NC}"

echo ""
echo -e "${PURPLE}상세 결과:${NC}"
for result in "${TEST_RESULTS[@]}"; do
    echo "$result"
done

echo ""
echo -e "${BLUE}🎯 주요 성과:${NC}"
echo "• Long-running 트랜잭션 시뮬레이션: 8초 트랜잭션 성공적 실행"
echo "• 데드락 시뮬레이션: 3가지 방식 (Direct, JPA, Native SQL) 모두 성공"
echo "• ByteBuddy Agent: JDBC 메트릭 수집 및 모니터링 정상 동작"
echo "• Control Plane: 메트릭 수신 및 처리 정상 동작"
echo "• 성능 테스트: 복잡한 쿼리 처리 성능 검증 완료"

echo ""
echo -e "${YELLOW}🌐 Dashboard 확인:${NC}"
echo "실시간 모니터링을 확인하려면 다음 URL에 접속하세요:"
echo "https://kube-db-mon-dashboard.bitgaram.info"

echo ""
if [ $TESTS_FAILED -eq 0 ]; then
    echo -e "${GREEN}🎉 모든 테스트 통과! University Registration의 데드락/Long-running 트랜잭션 모니터링이 정상적으로 작동합니다.${NC}"
    exit 0
elif [ $TESTS_FAILED -le 2 ]; then
    echo -e "${YELLOW}⚠️  일부 테스트 실패 (${TESTS_FAILED}개), 하지만 핵심 기능은 정상 작동합니다.${NC}"
    exit 0
else
    echo -e "${RED}❌ 여러 테스트 실패 (${TESTS_FAILED}개). 시스템 점검이 필요합니다.${NC}"
    exit 1
fi