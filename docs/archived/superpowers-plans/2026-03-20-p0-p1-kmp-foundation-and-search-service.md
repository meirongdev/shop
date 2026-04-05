# P0 + P1: KMP Foundation + search-service Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Establish the Compose Multiplatform build infrastructure (P0) and deliver a working Meilisearch-backed product search service (P1), enabling all subsequent feature migration work.

**Architecture:** Two independent workstreams. P0 sets up Gradle KMP modules (core, ui-shared, buyer-app, seller-app) that compile empty shell apps on Android/iOS/Web. P1 adds Outbox pattern to marketplace-service, creates a new search-service that consumes product events from Kafka and indexes them in Meilisearch, and wires BFFs to proxy search requests. P0 and P1 have no dependencies on each other and can be parallelized.

**Tech Stack:**
- P0: Kotlin 2.3.20, Compose Multiplatform 1.8, Ktor Client 3.1, Koin 4.1, Jetpack Navigation/ViewModel (KMP)
- P1: Kotlin + Spring Boot 3.5.11, Spring Kafka, Meilisearch Java SDK 0.14, Testcontainers

**Spec:** `docs/superpowers/specs/2026-03-20-kmp-meilisearch-design.md`

---

## Workstream A: search-service + Meilisearch (P1)

### Task 1: Add Outbox Pattern to marketplace-service

marketplace-service currently has no Kafka integration. We need to add the Outbox pattern (matching wallet-service's approach) so product lifecycle events are published to Kafka.

**Files:**
- Create: `marketplace-service/src/main/java/dev/meirong/shop/marketplace/domain/MarketplaceOutboxEventEntity.java`
- Create: `marketplace-service/src/main/java/dev/meirong/shop/marketplace/domain/MarketplaceOutboxEventRepository.java`
- Create: `marketplace-service/src/main/java/dev/meirong/shop/marketplace/service/MarketplaceOutboxPublisher.java`
- Create: `marketplace-service/src/main/java/dev/meirong/shop/marketplace/service/MarketplaceEventType.java`
- Create: `marketplace-service/src/main/resources/db/migration/V3__add_outbox_event_table.sql`
- Modify: `marketplace-service/src/main/java/dev/meirong/shop/marketplace/service/MarketplaceApplicationService.java` — inject outbox writes into product create/update/delete/publish/unpublish
- Modify: `marketplace-service/pom.xml` — add `spring-kafka` dependency
- Modify: `marketplace-service/src/main/resources/application.yml` — add Kafka bootstrap config
- Create: `shop-contracts/src/main/java/dev/meirong/shop/contracts/event/MarketplaceProductEventData.java`
- Test: `marketplace-service/src/test/java/dev/meirong/shop/marketplace/service/MarketplaceOutboxPublisherTest.java`
- Test: `marketplace-service/src/test/java/dev/meirong/shop/marketplace/service/MarketplaceApplicationServiceOutboxTest.java`

- [ ] **Step 1: Add Flyway migration for outbox table**

```sql
-- V3__add_outbox_event_table.sql
CREATE TABLE marketplace_outbox_event (
    id           CHAR(36)     NOT NULL PRIMARY KEY,
    aggregate_id VARCHAR(64)  NOT NULL,
    topic        VARCHAR(128) NOT NULL,
    event_type   VARCHAR(128) NOT NULL,
    payload      TEXT         NOT NULL,
    published    BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at   TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    published_at TIMESTAMP(6) NULL,
    INDEX idx_outbox_unpublished (published, created_at)
);
```

- [ ] **Step 2: Create event type enum**

```java
// MarketplaceEventType.java
public enum MarketplaceEventType {
    PRODUCT_CREATED,
    PRODUCT_UPDATED,
    PRODUCT_DELETED,
    PRODUCT_PUBLISHED,
    PRODUCT_UNPUBLISHED
}
```

- [ ] **Step 3: Create event data contract in shop-contracts**

```java
// shop-contracts/.../event/MarketplaceProductEventData.java
public record MarketplaceProductEventData(
    String productId,
    String sellerId,
    String sku,
    String name,
    String description,
    BigDecimal price,
    Integer inventory,
    boolean published,
    String categoryId,
    String categoryName,
    String imageUrl,
    String status,
    Instant occurredAt
) {}
```

- [ ] **Step 4: Create outbox entity and repository**

Model after wallet-service's `WalletOutboxEventEntity` / `WalletOutboxEventRepository`:

Match the wallet-service pattern exactly: use `String` for ID (not `UUID`), generate via `UUID.randomUUID().toString()`.

```java
// MarketplaceOutboxEventEntity.java
@Entity
@Table(name = "marketplace_outbox_event")
public class MarketplaceOutboxEventEntity {
    @Id @Column(nullable = false, length = 36)
    private String id;
    @Column(nullable = false, length = 64) private String aggregateId;
    @Column(nullable = false, length = 128) private String topic;
    @Column(nullable = false, length = 128) private String eventType;
    @Column(columnDefinition = "TEXT", nullable = false) private String payload;
    private boolean published;
    private Instant createdAt;
    private Instant publishedAt;

    protected MarketplaceOutboxEventEntity() {}

    public MarketplaceOutboxEventEntity(String aggregateId, String topic, String eventType, String payload) {
        this.id = UUID.randomUUID().toString();
        this.aggregateId = aggregateId;
        this.topic = topic;
        this.eventType = eventType;
        this.payload = payload;
        this.published = false;
        this.createdAt = Instant.now();
    }

    public void markPublished() {
        this.published = true;
        this.publishedAt = Instant.now();
    }

    // getters: getId(), getAggregateId(), getTopic(), getEventType(), getPayload(), isPublished()
}
```

```java
// MarketplaceOutboxEventRepository.java
public interface MarketplaceOutboxEventRepository extends JpaRepository<MarketplaceOutboxEventEntity, String> {
    List<MarketplaceOutboxEventEntity> findTop100ByPublishedFalseOrderByCreatedAtAsc();
}
```

- [ ] **Step 5: Create outbox publisher (scheduled Kafka sender)**

Model after `WalletOutboxPublisher.java`. Improvement over wallet-service: send `aggregateId` as Kafka key to ensure ordering per product (wallet-service sends no key). Requires `@EnableScheduling` on a config class.

```java
// MarketplaceOutboxPublisher.java
@Component
public class MarketplaceOutboxPublisher {
    private final MarketplaceOutboxEventRepository repository;
    private final KafkaTemplate<String, String> kafka;

    @Scheduled(fixedDelayString = "${shop.marketplace.outbox.publish-delay-ms:5000}")
    @Transactional
    public void publishPendingEvents() {
        var events = repository.findTop100ByPublishedFalseOrderByCreatedAtAsc();
        for (var event : events) {
            kafka.send(event.getTopic(), event.getAggregateId(), event.getPayload());
            event.markPublished();
        }
        repository.saveAll(events);
    }
}
```

Also add `@EnableScheduling` to `MarketplaceApplicationService` or create a new `MarketplaceSchedulingConfig`:

```java
@Configuration
@EnableScheduling
public class MarketplaceSchedulingConfig {}
```

- [ ] **Step 6: Add Kafka dependency to marketplace-service pom.xml**

Add to `marketplace-service/pom.xml` `<dependencies>`:

```xml
<dependency>
    <groupId>org.springframework.kafka</groupId>
    <artifactId>spring-kafka</artifactId>
</dependency>
```

- [ ] **Step 7: Add Kafka config to application.yml**

Add to `marketplace-service/src/main/resources/application.yml`:

```yaml
spring:
  kafka:
    bootstrap-servers: ${KAFKA_BOOTSTRAP_SERVERS:localhost:9092}
    producer:
      key-serializer: org.apache.kafka.common.serialization.StringSerializer
      value-serializer: org.apache.kafka.common.serialization.StringSerializer

shop:
  marketplace:
    outbox:
      publish-delay-ms: ${MARKETPLACE_OUTBOX_PUBLISH_DELAY_MS:5000}
      topic: ${MARKETPLACE_PRODUCT_TOPIC:marketplace.product.events.v1}
```

- [ ] **Step 8: Modify MarketplaceApplicationService to write outbox events**

In each product mutation method (create, update), after the JPA save, write an outbox event in the same transaction. Note: marketplace-service currently has no explicit publish/unpublish methods. PRODUCT_PUBLISHED/PRODUCT_UNPUBLISHED events fire when the `published` field changes during an update.

**Note on `sellerName`:** The current `MarketplaceProductEntity` has no `sellerName` field. For P1, omit `sellerName` from the event data and remove it from Meilisearch `searchableAttributes`. Resolving seller names requires a cross-service join with profile-service — defer to P2 when feature-marketplace is built.

```java
// Add to MarketplaceApplicationService
private final MarketplaceOutboxEventRepository outboxRepository;
private final ObjectMapper objectMapper;
@Value("${shop.marketplace.outbox.topic}") private String productTopic;

private void writeOutboxEvent(MarketplaceProductEntity product, MarketplaceEventType eventType) {
    var eventData = new MarketplaceProductEventData(
        product.getId().toString(), product.getSellerId(), product.getSku(),
        product.getName(), product.getDescription(), product.getPrice(),
        product.getInventory(), product.isPublished(), product.getCategoryId(),
        /* categoryName */, product.getImageUrl(),
        product.getStatus(), Instant.now()
    );
    var envelope = new EventEnvelope<>(
        UUID.randomUUID().toString(), "marketplace-service", eventType.name(),
        Instant.now(), eventData
    );
    var entity = new MarketplaceOutboxEventEntity(
        product.getId().toString(), productTopic,
        eventType.name(), objectMapper.writeValueAsString(envelope)
    );
    outboxRepository.save(entity);
}

// Determine event type for updates:
private MarketplaceEventType resolveUpdateEventType(MarketplaceProductEntity before, MarketplaceProductEntity after) {
    if (!before.isPublished() && after.isPublished()) return MarketplaceEventType.PRODUCT_PUBLISHED;
    if (before.isPublished() && !after.isPublished()) return MarketplaceEventType.PRODUCT_UNPUBLISHED;
    return MarketplaceEventType.PRODUCT_UPDATED;
}
```

Call `writeOutboxEvent(product, PRODUCT_CREATED)` after create, `writeOutboxEvent(product, resolveUpdateEventType(before, after))` after update.

- [ ] **Step 9: Update docker-compose.yml — add Kafka env to marketplace-service**

Add to marketplace-service environment in `docker-compose.yml`:

```yaml
KAFKA_BOOTSTRAP_SERVERS: kafka:9092
MARKETPLACE_PRODUCT_TOPIC: marketplace.product.events.v1
```

- [ ] **Step 10: Write integration test for outbox publisher**

```java
// MarketplaceOutboxPublisherTest.java
@SpringBootTest
@Testcontainers
class MarketplaceOutboxPublisherTest {
    @Container static KafkaContainer kafka = new KafkaContainer(...);
    @Container static MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8.4");

    @Test
    void shouldPublishUnpublishedEventsToKafka() {
        // Given: an unpublished outbox event in DB
        // When: publisher runs
        // Then: event appears on Kafka topic and is marked published
    }
}
```

- [ ] **Step 11: Write test for outbox event creation on product create**

```java
// MarketplaceApplicationServiceOutboxTest.java
@SpringBootTest
@Testcontainers
class MarketplaceApplicationServiceOutboxTest {
    @Test
    void createProduct_shouldWriteOutboxEvent() {
        // Given: valid UpsertProductRequest
        // When: createProduct is called
        // Then: outbox_event table has one PRODUCT_CREATED row
    }
}
```

- [ ] **Step 12: Run all marketplace-service tests**

```bash
cd /Users/matthew/projects/meirongdev/shop && mvn test -pl marketplace-service -am
```

Expected: All tests pass.

- [ ] **Step 13: Commit**

```bash
git add marketplace-service/ shop-contracts/src/main/java/dev/meirong/shop/contracts/event/MarketplaceProductEventData.java docker-compose.yml
git commit -m "feat(marketplace): add Outbox pattern for product event publishing to Kafka"
```

---

### Task 2: Create search-service Maven Module

**Files:**
- Create: `search-service/pom.xml`
- Create: `search-service/src/main/java/dev/meirong/shop/search/SearchServiceApplication.java`
- Create: `search-service/src/main/resources/application.yml`
- Modify: `pom.xml` (root) — add `search-service` to modules list

- [ ] **Step 1: Add search-service module to root pom.xml**

Add `<module>search-service</module>` to the `<modules>` list in root `pom.xml`.

- [ ] **Step 2: Create search-service pom.xml**

Model after other services (e.g., marketplace-service). Key dependencies:

```xml
<parent>
    <groupId>dev.meirong.shop</groupId>
    <artifactId>shop-platform</artifactId>
    <version>0.1.0-SNAPSHOT</version>
</parent>
<artifactId>search-service</artifactId>

<dependencies>
    <dependency>
        <groupId>dev.meirong.shop</groupId>
        <artifactId>shop-common</artifactId>
        <version>${project.version}</version>
    </dependency>
    <dependency>
        <groupId>dev.meirong.shop</groupId>
        <artifactId>shop-contracts</artifactId>
        <version>${project.version}</version>
    </dependency>
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-web</artifactId>
    </dependency>
    <dependency>
        <groupId>org.springframework.kafka</groupId>
        <artifactId>spring-kafka</artifactId>
    </dependency>
    <dependency>
        <groupId>com.meilisearch.sdk</groupId>
        <artifactId>meilisearch-java</artifactId>
        <version>0.14.2</version>
    </dependency>
    <!-- test -->
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-test</artifactId>
        <scope>test</scope>
    </dependency>
    <dependency>
        <groupId>org.springframework.kafka</groupId>
        <artifactId>spring-kafka-test</artifactId>
        <scope>test</scope>
    </dependency>
    <dependency>
        <groupId>org.testcontainers</groupId>
        <artifactId>testcontainers</artifactId>
        <scope>test</scope>
    </dependency>
    <dependency>
        <groupId>org.testcontainers</groupId>
        <artifactId>kafka</artifactId>
        <scope>test</scope>
    </dependency>
    <dependency>
        <groupId>org.testcontainers</groupId>
        <artifactId>junit-jupiter</artifactId>
        <scope>test</scope>
    </dependency>
</dependencies>
```

- [ ] **Step 3: Create application entry point and config**

```java
// SearchServiceApplication.java
@SpringBootApplication
public class SearchServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(SearchServiceApplication.class, args);
    }
}
```

```yaml
# application.yml
server:
  port: ${SERVER_PORT:8080}

management:
  server:
    port: 8081

spring:
  application:
    name: search-service
  kafka:
    bootstrap-servers: ${KAFKA_BOOTSTRAP_SERVERS:localhost:9092}
    consumer:
      group-id: search-service
      auto-offset-reset: earliest
      key-deserializer: org.apache.kafka.common.serialization.StringDeserializer
      value-deserializer: org.apache.kafka.common.serialization.StringDeserializer

shop:
  search:
    meilisearch:
      url: ${MEILISEARCH_URL:http://localhost:7700}
      admin-key: ${MEILISEARCH_ADMIN_KEY:}
      search-key: ${MEILISEARCH_SEARCH_KEY:}
    product-topic: ${MARKETPLACE_PRODUCT_TOPIC:marketplace.product.events.v1}
    marketplace-service-url: ${MARKETPLACE_SERVICE_URL:http://marketplace-service:8080}
    internal-token: ${SHOP_INTERNAL_TOKEN:}
```

- [ ] **Step 4: Verify module compiles**

```bash
cd /Users/matthew/projects/meirongdev/shop && mvn compile -pl search-service -am
```

Expected: BUILD SUCCESS.

- [ ] **Step 5: Commit**

```bash
git add search-service/ pom.xml
git commit -m "feat(search): scaffold search-service Maven module"
```

---

### Task 3: Implement Meilisearch Client Configuration

**Files:**
- Create: `search-service/src/main/java/dev/meirong/shop/search/config/SearchProperties.java`
- Create: `search-service/src/main/java/dev/meirong/shop/search/config/MeilisearchConfig.java`
- Create: `search-service/src/main/java/dev/meirong/shop/search/index/ProductIndexSettings.java`
- Test: `search-service/src/test/java/dev/meirong/shop/search/config/MeilisearchConfigTest.java`

- [ ] **Step 1: Create configuration properties**

```java
// SearchProperties.java
@ConfigurationProperties(prefix = "shop.search")
public record SearchProperties(
    MeilisearchProperties meilisearch,
    String productTopic,
    String marketplaceServiceUrl,
    String internalToken
) {
    public record MeilisearchProperties(String url, String adminKey, String searchKey) {}
}
```

- [ ] **Step 2: Create Meilisearch client bean**

```java
// MeilisearchConfig.java
@Configuration
@EnableConfigurationProperties(SearchProperties.class)
public class MeilisearchConfig {

    @Bean("meilisearchAdminClient")
    public Client meilisearchAdminClient(SearchProperties props) {
        return new Client(new Config(props.meilisearch().url(), props.meilisearch().adminKey()));
    }

    @Bean("meilisearchSearchClient")
    public Client meilisearchSearchClient(SearchProperties props) {
        return new Client(new Config(props.meilisearch().url(), props.meilisearch().searchKey()));
    }
}
```

All injection points must use `@Qualifier("meilisearchAdminClient")` or `@Qualifier("meilisearchSearchClient")` to disambiguate.

- [ ] **Step 3: Create index settings initializer**

```java
// ProductIndexSettings.java
@Component
public class ProductIndexSettings {

    public static final String INDEX_NAME = "products";

    private final Client adminClient;

    @EventListener(ApplicationReadyEvent.class)
    public void initializeIndex() {
        var index = adminClient.index(INDEX_NAME);
        // Note: sellerName deferred to P2 (requires cross-service join with profile-service)
        index.updateSearchableAttributesSettings(
            new String[]{"name", "description", "categoryName"});
        index.updateFilterableAttributesSettings(
            new String[]{"categoryId", "sellerId", "published", "priceInCents"});
        index.updateSortableAttributesSettings(
            new String[]{"priceInCents", "createdAt", "name"});
        index.updateTypoToleranceSettings(new TypoTolerance().setEnabled(true));
    }
}
```

- [ ] **Step 4: Write test**

```java
// MeilisearchConfigTest.java — verify beans are created
@SpringBootTest(properties = {
    "shop.search.meilisearch.url=http://localhost:7700",
    "shop.search.meilisearch.admin-key=test-admin-key",
    "shop.search.meilisearch.search-key=test-search-key"
})
class MeilisearchConfigTest {
    @Autowired @Qualifier("meilisearchAdminClient") Client adminClient;
    @Autowired @Qualifier("meilisearchSearchClient") Client searchClient;

    @Test void adminClientIsConfigured() { assertNotNull(adminClient); }
    @Test void searchClientIsConfigured() { assertNotNull(searchClient); }
}
```

- [ ] **Step 5: Commit**

```bash
git add search-service/
git commit -m "feat(search): configure Meilisearch client beans and index settings"
```

---

### Task 4: Implement Kafka Consumer for Product Events

**Files:**
- Create: `search-service/src/main/java/dev/meirong/shop/search/consumer/ProductEventConsumer.java`
- Create: `search-service/src/main/java/dev/meirong/shop/search/index/ProductDocument.java`
- Create: `search-service/src/main/java/dev/meirong/shop/search/index/ProductIndexer.java`
- Test: `search-service/src/test/java/dev/meirong/shop/search/consumer/ProductEventConsumerTest.java`
- Test: `search-service/src/test/java/dev/meirong/shop/search/index/ProductIndexerTest.java`

- [ ] **Step 1: Create Meilisearch document model**

```java
// ProductDocument.java
public record ProductDocument(
    String id,
    String sellerId,
    String sellerName,
    String sku,
    String name,
    String description,
    long priceInCents,      // store as cents for filtering/sorting
    int inventory,
    boolean published,
    String categoryId,
    String categoryName,
    String imageUrl,
    String status,
    Instant createdAt
) {
    public static ProductDocument fromEventData(MarketplaceProductEventData data) {
        return new ProductDocument(
            data.productId(), data.sellerId(), /* sellerName */ "",
            data.sku(), data.name(), data.description(),
            data.price().movePointRight(2).longValue(),
            data.inventory(), data.published(),
            data.categoryId(), data.categoryName(),
            data.imageUrl(), data.status(), data.occurredAt()
        );
    }
}
```

- [ ] **Step 2: Create indexer service**

```java
// ProductIndexer.java
@Service
public class ProductIndexer {

    private final Client adminClient;
    private final ObjectMapper objectMapper;
    private static final String INDEX = ProductIndexSettings.INDEX_NAME;

    public void index(ProductDocument doc) {
        adminClient.index(INDEX).addDocuments(objectMapper.writeValueAsString(List.of(doc)), "id");
    }

    public void remove(String productId) {
        adminClient.index(INDEX).deleteDocument(productId);
    }

    public void indexBatch(List<ProductDocument> docs) {
        adminClient.index(INDEX).addDocuments(objectMapper.writeValueAsString(docs), "id");
    }
}
```

- [ ] **Step 3: Create Kafka consumer**

```java
// ProductEventConsumer.java
@Component
public class ProductEventConsumer {

    private final ProductIndexer indexer;
    private final ObjectMapper objectMapper;

    private final KafkaTemplate<String, String> kafkaTemplate;

    @RetryableTopic(
        attempts = "4",
        backoff = @Backoff(delay = 1000, multiplier = 2),
        dltTopicSuffix = ".dlq",
        autoCreateTopics = "true"
    )
    @KafkaListener(topics = "${shop.search.product-topic}", groupId = "search-service")
    public void consume(String message) {
        var envelope = objectMapper.readValue(message,
            new TypeReference<EventEnvelope<MarketplaceProductEventData>>() {});
        var data = envelope.data();
        var doc = ProductDocument.fromEventData(data);

        switch (envelope.type()) {
            case "PRODUCT_CREATED", "PRODUCT_UPDATED", "PRODUCT_PUBLISHED" -> indexer.index(doc);
            case "PRODUCT_DELETED", "PRODUCT_UNPUBLISHED" -> indexer.remove(data.productId());
        }
    }

    @DltHandler
    public void handleDlt(String message) {
        // Log failed message for manual investigation
        log.error("Message sent to DLQ: {}", message);
    }
}
```

- [ ] **Step 4: Write unit test for ProductIndexer**

```java
// ProductIndexerTest.java
class ProductIndexerTest {
    // Mock Meilisearch Client, verify addDocuments/deleteDocument calls
    @Test void index_shouldCallAddDocuments() { ... }
    @Test void remove_shouldCallDeleteDocument() { ... }
}
```

- [ ] **Step 5: Write unit test for consumer event routing**

```java
// ProductEventConsumerTest.java
class ProductEventConsumerTest {
    // Mock ProductIndexer, verify correct method called per event type
    @Test void consume_productCreated_shouldIndex() { ... }
    @Test void consume_productDeleted_shouldRemove() { ... }
    @Test void consume_productUnpublished_shouldRemove() { ... }
}
```

- [ ] **Step 6: Run tests**

```bash
cd /Users/matthew/projects/meirongdev/shop && mvn test -pl search-service -am
```

- [ ] **Step 7: Commit**

```bash
git add search-service/
git commit -m "feat(search): implement Kafka consumer and Meilisearch product indexer"
```

---

### Task 5: Implement Search API

**Files:**
- Create: `search-service/src/main/java/dev/meirong/shop/search/controller/SearchController.java`
- Create: `search-service/src/main/java/dev/meirong/shop/search/service/ProductSearchService.java`
- Create: `shop-contracts/src/main/java/dev/meirong/shop/contracts/api/SearchApi.java`
- Test: `search-service/src/test/java/dev/meirong/shop/search/service/ProductSearchServiceTest.java`
- Test: `search-service/src/test/java/dev/meirong/shop/search/controller/SearchControllerTest.java`

- [ ] **Step 1: Add search API contract to shop-contracts**

```java
// SearchApi.java
public final class SearchApi {
    public static final String BASE_PATH = "/search/v1";
    public static final String SEARCH_PRODUCTS = BASE_PATH + "/products";
    public static final String REINDEX_PRODUCTS = BASE_PATH + "/products/_reindex";
    public static final String HEALTH = BASE_PATH + "/health";

    public record SearchProductsRequest(
        String q,
        String categoryId,
        String sort,
        int page,
        int hitsPerPage
    ) {}

    public record SearchProductsResponse(
        List<ProductHit> hits,
        long totalHits,
        int page,
        int totalPages,
        Map<String, Map<String, Integer>> facetDistribution
    ) {}

    public record ProductHit(
        String id, String sellerId, String name, String description,
        BigDecimal price, int inventory, String categoryId, String categoryName,
        String imageUrl, String status
    ) {}
}
```

- [ ] **Step 2: Implement search service**

```java
// ProductSearchService.java
@Service
public class ProductSearchService {

    private final Client searchClient;
    private final ObjectMapper objectMapper;

    public SearchApi.SearchProductsResponse search(SearchApi.SearchProductsRequest request) {
        var searchRequest = new SearchRequest(request.q())
            .setPage(request.page())
            .setHitsPerPage(request.hitsPerPage())
            .setFacets(new String[]{"categoryId"});

        if (request.categoryId() != null) {
            searchRequest.setFilter(new String[]{"categoryId = " + request.categoryId()});
        }
        if (request.sort() != null) {
            searchRequest.setSort(new String[]{request.sort()});
        }

        var result = searchClient.index(ProductIndexSettings.INDEX_NAME).search(searchRequest);
        var hits = result.getHits().stream()
            .map(hit -> objectMapper.convertValue(hit, SearchApi.ProductHit.class))
            .toList();

        return new SearchApi.SearchProductsResponse(
            hits, result.getTotalHits(),
            result.getPage(), result.getTotalPages(),
            result.getFacetDistribution()
        );
    }
}
```

- [ ] **Step 3: Implement search controller**

```java
// SearchController.java
@RestController
public class SearchController {

    private final ProductSearchService searchService;

    @GetMapping(SearchApi.SEARCH_PRODUCTS)
    public ApiResponse<SearchApi.SearchProductsResponse> searchProducts(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) String categoryId,
            @RequestParam(required = false) String sort,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int hitsPerPage) {
        var request = new SearchApi.SearchProductsRequest(q, categoryId, sort, page, hitsPerPage);
        return ApiResponse.success(searchService.search(request));
    }

    @GetMapping(SearchApi.HEALTH)
    public ApiResponse<String> health() {
        return ApiResponse.success("ok");
    }
}
```

- [ ] **Step 4: Write unit tests**

```java
// ProductSearchServiceTest.java — mock Meilisearch search client
@Test void search_withQuery_shouldReturnResults() { ... }
@Test void search_withCategoryFilter_shouldApplyFilter() { ... }
@Test void search_withSort_shouldApplySort() { ... }
```

```java
// SearchControllerTest.java — @WebMvcTest
@Test void searchProducts_shouldReturn200() { ... }
@Test void health_shouldReturn200() { ... }
```

- [ ] **Step 5: Run tests**

```bash
cd /Users/matthew/projects/meirongdev/shop && mvn test -pl search-service -am
```

- [ ] **Step 6: Commit**

```bash
git add search-service/ shop-contracts/
git commit -m "feat(search): implement product search API with Meilisearch"
```

---

### Task 6: Implement Full Reindex with Blue-Green Swap

**Files:**
- Create: `search-service/src/main/java/dev/meirong/shop/search/service/ReindexService.java`
- Create: `search-service/src/main/java/dev/meirong/shop/search/client/MarketplaceInternalClient.java`
- Modify: `search-service/src/main/java/dev/meirong/shop/search/controller/SearchController.java` — add reindex endpoint
- Modify: `marketplace-service/src/main/java/dev/meirong/shop/marketplace/controller/MarketplaceController.java` — add internal paginated list endpoint
- Create: `shop-contracts/src/main/java/dev/meirong/shop/contracts/api/MarketplaceInternalApi.java`
- Test: `search-service/src/test/java/dev/meirong/shop/search/service/ReindexServiceTest.java`

- [ ] **Step 1: Add internal API contract for marketplace paginated listing**

```java
// MarketplaceInternalApi.java
public final class MarketplaceInternalApi {
    public static final String BASE_PATH = "/marketplace/internal";
    public static final String LIST_ALL_PRODUCTS = BASE_PATH + "/products";

    public record PagedProductsResponse(
        List<MarketplaceApi.ProductResponse> products,
        int page,
        int totalPages,
        long totalElements
    ) {}
}
```

- [ ] **Step 2: Add internal endpoint to marketplace-service**

```java
// Add to MarketplaceController.java
@GetMapping(MarketplaceInternalApi.LIST_ALL_PRODUCTS)
public ApiResponse<MarketplaceInternalApi.PagedProductsResponse> listAllProducts(
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "500") int size) {
    var pageable = PageRequest.of(page, size, Sort.by("createdAt").ascending());
    var result = productRepository.findAll(pageable);
    var products = result.getContent().stream()
        .map(MarketplaceProductMapper::toResponse)
        .toList();
    return ApiResponse.success(new MarketplaceInternalApi.PagedProductsResponse(
        products, result.getNumber(), result.getTotalPages(), result.getTotalElements()
    ));
}
```

- [ ] **Step 3: Create marketplace internal client in search-service**

```java
// MarketplaceInternalClient.java
@Component
public class MarketplaceInternalClient {

    private final RestClient restClient;

    public MarketplaceInternalClient(SearchProperties props) {
        this.restClient = RestClient.builder()
            .baseUrl(props.marketplaceServiceUrl())
            .defaultHeader("X-Internal-Token", props.internalToken())
            .build();
    }

    public MarketplaceInternalApi.PagedProductsResponse fetchProducts(int page, int size) {
        var response = restClient.get()
            .uri(MarketplaceInternalApi.LIST_ALL_PRODUCTS + "?page={page}&size={size}", page, size)
            .retrieve()
            .body(new ParameterizedTypeReference<ApiResponse<MarketplaceInternalApi.PagedProductsResponse>>() {});
        return response.data();
    }
}
```

- [ ] **Step 4: Implement blue-green reindex service**

```java
// ReindexService.java
@Service
public class ReindexService {

    private final Client adminClient;
    private final MarketplaceInternalClient marketplaceClient;
    private final ObjectMapper objectMapper;

    public void reindex() {
        var tempIndex = "products_" + Instant.now().toEpochMilli();

        // 1. Create temp index with settings
        adminClient.createIndex(tempIndex, "id");
        // Copy settings from ProductIndexSettings

        // 2. Paginate through all products
        int page = 0;
        MarketplaceInternalApi.PagedProductsResponse response;
        do {
            response = marketplaceClient.fetchProducts(page, 500);
            var docs = response.products().stream()
                .map(ProductDocument::fromProductResponse)
                .toList();
            if (!docs.isEmpty()) {
                adminClient.index(tempIndex)
                    .addDocuments(objectMapper.writeValueAsString(docs), "id");
            }
            page++;
        } while (page < response.totalPages());

        // 3. Wait for indexing tasks to complete
        // Meilisearch tasks are async, poll until done

        // 4. Swap indexes atomically
        adminClient.swapIndexes(new SwapIndexesParams[]{
            new SwapIndexesParams(ProductIndexSettings.INDEX_NAME, tempIndex)
        });

        // 5. Delete old (now temp-named) index
        adminClient.deleteIndex(tempIndex);
    }
}
```

- [ ] **Step 5: Add reindex endpoint to controller**

```java
// Add to SearchController.java
@PostMapping(SearchApi.REINDEX_PRODUCTS)
public ApiResponse<String> reindex() {
    reindexService.reindex();
    return ApiResponse.success("reindex started");
}
```

- [ ] **Step 6: Write test**

```java
// ReindexServiceTest.java — mock MarketplaceInternalClient and Meilisearch Client
@Test void reindex_shouldCreateTempIndex_fetchAllPages_andSwap() { ... }
```

- [ ] **Step 7: Run tests**

```bash
cd /Users/matthew/projects/meirongdev/shop && mvn test -pl search-service,marketplace-service -am
```

- [ ] **Step 8: Commit**

```bash
git add search-service/ marketplace-service/ shop-contracts/
git commit -m "feat(search): implement blue-green full reindex with marketplace internal API"
```

---

### Task 7: Modify BFF Search to Proxy Through search-service

**Important:** BFF already has `searchProducts` endpoints (buyer-bff `BuyerController.java` line 87-89, `BuyerAggregationService.java` line 114-116). We are **modifying the existing implementation** to call search-service instead of marketplace-service, NOT adding new endpoints. All BFF endpoints use `@PostMapping` — we maintain this convention.

**Files:**
- Modify: `buyer-bff/src/main/java/dev/meirong/shop/buyerbff/config/BuyerClientProperties.java` — add search-service-url
- Modify: `buyer-bff/src/main/java/dev/meirong/shop/buyerbff/config/BuyerBffConfig.java` — add search RestClient bean
- Modify: `buyer-bff/src/main/java/dev/meirong/shop/buyerbff/service/BuyerAggregationService.java` — modify existing searchProducts to call search-service
- Modify: `buyer-bff/pom.xml` — add Resilience4j dependency
- Modify: `seller-bff/src/main/java/dev/meirong/shop/sellerbff/config/SellerClientProperties.java` — add search-service-url
- Modify: `seller-bff/src/main/java/dev/meirong/shop/sellerbff/config/SellerBffConfig.java` — add search RestClient bean
- Modify: `seller-bff/src/main/java/dev/meirong/shop/sellerbff/service/SellerAggregationService.java` — add search proxy
- Modify: `seller-bff/pom.xml` — add Resilience4j dependency
- Modify: `buyer-bff/src/main/resources/application.yml` — add search-service-url config
- Modify: `seller-bff/src/main/resources/application.yml` — add search-service-url config
- Modify: `docker-compose.yml` — add search-service-url env vars to BFFs
- Test: `buyer-bff/src/test/java/dev/meirong/shop/buyerbff/service/BuyerSearchProxyTest.java`

- [ ] **Step 1: Add Resilience4j dependency to BFF pom.xml files**

Add to both `buyer-bff/pom.xml` and `seller-bff/pom.xml`:

```xml
<dependency>
    <groupId>io.github.resilience4j</groupId>
    <artifactId>resilience4j-spring-boot3</artifactId>
</dependency>
```

- [ ] **Step 2: Add search-service-url to BFF properties**

In `BuyerClientProperties.java`, add field `searchServiceUrl`.
In `SellerClientProperties.java`, add field `searchServiceUrl`.

- [ ] **Step 3: Modify existing searchProducts in BuyerAggregationService**

Replace the existing `searchProducts` method body (which currently calls marketplace-service) to call search-service with circuit breaker fallback:

```java
// Modify existing method in BuyerAggregationService.java
@CircuitBreaker(name = "searchService", fallbackMethod = "searchProductsFallback")
public ApiResponse<SearchApi.SearchProductsResponse> searchProducts(MarketplaceApi.SearchProductsRequest request) {
    var uri = UriComponentsBuilder.fromUriString(clientProperties.getSearchServiceUrl() + SearchApi.SEARCH_PRODUCTS)
        .queryParamIfPresent("q", Optional.ofNullable(request.query()))
        .queryParamIfPresent("categoryId", Optional.ofNullable(request.categoryId()))
        .queryParam("page", request.page())
        .queryParam("hitsPerPage", request.size())
        .build().toUriString();

    return searchRestClient.get().uri(uri)
        .header("X-Internal-Token", clientProperties.getInternalToken())
        .retrieve()
        .body(new ParameterizedTypeReference<>() {});
}

// Fallback: degrade to marketplace-service LIKE search
private ApiResponse<SearchApi.SearchProductsResponse> searchProductsFallback(
        MarketplaceApi.SearchProductsRequest request, Throwable t) {
    // Call existing marketplace-service search as before
    return marketplaceRestClient.post()
        .uri(clientProperties.getMarketplaceServiceUrl() + MarketplaceApi.SEARCH)
        .body(request)
        .retrieve()
        .body(new ParameterizedTypeReference<>() {});
}
```

The existing `BuyerController.searchProducts` POST endpoint needs no change — it already delegates to `aggregationService.searchProducts()`.

Repeat the same pattern for `SellerAggregationService`.

- [ ] **Step 4: Update application.yml and docker-compose.yml**

Add to buyer-bff and seller-bff application.yml:
```yaml
shop.buyer.search-service-url: ${SEARCH_SERVICE_URL:http://search-service:8091}
```

Add to docker-compose.yml buyer-bff and seller-bff environments:
```yaml
SEARCH_SERVICE_URL: http://search-service:8091
```

Also add `search-service` to their `depends_on`.

- [ ] **Step 5: Write test**

```java
// BuyerSearchProxyTest.java
@Test void searchProducts_shouldProxyToSearchService() { ... }
@Test void searchProducts_whenSearchServiceDown_shouldFallbackToMarketplace() { ... }
```

- [ ] **Step 6: Run tests**

```bash
cd /Users/matthew/projects/meirongdev/shop && mvn test -pl buyer-bff,seller-bff -am
```

- [ ] **Step 7: Commit**

```bash
git add buyer-bff/ seller-bff/ docker-compose.yml
git commit -m "feat(bff): proxy product search to search-service with circuit breaker fallback"
```

---

### Task 8: Infrastructure — Docker Compose + K8s

**Files:**
- Modify: `docker-compose.yml` — add meilisearch, search-service, update depends_on
- Create: `search-service/Dockerfile`
- Modify: `k8s/infra/base.yaml` — add Meilisearch StatefulSet
- Modify: `k8s/apps/platform.yaml` — add search-service Deployment + Service

- [ ] **Step 1: Verify shared Dockerfile works for search-service**

All services use the shared `docker/Dockerfile.module`. No new Dockerfile needed. search-service runs on port 8080 internally (matching all other services) and is mapped to 8091 externally in docker-compose. The shared Dockerfile already exposes 8080 and 8081.

- [ ] **Step 2: Add meilisearch and search-service to docker-compose.yml**

```yaml
meilisearch:
  image: getmeili/meilisearch:v1.12
  ports:
    - "7700:7700"
  volumes:
    - meili_data:/meili_data
  environment:
    MEILI_ENV: production
    MEILI_MASTER_KEY: ${MEILI_MASTER_KEY:-masterKey123}
  healthcheck:
    test: ["CMD", "curl", "-f", "http://localhost:7700/health"]
    interval: 10s
    timeout: 5s
    retries: 5

search-service:
  build:
    context: .
    dockerfile: docker/Dockerfile.module
    args:
      MODULE: search-service
  ports:
    - "8091:8080"
  environment:
    KAFKA_BOOTSTRAP_SERVERS: kafka:9092
    MEILISEARCH_URL: http://meilisearch:7700
    MEILISEARCH_ADMIN_KEY: ${MEILISEARCH_ADMIN_KEY:-adminKey123}
    MEILISEARCH_SEARCH_KEY: ${MEILISEARCH_SEARCH_KEY:-searchKey123}
    MARKETPLACE_SERVICE_URL: http://marketplace-service:8080
    SHOP_INTERNAL_TOKEN: ${SHOP_INTERNAL_TOKEN}
    OTEL_EXPORTER_OTLP_ENDPOINT: http://otel-collector:4318/v1/traces
  depends_on:
    kafka:
      condition: service_healthy
    meilisearch:
      condition: service_healthy
  healthcheck:
    test: ["CMD", "curl", "-f", "http://localhost:8081/actuator/health/readiness"]
    interval: 15s
    timeout: 5s
    retries: 5
```

Add `meili_data` to the top-level `volumes` section.

- [ ] **Step 3: Add Meilisearch to k8s/infra/base.yaml**

```yaml
# Meilisearch StatefulSet
apiVersion: apps/v1
kind: StatefulSet
metadata:
  name: meilisearch
  namespace: shop
spec:
  serviceName: meilisearch
  replicas: 1
  selector:
    matchLabels:
      app: meilisearch
  template:
    metadata:
      labels:
        app: meilisearch
    spec:
      containers:
        - name: meilisearch
          image: getmeili/meilisearch:v1.12
          ports:
            - containerPort: 7700
          env:
            - name: MEILI_ENV
              value: production
            - name: MEILI_MASTER_KEY
              valueFrom:
                secretKeyRef:
                  name: shop-shared-secret
                  key: meili-master-key
          volumeMounts:
            - name: meili-data
              mountPath: /meili_data
  volumeClaimTemplates:
    - metadata:
        name: meili-data
      spec:
        accessModes: ["ReadWriteOnce"]
        resources:
          requests:
            storage: 5Gi
---
apiVersion: v1
kind: Service
metadata:
  name: meilisearch
  namespace: shop
spec:
  type: ClusterIP
  selector:
    app: meilisearch
  ports:
    - port: 7700
      targetPort: 7700
```

- [ ] **Step 4: Add search-service to k8s/apps/platform.yaml**

Model after other service deployments. Port 8091, management port 8081.

- [ ] **Step 5: Update .env.example with new env vars**

Add:
```
MEILI_MASTER_KEY=masterKey123
MEILISEARCH_ADMIN_KEY=adminKey123
MEILISEARCH_SEARCH_KEY=searchKey123
```

- [ ] **Step 6: Commit**

```bash
git add docker-compose.yml search-service/Dockerfile k8s/ .env.example
git commit -m "infra: add Meilisearch and search-service to Docker Compose and K8s"
```

---

### Task 9: Integration Test — Full Pipeline

**Files:**
- Create: `search-service/src/test/java/dev/meirong/shop/search/integration/SearchPipelineIntegrationTest.java`

- [ ] **Step 1: Write full pipeline integration test**

Uses Testcontainers for Kafka + Meilisearch (Docker-in-Docker):

```java
@SpringBootTest
@Testcontainers
class SearchPipelineIntegrationTest {

    @Container static KafkaContainer kafka = new KafkaContainer(...);
    // Meilisearch container via GenericContainer
    @Container static GenericContainer<?> meilisearch = new GenericContainer<>("getmeili/meilisearch:v1.12")
        .withExposedPorts(7700)
        .withEnv("MEILI_ENV", "development")
        .withEnv("MEILI_MASTER_KEY", "testMasterKey");

    @Test
    void fullPipeline_publishEvent_thenSearch() {
        // 1. Send a PRODUCT_CREATED event to Kafka
        // 2. Wait for consumer to process (poll Meilisearch until document appears)
        // 3. Call search API with product name
        // 4. Assert: hit count = 1, product data matches
    }

    @Test
    void fullPipeline_publishUnpublishEvent_thenSearchReturnsEmpty() {
        // 1. Send PRODUCT_CREATED then PRODUCT_UNPUBLISHED
        // 2. Search should return 0 hits
    }
}
```

- [ ] **Step 2: Run integration test**

```bash
cd /Users/matthew/projects/meirongdev/shop && mvn verify -pl search-service -am -Dtest=SearchPipelineIntegrationTest
```

Expected: Tests pass.

- [ ] **Step 3: Commit**

```bash
git add search-service/
git commit -m "test(search): add full pipeline integration test with Testcontainers"
```

---

## Workstream B: KMP Foundation (P0)

### Task 10: Gradle Build Infrastructure

**Files:**
- Create: `settings.gradle.kts`
- Create: `gradle.properties`
- Create: `build.gradle.kts`
- Create: `gradle/libs.versions.toml` (version catalog)
- Modify: `.gitignore` — add `build/`, `.gradle/`

- [ ] **Step 1: Add Gradle entries to .gitignore**

Append to `.gitignore`:
```
# Gradle
build/
.gradle/
kmp/*/build/
local.properties
```

- [ ] **Step 2: Create gradle/libs.versions.toml (version catalog)**

```toml
[versions]
kotlin = "2.3.20"
compose-multiplatform = "1.8.0"
ktor = "3.1.1"
koin = "4.1.0"
navigation = "2.9.0"
lifecycle = "2.9.0"
kotlinx-serialization = "1.8.1"
coil = "3.2.0"

[libraries]
ktor-client-core = { module = "io.ktor:ktor-client-core", version.ref = "ktor" }
ktor-client-content-negotiation = { module = "io.ktor:ktor-client-content-negotiation", version.ref = "ktor" }
ktor-client-auth = { module = "io.ktor:ktor-client-auth", version.ref = "ktor" }
ktor-serialization-json = { module = "io.ktor:ktor-serialization-kotlinx-json", version.ref = "ktor" }
ktor-client-okhttp = { module = "io.ktor:ktor-client-okhttp", version.ref = "ktor" }
ktor-client-darwin = { module = "io.ktor:ktor-client-darwin", version.ref = "ktor" }
ktor-client-js = { module = "io.ktor:ktor-client-js", version.ref = "ktor" }
ktor-client-mock = { module = "io.ktor:ktor-client-mock", version.ref = "ktor" }

koin-core = { module = "io.insert-koin:koin-core", version.ref = "koin" }
koin-compose = { module = "io.insert-koin:koin-compose", version.ref = "koin" }

navigation-compose = { module = "org.jetbrains.androidx.navigation:navigation-compose", version.ref = "navigation" }
lifecycle-viewmodel-compose = { module = "org.jetbrains.androidx.lifecycle:lifecycle-viewmodel-compose", version.ref = "lifecycle" }

kotlinx-serialization-json = { module = "org.jetbrains.kotlinx:kotlinx-serialization-json", version.ref = "kotlinx-serialization" }

coil-compose = { module = "io.coil-kt.coil3:coil-compose", version.ref = "coil" }
coil-network-ktor = { module = "io.coil-kt.coil3:coil-network-ktor3", version.ref = "coil" }

kotlin-test = { module = "org.jetbrains.kotlin:kotlin-test", version.ref = "kotlin" }

[plugins]
kotlin-multiplatform = { id = "org.jetbrains.kotlin.multiplatform", version.ref = "kotlin" }
kotlin-serialization = { id = "org.jetbrains.kotlin.plugin.serialization", version.ref = "kotlin" }
compose-multiplatform = { id = "org.jetbrains.compose", version.ref = "compose-multiplatform" }
compose-compiler = { id = "org.jetbrains.kotlin.plugin.compose", version.ref = "kotlin" }
android-application = { id = "com.android.application", version = "8.9.1" }
android-library = { id = "com.android.library", version = "8.9.1" }
```

- [ ] **Step 3: Create settings.gradle.kts**

```kotlin
rootProject.name = "shop-kmp"

pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
    }
}

include(":kmp:core")
include(":kmp:ui-shared")
include(":kmp:buyer-app")
include(":kmp:seller-app")
```

- [ ] **Step 4: Create gradle.properties**

```properties
org.gradle.jvmargs=-Xmx4g
kotlin.code.style=official
kotlin.mpp.stability.nowarn=true
android.useAndroidX=true
```

- [ ] **Step 5: Create root build.gradle.kts**

```kotlin
plugins {
    alias(libs.plugins.kotlin.multiplatform) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.compose.multiplatform) apply false
    alias(libs.plugins.compose.compiler) apply false
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
}
```

Also create `local.properties` (gitignored) pointing to Android SDK:
```properties
sdk.dir=/Users/<username>/Library/Android/sdk
```

- [ ] **Step 6: Install Gradle wrapper**

```bash
cd /Users/matthew/projects/meirongdev/shop && gradle wrapper --gradle-version 8.14
```

- [ ] **Step 7: Verify Gradle syncs**

```bash
cd /Users/matthew/projects/meirongdev/shop && ./gradlew tasks
```

Expected: Task list appears without errors.

- [ ] **Step 8: Commit**

```bash
git add settings.gradle.kts build.gradle.kts gradle.properties gradle/ .gitignore
git commit -m "build: initialize Gradle KMP build infrastructure with version catalog"
```

---

### Task 11: core Module — Networking, Models, DI

**Files:**
- Create: `kmp/core/build.gradle.kts`
- Create: `kmp/core/src/commonMain/kotlin/dev/meirong/shop/kmp/core/network/HttpClientFactory.kt`
- Create: `kmp/core/src/commonMain/kotlin/dev/meirong/shop/kmp/core/network/ApiResponse.kt`
- Create: `kmp/core/src/commonMain/kotlin/dev/meirong/shop/kmp/core/network/NetworkError.kt`
- Create: `kmp/core/src/commonMain/kotlin/dev/meirong/shop/kmp/core/model/Product.kt`
- Create: `kmp/core/src/commonMain/kotlin/dev/meirong/shop/kmp/core/model/SearchResult.kt`
- Create: `kmp/core/src/commonMain/kotlin/dev/meirong/shop/kmp/core/di/CoreModule.kt`
- Create: `kmp/core/src/commonMain/kotlin/dev/meirong/shop/kmp/core/session/TokenStorage.kt`
- Create: `kmp/core/src/androidMain/kotlin/dev/meirong/shop/kmp/core/network/HttpEngineFactory.android.kt`
- Create: `kmp/core/src/iosMain/kotlin/dev/meirong/shop/kmp/core/network/HttpEngineFactory.ios.kt`
- Create: `kmp/core/src/wasmJsMain/kotlin/dev/meirong/shop/kmp/core/network/HttpEngineFactory.wasmJs.kt`
- Test: `kmp/core/src/commonTest/kotlin/dev/meirong/shop/kmp/core/network/HttpClientFactoryTest.kt`
- Test: `kmp/core/src/commonTest/kotlin/dev/meirong/shop/kmp/core/model/ProductSerializationTest.kt`

- [ ] **Step 1: Create core build.gradle.kts**

```kotlin
plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.android.library)
}

android {
    namespace = "dev.meirong.shop.kmp.core"
    compileSdk = 35
    defaultConfig { minSdk = 26 }
}

kotlin {
    androidTarget()
    iosArm64()
    iosSimulatorArm64()
    wasmJs { browser() }

    sourceSets {
        commonMain.dependencies {
            implementation(libs.ktor.client.core)
            implementation(libs.ktor.client.content.negotiation)
            implementation(libs.ktor.client.auth)
            implementation(libs.ktor.serialization.json)
            implementation(libs.koin.core)
            implementation(libs.kotlinx.serialization.json)
        }
        androidMain.dependencies {
            implementation(libs.ktor.client.okhttp)
        }
        iosMain.dependencies {
            implementation(libs.ktor.client.darwin)
        }
        wasmJsMain.dependencies {
            implementation(libs.ktor.client.js)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
            implementation(libs.ktor.client.mock)
        }
    }
}
```

- [ ] **Step 2: Create ApiResponse model (mirrors backend)**

```kotlin
// ApiResponse.kt
@Serializable
data class ApiResponse<T>(
    val traceId: String? = null,
    val status: String,
    val message: String,
    val data: T? = null
)
```

- [ ] **Step 3: Create Product and SearchResult models**

```kotlin
// Product.kt — mirrors MarketplaceApi.ProductResponse
@Serializable
data class Product(
    val id: String,
    val sellerId: String,
    val sku: String,
    val name: String,
    val description: String,
    val priceInCents: Long,  // Store as cents to avoid floating-point precision issues
    val inventory: Int,
    val published: Boolean,
    val categoryId: String? = null,
    val categoryName: String? = null,
    val imageUrl: String? = null,
    val status: String? = null
)

// SearchResult.kt — mirrors SearchApi.SearchProductsResponse
@Serializable
data class SearchResult(
    val hits: List<ProductHit>,
    val totalHits: Long,
    val page: Int,
    val totalPages: Int
)

@Serializable
data class ProductHit(
    val id: String,
    val name: String,
    val description: String,
    val price: Double,
    val categoryId: String? = null,
    val categoryName: String? = null,
    val imageUrl: String? = null
)
```

- [ ] **Step 4: Create HttpClientFactory with expect/actual engine**

```kotlin
// HttpEngineFactory — expect
expect fun createHttpEngine(): HttpClientEngine

// HttpClientFactory.kt
object HttpClientFactory {
    fun create(tokenStorage: TokenStorage): HttpClient = HttpClient(createHttpEngine()) {
        install(ContentNegotiation) {
            json(Json {
                ignoreUnknownKeys = true
                isLenient = true
            })
        }
        install(Auth) {
            bearer {
                loadTokens { tokenStorage.loadTokens() }
                refreshTokens { tokenStorage.refreshTokens(client) }
            }
        }
        install(HttpRequestRetry) {
            retryOnServerErrors(maxRetries = 2)
            exponentialDelay()
        }
    }
}
```

Platform actuals:
```kotlin
// android: actual fun createHttpEngine() = OkHttp.create()
// ios: actual fun createHttpEngine() = Darwin.create()
// wasmJs: actual fun createHttpEngine() = Js.create()
```

- [ ] **Step 5: Create TokenStorage interface**

```kotlin
// TokenStorage.kt
interface TokenStorage {
    suspend fun loadTokens(): BearerTokens?
    suspend fun refreshTokens(client: HttpClient): BearerTokens?
    suspend fun saveTokens(access: String, refresh: String)
    suspend fun clear()
}
```

- [ ] **Step 6: Create Koin DI module**

```kotlin
// CoreModule.kt
val coreModule = module {
    single<TokenStorage> { get() } // platform-specific impl provided by app module
    single { HttpClientFactory.create(get()) }
}
```

- [ ] **Step 7: Write serialization test**

```kotlin
// ProductSerializationTest.kt
class ProductSerializationTest {
    @Test
    fun deserializeApiResponse() {
        val json = """{"traceId":"abc","status":"SC_OK","message":"Success","data":{"id":"1","sellerId":"s1","sku":"SKU1","name":"Test","description":"Desc","priceInCents":999,"inventory":10,"published":true}}"""
        val response = Json.decodeFromString<ApiResponse<Product>>(json)
        assertEquals("1", response.data?.id)
        assertEquals(999L, response.data?.priceInCents)
    }
}
```

- [ ] **Step 8: Write HttpClient test with MockEngine**

```kotlin
// HttpClientFactoryTest.kt
class HttpClientFactoryTest {
    @Test
    fun shouldDeserializeSuccessResponse() = runTest {
        val mockEngine = MockEngine { respond(content = """{"traceId":"t","status":"SC_OK","message":"ok","data":[]}""") }
        val client = HttpClient(mockEngine) { install(ContentNegotiation) { json() } }
        val response = client.get("/test").body<ApiResponse<List<String>>>()
        assertEquals("SC_OK", response.status)
    }
}
```

- [ ] **Step 9: Verify core compiles on all targets**

```bash
cd /Users/matthew/projects/meirongdev/shop && ./gradlew :kmp:core:build
```

- [ ] **Step 10: Commit**

```bash
git add kmp/core/
git commit -m "feat(kmp): implement core module with networking, models, DI, and token storage"
```

---

### Task 12: ui-shared Module — Theme and Common Components

**Files:**
- Create: `kmp/ui-shared/build.gradle.kts`
- Create: `kmp/ui-shared/src/commonMain/kotlin/dev/meirong/shop/kmp/ui/theme/ShopTheme.kt`
- Create: `kmp/ui-shared/src/commonMain/kotlin/dev/meirong/shop/kmp/ui/theme/Color.kt`
- Create: `kmp/ui-shared/src/commonMain/kotlin/dev/meirong/shop/kmp/ui/theme/Typography.kt`
- Create: `kmp/ui-shared/src/commonMain/kotlin/dev/meirong/shop/kmp/ui/components/ProductCard.kt`
- Create: `kmp/ui-shared/src/commonMain/kotlin/dev/meirong/shop/kmp/ui/components/SearchBar.kt`
- Create: `kmp/ui-shared/src/commonMain/kotlin/dev/meirong/shop/kmp/ui/components/PriceTag.kt`
- Create: `kmp/ui-shared/src/commonMain/kotlin/dev/meirong/shop/kmp/ui/components/LoadingIndicator.kt`
- Create: `kmp/ui-shared/src/commonMain/kotlin/dev/meirong/shop/kmp/ui/components/ErrorScreen.kt`

- [ ] **Step 1: Create ui-shared build.gradle.kts**

```kotlin
plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.compose.compiler)
}

kotlin {
    androidTarget()
    iosArm64()
    iosSimulatorArm64()
    wasmJs { browser() }

    sourceSets {
        commonMain.dependencies {
            implementation(project(":kmp:core"))
            implementation(compose.runtime)
            implementation(compose.foundation)
            implementation(compose.material3)
            implementation(compose.ui)
            implementation(compose.components.resources)
            implementation(libs.coil.compose)
            implementation(libs.coil.network.ktor)
        }
    }
}
```

- [ ] **Step 2: Create theme files**

```kotlin
// Color.kt
object ShopColors {
    val Primary = Color(0xFF1A73E8)
    val OnPrimary = Color.White
    val Surface = Color(0xFFFAFAFA)
    val Error = Color(0xFFD32F2F)
    // ... minimal set, extend as needed
}

// Typography.kt
object ShopTypography {
    // Use MaterialTheme defaults, customize later
}

// ShopTheme.kt
@Composable
fun ShopTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = lightColorScheme(
            primary = ShopColors.Primary,
            onPrimary = ShopColors.OnPrimary,
            surface = ShopColors.Surface,
            error = ShopColors.Error
        ),
        content = content
    )
}
```

- [ ] **Step 3: Create stub UI components**

Minimal composable stubs — just enough to verify compilation and usage in app modules. These will be fleshed out in P2-P4.

```kotlin
// ProductCard.kt
@Composable
fun ProductCard(name: String, price: String, imageUrl: String?, onClick: () -> Unit) {
    Card(onClick = onClick, modifier = Modifier.fillMaxWidth().padding(8.dp)) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(name, style = MaterialTheme.typography.titleMedium)
            PriceTag(price)
        }
    }
}

// SearchBar.kt
@Composable
fun SearchBar(query: String, onQueryChange: (String) -> Unit, onSearch: () -> Unit) {
    OutlinedTextField(
        value = query, onValueChange = onQueryChange,
        placeholder = { Text("Search products...") },
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
        keyboardActions = KeyboardActions(onSearch = { onSearch() })
    )
}

// PriceTag.kt
@Composable
fun PriceTag(price: String) {
    Text("$$price", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primary)
}

// LoadingIndicator.kt
@Composable
fun LoadingIndicator() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator()
    }
}

// ErrorScreen.kt
@Composable
fun ErrorScreen(message: String, onRetry: () -> Unit) {
    Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Text(message, color = MaterialTheme.colorScheme.error)
        Spacer(Modifier.height(16.dp))
        Button(onClick = onRetry) { Text("Retry") }
    }
}
```

- [ ] **Step 4: Verify compilation**

```bash
cd /Users/matthew/projects/meirongdev/shop && ./gradlew :kmp:ui-shared:build
```

- [ ] **Step 5: Commit**

```bash
git add kmp/ui-shared/
git commit -m "feat(kmp): implement ui-shared module with theme and common components"
```

---

### Task 13: buyer-app Shell (Three-Platform Compilation)

**Files:**
- Create: `kmp/buyer-app/build.gradle.kts`
- Create: `kmp/buyer-app/src/commonMain/kotlin/dev/meirong/shop/buyer/BuyerApp.kt`
- Create: `kmp/buyer-app/src/commonMain/kotlin/dev/meirong/shop/buyer/di/BuyerModule.kt`
- Create: `kmp/buyer-app/src/androidMain/kotlin/dev/meirong/shop/buyer/MainActivity.kt`
- Create: `kmp/buyer-app/src/androidMain/AndroidManifest.xml`
- Create: `kmp/buyer-app/src/iosMain/kotlin/dev/meirong/shop/buyer/MainViewController.kt`
- Create: `kmp/buyer-app/src/wasmJsMain/kotlin/dev/meirong/shop/buyer/Main.kt`
- Create: `kmp/buyer-app/src/wasmJsMain/resources/index.html`

- [ ] **Step 1: Create buyer-app build.gradle.kts**

```kotlin
plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.android.application)
}

kotlin {
    androidTarget()
    iosArm64 { binaries.framework { baseName = "BuyerApp"; isStatic = true } }
    iosSimulatorArm64 { binaries.framework { baseName = "BuyerApp"; isStatic = true } }
    wasmJs { browser { commonWebpackConfig { outputFileName = "buyer-app.js" } } }

    sourceSets {
        commonMain.dependencies {
            implementation(project(":kmp:core"))
            implementation(project(":kmp:ui-shared"))
            implementation(compose.runtime)
            implementation(compose.foundation)
            implementation(compose.material3)
            implementation(compose.ui)
            implementation(libs.navigation.compose)
            implementation(libs.lifecycle.viewmodel.compose)
            implementation(libs.koin.compose)
        }
    }
}

android {
    namespace = "dev.meirong.shop.buyer"
    compileSdk = 35
    defaultConfig {
        applicationId = "dev.meirong.shop.buyer"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0"
    }
}
```

- [ ] **Step 2: Create shared BuyerApp composable**

```kotlin
// BuyerApp.kt
@Composable
fun BuyerApp() {
    ShopTheme {
        val navController = rememberNavController()
        Scaffold(
            topBar = {
                TopAppBar(title = { Text("Shop") })
            }
        ) { padding ->
            NavHost(
                navController = navController,
                startDestination = "home",
                modifier = Modifier.padding(padding)
            ) {
                composable("home") {
                    // Placeholder home screen
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("Buyer Portal — Coming Soon")
                    }
                }
            }
        }
    }
}
```

- [ ] **Step 3: Create Koin DI module**

```kotlin
// BuyerModule.kt
val buyerModule = module {
    includes(coreModule)
    // Feature modules will be added in P2-P4
}
```

- [ ] **Step 4: Create Android entry point**

```kotlin
// MainActivity.kt
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { BuyerApp() }
    }
}
```

```xml
<!-- AndroidManifest.xml -->
<manifest xmlns:android="http://schemas.android.com/apk/res/android">
    <application android:label="Shop" android:theme="@style/Theme.AppCompat.Light.NoActionBar">
        <activity android:name=".MainActivity" android:exported="true">
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.LAUNCHER" />
            </intent-filter>
        </activity>
    </application>
</manifest>
```

- [ ] **Step 5: Create iOS entry point**

```kotlin
// MainViewController.kt
fun MainViewController() = ComposeUIViewController { BuyerApp() }
```

- [ ] **Step 6: Create Wasm entry point**

```kotlin
// Main.kt
@OptIn(ExperimentalComposeUiApi::class)
fun main() {
    CanvasBasedWindow(canvasElementId = "buyerApp", title = "Shop") {
        BuyerApp()
    }
}
```

```html
<!-- index.html -->
<!DOCTYPE html>
<html>
<head><meta charset="UTF-8"><title>Shop - Buyer Portal</title></head>
<body><canvas id="buyerApp"></canvas><script src="buyer-app.js"></script></body>
</html>
```

- [ ] **Step 7: Verify Android compilation**

```bash
cd /Users/matthew/projects/meirongdev/shop && ./gradlew :kmp:buyer-app:assembleDebug
```

- [ ] **Step 8: Verify Wasm compilation**

```bash
cd /Users/matthew/projects/meirongdev/shop && ./gradlew :kmp:buyer-app:wasmJsBrowserProductionWebpack
```

- [ ] **Step 9: Verify iOS framework compilation**

```bash
cd /Users/matthew/projects/meirongdev/shop && ./gradlew :kmp:buyer-app:linkDebugFrameworkIosSimulatorArm64
```

- [ ] **Step 10: Commit**

```bash
git add kmp/buyer-app/
git commit -m "feat(kmp): create buyer-app shell with three-platform compilation"
```

---

### Task 14: seller-app Shell (Three-Platform Compilation)

**Files:**
- Create: `kmp/seller-app/build.gradle.kts`
- Create: `kmp/seller-app/src/commonMain/kotlin/dev/meirong/shop/seller/SellerApp.kt`
- Create: `kmp/seller-app/src/commonMain/kotlin/dev/meirong/shop/seller/di/SellerModule.kt`
- Create: `kmp/seller-app/src/androidMain/kotlin/dev/meirong/shop/seller/MainActivity.kt`
- Create: `kmp/seller-app/src/androidMain/AndroidManifest.xml`
- Create: `kmp/seller-app/src/iosMain/kotlin/dev/meirong/shop/seller/MainViewController.kt`
- Create: `kmp/seller-app/src/wasmJsMain/kotlin/dev/meirong/shop/seller/Main.kt`
- Create: `kmp/seller-app/src/wasmJsMain/resources/index.html`

- [ ] **Step 1: Create seller-app (mirror buyer-app structure)**

Same structure as Task 13, with:
- `namespace = "dev.meirong.shop.seller"`
- `applicationId = "dev.meirong.shop.seller"`
- Framework name: `SellerApp`
- Webpack output: `seller-app.js`
- Canvas ID: `sellerApp`
- Placeholder text: `"Seller Portal — Coming Soon"`
- `sellerModule` includes `coreModule`

- [ ] **Step 2: Verify all three platforms compile**

```bash
cd /Users/matthew/projects/meirongdev/shop && ./gradlew :kmp:seller-app:assembleDebug :kmp:seller-app:wasmJsBrowserProductionWebpack :kmp:seller-app:linkDebugFrameworkIosSimulatorArm64
```

- [ ] **Step 3: Commit**

```bash
git add kmp/seller-app/
git commit -m "feat(kmp): create seller-app shell with three-platform compilation"
```

---

### Task 15: Wasm Bundle Size Baseline + CI Verification

**Files:**
- Create: `scripts/check-wasm-bundle-size.sh`

- [ ] **Step 1: Build production Wasm and measure**

```bash
cd /Users/matthew/projects/meirongdev/shop && ./gradlew :kmp:buyer-app:wasmJsBrowserProductionWebpack :kmp:seller-app:wasmJsBrowserProductionWebpack

# Check sizes
ls -lh kmp/buyer-app/build/dist/wasmJs/productionExecutable/*.wasm
ls -lh kmp/seller-app/build/dist/wasmJs/productionExecutable/*.wasm

# Check compressed size
gzip -k kmp/buyer-app/build/dist/wasmJs/productionExecutable/*.wasm
ls -lh kmp/buyer-app/build/dist/wasmJs/productionExecutable/*.wasm.gz
```

- [ ] **Step 2: Create bundle size check script**

```bash
#!/bin/bash
# scripts/check-wasm-bundle-size.sh
MAX_SIZE_MB=5  # compressed target from spec
for app in buyer-app seller-app; do
    WASM_FILE=$(find "kmp/$app/build/dist/wasmJs/productionExecutable" -name "*.wasm" | head -1)
    COMPRESSED=$(gzip -c "$WASM_FILE" | wc -c)
    SIZE_MB=$(echo "scale=2; $COMPRESSED / 1048576" | bc)
    echo "$app: ${SIZE_MB}MB compressed"
    if (( $(echo "$SIZE_MB > $MAX_SIZE_MB" | bc -l) )); then
        echo "WARNING: $app exceeds ${MAX_SIZE_MB}MB target"
        exit 1
    fi
done
```

- [ ] **Step 3: Record baseline in spec**

Update `docs/superpowers/specs/2026-03-20-kmp-meilisearch-design.md` Section 15 with actual measured sizes.

- [ ] **Step 4: Commit**

```bash
git add scripts/check-wasm-bundle-size.sh
git commit -m "build: add Wasm bundle size baseline check script"
```

---

## Summary

| Workstream | Tasks | Dependencies |
|------------|-------|--------------|
| **A: search-service (P1)** | Tasks 1-9 | Sequential (1 → 2 → 3 → 4 → 5 → 6 → 7 → 8 → 9) |
| **B: KMP Foundation (P0)** | Tasks 10-15 | Sequential (10 → 11 → 12 → 13 → 14 → 15) |
| **A ↔ B** | No cross-dependencies | Can be parallelized |

**Total tasks:** 15
**Estimated commits:** 15
**Prerequisites:** Java 25 JDK, Gradle 8.14+, Docker (for Testcontainers), Xcode (for iOS compilation)
