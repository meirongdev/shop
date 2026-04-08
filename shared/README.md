# Shared Libraries

This directory contains shared libraries used across all Java microservices in the Shop Platform.

## Module Overview

```
shared/
├── shop-common/              # Parent POM (packaging: pom)
│   ├── shop-common-bom/      # Bill of Materials for version management
│   ├── shop-common-core/     # Core utilities: API envelope, error model, tracing, metrics, etc.
│   ├── shop-starter-idempotency/    # Idempotency guard (DB + Redis Bloom Filter)
│   ├── shop-starter-resilience/     # Resilience4j helper (CB + Retry + Bulkhead + TimeLimiter)
│   ├── shop-starter-api-compat/     # API versioning & deprecation headers
│   └── shop-starter-feature-toggle/ # OpenFeature feature flags integration
└── shop-contracts/           # Shared API contracts: paths, DTOs, event schemas
```

---

## shop-common-core

**Artifact:** `dev.meirong.shop:shop-common-core`

**Purpose:** Foundational cross-cutting concerns for all Java microservices. Zero-config drop-in via Spring Boot auto-configuration.

| Package | Concern | Key Types |
|---------|---------|-----------|
| `api` | Universal HTTP response envelope | `ApiResponse<T>` with `success()` / `failure()` factories |
| `error` | Unified error model (RFC 7807) | `BusinessErrorCode` (interface), `BusinessException`, `CommonErrorCode` (enum with 14 codes) |
| `http` | Trusted header propagation | `HeaderPropagationInterceptor` — forwards gateway-injected headers on outbound `RestClient` calls; `TrustedHeaderNames` (9 constants) |
| `json` | Jackson compatibility | `JacksonCompatibilityAutoConfiguration` — disables `FAIL_ON_UNKNOWN_PROPERTIES` |
| `kafka` | Consumer error typing | `RetryableKafkaConsumerException`, `NonRetryableKafkaConsumerException` |
| `metrics` | Micrometer helper | `MetricsHelper` — factory for Counter/Timer/Gauge with automatic `shop_` prefix and `service` tag |
| `observability` | OTLP logging + Pyroscope profiling | `ObservabilityAutoConfiguration` — installs `OpenTelemetryAppender` and `PyroscopeAgent` |
| `trace` | Distributed tracing & MDC | `CorrelationFilter` (request ID), `TracingHeaderMdcFilter` (user/context headers), `TraceCorrelationResponseFilter` (response headers), `TraceIdExtractor` |
| `web` | Global exception handling + HTTP/2 | `GlobalExceptionHandler` (4 handlers), `ShopProblemDetails` (RFC 7807 factory), `TomcatHttp2AutoConfiguration` (tuned `initialWindowSize=1MB`) |

**Auto-configurations (9):** All use `@AutoConfiguration` — no manual bean registration needed.

---

## shop-common-bom

**Artifact:** `dev.meirong.shop:shop-common-bom`

**Purpose:** Bill of Materials for dependency management. Import this in your `<dependencyManagement>` to get consistent versions of all shop-common modules without specifying `<version>` on each dependency.

```xml
<dependencyManagement>
    <dependencies>
        <dependency>
            <groupId>dev.meirong.shop</groupId>
            <artifactId>shop-common-bom</artifactId>
            <version>${shop.version}</version>
            <type>pom</type>
            <scope>import</scope>
        </dependency>
    </dependencies>
</dependencyManagement>
```

---

## shop-starter-idempotency

**Artifact:** `dev.meirong.shop:shop-starter-idempotency`

**Purpose:** Idempotency guarantee for message processing and API calls. Prevents duplicate operations via a two-layer defense:

1. **Bloom Filter (fast path)** — Redis-backed probabilistic membership test, O(1) lookup
2. **Database (authoritative path)** — JPA-based `IdempotencyRepository` with unique constraint

| Type | Description |
|------|-------------|
| `IdempotencyGuard` | Interface — `executeOnce(key, action, fallback)` |
| `DbIdempotencyGuard` | DB-only implementation (active when Bloom Filter is disabled) |
| `RedisIdempotencyGuard` | Bloom Filter + DB fallback (active when `shop.idempotency.bloom-filter.enabled=true` and Redisson is on classpath) |
| `IdempotencyRepository` | JPA repository for persistent idempotency records |
| `IdempotencyExempt` | Annotation to mark methods as exempt from idempotency checks |
| `BloomFilterProperties` | `@ConfigurationProperties(prefix = "shop.idempotency.bloom-filter")` — `expectedInsertions`, `falseProbability`, `redisKey` |

**Metrics:** `shop.idempotency.bf.hit{result=duplicate|false_positive}`, `shop.idempotency.bf.miss`, `shop.idempotency.bf.fallback`

**Used by:** `order-service`, `wallet-service`, `marketplace-service`, `promotion-service`, `loyalty-service`, `activity-service`, `notification-service`, `webhook-service`, `search-service`

---

## shop-starter-resilience

**Artifact:** `dev.meirong.shop:shop-starter-resilience`

**Purpose:** BFF-layer resilience via Resilience4j. Composes CircuitBreaker + Retry + Bulkhead + TimeLimiter into a single decorator, with Java 25 virtual threads for async execution.

| Type | Description |
|------|-------------|
| `ResilienceHelper` | Core API — `read(instanceName, supplier)`, `write(instanceName, supplier/runnable)`, with optional `fallback` parameter |
| `ResilienceAutoConfiguration` | Auto-config — runs after all Resilience4j auto-configs, provides `ResilienceHelper` bean |

**Design notes:**
- `read()` enables Retry (for idempotent GET calls); `write()` disables Retry (for mutating operations)
- Uses `Executors.newVirtualThreadPerTaskExecutor()` for TimeLimiter's async execution
- Automatically sets MDC keys `downstreamService` and `retryable` for observability

**Used by:** `buyer-bff`, `seller-bff`

---

## shop-starter-api-compat

**Artifact:** `dev.meirong.shop:shop-starter-api-compat`

**Purpose:** API versioning detection and deprecation signaling via HTTP response headers.

| Type | Description |
|------|-------------|
| `ApiCompatibilityInterceptor` | Detects `/v{N}/` in request path → sets `X-API-Version` header; detects `@ApiDeprecation` → sets `Deprecation`, `X-API-Deprecated-Since`, `Sunset`, `X-API-Replacement` headers |
| `ApiDeprecation` | Annotation (`@since`, `sunsetAt`, `replacement`) — apply to controller classes or methods |
| `CompatibilityHeaderNames` | Header name constants |

**Used by:** `buyer-bff`, `seller-bff`

---

## shop-starter-feature-toggle

**Artifact:** `dev.meirong.shop:shop-starter-feature-toggle`

**Purpose:** Feature flag integration via OpenFeature SDK. Supports both static config (`application.yml`) and dynamic providers.

| Type | Description |
|------|-------------|
| `FeatureToggleService` | API — `isEnabled(flagKey, defaultValue)`, `requireEnabled(flagKey, defaultValue, message)` |
| `FeatureToggleProperties` | `@ConfigurationProperties(prefix = "shop.features")` — `Map<String, Boolean> flags` |
| `OpenFeaturePropertyProvider` | OpenFeature provider backed by `FeatureToggleProperties` |
| `FeatureToggleAutoConfiguration` | Auto-config — bootstraps OpenFeature SDK with domain `shop-platform` |

**Used by:** `search-service` (and available for any service needing feature flags)

---

## shop-contracts

**Artifact:** `dev.meirong.shop:shop-contracts`

**Purpose:** Single source of truth for API paths, DTOs, and Kafka event schemas shared between services. Prevents path typos and ensures contract compatibility.

### API Contracts (`dev.meirong.shop.contracts.api`)

16 API contract classes, each defining:
- **Base path** constant (e.g., `BuyerApi.BASE = "/buyer/v1"`)
- **Endpoint path** constants (e.g., `BuyerApi.GET_CART = "/buyer/v1/cart/get"`)
- **Request/Response DTOs** as `record` types with Jakarta Validation annotations

| Domain | Public API | Internal API |
|--------|-----------|--------------|
| Auth | `AuthApi` | — |
| Buyer (Cart/Wishlist) | `BuyerApi` | — |
| Profile | `ProfileApi` | `ProfileInternalApi` |
| Marketplace | `MarketplaceApi` | `MarketplaceInternalApi` |
| Order | `OrderApi` | — |
| Wallet | `WalletApi` | — |
| Promotion | `PromotionApi` | `PromotionInternalApi` |
| Search | `SearchApi` | — |
| Loyalty | `LoyaltyApi` | — |
| Activity | `ActivityApi` | — |
| Subscription | `SubscriptionApi` | — |
| Seller | `SellerApi` | — |
| Webhook | `WebhookApi` | — |

### Event Contracts (`dev.meirong.shop.contracts.event`)

| Type | Event | Trigger |
|------|-------|---------|
| `EventEnvelope<T>` | Generic wrapper for all Kafka events | — |
| `BuyerRegisteredEventData` | `buyer.registered.v1` | Buyer registration |
| `MarketplaceProductEventData` | `marketplace.product.events.v1` | Product lifecycle changes |
| `OrderEventData` | `order.events.v1` | Order state transitions |
| `WalletTransactionEventData` | `wallet.transactions.v1` | Wallet transactions |

**Used by:** All 15 backend services

---

## Dependency Graph

```
shop-contracts ──→ (jackson-annotations, jakarta.validation, springdoc)

shop-common-core ──→ (spring-boot-autoconfigure, spring-web, micrometer, OTLP, Pyroscope)
       ↑
       ├── shop-starter-idempotency ──→ (+ spring-data-jpa, redisson, micrometer)
       ├── shop-starter-resilience  ──→ (+ resilience4j-cb/retry/bulkhead/timelimiter)
       ├── shop-starter-api-compat  ──→ (+ spring-webmvc)
       └── shop-starter-feature-toggle ──→ (+ openfeature-sdk)

shop-common-bom ──→ manages versions for all above
```

## Usage Guidelines

### Adding a new service dependency

```xml
<!-- In your service's pom.xml -->
<dependencies>
    <!-- Core: always needed -->
    <dependency>
        <groupId>dev.meirong.shop</groupId>
        <artifactId>shop-common-core</artifactId>
    </dependency>

    <!-- Contracts: needed for API paths and DTOs -->
    <dependency>
        <groupId>dev.meirong.shop</groupId>
        <artifactId>shop-contracts</artifactId>
    </dependency>

    <!-- Optional starters: only when needed -->
    <dependency>
        <groupId>dev.meirong.shop</groupId>
        <artifactId>shop-starter-idempotency</artifactId>
    </dependency>
    <dependency>
        <groupId>dev.meirong.shop</groupId>
        <artifactId>shop-starter-resilience</artifactId>
    </dependency>
</dependencies>
```

### When to use which module

| Scenario | Module |
|----------|--------|
| Return a response from a controller | `shop-common-core` (`ApiResponse<T>`) |
| Throw a business error | `shop-common-core` (`BusinessException`) |
| Call another service via RestClient | `shop-common-core` (header propagation is automatic) |
| Prevent duplicate Kafka event processing | `shop-starter-idempotency` |
| Prevent duplicate wallet/payment operations | `shop-starter-idempotency` |
| BFF calling downstream services | `shop-starter-resilience` |
| Deprecate an API endpoint | `shop-starter-api-compat` (`@ApiDeprecation`) |
| Gate a feature behind a flag | `shop-starter-feature-toggle` |
| Define a new API endpoint path | `shop-contracts` (add to `*Api.java`) |
| Define a new Kafka event type | `shop-contracts` (add `*EventData.java` in `event/`) |
