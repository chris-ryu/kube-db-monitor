#!/bin/bash

echo "🔍 이벤트 처리 파이프라인 종합 검증 스크립트"
echo "=============================================="
echo "각 서비스 레이어에서 모든 이벤트 타입이 제대로 전달되는지 검증"
echo ""

# Pod 정보 수집
POD_NAME=$(kubectl get pods -n kubedb-monitor-test -l app=university-registration-demo -o jsonpath='{.items[0].metadata.name}')
CONTROL_PLANE_POD=$(kubectl get pods -n kubedb-monitor -l app=kubedb-monitor-control-plane -o jsonpath='{.items[0].metadata.name}')
DASHBOARD_POD=$(kubectl get pods -n kubedb-monitor -l app=kubedb-monitor-dashboard -o jsonpath='{.items[0].metadata.name}')

if [ -z "$POD_NAME" ]; then
    echo "❌ university-registration-demo Pod를 찾을 수 없습니다"
    exit 1
fi

echo "✅ 대상 Pod 확인:"
echo "  - Agent Pod: $POD_NAME"
echo "  - Control Plane Pod: $CONTROL_PLANE_POD"
echo "  - Dashboard Pod: $DASHBOARD_POD"

# 결과 추적을 위한 변수들
RESULTS=()
declare -A EVENT_COUNTS

# 테스트 시작 시간 기록 (로그 필터링용)
TEST_START_TIME=$(date -u +"%Y-%m-%dT%H:%M:%S")
echo "🕐 테스트 시작 시간: $TEST_START_TIME"

echo ""
echo "=================================================="
echo "📊 이벤트 타입별 검증 테스트"
echo "=================================================="

# 1. Query Execution 이벤트 테스트
echo ""
echo "1️⃣ QUERY_EXECUTION 이벤트 테스트"
echo "================================"

echo "코스 목록 조회 API 호출..."
kubectl exec $POD_NAME -n kubedb-monitor-test -c university-registration -- \
    curl -s "http://localhost:8080/api/courses?page=0&size=5" > /dev/null

sleep 2

# Agent에서 SQL 인터셉션 확인
QUERY_AGENT=$(kubectl logs $POD_NAME -n kubedb-monitor-test -c university-registration --since=1m | grep -c "🔍 JDBC 메서드 인터셉트")
RESULTS+=("Query Execution - Agent SQL 인터셉션: $QUERY_AGENT 건")
EVENT_COUNTS[query_agent]=$QUERY_AGENT

# Control Plane에서 query_execution 수신 확인
QUERY_CONTROL=$(kubectl logs $CONTROL_PLANE_POD -n kubedb-monitor --since=1m | grep -c "query_execution")
RESULTS+=("Query Execution - Control Plane 수신: $QUERY_CONTROL 건")
EVENT_COUNTS[query_control]=$QUERY_CONTROL

# 2. Transaction Event 테스트
echo ""
echo "2️⃣ TRANSACTION_EVENT 이벤트 테스트"
echo "================================"

echo "학생 등록 API 호출 (트랜잭션 유발)..."
kubectl exec $POD_NAME -n kubedb-monitor-test -c university-registration -- \
    curl -s -X POST "http://localhost:8080/api/students" \
    -H "Content-Type: application/json" \
    -d '{"name": "Transaction Test Student", "email": "txtest@example.com", "major": "Computer Science"}' > /dev/null

sleep 2

# Control Plane에서 transaction_event 수신 확인
TX_CONTROL=$(kubectl logs $CONTROL_PLANE_POD -n kubedb-monitor --since=1m | grep -c "transaction_event")
RESULTS+=("Transaction Event - Control Plane 수신: $TX_CONTROL 건")
EVENT_COUNTS[tx_control]=$TX_CONTROL

# 3. Long-running Transaction 이벤트 테스트 (수정된 부분)
echo ""
echo "3️⃣ LONG_RUNNING_TRANSACTION 이벤트 테스트"
echo "========================================"

echo "8초 Long-running transaction 실행..."
kubectl exec $POD_NAME -n kubedb-monitor-test -c university-registration -- \
    curl -s -X POST "http://localhost:8080/api/data/long-running-test?duration=8000" > /dev/null &

CURL_PID=$!

# 실행 중 모니터링
for i in {2..10}; do
    sleep 1
    echo "  [$i/10] 모니터링 중..."
    
    # Long-running transaction 감지 확인
    LR_DETECTED=$(kubectl logs $POD_NAME -n kubedb-monitor-test -c university-registration --since=30s | grep -c "Long-running transaction detected")
    if [ $LR_DETECTED -gt 0 ]; then
        echo "    ✅ Long-running transaction 감지됨!"
        break
    fi
done

# 백그라운드 작업 완료 대기
wait $CURL_PID

sleep 3

# Agent에서 Long-running transaction 감지 확인
LR_AGENT=$(kubectl logs $POD_NAME -n kubedb-monitor-test -c university-registration --since=2m | grep -c "Long-running transaction detected")
RESULTS+=("Long-running Transaction - Agent 감지: $LR_AGENT 건")
EVENT_COUNTS[lr_agent]=$LR_AGENT

# Control Plane에서 long_running_transaction 수신 확인
LR_CONTROL=$(kubectl logs $CONTROL_PLANE_POD -n kubedb-monitor --since=2m | grep -c "long_running_transaction")
RESULTS+=("Long-running Transaction - Control Plane 수신: $LR_CONTROL 건")
EVENT_COUNTS[lr_control]=$LR_CONTROL

# Dashboard에서 WebSocket 메시지 확인
if [ ! -z "$DASHBOARD_POD" ]; then
    LR_DASHBOARD=$(kubectl logs $DASHBOARD_POD -n kubedb-monitor --since=2m | grep -c "long_running")
    RESULTS+=("Long-running Transaction - Dashboard 수신: $LR_DASHBOARD 건")
    EVENT_COUNTS[lr_dashboard]=$LR_DASHBOARD
fi

# 4. Deadlock Event 테스트
echo ""
echo "4️⃣ DEADLOCK_EVENT 이벤트 테스트"
echo "=============================="

echo "데드락 시뮬레이션 실행..."
kubectl exec $POD_NAME -n kubedb-monitor-test -c university-registration -- \
    curl -s -X POST "http://localhost:8080/api/data/simulate-deadlock?concurrency=2" > /dev/null

sleep 3

# Agent에서 데드락 감지 확인
DL_AGENT=$(kubectl logs $POD_NAME -n kubedb-monitor-test -c university-registration --since=1m | grep -c "DEADLOCK DETECTED\|deadlock detected")
RESULTS+=("Deadlock - Agent 감지: $DL_AGENT 건")
EVENT_COUNTS[dl_agent]=$DL_AGENT

# Control Plane에서 deadlock_event 수신 확인
DL_CONTROL=$(kubectl logs $CONTROL_PLANE_POD -n kubedb-monitor --since=1m | grep -c "deadlock_event\|deadlock_detected")
RESULTS+=("Deadlock - Control Plane 수신: $DL_CONTROL 건")
EVENT_COUNTS[dl_control]=$DL_CONTROL

# 5. System Metrics 이벤트 테스트
echo ""
echo "5️⃣ SYSTEM_METRICS 이벤트 테스트"
echo "==============================="

sleep 5  # Connection Pool 메트릭이 주기적으로 전송되므로 대기

# Control Plane에서 system_metrics 수신 확인 (최근 1분간)
SYS_CONTROL=$(kubectl logs $CONTROL_PLANE_POD -n kubedb-monitor --since=1m | grep -c "system_metrics")
RESULTS+=("System Metrics - Control Plane 수신: $SYS_CONTROL 건")
EVENT_COUNTS[sys_control]=$SYS_CONTROL

echo ""
echo "=================================================="
echo "📊 종합 검증 결과"
echo "=================================================="

# 결과 출력
for result in "${RESULTS[@]}"; do
    echo "✓ $result"
done

echo ""
echo "🎯 이벤트 처리 파이프라인 상태:"
echo "==============================="

# 각 이벤트 타입별 상태 확인
declare -A EVENT_STATUS

# Query Execution
if [ ${EVENT_COUNTS[query_agent]} -gt 0 ] && [ ${EVENT_COUNTS[query_control]} -gt 0 ]; then
    EVENT_STATUS[query]="✅ PASS"
else
    EVENT_STATUS[query]="❌ FAIL"
fi

# Transaction Event  
if [ ${EVENT_COUNTS[tx_control]} -gt 0 ]; then
    EVENT_STATUS[transaction]="✅ PASS"
else
    EVENT_STATUS[transaction]="❌ FAIL"
fi

# Long-running Transaction
if [ ${EVENT_COUNTS[lr_agent]} -gt 0 ] && [ ${EVENT_COUNTS[lr_control]} -gt 0 ]; then
    EVENT_STATUS[longrunning]="✅ PASS"
else
    EVENT_STATUS[longrunning]="❌ FAIL"
fi

# Deadlock
if [ ${EVENT_COUNTS[dl_agent]} -gt 0 ] && [ ${EVENT_COUNTS[dl_control]} -gt 0 ]; then
    EVENT_STATUS[deadlock]="✅ PASS"
else
    EVENT_STATUS[deadlock]="❌ FAIL"
fi

# System Metrics
if [ ${EVENT_COUNTS[sys_control]} -gt 0 ]; then
    EVENT_STATUS[system]="✅ PASS"
else
    EVENT_STATUS[system]="❌ FAIL"
fi

echo "1. Query Execution: ${EVENT_STATUS[query]}"
echo "2. Transaction Event: ${EVENT_STATUS[transaction]}"  
echo "3. Long-running Transaction: ${EVENT_STATUS[longrunning]}"
echo "4. Deadlock Event: ${EVENT_STATUS[deadlock]}"
echo "5. System Metrics: ${EVENT_STATUS[system]}"

# 전체 성공률 계산
PASS_COUNT=0
TOTAL_COUNT=5

for status in "${EVENT_STATUS[@]}"; do
    if [[ "$status" == "✅ PASS" ]]; then
        ((PASS_COUNT++))
    fi
done

SUCCESS_RATE=$((PASS_COUNT * 100 / TOTAL_COUNT))

echo ""
echo "🏆 전체 성공률: $SUCCESS_RATE% ($PASS_COUNT/$TOTAL_COUNT)"

if [ $SUCCESS_RATE -eq 100 ]; then
    echo "🎉 모든 이벤트 처리 파이프라인이 정상 작동합니다!"
elif [ $SUCCESS_RATE -ge 80 ]; then
    echo "🔄 대부분의 이벤트가 정상 처리되지만 일부 확인이 필요합니다."
else
    echo "⚠️ 이벤트 처리 파이프라인에 문제가 있습니다. 점검이 필요합니다."
fi

echo ""
echo "🌐 실시간 확인:"
echo "Dashboard: https://kube-db-mon-dashboard.bitgaram.info"
echo "Control Plane: https://kube-db-mon-controlplane.bitgaram.info"

echo ""
echo "=============================================="
echo "🔍 이벤트 파이프라인 검증 완료"
echo "=============================================="

# 결과 반환 (스크립트 종료 코드)
if [ $SUCCESS_RATE -eq 100 ]; then
    exit 0
elif [ $SUCCESS_RATE -ge 80 ]; then
    exit 1
else
    exit 2
fi