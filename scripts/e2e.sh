#!/usr/bin/env bash
set -euo pipefail

cluster_name="${1:-shop-kind}"
overlay="${2:-dev}"
jobs="${E2E_BUILD_JOBS:-4}"
flow="${E2E_FLOW:-fast}"
script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
repo_root="$(cd "${script_dir}/.." && pwd)"
context_name="kind-${cluster_name}"

wait_for_app_deployment() {
  local deployment_name="$1"
  kubectl --context "${context_name}" -n shop rollout status "deployment/${deployment_name}" --timeout=300s
}

echo "==> Bootstrapping Kind cluster and infra"
bash "${repo_root}/scripts/kind-up.sh" "${cluster_name}"

if [[ "${flow}" == "legacy" ]]; then
  echo "==> Running legacy image build/load/deploy flow"
  bash "${repo_root}/scripts/build-images.sh" --changed --legacy -j "${jobs}"
  bash "${repo_root}/scripts/load-images-kind.sh" "${cluster_name}" --changed --kind-load
  bash "${repo_root}/scripts/deploy-kind.sh" "${overlay}" --all --legacy
else
  echo "==> Running fast image build/load/deploy flow"
  bash "${repo_root}/scripts/build-images.sh" --changed --fast -j "${jobs}"
  bash "${repo_root}/scripts/load-images-kind.sh" "${cluster_name}" --changed --registry
  bash "${repo_root}/scripts/deploy-kind.sh" "${overlay}" --changed
fi

echo "==> Waiting for application deployments"
deployments=(
  auth-server
  api-gateway
  buyer-bff
  seller-bff
  buyer-portal
  profile-service
  promotion-service
  wallet-service
  marketplace-service
  order-service
  search-service
  notification-service
  loyalty-service
  activity-service
  webhook-service
  subscription-service
)

pids=()
for deployment_name in "${deployments[@]}"; do
  wait_for_app_deployment "${deployment_name}" &
  pids+=($!)
done

# Wait for all background rollout status checks to complete
for pid in "${pids[@]}"; do
  wait "${pid}"
done

echo "==> Running smoke tests"
bash "${repo_root}/scripts/smoke-test.sh"

echo "==> Running UI page automation tests"
bash "${repo_root}/scripts/ui-e2e.sh"

echo ""
echo "✅ Local e2e flow completed successfully."
echo "   For stable local browser/curl access, run in another terminal:"
echo "     make local-access"
echo "   Then use:"
echo "     Gateway:    http://127.0.0.1:18080"
echo "     Buyer SSR:  http://127.0.0.1:18080/buyer/login"
echo "     Mailpit:    http://127.0.0.1:18025"
echo "     Prometheus: http://127.0.0.1:19090"
