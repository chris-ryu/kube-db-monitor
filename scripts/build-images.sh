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
    echo "  university-app  - 수강신청 앱 이미지만 빌드"
    echo ""
    echo "옵션:"
    echo "  --no-cache      - Docker 캐시 사용하지 않음 (기본값)"
    echo "  --push          - 이미지를 레지스트리에 푸시 (기본값)"
    echo "  --no-push       - 푸시하지 않음"
    echo "  --no-redeploy   - 배포 재시작 건너뛰기"
    echo ""
    echo "예시:"
    echo "  $0 all --push"
    echo "  $0 agent --no-cache"
    echo "  $0 control-plane"
}

# 파라미터 파싱
COMPONENT=${1:-all}
DOCKER_ARGS="--no-cache"  # 기본적으로 캐시 사용 안함 (코드 변경사항 확실히 반영)
PUSH_IMAGES="true"  # 기본적으로 레지스트리에 푸시 (Kubernetes 배포 반영)
REDEPLOY="true"  # 기본적으로 배포 재시작

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
        --no-redeploy)
            REDEPLOY="false"
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
        build_docker_image "kubedb-monitor-dashboard/Dockerfile" "$REGISTRY/kubedb-monitor/dashboard:$IMAGE_TAG" "./kubedb-monitor-dashboard"
    elif [[ -d "dashboard-frontend" ]]; then
        build_docker_image "dashboard-frontend/Dockerfile" "$REGISTRY/kubedb-monitor/dashboard-frontend:$IMAGE_TAG" "./dashboard-frontend"
    else
        log_warning "Dashboard 디렉터리를 찾을 수 없습니다."
    fi
}

# 수강신청 앱 이미지 빌드
build_university_app() {
    log_info "🎓 수강신청 앱 이미지 빌드 시작"
    
    # Backend 빌드
    if [[ -d "sample-apps/university-registration" ]]; then
        build_maven_project "sample-apps/university-registration"
        build_docker_image "sample-apps/university-registration/Dockerfile" "$REGISTRY/kubedb-monitor/university-registration:$IMAGE_TAG" "./sample-apps/university-registration"
    fi
    
    # Frontend 빌드
    if [[ -d "sample-apps/university-registration-ui" ]]; then
        build_docker_image "sample-apps/university-registration-ui/Dockerfile" "$REGISTRY/kubedb-monitor/university-registration-ui:$IMAGE_TAG" "./sample-apps/university-registration-ui"
    fi
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
    if kubectl get deployment university-registration-demo -n kubedb-monitor-test >/dev/null 2>&1; then
        log_info "university-registration-demo 재배포 중..."
        kubectl delete deployment university-registration-demo -n kubedb-monitor-test
        kubectl apply -f k8s/university-registration-demo-complete.yaml
        log_success "university-registration-demo 재배포 완료"
    fi
    
    # university-registration (UI 포함) 재배포
    if kubectl get deployment university-registration -n kubedb-monitor-test >/dev/null 2>&1; then
        log_info "university-registration 재배포 중..."
        kubectl delete deployment university-registration -n kubedb-monitor-test
        kubectl apply -f k8s/university-registration-with-ui.yaml
        log_success "university-registration 재배포 완료"
    fi
}

# Control Plane 재배포
redeploy_control_plane() {
    log_info "🎛️ Control Plane 재배포"
    
    if kubectl get deployment kubedb-monitor-control-plane -n kubedb-monitor >/dev/null 2>&1; then
        log_info "kubedb-monitor-control-plane 재배포 중..."
        kubectl delete deployment kubedb-monitor-control-plane -n kubedb-monitor
        kubectl apply -f k8s/kubedb-monitor-deployment.yaml
        log_success "kubedb-monitor-control-plane 재배포 완료"
    fi
}

# Dashboard 재배포
redeploy_dashboard() {
    log_info "📊 Dashboard 재배포"
    
    if kubectl get deployment kubedb-monitor-dashboard -n kubedb-monitor >/dev/null 2>&1; then
        log_info "kubedb-monitor-dashboard 재배포 중..."
        kubectl delete deployment kubedb-monitor-dashboard -n kubedb-monitor
        kubectl apply -f k8s/kubedb-monitor-deployment.yaml
        log_success "kubedb-monitor-dashboard 재배포 완료"
    fi
}

# University App 재배포
redeploy_university_app() {
    log_info "🎓 University App 재배포"
    
    # Backend 재배포
    if kubectl get deployment university-registration-demo -n kubedb-monitor-test >/dev/null 2>&1; then
        redeploy_agent_deployments  # Agent가 포함되어 있음
    fi
    
    # UI 재배포
    if kubectl get deployment university-registration-ui -n kubedb-monitor-test >/dev/null 2>&1; then
        log_info "university-registration-ui 재배포 중..."
        kubectl delete deployment university-registration-ui -n kubedb-monitor-test
        kubectl apply -f k8s/university-registration-with-ui.yaml
        log_success "university-registration-ui 재배포 완료"
    fi
}

# 메인 실행 부분
main() {
    log_info "KubeDB Monitor Docker 이미지 빌드 시작"
    log_info "컴포넌트: $COMPONENT, 푸시: $PUSH_IMAGES"
    
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
            redeploy_component "$COMPONENT"
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