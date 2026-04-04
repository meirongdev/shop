#!/usr/bin/env bash
set -euo pipefail

overlay="${1:-dev}"
shift || true
script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
repo_root="$(cd "${script_dir}/.." && pwd)"
source "${script_dir}/local-cicd-modules.sh"

mode="all"
deploy_mode="${SHOP_LOCAL_DEPLOY_MODE:-fast}"
base_ref="$(default_base_ref)"
cluster_name="${SHOP_LOCAL_CLUSTER:-shop-kind}"
context_name="kind-${cluster_name}"

while [[ $# -gt 0 ]]; do
  case "$1" in
    --all) mode="all"; shift ;;
    --changed) mode="changed"; shift ;;
    --base) base_ref="$2"; shift 2 ;;
    --legacy) deploy_mode="legacy"; shift ;;
    *) echo "error: unknown argument $1" >&2; exit 1 ;;
  esac
done

echo "==> Deploying overlay '${overlay}'"
if [[ "${deploy_mode}" == "legacy" ]]; then
  kubectl --context "${context_name}" apply -f "${repo_root}/k8s/apps/platform.yaml"
else
  kubectl --context "${context_name}" apply -k "${repo_root}/k8s/apps/overlays/${overlay}"
fi

if [[ "${mode}" == "changed" ]]; then
  deployments=()
  while IFS= read -r deployment_name; do
    deployments+=("${deployment_name}")
  done < <(detect_changed_modules "${base_ref}")
else
  deployments=("${ALL_MODULES[@]}")
fi

if [[ "${#deployments[@]}" -eq 0 || "${deploy_mode}" == "legacy" ]]; then
  deployments=("${ALL_MODULES[@]}")
fi

# Wave-based restart reduces peak CPU contention on single-node Kind clusters.
# Wave 1: domain/auth services (DB-dependent; init containers ensure MySQL is ready)
# Wave 2: BFFs, search, notification, portal (no direct DB; can overlap with wave 1 tail)
# Wave 3: api-gateway (depends on auth-server being healthy for first-request JWT validation)
WAVE1=(auth-server profile-service promotion-service wallet-service marketplace-service
       order-service loyalty-service activity-service webhook-service subscription-service)
WAVE2=(buyer-bff seller-bff search-service notification-service buyer-portal seller-portal)
WAVE3=(api-gateway)

rollout_wait() {
  local timeout="${1}"; shift
  local deps=("$@")
  local pids=() failed=()
  for d in "${deps[@]}"; do
    kubectl --context "${context_name}" -n shop rollout status \
      "deployment/${d}" --timeout="${timeout}" &
    pids+=($!)
  done
  for i in "${!pids[@]}"; do
    if ! wait "${pids[$i]}"; then
      failed+=("${deps[$i]}")
    fi
  done
  if [[ "${#failed[@]}" -gt 0 ]]; then
    echo "error: rollout timed out for: ${failed[*]}" >&2
    return 1
  fi
}

restart_deployments() {
  local deps=("$@")
  for d in "${deps[@]}"; do
    if array_contains "${d}" "${deployments[@]}"; then
      kubectl --context "${context_name}" -n shop rollout restart "deployment/${d}"
    fi
  done
}

wait_wave() {
  local timeout="${1}"; shift
  local deps=("$@")
  local filtered=()
  for d in "${deps[@]}"; do
    if array_contains "${d}" "${deployments[@]}"; then
      filtered+=("${d}")
    fi
  done
  if [[ "${#filtered[@]}" -gt 0 ]]; then
    echo "  ↳ waiting for: ${filtered[*]}"
    rollout_wait "${timeout}" "${filtered[@]}"
  fi
}

if [[ "${deploy_mode}" == "fast" ]]; then
  # Stagger restarts: let wave-1 JVMs claim CPU before subsequent waves start.
  restart_deployments "${WAVE1[@]}"
  if [[ "${#deployments[@]}" -gt 5 ]]; then
    sleep 20  # Only stagger on full restarts (>5 services at once)
  fi
  restart_deployments "${WAVE2[@]}"
  restart_deployments "${WAVE3[@]}"
fi

echo "Waiting for ${#deployments[@]} deployment(s) to roll out (3-wave schedule)…"
wait_wave 240s "${WAVE1[@]}"
wait_wave 180s "${WAVE2[@]}"
wait_wave 120s "${WAVE3[@]}"
