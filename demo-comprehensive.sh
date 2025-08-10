#!/bin/bash

# 포괄적인 쿼리 성능 테스트 데모
echo "🚀 포괄적인 Query Flow Animation 테스트 시작..."

echo "📊 다양한 성능의 쿼리들을 전송하여 파티클 분류 테스트"

# 빠른 쿼리들 (< 10ms) - 파란색 배경 파티클
echo "🔵 빠른 쿼리들 (< 10ms) 전송..."
for i in {1..10}; do
    execution_time=$((1 + RANDOM % 9))  # 1-9ms
    
    inner_data="{
        \"timestamp\": \"$(date -u +%Y-%m-%dT%H:%M:%SZ)\",
        \"pod_name\": \"fast-pod-$i\",
        \"event_type\": \"query_execution\",
        \"data\": {
            \"query_id\": \"fast_query_$i\",
            \"sql_pattern\": \"SELECT id FROM users WHERE email = ?\",
            \"sql_type\": \"SELECT\",
            \"execution_time_ms\": $execution_time,
            \"status\": \"SUCCESS\"
        },
        \"metrics\": {
            \"connection_pool_active\": $((3 + RANDOM % 5)),
            \"connection_pool_max\": 20
        }
    }"
    
    json="{
        \"type\": \"metrics\",
        \"data\": $inner_data,
        \"timestamp\": \"$(date -u +%Y-%m-%dT%H:%M:%SZ)\"
    }"
    
    echo "$json" | timeout 2s websocat --ping-interval 30 --one-message ws://localhost:8080/ws &
    sleep 0.2
done

# 보통 쿼리들 (10-49ms) - 주황색 파티클  
echo "🟠 보통 느린 쿼리들 (10-49ms) 전송..."
for i in {1..6}; do
    execution_time=$((10 + RANDOM % 39))  # 10-49ms
    
    inner_data="{
        \"timestamp\": \"$(date -u +%Y-%m-%dT%H:%M:%SZ)\",
        \"pod_name\": \"medium-pod-$i\",
        \"event_type\": \"query_execution\",
        \"data\": {
            \"query_id\": \"medium_query_$i\",
            \"sql_pattern\": \"SELECT * FROM orders WHERE user_id = ? AND status = ?\",
            \"sql_type\": \"SELECT\",
            \"execution_time_ms\": $execution_time,
            \"status\": \"SUCCESS\"
        },
        \"metrics\": {
            \"connection_pool_active\": $((8 + RANDOM % 5)),
            \"connection_pool_max\": 20
        }
    }"
    
    json="{
        \"type\": \"metrics\",
        \"data\": $inner_data,
        \"timestamp\": \"$(date -u +%Y-%m-%dT%H:%M:%SZ)\"
    }"
    
    echo "$json" | timeout 2s websocat --ping-interval 30 --one-message ws://localhost:8080/ws &
    sleep 0.3
done

# 느린 쿼리들 (50ms+) - 노란색 파티클
echo "🟡 느린 쿼리들 (50ms+) 전송..."
for i in {1..4}; do
    execution_time=$((50 + RANDOM % 200))  # 50-250ms
    
    inner_data="{
        \"timestamp\": \"$(date -u +%Y-%m-%dT%H:%M:%SZ)\",
        \"pod_name\": \"slow-pod-$i\",
        \"event_type\": \"query_execution\",
        \"data\": {
            \"query_id\": \"slow_query_$i\",
            \"sql_pattern\": \"SELECT COUNT(*) FROM large_table t1 JOIN another_table t2 ON t1.id = t2.ref_id\",
            \"sql_type\": \"SELECT\",
            \"execution_time_ms\": $execution_time,
            \"status\": \"SUCCESS\"
        },
        \"metrics\": {
            \"connection_pool_active\": $((12 + RANDOM % 5)),
            \"connection_pool_max\": 20
        }
    }"
    
    json="{
        \"type\": \"metrics\",
        \"data\": $inner_data,
        \"timestamp\": \"$(date -u +%Y-%m-%dT%H:%M:%SZ)\"
    }"
    
    echo "$json" | timeout 2s websocat --ping-interval 30 --one-message ws://localhost:8080/ws &
    sleep 0.4
done

# 에러 쿼리들 - 빨간색 파티클
echo "🔴 에러 쿼리들 전송..."
for i in {1..3}; do
    execution_time=$((20 + RANDOM % 100))  # 20-120ms (에러도 시간 소요)
    
    inner_data="{
        \"timestamp\": \"$(date -u +%Y-%m-%dT%H:%M:%SZ)\",
        \"pod_name\": \"error-pod-$i\",
        \"event_type\": \"query_execution\",
        \"data\": {
            \"query_id\": \"error_query_$i\",
            \"sql_pattern\": \"SELECT * FROM non_existent_table WHERE id = ?\",
            \"sql_type\": \"SELECT\",
            \"execution_time_ms\": $execution_time,
            \"status\": \"ERROR\",
            \"error_message\": \"Table 'database.non_existent_table' doesn't exist\"
        },
        \"metrics\": {
            \"connection_pool_active\": $((15 + RANDOM % 3)),
            \"connection_pool_max\": 20
        }
    }"
    
    json="{
        \"type\": \"metrics\",
        \"data\": $inner_data,
        \"timestamp\": \"$(date -u +%Y-%m-%dT%H:%M:%SZ)\"
    }"
    
    echo "$json" | timeout 2s websocat --ping-interval 30 --one-message ws://localhost:8080/ws &
    sleep 0.5
done

echo "⏳ 모든 쿼리 전송 완료. 잠시 기다린 후 브라우저에서 파티클 분류를 확인하세요!"
echo "📊 예상 결과:"
echo "  🔵 빠른 쿼리 (1-9ms): 10개 - 작고 연한 파란색 배경 파티클"  
echo "  🟠 보통 쿼리 (10-49ms): 6개 - 중간 크기 주황색 파티클"
echo "  🟡 느린 쿼리 (50ms+): 4개 - 큰 노란색 파티클" 
echo "  🔴 에러 쿼리: 3개 - 가장 큰 빨간색 파티클"

wait
echo "✅ 포괄적인 테스트 완료!"