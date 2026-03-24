#!/usr/bin/env bash
set -euo pipefail

# Run a local service with mirrord, intercepting traffic from the Kind cluster.
# Usage: ./kind/mirrord-run.sh <service-name>
# Example: ./kind/mirrord-run.sh order-service

SERVICE="${1:?Usage: mirrord-run.sh <service-name>}"
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
ROOT_DIR="$(cd "${SCRIPT_DIR}/.." && pwd)"

if ! command -v mirrord &>/dev/null; then
  echo "❌ mirrord is not installed. Install: brew install metalbear-co/mirrord/mirrord"
  exit 1
fi

echo "🔗 Starting ${SERVICE} locally with mirrord..."
echo "   Target: deployment/${SERVICE} in namespace shop"
echo ""

mirrord exec \
  --config-file "${SCRIPT_DIR}/mirrord.json" \
  --target "deployment/${SERVICE}" \
  -- mvn -pl "${SERVICE}" -am spring-boot:run \
  -Dspring-boot.run.profiles=local
