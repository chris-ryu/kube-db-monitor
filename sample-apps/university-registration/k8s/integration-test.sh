#!/bin/bash

# KubeDB Monitor Integration Test Script
# This script builds the sample application, creates Docker image, and deploys everything for testing

set -e

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# Configuration
REGISTRY="registry.bitgaram.info"
IMAGE_NAME="kubedb-monitor/university-registration"
TAG="latest"
FULL_IMAGE="${REGISTRY}/${IMAGE_NAME}:${TAG}"
NAMESPACE="kubedb-monitor-test"
APP_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
K8S_DIR="${APP_DIR}/k8s"

# Functions
log_info() {
    echo -e "${BLUE}[INFO]${NC} $1"
}

log_success() {
    echo -e "${GREEN}[SUCCESS]${NC} $1"
}

log_warning() {
    echo -e "${YELLOW}[WARNING]${NC} $1"
}

log_error() {
    echo -e "${RED}[ERROR]${NC} $1"
}

check_prerequisites() {
    log_info "Checking prerequisites..."
    
    # Check if Maven is installed
    if ! command -v mvn &> /dev/null; then
        log_error "Maven is not installed"
        exit 1
    fi
    
    # Check if Docker is installed
    if ! command -v docker &> /dev/null; then
        log_error "Docker is not installed"
        exit 1
    fi
    
    # Check if kubectl is installed
    if ! command -v kubectl &> /dev/null; then
        log_error "kubectl is not installed"
        exit 1
    fi
    
    # Check Kubernetes connection
    if ! kubectl cluster-info &> /dev/null; then
        log_error "Cannot connect to Kubernetes cluster"
        exit 1
    fi
    
    log_success "All prerequisites are satisfied"
}

build_application() {
    log_info "Building university registration application..."
    
    cd "${APP_DIR}"
    
    # Clean and build the application
    mvn clean package -DskipTests -q
    
    if [ $? -eq 0 ]; then
        log_success "Application built successfully"
    else
        log_error "Application build failed"
        exit 1
    fi
}

build_docker_image() {
    log_info "Building Docker image: ${FULL_IMAGE}"
    
    cd "${APP_DIR}"
    
    # Build Docker image
    docker build -t "${FULL_IMAGE}" .
    
    if [ $? -eq 0 ]; then
        log_success "Docker image built successfully"
    else
        log_error "Docker image build failed"
        exit 1
    fi
}

push_docker_image() {
    log_info "Pushing Docker image to registry..."
    
    # Push to registry
    docker push "${FULL_IMAGE}"
    
    if [ $? -eq 0 ]; then
        log_success "Docker image pushed successfully"
    else
        log_error "Docker image push failed"
        exit 1
    fi
}

setup_kubernetes() {
    log_info "Setting up Kubernetes resources..."
    
    # Create namespace
    kubectl apply -f "${K8S_DIR}/namespace.yaml"
    
    # Create registry secret (assuming credentials are already configured)
    if ! kubectl get secret registry-secret -n "${NAMESPACE}" &> /dev/null; then
        log_info "Creating registry secret..."
        kubectl create secret docker-registry registry-secret \
            --docker-server="${REGISTRY}" \
            --docker-username="admin" \
            --docker-password="qlcrkfka1#" \
            --namespace="${NAMESPACE}"
    fi
    
    log_success "Kubernetes namespace and secrets configured"
}

deploy_kubedb_monitor() {
    log_info "Deploying KubeDB Monitor system..."
    
    # Check if KubeDB Monitor CRDs exist
    if ! kubectl get crd kubedbmonitors.kubedb.io &> /dev/null; then
        log_warning "KubeDB Monitor CRDs not found. Applying CRDs..."
        
        # Apply KubeDB Monitor CRDs and operator
        cd "${APP_DIR}/../../../kubedb-monitor-controller"
        kubectl apply -f target/kubernetes/kubedb-monitor-crd.yaml
        kubectl apply -f target/kubernetes/kubedb-monitor-controller.yaml
    fi
    
    log_success "KubeDB Monitor system deployed"
}

deploy_sample_application() {
    log_info "Deploying university registration sample application..."
    
    # Apply deployment
    kubectl apply -f "${K8S_DIR}/deployment.yaml"
    
    # Wait for deployment to be ready
    log_info "Waiting for deployment to be ready..."
    kubectl rollout status deployment/university-registration -n "${NAMESPACE}" --timeout=300s
    
    if [ $? -eq 0 ]; then
        log_success "Sample application deployed successfully"
    else
        log_error "Sample application deployment failed"
        exit 1
    fi
}

run_integration_tests() {
    log_info "Running integration tests..."
    
    # Get service endpoint
    CLUSTER_IP=$(kubectl get svc university-registration-service -n "${NAMESPACE}" -o jsonpath='{.spec.clusterIP}')
    PORT=80
    
    log_info "Service available at: ${CLUSTER_IP}:${PORT}"
    
    # Test 1: Health check
    log_info "Test 1: Health check"
    kubectl run test-pod --image=curlimages/curl:latest --rm -i --tty --restart=Never -n "${NAMESPACE}" -- \
        curl -f "http://${CLUSTER_IP}:${PORT}/api/data/health"
    
    if [ $? -eq 0 ]; then
        log_success "Health check passed"
    else
        log_error "Health check failed"
    fi
    
    # Test 2: Data statistics
    log_info "Test 2: Data statistics"
    kubectl run test-pod --image=curlimages/curl:latest --rm -i --tty --restart=Never -n "${NAMESPACE}" -- \
        curl -f "http://${CLUSTER_IP}:${PORT}/api/data/stats"
    
    # Test 3: Performance test
    log_info "Test 3: Performance test"
    kubectl run test-pod --image=curlimages/curl:latest --rm -i --tty --restart=Never -n "${NAMESPACE}" -- \
        curl -f "http://${CLUSTER_IP}:${PORT}/api/data/performance-test"
    
    # Test 4: Check KubeDB Monitor metrics
    log_info "Test 4: Checking KubeDB Monitor metrics"
    sleep 10
    
    # Check if KubeDB Monitor is collecting metrics
    kubectl logs -l app=university-registration -n "${NAMESPACE}" --tail=50 | grep -i "kubedb\|monitor\|metrics"
    
    log_success "Integration tests completed"
}

generate_load() {
    log_info "Generating load for KubeDB Monitor testing..."
    
    CLUSTER_IP=$(kubectl get svc university-registration-service -n "${NAMESPACE}" -o jsonpath='{.spec.clusterIP}')
    PORT=80
    
    # Create a load generation script
    cat << EOF > /tmp/load-test.sh
#!/bin/bash
for i in {1..100}; do
    curl -s "http://${CLUSTER_IP}:${PORT}/api/courses?page=\$((RANDOM % 10))&size=20" > /dev/null &
    curl -s "http://${CLUSTER_IP}:${PORT}/api/data/performance-test" > /dev/null &
    curl -s "http://${CLUSTER_IP}:${PORT}/api/courses/popular?threshold=0.8" > /dev/null &
    
    if [ \$((i % 10)) -eq 0 ]; then
        echo "Completed \$i requests"
        sleep 1
    fi
done
wait
echo "Load test completed"
EOF

    # Run load test
    kubectl run load-test --image=curlimages/curl:latest --rm -i --restart=Never -n "${NAMESPACE}" -- \
        sh -c "$(cat /tmp/load-test.sh)"
    
    rm /tmp/load-test.sh
    
    log_success "Load test completed"
}

show_monitoring_info() {
    log_info "Showing monitoring information..."
    
    echo ""
    echo "=== KubeDB Monitor Integration Test Results ==="
    echo ""
    
    # Show pods status
    echo "Pods in ${NAMESPACE} namespace:"
    kubectl get pods -n "${NAMESPACE}" -o wide
    echo ""
    
    # Show services
    echo "Services in ${NAMESPACE} namespace:"
    kubectl get svc -n "${NAMESPACE}"
    echo ""
    
    # Show recent logs with KubeDB Monitor activity
    echo "Recent application logs (showing KubeDB Monitor activity):"
    kubectl logs -l app=university-registration -n "${NAMESPACE}" --tail=20 | grep -E "(kubedb|monitor|metrics|sql|performance)"
    echo ""
    
    # Show resource usage
    echo "Resource usage:"
    kubectl top pods -n "${NAMESPACE}" 2>/dev/null || echo "Metrics server not available"
    echo ""
    
    # Show KubeDB Monitor configuration
    echo "KubeDB Monitor annotations on deployment:"
    kubectl get deployment university-registration -n "${NAMESPACE}" -o jsonpath='{.spec.template.metadata.annotations}' | jq . 2>/dev/null || \
    kubectl get deployment university-registration -n "${NAMESPACE}" -o jsonpath='{.spec.template.metadata.annotations}'
    echo ""
    
    log_success "Monitoring information displayed"
}

cleanup() {
    if [ "${CLEANUP:-false}" = "true" ]; then
        log_info "Cleaning up test resources..."
        kubectl delete namespace "${NAMESPACE}" --timeout=60s
        log_success "Cleanup completed"
    else
        log_info "Skipping cleanup. To clean up manually, run: kubectl delete namespace ${NAMESPACE}"
    fi
}

main() {
    log_info "Starting KubeDB Monitor integration test..."
    
    # Parse command line arguments
    while [[ $# -gt 0 ]]; do
        case $1 in
            --cleanup)
                CLEANUP=true
                shift
                ;;
            --skip-build)
                SKIP_BUILD=true
                shift
                ;;
            --skip-push)
                SKIP_PUSH=true
                shift
                ;;
            --load-test)
                RUN_LOAD_TEST=true
                shift
                ;;
            *)
                log_error "Unknown argument: $1"
                exit 1
                ;;
        esac
    done
    
    # Execute steps
    check_prerequisites
    
    if [ "${SKIP_BUILD:-false}" != "true" ]; then
        build_application
        build_docker_image
    fi
    
    if [ "${SKIP_PUSH:-false}" != "true" ]; then
        push_docker_image
    fi
    
    setup_kubernetes
    deploy_kubedb_monitor
    deploy_sample_application
    run_integration_tests
    
    if [ "${RUN_LOAD_TEST:-false}" = "true" ]; then
        generate_load
    fi
    
    show_monitoring_info
    cleanup
    
    log_success "KubeDB Monitor integration test completed successfully!"
    echo ""
    echo "Next steps:"
    echo "1. Access the application: kubectl port-forward svc/university-registration-service 8080:80 -n ${NAMESPACE}"
    echo "2. View logs: kubectl logs -f deployment/university-registration -n ${NAMESPACE}"
    echo "3. Monitor metrics: kubectl port-forward svc/university-registration-service 8080:80 -n ${NAMESPACE} (then visit http://localhost:8080/actuator/prometheus)"
    echo "4. Test APIs: curl http://localhost:8080/api/data/stats"
}

# Run main function
main "$@"