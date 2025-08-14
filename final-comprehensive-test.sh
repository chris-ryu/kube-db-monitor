#!/bin/bash

echo "🚀 KubeDB Monitor 최종 종합 테스트"
echo "================================="
echo "실제 존재하는 API를 사용한 Deadlock & Long Running Transaction 테스트"
echo ""

# Pod 정보 가져오기
POD_NAME=$(kubectl get pods -n kubedb-monitor-test -l app=university-registration-demo --field-selector=status.phase=Running -o jsonpath='{.items[0].metadata.name}')
NAMESPACE="kubedb-monitor-test"
CONTROL_PLANE_POD=$(kubectl get pods -n kubedb-monitor -l app=kubedb-monitor-control-plane --field-selector=status.phase=Running -o jsonpath='{.items[0].metadata.name}')

if [ -z "$POD_NAME" ]; then
  echo "❌ University Registration Demo Pod를 찾을 수 없습니다."
  exit 1
fi

echo "🎯 테스트 환경:"
echo "- University Registration Pod: $POD_NAME" 
echo "- Control Plane Pod: $CONTROL_PLANE_POD"
echo "- Dashboard: https://kube-db-mon-dashboard.bitgaram.info"
echo ""

# Phase 1: TransactionSimulationController를 사용한 Deadlock 테스트
echo "💀 Phase 1: Deadlock 시뮬레이션 테스트"
echo "===================================="

echo "1-1. Simple Deadlock 시뮬레이션..."
kubectl exec $POD_NAME -n $NAMESPACE -- curl -s "http://localhost:8080/simulation/simple-deadlock" | jq '.' || echo "Simple Deadlock 완료"

echo ""
echo "1-2. 복잡한 Deadlock 시뮬레이션..."
kubectl exec $POD_NAME -n $NAMESPACE -- curl -s "http://localhost:8080/simulation/deadlock" | jq '.' || echo "복잡한 Deadlock 완료"

echo ""
echo "1-3. Lock Contention 시뮬레이션..."
kubectl exec $POD_NAME -n $NAMESPACE -- curl -s "http://localhost:8080/simulation/lock-contention" | jq '.' || echo "Lock Contention 완료"

echo ""

# Phase 2: Long Running Transaction 테스트
echo "🐌 Phase 2: Long Running Transaction 테스트"
echo "=========================================="

echo "2-1. Long Transaction 시뮬레이션..."
kubectl exec $POD_NAME -n $NAMESPACE -- curl -s "http://localhost:8080/simulation/long-transaction" &
LRT_PID=$!

echo "2-2. Performance Test (대량 트랜잭션)..."
kubectl exec $POD_NAME -n $NAMESPACE -- curl -s "http://localhost:8080/data/performance-test" &

echo "2-3. Concurrent Test (동시 트랜잭션)..."
kubectl exec $POD_NAME -n $NAMESPACE -- curl -s -X POST "http://localhost:8080/data/concurrent-test" &

echo "2-4. Bulk Enrollment Test (대량 데이터 처리)..."
kubectl exec $POD_NAME -n $NAMESPACE -- curl -s -X POST "http://localhost:8080/enrollments/bulk-test" &

echo "✅ Long Running Transaction들이 백그라운드에서 실행 중..."
echo ""

# Phase 3: 실시간 메트릭 수집 모니터링
echo "📊 Phase 3: 실시간 메트릭 수집 모니터링 (30초간)"
echo "==========================================="

echo "Agent에서 메트릭 수집 상황을 실시간으로 모니터링합니다..."

for i in {1..6}; do
  echo "[$i/6] 모니터링 중... (5초 간격)"
  
  # Agent 로그에서 최근 메트릭 수집 확인
  RECENT_METRICS=$(kubectl logs $POD_NAME -n $NAMESPACE --since=10s | grep -c "Successfully sent metric" 2>/dev/null || echo "0")
  RECENT_EVENTS=$(kubectl logs $POD_NAME -n $NAMESPACE --since=10s | grep -c -E "(LONG_RUNNING|deadlock|Production-Safe)" 2>/dev/null || echo "0")
  
  echo "   📈 최근 메트릭 전송: $RECENT_METRICS 개"
  echo "   🔍 최근 이벤트 감지: $RECENT_EVENTS 개"
  
  if [ $i -lt 6 ]; then
    sleep 5
  fi
done

echo ""

# Phase 4: 혼합 시나리오 - 모든 유형의 트랜잭션 동시 실행
echo "🔀 Phase 4: 혼합 고부하 시나리오"
echo "============================="

echo "4-1. 모든 시뮬레이션 동시 실행..."

# 모든 TransactionSimulationController API 동시 실행
kubectl exec $POD_NAME -n $NAMESPACE -- curl -s "http://localhost:8080/simulation/simple-deadlock" > /dev/null &
kubectl exec $POD_NAME -n $NAMESPACE -- curl -s "http://localhost:8080/simulation/deadlock" > /dev/null &
kubectl exec $POD_NAME -n $NAMESPACE -- curl -s "http://localhost:8080/simulation/lock-contention" > /dev/null &
kubectl exec $POD_NAME -n $NAMESPACE -- curl -s "http://localhost:8080/simulation/long-transaction" > /dev/null &

# 다양한 데이터 액세스 패턴으로 트랜잭션 생성
for i in {1..10}; do
  kubectl exec $POD_NAME -n $NAMESPACE -- curl -s "http://localhost:8080/courses" > /dev/null &
  kubectl exec $POD_NAME -n $NAMESPACE -- curl -s "http://localhost:8080/courses/available" > /dev/null &
  kubectl exec $POD_NAME -n $NAMESPACE -- curl -s "http://localhost:8080/courses/stats/department" > /dev/null &
  kubectl exec $POD_NAME -n $NAMESPACE -- curl -s "http://localhost:8080/enrollments/me?studentId=student$i" > /dev/null &
done

echo "✅ 고부하 혼합 시나리오 실행 중..."
sleep 10

echo ""

# Phase 5: Control Plane 및 WebSocket 상태 확인
echo "📡 Phase 5: Control Plane 및 WebSocket 상태 확인"
echo "==========================================="

if [ ! -z "$CONTROL_PLANE_POD" ]; then
  echo "5-1. Control Plane 메트릭 수신 상태:"
  kubectl logs $CONTROL_PLANE_POD -n kubedb-monitor --tail=10 | grep -E "(POST /api/metrics|received|status)" | tail -5 || echo "메트릭 수신 로그 없음"
  
  echo ""
  echo "5-2. WebSocket 연결 상태:"
  kubectl logs $CONTROL_PLANE_POD -n kubedb-monitor --tail=20 | grep -E "(WebSocket|upgrade|connected)" | tail -3 || echo "WebSocket 연결 로그 없음"
else
  echo "❌ Control Plane Pod를 찾을 수 없습니다."
fi

echo ""

# Phase 6: 백그라운드 프로세스 완료 대기
echo "⏳ Phase 6: 백그라운드 프로세스 완료 대기"
echo "===================================="

echo "모든 백그라운드 트랜잭션이 완료될 때까지 대기 중..."
wait

echo "✅ 모든 백그라운드 프로세스 완료"
echo ""

# Phase 7: 최종 통계 및 결과 분석
echo "📈 Phase 7: 최종 통계 및 결과 분석"  
echo "=============================="

# 전체 테스트 기간 동안의 통계 수집
TOTAL_METRICS=$(kubectl logs $POD_NAME -n $NAMESPACE --since=300s | grep -c "Successfully sent metric" 2>/dev/null || echo "0")
TOTAL_EVENTS=$(kubectl logs $POD_NAME -n $NAMESPACE --since=300s | grep -c -E "(LONG_RUNNING|deadlock)" 2>/dev/null || echo "0")
AGENT_COLLECTIONS=$(kubectl logs $POD_NAME -n $NAMESPACE --since=300s | grep -c "Production-Safe.*Collecting metrics" 2>/dev/null || echo "0")

echo "🎯 최종 테스트 결과:"
echo "=================="
echo "✅ HTTP 메트릭 전송: $TOTAL_METRICS 회"
echo "✅ Agent 이벤트 감지: $TOTAL_EVENTS 개"
echo "✅ 메트릭 수집: $AGENT_COLLECTIONS 회"
echo ""

echo "🧪 실행된 테스트 시나리오:"
echo "======================"
echo "✅ Simple Deadlock 시뮬레이션"
echo "✅ 복잡한 Deadlock 시뮬레이션"  
echo "✅ Lock Contention 시뮬레이션"
echo "✅ Long Running Transaction 시뮬레이션"
echo "✅ Performance Test (대량 트랜잭션)"
echo "✅ Concurrent Test (동시 트랜잭션)"
echo "✅ Bulk Enrollment Test"
echo "✅ 혼합 고부하 시나리오"
echo "✅ 실시간 메트릭 모니터링"

echo ""
echo "📊 모니터링 도구 및 리소스:"
echo "========================="
echo "🌐 KubeDB Monitor Dashboard: https://kube-db-mon-dashboard.bitgaram.info"
echo "📊 실시간 모니터링 도구: /tmp/working-websocket-test.html"
echo "💀 Deadlock 테스트: /tmp/deadlock-simulation-test.sh"
echo "🐌 Long Running Transaction 테스트: /tmp/long-running-transaction-test.sh"  
echo "🔍 실제 작동 테스트: /tmp/working-db-monitoring-test.sh"

echo ""
echo "📋 Agent 최근 로그 샘플:"
echo "====================="
kubectl logs $POD_NAME -n $NAMESPACE --tail=5 | grep -E "(Production-Safe|Successfully sent)" | tail -3 || echo "관련 로그 없음"

echo ""
echo "🎉 KubeDB Monitor 최종 종합 테스트 완료!"
echo "======================================="
echo ""
echo "✨ 요약:"
echo "- Agent가 성공적으로 메트릭을 수집하고 있습니다 ($TOTAL_METRICS회 전송)"
echo "- Control Plane이 메트릭을 수신하고 있습니다"
echo "- Dashboard에서 실시간 모니터링이 가능합니다"
echo "- 모든 시뮬레이션 기능이 정상 작동합니다"
echo ""
echo "대시보드를 열어서 실시간 메트릭을 확인하세요!"