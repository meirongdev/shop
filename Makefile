SHELL := /bin/bash
.SHELLFLAGS := -eu -o pipefail -c
.DEFAULT_GOAL := help

# Use standard Maven wrapper.
MVNW := ./mvnw
DOCS_DIR := docs-site
DOCS_STAMP := $(DOCS_DIR)/node_modules/.package-lock-stamp
CLUSTER ?= shop-kind
OVERLAY ?= dev
TILT_REGISTRY ?= localhost:5000
ARCHETYPE_MODULES := shared/shop-common,shared/shop-contracts,tooling/shop-archetypes/gateway-service-archetype,tooling/shop-archetypes/auth-service-archetype,tooling/shop-archetypes/bff-service-archetype,tooling/shop-archetypes/domain-service-archetype,tooling/shop-archetypes/event-worker-archetype,tooling/shop-archetypes/portal-service-archetype

.PHONY: help test build verify arch-test archetype-install archetype-test docs-install docs-build docs-start archetypes-install install-deps install-hooks local-checks local-checks-all platform-validate kind-bootstrap kind-deploy build-images build-changed load-images load-changed redeploy smoke-test verify-observability ui-e2e e2e-playwright e2e-playwright-seller e2e-playwright-buyer-app e2e-playwright-kmp local-access e2e registry tilt-up tilt-ci mirrord-run argocd-bootstrap kind-teardown clean-images clean-all

help: ## Show available developer commands
	@awk 'BEGIN {FS = ":.*## "; printf "\nUsage:\n  make <target>\n\nTargets:\n"} /^[a-zA-Z0-9_.-]+:.*## / { printf "  %-20s %s\n", $$1, $$2 }' $(MAKEFILE_LIST)

install-deps: ## Check and install required dependencies for e2e
	bash platform/scripts/install-deps.sh

test: ## Run full repository Maven tests
	$(MVNW) -q test

build: ## Build all Maven modules without tests
	$(MVNW) -q -DskipTests verify

verify: ## Run the local DX verification set (Maven verify + docs-site build)
	$(MVNW) -q verify
	$(MAKE) docs-build

arch-test: ## Run architecture-tests with required modules
	$(MVNW) -q -pl tooling/architecture-tests -am test

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

archetype-test: ## Run archetype generation and integration tests
	@echo "Running archetype generation tests..."
	bash platform/scripts/test-archetypes.sh

install-hooks: ## Configure Git to use the repository-managed hooks
	platform/scripts/install-git-hooks.sh

local-checks: ## Run path-aware local checks against origin/main
	platform/scripts/run-local-checks.sh --since-main

local-checks-all: ## Run all local checks regardless of changed files
	platform/scripts/run-local-checks.sh --all

platform-validate: ## Validate shell/Kustomize/Tilt/mirrord platform assets
	bash platform/scripts/validate-platform-assets.sh

kind-bootstrap: ## Create the Kind cluster and install infra dependencies
	platform/scripts/kind-up.sh $(CLUSTER)

kind-deploy: ## Apply platform manifests to the current cluster
	platform/scripts/deploy-kind.sh $(OVERLAY)

build-images: ## Build service images for local/Kind use
	platform/scripts/build-images.sh

load-images: ## Sync built images into the Kind cluster via local registry
	platform/scripts/load-images-kind.sh $(CLUSTER) --registry

build-changed: ## Build only Docker images for changed modules
	platform/scripts/build-images.sh --changed -j 4

load-changed: ## Load only Docker images for changed modules into CLUSTER
	platform/scripts/load-images-kind.sh $(CLUSTER) --changed

redeploy: ## Rebuild, reload, and restart a single MODULE (usage: make redeploy MODULE=buyer-bff)
	@test -n "$(MODULE)" || (echo "Usage: make redeploy MODULE=<service-name>" >&2 && false)
	platform/scripts/build-images.sh --fast --module $(MODULE)
	platform/scripts/load-images-kind.sh $(CLUSTER) --registry --module $(MODULE)
	kubectl --context kind-$(CLUSTER) -n shop rollout restart deployment/$(MODULE)
	kubectl --context kind-$(CLUSTER) -n shop rollout status deployment/$(MODULE) --timeout=300s

smoke-test: ## Run smoke tests against the local gateway entrypoint
	platform/scripts/smoke-test.sh

verify-observability: ## Verify Grafana, Prometheus, Loki, and Tempo are healthy
	platform/scripts/verify-observability.sh

ui-e2e: ## Run buyer SSR and seller/buyer KMP page automation checks
	bash platform/scripts/ui-e2e.sh

e2e-playwright: ## Run Playwright buyer tests (requires: make local-access running)
	cd frontend/e2e-tests && npx playwright test --project=buyer

e2e-playwright-seller: ## Build seller WASM, start proxy, run Playwright seller tests
	bash platform/scripts/kmp-e2e.sh --seller

e2e-playwright-buyer-app: ## Build buyer-app WASM, start proxy, run Playwright buyer-app tests
	bash platform/scripts/kmp-e2e.sh --buyer-app

e2e-playwright-kmp: ## Build all KMP WASM, start proxies, run all KMP Playwright tests
	bash platform/scripts/kmp-e2e.sh

local-access: ## Open stable local access to gateway, Mailpit, and Prometheus via port-forward
	platform/scripts/local-access.sh

e2e: ## Bootstrap Kind, build+push changed images, deploy, and verify buyer/seller flows
	bash platform/scripts/e2e.sh $(CLUSTER) $(OVERLAY)

registry: ## Start a local Docker registry for faster Kind/Tilt image cycles
	platform/scripts/setup-local-registry.sh $(CLUSTER)

tilt-up: ## Start Tilt for inner-loop Kubernetes development
	tilt up -- --registry=$(TILT_REGISTRY)

tilt-ci: ## Run Tilt once in CI/headless mode
	tilt ci -- --registry=$(TILT_REGISTRY)

mirrord-run: ## Run a local module through mirrord (usage: make mirrord-run MODULE=api-gateway)
	bash platform/scripts/mirrord-debug.sh $(MODULE)

argocd-bootstrap: ## Install ArgoCD (non-HA) and apply the shop-platform Application
	platform/scripts/argocd-bootstrap.sh

kind-teardown: ## Delete the Kind cluster and remove its kubeconfig context
	platform/kind/teardown.sh $(CLUSTER)

clean-images: ## Remove all local shop/* dev Docker images
	docker images --format '{{.Repository}}:{{.Tag}}' | grep '^localhost:5000/shop/' | xargs -r docker rmi --force || true
	docker images --format '{{.Repository}}:{{.Tag}}' | grep '^shop/' | xargs -r docker rmi --force || true

clean-all: kind-teardown clean-images ## Destroy the Kind cluster and delete all local dev images
