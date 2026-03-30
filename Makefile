SHELL := /bin/bash
.SHELLFLAGS := -eu -o pipefail -c
.DEFAULT_GOAL := help

MVNW := ./mvnw
DOCS_DIR := docs-site
DOCS_STAMP := $(DOCS_DIR)/node_modules/.package-lock-stamp
CLUSTER ?= shop-kind
OVERLAY ?= dev
TILT_REGISTRY ?= localhost:5000
ARCHETYPE_MODULES := shop-common,shop-contracts,shop-archetypes/gateway-service-archetype,shop-archetypes/auth-service-archetype,shop-archetypes/bff-service-archetype,shop-archetypes/domain-service-archetype,shop-archetypes/event-worker-archetype,shop-archetypes/portal-service-archetype

.PHONY: help test build verify arch-test docs-install docs-build docs-start archetypes-install install-hooks local-checks local-checks-all platform-validate kind-bootstrap kind-deploy build-images build-images-legacy load-images load-images-legacy build-changed load-changed redeploy smoke-test ui-e2e e2e-playwright e2e-playwright-seller local-access e2e e2e-legacy registry tilt-up tilt-ci mirrord-run argocd-bootstrap kind-teardown clean-images clean-all

help: ## Show available developer commands
	@awk 'BEGIN {FS = ":.*## "; printf "\nUsage:\n  make <target>\n\nTargets:\n"} /^[a-zA-Z0-9_.-]+:.*## / { printf "  %-20s %s\n", $$1, $$2 }' $(MAKEFILE_LIST)

test: ## Run full repository Maven tests
	$(MVNW) -q test

build: ## Build all Maven modules without tests
	$(MVNW) -q -DskipTests verify

verify: ## Run the local DX verification set (Maven verify + docs-site build)
	$(MVNW) -q verify
	$(MAKE) docs-build

arch-test: ## Run architecture-tests with required modules
	$(MVNW) -q -pl architecture-tests -am test

$(DOCS_STAMP): $(DOCS_DIR)/package-lock.json
	cd $(DOCS_DIR) && npm ci
	@mkdir -p $(dir $(DOCS_STAMP))
	@touch $(DOCS_STAMP)

docs-install: $(DOCS_STAMP) ## Install docs-site dependencies with npm ci

docs-build: $(DOCS_STAMP) ## Build the docs site
	cd $(DOCS_DIR) && npm run build

docs-start: $(DOCS_STAMP) ## Start the docs site locally
	cd $(DOCS_DIR) && npm run start

archetypes-install: ## Install archetypes into the local Maven repository
	$(MVNW) -q -pl $(ARCHETYPE_MODULES) -am install

install-hooks: ## Configure Git to use the repository-managed hooks
	./scripts/install-git-hooks.sh

local-checks: ## Run path-aware local checks against origin/main
	./scripts/run-local-checks.sh --since-main

local-checks-all: ## Run all local checks regardless of changed files
	./scripts/run-local-checks.sh --all

platform-validate: ## Validate shell/Kustomize/Tilt/mirrord platform assets
	bash ./scripts/validate-platform-assets.sh

kind-bootstrap: ## Create the Kind cluster and install infra dependencies
	./scripts/kind-up.sh $(CLUSTER)

kind-deploy: ## Apply platform manifests to the current cluster
	./scripts/deploy-kind.sh $(OVERLAY)

build-images: ## Build service images for local/Kind use (fast by default)
	./scripts/build-images.sh --fast

build-images-legacy: ## Build service images with the legacy Docker-in-Docker path
	./scripts/build-images.sh --legacy

load-images: ## Sync built images into the Kind cluster (registry push by default)
	./scripts/load-images-kind.sh $(CLUSTER) --registry

load-images-legacy: ## Load built images into the Kind cluster with kind load
	./scripts/load-images-kind.sh $(CLUSTER) --kind-load

build-changed: ## Build only Docker images for changed modules
	./scripts/build-images.sh --changed -j 4

load-changed: ## Load only Docker images for changed modules into CLUSTER
	./scripts/load-images-kind.sh $(CLUSTER) --changed

redeploy: ## Rebuild, reload, and restart a single MODULE (usage: make redeploy MODULE=buyer-bff)
	@test -n "$(MODULE)" || (echo "Usage: make redeploy MODULE=<service-name>" >&2 && false)
	./scripts/build-images.sh --fast --module $(MODULE)
	./scripts/load-images-kind.sh $(CLUSTER) --registry --module $(MODULE)
	kubectl --context kind-$(CLUSTER) -n shop rollout restart deployment/$(MODULE)
	kubectl --context kind-$(CLUSTER) -n shop rollout status deployment/$(MODULE) --timeout=300s

smoke-test: ## Run smoke tests against the local gateway entrypoint
	./scripts/smoke-test.sh

ui-e2e: ## Run buyer SSR and seller KMP page automation checks
	bash ./scripts/ui-e2e.sh

e2e-playwright: ## Run Playwright buyer tests (requires: make local-access running)
	cd e2e-tests && npx playwright test --project=buyer

e2e-playwright-seller: ## Build seller WASM, start proxy, run Playwright seller tests
	cd kmp && ./gradlew :seller-app:wasmJsBrowserDevelopmentExecutableDistribution
	node scripts/seller-web-proxy.mjs kmp/seller-app/build/dist/wasmJs/developmentExecutable http://127.0.0.1:18080 18181 &
	cd e2e-tests && npx playwright test --project=seller

local-access: ## Open stable local access to gateway, Mailpit, and Prometheus via port-forward
	./scripts/local-access.sh

e2e: ## Bootstrap Kind, run fast local build/deploy, and verify buyer/seller flows
	bash ./scripts/e2e.sh $(CLUSTER) $(OVERLAY)

e2e-legacy: ## Run the legacy Docker build + kind load loop
	E2E_FLOW=legacy bash ./scripts/e2e.sh $(CLUSTER) $(OVERLAY)

registry: ## Start a local Docker registry for faster Kind/Tilt image cycles
	./scripts/setup-local-registry.sh $(CLUSTER)

tilt-up: ## Start Tilt for inner-loop Kubernetes development
	tilt up -- --registry=$(TILT_REGISTRY)

tilt-ci: ## Run Tilt once in CI/headless mode
	tilt ci -- --registry=$(TILT_REGISTRY)

mirrord-run: ## Run a local module through mirrord (usage: make mirrord-run MODULE=api-gateway)
	bash ./scripts/mirrord-debug.sh $(MODULE)

argocd-bootstrap: ## Install ArgoCD (non-HA) and apply the shop-platform Application
	./scripts/argocd-bootstrap.sh

kind-teardown: ## Delete the Kind cluster and remove its kubeconfig context
	./kind/teardown.sh $(CLUSTER)

clean-images: ## Remove all local shop/* dev Docker images
	docker images --format '{{.Repository}}:{{.Tag}}' | grep '^localhost:5000/shop/' | xargs -r docker rmi --force || true
	docker images --format '{{.Repository}}:{{.Tag}}' | grep '^shop/' | xargs -r docker rmi --force || true

clean-all: kind-teardown clean-images ## Destroy the Kind cluster and delete all local dev images
