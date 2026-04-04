#!/bin/bash
set -euo pipefail

MAX_SIZE_MB=5

for app in buyer-app seller-app; do
  WASM_DIR="kmp/$app/build/kotlin-webpack/wasmJs/productionExecutable"
  if [[ ! -d "${WASM_DIR}" ]]; then
    WASM_DIR="kmp/$app/build/compileSync/wasmJs/main/productionExecutable/optimized"
  fi

  if [[ ! -d "${WASM_DIR}" ]]; then
    echo "ERROR: wasm bundle directory not found for $app. Build wasm first."
    exit 1
  fi

  COMPRESSED_BYTES=0
  WASM_COUNT=0
  while IFS= read -r -d '' wasm_file; do
    FILE_BYTES=$(gzip -c "$wasm_file" | wc -c | tr -d ' ')
    COMPRESSED_BYTES=$((COMPRESSED_BYTES + FILE_BYTES))
    WASM_COUNT=$((WASM_COUNT + 1))
  done < <(find "${WASM_DIR}" -maxdepth 1 -name "*.wasm" -print0)

  if [[ "${WASM_COUNT}" -eq 0 ]]; then
    echo "ERROR: wasm bundle not found for $app. Build wasm first."
    exit 1
  fi

  SIZE_MB=$(awk "BEGIN { printf \"%.2f\", ${COMPRESSED_BYTES}/1048576 }")
  echo "$app: ${SIZE_MB}MB compressed (${WASM_COUNT} wasm assets)"

  TOO_LARGE=$(awk "BEGIN { print (${SIZE_MB} > ${MAX_SIZE_MB}) ? 1 : 0 }")
  if [[ "$TOO_LARGE" -eq 1 ]]; then
    echo "ERROR: $app exceeds ${MAX_SIZE_MB}MB target"
    exit 1
  fi
done

echo "All bundles within size budget"
