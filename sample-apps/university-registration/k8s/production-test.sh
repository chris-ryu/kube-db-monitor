#!/bin/bash

# KubeDB Monitor Production Cluster Integration Test
# 실제 Kubernetes 클러스터에서 KubeDB Monitor 전체 시스템 테스트

set -e

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
MAGENTA='\033[0;35m'
NC='\033[0m' # No Color

# Configuration
REGISTRY="registry.bitgaram.info"
CONTROLLER_IMAGE="kubedb-monitor/controller:latest"
APP_IMAGE="kubedb-monitor/university-registration:latest"
WEBHOOK_DOMAIN="kubedb-monitor-webhook.bitgaram.info"
APP_DOMAIN="university-registration.bitgaram.info"
CONTROLLER_NAMESPACE="kubedb-monitor-system"
TEST_NAMESPACE="kubedb-monitor-test"

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

log_step() {
    echo -e "${MAGENTA}[STEP]${NC} $1"
}

check_prerequisites() {
    log_step "1. Checking prerequisites..."
    
    # Check kubectl connection
    if ! kubectl cluster-info &> /dev/null; then
        log_error "Cannot connect to Kubernetes cluster"
        exit 1
    fi
    
    # Check current context
    CURRENT_CONTEXT=$(kubectl config current-context)
    log_info "Current Kubernetes context: ${CURRENT_CONTEXT}"
    
    if [[ ! "$CURRENT_CONTEXT" == *"kubernetes-admin"* ]]; then
        log_warning "Not using the expected context. Current: ${CURRENT_CONTEXT}"
        read -p "Continue anyway? (y/N): " -n 1 -r
        echo
        if [[ ! $REPLY =~ ^[Yy]$ ]]; then
            exit 1
        fi
    fi
    
    # Check required tools
    for tool in kubectl curl jq; do
        if ! command -v $tool &> /dev/null; then
            log_error "$tool is not installed"
            exit 1
        fi
    done
    
    log_success "Prerequisites check passed"
}

setup_registry_secret() {
    log_step "2. Setting up registry secrets..."
    
    # Create namespaces first
    kubectl create namespace "$CONTROLLER_NAMESPACE" --dry-run=client -o yaml | kubectl apply -f -
    kubectl create namespace "$TEST_NAMESPACE" --dry-run=client -o yaml | kubectl apply -f -
    
    # Create registry secrets in both namespaces
    for ns in "$CONTROLLER_NAMESPACE" "$TEST_NAMESPACE"; do
        if ! kubectl get secret registry-secret -n "$ns" &> /dev/null; then
            log_info "Creating registry secret in namespace $ns..."
            kubectl create secret docker-registry registry-secret \
                --docker-server="$REGISTRY" \
                --docker-username="admin" \
                --docker-password="qlcrkfka1#" \
                --namespace="$ns"
        else
            log_info "Registry secret already exists in namespace $ns"
        fi
    done
    
    log_success "Registry secrets configured"
}

build_and_push_controller() {
    log_step "3. Building and pushing KubeDB Monitor Controller..."
    
    # Build controller image
    cd "$(dirname "${BASH_SOURCE[0]}")/../../../kubedb-monitor-controller"
    
    if [[ "${SKIP_BUILD:-false}" != "true" ]]; then
        log_info "Building controller application..."
        mvn clean package -DskipTests -q
        
        log_info "Building controller Docker image..."
        docker build -t "$REGISTRY/$CONTROLLER_IMAGE" .
        
        log_info "Pushing controller image..."
        docker push "$REGISTRY/$CONTROLLER_IMAGE"
    else
        log_info "Skipping controller build (--skip-build flag)"
    fi
    
    log_success "Controller image ready"
}

build_and_push_app() {
    log_step "4. Building and pushing sample application..."
    
    cd "$(dirname "${BASH_SOURCE[0]}")/.."
    
    if [[ "${SKIP_BUILD:-false}" != "true" ]]; then
        log_info "Building sample application..."
        mvn clean package -DskipTests -q
        
        log_info "Building application Docker image..."
        docker build -t "$REGISTRY/$APP_IMAGE" .
        
        log_info "Pushing application image..."
        docker push "$REGISTRY/$APP_IMAGE"
    else
        log_info "Skipping application build (--skip-build flag)"
    fi
    
    log_success "Application image ready"
}

deploy_controller() {
    log_step "5. Deploying KubeDB Monitor Controller..."
    
    cd "$(dirname "${BASH_SOURCE[0]}")"
    
    log_info "Applying controller resources..."
    kubectl apply -f kubedb-monitor-controller.yaml
    
    log_info "Waiting for controller deployment to be ready..."
    kubectl rollout status deployment/kubedb-monitor-controller -n "$CONTROLLER_NAMESPACE" --timeout=300s
    
    if [ $? -eq 0 ]; then
        log_success "Controller deployed successfully"
    else
        log_error "Controller deployment failed"
        kubectl describe deployment/kubedb-monitor-controller -n "$CONTROLLER_NAMESPACE"
        kubectl logs -l app=kubedb-monitor-controller -n "$CONTROLLER_NAMESPACE" --tail=50
        exit 1
    fi
}

deploy_application() {
    log_step "6. Deploying sample application..."
    
    log_info "Applying application resources..."
    kubectl apply -f namespace.yaml
    kubectl apply -f production-deployment.yaml
    
    log_info "Waiting for application deployment to be ready..."
    kubectl rollout status deployment/university-registration -n "$TEST_NAMESPACE" --timeout=300s
    
    if [ $? -eq 0 ]; then
        log_success "Application deployed successfully"
    else
        log_error "Application deployment failed"
        kubectl describe deployment/university-registration -n "$TEST_NAMESPACE"
        kubectl logs -l app=university-registration -n "$TEST_NAMESPACE" --tail=50
        exit 1
    fi
}

verify_dns_resolution() {
    log_step "7. Verifying DNS resolution..."
    
    for domain in "$WEBHOOK_DOMAIN" "$APP_DOMAIN"; do
        log_info "Testing DNS resolution for $domain..."
        
        if nslookup "$domain" &> /dev/null; then
            IP=$(nslookup "$domain" | grep "Address:" | tail -1 | awk '{print $2}')
            log_success "$domain resolves to $IP"
        else
            log_warning "$domain DNS resolution failed - this may be expected if DNS is not yet propagated"
        fi
    done
}

test_webhook_functionality() {
    log_step "8. Testing webhook functionality..."
    
    log_info "Checking webhook registration..."
    kubectl get mutatingadmissionwebhooks kubedb-monitor-webhook -o yaml > /tmp/webhook-config.yaml
    
    if grep -q "$WEBHOOK_DOMAIN" /tmp/webhook-config.yaml; then
        log_success "Webhook is properly registered with domain $WEBHOOK_DOMAIN"
    else
        log_error "Webhook configuration issue - domain not found"
        cat /tmp/webhook-config.yaml
    fi
    
    # Test webhook by creating a test pod with KubeDB Monitor annotations
    log_info "Testing webhook injection with a test pod..."
    
    cat << EOF | kubectl apply -f -
apiVersion: v1
kind: Pod
metadata:
  name: kubedb-monitor-test-pod
  namespace: $TEST_NAMESPACE
  annotations:
    kubedb.monitor/enable: "true"
    kubedb.monitor/db-types: "h2"
    kubedb.monitor/sampling-rate: "1.0"
spec:
  containers:
  - name: test
    image: alpine:latest
    command: ["sleep", "300"]
  restartPolicy: Never
EOF
    
    sleep 10
    
    # Check if the pod was modified by the webhook
    if kubectl get pod kubedb-monitor-test-pod -n "$TEST_NAMESPACE" -o yaml | grep -q "kubedb-monitor-agent"; then
        log_success "Webhook successfully injected KubeDB Monitor agent"
    else
        log_warning "Webhook injection may not be working properly"
        kubectl get pod kubedb-monitor-test-pod -n "$TEST_NAMESPACE" -o yaml
    fi
    
    # Clean up test pod
    kubectl delete pod kubedb-monitor-test-pod -n "$TEST_NAMESPACE" --ignore-not-found=true
}

run_application_tests() {
    log_step "9. Running application functionality tests..."
    
    # Get application service ClusterIP
    APP_IP=$(kubectl get svc university-registration-service -n "$TEST_NAMESPACE" -o jsonpath='{.spec.clusterIP}')
    log_info "Application service available at: $APP_IP:80"
    
    # Test 1: Health check
    log_info "Test 1: Health check"
    if kubectl run test-curl --image=curlimages/curl:latest --rm -i --restart=Never -n "$TEST_NAMESPACE" -- \
        curl -f -s "http://$APP_IP/api/data/health" > /tmp/health-response.json 2>/dev/null; then
        log_success "Health check passed"
        echo "Response: $(cat /tmp/health-response.json)"
    else
        log_error "Health check failed"
        kubectl logs -l app=university-registration -n "$TEST_NAMESPACE" --tail=20
    fi
    
    # Test 2: Data statistics
    log_info "Test 2: Data statistics"
    if kubectl run test-curl --image=curlimages/curl:latest --rm -i --restart=Never -n "$TEST_NAMESPACE" -- \
        curl -f -s "http://$APP_IP/api/data/stats" > /tmp/stats-response.json 2>/dev/null; then
        log_success "Data statistics retrieved"
        echo "Stats: $(cat /tmp/stats-response.json | head -5)"
    else
        log_warning "Data statistics test failed"
    fi
    
    # Test 3: Course search (DB intensive)
    log_info "Test 3: Course search with pagination"
    kubectl run test-curl --image=curlimages/curl:latest --rm -i --restart=Never -n "$TEST_NAMESPACE" -- \
        curl -f -s "http://$APP_IP/api/courses?page=0&size=10" > /tmp/courses-response.json 2>/dev/null || true
    
    # Test 4: Performance test (triggers many DB queries)
    log_info "Test 4: Performance test (triggers many DB operations)"
    kubectl run test-curl --image=curlimages/curl:latest --rm -i --restart=Never -n "$TEST_NAMESPACE" -- \
        curl -f -s "http://$APP_IP/api/data/performance-test" > /tmp/perf-response.json 2>/dev/null || true
    
    log_success "Application tests completed"
}

verify_monitoring_data() {
    log_step "10. Verifying KubeDB Monitor data collection..."
    
    log_info "Waiting for metrics collection (30 seconds)..."
    sleep 30
    
    # Check application logs for KubeDB Monitor activity
    log_info "Checking application logs for KubeDB Monitor activity..."
    kubectl logs -l app=university-registration -n "$TEST_NAMESPACE" --tail=100 > /tmp/app-logs.txt
    
    if grep -qi "kubedb\|monitor\|agent\|jdbc.*time\|sql.*executed" /tmp/app-logs.txt; then
        log_success "KubeDB Monitor is actively collecting metrics!"
        echo "Sample monitoring logs:"
        grep -i "kubedb\|monitor\|agent\|jdbc.*time\|sql.*executed" /tmp/app-logs.txt | tail -5
    else
        log_warning "KubeDB Monitor activity not clearly visible in logs"
        echo "Recent application logs:"
        tail -10 /tmp/app-logs.txt
    fi
    
    # Check Prometheus metrics
    log_info "Checking Prometheus metrics endpoint..."
    APP_IP=$(kubectl get svc university-registration-service -n "$TEST_NAMESPACE" -o jsonpath='{.spec.clusterIP}')
    kubectl run test-curl --image=curlimages/curl:latest --rm -i --restart=Never -n "$TEST_NAMESPACE" -- \
        curl -s "http://$APP_IP/actuator/prometheus" > /tmp/metrics.txt 2>/dev/null || true
    
    if [ -s /tmp/metrics.txt ] && grep -q "jvm\|http\|hikari" /tmp/metrics.txt; then
        log_success "Prometheus metrics are being exported"
        echo "Sample metrics:"
        grep -E "(jvm_|hikari_|http_)" /tmp/metrics.txt | head -3
    else
        log_warning "Prometheus metrics may not be properly configured"
    fi
}

load_test() {
    if [[ "${RUN_LOAD_TEST:-false}" == "true" ]]; then
        log_step "11. Running load test..."
        
        APP_IP=$(kubectl get svc university-registration-service -n "$TEST_NAMESPACE" -o jsonpath='{.spec.clusterIP}')
        
        log_info "Generating load for KubeDB Monitor testing..."
        
        # Create multiple parallel requests to generate DB load
        for i in {1..5}; do
            kubectl run load-test-$i --image=curlimages/curl:latest --rm --restart=Never -n "$TEST_NAMESPACE" -- \
                sh -c "
                for j in {1..20}; do
                    curl -s http://$APP_IP/api/courses?page=\$((j % 10)) > /dev/null &
                    curl -s http://$APP_IP/api/data/performance-test > /dev/null &
                    curl -s http://$APP_IP/api/courses/popular > /dev/null &
                done
                wait
                echo 'Load test $i completed'
                " &
        done
        
        wait
        log_success "Load test completed"
        
        # Check monitoring data after load test
        log_info "Checking monitoring data after load test..."
        sleep 10
        kubectl logs -l app=university-registration -n "$TEST_NAMESPACE" --tail=50 | grep -i "monitor\|agent\|sql" | tail -10
    fi
}

show_results() {
    log_step "12. Displaying results..."
    
    echo ""
    echo "============================================="
    echo "   KubeDB Monitor Production Test Results"
    echo "============================================="
    echo ""
    
    # Show deployment status
    echo "📦 Deployment Status:"
    echo "Controller Namespace: $CONTROLLER_NAMESPACE"
    kubectl get all -n "$CONTROLLER_NAMESPACE" --show-labels=false
    echo ""
    echo "Application Namespace: $TEST_NAMESPACE"
    kubectl get all -n "$TEST_NAMESPACE" --show-labels=false
    echo ""
    
    # Show ingress status
    echo "🌐 Ingress Configuration:"
    kubectl get ingress -n "$CONTROLLER_NAMESPACE" -o wide 2>/dev/null || echo "No ingress in controller namespace"
    kubectl get ingress -n "$TEST_NAMESPACE" -o wide 2>/dev/null || echo "No ingress in test namespace"
    echo ""
    
    # Show webhook configuration
    echo "🔗 Webhook Configuration:"
    kubectl get mutatingadmissionwebhooks kubedb-monitor-webhook -o custom-columns=NAME:.metadata.name,WEBHOOK:.webhooks[0].clientConfig.url
    echo ""
    
    # Show resource usage
    echo "📊 Resource Usage:"
    kubectl top pods -n "$CONTROLLER_NAMESPACE" 2>/dev/null || echo "Metrics server not available for controller namespace"
    kubectl top pods -n "$TEST_NAMESPACE" 2>/dev/null || echo "Metrics server not available for test namespace"
    echo ""
    
    # Show access URLs
    echo "🔗 Access URLs:"
    echo "• Application: https://$APP_DOMAIN"
    echo "• Webhook: https://$WEBHOOK_DOMAIN"
    echo "• Health Check: https://$APP_DOMAIN/api/data/health"
    echo "• Statistics: https://$APP_DOMAIN/api/data/stats"
    echo "• Metrics: https://$APP_DOMAIN/actuator/prometheus"
    echo ""
    
    echo "🎯 Manual Testing Commands:"
    echo "# Port forward for local testing:"
    echo "kubectl port-forward svc/university-registration-service 8080:80 -n $TEST_NAMESPACE"
    echo ""
    echo "# View real-time logs:"
    echo "kubectl logs -f deployment/university-registration -n $TEST_NAMESPACE"
    echo ""
    echo "# Monitor controller logs:"
    echo "kubectl logs -f deployment/kubedb-monitor-controller -n $CONTROLLER_NAMESPACE"
    echo ""
    
    log_success "Production test completed successfully!"
}

cleanup() {
    if [[ "${CLEANUP:-false}" == "true" ]]; then
        log_step "Cleaning up resources..."
        
        log_info "Removing application resources..."
        kubectl delete namespace "$TEST_NAMESPACE" --timeout=60s --ignore-not-found=true
        
        log_info "Removing controller resources..."
        kubectl delete namespace "$CONTROLLER_NAMESPACE" --timeout=60s --ignore-not-found=true
        
        log_info "Removing webhook configuration..."
        kubectl delete mutatingadmissionwebhooks kubedb-monitor-webhook --ignore-not-found=true
        
        log_success "Cleanup completed"
    else
        log_info "Skipping cleanup. To clean up manually:"
        echo "kubectl delete namespace $TEST_NAMESPACE"
        echo "kubectl delete namespace $CONTROLLER_NAMESPACE" 
        echo "kubectl delete mutatingadmissionwebhooks kubedb-monitor-webhook"
    fi
}

main() {
    echo "🚀 Starting KubeDB Monitor Production Cluster Integration Test"
    echo "Target Domains: $WEBHOOK_DOMAIN, $APP_DOMAIN"
    echo ""
    
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
            --load-test)
                RUN_LOAD_TEST=true
                shift
                ;;
            --help)
                echo "Usage: $0 [--cleanup] [--skip-build] [--load-test] [--help]"
                echo ""
                echo "Options:"
                echo "  --cleanup     Clean up all resources after test"
                echo "  --skip-build  Skip building and pushing Docker images"
                echo "  --load-test   Run load test to generate more monitoring data"
                echo "  --help        Show this help message"
                exit 0
                ;;
            *)
                log_error "Unknown argument: $1"
                echo "Use --help for usage information"
                exit 1
                ;;
        esac
    done
    
    # Execute test steps
    check_prerequisites
    setup_registry_secret
    build_and_push_controller
    build_and_push_app
    deploy_controller
    deploy_application
    verify_dns_resolution
    test_webhook_functionality
    run_application_tests
    verify_monitoring_data
    load_test
    show_results
    cleanup
    
    echo ""
    echo "🎉 KubeDB Monitor is now running in production cluster!"
    echo "📋 Next steps:"
    echo "1. Verify DNS propagation for the domains"
    echo "2. Configure SSL certificates (cert-manager)"
    echo "3. Monitor the logs for KubeDB Monitor activity"
    echo "4. Set up monitoring dashboards (Grafana/Prometheus)"
    echo ""
}

# Run main function
main "$@"