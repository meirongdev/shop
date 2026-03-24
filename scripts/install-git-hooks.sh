#!/usr/bin/env bash
set -euo pipefail

repo_root="$(git rev-parse --show-toplevel)"
cd "${repo_root}"

git config core.hooksPath .githooks

chmod +x .githooks/pre-commit .githooks/pre-push scripts/install-git-hooks.sh scripts/run-local-checks.sh

echo "Git hooks installed."
echo "Repository hooks path: .githooks"
echo "Override pre-push mode with HOOK_LOCAL_CHECK_MODE=--all|--staged|--since-main if needed."
