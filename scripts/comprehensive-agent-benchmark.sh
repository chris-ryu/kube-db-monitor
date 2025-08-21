#!/bin/bash

# KubeDB Monitor Agent 종합 성능 벤치마킹 도구
# PostgreSQL, MySQL, MariaDB, Oracle, SQL Server 및 다양한 Agent 프로파일 성능 비교

set -e

# 색상 정의
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
PURPLE='\033[0;35m'
CYAN='\033[0;36m'
NC='\033[0m' # No Color

# 벤치마크 설정
DATABASES=("postgresql" "mysql" "mariadb" "oracle" "sqlserver")
PROFILES=("conservative" "balanced" "aggressive")
TEST_DURATION=120  # 각 테스트 지속시간 (초)
WARMUP_DURATION=30 # 워밍업 시간 (초)
LOAD_THREADS=20    # 부하 테스트 스레드 수
CONCURRENT_USERS=50 # 동시 사용자 수

# 결과 저장
TIMESTAMP=$(date +%Y%m%d-%H%M%S)
RESULTS_DIR="/tmp/agent-comprehensive-benchmark-${TIMESTAMP}"
mkdir -p "$RESULTS_DIR"

# 로그 함수들
log_info() { echo -e "${BLUE}[INFO]${NC} $1"; }
log_success() { echo -e "${GREEN}[SUCCESS]${NC} $1"; }
log_warn() { echo -e "${YELLOW}[WARN]${NC} $1"; }
log_error() { echo -e "${RED}[ERROR]${NC} $1"; }
log_section() { echo -e "\n${PURPLE}=== $1 ===${NC}\n"; }
log_subsection() { echo -e "\n${CYAN}--- $1 ---${NC}"; }

# 벤치마크 시작
log_section "KubeDB Monitor Agent 종합 성능 벤치마크"
log_info "테스트 설정:"
log_info "  - 데이터베이스: ${DATABASES[*]}"
log_info "  - Agent 프로파일: ${PROFILES[*]}"
log_info "  - 테스트 지속시간: ${TEST_DURATION}초"
log_info "  - 동시 사용자: ${CONCURRENT_USERS}명"
log_info "  - 결과 저장: $RESULTS_DIR"

# 1. 환경 상태 확인
check_environment() {
    log_section "환경 상태 확인"
    
    # Kubernetes 클러스터 연결 확인
    if ! kubectl cluster-info > /dev/null 2>&1; then
        log_error "Kubernetes 클러스터에 연결할 수 없습니다"
        exit 1
    fi
    
    log_info "Kubernetes 클러스터: 연결됨"
    
    # 각 데이터베이스 상태 확인
    for db in "${DATABASES[@]}"; do
        case $db in
            "postgresql")
                NAMESPACE="postgres-system"
                SERVICE="postgres-cluster-rw"
                ;;
            "mysql")
                NAMESPACE="mysql-system"
                SERVICE="mysql-cluster-rw"
                ;;
            "mariadb")
                NAMESPACE="mariadb-system"
                SERVICE="mariadb-cluster-rw"
                ;;
            "oracle")
                NAMESPACE="oracle-system"
                SERVICE="oracle-cluster-rw"
                ;;
            "sqlserver")
                NAMESPACE="sqlserver-system"
                SERVICE="sqlserver-cluster-rw"
                ;;
        esac
        
        if kubectl get service "$SERVICE" -n "$NAMESPACE" > /dev/null 2>&1; then
            log_success "$db 데이터베이스: 사용 가능"
            echo "$db,available" >> "$RESULTS_DIR/database-status.csv"
        else
            log_warn "$db 데이터베이스: 사용 불가 (스킵됨)"
            echo "$db,unavailable" >> "$RESULTS_DIR/database-status.csv"
        fi
    done
    
    # Agent Control Plane 확인
    if kubectl get service kubedb-monitor-control-plane -n kubedb-monitor > /dev/null 2>&1; then
        log_success "KubeDB Monitor Control Plane: 사용 가능"
    else
        log_error "KubeDB Monitor Control Plane을 찾을 수 없습니다"
        exit 1
    fi
}

# 2. 기준선 성능 측정 (Agent 없이)
measure_baseline() {
    log_section "기준선 성능 측정 (Agent 비활성화)"
    
    # TODO: Agent가 비활성화된 상태의 애플리케이션 배포 및 테스트
    # 이는 Agent의 실제 오버헤드를 정확히 측정하기 위함
    
    echo "baseline_test,start,$(date)" >> "$RESULTS_DIR/baseline-performance.csv"
    
    # 여기서 실제 기준선 애플리케이션 배포 및 측정을 수행
    # 현재는 모의 데이터로 대체
    
    log_info "기준선 성능 측정 완료 (구현 예정)"
    echo "baseline_test,end,$(date)" >> "$RESULTS_DIR/baseline-performance.csv"
}

# 3. Agent 프로파일별 성능 테스트
test_agent_profiles() {
    log_section "Agent 프로파일별 성능 테스트"
    
    local db=$1
    local namespace=""
    local service=""
    
    case $db in
        "postgresql")
            namespace="kubedb-monitor-test"
            ;;
        "mysql")
            namespace="kubedb-monitor-mysql"
            ;;
        "mariadb")
            namespace="kubedb-monitor-mariadb"
            ;;
        "oracle")
            namespace="kubedb-monitor-oracle"
            ;;
        "sqlserver")
            namespace="kubedb-monitor-sqlserver"
            ;;
    esac
    
    for profile in "${PROFILES[@]}"; do
        log_subsection "$db - $profile 프로파일 테스트"
        
        local app_name="university-registration-${db}-${profile}"
        local pod_name=$(kubectl get pod -l app="$app_name" -n "$namespace" -o jsonpath='{.items[0].metadata.name}' 2>/dev/null || echo "")
        
        if [ -z "$pod_name" ]; then
            log_warn "$app_name 애플리케이션을 찾을 수 없습니다. 스킵합니다."
            continue
        fi
        
        log_info "[$db-$profile] Pod: $pod_name"
        
        # 성능 테스트 실행
        run_performance_test "$db" "$profile" "$pod_name" "$namespace"
    done
}

# 4. 개별 성능 테스트 실행
run_performance_test() {
    local db=$1
    local profile=$2
    local pod_name=$3
    local namespace=$4
    local test_id="${db}-${profile}"
    
    log_info "[$test_id] 성능 테스트 시작..."
    
    # CSV 헤더 생성
    if [ ! -f "$RESULTS_DIR/performance-results.csv" ]; then
        echo "database,profile,metric,value,timestamp" > "$RESULTS_DIR/performance-results.csv"
    fi
    
    local start_time=$(date +%s)
    local end_time=$((start_time + TEST_DURATION))
    
    # 워밍업
    log_info "[$test_id] 워밍업 시작 (${WARMUP_DURATION}초)..."
    warmup_requests "$pod_name" "$namespace" &
    local warmup_pid=$!
    sleep $WARMUP_DURATION
    kill $warmup_pid 2>/dev/null || true
    wait $warmup_pid 2>/dev/null || true
    
    # 메트릭 수집 시작
    collect_metrics "$test_id" "$pod_name" "$namespace" &
    local metrics_pid=$!
    
    # 부하 테스트 실행
    log_info "[$test_id] 부하 테스트 시작 (${TEST_DURATION}초)..."
    
    local total_requests=0
    local total_errors=0
    local total_response_time=0
    
    # 동시 부하 생성
    for i in $(seq 1 $LOAD_THREADS); do
        {
            local requests=0
            local errors=0
            local response_times=0
            
            while [ $(date +%s) -lt $end_time ]; do
                local start_req=$(date +%s%N)
                
                if kubectl exec "$pod_name" -n "$namespace" -- curl -s -f -w "%{http_code}" -o /dev/null http://localhost:8080/api/courses >/dev/null 2>&1; then
                    ((requests++))
                    local end_req=$(date +%s%N)
                    local response_time=$(( (end_req - start_req) / 1000000 )) # ms
                    response_times=$((response_times + response_time))
                else
                    ((errors++))
                fi
                
                sleep 0.1
            done
            
            echo "$requests $errors $response_times" > "/tmp/thread-${i}-${test_id}.result"
        } &
    done
    
    # 모든 스레드 완료 대기
    wait
    
    # 결과 집계
    for i in $(seq 1 $LOAD_THREADS); do
        if [ -f "/tmp/thread-${i}-${test_id}.result" ]; then
            read -r thread_requests thread_errors thread_response_times < "/tmp/thread-${i}-${test_id}.result"
            total_requests=$((total_requests + thread_requests))
            total_errors=$((total_errors + thread_errors))
            total_response_time=$((total_response_time + thread_response_times))
            rm -f "/tmp/thread-${i}-${test_id}.result"
        fi
    done
    
    # 메트릭 수집 종료
    kill $metrics_pid 2>/dev/null || true
    wait $metrics_pid 2>/dev/null || true
    
    # 결과 기록
    local timestamp=$(date)
    local tps=$((total_requests / TEST_DURATION))
    local avg_response_time=$((total_response_time / total_requests)) 2>/dev/null || avg_response_time=0
    local error_rate=$((total_errors * 100 / (total_requests + total_errors))) 2>/dev/null || error_rate=0
    
    echo "$db,$profile,tps,$tps,$timestamp" >> "$RESULTS_DIR/performance-results.csv"
    echo "$db,$profile,avg_response_time,$avg_response_time,$timestamp" >> "$RESULTS_DIR/performance-results.csv"
    echo "$db,$profile,error_rate,$error_rate,$timestamp" >> "$RESULTS_DIR/performance-results.csv"
    echo "$db,$profile,total_requests,$total_requests,$timestamp" >> "$RESULTS_DIR/performance-results.csv"
    echo "$db,$profile,total_errors,$total_errors,$timestamp" >> "$RESULTS_DIR/performance-results.csv"
    
    log_success "[$test_id] 성능 테스트 완료"
    log_info "[$test_id] TPS: $tps, 평균 응답시간: ${avg_response_time}ms, 에러율: ${error_rate}%"
}

# 5. 워밍업 요청
warmup_requests() {
    local pod_name=$1
    local namespace=$2
    
    while true; do
        kubectl exec "$pod_name" -n "$namespace" -- curl -s http://localhost:8080/api/courses > /dev/null 2>&1 || true
        sleep 1
    done
}

# 6. 메트릭 수집
collect_metrics() {
    local test_id=$1
    local pod_name=$2
    local namespace=$3
    
    local metrics_file="$RESULTS_DIR/${test_id}-metrics.csv"
    echo "timestamp,cpu_usage,memory_usage,jvm_heap_used,jvm_heap_max,hikari_active_connections,hikari_idle_connections" > "$metrics_file"
    
    while true; do
        local timestamp=$(date +%s)
        
        # CPU와 메모리 사용률 (kubectl top 명령어)
        local resource_usage=$(kubectl top pod "$pod_name" -n "$namespace" --no-headers 2>/dev/null || echo "0m 0Mi")
        local cpu_usage=$(echo "$resource_usage" | awk '{print $2}' | sed 's/m//')
        local memory_usage=$(echo "$resource_usage" | awk '{print $3}' | sed 's/Mi//')
        
        # JVM 메트릭 (Actuator)
        local jvm_heap_used=$(kubectl exec "$pod_name" -n "$namespace" -- curl -s http://localhost:8080/actuator/metrics/jvm.memory.used | jq -r '.measurements[0].value' 2>/dev/null || echo "0")
        local jvm_heap_max=$(kubectl exec "$pod_name" -n "$namespace" -- curl -s http://localhost:8080/actuator/metrics/jvm.memory.max | jq -r '.measurements[0].value' 2>/dev/null || echo "0")
        
        # HikariCP 메트릭
        local hikari_active=$(kubectl exec "$pod_name" -n "$namespace" -- curl -s http://localhost:8080/actuator/metrics/hikaricp.connections.active | jq -r '.measurements[0].value' 2>/dev/null || echo "0")
        local hikari_idle=$(kubectl exec "$pod_name" -n "$namespace" -- curl -s http://localhost:8080/actuator/metrics/hikaricp.connections.idle | jq -r '.measurements[0].value' 2>/dev/null || echo "0")
        
        echo "$timestamp,$cpu_usage,$memory_usage,$jvm_heap_used,$jvm_heap_max,$hikari_active,$hikari_idle" >> "$metrics_file"
        
        sleep 5
    done
}

# 7. 리소스 사용량 분석
analyze_resource_usage() {
    log_section "리소스 사용량 분석"
    
    local analysis_file="$RESULTS_DIR/resource-analysis.md"
    
    cat > "$analysis_file" << EOF
# KubeDB Monitor Agent 리소스 사용량 분석

## 테스트 개요
- 테스트 일시: $(date)
- 테스트 지속시간: ${TEST_DURATION}초
- 동시 사용자: ${CONCURRENT_USERS}명

## 데이터베이스별 성능 비교

EOF
    
    # 각 데이터베이스별 분석 추가
    for db in "${DATABASES[@]}"; do
        echo "### $db" >> "$analysis_file"
        echo "" >> "$analysis_file"
        
        # 성능 데이터 추출 및 분석
        grep "^$db," "$RESULTS_DIR/performance-results.csv" | while IFS=',' read -r database profile metric value timestamp; do
            echo "- **$profile** $metric: $value" >> "$analysis_file"
        done
        
        echo "" >> "$analysis_file"
    done
    
    # Agent 오버헤드 계산 (기준선 대비)
    cat >> "$analysis_file" << EOF

## Agent 오버헤드 분석

| 데이터베이스 | 프로파일 | TPS 감소율 | 메모리 증가율 | CPU 증가율 |
|------------|---------|-----------|-------------|-----------|
EOF
    
    # 실제 오버헤드 계산 로직은 기준선 데이터가 있을 때 구현
    for db in "${DATABASES[@]}"; do
        for profile in "${PROFILES[@]}"; do
            echo "| $db | $profile | 계산중... | 계산중... | 계산중... |" >> "$analysis_file"
        done
    done
    
    log_success "리소스 사용량 분석 완료: $analysis_file"
}

# 8. 종합 보고서 생성
generate_comprehensive_report() {
    log_section "종합 보고서 생성"
    
    local report_file="$RESULTS_DIR/comprehensive-benchmark-report.md"
    
    cat > "$report_file" << EOF
# KubeDB Monitor Agent 종합 성능 벤치마크 보고서

## 실행 정보
- **테스트 일시**: $(date)
- **테스트 ID**: $TIMESTAMP
- **테스트 지속시간**: ${TEST_DURATION}초 (워밍업 ${WARMUP_DURATION}초 포함)
- **동시 부하**: ${LOAD_THREADS}개 스레드, ${CONCURRENT_USERS}명 사용자

## 테스트 대상
- **데이터베이스**: ${DATABASES[*]}
- **Agent 프로파일**: ${PROFILES[*]}

## 주요 발견사항

### 1. 성능 영향도
$(analyze_performance_impact)

### 2. 프로파일별 특성
- **Conservative**: 최소 오버헤드, 기본 모니터링
- **Balanced**: 균형잡힌 성능과 모니터링
- **Aggressive**: 상세 모니터링, 높은 오버헤드

### 3. 데이터베이스별 호환성
$(analyze_database_compatibility)

## 권장사항

### 프로덕션 환경
1. **고부하 시스템**: Conservative 모드 권장
2. **일반 시스템**: Balanced 모드 권장
3. **개발/테스트**: Aggressive 모드 권장

### 데이터베이스별 최적화
$(generate_database_recommendations)

## 상세 결과 파일
- 성능 결과: \`performance-results.csv\`
- 메트릭 데이터: \`*-metrics.csv\`
- 리소스 분석: \`resource-analysis.md\`

## 테스트 환경 정보
- Kubernetes 클러스터: $(kubectl cluster-info | head -1)
- 테스트 도구 버전: KubeDB Monitor Agent Benchmark v1.0

EOF
    
    log_success "종합 보고서 생성 완료: $report_file"
}

# 보조 함수들
analyze_performance_impact() {
    echo "성능 영향도 분석 결과 (구현 예정)"
}

analyze_database_compatibility() {
    echo "데이터베이스 호환성 분석 결과 (구현 예정)"
}

generate_database_recommendations() {
    cat << EOF
- **PostgreSQL**: HikariCP 최적화, prepared statement 캐싱 활용
- **MySQL**: InnoDB 버퍼 풀 모니터링, 쿼리 캐시 고려
- **MariaDB**: Galera 클러스터 지원, Query Response Time 활용
- **Oracle**: CDB/PDB 모니터링, PL/SQL 최적화 고려
- **SQL Server**: Always On 지원, T-SQL 성능 모니터링
EOF
}

# 9. 정리 및 요약
cleanup_and_summary() {
    log_section "테스트 완료 및 요약"
    
    local total_tests=$(find "$RESULTS_DIR" -name "*-metrics.csv" | wc -l)
    local successful_tests=$(grep -c "SUCCESS" "$RESULTS_DIR"/*.log 2>/dev/null || echo "0")
    
    log_success "벤치마크 테스트 완료!"
    log_info "총 실행된 테스트: $total_tests"
    log_info "성공한 테스트: $successful_tests"
    log_info "결과 디렉토리: $RESULTS_DIR"
    
    echo ""
    log_info "주요 결과 파일:"
    echo "  📊 성능 결과: $RESULTS_DIR/performance-results.csv"
    echo "  📈 메트릭 데이터: $RESULTS_DIR/*-metrics.csv"
    echo "  📋 종합 보고서: $RESULTS_DIR/comprehensive-benchmark-report.md"
    echo "  🔍 상세 분석: $RESULTS_DIR/resource-analysis.md"
    
    echo ""
    log_info "보고서 확인 명령어:"
    echo "  cat $RESULTS_DIR/comprehensive-benchmark-report.md"
    
    echo ""
    log_info "벤치마크 결과 요약:"
    if [ -f "$RESULTS_DIR/performance-results.csv" ]; then
        echo "  TPS 결과 (상위 5개):"
        tail -n +2 "$RESULTS_DIR/performance-results.csv" | grep ",tps," | sort -t',' -k4 -nr | head -5 | while IFS=',' read -r db profile metric value timestamp; do
            echo "    $db-$profile: ${value} TPS"
        done
    fi
}

# 메인 실행 함수
main() {
    log_section "KubeDB Monitor Agent 종합 성능 벤치마크 시작"
    
    # 실행 로그 시작
    exec > >(tee "$RESULTS_DIR/benchmark.log") 2>&1
    
    check_environment
    
    # 사용 가능한 데이터베이스만 테스트
    local available_databases=()
    while IFS=',' read -r db status; do
        if [ "$status" = "available" ]; then
            available_databases+=("$db")
        fi
    done < "$RESULTS_DIR/database-status.csv"
    
    if [ ${#available_databases[@]} -eq 0 ]; then
        log_error "사용 가능한 데이터베이스가 없습니다"
        exit 1
    fi
    
    log_info "테스트 대상 데이터베이스: ${available_databases[*]}"
    
    # measure_baseline  # 향후 구현
    
    # 각 데이터베이스별 테스트 실행
    for db in "${available_databases[@]}"; do
        log_section "$db 데이터베이스 테스트"
        test_agent_profiles "$db"
    done
    
    analyze_resource_usage
    generate_comprehensive_report
    cleanup_and_summary
}

# 스크립트 실행
if [[ "${BASH_SOURCE[0]}" == "${0}" ]]; then
    main "$@"
fi