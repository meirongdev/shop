# Marketplace Search & Product Listing Design

> Version: 1.0 | Date: 2026-04-09
> Status: Live document — reflects current implementation

## 1. Architecture Overview

```
┌──────────────────────────────────────────────────────────────────────────────┐
│                              Client Layer                                     │
│                                                                              │
│  Buyer Portal (Kotlin SSR)  ←→  Buyer App (KMP WASM)  ←→  Seller App (KMP)  │
│         /buyer/home                  /buyer-app/               /seller/       │
└────────────────────────────┬─────────────────────────────────────────────────┘
                             │ Gateway (:8080)
                             │  /buyer/**       → buyer-portal:8080
                             │  /api/buyer/**   → buyer-bff:8080
                             │  /api/search/**  → search-service:8080
                             │  /api/seller/**  → seller-bff:8080
                             ▼
┌──────────────────────────────────────────────────────────────────────────────┐
│                           Backend-for-Frontend                                │
│                                                                              │
│  buyer-bff:8080                                                               │
│  ┌─────────────────────────────────────────────────────────────────────┐     │
│  │ searchProducts()                                                     │     │
│  │  1. Primary: search-service (ResilienceHelper: CB + Retry)          │     │
│  │  2. Fallback: marketplace-service (direct SQL LIKE search)          │     │
│  └─────────────────────────────────────────────────────────────────────┘     │
└───┬────────────────────────────┬─────────────────────────────────────────────┘
    │ Primary                    │ Fallback
    ▼                            ▼
┌──────────────────┐    ┌────────────────────────────────────┐
│  search-service  │    │  marketplace-service                │
│                  │    │                                     │
│  ┌────────────┐  │    │  ┌───────────────────────────────┐ │
│  │ MeiliSearch│  │    │  │ MySQL (marketplace_product)   │ │
│  │ :7700      │  │    │  │ SELECT ... LIKE ...           │ │
│  └────────────┘  │    │  └───────────────────────────────┘ │
│                  │    │                                     │
│  ┌────────────┐  │    │  ┌───────────────────────────────┐ │
│  │ Kafka      │◄─┼────┼──│ Outbox → Kafka                │ │
│  │ Consumer   │  │    │  │ marketplace.product.events.v1 │ │
│  └────────────┘  │    │  └───────────────────────────────┘ │
│                  │    │                                     │
│  ┌────────────┐  │    │  ┌───────────────────────────────┐ │
│  │ Reindex    │◄─┼────┼──│ Internal API                  │ │
│  │ Service    │  │    │  │ GET /internal/products        │ │
│  └────────────┘  │    │  └───────────────────────────────┘ │
└──────────────────┘    └─────────────────────────────────────┘
```

## 2. Data Flow: Product Creation → Search Results

### 2.1 Write Path (Seller → MySQL → Kafka)

```
Seller creates product via /seller/portal
        │
        ▼
marketplace-service: POST /marketplace/v1/product/create
        │
        ├─ 1. Save MarketplaceProductEntity → MySQL (marketplace_product table)
        │
        └─ 2. Write MarketplaceOutboxEventEntity → MySQL (same DB transaction)
              └─ EventEnvelope<MarketplaceProductEventData>
                 eventType: "PRODUCT_CREATED"
                 aggregateId: productId (UUID)
                 schemaVersion: 1
```

**Outbox Publisher** (scheduled every 5 seconds):
```
MarketplaceOutboxPublisher
  ├─ Polls top 100 unpublished events from marketplace_outbox_event
  ├─ Sends each to Kafka topic: marketplace.product.events.v1
  │    Key: productId
  │    Value: JSON serialized EventEnvelope
  └─ Marks events as published
```

### 2.2 Read Path Sync (Kafka → MeiliSearch)

```
Kafka topic: marketplace.product.events.v1
        │
        ▼
search-service: ProductEventConsumer (@KafkaListener, groupId: search-service)
        │
        ├─ Deserializes EventEnvelope<MarketplaceProductEventData>
        ├─ Asserts schema version == 1
        │
        └─ Event type dispatch:
            ├─ PRODUCT_CREATED / PRODUCT_UPDATED / PRODUCT_PUBLISHED
            │   ├─ if data.published() → ProductIndexer.index(doc)
            │   └─ else                → ProductIndexer.remove(productId)
            │
            ├─ PRODUCT_DELETED / PRODUCT_UNPUBLISHED
            │   └─ ProductIndexer.remove(productId)
            │
            └─ Unknown → log warning

Retry: 4 attempts, exponential backoff (1s × 2), DLQ auto-created
Idempotency: @IdempotencyExempt — index operations converge on same document ID
```

### 2.3 Initial Seed (Startup Full Sync)

Flyway migrations insert seed products directly into MySQL without emitting Kafka events.
On startup, search-service performs a full sync:

```
ProductIndexSettings.initializeIndex() (@EventListener ApplicationReadyEvent)
        │
        ├─ 1. Ensure MeiliSearch index "products" exists with settings
        │
        └─ 2. ReindexService.reindex()
              ├─ a. Create temp index: "products_<epochMillis>"
              ├─ b. Paginate marketplace internal API:
              │     GET /marketplace/internal/products?page=0&size=500
              │     → Filter published products → Map to ProductDocument
              │     → Batch-index into temp index
              │     → Repeat until all pages consumed
              ├─ c. Atomically swap: swapIndexes(["products", "products_<epoch>"])
              └─ d. Delete old index (now named "products_<epoch>")

If marketplace is not ready → log warning, rely on Kafka eventual consistency.
```

This is a **zero-downtime blue-green index swap** pattern.

## 3. MeiliSearch Index Configuration

**Index name:** `"products"`

| Setting | Attributes |
|---------|-----------|
| **Primary Key** | `id` |
| **Searchable** | `name`, `description`, `categoryName` |
| **Filterable** | `categoryId`, `sellerId`, `published`, `priceInCents` |
| **Sortable** | `priceInCents`, `createdAt`, `name`, `inventory` |
| **Ranking Rules** | `words` → `typo` → `proximity` → `attribute` → `sort` → `exactness` → `inventory:desc` |
| **Localized Attributes** | `name`, `description`, `categoryName` for `en`, `zh`, `ja` |
| **Faceting** | maxValuesPerFacet=100 |
| **Pagination** | maxTotalHits=5000 |
| **Typo Tolerance** | Enabled |

### ProductDocument Model

```java
public record ProductDocument(
    String id,           // UUID
    String sellerId,
    String sku,
    String name,
    String description,
    long priceInCents,   // BigDecimal shifted ×100 (e.g., 19.99 → 1999)
    int inventory,
    boolean published,
    String categoryId,
    String categoryName,
    String imageUrl,
    String status,
    Instant createdAt
)
```

Two factory methods:
- `fromEventData(MarketplaceProductEventData)` — from Kafka event
- `fromProductResponse(MarketplaceApi.ProductResponse)` — from marketplace internal API (reindex)

## 4. Search Query Flow

### 4.1 Buyer Portal Request

```
Browser → GET /buyer/home?q=hair&category=beauty&page=0
        │
        ▼
buyer-portal (Kotlin SSR): BuyerPortalController.home()
        │
        ├─ Get/create buyer shopping session
        ├─ apiClient.searchProducts(session, query, category, page)
        │     → POST /api/buyer/v1/product/search
        │     → Body: SearchProductsRequest(query, categoryId, page, pageSize=12)
        │
        ├─ apiClient.listCategories(session)
        ├─ apiClient.listCart(session)
        │
        └─ Render buyer-home.html (Thymeleaf)
              ├─ Product grid (12 per page)
              ├─ Category sidebar
              ├─ Search bar
              └─ Add-to-cart forms
```

### 4.2 BFF Aggregation (Primary + Fallback)

```
buyer-bff: POST /buyer/v1/product/search
        │
        ├─ Primary: search-service via SearchServiceClient
        │   ┌─────────────────────────────────────────────┐
        │   │ ResilienceHelper.read("searchService", ...) │
        │   │  ├─ Circuit Breaker                         │
        │   │  ├─ Retry (3 attempts)                      │
        │   │  ├─ Bulkhead                                │
        │   │  └─ TimeLimiter (timeout)                   │
        │   └─────────────────────────────────────────────┘
        │         │
        │         ▼
        │   search-service: GET /search/v1/products
        │         │         ?q={query}&categoryId={categoryId}
        │         │         &page={page}&hitsPerPage={size}
        │         │         (note: sort and locales are NOT forwarded by BFF)
        │         ▼
        │   MeiliSearch → SearchApi.SearchProductsResponse
        │
        └─ Fallback (on any search-service failure):
              │
              ▼
        marketplace-service: POST /marketplace/v1/product/search
              │              Body: SearchProductsRequest(query, categoryId, page, size)
              │
              ├─ SQL LIKE query on marketplace_product table:
              │    WHERE published = true
              │    AND (name LIKE '%query%' OR description LIKE '%query%')
              │    AND (categoryId = ? OR ? IS NULL)
              │    ORDER BY created_at DESC
              │    LIMIT size OFFSET page*size
              │
              └─ Map ProductsPageView → SearchApi.SearchProductsResponse
                   (ProductResponse → ProductHit, empty facetDistribution)
```

**Key properties of the fallback:**
- Synchronous (not async) — direct RestClient call
- Returns the **same** `SearchApi.SearchProductsResponse` type — callers see no difference
- No faceting from SQL backend (facetDistribution is empty)
- `sku` and `priceInCents` fields in `ProductHit` are **null** (not mapped from `ProductResponse`)
- Logs warning: `"search-service unavailable, fallback to marketplace search"`

## 5. API Endpoints

### 5.1 Gateway Routes

| Gateway Path | Target Service | Auth |
|---|---|---|
| `/buyer/**` | buyer-portal:8080 | Guest OK |
| `/api/buyer/**` | buyer-bff:8080 | JWT required |
| `/api/search/**` | search-service:8080 | Internal |
| `/api/seller/**` | seller-bff:8080 | JWT required |
| `/seller/**` | seller-portal:80 | JWT required |

### 5.2 Marketplace Service (`/marketplace/v1`)

| Endpoint | Method | Description |
|---|---|---|
| `/marketplace/v1/product/list` | POST | List products (publishedOnly flag) |
| `/marketplace/v1/product/create` | POST | Create product |
| `/marketplace/v1/product/update` | POST | Update product |
| `/marketplace/v1/product/get` | POST | Get single product |
| `/marketplace/v1/product/search` | POST | SQL LIKE search + pagination |
| `/marketplace/v1/category/list` | POST | List categories |
| `/marketplace/v1/product/inventory/deduct` | POST | Deduct inventory (Redis lock) |
| `/marketplace/internal/products` | GET | Internal: paginated all products (size=500) |

### 5.3 Search Service (`/search/v1`)

| Endpoint | Method | Description |
|---|---|---|
| `/search/v1/products` | GET | Full-text MeiliSearch search; supports `q`, `categoryId`, `sort`, `page`, `hitsPerPage`, `locales` |
| `/search/v1/products/suggestions` | GET | Autocomplete — **requires `SearchFeatureFlags.AUTOCOMPLETE` enabled** |
| `/search/v1/queries/trending` | GET | Trending search queries (in-memory analytics) — **requires `SearchFeatureFlags.TRENDING` enabled** |
| `/search/v1/products/_reindex` | POST | Trigger full reindex on-demand |
| `/search/v1/health` | GET | Health check |

> **Note:** `locales` param activates locale-aware ranking but requires `SearchFeatureFlags.LOCALE_AWARE_SEARCH` to be enabled (default: disabled — locales are silently ignored if flag is off).

### 5.4 Buyer BFF (`/api/buyer/v1`)

| Endpoint | Method | Description |
|---|---|---|
| `/buyer/v1/product/search` | POST | Search (search-service + marketplace fallback) |
| `/buyer/v1/product/get` | POST | Get single product |
| `/buyer/v1/category/list` | POST | List categories |
| `/buyer/v1/marketplace/list` | POST | List all marketplace products |

## 6. Event Contract

### EventEnvelope

```java
public record EventEnvelope<T>(
    String eventId,        // Unique event ID (UUID)
    String aggregateId,    // productId for product events
    String type,           // "PRODUCT_CREATED", "PRODUCT_PUBLISHED", etc.
    long timestamp,        // Epoch millis
    T data,                // MarketplaceProductEventData
    int schemaVersion      // CURRENT_SCHEMA_VERSION = 1
)
```

### MarketplaceProductEventData

```java
public record MarketplaceProductEventData(
    String productId,
    String sellerId,
    String sku,
    String name,
    String description,
    BigDecimal price,
    int inventory,
    boolean published,
    String categoryId,
    String categoryName,
    Instant createdAt
    // NOTE: imageUrl is absent — products indexed via Kafka will have imageUrl=null in MeiliSearch.
    // Only the reindex path (GET /marketplace/internal/products) supplies imageUrl.
)
```

### Kafka Topic

- **Name:** `marketplace.product.events.v1`
- **Key:** `productId` (ensures ordering per product)
- **Partitions:** Determined by topic config
- **Consumer Group:** `search-service` (single consumer instance per partition)

## 7. Failure Modes & Recovery

| Failure | Detection | Recovery |
|---|---|---|
| search-service down | Circuit breaker opens | Fallback to marketplace SQL search |
| MeiliSearch down | Health check fails | ReindexService will fail; restart re-creates index |
| Kafka consumer lag | DLQ accumulates | Restart consumer; events are replay-safe |
| Marketplace DB down | init container (wait-for-mysql) blocks startup | MySQL must be healthy first |
| Stale MeiliSearch index | Products in MySQL ≠ MeiliSearch | POST `/search/v1/products/_reindex` triggers full sync |
| `imageUrl` missing in index | Kafka events lack `imageUrl`; new products show no image until reindex runs | Run `POST /search/v1/products/_reindex` after product creation, or add `imageUrl` to the event contract |

## 8. Key Files

| Component | File Path |
|---|---|
| **Marketplace Service** | |
| Product Entity | `services/marketplace-service/.../domain/MarketplaceProductEntity.java` |
| Product Repository | `services/marketplace-service/.../domain/MarketplaceProductRepository.java` |
| Application Service | `services/marketplace-service/.../service/MarketplaceApplicationService.java` |
| Outbox Publisher | `services/marketplace-service/.../service/MarketplaceOutboxPublisher.java` |
| Controller | `services/marketplace-service/.../controller/MarketplaceController.java` |
| Internal Controller | `services/marketplace-service/.../controller/MarketplaceInternalController.java` |
| Flyway Migrations | `services/marketplace-service/.../db/migration/V1__init.sql` |
| **Search Service** | |
| ProductDocument | `services/search-service/.../index/ProductDocument.java` |
| ProductIndexer | `services/search-service/.../index/ProductIndexer.java` |
| ProductIndexSettings | `services/search-service/.../index/ProductIndexSettings.java` |
| ProductEventConsumer | `services/search-service/.../consumer/ProductEventConsumer.java` |
| ReindexService | `services/search-service/.../service/ReindexService.java` |
| ProductSearchService | `services/search-service/.../service/ProductSearchService.java` |
| SearchController | `services/search-service/.../controller/SearchController.java` |
| MarketplaceInternalClient | `services/search-service/.../client/MarketplaceInternalClient.java` |
| **Buyer BFF** | |
| BuyerAggregationService | `services/buyer-bff/.../service/BuyerAggregationService.java` |
| SearchServiceClient | `services/buyer-bff/.../client/SearchServiceClient.java` |
| BuyerController | `services/buyer-bff/.../controller/BuyerController.java` |
| **Buyer Portal** | |
| BuyerPortalController | `frontend/buyer-portal/.../controller/BuyerPortalController.kt` |
| BuyerPortalApiClient | `frontend/buyer-portal/.../service/BuyerPortalApiClient.kt` |
| buyer-home.html | `frontend/buyer-portal/.../templates/buyer-home.html` |
| **Contracts** | |
| SearchApi | `shared/shop-contracts/shop-contracts-search/.../SearchApi.java` |
| MarketplaceApi | `shared/shop-contracts/shop-contracts-marketplace/.../MarketplaceApi.java` |
| MarketplaceInternalApi | `shared/shop-contracts/shop-contracts-marketplace/.../MarketplaceInternalApi.java` |
| EventEnvelope | `shared/shop-contracts/shop-contracts-event-common/.../EventEnvelope.java` |
| MarketplaceProductEventData | `shared/shop-contracts/shop-contracts-marketplace/.../MarketplaceProductEventData.java` |

## 9. Known Issues & Improvement Backlog

> 以下问题已通过设计 review 识别，待后续 agent 处理。优先级 P0 > P1 > P2。

### P0 — Bug（影响功能正确性）

#### [SEARCH-001] Kafka 事件路径索引不含 `imageUrl`，导致搜索结果图片为空

- **根因**：`MarketplaceProductEventData` 没有 `imageUrl` 字段；`ProductDocument.fromEventData()` 只能赋 `null`。只有 reindex 路径（`GET /marketplace/internal/products`）才携带图片。
- **影响**：每次产品创建/更新，MeiliSearch 中该产品 `imageUrl = null`，直到下次手动 reindex。
- **需改动的文件**：
  - `shared/shop-contracts/.../event/MarketplaceProductEventData.java` — 添加 `String imageUrl`
  - `services/marketplace-service/.../service/MarketplaceApplicationService.java` — 发布事件时填充 `imageUrl`
  - `services/search-service/.../index/ProductDocument.java` — `fromEventData()` 映射 `imageUrl`
- **注意**：这是 event schema change，需评估是否需要升 `schemaVersion`（当前为 1）

---

#### [SEARCH-002] Fallback 响应中 `sku` 和 `priceInCents` 为 null

- **根因**：`BuyerAggregationService.searchProductsFallback()` 构造 `ProductHit` 时未映射这两个字段。
- **影响**：search-service 降级期间，购物车价格计算和 SKU 展示异常。
- **需改动的文件**：
  - `services/buyer-bff/src/main/java/dev/meirong/shop/buyerbff/service/BuyerAggregationService.java`（约 437–448 行）
  - 从 `product.sku()` 和 `product.price()` （需 ×100）补充映射

---

### P1 — 功能不完整

#### [SEARCH-003] BFF 未转发 `sort` 参数到 search-service

- **根因**：`BuyerAggregationService.searchProducts()` 的 `UriComponentsBuilder` 未包含 `sort`，且 `MarketplaceApi.SearchProductsRequest` 无此字段。
- **影响**：前端无法通过 BFF 使用按价格/时间排序功能，search-service 的 `sort` 参数形同虚设。
- **需改动的文件**：
  - `shared/shop-contracts/.../marketplace/MarketplaceApi.java` — `SearchProductsRequest` 添加 `String sort`
  - `services/buyer-bff/.../service/BuyerAggregationService.java` — URI 构造加 `.queryParamIfPresent("sort", ...)`

---

#### [SEARCH-004] BFF 未转发 `locales` 参数到 search-service

- **根因**：同 SEARCH-003，BFF 不传 `locales`，多语言搜索排序无法通过正常调用路径启用。
- **影响**：国际化搜索排名 (`LOCALE_AWARE_SEARCH` feature flag) 无法生效。
- **需改动的文件**：同 SEARCH-003，额外在 `SearchProductsRequest` 添加 `List<String> locales`

---

### P2 — 可观测性

#### [SEARCH-005] DLQ 消费缺少 metrics，无法触发告警

- **根因**：`ProductEventConsumer.handleDlt()` 只打 `log.error`，没有 Micrometer counter。
- **影响**：DLQ 消息积压无法被 Prometheus alert 感知，只能靠人工查日志发现。
- **需改动的文件**：
  - `services/search-service/src/main/java/dev/meirong/shop/search/consumer/ProductEventConsumer.java`
  - 注入 `MeterRegistry`，在 `handleDlt()` 执行 `counter("search.product.dlq.messages.total").increment()`
  - 在 `docs/OBSERVABILITY-ALERTING-SLO.md` 或 alert rules 中补充对应告警规则
