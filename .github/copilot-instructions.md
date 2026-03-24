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
- `api-gateway` is the edge (Spring Cloud Gateway/WebFlux). It routes:
  - `/auth/**` -> `auth-server`
  - `/buyer/**` -> `buyer-portal`
  - `/seller/**` -> `seller-portal`
  - `/api/buyer/**` -> `buyer-bff` (with `stripPrefix(1)`)
  - `/api/seller/**` -> `seller-bff` (with `stripPrefix(1)`)
- `auth-server` issues JWTs; gateway validates JWT for `/api/**`.
- BFFs (`buyer-bff`, `seller-bff`) aggregate downstream domain services via `RestClient`, often with virtual threads.
- Domain services own their own MySQL schema and Flyway migrations (`profile`, `promotion`, `wallet`, `marketplace`, `order`).
- Event flow: `wallet-service` writes outbox records in DB, scheduled publisher emits to Kafka, `promotion-service` consumes wallet topic.
- Portals (`buyer-portal`, `seller-portal`) are Kotlin + Thymeleaf apps calling gateway APIs (`/api` + contract paths).

## Key repository conventions

- API contracts are centralized in `shop-contracts`:
  - Path constants live in `contracts/api/*Api.java` (for controllers and clients).
  - Shared event envelope/DTOs live in `contracts/event/*`.
- Standard response envelope is `ApiResponse<T>` from `shop-common`; services return `ApiResponse.success(...)` and rely on `shop-common` global exception handling for `SC_*` error codes.
- Internal service trust model:
  - Gateway injects trusted headers (`X-Request-Id`, `X-Player-Id`, `X-Username`, `X-Roles`, `X-Portal`, `X-Internal-Token`).
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
  - Kotlin mainly for `buyer-portal` and `seller-portal`.
