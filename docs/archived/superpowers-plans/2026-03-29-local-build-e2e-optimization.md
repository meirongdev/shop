# Local Build / E2E Optimization Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make the local default build/deploy loop use a host-built, registry-backed fast path while preserving a legacy fallback and proving buyer/seller flows still work after local deployment.

**Architecture:** The fast path will reuse the existing changed-module detector, build JARs on the host with Maven, package them with a thin Dockerfile, push images to the local Kind registry, and refresh only the affected deployments in the `dev` overlay. The legacy path will remain available as an explicit fallback using the current Docker-in-Docker build plus `kind load` and the compatibility manifest copy.

**Tech Stack:** Bash, Maven Wrapper, Docker, Kind local registry, Kustomize, kubectl, curl, headless Chrome/Chromium

---

## File Map

- Create: `docker/Dockerfile.fast` — thin runtime image that copies a prebuilt JAR instead of compiling inside Docker
- Modify: `scripts/local-cicd-modules.sh` — shared module/image helper functions, image refs, JAR path helpers, and shared-path triggers
- Modify: `scripts/build-images.sh` — default fast host-build flow plus explicit `--legacy` fallback
- Modify: `scripts/load-images-kind.sh` — default registry push flow plus explicit `--kind-load` fallback
- Modify: `k8s/apps/overlays/dev/kustomization.yaml` — dev overlay rewrites app images to `localhost:5000/...` and forces `imagePullPolicy: Always`
- Modify: `scripts/deploy-kind.sh` — fast Kustomize apply + selective rollout restart/wait, plus explicit legacy apply mode
- Modify: `scripts/e2e.sh` — default fast orchestration plus legacy switch
- Modify: `Makefile` — expose fast defaults and legacy helper targets
- Modify: `README.md` — document the new default local loop and legacy fallback
- Modify: `docs-site/docs/getting-started/local-deployment.md` — align docs with the fast default path and verification flow
- Verify only: `scripts/smoke-test.sh`, `scripts/ui-e2e.sh`

---

### Task 1: Add fast-image foundations

**Files:**
- Create: `docker/Dockerfile.fast`
- Modify: `scripts/local-cicd-modules.sh`

- [ ] **Step 1: Confirm the fast-image surfaces do not exist yet**

Run:

```bash
test ! -f docker/Dockerfile.fast
rg -n "module_registry_image_ref|module_jar_path|docker/Dockerfile.fast" scripts/local-cicd-modules.sh
```

Expected: the `test` command succeeds and `rg` returns no matches.

- [ ] **Step 2: Create `docker/Dockerfile.fast`**

Write:

```dockerfile
ARG JAR_FILE

FROM eclipse-temurin:25-jre
WORKDIR /app
RUN apt-get update && apt-get install -y --no-install-recommends curl && rm -rf /var/lib/apt/lists/*
RUN addgroup --system appgroup && adduser --system --ingroup appgroup appuser
COPY ${JAR_FILE} app.jar
EXPOSE 8080
EXPOSE 8081
USER appuser
ENTRYPOINT ["java","-XX:MaxRAMPercentage=70.0","-XX:+HeapDumpOnOutOfMemoryError","-XX:HeapDumpPath=/tmp/heapdump.hprof","-jar","/app/app.jar"]
```

- [ ] **Step 3: Extend `scripts/local-cicd-modules.sh` with image/JAR helpers**

Add these shared helpers near the top of the file:

```bash
LOCAL_REGISTRY="${SHOP_LOCAL_REGISTRY:-localhost:5000}"
LOCAL_IMAGE_TAG="${SHOP_LOCAL_IMAGE_TAG:-dev}"

SHARED_PATHS=(
  shop-common
  shop-contracts
  pom.xml
  docker/Dockerfile.module
  docker/Dockerfile.fast
)

module_local_image_ref() {
  local module="$1"
  printf 'shop/%s:%s\n' "${module}" "${LOCAL_IMAGE_TAG}"
}

module_registry_image_ref() {
  local module="$1"
  printf '%s/shop/%s:%s\n' "${LOCAL_REGISTRY}" "${module}" "${LOCAL_IMAGE_TAG}"
}

module_runtime_image_ref() {
  local transport="$1"
  local module="$2"
  if [[ "${transport}" == "registry" ]]; then
    module_registry_image_ref "${module}"
  else
    module_local_image_ref "${module}"
  fi
}

module_jar_path() {
  local module="$1"
  printf '%s/target/%s-0.1.0-SNAPSHOT.jar\n' "${module}" "${module}"
}
```

Also change existing call sites from `module_image_ref` to `module_local_image_ref` where the script is talking about Docker-local images.

- [ ] **Step 4: Run syntax checks for the new shared layer**

Run:

```bash
bash -n scripts/local-cicd-modules.sh
test -f docker/Dockerfile.fast
```

Expected: both commands exit successfully.

- [ ] **Step 5: Commit**

```bash
git add docker/Dockerfile.fast scripts/local-cicd-modules.sh
git commit -m "feat(scripts): add fast local image metadata"
```

---

### Task 2: Make image builds default to host-built fast mode

**Files:**
- Modify: `scripts/build-images.sh`
- Verify: `docker/Dockerfile.fast`
- Verify: `scripts/local-cicd-modules.sh`

- [ ] **Step 1: Capture the current gap in `build-images.sh`**

Run:

```bash
bash scripts/build-images.sh --help 2>&1 | rg "legacy|host-build|Dockerfile.fast"
```

Expected: no matches, because the script does not expose the fast/legacy split yet.

- [ ] **Step 2: Add fast-vs-legacy argument parsing**

Update the option parser so the script defaults to fast mode but still accepts an explicit legacy fallback:

```bash
build_mode="${SHOP_LOCAL_BUILD_MODE:-fast}"

while [[ $# -gt 0 ]]; do
  case "$1" in
    --fast)
      build_mode="fast"
      shift
      ;;
    --legacy)
      build_mode="legacy"
      shift
      ;;
```

Keep the existing `--all`, `--changed`, `-j`, and `--base` flags.

- [ ] **Step 3: Add host-side Maven packaging and fast Docker packaging**

Add these functions:

```bash
build_host_jars() {
  local modules_csv
  modules_csv="$(IFS=,; printf '%s' "${modules[*]}")"
  ./mvnw -q -pl "${modules_csv}" -am -DskipTests package
}

build_fast_module() {
  local module="$1"
  local jar_file
  jar_file="$(module_jar_path "${module}")"
  [[ -f "${jar_file}" ]] || {
    echo "error: expected host-built artifact ${jar_file} is missing" >&2
    return 1
  }

  docker build \
    --build-arg JAR_FILE="${jar_file}" \
    -f docker/Dockerfile.fast \
    -t "$(module_local_image_ref "${module}")" \
    .
}

build_legacy_module() {
  local module="$1"
  docker build \
    --build-arg MODULE="${module}" \
    -f docker/Dockerfile.module \
    -t "$(module_local_image_ref "${module}")" \
    .
}
```

Before the parallel build fan-out, add:

```bash
if [[ "${build_mode}" == "fast" && "${#modules[@]}" -gt 0 ]]; then
  echo "==> Running host Maven package for: ${modules[*]}"
  build_host_jars
fi
```

Then dispatch either `build_fast_module` or `build_legacy_module` in the `xargs` fan-out.

- [ ] **Step 4: Verify both modes**

Run:

```bash
bash scripts/build-images.sh --changed --fast -j 1 2>&1 | head -20
bash scripts/build-images.sh --changed --legacy -j 1 2>&1 | head -20
```

Expected:

- fast mode prints `Running host Maven package`
- legacy mode skips the host Maven message and still builds Docker images with `docker/Dockerfile.module`

- [ ] **Step 5: Commit**

```bash
git add scripts/build-images.sh
git commit -m "feat(scripts): default local image builds to host-packaged fast mode"
```

---

### Task 3: Make local image sync and deployment default to registry-backed fast mode

**Files:**
- Modify: `scripts/load-images-kind.sh`
- Modify: `scripts/deploy-kind.sh`
- Modify: `k8s/apps/overlays/dev/kustomization.yaml`

- [ ] **Step 1: Confirm the dev overlay does not yet target the local registry**

Run:

```bash
kubectl kustomize k8s/apps/overlays/dev | rg "localhost:5000/shop/|imagePullPolicy: Always"
```

Expected: no matches.

- [ ] **Step 2: Rewrite the dev overlay to use registry image names**

Update `k8s/apps/overlays/dev/kustomization.yaml` so it keeps the replica patch and also rewrites every app image to `localhost:5000/shop/...:dev`:

```yaml
apiVersion: kustomize.config.k8s.io/v1beta1
kind: Kustomization
resources:
  - ../../base
images:
  - name: shop/auth-server
    newName: localhost:5000/shop/auth-server
    newTag: dev
  - name: shop/api-gateway
    newName: localhost:5000/shop/api-gateway
    newTag: dev
  - name: shop/buyer-bff
    newName: localhost:5000/shop/buyer-bff
    newTag: dev
  - name: shop/seller-bff
    newName: localhost:5000/shop/seller-bff
    newTag: dev
  - name: shop/profile-service
    newName: localhost:5000/shop/profile-service
    newTag: dev
  - name: shop/promotion-service
    newName: localhost:5000/shop/promotion-service
    newTag: dev
  - name: shop/wallet-service
    newName: localhost:5000/shop/wallet-service
    newTag: dev
  - name: shop/marketplace-service
    newName: localhost:5000/shop/marketplace-service
    newTag: dev
  - name: shop/order-service
    newName: localhost:5000/shop/order-service
    newTag: dev
  - name: shop/search-service
    newName: localhost:5000/shop/search-service
    newTag: dev
  - name: shop/notification-service
    newName: localhost:5000/shop/notification-service
    newTag: dev
  - name: shop/loyalty-service
    newName: localhost:5000/shop/loyalty-service
    newTag: dev
  - name: shop/activity-service
    newName: localhost:5000/shop/activity-service
    newTag: dev
  - name: shop/webhook-service
    newName: localhost:5000/shop/webhook-service
    newTag: dev
  - name: shop/subscription-service
    newName: localhost:5000/shop/subscription-service
    newTag: dev
  - name: shop/buyer-portal
    newName: localhost:5000/shop/buyer-portal
    newTag: dev
patches:
  - target:
      kind: Deployment
      namespace: shop
    patch: |-
      - op: replace
        path: /spec/replicas
        value: 1
      - op: replace
        path: /spec/template/spec/containers/0/imagePullPolicy
        value: Always
```

- [ ] **Step 3: Make `scripts/load-images-kind.sh` push by default and keep `--kind-load` as fallback**

Add a transport switch:

```bash
transport="${SHOP_LOCAL_IMAGE_TRANSPORT:-registry}"

case "$1" in
  --registry)  transport="registry" ;;
  --kind-load) transport="kind-load" ;;
esac
```

Then replace the inner loop with:

```bash
sync_module() {
  local module="$1"
  local local_ref registry_ref
  local_ref="$(module_local_image_ref "${module}")"
  registry_ref="$(module_registry_image_ref "${module}")"

  docker image inspect "${local_ref}" >/dev/null 2>&1 || {
    echo "error: local image ${local_ref} is missing. Run ./scripts/build-images.sh first." >&2
    return 1
  }

  if [[ "${transport}" == "registry" ]]; then
    bash "${script_dir}/setup-local-registry.sh" "${cluster_name}" >/dev/null
    docker tag "${local_ref}" "${registry_ref}"
    docker push "${registry_ref}"
  else
    kind load docker-image "${local_ref}" --name "${cluster_name}"
  fi
}
```

- [ ] **Step 4: Make `scripts/deploy-kind.sh` support fast-vs-legacy deployment behavior**

Use this script skeleton so the behavior is fully defined:

```bash
#!/usr/bin/env bash
set -euo pipefail

overlay="${1:-dev}"
shift || true
script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
repo_root="$(cd "${script_dir}/.." && pwd)"
source "${script_dir}/local-cicd-modules.sh"

mode="all"
deploy_mode="${SHOP_LOCAL_DEPLOY_MODE:-fast}"
base_ref="$(default_base_ref)"
cluster_name="${SHOP_LOCAL_CLUSTER:-shop-kind}"
context_name="kind-${cluster_name}"

while [[ $# -gt 0 ]]; do
  case "$1" in
    --all) mode="all"; shift ;;
    --changed) mode="changed"; shift ;;
    --base) base_ref="$2"; shift 2 ;;
    --legacy) deploy_mode="legacy"; shift ;;
    *) echo "error: unknown argument $1" >&2; exit 1 ;;
  esac
done

if [[ "${deploy_mode}" == "legacy" ]]; then
  kubectl --context "${context_name}" apply -f "${repo_root}/k8s/apps/platform.yaml"
else
  kubectl --context "${context_name}" apply -k "${repo_root}/k8s/apps/overlays/${overlay}"
fi

if [[ "${mode}" == "changed" ]]; then
  mapfile -t deployments < <(detect_changed_modules "${base_ref}")
else
  deployments=("${ALL_MODULES[@]}")
fi

if [[ "${#deployments[@]}" -eq 0 || "${deploy_mode}" == "legacy" ]]; then
  deployments=("${ALL_MODULES[@]}")
fi

if [[ "${deploy_mode}" == "fast" ]]; then
  for deployment_name in "${deployments[@]}"; do
    kubectl --context "${context_name}" -n shop rollout restart "deployment/${deployment_name}"
  done
fi

for deployment_name in "${deployments[@]}"; do
  kubectl --context "${context_name}" -n shop rollout status "deployment/${deployment_name}" --timeout=300s
done
```

- [ ] **Step 5: Verify the fast deploy path**

Run:

```bash
kubectl kustomize k8s/apps/overlays/dev | rg "localhost:5000/shop/api-gateway:dev"
bash scripts/load-images-kind.sh shop-kind --changed --registry 2>&1 | head -10
bash scripts/deploy-kind.sh dev --changed 2>&1 | head -20
```

Expected:

- Kustomize output shows `localhost:5000/shop/api-gateway:dev`
- image sync prints `docker push`
- deploy script applies the `dev` overlay and waits on the affected deployments

- [ ] **Step 6: Commit**

```bash
git add scripts/load-images-kind.sh scripts/deploy-kind.sh k8s/apps/overlays/dev/kustomization.yaml
git commit -m "feat(local-dev): default local deploys to registry-backed fast mode"
```

---

### Task 4: Wire the fast path into `e2e`, Make targets, and docs

**Files:**
- Modify: `scripts/e2e.sh`
- Modify: `Makefile`
- Modify: `README.md`
- Modify: `docs-site/docs/getting-started/local-deployment.md`

- [ ] **Step 1: Confirm the repository still documents the old load-first flow**

Run:

```bash
rg -n "load-images-kind.sh shop-kind|make e2e|make registry" README.md docs-site/docs/getting-started/local-deployment.md
```

Expected: matches describe the current `build -> load -> deploy` loop without a fast/legacy split.

- [ ] **Step 2: Update `scripts/e2e.sh` to default to fast mode**

Add an execution mode switch:

```bash
flow="${E2E_FLOW:-fast}"

if [[ "${flow}" == "legacy" ]]; then
  bash "${repo_root}/scripts/build-images.sh" --changed --legacy -j "${jobs}"
  bash "${repo_root}/scripts/load-images-kind.sh" "${cluster_name}" --changed --kind-load
  bash "${repo_root}/scripts/deploy-kind.sh" "${overlay}" --all --legacy
else
  bash "${repo_root}/scripts/build-images.sh" --changed --fast -j "${jobs}"
  bash "${repo_root}/scripts/load-images-kind.sh" "${cluster_name}" --changed --registry
  bash "${repo_root}/scripts/deploy-kind.sh" "${overlay}" --changed
fi
```

Keep the existing smoke and UI e2e calls unchanged after deployment succeeds.

- [ ] **Step 3: Update `Makefile` targets**

Add explicit fallback targets and keep the defaults on fast mode:

```make
build-images: ## Build service images for local/Kind use (fast by default)
	./scripts/build-images.sh --fast

build-images-legacy: ## Build service images with the legacy Docker-in-Docker path
	./scripts/build-images.sh --legacy

load-images: ## Sync built images into the Kind cluster (registry push by default)
	./scripts/load-images-kind.sh $(CLUSTER) --registry

load-images-legacy: ## Load built images into the Kind cluster with kind load
	./scripts/load-images-kind.sh $(CLUSTER) --kind-load

e2e: ## Bootstrap Kind, run fast local build/deploy, and verify buyer/seller flows
	bash ./scripts/e2e.sh $(CLUSTER) $(OVERLAY)

e2e-legacy: ## Run the legacy Docker build + kind load loop
	E2E_FLOW=legacy bash ./scripts/e2e.sh $(CLUSTER) $(OVERLAY)
```

Also update `.PHONY` to include `build-images-legacy`, `load-images-legacy`, and `e2e-legacy`.

- [ ] **Step 4: Update user-facing docs**

In both `README.md` and `docs-site/docs/getting-started/local-deployment.md`, replace the old “build then kind load” guidance with:

```md
1. `make registry`（首次或重建 Kind 后执行一次）
2. `make e2e`（默认走 fast：host Maven build + registry push + selective deploy）
3. 如需排障，使用 `make e2e-legacy`
4. 验证入口保持不变：`make local-access`、`make smoke-test`、`make ui-e2e`
```

Also update any direct command examples so `load-images-kind.sh` registry mode appears before `--kind-load`.

- [ ] **Step 5: Verify help and docs output**

Run:

```bash
make help | rg "build-images-legacy|load-images-legacy|e2e-legacy"
rg -n "host Maven build|e2e-legacy|registry push" README.md docs-site/docs/getting-started/local-deployment.md
```

Expected: new helper targets show up in `make help`, and both docs mention the fast default path plus the legacy fallback.

- [ ] **Step 6: Commit**

```bash
git add scripts/e2e.sh Makefile README.md docs-site/docs/getting-started/local-deployment.md
git commit -m "docs(local-dev): document fast default local build loop"
```

---

### Task 5: Validate fast and legacy flows end-to-end

**Files:**
- Verify: `scripts/build-images.sh`
- Verify: `scripts/load-images-kind.sh`
- Verify: `scripts/deploy-kind.sh`
- Verify: `scripts/e2e.sh`
- Verify: `scripts/smoke-test.sh`
- Verify: `scripts/ui-e2e.sh`

- [ ] **Step 1: Run repository validation for changed platform assets**

Run:

```bash
make platform-validate
```

Expected: shell syntax, Kustomize, Tiltfile, and manifest sync checks all pass.

- [ ] **Step 2: Run the fast local end-to-end loop**

Run:

```bash
make e2e
```

Expected:

- Kind bootstrap completes
- changed modules are host-built on the machine
- image sync uses registry push
- deployment applies the dev overlay and restarts only affected apps
- smoke tests pass
- buyer SSR and seller Web UI checks pass

- [ ] **Step 3: Prove the stable local access path still works**

In terminal A run:

```bash
make local-access
```

In terminal B run:

```bash
bash scripts/smoke-test.sh
bash scripts/ui-e2e.sh
```

Expected: smoke and UI e2e continue to pass when accessed through the documented `18080` port-forward path.

- [ ] **Step 4: Run the legacy fallback once**

Run:

```bash
make e2e-legacy
```

Expected: the fallback path still completes successfully, even though it is slower.

- [ ] **Step 5: Commit the verified implementation**

```bash
git add docker/Dockerfile.fast scripts/local-cicd-modules.sh scripts/build-images.sh scripts/load-images-kind.sh scripts/deploy-kind.sh scripts/e2e.sh Makefile k8s/apps/overlays/dev/kustomization.yaml README.md docs-site/docs/getting-started/local-deployment.md
git commit -m "feat(local-dev): accelerate local build and deploy loop"
```
