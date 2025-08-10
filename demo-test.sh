#!/bin/bash

# 매우 간단한 테스트 스크립트
echo "🚀 WebSocket 테스트 시작..."

for i in {1..5}; do
    echo "📊 쿼리 $i 전송 중..."
    
    # WebSocket 메시지 형태 (WebSocketMessage 구조)
    inner_data="{
        \"timestamp\": \"$(date -u +%Y-%m-%dT%H:%M:%SZ)\",
        \"pod_name\": \"test-pod-$i\",
        \"event_type\": \"query_execution\",
        \"data\": {
            \"query_id\": \"test_$i\",
            \"sql_pattern\": \"SELECT * FROM users WHERE id = ?\",
            \"sql_type\": \"SELECT\",
            \"execution_time_ms\": $((10 + RANDOM % 40)),
            \"status\": \"SUCCESS\"
        },
        \"metrics\": {
            \"connection_pool_active\": $((5 + RANDOM % 10)),
            \"connection_pool_max\": 20,
            \"heap_used_mb\": $((200 + RANDOM % 100))
        }
    }"
    
    # WebSocketMessage로 래핑
    json="{
        \"type\": \"metrics\",
        \"data\": $inner_data,
        \"timestamp\": \"$(date -u +%Y-%m-%dT%H:%M:%SZ)\"
    }"
    
    # WebSocket으로 전송
    echo "$json" | timeout 2s websocat --ping-interval 30 --one-message ws://localhost:8080/ws &
    
    sleep 0.5
done

echo "✅ 테스트 완료"