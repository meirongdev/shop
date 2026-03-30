#!/usr/bin/env bash
set -euo pipefail

context="${KUBECTL_CONTEXT:-$(kubectl config current-context 2>/dev/null || true)}"
namespace="${PORT_FORWARD_NAMESPACE:-shop}"
gateway_port="${LOCAL_GATEWAY_PORT:-18080}"
mailpit_port="${LOCAL_MAILPIT_PORT:-18025}"
prometheus_port="${LOCAL_PROMETHEUS_PORT:-19090}"

grafana_port="${LOCAL_GRAFANA_PORT:-13000}"
  echo "error: unable to determine kubectl context; set KUBECTL_CONTEXT explicitly." >&2
  exit 1
fi

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

start_port_forward() {
  local name="$1"
  local local_port="$2"
  local remote_port="$3"
  local log_file
  local pid

  log_file="$(mktemp "${TMPDIR:-/tmp}/shop-port-forward.${name}.XXXXXX")"
  kubectl --context "${context}" -n "${namespace}" \
    port-forward "svc/${name}" "${local_port}:${remote_port}" >"${log_file}" 2>&1 &
  pid=$!

  for _ in $(seq 1 20); do
    if ! kill -0 "${pid}" >/dev/null 2>&1; then
      cat "${log_file}" >&2
      return 1
    fi

    if curl -s -o /dev/null --max-time 2 "http://127.0.0.1:${local_port}" 2>/dev/null; then
      pids+=("${pid}")
      logs+=("${log_file}")
      return 0
    fi

    sleep 1
  done

  cat "${log_file}" >&2
  return 1
}

trap cleanup EXIT INT TERM

start_port_forward "api-gateway" "${gateway_port}" 8080
start_port_forward "mailpit" "${mailpit_port}" 8025
start_port_forward "prometheus" "${prometheus_port}" 9090
start_port_forward "grafana" "${grafana_port}" 3000

echo "Local access ready via kubectl port-forward on context '${context}'."
echo "  Gateway:    http://127.0.0.1:${gateway_port}"
echo "  Buyer SSR:  http://127.0.0.1:${gateway_port}/buyer/login"
echo "  Seller App: http://127.0.0.1:${gateway_port}/seller/"
echo "  Mailpit:    http://127.0.0.1:${mailpit_port}"
echo "  Prometheus: http://127.0.0.1:${prometheus_port}"
echo "  Grafana:    http://127.0.0.1:${grafana_port}"
echo ""
echo "Press Ctrl-C to stop forwarding."

wait
