SHELL := /bin/bash
.SHELLFLAGS := -eu -o pipefail -c
.DEFAULT_GOAL := help

MVNW := ./mvnw
DOCS_DIR := docs-site
DOCS_STAMP := $(DOCS_DIR)/node_modules/.package-lock-stamp
CLUSTER ?= shop-kind
ARCHETYPE_MODULES := shop-common,shop-contracts,shop-archetypes/gateway-service-archetype,shop-archetypes/auth-service-archetype,shop-archetypes/bff-service-archetype,shop-archetypes/domain-service-archetype,shop-archetypes/event-worker-archetype,shop-archetypes/portal-service-archetype

.PHONY: help test build verify arch-test docs-install docs-build docs-start archetypes-install install-hooks local-checks local-checks-all kind-bootstrap kind-deploy build-images build-images-legacy load-images load-images-legacy e2e e2e-legacy

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

kind-bootstrap: ## Create the Kind cluster and install infra dependencies
	./scripts/deploy-kind.sh $(CLUSTER)

kind-deploy: ## Apply platform manifests to the current cluster
	./scripts/kind-up.sh

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
