#!/usr/bin/env bash

LOCAL_REGISTRY="${SHOP_LOCAL_REGISTRY:-localhost:5000}"
LOCAL_IMAGE_TAG="${SHOP_LOCAL_IMAGE_TAG:-dev}"

ALL_MODULES=(
  auth-server
  api-gateway
  buyer-bff
  seller-bff
  profile-service
  promotion-service
  wallet-service
  marketplace-service
  order-service
  search-service
  notification-service
  loyalty-service
  activity-service
  webhook-service
  subscription-service
  buyer-portal
  seller-portal
  buyer-app
)

SHARED_PATHS=(
  shop-common
  shop-contracts
  pom.xml
  docker/Dockerfile.module
  docker/Dockerfile.fast
)

# Returns space-separated extra source paths for KMP modules whose source
# lives outside the conventional <module>/ directory.
module_extra_paths() {
  local module="$1"
  case "${module}" in
    seller-portal)
      echo "kmp/seller-app docker/Dockerfile.seller-portal docker/nginx-seller.conf"
      ;;
    buyer-app)
      echo "kmp/buyer-app docker/Dockerfile.buyer-app docker/nginx-buyer.conf"
      ;;
  esac
}

module_local_image_ref() {
  local module="$1"
  printf 'shop/%s:%s\n' "${module}" "${LOCAL_IMAGE_TAG}"
}

module_registry_image_ref() {
  local module="$1"
  printf '%s/shop/%s:%s\n' "${LOCAL_REGISTRY}" "${module}" "${LOCAL_IMAGE_TAG}"
}

module_runtime_image_ref() {
  local transport="$1"
  local module="$2"
  if [[ "${transport}" == "registry" ]]; then
    module_registry_image_ref "${module}"
  else
    module_local_image_ref "${module}"
  fi
}

module_jar_path() {
  local module="$1"
  printf '%s/target/%s-0.1.0-SNAPSHOT.jar\n' "${module}" "${module}"
}

array_contains() {
  local needle="$1"
  shift || true
  local item

  for item in "$@"; do
    if [[ "${item}" == "${needle}" ]]; then
      return 0
    fi
  done

  return 1
}

default_base_ref() {
  printf '%s\n' "${SHOP_LOCAL_CICD_BASE_REF:-origin/main}"
}

ensure_repo_root() {
  local repo_root
  repo_root="$(git rev-parse --show-toplevel)"
  cd "${repo_root}"
}

resolve_base_commit() {
  local base_ref="${1:-$(default_base_ref)}"

  if git rev-parse --verify "${base_ref}" >/dev/null 2>&1; then
    git merge-base HEAD "${base_ref}"
  fi
}

path_matches_target() {
  local path="$1"
  local target="$2"

  [[ "${path}" == "${target}" || "${path}" == "${target}/"* ]]
}

collect_changed_files() {
  local base_commit="$1"

  {
    git diff --name-only "${base_commit}"...HEAD --diff-filter=ACMRTUXB
    git diff --name-only --cached --diff-filter=ACMRTUXB
    git diff --name-only --diff-filter=ACMRTUXB
    git ls-files --others --exclude-standard
  } | sed '/^$/d' | sort -u
}

collect_modules_missing_local_images() {
  local module

  for module in "${ALL_MODULES[@]}"; do
    if ! docker image inspect "$(module_local_image_ref "${module}")" >/dev/null 2>&1; then
      printf '%s\n' "${module}"
    fi
  done
}

kind_context_name() {
  local cluster_name="$1"
  printf 'kind-%s\n' "${cluster_name}"
}

kind_cluster_exists() {
  local cluster_name="$1"
  kind get clusters 2>/dev/null | grep -qx "${cluster_name}"
}

kind_control_plane_node() {
  local cluster_name="$1"
  kind get nodes --name "${cluster_name}" 2>/dev/null | head -n 1
}

kind_cluster_has_app_deployments() {
  local cluster_name="$1"
  kubectl --context "$(kind_context_name "${cluster_name}")" -n shop get deployment api-gateway >/dev/null 2>&1
}

cluster_inventory_has_module_image() {
  local image_inventory="$1"
  local module="$2"
  local image_tag="${3:-${LOCAL_IMAGE_TAG}}"
  local local_ref
  local docker_hub_ref
  local registry_ref

  local_ref="shop/${module}:${image_tag}"
  docker_hub_ref="docker.io/${local_ref}"
  registry_ref="${LOCAL_REGISTRY}/${local_ref}"

  grep -Fxq "${local_ref}" <<<"${image_inventory}" ||
    grep -Fxq "${docker_hub_ref}" <<<"${image_inventory}" ||
    grep -Fxq "${registry_ref}" <<<"${image_inventory}"
}

collect_modules_missing_cluster_images() {
  local cluster_name="$1"
  local control_plane_node
  local image_inventory
  local module

  if ! kind_cluster_exists "${cluster_name}"; then
    printf '%s\n' "${ALL_MODULES[@]}"
    return 0
  fi

  if ! kind_cluster_has_app_deployments "${cluster_name}"; then
    printf '%s\n' "${ALL_MODULES[@]}"
    return 0
  fi

  control_plane_node="$(kind_control_plane_node "${cluster_name}")"
  if [[ -z "${control_plane_node}" ]]; then
    printf '%s\n' "${ALL_MODULES[@]}"
    return 0
  fi

  image_inventory="$(docker exec "${control_plane_node}" ctr -n k8s.io images ls -q 2>/dev/null || true)"
  for module in "${ALL_MODULES[@]}"; do
    if ! cluster_inventory_has_module_image "${image_inventory}" "${module}"; then
      printf '%s\n' "${module}"
    fi
  done
}

detect_changed_modules() {
  local base_ref="${1:-$(default_base_ref)}"
  local base_commit
  local module
  local path
  local matched
  local -a changed_files=()
  local -a modules=()

  base_commit="$(resolve_base_commit "${base_ref}")"
  if [[ -z "${base_commit}" ]]; then
    printf '%s\n' "${ALL_MODULES[@]}"
    return 0
  fi

  while IFS= read -r path; do
    changed_files+=("${path}")
  done < <(collect_changed_files "${base_commit}")

  if [[ "${#changed_files[@]}" -eq 0 ]]; then
    return 0
  fi

  for path in "${changed_files[@]}"; do
    for shared_path in "${SHARED_PATHS[@]}"; do
      if path_matches_target "${path}" "${shared_path}"; then
        printf '%s\n' "${ALL_MODULES[@]}"
        return 0
      fi
    done
  done

  for module in "${ALL_MODULES[@]}"; do
    matched=false
    for path in "${changed_files[@]}"; do
      if path_matches_target "${path}" "${module}"; then
        matched=true
        break
      fi
      # Check module-specific extra source paths (e.g. kmp/seller-app for seller-portal)
      local extra_paths
      extra_paths=$(module_extra_paths "${module}")
      if [[ -n "${extra_paths}" ]]; then
        for extra_path in ${extra_paths}; do
          if path_matches_target "${path}" "${extra_path}"; then
            matched=true
            break 2
          fi
        done
      fi
    done

    if [[ "${matched}" == "true" ]]; then
      modules+=("${module}")
    fi
  done

  if [[ "${#modules[@]}" -gt 0 ]]; then
    printf '%s\n' "${modules[@]}"
  fi
}

detect_build_modules() {
  local base_ref="${1:-$(default_base_ref)}"
  local module
  local -a changed_modules=()
  local -a missing_local_images=()

  while IFS= read -r module; do
    changed_modules+=("${module}")
  done < <(detect_changed_modules "${base_ref}")

  while IFS= read -r module; do
    missing_local_images+=("${module}")
  done < <(collect_modules_missing_local_images)

  for module in "${ALL_MODULES[@]}"; do
    if array_contains "${module}" ${changed_modules[@]+"${changed_modules[@]}"} || array_contains "${module}" ${missing_local_images[@]+"${missing_local_images[@]}"}; then
      printf '%s\n' "${module}"
    fi
  done
}

detect_load_modules() {
  local cluster_name="$1"
  local base_ref="${2:-$(default_base_ref)}"
  local module
  local -a changed_modules=()
  local -a missing_cluster_images=()

  while IFS= read -r module; do
    changed_modules+=("${module}")
  done < <(detect_changed_modules "${base_ref}")

  while IFS= read -r module; do
    missing_cluster_images+=("${module}")
  done < <(collect_modules_missing_cluster_images "${cluster_name}")

  for module in "${ALL_MODULES[@]}"; do
    if array_contains "${module}" ${changed_modules[@]+"${changed_modules[@]}"} || array_contains "${module}" ${missing_cluster_images[@]+"${missing_cluster_images[@]}"}; then
      printf '%s\n' "${module}"
    fi
  done
}
