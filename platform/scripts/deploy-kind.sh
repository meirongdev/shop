#!/usr/bin/env bash
set -euo pipefail

overlay="${1:-dev}"
shift || true
script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
repo_root="$(cd "${script_dir}/../.." && pwd)"
source "${script_dir}/local-cicd-modules.sh"

mode="all"
base_ref="$(default_base_ref)"
cluster_name="${SHOP_LOCAL_CLUSTER:-shop-kind}"
context_name="kind-${cluster_name}"

while [[ $# -gt 0 ]]; do
  case "$1" in
    --all) mode="all"; shift ;;
    --changed) mode="changed"; shift ;;
    --base) base_ref="$2"; shift 2 ;;
    *) echo "error: unknown argument $1" >&2; exit 1 ;;
  esac
done

# Content-addressable image tags: use sed to update all newTag values in the
# kustomization.yaml before apply, then restore after. This ensures kubectl apply
# sees changed pod template hashes and triggers native rolling updates automatically.
echo "==> Updating kustomize image tags to '${LOCAL_IMAGE_TAG}'"
kustomization_file="${repo_root}/platform/k8s/apps/overlays/${overlay}/kustomization.yaml"

# Save original content, update in-place with sed, restore after apply.
original_kustomization="$(cat "${kustomization_file}")"
sed -i.bak "s|^\([[:space:]]*newTag:[[:space:]]*\).*|\1${LOCAL_IMAGE_TAG}|g" "${kustomization_file}"
rm -f "${kustomization_file}.bak"

# Cleanup: restore original kustomization.yaml after apply (keeps git status clean).
trap 'echo "${original_kustomization}" > "${kustomization_file}"' EXIT

echo "==> Deploying overlay '${overlay}'"
kubectl --context "${context_name}" apply -k "${repo_root}/platform/k8s/apps/overlays/${overlay}"

if [[ "${mode}" == "changed" ]]; then
  deployments=()
  while IFS= read -r deployment_name; do
    deployments+=("${deployment_name}")
  done < <(detect_changed_modules "${base_ref}")
else
  deployments=("${ALL_MODULES[@]}")
fi

if [[ "${#deployments[@]}" -eq 0 ]]; then
  deployments=("${ALL_MODULES[@]}")
fi

# Wait for all deployments to roll out using native kubectl wait.
# With content-addressable image tags, kubectl apply triggers rolling updates automatically.
# We wait in parallel (up to 18 concurrent) since Kubernetes handles the rollout scheduling.
echo "==> Waiting for ${#deployments[@]} deployment(s) to roll out..."
rollout_timeout="390s"  # Matches startupProbe budget: 60s initialDelay + 30*10s + 30s safety

failed=()
pids=()
for d in "${deployments[@]}"; do
  kubectl --context "${context_name}" -n shop rollout status \
    "deployment/${d}" --timeout="${rollout_timeout}" &
  pids+=($!)
done

for i in "${!pids[@]}"; do
  if ! wait "${pids[$i]}"; then
    failed+=("${deployments[$i]}")
  fi
done

if [[ "${#failed[@]}" -gt 0 ]]; then
  echo "error: rollout timed out for: ${failed[*]}" >&2
  exit 1
fi

echo "✅ All deployments rolled out successfully."
