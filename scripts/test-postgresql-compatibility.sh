#!/bin/bash

# PostgreSQL JDBC 호환성 개선 테스트 스크립트
# 개선된 Agent 설정으로 autoCommit 문제 해결 검증

set -e

echo "🔍 PostgreSQL JDBC 호환성 개선 테스트 시작"
echo "========================================"

# 색상 정의
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# 설정
NAMESPACE="kubedb-monitor-test"
TEST_RESULTS_DIR="/tmp/postgresql-compatibility-test-results"
TIMESTAMP=$(date +%Y%m%d_%H%M%S)

# 결과 디렉토리 생성
mkdir -p ${TEST_RESULTS_DIR}

echo "📋 테스트 설정:"
echo "  - Namespace: ${NAMESPACE}"
echo "  - 결과 저장: ${TEST_RESULTS_DIR}"
echo "  - 타임스탬프: ${TIMESTAMP}"
echo ""

# Helper functions
log_info() {
    echo -e "${BLUE}ℹ️  $1${NC}"
}

log_success() {
    echo -e "${GREEN}✅ $1${NC}"
}

log_warning() {
    echo -e "${YELLOW}⚠️  $1${NC}"
}

log_error() {
    echo -e "${RED}❌ $1${NC}"
}

# Agent 빌드 및 배포
build_and_deploy_agent() {
    log_info "Agent 빌드 및 배포"
    
    cd /Users/narzis/workspace/kube-db-monitor
    
    # Agent 빌드
    log_info "Agent 빌드 중..."
    ./scripts/build-images.sh agent
    
    if [ $? -eq 0 ]; then
        log_success "Agent 빌드 완료"
    else
        log_error "Agent 빌드 실패"
        exit 1
    fi
}

# 기존 배포 정리
cleanup_existing_deployments() {
    log_info "기존 배포 정리"
    
    kubectl delete deployment university-registration-balanced -n ${NAMESPACE} --ignore-not-found=true
    kubectl delete deployment university-registration-no-agent -n ${NAMESPACE} --ignore-not-found=true
    
    # Pod가 완전히 삭제될 때까지 대기
    log_info "기존 Pod 완전 삭제 대기 중..."
    sleep 10
}

# Agent 없는 기준선 배포
deploy_no_agent_baseline() {
    log_info "Agent 없는 기준선 배포"
    
    kubectl apply -f k8s/agent-testing/postgresql-no-agent-test.yaml
    
    # Pod Ready 대기
    log_info "기준선 Pod Ready 대기 중..."
    kubectl wait --for=condition=Ready pod -l app=university-registration-no-agent -n ${NAMESPACE} --timeout=300s
    
    if [ $? -eq 0 ]; then
        log_success "기준선 배포 완료"
        return 0
    else
        log_error "기준선 배포 실패"
        return 1
    fi
}

# 개선된 Agent 배포
deploy_improved_agent() {
    log_info "개선된 Agent 배포"
    
    kubectl apply -f k8s/agent-testing/postgresql-balanced-improved.yaml
    
    # Pod Ready 대기
    log_info "개선된 Agent Pod 시작 대기 중..."
    sleep 30
    
    # Pod 상태 확인
    POD_NAME=$(kubectl get pods -n ${NAMESPACE} -l app=university-registration-balanced -o jsonpath='{.items[0].metadata.name}' 2>/dev/null || echo "")
    
    if [ -n "$POD_NAME" ]; then
        log_info "개선된 Agent Pod: ${POD_NAME}"
        
        # Pod 상태 모니터링 (최대 5분)
        for i in {1..10}; do
            POD_STATUS=$(kubectl get pod ${POD_NAME} -n ${NAMESPACE} -o jsonpath='{.status.phase}' 2>/dev/null || echo "Unknown")
            READY_STATUS=$(kubectl get pod ${POD_NAME} -n ${NAMESPACE} -o jsonpath='{.status.conditions[?(@.type=="Ready")].status}' 2>/dev/null || echo "Unknown")
            
            log_info "시도 ${i}/10: Pod 상태=${POD_STATUS}, Ready=${READY_STATUS}"
            
            if [ "$POD_STATUS" = "Running" ] && [ "$READY_STATUS" = "True" ]; then
                log_success "개선된 Agent 배포 완료"
                return 0
            fi
            
            if [ "$POD_STATUS" = "Failed" ] || [ "$POD_STATUS" = "Error" ]; then
                log_error "개선된 Agent Pod 실패 상태"
                return 1
            fi
            
            sleep 30
        done
        
        log_error "개선된 Agent 배포 타임아웃"
        return 1
    else
        log_error "개선된 Agent Pod 찾을 수 없음"
        return 1
    fi
}

# 호환성 테스트 실행
run_compatibility_tests() {
    local TEST_TYPE=$1
    local POD_SELECTOR=$2
    local RESULT_FILE="${TEST_RESULTS_DIR}/compatibility_test_${TEST_TYPE}_${TIMESTAMP}.txt"
    
    log_info "${TEST_TYPE} 호환성 테스트 실행"
    
    POD_NAME=$(kubectl get pods -n ${NAMESPACE} -l ${POD_SELECTOR} -o jsonpath='{.items[0].metadata.name}' 2>/dev/null || echo "")
    
    if [ -z "$POD_NAME" ]; then
        log_error "${TEST_TYPE} Pod 찾을 수 없음"
        echo "ERROR: Pod not found" > ${RESULT_FILE}
        return 1
    fi
    
    log_info "${TEST_TYPE} Pod: ${POD_NAME}"
    
    # 테스트 시작 시간
    echo "=== ${TEST_TYPE} 호환성 테스트 시작: $(date) ===" > ${RESULT_FILE}
    
    # 1. 기본 연결 테스트
    log_info "${TEST_TYPE}: 기본 연결 테스트"
    kubectl exec ${POD_NAME} -n ${NAMESPACE} -- curl -f http://localhost:8080/actuator/health/db >> ${RESULT_FILE} 2>&1
    if [ $? -eq 0 ]; then
        echo "✅ 기본 연결 테스트: 성공" >> ${RESULT_FILE}
        log_success "${TEST_TYPE}: 기본 연결 성공"
    else
        echo "❌ 기본 연결 테스트: 실패" >> ${RESULT_FILE}
        log_error "${TEST_TYPE}: 기본 연결 실패"
    fi
    
    # 2. 단순 쿼리 테스트
    log_info "${TEST_TYPE}: 단순 쿼리 테스트"
    kubectl exec ${POD_NAME} -n ${NAMESPACE} -- curl -f http://localhost:8080/api/courses >> ${RESULT_FILE} 2>&1
    if [ $? -eq 0 ]; then
        echo "✅ 단순 쿼리 테스트: 성공" >> ${RESULT_FILE}
        log_success "${TEST_TYPE}: 단순 쿼리 성공"
    else
        echo "❌ 단순 쿼리 테스트: 실패" >> ${RESULT_FILE}
        log_error "${TEST_TYPE}: 단순 쿼리 실패"
    fi
    
    # 3. 복잡한 쿼리 테스트 (NULL 파라미터 포함 - PostgreSQL 문제 쿼리)
    log_info "${TEST_TYPE}: 복잡한 쿼리 테스트 (NULL 파라미터)"
    kubectl exec ${POD_NAME} -n ${NAMESPACE} -- curl -f "http://localhost:8080/api/courses/search?department_id=&query=" >> ${RESULT_FILE} 2>&1
    if [ $? -eq 0 ]; then
        echo "✅ 복잡한 쿼리 테스트 (NULL 파라미터): 성공" >> ${RESULT_FILE}
        log_success "${TEST_TYPE}: 복잡한 쿼리 (NULL 파라미터) 성공"
    else
        echo "❌ 복잡한 쿼리 테스트 (NULL 파라미터): 실패" >> ${RESULT_FILE}
        log_error "${TEST_TYPE}: 복잡한 쿼리 (NULL 파라미터) 실패"
    fi
    
    # 4. 트랜잭션 테스트 
    log_info "${TEST_TYPE}: 트랜잭션 테스트"
    kubectl exec ${POD_NAME} -n ${NAMESPACE} -- curl -X POST -f http://localhost:8080/api/students \
        -H "Content-Type: application/json" \
        -d "{\"studentId\":\"TEST_${TEST_TYPE}_${TIMESTAMP}\",\"name\":\"테스트학생\",\"departmentId\":1}" >> ${RESULT_FILE} 2>&1
    if [ $? -eq 0 ]; then
        echo "✅ 트랜잭션 테스트: 성공" >> ${RESULT_FILE}
        log_success "${TEST_TYPE}: 트랜잭션 테스트 성공"
    else
        echo "❌ 트랜잭션 테스트: 실패" >> ${RESULT_FILE}
        log_error "${TEST_TYPE}: 트랜잭션 테스트 실패"
    fi
    
    # 5. 애플리케이션 로그에서 JDBC 에러 확인
    log_info "${TEST_TYPE}: JDBC 에러 로그 분석"
    echo "" >> ${RESULT_FILE}
    echo "=== JDBC 관련 에러 로그 분석 ===" >> ${RESULT_FILE}
    
    kubectl logs ${POD_NAME} -n ${NAMESPACE} --tail=200 | grep -E "(SQLException|JDBC.*ERROR|Unknown Types|Cannot commit|Unable to rollback|PSQLException)" >> ${RESULT_FILE} 2>/dev/null || true
    
    # 에러 카운트
    ERROR_COUNT=$(kubectl logs ${POD_NAME} -n ${NAMESPACE} --tail=200 | grep -E "(SQLException|JDBC.*ERROR|Unknown Types|Cannot commit|Unable to rollback|PSQLException)" | wc -l)
    echo "JDBC 에러 발생 건수: ${ERROR_COUNT}" >> ${RESULT_FILE}
    
    if [ ${ERROR_COUNT} -eq 0 ]; then
        log_success "${TEST_TYPE}: JDBC 에러 없음"
    else
        log_warning "${TEST_TYPE}: JDBC 에러 ${ERROR_COUNT}건 발견"
    fi
    
    # 테스트 종료 시간
    echo "" >> ${RESULT_FILE}
    echo "=== ${TEST_TYPE} 호환성 테스트 종료: $(date) ===" >> ${RESULT_FILE}
    
    log_success "${TEST_TYPE} 호환성 테스트 완료, 결과: ${RESULT_FILE}"
}

# 결과 비교 분석
compare_results() {
    log_info "결과 비교 분석"
    
    local COMPARISON_FILE="${TEST_RESULTS_DIR}/compatibility_comparison_${TIMESTAMP}.txt"
    
    echo "=== PostgreSQL JDBC 호환성 개선 전후 비교 ===" > ${COMPARISON_FILE}
    echo "테스트 시간: $(date)" >> ${COMPARISON_FILE}
    echo "" >> ${COMPARISON_FILE}
    
    # 기준선과 개선된 버전의 에러 개수 비교
    if [ -f "${TEST_RESULTS_DIR}/compatibility_test_no-agent_${TIMESTAMP}.txt" ] && [ -f "${TEST_RESULTS_DIR}/compatibility_test_improved-agent_${TIMESTAMP}.txt" ]; then
        
        NO_AGENT_ERRORS=$(grep "JDBC 에러 발생 건수:" "${TEST_RESULTS_DIR}/compatibility_test_no-agent_${TIMESTAMP}.txt" | grep -o '[0-9]*' || echo "0")
        IMPROVED_ERRORS=$(grep "JDBC 에러 발생 건수:" "${TEST_RESULTS_DIR}/compatibility_test_improved-agent_${TIMESTAMP}.txt" | grep -o '[0-9]*' || echo "0")
        
        echo "JDBC 에러 발생 건수 비교:" >> ${COMPARISON_FILE}
        echo "  - Agent 없음 (기준선): ${NO_AGENT_ERRORS} 건" >> ${COMPARISON_FILE}
        echo "  - 개선된 Agent: ${IMPROVED_ERRORS} 건" >> ${COMPARISON_FILE}
        echo "" >> ${COMPARISON_FILE}
        
        if [ ${IMPROVED_ERRORS} -lt ${NO_AGENT_ERRORS} ]; then
            echo "✅ 개선 결과: Agent 적용으로 JDBC 에러가 감소했습니다!" >> ${COMPARISON_FILE}
            log_success "Agent 개선 효과 확인: JDBC 에러 감소"
        elif [ ${IMPROVED_ERRORS} -eq ${NO_AGENT_ERRORS} ]; then
            if [ ${IMPROVED_ERRORS} -eq 0 ]; then
                echo "✅ 개선 결과: 두 환경 모두 JDBC 에러가 없습니다!" >> ${COMPARISON_FILE}
                log_success "Agent 호환성 완전 해결: 에러 없음"
            else
                echo "⚠️ 개선 결과: Agent 적용 후에도 JDBC 에러 수는 동일합니다." >> ${COMPARISON_FILE}
                log_warning "Agent 개선 효과 제한적: 에러 수 동일"
            fi
        else
            echo "❌ 개선 결과: Agent 적용으로 JDBC 에러가 증가했습니다!" >> ${COMPARISON_FILE}
            log_error "Agent 개선 실패: JDBC 에러 증가"
        fi
        
    else
        echo "❌ 비교 분석 실패: 테스트 결과 파일을 찾을 수 없습니다." >> ${COMPARISON_FILE}
        log_error "결과 비교 실패: 테스트 결과 파일 없음"
    fi
    
    echo "" >> ${COMPARISON_FILE}
    echo "=== 상세 로그 파일 위치 ===" >> ${COMPARISON_FILE}
    echo "기준선 (Agent 없음): ${TEST_RESULTS_DIR}/compatibility_test_no-agent_${TIMESTAMP}.txt" >> ${COMPARISON_FILE}
    echo "개선된 Agent: ${TEST_RESULTS_DIR}/compatibility_test_improved-agent_${TIMESTAMP}.txt" >> ${COMPARISON_FILE}
    echo "" >> ${COMPARISON_FILE}
    
    log_success "비교 분석 완료: ${COMPARISON_FILE}"
}

# 메인 실행 함수
main() {
    log_info "PostgreSQL JDBC 호환성 개선 테스트 시작"
    
    # 1. Agent 빌드 및 배포
    build_and_deploy_agent
    
    # 2. 기존 배포 정리
    cleanup_existing_deployments
    
    # 3. Agent 없는 기준선 배포 및 테스트
    log_info "=== 1단계: Agent 없는 기준선 테스트 ==="
    if deploy_no_agent_baseline; then
        run_compatibility_tests "no-agent" "app=university-registration-no-agent"
    else
        log_error "기준선 배포 실패, 테스트 중단"
        exit 1
    fi
    
    # 4. 개선된 Agent 배포 및 테스트
    log_info "=== 2단계: 개선된 Agent 테스트 ==="
    if deploy_improved_agent; then
        run_compatibility_tests "improved-agent" "app=university-registration-balanced"
    else
        log_warning "개선된 Agent 배포 실패, 기준선과의 비교만 수행"
    fi
    
    # 5. 결과 비교 분석
    log_info "=== 3단계: 결과 비교 분석 ==="
    compare_results
    
    # 6. 최종 요약
    echo ""
    echo "🎉 PostgreSQL JDBC 호환성 개선 테스트 완료!"
    echo "========================================"
    echo "📊 결과 요약:"
    echo "  - 테스트 결과: ${TEST_RESULTS_DIR}/"
    echo "  - 타임스탬프: ${TIMESTAMP}"
    echo ""
    echo "📁 생성된 파일:"
    ls -la ${TEST_RESULTS_DIR}/*${TIMESTAMP}*
    echo ""
    
    log_success "모든 테스트가 완료되었습니다!"
}

# 스크립트 실행
main "$@"