#!/bin/bash

echo "🔧 Testing ASM + JDBCMethodInterceptor Long Running Transaction detection"

POD_NAME=$(kubectl get pods -n kubedb-monitor-test --no-headers -o custom-columns=":metadata.name" | grep university-registration)
echo "Testing with pod: $POD_NAME"

echo "Triggering 10 health checks to generate database activity..."
for i in {1..10}; do
    echo "Query $i: $(date)"
    kubectl exec -n kubedb-monitor-test $POD_NAME -c university-registration -- curl -s http://localhost:8080/actuator/health > /dev/null
    sleep 1
done

echo "Checking logs for Long Running Transaction detection..."
kubectl logs -n kubedb-monitor-test $POD_NAME -c university-registration | grep -E "(executeStatement|SYSTEM.OUT|Long Running|LONG RUNNING)" | tail -15