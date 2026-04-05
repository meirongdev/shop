#!/usr/bin/env bash
set -euo pipefail

REPO_ROOT="$(git rev-parse --show-toplevel)"
DOCS_DIR="$REPO_ROOT/docs"
MATRIX_FILE="$DOCS_DIR/SOURCE-OF-TRUTH-MATRIX.md"

if [ ! -f "$MATRIX_FILE" ]; then
  echo "SOURCE-OF-TRUTH-MATRIX.md not found; skipping check."
  exit 0
fi

# Extract allowed doc paths from matrix (accepts bare filenames or docs/ paths)
TOKENS=$(grep -Eo '[A-Za-z0-9_./-]+\.md' "$MATRIX_FILE" | sort -u)

ALLOWED=""
while IFS= read -r token; do
  if [[ -z "$token" ]]; then
    continue
  fi
  if [[ "$token" == docs/* ]]; then
    ALLOWED="$ALLOWED"$'\n'"$token"
  else
    # if a bare filename refers to a file in docs/, normalize to docs/<file>
    if [ -f "$DOCS_DIR/$token" ]; then
      ALLOWED="$ALLOWED"$'\n'"docs/$token"
    else
      # keep token as-is if it exists relative to repo root
      if [ -f "$REPO_ROOT/$token" ]; then
        ALLOWED="$ALLOWED"$'\n'"$token"
      fi
    fi
  fi
done <<< "$TOKENS"

ALLOWED=$(echo -e "$ALLOWED" | grep -v '^$' | sort -u)

# Additional whitelisted directories (relative to repo root)
WHITELIST_DIRS=(
  "$DOCS_DIR/archived"
  "$DOCS_DIR/superpowers/specs"
  "$DOCS_DIR/services"
  "$DOCS_DIR/deployment"
  "$DOCS_DIR/handoff"
)

FAILED=0

# Find all markdown files under docs (excluding archived via whitelist check)
while IFS= read -r file; do
  rel="${file#$REPO_ROOT/}"

  # Skip archived or explicitly whitelisted directories
  skip=false
  for w in "${WHITELIST_DIRS[@]}"; do
    if [[ "$file" == "$w"* ]]; then
      skip=true
      break
    fi
  done
  if [ "$skip" = true ]; then
    continue
  fi

  # If rel is exactly referenced in ALLOWED, ok
  if echo "$ALLOWED" | grep -Fxq "$rel"; then
    continue
  fi

  # If any allowed entry is a prefix of this file path, treat as allowed
  prefix_match=false
  while read -r allowed; do
    if [[ -z "$allowed" ]]; then continue; fi
    if [[ "$rel" == "$allowed"* ]]; then
      prefix_match=true
      break
    fi
  done <<< "$ALLOWED"
  if [ "$prefix_match" = true ]; then
    continue
  fi

  echo "UNLISTED DOC: $rel (not referenced in SOURCE-OF-TRUTH-MATRIX.md and not in whitelist)"
  FAILED=1

done < <(find "$DOCS_DIR" -type f -name '*.md' | sort)

if [ "$FAILED" -eq 1 ]; then
  echo "Doc SSOT check failed. Add the doc to docs/SOURCE-OF-TRUTH-MATRIX.md or move it under docs/archived/."
  exit 2
fi

echo "Doc SSOT check passed."
exit 0
