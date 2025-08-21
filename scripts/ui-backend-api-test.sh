#!/bin/bash

# University Registration UI-Backend API 통신 테스트 스위트
# UI에서 발생하는 API 호출 오류를 체계적으로 진단하고 해결

set -e

# 색상 정의
GREEN='\033[32m'
YELLOW='\033[33m'
RED='\033[31m'
BLUE='\033[34m'
CYAN='\033[36m'
RESET='\033[0m'

# 로깅 함수
log_info() {
    echo -e "${BLUE}[INFO]${RESET} $1"
}

log_success() {
    echo -e "${GREEN}[SUCCESS]${RESET} $1"
}

log_warning() {
    echo -e "${YELLOW}[WARNING]${RESET} $1"
}

log_error() {
    echo -e "${RED}[ERROR]${RESET} $1"
}

log_step() {
    echo -e "${CYAN}[STEP]${RESET} $1"
}

# 설정
BASE_URL="https://university-registration.bitgaram.info"
API_BASE_URL="${BASE_URL}/api"
TIMEOUT=10

# 테스트 결과 저장
PASSED_TESTS=0
FAILED_TESTS=0
WARNINGS=0

echo -e "${BLUE}======================================${RESET}"
echo -e "${BLUE}   University Registration API 테스트${RESET}"
echo -e "${BLUE}======================================${RESET}"
echo ""

# 1. 기본 연결 테스트
log_step "1. 기본 연결 및 SSL 인증서 테스트"
echo "=================================================="

log_info "UI 홈페이지 연결 테스트..."
if curl -k -s -o /dev/null -w "%{http_code}" --max-time $TIMEOUT "$BASE_URL/" | grep -q "200"; then
    log_success "UI 홈페이지 연결 성공 (200)"
    PASSED_TESTS=$((PASSED_TESTS + 1))
else
    log_error "UI 홈페이지 연결 실패"
    FAILED_TESTS=$((FAILED_TESTS + 1))
fi

log_info "SSL 인증서 유효성 검사..."
if curl -s --max-time $TIMEOUT "$BASE_URL/" > /dev/null 2>&1; then
    log_success "SSL 인증서 유효 (HTTPS 연결 성공)"
    PASSED_TESTS=$((PASSED_TESTS + 1))
else
    log_warning "SSL 인증서 경고 또는 자체 서명된 인증서"
    WARNINGS=$((WARNINGS + 1))
fi

echo ""

# 2. Spring Boot Health Check
log_step "2. Spring Boot Health Check"
echo "=================================================="

log_info "Actuator Health 엔드포인트 테스트..."
HEALTH_RESPONSE=$(curl -k -s --max-time $TIMEOUT "$API_BASE_URL/actuator/health" 2>/dev/null || echo "")

if [[ -n "$HEALTH_RESPONSE" ]]; then
    if echo "$HEALTH_RESPONSE" | jq . > /dev/null 2>&1; then
        STATUS=$(echo "$HEALTH_RESPONSE" | jq -r '.status' 2>/dev/null || echo "UNKNOWN")
        if [[ "$STATUS" == "UP" ]]; then
            log_success "Spring Boot Health Check 성공: $STATUS"
            PASSED_TESTS=$((PASSED_TESTS + 1))
            
            # DB 연결 상태 확인
            DB_STATUS=$(echo "$HEALTH_RESPONSE" | jq -r '.components.db.status' 2>/dev/null || echo "UNKNOWN")
            if [[ "$DB_STATUS" == "UP" ]]; then
                log_success "데이터베이스 연결 상태 정상: $DB_STATUS"
                PASSED_TESTS=$((PASSED_TESTS + 1))
            else
                log_error "데이터베이스 연결 상태 오류: $DB_STATUS"
                FAILED_TESTS=$((FAILED_TESTS + 1))
            fi
        else
            log_error "Spring Boot Health Check 실패: $STATUS"
            FAILED_TESTS=$((FAILED_TESTS + 1))
        fi
    else
        log_error "Health 응답이 유효한 JSON이 아님: $HEALTH_RESPONSE"
        FAILED_TESTS=$((FAILED_TESTS + 1))
    fi
else
    log_error "Health Check 응답 없음 (404/500/timeout)"
    FAILED_TESTS=$((FAILED_TESTS + 1))
fi

echo ""

# 3. Courses API 테스트 (UI 에러의 주요 원인)
log_step "3. Courses API 테스트 (UI 오류 진단)"
echo "=================================================="

log_info "GET /api/courses 엔드포인트 테스트 (UI와 동일한 쿼리 파라미터)..."
COURSES_RESPONSE=$(curl -k -s -w "HTTPSTATUS:%{http_code}" --max-time $TIMEOUT "$API_BASE_URL/courses?page=0&size=20" 2>/dev/null || echo "HTTPSTATUS:000")

HTTP_CODE=$(echo "$COURSES_RESPONSE" | sed -E 's/.*HTTPSTATUS:([0-9]{3}).*/\1/')
RESPONSE_BODY=$(echo "$COURSES_RESPONSE" | sed -E 's/HTTPSTATUS:[0-9]{3}$//')

log_info "HTTP 상태 코드: $HTTP_CODE"

case $HTTP_CODE in
    200)
        log_success "Courses API 호출 성공 (200)"
        PASSED_TESTS=$((PASSED_TESTS + 1))
        
        # JSON 유효성 검사
        if echo "$RESPONSE_BODY" | jq . > /dev/null 2>&1; then
            log_success "응답이 유효한 JSON 형식"
            PASSED_TESTS=$((PASSED_TESTS + 1))
            
            # Spring Data 페이지네이션 응답 형식 검사
            if echo "$RESPONSE_BODY" | jq '.content' > /dev/null 2>&1; then
                log_success "Spring Data 페이지네이션 응답 형식 확인됨"
                PASSED_TESTS=$((PASSED_TESTS + 1))
                
                # content 필드가 배열인지 검사 (UI에서 e.data.map 오류 해결)
                if echo "$RESPONSE_BODY" | jq '.content | type == "array"' | grep -q "true"; then
                    log_success "content 필드가 배열 형식 (UI map() 함수 호환)"
                    PASSED_TESTS=$((PASSED_TESTS + 1))
                    
                    COURSE_COUNT=$(echo "$RESPONSE_BODY" | jq '.content | length')
                    log_info "반환된 과목 수: $COURSE_COUNT"
                    
                    if [[ $COURSE_COUNT -gt 0 ]]; then
                        log_success "과목 데이터 존재 ($COURSE_COUNT개)"
                        PASSED_TESTS=$((PASSED_TESTS + 1))
                        
                        # 첫 번째 과목의 필수 필드 검사
                        FIRST_COURSE=$(echo "$RESPONSE_BODY" | jq '.content[0]' 2>/dev/null || echo "{}")
                        log_info "첫 번째 과목 데이터 샘플:"
                        echo "$FIRST_COURSE" | jq . 2>/dev/null || echo "$FIRST_COURSE"
                    else
                        log_warning "과목 데이터가 없음 (빈 배열이지만 정상적인 페이지네이션 응답)"
                        WARNINGS=$((WARNINGS + 1))
                    fi
                else
                    log_error "content 필드가 배열이 아님 - UI map() 오류의 원인"
                    log_info "content 타입: $(echo "$RESPONSE_BODY" | jq '.content | type' 2>/dev/null || echo "invalid")"
                    FAILED_TESTS=$((FAILED_TESTS + 1))
                fi
                
                # 페이지네이션 메타데이터 검사
                TOTAL_ELEMENTS=$(echo "$RESPONSE_BODY" | jq '.totalElements' 2>/dev/null || echo "null")
                TOTAL_PAGES=$(echo "$RESPONSE_BODY" | jq '.totalPages' 2>/dev/null || echo "null")
                log_info "페이지네이션 정보: totalElements=$TOTAL_ELEMENTS, totalPages=$TOTAL_PAGES"
                
            else
                log_error "Spring Data 페이지네이션 형식이 아님 (content 필드 없음)"
                log_info "실제 응답 형식: $(echo "$RESPONSE_BODY" | jq 'keys' 2>/dev/null || echo "invalid JSON")"
                log_info "응답 내용: $RESPONSE_BODY"
                FAILED_TESTS=$((FAILED_TESTS + 1))
            fi
        else
            log_error "응답이 유효한 JSON이 아님"
            log_info "응답 내용: $RESPONSE_BODY"
            FAILED_TESTS=$((FAILED_TESTS + 1))
        fi
        ;;
    404)
        log_error "Courses API 엔드포인트를 찾을 수 없음 (404)"
        log_info "가능한 원인: context-path 설정 문제, URL 매핑 오류"
        FAILED_TESTS=$((FAILED_TESTS + 1))
        ;;
    500)
        log_error "서버 내부 오류 (500)"
        log_info "응답 내용: $RESPONSE_BODY"
        log_info "가능한 원인: 데이터베이스 연결, 쿼리 오류, 데이터 초기화 문제"
        FAILED_TESTS=$((FAILED_TESTS + 1))
        ;;
    000)
        log_error "API 서버 연결 실패 (timeout/connection refused)"
        FAILED_TESTS=$((FAILED_TESTS + 1))
        ;;
    *)
        log_error "예상치 못한 HTTP 상태 코드: $HTTP_CODE"
        log_info "응답 내용: $RESPONSE_BODY"
        FAILED_TESTS=$((FAILED_TESTS + 1))
        ;;
esac

echo ""

# 4. Cart API 테스트 (UI 오류 진단)
log_step "4. Cart API 테스트 (UI 장바구니 오류 진단)"
echo "=================================================="

log_info "Cart API 파라미터 요구사항 분석..."

# 먼저 POST /api/cart/items 테스트 (브라우저에서 실패하는 요청)
log_info "POST /api/cart/items 테스트 (장바구니 추가 기능)..."
CART_ADD_RESPONSE=$(curl -k -s -w "HTTPSTATUS:%{http_code}" --max-time $TIMEOUT \
  -X POST \
  -H "Content-Type: application/json" \
  -d '{"studentId":"2024001","courseId":"CS001"}' \
  "$API_BASE_URL/cart/items" 2>/dev/null || echo "HTTPSTATUS:000")

HTTP_CODE_ADD=$(echo "$CART_ADD_RESPONSE" | sed -E 's/.*HTTPSTATUS:([0-9]{3}).*/\1/')
RESPONSE_BODY_ADD=$(echo "$CART_ADD_RESPONSE" | sed -E 's/HTTPSTATUS:[0-9]{3}$//')

log_info "Cart 추가 HTTP 상태 코드: $HTTP_CODE_ADD"

case $HTTP_CODE_ADD in
    200)
        log_success "Cart 추가 성공 (200)"
        PASSED_TESTS=$((PASSED_TESTS + 1))
        ;;
    400)
        log_error "Cart 추가 400 Bad Request - 요청 데이터 문제"
        log_info "응답 내용: $RESPONSE_BODY_ADD"
        FAILED_TESTS=$((FAILED_TESTS + 1))
        ;;
    404)
        log_error "Cart 추가 엔드포인트 404 Not Found"
        FAILED_TESTS=$((FAILED_TESTS + 1))
        ;;
    *)
        log_warning "Cart 추가 응답 코드: $HTTP_CODE_ADD"
        log_info "응답 내용: $RESPONSE_BODY_ADD"
        WARNINGS=$((WARNINGS + 1))
        ;;
esac

echo ""

# UI가 studentId 파라미터 없이 호출하는 경우
log_info "GET /api/cart (파라미터 없음) - UI 실제 호출 패턴 테스트..."
CART_RESPONSE_NO_PARAM=$(curl -k -s -w "HTTPSTATUS:%{http_code}" --max-time $TIMEOUT "$API_BASE_URL/cart" 2>/dev/null || echo "HTTPSTATUS:000")

HTTP_CODE=$(echo "$CART_RESPONSE_NO_PARAM" | sed -E 's/.*HTTPSTATUS:([0-9]{3}).*/\1/')
RESPONSE_BODY=$(echo "$CART_RESPONSE_NO_PARAM" | sed -E 's/HTTPSTATUS:[0-9]{3}$//')

log_info "HTTP 상태 코드: $HTTP_CODE"

case $HTTP_CODE in
    400)
        log_error "Cart API 400 Bad Request - studentId 파라미터 필수"
        log_info "UI 오류 원인: Cart API는 studentId 파라미터를 필수로 요구함"
        log_info "응답 내용: $RESPONSE_BODY"
        FAILED_TESTS=$((FAILED_TESTS + 1))
        
        # studentId와 함께 테스트
        log_info "GET /api/cart?studentId=test 테스트 (올바른 호출)..."
        CART_RESPONSE_WITH_PARAM=$(curl -k -s -w "HTTPSTATUS:%{http_code}" --max-time $TIMEOUT "$API_BASE_URL/cart?studentId=test" 2>/dev/null || echo "HTTPSTATUS:000")
        
        HTTP_CODE_PARAM=$(echo "$CART_RESPONSE_WITH_PARAM" | sed -E 's/.*HTTPSTATUS:([0-9]{3}).*/\1/')
        RESPONSE_BODY_PARAM=$(echo "$CART_RESPONSE_WITH_PARAM" | sed -E 's/HTTPSTATUS:[0-9]{3}$//')
        
        log_info "studentId 포함 시 HTTP 상태 코드: $HTTP_CODE_PARAM"
        
        if [[ $HTTP_CODE_PARAM -eq 200 ]]; then
            log_success "studentId 파라미터 포함 시 정상 응답 (200)"
            PASSED_TESTS=$((PASSED_TESTS + 1))
            
            # CartSummaryDTO 응답 형식 검사
            if echo "$RESPONSE_BODY_PARAM" | jq . > /dev/null 2>&1; then
                log_success "응답이 유효한 JSON 형식"
                PASSED_TESTS=$((PASSED_TESTS + 1))
                
                # cartItems 필드가 배열인지 검사 (UI에서 map() 함수 사용)
                if echo "$RESPONSE_BODY_PARAM" | jq '.cartItems' > /dev/null 2>&1; then
                    if echo "$RESPONSE_BODY_PARAM" | jq '.cartItems | type == "array"' 2>/dev/null | grep -q "true"; then
                        log_success "cartItems 필드가 배열 형식 (UI map() 함수 호환)"
                        PASSED_TESTS=$((PASSED_TESTS + 1))
                        
                        CART_ITEM_COUNT=$(echo "$RESPONSE_BODY_PARAM" | jq '.cartItems | length' 2>/dev/null || echo "0")
                        log_info "장바구니 아이템 수: $CART_ITEM_COUNT"
                        
                        # CartSummaryDTO 응답 구조 확인
                        log_info "CartSummaryDTO 응답 샘플:"
                        echo "$RESPONSE_BODY_PARAM" | jq . 2>/dev/null || echo "$RESPONSE_BODY_PARAM"
                        
                    else
                        log_error "cartItems 필드가 배열이 아님 - UI map() 오류 원인"
                        FAILED_TESTS=$((FAILED_TESTS + 1))
                    fi
                else
                    log_error "cartItems 필드가 응답에 없음"
                    FAILED_TESTS=$((FAILED_TESTS + 1))
                fi
            else
                log_error "응답이 유효한 JSON이 아님"
                FAILED_TESTS=$((FAILED_TESTS + 1))
            fi
        else
            log_error "studentId 파라미터 포함해도 오류 발생: $HTTP_CODE_PARAM"
            log_info "응답 내용: $RESPONSE_BODY_PARAM"
            FAILED_TESTS=$((FAILED_TESTS + 1))
        fi
        ;;
    200)
        log_success "Cart API 호출 성공 (200)"
        PASSED_TESTS=$((PASSED_TESTS + 1))
        ;;
    404)
        log_error "Cart API 엔드포인트를 찾을 수 없음 (404)"
        FAILED_TESTS=$((FAILED_TESTS + 1))
        ;;
    *)
        log_warning "Cart API 응답 코드: $HTTP_CODE"
        log_info "응답 내용: $RESPONSE_BODY"
        WARNINGS=$((WARNINGS + 1))
        ;;
esac

echo ""

# 5. API 라우팅 진단
log_step "5. API 라우팅 및 Context Path 진단"
echo "=================================================="

log_info "Spring Boot 컨텍스트 경로 설정 확인..."

# 다양한 경로 패턴 테스트 (UI가 실제 사용하는 엔드포인트 포함)
API_ENDPOINTS=(
    "/api/courses"
    "/api/courses?page=0&size=20"
    "/courses" 
    "/api/actuator/health"
    "/actuator/health"
)

for endpoint in "${API_ENDPOINTS[@]}"; do
    log_info "테스트 경로: ${BASE_URL}${endpoint}"
    HTTP_CODE=$(curl -k -s -o /dev/null -w "%{http_code}" --max-time 5 "${BASE_URL}${endpoint}" 2>/dev/null || echo "000")
    
    case $HTTP_CODE in
        200) log_success "  ✅ $endpoint -> 200 (정상)" ;;
        404) log_warning "  ❌ $endpoint -> 404 (Not Found)" ;;
        500) log_error "  ⚠️  $endpoint -> 500 (Server Error)" ;;
        000) log_warning "  ⏱️  $endpoint -> Timeout/Connection Error" ;;
        *) log_info "  ℹ️  $endpoint -> $HTTP_CODE" ;;
    esac
done

echo ""

# 6. 결과 요약 및 해결 방안 제시
log_step "6. 테스트 결과 요약 및 해결 방안"
echo "=================================================="

TOTAL_TESTS=$((PASSED_TESTS + FAILED_TESTS))
SUCCESS_RATE=$((PASSED_TESTS * 100 / TOTAL_TESTS)) 2>/dev/null || SUCCESS_RATE=0

echo -e "${CYAN}==================================="
echo -e "     API 테스트 결과 요약"
echo -e "===================================${RESET}"
echo ""
echo -e "${GREEN}통과한 테스트: $PASSED_TESTS${RESET}"
echo -e "${RED}실패한 테스트: $FAILED_TESTS${RESET}"
echo -e "${YELLOW}경고: $WARNINGS${RESET}"
echo -e "${BLUE}성공률: ${SUCCESS_RATE}%${RESET}"
echo ""

if [[ $FAILED_TESTS -gt 0 ]]; then
    echo -e "${YELLOW}🔧 해결 방안:${RESET}"
    echo "1. Cart API studentId 파라미터 문제 (주요 UI 오류 원인):"
    echo "   - UI에서 Cart API 호출 시 studentId 파라미터 필수 추가"
    echo "   - 또는 CartController.getCart() 메서드를 Optional 파라미터로 수정"
    echo "   - 현재: GET /api/cart (400 Bad Request)"
    echo "   - 필요: GET /api/cart?studentId=\${currentStudentId}"
    echo ""
    echo "2. Spring Boot 앱 로그 확인:"
    echo "   kubectl logs -n kubedb-monitor-test deployment/university-registration-demo --tail=50"
    echo ""
    echo "3. 데이터베이스 연결 확인:"
    echo "   kubectl exec -n postgres-system postgres-cluster-1 -- psql -U postgres -d university -c '\l'"
    echo ""
    echo "4. Context Path 설정 확인:"
    echo "   application.yml의 server.servlet.context-path 설정"
    echo ""
    echo "5. 샘플 데이터 초기화:"
    echo "   APP_REGISTRATION_INITIALIZE_SAMPLE_DATA=true 환경변수 설정"
    echo ""
    echo "6. UI 프론트엔드 수정 권장사항:"
    echo "   - Cart 컴포넌트에서 API 호출 시 studentId 쿼리 파라미터 추가"
    echo "   - 사용자 인증 상태에서 현재 학생 ID 가져와서 사용"
    echo ""
else
    log_success "🎉 모든 API 테스트 통과! UI-Backend 통신이 정상입니다."
fi

exit $FAILED_TESTS