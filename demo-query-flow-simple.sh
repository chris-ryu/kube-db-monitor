#!/bin/bash

# Simple Real-time Query Flow 데모 스크립트
# WebSocket으로 샘플 데이터 전송

set -e

WS_URL="ws://localhost:8080/ws"
DEMO_DURATION=30  # 30초로 단축
BASE_QPS=2

QUERY_ID=1

echo "🚀 Real-time Query Flow 데모 시작"
echo "📡 WebSocket URL: $WS_URL"
echo "⏱️  실행 시간: ${DEMO_DURATION}초"
echo "📊 기본 QPS: $BASE_QPS"
echo

# 단순 쿼리 패턴들
QUERIES=(
    "SELECT * FROM users WHERE id = ?|SELECT|5|0.01"
    "UPDATE users SET last_login = NOW() WHERE id = ?|UPDATE|8|0.005"
    "INSERT INTO orders VALUES (?, ?, ?)|INSERT|12|0.02"
    "SELECT COUNT(*) FROM orders|SELECT|45|0.01"
)

# 시나리오 설정 (multiplier 방식)
get_scenario_multipliers() {
    local elapsed=$1
    local qps_mult=1.0
    local latency_mult=1.0
    local error_mult=1.0
    
    if (( elapsed >= 15 && elapsed < 30 )); then
        # HIGH_LOAD scenario
        qps_mult=3.0
        latency_mult=1.5
        error_mult=1.2
        echo "HIGH_LOAD"
    elif (( elapsed >= 30 && elapsed < 45 )); then
        # SLOW_QUERIES scenario  
        qps_mult=0.8
        latency_mult=3.0
        error_mult=2.0
        echo "SLOW_QUERIES"
    else
        # NORMAL scenario
        echo "NORMAL"
    fi
    
    echo "$qps_mult $latency_mult $error_mult"
}

generate_query_id() {
    echo "query_$((QUERY_ID++))"
}

get_random_pattern() {
    local index=$((RANDOM % ${#QUERIES[@]}))
    echo "${QUERIES[$index]}"
}

simulate_execution_time() {
    local base_time=$1
    local multiplier=$2
    local variation=$(echo "scale=0; $base_time * 0.3" | bc)
    local random_factor=$((RANDOM % (2 * variation) - variation))
    local result=$(echo "scale=0; ($base_time + $random_factor) * $multiplier" | bc)
    
    # 최소 1ms
    if (( $(echo "$result < 1" | bc -l) )); then
        result=1
    fi
    echo "$result"
}

should_simulate_error() {
    local base_rate=$1
    local multiplier=$2
    local threshold=$(echo "scale=2; $base_rate * $multiplier * 100" | bc)
    local random_val=$((RANDOM % 100))
    
    if (( $(echo "$random_val < $threshold" | bc -l) )); then
        echo "true"
    else
        echo "false"
    fi
}

generate_query_metric() {
    local elapsed=$1
    local pattern_data=$(get_random_pattern)
    local sql_pattern=$(echo "$pattern_data" | cut -d'|' -f1)
    local sql_type=$(echo "$pattern_data" | cut -d'|' -f2)
    local avg_exec_time=$(echo "$pattern_data" | cut -d'|' -f3)
    local error_rate=$(echo "$pattern_data" | cut -d'|' -f4)
    
    # 시나리오 multipliers 가져오기
    local scenario_info=$(get_scenario_multipliers "$elapsed")
    local scenario_name=$(echo "$scenario_info" | head -n1)
    local multipliers=$(echo "$scenario_info" | tail -n1)
    local latency_mult=$(echo "$multipliers" | cut -d' ' -f2)
    local error_mult=$(echo "$multipliers" | cut -d' ' -f3)
    
    local query_id=$(generate_query_id)
    local execution_time=$(simulate_execution_time "$avg_exec_time" "$latency_mult")
    local is_error=$(should_simulate_error "$error_rate" "$error_mult")
    
    local timestamp=$(date -u +"%Y-%m-%dT%H:%M:%S.%3NZ" 2>/dev/null || date -u +"%Y-%m-%dT%H:%M:%SZ")
    local pod_name="kube-db-monitor-$((RANDOM % 3 + 1))"
    local event_type="query_execution"
    local status="SUCCESS"
    
    if [[ "$is_error" == "true" ]]; then
        event_type="query_error"
        status="ERROR"
    fi
    
    # 커넥션 풀 값 시뮬레이션
    local connection_active=$((5 + RANDOM % 10))
    local connection_max=20
    local connection_idle=$((connection_max - connection_active - 2))
    local heap_used=$((200 + RANDOM % 100))
    
    # JSON 생성
    cat << EOF
{
  "timestamp": "$timestamp",
  "pod_name": "$pod_name",
  "event_type": "$event_type",
  "data": {
    "query_id": "$query_id",
    "sql_pattern": "$sql_pattern",
    "sql_type": "$sql_type",
    "table_names": ["users"],
    "execution_time_ms": $execution_time,
    "status": "$status"$([ "$is_error" == "true" ] && echo ',"error_message": "Simulated database error"' || echo "")
  },
  "metrics": {
    "connection_pool_active": $connection_active,
    "connection_pool_idle": $connection_idle,
    "connection_pool_max": $connection_max,
    "connection_pool_usage_ratio": $(echo "scale=2; $connection_active / $connection_max" | bc),
    "heap_used_mb": $heap_used,
    "heap_max_mb": 512,
    "heap_usage_ratio": $(echo "scale=2; $heap_used / 512" | bc),
    "cpu_usage_ratio": $(echo "scale=2; (10 + $RANDOM % 80) / 100" | bc)
  }
}
EOF
}

send_to_websocket() {
    local json_data="$1"
    
    # websocat을 사용하여 WebSocket으로 전송
    if command -v websocat >/dev/null 2>&1; then
        echo "$json_data" | timeout 2s websocat --ping-interval 30 --one-message "$WS_URL" 2>/dev/null &
    elif command -v wscat >/dev/null 2>&1; then
        echo "$json_data" | timeout 2s wscat -c "$WS_URL" 2>/dev/null &
    else
        # HTTP POST fallback (if supported)
        curl -s -X POST -H "Content-Type: application/json" -d "$json_data" "${WS_URL/ws:/http:}/metrics" 2>/dev/null &
    fi
    
    # 백그라운드 프로세스 정리
    sleep 0.1
}

main() {
    local start_time=$(date +%s)
    local query_count=0
    local last_scenario=""
    
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
        
        # 시나리오 전환 로그
        local scenario_info=$(get_scenario_multipliers "$elapsed")
        local current_scenario=$(echo "$scenario_info" | head -n1)
        if [[ "$current_scenario" != "$last_scenario" ]]; then
            echo "🔄 시나리오 전환: $current_scenario"
            last_scenario="$current_scenario"
        fi
        
        # 쿼리 메트릭 생성 및 전송
        local query_metric=$(generate_query_metric "$elapsed")
        send_to_websocket "$query_metric"
        
        query_count=$((query_count + 1))
        
        # 5개마다 로그 출력
        if (( query_count % 5 == 0 )); then
            local multipliers=$(echo "$scenario_info" | tail -n1)
            local qps_mult=$(echo "$multipliers" | cut -d' ' -f1)
            local current_qps=$(echo "scale=1; $BASE_QPS * $qps_mult" | bc)
            
            echo "📊 생성된 쿼리: $query_count | 시나리오: $current_scenario | QPS: $current_qps"
        fi
        
        # QPS에 맞춰 대기
        local multipliers=$(echo "$scenario_info" | tail -n1)
        local qps_mult=$(echo "$multipliers" | cut -d' ' -f1)
        local delay=$(echo "scale=3; 1 / ($BASE_QPS * $qps_mult)" | bc)
        
        sleep "$delay"
    done
}

# 종료 처리
cleanup() {
    echo
    echo "🛑 데모 중단됨"
    # 백그라운드 프로세스 정리
    jobs -p | xargs -r kill 2>/dev/null || true
    exit 0
}

trap cleanup SIGINT SIGTERM

# 필요한 도구 확인
echo "🔍 필요한 도구 확인 중..."
if ! command -v bc >/dev/null 2>&1; then
    echo "❌ bc 명령어가 필요합니다: brew install bc"
    exit 1
fi

if ! command -v websocat >/dev/null 2>&1 && ! command -v wscat >/dev/null 2>&1; then
    echo "❌ websocat 또는 wscat이 필요합니다"
    echo "   websocat 설치: brew install websocat"
    echo "   wscat 설치: npm install -g wscat"
    exit 1
fi

echo "✅ 필요한 도구 확인 완료"
echo

# 메인 실행
main