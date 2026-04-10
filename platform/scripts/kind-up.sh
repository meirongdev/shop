#!/usr/bin/env bash
set -euo pipefail

cluster_name="${1:-shop-kind}"
script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
repo_root="$(cd "${script_dir}/../.." && pwd)"
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

  # Detect and fix stale IP in kubelet.conf after container restarts (e.g. OrbStack IP churn).
  # The container's eth0 IP can change; kubelet stores the old IP in /etc/kubernetes/kubelet.conf.
  control_plane_container="${cluster_name}-control-plane"
  current_ip="$(docker exec "${control_plane_container}" ip -4 addr show eth0 2>/dev/null \
    | awk '/inet / {split($2,a,"/"); print a[1]}')"
  if [[ -n "${current_ip}" ]]; then
    stale_ip="$(docker exec "${control_plane_container}" \
      grep -oP '(?<=server: https://)[\d.]+(?=:6443)' /etc/kubernetes/kubelet.conf 2>/dev/null || true)"
    if [[ -n "${stale_ip}" && "${stale_ip}" != "${current_ip}" ]]; then
      echo "⚠️  Kubelet kubeconfig has stale IP ${stale_ip} (current: ${current_ip}). Fixing…"
      docker exec "${control_plane_container}" \
        sed -i "s|server: https://${stale_ip}:6443|server: https://${current_ip}:6443|g" \
        /etc/kubernetes/kubelet.conf
      docker exec "${control_plane_container}" systemctl restart kubelet
      echo "⏳ Waiting for node to become Ready after kubelet restart…"
      kubectl --context "${context_name}" wait --for=condition=Ready \
        "node/${control_plane_container}" --timeout=120s
    fi
  fi
else
  echo "📦 Creating Kind cluster '${cluster_name}'..."
  kind create cluster --name "${cluster_name}" --config "${repo_root}/platform/kind/cluster-config.yaml"
  echo "✅ Kind cluster created"
fi

kubectl config use-context "${context_name}" >/dev/null

bash "${repo_root}/platform/scripts/setup-local-registry.sh" "${cluster_name}"

if ! kubectl --context "${context_name}" -n kube-system get daemonset calico-node >/dev/null 2>&1; then
  echo "🔒 Installing Calico CNI for Kind multi-node..."
  # Calico is the recommended CNI for Kind multi-node (works reliably in containerized nodes).
  # Cilium with kubeProxyReplacement=true requires eBPF support that Docker containers lack.
  calico_version="v3.29.3"
  calico_manifest="https://raw.githubusercontent.com/projectcalico/calico/${calico_version}/manifests/calico.yaml"

  kubectl apply -f "${calico_manifest}"
else
  echo "✅ Calico already installed"
fi

echo "⏳ Waiting for Calico and node readiness..."
kubectl --context "${context_name}" -n kube-system rollout status daemonset/calico-node --timeout=300s

# Wait for all nodes to be Ready (control-plane + workers on multi-node clusters)
kubectl --context "${context_name}" wait --for=condition=Ready nodes --all --timeout=300s

kubectl --context "${context_name}" apply -f "${repo_root}/platform/k8s/namespace.yaml"
kubectl --context "${context_name}" apply -f "${repo_root}/platform/k8s/infra/base.yaml"
kubectl --context "${context_name}" apply -k "${repo_root}/platform/k8s/observability"

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

echo "⏳ Waiting for observability deployments..."
wait_for_deployment shop garage &
wait_for_deployment shop loki &
wait_for_deployment shop tempo &
wait_for_deployment shop pyroscope &
wait_for_deployment shop grafana &
wait

echo "✅ Kind infrastructure is ready"
