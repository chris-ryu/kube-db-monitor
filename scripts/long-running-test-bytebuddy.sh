#!/bin/bash

echo "📊 ByteBuddy Agent Long-running Transaction 테스트"
echo "======================================"

# Pod 정보 확인
POD_NAME=$(kubectl get pods -n kubedb-monitor-test -l app=university-registration-demo --no-headers -o custom-columns=":metadata.name" | head -1)
echo "Pod: $POD_NAME"

if [ -z "$POD_NAME" ]; then
    echo "❌ Pod를 찾을 수 없습니다"
    exit 1
fi

echo ""
echo "1. Long-running transaction API 호출 - 대량 등록 테스트"
echo "--------------------------------------------"

# Bulk enrollment test (복잡한 트랜잭션 시뮬레이션)
kubectl exec $POD_NAME -n kubedb-monitor-test -- curl -s -X POST \
    http://localhost:8080/api/enrollments/bulk-test \
    -H "Content-Type: application/json" \
    -d '{"studentCount": 10, "courseCount": 5}' || echo "API 호출 실패"

echo ""
echo "2. Deadlock 시뮬레이션으로 복잡한 트랜잭션 테스트"
echo "--------------------------------------------"

# Deadlock 시뮬레이션 (long-running transaction 유발)
kubectl exec $POD_NAME -n kubedb-monitor-test -- curl -s -X POST \
    http://localhost:8080/api/data/simulate-deadlock-direct \
    -H "Content-Type: application/json" \
    -d '5' || echo "Deadlock API 호출 실패"

echo ""
echo "3. 최근 로그에서 Long-running transaction 감지 확인"
echo "--------------------------------------------"
kubectl logs $POD_NAME -n kubedb-monitor-test --tail=20 | grep -E "(Long-running|transaction|AutoCommit|JDBC.*인터셉트)" || echo "관련 로그 없음"

echo ""
echo "✅ 테스트 완료"