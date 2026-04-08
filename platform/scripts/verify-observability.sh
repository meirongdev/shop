#!/usr/bin/env bash
# verify-observability.sh — automated validation of the Grafana observability stack.
#
# Checks:
#   1. Grafana API health
#   2. Prometheus datasource reachability
#   3. Loki datasource reachability
#   4. Tempo datasource reachability
#   5. Dashboard existence (service-health, business-overview)
#   6. Prometheus has scraped shop-apps metrics
#   7. Loki ready endpoint
#   8. Tempo ready endpoint
#
# Usage:
#   scripts/verify-observability.sh                      # auto port-forward
#   GRAFANA_URL=http://127.0.0.1:13000 scripts/verify-observability.sh
set -euo pipefail

GRAFANA_URL="${GRAFANA_URL:-}"
GRAFANA_LOCAL_PORT="${LOCAL_GRAFANA_PORT:-13000}"
PROMETHEUS_URL="${PROMETHEUS_URL:-}"
PROMETHEUS_LOCAL_PORT="${LOCAL_PROMETHEUS_PORT:-19090}"
LOKI_URL="${LOKI_URL:-}"
TEMPO_URL="${TEMPO_URL:-}"
TIMEOUT="${OBS_TIMEOUT_SECONDS:-10}"
RETRIES="${OBS_RETRIES:-3}"
RETRY_DELAY="${OBS_RETRY_DELAY_SECONDS:-5}"

PORT_FORWARD_NAMESPACE="${PORT_FORWARD_NAMESPACE:-shop}"
PORT_FORWARD_CONTEXT="${PORT_FORWARD_CONTEXT:-$(kubectl config current-context 2>/dev/null || true)}"

failures=0
pids=()
logs=()

cleanup() {
  local pid
  for pid in "${pids[@]:-}"; do
    kill "${pid}" >/dev/null 2>&1 || true
    wait "${pid}" >/dev/null 2>&1 || true
  done
  rm -f "${logs[@]:-}"
}
trap cleanup EXIT INT TERM

http_get() {
  local url="$1"
  local -a extra_args=("${@:2}")
  curl -s -o /dev/null -w '%{http_code}' --max-time "${TIMEOUT}" "${extra_args[@]}" "${url}" 2>/dev/null || true
}

http_get_body() {
  local url="$1"
  curl -s --max-time "${TIMEOUT}" "${url}" 2>/dev/null || true
}

start_port_forward() {
  local svc="$1"
  local local_port="$2"
  local remote_port="$3"
  local health_path="${4:-/}"
  local log_file
  local pid

  [[ -n "${PORT_FORWARD_CONTEXT}" ]] || return 1
  log_file="$(mktemp "${TMPDIR:-/tmp}/shop-obs-pf.${svc}.XXXXXX")"
  kubectl --context "${PORT_FORWARD_CONTEXT}" -n "${PORT_FORWARD_NAMESPACE}" \
    port-forward "svc/${svc}" "${local_port}:${remote_port}" >"${log_file}" 2>&1 &
  pid=$!

  for _ in $(seq 1 30); do
    if ! kill -0 "${pid}" >/dev/null 2>&1; then
      cat "${log_file}" >&2
      return 1
    fi
    if [[ "$(http_get "http://127.0.0.1:${local_port}${health_path}")" != "000" ]]; then
      pids+=("${pid}")
      logs+=("${log_file}")
      return 0
    fi
    sleep 1
  done

  cat "${log_file}" >&2
  return 1
}

pass() { echo "[PASS] $1"; }
fail() {
  echo "[FAIL] $1${2:+: $2}"
  failures=$((failures + 1))
}

check_http() {
  local name="$1"
  local url="$2"
  local expected="${3:-200}"
  local attempt=1
  local status

  while (( attempt <= RETRIES )); do
    status="$(http_get "${url}")"
    if [[ "${status}" == "${expected}" ]]; then
      pass "${name} (${status})"
      return 0
    fi
    (( attempt < RETRIES )) && sleep "${RETRY_DELAY}"
    ((attempt += 1))
  done
  fail "${name}" "expected ${expected}, got ${status:-000}"
}

check_json_contains() {
  local name="$1"
  local url="$2"
  local pattern="$3"
  local body

  body="$(http_get_body "${url}")"
  if echo "${body}" | grep -q "${pattern}"; then
    pass "${name}"
  else
    fail "${name}" "pattern '${pattern}' not found in response"
  fi
}

# ── Auto port-forward if URLs not provided ──────────────────────────────────
if [[ -z "${GRAFANA_URL}" ]]; then
  echo "[INFO] Port-forwarding grafana → 127.0.0.1:${GRAFANA_LOCAL_PORT}"
  start_port_forward grafana "${GRAFANA_LOCAL_PORT}" 3000 /api/health
  GRAFANA_URL="http://127.0.0.1:${GRAFANA_LOCAL_PORT}"
fi

if [[ -z "${PROMETHEUS_URL}" ]]; then
  echo "[INFO] Port-forwarding prometheus → 127.0.0.1:${PROMETHEUS_LOCAL_PORT}"
  start_port_forward prometheus "${PROMETHEUS_LOCAL_PORT}" 9090 /-/ready
  PROMETHEUS_URL="http://127.0.0.1:${PROMETHEUS_LOCAL_PORT}"
fi

if [[ -z "${LOKI_URL}" ]]; then
  loki_port="${LOCAL_LOKI_PORT:-13100}"
  echo "[INFO] Port-forwarding loki → 127.0.0.1:${loki_port}"
  start_port_forward loki "${loki_port}" 3100 /ready
  LOKI_URL="http://127.0.0.1:${loki_port}"
fi

if [[ -z "${TEMPO_URL}" ]]; then
  tempo_port="${LOCAL_TEMPO_PORT:-13200}"
  echo "[INFO] Port-forwarding tempo → 127.0.0.1:${tempo_port}"
  start_port_forward tempo "${tempo_port}" 3200 /ready
  TEMPO_URL="http://127.0.0.1:${tempo_port}"
fi

echo ""
echo "Verifying observability stack"
echo "  Grafana:    ${GRAFANA_URL}"
echo "  Prometheus: ${PROMETHEUS_URL}"
echo "  Loki:       ${LOKI_URL}"
echo "  Tempo:      ${TEMPO_URL}"
echo ""

# ── 1. Grafana health ────────────────────────────────────────────────────────
check_http "Grafana API health" "${GRAFANA_URL}/api/health" "200"

# ── 2. Grafana datasource checks ────────────────────────────────────────────
check_json_contains "Grafana has Prometheus datasource" \
  "${GRAFANA_URL}/api/datasources" '"Prometheus"'

check_json_contains "Grafana has Loki datasource" \
  "${GRAFANA_URL}/api/datasources" '"Loki"'

check_json_contains "Grafana has Tempo datasource" \
  "${GRAFANA_URL}/api/datasources" '"Tempo"'

check_json_contains "Grafana has Pyroscope datasource" \
  "${GRAFANA_URL}/api/datasources" '"Pyroscope"'

# ── 3. Grafana datasource proxy health checks ────────────────────────────────
check_http "Grafana→Prometheus proxy" \
  "${GRAFANA_URL}/api/datasources/proxy/uid/prometheus/api/v1/query?query=up" "200"

check_http "Grafana→Loki proxy ready" \
  "${GRAFANA_URL}/api/datasources/proxy/uid/loki/ready" "200"

check_http "Grafana→Tempo proxy ready" \
  "${GRAFANA_URL}/api/datasources/proxy/uid/tempo/ready" "200"

# ── 4. Dashboard existence ───────────────────────────────────────────────────
check_json_contains "Dashboard: Service Health exists" \
  "${GRAFANA_URL}/api/search?query=shop" '"shop-service-health"'

check_json_contains "Dashboard: Business Overview exists" \
  "${GRAFANA_URL}/api/search?query=shop" '"shop-business"'

# ── 5. Prometheus direct checks ──────────────────────────────────────────────
check_http "Prometheus ready" "${PROMETHEUS_URL}/-/ready" "200"

check_json_contains "Prometheus scrapes shop-apps job" \
  "${PROMETHEUS_URL}/api/v1/targets?state=active" '"shop-apps"'

check_json_contains "Prometheus scrapes observability job" \
  "${PROMETHEUS_URL}/api/v1/targets?state=active" '"observability"'

# Check at least one shop app is UP in prometheus
prometheus_up="$(http_get_body "${PROMETHEUS_URL}/api/v1/query?query=up%7Bjob%3D%22shop-apps%22%7D")"
if echo "${prometheus_up}" | grep -q '"value":\["'; then
  pass "Prometheus has shop-apps metrics"
else
  fail "Prometheus has shop-apps metrics" "no data from up{job=\"shop-apps\"}"
fi

# ── 6. Loki direct check ─────────────────────────────────────────────────────
check_http "Loki ready" "${LOKI_URL}/ready" "200"

# ── 7. Tempo direct check ────────────────────────────────────────────────────
check_http "Tempo ready" "${TEMPO_URL}/ready" "200"

# ── 8. Prometheus Alert Rules metadata check ─────────────────────────────────
check_json_contains "Prometheus alerts have dashboard_url" \
  "${PROMETHEUS_URL}/api/v1/rules" '"dashboard_url"'

check_json_contains "Prometheus alerts have runbook_url" \
  "${PROMETHEUS_URL}/api/v1/rules" '"runbook_url"'

# ── Summary ──────────────────────────────────────────────────────────────────
echo ""
if (( failures > 0 )); then
  echo "Observability verification FAILED: ${failures} check(s) did not pass."
  exit 1
fi
echo "All observability checks passed."
