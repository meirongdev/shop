#!/usr/bin/env bash
set -euo pipefail

usage() {
  echo "Usage: $0 --all|--staged|--since-main" >&2
  exit 1
}

mode="${1:---since-main}"

case "${mode}" in
  --all|--staged|--since-main) ;;
  *) usage ;;
esac

repo_root="$(git rev-parse --show-toplevel)"
cd "${repo_root}"

collect_files() {
  case "${mode}" in
    --all)
      git ls-files
      ;;
    --staged)
      git diff --cached --name-only --diff-filter=ACMRTUXB
      ;;
    --since-main)
      if git rev-parse --verify origin/main >/dev/null 2>&1; then
        base_commit="$(git merge-base HEAD origin/main)"
        git diff --name-only "${base_commit}"...HEAD --diff-filter=ACMRTUXB
      else
        git ls-files
      fi
      ;;
  esac
}

changed_files=()
while IFS= read -r path; do
  changed_files+=("${path}")
done < <(collect_files | sed '/^$/d' | sort -u)

if [[ "${#changed_files[@]}" -eq 0 ]]; then
  echo "No changed files detected; skipping local checks."
  exit 0
fi

run_maven=false
run_docs=false
run_gradle=false
k8s_changed=false
run_platform=false

for path in "${changed_files[@]}"; do
  case "${path}" in
    docs-site/*)
      run_docs=true
      ;;
    frontend/kmp/*|*.gradle.kts|gradle.properties)
      run_gradle=true
      ;;
    platform/docker/*|platform/k8s/*|platform/kind/*|Tiltfile|.mirrord/*)
      k8s_changed=true
      run_platform=true
      ;;
    .github/workflows/*|Makefile|.editorconfig|.githooks/*|platform/scripts/*)
      run_maven=true
      run_docs=true
      run_gradle=true
      run_platform=true
      ;;
    pom.xml|mvnw|mvnw.cmd|.mvn/*|*/pom.xml|*.java|*.kt|*.kts|*.xml|*.yml|*.yaml|*.properties)
      run_maven=true
      ;;
  esac
done

echo "Evaluating local checks in mode: ${mode}"

if [[ "${run_maven}" == "false" && "${run_docs}" == "false" && "${run_platform}" == "false" ]]; then
  if [[ "${k8s_changed}" == "true" ]]; then
    echo ""
    echo "Warning: Docker/Kubernetes changes detected."
    echo "Consider running: make e2e"
  fi

  echo "No Maven or docs-site checks required for the detected changes."
  exit 0
fi

if [[ "${run_platform}" == "true" ]]; then
  echo "==> Validating platform assets"
  bash ./platform/scripts/validate-platform-assets.sh
fi

if [[ "${run_maven}" == "true" ]]; then
  echo "==> Running Maven verify"
  ./mvnw -q verify
fi

if [[ "${run_gradle}" == "true" ]]; then
  echo "==> Running Gradle compile check"
  ./gradlew :kmp:core:compileKotlinWasmJs --quiet 2>&1 || {
    echo "ERROR: Gradle compilation failed for KMP core module"
    exit 1
  }
fi

if [[ "${run_docs}" == "true" ]]; then
  if [[ ! -d docs-site/node_modules ]]; then
    echo "==> Installing docs-site dependencies"
    (
      cd docs-site
      npm ci
    )
  fi

  echo "==> Running docs-site build"
  (
    cd docs-site
    npm run build
  )
fi

if [[ "${k8s_changed}" == "true" ]]; then
  echo ""
  echo "Docker/Kubernetes changes detected."
  echo "Consider running: make e2e"
fi

echo "Local checks completed successfully."
