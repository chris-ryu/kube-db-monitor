#!/bin/bash

echo "🐌 KubeDB Monitor Long Running Transaction 테스트"
echo "=============================================="

# Pod 정보 가져오기
POD_NAME=$(kubectl get pods -n kubedb-monitor-test -l app=university-registration-demo --field-selector=status.phase=Running -o jsonpath='{.items[0].metadata.name}')
NAMESPACE="kubedb-monitor-test"

if [ -z "$POD_NAME" ]; then
  echo "❌ University Registration Demo Pod를 찾을 수 없습니다."
  exit 1
fi

echo "🎯 테스트 대상 Pod: $POD_NAME"
echo ""

# 1. 기본 Long Running Transaction 테스트 (8초)
echo "1️⃣ 기본 Long Running Transaction 테스트 (8초)"
echo "=============================================="
echo "8초간 실행되는 long running transaction 시뮬레이션..."

LRT_RESPONSE_8s=$(kubectl exec $POD_NAME -n $NAMESPACE -- curl -s -X POST \
  "http://localhost:8080/api/data/long-running-test?duration=8000")

echo "응답: $LRT_RESPONSE_8s"
echo ""

# 2. 긴 Long Running Transaction 테스트 (15초)
echo "2️⃣ 긴 Long Running Transaction 테스트 (15초)"
echo "==========================================="
echo "15초간 실행되는 long running transaction 시뮬레이션..."

LRT_RESPONSE_15s=$(kubectl exec $POD_NAME -n $NAMESPACE -- curl -s -X POST \
  "http://localhost:8080/api/data/long-running-test?duration=15000")

echo "응답: $LRT_RESPONSE_15s"
echo ""

# 3. 매우 긴 Long Running Transaction 테스트 (30초)
echo "3️⃣ 매우 긴 Long Running Transaction 테스트 (30초)"
echo "=============================================="
echo "30초간 실행되는 매우 긴 long running transaction 시뮬레이션..."

# 백그라운드에서 실행하여 병렬 처리
kubectl exec $POD_NAME -n $NAMESPACE -- curl -s -X POST \
  "http://localhost:8080/api/data/long-running-test?duration=30000" &

LRT_PID=$!
echo "Long Running Transaction이 백그라운드에서 실행 중입니다 (PID: $LRT_PID)..."
echo ""

# 4. 동시 다중 Long Running Transaction 테스트
echo "4️⃣ 동시 다중 Long Running Transaction 테스트"
echo "=========================================="
echo "5개의 동시 long running transaction 실행..."

PIDS=()
for i in {1..5}; do
  duration=$((5000 + i * 2000))  # 5초, 7초, 9초, 11초, 13초
  echo "Long Running Transaction $i 시작 (${duration}ms)..."
  
  kubectl exec $POD_NAME -n $NAMESPACE -- curl -s -X POST \
    "http://localhost:8080/api/data/long-running-test?duration=$duration" > /tmp/lrt_$i.log &
  
  PIDS+=($!)
  sleep 1
done

echo "✅ 5개의 동시 Long Running Transaction 실행 중..."
echo ""

# 5. 실시간 Agent 로그 모니터링
echo "5️⃣ 실시간 Agent 로그 모니터링"
echo "============================"
echo "Long Running Transaction 감지 로그를 실시간으로 확인 중 (15초간)..."

# 15초간 실시간 로그 모니터링
timeout 15s kubectl logs $POD_NAME -n $NAMESPACE -f | grep -E "(LONG.RUNNING|🐌|Long)" &
LOG_PID=$!

sleep 15
kill $LOG_PID 2>/dev/null

echo ""
echo "최근 Long Running Transaction 관련 로그:"
kubectl logs $POD_NAME -n $NAMESPACE --tail=30 | grep -E -i "(long.running|🐌|duration)" || echo "Long Running Transaction 관련 로그를 찾을 수 없습니다."
echo ""

# 6. 동시 트랜잭션 완료 대기
echo "6️⃣ 동시 트랜잭션 완료 대기"
echo "========================="
echo "실행 중인 트랜잭션들이 완료될 때까지 대기 중..."

# 백그라운드 프로세스 완료 대기
if [ ! -z "$LRT_PID" ] && kill -0 $LRT_PID 2>/dev/null; then
  echo "30초 Long Running Transaction 대기 중..."
  wait $LRT_PID
  echo "✅ 30초 Long Running Transaction 완료"
fi

# 다중 트랜잭션 완료 대기
for pid in "${PIDS[@]}"; do
  if kill -0 $pid 2>/dev/null; then
    wait $pid
  fi
done

echo "✅ 모든 동시 트랜잭션 완료"
echo ""

# 7. Control Plane에서 Long Running Transaction 이벤트 확인
echo "7️⃣ Control Plane Long Running Transaction 이벤트 확인"
echo "==============================================="
CONTROL_PLANE_POD=$(kubectl get pods -n kubedb-monitor -l app=kubedb-monitor-control-plane --field-selector=status.phase=Running -o jsonpath='{.items[0].metadata.name}')

if [ ! -z "$CONTROL_PLANE_POD" ]; then
  echo "Control Plane Pod: $CONTROL_PLANE_POD"
  echo "Long Running Transaction 이벤트 수신 로그:"
  kubectl logs $CONTROL_PLANE_POD -n kubedb-monitor --tail=20 | grep -E -i "(long.running|duration|websocket)" || echo "Control Plane에서 Long Running Transaction 이벤트 로그를 찾을 수 없습니다."
else
  echo "❌ Control Plane Pod를 찾을 수 없습니다."
fi
echo ""

# 8. Dashboard에서 Long Running Transaction 확인
echo "8️⃣ Dashboard Long Running Transaction 확인"
echo "========================================"
echo "대시보드에서 Long Running Transaction 이벤트를 실시간으로 확인하세요:"
echo "🌐 https://kube-db-mon-dashboard.bitgaram.info"
echo ""

# 9. 최종 상태 확인
echo "9️⃣ 최종 테스트 상태"
echo "=================="
echo "✅ 8초 LRT 테스트: 완료"
echo "✅ 15초 LRT 테스트: 완료"
echo "✅ 30초 LRT 테스트: 완료"
echo "✅ 다중 동시 LRT 테스트: 완료"
echo "✅ 실시간 로그 모니터링: 완료"
echo ""
echo "🎉 Long Running Transaction 테스트 완료!"
echo "대시보드에서 실시간으로 Long Running Transaction 이벤트를 확인하세요."

# 결과 파일들 정리
echo ""
echo "📝 생성된 결과 파일들:"
if ls /tmp/lrt_*.log 1> /dev/null 2>&1; then
  echo "- 트랜잭션 로그들: $(ls /tmp/lrt_*.log | wc -l)개 파일"
fi