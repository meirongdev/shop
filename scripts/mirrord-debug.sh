#!/usr/bin/env bash
set -euo pipefail

script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
source "${script_dir}/local-cicd-modules.sh"

usage() {
  cat <<'EOF' >&2
Usage: ./scripts/mirrord-debug.sh <module> [--namespace NS] [--target TARGET] [--config FILE] [--] [command...]

Examples:
  ./scripts/mirrord-debug.sh api-gateway
  ./scripts/mirrord-debug.sh buyer-bff -- ./mvnw -pl buyer-bff -am spring-boot:run
  ./scripts/mirrord-debug.sh marketplace-service --namespace shop --target deployment/marketplace-service

Defaults:
  namespace: shop
  target:    deployment/<module>
  command:   ./mvnw -pl <module> -am spring-boot:run

If .mirrord/mirrord.<module>.json exists, it is passed automatically via -f unless --config overrides it.
EOF
  exit 1
}

ensure_repo_root

module="${1:-}"
[[ -n "${module}" ]] || usage
shift || true

namespace="${MIRRORD_NAMESPACE:-shop}"
target=""
config_file=""
command=()

while [[ $# -gt 0 ]]; do
  case "$1" in
    --namespace)
      [[ $# -ge 2 ]] || usage
      namespace="$2"
      shift 2
      ;;
    --target)
      [[ $# -ge 2 ]] || usage
      target="$2"
      shift 2
      ;;
    --config)
      [[ $# -ge 2 ]] || usage
      config_file="$2"
      shift 2
      ;;
    --help|-h)
      usage
      ;;
    --)
      shift
      command=("$@")
      break
      ;;
    *)
      usage
      ;;
  esac
done

module_known=false
for known_module in "${ALL_MODULES[@]}"; do
  if [[ "${known_module}" == "${module}" ]]; then
    module_known=true
    break
  fi
done

[[ "${module_known}" == "true" ]] || {
  echo "error: unknown module '${module}'" >&2
  usage
}

if [[ -z "${target}" ]]; then
  target="deployment/${module}"
fi

if [[ -z "${config_file}" ]]; then
  candidate=".mirrord/mirrord.${module}.json"
  if [[ -f "${candidate}" ]]; then
    config_file="${candidate}"
  fi
fi

if [[ "${#command[@]}" -eq 0 ]]; then
  command=(./mvnw -pl "${module}" -am spring-boot:run)
fi

mirrord_args=(
  exec
  --target "${target}"
  --namespace "${namespace}"
)

if [[ -n "${config_file}" ]]; then
  mirrord_args+=(-f "${config_file}")
fi

echo "==> Launching mirrord for ${module}"
echo "    Namespace: ${namespace}"
echo "    Target:    ${target}"
if [[ -n "${config_file}" ]]; then
  echo "    Config:    ${config_file}"
fi
echo "    Command:   ${command[*]}"

exec mirrord "${mirrord_args[@]}" -- "${command[@]}"
