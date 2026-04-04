# Changelog

All notable changes to the Shop Platform project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

### Added

#### Platform Engineering

- **Archetype Automation Testing** (`archetype-tests/` module)
  - Added comprehensive integration tests for all 6 Maven archetypes
  - Test coverage includes:
    - `domain-service-archetype`
    - `bff-service-archetype`
    - `event-worker-archetype`
    - `gateway-service-archetype`
    - `auth-service-archetype`
    - `portal-service-archetype`
  - Each test verifies: directory structure, K8s configs, compilation, test execution, dependencies
  - New `make archetype-test` command for running all archetype tests
  - New `scripts/test-archetypes.sh` script for CI/CD integration
  - GitHub Actions `archetype-test` job integrated into CI pipeline
  - Documentation: `docs/ARCHETYPE-TESTING-IMPROVEMENT-PLAN.md`

- **New Makefile Targets**
  - `make archetype-test` - Run archetype generation and integration tests

- **New Documentation**
  - `docs/ARCHETYPE-TESTING-IMPROVEMENT-PLAN.md` - Complete archetype testing improvement plan
  - Updated `docs/ARCHETYPE-TUTORIAL.md` - Added "Automation Testing" section (Section 7)
  - Updated `shop-archetypes/README.md` - Added testing documentation
  - Updated `docs/ROADMAP-2026.md` - Marked archetype testing as completed
  - Updated `docs/DEVELOPER-EXPERIENCE-STANDARD-2026.md` - Added archetype-test command
  - Updated `docs/ENGINEERING-STANDARDS-2026.md` - Updated archetype testing status

#### Test Module Structure

```
archetype-tests/
├── pom.xml
└── src/test/java/dev/meirong/shop/archetype/
    ├── AbstractArchetypeTest.java         # Base test class
    ├── DomainServiceArchetypeTest.java
    ├── BffServiceArchetypeTest.java
    ├── EventWorkerArchetypeTest.java
    ├── GatewayServiceArchetypeTest.java
    ├── AuthServiceArchetypeTest.java
    └── PortalServiceArchetypeTest.java
```

### Changed

- **CI Pipeline** (`.github/workflows/ci.yml`)
  - Added new `archetype-test` job
  - Runs on Maven file changes
  - Timeout: 20 minutes
  - Uploads test results as artifacts

- **Root POM** (`pom.xml`)
  - Added `archetype-tests` module to build reactor

### Documentation Updates

| Document | Changes |
|----------|---------|
| `docs/ARCHETYPE-TESTING-IMPROVEMENT-PLAN.md` | NEW - Complete testing improvement plan |
| `docs/ARCHETYPE-TUTORIAL.md` | Added Section 7: Automation Testing |
| `docs/ROADMAP-2026.md` | Marked archetype testing as completed |
| `docs/DEVELOPER-EXPERIENCE-STANDARD-2026.md` | Added archetype-test command, updated CI section |
| `docs/ENGINEERING-STANDARDS-2026.md` | Updated archetype testing status |
| `shop-archetypes/README.md` | Added testing documentation section |

---

## [0.5.0] - 2026-03-23

### Added

#### Core Infrastructure
- Microservices architecture: Gateway + BFF + Domain Services (12 original modules)
- Authentication center: JWT login (buyer / seller)
- Event-driven: Outbox Pattern + Kafka (wallet-service, order-service)
- Observability: Prometheus + OpenTelemetry Collector + structured logging
- Kind/Kubernetes deployment + mirrord local access + local Registry & Tilt inner loop
- API Gateway: YAML routing + Virtual Threads + Redis Lua rate limiting + Canary + Trusted Headers

#### Shopping Core Flow
- Product catalog: Basic CRUD + multi-spec SKU + buyer reviews
- Order state machine: Complete 10 states + timeout cancel + shipping + refund
- Stripe payment callback: `/internal/orders/payment-confirm`
- Guest shopping flow: GUEST orders + order_token + public tracking API + guest pages
- Wallet service: Recharge, balance, Stripe integration
- Guest cart (Redis) + cart merging + Gateway CORS

#### Notification & Promotion
- notification-service (:8092): Channel SPI + EmailChannel + 7 templates + idempotency + retry
- Promotion engine upgrade: Strategy pattern + calculate API
- Promotion service: Coupons, recharge rewards (WalletRewardListener)

#### User Growth
- loyalty-service (:8088): Points account, check-in, redemption, newbie tasks + points expiry batch task
- New user registration benefits: +100 pts + 7 guided tasks + 3 welcome coupons + welcome email
- buyer-bff points deduction (checkout path, with CircuitBreaker fallback)

#### Engagement & Monetization
- activity-service (:8089): 4 implemented game types (InstantLottery / RedEnvelope / CollectCard / VirtualFarm) + AntiCheatGuard
- search-service (:8091): Meilisearch basic search + autocomplete + trending + OpenFeature feature flags

#### Platform Expansion
- Multi-vendor marketplace: Store homepage + merchant ratings + buyer store browsing page
- webhook-service (:8093): Event subscription + HMAC-SHA256 signature + exponential backoff retry
- subscription-service (:8094): Subscription plans + subscription lifecycle + auto-renewal

### Changed

- **Seller UI Modernization**: Removed Kotlin Thymeleaf SSR, adopted **KMP (Compose Multiplatform)** for Web/Android/iOS

### Fixed

- Various bug fixes across services

---

## Version History

| Version | Date | Key Highlights |
|---------|------|---------------|
| 0.5.0 | 2026-03-23 | Core shopping flow complete, KMP seller UI |
| 0.1.0 | 2026-01-01 | Initial platform setup |
