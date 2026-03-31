# GEMINI.md - Shop Platform Engineering Context

This file provides foundational context and instructions for Gemini CLI to ensure consistent engineering standards across the `shop-platform` repository.

## Project Overview
A Maven multi-module microservices architecture featuring Spring Boot services, a Spring Cloud Gateway, and diverse frontends including Kotlin Thymeleaf SSR and Kotlin Multiplatform (Compose).

## Operational Commands

### Backend (Maven)
- **Full multi-module build:** `mvn clean verify`
- **Full tests:** `mvn test`
- **Build without tests:** `mvn -DskipTests verify`
- **Build one module (+ deps):** `mvn -pl <module> -am clean package`
- **Test one module (+ deps):** `mvn -pl <module> -am test`
- **Run single test class:** `mvn -pl <module> -am -Dtest=ClassName test`
- **Run single test method:** `mvn -pl <module> -am -Dtest=ClassName#methodName test`

### Playwright E2E Tests (`e2e-tests/`)
- **Setup:** `cd e2e-tests && npm install && npx playwright install chromium`
- **Run buyer tests:** `make local-access & && make e2e-playwright`
- **Run seller tests:** `make e2e-playwright-seller` (builds seller WASM + starts proxy)
- **Run buyer-app tests:** `make e2e-playwright-buyer-app` (builds buyer-app WASM + starts proxy)
- **Run all KMP tests:** `make e2e-playwright-kmp` (builds both + starts proxies)
- **Verify observability:** `make verify-observability`
- **Run all:** `cd e2e-tests && npx playwright test`
- **Open report:** `cd e2e-tests && npx playwright show-report`
- Buyer project: 18 tests (login, guest mode, all authenticated pages at gateway:18080)
- Seller project: 8 tests (KMP/WASM app via e2e token injection at seller-proxy:18181)
- Buyer-app project: 12 tests (KMP/WASM app via e2e token injection at buyer-app-proxy:18182)

### Documentation (Docusaurus)
- **Local development:** `cd docs-site && npm install && npm run start`
- **Production build:** `cd docs-site && npm run build`

## Architectural Blueprint

### Routing & Gateway (`api-gateway`)
- `/auth/**` → `auth-server`
- `/buyer/**` → `buyer-portal`
- `/buyer-app/**` → `buyer-app` (KMP/Compose WASM, nginx static, Prefix stripped)
- `/seller/**` → `seller-portal` (KMP/Compose WASM, nginx static, Prefix stripped)
- `/api/buyer/**` → `buyer-bff` (Prefix stripped)
- `/api/seller/**` → `seller-bff` (Prefix stripped)
- `/api/loyalty/**` → `loyalty-service` (Prefix stripped)
- `/api/activity/**` → `activity-service` (Prefix stripped)
- `/api/webhook/**` → `webhook-service` (Prefix stripped)
- `/api/subscription/**` → `subscription-service` (Prefix stripped)
- Canary routes for `/api/buyer/**` and `/api/seller/**` can divert traffic to `buyer-bff-v2` / `seller-bff-v2` (declared before stable routes).

### Service Responsibilities
- **`auth-server`**: JWT issuance and identity management.
- **BFFs (`buyer-bff`, `seller-bff`)**: Downstream aggregation via `RestClient` (backed by `JdkClientHttpRequestFactory`) and Virtual Threads.
- **Domain Services**: `profile`, `promotion`, `wallet`, `marketplace`, `order`, `loyalty`, `activity`, `subscription`.
  - Each owns a private MySQL schema.
  - Migrations handled via Flyway.
- **Supporting Services**: `search-service` (Meilisearch), `notification-service`, `webhook-service`.
- **Event Mesh**: Transactional Outbox pattern in DB → Scheduled Publisher (5s polling) → Kafka → Consumers. Key topics: `wallet.transactions.v1`, `order.events.v1`, `buyer.registered.v1`, `marketplace.product.events.v1`, `loyalty.checkin.v1`, `activity.prize.won.v1`.

## Engineering Conventions

### API & Contracts
- **Source of Truth**: All contracts are centralized in `shop-contracts`.
  - Path constants: `contracts/api/*Api.java`
  - Event DTOs: `contracts/event/*`
- **Standard Response**: Wrap all API responses in `ApiResponse<T>` from `shop-common`.

### Security & Internal Trust
- **Header Injection**: Gateway injects `X-Request-Id`, `X-Buyer-Id`, `X-Username`, `X-Roles`, `X-Portal`, and `X-Internal-Token`. (`X-Buyer-Id` carries the authenticated buyer's principal ID from the `principalId` JWT claim.)
- **Validation**: Services must use `InternalAccessFilter` (from `shop-common`) to validate the `X-Internal-Token`.

### Configuration
- **Type Safety**: Prefer `@ConfigurationProperties` records.
- **Environment**: Define values in `application.yml` using `${ENV_VAR:default}` syntax.

### Observability
- **Ports**: Application on `8080`, Actuator/Management on `8081`.
- **Metrics/Tracing**: Prometheus and OTLP tracing are standard. Structured JSON logging (Logstash) for consoles.

## Technology Stack
- **Java**: Primary for Gateway, Auth, BFFs, and Domain services.
- **Kotlin**: Used for `buyer-portal` (Spring Boot + Thymeleaf SSR).
- **Kotlin Multiplatform**: Compose Multiplatform for `kmp/seller-app` (Web WASM/Android/iOS → `/seller/**`), `kmp/buyer-app` (Web WASM → `/buyer-app/**`), `kmp/buyer-android-app`, `kmp/seller-android-app`, shared `core` + `ui-shared`, and 7 feature modules.
