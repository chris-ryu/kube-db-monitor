#!/bin/bash

# KubeDB Monitor Agent 호환성 테스트를 위한 전체 데이터베이스 환경 배포 스크립트
# PostgreSQL, MySQL, MariaDB, Oracle, SQL Server 데이터베이스와 테스트 애플리케이션 일괄 배포

set -e

# 색상 정의
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
PURPLE='\033[0;35m'
CYAN='\033[0;36m'
NC='\033[0m' # No Color

# 로그 함수
log_info() { echo -e "${BLUE}[INFO]${NC} $1"; }
log_success() { echo -e "${GREEN}[SUCCESS]${NC} $1"; }
log_warn() { echo -e "${YELLOW}[WARN]${NC} $1"; }
log_error() { echo -e "${RED}[ERROR]${NC} $1"; }
log_section() { echo -e "\n${PURPLE}=== $1 ===${NC}\n"; }
log_subsection() { echo -e "\n${CYAN}--- $1 ---${NC}"; }

# 스크립트 설정
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" &> /dev/null && pwd)"
PROJECT_ROOT="$(dirname "$SCRIPT_DIR")"
DATABASES=("postgresql" "mysql" "mariadb" "oracle" "sqlserver")
DEPLOY_MODE="${1:-all}"  # all, databases-only, apps-only

log_section "KubeDB Monitor Agent 호환성 테스트 환경 배포"
log_info "프로젝트 루트: $PROJECT_ROOT"
log_info "배포 모드: $DEPLOY_MODE"

# 1. 사전 요구사항 확인
check_prerequisites() {
    log_subsection "사전 요구사항 확인"
    
    # kubectl 확인
    if ! command -v kubectl &> /dev/null; then
        log_error "kubectl이 설치되어 있지 않습니다"
        exit 1
    fi
    
    # Kubernetes 클러스터 연결 확인
    if ! kubectl cluster-info > /dev/null 2>&1; then
        log_error "Kubernetes 클러스터에 연결할 수 없습니다"
        exit 1
    fi
    
    # 필요한 파일들 확인
    local required_files=(
        "$PROJECT_ROOT/configs/agent-profiles.yaml"
        "$PROJECT_ROOT/k8s/databases/postgresql-cluster.yaml"
        "$PROJECT_ROOT/k8s/databases/mysql-cluster.yaml"
        "$PROJECT_ROOT/k8s/databases/mariadb-cluster.yaml"
        "$PROJECT_ROOT/k8s/databases/oracle-cluster.yaml"
        "$PROJECT_ROOT/k8s/databases/sqlserver-cluster.yaml"
        "$PROJECT_ROOT/k8s/agent-testing/postgresql-agent-test-deployments.yaml"
        "$PROJECT_ROOT/k8s/agent-testing/mysql-agent-test-deployment.yaml"
    )
    
    for file in "${required_files[@]}"; do
        if [ ! -f "$file" ]; then
            log_error "필수 파일을 찾을 수 없습니다: $file"
            exit 1
        fi
    done
    
    log_success "사전 요구사항 확인 완료"
}

# 2. Agent 설정 배포
deploy_agent_configs() {
    log_subsection "KubeDB Monitor Agent 설정 배포"
    
    # kubedb-monitor 네임스페이스 생성 (이미 존재하면 무시)
    kubectl create namespace kubedb-monitor --dry-run=client -o yaml | kubectl apply -f -
    
    # Agent 프로파일 ConfigMap 배포
    kubectl apply -f "$PROJECT_ROOT/configs/agent-profiles.yaml"
    
    log_success "Agent 설정 배포 완료"
}

# 3. PostgreSQL 환경 배포
deploy_postgresql() {
    log_subsection "PostgreSQL 환경 배포"
    
    # PostgreSQL이 이미 실행 중인지 확인
    if kubectl get namespace postgres-system > /dev/null 2>&1; then
        log_info "PostgreSQL 환경이 이미 존재합니다. 기존 환경을 사용합니다."
    else
        log_info "PostgreSQL 클러스터 배포 중..."
        # 실제 PostgreSQL 배포는 기존 설정 사용 (CloudNativePG 등)
        log_warn "PostgreSQL은 기존에 설치된 환경을 사용합니다."
    fi
    
    # PostgreSQL Agent 테스트 애플리케이션 배포
    if [ "$DEPLOY_MODE" = "all" ] || [ "$DEPLOY_MODE" = "apps-only" ]; then
        log_info "PostgreSQL Agent 테스트 애플리케이션 배포..."
        kubectl apply -f "$PROJECT_ROOT/k8s/agent-testing/postgresql-agent-test-deployments.yaml"
        
        # 배포 완료 대기
        kubectl rollout status deployment/university-registration-conservative -n kubedb-monitor-test --timeout=300s || log_warn "Conservative 배포 대기 시간 초과"
        kubectl rollout status deployment/university-registration-balanced -n kubedb-monitor-test --timeout=300s || log_warn "Balanced 배포 대기 시간 초과"
        kubectl rollout status deployment/university-registration-aggressive -n kubedb-monitor-test --timeout=300s || log_warn "Aggressive 배포 대기 시간 초과"
    fi
    
    log_success "PostgreSQL 환경 배포 완료"
}

# 4. MySQL 환경 배포
deploy_mysql() {
    log_subsection "MySQL 환경 배포"
    
    if [ "$DEPLOY_MODE" = "all" ] || [ "$DEPLOY_MODE" = "databases-only" ]; then
        log_info "MySQL 클러스터 배포 중..."
        kubectl apply -f "$PROJECT_ROOT/k8s/databases/mysql-cluster.yaml"
        
        # MySQL 준비 상태 대기
        log_info "MySQL 클러스터 준비 대기..."
        kubectl wait --for=condition=ready pod -l app=mysql-cluster -n mysql-system --timeout=600s || log_warn "MySQL 준비 대기 시간 초과"
    fi
    
    if [ "$DEPLOY_MODE" = "all" ] || [ "$DEPLOY_MODE" = "apps-only" ]; then
        log_info "MySQL Agent 테스트 애플리케이션 배포..."
        kubectl apply -f "$PROJECT_ROOT/k8s/agent-testing/mysql-agent-test-deployment.yaml"
        
        # 배포 완료 대기
        kubectl rollout status deployment/university-registration-mysql-conservative -n kubedb-monitor-mysql --timeout=300s || log_warn "MySQL Conservative 배포 대기 시간 초과"
        kubectl rollout status deployment/university-registration-mysql-balanced -n kubedb-monitor-mysql --timeout=300s || log_warn "MySQL Balanced 배포 대기 시간 초과"
    fi
    
    log_success "MySQL 환경 배포 완료"
}

# 5. MariaDB 환경 배포
deploy_mariadb() {
    log_subsection "MariaDB 환경 배포"
    
    if [ "$DEPLOY_MODE" = "all" ] || [ "$DEPLOY_MODE" = "databases-only" ]; then
        log_info "MariaDB 클러스터 배포 중..."
        kubectl apply -f "$PROJECT_ROOT/k8s/databases/mariadb-cluster.yaml"
        
        # MariaDB 준비 상태 대기
        log_info "MariaDB 클러스터 준비 대기..."
        kubectl wait --for=condition=ready pod -l app=mariadb-cluster -n mariadb-system --timeout=600s || log_warn "MariaDB 준비 대기 시간 초과"
    fi
    
    log_success "MariaDB 환경 배포 완료"
}

# 6. Oracle 환경 배포
deploy_oracle() {
    log_subsection "Oracle 환경 배포"
    
    if [ "$DEPLOY_MODE" = "all" ] || [ "$DEPLOY_MODE" = "databases-only" ]; then
        log_warn "Oracle Database 23c Free 배포 중... (시간이 오래 걸릴 수 있습니다)"
        kubectl apply -f "$PROJECT_ROOT/k8s/databases/oracle-cluster.yaml"
        
        # Oracle 준비 상태 대기 (더 긴 시간 필요)
        log_info "Oracle 데이터베이스 초기화 대기... (최대 15분)"
        kubectl wait --for=condition=ready pod -l app=oracle-cluster -n oracle-system --timeout=900s || log_warn "Oracle 준비 대기 시간 초과"
    fi
    
    log_success "Oracle 환경 배포 완료"
}

# 7. SQL Server 환경 배포
deploy_sqlserver() {
    log_subsection "SQL Server 환경 배포"
    
    if [ "$DEPLOY_MODE" = "all" ] || [ "$DEPLOY_MODE" = "databases-only" ]; then
        log_info "SQL Server 2022 배포 중..."
        kubectl apply -f "$PROJECT_ROOT/k8s/databases/sqlserver-cluster.yaml"
        
        # SQL Server 준비 상태 대기
        log_info "SQL Server 클러스터 준비 대기..."
        kubectl wait --for=condition=ready pod -l app=sqlserver-cluster -n sqlserver-system --timeout=600s || log_warn "SQL Server 준비 대기 시간 초과"
    fi
    
    log_success "SQL Server 환경 배포 완료"
}

# 8. 배포 상태 확인
check_deployment_status() {
    log_section "배포 상태 확인"
    
    echo "| 데이터베이스 | 네임스페이스 | Pod 상태 | 서비스 상태 |"
    echo "|------------|-------------|----------|------------|"
    
    # PostgreSQL 확인
    local pg_pods=$(kubectl get pods -n kubedb-monitor-test --no-headers 2>/dev/null | wc -l)
    local pg_services=$(kubectl get services -n kubedb-monitor-test --no-headers 2>/dev/null | wc -l)
    echo "| PostgreSQL | kubedb-monitor-test | $pg_pods 개 | $pg_services 개 |"
    
    # MySQL 확인
    local mysql_pods=$(kubectl get pods -n mysql-system --no-headers 2>/dev/null | wc -l)
    local mysql_services=$(kubectl get services -n mysql-system --no-headers 2>/dev/null | wc -l)
    echo "| MySQL | mysql-system | $mysql_pods 개 | $mysql_services 개 |"
    
    # MariaDB 확인
    local mariadb_pods=$(kubectl get pods -n mariadb-system --no-headers 2>/dev/null | wc -l)
    local mariadb_services=$(kubectl get services -n mariadb-system --no-headers 2>/dev/null | wc -l)
    echo "| MariaDB | mariadb-system | $mariadb_pods 개 | $mariadb_services 개 |"
    
    # Oracle 확인
    local oracle_pods=$(kubectl get pods -n oracle-system --no-headers 2>/dev/null | wc -l)
    local oracle_services=$(kubectl get services -n oracle-system --no-headers 2>/dev/null | wc -l)
    echo "| Oracle | oracle-system | $oracle_pods 개 | $oracle_services 개 |"
    
    # SQL Server 확인
    local sqlserver_pods=$(kubectl get pods -n sqlserver-system --no-headers 2>/dev/null | wc -l)
    local sqlserver_services=$(kubectl get services -n sqlserver-system --no-headers 2>/dev/null | wc -l)
    echo "| SQL Server | sqlserver-system | $sqlserver_pods 개 | $sqlserver_services 개 |"
    
    echo ""
    log_info "상세 상태 확인 명령어:"
    echo "  kubectl get pods --all-namespaces | grep -E '(postgres|mysql|mariadb|oracle|sqlserver)'"
    echo "  kubectl get services --all-namespaces | grep -E '(postgres|mysql|mariadb|oracle|sqlserver)'"
}

# 9. 테스트 준비
prepare_testing() {
    log_section "테스트 환경 준비"
    
    log_info "Agent 프로파일 테스트 스크립트 준비..."
    if [ -f "$PROJECT_ROOT/scripts/test-agent-profiles.sh" ]; then
        log_success "PostgreSQL Agent 프로파일 테스트: $PROJECT_ROOT/scripts/test-agent-profiles.sh"
    fi
    
    if [ -f "$PROJECT_ROOT/scripts/comprehensive-agent-benchmark.sh" ]; then
        log_success "종합 성능 벤치마크: $PROJECT_ROOT/scripts/comprehensive-agent-benchmark.sh"
    fi
    
    echo ""
    log_info "테스트 실행 명령어:"
    echo "  # PostgreSQL Agent 프로파일 테스트"
    echo "  $PROJECT_ROOT/scripts/test-agent-profiles.sh"
    echo ""
    echo "  # 종합 성능 벤치마크 (모든 DB)"
    echo "  $PROJECT_ROOT/scripts/comprehensive-agent-benchmark.sh"
    echo ""
    echo "  # 개별 데이터베이스 접속 테스트"
    echo "  kubectl exec -it deployment/postgres-cluster-rw -n postgres-system -- psql -h localhost -U postgres"
    echo "  kubectl exec -it deployment/mysql-cluster -n mysql-system -- mysql -u root -p"
}

# 10. 정리 스크립트 생성
generate_cleanup_script() {
    log_subsection "정리 스크립트 생성"
    
    local cleanup_script="$PROJECT_ROOT/scripts/cleanup-all-databases.sh"
    
    cat > "$cleanup_script" << 'EOF'
#!/bin/bash

# KubeDB Monitor Agent 테스트 환경 정리 스크립트
# 모든 테스트 데이터베이스와 애플리케이션을 제거합니다

set -e

echo "🗑️  KubeDB Monitor Agent 테스트 환경 정리 중..."

# 테스트 애플리케이션 제거
echo "📱 테스트 애플리케이션 제거..."
kubectl delete -f /Users/narzis/workspace/kube-db-monitor/k8s/agent-testing/ --ignore-not-found=true

# 데이터베이스 제거
echo "🗄️  데이터베이스 클러스터 제거..."
kubectl delete -f /Users/narzis/workspace/kube-db-monitor/k8s/databases/ --ignore-not-found=true

# Agent 설정 제거
echo "⚙️  Agent 설정 제거..."
kubectl delete -f /Users/narzis/workspace/kube-db-monitor/configs/agent-profiles.yaml --ignore-not-found=true

# 네임스페이스 제거
echo "📁 네임스페이스 제거..."
kubectl delete namespace mysql-system mariadb-system oracle-system sqlserver-system kubedb-monitor-mysql kubedb-monitor-mariadb kubedb-monitor-oracle kubedb-monitor-sqlserver --ignore-not-found=true

echo "✅ 정리 완료!"
echo "📝 PostgreSQL과 kubedb-monitor-test 네임스페이스는 수동으로 관리해주세요."
EOF
    
    chmod +x "$cleanup_script"
    
    log_success "정리 스크립트 생성: $cleanup_script"
}

# 메인 실행 함수
main() {
    local start_time=$(date +%s)
    
    check_prerequisites
    deploy_agent_configs
    
    case $DEPLOY_MODE in
        "all")
            deploy_postgresql
            deploy_mysql
            deploy_mariadb
            deploy_oracle
            deploy_sqlserver
            ;;
        "databases-only")
            deploy_mysql
            deploy_mariadb
            deploy_oracle
            deploy_sqlserver
            ;;
        "apps-only")
            deploy_postgresql
            deploy_mysql
            deploy_mariadb
            ;;
        *)
            log_error "알 수 없는 배포 모드: $DEPLOY_MODE"
            log_info "사용법: $0 [all|databases-only|apps-only]"
            exit 1
            ;;
    esac
    
    check_deployment_status
    prepare_testing
    generate_cleanup_script
    
    local end_time=$(date +%s)
    local duration=$((end_time - start_time))
    
    log_section "배포 완료"
    log_success "전체 배포 시간: ${duration}초"
    log_success "KubeDB Monitor Agent 호환성 테스트 환경이 준비되었습니다!"
    
    echo ""
    log_info "다음 단계:"
    echo "  1. 데이터베이스 준비 상태 확인"
    echo "  2. Agent 프로파일 테스트 실행"
    echo "  3. 종합 성능 벤치마크 실행"
    echo "  4. 테스트 완료 후 정리 스크립트 실행"
    
    echo ""
    log_warn "참고사항:"
    echo "  • Oracle 데이터베이스는 초기화에 시간이 걸릴 수 있습니다"
    echo "  • 각 데이터베이스의 로그를 확인하여 정상 작동을 확인하세요"
    echo "  • 리소스 부족 시 일부 데이터베이스를 선택적으로 배포하세요"
}

# 스크립트 실행
if [[ "${BASH_SOURCE[0]}" == "${0}" ]]; then
    main "$@"
fi