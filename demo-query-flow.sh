#!/bin/bash

# Real-time Query Flow 패널 애니메이션 효과를 확인할 수 있는 데모용 샘플 데이터 스크립트
# 다양한 쿼리 패턴과 성능 특성을 시뮬레이션하여 WebSocket으로 전송

set -e

# Check bash version for associative arrays
if [[ ${BASH_VERSION%%.*} -lt 4 ]]; then
    echo "⚠️  Warning: Bash 4+ required for associative arrays. Using zsh instead..."
    exec zsh "$0" "$@"
fi

# 설정값
WS_URL="ws://localhost:8080/ws"
DEMO_DURATION=60  # 60초 동안 실행
BASE_QPS=2       # 기본 초당 쿼리 수

echo "🚀 Real-time Query Flow 데모 시작"
echo "📡 WebSocket URL: $WS_URL"
echo "⏱️  실행 시간: ${DEMO_DURATION}초"
echo "📊 기본 QPS: $BASE_QPS"
echo

# 쿼리 패턴들
declare -a QUERY_PATTERNS=(
    "SELECT * FROM users WHERE id = ?|SELECT|users|5|0.01"
    "SELECT u.*, p.* FROM users u JOIN profiles p ON u.id = p.user_id WHERE u.status = ?|SELECT|users,profiles|25|0.02"
    "UPDATE users SET last_login = NOW() WHERE id = ?|UPDATE|users|8|0.005"
    "INSERT INTO orders (user_id, product_id, quantity, total_amount) VALUES (?, ?, ?, ?)|INSERT|orders|12|0.03"
    "SELECT COUNT(*) FROM orders WHERE created_at >= ? AND status IN ('pending', 'processing')|SELECT|orders|45|0.01"
    "SELECT o.*, u.username, p.name FROM orders o JOIN users u ON o.user_id = u.id JOIN products p ON o.product_id = p.id WHERE o.created_at >= ?|SELECT|orders,users,products|85|0.05"
    "DELETE FROM sessions WHERE expires_at < NOW()|DELETE|sessions|15|0.001"
)

# 시나리오별 설정 (QPS배수|지연시간배수|에러율배수)
declare -A SCENARIOS=(
    ["NORMAL"]="1.0|1.0|1.0"
    ["HIGH_LOAD"]="3.0|1.5|1.2"
    ["SLOW_QUERIES"]="0.8|3.0|2.0"
    ["ERROR_SPIKE"]="1.2|1.0|5.0"
)

# 전역 변수
QUERY_ID=1
CURRENT_SCENARIO="NORMAL"
CONNECTION_ACTIVE=5
CONNECTION_MAX=20

# 랜덤 숫자 생성 함수
random_between() {
    local min=$1
    local max=$2
    echo $((min + RANDOM % (max - min + 1)))
}

# 실수 계산 함수
calc() {
    echo "scale=2; $*" | bc -l
}

# 쿼리 ID 생성
generate_query_id() {
    echo "query_$((QUERY_ID++))"
}

# 랜덤 쿼리 패턴 선택
get_random_pattern() {
    local index=$((RANDOM % ${#QUERY_PATTERNS[@]}))
    echo "${QUERY_PATTERNS[$index]}"
}

# 실행 시간 시뮬레이션
simulate_execution_time() {
    local base_time=$1
    local multiplier=$2
    
    # 30% 변동폭 추가
    local variation=$(calc "$base_time * 0.3")
    local random_factor=$(calc "($RANDOM % 100 - 50) / 100 * $variation")
    local result=$(calc "($base_time + $random_factor) * $multiplier")
    
    # 최소 1ms
    result=$(calc "if ($result < 1) 1 else $result")
    printf "%.0f" "$result"
}

# 에러 시뮬레이션
should_simulate_error() {
    local base_rate=$1
    local multiplier=$2
    local threshold=$(calc "$base_rate * $multiplier * 100")
    local random_val=$((RANDOM % 100))
    
    if (( $(echo "$random_val < $threshold" | bc -l) )); then
        echo "true"
    else
        echo "false"
    fi
}

# 커넥션 풀 업데이트
update_connection_pool() {
    local scenario_config="${SCENARIOS[$CURRENT_SCENARIO]}"
    local qps_mult=$(echo "$scenario_config" | cut -d'|' -f1)
    
    # 커넥션 풀 시뮬레이션
    local base_active=5
    local variation=$((RANDOM % 6 - 3))  # -3 ~ +3
    CONNECTION_ACTIVE=$(calc "$base_active * $qps_mult + $variation")
    CONNECTION_ACTIVE=$(printf "%.0f" "$CONNECTION_ACTIVE")
    
    # 최소 1, 최대 CONNECTION_MAX
    if (( CONNECTION_ACTIVE < 1 )); then
        CONNECTION_ACTIVE=1
    elif (( CONNECTION_ACTIVE > CONNECTION_MAX )); then
        CONNECTION_ACTIVE=$CONNECTION_MAX
    fi
}

# 쿼리 메트릭 생성
generate_query_metric() {
    local pattern_data=$(get_random_pattern)
    local sql_pattern=$(echo "$pattern_data" | cut -d'|' -f1)
    local sql_type=$(echo "$pattern_data" | cut -d'|' -f2)
    local table_names=$(echo "$pattern_data" | cut -d'|' -f3)
    local avg_exec_time=$(echo "$pattern_data" | cut -d'|' -f4)
    local error_rate=$(echo "$pattern_data" | cut -d'|' -f5)
    
    local scenario_config="${SCENARIOS[$CURRENT_SCENARIO]}"
    local qps_mult=$(echo "$scenario_config" | cut -d'|' -f1)
    local latency_mult=$(echo "$scenario_config" | cut -d'|' -f2)
    local error_mult=$(echo "$scenario_config" | cut -d'|' -f3)
    
    local query_id=$(generate_query_id)
    local execution_time=$(simulate_execution_time "$avg_exec_time" "$latency_mult")
    local is_error=$(should_simulate_error "$error_rate" "$error_mult")
    
    update_connection_pool
    
    local timestamp=$(date -u +"%Y-%m-%dT%H:%M:%S.%3NZ")
    local pod_name="kube-db-monitor-$((RANDOM % 3 + 1))"
    local event_type="query_execution"
    local status="SUCCESS"
    local error_message=""
    
    if [[ "$is_error" == "true" ]]; then
        event_type="query_error"
        status="ERROR"
        error_message="Simulated database error"
    fi
    
    # JSON 생성
    local json=$(cat <<EOF
{
  "timestamp": "$timestamp",
  "pod_name": "$pod_name",
  "event_type": "$event_type",
  "data": {
    "query_id": "$query_id",
    "sql_pattern": "$sql_pattern",
    "sql_type": "$sql_type",
    "table_names": [$(echo "$table_names" | sed 's/,/","/g' | sed 's/^/"/' | sed 's/$/"/')],
    "execution_time_ms": $execution_time,
    "status": "$status"$([ "$is_error" == "true" ] && echo ",\"error_message\": \"$error_message\"" || echo "")
  },
  "metrics": {
    "connection_pool_active": $CONNECTION_ACTIVE,
    "connection_pool_idle": $((CONNECTION_MAX - CONNECTION_ACTIVE - 2)),
    "connection_pool_max": $CONNECTION_MAX,
    "connection_pool_usage_ratio": $(calc "$CONNECTION_ACTIVE / $CONNECTION_MAX"),
    "heap_used_mb": $((200 + RANDOM % 100)),
    "heap_max_mb": 512,
    "heap_usage_ratio": $(calc "(200 + $RANDOM % 100) / 512"),
    "cpu_usage_ratio": $(calc "0.1 + ($RANDOM % 80) / 100")
  }
}
EOF
)
    echo "$json"
}

# WebSocket으로 데이터 전송
send_to_websocket() {
    local json_data="$1"
    
    # websocat 또는 wscat 사용 (설치 필요)
    if command -v websocat &> /dev/null; then
        echo "$json_data" | websocat --ping-interval 30 "$WS_URL" &
    elif command -v wscat &> /dev/null; then
        echo "$json_data" | wscat -c "$WS_URL" &
    else
        # curl을 사용한 HTTP POST (WebSocket 서버가 POST도 지원하는 경우)
        curl -s -X POST \
            -H "Content-Type: application/json" \
            -d "$json_data" \
            "${WS_URL/ws:/http:}/metrics" 2>/dev/null || true
    fi
}

# 시나리오 전환
switch_scenario() {
    local scenarios=("NORMAL" "HIGH_LOAD" "SLOW_QUERIES" "ERROR_SPIKE")
    local current_index=0
    
    # 현재 시나리오 인덱스 찾기
    for i in "${!scenarios[@]}"; do
        if [[ "${scenarios[$i]}" == "$CURRENT_SCENARIO" ]]; then
            current_index=$i
            break
        fi
    done
    
    # 다음 시나리오로 전환
    local next_index=$(((current_index + 1) % ${#scenarios[@]}))
    CURRENT_SCENARIO="${scenarios[$next_index]}"
    
    echo "🔄 시나리오 전환: $CURRENT_SCENARIO"
}

# 메인 실행 함수
main() {
    local start_time=$(date +%s)
    local query_count=0
    local scenario_switch_time=$((start_time + 15))  # 15초마다 시나리오 전환
    
    echo "🎬 데이터 생성 시작..."
    echo
    
    while true; do
        local current_time=$(date +%s)
        local elapsed=$((current_time - start_time))
        
        # 데모 시간 종료 확인
        if (( elapsed >= DEMO_DURATION )); then
            echo
            echo "⏰ 데모 완료 (${DEMO_DURATION}초)"
            echo "📊 총 생성된 쿼리: $query_count"
            break
        fi
        
        # 시나리오 전환 시간 확인
        if (( current_time >= scenario_switch_time )); then
            switch_scenario
            scenario_switch_time=$((current_time + 15))
        fi
        
        # 쿼리 메트릭 생성 및 전송
        local query_metric=$(generate_query_metric)
        send_to_websocket "$query_metric"
        
        query_count=$((query_count + 1))
        
        # 10개마다 로그 출력
        if (( query_count % 10 == 0 )); then
            local scenario_config="${SCENARIOS[$CURRENT_SCENARIO]}"
            local qps_mult=$(echo "$scenario_config" | cut -d'|' -f1)
            local current_qps=$(calc "$BASE_QPS * $qps_mult")
            
            echo "📊 생성된 쿼리: $query_count | 시나리오: $CURRENT_SCENARIO | QPS: $current_qps"
        fi
        
        # QPS에 맞춰 대기
        local scenario_config="${SCENARIOS[$CURRENT_SCENARIO]}"
        local qps_mult=$(echo "$scenario_config" | cut -d'|' -f1)
        local current_qps=$(calc "$BASE_QPS * $qps_mult")
        local delay=$(calc "1 / $current_qps")
        
        # 약간의 랜덤 지연 (±20%)
        local random_factor=$(calc "0.8 + ($RANDOM % 40) / 100")
        delay=$(calc "$delay * $random_factor")
        
        sleep "$delay"
    done
}

# 종료 시그널 처리
cleanup() {
    echo
    echo "🛑 데모 중단됨"
    exit 0
}

trap cleanup SIGINT SIGTERM

# 필요한 도구 확인
echo "🔍 필요한 도구 확인 중..."
if ! command -v bc &> /dev/null; then
    echo "❌ bc 명령어가 필요합니다: brew install bc 또는 apt-get install bc"
    exit 1
fi

if ! command -v websocat &> /dev/null && ! command -v wscat &> /dev/null && ! command -v curl &> /dev/null; then
    echo "❌ websocat, wscat, 또는 curl 중 하나가 필요합니다"
    echo "   websocat 설치: brew install websocat"
    echo "   wscat 설치: npm install -g wscat"
    exit 1
fi

echo "✅ 필요한 도구 확인 완료"
echo

# 사용법 출력
cat << EOF
🎮 데모 명령어:
   Ctrl+C - 데모 중단
   서버가 자동으로 다른 성능 시나리오 간 전환됩니다

📋 시나리오:
   NORMAL      - 정상 상태 (QPS: ${BASE_QPS})
   HIGH_LOAD   - 높은 부하 (QPS: $(calc "$BASE_QPS * 3"))
   SLOW_QUERIES - 느린 쿼리 (평균 지연시간 3배 증가)
   ERROR_SPIKE  - 에러 급증 (에러율 5배 증가)

EOF

# 메인 실행
main