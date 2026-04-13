---
title: 入口与聚合服务
---

# 入口与聚合服务

这一组服务决定“请求如何进入平台、如何被聚合成端侧可消费的 API，以及页面如何交付”。

## 服务清单

| 服务 | 主职责 | 主技术栈 | 可扩展点 |
|---|---|---|---|
| `api-gateway` | 统一入口、JWT 校验、Trusted Headers、限流、灰度、追踪头 | Spring Cloud Gateway MVC、Spring Security、Redis Lua、OTLP | YAML 路由、自定义 filter、限流阈值、暴露头策略 |
| `auth-server` | 登录、JWT、社交登录、OTP、guest token | Spring Security、JPA、Redis、JWT | `SmsGateway`、社交登录 provider、claims / ttl 策略 |
| `buyer-bff` | 买家聚合、guest cart、结账编排、订单/积分入口 | RestClient、Virtual Threads、Resilience4j、Redis | 下游客户端注册、聚合编排、guest cart 策略、timeout / circuit breaker |
| `seller-bff` | 卖家聚合、商品/订单/促销后台接口 | RestClient、Virtual Threads | 新增下游客户端、聚合查询模型、timeout 配置 |
| `buyer-portal` | 买家 SSR 门户，SEO 友好的商品与购物页面 | Kotlin、Spring Boot、Thymeleaf | API client、Controller、模板与条件渲染 |
| KMP `buyer-app` | 买家端 SPA（Web WASM），交互式购物体验 | Kotlin Multiplatform、Compose Multiplatform | feature 模块、`buyer-bff` 后端 |
| KMP `seller-app` | 卖家管理端（Web WASM / Android / iOS） | Kotlin Multiplatform、Compose Multiplatform | feature 模块、`SellerApi` 契约、`seller-bff` 后端 |

## api-gateway

关键能力：

- 统一 northbound 路由与 JWT 校验。
- 统一注入 `X-Request-Id`、`X-Buyer-Id`、`X-Username`、`X-Roles`、`X-Portal` 等可信头。
- 默认对客户端暴露 `X-Request-Id` 和 `X-Trace-Id`，便于排障。
- Redis Lua 负责限流，Gateway filter 负责追踪关联和流量治理。

适合扩展的地方：

- 新 public path / route / canary 规则。
- 新的 filter（例如审计、灰度、header 规范化）。
- 限流维度与阈值策略。

## auth-server

关键能力：

- 支持登录、guest token、Google / Apple OAuth2、OTP。
- `SmsGateway` 已把短信发送抽成接口，当前有 mock 与 Twilio 两种实现。
- `JwtTokenService` 收敛 token 签发与 claims 逻辑。

适合扩展的地方：

- 新短信供应商。
- 新社交登录提供方。
- token claims、有效期和 issuer 策略。

## Buyer / Seller BFF

共同模式：

- 用 `RestClient + Virtual Threads` 聚合多个下游。
- 把超时、降级和端侧视图模型集中在 BFF，而不污染领域服务。
- buyer-bff 还负责 guest cart 和 checkout 编排，因此更强调 resilience 边界。

适合扩展的地方：

- 新下游服务客户端。
- 新聚合接口或新的 Dashboard 视图模型。
- 非核心依赖的降级与回退策略。

## Buyer Portal（SSR）

`buyer-portal` 的角色不是再发明一套业务服务，而是：

- 复用 Gateway / BFF 的 northbound API（`/api/buyer/**`）。
- 通过 Kotlin + Thymeleaf 渲染 SEO 友好的商品列表、商品详情等公开页面。
- 游客模式下使用 guest JWT，登录后无缝切换到已认证态。
- 把展示层的文案、布局和条件渲染优先留在模板里。

适合扩展的地方：

- 页面模板和组件片段（`templates/fragments/`）。
- Controller / API client 的调用编排。
- 与特性开关联动的 UI 展示。

## Buyer App（KMP WASM）

买家 SPA 由 `kmp/buyer-app` 承载，采用 **Kotlin Multiplatform + Compose Multiplatform**，面向需要更丰富交互体验的买家场景（Cart、结账、订单追踪、积分等）：

- Web WASM 目标，通过网关 `/buyer-app/**` 路由访问。
- 后端对接 `buyer-bff`（`/api/buyer/**`），认证走 `auth-server` JWT。
- 与 `buyer-portal`（SSR）互补：SSR 负责 SEO 和访客浏览，buyer-app 负责登录后的完整交互流程。
- 共享 `kmp/core`、`kmp/ui-shared` 以及 `kmp/feature-*` 功能模块。

## Seller App（KMP）

卖家管理端由 `kmp/seller-app` 承载，采用 **Kotlin Multiplatform + Compose Multiplatform**，覆盖：

- Web（WASM）、Android、iOS 三端共享同一份 feature 代码。
- 后端对接 `seller-bff`（`/api/seller/**`），认证走 `auth-server` JWT。
- 无 SEO 需求，适合纯 SPA / 原生 UI 模型，省去 SSR 维护成本。

KMP 模块结构：

| 模块 | 职责 |
|------|------|
| `kmp/core` | 网络、token 存储、共享数据模型 |
| `kmp/ui-shared` | 共享 Compose UI 组件 |
| `kmp/feature-*` | 功能模块（auth、marketplace、cart、order、wallet、profile、promotion） |
| `kmp/buyer-app` | Web WASM 入口 |
| `kmp/buyer-android-app` | Android 入口 |
| `kmp/seller-app` | Web WASM 入口 |
| `kmp/seller-android-app` | Android 入口 |

## KMP 与后端交互详解

本节详细说明 KMP 客户端（buyer-app / seller-app）如何与后端服务进行通信。

### 整体数据流

```
┌─────────────────┐    HTTPS      ┌─────────────┐   HTTP   ┌───────────┐   HTTP   ┌──────────────┐
│  KMP buyer-app  │ ─────────────→│ api-gateway  │────────→│ buyer-bff │────────→│ 领域服务      │
│  (WASM/Android) │ Bearer JWT    │  :8080       │         │           │         │ (order/wallet │
└─────────────────┘               └─────────────┘         └───────────┘         │  /profile...) │
                                       │                                        └──────────────┘
┌─────────────────┐    HTTPS      │  路由分发 +    │   HTTP   ┌───────────┐
│  KMP seller-app │ ─────────────→│  JWT 校验 +    │────────→│ seller-bff│
│  (WASM/Android) │ Bearer JWT    │  Trusted Headers│        └───────────┘
└─────────────────┘               └─────────────┘
```

### 认证流程

1. **登录**：KMP `AuthRepository` 调用 `/auth/v1/token/login`（不经过 `/api` 前缀），Gateway 路由到 `auth-server`。
2. **Token 存储**：登录成功后，`AccessToken` + `RefreshToken` 保存到共享的 `MutableTokenStorage`。
3. **自动注入**：Ktor Auth 插件从 `tokenStorage.loadTokens()` 读取 token，每次 API 请求自动加 `Authorization: Bearer <token>` 头。
4. **Token 刷新**：当 API 返回 401 时，Auth 插件自动调用 `/auth/v1/token/refresh` 刷新 token，刷新后重试原请求。

代码关键路径：

```
kmp/core/session/TokenStorage.kt          → Token 存储接口
kmp/core/network/HttpClientFactory.kt      → 创建带 Auth 插件的 HttpClient
kmp/feature-auth/data/AuthRepository.kt    → 登录/注册（使用 NoOpTokenStorage）
kmp/buyer-app/BuyerApp.kt                  → 创建共享 MutableTokenStorage
kmp/seller-app/SellerApp.kt                → 创建共享 MutableTokenStorage
```

### URL 路由规则

KMP 应用通过 `gatewayApiBaseUrl()` 获取基础 URL：

| 环境 | `gatewayApiBaseUrl()` 返回值 | 说明 |
|------|------------------------------|------|
| WASM（浏览器） | `""` (空字符串) | 相对路径，浏览器自动使用当前域名 |
| Android/iOS | `"http://10.0.2.2:8080/api"` | 模拟器访问宿主机 |

请求 URL 构建示例：

```
# Buyer 获取购物车
baseUrl + "/buyer/v1/cart/list"
→ WASM: "/api/buyer/v1/cart/list"（相对路径）
→ Gateway 匹配 /api/buyer/** → StripPrefix=1 → buyer-bff 收到 "/buyer/v1/cart/list"

# 登录
authBaseUrl + "/auth/v1/token/login"
→ WASM: "/auth/v1/token/login"（相对路径）
→ Gateway 匹配 /auth/** → auth-server 收到 "/auth/v1/token/login"

# Seller 获取商品列表
baseUrl + "/seller/v1/marketplace/products"
→ WASM: "/api/seller/v1/marketplace/products"（相对路径）
→ Gateway 匹配 /api/seller/** → StripPrefix=1 → seller-bff 收到 "/seller/v1/marketplace/products"
```

### Gateway 路由 StripPrefix 规则

| Gateway 路径 | StripPrefix | 后端服务 | 后端收到的路径 |
|---|---|---|---|
| `/auth/**` | 无 | auth-server | `/auth/**` |
| `/buyer/**` | 无 | buyer-portal | `/buyer/**` |
| `/buyer-app/**` | 1 | buyer-app (nginx) | `/**` |
| `/seller/**` | 1 | seller-portal (nginx) | `/**` |
| `/api/buyer/**` | 1 | buyer-bff | `/buyer/**` |
| `/api/seller/**` | 1 | seller-bff | `/seller/**` |
| `/api/loyalty/**` | 1 | loyalty-service | `/loyalty/**` |
| `/api/activity/**` | 1 | activity-service | `/activity/**` |
| `/api/webhook/**` | 1 | webhook-service | `/webhook/**` |
| `/api/subscription/**` | 1 | subscription-service | `/subscription/**` |
| `/public/buyer/**` | 1 | buyer-bff | `/buyer/**` |

> 注意：`search-service` 没有直接的 Gateway API 路由，由 BFF 通过内部地址直接调用。

### KMP 模块与后端 API 对应关系

| KMP Feature 模块 | 调用的 BFF/Service | 核心 API 路径 |
|---|---|---|
| `feature-auth` | auth-server | `/auth/v1/token/login`, `/auth/v1/token/refresh`, `/auth/v1/register` |
| `feature-marketplace` | buyer-bff / seller-bff | `/buyer/v1/marketplace/**`, `/seller/v1/marketplace/**` |
| `feature-cart` | buyer-bff | `/buyer/v1/cart/list`, `/buyer/v1/cart/add`, `/buyer/v1/cart/checkout` |
| `feature-order` | buyer-bff / seller-bff | `/buyer/v1/orders/**`, `/seller/v1/orders/**` |
| `feature-wallet` | buyer-bff / seller-bff | `/buyer/v1/wallet/**`, `/seller/v1/wallet/**` |
| `feature-profile` | buyer-bff / seller-bff | `/buyer/v1/profile/**`, `/seller/v1/profile/**` |
| `feature-promotion` | buyer-bff / seller-bff | `/buyer/v1/promotion/**`, `/seller/v1/promotion/**` |

### 请求/响应契约

- **请求 DTO**：KMP 端在各 Repository 中定义 `@Serializable` 私有 DTO（如 `BuyerContextRequestDto`），字段名必须与 `shop-contracts` 中 Java DTO 的 JSON 字段名一致。
- **响应包装**：所有后端 API 返回 `ApiResponse<T>` 结构，包含 `success`、`data`、`message` 字段。
- **错误处理**：`ApiResponse.success == false` 时，KMP 端从 `message` 字段提取错误信息显示给用户。
- **JSON 解析**：使用 `kotlinx.serialization`，配置 `ignoreUnknownKeys = true`，允许后端新增字段而不影响客户端。

### BFF 聚合模式

BFF 在 KMP 与领域服务之间起到聚合、适配和容错的作用：

```
KMP buyer-app → buyer-bff → marketplace-service  (商品信息)
                           → order-service        (订单操作)
                           → wallet-service       (支付/钱包)
                           → promotion-service    (优惠券)
                           → loyalty-service      (积分)
                           → activity-service     (活动)

KMP seller-app → seller-bff → marketplace-service (商品管理)
                             → order-service       (订单管理)
                             → promotion-service   (促销管理)
                             → profile-service     (店铺资料)
                             → subscription-service(订阅管理)
```

- **非核心依赖降级**：buyer-bff 对 promotion/loyalty 使用 Resilience4j CircuitBreaker，降级时返回空/默认值。
- **核心依赖快速失败**：marketplace/order 调用失败直接抛异常，KMP 端展示错误。
