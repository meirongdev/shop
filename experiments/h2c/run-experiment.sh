#!/usr/bin/env bash
# experiments/h2c/run-experiment.sh
# Activates load-test Spring profile (no CB/bulkhead/timelimiter) on buyer-bff,
# runs the h2c k6 experiment, then restores buyer-bff to its production config.
set -euo pipefail

RESULTS_FILE="experiments/h2c/h2c-results.json"
PF_PORT="${PF_PORT:-38080}"
NAMESPACE="${NAMESPACE:-shop}"
INTERNAL_TOKEN="${INTERNAL_TOKEN:-local-dev-internal-token-change-me}"
BASE_URL="http://localhost:${PF_PORT}"

# -- 1. Activate load-test profile
echo "=== [1/5] Activating load-test profile (disabling CB/bulkhead/timelimiter) ==="
kubectl -n "${NAMESPACE}" set env deployment/buyer-bff SPRING_PROFILES_ACTIVE=load-test
kubectl -n "${NAMESPACE}" rollout status deployment/buyer-bff --timeout=120s
echo "buyer-bff restarted with load-test profile"

# -- 2. Re-establish port-forward
echo ""
echo "=== [2/5] Starting port-forward ==="
PF_PID=""
stop_portforward() {
  [ -n "${PF_PID}" ] && kill "${PF_PID}" 2>/dev/null || true
}
POD=$(kubectl -n "${NAMESPACE}" get pod -l app=buyer-bff --field-selector=status.phase=Running -o jsonpath='{.items[0].metadata.name}')
kubectl -n "${NAMESPACE}" port-forward "${POD}" "${PF_PORT}:8080" &
PF_PID=$!
sleep 3
echo "port-forward started (PID=${PF_PID})"

cleanup() {
  stop_portforward
  echo ""
  echo "=== [5/5] Restoring buyer-bff production config ==="
  kubectl -n "${NAMESPACE}" set env deployment/buyer-bff SPRING_PROFILES_ACTIVE-
  kubectl -n "${NAMESPACE}" rollout status deployment/buyer-bff --timeout=120s 2>/dev/null || true
  echo "buyer-bff production config restored"
}
trap cleanup EXIT

# -- 3. Health check
echo ""
echo "=== [3/5] Verifying service availability ==="
HTTP_STATUS=$(curl -s -o /dev/null -w "%{http_code}" -X POST \
  -H 'Content-Type: application/json' \
  -H "X-Internal-Token: ${INTERNAL_TOKEN}" \
  -d '{}' \
  "${BASE_URL}/buyer/v1/marketplace/list")
if [ "${HTTP_STATUS}" != "200" ]; then
  echo "marketplace/list returned ${HTTP_STATUS} - check cluster state"
  exit 1
fi

echo ""
echo "=== Verify h2c is active (via logs or Tempo) ==="
echo "Check marketplace-service logs for HTTP/2 connection confirmation:"
echo "  kubectl -n ${NAMESPACE} logs -l app=marketplace-service --tail=20"
echo "OR check Grafana Tempo: buyer-bff trace -> marketplace-service span, http.flavor should be 2.0"
echo "Press Enter to continue, Ctrl+C to abort..."
read -r

# -- 4. Run k6 experiment
echo ""
echo "=== [4/5] Running h2c experiment (~90s) ==="
k6 run \
  --env BASE_URL="${BASE_URL}" \
  --env INTERNAL_TOKEN="${INTERNAL_TOKEN}" \
  --summary-export="${RESULTS_FILE}" \
  experiments/h2c/load-test.js

echo ""
echo "Experiment complete. Results written to ${RESULTS_FILE}"
echo "Capture Grafana snapshots to experiments/h2c/grafana-h2c/:"
echo "  - http_server_requests_seconds p95 (buyer-bff)"
echo "  - resilience4j_timelimiter_calls_total (should be 0 in load-test profile)"
echo "  - Tempo: buyer-bff -> marketplace-service span (http.flavor should be 2.0)"
