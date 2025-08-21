#!/bin/bash

# KubeDB Monitor 전체 솔루션 E2E 테스트
# Agent → Control Plane → Dashboard-frontend 전체 플로우 검증

set -e

# 색상 정의
GREEN='\033[32m'
YELLOW='\033[33m'
RED='\033[31m'
BLUE='\033[34m'
CYAN='\033[36m'
RESET='\033[0m'

# 테스트 결과 추적
TEST_RESULTS=()
START_TIME=$(date +%s)

# 로그 함수
log_info() {
    echo -e "${BLUE}[INFO]${RESET} $1"
}

log_success() {
    echo -e "${GREEN}[SUCCESS]${RESET} $1"
    TEST_RESULTS+=("✅ $1")
}

log_warning() {
    echo -e "${YELLOW}[WARNING]${RESET} $1"
    TEST_RESULTS+=("⚠️ $1")
}

log_error() {
    echo -e "${RED}[ERROR]${RESET} $1"
    TEST_RESULTS+=("❌ $1")
}

log_step() {
    echo -e "${CYAN}[STEP]${RESET} $1"
}

# 테스트 상태 변수
LAYER_RESULTS=()

# 1단계: 이미지 빌드 및 배포
build_and_deploy() {
    log_step "1️⃣ 이미지 빌드 및 배포 시작"
    
    # 현재 배포 삭제
    log_info "기존 배포 삭제 중..."
    kubectl delete -f k8s/kubedb-monitor-deployment.yaml --ignore-not-found=true > /dev/null 2>&1 || true
    
    # Pod 완전 종료 대기
    log_info "Pod 완전 종료 대기 중..."
    sleep 15
    
    # 이미지 빌드
    log_info "모든 컴포넌트 이미지 빌드 중..."
    if ./scripts/build-images.sh all > /dev/null 2>&1; then
        log_success "이미지 빌드 완료"
    else
        log_error "이미지 빌드 실패"
        return 1
    fi
    
    # 새 배포
    log_info "새로운 배포 시작..."
    if kubectl apply -f k8s/kubedb-monitor-deployment.yaml > /dev/null 2>&1; then
        log_success "배포 성공"
    else
        log_error "배포 실패"
        return 1
    fi
    
    # Pod 준비 대기
    log_info "Pod 준비 대기 중..."
    kubectl wait --for=condition=ready pod -l app.kubernetes.io/part-of=kubedb-monitor --timeout=180s
    
    if [ $? -eq 0 ]; then
        log_success "모든 Pod 준비 완료"
    else
        log_warning "일부 Pod 준비 시간 초과"
    fi
    
    sleep 10  # 추가 안정화 시간
}

# 2단계: 동적 Pod 이름 파악
discover_pods() {
    log_step "2️⃣ 동적 Pod 이름 파악"
    
    # University Registration App Pod (여러 네임스페이스 확인)
    UNIVERSITY_POD=$(kubectl get pods --all-namespaces -l app=university-registration-demo -o jsonpath='{.items[0].metadata.name}' 2>/dev/null)
    UNIVERSITY_NAMESPACE=$(kubectl get pods --all-namespaces -l app=university-registration-demo -o jsonpath='{.items[0].metadata.namespace}' 2>/dev/null)
    
    if [ -n "$UNIVERSITY_POD" ] && [ -n "$UNIVERSITY_NAMESPACE" ]; then
        log_success "University App Pod: $UNIVERSITY_POD (namespace: $UNIVERSITY_NAMESPACE)"
    else
        log_error "University App Pod를 찾을 수 없음"
        return 1
    fi
    
    # Control Plane Pod (kubedb-monitor 네임스페이스)
    CONTROL_PLANE_POD=$(kubectl get pods -n kubedb-monitor -l app=kubedb-monitor-control-plane -o jsonpath='{.items[0].metadata.name}' 2>/dev/null)
    if [ -n "$CONTROL_PLANE_POD" ]; then
        log_success "Control Plane Pod: $CONTROL_PLANE_POD (namespace: kubedb-monitor)"
        CONTROL_PLANE_NAMESPACE="kubedb-monitor"
    else
        log_error "Control Plane Pod를 찾을 수 없음"
        return 1
    fi
    
    # Dashboard Frontend Pod (kubedb-monitor 네임스페이스)
    DASHBOARD_POD=$(kubectl get pods -n kubedb-monitor -l app=kubedb-monitor-dashboard -o jsonpath='{.items[0].metadata.name}' 2>/dev/null)
    if [ -n "$DASHBOARD_POD" ]; then
        log_success "Dashboard Frontend Pod: $DASHBOARD_POD (namespace: kubedb-monitor)"
        DASHBOARD_NAMESPACE="kubedb-monitor"
    else
        log_error "Dashboard Frontend Pod를 찾을 수 없음"
        return 1
    fi
    
    # Pod 상태 확인
    log_info "Pod 상태 확인:"
    kubectl get pods -n kubedb-monitor
    kubectl get pods -n "$UNIVERSITY_NAMESPACE" -l app=university-registration-demo
}

# 3단계: 로그 클리어 및 모니터링 준비
prepare_monitoring() {
    log_step "3️⃣ 로그 모니터링 준비"
    
    # 로그 파일 생성
    LOG_DIR="/tmp/kubedb-e2e-logs-$(date +%Y%m%d-%H%M%S)"
    mkdir -p "$LOG_DIR"
    
    log_info "로그 디렉토리: $LOG_DIR"
    
    # 백그라운드 로그 수집 시작 (네임스페이스 포함)
    kubectl logs -f "$UNIVERSITY_POD" -n "$UNIVERSITY_NAMESPACE" > "$LOG_DIR/university.log" 2>&1 &
    UNIVERSITY_LOG_PID=$!
    
    kubectl logs -f "$CONTROL_PLANE_POD" -n "$CONTROL_PLANE_NAMESPACE" > "$LOG_DIR/control-plane.log" 2>&1 &
    CONTROL_PLANE_LOG_PID=$!
    
    kubectl logs -f "$DASHBOARD_POD" -n "$DASHBOARD_NAMESPACE" > "$LOG_DIR/dashboard.log" 2>&1 &
    DASHBOARD_LOG_PID=$!
    
    log_success "로그 수집 시작 (PID: $UNIVERSITY_LOG_PID, $CONTROL_PLANE_LOG_PID, $DASHBOARD_LOG_PID)"
    
    # 초기 안정화 시간
    sleep 5
}

# 4단계: REST API 테스트 요청 실행
execute_test_request() {
    log_step "4️⃣ REST API 테스트 요청 실행"
    
    # 테스트 요청 실행
    log_info "과목 정보 조회 요청 실행..."
    
    # University App에 직접 요청 (네임스페이스 포함)
    RESPONSE=$(kubectl exec "$UNIVERSITY_POD" -n "$UNIVERSITY_NAMESPACE" -- curl -s "http://localhost:8080/api/courses?page=0&size=5" 2>/dev/null || echo "")
    
    if [ -n "$RESPONSE" ] && [[ "$RESPONSE" == *"content"* ]]; then
        log_success "REST API 요청 성공"
        
        # 응답 내용 로그
        echo "$RESPONSE" | head -c 200
        echo "..."
        
        # 추가 테스트 요청들
        log_info "추가 테스트 요청 실행 중..."
        
        # 개별 과목 조회
        kubectl exec "$UNIVERSITY_POD" -n "$UNIVERSITY_NAMESPACE" -- curl -s "http://localhost:8080/api/courses/CS101" > /dev/null 2>&1 &
        
        # 학과 정보 조회
        kubectl exec "$UNIVERSITY_POD" -n "$UNIVERSITY_NAMESPACE" -- curl -s "http://localhost:8080/api/departments" > /dev/null 2>&1 &
        
        # 수강신청 시도 (실패 예상)
        kubectl exec "$UNIVERSITY_POD" -n "$UNIVERSITY_NAMESPACE" -- curl -X POST "http://localhost:8080/api/enrollments/CS101?studentId=TEST001" > /dev/null 2>&1 &
        
        wait  # 모든 백그라운드 요청 완료 대기
        
        log_success "모든 테스트 요청 실행 완료"
        
    else
        log_error "REST API 요청 실패"
        return 1
    fi
    
    # 이벤트 전파 대기
    log_info "이벤트 전파 대기 중..."
    sleep 10
}

# 5단계: 각 레이어별 로그 분석
analyze_layer_logs() {
    log_step "5️⃣ 레이어별 로그 분석"
    
    # 로그 수집 중단
    kill $UNIVERSITY_LOG_PID $CONTROL_PLANE_LOG_PID $DASHBOARD_LOG_PID 2>/dev/null || true
    sleep 2
    
    # University App (Agent 포함) 로그 분석
    analyze_university_logs() {
        log_info "📱 University App + Agent 레이어 분석"
        
        local sql_events=0
        local agent_events=0
        local errors=0
        
        if [ -f "$LOG_DIR/university.log" ]; then
            # SQL 쿼리 이벤트 확인
            sql_events=$(grep -c -i "select\|insert\|update\|delete" "$LOG_DIR/university.log" 2>/dev/null || echo "0")
            
            # Agent 관련 이벤트 확인
            agent_events=$(grep -c -i "agent\|jdbc\|monitoring" "$LOG_DIR/university.log" 2>/dev/null || echo "0")
            
            # 에러 확인
            errors=$(grep -c -i "error\|exception\|failed" "$LOG_DIR/university.log" 2>/dev/null || echo "0")
            
            if [ $sql_events -gt 0 ]; then
                log_success "University App: SQL 이벤트 감지됨 ($sql_events개)"
                LAYER_RESULTS+=("University-SQL: ✅ $sql_events events")
            else
                log_warning "University App: SQL 이벤트 미감지"
                LAYER_RESULTS+=("University-SQL: ⚠️ No events")
            fi
            
            if [ $agent_events -gt 0 ]; then
                log_success "Agent: 모니터링 이벤트 감지됨 ($agent_events개)"
                LAYER_RESULTS+=("Agent: ✅ $agent_events events")
            else
                log_warning "Agent: 모니터링 이벤트 미감지"
                LAYER_RESULTS+=("Agent: ⚠️ No events")
            fi
            
            if [ $errors -gt 5 ]; then
                log_warning "University App: 다수의 에러 발생 ($errors개)"
            fi
        else
            log_error "University App 로그 파일 없음"
            LAYER_RESULTS+=("University: ❌ No logs")
        fi
    }
    
    # Control Plane 로그 분석
    analyze_control_plane_logs() {
        log_info "🎛️ Control Plane 레이어 분석"
        
        local received_events=0
        local processed_events=0
        local websocket_events=0
        local errors=0
        
        if [ -f "$LOG_DIR/control-plane.log" ]; then
            # 수신된 이벤트 확인
            received_events=$(grep -c -i "received\|incoming\|event" "$LOG_DIR/control-plane.log" 2>/dev/null || echo "0")
            
            # 처리된 이벤트 확인
            processed_events=$(grep -c -i "processed\|handling\|forwarding" "$LOG_DIR/control-plane.log" 2>/dev/null || echo "0")
            
            # WebSocket 관련 이벤트 확인
            websocket_events=$(grep -c -i "websocket\|ws\|dashboard" "$LOG_DIR/control-plane.log" 2>/dev/null || echo "0")
            
            # 에러 확인
            errors=$(grep -c -i "error\|exception\|failed" "$LOG_DIR/control-plane.log" 2>/dev/null || echo "0")
            
            if [ $received_events -gt 0 ]; then
                log_success "Control Plane: 이벤트 수신 확인됨 ($received_events개)"
                LAYER_RESULTS+=("ControlPlane-Receive: ✅ $received_events events")
            else
                log_warning "Control Plane: 이벤트 수신 미확인"
                LAYER_RESULTS+=("ControlPlane-Receive: ⚠️ No events")
            fi
            
            if [ $websocket_events -gt 0 ]; then
                log_success "Control Plane: Dashboard 전송 확인됨 ($websocket_events개)"
                LAYER_RESULTS+=("ControlPlane-Forward: ✅ $websocket_events events")
            else
                log_warning "Control Plane: Dashboard 전송 미확인"
                LAYER_RESULTS+=("ControlPlane-Forward: ⚠️ No events")
            fi
            
            if [ $errors -gt 3 ]; then
                log_warning "Control Plane: 다수의 에러 발생 ($errors개)"
            fi
        else
            log_error "Control Plane 로그 파일 없음"
            LAYER_RESULTS+=("ControlPlane: ❌ No logs")
        fi
    }
    
    # Dashboard Frontend 로그 분석
    analyze_dashboard_logs() {
        log_info "📊 Dashboard Frontend 레이어 분석"
        
        local websocket_connections=0
        local data_updates=0
        local ui_events=0
        local errors=0
        
        if [ -f "$LOG_DIR/dashboard.log" ]; then
            # WebSocket 연결 확인
            websocket_connections=$(grep -c -i "websocket\|connected\|ws" "$LOG_DIR/dashboard.log" 2>/dev/null || echo "0")
            
            # 데이터 업데이트 확인
            data_updates=$(grep -c -i "update\|refresh\|data" "$LOG_DIR/dashboard.log" 2>/dev/null || echo "0")
            
            # UI 이벤트 확인
            ui_events=$(grep -c -i "render\|component\|chart" "$LOG_DIR/dashboard.log" 2>/dev/null || echo "0")
            
            # 에러 확인
            errors=$(grep -c -i "error\|exception\|failed" "$LOG_DIR/dashboard.log" 2>/dev/null || echo "0")
            
            if [ $websocket_connections -gt 0 ]; then
                log_success "Dashboard: WebSocket 연결 확인됨 ($websocket_connections개)"
                LAYER_RESULTS+=("Dashboard-Connection: ✅ $websocket_connections events")
            else
                log_warning "Dashboard: WebSocket 연결 미확인"
                LAYER_RESULTS+=("Dashboard-Connection: ⚠️ No events")
            fi
            
            if [ $data_updates -gt 0 ] || [ $ui_events -gt 0 ]; then
                log_success "Dashboard: UI 업데이트 확인됨 (데이터: $data_updates, UI: $ui_events)"
                LAYER_RESULTS+=("Dashboard-UI: ✅ $((data_updates + ui_events)) events")
            else
                log_warning "Dashboard: UI 업데이트 미확인"
                LAYER_RESULTS+=("Dashboard-UI: ⚠️ No events")
            fi
            
            if [ $errors -gt 3 ]; then
                log_warning "Dashboard: 다수의 에러 발생 ($errors개)"
            fi
        else
            log_error "Dashboard 로그 파일 없음"
            LAYER_RESULTS+=("Dashboard: ❌ No logs")
        fi
    }
    
    # 각 레이어 분석 실행
    analyze_university_logs
    analyze_control_plane_logs
    analyze_dashboard_logs
}

# 6단계: 최종 결과 분석
analyze_final_results() {
    log_step "6️⃣ 최종 E2E 플로우 분석"
    
    local success_layers=0
    local warning_layers=0
    local failed_layers=0
    
    # 레이어별 결과 분석
    for result in "${LAYER_RESULTS[@]}"; do
        if [[ "$result" == *"✅"* ]]; then
            ((success_layers++))
        elif [[ "$result" == *"⚠️"* ]]; then
            ((warning_layers++))
        else
            ((failed_layers++))
        fi
    done
    
    # E2E 플로우 성공 기준
    local e2e_success=false
    
    # University App SQL 이벤트와 Control Plane 수신이 모두 성공하면 기본 플로우 성공
    if grep -q "University-SQL: ✅" <<< "${LAYER_RESULTS[*]}" && 
       grep -q "ControlPlane-Receive: ✅" <<< "${LAYER_RESULTS[*]}"; then
        e2e_success=true
    fi
    
    # Dashboard까지 이벤트가 도달하면 완전한 E2E 성공
    local complete_e2e=false
    if $e2e_success && grep -q "Dashboard.*: ✅" <<< "${LAYER_RESULTS[*]}"; then
        complete_e2e=true
    fi
    
    # 최종 결과
    if $complete_e2e; then
        log_success "🎉 완전한 E2E 플로우 성공! (Agent → Control Plane → Dashboard)"
        TEST_RESULTS+=("🎯 Complete E2E Flow: SUCCESS")
    elif $e2e_success; then
        log_success "✅ 기본 E2E 플로우 성공 (Agent → Control Plane)"
        log_warning "Dashboard까지의 완전한 플로우는 부분 성공"
        TEST_RESULTS+=("🎯 Basic E2E Flow: SUCCESS")
        TEST_RESULTS+=("🎯 Complete E2E Flow: PARTIAL")
    else
        log_error "❌ E2E 플로우 실패"
        TEST_RESULTS+=("🎯 E2E Flow: FAILED")
    fi
    
    return $($complete_e2e && echo 0 || echo 1)
}

# 7단계: 상세 보고서 생성
generate_report() {
    log_step "7️⃣ 상세 보고서 생성"
    
    local end_time=$(date +%s)
    local duration=$((end_time - START_TIME))
    
    local report_file="$LOG_DIR/e2e-test-report.md"
    
    cat > "$report_file" << EOF
# KubeDB Monitor E2E 테스트 보고서

## 📋 테스트 개요
- **실행 시간**: $(date)
- **소요 시간**: ${duration}초
- **로그 디렉토리**: $LOG_DIR

## 🔍 Pod 정보
- **University App**: $UNIVERSITY_POD
- **Control Plane**: $CONTROL_PLANE_POD  
- **Dashboard**: $DASHBOARD_POD

## 📊 레이어별 결과
EOF
    
    for result in "${LAYER_RESULTS[@]}"; do
        echo "- $result" >> "$report_file"
    done
    
    cat >> "$report_file" << EOF

## 🎯 전체 테스트 결과
EOF
    
    for result in "${TEST_RESULTS[@]}"; do
        echo "- $result" >> "$report_file"
    done
    
    cat >> "$report_file" << EOF

## 📁 로그 파일
- University App: \`$LOG_DIR/university.log\`
- Control Plane: \`$LOG_DIR/control-plane.log\`
- Dashboard: \`$LOG_DIR/dashboard.log\`

## 🔧 문제 해결 가이드
로그 파일을 확인하여 각 레이어에서 발생한 문제를 분석하세요.

\`\`\`bash
# 특정 키워드로 로그 검색
grep -n "ERROR\|Exception" $LOG_DIR/*.log

# SQL 이벤트 확인
grep -n -i "SELECT\|INSERT" $LOG_DIR/university.log

# WebSocket 이벤트 확인  
grep -n -i "websocket" $LOG_DIR/control-plane.log $LOG_DIR/dashboard.log
\`\`\`
EOF
    
    log_success "보고서 생성 완료: $report_file"
}

# 정리 함수
cleanup() {
    log_info "정리 작업 중..."
    
    # 백그라운드 프로세스 종료
    kill $UNIVERSITY_LOG_PID $CONTROL_PLANE_LOG_PID $DASHBOARD_LOG_PID 2>/dev/null || true
    
    log_info "E2E 테스트 완료"
}

# 메인 실행
main() {
    local skip_build=false
    
    # 인자 처리
    for arg in "$@"; do
        case $arg in
            --skip-build)
                skip_build=true
                shift
                ;;
        esac
    done
    
    if $skip_build; then
        log_info "⚡ KubeDB Monitor 빠른 E2E 테스트 시작 (빌드 생략)"
    else
        log_info "🚀 KubeDB Monitor 전체 솔루션 E2E 테스트 시작"
    fi
    
    # Trap으로 정리 함수 등록
    trap cleanup EXIT
    
    # 단계별 실행
    if ! $skip_build; then
        build_and_deploy || exit 1
    else
        log_info "빌드 및 배포 단계 생략됨"
    fi
    
    discover_pods || exit 1
    prepare_monitoring || exit 1
    execute_test_request || exit 1
    analyze_layer_logs
    
    if analyze_final_results; then
        log_success "🎉 전체 E2E 테스트 성공!"
        exit_code=0
    else
        log_warning "⚠️ E2E 테스트 부분 성공 또는 실패"
        exit_code=1
    fi
    
    generate_report
    
    # 결과 요약
    echo
    log_info "📋 테스트 결과 요약:"
    for result in "${TEST_RESULTS[@]}"; do
        echo "  $result"
    done
    
    echo
    log_info "📁 자세한 내용은 보고서를 확인하세요: $LOG_DIR/e2e-test-report.md"
    
    exit $exit_code
}

# 스크립트 실행
if [[ "${BASH_SOURCE[0]}" == "${0}" ]]; then
    main "$@"
fi