#!/bin/bash

# KubeDB Monitor Docker 이미지 빌드 스크립트
# 사용법: ./build-images.sh [component] [options]
# 컴포넌트: all, agent, control-plane, dashboard, university-app
# 옵션: --no-cache, --push, --no-push

set -e

# 설정
REGISTRY="${DOCKER_REGISTRY:-registry.bitgaram.info}"
USERNAME="${DOCKER_USERNAME:-admin}"
PASSWORD="${DOCKER_PASSWORD:-qlcrkfka1#}"
IMAGE_TAG="${IMAGE_TAG:-latest}"

# 색상 정의
GREEN='\033[32m'
YELLOW='\033[33m'
RED='\033[31m'
BLUE='\033[34m'
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

# 사용법 표시
show_usage() {
    echo "사용법: $0 [component] [options]"
    echo ""
    echo "컴포넌트:"
    echo "  all             - 모든 이미지 빌드"
    echo "  agent           - Agent 이미지만 빌드"
    echo "  control-plane   - Control Plane 이미지만 빌드" 
    echo "  dashboard       - Dashboard 이미지만 빌드"
    echo "  university-app  - 수강신청 앱 이미지 빌드 (Backend API + Frontend UI)"
    echo ""
    echo "옵션:"
    echo "  --no-cache      - Docker 캐시 사용하지 않음 (기본값)"
    echo "  --push          - 이미지를 레지스트리에 푸시 (기본값)"
    echo "  --no-push       - 푸시하지 않음"
    echo "  --redeploy      - 빌드 후 기존 배포 제거 및 재배포 (기본값)"
    echo "  --no-redeploy   - 배포 재시작 건너뛰기"
    echo "  --force-delete  - 기존 배포 강제 삭제 (기본값)"
    echo ""
    echo "예시:"
    echo "  $0 all                    # 모든 컴포넌트 빌드, 푸시, 재배포"
    echo "  $0 agent --no-redeploy    # Agent만 빌드, 푸시 (재배포 안함)"
    echo "  $0 control-plane --no-push # Control Plane 빌드만 (푸시 안함)"
    echo "  $0 dashboard              # Dashboard 빌드, 푸시, 재배포"
}

# 파라미터 파싱
COMPONENT=${1:-all}
DOCKER_ARGS="--no-cache"  # 기본적으로 캐시 사용 안함 (코드 변경사항 확실히 반영)
PUSH_IMAGES="true"  # 기본적으로 레지스트리에 푸시 (Kubernetes 배포 반영)
REDEPLOY="true"  # 기본적으로 배포 재시작 (완전한 CI/CD 파이프라인)
FORCE_DELETE="true"  # 기본적으로 기존 배포 강제 삭제

for arg in "$@"; do
    case $arg in
        --no-cache)
            DOCKER_ARGS="$DOCKER_ARGS --no-cache"
            ;;
        --push)
            PUSH_IMAGES="true"
            ;;
        --no-push)
            PUSH_IMAGES="false"
            ;;
        --redeploy)
            REDEPLOY="true"
            ;;
        --no-redeploy)
            REDEPLOY="false"
            ;;
        --force-delete)
            FORCE_DELETE="true"
            ;;
        --help|-h)
            show_usage
            exit 0
            ;;
    esac
done

# Docker 로그인 함수
docker_login() {
    if [[ "$PUSH_IMAGES" == "true" ]]; then
        log_info "Docker 레지스트리에 로그인 중..."
        echo "$PASSWORD" | docker login "$REGISTRY" -u "$USERNAME" --password-stdin
        log_success "Docker 로그인 완료"
    fi
}

# Maven 빌드 함수
build_maven_project() {
    local project_dir=$1
    log_info "Maven 프로젝트 빌드: $project_dir"
    cd "$project_dir"
    mvn clean package -DskipTests=true -q
    cd - > /dev/null
    log_success "Maven 빌드 완료: $project_dir"
}

# Docker 이미지 빌드 함수
build_docker_image() {
    local dockerfile=$1
    local image_name=$2
    local context_dir=${3:-.}
    
    log_info "Docker 이미지 빌드: $image_name"
    docker build $DOCKER_ARGS -f "$dockerfile" -t "$image_name" "$context_dir"
    log_success "이미지 빌드 완료: $image_name"
    
    if [[ "$PUSH_IMAGES" == "true" ]]; then
        log_info "이미지 푸시: $image_name"
        docker push "$image_name"
        log_success "이미지 푸시 완료: $image_name"
    fi
}

# 배포 상태 대기 함수
wait_for_deployment_ready() {
    local deployment_name=$1
    local namespace=$2
    local max_wait=${3:-300}  # 최대 대기 시간 (초)
    
    log_info "배포 준비 상태 대기 중: $deployment_name (네임스페이스: $namespace)"
    
    local count=0
    while [[ $count -lt $max_wait ]]; do
        if kubectl get deployment "$deployment_name" -n "$namespace" >/dev/null 2>&1; then
            if kubectl rollout status deployment/"$deployment_name" -n "$namespace" --timeout=60s >/dev/null 2>&1; then
                log_success "배포 준비 완료: $deployment_name"
                return 0
            fi
        fi
        
        sleep 10
        count=$((count + 10))
        log_info "대기 중... ($count/${max_wait}초)"
    done
    
    log_warning "배포 준비 대기 시간 초과: $deployment_name"
    return 1
}

# 기존 배포 완전 제거 함수
force_delete_deployment() {
    local deployment_name=$1
    local namespace=$2
    
    if kubectl get deployment "$deployment_name" -n "$namespace" >/dev/null 2>&1; then
        log_info "기존 배포 제거 중: $deployment_name (네임스페이스: $namespace)"
        
        # Grace period 설정하여 강제 삭제
        kubectl delete deployment "$deployment_name" -n "$namespace" --grace-period=30 --timeout=60s
        
        # Pod가 완전히 종료될 때까지 대기
        local count=0
        while kubectl get pods -n "$namespace" -l app="$deployment_name" | grep -v "No resources found" >/dev/null 2>&1; do
            if [[ $count -gt 12 ]]; then  # 2분 대기
                log_warning "Pod 종료 시간 초과, 강제 종료 시도: $deployment_name"
                kubectl delete pods -n "$namespace" -l app="$deployment_name" --grace-period=0 --force >/dev/null 2>&1 || true
                break
            fi
            sleep 10
            count=$((count + 1))
            log_info "Pod 종료 대기 중... ($deployment_name)"
        done
        
        log_success "기존 배포 완전 제거 완료: $deployment_name"
    else
        log_info "기존 배포가 존재하지 않음: $deployment_name"
    fi
}

# Agent 이미지 빌드
build_agent() {
    log_info "🤖 Agent 이미지 빌드 시작"
    
    # Agent Maven 빌드
    if [[ -d "kubedb-monitor-agent" ]]; then
        build_maven_project "kubedb-monitor-agent"
    fi
    
    # Docker 이미지 빌드
    if [[ -f "Dockerfile.agent" ]]; then
        build_docker_image "Dockerfile.agent" "$REGISTRY/kubedb-monitor/agent:$IMAGE_TAG"
    else
        log_warning "Dockerfile.agent를 찾을 수 없습니다."
    fi
}

# Control Plane 이미지 빌드
build_control_plane() {
    log_info "🎛️ Control Plane 이미지 빌드 시작"
    
    # Control Plane 빌드
    if [[ -d "kubedb-monitor-control-plane" ]]; then
        cd kubedb-monitor-control-plane
        go mod tidy
        go build -o kubedb-monitor-control-plane .
        cd - > /dev/null
        build_docker_image "Dockerfile" "$REGISTRY/kubedb-monitor/control-plane:$IMAGE_TAG" "./kubedb-monitor-control-plane"
    elif [[ -d "control-plane" ]]; then
        build_docker_image "control-plane/Dockerfile" "$REGISTRY/kubedb-monitor/control-plane:$IMAGE_TAG" "./control-plane"
    else
        log_warning "Control Plane 디렉터리를 찾을 수 없습니다."
    fi
}

# Dashboard 이미지 빌드  
build_dashboard() {
    log_info "📊 Dashboard 이미지 빌드 시작"
    
    if [[ -d "kubedb-monitor-dashboard" ]]; then
        build_docker_image "kubedb-monitor-dashboard/Dockerfile" "$REGISTRY/kubedb-monitor/dashboard-frontend:$IMAGE_TAG" "./kubedb-monitor-dashboard"
    elif [[ -d "dashboard-frontend" ]]; then
        build_docker_image "dashboard-frontend/Dockerfile" "$REGISTRY/kubedb-monitor/dashboard-frontend:$IMAGE_TAG" "./dashboard-frontend"
    else
        log_warning "Dashboard 디렉터리를 찾을 수 없습니다."
    fi
}

# 수강신청 앱 이미지 빌드
build_university_app() {
    log_info "🎓 수강신청 앱 이미지 빌드 시작 (Backend + Frontend)"
    
    # Backend (API 서비스) 빌드
    if [[ -d "sample-apps/university-registration" ]]; then
        log_info "🔧 Backend (API) Maven 빌드 중..."
        build_maven_project "sample-apps/university-registration"
        build_docker_image "sample-apps/university-registration/Dockerfile" "$REGISTRY/kubedb-monitor/university-registration:$IMAGE_TAG" "./sample-apps/university-registration"
        log_success "Backend (API) 이미지 빌드 완료"
    else
        log_warning "Backend 디렉터리를 찾을 수 없습니다: sample-apps/university-registration"
    fi
    
    # Frontend (UI) 빌드
    if [[ -d "sample-apps/university-registration-ui" ]]; then
        log_info "🎨 Frontend (UI) 이미지 빌드 중..."
        build_docker_image "sample-apps/university-registration-ui/Dockerfile" "$REGISTRY/kubedb-monitor/university-registration-ui:$IMAGE_TAG" "./sample-apps/university-registration-ui"
        log_success "Frontend (UI) 이미지 빌드 완료"
    else
        log_warning "Frontend 디렉터리를 찾을 수 없습니다: sample-apps/university-registration-ui"
    fi
    
    log_success "🎉 수강신청 앱 (Backend + Frontend) 빌드 완료"
}

# 컴포넌트별 배포 재시작 함수
redeploy_component() {
    local component=$1
    
    log_info "🔄 배포 재시작: $component"
    
    case $component in
        "agent")
            redeploy_agent_deployments
            ;;
        "control-plane")
            redeploy_control_plane
            ;;
        "dashboard")
            redeploy_dashboard
            ;;
        "university-app")
            redeploy_university_app
            ;;
        "all")
            log_info "전체 배포 재시작 수행"
            redeploy_agent_deployments
            redeploy_control_plane
            redeploy_dashboard
            redeploy_university_app
            ;;
        *)
            log_warning "알 수 없는 컴포넌트: $component, 배포 재시작 건너뛰기"
            ;;
    esac
}

# Agent가 포함된 배포들 재시작
redeploy_agent_deployments() {
    log_info "📦 Agent 관련 배포 재시작"
    
    # university-registration-demo 재배포
    force_delete_deployment "university-registration-demo" "kubedb-monitor-test"
    log_info "university-registration-demo 재배포 중..."
    kubectl apply -f k8s/university-registration-demo-complete.yaml
    wait_for_deployment_ready "university-registration-demo" "kubedb-monitor-test" 180
    log_success "university-registration-demo 재배포 완료"
    
    # university-registration-ui (UI 포함) 재배포
    force_delete_deployment "university-registration-ui" "kubedb-monitor-test"
    log_info "university-registration-ui 재배포 중..."
    kubectl apply -f k8s/university-registration-with-ui.yaml
    wait_for_deployment_ready "university-registration-ui" "kubedb-monitor-test" 180
    log_success "university-registration-ui 재배포 완료"
}

# Control Plane 재배포
redeploy_control_plane() {
    log_info "🎛️ Control Plane 재배포"
    
    force_delete_deployment "kubedb-monitor-control-plane" "kubedb-monitor"
    log_info "kubedb-monitor-control-plane 재배포 중..."
    kubectl apply -f k8s/kubedb-monitor-deployment.yaml
    wait_for_deployment_ready "kubedb-monitor-control-plane" "kubedb-monitor" 120
    log_success "kubedb-monitor-control-plane 재배포 완료"
}

# Dashboard 재배포
redeploy_dashboard() {
    log_info "📊 Dashboard 재배포"
    
    force_delete_deployment "kubedb-monitor-dashboard" "kubedb-monitor"
    log_info "kubedb-monitor-dashboard 재배포 중..."
    kubectl apply -f k8s/kubedb-monitor-deployment.yaml
    wait_for_deployment_ready "kubedb-monitor-dashboard" "kubedb-monitor" 120
    log_success "kubedb-monitor-dashboard 재배포 완료"
}

# University App 재배포
redeploy_university_app() {
    log_info "🎓 University App 재배포 (Backend API + Frontend UI)"
    
    # Backend API 서비스 재배포 (Agent가 포함되어 있음)
    log_info "🔧 Backend API 서비스 재배포 중..."
    force_delete_deployment "university-registration-demo" "kubedb-monitor-test"
    log_info "university-registration-demo 새로운 배포 적용 중..."
    kubectl apply -f k8s/university-registration-with-ui.yaml
    wait_for_deployment_ready "university-registration-demo" "kubedb-monitor-test" 180
    log_success "Backend API 서비스 재배포 완료"
    
    # Frontend UI 서비스 재배포
    log_info "🎨 Frontend UI 서비스 재배포 중..."
    force_delete_deployment "university-registration-ui" "kubedb-monitor-test"
    log_info "university-registration-ui 새로운 배포 적용 중..."
    # UI deployment도 university-registration-with-ui.yaml에서 재생성 필요
    kubectl apply -f k8s/university-registration-with-ui.yaml
    wait_for_deployment_ready "university-registration-ui" "kubedb-monitor-test" 120
    log_success "Frontend UI 서비스 재배포 완료"
    
    # 서비스 상태 확인
    log_info "📊 University App 서비스 상태 확인"
    echo "----------------------------------------"
    echo "🔧 Backend API Service:"
    kubectl get service university-registration-demo-service -n kubedb-monitor-test -o wide 2>/dev/null || log_warning "Backend API Service를 찾을 수 없음"
    
    echo "🎨 Frontend UI Service:"
    kubectl get service university-registration-ui-service -n kubedb-monitor-test -o wide 2>/dev/null || log_warning "Frontend UI Service를 찾을 수 없음"
    
    echo "🌐 Ingress:"
    kubectl get ingress university-registration-demo-ingress -n kubedb-monitor-test 2>/dev/null || log_warning "University Ingress를 찾을 수 없음"
    echo "----------------------------------------"
    
    log_success "🎉 University App (Backend + Frontend) 재배포 완료"
}

# 배포 상태 확인 함수들
check_deployment_status() {
    log_info "📊 전체 배포 상태 확인"
    
    echo "----------------------------------------"
    echo "🎛️  Control Plane 상태:"
    kubectl get deployment kubedb-monitor-control-plane -n kubedb-monitor -o wide 2>/dev/null || log_warning "Control Plane 배포를 찾을 수 없음"
    
    echo "----------------------------------------"
    echo "📊 Dashboard 상태:"
    kubectl get deployment kubedb-monitor-dashboard -n kubedb-monitor -o wide 2>/dev/null || log_warning "Dashboard 배포를 찾을 수 없음"
    
    echo "----------------------------------------"
    echo "🎓 University App 상태:"
    echo "  🔧 Backend (API):"
    kubectl get deployment university-registration-demo -n kubedb-monitor-test -o wide 2>/dev/null || log_warning "University Backend (API) 배포를 찾을 수 없음"
    echo "  🎨 Frontend (UI):"
    kubectl get deployment university-registration-ui -n kubedb-monitor-test -o wide 2>/dev/null || log_warning "University Frontend (UI) 배포를 찾을 수 없음"
    
    echo "----------------------------------------"
    echo "🔗 서비스 상태:"
    echo "  🎛️ KubeDB Monitor Services:"
    kubectl get services -n kubedb-monitor 2>/dev/null
    echo "  🎓 University App Services:"
    kubectl get services -n kubedb-monitor-test 2>/dev/null
    
    echo "----------------------------------------"
    echo "🌐 Ingress 상태:"
    kubectl get ingress -n kubedb-monitor 2>/dev/null || echo "  KubeDB Monitor Ingress 없음"
    kubectl get ingress -n kubedb-monitor-test 2>/dev/null || echo "  University App Ingress 없음"
    
    echo "----------------------------------------"
    log_success "배포 상태 확인 완료"
}

check_component_status() {
    local component=$1
    log_info "📊 $component 배포 상태 확인"
    
    case $component in
        "agent")
            kubectl get deployment university-registration-demo -n kubedb-monitor-test -o wide 2>/dev/null || log_warning "Agent 관련 배포를 찾을 수 없음"
            kubectl get deployment university-registration-ui -n kubedb-monitor-test -o wide 2>/dev/null || true
            ;;
        "control-plane")
            kubectl get deployment kubedb-monitor-control-plane -n kubedb-monitor -o wide 2>/dev/null || log_warning "Control Plane 배포를 찾을 수 없음"
            ;;
        "dashboard")
            kubectl get deployment kubedb-monitor-dashboard -n kubedb-monitor -o wide 2>/dev/null || log_warning "Dashboard 배포를 찾을 수 없음"
            ;;
        "university-app")
            echo "🎓 University App 배포 상태:"
            kubectl get deployment university-registration-demo -n kubedb-monitor-test -o wide 2>/dev/null || log_warning "University Backend (API) 배포를 찾을 수 없음"
            kubectl get deployment university-registration-ui -n kubedb-monitor-test -o wide 2>/dev/null || log_warning "University Frontend (UI) 배포를 찾을 수 없음"
            echo "🔗 University App 서비스 상태:"
            kubectl get service university-registration-demo-service -n kubedb-monitor-test 2>/dev/null || log_warning "University API Service를 찾을 수 없음"
            kubectl get service university-registration-ui-service -n kubedb-monitor-test 2>/dev/null || log_warning "University UI Service를 찾을 수 없음"
            kubectl get ingress university-registration-demo-ingress -n kubedb-monitor-test 2>/dev/null || log_warning "University Ingress를 찾을 수 없음"
            ;;
    esac
    
    log_success "$component 상태 확인 완료"
}

# 메인 실행 부분
main() {
    log_info "🚀 KubeDB Monitor Docker 이미지 빌드 및 배포 파이프라인 시작"
    log_info "컴포넌트: $COMPONENT"
    log_info "푸시: $PUSH_IMAGES, 재배포: $REDEPLOY, 강제삭제: $FORCE_DELETE"
    
    # Docker 로그인
    docker_login
    
    # 컴포넌트별 빌드
    case $COMPONENT in
        "all")
            build_agent
            build_control_plane
            build_dashboard
            build_university_app
            ;;
        "agent")
            build_agent
            ;;
        "control-plane")
            build_control_plane
            ;;
        "dashboard")
            build_dashboard
            ;;
        "university-app")
            build_university_app
            ;;
        *)
            log_error "알 수 없는 컴포넌트: $COMPONENT"
            show_usage
            exit 1
            ;;
    esac
    
    # 완료 메시지
    log_success "🎉 모든 이미지 빌드 완료!"
    
    if [[ "$PUSH_IMAGES" == "true" ]]; then
        log_info "빌드된 이미지들:"
        docker images | grep "$REGISTRY/kubedb-monitor" | head -10
        
        # 이미지 푸시 후 자동 배포 재시작
        if [[ "$REDEPLOY" == "true" ]]; then
            log_info "🚀 배포 재시작 시작..."
            redeploy_component "$COMPONENT"
            
            # 배포 상태 확인
            log_info "📊 배포 상태 확인"
            case $COMPONENT in
                "all")
                    check_deployment_status
                    ;;
                *)
                    check_component_status "$COMPONENT"
                    ;;
            esac
        else
            log_info "배포 재시작 건너뛰기 (--no-redeploy 옵션)"
        fi
    fi
    
    log_info "배포 방법:"
    log_info "  make deploy     # 전체 배포"
    log_info "  make redeploy   # 삭제 후 재배포"
    log_info "  make status     # 배포 상태 확인"
}

# 스크립트 실행
main "$@"