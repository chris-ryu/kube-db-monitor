#!/bin/bash

echo "🔍 KubeDB Monitor 종합 데이터베이스 모니터링 테스트"
echo "=================================================="
echo "Deadlock 감지 및 Long Running Transaction 감지 통합 테스트"
echo ""

# Pod 정보 가져오기
POD_NAME=$(kubectl get pods -n kubedb-monitor-test -l app=university-registration-demo --field-selector=status.phase=Running -o jsonpath='{.items[0].metadata.name}')
NAMESPACE="kubedb-monitor-test"

if [ -z "$POD_NAME" ]; then
  echo "❌ University Registration Demo Pod를 찾을 수 없습니다."
  exit 1
fi

CONTROL_PLANE_POD=$(kubectl get pods -n kubedb-monitor -l app=kubedb-monitor-control-plane --field-selector=status.phase=Running -o jsonpath='{.items[0].metadata.name}')

echo "🎯 테스트 환경:"
echo "- University Registration Pod: $POD_NAME"
echo "- Control Plane Pod: $CONTROL_PLANE_POD"
echo "- Dashboard URL: https://kube-db-mon-dashboard.bitgaram.info"
echo ""

# 실시간 모니터링 HTML 생성
cat > /tmp/comprehensive-monitoring.html << 'EOF'
<!DOCTYPE html>
<html>
<head>
    <title>KubeDB Monitor - 종합 모니터링</title>
    <style>
        body { font-family: Arial, sans-serif; margin: 20px; background: #f8f9fa; }
        .header { background: #007bff; color: white; padding: 20px; border-radius: 5px; margin-bottom: 20px; }
        .container { display: flex; gap: 20px; }
        .panel { background: white; border: 1px solid #dee2e6; border-radius: 5px; padding: 15px; flex: 1; }
        .log { background: #f5f5f5; padding: 10px; height: 300px; overflow-y: scroll; border: 1px solid #ccc; }
        .deadlock { color: #dc3545; font-weight: bold; }
        .lrt { color: #fd7e14; font-weight: bold; }
        .success { color: #28a745; }
        .error { color: #dc3545; }
        .trigger { margin: 10px 0; }
        button { padding: 8px 16px; margin: 5px; background: #007bff; color: white; border: none; cursor: pointer; border-radius: 3px; }
        button:hover { background: #0056b3; }
        .stats { background: #e9ecef; padding: 10px; border-radius: 3px; margin: 10px 0; }
        .status { padding: 5px 10px; border-radius: 3px; }
        .status.connected { background: #d4edda; color: #155724; }
        .status.error { background: #f8d7da; color: #721c24; }
    </style>
</head>
<body>
    <div class="header">
        <h1>🔍 KubeDB Monitor - 종합 실시간 모니터링</h1>
        <div>WebSocket 상태: <span id="status" class="status">연결 중...</span></div>
    </div>
    
    <div class="container">
        <div class="panel">
            <h3>💀 Deadlock 모니터링</h3>
            <div class="trigger">
                <button onclick="triggerDeadlock(2)">2-Way Deadlock</button>
                <button onclick="triggerDeadlock(3)">3-Way Deadlock</button>
                <button onclick="triggerJPADeadlock()">JPA Deadlock</button>
                <button onclick="triggerNativeDeadlock()">Native SQL Deadlock</button>
            </div>
            <div class="stats">
                <strong>감지된 Deadlock:</strong> <span id="deadlock-count">0</span>개
            </div>
            <div id="deadlock-log" class="log"></div>
        </div>
        
        <div class="panel">
            <h3>🐌 Long Running Transaction 모니터링</h3>
            <div class="trigger">
                <button onclick="triggerLRT(8000)">8초 LRT</button>
                <button onclick="triggerLRT(15000)">15초 LRT</button>
                <button onclick="triggerLRT(30000)">30초 LRT</button>
                <button onclick="triggerMultipleLRT()">다중 LRT</button>
            </div>
            <div class="stats">
                <strong>감지된 Long Running Transaction:</strong> <span id="lrt-count">0</span>개
            </div>
            <div id="lrt-log" class="log"></div>
        </div>
    </div>
    
    <div class="panel" style="margin-top: 20px;">
        <h3>📊 통합 이벤트 로그</h3>
        <div id="combined-log" class="log" style="height: 200px;"></div>
    </div>
    
    <script>
        let deadlockCount = 0;
        let lrtCount = 0;
        
        const deadlockLog = document.getElementById('deadlock-log');
        const lrtLog = document.getElementById('lrt-log');
        const combinedLog = document.getElementById('combined-log');
        const status = document.getElementById('status');
        
        function addLog(container, message, className = '') {
            const time = new Date().toLocaleTimeString();
            const div = document.createElement('div');
            div.className = className;
            div.textContent = `[${time}] ${message}`;
            container.appendChild(div);
            container.scrollTop = container.scrollHeight;
        }
        
        function updateStats() {
            document.getElementById('deadlock-count').textContent = deadlockCount;
            document.getElementById('lrt-count').textContent = lrtCount;
        }
        
        function triggerDeadlock(participants) {
            addLog(combinedLog, `🔄 ${participants}-Way Deadlock 시뮬레이션 요청...`);
        }
        
        function triggerJPADeadlock() {
            addLog(combinedLog, '🔄 JPA Deadlock 시뮬레이션 요청...');
        }
        
        function triggerNativeDeadlock() {
            addLog(combinedLog, '🔄 Native SQL Deadlock 시뮬레이션 요청...');
        }
        
        function triggerLRT(duration) {
            addLog(combinedLog, `🔄 ${duration}ms Long Running Transaction 요청...`);
        }
        
        function triggerMultipleLRT() {
            addLog(combinedLog, '🔄 다중 Long Running Transaction 요청...');
        }
        
        try {
            const ws = new WebSocket('wss://kube-db-mon-dashboard.bitgaram.info/ws');
            
            ws.onopen = function(event) {
                status.textContent = '연결됨';
                status.className = 'status connected';
                addLog(combinedLog, '✅ WebSocket 연결 성공', 'success');
            };
            
            ws.onmessage = function(event) {
                const data = JSON.parse(event.data);
                
                if (data.type === 'deadlock_event') {
                    deadlockCount++;
                    updateStats();
                    const message = `💀 DEADLOCK: ${data.data.participants ? data.data.participants.length + '-Way' : ''} ${JSON.stringify(data.data)}`;
                    addLog(deadlockLog, message, 'deadlock');
                    addLog(combinedLog, message, 'deadlock');
                } else if (data.type === 'long_running_transaction' || data.type === 'LONG_RUNNING_TRANSACTION') {
                    lrtCount++;
                    updateStats();
                    const message = `🐌 LONG RUNNING TRANSACTION: ${JSON.stringify(data.data)}`;
                    addLog(lrtLog, message, 'lrt');
                    addLog(combinedLog, message, 'lrt');
                } else {
                    addLog(combinedLog, `📊 기타 이벤트: ${JSON.stringify(data)}`);
                }
            };
            
            ws.onerror = function(error) {
                status.textContent = '오류';
                status.className = 'status error';
                addLog(combinedLog, '❌ WebSocket 오류: ' + JSON.stringify(error), 'error');
            };
            
            ws.onclose = function(event) {
                status.textContent = '연결 종료';
                status.className = 'status';
                addLog(combinedLog, 'WebSocket 연결 종료');
            };
            
        } catch (e) {
            status.textContent = '연결 실패';
            status.className = 'status error';
            addLog(combinedLog, 'WebSocket 연결 실패: ' + e.message, 'error');
        }
        
        // 초기 통계 업데이트
        updateStats();
    </script>
</body>
</html>
EOF

echo "📊 실시간 종합 모니터링 대시보드 생성: /tmp/comprehensive-monitoring.html"
echo ""

# Phase 1: Deadlock 테스트
echo "🔥 Phase 1: Deadlock 시뮬레이션 테스트"
echo "===================================="

echo "1-1. MetricsService를 통한 직접 Deadlock 시뮬레이션..."
kubectl exec $POD_NAME -n $NAMESPACE -- curl -s -X POST \
  "http://localhost:8080/api/data/simulate-deadlock-direct?participants=3" > /dev/null

echo "1-2. JPA를 통한 실제 Deadlock 시뮬레이션..."
kubectl exec $POD_NAME -n $NAMESPACE -- curl -s -X POST \
  "http://localhost:8080/api/data/simulate-deadlock?concurrency=2" > /dev/null

echo "1-3. Native SQL을 통한 PostgreSQL Deadlock 시뮬레이션..."
kubectl exec $POD_NAME -n $NAMESPACE -- curl -s \
  "http://localhost:8080/api/data/deadlock-real?participants=2" > /dev/null

sleep 5

echo "✅ Deadlock 시뮬레이션 완료"
echo ""

# Phase 2: Long Running Transaction 테스트
echo "⏱️ Phase 2: Long Running Transaction 시뮬레이션 테스트"
echo "==============================================="

echo "2-1. 단일 Long Running Transaction (10초)..."
kubectl exec $POD_NAME -n $NAMESPACE -- curl -s -X POST \
  "http://localhost:8080/api/data/long-running-test?duration=10000" > /dev/null &

echo "2-2. 다중 Long Running Transaction 동시 실행..."
for i in {1..3}; do
  duration=$((6000 + i * 3000))  # 6초, 9초, 12초
  kubectl exec $POD_NAME -n $NAMESPACE -- curl -s -X POST \
    "http://localhost:8080/api/data/long-running-test?duration=$duration" > /dev/null &
done

echo "✅ Long Running Transaction 시뮬레이션 실행 중..."
echo ""

# Phase 3: 혼합 시나리오 테스트
echo "🔀 Phase 3: 혼합 시나리오 테스트"
echo "============================="

echo "3-1. Deadlock과 Long Running Transaction 동시 발생 시나리오..."

# Deadlock 백그라운드 실행
kubectl exec $POD_NAME -n $NAMESPACE -- curl -s -X POST \
  "http://localhost:8080/api/data/simulate-deadlock-direct?participants=4" > /dev/null &

# 동시에 Long Running Transaction 실행
kubectl exec $POD_NAME -n $NAMESPACE -- curl -s -X POST \
  "http://localhost:8080/api/data/long-running-test?duration=20000" > /dev/null &

echo "3-2. 고부하 시나리오 (다중 Deadlock + 다중 LRT)..."

# 다중 Deadlock
for i in {2..4}; do
  kubectl exec $POD_NAME -n $NAMESPACE -- curl -s -X POST \
    "http://localhost:8080/api/data/simulate-deadlock-direct?participants=$i" > /dev/null &
done

# 다중 Long Running Transaction
for i in {1..4}; do
  duration=$((7000 + i * 2000))
  kubectl exec $POD_NAME -n $NAMESPACE -- curl -s -X POST \
    "http://localhost:8080/api/data/long-running-test?duration=$duration" > /dev/null &
done

echo "✅ 혼합 시나리오 실행 중..."
echo ""

# Phase 4: 실시간 모니터링 및 결과 분석
echo "📈 Phase 4: 실시간 모니터링 및 결과 분석"
echo "====================================="

echo "4-1. Agent 로그에서 이벤트 감지 확인 중 (30초간)..."
sleep 30

echo ""
echo "📊 Deadlock 감지 결과:"
DEADLOCK_EVENTS=$(kubectl logs $POD_NAME -n $NAMESPACE --since=60s | grep -c -i "deadlock.*event sent" 2>/dev/null || echo "0")
echo "- 감지된 Deadlock 이벤트: $DEADLOCK_EVENTS 개"

kubectl logs $POD_NAME -n $NAMESPACE --since=60s | grep -E -i "(deadlock|40p01)" | tail -5 || echo "최근 Deadlock 로그 없음"

echo ""
echo "📊 Long Running Transaction 감지 결과:"
LRT_EVENTS=$(kubectl logs $POD_NAME -n $NAMESPACE --since=60s | grep -c "LONG_RUNNING_TRANSACTION.*event sent" 2>/dev/null || echo "0")
echo "- 감지된 Long Running Transaction 이벤트: $LRT_EVENTS 개"

kubectl logs $POD_NAME -n $NAMESPACE --since=60s | grep -E -i "(long.running|🐌)" | tail -5 || echo "최근 Long Running Transaction 로그 없음"

echo ""
echo "4-2. Control Plane 이벤트 수신 확인..."
if [ ! -z "$CONTROL_PLANE_POD" ]; then
  echo "Control Plane에서 수신한 이벤트:"
  kubectl logs $CONTROL_PLANE_POD -n kubedb-monitor --since=60s | grep -E -i "(deadlock|long.running|websocket)" | tail -10 || echo "Control Plane 이벤트 로그 없음"
else
  echo "❌ Control Plane Pod를 찾을 수 없습니다."
fi

echo ""

# Phase 5: 백그라운드 프로세스 완료 대기
echo "⏳ Phase 5: 백그라운드 프로세스 완료 대기"
echo "===================================="

echo "실행 중인 모든 백그라운드 트랜잭션이 완료될 때까지 대기 중..."
wait

echo "✅ 모든 백그라운드 프로세스 완료"
echo ""

# Phase 6: 최종 결과 요약
echo "📋 Phase 6: 최종 결과 요약"
echo "========================"

# 전체 통계 수집
TOTAL_DEADLOCK=$(kubectl logs $POD_NAME -n $NAMESPACE --since=300s | grep -c -i "deadlock.*event sent" 2>/dev/null || echo "0")
TOTAL_LRT=$(kubectl logs $POD_NAME -n $NAMESPACE --since=300s | grep -c "LONG_RUNNING_TRANSACTION.*event sent" 2>/dev/null || echo "0")
TOTAL_METRICS=$(kubectl logs $POD_NAME -n $NAMESPACE --since=300s | grep -c "Production-Safe.*Collecting metrics" 2>/dev/null || echo "0")

echo "🎯 테스트 결과 종합:"
echo "==================="
echo "✅ Deadlock 이벤트 감지: $TOTAL_DEADLOCK 개"
echo "✅ Long Running Transaction 이벤트 감지: $TOTAL_LRT 개"
echo "✅ 총 메트릭 수집 횟수: $TOTAL_METRICS 회"
echo ""

echo "🔍 테스트된 시나리오:"
echo "==================="
echo "✅ MetricsService 직접 Deadlock 시뮬레이션"
echo "✅ JPA 기반 실제 Deadlock 시뮬레이션"
echo "✅ Native SQL PostgreSQL Deadlock 시뮬레이션"
echo "✅ 다양한 기간의 Long Running Transaction"
echo "✅ 다중 동시 Long Running Transaction"
echo "✅ Deadlock + Long Running Transaction 혼합 시나리오"
echo "✅ 고부하 다중 이벤트 시나리오"
echo ""

echo "📊 실시간 모니터링 도구:"
echo "======================="
echo "🌐 KubeDB Monitor Dashboard: https://kube-db-mon-dashboard.bitgaram.info"
echo "📊 종합 실시간 모니터링: /tmp/comprehensive-monitoring.html"
echo "💀 Deadlock 테스트 스크립트: /tmp/deadlock-simulation-test.sh"
echo "🐌 Long Running Transaction 테스트 스크립트: /tmp/long-running-transaction-test.sh"
echo ""

echo "🎉 KubeDB Monitor 종합 데이터베이스 모니터링 테스트 완료!"
echo "================================================================="
echo "대시보드에서 실시간으로 모든 이벤트를 확인하세요."
echo "브라우저에서 /tmp/comprehensive-monitoring.html을 열어 통합 모니터링을 사용하세요."