#!/usr/bin/env bash
set -euo pipefail

CLUSTER_NAME="${1:-shop-kind}"
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
ROOT_DIR="$(cd "${SCRIPT_DIR}/.." && pwd)"

echo "╔══════════════════════════════════════════╗"
echo "║   Shop Platform — Kind Cluster Setup     ║"
echo "╚══════════════════════════════════════════╝"
echo ""
bash "${ROOT_DIR}/scripts/e2e.sh" "${CLUSTER_NAME}" dev
