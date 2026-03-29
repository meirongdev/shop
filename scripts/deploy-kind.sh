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

if [[ "${deploy_mode}" == "fast" ]]; then
  for deployment_name in "${deployments[@]}"; do
    kubectl --context "${context_name}" -n shop rollout restart "deployment/${deployment_name}"
  done
fi

for deployment_name in "${deployments[@]}"; do
  kubectl --context "${context_name}" -n shop rollout status "deployment/${deployment_name}" --timeout=300s
done
