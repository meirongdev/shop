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
| KMP `seller-app` | 卖家管理端（Web WASM / Android / iOS） | Kotlin Multiplatform、Compose Multiplatform | feature 模块、`SellerApi` 契约、`seller-bff` 后端 |

## api-gateway

关键能力：

- 统一 northbound 路由与 JWT 校验。
- 统一注入 `X-Request-Id`、`X-Buyer-Id`、`X-Buyer-Id`、`X-Internal-Token` 等可信头。
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
| `kmp/seller-app` | Web WASM 入口 |
| `kmp/seller-android-app` | Android 入口 |
