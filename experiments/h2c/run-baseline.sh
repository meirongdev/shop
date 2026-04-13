#!/usr/bin/env bash
# experiments/h2c/run-baseline.sh
# Activates load-test Spring profile on buyer-bff, forces buyer-bff -> marketplace
# traffic to HTTP/1.1, enables h2c support on marketplace-service for apples-to-
# apples comparison, then restores both Deployments to their default config.
set -euo pipefail

RESULTS_FILE="${RESULTS_FILE:-experiments/h2c/baseline-results.json}"
LOAD_TEST_SCRIPT="${LOAD_TEST_SCRIPT:-experiments/h2c/load-test.js}"
HEALTHCHECK_PATH="${HEALTHCHECK_PATH:-/buyer/v1/marketplace/list}"
HEALTHCHECK_BODY="${HEALTHCHECK_BODY:-{}}"
PF_PORT="${PF_PORT:-38080}"
NAMESPACE="${NAMESPACE:-shop}"
BASE_URL="http://localhost:${PF_PORT}"
TARGET_VUS="${TARGET_VUS:-10}"
RAMP_SECONDS="${RAMP_SECONDS:-15}"
STEADY_SECONDS="${STEADY_SECONDS:-60}"
SLEEP_SECONDS="${SLEEP_SECONDS:-0.1}"
FANOUT="${FANOUT:-8}"
HEADER_BYTES="${HEADER_BYTES:-0}"
MARKETPLACE_SERVICE_URL_OVERRIDE="${MARKETPLACE_SERVICE_URL_OVERRIDE:-}"
BUYER_JDK_JAVA_OPTIONS="${BUYER_JDK_JAVA_OPTIONS:-}"

# -- 1. Activate load-test profile and baseline transport
echo "=== [1/5] Preparing baseline config (load-test + HTTP/1.1 client) ==="
kubectl -n "${NAMESPACE}" set env deployment/buyer-bff \
  SPRING_PROFILES_ACTIVE=load-test \
  BUYER_BFF_HTTP_VERSION=HTTP_1_1
if [ -n "${MARKETPLACE_SERVICE_URL_OVERRIDE}" ]; then
  kubectl -n "${NAMESPACE}" set env deployment/buyer-bff \
    MARKETPLACE_SERVICE_URL="${MARKETPLACE_SERVICE_URL_OVERRIDE}"
fi
if [ -n "${BUYER_JDK_JAVA_OPTIONS}" ]; then
  kubectl -n "${NAMESPACE}" set env deployment/buyer-bff \
    JDK_JAVA_OPTIONS="${BUYER_JDK_JAVA_OPTIONS}"
fi
kubectl -n "${NAMESPACE}" set env deployment/marketplace-service SERVER_HTTP2_ENABLED=true
kubectl -n "${NAMESPACE}" rollout status deployment/buyer-bff deployment/marketplace-service --timeout=180s
echo "buyer-bff and marketplace-service restarted with baseline settings"

# -- 2. Re-establish port-forward
echo ""
echo "=== [2/5] Starting port-forward ==="
PF_PID=""
stop_portforward() {
  [ -n "${PF_PID}" ] && kill "${PF_PID}" 2>/dev/null || true
}
kubectl -n "${NAMESPACE}" port-forward deployment/buyer-bff "${PF_PORT}:8080" &
PF_PID=$!
sleep 5
echo "port-forward started (PID=${PF_PID})"

cleanup() {
  stop_portforward
  echo ""
  echo "=== [5/5] Restoring production config ==="
  unset_args=(SPRING_PROFILES_ACTIVE- BUYER_BFF_HTTP_VERSION-)
  if [ -n "${MARKETPLACE_SERVICE_URL_OVERRIDE}" ]; then
    unset_args+=(MARKETPLACE_SERVICE_URL-)
  fi
  if [ -n "${BUYER_JDK_JAVA_OPTIONS}" ]; then
    unset_args+=(JDK_JAVA_OPTIONS-)
  fi
  kubectl -n "${NAMESPACE}" set env deployment/buyer-bff "${unset_args[@]}"
  kubectl -n "${NAMESPACE}" set env deployment/marketplace-service SERVER_HTTP2_ENABLED-
  kubectl -n "${NAMESPACE}" rollout status deployment/buyer-bff deployment/marketplace-service --timeout=180s \
    2>/dev/null || true
  echo "buyer-bff and marketplace-service production config restored"
}
trap cleanup EXIT

# -- 3. Health check
echo ""
echo "=== [3/5] Verifying service availability ==="
HTTP_STATUS=""
for _ in $(seq 1 10); do
  HTTP_STATUS=$(curl -s -o /dev/null -w "%{http_code}" -X POST \
    -H 'Content-Type: application/json' \
    -d "${HEALTHCHECK_BODY}" \
    "${BASE_URL}${HEALTHCHECK_PATH}" || true)
  if [ "${HTTP_STATUS}" = "200" ]; then
    break
  fi
  sleep 2
done
if [ "${HTTP_STATUS}" != "200" ]; then
  echo "${HEALTHCHECK_PATH} returned ${HTTP_STATUS} - check cluster state"
  exit 1
fi
echo "Service is available"

# -- 4. Run k6 baseline
echo ""
echo "=== [4/5] Running HTTP/1.1 baseline (~90s) ==="
k6 run \
  --env BASE_URL="${BASE_URL}" \
  --env TARGET_VUS="${TARGET_VUS}" \
  --env RAMP_SECONDS="${RAMP_SECONDS}" \
  --env STEADY_SECONDS="${STEADY_SECONDS}" \
  --env SLEEP_SECONDS="${SLEEP_SECONDS}" \
  --env FANOUT="${FANOUT}" \
  --env HEADER_BYTES="${HEADER_BYTES}" \
  --summary-export="${RESULTS_FILE}" \
  "${LOAD_TEST_SCRIPT}"

echo ""
echo "Baseline complete. Results written to ${RESULTS_FILE}"
echo "Capture Grafana snapshots to experiments/h2c/grafana-baseline/:"
echo "  - http_server_requests_seconds p95 (buyer-bff)"
echo "  - JVM threads live (buyer-bff)"
echo "  - Tempo: buyer-bff -> marketplace-service span (http.flavor should be 1.1)"
