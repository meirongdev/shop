#!/usr/bin/env bash
set -euo pipefail

script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# Shared module detection keeps build/load behavior aligned.
source "${script_dir}/local-cicd-modules.sh"

usage() {
  cat <<'EOF' >&2
Usage: ./scripts/build-images.sh [--all|--changed|--module MODULE] [--fast|--legacy] [-j N] [--base REF]

  --all           Build all module images (default)
  --changed       Build only module images affected by Git changes
  --module MODULE Build a single named module (e.g. --module buyer-bff)
  --fast          Build host-packaged jars into images (default)
  --legacy        Build module images with docker/Dockerfile.module
  -j N            Parallel Docker build jobs (default: 4)
  --base REF      Git ref used for change detection (default: origin/main)
EOF
  exit 1
}

mode="all"
single_module=""
build_mode="${SHOP_LOCAL_BUILD_MODE:-fast}"
jobs=4
base_ref="$(default_base_ref)"

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
    --module)
      [[ $# -ge 2 ]] || usage
      mode="module"
      single_module="$2"
      shift 2
      ;;
    --fast)
      build_mode="fast"
      shift
      ;;
    --legacy)
      build_mode="legacy"
      shift
      ;;
    -j)
      [[ $# -ge 2 ]] || usage
      jobs="$2"
      shift 2
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
      usage
      ;;
  esac
done

[[ "${jobs}" =~ ^[1-9][0-9]*$ ]] || {
  echo "error: -j expects a positive integer" >&2
  exit 1
}

ensure_repo_root

build_host_jars() {
  local modules_csv
  local maven_modules=()

  for m in "${modules[@]}"; do
    [[ "${m}" != "seller-portal" && "${m}" != "buyer-app" ]] && maven_modules+=("${m}")
  done

  [[ "${#maven_modules[@]}" -eq 0 ]] && return 0

  # Convert short module names to full Maven paths for services
  local maven_paths=()
  for m in "${maven_modules[@]}"; do
    case "${m}" in
      auth-server|api-gateway|buyer-bff|seller-bff|profile-service|promotion-service|wallet-service|marketplace-service|order-service|search-service|notification-service|loyalty-service|activity-service|webhook-service|subscription-service)
        maven_paths+=("services/${m}")
        ;;
      buyer-portal)
        maven_paths+=("frontend/buyer-portal")
        ;;
      *)
        maven_paths+=("${m}")
        ;;
    esac
  done

  modules_csv="$(IFS=,; printf '%s' "${maven_paths[*]}")"
  # Include 'clean' so renamed/deleted resource files never ghost into the JAR.
  ./mvnw -q --no-transfer-progress -T 1C -pl "${modules_csv}" -am -DskipTests clean package
}

build_fast_module() {
  local module="$1"
  local jar_file

  if [[ "${module}" == "seller-portal" ]]; then
    build_seller_portal
    return
  fi

  if [[ "${module}" == "buyer-app" ]]; then
    build_buyer_app
    return
  fi

  jar_file="$(module_jar_path "${module}")"
  [[ -f "${jar_file}" ]] || {
    echo "error: expected host-built artifact ${jar_file} is missing" >&2
    return 1
  }

  echo "==> Building $(module_local_image_ref "${module}") with platform/docker/Dockerfile.fast"
  docker build \
    --build-arg JAR_FILE="${jar_file}" \
    -f platform/docker/Dockerfile.fast \
    -t "$(module_local_image_ref "${module}")" \
    .
}

build_seller_portal() {
  local dist_dir="frontend/kmp/seller-app/build/dist/wasmJs/productionExecutable"

  if [[ ! -d "${dist_dir}" ]]; then
    echo "==> Building seller-portal WASM (first time, may take several minutes)..."
    ./gradlew :kmp:seller-app:wasmJsBrowserProductionWebpack --no-daemon -q
  fi

  echo "==> Building $(module_local_image_ref "seller-portal") with platform/docker/Dockerfile.seller-portal"
  docker build \
    --build-arg "DIST_DIR=${dist_dir}" \
    -f platform/docker/Dockerfile.seller-portal \
    -t "$(module_local_image_ref "seller-portal")" \
    .
}

build_buyer_app() {
  local dist_dir="frontend/kmp/buyer-app/build/dist/wasmJs/productionExecutable"

  if [[ ! -d "${dist_dir}" ]]; then
    echo "==> Building buyer-app WASM (first time, may take several minutes)..."
    ./gradlew :kmp:buyer-app:wasmJsBrowserProductionWebpack --no-daemon -q
  fi

  echo "==> Building $(module_local_image_ref "buyer-app") with platform/docker/Dockerfile.buyer-app"
  docker build \
    --build-arg "DIST_DIR=${dist_dir}" \
    -f platform/docker/Dockerfile.buyer-app \
    -t "$(module_local_image_ref "buyer-app")" \
    .
}

build_legacy_module() {
  local module="$1"

  echo "==> Building $(module_local_image_ref "${module}") with platform/docker/Dockerfile.module"
  docker build \
    --build-arg MODULE="${module}" \
    -f platform/docker/Dockerfile.module \
    -t "$(module_local_image_ref "${module}")" \
    .
}

export LOCAL_IMAGE_TAG LOCAL_REGISTRY
export -f module_local_image_ref module_jar_path build_host_jars build_fast_module build_seller_portal build_buyer_app build_legacy_module

if [[ "${mode}" == "changed" ]]; then
  modules=()
  while IFS= read -r module; do
    modules+=("${module}")
  done < <(detect_build_modules "${base_ref}")
elif [[ "${mode}" == "module" ]]; then
  array_contains "${single_module}" "${ALL_MODULES[@]}" || {
    echo "error: unknown module '${single_module}'. Valid modules: ${ALL_MODULES[*]}" >&2
    exit 1
  }
  modules=("${single_module}")
else
  modules=("${ALL_MODULES[@]}")
fi

if [[ "${#modules[@]}" -eq 0 ]]; then
  echo "No module changes detected and all local images are already present. Nothing to build."
  exit 0
fi

if [[ "${build_mode}" == "fast" && "${#modules[@]}" -gt 0 ]]; then
  echo "==> Running host Maven package for: ${modules[*]}"
  build_host_jars
fi

echo "Building ${#modules[@]} module(s) with ${jobs} parallel job(s): ${modules[*]}"
if [[ "${build_mode}" == "fast" ]]; then
  build_function="build_fast_module"
else
  build_function="build_legacy_module"
fi

printf '%s\n' "${modules[@]}" | xargs -n 1 -P "${jobs}" bash -c "${build_function} \"\$1\"" _
echo "Docker image build completed."
