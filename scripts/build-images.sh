#!/bin/bash

set -e

REGISTRY="registry.bitgaram.info"
USERNAME="admin"
PASSWORD="qlcrkfka1#"

echo "Building KubeDB Monitor Docker images..."

# Build the project first
echo "Building Maven project..."
cd /Users/narzis/workspace/kube-db-monitor/sample-apps/university-registration
mvn clean package -DskipTests=true
cd ../..

# Login to registry
echo "Logging into registry..."
echo "$PASSWORD" | docker login "$REGISTRY" -u "$USERNAME" --password-stdin

# Build and tag agent image
echo "Building agent Docker image..."
docker build -f Dockerfile.agent -t "$REGISTRY/kubedb-monitor/agent:latest" .

# Build and tag controller image
echo "Building controller Docker image..."
docker build -f Dockerfile.controller -t "$REGISTRY/kubedb-monitor/controller:latest" .

# Build and tag dashboard frontend image
echo "Building dashboard frontend Docker image..."
docker build -t "$REGISTRY/kubedb-monitor/dashboard-frontend:latest" ./dashboard-frontend/

# Build and tag control plane image
echo "Building control plane Docker image..."
docker build -t "$REGISTRY/kubedb-monitor/control-plane:latest" ./control-plane/

# Push images to registry
echo "Pushing images to registry..."
docker push "$REGISTRY/kubedb-monitor/agent:latest"
docker push "$REGISTRY/kubedb-monitor/controller:latest"
docker push "$REGISTRY/kubedb-monitor/dashboard-frontend:latest"
docker push "$REGISTRY/kubedb-monitor/control-plane:latest"

echo "Docker images built and pushed successfully:"
docker images | grep "$REGISTRY/kubedb-monitor"

echo "To deploy to Kubernetes:"
echo "1. Create namespace: kubectl apply -f k8s/namespace.yaml"
echo "2. Create RBAC: kubectl apply -f k8s/rbac.yaml"
echo "3. Create deployment: kubectl apply -f k8s/deployment.yaml"
echo "4. Create webhook config: kubectl apply -f k8s/webhook-config.yaml"
echo "5. Deploy example app: kubectl apply -f k8s/example-app.yaml"