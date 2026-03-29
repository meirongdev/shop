#!/usr/bin/env bash
set -euo pipefail

script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
source "${script_dir}/local-cicd-modules.sh"

usage() {
  cat <<'EOF' >&2
Usage: ./scripts/load-images-kind.sh [cluster-name] [--all|--changed] [--base REF] [--registry|--kind-load]

  cluster-name  Kind cluster name (default: shop-kind)
  --all         Load all module images (default)
  --changed     Load only module images affected by Git changes
  --base REF    Git ref used for change detection (default: origin/main)
  --registry    Push images to the local registry and let pods pull them (default)
  --kind-load   Load local images directly into the Kind nodes
EOF
  exit 1
}

cluster_name="shop-kind"
mode="all"
base_ref="$(default_base_ref)"
cluster_name_set=false
transport="${SHOP_LOCAL_IMAGE_TRANSPORT:-registry}"

sync_module() {
  local module="$1"
  local local_ref registry_ref
  local_ref="$(module_local_image_ref "${module}")"
  registry_ref="$(module_registry_image_ref "${module}")"

  docker image inspect "${local_ref}" >/dev/null 2>&1 || {
    echo "error: local image ${local_ref} is missing. Run ./scripts/build-images.sh first." >&2
    return 1
  }

  if [[ "${transport}" == "registry" ]]; then
    bash "${script_dir}/setup-local-registry.sh" "${cluster_name}" >/dev/null
    docker tag "${local_ref}" "${registry_ref}"
    docker push "${registry_ref}"
  else
    kind load docker-image "${local_ref}" --name "${cluster_name}"
  fi
}

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
    --registry)
      transport="registry"
      shift
      ;;
    --kind-load)
      transport="kind-load"
      shift
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
  echo "No module changes detected and the Kind cluster already has the required images. No images to sync."
  exit 0
fi

echo "Syncing ${#modules[@]} image(s) to Kind cluster '${cluster_name}' via ${transport}: ${modules[*]}"
for module in "${modules[@]}"; do
  echo "==> Syncing $(module_local_image_ref "${module}")"
  sync_module "${module}"
done

echo "Kind image sync completed."
