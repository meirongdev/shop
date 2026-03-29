#!/usr/bin/env bash
set -euo pipefail

script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
source "${script_dir}/local-cicd-modules.sh"

usage() {
  cat <<'EOF' >&2
Usage: ./scripts/load-images-kind.sh [cluster-name] [--all|--changed] [--base REF]

  cluster-name  Kind cluster name (default: shop-kind)
  --all         Load all module images (default)
  --changed     Load only module images affected by Git changes
  --base REF    Git ref used for change detection (default: origin/main)
EOF
  exit 1
}

cluster_name="shop-kind"
mode="all"
base_ref="$(default_base_ref)"
cluster_name_set=false

while [[ $# -gt 0 ]]; do
  case "$1" in
    --all)
      mode="all"
      shift
      ;;
    --changed)
      mode="changed"
      shift
      ;;
    --base)
      [[ $# -ge 2 ]] || usage
      base_ref="$2"
      shift 2
      ;;
    -h|--help)
      usage
      ;;
    *)
      if [[ "${cluster_name_set}" == "false" ]]; then
        cluster_name="$1"
        cluster_name_set=true
        shift
      else
        usage
      fi
      ;;
  esac
done

ensure_repo_root

if [[ "${mode}" == "changed" ]]; then
  modules=()
  while IFS= read -r module; do
    modules+=("${module}")
  done < <(detect_load_modules "${cluster_name}" "${base_ref}")
else
  modules=("${ALL_MODULES[@]}")
fi

if [[ "${#modules[@]}" -eq 0 ]]; then
  echo "No module changes detected and the Kind cluster already has the required images. No images to load."
  exit 0
fi

echo "Loading ${#modules[@]} image(s) into Kind cluster '${cluster_name}': ${modules[*]}"
for module in "${modules[@]}"; do
  local_image_ref="$(module_local_image_ref "${module}")"

  if ! docker image inspect "${local_image_ref}" >/dev/null 2>&1; then
    echo "error: local image ${local_image_ref} is missing. Run ./scripts/build-images.sh first." >&2
    exit 1
  fi

  echo "==> Loading ${local_image_ref}"
  kind load docker-image "${local_image_ref}" --name "${cluster_name}"
done

echo "Kind image loading completed."
