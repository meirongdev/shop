#!/usr/bin/env bash
set -euo pipefail

repo_root="$(git rev-parse --show-toplevel)"
cd "${repo_root}"

script_files=(
  scripts/build-images.sh
  scripts/load-images-kind.sh
  scripts/local-cicd-modules.sh
  scripts/run-local-checks.sh
  scripts/smoke-test.sh
  scripts/setup-local-registry.sh
  scripts/argocd-bootstrap.sh
  scripts/deploy-kind.sh
  scripts/e2e.sh
  scripts/local-access.sh
  scripts/mirrord-debug.sh
  kind/setup.sh
  kind/mirrord-run.sh
  kind/teardown.sh
)

echo "==> Validating shell scripts"
bash -n "${script_files[@]}"

echo "==> Validating Tiltfile syntax"
python3 - <<'PY'
from pathlib import Path
compile(Path("Tiltfile").read_text(), "Tiltfile", "exec")
PY

echo "==> Validating mirrord example configs"
python3 -m json.tool .mirrord/mirrord.api-gateway.json >/dev/null
python3 -m json.tool .mirrord/mirrord.buyer-bff.json >/dev/null
python3 -m json.tool .mirrord/mirrord.marketplace-service.json >/dev/null
python3 -m json.tool kind/mirrord.json >/dev/null

echo "==> Validating Kustomize overlay"
kubectl kustomize k8s/apps/overlays/dev >/dev/null

echo "==> Validating Kind registry wiring uses containerd certs.d hosts config"
grep -Fq 'config_path = "/etc/containerd/certs.d"' kind/cluster-config.yaml || {
  echo "error: kind/cluster-config.yaml must enable containerd certs.d registry config for the local Kind registry." >&2
  exit 1
}
if grep -Fq 'registry.mirrors."localhost:5000"' kind/cluster-config.yaml; then
  echo "error: kind/cluster-config.yaml must not use the legacy registry.mirrors patch, which breaks modern Kind/containerd nodes." >&2
  exit 1
fi
grep -Fq 'hosts.toml' scripts/setup-local-registry.sh || {
  echo "error: scripts/setup-local-registry.sh must install hosts.toml aliases into Kind nodes for localhost registry pulls." >&2
  exit 1
}

echo "==> Validating fast Docker build context rules"
grep -Fxq '!docker/Dockerfile.fast' .dockerignore || {
  echo "error: .dockerignore must include docker/Dockerfile.fast for fast local image builds." >&2
  exit 1
}
grep -Fxq '!**/target/*.jar' .dockerignore || {
  echo "error: .dockerignore must include host-built target jars for docker/Dockerfile.fast." >&2
  exit 1
}

fast_context_dir=".docker-context-validation"
fast_context_target="${fast_context_dir}/target"
fast_context_jar="${fast_context_target}/context-check.jar"
fast_context_image="shop/fast-context-validation:check"
cleanup_fast_context() {
  rm -rf "${fast_context_dir}"
  docker image rm -f "${fast_context_image}" >/dev/null 2>&1 || true
}
trap cleanup_fast_context EXIT
mkdir -p "${fast_context_target}"
printf 'fast-context-check\n' > "${fast_context_jar}"
docker build --no-cache \
  --build-arg JAR_FILE="${fast_context_jar}" \
  -f docker/Dockerfile.fast \
  -t "${fast_context_image}" \
  . >/dev/null
docker run --rm --entrypoint sh "${fast_context_image}" -c 'test -s /app/app.jar' || {
  echo "error: docker/Dockerfile.fast cannot read non-empty target artifacts from the Docker context." >&2
  exit 1
}

echo "==> Validating legacy platform manifest stays in sync"
cmp -s k8s/apps/platform.yaml k8s/apps/base/platform.yaml || {
  echo "error: k8s/apps/platform.yaml and k8s/apps/base/platform.yaml differ." >&2
  echo "Keep the compatibility copy in sync with:" >&2
  echo "  cp k8s/apps/base/platform.yaml k8s/apps/platform.yaml" >&2
  exit 1
}

echo "Platform asset validation completed successfully."
