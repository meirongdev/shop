#!/usr/bin/env bash
set -euo pipefail

GATEWAY_URL="${GATEWAY_URL:-http://localhost:8080}"
TIMEOUT="${SMOKE_TIMEOUT_SECONDS:-5}"
RETRIES="${SMOKE_RETRIES:-3}"
RETRY_DELAY="${SMOKE_RETRY_DELAY_SECONDS:-5}"
SMOKE_AUTO_PORT_FORWARD="${SMOKE_AUTO_PORT_FORWARD:-true}"
PORT_FORWARD_NAMESPACE="${PORT_FORWARD_NAMESPACE:-shop}"
PORT_FORWARD_GATEWAY_LOCAL_PORT="${PORT_FORWARD_GATEWAY_LOCAL_PORT:-18080}"
PORT_FORWARD_CONTEXT="${PORT_FORWARD_CONTEXT:-$(kubectl config current-context 2>/dev/null || true)}"

failures=0
port_forward_pid=""
port_forward_log=""

request_status() {
  local method="$1"
  local url="$2"
  local body="${3:-}"
  local content_type="${4:-}"
  local status
  local -a args=(
    -s
    -o /dev/null
    -w '%{http_code}'
    --max-time "${TIMEOUT}"
    -X "${method}"
  )

  if [[ -n "${content_type}" ]]; then
    args+=(-H "Content-Type: ${content_type}")
  fi

  if [[ -n "${body}" ]]; then
    args+=(--data "${body}")
  fi

  status="$(curl "${args[@]}" "${url}" 2>/dev/null || true)"
  printf '%s' "${status:-000}"
}

cleanup() {
  if [[ -n "${port_forward_pid}" ]]; then
    kill "${port_forward_pid}" >/dev/null 2>&1 || true
    wait "${port_forward_pid}" >/dev/null 2>&1 || true
  fi
  if [[ -n "${port_forward_log}" ]]; then
    rm -f "${port_forward_log}"
  fi
}

start_gateway_port_forward() {
  if ! command -v kubectl >/dev/null 2>&1; then
    return 1
  fi
  if [[ -z "${PORT_FORWARD_CONTEXT}" ]]; then
    return 1
  fi

  port_forward_log="$(mktemp "${TMPDIR:-/tmp}/shop-gateway-port-forward.XXXXXX")"
  kubectl --context "${PORT_FORWARD_CONTEXT}" -n "${PORT_FORWARD_NAMESPACE}" \
    port-forward svc/api-gateway "${PORT_FORWARD_GATEWAY_LOCAL_PORT}:8080" >"${port_forward_log}" 2>&1 &
  port_forward_pid=$!

  for _ in $(seq 1 20); do
    if ! kill -0 "${port_forward_pid}" >/dev/null 2>&1; then
      cat "${port_forward_log}" >&2
      return 1
    fi

    if [[ "$(request_status "GET" "http://127.0.0.1:${PORT_FORWARD_GATEWAY_LOCAL_PORT}/v3/api-docs/gateway")" != "000" ]]; then
      return 0
    fi

    sleep 1
  done

  cat "${port_forward_log}" >&2
  return 1
}

trap cleanup EXIT

check() {
  local name="$1"
  local method="$2"
  local path="$3"
  local expected_status="${4:-200}"
  local body="${5:-}"
  local content_type="${6:-}"
  local url="${GATEWAY_URL%/}${path}"
  local attempt=1
  local status

  while (( attempt <= RETRIES )); do
    status="$(request_status "${method}" "${url}" "${body}" "${content_type}")"
    if [[ "${status}" == "${expected_status}" ]]; then
      echo "[PASS] ${name} (${status})"
      return 0
    fi

    if (( attempt < RETRIES )); then
      echo "[WAIT] ${name} expected ${expected_status}, got ${status}; retrying in ${RETRY_DELAY}s (${attempt}/${RETRIES})"
      sleep "${RETRY_DELAY}"
    fi

    ((attempt += 1))
  done

  echo "[FAIL] ${name} expected ${expected_status}, got ${status}"
  ((failures += 1))
}

if [[ "${SMOKE_AUTO_PORT_FORWARD}" == "true" && \
      "$(request_status "GET" "${GATEWAY_URL%/}/v3/api-docs/gateway")" == "000" ]]; then
  if start_gateway_port_forward; then
    GATEWAY_URL="http://127.0.0.1:${PORT_FORWARD_GATEWAY_LOCAL_PORT}"
    echo "[INFO] Local host mapping unavailable; using temporary gateway port-forward at ${GATEWAY_URL}"
  fi
fi

echo "Smoke testing ${GATEWAY_URL}"
echo ""
check "Gateway OpenAPI endpoint" "GET" "/v3/api-docs/gateway" "200"
check "Auth login validation" "POST" "/auth/v1/token/login" "400" "{}" "application/json"
check "Buyer public marketplace" "POST" "/public/buyer/v1/marketplace/list" "200"
check "Seller API requires authentication" "POST" "/api/seller/v1/dashboard/get" "401" "{}" "application/json"
check "Seller portal SPA shell" "GET" "/seller/" "200"
check "Buyer KMP app SPA shell" "GET" "/buyer-app/" "200"
echo ""

if (( failures > 0 )); then
  echo "Smoke tests failed: ${failures} check(s) did not pass."
  exit 1
fi

echo "All smoke tests passed."
