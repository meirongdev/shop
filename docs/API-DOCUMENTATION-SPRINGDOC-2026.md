# Shop Platform — SpringDoc OpenAPI 统一 API 文档 2026 最佳实践

> 版本：1.0 | 日期：2026-03-23
> 适用范围：所有暴露 HTTP 接口的服务（Gateway / Auth / BFF / Domain Service）

---

## 一、目标与原则

### 1.1 目标

| 目标 | 说明 |
|------|------|
| **统一入口** | 所有服务的 API 通过网关聚合在单一 Swagger UI 中，方便联调与对接 |
| **契约可机读** | 每个服务暴露标准 `/v3/api-docs`（JSON），可用于自动化代码生成、契约测试 |
| **安全可用** | Swagger UI 内置 JWT Bearer 认证支持，生产环境默认关闭 |
| **注解成本低** | 优先利用 SpringDoc 自动推断，只在必要处补充 `@Operation` / `@Schema` |

### 1.2 版本依赖基线

```
Spring Boot      3.5.11
Spring Cloud     2025.0.1
springdoc-openapi 2.8.9   （父 POM 已统一管理）
```

---

## 二、架构设计：网关聚合模式

```
浏览器
  └─► http://localhost:8080/swagger-ui.html   (api-gateway SwaggerUI)
        ├─ [Buyer BFF]   → 代理 /v3/api-docs/buyer  → buyer-bff:8080/v3/api-docs
        ├─ [Seller BFF]  → 代理 /v3/api-docs/seller → seller-bff:8080/v3/api-docs
        ├─ [Auth Server] → 代理 /v3/api-docs/auth   → auth-server:8080/v3/api-docs
        ├─ [Loyalty]     → 代理 /v3/api-docs/loyalty → loyalty-service:8080/v3/api-docs
        └─ ...（其他服务）
```

**核心设计原则：**
- 每个服务自己维护、自己生成 OpenAPI JSON，网关**只做代理和聚合展示**
- 网关不承载任何 API 文档逻辑，仅透传 `/v3/api-docs/*` 请求
- 内部服务（Domain Services）也暴露 `/v3/api-docs`，通过 internal 路由访问（本地开发直连）

---

## 三、网关配置（api-gateway）

### 3.1 添加依赖

`api-gateway` 使用 `spring-cloud-starter-gateway-server-webmvc`，添加 WebMVC 版 SpringDoc：

```xml
<!-- api-gateway/pom.xml -->
<dependency>
    <groupId>org.springdoc</groupId>
    <artifactId>springdoc-openapi-starter-webmvc-ui</artifactId>
</dependency>
```

### 3.2 添加 API Docs 代理路由

在 `api-gateway/src/main/resources/application.yml` 的 `routes` 部分追加：

```yaml
spring:
  cloud:
    gateway:
      server:
        webmvc:
          routes:
            # ── 已有业务路由（保持不变） ──────────────────────────────────────────

            # ── OpenAPI 文档聚合路由（仅在非生产环境生效，通过 profile 控制） ──────
            - id: api-docs-buyer-bff
              uri: ${BUYER_BFF_URI:http://buyer-bff:8080}
              predicates:
                - Path=/v3/api-docs/buyer
              filters:
                - RewritePath=/v3/api-docs/buyer, /v3/api-docs

            - id: api-docs-seller-bff
              uri: ${SELLER_BFF_URI:http://seller-bff:8080}
              predicates:
                - Path=/v3/api-docs/seller
              filters:
                - RewritePath=/v3/api-docs/seller, /v3/api-docs

            - id: api-docs-auth
              uri: ${AUTH_SERVER_URI:http://auth-server:8080}
              predicates:
                - Path=/v3/api-docs/auth
              filters:
                - RewritePath=/v3/api-docs/auth, /v3/api-docs

            - id: api-docs-loyalty
              uri: ${LOYALTY_SERVICE_URI:http://loyalty-service:8080}
              predicates:
                - Path=/v3/api-docs/loyalty
              filters:
                - RewritePath=/v3/api-docs/loyalty, /v3/api-docs

            - id: api-docs-activity
              uri: ${ACTIVITY_SERVICE_URI:http://activity-service:8080}
              predicates:
                - Path=/v3/api-docs/activity
              filters:
                - RewritePath=/v3/api-docs/activity, /v3/api-docs

            - id: api-docs-webhook
              uri: ${WEBHOOK_SERVICE_URI:http://webhook-service:8080}
              predicates:
                - Path=/v3/api-docs/webhook
              filters:
                - RewritePath=/v3/api-docs/webhook, /v3/api-docs

            - id: api-docs-subscription
              uri: ${SUBSCRIPTION_SERVICE_URI:http://subscription-service:8080}
              predicates:
                - Path=/v3/api-docs/subscription
              filters:
                - RewritePath=/v3/api-docs/subscription, /v3/api-docs
```

### 3.3 SwaggerUI 聚合配置

```yaml
# api-gateway/src/main/resources/application.yml
springdoc:
  api-docs:
    enabled: ${SPRINGDOC_ENABLED:true}
  swagger-ui:
    enabled: ${SPRINGDOC_ENABLED:true}
    path: /swagger-ui.html
    # 聚合所有下游服务
    urls:
      - name: Buyer BFF
        url: /v3/api-docs/buyer
      - name: Seller BFF
        url: /v3/api-docs/seller
      - name: Auth Server
        url: /v3/api-docs/auth
      - name: Loyalty Service
        url: /v3/api-docs/loyalty
      - name: Activity Service
        url: /v3/api-docs/activity
      - name: Webhook Service
        url: /v3/api-docs/webhook
      - name: Subscription Service
        url: /v3/api-docs/subscription
    # 默认展示的服务
    urls-primary-name: Buyer BFF
    # 持久化 Auth Token，刷新页面不丢失
    persist-authorization: true
    # 操作展示模式：list（平铺）/ full（展开）/ none（折叠）
    operations-sorter: alpha
    tags-sorter: alpha
    display-request-duration: true
    # 关闭"try-it-out"默认开启（按需启用）
    try-it-out-enabled: false
```

### 3.4 网关侧 OpenAPI Bean（只做聚合元信息）

```java
// api-gateway/src/main/java/dev/meirong/shop/gateway/config/OpenApiConfig.java
package dev.meirong.shop.gateway.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI gatewayOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("Shop Platform API")
                        .description("微服务聚合文档 — 通过网关统一访问所有服务接口")
                        .version("v1")
                        .contact(new Contact().name("Shop Platform Team")));
    }
}
```

---

## 四、各服务配置标准

### 4.1 通用 YAML 配置（所有服务）

所有暴露 HTTP 接口的服务在 `application.yml` 中添加：

```yaml
springdoc:
  api-docs:
    enabled: ${SPRINGDOC_ENABLED:true}
    path: /v3/api-docs
  swagger-ui:
    # 本地开发访问 http://localhost:{port}/swagger-ui.html
    enabled: ${SPRINGDOC_ENABLED:true}
    path: /swagger-ui.html
    # 在 Swagger UI 中默认启用 Bearer Token 输入框
    persist-authorization: true
  # 将 Actuator 端点从文档中排除
  paths-to-exclude: /actuator/**
```

**生产环境关闭：**

```yaml
# application-prod.yml / K8s ConfigMap
SPRINGDOC_ENABLED: "false"
```

### 4.2 OpenAPI Bean 标准（每个服务）

以 `buyer-bff` 为例，其他服务类似：

```java
// buyer-bff/.../config/OpenApiConfig.java
package dev.meirong.shop.buyerbff.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    private static final String BEARER_AUTH = "BearerAuth";

    @Bean
    public OpenAPI buyerBffOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("Buyer BFF API")
                        .description("买家端聚合服务接口 — 面向买家端 App/Web 的 BFF 层")
                        .version("v1"))
                // 全局声明 JWT Bearer 认证
                .addSecurityItem(new SecurityRequirement().addList(BEARER_AUTH))
                .components(new Components()
                        .addSecuritySchemes(BEARER_AUTH, new SecurityScheme()
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")
                                .description("通过 /auth/token 获取 JWT，填入此处（不需要加 'Bearer ' 前缀）")));
    }
}
```

各服务 OpenAPI Bean 标题参考：

| 服务 | title | description |
|------|-------|-------------|
| `buyer-bff` | Buyer BFF API | 买家端聚合服务接口 |
| `seller-bff` | Seller BFF API | 卖家端聚合服务接口 |
| `auth-server` | Auth Server API | 认证与 Token 管理 |
| `loyalty-service` | Loyalty Service API | 积分与忠诚度管理 |
| `activity-service` | Activity Service API | 用户行为与游戏化 |
| `webhook-service` | Webhook Service API | Webhook 配置与触发 |
| `subscription-service` | Subscription Service API | 订阅计划管理 |

---

## 五、Controller 注解规范

### 5.1 注解分层策略

SpringDoc 会自动从方法签名、`@RequestBody`、`@PathVariable`、Jakarta Validation 推断大量信息。**只在以下场景补充注解：**

| 场景 | 注解 | 说明 |
|------|------|------|
| 控制器分组 | `@Tag` | 为一组接口指定名称与描述（类级别） |
| 接口说明不直观 | `@Operation(summary)` | 补充一句话摘要 |
| 多种错误响应 | `@ApiResponse` | 明确 4xx/5xx 响应体 |
| 部分端点无需认证 | `@SecurityRequirements({})` | 覆盖全局安全要求 |
| 不需要出现在文档中 | `@Hidden` | 隐藏内部端点 |

### 5.2 示例：BFF 控制器

```java
@Tag(name = "商品", description = "买家端商品浏览、搜索相关接口")
@RestController
@RequestMapping("/buyer/products")
public class BuyerProductController {

    @Operation(summary = "搜索商品列表")
    @ApiResponse(responseCode = "200", description = "搜索成功")
    @ApiResponse(responseCode = "400", description = "参数非法",
            content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    @GetMapping
    public ApiResponse<Page<ProductSummaryDto>> search(@Valid ProductSearchRequest request) { ... }

    @Operation(summary = "获取商品详情")
    @GetMapping("/{productId}")
    public ApiResponse<ProductDetailDto> getDetail(@PathVariable String productId) { ... }

    // 内部探活端点：不出现在文档中
    @Hidden
    @GetMapping("/internal/ping")
    public String ping() { return "pong"; }
}
```

### 5.3 示例：Auth Controller（公开端点）

```java
@Tag(name = "认证", description = "Token 获取与刷新")
@RestController
@RequestMapping("/auth")
public class AuthController {

    // 登录不需要 JWT，覆盖全局 SecurityRequirement
    @SecurityRequirements  // 空注解 = 无认证要求
    @Operation(summary = "用户登录，获取 JWT")
    @PostMapping("/token")
    public TokenResponse login(@Valid @RequestBody LoginRequest request) { ... }

    @Operation(summary = "刷新 JWT")
    @PostMapping("/token/refresh")
    public TokenResponse refresh(@Valid @RequestBody RefreshTokenRequest request) { ... }
}
```

---

## 六、shop-contracts DTO 注解规范

`shop-contracts` 中的 DTO 是所有服务共享的契约，在此处添加 `@Schema` 可让所有服务的文档同步受益：

```java
// shop-contracts/.../dto/ProductSummaryDto.java
@Schema(description = "商品摘要信息（列表页）")
public record ProductSummaryDto(
        @Schema(description = "商品 ID", example = "prod_abc123")
        String productId,

        @Schema(description = "商品标题", example = "经典白T恤 M码")
        String title,

        @Schema(description = "价格（分）", example = "9900")
        Long priceInCents,

        @Schema(description = "库存状态", allowableValues = {"IN_STOCK", "LOW_STOCK", "OUT_OF_STOCK"})
        String stockStatus
) {}
```

**原则：**
- `@Schema` 加在 `shop-contracts` 的共享 DTO，不在各服务重复
- 使用 `example` 字段提供真实示例值，提升联调效率
- 枚举类型用 `allowableValues` 或直接在枚举上加 `@Schema`

---

## 七、内部服务调用排除策略

### 7.1 内部端点用 `@Hidden` 隐藏

```java
// 不希望暴露给外部的管理/内部端点
@Hidden
@RestController
@RequestMapping("/internal")
public class InternalAdminController { ... }
```

### 7.2 排除 Actuator 路径

已在 YAML 中配置 `paths-to-exclude: /actuator/**`，Actuator 端点不出现在文档中。

### 7.3 Domain Service 的文档访问控制

Domain Service（`profile-service`、`marketplace-service` 等）不通过外部网关暴露 Swagger UI，**仅在本地开发直连端口访问**：

```yaml
# domain-service/application-local.yml
springdoc:
  api-docs:
    enabled: true
  swagger-ui:
    enabled: true
```

```yaml
# domain-service/application-prod.yml
springdoc:
  api-docs:
    enabled: false
  swagger-ui:
    enabled: false
```

---

## 八、安全配置细节

### 8.1 Swagger UI 访问不需要 JWT

网关侧需要在 JWT 过滤器中**放行 Swagger 相关路径**：

```java
// api-gateway/.../filter/JwtAuthFilter.java（或安全配置）
private static final List<String> PUBLIC_PATHS = List.of(
    "/auth/**",
    "/public/**",
    "/swagger-ui/**",
    "/swagger-ui.html",
    "/v3/api-docs/**",
    "/webjars/**"
);
```

### 8.2 "Try it out" 与 JWT 使用流程

1. 打开 `http://localhost:8080/swagger-ui.html`
2. 选择 **Auth Server** 服务，调用 `POST /auth/token` 获取 JWT
3. 点击右上角 **Authorize** 按钮，粘贴 JWT（不需要加 `Bearer ` 前缀）
4. 切换到目标服务（如 **Buyer BFF**），所有请求自动携带 `Authorization: Bearer <token>`

---

## 九、文档质量检查清单

每个服务上线前，确认以下事项：

- [ ] `OpenApiConfig` Bean 已创建，`title` / `description` / `version` 已填写
- [ ] 所有 `@RestController` 已添加 `@Tag`
- [ ] 公开端点（无需认证）已标注 `@SecurityRequirements`
- [ ] 内部/管理端点已标注 `@Hidden`
- [ ] 主要 DTO 已在 `shop-contracts` 添加 `@Schema(description, example)`
- [ ] `application.yml` 已配置 `springdoc.api-docs.enabled: ${SPRINGDOC_ENABLED:true}`
- [ ] 网关 `application.yml` 已添加对应的 `/v3/api-docs/{serviceName}` 路由
- [ ] 网关 `springdoc.swagger-ui.urls` 已添加对应条目
- [ ] `application-prod.yml` 中 `SPRINGDOC_ENABLED=false`

---

## 十、本地快速验证

```bash
# 启动所有服务后，访问网关聚合文档
open http://localhost:8080/swagger-ui.html

# 直接访问某服务的 OpenAPI JSON（不经过网关）
curl http://localhost:8083/v3/api-docs | jq .info

# 验证网关代理是否正常
curl http://localhost:8080/v3/api-docs/buyer | jq .info
```

---

## 十一、架构决策记录（ADR）

### ADR-001：选择网关聚合而非独立文档服务

**决策**：Swagger UI 部署在 `api-gateway`，通过路由代理各服务 `/v3/api-docs`，而非引入独立的 API Portal 服务。

**理由**：
- 零额外服务成本；路由逻辑复用已有网关基础设施
- 文档路径与业务路径统一在一个域名下，无跨域问题
- 网关已承载 JWT 验证逻辑，Swagger UI 的认证流程天然打通

**权衡**：
- 网关需要添加 SpringDoc 依赖（约 2MB，可接受）
- 若某个下游服务不可用，其对应文档 Tab 会显示加载失败，不影响其他服务

### ADR-002：注解最小化策略

**决策**：不要求所有 Controller 方法都加 `@Operation`，仅在自动推断不足时补充。

**理由**：
- SpringDoc 2.x 已能从方法签名、参数类型、Jackson 等自动生成 80% 的文档
- 过度注解导致与代码双倍维护成本，且容易产生文档与实现不一致
- `shop-contracts` 的 DTO 注解一次书写，全服务受益

---

## 十二、演进路径

| 阶段 | 事项 |
|------|------|
| **当前（Phase 1）** | 本文档所描述的基线：网关聚合 + 各服务 OpenAPI Bean + 关键 DTO `@Schema` |
| **Phase 2** | 接入 `springdoc-openapi-maven-plugin` 在 CI 中生成静态 OpenAPI JSON，推送到 `docs-site/` 存档 |
| **Phase 3** | 使用 OpenAPI Generator 为前端/移动端生成类型化客户端 SDK（TypeScript / Swift / Kotlin） |
| **Phase 4** | Spectral / Redocly Lint 在 CI 中对 OpenAPI JSON 做规范校验（禁止裸 `string`、要求 `example`） |
