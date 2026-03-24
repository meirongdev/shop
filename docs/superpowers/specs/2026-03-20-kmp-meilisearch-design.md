# KMP 跨平台应用 + Meilisearch 搜索架构设计

> 日期：2026-03-20
> 状态：Draft
> 范围：Compose Multiplatform 替换 Thymeleaf Portal + Meilisearch 商品搜索

---

## 1. 背景与目标

### 现状

- buyer-portal / seller-portal 基于 Kotlin + Thymeleaf SSR，仅支持 Web
- 商品搜索使用 MySQL LIKE 模糊匹配，无分词、无 typo tolerance、无 facet
- 原 Roadmap Phase 3 计划引入 Elasticsearch，但 ES 运维成本高

### 目标

1. 使用 **Compose Multiplatform** 实现 Android + iOS + Web (Wasm) 跨平台应用，替换现有 Thymeleaf Portal
2. 使用 **Meilisearch** 替代 Elasticsearch 实现商品搜索，先商品后按需扩展
3. 遵循 2026 最佳实践，方便后续开发

---

## 2. 关键决策与 Tradeoff

### 2.1 KMP 项目结构

| 方案 | 描述 | 优点 | 缺点 |
|------|------|------|------|
| A — Gradle + Maven 共存，扁平结构 | `kmp/` 下放 core、buyer-app、seller-app | 简单 | feature 无法在 buyer/seller 间复用 |
| B — 独立仓库 | KMP 应用放新 repo，OpenAPI code-gen 生成模型 | 构建系统干净 | 模型同步依赖 code-gen，两 repo 一致性维护成本高 |
| **C — 单仓库，分层 feature 模块（选定）** | `kmp/` 下分 core、ui-shared、feature-*、buyer-app、seller-app | feature 可跨 app 复用，职责清晰，与 Jetpack Navigation per-feature graph 契合 | 初始模块多（但长期受益） |

**选定方案 C 的原因**：买家和卖家共享多个 feature（订单、钱包、个人资料），feature 模块化是 KMP 社区最佳实践，避免重复代码。

### 2.2 Meilisearch 集成方式

| 方案 | 描述 | 优点 | 缺点 |
|------|------|------|------|
| X — marketplace-service 内嵌 | 搜索逻辑内嵌在 marketplace-service | 最简单 | 职责膨胀，扩展到订单搜索时需侵入多服务 |
| **Y — 独立 search-service（选定）** | 新增 search-service 消费 Kafka 事件同步索引 | 关注点分离，扩展只需新增 consumer + index | 多一个服务 |
| Z — BFF 直连 Meilisearch | BFF 直接查询 Meilisearch | 少一跳 | 破坏分层，多 BFF 重复搜索逻辑 |

**选定方案 Y 的原因**：与现有 Outbox + Kafka 架构一致，Roadmap 明确后续要扩展搜索范围，独立服务是必然归宿。

### 2.3 其他候选搜索方案的排除

用户考虑过 Garage + Quickwit 和 Garage + OpenObserve 作为替代：

- **Quickwit**：基于对象存储的日志/事件搜索引擎，擅长 append-only 分析，不支持商品搜索所需的 typo tolerance、facet、即时建议
- **OpenObserve**：定位是可观测性平台（日志/metrics/traces），非应用搜索工具
- **Garage**：分布式 S3 兼容存储，是 Quickwit/OpenObserve 的底层存储，与应用搜索无直接关系

结论：这些工具适合可观测性场景（可未来替换 Loki+Tempo），但不适合商品搜索。两条线可并存但不互相替代。当前仅关注 Meilisearch 商品搜索，可观测性栈暂不动。

### 2.4 其他技术选型

| 决策点 | 选择 | 原因 |
|--------|------|------|
| 导航/状态 | Jetpack Navigation + ViewModel | Google 2025 已官方支持 KMP，最佳生态兼容 |
| 网络层 | Ktor Client | JetBrains 官方，KMP 原生支持 |
| DI | Koin | 原生支持 KMP 全平台，纯 Kotlin 实现，无注解处理器（Hilt 仅 Android） |
| Web 部署 | Nginx/Caddy 独立静态站 | 前后端完全分离，Wasm 产物作为静态资源 |
| 迁移策略 | BFF 瘦身为纯 API 聚合 + KMP 接管渲染 | 保留 BFF 聚合价值，去掉 Thymeleaf 渲染职责 |
| API 契约同步 | 手动对齐 + CI 校验 | 避免 OpenAPI code-gen 质量问题，自动化守护一致性 |

---

## 3. 架构设计

### 3.1 KMP 模块结构

```
shop/
├── pom.xml                              ← Maven（后端，不变）
├── settings.gradle.kts                  ← Gradle KMP root
├── gradle.properties
├── build.gradle.kts                     ← Gradle convention plugins
├── kmp/
│   ├── core/                            ← :kmp:core
│   ├── ui-shared/                       ← :kmp:ui-shared
│   ├── feature-marketplace/             ← :kmp:feature-marketplace
│   ├── feature-cart/                    ← :kmp:feature-cart
│   ├── feature-order/                   ← :kmp:feature-order
│   ├── feature-wallet/                  ← :kmp:feature-wallet
│   ├── feature-profile/                 ← :kmp:feature-profile
│   ├── feature-promotion/               ← :kmp:feature-promotion
│   ├── feature-auth/                    ← :kmp:feature-auth
│   ├── buyer-app/                       ← :kmp:buyer-app
│   └── seller-app/                      ← :kmp:seller-app
```

### 3.2 模块职责与依赖

```
buyer-app / seller-app
    ├── feature-marketplace
    ├── feature-cart          (buyer only)
    ├── feature-order
    ├── feature-wallet
    ├── feature-profile
    ├── feature-promotion
    ├── feature-auth
    ├── ui-shared
    └── core

core（零 UI 依赖）
    ├── networking     — Ktor Client 封装，拦截器（auth token 注入、错误处理）
    ├── models         — 与 shop-contracts 对齐的 @Serializable data class
    ├── di             — Koin 模块定义
    ├── session        — Token 存储（expect/actual: Android EncryptedSharedPrefs / iOS Keychain / Web cookie）
    └── platform       — 平台抽象

ui-shared（依赖 core）
    ├── theme          — Material 3 主题、颜色、字体
    ├── components     — 通用组件（ProductCard, OrderItem, PriceTag, SearchBar...）
    └── navigation     — 共享 NavHost 配置工具

feature-*（依赖 core + ui-shared）
    ├── data           — Repository 实现，调用 core/networking
    ├── domain         — 业务逻辑（简单 feature 可省略）
    └── ui             — Screen composables + ViewModel
```

### 3.3 平台 Target

| 平台 | Target | 产物 |
|------|--------|------|
| Android | Kotlin/JVM | AAR → APK |
| iOS | Kotlin/Native | XCFramework |
| Web | Kotlin/Wasm | JS + Wasm 静态资源 |

### 3.4 调用链路

```
KMP App（任意平台）
    │  HTTPS / JSON
    ▼
api-gateway (8080)
    │  JWT 校验 → 注入 Trusted Headers
    ▼
buyer-bff / seller-bff
    │  聚合调用
    ├──→ marketplace-service    （商品 CRUD）
    ├──→ search-service         （商品搜索）
    ├──→ order-service          （订单）
    ├──→ wallet-service         （钱包）
    ├──→ profile-service        （用户资料）
    └──→ promotion-service      （促销）
```

### 3.5 Ktor Client 封装

```kotlin
// core/networking
expect fun createHttpEngine(): HttpClientEngine

val httpClient = HttpClient(createHttpEngine()) {
    install(ContentNegotiation) { json(lenientJson) }
    install(Auth) {
        bearer {
            loadTokens { tokenStorage.get() }
            refreshTokens { refreshToken() }
        }
    }
    install(DefaultRequest) {
        url(BuildConfig.API_GATEWAY_URL)
    }
    install(ResponseValidator) {
        // 统一处理 ApiResponse<T> 中的错误码
    }
}
```

### 3.6 认证流程

**游客：**
```
KMP App → auth-server /auth/v1/token/guest（仅 buyer portal）
         ← guest buyer JWT access_token
后续请求：
KMP App → Gateway（Bearer token）→ /api/buyer/** → BFF → 下游服务
```

当前实现说明：

- 为了复用现有 Gateway `/api/**` JWT 鉴权与可信头注入链路，游客模式当前采用 **buyer-only guest JWT**，而不是最初草案中的 `guest_session_id` 白名单方案。
- guest buyer 允许浏览、搜索、促销查看与购物车操作。
- `wallet`、`checkout`、`order history`、`profile` 等账户/资金相关能力仍要求完整 buyer 登录。

**登录用户：**
```
KMP App → auth-server /auth/v1/login
         ← JWT access_token + refresh_token
后续请求：
KMP App → Gateway（Bearer token）→ BFF → 下游服务
```

**Token 存储（expect/actual）：**

| 平台 | 实现 |
|------|------|
| Android | EncryptedSharedPreferences |
| iOS | Keychain |
| Web | HttpOnly cookie（gateway 层 Set-Cookie） |

---

## 4. search-service + Meilisearch 设计

### 4.1 服务架构

```
┌─────────────────┐    Kafka     ┌────────────────┐     HTTP     ┌─────────────┐
│ marketplace-svc │──────────────│ search-service │─────────────│ Meilisearch │
│  (Outbox+Kafka) │  marketplace.│   (8091)       │  index/query │  (7700)     │
│                 │  product.    │                │             │             │
│                 │  events.v1   │                │             │             │
└─────────────────┘              └────────────────┘             └─────────────┘
                                        ▲
                                        │ REST
                                  buyer-bff / seller-bff
```

### 4.2 数据同步

**增量同步（Kafka Consumer）：**

| 事件 | Meilisearch 操作 |
|------|-------------------|
| PRODUCT_CREATED | addDocuments |
| PRODUCT_UPDATED | updateDocuments |
| PRODUCT_DELETED | deleteDocument |
| PRODUCT_PUBLISHED | 添加到索引 |
| PRODUCT_UNPUBLISHED | 从索引移除 |

**全量同步（Blue-Green Index Swap）：**
1. search-service 创建新索引 `products_v{timestamp}`
2. 调用 marketplace-service 内部分页 API（`GET /marketplace/internal/products?page=0&size=500`），逐批写入新索引
3. 全量写入完成后，通过 Meilisearch index swap API 原子切换 `products` → `products_v{timestamp}`
4. 删除旧索引

触发方式：启动时自动执行 + `POST /search/v1/products/_reindex` 手动触发。

### 4.3 Meilisearch 索引配置

```json
{
  "uid": "products",
  "primaryKey": "id",
  "searchableAttributes": ["name", "description", "categoryName"],
  "filterableAttributes": ["categoryId", "sellerId", "published", "priceInCents"],
  "sortableAttributes": ["priceInCents", "createdAt", "name"],
  "rankingRules": ["words", "typo", "proximity", "attribute", "sort", "exactness"],
  "faceting": { "maxValuesPerFacet": 100 },
  "pagination": { "maxTotalHits": 5000 },
  "typoTolerance": { "enabled": true }
}
```

中文分词：Meilisearch v1.x 内置 CJK tokenizer，无需额外插件。

### 4.4 search-service API

```
GET  /search/v1/products?q=关键词&categoryId=xxx&sort=price:asc&page=1&hitsPerPage=20
POST /search/v1/products/_reindex          （内部 API，全量重建）
GET  /search/v1/health                     （健康检查）
```

响应格式遵循 `ApiResponse<T>` 规范。

### 4.5 技术栈

Kotlin + Spring Boot 3.5 + Spring Kafka consumer + Meilisearch Java SDK。与其他服务技术栈一致。

### 4.6 扩展路径

扩展搜索范围只需：
1. 新增 Kafka consumer（如 `order.events.v1`）
2. 新增 Meilisearch index（如 `orders`）
3. 新增 API 端点（如 `/search/v1/orders`）

无需修改任何现有服务。

---

## 5. BFF 瘦身改造

### 移除

- Thymeleaf 依赖及所有 `templates/`
- Controller 中返回 `ModelAndView` 的方法
- 静态资源（CSS/JS/images）

### 保留并强化

- REST API 端点（返回 JSON）
- 多服务聚合逻辑
- 请求校验、DTO 转换
- Trusted header 传递机制

### 新增

- 搜索代理端点：BFF → search-service
- API 版本前缀 `/api/v1/` 统一规范

---

## 6. 部署架构

### 6.1 Web 静态站

```
                    ┌──────────────────────┐
                    │   Nginx / Caddy      │
                    │   buyer.shop.com     │  ← Compose Wasm 产物
                    │   seller.shop.com    │  ← Compose Wasm 产物
                    └──────────┬───────────┘
                               │ /api/* 反向代理
                               ▼
                    ┌──────────────────────┐
                    │   api-gateway (8080) │
                    └──────────────────────┘
```

Nginx 配置要点：
- SPA fallback：非静态资源路由返回 `index.html`
- Wasm MIME type：`application/wasm`
- 开启 gzip/brotli 压缩

### 6.2 Docker Compose 新增

```yaml
search-service:
  build: ./search-service
  ports: ["8091:8091"]
  depends_on:
    kafka:
      condition: service_healthy
    meilisearch:
      condition: service_healthy

meilisearch:
  image: getmeili/meilisearch:v1.12
  ports: ["7700:7700"]
  volumes: [meili_data:/meili_data]
  environment:
    MEILI_ENV: production
    MEILI_MASTER_KEY: ${MEILI_MASTER_KEY}
  healthcheck:
    test: ["CMD", "curl", "-f", "http://localhost:7700/health"]
    interval: 10s
    timeout: 5s
    retries: 5

buyer-web:
  image: nginx:1.27-alpine
  ports: ["8100:80"]
  volumes:
    - ./kmp/buyer-app/build/dist/wasmJs/productionExecutable:/usr/share/nginx/html:ro

seller-web:
  image: nginx:1.27-alpine
  ports: ["8101:80"]
  volumes:
    - ./kmp/seller-app/build/dist/wasmJs/productionExecutable:/usr/share/nginx/html:ro
```

### 6.3 K8s

- search-service：Deployment + Service + HPA
- meilisearch：StatefulSet + PVC（索引持久化）
- buyer-web / seller-web：Deployment + Nginx ConfigMap + Ingress

---

## 7. 迁移阶段

| 阶段 | 内容 | 产出 |
|------|------|------|
| **P0: 基础设施** | Gradle 构建搭建、core 模块、ui-shared 主题、Ktor Client 封装、Koin DI | 三平台空壳 App 可编译 |
| **P1: search-service** | search-service 开发、Kafka consumer、Meilisearch 部署、BFF 搜索代理 | 商品搜索可用 |
| **P2: auth + marketplace** | 登录/游客流程、商品列表/详情/搜索页面 | 核心浏览体验 |
| **P3: cart + order** | 购物车、下单、订单列表/详情 | 核心交易流程 |
| **P4: wallet + profile + promotion** | 钱包充值/支付、个人资料、促销管理 | 功能完整 |
| **P5: 收尾** | 移除 Thymeleaf 依赖、清理旧代码、正式切流 | 迁移完成 |

P0 + P1 可并行（无依赖）。旧 Thymeleaf Portal 在 P5 之前保持运行。

---

## 8. API 契约同步策略

`shop-contracts`（Java records）与 `kmp/core/models`（Kotlin @Serializable data class）手动对齐：

- 字段名和类型一一对应
- CI 脚本比对两边字段，不一致时构建失败
- 不引入 OpenAPI code-gen，保持手写但有自动化守护
- CI 校验方式：对比序列化后的 JSON schema shape（字段名 + 类型），而非源码级字段比对；通过 JSON contract test 覆盖请求/响应的序列化往返一致性

---

## 9. SEO 策略

### 问题

从 Thymeleaf SSR 迁移到 Compose/Wasm 后，搜索引擎爬虫无法执行 Wasm，商品页面和分类页面将不可索引。对电商平台，SEO 流量至关重要。

### 方案：预渲染服务（Prerender）

在 Nginx 层根据 User-Agent 判断是否为爬虫，爬虫请求转发到预渲染服务，普通用户直接访问 Wasm 应用。

```
                        ┌─────────────────┐
              crawler → │ Prerender (Node) │ → 返回静态 HTML
              ↗         └─────────────────┘
Nginx 判断 UA
              ↘         ┌─────────────────┐
              user →    │ Compose Wasm App │
                        └─────────────────┘
```

实现选型：
- **Prerender.io 自托管版**（开源，Node.js，headless Chromium）
- 或 **rendertron**（Google 开源）

缓存策略：
- 预渲染结果缓存到 Redis，TTL 24 小时
- 商品更新事件触发缓存失效

关键页面：商品详情页、分类列表页、首页。卖家端（`seller.shop.com`）不需要 SEO，可跳过预渲染。

### 备选方案

如果 Wasm bundle 过大或 SEO 效果不佳，可为买家端关键公开页面保留一个轻量 SSR 层（Kotlin/JS + Ktor server），仅覆盖商品详情和分类页面，其余页面走 Wasm。此为 fallback，P2 阶段评估是否需要。

---

## 10. 测试策略

### 10.1 KMP 共享模块测试

| 层 | 测试方式 | 运行环境 |
|----|----------|----------|
| `core/models` | `commonTest`：序列化/反序列化测试 | JVM + JS + Native |
| `core/networking` | `commonTest`：Ktor MockEngine 测试 API 调用、错误处理、token 刷新 | JVM + JS + Native |
| `feature-*/data` | `commonTest`：Repository 单元测试（mock network layer） | JVM |
| `feature-*/ui` | Compose UI 测试（`@OptIn(ExperimentalTestApi::class)`） | JVM (Desktop) |

### 10.2 search-service 测试

- **集成测试**：Testcontainers（Kafka + Meilisearch Docker）验证完整链路：发送 Kafka 事件 → 消费 → 索引 → 搜索返回结果
- **Contract test**：验证 search-service 返回的 JSON 与 KMP `core/models` 中 `SearchResult` 的序列化一致

### 10.3 E2E 测试

- **Web**：Playwright 对 Wasm 应用进行关键路径 E2E 测试（搜索 → 商品详情 → 加购 → 下单）
- **Android**：Compose UI Test（`createComposeRule`）
- **iOS**：XCUITest 或 Maestro（跨平台 E2E 框架）

---

## 11. 容错与降级

### 11.1 search-service → Meilisearch

| 场景 | 处理方式 |
|------|----------|
| Meilisearch 不可用 | search-service 返回 503，BFF 降级为调用 marketplace-service 原有 LIKE 查询 |
| 搜索超时（>2s） | 熔断（Resilience4j CircuitBreaker），降级同上 |
| Kafka consumer 消费失败 | 重试 3 次 → 发送到 DLQ（`marketplace.product.events.v1.dlq`）→ 告警 |

### 11.2 BFF → search-service

BFF 对 search-service 调用配置 Resilience4j：
- **CircuitBreaker**：失败率 > 50% 时打开，30s 后半开探测
- **Timeout**：3s
- **Fallback**：降级到 marketplace-service 原有搜索接口

### 11.3 KMP App 网络异常

- Ktor Client 配置重试插件：网络错误重试 2 次，指数退避
- 移动端离线时展示上次缓存的商品列表（本地缓存用 DataStore，非全离线方案）
- 搜索无结果 / 服务异常时统一展示友好的错误 UI 组件

---

## 12. Meilisearch 安全配置

### API Key 分离

| Key 类型 | 用途 | 权限 |
|----------|------|------|
| Master Key | 运维管理（创建其他 key） | 全部（不写入代码） |
| Admin Key | search-service 索引写入 | documents.*, indexes.*, settings.* |
| Search Key | search-service 查询 | search |

### 网络隔离

- **Docker Compose**：Meilisearch 不暴露到宿主机，仅通过 Docker 内部网络供 search-service 访问
- **K8s**：Meilisearch 使用 ClusterIP Service，不配置 Ingress，仅 search-service Pod 可达
- **NetworkPolicy**：仅允许 search-service namespace 访问 Meilisearch 7700 端口

---

## 13. Gradle / Maven 共存策略

### 目录隔离

- Maven：产物输出到 `target/`（各服务子目录）
- Gradle：产物输出到 `build/`（`kmp/` 子目录）
- `.gitignore` 同时忽略 `target/` 和 `build/`
- 两者无任何文件交叉

### CI Pipeline

```
CI Job 矩阵：
├── backend (Maven)
│   └── mvn clean verify -pl !buyer-portal,!seller-portal  ← P5 后移除排除
├── kmp-android (Gradle)
│   └── ./gradlew :kmp:buyer-app:assembleDebug :kmp:seller-app:assembleDebug
├── kmp-ios (Gradle, macOS runner)
│   └── ./gradlew :kmp:buyer-app:linkDebugFrameworkIosArm64 :kmp:seller-app:linkDebugFrameworkIosArm64
├── kmp-web (Gradle)
│   └── ./gradlew :kmp:buyer-app:wasmJsBrowserProductionWebpack :kmp:seller-app:wasmJsBrowserProductionWebpack
└── contract-check
    └── 校验 shop-contracts ↔ kmp/core/models 一致性
```

### IDE 使用

- 后端开发：IntelliJ IDEA 打开项目根目录，自动识别 Maven
- KMP 开发：IntelliJ IDEA / Android Studio 打开项目根目录，自动识别 Gradle
- 两者可共存于同一 IDE 窗口（IntelliJ 2026.x 支持 Maven + Gradle 混合项目）

---

## 14. 卖家端扩展模块

`feature-marketplace` 在买家端和卖家端的职责不同：

| 模块 | 买家端 | 卖家端 |
|------|--------|--------|
| `feature-marketplace` | 商品浏览、搜索、详情 | 商品 CRUD、库存管理、图片上传 |
| `feature-order` | 我的订单、订单详情 | 订单管理、发货操作 |
| `feature-promotion` | 查看促销、获取优惠券码并在结账使用 | 促销创建、编辑、数据统计 |

实现方式：每个 feature 模块内部通过 `expect/actual` 或条件组合来区分买家/卖家 UI，共享 data 层和 domain 层。不新增额外模块，保持模块数量可控。当前买家端促销实现采用“活动/券码发现 + 购物车结账输入 coupon code”的闭环，不单独维护一份“已领取优惠券”状态。

---

## 15. 已知风险与缓解措施

| 风险 | 影响 | 缓解措施 |
|------|------|----------|
| Compose/Wasm bundle 过大（10-30MB） | 首次加载慢，影响转化率 | P0 输出 bundle size 基线；开启 brotli 压缩（通常压缩到 3-5MB）；设置 < 5MB 压缩后的目标 |
| Compose/Wasm 浏览器兼容性 | 部分旧浏览器不支持 Wasm GC | 仅支持现代浏览器（Chrome 119+、Firefox 120+、Safari 18.2+）；P0 阶段验证 |
| CORS 配置 | Wasm 应用跨域调用 API | Nginx 反向代理 `/api/*` 到 gateway，保持同源；无需额外 CORS 配置 |
| 旧 Portal 与新应用并存期间的路由冲突 | 端口已保持一致（8100/8101），gateway 无感知 | gateway 路由不变，仅后端实现从 Thymeleaf 切换到 Nginx 静态站 |

当前本地基线（生产 webpack 产物目录下全部 `.wasm` 资源经 gzip 压缩后求和）：

- `buyer-app`: `4.21MB`
- `seller-app`: `4.04MB`
