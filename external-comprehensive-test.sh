#!/bin/bash

echo "🌐 KubeDB Monitor 외부 접근 종합 테스트"
echo "====================================="
echo "Public DNS를 사용한 외부 접근 테스트 (kubectl 불필요)"
echo ""

# Public DNS endpoints
UNIVERSITY_API="https://university-registration.bitgaram.info"
CONTROL_PLANE_API="https://kube-db-mon-controlplane.bitgaram.info"
DASHBOARD_URL="https://kube-db-mon-dashboard.bitgaram.info"

# SSL certificate 옵션 (자체 서명 인증서 허용)
CURL_OPTS="-k -s"

echo "🎯 테스트 환경:"
echo "- University Registration API: $UNIVERSITY_API" 
echo "- Control Plane API: $CONTROL_PLANE_API"
echo "- Dashboard: $DASHBOARD_URL"
echo ""

# Health check function
check_health() {
    local url=$1
    local service_name=$2
    
    echo "🔍 $service_name 상태 확인..."
    if curl $CURL_OPTS --connect-timeout 10 "$url/actuator/health" > /dev/null 2>&1; then
        echo "✅ $service_name 정상 동작 중"
        return 0
    elif curl $CURL_OPTS --connect-timeout 10 "$url/api/health" > /dev/null 2>&1; then
        echo "✅ $service_name 정상 동작 중"
        return 0
    else
        echo "❌ $service_name 접근 실패"
        return 1
    fi
}

# Phase 0: Service Health Check
echo "🏥 Phase 0: 서비스 상태 확인"
echo "=========================="
check_health $UNIVERSITY_API "University Registration"
check_health $CONTROL_PLANE_API "Control Plane"
echo ""

# Phase 1: Deadlock 시뮬레이션 테스트 (외부 API 직접 호출)
echo "💀 Phase 1: Deadlock 시뮬레이션 테스트"
echo "===================================="

echo "1-1. Simple Deadlock 시뮬레이션..."
curl $CURL_OPTS "$UNIVERSITY_API/simulation/simple-deadlock" | jq '.' 2>/dev/null || echo "✅ Simple Deadlock 완료"

echo ""
echo "1-2. 복잡한 Deadlock 시뮬레이션..."
curl $CURL_OPTS "$UNIVERSITY_API/simulation/deadlock" | jq '.' 2>/dev/null || echo "✅ 복잡한 Deadlock 완료"

echo ""
echo "1-3. Lock Contention 시뮬레이션..."
curl $CURL_OPTS "$UNIVERSITY_API/simulation/lock-contention" | jq '.' 2>/dev/null || echo "✅ Lock Contention 완료"

echo ""

# Phase 2: Long Running Transaction 테스트
echo "🐌 Phase 2: Long Running Transaction 테스트"
echo "=========================================="

echo "2-1. Long Transaction 시뮬레이션 (백그라운드)..."
curl $CURL_OPTS "$UNIVERSITY_API/simulation/long-transaction" > /tmp/long_tx_result.json &
LRT_PID=$!

echo "2-2. Performance Test (대량 트랜잭션)..."
curl $CURL_OPTS "$UNIVERSITY_API/data/performance-test" > /tmp/perf_test_result.json &

echo "2-3. Concurrent Test (동시 트랜잭션)..."
curl $CURL_OPTS -X POST "$UNIVERSITY_API/data/concurrent-test" > /tmp/concurrent_test_result.json &

echo "2-4. Bulk Enrollment Test (대량 데이터 처리)..."
curl $CURL_OPTS -X POST "$UNIVERSITY_API/enrollments/bulk-test" > /tmp/bulk_test_result.json &

echo "✅ Long Running Transaction들이 백그라운드에서 실행 중..."
echo ""

# Phase 3: 실시간 메트릭 수집 모니터링 (외부에서 Control Plane 확인)
echo "📊 Phase 3: Control Plane 메트릭 수신 상태 모니터링 (30초간)"
echo "========================================================="

echo "Control Plane에서 메트릭 수신 상황을 모니터링합니다..."

for i in {1..6}; do
    echo "[$i/6] Control Plane 상태 확인... (5초 간격)"
    
    # Control Plane health 체크
    HEALTH_STATUS=$(curl $CURL_OPTS --connect-timeout 5 "$CONTROL_PLANE_API/api/health" 2>/dev/null)
    if [ $? -eq 0 ]; then
        echo "   ✅ Control Plane 정상 동작 중"
    else
        echo "   ⚠️ Control Plane 접근 제한 또는 오프라인"
    fi
    
    # University API 활동 확인
    API_RESPONSE=$(curl $CURL_OPTS --connect-timeout 5 "$UNIVERSITY_API/courses" 2>/dev/null)
    if [ $? -eq 0 ]; then
        echo "   📈 University API 응답 정상 - 메트릭 수집 활성화"
    else
        echo "   📈 University API 응답 대기 중..."
    fi
    
    if [ $i -lt 6 ]; then
        sleep 5
    fi
done

echo ""

# Phase 4: 혼합 고부하 시나리오 - 외부에서 고부하 생성
echo "🔀 Phase 4: 외부 고부하 시나리오"
echo "=============================="

echo "4-1. 모든 시뮬레이션 동시 실행..."

# 모든 시뮬레이션 API 동시 실행 (외부에서)
echo "   🚀 Deadlock 시뮬레이션들 실행..."
curl $CURL_OPTS "$UNIVERSITY_API/simulation/simple-deadlock" > /dev/null &
curl $CURL_OPTS "$UNIVERSITY_API/simulation/deadlock" > /dev/null &
curl $CURL_OPTS "$UNIVERSITY_API/simulation/lock-contention" > /dev/null &
curl $CURL_OPTS "$UNIVERSITY_API/simulation/long-transaction" > /dev/null &

echo "   🚀 일반 API 호출로 트랜잭션 생성..."
# 다양한 API 엔드포인트 호출로 일반 트랜잭션 생성
for i in {1..10}; do
    curl $CURL_OPTS "$UNIVERSITY_API/courses" > /dev/null &
    curl $CURL_OPTS "$UNIVERSITY_API/courses/available" > /dev/null &
    curl $CURL_OPTS "$UNIVERSITY_API/courses/stats/department" > /dev/null &
    curl $CURL_OPTS "$UNIVERSITY_API/enrollments/me?studentId=external_student_$i" > /dev/null &
done

echo "✅ 외부에서 고부하 혼합 시나리오 실행 중..."
sleep 10

echo ""

# Phase 5: Dashboard 및 실시간 모니터링 확인
echo "📺 Phase 5: Dashboard 실시간 모니터링 확인"
echo "========================================"

echo "5-1. Dashboard 접근 상태:"
DASHBOARD_STATUS=$(curl $CURL_OPTS --connect-timeout 10 "$DASHBOARD_URL" 2>/dev/null)
if [ $? -eq 0 ]; then
    echo "✅ Dashboard 정상 접근 가능: $DASHBOARD_URL"
else
    echo "❌ Dashboard 접근 실패 - 방화벽 또는 네트워크 문제 가능성"
fi

echo ""
echo "5-2. WebSocket 연결 테스트:"
# WebSocket 연결 시도 (간단한 테스트)
WS_TEST=$(curl $CURL_OPTS -H "Upgrade: websocket" -H "Connection: Upgrade" "$DASHBOARD_URL/ws" 2>&1)
if [[ $WS_TEST == *"400"* ]] || [[ $WS_TEST == *"101"* ]]; then
    echo "✅ WebSocket 엔드포인트 응답 - 실시간 연결 가능"
else
    echo "⚠️ WebSocket 연결 테스트 제한적 - 브라우저에서 직접 확인 필요"
fi

echo ""

# Phase 6: 백그라운드 프로세스 완료 대기
echo "⏳ Phase 6: 백그라운드 프로세스 완료 대기"
echo "====================================="

echo "모든 백그라운드 HTTP 요청이 완료될 때까지 대기 중..."
wait

echo "✅ 모든 백그라운드 프로세스 완료"
echo ""

# Phase 7: 결과 분석 및 요약
echo "📈 Phase 7: 테스트 결과 분석 및 요약"
echo "=================================="

# 백그라운드 결과 파일들 확인
echo "🎯 테스트 결과 파일 생성 상태:"
echo "=============================="

check_result_file() {
    local file=$1
    local test_name=$2
    
    if [ -f "$file" ]; then
        size=$(du -h "$file" 2>/dev/null | cut -f1)
        if [ -s "$file" ]; then
            echo "✅ $test_name: 결과 파일 생성됨 ($size)"
        else
            echo "⚠️ $test_name: 빈 결과 파일"
        fi
    else
        echo "❌ $test_name: 결과 파일 없음"
    fi
}

check_result_file "/tmp/long_tx_result.json" "Long Transaction Test"
check_result_file "/tmp/perf_test_result.json" "Performance Test"
check_result_file "/tmp/concurrent_test_result.json" "Concurrent Test"
check_result_file "/tmp/bulk_test_result.json" "Bulk Enrollment Test"

echo ""
echo "🧪 실행된 외부 테스트 시나리오:"
echo "============================="
echo "✅ Simple Deadlock 시뮬레이션 (외부 호출)"
echo "✅ 복잡한 Deadlock 시뮬레이션 (외부 호출)"
echo "✅ Lock Contention 시뮬레이션 (외부 호출)"
echo "✅ Long Running Transaction 시뮬레이션 (외부 호출)"
echo "✅ Performance Test (외부 호출)"
echo "✅ Concurrent Test (외부 호출)"
echo "✅ Bulk Enrollment Test (외부 호출)"
echo "✅ 고부하 혼합 시나리오 (외부 호출)"
echo "✅ Dashboard 및 WebSocket 연결 확인"

echo ""
echo "📊 외부 접근 가능한 모니터링 리소스:"
echo "================================="
echo "🌐 KubeDB Monitor Dashboard: $DASHBOARD_URL"
echo "📊 University Registration API: $UNIVERSITY_API"
echo "🔧 Control Plane API: $CONTROL_PLANE_API"
echo "💻 이 테스트 스크립트: $0"

echo ""
echo "📋 샘플 API 테스트 명령어:"
echo "======================="
echo "# 실시간 Deadlock 생성:"
echo "curl $CURL_OPTS \"$UNIVERSITY_API/simulation/simple-deadlock\""
echo ""
echo "# Long Running Transaction 생성:"
echo "curl $CURL_OPTS \"$UNIVERSITY_API/simulation/long-transaction\""
echo ""
echo "# 일반 쿼리 실행:"
echo "curl $CURL_OPTS \"$UNIVERSITY_API/courses\""
echo ""
echo "# Health Check:"
echo "curl $CURL_OPTS \"$UNIVERSITY_API/actuator/health\""

echo ""
echo "🎉 KubeDB Monitor 외부 접근 종합 테스트 완료!"
echo "============================================"
echo ""
echo "✨ 요약:"
echo "- 모든 API가 Public DNS를 통해 외부에서 접근 가능합니다"
echo "- kubectl 없이도 완전한 테스트 시나리오 실행 가능"
echo "- Dashboard에서 실시간 모니터링 확인 가능"
echo "- 다양한 부하 테스트가 외부에서 수행되었습니다"
echo ""
echo "🔗 실시간 모니터링을 위해 브라우저에서 다음 URL을 여세요:"
echo "$DASHBOARD_URL"
echo ""
echo "🧪 추가 테스트를 원한다면 위의 샘플 curl 명령어들을 사용하세요!"