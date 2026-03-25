#!/usr/bin/env bash
set -euo pipefail

CLUSTER_NAME="${1:-shop-kind}"
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
ROOT_DIR="$(cd "${SCRIPT_DIR}/.." && pwd)"

echo "╔══════════════════════════════════════════╗"
echo "║   Shop Platform — Kind Cluster Setup     ║"
echo "╚══════════════════════════════════════════╝"
echo ""

# 1. Create Kind cluster
if kind get clusters 2>/dev/null | grep -q "^${CLUSTER_NAME}$"; then
  echo "✅ Kind cluster '${CLUSTER_NAME}' already exists"
else
  echo "📦 Creating Kind cluster '${CLUSTER_NAME}'..."
  kind create cluster --name "${CLUSTER_NAME}" --config "${ROOT_DIR}/kind/cluster-config.yaml"
  echo "✅ Kind cluster created"
fi

# 2. Install Cilium CNI
echo ""
echo "🔒 Installing Cilium CNI..."
cilium install \
  --set kubeProxyReplacement=true \
  --set k8sServiceHost=kind-control-plane \
  --set k8sServicePort=6443
cilium status --wait
echo "✅ Cilium installed"

# 3. Build all Docker images
echo ""
echo "🔨 Building Docker images..."
bash "${ROOT_DIR}/scripts/build-images.sh"
echo "✅ Docker images built"

# 3. Load images into Kind
echo ""
echo "📤 Loading images into Kind..."
bash "${ROOT_DIR}/scripts/load-images-kind.sh" "${CLUSTER_NAME}"
echo "✅ Images loaded"

# 4. Apply Kubernetes manifests
echo ""
echo "🚀 Deploying to Kind cluster..."
kubectl apply -f "${ROOT_DIR}/k8s/namespace.yaml"
kubectl apply -f "${ROOT_DIR}/k8s/infra/base.yaml"

echo "⏳ Waiting for MySQL to be ready..."
kubectl -n shop wait --for=condition=ready pod -l app=mysql --timeout=120s 2>/dev/null || true

echo "⏳ Waiting for Kafka to be ready..."
kubectl -n shop wait --for=condition=ready pod -l app=kafka --timeout=120s 2>/dev/null || true

kubectl apply -f "${ROOT_DIR}/k8s/apps/platform.yaml"
echo "✅ All manifests applied"

# 5. Create NodePort service for gateway access
echo ""
echo "🌐 Exposing api-gateway via NodePort..."
kubectl -n shop patch svc api-gateway -p '{"spec":{"type":"NodePort","ports":[{"port":8080,"targetPort":8080,"nodePort":30080,"name":"http"}]}}' 2>/dev/null || true

# 6. Wait for core services
echo ""
echo "⏳ Waiting for services to become ready (this may take 2-3 minutes)..."
for svc in auth-server api-gateway buyer-bff seller-bff profile-service promotion-service wallet-service marketplace-service order-service search-service notification-service loyalty-service activity-service webhook-service subscription-service buyer-portal seller-portal; do
  kubectl -n shop wait --for=condition=ready pod -l app=${svc} --timeout=180s 2>/dev/null || echo "⚠️  ${svc} not ready yet"
done

echo ""
echo "╔══════════════════════════════════════════╗"
echo "║          🎉 Setup Complete!              ║"
echo "╠══════════════════════════════════════════╣"
echo "║  Gateway:    http://localhost:8080       ║"
echo "║  Mailpit:    http://localhost:8025       ║"
echo "║  Prometheus: http://localhost:9090       ║"
echo "╠══════════════════════════════════════════╣"
echo "║  kubectl -n shop get pods                ║"
echo "║  kubectl -n shop logs -f <pod>           ║"
echo "╚══════════════════════════════════════════╝"
