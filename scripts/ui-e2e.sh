#!/usr/bin/env bash
set -euo pipefail

GATEWAY_URL="${GATEWAY_URL:-http://127.0.0.1:18080}"
UI_E2E_TIMEOUT_SECONDS="${UI_E2E_TIMEOUT_SECONDS:-10}"
UI_E2E_PORT_FORWARD_CONTEXT="${UI_E2E_PORT_FORWARD_CONTEXT:-$(kubectl config current-context 2>/dev/null || true)}"
UI_E2E_PORT_FORWARD_NAMESPACE="${UI_E2E_PORT_FORWARD_NAMESPACE:-shop}"
UI_E2E_GATEWAY_LOCAL_PORT="${UI_E2E_GATEWAY_LOCAL_PORT:-18080}"
SELLER_WASM_BUILD_TASK="${SELLER_WASM_BUILD_TASK:-:kmp:seller-app:wasmJsBrowserDevelopmentExecutableDistribution}"
SELLER_WASM_DIST_DIR="${SELLER_WASM_DIST_DIR:-kmp/seller-app/build/dist/wasmJs/developmentExecutable}"
SELLER_WEB_PORT="${SELLER_WEB_PORT:-18181}"
SELLER_CHROME_VIRTUAL_TIME_BUDGET_MS="${SELLER_CHROME_VIRTUAL_TIME_BUDGET_MS:-20000}"

failures=0
port_forward_pid=""
port_forward_log=""
seller_proxy_pid=""
seller_proxy_log=""
seller_session_query=""

request_status() {
  local method="$1"
  local url="$2"
  local body="${3:-}"
  local content_type="${4:-}"
  local -a args=(
    -s
    -o /dev/null
    -w '%{http_code}'
    --max-time "${UI_E2E_TIMEOUT_SECONDS}"
    -X "${method}"
  )

  if [[ -n "${content_type}" ]]; then
    args+=(-H "Content-Type: ${content_type}")
  fi

  if [[ -n "${body}" ]]; then
    args+=(--data "${body}")
  fi

  curl "${args[@]}" "${url}" 2>/dev/null || true
}

cleanup() {
  if [[ -n "${seller_proxy_pid}" ]]; then
    kill "${seller_proxy_pid}" >/dev/null 2>&1 || true
    wait "${seller_proxy_pid}" >/dev/null 2>&1 || true
  fi
  if [[ -n "${port_forward_pid}" ]]; then
    kill "${port_forward_pid}" >/dev/null 2>&1 || true
    wait "${port_forward_pid}" >/dev/null 2>&1 || true
  fi
  rm -f "${seller_proxy_log:-}" "${port_forward_log:-}"
}

trap cleanup EXIT

pass() {
  echo "[PASS] $1"
}

fail() {
  echo "[FAIL] $1"
  if [[ $# -ge 2 && -n "${2:-}" ]]; then
    echo "       $2"
  fi
  failures=$((failures + 1))
}

assert_file_contains() {
  local file="$1"
  local pattern="$2"
  grep -q "${pattern}" "${file}"
}

start_gateway_port_forward() {
  [[ -n "${UI_E2E_PORT_FORWARD_CONTEXT}" ]] || return 1
  port_forward_log="$(mktemp "${TMPDIR:-/tmp}/shop-ui-e2e-port-forward.XXXXXX")"
  kubectl --context "${UI_E2E_PORT_FORWARD_CONTEXT}" -n "${UI_E2E_PORT_FORWARD_NAMESPACE}" \
    port-forward svc/api-gateway "${UI_E2E_GATEWAY_LOCAL_PORT}:8080" >"${port_forward_log}" 2>&1 &
  port_forward_pid=$!

  for _ in $(seq 1 20); do
    if ! kill -0 "${port_forward_pid}" >/dev/null 2>&1; then
      cat "${port_forward_log}" >&2
      return 1
    fi

    if [[ "$(request_status GET "http://127.0.0.1:${UI_E2E_GATEWAY_LOCAL_PORT}/v3/api-docs/gateway")" == "200" ]]; then
      GATEWAY_URL="http://127.0.0.1:${UI_E2E_GATEWAY_LOCAL_PORT}"
      return 0
    fi

    sleep 1
  done

  cat "${port_forward_log}" >&2
  return 1
}

detect_chrome() {
  local candidate
  for candidate in \
    "${CHROME_BIN:-}" \
    "google-chrome" \
    "google-chrome-stable" \
    "chromium" \
    "chromium-browser" \
    "/Applications/Google Chrome.app/Contents/MacOS/Google Chrome"
  do
    if [[ -z "${candidate}" ]]; then
      continue
    fi
    if [[ -x "${candidate}" ]]; then
      printf '%s\n' "${candidate}"
      return 0
    fi
    if command -v "${candidate}" >/dev/null 2>&1; then
      command -v "${candidate}"
      return 0
    fi
  done
  return 1
}

check_buyer_page() {
  local name="$1"
  local url="$2"
  local expected="$3"
  local cookie_file="${4:-}"
  local output
  output="$(mktemp "${TMPDIR:-/tmp}/shop-ui-e2e-buyer.XXXXXX.html")"

  if [[ -n "${cookie_file}" ]]; then
    status="$(curl -sS -c "${cookie_file}" -b "${cookie_file}" -o "${output}" -w '%{http_code}' "${url}")"
  else
    status="$(curl -sS -o "${output}" -w '%{http_code}' "${url}")"
  fi

  if [[ "${status}" != "200" ]]; then
    fail "${name}" "expected HTTP 200, got ${status}"
  elif ! assert_file_contains "${output}" "${expected}"; then
    fail "${name}" "expected to find '${expected}'"
  else
    pass "${name}"
  fi

  rm -f "${output}"
}

check_buyer_login_flow() {
  local cookie_file
  local headers_file
  local home_file
  cookie_file="$(mktemp "${TMPDIR:-/tmp}/shop-ui-e2e-buyer-cookies.XXXXXX")"
  headers_file="$(mktemp "${TMPDIR:-/tmp}/shop-ui-e2e-buyer-headers.XXXXXX")"
  home_file="$(mktemp "${TMPDIR:-/tmp}/shop-ui-e2e-buyer-home.XXXXXX.html")"

  curl -sS -c "${cookie_file}" -b "${cookie_file}" "${GATEWAY_URL}/buyer/login" >/dev/null
  status="$(curl -sS -D "${headers_file}" -o /dev/null -c "${cookie_file}" -b "${cookie_file}" \
    -H 'Content-Type: application/x-www-form-urlencoded' \
    -X POST "${GATEWAY_URL}/buyer/login" \
    --data 'username=buyer.demo&password=password' \
    -w '%{http_code}')"

  if [[ "${status}" != "303" ]]; then
    fail "Buyer login form redirects" "expected HTTP 303, got ${status}"
  elif ! grep -q '^location: /buyer/home' "${headers_file}"; then
    fail "Buyer login form redirects" "missing redirect to /buyer/home"
  else
    pass "Buyer login form redirects"
  fi

  status="$(curl -sS -c "${cookie_file}" -b "${cookie_file}" -o "${home_file}" -w '%{http_code}' "${GATEWAY_URL}/buyer/home")"
  if [[ "${status}" != "200" ]]; then
    fail "Buyer authenticated home loads" "expected HTTP 200, got ${status}"
  elif ! grep -q 'Buyer Demo' "${home_file}"; then
    fail "Buyer authenticated home loads" "missing Buyer Demo marker"
  else
    pass "Buyer authenticated home loads"
  fi

  for route in /buyer/cart /buyer/activities /buyer/welcome /buyer/invite /buyer/loyalty /buyer/orders /buyer/wallet /buyer/profile; do
    status="$(curl -sS -c "${cookie_file}" -b "${cookie_file}" -o "${home_file}" -w '%{http_code}' "${GATEWAY_URL}${route}")"
    if [[ "${status}" != "200" ]]; then
      fail "Buyer page ${route} loads" "expected HTTP 200, got ${status}"
    elif ! grep -q 'Shop Platform' "${home_file}"; then
      fail "Buyer page ${route} loads" "missing rendered page shell"
    else
      pass "Buyer page ${route} loads"
    fi
  done

  rm -f "${cookie_file}" "${headers_file}" "${home_file}"
}

start_seller_proxy() {
  seller_proxy_log="$(mktemp "${TMPDIR:-/tmp}/shop-ui-e2e-seller-proxy.XXXXXX")"
  node ./scripts/seller-web-proxy.mjs "${SELLER_WASM_DIST_DIR}" "${GATEWAY_URL}" "${SELLER_WEB_PORT}" >"${seller_proxy_log}" 2>&1 &
  seller_proxy_pid=$!

  for _ in $(seq 1 20); do
    if ! kill -0 "${seller_proxy_pid}" >/dev/null 2>&1; then
      cat "${seller_proxy_log}" >&2
      return 1
    fi

    if [[ "$(request_status GET "http://127.0.0.1:${SELLER_WEB_PORT}/")" == "200" ]]; then
      return 0
    fi

    sleep 1
  done

  cat "${seller_proxy_log}" >&2
  return 1
}

run_seller_dump() {
  local route="$1"
  local auto_login="${2:-1}"
  local output="$3"
  local chrome_profile
  local effective_auto_login="${auto_login}"
  local extra_query=""
  chrome_profile="$(mktemp -d "${TMPDIR:-/tmp}/shop-ui-e2e-chrome.XXXXXX")"

  if [[ "${auto_login}" == "1" && -n "${seller_session_query}" ]]; then
    effective_auto_login="0"
    extra_query="&${seller_session_query}"
  fi

  "${CHROME_BIN}" \
    --headless=new \
    --disable-gpu \
    --no-first-run \
    --user-data-dir="${chrome_profile}" \
    --run-all-compositor-stages-before-draw \
    --virtual-time-budget="${SELLER_CHROME_VIRTUAL_TIME_BUDGET_MS}" \
    --dump-dom \
    "http://127.0.0.1:${SELLER_WEB_PORT}/?e2e=1&e2eRoute=${route}&e2eAutoLogin=${effective_auto_login}${extra_query}" \
    >"${output}" 2>/dev/null || true

  rm -rf "${chrome_profile}"
}

prepare_seller_session() {
  local auth_response
  auth_response="$(curl -sS -H 'Content-Type: application/json' -X POST "${GATEWAY_URL}/auth/v1/token/login" \
    --data '{"username":"seller.demo","password":"password","portal":"seller"}')"

  seller_session_query="$(
    AUTH_RESPONSE="${auth_response}" python3 - <<'PY'
import json
import os
import urllib.parse

payload = json.loads(os.environ["AUTH_RESPONSE"])
data = payload["data"]
params = {
    "e2eAccessToken": data["accessToken"],
    "e2eUsername": data["username"],
    "e2ePrincipalId": data["principalId"],
    "e2eDisplayName": data["displayName"],
}
print("&".join(f"{key}={urllib.parse.quote(str(value), safe='')}" for key, value in params.items()))
PY
  )"
}

check_seller_route() {
  local name="$1"
  local route="$2"
  local auto_login="${3:-1}"
  local output
  output="$(mktemp "${TMPDIR:-/tmp}/shop-ui-e2e-seller.XXXXXX.html")"

  run_seller_dump "${route}" "${auto_login}" "${output}"

  if ! grep -q 'id="seller-app-e2e"' "${output}"; then
    fail "${name}" "missing seller e2e marker"
  elif ! grep -q "data-route=\"${route}\"" "${output}"; then
    fail "${name}" "marker route mismatch"
  elif ! grep -q 'data-status="ready"' "${output}"; then
    fail "${name}" "seller route did not become ready"
  elif [[ "${auto_login}" == "1" ]] && ! grep -q 'data-user="seller.demo"' "${output}"; then
    fail "${name}" "seller auto-login did not complete"
  else
    pass "${name}"
  fi

  rm -f "${output}"
}

if [[ "$(request_status GET "${GATEWAY_URL}/v3/api-docs/gateway")" != "200" ]]; then
  start_gateway_port_forward
fi

CHROME_BIN="$(detect_chrome || true)"
if [[ -z "${CHROME_BIN}" ]]; then
  echo "error: could not find a Chrome/Chromium binary for seller UI automation." >&2
  exit 1
fi

echo "UI e2e testing ${GATEWAY_URL}"
echo ""

check_buyer_page "Buyer login page renders demo entrypoints" "${GATEWAY_URL}/buyer/login" "Continue as guest"
check_buyer_page "Buyer guest home renders" "${GATEWAY_URL}/buyer/home" "Guest buyer"
check_buyer_login_flow

echo "==> Building seller web bundle with ${SELLER_WASM_BUILD_TASK}"
./gradlew -q "${SELLER_WASM_BUILD_TASK}"

if [[ ! -f "${SELLER_WASM_DIST_DIR}/index.html" ]]; then
  echo "error: seller web dist was not generated at ${SELLER_WASM_DIST_DIR}" >&2
  exit 1
fi

start_seller_proxy
prepare_seller_session
check_seller_route "Seller auth screen renders" "auth" "0"
for route in marketplace orders wallet promotions profile; do
  check_seller_route "Seller route ${route} loads" "${route}" "1"
done

echo ""
if (( failures > 0 )); then
  echo "UI e2e tests failed: ${failures} check(s) did not pass."
  exit 1
fi

echo "All UI e2e tests passed."
