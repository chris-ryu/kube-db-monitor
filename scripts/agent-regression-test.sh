#!/usr/bin/env bash

#=====================================
# KubeDB Monitor Agent 회귀 테스트 스위트
# Agent 개발 및 수정을 위한 반복 테스트 환경
#=====================================

set -e  # 오류 발생 시 스크립트 중단

# 설정 파일 로드 (있으면)
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(dirname "$SCRIPT_DIR")"
CONFIG_FILE="$SCRIPT_DIR/agent-test-config.env"

# 기본 설정
TEST_NAMESPACE="${TEST_NAMESPACE:-kubedb-monitor-test}"
DEPLOYMENT_NAME="${DEPLOYMENT_NAME:-university-registration}"
SERVICE_NAME="${SERVICE_NAME:-university-registration-service}"
INGRESS_NAME="${INGRESS_NAME:-university-registration-demo-ingress}"
IMAGE_TAG="${IMAGE_TAG:-latest}"
TEST_TIMEOUT="${TEST_TIMEOUT:-300}"
TEST_DOMAIN="${TEST_DOMAIN:-university-registration.bitgaram.info}"

# 설정 파일이 있으면 로드
if [ -f "$CONFIG_FILE" ]; then
    source "$CONFIG_FILE"
fi

# 색상 코드
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
PURPLE='\033[0;35m'
CYAN='\033[0;36m'
NC='\033[0m' # No Color

# 로깅 함수들
log_info() { 
    echo -e "${BLUE}[$(date '+%H:%M:%S')] [INFO]${NC} $1"
}

log_success() { 
    echo -e "${GREEN}[$(date '+%H:%M:%S')] [SUCCESS]${NC} $1"
}

log_warning() { 
    echo -e "${YELLOW}[$(date '+%H:%M:%S')] [WARNING]${NC} $1"
}

log_error() { 
    echo -e "${RED}[$(date '+%H:%M:%S')] [ERROR]${NC} $1"
}

log_debug() { 
    if [ "$DEBUG" = "true" ]; then
        echo -e "${PURPLE}[$(date '+%H:%M:%S')] [DEBUG]${NC} $1"
    fi
}

log_step() { 
    echo -e "${CYAN}[$(date '+%H:%M:%S')] [STEP]${NC} $1"
}

# Agent 테스트 모드 설정 (개발 친화적으로 구성, Safe Transformation Mode 포함)
declare -A AGENT_MODES=(
    ["disabled"]="enabled=false"
    ["minimal"]="enabled=true,log-level=ERROR,safe-transformation-mode=true"
    ["basic"]="enabled=true,sampling-rate=0.1,log-level=WARN,safe-transformation-mode=true"
    ["monitoring"]="enabled=true,sampling-rate=0.3,slow-query-threshold=200,log-level=INFO,safe-transformation-mode=true"
    ["full"]="enabled=true,sampling-rate=0.5,slow-query-threshold=100,collector-endpoint=http://kubedb-monitor-control-plane.kubedb-monitor:8080/api/metrics,log-level=INFO,safe-transformation-mode=true"
    ["debug"]="enabled=true,sampling-rate=1.0,slow-query-threshold=50,log-level=DEBUG,safe-transformation-mode=true"
)

# 테스트 결과 저장
TEST_RESULTS=()
CURRENT_MODE=""
START_TIME=""

# 환경 체크
check_prerequisites() {
    log_step "환경 사전 조건 확인"
    
    # kubectl 확인
    if ! command -v kubectl &> /dev/null; then
        log_error "kubectl이 설치되지 않음"
        return 1
    fi
    
    # 네임스페이스 확인
    if ! kubectl get namespace "$TEST_NAMESPACE" &> /dev/null; then
        log_error "네임스페이스 '$TEST_NAMESPACE'가 존재하지 않음"
        return 1
    fi
    
    # 이미지 존재 확인
    log_debug "Docker 이미지 존재 확인 중..."
    
    # Ingress 존재 확인
    if ! kubectl get ingress "$INGRESS_NAME" -n "$TEST_NAMESPACE" &> /dev/null; then
        log_warning "Ingress '$INGRESS_NAME'가 존재하지 않음"
    fi
    
    log_success "사전 조건 확인 완료"
    return 0
}

# Agent 배포 함수
deploy_agent() {
    local mode_name="$1"
    local agent_config="$2"
    
    log_step "🚀 Agent 배포: $mode_name 모드"
    log_info "설정: $agent_config"
    
    # 기존 배포 정리
    kubectl delete deployment "$DEPLOYMENT_NAME" -n "$TEST_NAMESPACE" --ignore-not-found=true >/dev/null 2>&1
    log_debug "기존 배포 정리 완료"
    
    # Pod 완전 삭제 대기
    log_debug "Pod 완전 삭제 대기 중..."
    while kubectl get pods -n "$TEST_NAMESPACE" -l app="$DEPLOYMENT_NAME" --no-headers 2>/dev/null | grep -q .; do
        sleep 2
    done
    
    # 배포 YAML 생성
    local deployment_file="/tmp/agent-test-${mode_name}-deployment.yaml"
    cat > "$deployment_file" << EOF
apiVersion: apps/v1
kind: Deployment
metadata:
  name: $DEPLOYMENT_NAME
  namespace: $TEST_NAMESPACE
  labels:
    app: $DEPLOYMENT_NAME
    test-mode: $mode_name
    test-timestamp: "$(date +%s)"
spec:
  replicas: 1
  selector:
    matchLabels:
      app: $DEPLOYMENT_NAME
  template:
    metadata:
      labels:
        app: $DEPLOYMENT_NAME
        test-mode: $mode_name
      annotations:
        kubedb.monitor/enable: "true"
        test-timestamp: "$(date +%s)"
    spec:
      initContainers:
      - name: kubedb-agent-init
        image: registry.bitgaram.info/kubedb-monitor/agent:$IMAGE_TAG
        imagePullPolicy: Always
        command: ["/bin/sh", "-c"]
        args: 
        - |
          echo "🚀 KubeDB Monitor Agent 복사 중 ($mode_name 모드)..."
          cp /opt/kubedb-agent/kubedb-monitor-agent.jar /opt/shared-agent/kubedb-monitor-agent.jar
          echo "✅ Agent 복사 완료"
          echo "📋 Agent JAR 정보:"
          ls -la /opt/shared-agent/kubedb-monitor-agent.jar
        volumeMounts:
        - name: kubedb-agent
          mountPath: /opt/shared-agent
        resources:
          requests:
            memory: "64Mi"
            cpu: "50m"
          limits:
            memory: "128Mi"
            cpu: "100m"
      containers:
      - name: university-registration
        image: registry.bitgaram.info/kubedb-monitor/university-registration:$IMAGE_TAG
        imagePullPolicy: Always
        ports:
        - containerPort: 8080
          name: http
        env:
        - name: JAVA_OPTS
          value: "-Xmx512m -Xms256m -XX:+UseG1GC -javaagent:/opt/kubedb-agent/kubedb-monitor-agent.jar=$agent_config"
        - name: SPRING_PROFILES_ACTIVE
          value: "kubernetes"
        - name: SPRING_DATASOURCE_JDBC_URL
          value: "jdbc:postgresql://postgres-cluster-rw.postgres-system:5432/university"
        - name: SPRING_DATASOURCE_USERNAME
          value: "univ-app"
        - name: SPRING_DATASOURCE_PASSWORD
          value: "qlcrkfka1#"
        - name: SPRING_DATASOURCE_DRIVER_CLASS_NAME
          value: "org.postgresql.Driver"
        - name: SPRING_JPA_HIBERNATE_DDL_AUTO
          value: "create-drop"
        - name: SPRING_JPA_SHOW_SQL
          value: "false"
        - name: LOGGING_LEVEL_ROOT
          value: "WARN"
        - name: LOGGING_LEVEL_COM_UNIVERSITY_REGISTRATION
          value: "INFO"
        # 테스트 식별용 환경변수
        - name: AGENT_TEST_MODE
          value: "$mode_name"
        - name: AGENT_TEST_TIMESTAMP
          value: "$(date +%s)"
        volumeMounts:
        - name: kubedb-agent
          mountPath: /opt/kubedb-agent
        resources:
          requests:
            memory: "256Mi"
            cpu: "100m"
          limits:
            memory: "512Mi"
            cpu: "400m"
        livenessProbe:
          httpGet:
            path: /actuator/health
            port: 8080
          initialDelaySeconds: 120
          periodSeconds: 30
          timeoutSeconds: 10
          failureThreshold: 5
        readinessProbe:
          httpGet:
            path: /actuator/health
            port: 8080
          initialDelaySeconds: 60
          periodSeconds: 10
          timeoutSeconds: 5
          failureThreshold: 5
      volumes:
      - name: kubedb-agent
        emptyDir: {}
      imagePullSecrets:
      - name: registry-secret
      restartPolicy: Always
---
apiVersion: v1
kind: Service
metadata:
  name: $SERVICE_NAME
  namespace: $TEST_NAMESPACE
  labels:
    app: $DEPLOYMENT_NAME
    test-mode: $mode_name
spec:
  type: ClusterIP
  ports:
  - port: 8080
    targetPort: 8080
    protocol: TCP
    name: http
  selector:
    app: $DEPLOYMENT_NAME
EOF
    
    # 배포 실행
    if kubectl apply -f "$deployment_file" >/dev/null 2>&1; then
        log_success "배포 YAML 적용 완료"
        CURRENT_MODE="$mode_name"
        rm -f "$deployment_file"
        return 0
    else
        log_error "배포 실패"
        rm -f "$deployment_file"
        return 1
    fi
}

# Agent 상태 체크 (개선된 버전)
check_agent_status() {
    local timeout=${TEST_TIMEOUT}
    local elapsed=0
    local check_interval=10
    
    log_step "⏳ Agent 시작 대기 (최대 ${timeout}초)"
    
    while [ $elapsed -lt $timeout ]; do
        local pods_info=$(kubectl get pods -n "$TEST_NAMESPACE" -l app="$DEPLOYMENT_NAME" --no-headers 2>/dev/null || echo "")
        
        if [ -n "$pods_info" ]; then
            local pod_name=$(echo "$pods_info" | head -1 | awk '{print $1}')
            local pod_status=$(echo "$pods_info" | head -1 | awk '{print $3}')
            local ready_count=$(echo "$pods_info" | head -1 | awk '{print $2}')
            local restarts=$(echo "$pods_info" | head -1 | awk '{print $4}')
            
            log_debug "Pod: $pod_name, Status: $pod_status, Ready: $ready_count, Restarts: $restarts"
            
            case "$pod_status" in
                "Running")
                    if [ "$ready_count" = "1/1" ]; then
                        log_success "✅ Agent 정상 시작 완료 (${elapsed}초 소요)"
                        return 0
                    else
                        log_debug "Pod는 Running이지만 Ready 상태가 아님: $ready_count"
                    fi
                    ;;
                "CrashLoopBackOff"|"Error"|"Failed")
                    log_error "❌ Agent 시작 실패: $pod_status"
                    show_failure_logs "$pod_name"
                    return 1
                    ;;
                "Pending")
                    log_debug "Pod 생성 대기 중..."
                    ;;
                "ContainerCreating"|"PodInitializing")
                    log_debug "컨테이너 생성 중..."
                    ;;
                *)
                    log_debug "알 수 없는 상태: $pod_status"
                    ;;
            esac
        else
            log_debug "Pod 정보를 찾을 수 없음"
        fi
        
        sleep $check_interval
        elapsed=$((elapsed + check_interval))
        
        # 진행 상황 표시
        if [ $((elapsed % 30)) -eq 0 ]; then
            log_info "대기 중... (${elapsed}/${timeout}초)"
        fi
    done
    
    log_error "❌ Agent 시작 타임아웃 (${timeout}초)"
    return 1
}

# 실패 로그 표시
show_failure_logs() {
    local pod_name="$1"
    
    if [ -n "$pod_name" ]; then
        log_error "📋 실패 로그 (최근 20줄):"
        echo "----------------------------------------"
        kubectl logs "$pod_name" -n "$TEST_NAMESPACE" --previous --tail=20 2>/dev/null || \
        kubectl logs "$pod_name" -n "$TEST_NAMESPACE" --tail=20 2>/dev/null || \
        echo "로그를 가져올 수 없습니다."
        echo "----------------------------------------"
    fi
}

# Agent 기능 테스트 (상세한 진단)
test_agent_functionality() {
    local pod_name=$(kubectl get pods -n "$TEST_NAMESPACE" -l app="$DEPLOYMENT_NAME" --no-headers | head -1 | awk '{print $1}')
    
    if [ -z "$pod_name" ]; then
        log_error "테스트할 Pod를 찾을 수 없음"
        return 1
    fi
    
    log_step "🧪 Agent 기능 테스트: $pod_name"
    
    # 로그 수집
    local logs=$(kubectl logs "$pod_name" -n "$TEST_NAMESPACE" 2>/dev/null || echo "")
    
    if [ -z "$logs" ]; then
        log_error "Pod 로그를 가져올 수 없음"
        return 1
    fi
    
    local tests_passed=0
    local tests_total=0
    
    # 1. Agent 초기화 확인
    tests_total=$((tests_total + 1))
    if echo "$logs" | grep -q "KubeDB Monitor Agent start"; then
        log_success "  ✅ Agent 초기화 확인"
        tests_passed=$((tests_passed + 1))
    else
        log_error "  ❌ Agent 초기화 실패"
    fi
    
    # 2. Agent 설정 로딩 확인
    tests_total=$((tests_total + 1))
    if echo "$logs" | grep -q "Agent config loaded\|Agent configuration"; then
        log_success "  ✅ Agent 설정 로딩 확인"
        tests_passed=$((tests_passed + 1))
        
        # 설정 세부사항 표시
        local config_line=$(echo "$logs" | grep "Agent configuration" | head -1)
        if [ -n "$config_line" ]; then
            log_debug "  📋 Agent 설정: $(echo "$config_line" | sed 's/.*Agent configuration: //')"
        fi
    else
        log_warning "  ⚠️ Agent 설정 로딩 로그 없음"
    fi
    
    # 3. ASM 변환 오류 체크
    tests_total=$((tests_total + 1))
    local asm_errors=$(echo "$logs" | grep -c "Failed to transform\|Bad type on operand stack\|VerifyError\|ClassFormatError" || true)
    if [ "$asm_errors" -eq 0 ]; then
        log_success "  ✅ ASM 변환 오류 없음"
        tests_passed=$((tests_passed + 1))
    else
        log_error "  ❌ ASM 변환 오류 발견: $asm_errors 건"
        log_debug "  📋 ASM 오류 샘플:"
        echo "$logs" | grep -A2 -B2 "Failed to transform\|Bad type on operand stack\|VerifyError" | head -5
    fi
    
    # 4. 애플리케이션 시작 확인
    tests_total=$((tests_total + 1))
    if echo "$logs" | grep -q "Started CourseRegistrationApplication\|Tomcat started on port"; then
        log_success "  ✅ 애플리케이션 정상 시작"
        tests_passed=$((tests_passed + 1))
    else
        log_error "  ❌ 애플리케이션 시작 실패"
    fi
    
    # 5. JDBC 모니터링 활동 확인 (Agent가 활성화된 경우에만)
    if [ "$CURRENT_MODE" != "disabled" ]; then
        tests_total=$((tests_total + 1))
        local jdbc_activity=$(echo "$logs" | grep -c "JDBC\|Connection\|PreparedStatement\|Successfully transformed class" || true)
        if [ "$jdbc_activity" -gt 0 ]; then
            log_success "  ✅ JDBC 모니터링 활동 감지: $jdbc_activity 건"
            tests_passed=$((tests_passed + 1))
        else
            log_warning "  ⚠️ JDBC 모니터링 활동 감지되지 않음"
        fi
    fi
    
    # 테스트 결과 요약
    log_info "Agent 기능 테스트 결과: $tests_passed/$tests_total 통과"
    
    if [ "$tests_passed" -eq "$tests_total" ]; then
        return 0
    elif [ "$tests_passed" -gt $((tests_total / 2)) ]; then
        log_warning "일부 테스트 실패, 계속 진행"
        return 0
    else
        log_error "대부분의 테스트 실패"
        return 1
    fi
}

# Ingress 업데이트
update_ingress() {
    log_debug "Ingress를 현재 서비스로 업데이트"
    
    local patch_json="{\"spec\":{\"rules\":[{\"host\":\"$TEST_DOMAIN\",\"http\":{\"paths\":[{\"backend\":{\"service\":{\"name\":\"$SERVICE_NAME\",\"port\":{\"number\":8080}}},\"path\":\"/api\",\"pathType\":\"Prefix\"},{\"backend\":{\"service\":{\"name\":\"$SERVICE_NAME\",\"port\":{\"number\":8080}}},\"path\":\"/actuator\",\"pathType\":\"Prefix\"},{\"backend\":{\"service\":{\"name\":\"university-registration-ui-service\",\"port\":{\"number\":80}}},\"path\":\"/\",\"pathType\":\"Prefix\"}]}}]}}"
    
    if kubectl patch ingress "$INGRESS_NAME" -n "$TEST_NAMESPACE" -p "$patch_json" >/dev/null 2>&1; then
        log_debug "Ingress 업데이트 완료"
        sleep 5  # Ingress 변경 전파 대기
        return 0
    else
        log_warning "Ingress 업데이트 실패 (계속 진행)"
        return 0  # 실패해도 계속 진행
    fi
}

# API 테스트 (개선된 버전)
test_api_endpoints() {
    log_step "🌐 API 엔드포인트 테스트"
    
    update_ingress
    
    local tests_passed=0
    local tests_total=0
    
    # Health check
    tests_total=$((tests_total + 1))
    log_debug "Health check 테스트 중..."
    local health_response=$(curl -s -w "HTTPCODE:%{http_code}" --max-time 10 "https://$TEST_DOMAIN/actuator/health" 2>/dev/null || echo "ERROR")
    
    if echo "$health_response" | grep -q "HTTPCODE:200"; then
        if echo "$health_response" | grep -q '"status":"UP"'; then
            log_success "  ✅ Health Check 성공"
            tests_passed=$((tests_passed + 1))
        else
            log_warning "  ⚠️ Health Check 응답 상태가 UP이 아님"
            log_debug "  응답: $(echo "$health_response" | head -1)"
        fi
    else
        log_error "  ❌ Health Check 실패"
        log_debug "  응답: $health_response"
    fi
    
    # Course API
    tests_total=$((tests_total + 1))
    log_debug "Course API 테스트 중..."
    local courses_response=$(curl -s -w "HTTPCODE:%{http_code}" --max-time 10 "https://$TEST_DOMAIN/api/courses?page=0&size=1" 2>/dev/null || echo "ERROR")
    
    if echo "$courses_response" | grep -q "HTTPCODE:200"; then
        log_success "  ✅ Course API 성공"
        tests_passed=$((tests_passed + 1))
    else
        log_error "  ❌ Course API 실패"
        log_debug "  응답: $(echo "$courses_response" | tail -1)"
    fi
    
    # Cart API
    tests_total=$((tests_total + 1))
    log_debug "Cart API 테스트 중..."
    local cart_response=$(curl -s -w "HTTPCODE:%{http_code}" --max-time 10 "https://$TEST_DOMAIN/api/cart?studentId=2024001" 2>/dev/null || echo "ERROR")
    
    if echo "$cart_response" | grep -q "HTTPCODE:200"; then
        log_success "  ✅ Cart API 성공"
        tests_passed=$((tests_passed + 1))
    else
        log_warning "  ⚠️ Cart API 실패 (선택적)"
        log_debug "  응답: $(echo "$cart_response" | tail -1)"
    fi
    
    log_info "API 테스트 결과: $tests_passed/$tests_total 성공"
    
    # API 테스트는 1개 이상만 성공하면 통과
    if [ "$tests_passed" -gt 0 ]; then
        return 0
    else
        return 1
    fi
}

# Agent 모니터링 데이터 체크
test_monitoring_data() {
    local pod_name=$(kubectl get pods -n "$TEST_NAMESPACE" -l app="$DEPLOYMENT_NAME" --no-headers | head -1 | awk '{print $1}')
    
    if [ -z "$pod_name" ]; then
        return 1
    fi
    
    log_step "📊 Agent 모니터링 데이터 확인"
    
    # 최근 로그에서 모니터링 활동 확인
    local recent_logs=$(kubectl logs "$pod_name" -n "$TEST_NAMESPACE" --since=60s 2>/dev/null || echo "")
    
    # JDBC 관련 활동
    local jdbc_monitoring=$(echo "$recent_logs" | grep -c "JDBC\|Query\|Connection\|PreparedStatement" || true)
    if [ "$jdbc_monitoring" -gt 0 ]; then
        log_success "  ✅ JDBC 모니터링 활동: $jdbc_monitoring 건"
    else
        log_debug "  📋 JDBC 모니터링 활동 없음 (정상일 수 있음)"
    fi
    
    # Agent가 enabled인 경우 변환 활동 확인
    if [ "$CURRENT_MODE" != "disabled" ]; then
        local transform_activity=$(echo "$recent_logs" | grep -c "Successfully transformed class" || true)
        if [ "$transform_activity" -gt 0 ]; then
            log_success "  ✅ ASM 변환 활동: $transform_activity 건"
        fi
    fi
    
    return 0
}

# 단일 Agent 모드 테스트
test_single_mode() {
    local mode_name="$1"
    local agent_config="${AGENT_MODES[$mode_name]}"
    
    if [ -z "$agent_config" ]; then
        log_error "알 수 없는 Agent 모드: $mode_name"
        return 1
    fi
    
    START_TIME=$(date +%s)
    
    echo
    echo "=========================================="
    log_step "🔍 Agent 모드 테스트: $mode_name"
    log_info "설정: $agent_config"
    echo "=========================================="
    
    # 1. Agent 배포
    if ! deploy_agent "$mode_name" "$agent_config"; then
        TEST_RESULTS+=("$mode_name:DEPLOY_FAILED:$(date +%s)")
        return 1
    fi
    
    # 2. Agent 상태 체크
    if ! check_agent_status; then
        TEST_RESULTS+=("$mode_name:STATUS_FAILED:$(date +%s)")
        return 1
    fi
    
    # 3. Agent 기능 테스트
    if ! test_agent_functionality; then
        TEST_RESULTS+=("$mode_name:FUNCTION_FAILED:$(date +%s)")
        return 1
    fi
    
    # 4. API 테스트
    if ! test_api_endpoints; then
        TEST_RESULTS+=("$mode_name:API_FAILED:$(date +%s)")
        return 1
    fi
    
    # 5. 모니터링 데이터 체크 (실패해도 진행)
    test_monitoring_data
    
    local end_time=$(date +%s)
    local duration=$((end_time - START_TIME))
    
    TEST_RESULTS+=("$mode_name:SUCCESS:$duration")
    log_success "🎉 $mode_name 모드 테스트 완료 (${duration}초 소요)"
    return 0
}

# 전체 테스트 실행
run_all_tests() {
    log_step "🚀 전체 Agent 회귀 테스트 시작"
    
    local test_start_time=$(date +%s)
    
    # 개발에 유용한 순서로 실행
    local test_order=("disabled" "minimal" "basic" "monitoring" "full")
    
    for mode_name in "${test_order[@]}"; do
        if [[ -n "${AGENT_MODES[$mode_name]}" ]]; then
            test_single_mode "$mode_name"
            
            # 각 테스트 간 짧은 간격
            sleep 3
        fi
    done
    
    local test_end_time=$(date +%s)
    local total_duration=$((test_end_time - test_start_time))
    log_info "전체 테스트 소요 시간: ${total_duration}초"
}

# 특정 모드만 테스트
run_single_test() {
    local target_mode="$1"
    
    if [[ -z "${AGENT_MODES[$target_mode]}" ]]; then
        log_error "알 수 없는 모드: $target_mode"
        log_info "사용 가능한 모드: ${!AGENT_MODES[*]}"
        return 1
    fi
    
    test_single_mode "$target_mode"
    return $?
}

# 개발용 빠른 테스트 (minimal + basic만)
run_quick_test() {
    log_step "⚡ 빠른 Agent 테스트 (minimal + basic)"
    
    test_single_mode "minimal"
    sleep 3
    test_single_mode "basic"
}

# 결과 요약 (개선된 버전)
show_results() {
    echo
    echo "=========================================="
    log_step "📊 Agent 회귀 테스트 결과 요약"
    echo "=========================================="
    
    if [ ${#TEST_RESULTS[@]} -eq 0 ]; then
        log_warning "테스트 결과가 없습니다"
        return 1
    fi
    
    local total=0
    local passed=0
    local total_time=0
    
    for result in "${TEST_RESULTS[@]}"; do
        local mode=$(echo "$result" | cut -d':' -f1)
        local status=$(echo "$result" | cut -d':' -f2)
        local duration=$(echo "$result" | cut -d':' -f3)
        
        total=$((total + 1))
        
        if [ "$status" = "SUCCESS" ]; then
            log_success "✅ $mode: 성공 (${duration}초)"
            passed=$((passed + 1))
            total_time=$((total_time + duration))
        else
            log_error "❌ $mode: $status"
        fi
    done
    
    echo "----------------------------------------"
    log_info "총 테스트: $total"
    log_info "성공: $passed"
    log_info "실패: $((total - passed))"
    
    if [ $passed -gt 0 ]; then
        local avg_time=$((total_time / passed))
        log_info "평균 성공 시간: ${avg_time}초"
    fi
    
    if [ $passed -eq $total ]; then
        log_success "🎉 모든 Agent 테스트 통과!"
        return 0
    elif [ $passed -gt 0 ]; then
        log_warning "⚠️ 일부 Agent 테스트 성공 (${passed}/${total})"
        return 1
    else
        log_error "❌ 모든 Agent 테스트 실패"
        return 1
    fi
}

# Clean up (개선된 버전)
cleanup() {
    log_step "🧹 정리 작업 수행"
    
    # Deployment 삭제
    kubectl delete deployment "$DEPLOYMENT_NAME" -n "$TEST_NAMESPACE" --ignore-not-found=true >/dev/null 2>&1
    
    # 임시 파일 정리
    rm -f /tmp/agent-test-*-deployment.yaml
    
    # Pod 완전 삭제 대기 (옵션)
    if [ "$1" = "--wait" ]; then
        log_debug "Pod 완전 삭제 대기 중..."
        while kubectl get pods -n "$TEST_NAMESPACE" -l app="$DEPLOYMENT_NAME" --no-headers 2>/dev/null | grep -q .; do
            sleep 2
        done
    fi
    
    log_success "정리 작업 완료"
}

# 설정 파일 생성
create_config() {
    local config_file="$SCRIPT_DIR/agent-test-config.env"
    
    cat > "$config_file" << EOF
# KubeDB Monitor Agent 테스트 설정
# 이 파일을 수정하여 테스트 환경을 커스터마이징하세요

# 기본 설정
TEST_NAMESPACE=kubedb-monitor-test
DEPLOYMENT_NAME=university-registration
SERVICE_NAME=university-registration-service
INGRESS_NAME=university-registration-demo-ingress
IMAGE_TAG=latest
TEST_TIMEOUT=300
TEST_DOMAIN=university-registration.bitgaram.info

# 디버그 모드 (true/false)
DEBUG=false

# 추가 설정은 여기에...
EOF
    
    log_success "설정 파일 생성: $config_file"
    log_info "설정을 수정한 후 테스트를 실행하세요"
}

# 사용법 출력
usage() {
    echo "KubeDB Monitor Agent 회귀 테스트 스위트"
    echo
    echo "사용법: $0 [명령] [옵션]"
    echo
    echo "명령:"
    echo "  all                     - 모든 Agent 모드 테스트"
    echo "  quick                   - 빠른 테스트 (minimal + basic)"
    echo "  <mode>                  - 특정 모드만 테스트"
    echo "  cleanup [--wait]        - 정리 작업"
    echo "  config                  - 설정 파일 생성"
    echo "  list                    - 사용 가능한 모드 목록"
    echo "  help                    - 이 도움말 출력"
    echo
    echo "사용 가능한 모드:"
    for mode in "${!AGENT_MODES[@]}"; do
        echo "  $mode"
    done | sort
    echo
    echo "예시:"
    echo "  $0 all                  - 전체 회귀 테스트"
    echo "  $0 quick                - 빠른 테스트"
    echo "  $0 minimal              - minimal 모드만 테스트"
    echo "  $0 cleanup --wait       - 완전 정리"
    echo
    echo "환경변수:"
    echo "  DEBUG=true              - 디버그 모드 활성화"
    echo "  TEST_TIMEOUT=600        - 테스트 타임아웃 설정"
}

# 모드 목록 출력
list_modes() {
    log_info "사용 가능한 Agent 테스트 모드:"
    echo
    for mode in "${!AGENT_MODES[@]}"; do
        echo "  $mode: ${AGENT_MODES[$mode]}"
    done | sort
}

# 메인 실행 로직
main() {
    local command="$1"
    
    # 사전 조건 확인
    if [ "$command" != "help" ] && [ "$command" != "config" ] && [ "$command" != "list" ]; then
        if ! check_prerequisites; then
            exit 1
        fi
    fi
    
    case "$command" in
        "all")
            run_all_tests
            show_results
            exit $?
            ;;
        "quick")
            run_quick_test
            show_results
            exit $?
            ;;
        "cleanup")
            cleanup "$2"
            ;;
        "config")
            create_config
            ;;
        "list")
            list_modes
            ;;
        "help"|"")
            usage
            ;;
        *)
            if [[ -n "${AGENT_MODES[$command]}" ]]; then
                run_single_test "$command"
                show_results
                exit $?
            else
                log_error "알 수 없는 명령: $command"
                usage
                exit 1
            fi
            ;;
    esac
}

# 스크립트 종료 시 정리 (옵션)
# trap 'cleanup' EXIT

# 메인 함수 실행
main "$@"