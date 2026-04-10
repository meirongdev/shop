#!/usr/bin/env bash
set -euo pipefail

repo_root="$(git rev-parse --show-toplevel)"
cd "${repo_root}"

script_files=(
  platform/scripts/build-images.sh
  platform/scripts/load-images-kind.sh
  platform/scripts/local-cicd-modules.sh
  platform/scripts/run-local-checks.sh
  platform/scripts/smoke-test.sh
  platform/scripts/verify-observability.sh
  platform/scripts/setup-local-registry.sh
  platform/scripts/argocd-bootstrap.sh
  platform/scripts/deploy-kind.sh
  platform/scripts/e2e.sh
  platform/scripts/local-access.sh
  platform/scripts/mirrord-debug.sh
  platform/kind/setup.sh
  platform/kind/mirrord-run.sh
  platform/kind/teardown.sh
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
python3 -m json.tool platform/kind/mirrord.json >/dev/null

echo "==> Validating Kustomize overlay"
kubectl kustomize platform/k8s/apps/overlays/dev >/dev/null

echo "==> Validating Kind registry wiring uses containerd certs.d hosts config"
grep -Fq 'config_path = "/etc/containerd/certs.d"' platform/kind/cluster-config.yaml || {
  echo "error: platform/kind/cluster-config.yaml must enable containerd certs.d registry config for the local Kind registry." >&2
  exit 1
}
if grep -Fq 'registry.mirrors."localhost:5000"' platform/kind/cluster-config.yaml; then
  echo "error: platform/kind/cluster-config.yaml must not use the legacy registry.mirrors patch, which breaks modern Kind/containerd nodes." >&2
  exit 1
fi
grep -Fq 'hosts.toml' platform/scripts/setup-local-registry.sh || {
  echo "error: platform/scripts/setup-local-registry.sh must install hosts.toml aliases into Kind nodes for localhost registry pulls." >&2
  exit 1
}

echo "==> Validating fast Docker build context rules"
grep -Fxq '!platform/docker/Dockerfile.fast' .dockerignore || {
  echo "error: .dockerignore must include platform/docker/Dockerfile.fast for fast local image builds." >&2
  exit 1
}
grep -Fxq '!**/target/*.jar' .dockerignore || {
  echo "error: .dockerignore must include host-built target jars for platform/docker/Dockerfile.fast." >&2
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
  -f platform/docker/Dockerfile.fast \
  -t "${fast_context_image}" \
  . >/dev/null
docker run --rm --entrypoint sh "${fast_context_image}" -c 'test -s /app/app.jar' || {
  echo "error: platform/docker/Dockerfile.fast cannot read non-empty target artifacts from the Docker context." >&2
  exit 1
}

echo "Platform asset validation completed successfully."
