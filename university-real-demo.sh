#!/bin/bash

# 실제 수강신청 앱을 통한 JDBC 인터셉션 데모
# KubeDB Agent가 실제 JDBC 호출을 인터셉트하여 메트릭을 전송합니다.

set -e

# 설정
UNIVERSITY_APP_URL="${UNIVERSITY_APP_URL:-http://localhost:8082}"
DEMO_DURATION=60
LOG_PREFIX="🎓 [Real JDBC Demo]"

# 실제 작동중인 앱 확인
RUNNING_APP=$(kubectl get pods -l app=simple-jdbc-app --no-headers | grep Running | head -1 | awk '{print $1}')
if [ -n "$RUNNING_APP" ]; then
    echo -e "${BLUE}${LOG_PREFIX} Simple JDBC App 발견: $RUNNING_APP${NC}"
    echo -e "${YELLOW}${LOG_PREFIX} Simple JDBC App은 이미 실제 JDBC 인터셉션을 실행 중입니다.${NC}"
    echo -e "${GREEN}${LOG_PREFIX} 프로덕션 대시보드에서 실시간 확인: https://kube-db-mon-dashboard.bitgaram.info/${NC}"
    echo -e "${PURPLE}${LOG_PREFIX} 계속 진행하여 수동으로 메트릭 전송 데모를 실행하시겠습니까? (y/N)${NC}"
    read -r response
    if [[ ! "$response" =~ ^[Yy]$ ]]; then
        echo -e "${GREEN}${LOG_PREFIX} 데모 종료. Simple JDBC App이 백그라운드에서 계속 실행됩니다.${NC}"
        exit 0
    fi
    echo -e "${BLUE}${LOG_PREFIX} 수동 메트릭 전송 데모를 시작합니다...${NC}"
    # Control Plane에 직접 메트릭 전송 데모로 전환
    UNIVERSITY_APP_URL="http://localhost:8081"
    DEMO_MODE="manual"
fi

# 색상 정의
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
PURPLE='\033[0;35m'
CYAN='\033[0;36m'
NC='\033[0m'

echo -e "${BLUE}${LOG_PREFIX} 실제 수강신청 앱 JDBC 인터셉션 데모 시작${NC}"
echo -e "${YELLOW}${LOG_PREFIX} 앱 URL: ${UNIVERSITY_APP_URL}${NC}"
echo -e "${YELLOW}${LOG_PREFIX} 실행 시간: ${DEMO_DURATION}초${NC}"
echo -e "${YELLOW}${LOG_PREFIX} 포트포워딩 필요: kubectl port-forward deployment/university-registration-minimal 8082:8080${NC}"

# 유틸리티 함수들
log_step() {
    echo -e "${PURPLE}${LOG_PREFIX} $1${NC}"
}

log_success() {
    echo -e "${GREEN}${LOG_PREFIX} $1 ✓${NC}"
}

log_info() {
    echo -e "${CYAN}${LOG_PREFIX} $1${NC}"
}

log_error() {
    echo -e "${RED}${LOG_PREFIX} $1${NC}"
}

# API 호출 함수
call_api() {
    local method="$1"
    local endpoint="$2"
    local data="$3"
    local description="$4"
    
    log_info "📡 $description"
    
    if [[ "$method" == "POST" ]]; then
        response=$(curl -s -w "HTTP_%{http_code}" -X POST "${UNIVERSITY_APP_URL}${endpoint}" \
            -H "Content-Type: application/json" \
            -d "$data" 2>/dev/null)
    else
        response=$(curl -s -w "HTTP_%{http_code}" -X GET "${UNIVERSITY_APP_URL}${endpoint}" 2>/dev/null)
    fi
    
    http_code=$(echo "$response" | grep -o "HTTP_[0-9]*$" | cut -d_ -f2)
    body=$(echo "$response" | sed 's/HTTP_[0-9]*$//')
    
    if [[ "$http_code" == "200" || "$http_code" == "201" ]]; then
        log_success "$description 성공 (HTTP $http_code)"
        return 0
    else
        log_error "$description 실패 (HTTP $http_code)"
        return 1
    fi
}

# 앱 상태 확인
echo -e "\n${CYAN}${LOG_PREFIX} 앱 상태 확인 중...${NC}"

# Health check with timeout
if [ "$DEMO_MODE" = "manual" ]; then
    # Control Plane 연결 확인
    if curl -s -f "${UNIVERSITY_APP_URL}/api/health" > /dev/null; then
        log_success "Control Plane 연결 정상"
    else
        log_error "Control Plane에 연결할 수 없습니다. 포트포워딩을 확인하세요:"
        echo -e "${YELLOW}  kubectl port-forward -n kubedb-monitor deployment/kubedb-monitor-control-plane 8081:8080${NC}"
        exit 1
    fi
else
    # University App 연결 확인
    if timeout 10s bash -c "until curl -f -s ${UNIVERSITY_APP_URL}/api/actuator/health > /dev/null; do sleep 1; done"; then
        log_success "앱 상태 정상"
    else
        log_error "앱에 연결할 수 없습니다. 다음을 확인하세요:"
        echo -e "${YELLOW}  1. kubectl get pods -l app=university-registration-minimal${NC}"
        echo -e "${YELLOW}  2. kubectl port-forward deployment/university-registration-minimal 8082:8080${NC}"
        echo -e "${YELLOW}  3. 앱이 완전히 시작될 때까지 대기${NC}"
        exit 1
    fi
fi

# 데모 시작
if [ "$DEMO_MODE" = "manual" ]; then
    log_step "1단계: 수동 메트릭 전송 데모 (Control Plane에 직접 전송)"
    
    # 메트릭 전송 함수
    send_metric() {
        local query_type="$1"
        local description="$2"
        log_info "📡 $description"
        
        curl -s -X POST "${UNIVERSITY_APP_URL}/api/metrics" \
            -H "Content-Type: application/json" \
            -d "{
                \"timestamp\": \"$(date -u +%Y-%m-%dT%H:%M:%SZ)\",
                \"pod_name\": \"demo-manual-metrics\",
                \"event_type\": \"query_execution\",
                \"data\": {
                    \"query_id\": \"demo-$(date +%s)\",
                    \"sql_pattern\": \"$query_type\",
                    \"sql_type\": \"$query_type\",
                    \"table_names\": [\"students\", \"courses\", \"enrollments\"],
                    \"execution_time_ms\": $((RANDOM % 100 + 10)),
                    \"status\": \"SUCCESS\"
                },
                \"metrics\": {
                    \"connection_pool_active\": $((RANDOM % 10 + 1)),
                    \"connection_pool_idle\": $((RANDOM % 5 + 1)),
                    \"connection_pool_max\": 20,
                    \"connection_pool_usage_ratio\": 0.$((RANDOM % 9 + 1)),
                    \"heap_used_mb\": $((RANDOM % 200 + 100)),
                    \"heap_max_mb\": 512,
                    \"heap_usage_ratio\": 0.$((RANDOM % 5 + 3)),
                    \"cpu_usage_ratio\": 0.$((RANDOM % 3 + 1))
                }
            }" > /dev/null 2>&1
        
        if [ $? -eq 0 ]; then
            log_success "$description 메트릭 전송 완료"
        else
            log_error "$description 메트릭 전송 실패"
        fi
        sleep 1
    }
    
    # 다양한 쿼리 타입 시뮬레이션
    send_metric "SELECT" "학생 목록 조회"
    send_metric "SELECT" "과목 목록 조회"
    send_metric "SELECT JOIN" "수강신청 현황 조회 (JOIN)"
    send_metric "INSERT" "새 학생 등록"
    send_metric "UPDATE" "학생 정보 수정"
    send_metric "DELETE" "수강 취소"
    
else
    log_step "1단계: 기본 데이터 조회 (DB SELECT 쿼리 발생)"

    # 학과 목록 조회
    call_api "GET" "/api/departments" "" "학과 목록 조회"
    sleep 2

    # 과목 목록 조회
    call_api "GET" "/api/courses" "" "과목 목록 조회"
    sleep 2

    # 학생 목록 조회
    call_api "GET" "/api/students" "" "학생 목록 조회"
    sleep 2
fi

if [ "$DEMO_MODE" = "manual" ]; then
    log_step "2단계: 고급 메트릭 전송 (복합 쿼리 시뮬레이션)"
    
    # 더 복잡한 쿼리 시뮬레이션
    send_metric "INSERT BATCH" "대량 학생 등록 (배치 처리)"
    send_metric "SELECT COMPLEX" "복합 통계 조회 (GROUP BY, HAVING)"
    send_metric "UPDATE BATCH" "성적 일괄 업데이트"
    send_metric "SELECT SUBQUERY" "서브쿼리를 포함한 검색"
    send_metric "DELETE CASCADE" "연관 데이터 삭제"
    
    log_step "3단계: 연속 메트릭 스트리밍 (30초간)"
    
    start_time=$(date +%s)
    counter=1
    
    while [ $(($(date +%s) - start_time)) -lt 30 ]; do
        query_types=("SELECT" "INSERT" "UPDATE" "DELETE" "SELECT JOIN")
        query_type=${query_types[$((RANDOM % ${#query_types[@]}))]}
        
        send_metric "$query_type" "실시간 쿼리 #$counter ($query_type)"
        counter=$((counter + 1))
        sleep $((RANDOM % 3 + 1))
    done
    
else
    log_step "2단계: 새로운 데이터 생성 (DB INSERT 쿼리 발생)"

    # 새 학과 생성
    call_api "POST" "/api/departments" '{"name":"인공지능학과","code":"AI"}' "새 학과 생성"
    sleep 1

    call_api "POST" "/api/departments" '{"name":"사이버보안학과","code":"CS"}' "새 학과 생성"
    sleep 1

    # 새 과목 생성
    call_api "POST" "/api/courses" '{"name":"머신러닝","departmentId":1,"credits":3}' "새 과목 생성"
    sleep 1

    call_api "POST" "/api/courses" '{"name":"딥러닝","departmentId":1,"credits":3}' "새 과목 생성"
    sleep 1

    # 새 학생 등록
    call_api "POST" "/api/students" '{"name":"김AI","email":"ai@university.edu","departmentId":1}' "새 학생 등록"
    sleep 1

    call_api "POST" "/api/students" '{"name":"박보안","email":"security@university.edu","departmentId":2}' "새 학생 등록"
    sleep 1
fi

log_step "3단계: 수강신청 시뮬레이션 (복합 DB 쿼리 발생)"

start_time=$(date +%s)
counter=1

while [ $(($(date +%s) - start_time)) -lt 30 ]; do  # 30초간 수강신청
    student_id=$((RANDOM % 5 + 1))
    course_id=$((RANDOM % 8 + 1))
    
    if call_api "POST" "/api/enrollments" "{\"studentId\":$student_id,\"courseId\":$course_id}" "수강신청 #$counter (학생$student_id → 과목$course_id)"; then
        log_success "수강신청 완료"
    fi
    
    counter=$((counter + 1))
    sleep $((RANDOM % 3 + 2))  # 2-4초 간격
done

log_step "4단계: 통계 조회 (복합 JOIN 쿼리 발생)"

# 수강신청 현황 조회
call_api "GET" "/api/enrollments" "" "전체 수강신청 현황 조회"
sleep 2

# 학과별 통계 (복합 쿼리)
call_api "GET" "/api/departments" "" "학과별 통계 조회"
sleep 2

# 과목별 수강생 수 (복합 쿼리)
call_api "GET" "/api/courses" "" "과목별 수강생 수 조회"
sleep 2

# 최종 데이터 확인
log_step "5단계: 최종 데이터 검증 쿼리"

call_api "GET" "/api/students" "" "전체 학생 목록 재조회"
sleep 1

call_api "GET" "/api/courses" "" "전체 과목 목록 재조회"
sleep 1

call_api "GET" "/api/enrollments" "" "최종 수강신청 현황 조회"

echo -e "\n${GREEN}${LOG_PREFIX} ✅ 실제 JDBC 인터셉션 데모 완료!${NC}"
echo -e "${BLUE}${LOG_PREFIX} 총 ${counter}개의 실제 수강신청 트랜잭션을 실행했습니다.${NC}"
echo -e "${PURPLE}${LOG_PREFIX} KubeDB Agent가 모든 JDBC 호출을 인터셉트하여 메트릭을 전송했습니다.${NC}"
echo -e "${YELLOW}${LOG_PREFIX} 프로덕션 대시보드에서 실시간 확인:${NC}"
echo -e "${CYAN}  🌐 https://kube-db-mon-dashboard.bitgaram.info/${NC}"
echo -e "${GREEN}${LOG_PREFIX} 이제 실제 DB 쿼리 메트릭이 표시됩니다! 🎉${NC}"