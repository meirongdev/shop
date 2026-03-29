# Copilot Instructions for `shop-platform`

## Build, test, and lint commands

- Full multi-module build (root): `mvn clean verify`
- Full tests (root): `mvn test`
- Build without tests: `mvn -DskipTests verify`
- Build one module (+ required dependencies): `mvn -pl <module> -am clean package`
- Test one module (+ required dependencies): `mvn -pl <module> -am test`
- Run one test class: `mvn -pl auth-server -am -Dtest=AuthControllerTest test`
- Run one test method: `mvn -pl auth-server -am -Dtest=AuthControllerTest#me_withValidJwt_returnsUserInfo test`
- Docs site (Docusaurus): `cd docs-site && npm install && npm run start`
- Docs production build: `cd docs-site && npm run build`

Lint/static-analysis note: there is no dedicated Maven lint plugin configured (no Checkstyle/SpotBugs/PMD/ktlint/Spotless in module poms).

## High-level architecture

- Maven multi-module microservices platform. Root `pom.xml` aggregates shared libraries plus service modules.
- `api-gateway` is the edge (Spring Cloud Gateway MVC). It routes:
  - `/auth/**` → `auth-server`
  - `/buyer/**` → `buyer-portal`
  - `/api/buyer/**` → `buyer-bff` (with `stripPrefix(1)`)
  - `/api/seller/**` → `seller-bff` (with `stripPrefix(1)`)
  - `/api/loyalty/**` → `loyalty-service` (with `stripPrefix(1)`)
  - `/api/activity/**` → `activity-service` (with `stripPrefix(1)`)
  - `/api/webhook/**` → `webhook-service` (with `stripPrefix(1)`)
  - `/api/subscription/**` → `subscription-service` (with `stripPrefix(1)`)
  - Canary routes for `/api/buyer/**` and `/api/seller/**` divert traffic to `buyer-bff-v2` / `seller-bff-v2` based on Canary filter (must be declared before stable routes).
- `auth-server` issues JWTs; gateway validates JWT for `/api/**`.
- BFFs (`buyer-bff`, `seller-bff`) aggregate downstream domain services via `RestClient` (backed by `JdkClientHttpRequestFactory`), with virtual threads enabled.
- Domain services own their own MySQL schema and Flyway migrations: `profile`, `promotion`, `wallet`, `marketplace`, `order`, `loyalty`, `activity`, `subscription`.
- Event flow uses the Transactional Outbox pattern: services write business data + outbox record atomically, a 5-second scheduled publisher emits to Kafka, consumers apply idempotently. Key topics: `wallet.transactions.v1`, `order.events.v1`, `buyer.registered.v1`, `marketplace.product.events.v1`, `loyalty.checkin.v1`, `activity.prize.won.v1`.
- `buyer-portal` is a Kotlin + Thymeleaf SSR app calling gateway APIs (`/api` + contract paths); kept for SEO and guest-mode buyer pages.
- Seller UI is handled by the **KMP `seller-app`** (Kotlin Multiplatform + Compose Multiplatform), targeting Web WASM, Android, and iOS; it calls `seller-bff` via `/api/seller/**`.
- Buyer UI is handled by the **KMP `buyer-app`** (Compose WASM) and **`buyer-android-app`** (Android native); shared logic lives in KMP feature modules (`feature-auth`, `feature-cart`, `feature-marketplace`, `feature-order`, `feature-profile`, `feature-promotion`, `feature-wallet`) and `core`/`ui-shared`.

## Key repository conventions

- API contracts are centralized in `shop-contracts`:
  - Path constants live in `contracts/api/*Api.java` (for controllers and clients).
  - Shared event envelope/DTOs live in `contracts/event/*`.
- Standard response envelope is `ApiResponse<T>` from `shop-common`; services return `ApiResponse.success(...)` and rely on `shop-common` global exception handling for `SC_*` error codes.
- Internal service trust model:
  - Gateway injects trusted headers (`X-Request-Id`, `X-Buyer-Id`, `X-Username`, `X-Roles`, `X-Portal`, `X-Internal-Token`). Note: `X-Buyer-Id` carries the authenticated buyer's principal ID (sourced from the `principalId` JWT claim).
  - Service-side `InternalAccessFilter` (from `shop-common`) validates `X-Internal-Token` when `shop.security.internal.enabled=true`.
  - `/actuator` is typically excluded from internal-token filtering.
- Config pattern:
  - Use `@ConfigurationProperties(prefix = "shop...")` records for typed config.
  - `application.yml` values map to env vars with defaults (`${ENV_VAR:default}`), especially service URLs and security tokens.
- Runtime/ops baseline across services:
  - App port `8080`, management/actuator port `8081`.
  - Prometheus endpoint exposed, OTLP tracing configured, structured logstash console format.
- Language split:
  - Java for gateway/auth/BFF/domain/shared modules.
  - Kotlin for `buyer-portal` (Spring Boot + Thymeleaf SSR).
  - Kotlin Multiplatform (Compose Multiplatform) for `kmp/seller-app`, `kmp/buyer-app`, `kmp/buyer-android-app`, `kmp/seller-android-app`, and shared `kmp/core` + `kmp/ui-shared` libraries (Web WASM / Android / iOS).
