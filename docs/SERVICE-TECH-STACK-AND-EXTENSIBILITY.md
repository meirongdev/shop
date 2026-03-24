# Shop Platform — 服务技术栈与扩展能力矩阵

> 版本：1.0 | 更新时间：2026-03-24
> 配套文档：`docs/ARCHITECTURE-DESIGN.md`、`docs/TECH-STACK-BEST-PRACTICES-2026.md`、`docs/OBSERVABILITY-ALERTING-SLO.md`

---

## 一、这份文档回答什么问题

当团队准备做二次开发、补新服务、替换供应商，或者把已有能力扩展到新的业务场景时，最常见的四个问题是：

1. 这个服务到底负责什么，不应该负责什么。
2. 它当前主栈是什么，应该沿着哪条技术路线继续演进。
3. 已经预留了哪些扩展面，不需要从零重构就能接能力。
4. 这些技术选型分别在解决什么架构或设计问题。

本文按运行层次整理现有服务，优先描述**代码里已经存在**的扩展能力，而不是抽象愿景。

---

## 二、入口、身份与聚合层

| 服务 | 主职责 | 当前主栈 | 已有扩展面 | 主要解决的问题 |
|------|--------|----------|------------|----------------|
| `api-gateway` | 统一 northbound 入口、JWT 校验、Trusted Headers 注入、限流、灰度、响应关联头 | Spring Cloud Gateway Server MVC、Spring Security、Redis Lua、Micrometer/OTLP | `application.yml` 路由定义、`filter/` 下自定义过滤器、`GatewayProperties` 限流阈值、响应头暴露策略 | 把安全、流量治理、追踪关联统一收口，避免各服务重复实现 |
| `auth-server` | 登录、JWT 签发、Google/Apple/Otp、guest token、注册后联动 profile | Spring Boot、Spring Security、JPA、Redis、Kafka | `SmsGateway` 接口、`SocialLoginService`、`JwtTokenService`、`AuthProperties` / `AuthOtpProperties` / `AuthSmsProperties` | 把身份、令牌和认证方式集中治理，避免业务服务耦合登录细节 |
| `buyer-bff` | 买家聚合、游客购物流、购物车/结账编排、订单与积分中心入口 | Spring Boot、RestClient、Virtual Threads、Resilience4j、Redis | `BuyerBffConfig` 中新增下游客户端、`BuyerAggregationService` 编排逻辑、`GuestCartStore`、超时与熔断参数 | 在不侵入领域事实的前提下组合多个域服务，控制用户面接口粒度 |
| `seller-bff` | 卖家工作台聚合、商品/订单/促销管理编排 | Spring Boot、RestClient、Virtual Threads | `SellerBffConfig`、`SellerAggregationService`、搜索专用 `RestClient`、超时配置 | 把卖家侧查询与轻量编排集中在一层，降低 Portal 与领域服务耦合 |
| `buyer-portal` | 买家 SSR 门户、表单与模板渲染、会话态 UI 入口 | Kotlin、Spring Boot、Thymeleaf | `BuyerPortalApiClient`、Controller、Thymeleaf 模板、条件渲染与特性开关 | 在不引入前后端分离复杂度的前提下快速交付买家页面 |
| `seller-portal` | 卖家 SSR 门户、商品/订单/促销后台页面 | Kotlin、Spring Boot、Thymeleaf | `SellerPortalApiClient`、Controller、Thymeleaf 模板、表单处理 | 用 SSR 保持管理端页面交付速度，并复用后端统一安全链路 |

### 这一层推荐怎样扩展

- **入口治理能力**优先放在 `api-gateway`：新增公开路由、限流白名单、灰度 header、统一追踪头时，不要散落到下游服务。
- **聚合逻辑**优先放在 BFF：新增“买家首页组合接口”“卖家看板组合接口”时，复用 `RestClient + Virtual Threads + timeout` 模式，而不是把跨域编排塞回领域服务。
- **认证方式**优先放在 `auth-server`：SMS、社交登录、新 claims 方案都应沿着 `SmsGateway`、`SocialLoginService`、`JwtTokenService` 扩展，而不是让 Gateway/BFF 自己生成身份信息。

---

## 三、核心交易与账户域

| 服务 | 主职责 | 当前主栈 | 已有扩展面 | 主要解决的问题 |
|------|--------|----------|------------|----------------|
| `profile-service` | 买家/卖家档案、地址簿、店铺资料 | Spring Boot、JPA、Flyway、MySQL | JPA entity / repository、DTO 与校验规则、按角色扩展资料字段 | 让“用户资料事实”拥有独立 schema 和迁移节奏 |
| `marketplace-service` | 商品目录、SKU、评价、库存事实源 | Spring Boot、JPA、Flyway、MySQL、Redisson、Kafka | 商品/评价数据模型、`MarketplaceOutboxPublisher`、库存锁 key 规则、补偿任务策略 | 用单服务持有商品与库存事实，并通过分布式锁避免超卖与并发补偿冲突 |
| `order-service` | 下单、订单状态机、购物车（登录态）、订单批处理 | Spring Boot、JPA、Flyway、MySQL、Kafka、Redisson | 状态流转规则、`OrderOutboxPublisher`、`OrderScheduler`、幂等键策略 | 把交易状态机和异步后处理收敛到订单域，保持状态一致性 |
| `wallet-service` | 余额账户、充值/提现、支付意图、支付提供方接入 | Spring Boot、JPA、Flyway、MySQL、Kafka、Redisson、Stripe SDK | `StripeGateway` 接口、`MockPaymentGateway` / `StripePaymentGateway`、提供方配置与路由 | 用统一支付网关抽象承接真实支付，避免上层直接依赖第三方 SDK |

### 这一层推荐怎样扩展

- **新增交易事件**：优先沿用 outbox + Kafka，而不是在事务里直接调用远端服务。
- **新增库存或账户一致性规则**：先看 Redisson / scheduler 是否已有协调点，不要额外引入新的分布式协调层。
- **新增支付提供方**：沿着 `StripeGateway` 同级增加实现，再通过配置切换或路由扩展。

---

## 四、增长、激励与互动域

| 服务 | 主职责 | 当前主栈 | 已有扩展面 | 主要解决的问题 |
|------|--------|----------|------------|----------------|
| `promotion-service` | 促销引擎、优惠券、结算折扣、欢迎券/钱包奖励监听 | Spring Boot、JPA、Flyway、MySQL、Kafka、Redisson | `BenefitCalculator`、`ConditionEvaluator`、`PromotionEngine` 自动装配、双轨 coupon 模型 | 用策略模式支持促销规则持续新增，而不让核心结算逻辑变成巨型 if/else |
| `loyalty-service` | 积分账户、签到、兑换、过期处理、成长规则 | Spring Boot、JPA、Flyway、MySQL、Kafka | `LoyaltyProperties`、签到/兑换服务、过期批处理、事件监听器 | 把留存激励从交易主链路拆出，通过事件和配置持续演进 |
| `activity-service` | 互动玩法、反作弊、参与记录、奖励派发编排 | Spring Boot、JPA、Flyway、MySQL、Redis、Kafka | `GamePlugin`、`GamePluginRegistry`、`AntiCheatGuard`、插件自有扩展表前缀、`RewardDispatcher` | 用插件架构支撑多玩法并行演进，避免每新增一种游戏都复制一套服务 |
| `notification-service` | 邮件通知、模板渲染、事件监听、幂等重试 | Spring Boot、Spring Mail、Thymeleaf、JPA、Kafka | `NotificationChannel`、`NotificationRouteConfig`、`templates/email/*`、监听器与重试任务 | 把“通知渠道”和“业务事件”解耦，支持后续接 SMS/IM/WhatsApp |

### 这一层推荐怎样扩展

- **加一种促销类型**：实现新的 `BenefitCalculator` / `ConditionEvaluator`，交给 `PromotionEngine` 自动发现。
- **加一种互动玩法**：实现新的 `GamePlugin`，按插件自己的扩展表前缀和奖励策略落地。
- **加一种通知渠道**：实现 `NotificationChannel`，再扩展 `NotificationRouteConfig` 与模板即可。

---

## 五、平台能力、开放能力与后台任务

| 服务 | 主职责 | 当前主栈 | 已有扩展面 | 主要解决的问题 |
|------|--------|----------|------------|----------------|
| `search-service` | 检索、联想词、热词、重建索引、搜索开关试点 | Spring Boot、Meilisearch、Kafka、OpenFeature | `ProductSearchService` 封装、索引配置、`shop.features.*`、重建任务 | 用搜索投影承接检索需求，避免把全文搜索压回交易库 |
| `webhook-service` | 事件订阅、回调投递、HMAC 签名、失败重试 | Spring Boot、JPA、Flyway、MySQL、Kafka | `WebhookDeliveryService`、`WebhookSigner`、重试/退避配置、payload 转换 | 把对外集成边界从内部事件消费链路中独立出来 |
| `subscription-service` | 订阅计划、生命周期、续费任务、续费幂等协调 | Spring Boot、JPA、Flyway、MySQL、Kafka、Redisson | 计划模板、生命周期状态、`SubscriptionRenewalService`、续费调度参数 | 让订阅与续费节奏独立于一次性订单模型 |

### 这一层推荐怎样扩展

- **换搜索引擎**：优先沿 `ProductSearchService` 封装替换实现，而不是让 Controller 直接耦合 SDK。
- **改 webhook 签名或退避策略**：优先收敛到 `WebhookSigner` 与 `WebhookDeliveryService`，保持外部集成面统一。
- **加新的订阅计划或生命周期节点**：优先修改订阅域模型与续费调度，不要复用订单状态机硬凑订阅流程。

---

## 六、横向复用与二次开发的通用积木

### 6.1 `shop-common`

平台公共能力默认来自 `shop-common`：

- `ApiResponse<T>` 统一 northbound 响应模型
- `BusinessException` / `CommonErrorCode` 统一错误语义
- `InternalAccessFilter` 校验 `X-Internal-Token`
- `TraceIdExtractor`、结构化日志、OTLP logback appender、自举式可观测配置
- `shop.profiling.*` 持续分析开关
- Feature toggle、幂等、防重复这类通用能力

### 6.2 `shop-contracts`

所有服务间 API path 常量、共享 DTO、事件 envelope 都应优先进入 `shop-contracts`。这让 BFF、Portal、Worker、OpenAPI 文档可以围绕同一份契约演进，而不是靠字符串复制。

### 6.3 Outbox + Kafka

涉及跨服务传播的业务事实，默认走：

1. 业务写库
2. 同事务写 outbox
3. 定时 publisher 发 Kafka
4. 消费端按业务幂等落地

这样做的目标不是“最炫的事件架构”，而是让交易事实具备**原子写入、重放恢复、失败补偿**三件事。

### 6.4 配置与模板

- 服务级配置优先用 `@ConfigurationProperties` 收敛。
- Portal 与 Notification 的文案、渲染结构优先放模板文件，不要把大量展示文案硬编码在 Java/Kotlin 中。
- 特性开关优先挂在 `shop.features.*`，用来支撑灰度与实验，而不是替代所有业务配置。

### 6.5 脚手架

`shop-archetypes` 已经沉淀了新的 domain service、event worker 等模板。新增服务时优先从 archetype 出发，而不是复制旧模块后再手工删改。

---

## 七、技术选型与架构问题的对应关系

| 架构/设计问题 | 当前技术选择 | 为什么这样解决 |
|---------------|--------------|----------------|
| 外部流量的统一鉴权、限流、追踪关联放哪里 | `api-gateway` + Spring Security + Redis Lua + Trusted Headers | 把 northbound 规则收敛在一个地方，减少下游服务重复实现 |
| 多下游聚合如何既保持同步代码可读性，又避免线程池爆炸 | BFF 的 RestClient + Virtual Threads + timeout / CircuitBreaker | 读写模型仍是同步代码，但 I/O 并发成本更低，且失败边界清晰 |
| 领域事实如何避免被 BFF 或 Portal 侵蚀 | 每个 domain service 独立 schema + Flyway | 事实归属清晰，迁移与发布节奏可独立演进 |
| 跨服务事务如何避免分布式事务 | Outbox Pattern + Kafka + consumer 幂等 | 保证本地事务原子性，同时保留事件可重放与补偿能力 |
| 高并发库存、续费、批处理协调如何实现 | Redis / Redisson / Lua / RLock | 在共享缓存平面内解决高频原子操作与多实例协调问题 |
| 促销、活动、通知等经常变化的业务规则如何扩展 | Strategy / Plugin / Channel 接口 | 避免核心应用服务堆满条件分支，让新增规则以新实现接入 |
| 搜索、热词、联想词为什么不直接查交易库 | `search-service` + Meilisearch + Kafka 投影 | 读模型独立优化，降低对交易库的检索压力 |
| 如何让新服务一上来就具备可观测性 | Micrometer + OTLP + Collector + Tempo/Loki/Grafana/Pyroscope | 把 traces / logs / metrics / profiles 放进统一观测链路，减少后补成本 |

---

## 八、给二次开发的最小检查清单

新增一个扩展点之前，建议按下面顺序检查：

1. **是不是已有服务边界内的问题**：能扩在原服务，就不要先拆新服务。
2. **是不是已有 SPI / strategy / template 可以复用**：例如 `GamePlugin`、`BenefitCalculator`、`NotificationChannel`、`StripeGateway`。
3. **是不是要补共享契约**：BFF、Portal、Worker 需要共用的 DTO / path / event，请先更新 `shop-contracts`。
4. **是不是要补观测与告警**：新增关键链路时，同步补指标、trace、日志关键字段与文档。
5. **是不是要补运行清单**：如果配置、topic、Kind 脚本或 K8s manifest 发生变化，文档和清单都要一起更新。

做到了这五点，平台就能在不推倒重来的前提下继续长大。
