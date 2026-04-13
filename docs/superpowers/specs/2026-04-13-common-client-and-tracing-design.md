# Shop Platform — 公共 Client 抽取与 Tracing 传播设计

> 版本：2.0 | 日期：2026-04-13
> 状态：提案已批准 | 范围：BFF 聚合层与下游调用链路

---

## 一、背景与问题

### 1.1 现状
目前 `buyer-bff` 和 `seller-bff` 各自维护了一套针对下游服务的 `*ServiceClient` 接口。虽然它们都使用了 `shop-contracts` 中的常量，但仍存在以下问题：

- **代码重复**：相同的 `@HttpExchange` 接口定义在多个 BFF 模块中出现。
- **Tracing 丢失风险**：在配置类中手动通过 `RestClient.builder()` 创建实例，若未正确使用 Spring 容器注入的 `Builder` Bean，会导致 Micrometer Tracing 的自动配置失效，链路信息无法在下游传播。Spring Boot 官方已确认此问题 — 手动调用 `RestClient.builder()` 会绕过 auto-configuration 附加的 observation 拦截器（[spring-projects/spring-boot#42502](https://github.com/spring-projects/spring-boot/issues/42502)）。
- **Baggage 传递不一**：业务上下文（如 `X-Buyer-Id`, `X-Portal`）的透传逻辑散落在各处，缺乏统一的拦截器处理。
- **错误处理重复**：`handleDownstreamError` 逻辑在各配置类中重复编写。

### 1.2 目标
1.  **两层抽取**：基础设施能力下沉至 `shop-common`，业务接口提取至 `shop-clients`。
2.  **自动透传**：确保 TraceId 和 Baggage 在跨服务调用时 100% 可靠透传。
3.  **极简配置**：BFF 仅需引入依赖并声明 Bean，无需处理底层 `RestClient` 的拦截器挂载。

---

## 二、架构设计

### 2.1 模块分层

| 模块 | 职责 | 核心组件 |
|------|------|----------|
| **`shop-starter-http-client`** | (Starter) 提供带 Tracing 的基础能力 | `TracingHeaderInterceptor`, `CommonStatusHandler`, `AutoConfig`, `HttpExchangeSupport` |
| **`shop-clients`** | 存放所有服务的 `@HttpExchange` 接口 | `OrderServiceClient`, `MarketplaceServiceClient` |
| **`shop-contracts`** | (不变) 提供 DTO 和路径常量 | `OrderApi`, `MarketplaceApi` |

> **命名决策**：模块命名为 `shop-starter-http-client` 而非 `shop-common-client`，原因：
> 1. 与现有 `shop-starter-resilience`、`shop-starter-idempotency` 命名一致
> 2. 明确其 Spring Boot Starter 身份（包含 `@AutoConfiguration` + `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`）
> 3. 避免与 `shop-common-core` 概念混淆

### 2.2 依赖关系

```
shop-clients ──depends-on──> shop-contracts-* (DTOs + path constants)
shop-clients ──depends-on──> shop-starter-http-client (tracing + factory)

buyer-bff ──depends-on──> shop-clients
buyer-bff ──depends-on──> shop-starter-http-client
buyer-bff ──depends-on──> shop-common-core (shared infra)

domain-service ──depends-on──> shop-starter-http-client (ad-hoc RestClient calls)
domain-service ──depends-on──> shop-common-core (ApiResponse, exceptions)
```

### 2.3 数据流向

```
[BFF Request]
      │
      ▼
[shop-clients: *ServiceClient]
      │
      ▼
[shop-starter-http-client: RestClient + HttpServiceProxyFactory]
      ├─> TracingHeaderInterceptor (从 BaggageField 提取业务上下文，设置 HTTP Headers)
      ├─> ObservationRestClientCustomizer (由 Spring Boot 自动注入 — 处理 Span/Trace 传播)
      └─> defaultStatusHandler(HttpStatusCode::isError, ...) (将下游 ApiResponse<Error> 转为 BusinessException)
      │
      ▼
[下游 Domain Service]
```

---

## 三、核心技术决策与依据

### 3.1 Tracing 传播：使用 Auto-configured `RestClient.Builder`

**问题**：当前 `BuyerBffConfig` 和 `SellerBffConfig` 各自定义了 `RestClient.Builder` Bean：

```java
// 当前代码 — BuyerBffConfig.java 第 45 行
@Bean
RestClient.Builder restClientBuilder(JdkClientHttpRequestFactory jdkClientHttpRequestFactory) {
    return RestClient.builder()
            .requestFactory(jdkClientHttpRequestFactory);  // ← 绕过 Spring Boot auto-config!
}
```

这会导致 Spring Boot 的 `ObservationRestClientCustomizer` 无法自动注入 tracing 拦截器（[spring-projects/spring-boot#42502](https://github.com/spring-projects/spring-boot/issues/42502)）。

**解决方案**：注入 Spring Boot 自动配置的 `RestClient.Builder` Bean，而非自行定义。

**官方文档依据**：
- Spring Boot Tracing 文档明确指出："To automatically propagate traces over the network, use the auto-configured `RestTemplateBuilder`, `RestClient.Builder` or `WebClient`."（[docs.spring.io/spring-boot/reference/actuator/tracing.html](https://docs.spring.io/spring-boot/reference/actuator/tracing.html)）
- Spring Boot 3.2+ 通过 `RestClientBuilderConfigurer` 自动将 `ObservationRestClientCustomizer` 应用到所有注入的 `RestClient.Builder` Bean（[Spring Boot 3.5.9 Javadoc — ObservationRestClientCustomizer](https://docs.spring.io/spring-boot/3.5.9/api/java/org/springframework/boot/actuate/metrics/web/client/ObservationRestClientCustomizer.html)）
- `ObservationRestClientCustomizer` 自 Spring Boot 3.2.0 引入，负责将 `ObservationRegistry` 绑定到 `RestClient.Builder`，从而在每次 HTTP 调用时自动创建 span 并传播 trace context（[Spring Boot 3.2.19 Javadoc](https://docs.enterprise.spring.io/spring-boot/docs/3.2.19/api/org/springframework/boot/actuate/metrics/web/client/ObservationRestClientCustomizer.html)）

**实现**：
```java
// shop-starter-http-client 中定义一个工厂 Bean
@Configuration(proxyBeanMethods = false)
public class ShopHttpClientAutoConfiguration {

    @Bean
    @ConditionalOnBean(RestClient.Builder.class)
    public ShopHttpExchangeSupport shopHttpExchangeSupport(
            RestClient.Builder restClientBuilder,  // ← 注入 auto-configured builder
            ObjectProvider<Tracer> tracerProvider,
            ObjectMapper objectMapper) {
        return new ShopHttpExchangeSupport(restClientBuilder, tracerProvider, objectMapper);
    }
}
```

BFF 配置简化为：
```java
// BuyerBffConfig.java — 重构后
@Configuration(proxyBeanMethods = false)
public class BuyerBffConfig {

    @Bean
    OrderServiceClient orderServiceClient(ShopHttpExchangeSupport support,
                                          BuyerClientProperties properties) {
        return support.createClient(properties.orderServiceUrl(), OrderServiceClient.class);
    }
}
```

### 3.2 Baggage 传播：使用 `BaggageField` API

**问题**：当前 `HeaderPropagationInterceptor` 通过 `RequestContextHolder` 读取 incoming request headers 并转发。这种方式：
1. 依赖 Servlet `HttpServletRequest` 上下文，在 Kafka listener 等非 HTTP 场景不可用
2. 不与 Micrometer Tracing 联动，无法参与 W3C TraceContext 传播

**解决方案**：使用 Micrometer 的 `BaggageField` API 进行上下文传播。

**官方文档依据**：
- Spring Boot Tracing 文档："Propagating baggage fields over the network is done by configuring `management.tracing.baggage.remote-fields`"（[docs.spring.io/spring-boot/reference/actuator/tracing.html](https://docs.spring.io/spring-boot/reference/actuator/tracing.html)）
- Baggage 通过 `Tracer.createBaggageInScope()` 创建，`BaggageField.getValue()` 读取（[Spring Boot 3.5 Tracing 最佳实践](https://meirong.dev/posts/spring-boot-35-tracing-best-practices/)）
- Stack Overflow 上关于 Brave Baggage 初始化与传播的讨论确认了 `BaggageField` 是跨线程/跨调用传播上下文的标准方式（[stackoverflow.com/questions/75912175](https://stackoverflow.com/questions/75912175/how-to-initialize-micrometer-brave-baggate-field-spring-boot-3)）

**实现**：
```java
// shop-starter-http-client
@Component
public class TracingHeaderMdcInitializer {

    private final BaggageField buyerIdField;
    private final BaggageField portalField;
    private final BaggageField usernameField;

    public TracingHeaderMdcInitializer(Tracer tracer) {
        // 创建并注册 BaggageField 实例
        this.buyerIdField = BaggageField.create(TrustedHeaderNames.BUYER_ID);
        this.portalField = BaggageField.create(TrustedHeaderNames.PORTAL);
        this.usernameField = BaggageField.create(TrustedHeaderNames.USERNAME);
    }

    // 在 Gateway Filter 或 BFF Controller 入口处将 headers 写入 Baggage
    public void captureHeaders(HttpServletRequest request) {
        String buyerId = request.getHeader(TrustedHeaderNames.BUYER_ID);
        if (buyerId != null) {
            buyerIdField.updateValue(buyerId);
        }
        // ... similar for portal, username
    }
}
```

`application.yml` 配置（各服务统一）：
```yaml
management:
  tracing:
    baggage:
      remote-fields: X-Buyer-Id, X-Portal, X-Username, X-Seller-Id, X-Order-Id
      correlation-fields: X-Buyer-Id, X-Portal, X-Username
```

> **`remote-fields`**：自动作为 HTTP 响应头传播到下游
> **`correlation-fields`**：自动注入 MDC，用于日志关联

### 3.3 错误处理：使用 `defaultStatusHandler` 而非 `ResponseErrorHandler`

**问题**：当前 BFF 在 `createServiceProxy` 中使用 `.defaultStatusHandler(HttpStatusCode::isError, ...)` 处理下游错误。但 `@HttpExchange` 代理对错误的处理行为需要明确。

**官方文档依据**：
- Spring Framework 6.2 文档："`@HttpExchange` 代理继承底层客户端的错误处理行为。当 `RestClient` 配置了 `.defaultStatusHandler(HttpStatusCode::isError, handler)` 时，所有通过 `@HttpExchange` 发起的调用都会自动使用该 handler。"（[docs.spring.io/spring-framework/reference/integration/rest-clients.html](https://docs.spring.io/spring-framework/reference/integration/rest-clients.html)）
- 默认情况下，4xx/5xx 响应会抛出 `RestClientException`（或更具体的 `HttpClientErrorException` / `HttpServerErrorException`），`@HttpExchange` 代理会将异常传播给调用方（[Spring Framework 7.0.6 文档](https://docs.spring.io/spring-framework/reference/6.2-SNAPSHOT/web/webflux-http-interface-client.html)）
- Spring Framework 6.2.0 变更了 `DefaultResponseErrorHandler` 的调用链（[spring-projects/spring-framework#33980](https://github.com/spring-projects/spring-framework/issues/33980)），但这主要影响 `RestTemplate`。`RestClient` 使用 `defaultStatusHandler` 机制，不受此变更影响。
- Spring 官方博客确认 `RestClientAdapter` + `HttpServiceProxyFactory` 模式下，`RestClient` 上配置的 `defaultStatusHandler` 全局应用于所有 `@HttpExchange` 调用（[spring.io/blog/2025/09/23/http-service-client-enhancements](https://spring.io/blog/2025/09/23/http-service-client-enhancements)）

**实现**：
```java
// ShopHttpExchangeSupport.java
public class ShopHttpExchangeSupport {

    private final RestClient.Builder restClientBuilder;
    private final ObjectMapper objectMapper;

    public <T> T createClient(String baseUrl, Class<T> clientClass) {
        RestClient restClient = restClientBuilder
                .baseUrl(baseUrl)
                .defaultStatusHandler(HttpStatusCode::isError, this::handleDownstreamError)
                .build();

        return HttpServiceProxyFactory
                .builderFor(RestClientAdapter.create(restClient))
                .build()
                .createClient(clientClass);
    }

    private void handleDownstreamError(HttpRequest request, ClientHttpResponse response) {
        // 解析下游 ApiResponse<Error> 或 ProblemDetail
        // 抛出 BusinessException(CommonErrorCode.DOWNSTREAM_ERROR, message)
    }
}
```

> **关键**：此处 `restClientBuilder` 是从 Spring 容器注入的 auto-configured builder，已包含 `ObservationRestClientCustomizer` 附加的 tracing 拦截器。`defaultStatusHandler` 在 tracing 拦截器之后执行，不影响 trace 传播。

### 3.4 `@HttpExchange` 接口定义规范

**问题**：原设计文档展示了 `@HttpExchange(OrderApi.BASE_PATH)` 的用法，但 path 解析规则需要明确。

**官方文档依据**：
- `@HttpExchange` 的 value 作为路径前缀，与 `RestClient.Builder.baseUrl()` 拼接（[docs.spring.io/spring-framework/reference/integration/rest-clients.html](https://docs.spring.io/spring-framework/reference/integration/rest-clients.html)）
- Spring 6.2 引入了 `HttpServiceProxyRegistry` 和声明式注册，但 Spring Boot 3.5 尚未默认支持，当前仍使用 `HttpServiceProxyFactory` 手动创建（[spring.io/blog/2025/09/23/http-service-client-enhancements](https://spring.io/blog/2025/09/23/http-service-client-enhancements)）

**实现规范**：
```java
// shop-clients 中定义
@HttpExchange  // ← 不指定 value，所有路径在方法级别标注
public interface OrderServiceClient {

    @PostExchange(OrderApi.CART_LIST)
    ApiResponse<OrderApi.CartView> listCart(@RequestBody OrderApi.ListCartRequest request);

    @PostExchange(OrderApi.CART_ADD)
    ApiResponse<OrderApi.CartItemResponse> addToCart(@RequestBody OrderApi.AddToCartRequest request);

    @PostExchange(OrderApi.ORDER_GET)
    ApiResponse<OrderApi.OrderResponse> getOrder(@RequestBody OrderApi.GetOrderRequest request);
}
```

> 方法级 `@PostExchange` 使用 `OrderApi.*` 路径常量（完整路径如 `/order/v1/cart/add`），与 `RestClient` 的 `baseUrl`（如 `http://order-service:8080`）拼接后得到完整 URL。
>
> 不推荐在类级别 `@HttpExchange("/order/v1")` 然后在方法上用相对路径，因为这会切断与 `OrderApi` 路径常量的直接引用关系。

### 3.5 Domain Service 的 HTTP 调用

**问题**：非 BFF 服务（如 `loyalty-service` → `profile-service`）也进行 HTTP 调用，是否使用同一基础设施？

**回答**：是。`shop-starter-http-client` 应同时服务于：

1. **BFF → Domain Service**（通过 `@HttpExchange` 接口代理）
2. **Domain Service → Domain Service**（通过注入 `RestClient.Builder` 直接调用）

**实现**：
```java
// loyalty-service — OrderEventListener.java 重构后
public OrderEventListener(ObjectMapper objectMapper,
                          RestClient.Builder restClientBuilder,  // ← auto-configured
                          LoyaltyProperties properties) {
    this.restClient = restClientBuilder
            .defaultStatusHandler(HttpStatusCode::isError, SharedErrorHandler::handle)
            .build();
}
```

由于 `RestClient.Builder` 是 Spring Boot 自动配置的，自动包含 tracing 拦截器。

---

## 四、模块结构

### 4.1 `shop-starter-http-client`

```
shared/shop-common/shop-starter-http-client/
├── pom.xml
├── src/main/java/dev/meirong/shop/httpclient/
│   ├── config/
│   │   └── ShopHttpClientAutoConfiguration.java    # @AutoConfiguration, 注册 ShopHttpExchangeSupport
│   ├── interceptor/
│   │   └── TracingHeaderInterceptor.java            # ClientHttpRequestInterceptor, 从 BaggageField 读取值并设置 HTTP headers
│   ├── error/
│   │   └── SharedDownstreamErrorHandler.java        # 统一的下游错误解析与 BusinessException 映射
│   └── support/
│       └── ShopHttpExchangeSupport.java              # 核心工厂：createClient(baseUrl, clientClass)
├── src/main/resources/
│   └── META-INF/spring/
│       └── org.springframework.boot.autoconfigure.AutoConfiguration.imports
└── src/test/java/dev/meirong/shop/httpclient/
    ├── ShopHttpExchangeSupportTest.java
    └── TracingHeaderInterceptorTest.java
```

**依赖**：
```xml
<dependencies>
    <!-- Spring Boot auto-configuration -->
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-web</artifactId>
    </dependency>

    <!-- Micrometer Tracing — tracing + baggage -->
    <dependency>
        <groupId>io.micrometer</groupId>
        <artifactId>micrometer-tracing</artifactId>
    </dependency>

    <!-- Jackson for error response parsing -->
    <dependency>
        <groupId>com.fasterxml.jackson.core</groupId>
        <artifactId>jackson-databind</artifactId>
    </dependency>

    <!-- 项目内部 -->
    <dependency>
        <groupId>dev.meirong.shop</groupId>
        <artifactId>shop-common-core</artifactId>
    </dependency>
</dependencies>
```

### 4.2 `shop-clients`

```
shared/shop-clients/
├── pom.xml
├── src/main/java/dev/meirong/shop/clients/
│   ├── order/
│   │   └── OrderServiceClient.java
│   ├── marketplace/
│   │   ├── MarketplaceServiceClient.java
│   │   └── MarketplaceInternalServiceClient.java
│   ├── profile/
│   │   ├── ProfileServiceClient.java
│   │   └── ProfileInternalServiceClient.java
│   ├── wallet/
│   │   └── WalletServiceClient.java
│   ├── promotion/
│   │   ├── PromotionServiceClient.java
│   │   └── PromotionInternalServiceClient.java
│   ├── loyalty/
│   │   └── LoyaltyServiceClient.java
│   ├── search/
│   │   └── SearchServiceClient.java
│   └── activity/
│       └── ActivityServiceClient.java
└── src/test/java/dev/meirong/shop/clients/
    └── (接口编译验证测试)
```

**依赖**：
```xml
<dependencies>
    <!-- Spring Web for @HttpExchange annotations -->
    <dependency>
        <groupId>org.springframework</groupId>
        <artifactId>spring-web</artifactId>
    </dependency>

    <!-- 项目内部：contracts for DTOs + path constants -->
    <dependency>
        <groupId>dev.meirong.shop</groupId>
        <artifactId>shop-contracts-order</artifactId>
    </dependency>
    <dependency>
        <groupId>dev.meirong.shop</groupId>
        <artifactId>shop-contracts-marketplace</artifactId>
    </dependency>
    <!-- ... other contract modules ... -->
</dependencies>
```

> **注意**：`shop-clients` 仅依赖 `spring-web`（annotations only），不依赖 `spring-boot-starter-web`，保持轻量。

---

## 五、演进路径

### Phase 1：新建 `shop-starter-http-client` 模块

1. 在 `shared/shop-common/` 下新建 `shop-starter-http-client` 模块
2. 将现有 `HeaderPropagationInterceptor` 和 `HeaderPropagationAutoConfiguration` 中的逻辑迁移到 `TracingHeaderInterceptor`（升级为基于 `BaggageField` 而非 `RequestContextHolder`）
3. 实现 `SharedDownstreamErrorHandler` 和 `ShopHttpExchangeSupport`
4. 在 BFF 的 `pom.xml` 中引入新依赖
5. **保留**原有 `HeaderPropagationAutoConfiguration` 并标记 `@Deprecated`，确保向后兼容

### Phase 2：新建 `shop-clients` 模块

1. 将 `buyer-bff/src/main/java/.../client/` 下的所有 `*ServiceClient.java` 迁移到 `shop-clients`
2. 将 `seller-bff/src/main/java/.../client/` 下的 `*ServiceClient.java` 合并（去重后）到 `shop-clients`
3. 更新接口，确保所有路径使用 `*Api.java` 常量
4. 在 `pom.xml` 中注册 `shop-clients` 模块

### Phase 3：重构 BFF 配置

1. **删除** `BuyerBffConfig` 和 `SellerBffConfig` 中的 `RestClient.Builder` Bean 定义
2. **删除** `createServiceProxy` / `createClient` 私有方法
3. **改用** `ShopHttpExchangeSupport.createClient(baseUrl, ClientClass)` 注册所有客户端 Bean
4. 确认 `micrometer-tracing-bridge-otel` 依赖仍在各 BFF 的 `pom.xml` 中（确保 auto-configuration 生效）
5. 确认 `application.yml` 中配置了 `management.tracing.baggage.remote-fields`

### Phase 4：重构 Domain Service

1. 确认所有域服务中的 `RestClient.Builder` 注入来自 Spring Boot auto-configuration（不自行定义 Bean）
2. 统一使用 `SharedDownstreamErrorHandler` 处理下游错误
3. 移除各服务中重复的 `RestClient.builder()` 直接调用

### Phase 5：清理与验证

1. 删除 `HeaderPropagationAutoConfiguration` 和 `HeaderPropagationInterceptor`（已 deprecated）
2. 运行 `make verify-observability` 验证 trace 传播
3. 运行所有契约测试确认行为不变

---

## 六、验证标准

### 6.1 Tracing 连续性验证

```bash
# 1. 启动本地 Kind 集群
make e2e

# 2. 通过 Gateway 发起 BFF 请求
curl -H "Authorization: Bearer <token>" \
     http://127.0.0.1:18080/api/buyer/dashboard

# 3. 在 Prometheus/Grafana 中检查 trace 链路：
#    Gateway → buyer-bff → order-service → marketplace-service
#    确认所有 span 共享同一个 traceId
```

**验证点**：
- Gateway 产生的 `traceId` 与下游所有 service 的 `traceId` 一致
- 每个 span 的 `parentId` 指向上一个 span 的 `spanId`
- BFF 到 Domain Service 的调用产生新的 span（非 orphan span）

### 6.2 Baggage 传播验证

```bash
# 在 BFF 日志中验证 X-Buyer-Id 已注入 MDC
# 在 Domain Service 日志中验证同一 X-Buyer-Id 存在
```

**验证点**：
- `X-Buyer-Id` 从 Gateway 传入 BFF 后，在 BFF → Domain Service 调用中作为 HTTP header 传播
- `X-Portal`、`X-Username` 同理

### 6.3 错误处理验证

| 场景 | 预期行为 |
|------|---------|
| 下游返回 400 + `ApiResponse<Error>` | 抛出 `BusinessException(DOWNSTREAM_ERROR, "<message>")` |
| 下游返回 500 + `ProblemDetail` | 抛出 `BusinessException(DOWNSTREAM_ERROR, "<detail>")` |
| 下游网络超时 | 抛出 `BusinessException(DOWNSTREAM_ERROR, "Downstream request failed: ...")` |
| 下游返回 200 正常响应 | 正确反序列化为 DTO record |

**验证方式**：
```bash
# 运行契约测试（使用 WireMock 模拟下游错误响应）
./mvnw -pl services/buyer-bff test -Dtest=*ContractTest
```

---

## 七、风险与缓解

| 风险 | 影响 | 缓解措施 |
|------|------|---------|
| `shop-clients` 模块依赖过多 contract 子模块 | 编译时间增加、循环依赖风险 | 按域拆分子模块（`shop-clients-order`、`shop-clients-marketplace`），BFF 按需引用 |
| `BaggageField` 在 Kafka listener 中未初始化 | Baggage 传播在异步场景失效 | 在 Kafka listener 中通过 `@BeforeBatch` hook 手动设置 baggage（参考 [StackOverflow 讨论](https://stackoverflow.com/questions/75912175)） |
| `defaultStatusHandler` 覆盖 tracing 拦截器 | Trace 传播中断 | `defaultStatusHandler` 在 `ObservationRestClientCustomizer` 之后执行（顺序由 `RestClient.Builder` 拦截器链保证），不影响 tracing |
| `shop-clients` 接口变更导致下游不兼容 | BFF 编译失败 | 接口变更视为 breaking change，通过 BOM 版本管理 |

---

## 八、参考链接

| 主题 | 链接 | 日期 |
|------|------|------|
| Spring Boot Tracing — RestClient 自动传播 | https://docs.spring.io/spring-boot/reference/actuator/tracing.html | 当前 (Boot 4.0.5 文档，3.x 行为一致) |
| Spring Boot 3.5 Tracing 最佳实践 | https://meirong.dev/posts/spring-boot-35-tracing-best-practices/ | 2026-04-08 |
| ObservationRestClientCustomizer Javadoc | https://docs.spring.io/spring-boot/3.5.9/api/java/org/springframework/boot/actuate/metrics/web/client/ObservationRestClientCustomizer.html | Spring Boot 3.5.9 |
| RestClient auto-config 不传播 trace 的 issue | https://github.com/spring-projects/spring-boot/issues/42502 | 2024-10-02 |
| RestClient 文档 issue（缺少 RestClient.Builder 提及） | https://github.com/spring-projects/spring-boot/issues/41182 | 2024-06-20 |
| Spring Framework — HTTP Interface Client 文档 | https://docs.spring.io/spring-framework/reference/6.2-SNAPSHOT/web/webflux-http-interface-client.html | Spring 6.2-SNAPSHOT |
| Spring Framework — REST Clients 文档 | https://docs.spring.io/spring-framework/reference/integration/rest-clients.html | Spring 6.2+ |
| Spring 博客 — HTTP Service Client Enhancements (Spring 7) | https://spring.io/blog/2025/09/23/http-service-client-enhancements | 2025-09-23 |
| Spring Framework 6.2.0 — DefaultResponseErrorHandler 变更 | https://github.com/spring-projects/spring-framework/issues/33980 | 2024-11-27 |
| Micrometer Brave Baggage 初始化与传播 | https://stackoverflow.com/questions/75912175/how-to-initialize-micrometer-brave-baggate-field-spring-boot-3 | 2023-04-02 |
| Spring Framework 6.2 发布说明 | https://github.com/spring-projects/spring-framework/wiki/Spring-Framework-6.2-Release-Notes | 2024-11 |
| Terasoluna — Spring 6 HTTP Interface 指南 | https://terasolunaorg.github.io/guideline/current/ja/ArchitectureInDetail/WebServiceDetail/RestClient.html | 持续更新 |
