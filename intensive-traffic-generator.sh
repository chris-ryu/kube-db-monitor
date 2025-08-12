#!/bin/bash

# 매우 집중적인 트래픽 생성으로 확실한 TPS 이벤트 트리거
BASE_URL="https://university-registration.bitgaram.info"

echo "🚀 Starting Intensive Traffic Generation for TPS Events"
echo "Target: $BASE_URL"
echo "Goal: Generate >2 TPS consistently to trigger TPS_EVENT"

# 30초 동안 지속적으로 초당 5개씩 요청 (총 150개)
echo "📊 Phase 1: 30초간 지속적 고빈도 트래픽 (5 req/sec = 확실한 2 TPS 초과)"

for i in {1..30}; do
    echo "Second $i: Sending 5 concurrent requests..."
    
    # 동시에 5개 요청 전송
    curl -s "$BASE_URL/api/courses" > /dev/null &
    curl -s "$BASE_URL/api/students" > /dev/null &  
    curl -s "$BASE_URL/" > /dev/null &
    curl -s "$BASE_URL/actuator/health" > /dev/null &
    curl -s "$BASE_URL/api/enrollments" > /dev/null &
    
    sleep 1  # 1초 대기 = 정확히 5 TPS
done

wait

echo "✅ Phase 1 Complete: 150 requests in 30 seconds = 5 TPS average"
echo ""
echo "📊 Phase 2: 집중 폭발 트래픽 (1초에 20개 요청)"

# 1초에 20개 요청을 3번 반복 = 60개 요청
for phase in {1..3}; do
    echo "Burst Phase $phase: 20 requests in 1 second..."
    
    for i in {1..20}; do
        curl -s "$BASE_URL/api/courses?burst=$phase-$i" > /dev/null &
    done
    
    sleep 1
done

wait

echo "✅ Phase 2 Complete: 60 burst requests"
echo ""
echo "📊 Final Phase: 최대 집중 트래픽 (0.5초에 30개 요청)"

# 0.5초에 30개 = 60 TPS
for i in {1..30}; do
    curl -s "$BASE_URL/?final=$i" > /dev/null &
done

sleep 0.5

for i in {31..60}; do
    curl -s "$BASE_URL/?final=$i" > /dev/null &
done

wait

echo "✅ Final Phase Complete: 60 requests in 1 second = 60 TPS"
echo ""
echo "🎯 Total Traffic Generated:"
echo "   - 150 requests over 30 seconds (sustained 5 TPS)"
echo "   - 60 burst requests in 3 seconds (20 TPS peak)"  
echo "   - 60 final requests in 1 second (60 TPS peak)"
echo "   - Grand Total: 270 requests"
echo ""
echo "🎯 Expected Results on Dashboard:"
echo "   ✅ TPS value should exceed 2"
echo "   ✅ TPS_EVENT entries should appear in Recent Queries"
echo "   ✅ Agent should log '🚨 TPS Threshold Exceeded' messages"
echo ""
echo "Monitor Agent logs: kubectl logs university-registration-demo-7465ffd546-vf4xz -n kubedb-monitor-test --tail=20"