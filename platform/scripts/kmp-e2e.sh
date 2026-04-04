#!/usr/bin/env bash
# -----------------------------------------------------------------------------
# kmp-e2e.sh — Automated KMP WASM page verification for buyer-app and seller-app
#
# Builds WASM bundles, starts local proxies, runs Playwright tests, and cleans up.
# Requires: gateway accessible (via make local-access or Kind cluster), Node.js, Gradle.
#
# Usage:
#   bash scripts/kmp-e2e.sh              # run both buyer-app and seller-app tests
#   bash scripts/kmp-e2e.sh --seller     # seller-app tests only
#   bash scripts/kmp-e2e.sh --buyer-app  # buyer-app tests only
# -----------------------------------------------------------------------------
set -euo pipefail

GATEWAY_URL="${GATEWAY_URL:-http://127.0.0.1:18080}"
SELLER_WEB_PORT="${SELLER_WEB_PORT:-18181}"
BUYER_APP_WEB_PORT="${BUYER_APP_WEB_PORT:-18182}"
UI_E2E_TIMEOUT_SECONDS="${UI_E2E_TIMEOUT_SECONDS:-10}"

SELLER_WASM_BUILD_TASK="${SELLER_WASM_BUILD_TASK:-:kmp:seller-app:wasmJsBrowserDevelopmentExecutableDistribution}"
SELLER_WASM_DIST_DIR="${SELLER_WASM_DIST_DIR:-frontend/kmp/seller-app/build/dist/wasmJs/developmentExecutable}"

BUYER_APP_WASM_BUILD_TASK="${BUYER_APP_WASM_BUILD_TASK:-:kmp:buyer-app:wasmJsBrowserDevelopmentExecutableDistribution}"
BUYER_APP_WASM_DIST_DIR="${BUYER_APP_WASM_DIST_DIR:-frontend/kmp/buyer-app/build/dist/wasmJs/developmentExecutable}"

script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
repo_root="$(cd "${script_dir}/.." && pwd)"

run_seller=true
run_buyer_app=true
for arg in "$@"; do
  case "${arg}" in
    --seller)     run_buyer_app=false ;;
    --buyer-app)  run_seller=false ;;
  esac
done

seller_proxy_pid=""
buyer_app_proxy_pid=""
failures=0

cleanup() {
  if [[ -n "${seller_proxy_pid}" ]]; then
    kill "${seller_proxy_pid}" >/dev/null 2>&1 || true
    wait "${seller_proxy_pid}" >/dev/null 2>&1 || true
  fi
  if [[ -n "${buyer_app_proxy_pid}" ]]; then
    kill "${buyer_app_proxy_pid}" >/dev/null 2>&1 || true
    wait "${buyer_app_proxy_pid}" >/dev/null 2>&1 || true
  fi
}
trap cleanup EXIT

wait_for_proxy() {
  local port="$1"
  local label="$2"
  for _ in $(seq 1 30); do
    if curl -sS -o /dev/null -w '' --max-time 2 "http://127.0.0.1:${port}/" >/dev/null 2>&1; then
      echo "  ${label} proxy ready on port ${port}"
      return 0
    fi
    sleep 1
  done
  echo "error: ${label} proxy failed to start on port ${port}" >&2
  return 1
}

# ---------------------------------------------------------------------------
# Verify gateway is reachable
# ---------------------------------------------------------------------------
echo "==> Checking gateway at ${GATEWAY_URL}"
gateway_status="$(curl -sS -o /dev/null -w '%{http_code}' --max-time "${UI_E2E_TIMEOUT_SECONDS}" "${GATEWAY_URL}/v3/api-docs/gateway" 2>/dev/null || true)"
if [[ "${gateway_status}" != "200" ]]; then
  echo "error: gateway not reachable at ${GATEWAY_URL} (HTTP ${gateway_status})" >&2
  echo "  Start with: make local-access" >&2
  exit 1
fi
echo "  Gateway OK"
echo ""

# ---------------------------------------------------------------------------
# Build WASM bundles
# ---------------------------------------------------------------------------
build_tasks=()
if [[ "${run_seller}" == "true" ]]; then
  build_tasks+=("${SELLER_WASM_BUILD_TASK}")
fi
if [[ "${run_buyer_app}" == "true" ]]; then
  build_tasks+=("${BUYER_APP_WASM_BUILD_TASK}")
fi

if (( ${#build_tasks[@]} > 0 )); then
  echo "==> Building KMP WASM bundles"
  ./gradlew -q "${build_tasks[@]}"
  echo "  Build complete"
  echo ""
fi

# ---------------------------------------------------------------------------
# Start proxies
# ---------------------------------------------------------------------------
if [[ "${run_seller}" == "true" ]]; then
  if [[ ! -f "${SELLER_WASM_DIST_DIR}/index.html" ]]; then
    echo "error: seller WASM dist not found at ${SELLER_WASM_DIST_DIR}" >&2
    exit 1
  fi
  echo "==> Starting seller proxy on port ${SELLER_WEB_PORT}"
  node "${repo_root}/scripts/seller-web-proxy.mjs" "${SELLER_WASM_DIST_DIR}" "${GATEWAY_URL}" "${SELLER_WEB_PORT}" &
  seller_proxy_pid=$!
  wait_for_proxy "${SELLER_WEB_PORT}" "Seller"
fi

if [[ "${run_buyer_app}" == "true" ]]; then
  if [[ ! -f "${BUYER_APP_WASM_DIST_DIR}/index.html" ]]; then
    echo "error: buyer-app WASM dist not found at ${BUYER_APP_WASM_DIST_DIR}" >&2
    exit 1
  fi
  echo "==> Starting buyer-app proxy on port ${BUYER_APP_WEB_PORT}"
  node "${repo_root}/scripts/seller-web-proxy.mjs" "${BUYER_APP_WASM_DIST_DIR}" "${GATEWAY_URL}" "${BUYER_APP_WEB_PORT}" &
  buyer_app_proxy_pid=$!
  wait_for_proxy "${BUYER_APP_WEB_PORT}" "Buyer-app"
fi
echo ""

# ---------------------------------------------------------------------------
# Run Playwright tests
# ---------------------------------------------------------------------------
projects=()
if [[ "${run_seller}" == "true" ]]; then
  projects+=(--project=seller)
fi
if [[ "${run_buyer_app}" == "true" ]]; then
  projects+=(--project=buyer-app)
fi

echo "==> Running Playwright KMP tests"
cd "${repo_root}/e2e-tests"

GATEWAY_URL="${GATEWAY_URL}" \
  SELLER_PROXY_URL="http://127.0.0.1:${SELLER_WEB_PORT}" \
  BUYER_APP_PROXY_URL="http://127.0.0.1:${BUYER_APP_WEB_PORT}" \
  npx playwright test "${projects[@]}" || failures=$?

echo ""
if (( failures > 0 )); then
  echo "KMP e2e tests failed. Run 'cd e2e-tests && npx playwright show-report' for details."
  exit 1
fi

echo "✅ All KMP e2e tests passed."
