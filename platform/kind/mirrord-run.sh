#!/usr/bin/env bash
set -euo pipefail

# Legacy compatibility wrapper around scripts/mirrord-debug.sh.
# Usage: ./kind/mirrord-run.sh <service-name>
# Example: ./kind/mirrord-run.sh order-service

SERVICE="${1:?Usage: mirrord-run.sh <service-name>}"
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
ROOT_DIR="$(cd "${SCRIPT_DIR}/.." && pwd)"

exec bash "${ROOT_DIR}/scripts/mirrord-debug.sh" "${SERVICE}"
