# Shop Platform - Project Context

## Project Overview

A **cloud-native microservices e-commerce platform** built on **Java 25 + Spring Boot 3.5.11 + Spring Cloud 2025.0.1 + Kotlin 2.3**. This is a technical validation platform for verifying a **Gateway + Thin BFF + Domain Service** architecture under a 2026 technology baseline.

### Role Separation Policy (Important Architecture Constraint)

**This platform enforces strict Buyer/Seller role separation. Sellers are prohibited from shopping.**

| Role | Capabilities | Entry Points | Restrictions |
|------|-------------|--------------|--------------|
| **Buyer** | Browse, cart, checkout, orders, loyalty, activities | `/buyer/**`, `/buyer-app/**`, `/api/buyer/**` | Shopping only |
| **Seller** | Product management, order fulfillment, promotions, wallet withdrawals | `/seller/**`, `/api/seller/**` | **No shopping allowed** |
| **Guest** | Browse, guest checkout (no registration) | `/buyer/**` (unauthenticated) | Guest paths only |

**Implementation details:**
- JWT carries a single role claim (`ROLE_BUYER` or `ROLE_SELLER`)
- Gateway routes to different BFFs: `/buyer/**` → buyer-bff, `/seller/**` → seller-bff
- Seller API contract (`SellerApi.java`) contains **no** cart, checkout, or purchasing endpoints
- Seller BFF exposes **no** shopping-related aggregation endpoints
- No role-switching mechanism exists; an account is exclusively Buyer or Seller

**Rationale:**
- Simplifies system boundaries and reduces test complexity
- Prevents seller privilege abuse (e.g., self-purchasing, order manipulation)
- Clear separation of duties for audit and compliance

### Core Architecture

```
Client
  └→ services/api-gateway:8080 (Spring Cloud Gateway MVC, JWT validation, rate limiting)
       ├→ /auth/**             → services/auth-server
       ├→ /buyer/**            → frontend/buyer-portal (Kotlin + Thymeleaf SSR)
       ├→ /buyer-app/**        → frontend/kmp/buyer-app (KMP WASM SPA)
       ├→ /seller/**           → frontend/kmp/seller-app (KMP WASM SPA)
       ├→ /api/buyer/**        → services/buyer-bff (aggregates domain services)
       ├→ /api/seller/**       → services/seller-bff (aggregates domain services)
       ├→ /api/loyalty/**      → services/loyalty-service
       ├→ /api/activity/**     → services/activity-service
       ├→ /api/webhook/**      → services/webhook-service
       └→ /api/subscription/** → services/subscription-service
```

### Technology Stack

| Layer | Technology |
|-------|------------|
| **JDK** | Java 25 |
| **Framework** | Spring Boot 3.5.11 |
| **Cloud** | Spring Cloud 2025.0.1 |
| **Kotlin** | 2.3.20 (for buyer-portal SSR) |
| **KMP** | Kotlin Multiplatform + Compose Multiplatform (buyer/seller apps) |
| **Build** | Maven (Java services) + Gradle (KMP modules) |
| **Database** | MySQL per service + Flyway migrations |
| **Cache/Coordination** | Redis (Redisson for distributed locks, Bloom filters) |
| **Event Bus** | Kafka with Transactional Outbox pattern |
| **Observability** | Actuator + Prometheus + OTLP tracing |

### Module Structure

| Module | Description |
|--------|-------------|
| `shared/shop-common` | Common response envelope, error model, internal auth filter |
| `shared/shop-contracts` | API path constants, DTOs, event contracts |
| `services/auth-server` | JWT authentication service |
| `services/api-gateway` | Unified routing, JWT validation, trusted headers injection |
| `services/buyer-bff` / `services/seller-bff` | Backend-for-Frontend aggregation layer |
| `frontend/buyer-portal` | Kotlin SSR portal (Thymeleaf) — SEO/guest-mode buyer pages |
| `frontend/kmp/buyer-app` | Buyer KMP WASM SPA — interactive shopping experience (`/buyer-app/`) |
| `frontend/kmp/seller-app` | Seller KMP WASM SPA — seller management app (`/seller/`) |
| `frontend/kmp/*` | Compose Multiplatform buyer/seller apps (WASM, Android, iOS) |
| `services/*-service` | Domain services: profile, promotion, wallet, marketplace, order, search, notification, loyalty, activity, subscription, webhook |
| Domain Services | `profile`, `promotion`, `wallet`, `marketplace`, `order`, `search`, `notification`, `loyalty`, `activity`, `subscription`, `webhook` |

### New Directory Structure (April 2026)

The repository has been restructured into logical domains:

```
shop/
├── shared/              # Shared libraries (shop-common, shop-contracts)
├── services/            # All backend microservices (15 modules)
├── frontend/            # Frontend applications (buyer-portal, KMP, e2e-tests)
├── platform/            # Infrastructure (k8s, kind, docker, scripts)
└── tooling/             # Build quality (architecture-tests, archetypes)
```

## Building and Running

### Prerequisites

- **JDK 25+** (Temurin recommended)
- **Maven 3.9+** (or use `./mvnw`)
- **Node.js 20+** (for docs-site, Playwright tests)
- **Kind** (Kubernetes in Docker) for local deployment
- **Docker** for container builds
- **kubectl** for cluster management

### Quick Start Commands

```bash
# Full local E2E: create Kind cluster + build + deploy + verify
make e2e

# Open stable local access to gateway, Mailpit, Prometheus
make local-access

# Default entry points after e2e:
# - Buyer Portal (SSR):  http://127.0.0.1:18080/buyer/login
# - Buyer App (KMP):     http://127.0.0.1:18080/buyer-app/
# - Seller Portal (KMP): http://127.0.0.1:18080/seller/
# - Gateway docs: http://127.0.0.1:18080/v3/api-docs/gateway
# - Mailpit:      http://127.0.0.1:18025
# - Prometheus:   http://127.0.0.1:19090
# - Grafana:      http://127.0.0.1:13000
```

### Common Development Workflows

| Task | Command |
|------|---------|
| Build all modules (skip tests) | `make build` |
| Run all tests | `make test` |
| Maven verify + docs build | `make verify` |
| Architecture tests (ArchUnit) | `make arch-test` |
| Build single module | `./mvnw -pl <module> -am package` |
| Run single test class | `./mvnw -pl <module> -am -Dtest=ClassName test` |
| Rebuild + redeploy one service | `make redeploy MODULE=buyer-bff` |
| Debug with mirrord | `make mirrord-run MODULE=api-gateway` |
| Tilt inner-loop dev | `make tilt-up` |
| Run Playwright E2E (buyer) | `make e2e-playwright` |
| Run Playwright E2E (seller) | `make e2e-playwright-seller` |
| Run Playwright E2E (buyer-app) | `make e2e-playwright-buyer-app` |
| Run Playwright E2E (all KMP) | `make e2e-playwright-kmp` |

### Kubernetes Deployment

```bash
# Bootstrap Kind cluster
make kind-bootstrap

# Build and load images
make build-images && make load-images

# Deploy to cluster
make kind-deploy

# Validate platform assets
make platform-validate
```

## Development Conventions

### Code Organization

- **API contracts** live in `shop-contracts/contracts/api/*Api.java` - use these constants in controllers and clients
- **Event DTOs** live in `shop-contracts/contracts/event/`
- All controllers return `ApiResponse<T>` from `shop-common`
- Error codes use `SC_*` constants from `shop-contracts`
- Error responses follow **RFC 7807 Problem Details**

### Configuration Patterns

- Use `@ConfigurationProperties(prefix = "shop...")` records for typed config (not `@Value`)
- `application.yml` uses `${ENV_VAR:default}` for 12-factor compatibility
- App port: `8080`, Management/actuator port: `8081`
- All services with Flyway set `spring.flyway.repair-on-migrate: true`

### Internal Trust Model

Gateway injects trusted headers on every request:
- `X-Request-Id`, `X-Buyer-Id` (JWT `principalId` claim), `X-Username`, `X-Roles`, `X-Portal`, `X-Internal-Token`
- Domain services enforce `InternalAccessFilter` (from `shop-common`) when `shop.security.internal.enabled=true`
- BFF-to-service calls always include `X-Internal-Token`

### Testing Practices

- Integration tests extend `AbstractMySqlIntegrationTest` (Testcontainers `@ServiceConnection`)
- HTTP client stubs use WireMock
- **ArchUnit rules** (19 rules in `architecture-tests` module) enforce:
  - No field injection
  - No `RestTemplate`
  - No `System.out/err`
  - Kafka listeners must be idempotent
- **Playwright E2E tests** in `e2e-tests/`:
  - `buyer` project: tests for buyer portal (SSR) + buyer-app (KMP WASM) shell
  - `seller` project: tests for KMP/WASM seller app + seller portal gateway shell

### Async/Event-Driven Architecture

Services use **Transactional Outbox** pattern:
1. Write business data + outbox record atomically
2. 5-second scheduler emits to Kafka
3. Consumers apply idempotently via Bloom Filter checks

Key topics: `wallet.transactions.v1`, `order.events.v1`, `buyer.registered.v1`, `marketplace.product.events.v1`, `loyalty.checkin.v1`, `activity.prize.won.v1`

### K8s Deployment Notes

- All deployments include `startupProbe` (failureThreshold=30, periodSeconds=10 → 5-minute startup budget)
- Redis is used for: gateway rate limiting, OTP, guest cart, anti-cheating, Bloom Filter idempotency, inventory coordination

## Demo Accounts

| Role | Username | Password |
|------|----------|----------|
| Buyer | buyer.demo | password |
| Buyer VIP | buyer.vip | password |
| Seller | seller.demo | password |

## Key Documentation

| Document | Description |
|----------|-------------|
| `docs/ENGINEERING-STANDARDS-2026.md` | 2026 unified tech stack & microservice scaffold standard |
| `docs/COMPATIBILITY-DEVELOPMENT-STANDARD-2026.md` | Version upgrade / compatibility norms |
| `docs/DEVELOPER-EXPERIENCE-STANDARD-2026.md` | CI, Makefile, git hooks, archetype workflow |
| `docs/ARCHUNIT-RULES.md` | 19 ArchUnit architecture rules |
| `docs/API-DOCUMENTATION-SPRINGDOC-2026.md` | OpenAPI documentation standard |
| `docs/TECH-STACK-BEST-PRACTICES-2026.md` | Technology stack adaptation & reuse guidelines |

## Git Hooks

Install pre-commit hooks for automated checks:

```bash
make install-hooks
```

## CI/CD

GitHub Actions workflow (`.github/workflows/ci.yml`) runs on push/PR:
- **platform-validate**: Validate shell/Kustomize/Tilt/mirrord assets
- **maven-verify**: Full Maven verify with JDK 25
- **docs-site**: Build docs-site production bundle

Path filters detect changes and skip unrelated jobs.
