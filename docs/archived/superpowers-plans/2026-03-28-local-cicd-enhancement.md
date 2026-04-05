# Local CI/CD Enhancement Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Enhance the existing local CI/CD scripting layer with incremental image builds, parallel execution, smoke tests, and a one-command E2E workflow — without introducing new orchestration tools (Tekton/ArgoCD).

**Architecture:** Three phases of progressive enhancement. Phase 1 optimizes existing bash scripts (incremental + parallel builds, smoke tests, unified `make e2e`). Phase 2 introduces Tilt.dev for inner-loop hot-reload during active development. Phase 3 (optional, deferred) adds ArgoCD for GitOps-style sync against the same in-repo manifests.

**Tech Stack:** Bash, GNU parallel / xargs, curl, Kind, Tilt.dev (Phase 2), ArgoCD (Phase 3)

**Context — Current State:**
- `scripts/build-images.sh` — serial loop, builds all 16 modules every time (~15-20 min full build)
- `scripts/load-images-kind.sh` — serial loop, loads all 16 images every time
- `scripts/kind-up.sh` — creates Kind cluster + infra
- `scripts/deploy-kind.sh` — `kubectl apply -f k8s/apps/platform.yaml`
- Kind cluster: Cilium CNI (no kube-proxy), NodePort 30080→8080 for api-gateway
- No smoke tests, no Tiltfile, no ArgoCD

---

## Phase 1: Optimize Existing Scripts

### Task 1: Incremental image build script

**Files:**
- Modify: `scripts/build-images.sh`

The current script always builds all 16 modules. Add `--changed` mode that uses `git diff` to detect which modules have source changes vs `origin/main` (or a configurable base), and only build those. Keep the `--all` default for CI/backwards-compat.

- [ ] **Step 1: Read current script and understand module→directory mapping**

Each module name in the `modules` array maps 1:1 to a top-level directory of the same name (e.g., `auth-server/`, `api-gateway/`). The Dockerfile builds with `--build-arg MODULE=<name>` and copies `${MODULE}/target/${MODULE}-0.1.0-SNAPSHOT.jar`. Additionally, changes to `shop-common/` or `shop-contracts/` affect all modules.

- [ ] **Step 2: Replace `scripts/build-images.sh` with incremental + parallel support**

```bash
#!/usr/bin/env bash
set -euo pipefail

ALL_MODULES=(
  auth-server
  api-gateway
  buyer-bff
  seller-bff
  profile-service
  promotion-service
  wallet-service
  marketplace-service
  order-service
  search-service
  notification-service
  loyalty-service
  activity-service
  webhook-service
  subscription-service
  buyer-portal
)

# Shared modules: changes here mean rebuild everything
SHARED_DIRS="shop-common shop-contracts pom.xml docker/Dockerfile.module"

usage() {
  echo "Usage: $0 [--all|--changed] [-j N]"
  echo "  --all       Build all modules (default)"
  echo "  --changed   Build only modules with changes vs origin/main"
  echo "  -j N        Parallel jobs (default: 4)"
  exit 1
}

mode="all"
jobs=4

while [[ $# -gt 0 ]]; do
  case "$1" in
    --all)     mode="all"; shift ;;
    --changed) mode="changed"; shift ;;
    -j)        jobs="$2"; shift 2 ;;
    *)         usage ;;
  esac
done

detect_changed_modules() {
  local base_commit
  if git rev-parse --verify origin/main >/dev/null 2>&1; then
    base_commit="$(git merge-base HEAD origin/main)"
  else
    # No origin/main — build everything
    printf '%s\n' "${ALL_MODULES[@]}"
    return
  fi

  local changed_files
  changed_files="$(git diff --name-only "${base_commit}"...HEAD)"

  # If any shared dir changed, rebuild all
  for shared in ${SHARED_DIRS}; do
    if echo "${changed_files}" | grep -q "^${shared}"; then
      printf '%s\n' "${ALL_MODULES[@]}"
      return
    fi
  done

  # Otherwise, only modules whose directory has changes
  local found=()
  for module in "${ALL_MODULES[@]}"; do
    if echo "${changed_files}" | grep -q "^${module}/"; then
      found+=("${module}")
    fi
  done

  if [[ ${#found[@]} -eq 0 ]]; then
    echo "No module changes detected." >&2
    return
  fi

  printf '%s\n' "${found[@]}"
}

build_module() {
  local module="$1"
  echo "==> Building shop/${module}:dev"
  docker build \
    --build-arg MODULE="${module}" \
    -f docker/Dockerfile.module \
    -t "shop/${module}:dev" \
    . 2>&1 | tail -1
}
export -f build_module

if [[ "${mode}" == "changed" ]]; then
  mapfile -t modules < <(detect_changed_modules)
else
  modules=("${ALL_MODULES[@]}")
fi

if [[ ${#modules[@]} -eq 0 ]]; then
  echo "Nothing to build."
  exit 0
fi

echo "Building ${#modules[@]} module(s) with ${jobs} parallel jobs: ${modules[*]}"
printf '%s\n' "${modules[@]}" | xargs -I{} -P "${jobs}" bash -c 'build_module "$@"' _ {}
echo "Done."
```

- [ ] **Step 3: Verify `--all` mode builds all 16 modules (same behavior as before)**

Run: `bash scripts/build-images.sh --all -j 1 2>&1 | head -20`
Expected: `Building 16 module(s)` header followed by `==> Building shop/auth-server:dev` etc.

- [ ] **Step 4: Verify `--changed` mode on a branch with one module change**

Run (on a branch that touched only `auth-server/`):
```bash
bash scripts/build-images.sh --changed
```
Expected: Only `auth-server` built.

- [ ] **Step 5: Verify shared-dir change triggers full rebuild**

Run (after touching `shop-common/`):
```bash
bash scripts/build-images.sh --changed 2>&1 | head -3
```
Expected: `Building 16 module(s)` — all modules rebuild.

- [ ] **Step 6: Commit**

```bash
git add scripts/build-images.sh
git commit -m "feat(scripts): incremental + parallel image builds

build-images.sh now supports --changed (git diff vs origin/main) and
-j N for parallel Docker builds. Shared dir changes (shop-common,
shop-contracts, pom.xml, Dockerfile) trigger full rebuild."
```

---

### Task 2: Incremental image loading

**Files:**
- Modify: `scripts/load-images-kind.sh`

Mirror the `--changed` / `--all` logic for `kind load`. Loading all 16 images is slow; only load what was built.

- [ ] **Step 1: Replace `scripts/load-images-kind.sh`**

```bash
#!/usr/bin/env bash
set -euo pipefail

cluster_name="${1:-shop-kind}"
shift || true

ALL_MODULES=(
  auth-server
  api-gateway
  buyer-bff
  seller-bff
  profile-service
  promotion-service
  wallet-service
  marketplace-service
  order-service
  search-service
  notification-service
  loyalty-service
  activity-service
  webhook-service
  subscription-service
  buyer-portal
)

mode="all"
for arg in "$@"; do
  case "${arg}" in
    --changed) mode="changed" ;;
    --all)     mode="all" ;;
  esac
done

if [[ "${mode}" == "changed" ]]; then
  # Reuse the same detection logic from build-images.sh
  SHARED_DIRS="shop-common shop-contracts pom.xml docker/Dockerfile.module"
  base_commit="$(git merge-base HEAD origin/main 2>/dev/null || echo "")"

  if [[ -z "${base_commit}" ]]; then
    modules=("${ALL_MODULES[@]}")
  else
    changed_files="$(git diff --name-only "${base_commit}"...HEAD)"
    shared_changed=false
    for shared in ${SHARED_DIRS}; do
      if echo "${changed_files}" | grep -q "^${shared}"; then
        shared_changed=true
        break
      fi
    done

    if [[ "${shared_changed}" == "true" ]]; then
      modules=("${ALL_MODULES[@]}")
    else
      modules=()
      for module in "${ALL_MODULES[@]}"; do
        if echo "${changed_files}" | grep -q "^${module}/"; then
          modules+=("${module}")
        fi
      done
    fi
  fi
else
  modules=("${ALL_MODULES[@]}")
fi

if [[ ${#modules[@]} -eq 0 ]]; then
  echo "No images to load."
  exit 0
fi

echo "Loading ${#modules[@]} image(s) into cluster '${cluster_name}': ${modules[*]}"
for module in "${modules[@]}"; do
  echo "==> Loading shop/${module}:dev"
  kind load docker-image "shop/${module}:dev" --name "${cluster_name}"
done
```

- [ ] **Step 2: Quick test**

Run: `bash scripts/load-images-kind.sh shop-kind --changed 2>&1 | head -5`
Expected: Only changed module images attempted (or "No images to load" if nothing changed).

- [ ] **Step 3: Commit**

```bash
git add scripts/load-images-kind.sh
git commit -m "feat(scripts): incremental Kind image loading

load-images-kind.sh now supports --changed flag to only load images
for modules with git changes, matching build-images.sh behavior."
```

---

### Task 3: Smoke test script

**Files:**
- Create: `scripts/smoke-test.sh`

After deploying to Kind, run lightweight checks against the gateway NodePort (localhost:8080) to verify the critical path is up: gateway health, auth endpoint, buyer-bff, seller-bff.

- [ ] **Step 1: Create `scripts/smoke-test.sh`**

```bash
#!/usr/bin/env bash
set -euo pipefail

GATEWAY_URL="${GATEWAY_URL:-http://localhost:8080}"
TIMEOUT=5
RETRIES=3
RETRY_DELAY=5

red()   { printf '\033[0;31m%s\033[0m\n' "$*"; }
green() { printf '\033[0;32m%s\033[0m\n' "$*"; }

failures=0

check() {
  local name="$1" url="$2" expected_status="${3:-200}"
  local attempt=0 status

  while (( attempt < RETRIES )); do
    status=$(curl -s -o /dev/null -w '%{http_code}' --max-time "${TIMEOUT}" "${url}" 2>/dev/null || echo "000")
    if [[ "${status}" == "${expected_status}" ]]; then
      green "  ✓ ${name} — ${status}"
      return 0
    fi
    (( attempt++ ))
    if (( attempt < RETRIES )); then
      echo "  ↻ ${name} — got ${status}, retrying in ${RETRY_DELAY}s (${attempt}/${RETRIES})"
      sleep "${RETRY_DELAY}"
    fi
  done

  red "  ✗ ${name} — expected ${expected_status}, got ${status}"
  (( failures++ ))
}

echo "Smoke testing ${GATEWAY_URL} ..."
echo ""

echo "── Infrastructure ──"
check "Gateway actuator health" "${GATEWAY_URL}/actuator/health"

echo ""
echo "── Auth ──"
check "POST /auth/login (no body → 400)" "${GATEWAY_URL}/auth/login" "400"

echo ""
echo "── Buyer BFF ──"
check "GET /api/buyer/marketplace/products (public)" "${GATEWAY_URL}/api/buyer/marketplace/products"

echo ""
echo "── Seller BFF ──"
check "GET /api/seller/ (no auth → 401)" "${GATEWAY_URL}/api/seller/marketplace/products" "401"

echo ""
if (( failures > 0 )); then
  red "FAILED: ${failures} check(s) did not pass."
  exit 1
else
  green "All smoke checks passed."
fi
```

- [ ] **Step 2: Make it executable**

Run: `chmod +x scripts/smoke-test.sh`

- [ ] **Step 3: Test against a running Kind cluster (if available)**

Run: `bash scripts/smoke-test.sh`
Expected: Either all pass (cluster running) or all fail with `000` (cluster not running). Both are valid — the script itself should not error.

- [ ] **Step 4: Commit**

```bash
git add scripts/smoke-test.sh
git commit -m "feat(scripts): add smoke-test.sh for post-deploy verification

Checks gateway health, auth endpoint, buyer-bff (public), and
seller-bff (auth-required) via the Kind NodePort. Supports retries
and configurable GATEWAY_URL."
```

---

### Task 4: Makefile `e2e` and `deploy` targets

**Files:**
- Modify: `Makefile`

Add convenience targets that chain the workflow: build changed → load → deploy → smoke test.

- [ ] **Step 1: Add new targets to `Makefile`**

Append before the last empty line at end of file, and update `.PHONY`:

```makefile
e2e: ## Build changed images → load → deploy → smoke test (full local cycle)
	$(MAKE) build-changed
	$(MAKE) load-changed
	$(MAKE) kind-deploy
	@echo "Waiting 30s for pods to start..."
	@sleep 30
	./scripts/smoke-test.sh

build-changed: ## Build only Docker images for modules with changes
	./scripts/build-images.sh --changed -j 4

load-changed: ## Load only changed images into Kind cluster
	./scripts/load-images-kind.sh $(CLUSTER) --changed

smoke-test: ## Run smoke tests against the local Kind cluster
	./scripts/smoke-test.sh
```

Update `.PHONY` to include the new targets:

```makefile
.PHONY: help test build verify arch-test docs-install docs-build docs-start archetypes-install install-hooks local-checks local-checks-all kind-bootstrap kind-deploy build-images load-images e2e build-changed load-changed smoke-test
```

- [ ] **Step 2: Verify `make help` shows all new targets**

Run: `make help`
Expected: `e2e`, `build-changed`, `load-changed`, `smoke-test` all appear with descriptions.

- [ ] **Step 3: Commit**

```bash
git add Makefile
git commit -m "feat(make): add e2e, build-changed, load-changed, smoke-test targets

make e2e runs the full local cycle: incremental build → load → deploy → smoke.
Individual targets available for partial workflows."
```

---

### Task 5: Update `run-local-checks.sh` to detect Docker/K8s file changes

**Files:**
- Modify: `scripts/run-local-checks.sh`

Currently the script only triggers Maven or docs checks. Add detection for `docker/`, `k8s/`, `kind/` changes so it advises (prints a warning) when manifests changed but weren't redeployed. This is informational only — not blocking.

- [ ] **Step 1: Add K8s/Docker change detection after the `run_docs` loop**

In `scripts/run-local-checks.sh`, after the existing `for path in` loop (after line 64), add a `k8s_changed` flag and matching case:

Add this variable next to `run_maven=false` / `run_docs=false`:

```bash
k8s_changed=false
```

Add this case branch inside the `for path in` loop:

```bash
    docker/*|k8s/*|kind/*)
      k8s_changed=true
      ;;
```

Add this block after the Maven/docs check blocks (before the final success echo):

```bash
if [[ "${k8s_changed}" == "true" ]]; then
  echo ""
  echo "⚠  Docker/K8s manifest changes detected."
  echo "   Consider running: make e2e"
fi
```

- [ ] **Step 2: Test with a mock change to `k8s/apps/platform.yaml`**

Run: `echo "" >> k8s/apps/platform.yaml && bash scripts/run-local-checks.sh --since-main; git checkout k8s/apps/platform.yaml`
Expected: Warning message about Docker/K8s changes is printed.

- [ ] **Step 3: Commit**

```bash
git add scripts/run-local-checks.sh
git commit -m "feat(scripts): detect Docker/K8s manifest changes in local-checks

Prints advisory when docker/, k8s/, or kind/ files changed,
suggesting 'make e2e' to verify deployment."
```

---

## Phase 2: Tilt.dev Inner-Loop (Hot Reload)

### Task 6: Install Tilt and create initial Tiltfile

**Files:**
- Create: `Tiltfile`

Tilt watches source files, rebuilds images, and redeploys to Kind automatically. Start with 3 core services (api-gateway, buyer-bff, marketplace-service) to validate the workflow. Other services stay deployed via static manifests.

**Prerequisites:** `brew install tilt` (or `curl -fsSL https://raw.githubusercontent.com/tilt-dev/tilt/master/scripts/install.sh | bash`)

- [ ] **Step 1: Create `Tiltfile` in repo root**

```python
# -*- mode: Python -*-

# ── Config ───────────────────────────────────────────────────────────────────
# Services managed by Tilt (hot-reload on code change).
# All other services are deployed via static K8s manifests.
TILT_SERVICES = ['api-gateway', 'buyer-bff', 'marketplace-service']

ALL_MODULES = [
    'auth-server', 'api-gateway', 'buyer-bff', 'seller-bff',
    'profile-service', 'promotion-service', 'wallet-service',
    'marketplace-service', 'order-service', 'search-service',
    'notification-service', 'loyalty-service', 'activity-service',
    'webhook-service', 'subscription-service', 'buyer-portal',
]

# ── Infrastructure (static, apply once) ──────────────────────────────────────
k8s_yaml('k8s/infra/base.yaml')

# ── Application manifests (static) ──────────────────────────────────────────
# We apply the full platform.yaml; Tilt takes over image builds for
# TILT_SERVICES while leaving the rest as-is.
k8s_yaml('k8s/apps/platform.yaml')

# ── Tilt-managed service builds ─────────────────────────────────────────────
for svc in TILT_SERVICES:
    docker_build(
        'shop/' + svc + ':dev',
        context='.',
        dockerfile='docker/Dockerfile.module',
        build_args={'MODULE': svc},
        # Only trigger rebuild when this module or shared code changes
        only=[
            svc + '/',
            'shop-common/',
            'shop-contracts/',
            'pom.xml',
        ],
        # Live-update: sync jar into running container (skip full rebuild
        # when only the module jar changed). Falls back to full docker_build
        # if Dockerfile or pom.xml changes.
        live_update=[
            fall_back_on(['docker/Dockerfile.module', 'pom.xml']),
            sync(svc + '/target/' + svc + '-0.1.0-SNAPSHOT.jar', '/app/app.jar'),
            run('kill -HUP 1 || true'),  # signal JVM restart if using devtools
        ],
    )
    k8s_resource(svc, port_forwards=[], labels=['tilt-managed'])

# ── Static services: just label them for Tilt UI grouping ───────────────────
STATIC_MODULES = [m for m in ALL_MODULES if m not in TILT_SERVICES]
for svc in STATIC_MODULES:
    k8s_resource(svc, labels=['static'])

# ── Port forwards (gateway is the main entry) ───────────────────────────────
# Kind NodePort 30080→8080 already handles this, but Tilt can add extras:
k8s_resource('api-gateway', port_forwards=['8080:8080'], labels=['tilt-managed'])
```

- [ ] **Step 2: Verify Tilt can parse the file**

Run: `tilt ci --only=api-gateway 2>&1 | head -20` (or `tilt up` interactively)
Expected: Tilt starts, builds api-gateway image, applies manifests.

Note: `tilt ci` runs headless and exits after initial deploy — good for verification. `tilt up` opens the dashboard for interactive use.

- [ ] **Step 3: Add Tilt targets to Makefile**

Append to `Makefile`:

```makefile
tilt-up: ## Start Tilt for inner-loop development (hot-reload)
	tilt up

tilt-ci: ## Run Tilt in CI mode (build + deploy + exit)
	tilt ci
```

Update `.PHONY`:

```makefile
.PHONY: help test build verify arch-test docs-install docs-build docs-start archetypes-install install-hooks local-checks local-checks-all kind-bootstrap kind-deploy build-images load-images e2e build-changed load-changed smoke-test tilt-up tilt-ci
```

- [ ] **Step 4: Add Tilt cache to `.gitignore`**

Append to `.gitignore` (if not already there):

```
# Tilt
.tilt/
tilt_modules/
```

- [ ] **Step 5: Commit**

```bash
git add Tiltfile Makefile .gitignore
git commit -m "feat: add Tiltfile for inner-loop hot-reload development

Tilt manages api-gateway, buyer-bff, marketplace-service with
live-update. Other services deployed via static K8s manifests.
Run with: make tilt-up"
```

---

### Task 7: Local container registry for faster image cycling

**Files:**
- Modify: `kind/cluster-config.yaml`
- Create: `scripts/setup-local-registry.sh`

`kind load docker-image` is slow for large images. A local registry at `localhost:5000` lets `docker push` + Kind pull work faster, and is required for Tilt's `docker_build` to push efficiently.

- [ ] **Step 1: Create `scripts/setup-local-registry.sh`**

```bash
#!/usr/bin/env bash
set -euo pipefail

# Creates a local Docker registry at localhost:5000 and connects it
# to the Kind cluster network. Idempotent — safe to run multiple times.

REGISTRY_NAME="kind-registry"
REGISTRY_PORT=5000
CLUSTER_NAME="${1:-shop-kind}"

# Start registry if not running
if ! docker inspect "${REGISTRY_NAME}" >/dev/null 2>&1; then
  echo "==> Starting local registry at localhost:${REGISTRY_PORT}"
  docker run -d --restart=always \
    -p "127.0.0.1:${REGISTRY_PORT}:5000" \
    --network bridge \
    --name "${REGISTRY_NAME}" \
    registry:2
else
  echo "==> Registry '${REGISTRY_NAME}' already running."
fi

# Connect registry to Kind network (if not already connected)
if ! docker network inspect kind | grep -q "${REGISTRY_NAME}"; then
  echo "==> Connecting registry to Kind network"
  docker network connect kind "${REGISTRY_NAME}" 2>/dev/null || true
fi

# Document the registry for Kind nodes
# See: https://kind.sigs.k8s.io/docs/user/local-registry/
cat <<EOF | kubectl apply -f -
apiVersion: v1
kind: ConfigMap
metadata:
  name: local-registry-hosting
  namespace: kube-public
data:
  localRegistryHosting.v1: |
    host: "localhost:${REGISTRY_PORT}"
    help: "https://kind.sigs.k8s.io/docs/user/local-registry/"
EOF

echo "==> Local registry ready: localhost:${REGISTRY_PORT}"
echo "    Push:  docker tag shop/foo:dev localhost:${REGISTRY_PORT}/shop/foo:dev && docker push localhost:${REGISTRY_PORT}/shop/foo:dev"
```

- [ ] **Step 2: Make executable**

Run: `chmod +x scripts/setup-local-registry.sh`

- [ ] **Step 3: Add `containerdConfigPatches` to `kind/cluster-config.yaml`**

Add at the end of `kind/cluster-config.yaml`:

```yaml
containerdConfigPatches:
  - |-
    [plugins."io.containerd.grpc.v1.cri".registry.mirrors."localhost:5000"]
      endpoint = ["http://kind-registry:5000"]
```

Note: This only takes effect on cluster creation. Existing clusters need recreation (`kind delete cluster && make kind-bootstrap`).

- [ ] **Step 4: Add Makefile target**

Append to `Makefile`:

```makefile
registry: ## Start local Docker registry at localhost:5000
	./scripts/setup-local-registry.sh $(CLUSTER)
```

Update `.PHONY` to include `registry`.

- [ ] **Step 5: Commit**

```bash
git add scripts/setup-local-registry.sh kind/cluster-config.yaml Makefile
git commit -m "feat: add local Docker registry for faster Kind image cycling

scripts/setup-local-registry.sh starts localhost:5000 registry and
connects it to the Kind network. kind/cluster-config.yaml updated
with containerd mirror config (requires cluster recreation)."
```

---

## Phase 3: ArgoCD GitOps (Optional, Deferred)

> This phase is only needed when you want declarative sync, drift detection,
> or multi-environment promotion. The current `kubectl apply` workflow is
> sufficient for single-developer local development.

### Task 8: ArgoCD non-HA deployment

**Files:**
- Create: `k8s/infra/argocd.yaml`

Deploy ArgoCD in non-HA (single-replica) mode, suitable for local Kind.

- [ ] **Step 1: Create `k8s/infra/argocd.yaml`**

```yaml
# ArgoCD non-HA install for local Kind development.
# Source: https://raw.githubusercontent.com/argoproj/argo-cd/stable/manifests/install.yaml
# Applied via: kubectl apply -n argocd -f k8s/infra/argocd.yaml
#
# We do NOT vendor the full manifest here. Instead, the bootstrap script
# fetches it directly. This file serves as documentation and as a place to
# layer local patches if needed.
#
# After install, create the Application CRD:
#   kubectl apply -f k8s/infra/argocd-app.yaml
---
apiVersion: v1
kind: Namespace
metadata:
  name: argocd
```

- [ ] **Step 2: Create ArgoCD Application CRD pointing to same repo**

Create `k8s/infra/argocd-app.yaml`:

```yaml
# ArgoCD Application that syncs k8s/apps/platform.yaml from this repo.
# ArgoCD watches the same repo (no manifest split needed for single-team).
apiVersion: argoproj.io/v1alpha1
kind: Application
metadata:
  name: shop-platform
  namespace: argocd
spec:
  project: default
  source:
    # Use local path when repo is on the Kind node, or a Git URL for remote.
    # For local dev, ArgoCD can watch a Git repo URL.
    repoURL: https://github.com/meirongdev/shop.git
    targetRevision: HEAD
    path: k8s/apps
  destination:
    server: https://kubernetes.default.svc
    namespace: shop
  syncPolicy:
    automated:
      prune: true
      selfHeal: true
    syncOptions:
      - CreateNamespace=true
```

- [ ] **Step 3: Create ArgoCD bootstrap script**

Create `scripts/argocd-bootstrap.sh`:

```bash
#!/usr/bin/env bash
set -euo pipefail

echo "==> Installing ArgoCD (non-HA) into Kind cluster"
kubectl apply -f k8s/infra/argocd.yaml
kubectl apply -n argocd -f https://raw.githubusercontent.com/argoproj/argo-cd/stable/manifests/install.yaml

echo "==> Waiting for ArgoCD server to be ready..."
kubectl wait --for=condition=available --timeout=120s deployment/argocd-server -n argocd

echo "==> Applying shop-platform Application"
kubectl apply -f k8s/infra/argocd-app.yaml

echo ""
echo "ArgoCD is ready."
echo "  Dashboard: kubectl port-forward svc/argocd-server -n argocd 9443:443"
echo "  Username:  admin"
echo "  Password:  kubectl -n argocd get secret argocd-initial-admin-secret -o jsonpath='{.data.password}' | base64 -d"
```

- [ ] **Step 4: Make executable and add Makefile target**

Run: `chmod +x scripts/argocd-bootstrap.sh`

Append to `Makefile`:

```makefile
argocd-bootstrap: ## Install ArgoCD (non-HA) and create shop-platform Application
	./scripts/argocd-bootstrap.sh
```

Update `.PHONY` to include `argocd-bootstrap`.

- [ ] **Step 5: Commit**

```bash
git add k8s/infra/argocd.yaml k8s/infra/argocd-app.yaml scripts/argocd-bootstrap.sh Makefile
git commit -m "feat: add ArgoCD non-HA setup for optional GitOps sync

ArgoCD watches k8s/apps/ in the same repo (no manifest split).
Install with: make argocd-bootstrap
Dashboard: kubectl port-forward svc/argocd-server -n argocd 9443:443"
```

---

### Task 9: Kustomize overlays for environment separation

**Files:**
- Create: `k8s/apps/base/kustomization.yaml`
- Create: `k8s/apps/overlays/dev/kustomization.yaml`
- Modify: `k8s/infra/argocd-app.yaml` (point to overlay)

Restructure `k8s/apps/` to use Kustomize overlays, enabling future staging/prod differentiation while keeping dev as the default.

- [ ] **Step 1: Create base Kustomization**

Create `k8s/apps/base/kustomization.yaml`:

```yaml
apiVersion: kustomize.config.k8s.io/v1beta1
kind: Kustomization
resources:
  - ../platform.yaml
```

- [ ] **Step 2: Create dev overlay**

Create `k8s/apps/overlays/dev/kustomization.yaml`:

```yaml
apiVersion: kustomize.config.k8s.io/v1beta1
kind: Kustomization
resources:
  - ../../base
patches:
  # Dev-specific: single replica, relaxed resource limits
  - target:
      kind: Deployment
      namespace: shop
    patch: |
      - op: replace
        path: /spec/replicas
        value: 1
```

- [ ] **Step 3: Verify Kustomize build works**

Run: `kubectl kustomize k8s/apps/overlays/dev | head -30`
Expected: Rendered YAML with `replicas: 1` on all Deployments.

- [ ] **Step 4: Update ArgoCD Application to use overlay path**

In `k8s/infra/argocd-app.yaml`, change `path: k8s/apps` to:

```yaml
    path: k8s/apps/overlays/dev
```

- [ ] **Step 5: Update `deploy-kind.sh` to use Kustomize**

Replace `scripts/deploy-kind.sh`:

```bash
#!/usr/bin/env bash
set -euo pipefail

overlay="${1:-dev}"
echo "==> Deploying overlay: ${overlay}"
kubectl apply -k "k8s/apps/overlays/${overlay}"
```

- [ ] **Step 6: Commit**

```bash
git add k8s/apps/base/ k8s/apps/overlays/ k8s/infra/argocd-app.yaml scripts/deploy-kind.sh
git commit -m "feat: add Kustomize overlays for multi-environment support

k8s/apps/base/ wraps platform.yaml; k8s/apps/overlays/dev/ applies
dev-specific patches (single replica). deploy-kind.sh now uses
kubectl apply -k. ArgoCD Application points to dev overlay."
```

---

## Summary of Deliverables

| Phase | Task | What It Does | Key Command |
|-------|------|--------------|-------------|
| 1 | Task 1 | Incremental + parallel image builds | `make build-changed` |
| 1 | Task 2 | Incremental image loading into Kind | `make load-changed` |
| 1 | Task 3 | Smoke tests after deploy | `make smoke-test` |
| 1 | Task 4 | One-command E2E cycle | `make e2e` |
| 1 | Task 5 | Local-checks warns on K8s changes | `make local-checks` |
| 2 | Task 6 | Tilt hot-reload for 3 core services | `make tilt-up` |
| 2 | Task 7 | Local registry for fast image push | `make registry` |
| 3 | Task 8 | ArgoCD GitOps sync (optional) | `make argocd-bootstrap` |
| 3 | Task 9 | Kustomize overlays (dev/staging) | `kubectl apply -k k8s/apps/overlays/dev` |
