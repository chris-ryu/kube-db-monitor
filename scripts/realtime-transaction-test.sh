#!/bin/bash

POD_NAME=$(kubectl get pods -n kubedb-monitor-test -l app=university-registration-demo --no-headers -o custom-columns=":metadata.name" | head -1)

echo "📊 실시간 Long-running Transaction 테스트"
echo "Pod: $POD_NAME"

# Background로 로그 모니터링 시작
kubectl logs $POD_NAME -n kubedb-monitor-test -f | grep -E "(Long-running|transaction.*detected|AutoCommit|JDBC.*인터셉트|트랜잭션)" &
LOG_PID=$!

sleep 2

echo "🚀 여러 복잡한 API를 연속으로 호출하여 long-running transaction 유발..."

# 복잡한 통계 쿼리들 연속 호출
for i in {1..3}; do
    echo "API 호출 $i/3..."
    kubectl exec $POD_NAME -n kubedb-monitor-test -- curl -s "http://localhost:8080/api/courses/stats/department" >/dev/null &
    kubectl exec $POD_NAME -n kubedb-monitor-test -- curl -s "http://localhost:8080/api/courses/stats/professor" >/dev/null &
    kubectl exec $POD_NAME -n kubedb-monitor-test -- curl -s "http://localhost:8080/api/courses/enrollment-details" >/dev/null &
    sleep 2
done

echo "🔄 5초 대기 후 로그 모니터링 종료..."
sleep 5

# 로그 모니터링 종료
kill $LOG_PID 2>/dev/null

echo "✅ 테스트 완료"