#!/usr/bin/env bash
set -euo pipefail

CLUSTER_NAME="${1:-shop-kind}"

echo "🗑️  Deleting Kind cluster '${CLUSTER_NAME}'..."
kind delete cluster --name "${CLUSTER_NAME}"
echo "✅ Kind cluster deleted"
