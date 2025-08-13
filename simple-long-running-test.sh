#!/bin/bash

# Simple Long Running Transaction Test
# 현재 Agent 환경에서 정말로 Long Running Transaction이 감지되는지 확인

RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[0;33m'
BLUE='\033[0;34m'
NC='\033[0m'

echo "🧪 Simple Long Running Transaction Test"
echo "====================================="

LOCAL_URL="http://localhost:8090"
DASHBOARD_URL="http://localhost:3000"

echo "Step 1: 현재 Agent 버전 확인..."
POD_NAME=$(kubectl get pods -n kubedb-monitor-test -l app=university-registration-demo --no-headers -o custom-columns=":metadata.name" | head -1)

if [ -z "$POD_NAME" ]; then
    echo "❌ Cannot find university demo pod"
    exit 1
fi

echo "✅ Found pod: $POD_NAME"

echo ""
echo "Step 2: Agent 로그에서 최근 JDBC interception 확인..."
if kubectl logs -n kubedb-monitor-test "$POD_NAME" --tail=20 | grep -q "🔍 JDBCMethodInterceptor.executeStatement called"; then
    echo "✅ JDBC interception이 작동 중"
else
    echo "⚠️  JDBC interception 메시지를 찾을 수 없음"
fi

echo ""
echo "Step 3: 현재 대시보드 상태 확인 (브라우저에서)..."
echo "📊 Dashboard URL: $DASHBOARD_URL"
echo "   - Long Running Transaction Alert panel 확인"
echo "   - Current Status: 'All Transactions Running Normally' 인지 확인"

echo ""
echo "Step 4: 단순한 쿼리 테스트 (5개 연속)..."
for i in {1..5}; do
    echo "   Query $i..."
    curl -s "$LOCAL_URL/courses" > /dev/null 2>&1 &
done
wait

echo "✅ 5개 쿼리 완료"

echo ""
echo "Step 5: Agent 로그에서 방금 실행된 쿼리 확인..."
echo "🔍 최근 JDBC 로그 (마지막 10라인):"
kubectl logs -n kubedb-monitor-test "$POD_NAME" --tail=10 | grep "🔍 JDBCMethodInterceptor.executeStatement called" | tail -5

echo ""
echo "Step 6: Long Running Transaction 관련 로그 검색..."
if kubectl logs -n kubedb-monitor-test "$POD_NAME" --tail=100 | grep -q "Long Running Transaction\|🐌\|LONG_RUNNING_TRANSACTION"; then
    echo "✅ Long Running Transaction 이벤트 발견!"
    kubectl logs -n kubedb-monitor-test "$POD_NAME" --tail=100 | grep "Long Running Transaction\|🐌\|LONG_RUNNING_TRANSACTION" | tail -3
else
    echo "❌ Long Running Transaction 이벤트를 찾을 수 없음"
fi

echo ""
echo "Step 7: TransactionAware 관련 로그 검색..."
if kubectl logs -n kubedb-monitor-test "$POD_NAME" --tail=100 | grep -q "🔄\|transaction.*aware\|TransactionAware\|onQueryExecution"; then
    echo "✅ TransactionAware 로직 실행 중!"
    kubectl logs -n kubedb-monitor-test "$POD_NAME" --tail=100 | grep "🔄\|transaction.*aware\|TransactionAware\|onQueryExecution" | tail -3
else
    echo "❌ TransactionAware 로직이 실행되지 않고 있음"
    echo "💡 이것이 Long Running Transaction이 감지되지 않는 주요 원인일 수 있습니다"
fi

echo ""
echo "Step 8: Agent 설정 및 초기화 로그 확인..."
if kubectl logs -n kubedb-monitor-test "$POD_NAME" | grep -q "TransactionAwareJDBCInterceptor initialized"; then
    echo "✅ TransactionAwareJDBCInterceptor가 초기화됨"
else
    echo "❌ TransactionAwareJDBCInterceptor 초기화 로그를 찾을 수 없음"
fi

echo ""
echo "🎯 결론 및 권고사항:"
echo "=================="

# Connection extraction 확인
if kubectl logs -n kubedb-monitor-test "$POD_NAME" --tail=50 | grep -q "🔧 SYSTEM.OUT: Attempting to extract connection\|extractConnection"; then
    echo "✅ Connection extraction 로직이 작동 중"
    if kubectl logs -n kubedb-monitor-test "$POD_NAME" --tail=50 | grep -q "✅ SYSTEM.OUT: Connection extraction SUCCESSFUL"; then
        echo "✅ Connection extraction 성공"
    else
        echo "⚠️  Connection extraction이 실패할 수 있음"
    fi
else
    echo "❌ Connection extraction 로직이 실행되지 않음"
    echo "💡 이는 향상된 Agent가 제대로 배포되지 않았을 수 있음을 의미합니다"
fi

echo ""
echo "📋 체크리스트:"
echo "  □ JDBC interception 작동 여부"
echo "  □ Connection extraction 성공 여부"  
echo "  □ TransactionAware 로직 실행 여부"
echo "  □ Long Running Transaction 임계값 (5초) 도달"
echo "  □ 대시보드 패널 업데이트 확인"

echo ""
echo "💡 다음 단계:"
echo "  1. 위 체크리스트에서 실패한 항목 수정"
echo "  2. 필요시 Agent 재배포"
echo "  3. 더 긴 시간의 트랜잭션 시뮬레이션 테스트"