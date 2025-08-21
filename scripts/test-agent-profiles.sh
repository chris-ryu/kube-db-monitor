#!/bin/bash

# KubeDB Monitor Agent 프로파일별 호환성 테스트 스크립트
# PostgreSQL 기반 3가지 Agent 모드 테스트 (Conservative/Balanced/Aggressive)

set -e

# 색상 정의
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
PURPLE='\033[0;35m'
NC='\033[0m' # No Color

# 로그 함수
log_info() {
    echo -e "${BLUE}[INFO]${NC} $1"
}

log_success() {
    echo -e "${GREEN}[SUCCESS]${NC} $1"
}

log_warn() {
    echo -e "${YELLOW}[WARN]${NC} $1"
}

log_error() {
    echo -e "${RED}[ERROR]${NC} $1"
}

log_section() {
    echo -e "\n${PURPLE}=== $1 ===${NC}\n"
}

# 테스트 설정
NAMESPACE="kubedb-monitor-test"
PROFILES=("conservative" "balanced" "aggressive")
TEST_DURATION=60  # 각 테스트 지속시간 (초)
LOAD_THREADS=10   # 부하 테스트 스레드 수

# 결과 저장 디렉토리
RESULTS_DIR="/tmp/agent-profile-test-results-$(date +%Y%m%d-%H%M%S)"
mkdir -p "$RESULTS_DIR"

log_section "KubeDB Monitor Agent 프로파일 테스트 시작"
log_info "테스트 결과 저장 위치: $RESULTS_DIR"

# 1. Agent 프로파일 배포
deploy_agent_profiles() {
    log_section "Agent 프로파일별 배포"
    
    # ConfigMap 배포
    log_info "Agent 설정 ConfigMap 배포..."
    kubectl apply -f /Users/narzis/workspace/kube-db-monitor/configs/agent-profiles.yaml
    
    # 테스트 배포 적용
    log_info "Agent 프로파일별 테스트 배포..."
    kubectl apply -f /Users/narzis/workspace/kube-db-monitor/k8s/agent-testing/postgresql-agent-test-deployments.yaml
    
    # 배포 완료 대기
    log_info "배포 완료 대기..."
    for profile in "${PROFILES[@]}"; do
        log_info "university-registration-${profile} 배포 대기..."
        kubectl rollout status deployment/university-registration-${profile} -n ${NAMESPACE} --timeout=300s
        
        # Pod 준비 상태 확인
        kubectl wait --for=condition=ready pod -l app=university-registration-${profile} -n ${NAMESPACE} --timeout=180s
        
        log_success "university-registration-${profile} 배포 완료"
    done
}

# 2. 기본 헬스체크
health_check() {
    log_section "기본 헬스체크"
    
    for profile in "${PROFILES[@]}"; do
        log_info "[$profile] 헬스체크 수행..."
        
        POD_NAME=$(kubectl get pod -l app=university-registration-${profile} -n ${NAMESPACE} -o jsonpath='{.items[0].metadata.name}')
        SERVICE_NAME="university-registration-${profile}-service"
        
        # 애플리케이션 시작 대기
        sleep 30
        
        # 헬스체크 엔드포인트 테스트
        kubectl exec ${POD_NAME} -n ${NAMESPACE} -- curl -f http://localhost:8080/actuator/health > "${RESULTS_DIR}/${profile}-health.json" 2>/dev/null
        
        if [ $? -eq 0 ]; then
            log_success "[$profile] 헬스체크 통과"
        else
            log_error "[$profile] 헬스체크 실패"
        fi
        
        # API 엔드포인트 테스트
        kubectl exec ${POD_NAME} -n ${NAMESPACE} -- curl -f http://localhost:8080/api/courses > "${RESULTS_DIR}/${profile}-api-test.json" 2>/dev/null
        
        if [ $? -eq 0 ]; then
            log_success "[$profile] API 엔드포인트 정상"
        else
            log_error "[$profile] API 엔드포인트 오류"
        fi
    done
}

# 3. Agent 로그 및 메트릭 수집
collect_agent_logs() {
    log_section "Agent 로그 및 메트릭 수집"
    
    for profile in "${PROFILES[@]}"; do
        log_info "[$profile] Agent 로그 수집..."
        
        POD_NAME=$(kubectl get pod -l app=university-registration-${profile} -n ${NAMESPACE} -o jsonpath='{.items[0].metadata.name}')
        
        # 애플리케이션 로그 수집
        kubectl logs ${POD_NAME} -n ${NAMESPACE} --tail=500 > "${RESULTS_DIR}/${profile}-app.log"
        
        # JVM 메트릭 수집
        kubectl exec ${POD_NAME} -n ${NAMESPACE} -- curl -s http://localhost:8080/actuator/metrics > "${RESULTS_DIR}/${profile}-jvm-metrics.json" 2>/dev/null || true
        
        # HikariCP 메트릭 수집
        kubectl exec ${POD_NAME} -n ${NAMESPACE} -- curl -s http://localhost:8080/actuator/metrics/hikaricp.connections > "${RESULTS_DIR}/${profile}-hikaricp.json" 2>/dev/null || true
        
        log_success "[$profile] 로그 및 메트릭 수집 완료"
    done
}

# 4. 부하 테스트
load_test() {
    log_section "부하 테스트 실행"
    
    for profile in "${PROFILES[@]}"; do
        log_info "[$profile] 부하 테스트 시작 (${TEST_DURATION}초 동안 ${LOAD_THREADS}개 스레드)"
        
        POD_NAME=$(kubectl get pod -l app=university-registration-${profile} -n ${NAMESPACE} -o jsonpath='{.items[0].metadata.name}')
        
        # 부하 테스트 시작 시간 기록
        START_TIME=$(date +%s)
        echo "Start Time: $(date)" > "${RESULTS_DIR}/${profile}-load-test.log"
        
        # 동시에 여러 요청 실행 (백그라운드)
        for i in $(seq 1 ${LOAD_THREADS}); do
            {
                REQUESTS=0
                ERRORS=0
                END_TIME=$((START_TIME + TEST_DURATION))
                
                while [ $(date +%s) -lt $END_TIME ]; do
                    # API 요청
                    if kubectl exec ${POD_NAME} -n ${NAMESPACE} -- curl -s -f -w "%{http_code}\\n" http://localhost:8080/api/courses > /dev/null 2>&1; then
                        ((REQUESTS++))
                    else
                        ((ERRORS++))
                    fi
                    
                    # 요청 간격
                    sleep 0.1
                done
                
                echo "Thread $i: Requests=$REQUESTS, Errors=$ERRORS" >> "${RESULTS_DIR}/${profile}-load-test.log"
            } &
        done
        
        # 모든 백그라운드 작업 완료 대기
        wait
        
        # 최종 시간 기록
        echo "End Time: $(date)" >> "${RESULTS_DIR}/${profile}-load-test.log"
        
        log_success "[$profile] 부하 테스트 완료"
    done
}

# 5. 성능 비교 분석
performance_analysis() {
    log_section "성능 비교 분석"
    
    # 리소스 사용량 수집
    for profile in "${PROFILES[@]}"; do
        log_info "[$profile] 리소스 사용량 수집..."
        
        POD_NAME=$(kubectl get pod -l app=university-registration-${profile} -n ${NAMESPACE} -o jsonpath='{.items[0].metadata.name}')
        
        # CPU 및 메모리 사용량
        kubectl top pod ${POD_NAME} -n ${NAMESPACE} --no-headers > "${RESULTS_DIR}/${profile}-resource-usage.txt" 2>/dev/null || echo "리소스 정보 수집 실패" > "${RESULTS_DIR}/${profile}-resource-usage.txt"
        
        # Agent 특화 메트릭 수집 (Control Plane에서)
        curl -s "http://kubedb-monitor-control-plane.kubedb-monitor:8080/api/metrics/profile/${profile}" > "${RESULTS_DIR}/${profile}-agent-metrics.json" 2>/dev/null || echo "{\"error\": \"메트릭 수집 실패\"}" > "${RESULTS_DIR}/${profile}-agent-metrics.json"
    done
    
    # 비교 리포트 생성
    log_info "성능 비교 리포트 생성..."
    
    cat > "${RESULTS_DIR}/performance-comparison.md" << EOF
# KubeDB Monitor Agent 프로파일 성능 비교 리포트

## 테스트 개요
- 테스트 일시: $(date)
- 테스트 대상: PostgreSQL + HikariCP + Spring Boot
- 테스트 지속시간: ${TEST_DURATION}초
- 부하 테스트 스레드: ${LOAD_THREADS}개

## 프로파일별 설정 요약

### Conservative 모드
- 샘플링 비율: 5%
- 슬로우 쿼리 임계값: 200ms
- 로그 레벨: ERROR
- 메모리 제한: 64MB
- 적용 대상: 프로덕션 고부하 환경

### Balanced 모드  
- 샘플링 비율: 10%
- 슬로우 쿼리 임계값: 100ms
- 로그 레벨: WARN
- 메모리 제한: 128MB
- 적용 대상: 일반 프로덕션 환경

### Aggressive 모드
- 샘플링 비율: 50%
- 슬로우 쿼리 임계값: 50ms
- 로그 레벨: DEBUG
- 메모리 제한: 256MB
- 적용 대상: 개발/테스트 환경

## 테스트 결과 파일
$(ls -la ${RESULTS_DIR}/ | grep -v "^total" | grep -v "^d")

## 권장사항
1. **프로덕션 환경**: Conservative 또는 Balanced 모드 사용
2. **개발/테스트**: Aggressive 모드로 상세 모니터링
3. **성능 우선**: Conservative 모드로 오버헤드 최소화
4. **모니터링 우선**: Balanced 모드로 균형잡힌 관찰

EOF

    log_success "성능 비교 리포트 생성 완료"
}

# 6. 정리
cleanup() {
    log_section "테스트 환경 정리"
    
    log_warn "테스트 배포 유지 중 (수동 정리 필요)"
    log_info "정리 명령어:"
    echo "kubectl delete -f /Users/narzis/workspace/kube-db-monitor/k8s/agent-testing/postgresql-agent-test-deployments.yaml"
    echo "kubectl delete -f /Users/narzis/workspace/kube-db-monitor/configs/agent-profiles.yaml"
}

# 메인 실행
main() {
    log_section "PostgreSQL Agent 프로파일 호환성 테스트"
    
    deploy_agent_profiles
    sleep 60  # 안정화 대기
    
    health_check
    collect_agent_logs
    load_test
    performance_analysis
    
    log_section "테스트 완료"
    log_success "결과 확인: cat ${RESULTS_DIR}/performance-comparison.md"
    log_info "상세 로그: ls -la ${RESULTS_DIR}/"
    
    cleanup
}

# 스크립트 실행
if [[ "${BASH_SOURCE[0]}" == "${0}" ]]; then
    main "$@"
fi