#!/usr/bin/env bash
set -euo pipefail

SOURCE_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
TARGET_DIR="${1:-/Users/matthew/projects/meirongdev/shop}"

if [[ ! -d "${TARGET_DIR}" ]]; then
  echo "Target directory does not exist: ${TARGET_DIR}" >&2
  exit 1
fi

rsync -a --delete \
  --exclude '.git/' \
  "${SOURCE_DIR}/" "${TARGET_DIR}/"

echo "Synced staged project into: ${TARGET_DIR}"
