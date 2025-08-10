#!/bin/bash

# 연속적인 데이터 전송으로 애니메이션 효과 확인
echo "🎬 Real-time Query Flow 애니메이션 데모 시작..."
echo "브라우저에서 http://localhost:3002 를 확인하세요!"
echo "30초 동안 연속 데이터를 전송합니다..."
echo

QUERIES=("SELECT * FROM users WHERE id = ?" "UPDATE users SET last_login = NOW()" "INSERT INTO orders VALUES" "SELECT COUNT(*) FROM products" "DELETE FROM sessions WHERE expired")
TYPES=("SELECT" "UPDATE" "INSERT" "SELECT" "DELETE")

for i in {1..60}; do
    # 랜덤 쿼리 선택
    idx=$((RANDOM % ${#QUERIES[@]}))
    query="${QUERIES[$idx]}"
    type="${TYPES[$idx]}"
    
    # 실행 시간 랜덤 (10-100ms)
    exec_time=$((10 + RANDOM % 90))
    
    # 가끔 에러 발생 시뮬레이션 (10% 확률)
    status="SUCCESS"
    event_type="query_execution"
    if (( RANDOM % 10 == 0 )); then
        status="ERROR"
        event_type="query_error"
        exec_time=$((exec_time * 2))  # 에러 시 더 오래 걸림
    fi
    
    # JSON 생성
    json="{
        \"timestamp\": \"$(date -u +%Y-%m-%dT%H:%M:%S.%3NZ)\",
        \"pod_name\": \"kube-db-monitor-$((RANDOM % 3 + 1))\",
        \"event_type\": \"$event_type\",
        \"data\": {
            \"query_id\": \"demo_query_$i\",
            \"sql_pattern\": \"$query\",
            \"sql_type\": \"$type\",
            \"execution_time_ms\": $exec_time,
            \"status\": \"$status\"
        },
        \"metrics\": {
            \"connection_pool_active\": $((5 + RANDOM % 10)),
            \"connection_pool_idle\": $((2 + RANDOM % 5)),
            \"connection_pool_max\": 20,
            \"connection_pool_usage_ratio\": $(echo "scale=2; (5 + $RANDOM % 10) / 20" | bc),
            \"heap_used_mb\": $((200 + RANDOM % 100)),
            \"heap_max_mb\": 512,
            \"cpu_usage_ratio\": $(echo "scale=2; (10 + $RANDOM % 60) / 100" | bc)
        }
    }"
    
    # WebSocket으로 전송
    echo "$json" | timeout 1s websocat --ping-interval 30 --one-message ws://localhost:8080/ws >/dev/null 2>&1 &
    
    # 진행률 표시
    if (( i % 5 == 0 )); then
        echo "📊 전송된 쿼리: $i/60 | 상태: $status | 실행시간: ${exec_time}ms"
    fi
    
    # QPS 조절 (초당 2개)
    sleep 0.5
done

echo
echo "✅ 데모 완료! 브라우저에서 Real-time Query Flow 패널의 애니메이션을 확인하세요."
echo "🎯 확인 포인트:"
echo "   - 파티클이 App → Pool → DB → App 경로로 움직임"
echo "   - 실행 시간에 따른 파티클 색상 변화 (초록→주황→노랑→빨강)"
echo "   - 실시간 쿼리 목록 업데이트 및 슬라이드 애니메이션"
echo "   - 에러 쿼리의 빨간색 표시"