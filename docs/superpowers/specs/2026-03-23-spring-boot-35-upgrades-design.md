# Shop Platform — Spring Boot 3.5 最佳实践升级 设计文档

> 版本：1.0 | 日期：2026-03-23

---

## 一、概述

### 1.1 目标与范围

本文档描述将 Shop Platform 15 个微服务升级至充分利用 Spring Boot 3.x（当前版本 3.5.11）现代特性的设计方案。目标是在**不破坏现有 API 契约**的前提下，通过渐进式引入四项高价值特性，提升系统的可观测性、测试效率、运维体验和开发效率：

1. **Problem Details（RFC 7807）**：统一错误响应格式，减少各服务之间的响应结构差异。
2. **Testcontainers `@ServiceConnection`**：消除集成测试中的 `@DynamicPropertySource` 样板代码。
3. **HTTP Interfaces（`@HttpExchange`）**：用声明式接口替换 `BuyerAggregationService` 中大量的 `RestClient` 命令式调用。
4. **CDS（Class Data Sharing）**：通过 AOT 编译 + CDS 训练存档，将 K8s Pod 冷启动时间缩短 20–40%。

**不在本期范围内：**
- 修改任何 API 响应结构（对外契约零破坏）
- GraalVM Native 生产部署（列为长期任务，见第六节）
- Kotlin Coroutines 改造

**受影响的服务（共 15 个）：**

| 服务 | 类型 | 受影响的升级项 |
|---|---|---|
| api-gateway | Gateway（WebFlux） | CDS |
| auth-server | Auth | Problem Details、CDS |
| buyer-bff | BFF | Problem Details、HTTP Interfaces、CDS |
| seller-bff | BFF | Problem Details、CDS |
| marketplace-service | Domain | Problem Details、@ServiceConnection、CDS |
| order-service | Domain | Problem Details、@ServiceConnection、CDS |
| profile-service | Domain | Problem Details、@ServiceConnection、CDS |
| promotion-service | Domain / Worker | Problem Details、@ServiceConnection、CDS |
| wallet-service | Domain | Problem Details、@ServiceConnection、CDS |
| loyalty-service | Domain | Problem Details、@ServiceConnection、CDS |
| search-service | Domain | Problem Details、@ServiceConnection、CDS |
| notification-service | Worker | Problem Details、@ServiceConnection、CDS |
| activity-service | Domain | Problem Details、CDS |
| webhook-service | Worker | Problem Details、@ServiceConnection、CDS |
| subscription-service | Domain | Problem Details、@ServiceConnection、CDS |

### 1.2 升级项优先级一览表（按 ROI 排序）

| 优先级 | 升级项 | 工作量 | 收益 | 风险 |
|:---:|---|---|---|---|
| P0 | Problem Details（RFC 7807） | 低（shop-common 一次性修改） | 客户端错误处理统一，减少跨服务调试成本 | 极低（向后兼容） |
| P1 | Testcontainers `@ServiceConnection` | 低–中（逐服务替换） | 测试代码减少 ~30%，消除手写端口配置出错风险 | 低 |
| P2 | HTTP Interfaces `@HttpExchange` | 中（buyer-bff 试点） | `BuyerAggregationService` 行数减少 ~40%，接口可测性提升 | 中（需验证 Resilience4j 集成） |
| P3 | CDS 启动加速 | 中（构建流程修改） | 冷启动减少 20–40%，K8s rolling deploy 更快 | 低–中（CI 构建时间增加） |
| P4 | GraalVM Native（长期） | 高 | 镜像 60–80% 更小，启动 < 1s | 高（反射、动态代理限制） |

---

## 二、Problem Details（RFC 7807）统一错误模型

### 2.1 现状问题（各服务错误响应不一致）

当前 `GlobalExceptionHandler`（`shop-common/src/main/java/dev/meirong/shop/common/web/GlobalExceptionHandler.java`）返回平台自定义的 `ApiResponse<Void>` 结构：

```json
{
  "traceId": "abc123",
  "status": "SC_VALIDATION_ERROR",
  "message": "field must not be blank",
  "data": null
}
```

**问题：**
- 客户端（前端、第三方集成商）无法依赖标准 HTTP 错误格式（RFC 7807 / RFC 9457）。
- 各域服务异常处理未统一继承 `GlobalExceptionHandler`，存在服务间响应格式漂移风险。
- Spring Boot 内置的 `DefaultHandlerExceptionResolver`（处理 405、415 等 MVC 框架异常）返回的错误体与业务异常不同格式，需要单独处理。

### 2.2 设计：统一 ProblemDetail 结构 + shop-common ErrorHandler

**设计原则：**
- `ApiResponse` 自定义格式**保留**，用于所有成功响应（`200 OK`）。
- 所有**错误响应（4xx/5xx）**迁移至 `ProblemDetail`（RFC 7807），通过 Spring MVC 内置支持输出。
- 在 `GlobalExceptionHandler` 中新增 `GlobalProblemDetailHandler` 或扩展现有类，统一将 `BusinessException` 映射为 `ProblemDetail`。
- 保留 `traceId` 扩展字段（通过 `ProblemDetail.setProperty("traceId", ...)` 实现）。

**统一 ProblemDetail 结构：**

```json
{
  "type": "https://shop.dev.meirong/errors/SC_VALIDATION_ERROR",
  "title": "Validation Error",
  "status": 400,
  "detail": "field 'email' must not be blank",
  "instance": "/v1/profiles/register",
  "traceId": "abc123def456",
  "errorCode": "SC_VALIDATION_ERROR"
}
```

字段说明：
- `type`：错误类型 URI（以业务错误码结尾，便于文档化）
- `title`：人类可读错误标题（固定，不含动态内容）
- `status`：HTTP 状态码
- `detail`：具体错误信息（来自异常 `message`）
- `instance`：发生错误的请求路径
- `traceId`：当前 Trace ID（扩展字段，来自 `TraceIdExtractor`）
- `errorCode`：业务错误码（`BusinessErrorCode.getCode()`，供客户端程序化处理）

### 2.3 配置方式（一行开启 + 自定义 ControllerAdvice）

**Step 1：application.yml 全服务添加（已内置，仅需开启）：**

```yaml
spring:
  mvc:
    problemdetails:
      enabled: true
```

此配置开启 Spring MVC 对 `ResponseEntityExceptionHandler` 的 RFC 7807 格式输出，覆盖 405、415、400 等框架异常。

**Step 2：`GlobalExceptionHandler` 改为继承 `ResponseEntityExceptionHandler`：**

```java
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ProblemDetail> handleBusinessException(
            BusinessException ex, HttpServletRequest request) {

        ProblemDetail pd = ProblemDetail.forStatusAndDetail(
                ex.getErrorCode().getHttpStatus(), ex.getMessage());
        pd.setType(URI.create("https://shop.dev.meirong/errors/" + ex.getErrorCode().getCode()));
        pd.setTitle(toTitle(ex.getErrorCode()));
        pd.setInstance(URI.create(request.getRequestURI()));
        pd.setProperty("traceId", TraceIdExtractor.currentTraceId());
        pd.setProperty("errorCode", ex.getErrorCode().getCode());
        return ResponseEntity.status(ex.getErrorCode().getHttpStatus()).body(pd);
    }

    // Validation、ConstraintViolation、Generic 同理...
}
```

**向后兼容说明：**
- 成功响应（`ApiResponse<T>`）格式不变。
- 错误响应格式变化，需在 API 变更日志中注明，并与前端/移动端协商更新时间。
- 建议通过 `Accept: application/problem+json` Content Negotiation 提供两种格式（短期过渡方案）。

### 2.4 错误响应示例

**BusinessException（库存不足）：**

```json
HTTP/1.1 400 Bad Request
Content-Type: application/problem+json

{
  "type": "https://shop.dev.meirong/errors/SC_INVENTORY_INSUFFICIENT",
  "title": "Inventory Insufficient",
  "status": 400,
  "detail": "Product SKU-789 has only 2 units in stock",
  "instance": "/v1/orders/checkout",
  "traceId": "0af7651916cd43dd8448eb211c80319c",
  "errorCode": "SC_INVENTORY_INSUFFICIENT"
}
```

**Validation 失败（MVC 内置）：**

```json
HTTP/1.1 400 Bad Request
Content-Type: application/problem+json

{
  "type": "https://shop.dev.meirong/errors/SC_VALIDATION_ERROR",
  "title": "Validation Error",
  "status": 400,
  "detail": "email: must not be blank, phone: invalid format",
  "instance": "/v1/profiles/register",
  "traceId": "1bf8651916cd43dd8448eb211c80319d",
  "errorCode": "SC_VALIDATION_ERROR"
}
```

**框架异常（405 Method Not Allowed，Spring MVC 内置处理）：**

```json
HTTP/1.1 405 Method Not Allowed
Content-Type: application/problem+json

{
  "type": "about:blank",
  "title": "Method Not Allowed",
  "status": 405,
  "detail": "Method 'DELETE' is not supported.",
  "instance": "/v1/orders/123"
}
```

---

## 四、HTTP Interfaces（`@HttpExchange`）声明式客户端

### 4.1 当前问题（RestClient 样板代码过多）

`BuyerAggregationService` 当前直接持有 `RestClient` 实例，调用约 8 个下游服务（profile、wallet、promotion、marketplace、order、search、loyalty），每个调用都需要：

```java
restClient.get()
    .uri(properties.profileServiceUrl() + "/v1/profiles/{buyerId}", buyerId)
    .header("X-Internal-Token", properties.internalToken())
    .retrieve()
    .onStatus(status -> status.is4xxClientError(), (req, res) -> { ... })
    .body(new ParameterizedTypeReference<ApiResponse<ProfileApi.ProfileResponse>>() {});
```

问题：
- 每个调用 8–15 行样板代码，`BuyerAggregationService` 文件超过 400 行
- baseUrl、超时、内部 Token Header 在每个调用中重复
- 单元测试需 mock `RestClient` 整条调用链，测试代码脆弱
- 新增调用时易遗漏 `X-Internal-Token` 或错误处理

### 4.2 设计：各服务 Client 模块化（`*ServiceClient` 接口）

**目录结构（buyer-bff）：**

```
buyer-bff/src/main/java/dev/meirong/shop/buyerbff/
├── client/
│   ├── OrderServiceClient.java         ← @HttpExchange 接口
│   ├── MarketplaceServiceClient.java
│   ├── ProfileServiceClient.java
│   ├── WalletServiceClient.java
│   ├── PromotionServiceClient.java
│   ├── LoyaltyServiceClient.java
│   └── SearchServiceClient.java
├── config/
│   ├── BuyerBffConfig.java             ← 注册 HttpServiceProxyFactory + Bean
│   └── BuyerClientProperties.java
└── service/
    └── BuyerAggregationService.java    ← 注入接口，不再直接用 RestClient
```

**接口声明示例：**

```java
// buyer-bff/src/main/java/dev/meirong/shop/buyerbff/client/OrderServiceClient.java
@HttpExchange("/v1/orders")
public interface OrderServiceClient {

    @GetExchange("/{orderId}")
    ApiResponse<OrderApi.OrderResponse> getOrder(@PathVariable String orderId);

    @PostExchange("/checkout")
    ApiResponse<OrderApi.CheckoutResponse> checkout(@RequestBody OrderApi.CheckoutRequest request);

    @GetExchange
    ApiResponse<PageResponse<OrderApi.OrderSummary>> listOrders(
            @RequestParam String buyerId,
            @RequestParam int page,
            @RequestParam int size);
}
```

```java
// buyer-bff/src/main/java/dev/meirong/shop/buyerbff/client/MarketplaceServiceClient.java
@HttpExchange("/v1/marketplace")
public interface MarketplaceServiceClient {

    @GetExchange("/products/{productId}")
    ApiResponse<MarketplaceApi.ProductResponse> getProduct(@PathVariable String productId);

    @GetExchange("/products")
    ApiResponse<PageResponse<MarketplaceApi.ProductSummary>> listProducts(
            @RequestParam int page,
            @RequestParam int size);

    @PatchExchange("/products/{productId}/inventory")
    ApiResponse<Void> deductInventory(@PathVariable String productId,
                                       @RequestBody MarketplaceApi.InventoryDeductRequest request);
}
```

### 4.3 与 Resilience4j 集成方案（Decorator 模式）

`@HttpExchange` 接口代理本身不支持 `@CircuitBreaker` 注解（因为代理对象由 `HttpServiceProxyFactory` 生成，不经过 Spring AOP）。解决方案：**Decorator 包装层**。

```java
// 方案 A：Wrapper Service（推荐，简单直接）
@Service
public class ResilientOrderClient {

    private final OrderServiceClient delegate;

    public ResilientOrderClient(OrderServiceClient delegate) {
        this.delegate = delegate;
    }

    @CircuitBreaker(name = "orderService", fallbackMethod = "getOrderFallback")
    public ApiResponse<OrderApi.OrderResponse> getOrder(String orderId) {
        return delegate.getOrder(orderId);
    }

    ApiResponse<OrderApi.OrderResponse> getOrderFallback(String orderId, Exception ex) {
        throw new BusinessException(CommonErrorCode.DOWNSTREAM_ERROR,
                "order-service unavailable: " + ex.getMessage(), ex);
    }
}
```

```java
// 方案 B：CircuitBreakerDecorator（适合统一管理多接口熔断）
@Bean
OrderServiceClient orderServiceClient(HttpServiceProxyFactory factory,
                                       CircuitBreakerRegistry cbRegistry) {
    OrderServiceClient raw = factory.createClient(OrderServiceClient.class);
    CircuitBreaker cb = cbRegistry.circuitBreaker("orderService");
    return (OrderServiceClient) Proxy.newProxyInstance(
            OrderServiceClient.class.getClassLoader(),
            new Class[]{OrderServiceClient.class},
            (proxy, method, args) ->
                cb.executeCheckedSupplier(() -> method.invoke(raw, args)));
}
```

**推荐方案 A**：Wrapper Service 对现有代码侵入性最小，`@CircuitBreaker` fallback 方法签名清晰，方便单元测试。

### 4.4 代码示例（buyer-bff → order-service 完整流程）

**Config 注册 Bean：**

```java
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(BuyerClientProperties.class)
public class BuyerBffConfig {

    @Bean
    RestClient.Builder internalRestClientBuilder(BuyerClientProperties props) {
        return RestClient.builder()
                .defaultHeader("X-Internal-Token", props.internalToken())
                .requestFactory(requestFactory(props));
    }

    @Bean
    OrderServiceClient orderServiceClient(
            RestClient.Builder builder, BuyerClientProperties props) {
        RestClient client = builder
                .baseUrl(props.orderServiceUrl())
                .build();
        return HttpServiceProxyFactory
                .builderFor(RestClientAdapter.create(client))
                .build()
                .createClient(OrderServiceClient.class);
    }

    @Bean
    MarketplaceServiceClient marketplaceServiceClient(
            RestClient.Builder builder, BuyerClientProperties props) {
        RestClient client = builder
                .baseUrl(props.marketplaceServiceUrl())
                .build();
        return HttpServiceProxyFactory
                .builderFor(RestClientAdapter.create(client))
                .build()
                .createClient(MarketplaceServiceClient.class);
    }

    // ... 其余 5 个 client 同理
}
```

**BuyerAggregationService 调用侧（Before vs After）：**

```java
// Before（~12 行）
ApiResponse<OrderApi.OrderResponse> resp = restClient.get()
    .uri(properties.orderServiceUrl() + "/v1/orders/{id}", orderId)
    .header("X-Internal-Token", properties.internalToken())
    .retrieve()
    .onStatus(HttpStatusCode::is4xxClientError, (req, res) -> {
        throw new BusinessException(CommonErrorCode.DOWNSTREAM_ERROR, "order not found");
    })
    .body(new ParameterizedTypeReference<>() {});

// After（~1 行）
ApiResponse<OrderApi.OrderResponse> resp = orderServiceClient.getOrder(orderId);
```

### 4.5 迁移策略（渐进式，不一次性替换）

| 阶段 | 内容 | 时间 |
|---|---|---|
| 试点 | buyer-bff 引入 `OrderServiceClient` + `MarketplaceServiceClient`，保留原 `RestClient` 作并行备份 | Task 3A |
| 验证 | 全量 integration test 通过，熔断降级行为与原实现一致 | Task 3A |
| 扩展 | buyer-bff 剩余 5 个 client（Profile、Wallet、Promotion、Loyalty、Search） | Task 3B |
| seller-bff | 按同样模式引入 seller-bff 客户端接口 | Task 3C（可选） |

**回退策略：** 在同一个 `BuyerBffConfig` 中，如果 `@HttpExchange` 代理 Bean 创建失败，通过 `@ConditionalOnMissingBean` fallback 到原 `RestClient` Bean。

---

## 五、Testcontainers `@ServiceConnection`

### 5.1 现状问题（`@DynamicPropertySource` 样板代码）

当前 `SearchPipelineIntegrationTest` 以及 archetype 模板均使用手动属性注册：

```java
@DynamicPropertySource
static void configureProperties(DynamicPropertyRegistry registry) {
    registry.add("spring.kafka.bootstrap-servers", kafka::getBootstrapServers);
    registry.add("spring.datasource.url", mysql::getJdbcUrl);
    registry.add("spring.datasource.username", mysql::getUsername);
    registry.add("spring.datasource.password", mysql::getPassword);
    registry.add("meilisearch.host", () -> "http://localhost:" + meilisearch.getMappedPort(7700));
    registry.add("meilisearch.api-key", () -> "testMasterKey");
}
```

问题：
- 每个集成测试类重复 6–10 行属性绑定代码
- 属性名称拼写错误不会在编译期发现
- 新增容器时易漏写对应 `registry.add()`
- 多个测试类共享容器时需额外使用 `@TestClassOrder` 或静态字段

### 5.2 统一测试基类设计（`ShopIntegrationTest`）

在 `shop-common` 的 `src/test/java` 下新增统一基类：

```java
// shop-common/src/test/java/dev/meirong/shop/common/test/ShopIntegrationTest.java
@SpringBootTest
@Testcontainers
public abstract class ShopIntegrationTest {

    @Container
    @ServiceConnection
    protected static final MySQLContainer<?> mysql =
            new MySQLContainer<>("mysql:8.4")
                    .withDatabaseName("shop_test")
                    .withUsername("shop")
                    .withPassword("shop-secret");

    @Container
    @ServiceConnection
    protected static final KafkaContainer kafka =
            new KafkaContainer(DockerImageName.parse("apache/kafka-native:4.0.0"));

    @Container
    @ServiceConnection
    protected static final GenericContainer<?> redis =
            new GenericContainer<>(DockerImageName.parse("redis:7-alpine"))
                    .withExposedPorts(6379);
}
```

**`@ServiceConnection` 的工作原理：**
- Spring Boot 3.1+ 通过 `ContainerConnectionDetailsFactory` SPI 自动识别容器类型
- `MySQLContainer` → 自动绑定 `spring.datasource.url/username/password/driver-class-name`
- `KafkaContainer` → 自动绑定 `spring.kafka.bootstrap-servers`
- `GenericContainer("redis")` + `@ServiceConnection` → 自动绑定 `spring.data.redis.host/port`

### 5.3 支持的容器类型（MySQL / Redis / Kafka）

| 容器 | `@ServiceConnection` 支持 | 自动绑定属性 |
|---|---|---|
| `MySQLContainer` | 原生支持 | `spring.datasource.*` |
| `KafkaContainer`（Apache Kafka 原生）| 原生支持 | `spring.kafka.bootstrap-servers` |
| `RedisContainer` / `GenericContainer("redis")` | 需要 `spring-boot-testcontainers` 依赖 | `spring.data.redis.host/port` |
| `GenericContainer("getmeili/meilisearch")` | 不支持（需自定义 `ConnectionDetailsFactory`） | 保留 `@DynamicPropertySource` |

**MeiliSearch 容器例外处理：**

MeiliSearch 不是 Spring Boot 官方支持的 `@ServiceConnection` 容器，仍需保留 `@DynamicPropertySource`，但可封装在基类中：

```java
public abstract class ShopSearchIntegrationTest extends ShopIntegrationTest {

    @Container
    protected static final GenericContainer<?> meilisearch =
            new GenericContainer<>(DockerImageName.parse("getmeili/meilisearch:v1.12"))
                    .withExposedPorts(7700)
                    .withEnv("MEILI_ENV", "development")
                    .withEnv("MEILI_MASTER_KEY", "testMasterKey");

    @DynamicPropertySource
    static void meilisearchProperties(DynamicPropertyRegistry registry) {
        registry.add("meilisearch.host",
                () -> "http://localhost:" + meilisearch.getMappedPort(7700));
        registry.add("meilisearch.api-key", () -> "testMasterKey");
    }
}
```

### 5.4 代码示例

**Before（`SearchPipelineIntegrationTest` 当前模式）：**

```java
@SpringBootTest
@Testcontainers
class SearchPipelineIntegrationTest {

    @Container
    static KafkaContainer kafka =
            new KafkaContainer(DockerImageName.parse("confluentinc/cp-kafka:7.9.0"));

    @Container
    static GenericContainer<?> meilisearch =
            new GenericContainer<>(DockerImageName.parse("getmeili/meilisearch:v1.12"))
                    .withExposedPorts(7700);

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.kafka.bootstrap-servers", kafka::getBootstrapServers);
        registry.add("meilisearch.host",
                () -> "http://localhost:" + meilisearch.getMappedPort(7700));
        registry.add("meilisearch.api-key", () -> "testMasterKey");
    }

    // ... 测试方法
}
```

**After（继承 `ShopSearchIntegrationTest`）：**

```java
class SearchPipelineIntegrationTest extends ShopSearchIntegrationTest {

    // 无需 @DynamicPropertySource，kafka 连接由 @ServiceConnection 自动注入
    // meilisearch 连接仍由基类的 @DynamicPropertySource 处理

    // ... 测试方法（不变）
}
```

**改造前后对比（每个测试类节省约 8 行）：**

| 指标 | Before | After |
|---|---|---|
| 容器声明重复 | 每个测试类重复 | 基类声明一次 |
| `@DynamicPropertySource` 行数 | 6–10 行/类 | 0（MySQL/Kafka/Redis） |
| 属性名拼写错误风险 | 有 | 无（自动绑定） |
| 容器复用（JVM 级别） | 需手动配置 `reuse=true` | 基类静态字段自动共享 |

---

## 六、GraalVM Native（长期演进）

### 6.1 适合 Native 的服务类型

并非所有服务都适合优先 Native 化。按照收益/风险比排序：

| 优先级 | 服务 | 原因 |
|:---:|---|---|
| 高 | `notification-service`、`webhook-service` | 无复杂反射依赖，以 Kafka Consumer + HTTP Call 为主 |
| 高 | `search-service` | 无 JPA；依赖 MeiliSearch Java SDK（需验证 Native 兼容性） |
| 中 | `profile-service`、`loyalty-service` | 标准 JPA + REST，反射依赖可通过 `@RegisterReflectionForBinding` 处理 |
| 低 | `buyer-bff`、`seller-bff` | 大量动态 JSON 处理、Jackson 反射，Native hints 工作量大 |
| 不建议 | `api-gateway` | Spring Cloud Gateway 的 WebFlux + Reactor Netty Native 支持尚未稳定 |
| 不建议 | `auth-server` | Spring Authorization Server + OAuth2 动态代理，Native hints 极复杂 |

### 6.2 前置条件与已知限制

**前置条件：**
1. 所有服务已完成 AOT 处理（Task 4 完成后自动满足）
2. GraalVM CE 25+ 或 Liberica NIK 25+ 作为构建 JDK
3. `native-maven-plugin` 版本与 Spring Boot 3.5 对应（`0.10.x`）

**已知限制与应对：**

| 限制 | 影响 | 应对方案 |
|---|---|---|
| 反射（`Class.forName`、`Method.invoke`） | Jackson `@JsonTypeInfo`、MyBatis | `@RegisterReflectionForBinding`、`reflect-config.json` |
| 动态代理（JDK Proxy） | Resilience4j AOP、Spring Security | `@NativeHints` 声明 proxy interfaces |
| 资源加载（`classpath:` 扫描） | i18n 消息文件、Liquibase 脚本 | `resource-config.json`、`@NativeRuntimeHints` |
| 第三方库 Native 兼容性 | Stripe SDK、MeiliSearch Java SDK | 需评估各 SDK 的 GraalVM Reachability Metadata |

### 6.3 渐进式路径

```
Phase 1（当前）：JVM + AOT + CDS
       ↓ 验证 AOT 无回归（3–6 个月）
Phase 2：选择 1–2 个无复杂依赖的服务试点 Native
       → notification-service / webhook-service
       ↓ CI 增加 native-test profile
Phase 3：扩展到更多服务，建立 Native hints 共享库
       → shop-common 提供 @NativeHints 配置类
       ↓
Phase 4：K8s 生产部署切换到 Native 镜像
       → 镜像大小 60–80% 减小，启动 < 1s
```

**建议 Milestone 触发条件：**
- Phase 2 触发：所有服务 AOT 构建通过 CI 3 个月无回归
- Phase 3 触发：2 个 Native 试点服务在预发环境稳定运行 1 个月

---

## 七、实施路径（分阶段，优先级排序）

| 阶段 | 任务 | 关键产出 | 建议时间 |
|:---:|---|---|---|
| Phase 1 | Problem Details 全服务统一 | `GlobalExceptionHandler` 改造、15 个服务 `application.yml` 更新 | Sprint 1 |
| Phase 1 | Testcontainers `@ServiceConnection` | `ShopIntegrationTest` 基类、3–5 个测试迁移 | Sprint 1 |
| Phase 2 | HTTP Interfaces 试点（buyer-bff） | `OrderServiceClient` + `MarketplaceServiceClient` + Resilience4j 集成 | Sprint 2 |
| Phase 2 | HTTP Interfaces 扩展（buyer-bff 全量） | 剩余 5 个 `*ServiceClient` 接口 | Sprint 2 |
| Phase 3 | CDS 构建集成 | 根 `pom.xml` AOT 配置、各服务 `Dockerfile` training 阶段 | Sprint 3 |
| Phase 4 | GraalVM Native 试点 | `notification-service` Native 构建 CI | Sprint 5+ |

**验收标准（整体）：**
- [ ] 所有服务错误响应均为 `application/problem+json` 格式，包含 `traceId` 和 `errorCode` 扩展字段
- [ ] `@ServiceConnection` 覆盖 MySQL、Kafka、Redis 三类容器的所有集成测试
- [ ] `BuyerAggregationService` 直接 `RestClient` 调用全部替换为接口调用，行数减少 ≥ 30%
- [ ] CI 构建时间增加 ≤ 2 分钟（AOT + CDS training）
- [ ] K8s staging 环境 Pod 冷启动时间均值降低 ≥ 20%
