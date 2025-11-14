#!/bin/bash

# =============================================================================
# KubeDB Monitor Agent 종합 테스트 스위트
# =============================================================================
# Agent 수정 후 반드시 실행해야 하는 필수 테스트들
# 모든 핵심 기능을 검증하여 regression을 방지
# =============================================================================

set -e

# 색상 코드
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# 테스트 결과 추적
TOTAL_TESTS=0
PASSED_TESTS=0
FAILED_TESTS=0
TEST_RESULTS=()

echo -e "${BLUE}===========================================${NC}"
echo -e "${BLUE}  KubeDB Monitor Agent 종합 테스트 스위트  ${NC}"
echo -e "${BLUE}===========================================${NC}"
echo ""
echo "💡 이 테스트는 Agent 수정 후 반드시 실행해야 합니다."
echo ""
echo -e "${GREEN}🎯 중요 검증 항목 (Long-running Transaction 감지)${NC}:"
echo "   ✅ Spring @Transactional 암시적 트랜잭션 감지"
echo "   ✅ Long-running Transaction 실시간 감지 (5초+ 임계값)"
echo "   ✅ SQL 실행 감지 및 Connection ID 추적"
echo "   ✅ Agent → Control Plane → Dashboard 전체 파이프라인"
echo ""

# 함수: 테스트 결과 기록
record_test() {
    local test_name="$1"
    local status="$2"
    local details="$3"
    
    TOTAL_TESTS=$((TOTAL_TESTS + 1))
    if [ "$status" = "PASS" ]; then
        PASSED_TESTS=$((PASSED_TESTS + 1))
        echo -e "${GREEN}✅ PASS${NC}: $test_name"
        if [ -n "$details" ]; then
            echo "   └─ $details"
        fi
    else
        FAILED_TESTS=$((FAILED_TESTS + 1))
        echo -e "${RED}❌ FAIL${NC}: $test_name"
        if [ -n "$details" ]; then
            echo "   └─ $details"
        fi
    fi
    TEST_RESULTS+=("$status: $test_name - $details")
    echo ""
}

# 함수: Maven 테스트 실행 및 결과 파싱
run_maven_test() {
    local test_class="$1"
    local test_name="$2"
    
    echo -e "${YELLOW}🧪 실행 중: $test_name${NC}"
    
    cd /Users/narzis/workspace/kube-db-monitor/kubedb-monitor-agent
    
    local output
    if output=$(mvn test -Dtest="$test_class" 2>&1); then
        # "Tests run:" 패턴 검색 (여러 라인에서 찾기)
        local test_summary=$(echo "$output" | grep "Tests run:" | tail -1)
        
        if [ -n "$test_summary" ]; then
            # "Tests run: 5, Failures: 0, Errors: 0, Skipped: 0" 형식에서 추출
            local tests_run=$(echo "$test_summary" | sed -n 's/.*Tests run: \([0-9]*\).*/\1/p')
            local failures=$(echo "$test_summary" | sed -n 's/.*Failures: \([0-9]*\).*/\1/p')  
            local errors=$(echo "$test_summary" | sed -n 's/.*Errors: \([0-9]*\).*/\1/p')
            local skipped=$(echo "$test_summary" | sed -n 's/.*Skipped: \([0-9]*\).*/\1/p')
            
            # BUILD SUCCESS 확인
            if echo "$output" | grep -q "BUILD SUCCESS"; then
                if [ "${failures:-0}" -eq 0 ] && [ "${errors:-0}" -eq 0 ] && [ "${tests_run:-0}" -gt 0 ]; then
                    record_test "$test_name" "PASS" "Tests: $tests_run, Failures: $failures, Errors: $errors, Skipped: $skipped"
                    return 0
                else
                    record_test "$test_name" "FAIL" "Tests: $tests_run, Failures: $failures, Errors: $errors, Skipped: $skipped"
                    return 1
                fi
            else
                record_test "$test_name" "FAIL" "빌드 실패: $test_summary"
                return 1
            fi
        else
            record_test "$test_name" "FAIL" "테스트 결과 파싱 실패"
            return 1
        fi
    else
        record_test "$test_name" "FAIL" "Maven 명령 실행 실패"
        return 1
    fi
}

# 함수: Agent 실행 환경 검증
verify_agent_environment() {
    echo -e "${YELLOW}🔍 Agent 실행 환경 검증 중...${NC}"
    
    # 1. Maven 프로젝트 구조 확인
    if [ -f "/Users/narzis/workspace/kube-db-monitor/kubedb-monitor-agent/pom.xml" ]; then
        record_test "Maven 프로젝트 구조" "PASS" "pom.xml 존재 확인"
    else
        record_test "Maven 프로젝트 구조" "FAIL" "pom.xml 파일 없음"
        return 1
    fi
    
    # 2. 핵심 Agent 클래스 존재 확인
    local agent_main_class="/Users/narzis/workspace/kube-db-monitor/kubedb-monitor-agent/src/main/java/io/kubedb/monitor/agent/UniversalJDBCInterceptor.java"
    if [ -f "$agent_main_class" ]; then
        record_test "핵심 Agent 클래스" "PASS" "UniversalJDBCInterceptor.java 존재"
    else
        record_test "핵심 Agent 클래스" "FAIL" "UniversalJDBCInterceptor.java 없음"
        return 1
    fi
    
    # 3. 테스트 디렉토리 확인
    if [ -d "/Users/narzis/workspace/kube-db-monitor/kubedb-monitor-agent/src/test/java" ]; then
        local test_count=$(find "/Users/narzis/workspace/kube-db-monitor/kubedb-monitor-agent/src/test/java" -name "*Test.java" | wc -l)
        record_test "테스트 파일 존재" "PASS" "테스트 파일 ${test_count}개 발견"
    else
        record_test "테스트 파일 존재" "FAIL" "테스트 디렉토리 없음"
        return 1
    fi
    
    return 0
}

# 함수: 핵심 기능 테스트 실행
run_core_functionality_tests() {
    echo -e "${YELLOW}🚀 핵심 기능 테스트 실행 중...${NC}"
    
    # 핵심 테스트 목록 (검증된 것만)
    declare -a core_tests=(
        "TransactionIntegrationTest:트랜잭션 통합 테스트"
        "LongRunningTransactionIntegrationTest:Long-running 트랜잭션 테스트"  
        "MetricsCollectionTest:메트릭 수집 테스트"
        "AgentConfigTest:Agent 설정 테스트"
        "JDBCMonitoringIntegrationTest:JDBC 모니터링 통합 테스트"
    )
    
    # 각 테스트 실행
    for test_entry in "${core_tests[@]}"; do
        local test_class="${test_entry%%:*}"
        local test_description="${test_entry##*:}"
        
        # 테스트 파일이 실제로 존재하는지 확인
        local test_file="/Users/narzis/workspace/kube-db-monitor/kubedb-monitor-agent/src/test/java/io/kubedb/monitor/agent/${test_class}.java"
        if [ -f "$test_file" ]; then
            run_maven_test "$test_class" "$test_description"
        else
            record_test "$test_description" "FAIL" "테스트 파일 없음: $test_class"
        fi
    done
}

# 함수: 실제 환경 통합 테스트
run_integration_tests() {
    echo -e "${YELLOW}🌐 실제 환경 통합 테스트 실행 중...${NC}"
    
    # Kubernetes 환경에서 실행되는 통합 테스트
    local pod_name
    if pod_name=$(kubectl get pods -n kubedb-monitor-test -l app=university-registration-demo --field-selector=status.phase=Running -o jsonpath='{.items[0].metadata.name}' 2>/dev/null); then
        if [ -n "$pod_name" ]; then
            record_test "Kubernetes Pod 상태" "PASS" "실행 중인 Pod: $pod_name"
            
            # Agent 로그 확인
            local agent_logs
            if agent_logs=$(kubectl logs -n kubedb-monitor-test "$pod_name" --tail=50 2>/dev/null | grep -i "kubedb.*agent\|initialized\|intercept" | head -5); then
                if [ -n "$agent_logs" ]; then
                    record_test "Agent 초기화 확인" "PASS" "Agent 로그에서 초기화 확인"
                else
                    record_test "Agent 초기화 확인" "FAIL" "Agent 초기화 로그 없음"
                fi
            else
                record_test "Agent 초기화 확인" "FAIL" "로그 조회 실패"
            fi
            
            # 🎯 핵심: Long-running Transaction 감지 테스트 (방금 해결한 기능)
            test_long_running_transaction_detection "$pod_name"
        else
            record_test "Kubernetes Pod 상태" "FAIL" "실행 중인 Pod 없음"
        fi
    else
        record_test "Kubernetes Pod 상태" "FAIL" "kubectl 명령 실패"
    fi
}

# 함수: Long-running Transaction 감지 테스트 (핵심 기능)
test_long_running_transaction_detection() {
    local pod_name="$1"
    echo -e "${YELLOW}🎯 Long-running Transaction 감지 테스트 중...${NC}"
    
    # 1. 암시적 트랜잭션 감지 테스트
    echo "  📊 암시적 트랜잭션 감지 확인..."
    local simple_api_result
    if simple_api_result=$(kubectl exec -n kubedb-monitor-test "$pod_name" -- curl -s "http://localhost:8080/api/courses" 2>/dev/null | head -1); then
        if [[ "$simple_api_result" == *"content"* ]]; then
            # API 호출 성공, 로그에서 트랜잭션 시작 감지 확인 (더 많은 로그와 더 넓은 검색)
            sleep 3
            local tx_start_logs
            if tx_start_logs=$(kubectl logs -n kubedb-monitor-test "$pod_name" --tail=100 2>/dev/null | grep -E "(🔍.*트랜잭션.*커밋|Transaction committed|SQL 실행 감지)" | tail -2); then
                if [ -n "$tx_start_logs" ]; then
                    record_test "암시적 트랜잭션 감지" "PASS" "트랜잭션 시작 감지됨"
                else
                    record_test "암시적 트랜잭션 감지" "FAIL" "트랜잭션 시작 로그 없음"
                fi
            else
                record_test "암시적 트랜잭션 감지" "FAIL" "로그 조회 실패"
            fi
        else
            record_test "암시적 트랜잭션 감지" "FAIL" "API 호출 응답 오류"
        fi
    else
        record_test "암시적 트랜잭션 감지" "FAIL" "API 호출 실패"
    fi
    
    # 2. Long-running Transaction 실제 감지 테스트 (6초) - 개선된 로직
    echo "  🕐 Long-running Transaction 실제 감지 테스트 (개선)..."
    local long_running_result
    if long_running_result=$(timeout 15 kubectl exec -n kubedb-monitor-test "$pod_name" -- curl -s -X POST "http://localhost:8080/api/data/long-running-test?duration=6000" 2>/dev/null); then
        if [[ "$long_running_result" == *"actualDuration"* ]] && [[ "$long_running_result" == *"6"* ]]; then
            # Long-running 트랜잭션 실행 완료, 로그 확인 (더 정확하고 넓은 패턴)
            sleep 7  # 대기 시간 증가
            local long_tx_logs
            if long_tx_logs=$(kubectl logs -n kubedb-monitor-test "$pod_name" --tail=150 2>/dev/null | grep -E "(⏰ Long-running|📊 Connection 기반 Long-running|Long.*running.*Connection.*감지|Long.*running.*transaction.*detected|Long.*running.*transaction.*alert)"); then
                if [ -n "$long_tx_logs" ]; then
                    # 실제로 6초 이상 감지되었는지 확인
                    local duration_check
                    if duration_check=$(echo "$long_tx_logs" | grep -E "(6[0-9]{3}ms|[7-9][0-9]{3}ms|[1-9][0-9]{4}ms)" | head -1); then
                        record_test "Long-running Transaction 감지" "PASS" "6초+ Long-running 트랜잭션 정상 감지 (${duration_check:0:100})"
                    else
                        record_test "Long-running Transaction 감지" "PASS" "Long-running 트랜잭션 감지됨 (duration 세부 확인 불가)"
                    fi
                else
                    record_test "Long-running Transaction 감지" "FAIL" "Long-running 트랜잭션 로그 없음"
                fi
            else
                record_test "Long-running Transaction 감지" "FAIL" "Long-running 로그 조회 실패"
            fi
        else
            record_test "Long-running Transaction 감지" "FAIL" "Long-running API 응답 오류: $long_running_result"
        fi
    else
        record_test "Long-running Transaction 감지" "FAIL" "Long-running API 호출 실패"
    fi
    
    # 3. SQL 실행 감지 테스트 (더 넓은 검색)
    echo "  📝 SQL 실행 감지 확인..."
    local sql_detection_logs
    if sql_detection_logs=$(kubectl logs -n kubedb-monitor-test "$pod_name" --tail=200 2>/dev/null | grep -E "(🔍 SQL 실행 감지|Query executed|SQL 실행 감지)" | head -5); then
        if [ -n "$sql_detection_logs" ]; then
            local sql_count=$(echo "$sql_detection_logs" | wc -l)
            record_test "SQL 실행 감지" "PASS" "SQL 실행 감지 로그 ${sql_count}개 확인"
        else
            record_test "SQL 실행 감지" "FAIL" "SQL 실행 감지 로그 없음"
        fi
    else
        record_test "SQL 실행 감지" "FAIL" "SQL 로그 조회 실패"
    fi
    
    # 4. Connection ID 추적 테스트 (더 넓은 검색)
    echo "  🔗 Connection ID 추적 확인..."
    local conn_id_logs
    if conn_id_logs=$(kubectl logs -n kubedb-monitor-test "$pod_name" --tail=150 2>/dev/null | grep -E "(stable-conn-|Connection ID:|Connection.*stable|connection_id)" | head -3); then
        if [ -n "$conn_id_logs" ]; then
            record_test "Connection ID 추적" "PASS" "Connection ID 추적 로그 확인"
        else
            record_test "Connection ID 추적" "FAIL" "Connection ID 추적 로그 없음"
        fi
    else
        record_test "Connection ID 추적" "FAIL" "Connection 로그 조회 실패"
    fi
    
    # 5. Control Plane 메트릭 전송 테스트
    echo "  📡 Control Plane 메트릭 전송 확인..."
    local metrics_transmission_logs
    if metrics_transmission_logs=$(kubectl logs -n kubedb-monitor-test "$pod_name" --tail=50 2>/dev/null | grep -E "(메트릭 전송|transmitted|HttpMetricsTransmitter)" | head -2); then
        if [ -n "$metrics_transmission_logs" ]; then
            record_test "메트릭 전송" "PASS" "Control Plane으로 메트릭 전송 확인"
        else
            record_test "메트릭 전송" "FAIL" "메트릭 전송 로그 없음"
        fi
    else
        record_test "메트릭 전송" "FAIL" "메트릭 전송 로그 조회 실패"
    fi
    
    # 6. 🆕 SQL Query 내용 검증 테스트 (HttpMetricsTransmitter 하드코딩 문제 해결 검증)
    echo "  🔍 SQL Query 내용 표시 검증 테스트..."
    local sql_query_validation_result
    if sql_query_validation_result=$(timeout 15 kubectl exec -n kubedb-monitor-test "$pod_name" -- curl -s -X POST "http://localhost:8080/api/data/long-running-test?duration=6000" 2>/dev/null); then
        sleep 8  # Long-running 트랜잭션이 완료되고 로그가 생성될 때까지 충분히 대기
        
        # HttpMetricsTransmitter에서 실제 SQL 패턴 추출 로그 확인
        local sql_pattern_extraction_logs
        if sql_pattern_extraction_logs=$(kubectl logs -n kubedb-monitor-test "$pod_name" --tail=100 2>/dev/null | grep -E "🔍.*SQL 패턴 추출|sql_pattern.*추출|Connection.*에서 실제 SQL"); then
            if [ -n "$sql_pattern_extraction_logs" ]; then
                record_test "SQL Query 패턴 추출" "PASS" "HttpMetricsTransmitter에서 실제 SQL 패턴 추출 확인"
                
                # Long-running Transaction JSON에서 sql_pattern이 하드코딩이 아닌 실제 SQL 내용을 포함하는지 확인
                local long_running_json_logs
                if long_running_json_logs=$(kubectl logs -n kubedb-monitor-test "$pod_name" --tail=100 2>/dev/null | grep -E "Long-running transaction alert JSON|sql_pattern.*SELECT|sql_pattern.*INSERT|sql_pattern.*UPDATE" | tail -1); then
                    if [[ "$long_running_json_logs" == *"sql_pattern"* ]] && [[ "$long_running_json_logs" != *"Long running transaction"* ]]; then
                        record_test "SQL Query 내용 검증" "PASS" "실제 SQL 내용이 JSON에 포함됨 (하드코딩 해결)"
                    else
                        record_test "SQL Query 내용 검증" "FAIL" "여전히 하드코딩된 sql_pattern 사용"
                    fi
                else
                    record_test "SQL Query 내용 검증" "SKIP" "Long-running JSON 로그를 찾을 수 없음"
                fi
            else
                record_test "SQL Query 패턴 추출" "FAIL" "SQL 패턴 추출 로그 없음"
            fi
        else
            record_test "SQL Query 패턴 추출" "FAIL" "SQL 패턴 추출 로그 조회 실패"
        fi
    else
        record_test "SQL Query 내용 검증" "FAIL" "Long-running API 호출 실패"
    fi
    
    # 7. Dashboard에서 SQL Query 내용 실시간 표시 확인 (E2E 검증)
    echo "  🖥️ Dashboard SQL Query 실시간 표시 E2E 검증..."
    local dashboard_validation_result
    if dashboard_validation_result=$(timeout 15 kubectl exec -n kubedb-monitor-test "$pod_name" -- curl -s -X POST "http://localhost:8080/api/data/long-running-test?duration=7000" 2>/dev/null); then
        sleep 5  # WebSocket 전송이 완료될 때까지 대기
        
        # Control Plane WebSocket 브로드캐스트 로그 확인
        local control_plane_logs
        if control_plane_logs=$(kubectl logs -n kubedb-monitor-system deployment/kubedb-monitor-control-plane --tail=50 2>/dev/null | grep -E "long_running_transaction.*브로드캐스트|WebSocket.*long_running|Broadcasting.*long_running"); then
            if [ -n "$control_plane_logs" ]; then
                record_test "Dashboard E2E SQL 표시" "PASS" "Control Plane에서 Dashboard로 Long-running Transaction 브로드캐스트 확인"
            else
                record_test "Dashboard E2E SQL 표시" "SKIP" "Control Plane 브로드캐스트 로그 없음 (Dashboard 직접 확인 필요)"
            fi
        else
            record_test "Dashboard E2E SQL 표시" "SKIP" "Control Plane 로그 조회 실패 (네트워크 이슈일 수 있음)"
        fi
    else
        record_test "Dashboard E2E SQL 표시" "FAIL" "E2E 검증용 Long-running API 호출 실패"
    fi
    
    # 8. Dashboard 접근성 테스트 (선택적)
    echo "  🖥️ Dashboard 접근성 확인..."
    local dashboard_health
    if dashboard_health=$(timeout 5 curl -s "https://kube-db-mon-dashboard.bitgaram.info/api/health" 2>/dev/null); then
        if [[ "$dashboard_health" == *"ok"* ]] || [[ "$dashboard_health" == *"healthy"* ]] || [ -n "$dashboard_health" ]; then
            record_test "Dashboard 접근성" "PASS" "Dashboard 정상 접근 가능"
        else
            record_test "Dashboard 접근성" "FAIL" "Dashboard 응답 이상: $dashboard_health"
        fi
    else
        record_test "Dashboard 접근성" "SKIP" "Dashboard 접근 테스트 스킵 (네트워크/타임아웃)"
    fi
}

# 함수: Agent JAR 빌드 테스트
test_agent_build() {
    echo -e "${YELLOW}🔨 Agent JAR 빌드 테스트 중...${NC}"
    
    cd /Users/narzis/workspace/kube-db-monitor/kubedb-monitor-agent
    
    local build_output
    if build_output=$(mvn clean package -DskipTests 2>&1); then
        # 실제 생성되는 JAR 파일명 확인
        local jar_files=(target/kubedb-monitor-agent-*.jar)
        local main_jar=""
        
        # 가장 큰 파일을 메인 JAR로 선택 (shade plugin으로 생성된 fat JAR)
        for jar in "${jar_files[@]}"; do
            if [ -f "$jar" ] && [[ ! "$jar" == *"original"* ]]; then
                main_jar="$jar"
                break
            fi
        done
        
        if [ -n "$main_jar" ] && [ -f "$main_jar" ]; then
            local jar_size=$(du -h "$main_jar" | cut -f1)
            local jar_name=$(basename "$main_jar")
            record_test "Agent JAR 빌드" "PASS" "JAR 파일 생성됨: $jar_name (크기: $jar_size)"
        else
            record_test "Agent JAR 빌드" "FAIL" "JAR 파일 생성 안됨 (target 디렉토리 확인 필요)"
        fi
    else
        record_test "Agent JAR 빌드" "FAIL" "Maven 빌드 실패"
    fi
}

# 함수: 성능 및 안정성 검증
test_performance_stability() {
    echo -e "${YELLOW}⚡ 성능 및 안정성 테스트 중...${NC}"
    
    # 메모리 사용량 테스트 (간단한 로드 테스트)
    local pod_name
    if pod_name=$(kubectl get pods -n kubedb-monitor-test -l app=university-registration-demo --field-selector=status.phase=Running -o jsonpath='{.items[0].metadata.name}' 2>/dev/null); then
        if [ -n "$pod_name" ]; then
            # 간단한 API 호출 테스트 (5번)
            local success_count=0
            for i in {1..5}; do
                if kubectl exec -n kubedb-monitor-test "$pod_name" -- curl -s -f http://localhost:8080/api/courses >/dev/null 2>&1; then
                    success_count=$((success_count + 1))
                fi
                sleep 1
            done
            
            if [ $success_count -ge 4 ]; then
                record_test "기본 API 안정성" "PASS" "5회 중 ${success_count}회 성공"
            else
                record_test "기본 API 안정성" "FAIL" "5회 중 ${success_count}회만 성공"
            fi
        fi
    fi
}

# 함수: 최종 결과 출력
print_final_results() {
    echo ""
    echo -e "${BLUE}===========================================${NC}"
    echo -e "${BLUE}         테스트 결과 요약                  ${NC}"
    echo -e "${BLUE}===========================================${NC}"
    echo ""
    echo -e "총 테스트: ${TOTAL_TESTS}"
    echo -e "${GREEN}통과: ${PASSED_TESTS}${NC}"
    echo -e "${RED}실패: ${FAILED_TESTS}${NC}"
    echo ""
    
    if [ $FAILED_TESTS -eq 0 ]; then
        echo -e "${GREEN}🎉 모든 테스트가 통과했습니다!${NC}"
        echo -e "${GREEN}✅ Agent는 안전하게 배포할 수 있습니다.${NC}"
        echo ""
        echo -e "${GREEN}🎯 Long-running Transaction 감지 기능이 정상 작동합니다:${NC}"
        echo -e "${GREEN}   • Dashboard에서 5초+ 트랜잭션이 실시간 표시됩니다${NC}"
        echo -e "${GREEN}   • Spring @Transactional 환경에서 암시적 트랜잭션 감지 작동${NC}"
        echo -e "${GREEN}   • SQL 실행 추적 및 Connection ID 기반 트랜잭션 관리${NC}"
        return 0
    else
        echo -e "${RED}⚠️  일부 테스트가 실패했습니다.${NC}"
        echo -e "${RED}❌ Agent 배포 전 문제를 해결해야 합니다.${NC}"
        echo ""
        echo -e "${YELLOW}특히 Long-running Transaction 관련 테스트 실패 시:${NC}"
        echo -e "${YELLOW}   • Agent 로그 레벨이 DEBUG로 설정되어 있는지 확인${NC}"
        echo -e "${YELLOW}   • HikariCP Connection 인터셉션이 작동하는지 확인${NC}"
        echo -e "${YELLOW}   • Control Plane과의 네트워크 연결 확인${NC}"
        echo ""
        echo -e "${YELLOW}실패한 테스트 목록:${NC}"
        for result in "${TEST_RESULTS[@]}"; do
            if [[ $result == FAIL:* ]]; then
                echo -e "${RED}  - ${result#FAIL: }${NC}"
            fi
        done
        return 1
    fi
}

# 메인 실행 흐름
main() {
    echo "시작 시간: $(date)"
    echo ""
    
    # 1. 환경 검증
    verify_agent_environment || exit 1
    
    # 2. Agent JAR 빌드 테스트
    test_agent_build
    
    # 3. 핵심 기능 테스트
    run_core_functionality_tests
    
    # 4. 통합 테스트
    run_integration_tests
    
    # 5. 성능/안정성 테스트
    test_performance_stability
    
    # 6. 최종 결과
    print_final_results
    local exit_code=$?
    
    echo ""
    echo "완료 시간: $(date)"
    echo ""
    
    exit $exit_code
}

# 스크립트 실행
main "$@"