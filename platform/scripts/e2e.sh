#!/usr/bin/env bash
set -euo pipefail

cluster_name="${1:-shop-kind}"
overlay="${2:-dev}"
jobs="${E2E_BUILD_JOBS:-4}"
# Set E2E_FULL_UI=1 to include the seller Gradle/WASM build (~10 min).
# Default: only buyer SSR checks (fast). Seller WASM is verified by make e2e-playwright-seller.
skip_seller_ui="${E2E_FULL_UI:-}"
script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
repo_root="$(cd "${script_dir}/../.." && pwd)"

# Generate content-addressable image tag ONCE for the entire e2e flow.
# Export so all child scripts (build, load, deploy) use the same tag.
export SHOP_LOCAL_IMAGE_TAG="${SHOP_LOCAL_IMAGE_TAG:-dev-$(git -C "${repo_root}" rev-parse --short HEAD)-$(date +%s)}"

_t0=$(date +%s)
phase_start() { _phase_t=$(date +%s); echo "==> $1"; }
phase_done()  { echo "   ✓ done in $(( $(date +%s) - _phase_t ))s"; }

echo "==> Image tag for this e2e run: ${SHOP_LOCAL_IMAGE_TAG}"

# Check and install dependencies before starting e2e
phase_start "Checking dependencies"
bash "${repo_root}/platform/scripts/install-deps.sh"
phase_done

phase_start "Bootstrapping Kind cluster and infra"
bash "${repo_root}/platform/scripts/kind-up.sh" "${cluster_name}"
phase_done

phase_start "Maven build (clean package, -T 1C)"
bash "${repo_root}/platform/scripts/build-images.sh" --changed -j "${jobs}"
phase_done

phase_start "Registry push (parallel, ${jobs} workers) + deploy"
bash "${repo_root}/platform/scripts/load-images-kind.sh" "${cluster_name}" --changed --registry
bash "${repo_root}/platform/scripts/deploy-kind.sh" "${overlay}" --changed
phase_done

phase_start "Smoke tests"
# deploy-kind.sh already waits for all rollouts (including readiness probes).
# smoke-test.sh has its own auto-port-forward + retry logic.
bash "${repo_root}/platform/scripts/smoke-test.sh"
phase_done

if [[ -n "${skip_seller_ui}" ]]; then
  phase_start "Full UI tests (buyer + seller WASM)"
  bash "${repo_root}/platform/scripts/ui-e2e.sh"
  phase_done
else
  phase_start "Buyer UI tests (seller WASM skipped; use E2E_FULL_UI=1 to include)"
  bash "${repo_root}/platform/scripts/ui-e2e.sh" --buyer-only
  phase_done
fi

echo ""
echo "✅ Local e2e flow completed in $(( $(date +%s) - _t0 ))s."
echo "   For stable local browser/curl access, run in another terminal:"
echo "     make local-access"
echo "   Then use:"
echo "     Gateway:       http://127.0.0.1:18080"
echo "     Buyer SSR:     http://127.0.0.1:18080/buyer/login"
echo "     Seller Portal: http://127.0.0.1:18080/seller/"
echo "     Mailpit:       http://127.0.0.1:18025"
echo "     Prometheus:    http://127.0.0.1:19090"
echo "     Grafana:       http://127.0.0.1:13000  (admin/admin)"
