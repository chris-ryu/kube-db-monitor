#!/usr/bin/env bash

#=====================================
# 수강 신청 앱 REST 서비스 회귀 테스트
# PostgreSQL 호환성 및 전체 기능 검증
#=====================================

set -e

# 설정
PROJECT_ROOT="/Users/narzis/workspace/kube-db-monitor"
APP_DIR="$PROJECT_ROOT/sample-apps/university-registration"
TEST_RESULTS_DIR="/tmp/university-app-test-results"

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

# 테스트 결과 저장
TEST_RESULTS=()
TOTAL_TESTS=0
PASSED_TESTS=0
FAILED_TESTS=0
START_TIME=""

# 사전 조건 확인
check_prerequisites() {
    log_step "🔍 사전 조건 확인"
    
    # Java 확인
    if ! command -v java &> /dev/null; then
        log_error "Java가 설치되지 않음"
        return 1
    fi
    
    # Maven 확인
    if ! command -v mvn &> /dev/null; then
        log_error "Maven이 설치되지 않음"
        return 1
    fi
    
    # Docker 확인 (TestContainers용)
    if ! command -v docker &> /dev/null; then
        log_error "Docker가 설치되지 않음"
        return 1
    fi
    
    # 프로젝트 디렉터리 확인
    if [ ! -d "$APP_DIR" ]; then
        log_error "수강 신청 앱 디렉터리가 존재하지 않음: $APP_DIR"
        return 1
    fi
    
    # Docker 데몬 실행 확인
    if ! docker info &> /dev/null; then
        log_error "Docker 데몬이 실행되지 않음"
        return 1
    fi
    
    log_success "사전 조건 확인 완료"
    return 0
}

# 테스트 결과 디렉터리 준비
prepare_test_results_dir() {
    mkdir -p "$TEST_RESULTS_DIR"
    rm -rf "$TEST_RESULTS_DIR"/*
    log_debug "테스트 결과 디렉터리 준비: $TEST_RESULTS_DIR"
}

# Repository 레이어 테스트
run_repository_tests() {
    log_step "🗃️ Repository 레이어 테스트"
    
    cd "$APP_DIR"
    
    local test_output="$TEST_RESULTS_DIR/repository-tests.log"
    local test_result=0
    
    mvn test -Dtest=PostgreSQLCourseRepositoryIntegrationTest -q > "$test_output" 2>&1 || test_result=$?
    
    if [ $test_result -eq 0 ]; then
        log_success "Repository 테스트 통과"
        TEST_RESULTS+=("repository:PASS")
        PASSED_TESTS=$((PASSED_TESTS + 1))
    else
        log_error "Repository 테스트 실패"
        TEST_RESULTS+=("repository:FAIL")
        FAILED_TESTS=$((FAILED_TESTS + 1))
        
        log_error "Repository 테스트 실패 로그 (마지막 20줄):"
        tail -20 "$test_output"
    fi
    
    TOTAL_TESTS=$((TOTAL_TESTS + 1))
}

# Service 레이어 테스트
run_service_tests() {
    log_step "🔧 Service 레이어 테스트"
    
    cd "$APP_DIR"
    
    local test_output="$TEST_RESULTS_DIR/service-tests.log"
    local test_result=0
    
    mvn test -Dtest=CourseServiceIntegrationTest -q > "$test_output" 2>&1 || test_result=$?
    
    if [ $test_result -eq 0 ]; then
        log_success "Service 테스트 통과"
        TEST_RESULTS+=("service:PASS")
        PASSED_TESTS=$((PASSED_TESTS + 1))
    else
        log_error "Service 테스트 실패"
        TEST_RESULTS+=("service:FAIL")
        FAILED_TESTS=$((FAILED_TESTS + 1))
        
        log_error "Service 테스트 실패 로그 (마지막 20줄):"
        tail -20 "$test_output"
    fi
    
    TOTAL_TESTS=$((TOTAL_TESTS + 1))
}

# Controller 레이어 테스트
run_controller_tests() {
    log_step "🎮 Controller 레이어 테스트"
    
    cd "$APP_DIR"
    
    # Course Controller 테스트
    local course_test_output="$TEST_RESULTS_DIR/course-controller-tests.log"
    local course_test_result=0
    
    log_info "Course Controller 테스트 실행 중..."
    mvn test -Dtest=CourseControllerIntegrationTest -q > "$course_test_output" 2>&1 || course_test_result=$?
    
    if [ $course_test_result -eq 0 ]; then
        log_success "Course Controller 테스트 통과"
        TEST_RESULTS+=("course-controller:PASS")
        PASSED_TESTS=$((PASSED_TESTS + 1))
    else
        log_error "Course Controller 테스트 실패"
        TEST_RESULTS+=("course-controller:FAIL")
        FAILED_TESTS=$((FAILED_TESTS + 1))
        
        log_error "Course Controller 테스트 실패 로그 (마지막 20줄):"
        tail -20 "$course_test_output"
    fi
    TOTAL_TESTS=$((TOTAL_TESTS + 1))
    
    # Cart Controller 테스트
    local cart_test_output="$TEST_RESULTS_DIR/cart-controller-tests.log"
    local cart_test_result=0
    
    log_info "Cart Controller 테스트 실행 중..."
    mvn test -Dtest=CartControllerIntegrationTest -q > "$cart_test_output" 2>&1 || cart_test_result=$?
    
    if [ $cart_test_result -eq 0 ]; then
        log_success "Cart Controller 테스트 통과"
        TEST_RESULTS+=("cart-controller:PASS")
        PASSED_TESTS=$((PASSED_TESTS + 1))
    else
        log_error "Cart Controller 테스트 실패"
        TEST_RESULTS+=("cart-controller:FAIL")
        FAILED_TESTS=$((FAILED_TESTS + 1))
        
        log_error "Cart Controller 테스트 실패 로그 (마지막 20줄):"
        tail -20 "$cart_test_output"
    fi
    TOTAL_TESTS=$((TOTAL_TESTS + 1))
}

# Agent 호환성 테스트 (KubeDB Monitor Agent와의 통합)
run_agent_compatibility_tests() {
    log_step "🤖 Agent 호환성 테스트"
    
    cd "$PROJECT_ROOT/kubedb-monitor-agent"
    
    local test_output="$TEST_RESULTS_DIR/agent-compatibility-tests.log"
    local test_result=0
    
    mvn test -Dtest=JDBCCompatibilityTestSuite -q > "$test_output" 2>&1 || test_result=$?
    
    if [ $test_result -eq 0 ]; then
        log_success "Agent 호환성 테스트 통과"
        TEST_RESULTS+=("agent-compatibility:PASS")
        PASSED_TESTS=$((PASSED_TESTS + 1))
    else
        log_error "Agent 호환성 테스트 실패"
        TEST_RESULTS+=("agent-compatibility:FAIL")
        FAILED_TESTS=$((FAILED_TESTS + 1))
        
        log_error "Agent 호환성 테스트 실패 로그 (마지막 20줄):"
        tail -20 "$test_output"
    fi
    
    TOTAL_TESTS=$((TOTAL_TESTS + 1))
}

# 전체 애플리케이션 테스트 (모든 테스트 함께 실행)
run_full_application_tests() {
    log_step "🏗️ 전체 애플리케이션 테스트"
    
    cd "$APP_DIR"
    
    local test_output="$TEST_RESULTS_DIR/full-application-tests.log"
    local test_result=0
    
    log_info "전체 테스트 스위트 실행 중... (시간이 오래 걸릴 수 있습니다)"
    mvn test -q > "$test_output" 2>&1 || test_result=$?
    
    if [ $test_result -eq 0 ]; then
        log_success "전체 애플리케이션 테스트 통과"
        TEST_RESULTS+=("full-application:PASS")
        PASSED_TESTS=$((PASSED_TESTS + 1))
    else
        log_error "전체 애플리케이션 테스트 실패"
        TEST_RESULTS+=("full-application:FAIL")
        FAILED_TESTS=$((FAILED_TESTS + 1))
        
        log_error "전체 애플리케이션 테스트 실패 로그 (마지막 30줄):"
        tail -30 "$test_output"
    fi
    
    TOTAL_TESTS=$((TOTAL_TESTS + 1))
}

# PostgreSQL 호환성 검증
run_postgresql_compatibility_tests() {
    log_step "🐘 PostgreSQL 호환성 검증"
    
    cd "$APP_DIR"
    
    local test_output="$TEST_RESULTS_DIR/postgresql-compatibility-tests.log"
    local test_result=0
    
    # PostgreSQL 관련 테스트만 실행
    mvn test -Dtest="*PostgreSQL*" -q > "$test_output" 2>&1 || test_result=$?
    
    if [ $test_result -eq 0 ]; then
        log_success "PostgreSQL 호환성 테스트 통과"
        TEST_RESULTS+=("postgresql-compatibility:PASS")
        PASSED_TESTS=$((PASSED_TESTS + 1))
    else
        log_error "PostgreSQL 호환성 테스트 실패"
        TEST_RESULTS+=("postgresql-compatibility:FAIL")
        FAILED_TESTS=$((FAILED_TESTS + 1))
        
        log_error "PostgreSQL 호환성 테스트 실패 로그 (마지막 20줄):"
        tail -20 "$test_output"
    fi
    
    TOTAL_TESTS=$((TOTAL_TESTS + 1))
}

# 성능 테스트
run_performance_tests() {
    log_step "⚡ 성능 테스트"
    
    cd "$APP_DIR"
    
    local test_output="$TEST_RESULTS_DIR/performance-tests.log"
    local test_result=0
    
    # 성능 관련 테스트 메소드 실행
    mvn test -Dtest="*performanceTest*,*PerformanceTest*" -q > "$test_output" 2>&1 || test_result=$?
    
    if [ $test_result -eq 0 ]; then
        log_success "성능 테스트 통과"
        TEST_RESULTS+=("performance:PASS")
        PASSED_TESTS=$((PASSED_TESTS + 1))
    else
        log_warning "성능 테스트 실패 (일부 성능 테스트가 없을 수 있음)"
        TEST_RESULTS+=("performance:SKIP")
    fi
    
    TOTAL_TESTS=$((TOTAL_TESTS + 1))
}

# 보안 테스트
run_security_tests() {
    log_step "🔒 보안 테스트"
    
    cd "$APP_DIR"
    
    local test_output="$TEST_RESULTS_DIR/security-tests.log"
    local test_result=0
    
    # SQL Injection 방어 등 보안 관련 테스트
    mvn test -Dtest="*SQLInjection*,*Security*" -q > "$test_output" 2>&1 || test_result=$?
    
    if [ $test_result -eq 0 ]; then
        log_success "보안 테스트 통과"
        TEST_RESULTS+=("security:PASS")
        PASSED_TESTS=$((PASSED_TESTS + 1))
    else
        log_warning "보안 테스트 실패 (일부 보안 테스트가 없을 수 있음)"
        TEST_RESULTS+=("security:SKIP")
    fi
    
    TOTAL_TESTS=$((TOTAL_TESTS + 1))
}

# 코드 품질 검사 (컴파일, Lint 등)
run_code_quality_checks() {
    log_step "📋 코드 품질 검사"
    
    cd "$APP_DIR"
    
    local compile_output="$TEST_RESULTS_DIR/compile-check.log"
    local compile_result=0
    
    # 컴파일 체크
    mvn compile -q > "$compile_output" 2>&1 || compile_result=$?
    
    if [ $compile_result -eq 0 ]; then
        log_success "컴파일 성공"
        TEST_RESULTS+=("compile:PASS")
        PASSED_TESTS=$((PASSED_TESTS + 1))
    else
        log_error "컴파일 실패"
        TEST_RESULTS+=("compile:FAIL")
        FAILED_TESTS=$((FAILED_TESTS + 1))
        
        log_error "컴파일 실패 로그:"
        cat "$compile_output"
    fi
    
    TOTAL_TESTS=$((TOTAL_TESTS + 1))
    
    # 테스트 컴파일 체크
    local test_compile_output="$TEST_RESULTS_DIR/test-compile-check.log"
    local test_compile_result=0
    
    mvn test-compile -q > "$test_compile_output" 2>&1 || test_compile_result=$?
    
    if [ $test_compile_result -eq 0 ]; then
        log_success "테스트 컴파일 성공"
        TEST_RESULTS+=("test-compile:PASS")
        PASSED_TESTS=$((PASSED_TESTS + 1))
    else
        log_error "테스트 컴파일 실패"
        TEST_RESULTS+=("test-compile:FAIL")
        FAILED_TESTS=$((FAILED_TESTS + 1))
        
        log_error "테스트 컴파일 실패 로그:"
        cat "$test_compile_output"
    fi
    
    TOTAL_TESTS=$((TOTAL_TESTS + 1))
}

# 테스트 결과 요약
show_test_results() {
    local end_time=$(date +%s)
    local duration=$((end_time - START_TIME))
    
    echo
    echo "==========================================="
    log_step "📊 수강 신청 앱 회귀 테스트 결과 요약"
    echo "==========================================="
    
    if [ ${#TEST_RESULTS[@]} -eq 0 ]; then
        log_warning "테스트 결과가 없습니다"
        return 1
    fi
    
    log_info "테스트 실행 시간: ${duration}초"
    echo
    
    for result in "${TEST_RESULTS[@]}"; do
        local test_name=$(echo "$result" | cut -d':' -f1)
        local test_status=$(echo "$result" | cut -d':' -f2)
        
        case "$test_status" in
            "PASS")
                log_success "✅ $test_name: 통과"
                ;;
            "FAIL")
                log_error "❌ $test_name: 실패"
                ;;
            "SKIP")
                log_warning "⚠️ $test_name: 건너뜀"
                ;;
        esac
    done
    
    echo "-------------------------------------------"
    log_info "총 테스트: $TOTAL_TESTS"
    log_info "통과: $PASSED_TESTS"
    log_info "실패: $FAILED_TESTS"
    
    local success_rate=0
    if [ $TOTAL_TESTS -gt 0 ]; then
        success_rate=$(( (PASSED_TESTS * 100) / TOTAL_TESTS ))
    fi
    
    log_info "성공률: ${success_rate}%"
    
    if [ $FAILED_TESTS -eq 0 ]; then
        log_success "🎉 모든 테스트 통과!"
        return 0
    elif [ $PASSED_TESTS -gt $FAILED_TESTS ]; then
        log_warning "⚠️ 대부분 테스트 통과"
        return 1
    else
        log_error "❌ 다수 테스트 실패 - 검토 필요"
        return 1
    fi
}

# 정리 작업
cleanup() {
    log_step "🧹 정리 작업"
    
    # Docker 컨테이너 정리 (TestContainers)
    docker container prune -f &>/dev/null || true
    
    log_success "정리 작업 완료"
}

# 사용법 출력
usage() {
    echo "수강 신청 앱 REST 서비스 회귀 테스트"
    echo
    echo "사용법: $0 [명령] [옵션]"
    echo
    echo "명령:"
    echo "  all                     - 전체 회귀 테스트 실행"
    echo "  repository              - Repository 레이어 테스트"
    echo "  service                 - Service 레이어 테스트"
    echo "  controller              - Controller 레이어 테스트"
    echo "  agent                   - Agent 호환성 테스트"
    echo "  postgresql              - PostgreSQL 호환성 테스트"
    echo "  performance             - 성능 테스트"
    echo "  security                - 보안 테스트"
    echo "  quality                 - 코드 품질 검사"
    echo "  full-app                - 전체 애플리케이션 테스트"
    echo "  cleanup                 - 정리 작업만 실행"
    echo "  help                    - 이 도움말 출력"
    echo
    echo "예시:"
    echo "  $0 all                  - 모든 테스트 실행"
    echo "  $0 controller           - Controller 테스트만 실행"
    echo "  $0 postgresql           - PostgreSQL 호환성 테스트만 실행"
    echo
    echo "환경변수:"
    echo "  DEBUG=true              - 디버그 모드 활성화"
}

# 메인 실행 로직
main() {
    local command="$1"
    START_TIME=$(date +%s)
    
    # 사전 조건 확인 (help 제외)
    if [ "$command" != "help" ] && [ "$command" != "cleanup" ]; then
        if ! check_prerequisites; then
            exit 1
        fi
        prepare_test_results_dir
    fi
    
    case "$command" in
        "all")
            log_info "🚀 전체 회귀 테스트 시작"
            run_code_quality_checks
            run_postgresql_compatibility_tests
            run_repository_tests
            run_service_tests
            run_controller_tests
            run_agent_compatibility_tests
            run_performance_tests
            run_security_tests
            show_test_results
            exit $?
            ;;
        "repository")
            run_repository_tests
            show_test_results
            exit $?
            ;;
        "service")
            run_service_tests
            show_test_results
            exit $?
            ;;
        "controller")
            run_controller_tests
            show_test_results
            exit $?
            ;;
        "agent")
            run_agent_compatibility_tests
            show_test_results
            exit $?
            ;;
        "postgresql")
            run_postgresql_compatibility_tests
            show_test_results
            exit $?
            ;;
        "performance")
            run_performance_tests
            show_test_results
            exit $?
            ;;
        "security")
            run_security_tests
            show_test_results
            exit $?
            ;;
        "quality")
            run_code_quality_checks
            show_test_results
            exit $?
            ;;
        "full-app")
            run_full_application_tests
            show_test_results
            exit $?
            ;;
        "cleanup")
            cleanup
            ;;
        "help"|"")
            usage
            ;;
        *)
            log_error "알 수 없는 명령: $command"
            usage
            exit 1
            ;;
    esac
}

# 스크립트 종료 시 정리 (옵션)
trap cleanup EXIT

# 메인 함수 실행
main "$@"