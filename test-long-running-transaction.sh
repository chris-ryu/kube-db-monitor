#!/bin/bash

echo "🔧 Long Running Transaction 테스트 시작"

POD_NAME=$(kubectl get pods -n kubedb-monitor-test --no-headers -o custom-columns=":metadata.name" | grep university-registration)
echo "Testing with pod: $POD_NAME"

echo "현재 대시보드 상태를 확인 후 Long Running Transaction 실행..."

# Long Running Transaction을 시뮬레이션하기 위해 Pod 내에서 직접 테스트
kubectl exec -n kubedb-monitor-test $POD_NAME -c university-registration -- /bin/bash -c "
echo 'Long Running Transaction 시뮬레이션 시작';
java -cp '/app/BOOT-INF/lib/postgresql-*.jar:/app/BOOT-INF/classes' -Djava.javaagent.path=/app/kubedb-monitor-agent.jar -Dspring.profiles.active=development io.kubedb.monitor.agent.JDBCMethodInterceptor &
sleep 6;
echo 'Long Running Transaction 시뮬레이션 완료 (6초)';
"

echo "Long Running Transaction 실행 후 Agent 로그 확인..."
kubectl logs -n kubedb-monitor-test $POD_NAME -c university-registration | grep -E "(LONG RUNNING|Long Running|🐌)" | tail -10

echo "Controller 로그에서 이벤트 수신 확인..."
kubectl logs -n kubedb-monitor kubedb-monitor-control-plane-* | grep -E "(LONG_RUNNING|Long Running)" | tail -5