#!/usr/bin/env bash
set -euo pipefail

# Force-delete all terminating pods in the shop namespace
# This is a recovery script when rollout restart leaves stuck pods

cluster_name="${1:-shop-kind}"
context_name="kind-${cluster_name}"
namespace="shop"

echo "==> Force-deleting stuck terminating pods in namespace '${namespace}'..."

# Get all pods in Terminating state
terminating_pods=$(kubectl --context "${context_name}" -n "${namespace}" get pods \
  --field-selector=status.phase=Running -o json 2>/dev/null | \
  jq -r '.items[] | select(.metadata.deletionTimestamp != null) | .metadata.name' 2>/dev/null || true)

if [[ -z "${terminating_pods}" ]]; then
  # Also check for pods that are actually terminating (have deletionTimestamp)
  terminating_pods=$(kubectl --context "${context_name}" -n "${namespace}" get pods -o json 2>/dev/null | \
    jq -r '.items[] | select(.metadata.deletionTimestamp != null) | .metadata.name' 2>/dev/null || true)
fi

if [[ -z "${terminating_pods}" ]]; then
  echo "✅ No stuck terminating pods found"
  exit 0
fi

echo "Found stuck pods:"
echo "${terminating_pods}" | while read -r pod; do
  echo "  - ${pod}"
done

echo ""
echo "==> Force deleting..."

echo "${terminating_pods}" | while read -r pod; do
  echo "  Force deleting ${pod}..."
  # Force delete without waiting for graceful shutdown
  kubectl --context "${context_name}" -n "${namespace}" delete pod "${pod}" \
    --grace-period=0 --force 2>/dev/null || true
done

echo ""
echo "✅ Stuck pods cleanup complete"
echo ""
echo "==> Checking for old ReplicaSets..."

# Get all deployments and check for multiple ReplicaSets
deployments=$(kubectl --context "${context_name}" -n "${namespace}" get deployments -o json | \
  jq -r '.items[].metadata.name' 2>/dev/null || true)

if [[ -n "${deployments}" ]]; then
  echo "${deployments}" | while read -r deployment; do
    replica_count=$(kubectl --context "${context_name}" -n "${namespace}" get deployment "${deployment}" \
      -o json 2>/dev/null | jq '.status.replicas // 0' 2>/dev/null || echo "0")
    ready_count=$(kubectl --context "${context_name}" -n "${namespace}" get deployment "${deployment}" \
      -o json 2>/dev/null | jq '.status.readyReplicas // 0' 2>/dev/null || echo "0")

    if [[ "${replica_count}" -gt 1 ]]; then
      echo "  ⚠️  ${deployment}: ${replica_count} replicas, ${ready_count} ready"
    fi
  done
fi

echo ""
echo "==> Cleanup complete. You can now run 'make e2e' again."
