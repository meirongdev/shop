# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Build & Test Commands

```bash
# Build
make build                          # Build all Maven modules (skip tests)
make verify                         # Maven verify + docs-site build

# Test
make test                           # All Maven tests
make arch-test                      # Architecture tests (ArchUnit, 19 rules)
./mvnw -pl services/<module> -am test        # Single module + required dependencies
./mvnw -pl services/auth-server -am -Dtest=AuthControllerTest test                        # Single test class
./mvnw -pl services/auth-server -am -Dtest=AuthControllerTest#me_withValidJwt_returnsUserInfo test  # Single test method

# Playwright E2E tests (frontend/e2e-tests/ directory, requires running cluster + port-forward)
make local-access &                 # Port-forward gateway→18080, Grafana→13000 (keep running)
make e2e-playwright                 # Run buyer portal Playwright tests
make e2e-playwright-seller          # Build seller WASM + run seller Playwright tests
make e2e-playwright-buyer-app       # Build buyer-app WASM + run buyer-app Playwright tests
make e2e-playwright-kmp             # Build all KMP WASM + run all KMP Playwright tests
cd e2e-tests && npx playwright test # Run all Playwright tests
cd e2e-tests && npx playwright show-report  # Open HTML test report
make verify-observability           # Verify Grafana/Prometheus/Loki/Tempo are healthy

# Code quality (no dedicated lint plugin; ArchUnit enforces rules)
make local-checks                   # Path-aware pre-PR checks (vs origin/main)
make local-checks-all               # All local checks regardless of changed files
make install-hooks                  # Install Git pre-commit hooks

# Docs site (Docusaurus)
make docs-start                     # Serve locally
make docs-build                     # Production build

# Local deployment (Kind)
make kind-bootstrap                 # Create Kind cluster + infra
make build-images && make load-images && make kind-deploy
make platform-validate             # Validate shell/Kustomize/Tilt/mirrord assets
make mirrord-run MODULE=api-gateway # Run a local process against a remote Kind deployment
```

## High-Level Architecture

Maven multi-module microservices platform. Java 25 + Spring Boot 3.5.11 + Spring Cloud 2025.0.1. KMP (Kotlin Multiplatform) modules use Gradle.

### Service Topology

```
Client
  └→ api-gateway:8080  (Spring Cloud Gateway MVC, JWT validation, rate limiting)
       ├→ /auth/**             → auth-server
       ├→ /buyer/**            → buyer-portal  (Kotlin + Thymeleaf SSR)
       ├→ /buyer-app/**        → buyer-app     (KMP/Compose WASM — nginx static)
       ├→ /seller/**           → seller-portal (KMP/Compose WASM — nginx static)
       ├→ /api/buyer/**        → buyer-bff     (aggregates: marketplace, order, wallet, promotion, loyalty, activity, search)
       ├→ /api/seller/**       → seller-bff    (aggregates: profile, marketplace, order, promotion, subscription)
       ├→ /api/loyalty/**      → loyalty-service
       ├→ /api/activity/**     → activity-service
       ├→ /api/webhook/**      → webhook-service
       └→ /api/subscription/** → subscription-service
```

Canary routes for `/api/buyer/**` and `/api/seller/**` are declared **before** stable routes and divert traffic to `buyer-bff-v2` / `seller-bff-v2` based on the Canary filter.

### BFF Layer
BFFs call domain services via `RestClient` (backed by `JdkClientHttpRequestFactory`) with virtual threads enabled. Non-critical downstream calls (promotion, loyalty) use Resilience4j `CircuitBreaker` with fallbacks. Critical paths (marketplace, order) fail fast.

### Async / Event-Driven (Transactional Outbox)
Services write business data + outbox record atomically. A 5-second scheduler emits to Kafka. Consumers apply idempotently via Bloom Filter checks. Key topics:
- `wallet.transactions.v1`, `order.events.v1`, `buyer.registered.v1`
- `marketplace.product.events.v1`, `loyalty.checkin.v1`, `activity.prize.won.v1`

### Database per Service
Each domain service owns its MySQL schema and Flyway migrations (`src/main/resources/db/migration/`). JPA DDL mode is `validate`—never `update`.

### Language Split
- **Java**: `services/api-gateway`, `services/auth-server`, BFFs, all domain services, `shared/shop-common`, `shared/shop-contracts`
- **Kotlin**: `buyer-portal` (Spring Boot + Thymeleaf SSR)
- **Kotlin Multiplatform / Compose Multiplatform**: `kmp/seller-app` (Web WASM / Android / iOS → served at `/seller/**`), `kmp/buyer-app` (Web WASM → served at `/buyer-app/**`), `kmp/buyer-android-app`, plus shared `kmp/core`, `kmp/ui-shared`, and six `kmp/feature-*` modules

## Key Conventions

### Contracts & Responses
- **API path constants** live in `shared/shop-contracts/contracts/api/*Api.java`—use them in both controllers and `@HttpExchange` clients.
- **Event DTOs** live in `shared/shop-contracts/contracts/event/`.
- All controllers return `ApiResponse<T>` from `shared/shop-common`; error codes are `SC_*` constants also from `shared/shop-contracts`.
- Error responses follow RFC 7807 Problem Details; global exception handling is in `shared/shop-common`.

### Internal Trust Model
- Gateway injects trusted headers on every request: `X-Request-Id`, `X-Buyer-Id` (= JWT `principalId` claim), `X-Username`, `X-Roles`, `X-Portal`, `X-Internal-Token`.
- Domain services enforce `InternalAccessFilter` (from `shared/shop-common`) when `shop.security.internal.enabled=true`.
- BFF-to-service calls always include `X-Internal-Token`.

### Configuration
- Use `@ConfigurationProperties(prefix = "shop...")` records for typed config, not `@Value`.
- `application.yml` values use `${ENV_VAR:default}` for 12-factor compatibility.
- App port: `8080`. Management/actuator port: `8081`.
- Observability: Prometheus endpoint, OTLP tracing to `otel-collector:4318`, logstash JSON console format.

### Testing
- Integration tests extend `AbstractMySqlIntegrationTest` (Testcontainers `@ServiceConnection`).
- HTTP client stubs use WireMock.
- Architecture rules are enforced via ArchUnit (`architecture-tests` module, 19 rules). Notable rules: no field injection, no `RestTemplate`, no `System.out/err`, Kafka listeners must be idempotent.
- **Playwright E2E tests** live in `frontend/e2e-tests/` (Node.js, `@playwright/test`). Three projects:
  - `buyer` — tests covering the buyer portal SSR (login, guest mode, all authenticated pages).
  - `seller` — tests covering the KMP/WASM seller app via e2e token injection, **plus seller portal gateway SPA shell checks** at `/seller/`.
  - `buyer-app` — tests covering the KMP/WASM buyer app via e2e token injection (auth, guest, routes), **plus buyer-app gateway SPA shell checks** at `/buyer-app/`.
  - Requires gateway on port 18080 (`make local-access &`) and seller proxy on 18181 for seller tests.
- **`platform/scripts/verify-observability.sh`** — automated validation of Grafana, Prometheus, Loki, and Tempo health and datasource connectivity. Run with `make verify-observability`.

### K8s / Deployment Notes
- All deployments include a `startupProbe` (failureThreshold=30, periodSeconds=10 → 5-minute startup budget) to prevent liveness probe kills during slow JVM/WASM startup.
- All services with Flyway migrations set `spring.flyway.repair-on-migrate: true` to auto-repair failed migrations on startup.

### ArchUnit Rules
Run `make arch-test` to verify all 19 rules. Rules cover: encoding, layering, naming conventions, Spring best practices, and Kafka idempotency guards. See `docs/ARCHUNIT-RULES.md` for the full list.
