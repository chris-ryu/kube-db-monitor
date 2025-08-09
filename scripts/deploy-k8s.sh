#!/bin/bash

set -e

echo "Deploying KubeDB Monitor to Kubernetes..."

# Check if kubectl is available
if ! command -v kubectl &> /dev/null; then
    echo "kubectl is not installed or not in PATH"
    exit 1
fi

# Create namespace
echo "Creating namespace..."
kubectl apply -f k8s/namespace.yaml

# Create registry secret
echo "Creating registry secret..."
kubectl apply -f k8s/registry-secret.yaml

# Create RBAC
echo "Creating RBAC..."
kubectl apply -f k8s/rbac.yaml

# Deploy controller
echo "Deploying controller..."
kubectl apply -f k8s/deployment.yaml

# Wait for deployment to be ready
echo "Waiting for controller deployment to be ready..."
kubectl wait --for=condition=available --timeout=300s deployment/kubedb-monitor-controller -n kubedb-monitor

# Configure webhooks
echo "Configuring admission webhooks..."
kubectl apply -f k8s/webhook-config.yaml

# Show status
echo "Deployment completed! Checking status..."
kubectl get all -n kubedb-monitor

echo ""
echo "To test the system, deploy the example app:"
echo "kubectl apply -f k8s/example-app.yaml"
echo ""
echo "To check logs:"
echo "kubectl logs -f deployment/kubedb-monitor-controller -n kubedb-monitor"