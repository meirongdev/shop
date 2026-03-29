#!/usr/bin/env bash
set -euo pipefail

cluster_name="${1:-shop-kind}"
script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
repo_root="$(cd "${script_dir}/.." && pwd)"
context_name="kind-${cluster_name}"

wait_for_deployment() {
  local namespace="$1"
  local deployment_name="$2"
  kubectl --context "${context_name}" -n "${namespace}" rollout status "deployment/${deployment_name}" --timeout=300s
}

wait_for_statefulset() {
  local namespace="$1"
  local statefulset_name="$2"
  kubectl --context "${context_name}" -n "${namespace}" rollout status "statefulset/${statefulset_name}" --timeout=300s
}

if kind get clusters 2>/dev/null | grep -q "^${cluster_name}$"; then
  echo "✅ Kind cluster '${cluster_name}' already exists"
  kind export kubeconfig --name "${cluster_name}" >/dev/null
else
  echo "📦 Creating Kind cluster '${cluster_name}'..."
  kind create cluster --name "${cluster_name}" --config kind/cluster-config.yaml
  echo "✅ Kind cluster created"
fi

kubectl config use-context "${context_name}" >/dev/null

bash "${repo_root}/scripts/setup-local-registry.sh" "${cluster_name}"

control_plane_node="$(kind get nodes --name "${cluster_name}" | head -n 1)"
if ! kubectl --context "${context_name}" -n kube-system get daemonset cilium >/dev/null 2>&1; then
  echo "🔒 Installing Cilium CNI..."
  cilium install \
    --context "${context_name}" \
    --set kubeProxyReplacement=true \
    --set k8sServiceHost="${control_plane_node}" \
    --set k8sServicePort=6443
else
  echo "✅ Cilium already installed"
fi

echo "⏳ Waiting for Cilium and node readiness..."
cilium status --context "${context_name}" --wait
kubectl --context "${context_name}" wait --for=condition=Ready "node/${control_plane_node}" --timeout=300s

kubectl --context "${context_name}" apply -f "${repo_root}/k8s/namespace.yaml"
kubectl --context "${context_name}" apply -f "${repo_root}/k8s/infra/base.yaml"

echo "⏳ Waiting for infrastructure deployments..."
wait_for_deployment shop mysql &
wait_for_deployment shop redis &
wait_for_deployment shop kafka &
wait_for_deployment shop mailpit &
wait_for_deployment shop otel-collector &
wait_for_deployment shop prometheus &
wait_for_deployment shop spring-cloud-kubernetes-configuration-watcher &
wait_for_statefulset shop meilisearch &
wait

echo "✅ Kind infrastructure is ready"
