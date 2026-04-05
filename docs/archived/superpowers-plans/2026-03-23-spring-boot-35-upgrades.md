# Shop Platform — Spring Boot 3.5 最佳实践升级 实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task.

**Goal:** 渐进式引入 Problem Details、Testcontainers @ServiceConnection、HTTP Interfaces、CDS 四项 Spring Boot 3.x 特性，不破坏现有 API 契约。

**Spec:** `docs/superpowers/specs/2026-03-23-spring-boot-35-upgrades-design.md`

**Tech Stack:** Spring Boot 3.5.11 / Java 25 / Resilience4j 2.x / Testcontainers / ArchUnit

---

## Phase 1：Problem Details + Testcontainers（Sprint 1，低风险高收益）

---

## Task 1: Problem Details — shop-common GlobalExceptionHandler 改造

**Goal:** 在 shop-common 中统一 Problem Details 响应格式，所有服务一次性获得标准化错误响应，包含 `traceId` 扩展字段。

**Files:**
- Modify: `shop-common/src/main/java/dev/meirong/shop/common/exception/GlobalExceptionHandler.java`（或新建）
- Create: `shop-common/src/main/java/dev/meirong/shop/common/exception/ShopProblemDetail.java`

- [ ] **Step 1: 创建 ShopProblemDetail 工厂类**

```java
// ShopProblemDetail.java
public final class ShopProblemDetail {

    private ShopProblemDetail() {}

    /**
     * 构造标准 ProblemDetail，自动注入 traceId 和 errorCode 扩展字段。
     */
    public static ProblemDetail of(HttpStatus status, String title, String detail,
                                    String errorCode, HttpServletRequest request) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setTitle(title);
        problem.setType(URI.create("/errors/" + errorCode.toLowerCase().replace('_', '-')));
        // 从 MDC 或请求头注入 traceId
        String traceId = MDC.get("traceId");
        if (traceId == null) traceId = request.getHeader("X-B3-TraceId");
        if (traceId != null) problem.setProperty("traceId", traceId);
        problem.setProperty("errorCode", errorCode);
        return problem;
    }
}
```

- [ ] **Step 2: 修改 GlobalExceptionHandler，使用 ProblemDetail 返回**

```java
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(BusinessException.class)
    public ProblemDetail handleBusinessException(BusinessException ex, HttpServletRequest req) {
        return ShopProblemDetail.of(
            HttpStatus.UNPROCESSABLE_ENTITY,
            "Business Rule Violation",
            ex.getMessage(),
            ex.getErrorCode(),   // 如 "COUPON_NOT_FOUND"
            req
        );
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ProblemDetail handleValidation(MethodArgumentNotValidException ex, HttpServletRequest req) {
        String detail = ex.getBindingResult().getFieldErrors().stream()
            .map(e -> e.getField() + ": " + e.getDefaultMessage())
            .collect(joining(", "));
        return ShopProblemDetail.of(HttpStatus.BAD_REQUEST, "Validation Failed", detail, "VALIDATION_FAILED", req);
    }

    @ExceptionHandler(Exception.class)
    public ProblemDetail handleUnexpected(Exception ex, HttpServletRequest req) {
        log.error("Unexpected error", ex);
        return ShopProblemDetail.of(
            HttpStatus.INTERNAL_SERVER_ERROR, "Internal Server Error",
            "An unexpected error occurred.", "INTERNAL_ERROR", req);
    }
}
```

- [ ] **Step 3: 在 15 个服务的 application.yml 各加一行**

```yaml
spring:
  mvc:
    problemdetails:
      enabled: true
```

- [ ] **Step 4: 编写 GlobalExceptionHandlerTest 验证响应格式**

```java
@WebMvcTest
class GlobalExceptionHandlerTest {
    @Test
    void businessException_returns_problem_detail() {
        // given: controller throws BusinessException
        // then: response content-type is application/problem+json
        // and: body contains { "errorCode": "...", "traceId": "..." }
    }
}
```

预期输出：所有服务异常响应 Content-Type 为 `application/problem+json`，Body 包含 `errorCode` 和 `traceId`。

---

## Task 2: Testcontainers @ServiceConnection — ShopIntegrationTest 基类

**Goal:** 创建共享测试基类，消除所有集成测试中的 `@DynamicPropertySource` 样板代码。

**Files:**
- Create: `shop-common/src/test/java/dev/meirong/shop/common/test/ShopIntegrationTest.java`
- Modify: `order-service/src/test/java/.../OrderApplicationServiceIntegrationTest.java`（示例迁移）
- Modify: `promotion-service/src/test/java/.../CouponApplicationServiceIntegrationTest.java`（示例迁移）

- [ ] **Step 1: 确认 Testcontainers 依赖（pom.xml）**

Spring Boot 3.1+ 通过 `spring-boot-testcontainers` 提供 @ServiceConnection 支持：

```xml
<!-- 在根 pom.xml 的 dependencyManagement 中已由 spring-boot-starter-test 引入 -->
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-testcontainers</artifactId>
    <scope>test</scope>
</dependency>
<dependency>
    <groupId>org.testcontainers</groupId>
    <artifactId>mysql</artifactId>
    <scope>test</scope>
</dependency>
<dependency>
    <groupId>org.testcontainers</groupId>
    <artifactId>kafka</artifactId>
    <scope>test</scope>
</dependency>
```

- [ ] **Step 2: 创建 ShopIntegrationTest 基类**

```java
@SpringBootTest
@Testcontainers
public abstract class ShopIntegrationTest {

    @Container
    @ServiceConnection
    static MySQLContainer<?> mysql =
        new MySQLContainer<>(DockerImageName.parse("mysql:8.0"));

    @Container
    @ServiceConnection
    static KafkaContainer kafka =
        new KafkaContainer(DockerImageName.parse("confluentinc/cp-kafka:7.6"));

    // Redis（如服务使用）
    @Container
    @ServiceConnection
    static GenericContainer<?> redis =
        new GenericContainer<>(DockerImageName.parse("redis:7-alpine"))
            .withExposedPorts(6379);

    // @ServiceConnection 自动注入：
    //   spring.datasource.url / username / password
    //   spring.kafka.bootstrap-servers
    //   spring.data.redis.host / port
    // 不再需要 @DynamicPropertySource！
}
```

- [ ] **Step 3: 迁移 2-3 个现有集成测试（示范性迁移）**

**Before（删除）：**
```java
@DynamicPropertySource
static void properties(DynamicPropertyRegistry r) {
    r.add("spring.datasource.url", mysql::getJdbcUrl);
    r.add("spring.datasource.username", mysql::getUsername);
    r.add("spring.datasource.password", mysql::getPassword);
}
```

**After（extends 基类，无需其他修改）：**
```java
class OrderApplicationServiceIntegrationTest extends ShopIntegrationTest {
    // @DynamicPropertySource 完全删除
}
```

预期输出：迁移后的集成测试通过，测试类代码减少约 15 行。

---

## Phase 2：HTTP Interfaces 试点（Sprint 2）

---

## Task 3: buyer-bff — HTTP Interfaces 试点（OrderServiceClient + MarketplaceServiceClient）

**Goal:** 将 buyer-bff 中调用 order-service 和 marketplace-service 的 RestClient 代码替换为声明式 @HttpExchange 接口。验证与 Resilience4j 的集成方案。

**Files:**
- Create: `buyer-bff/src/main/java/dev/meirong/shop/buyerbff/client/OrderServiceClient.java`
- Create: `buyer-bff/src/main/java/dev/meirong/shop/buyerbff/client/MarketplaceServiceClient.java`
- Create: `buyer-bff/src/main/java/dev/meirong/shop/buyerbff/config/HttpClientConfig.java`
- Modify: `buyer-bff/src/main/java/dev/meirong/shop/buyerbff/service/BuyerAggregationService.java`

- [ ] **Step 1: 创建 OrderServiceClient 接口**

```java
@HttpExchange("/v1/orders")
public interface OrderServiceClient {

    @GetExchange("/{id}")
    OrderApi.OrderResponse getOrder(@PathVariable String id);

    @GetExchange
    OrderApi.OrdersPageView listBuyerOrders(
        @RequestParam String buyerId,
        @RequestParam int page,
        @RequestParam int size
    );

    @PostExchange
    OrderApi.OrderResponse createOrder(@RequestBody OrderApi.CreateOrderRequest request);

    @GetExchange("/by-token/{token}")
    OrderApi.OrderResponse getOrderByToken(@PathVariable String token);
}
```

- [ ] **Step 2: 创建 MarketplaceServiceClient 接口**

```java
@HttpExchange("/v1")
public interface MarketplaceServiceClient {

    @GetExchange("/products/{id}")
    MarketplaceApi.ProductResponse getProduct(@PathVariable String id);

    @GetExchange("/products")
    MarketplaceApi.ProductsPageView listProducts(
        @RequestParam int page,
        @RequestParam int size
    );

    @PostExchange("/products/{productId}/variants/{variantId}/deduct-inventory")
    void deductInventory(@PathVariable String productId,
                          @PathVariable String variantId,
                          @RequestBody MarketplaceApi.DeductInventoryRequest request);
}
```

- [ ] **Step 3: HttpClientConfig — 注册为 Bean（含 Resilience4j Decorator）**

```java
@Configuration
public class HttpClientConfig {

    @Bean
    public OrderServiceClient orderServiceClient(
            RestClient.Builder builder,
            CircuitBreakerRegistry cbRegistry,
            @Value("${shop.services.order.base-url}") String baseUrl) {
        RestClient restClient = builder.baseUrl(baseUrl).build();
        RestClientAdapter adapter = RestClientAdapter.create(restClient);
        HttpServiceProxyFactory factory = HttpServiceProxyFactory
            .builderFor(adapter).build();
        OrderServiceClient client = factory.createClient(OrderServiceClient.class);
        // 包装 Resilience4j CircuitBreaker（保持现有熔断配置）
        CircuitBreaker cb = cbRegistry.circuitBreaker("orderService");
        return CircuitBreakerDecorator.ofSupplier(cb, () -> client).get();
    }
}
```

- [ ] **Step 4: BuyerAggregationService 中替换两个客户端调用，运行测试验证**

预期输出：`./mvnw test -pl buyer-bff` 通过；BuyerAggregationService 代码减少 ≥ 30 行。

---

## Task 4: buyer-bff — HTTP Interfaces 全量推广（剩余 5 个客户端）

**Goal:** 完成 buyer-bff 所有下游的接口化：promotion / loyalty / search / profile / wallet。

**Files:**
- Create: `buyer-bff/src/main/java/dev/meirong/shop/buyerbff/client/PromotionServiceClient.java`
- Create: `buyer-bff/src/main/java/dev/meirong/shop/buyerbff/client/LoyaltyServiceClient.java`
- Create: `buyer-bff/src/main/java/dev/meirong/shop/buyerbff/client/SearchServiceClient.java`
- Create: `buyer-bff/src/main/java/dev/meirong/shop/buyerbff/client/ProfileServiceClient.java`
- Create: `buyer-bff/src/main/java/dev/meirong/shop/buyerbff/client/WalletServiceClient.java`
- Modify: `buyer-bff/src/main/java/dev/meirong/shop/buyerbff/service/BuyerAggregationService.java`
- Modify: `buyer-bff/src/main/java/dev/meirong/shop/buyerbff/config/HttpClientConfig.java`

- [ ] **Step 1-5:** 各创建对应 @HttpExchange 接口，在 HttpClientConfig 中注册 Bean（含 CB Decorator），替换 BuyerAggregationService 中对应的 RestClient 调用。

- [ ] **Step 6: 运行全量测试**

```bash
./mvnw test -pl buyer-bff
```

预期输出：BuyerAggregationService 中直接调用 RestClient 的代码全部消除，仅通过接口调用。

---

## Phase 4：GraalVM Native（长期，Sprint 5+）

---

## Task 7: GraalVM Native 试点（notification-service）

**说明：** 此任务为长期任务，触发条件是所有服务 AOT 构建在 CI 中稳定 3 个月无回归。

**Files:**
- Modify: `notification-service/pom.xml`（添加 native profile）
- Create: `notification-service/src/main/resources/META-INF/native-image/`（native hints）

- [ ] **Step 1: 添加 native Maven profile**

```xml
<profiles>
    <profile>
        <id>native</id>
        <build>
            <plugins>
                <plugin>
                    <groupId>org.graalvm.buildtools</groupId>
                    <artifactId>native-maven-plugin</artifactId>
                </plugin>
            </plugins>
        </build>
    </profile>
</profiles>
```

- [ ] **Step 2: 修复 Native 不兼容的动态反射（Thymeleaf / Kafka 可能需要 hints）**

- [ ] **Step 3: 验证 Native 构建并测量启动时间**

```bash
./mvnw native:compile -Pnative -pl notification-service
./target/notification-service
# 预期：启动 < 1 秒，内存 < 100MB
```

预期输出：notification-service Native 镜像在 CI 构建通过，启动时间 < 1s。

---

## 验收标准

| 阶段 | 验收项 |
|------|--------|
| Phase 1 Task 1 | 所有服务错误响应 Content-Type 为 `application/problem+json`，含 `errorCode` + `traceId` |
| Phase 1 Task 2 | 3+ 个集成测试迁移到 @ServiceConnection，删除 @DynamicPropertySource |
| Phase 2 Task 3 | buyer-bff order/marketplace 调用通过接口完成，现有测试通过 |
| Phase 2 Task 4 | buyer-bff 所有下游接口化，BuyerAggregationService 直接 RestClient 调用归零 |
| Phase 3 Task 5 | `./mvnw process-aot` 所有服务通过 |
| Phase 3 Task 6 | CDS Docker 构建成功，启动时间降低 ≥ 20% |
| Phase 4 Task 7（长期） | notification-service Native 镜像构建通过，启动 < 1s |
