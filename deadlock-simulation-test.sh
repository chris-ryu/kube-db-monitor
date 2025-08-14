#!/bin/bash

echo "💀 KubeDB Monitor Deadlock 시뮬레이션 테스트"
echo "=========================================="

# Pod 정보 가져오기
POD_NAME=$(kubectl get pods -n kubedb-monitor-test -l app=university-registration-demo --field-selector=status.phase=Running -o jsonpath='{.items[0].metadata.name}')
NAMESPACE="kubedb-monitor-test"

if [ -z "$POD_NAME" ]; then
  echo "❌ University Registration Demo Pod를 찾을 수 없습니다."
  exit 1
fi

echo "🎯 테스트 대상 Pod: $POD_NAME"
echo ""

# 1. 직접 Deadlock 시뮬레이션 (MetricsService 사용)
echo "1️⃣ 직접 Deadlock 시뮬레이션 (MetricsService)"
echo "============================================"
echo "3개 참가자로 deadlock 시뮬레이션 실행 중..."

DEADLOCK_RESPONSE=$(kubectl exec $POD_NAME -n $NAMESPACE -- curl -s -X POST \
  "http://localhost:8080/api/data/simulate-deadlock-direct?participants=3")

echo "응답: $DEADLOCK_RESPONSE"
echo ""

# 2. 실제 JPA Deadlock 시뮬레이션
echo "2️⃣ 실제 JPA Deadlock 시뮬레이션"
echo "==============================="
echo "2개 동시 트랜잭션으로 JPA deadlock 시뮬레이션 실행 중..."

JPA_DEADLOCK_RESPONSE=$(kubectl exec $POD_NAME -n $NAMESPACE -- curl -s -X POST \
  "http://localhost:8080/api/data/simulate-deadlock?concurrency=2")

echo "응답: $JPA_DEADLOCK_RESPONSE"
echo ""

# 3. Native SQL Deadlock 시뮬레이션
echo "3️⃣ Native SQL Deadlock 시뮬레이션"
echo "================================="
echo "PostgreSQL Advisory Lock을 사용한 실제 deadlock 시뮬레이션..."

NATIVE_DEADLOCK_RESPONSE=$(kubectl exec $POD_NAME -n $NAMESPACE -- curl -s \
  "http://localhost:8080/api/data/deadlock-real?participants=2")

echo "응답: $NATIVE_DEADLOCK_RESPONSE"
echo ""

# 4. 다중 Deadlock 이벤트 생성
echo "4️⃣ 다중 Deadlock 이벤트 생성"
echo "==========================="
echo "여러 개의 deadlock 이벤트를 연속으로 생성..."

for i in {1..3}; do
  echo "Deadlock 이벤트 $i 생성 중..."
  kubectl exec $POD_NAME -n $NAMESPACE -- curl -s -X POST \
    "http://localhost:8080/api/data/simulate-deadlock-direct?participants=$((2+i))" > /dev/null
  sleep 2
done

echo "✅ 3개의 deadlock 이벤트 생성 완료"
echo ""

# 5. Agent 로그에서 Deadlock 감지 확인
echo "5️⃣ Agent 로그에서 Deadlock 감지 확인"
echo "==================================="
echo "최근 Deadlock 관련 로그 (최근 20줄):"

kubectl logs $POD_NAME -n $NAMESPACE --tail=20 | grep -E -i "(deadlock|40p01|victim)" || echo "Deadlock 관련 로그를 찾을 수 없습니다."
echo ""

# 6. Control Plane에서 Deadlock 이벤트 수집 확인
echo "6️⃣ Control Plane Deadlock 이벤트 수집 확인"
echo "======================================="
CONTROL_PLANE_POD=$(kubectl get pods -n kubedb-monitor -l app=kubedb-monitor-control-plane --field-selector=status.phase=Running -o jsonpath='{.items[0].metadata.name}')

if [ ! -z "$CONTROL_PLANE_POD" ]; then
  echo "Control Plane Pod: $CONTROL_PLANE_POD"
  echo "Deadlock 이벤트 수신 로그:"
  kubectl logs $CONTROL_PLANE_POD -n kubedb-monitor --tail=15 | grep -E -i "(deadlock|websocket|event)" || echo "Control Plane에서 deadlock 이벤트 로그를 찾을 수 없습니다."
else
  echo "❌ Control Plane Pod를 찾을 수 없습니다."
fi
echo ""

# 7. Dashboard에서 Deadlock 확인
echo "7️⃣ Dashboard Deadlock 표시 확인"
echo "==============================="
echo "대시보드에서 deadlock 이벤트를 실시간으로 확인하세요:"
echo "🌐 https://kube-db-mon-dashboard.bitgaram.info"
echo ""

# 8. 최종 상태 확인
echo "8️⃣ 최종 테스트 상태"
echo "=================="
echo "✅ 직접 시뮬레이션: 완료"
echo "✅ JPA 시뮬레이션: 완료" 
echo "✅ Native SQL 시뮬레이션: 완료"
echo "✅ 다중 이벤트 생성: 완료"
echo "✅ 로그 확인: 완료"
echo ""
echo "🎉 Deadlock 시뮬레이션 테스트 완료!"
echo "대시보드에서 실시간으로 deadlock 이벤트를 확인하세요."