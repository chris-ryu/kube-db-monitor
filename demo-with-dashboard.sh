#!/bin/bash

# KubeDB Monitor 대시보드 포함 완전한 데모 스크립트
# 실제 DB 모니터링 + 제니퍼 스타일 대시보드 UI 확인

set -e

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

# 대기 함수
wait_for_input() {
    echo ""
    echo -e "${CYAN}👆 Press Enter to continue...${NC}"
    read
}

# 스크립트 시작
print_header "KubeDB Monitor - 제니퍼 스타일 대시보드 데모"

echo "🎯 이 데모에서 보여드릴 내용:"
echo "  [1] KubeDB Agent JSON 로그 개선 확인"
echo "  [2] 실시간 대시보드 UI 시작"
echo "  [3] 수강신청 앱 + 대시보드 연동 데모"
echo "  [4] 제니퍼 스타일 실시간 애니메이션 확인"

wait_for_input

# PHASE 1: 대시보드 시스템 시작
print_phase "PHASE 1: 대시보드 시스템 초기화"

print_demo "제니퍼 스타일 대시보드와 Control Plane을 시작하겠습니다."

print_status "Docker 컨테이너 정리 중..."
docker-compose down 2>/dev/null || true

print_status "대시보드 시스템 빌드 및 시작 중..."
docker-compose up -d --build

print_status "서비스 시작 대기 중..."
echo "  - Control Plane (WebSocket 서버): http://localhost:8080"
echo "  - 대시보드 UI: http://localhost:3000"

# 서비스 헬스체크
print_status "서비스 헬스체크 중..."
for i in {1..30}; do
    if curl -s http://localhost:8080/api/health > /dev/null 2>&1; then
        print_success "✅ Control Plane 준비 완료"
        break
    fi
    if [ $i -eq 30 ]; then
        print_error "Control Plane 시작 실패"
        exit 1
    fi
    sleep 2
done

for i in {1..60}; do
    if curl -s http://localhost:3000 > /dev/null 2>&1; then
        print_success "✅ 대시보드 UI 준비 완료"
        break
    fi
    if [ $i -eq 60 ]; then
        print_error "대시보드 UI 시작 실패"
        exit 1
    fi
    sleep 2
done

print_success "🎉 대시보드 시스템이 성공적으로 시작되었습니다!"
echo ""
echo "📊 대시보드 접속 정보:"
echo "  - 메인 대시보드: http://localhost:3000"
echo "  - WebSocket API: ws://localhost:8080/ws"
echo "  - Control Plane Health: http://localhost:8080/api/health"

wait_for_input

# PHASE 2: 쿠버네티스 환경 준비
print_phase "PHASE 2: 쿠버네티스 환경 준비"

print_demo "수강신청 앱을 배포하고 KubeDB Agent JSON 로그 포맷을 확인하겠습니다."

# 환경 초기화
print_status "기존 환경 정리 중..."
./demo-environment-setup.sh

# Pod 상태 확인
POD_NAME=$(kubectl get pods -n kubedb-monitor-test -l app=university-registration-demo --no-headers | awk '{print $1}' | head -1)

if [ -n "$POD_NAME" ]; then
    print_success "✅ 수강신청 앱 배포 완료: $POD_NAME"
else
    print_error "수강신청 앱 배포 실패"
    exit 1
fi

wait_for_input

# PHASE 3: JSON 로그 포맷 확인
print_phase "PHASE 3: 개선된 JSON 로그 포맷 확인"

print_demo "KubeDB Agent의 개선된 JSON 로그 포맷을 확인해보겠습니다."

print_status "최신 JSON 형태 로그 확인 중..."
echo ""
echo "🔍 기존 로그 형태:"
echo "   JDBC Method intercepted: org/springframework/jdbc/core/JdbcTemplate.execute"
echo ""
echo "🆕 개선된 JSON 로그 형태:"
kubectl logs -n kubedb-monitor-test "$POD_NAME" --tail=5 | grep -E "(KubeDB|JDBC|Agent)" | tail -3

wait_for_input

# PHASE 4: 대시보드 실시간 연동 데모
print_phase "PHASE 4: 대시보드 실시간 연동 데모"

print_demo "이제 실제 대시보드 UI에서 실시간 메트릭을 확인해보겠습니다."

# 대시보드 화면 열기 안내
print_success "🌐 대시보드를 브라우저에서 열어주세요:"
echo ""
echo "  📍 메인 URL: http://localhost:3000"
echo ""
echo "  👀 확인할 UI 요소들:"
echo "    - 실시간 QPS, 평균 지연시간, 연결 상태"
echo "    - 쿼리 플로우 애니메이션 (파티클 효과)"
echo "    - 제니퍼 스타일 네온 그린 컬러 테마"
echo "    - 실시간 메트릭 카드 업데이트"

wait_for_input

# PHASE 5: 실시간 데이터 생성 및 대시보드 확인
print_phase "PHASE 5: 실시간 데이터 생성 및 대시보드 확인"

print_demo "수강신청 API를 호출하여 실시간 데이터를 생성하고 대시보드에서 확인해보겠습니다."

# 포트 포워딩 설정
print_status "수강신청 앱 포트 포워딩 설정 중..."
kubectl port-forward -n kubedb-monitor-test pod/"$POD_NAME" 8081:8080 > /dev/null 2>&1 &
PORT_FORWARD_PID=$!
sleep 5

# 헬스체크
if curl -s http://localhost:8081/api/actuator/health > /dev/null 2>&1; then
    print_success "✅ 수강신청 앱 연결 성공"
else
    print_error "수강신청 앱 연결 실패"
    kill $PORT_FORWARD_PID 2>/dev/null || true
    exit 1
fi

print_demo "실시간 데이터 생성 중... 대시보드에서 다음을 확인하세요:"
echo ""
echo "  📊 실시간 메트릭 업데이트:"
echo "    - QPS 증가"
echo "    - 평균 지연시간 변화"
echo "    - 쿼리 플로우 애니메이션"
echo ""
echo "  🎨 애니메이션 효과:"
echo "    - 쿼리 파티클 흐름"
echo "    - 메트릭 카드 펄스"
echo "    - 실시간 차트 업데이트"

# 연속적인 API 호출로 대시보드 데이터 생성
print_status "연속 API 호출 시작 (30초간)..."
for i in {1..30}; do
    # 다양한 API 호출
    curl -s http://localhost:8081/api/actuator/health > /dev/null &
    curl -s -X POST http://localhost:8081/api/departments \
        -H "Content-Type: application/json" \
        -d "{\"name\": \"테스트학과$i\", \"code\": \"TEST$i\"}" > /dev/null 2>&1 &
    
    if [ $((i % 3)) -eq 0 ]; then
        curl -s -X POST http://localhost:8081/api/students \
            -H "Content-Type: application/json" \
            -d "{\"name\": \"학생$i\", \"studentId\": \"2024$i\"}" > /dev/null 2>&1 &
    fi
    
    echo -n "⚡"
    sleep 1
done

echo ""
print_success "✅ 30초간 API 호출 완료"

wait_for_input

# PHASE 6: 대시보드 기능 상세 확인
print_phase "PHASE 6: 대시보드 기능 상세 확인"

print_demo "대시보드의 각 기능을 자세히 확인해보겠습니다."

echo ""
echo "🎯 대시보드 주요 기능 체크리스트:"
echo ""
echo "  📊 메트릭 카드 (4개):"
echo "    □ QPS (Queries Per Second)"
echo "    □ 평균 지연시간 (Average Latency)"
echo "    □ 활성 연결 (Active Connections)"
echo "    □ 에러율 (Error Rate)"
echo ""
echo "  🎭 쿼리 플로우 애니메이션:"
echo "    □ App → Pool → DB → App 흐름"
echo "    □ 실시간 쿼리 파티클 효과"
echo "    □ 연결 상태 표시기"
echo "    □ 실행 중인 쿼리 목록"
echo ""
echo "  🎨 제니퍼 스타일 UI:"
echo "    □ 네온 그린 (#00ff88) 컬러"
echo "    □ 다크 테마 배경"
echo "    □ 부드러운 애니메이션"
echo "    □ 글로우 효과"

wait_for_input

# PHASE 7: 고부하 테스트
print_phase "PHASE 7: 고부하 상황에서 대시보드 성능 테스트"

print_demo "대량의 API 호출을 발생시켜 대시보드의 실시간 성능을 테스트해보겠습니다."

print_status "고부하 API 호출 시작 (60초간)..."
echo ""
echo "  📈 대시보드에서 확인할 내용:"
echo "    - QPS 급격한 증가"
echo "    - 메트릭 카드 실시간 업데이트"
echo "    - 쿼리 애니메이션 가속화"
echo "    - 연결 풀 사용률 변화"

# 병렬로 대량 요청 생성
for i in {1..60}; do
    (
        curl -s http://localhost:8081/api/actuator/health > /dev/null
        curl -s http://localhost:8081/api/actuator/metrics > /dev/null
        curl -s -X POST http://localhost:8081/api/departments \
            -H "Content-Type: application/json" \
            -d "{\"name\": \"부하테스트$i\", \"code\": \"LOAD$i\"}" > /dev/null 2>&1
    ) &
    
    # 백그라운드 프로세스 제한
    if [ $((i % 10)) -eq 0 ]; then
        wait
        echo -n "🔥"
    fi
    
    sleep 0.5
done

wait
echo ""
print_success "✅ 고부하 테스트 완료"

wait_for_input

# PHASE 8: 결과 요약 및 정리
print_phase "PHASE 8: 데모 결과 요약"

print_demo "KubeDB Monitor 대시보드 데모가 완료되었습니다."

echo ""
echo "📊 데모 결과 요약:"
echo ""
echo "✅ 성공적으로 완료된 항목:"
echo "  - Agent JSON 로그 포맷 개선"
echo "  - 제니퍼 스타일 대시보드 UI"
echo "  - 실시간 WebSocket 연결"
echo "  - 쿼리 플로우 애니메이션"
echo "  - 메트릭 카드 실시간 업데이트"
echo "  - 고부하 상황 대응"
echo ""

# 현재 상태 확인
print_status "현재 실행 중인 서비스:"
docker-compose ps

echo ""
print_status "대시보드 접속 정보 (계속 사용 가능):"
echo "  - 메인 대시보드: http://localhost:3000"
echo "  - Control Plane API: http://localhost:8080"
echo "  - 수강신청 앱: http://localhost:8081"

echo ""
echo "🛑 데모 종료 옵션:"
echo "  1. 서비스 계속 실행: 그대로 두기"
echo "  2. 정리 및 종료: docker-compose down"
echo ""

read -p "서비스를 종료하시겠습니까? (y/n): " -n 1 -r
echo
if [[ $REPLY =~ ^[Yy]$ ]]; then
    print_status "서비스 정리 중..."
    
    # 포트 포워딩 정리
    kill $PORT_FORWARD_PID 2>/dev/null || true
    pkill -f "kubectl port-forward" 2>/dev/null || true
    
    # Docker 컨테이너 정리
    docker-compose down
    
    print_success "✅ 모든 서비스가 정리되었습니다."
else
    print_success "✅ 서비스가 계속 실행됩니다."
    echo ""
    echo "📋 사용 가능한 명령어:"
    echo "  - 로그 확인: docker-compose logs -f"
    echo "  - 서비스 중지: docker-compose down"
    echo "  - 서비스 재시작: docker-compose restart"
fi

print_header "🎉 KubeDB Monitor 대시보드 데모 완료!"

echo ""
echo "💡 추가 개발 방향:"
echo "  - 더 많은 차트 유형 추가"
echo "  - 알림 시스템 구현"
echo "  - 사용자 설정 기능"
echo "  - 모바일 반응형 최적화"
echo "  - 데이터 내보내기 기능"
echo ""

print_success "🚀 제니퍼 스타일 KubeDB Monitor 대시보드 데모를 성공적으로 완료했습니다!"