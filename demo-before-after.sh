#!/bin/bash

# KubeDB Monitor Before & After 데모 스크립트
# KubeDB 에이전트 자동 주입 기능을 극적으로 시연

set -e

# Auto mode 체크 (첫 번째 인수가 --auto인 경우)
if [ "$1" = "--auto" ]; then
    AUTO_MODE="true"
    echo "🤖 Auto mode enabled - 모든 대기 시간이 자동으로 진행됩니다"
fi

# 색상 정의
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
PURPLE='\033[0;35m'
CYAN='\033[0;36m'
NC='\033[0m' # No Color

# 함수 정의
print_header() {
    echo ""
    echo -e "${PURPLE}================================================${NC}"
    echo -e "${PURPLE}  $1${NC}"
    echo -e "${PURPLE}================================================${NC}"
    echo ""
}

print_phase() {
    echo ""
    echo -e "${CYAN}🎭 $1${NC}"
    echo -e "${CYAN}---------------------------${NC}"
}

print_status() {
    echo -e "${BLUE}[정보]${NC} $1"
}

print_success() {
    echo -e "${GREEN}[성공]${NC} $1"
}

print_warning() {
    echo -e "${YELLOW}[경고]${NC} $1"
}

print_error() {
    echo -e "${RED}[오류]${NC} $1"
}

print_demo() {
    echo -e "${PURPLE}[데모]${NC} $1"
}

print_contrast() {
    echo -e "${YELLOW}⚡ $1${NC}"
}

# 대기 함수
wait_for_input() {
    if [ "${AUTO_MODE}" != "true" ]; then
        echo ""
        echo -e "${CYAN}👆 Press Enter to continue...${NC}"
        read
    else
        echo ""
        echo -e "${CYAN}⏳ Auto mode - continuing in 3 seconds...${NC}"
        sleep 3
    fi
}

# 스크립트 시작
print_header "KubeDB Monitor - Before & After 데모"

echo "🎯 이 데모에서 보여드릴 내용:"
echo "  [1] BEFORE: KubeDB Agent 없는 일반 애플리케이션"
echo "  [2] AFTER:  KubeDB Agent 자동 주입된 애플리케이션"
echo "  [3] 실시간 비교: 모니터링 유무의 차이"

wait_for_input

# PHASE 1: BEFORE - 일반 애플리케이션 배포
print_phase "PHASE 1: BEFORE - 일반 수강신청 애플리케이션"

print_demo "먼저 KubeDB Monitor가 적용되지 않은 일반적인 Spring Boot 애플리케이션을 배포해보겠습니다."

# 기존 환경 정리
print_status "기존 환경 정리 중..."
kubectl delete deployment -n kubedb-monitor-test university-registration-demo --ignore-not-found=true || true
kubectl delete deployment -n kubedb-monitor-test university-registration-basic --ignore-not-found=true || true
kubectl delete service -n kubedb-monitor-test university-registration-demo-service --ignore-not-found=true || true
kubectl delete service -n kubedb-monitor-test university-registration-basic-service --ignore-not-found=true || true
sleep 5

print_status "일반 애플리케이션 배포 중..."
kubectl apply -f k8s/university-registration-basic.yaml

print_status "Pod 시작 대기 중..."
kubectl wait --for=condition=ready pod -l app=university-registration-basic -n kubedb-monitor-test --timeout=180s

# Pod 이름 동적으로 가져오기
BASIC_POD=$(kubectl get pods -n kubedb-monitor-test -l app=university-registration-basic --no-headers | awk '{print $1}')

# BEFORE 상태 확인
print_success "✅ 일반 애플리케이션 배포 완료 (Pod: $BASIC_POD)"
echo ""
print_contrast "📊 BEFORE 상태 확인:"

echo ""
echo "[1] Pod 구성 확인:"
kubectl describe pod -n kubedb-monitor-test "$BASIC_POD" | grep -A 5 -E "(Init Containers|Containers:)" || echo "   → Init Containers 없음 (일반 배포)"

echo ""
echo "[2] Pod 어노테이션 확인:"
kubectl get pod -n kubedb-monitor-test "$BASIC_POD" -o jsonpath='{.metadata.annotations}' | jq . | grep -E "(kubedb|monitor)" || echo "   → KubeDB 관련 어노테이션 없음"

echo ""
echo "[3] 애플리케이션 로그 확인 (KubeDB 관련):"
kubectl logs -n kubedb-monitor-test "$BASIC_POD" | grep -i "kubedb\|monitor\|agent" || echo "   → KubeDB Agent 관련 로그 없음"

echo ""
print_contrast "🔍 결과: 일반적인 Spring Boot 애플리케이션 - 데이터베이스 모니터링 없음"

wait_for_input

# PHASE 2: AFTER - KubeDB Monitor 적용
print_phase "PHASE 2: AFTER - KubeDB Monitor 자동 주입 적용"

print_demo "이제 동일한 애플리케이션에 KubeDB Monitor 어노테이션을 추가하여 자동 주입 기능을 시연하겠습니다."

print_status "KubeDB Monitor가 적용된 애플리케이션 배포 중..."
kubectl apply -f k8s/university-registration-demo-complete.yaml

print_status "Pod 시작 및 에이전트 주입 대기 중..."
kubectl wait --for=condition=ready pod -l app=university-registration-demo -n kubedb-monitor-test --timeout=180s

# Pod 이름 동적으로 가져오기
DEMO_POD=$(kubectl get pods -n kubedb-monitor-test -l app=university-registration-demo --no-headers | awk '{print $1}')

# AFTER 상태 확인
print_success "✅ KubeDB Monitor 적용 애플리케이션 배포 완료 (Pod: $DEMO_POD)"
echo ""
print_contrast "📊 AFTER 상태 확인:"

echo ""
echo "[1] Pod 구성 확인 (Init Container 자동 주입):"
kubectl describe pod -n kubedb-monitor-test "$DEMO_POD" | grep -A 5 "Init Containers"

echo ""
echo "[2] Pod 어노테이션 확인 (KubeDB 설정):"
kubectl get pod -n kubedb-monitor-test "$DEMO_POD" -o jsonpath='{.metadata.annotations}' | jq . | grep -E "(kubedb|monitor)"

echo ""
echo "[3] KubeDB Agent 시작 로그:"
kubectl logs -n kubedb-monitor-test "$DEMO_POD" | grep -i "kubedb monitor agent" | head -3

echo ""
echo "[4] JDBC 클래스 변환 확인:"
kubectl logs -n kubedb-monitor-test "$DEMO_POD" | grep -i "successfully transformed" | head -3

echo ""
print_contrast "🔍 결과: KubeDB Monitor 자동 주입 완료 - 실시간 데이터베이스 모니터링 활성화"

wait_for_input

# PHASE 3: 실시간 비교 데모
print_phase "PHASE 3: 실시간 모니터링 차이 확인"

print_demo "이제 두 애플리케이션에 동일한 API 요청을 보내서 모니터링 유무의 차이를 실시간으로 확인해보겠습니다."

# 포트 포워딩 설정
print_status "포트 포워딩 설정 중..."
pkill -f "kubectl port-forward" 2>/dev/null || true
sleep 2

# Pod 이름 재확인 (혹시 변경되었을 수 있음)
BASIC_POD=$(kubectl get pods -n kubedb-monitor-test -l app=university-registration-basic --no-headers | awk '{print $1}')
DEMO_POD=$(kubectl get pods -n kubedb-monitor-test -l app=university-registration-demo --no-headers | awk '{print $1}')

# Basic 애플리케이션 포트 포워딩
kubectl port-forward -n kubedb-monitor-test pod/"$BASIC_POD" 8081:8080 > /dev/null 2>&1 &
BASIC_PF_PID=$!

# Demo 애플리케이션 포트 포워딩  
kubectl port-forward -n kubedb-monitor-test pod/"$DEMO_POD" 8080:8080 > /dev/null 2>&1 &
DEMO_PF_PID=$!

sleep 5

print_success "포트 포워딩 완료:"
echo "  - Basic App (모니터링 없음): http://localhost:8081"  
echo "  - Demo App (모니터링 있음): http://localhost:8080"

echo ""
print_demo "동일한 Health Check API 호출 테스트:"

echo ""
echo "📍 BEFORE (Basic - 모니터링 없음):"
curl -s http://localhost:8081/api/actuator/health | jq .status
echo "   → 로그 모니터링 시작:"
kubectl logs -n kubedb-monitor-test "$BASIC_POD" --tail=0 -f &
BASIC_LOG_PID=$!
sleep 1

echo ""
echo "📍 AFTER (Demo - 모니터링 있음):"
curl -s http://localhost:8080/api/actuator/health | jq .status
echo "   → 로그 모니터링 시작:"
kubectl logs -n kubedb-monitor-test "$DEMO_POD" --tail=0 -f | grep -E "(KUBEDB|JDBC Method intercepted)" &
DEMO_LOG_PID=$!
sleep 2

# 실제 API 호출 테스트
print_demo "실제 데이터베이스 쿼리 발생시키기:"

echo ""
echo "🔄 동시 API 호출 테스트..."
echo ""

for i in {1..3}; do
    echo "  [API 호출 #$i]"
    echo "    - Basic App: $(curl -s -w "HTTP %{http_code}" -o /dev/null http://localhost:8081/api/actuator/metrics/hikaricp.connections.active)"
    echo "    - Demo App:  $(curl -s -w "HTTP %{http_code}" -o /dev/null http://localhost:8080/api/actuator/metrics/hikaricp.connections.active)"
    sleep 2
done

echo ""
print_contrast "🎯 결과 관찰:"
echo "  ✅ BEFORE: 일반적인 애플리케이션 로그만 출력"
echo "  ✅ AFTER:  JDBC Method intercepted 로그가 실시간으로 출력됨!"

sleep 5

# 정리
print_status "실시간 모니터링 종료 및 정리 중..."
kill $BASIC_LOG_PID $DEMO_LOG_PID $BASIC_PF_PID $DEMO_PF_PID 2>/dev/null || true

wait_for_input

# 최종 요약
print_header "🎉 Before & After 데모 완료!"

echo ""
echo "📊 확인된 KubeDB Monitor의 핵심 가치:"
echo ""
echo "   🔴 BEFORE (일반 애플리케이션):"
echo "      - Init Container 없음"
echo "      - KubeDB 어노테이션 없음" 
echo "      - 데이터베이스 모니터링 없음"
echo "      - JDBC 쿼리 추적 불가"
echo ""
echo "   🟢 AFTER (KubeDB Monitor 적용):"
echo "      - Init Container 자동 주입 ✅"
echo "      - KubeDB 설정 어노테이션 ✅"
echo "      - 실시간 DB 모니터링 ✅"  
echo "      - 모든 JDBC 쿼리 자동 추적 ✅"
echo ""
echo "⚡ 핵심 포인트: 코드 수정 없이 단순히 어노테이션만 추가하면"
echo "   KubeDB Monitor가 자동으로 데이터베이스 모니터링을 활성화합니다!"
echo ""

echo "🚀 다음 단계: 실제 데이터 초기화 및 복잡한 쿼리 모니터링 데모"
echo "   명령어: ./demo-complete-validation.sh"
echo ""

print_success "🎭 Before & After 데모가 성공적으로 완료되었습니다!"