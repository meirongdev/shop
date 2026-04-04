# Shop Platform — 2026 电商平台 Roadmap

> 版本：5.0 | 日期：2026-03-23
> 参考：Shopify 2025、Amazon 2026、TikTok Shop、Temu、小红书电商、Shein

---

## 一、2026 年电商行业趋势分析

| 趋势 | 描述 | 对我们的影响 |
|------|------|-------------|
| **零摩擦购物** | 游客即买，一键支付成为标配 | ✅ 已列入核心需求 |
| **社交电商** | 内容种草 → 商品 → 购买链路缩短 | Phase 4 规划 |
| **直播带货** | 实时互动购物，LV/品牌直播间 | Phase 4 规划 |
| **AI 推荐** | 个性化首页、搜索、定价 | Phase 4 规划 |
| **先买后付 (BNPL)** | Klarna、Afterpay 渗透率持续提升 | Phase 3 支付扩展 |
| **跨境电商** | 多语言、多货币、本地化物流 | Phase 4 规划 |
| **可持续消费** | 碳足迹标签、二手/循环商品 | Phase 5 规划 |
| **即时配送** | 2 小时达，同城快送 | Phase 4 物流整合 |
| **语音/AR 购物** | AR 试穿、语音搜索 | Phase 5 探索 |
| **订阅制** | 用户订阅商品定期配送 | Phase 5 规划 |

---

## 二、当前状态（v0.5 — 2026-03）

### 已完成 ✅

**核心基础设施：**
- [x] 微服务架构：Gateway + BFF + Domain Services（12 个原始模块）
- [x] 认证中心：JWT 登录（buyer / seller）
- [x] 事件驱动：Outbox Pattern + Kafka（wallet-service、order-service）
- [x] 可观测性：Prometheus + OpenTelemetry Collector（采集层）+ 结构化日志
- [x] Kind/Kubernetes 部署 + mirrord 本地接入 + **本地 Registry & Tilt 内循环**
- [x] API Gateway：YAML 路由 + Virtual Threads + Redis Lua 限流 + Canary 灰度 + Trusted Headers
- [x] **Seller UI 现代化**：移除 Kotlin Thymeleaf SSR，全面采用 **KMP (Compose Multiplatform)** 覆盖 Web/Android/iOS

**购物核心流程：**
- [x] 商品目录：基础 CRUD + 多规格 SKU + 买家评价
- [x] 订单状态机：完整 10 状态 + 超时取消 + 发货 + 退款
- [x] Stripe 支付回调：`/internal/orders/payment-confirm`
- [x] 游客购物流：GUEST 订单 + order_token + 公开追踪 API + 游客页面
- [x] 钱包服务：充值、余额、Stripe 集成
- [x] 游客购物车（Redis）+ 购物车合并 + Gateway CORS

**通知 & 促销：**
- [x] notification-service（:8092）：Channel SPI + EmailChannel + 7 种模板 + 幂等 + 重试
- [x] 促销引擎升级：Strategy 模式 + calculate API
- [x] 促销服务：优惠券、充值奖励（WalletRewardListener）
- [x] promotion-service：`coupon_template` / `coupon_instance` 核心拆分（V4 迁移 + template/instance service 已完成；legacy `coupon` 模型仍在过渡兼容）

**用户增长：**
- [x] loyalty-service（:8088）：积分账户、签到、兑换、新手任务 + 积分到期批量任务（每日 2 AM）
- [x] 新用户注册福利：+100 pts + 7 项引导任务 + 3 张欢迎券 + 欢迎邮件
- [x] buyer-bff 积分抵扣（checkout 路径，含 CircuitBreaker 降级）+ buyer-portal 积分 Hub 页（含积分商城/兑换功能，嵌入同一页面）

**参与 & 变现：**
- [x] activity-service（:8089）：当前 4 种已实现玩法（InstantLottery / RedEnvelope / CollectCard / VirtualFarm）+ AntiCheatGuard
- [x] buyer-portal 活动广场：活动列表页 + 游戏详情页 + 参与接口（`buyer-activities.html` + `buyer-activity-detail.html`）
- [x] search-service（:8091）：Meilisearch 基础搜索 + autocomplete + trending + OpenFeature 特性开关

**平台扩张：**
- [x] 多商家市场化：店铺主页 + 商家评分 + 买家店铺浏览页
- [x] webhook-service（:8093）：事件订阅 + HMAC-SHA256 签名 + 指数退避重试
- [x] subscription-service（:8094）：订阅计划 + 订阅生命周期 + 自动续费

### 待实现 📐

> 以下均为**需要代码开发与验证**的研发任务，不属于文档整理任务。

**基础设施 & 可观测性（新增，与业务并行）：**
> 设计文档：`docs/superpowers/specs/2026-03-23-observability-stack-design.md` | 实施计划：`docs/superpowers/plans/2026-03-23-observability-stack.md`
- [x] Garage S3：Kind 集群内 S3 兼容对象存储（文件存储 / 可观测性后端 / 冷数据归档）
- [x] Loki + Tempo + Grafana：接入 Garage S3 后端，完成可观测性可视化闭环
- [x] OTEL Collector：将 debug exporter 替换为 Tempo（traces）+ Loki（logs）真实导出
- [x] Prometheus alert rules：落地 P1/P2 告警规则（OBSERVABILITY-ALERTING-SLO.md 草案）
- [x] Kafka consumer lag 监控：暴露 `kafka_consumergroup_lag` 指标（kafka-exporter）

**Phase 3 收尾：**
> 设计文档：`docs/superpowers/specs/2026-03-23-phase3-business-features-design.md` | 实施计划：`docs/superpowers/plans/2026-03-23-phase3-business-features.md`
> Apple Sign-In 设计文档：`docs/superpowers/specs/2026-03-23-apple-signin-design.md` | 实施计划：`docs/superpowers/plans/2026-03-23-apple-signin.md`
- [ ] Apple Sign-In —— auth-server Apple ID token 校验 + nonce/state 防重放 + buyer-portal 登录入口
- [ ] SMS OTP 登录 —— auth-server SPI 扩展 + Redis OTP + Twilio adapter + buyer-portal 登录页
- [ ] buyer-portal：注册成功页 `/buyer/welcome` + 邀请好友裂变链接 —— 含 referral_record 建模 + 裂变奖励
- [ ] 支付扩展剩余项：PayPal Order API v2 + Klarna BNPL（Pay Later）via wallet-service PaymentProvider SPI（Apple Pay / Google Pay 已通过 Stripe Web 支持）

**平台工程 P1（可观测性 & 治理，并行）：**
> 弹性治理设计文档：`docs/superpowers/specs/2026-03-23-resilience-strategy-design.md` | 实施计划：`docs/superpowers/plans/2026-03-23-resilience-strategy.md`
> Spring Boot 3.5 升级设计文档：`docs/superpowers/specs/2026-03-23-spring-boot-35-upgrades-design.md` | 实施计划：`docs/superpowers/plans/2026-03-23-spring-boot-35-upgrades.md`
- [x] **Resilience4j 全量标准化**：已在 `shop-common` 新增程序化四层防护 `ResilienceHelper`，`seller-bff` / `buyer-bff` 已统一接入，promotion/search/checkout 旧 annotation 路径已收口，并已通过 focused Maven 验证
- [x] **补偿持久化**：promotion / marketplace / loyalty 三服务 compensation_task Outbox + Scheduled retry 已落地（CompensationTaskEntity + CompensationRetryScheduler + DB migration）
- [x] **Kafka 幂等规范**：loyalty OrderEventListener / BuyerRegisteredListener、promotion WalletRewardListener 已从 `@IdempotencyExempt` 迁移至 `IdempotencyGuard.executeOnce()`，全量 consumer 统一 guard 完成
- [x] **Bloom Filter 幂等加速**：`shop-common` 已落地 `IdempotencyGuard` / Redis BF auto-config，`wallet-service`（HTTP `Idempotency-Key`）与 `promotion-service`（welcome coupon Kafka consumer）已完成首批接入并通过 focused Maven 验证
  > 设计文档：`docs/superpowers/specs/2026-03-23-bloom-filter-idempotency-design.md` | 实施计划：`docs/superpowers/plans/2026-03-23-bloom-filter-idempotency.md`
- [x] **ArchUnit 规则**：新增 `architecture-tests` 模块，落地 5 大类共 19 条规则（编码规范、分层约束、命名规范、Spring 专项、幂等契约），同步修复 activity-service / marketplace-service 分层违规；详见 `docs/ARCHUNIT-RULES.md`
- [x] **Archetype 自动化测试**：新增 `archetype-tests` 模块，为 6 个 archetype 提供完整的生成验证测试（目录结构、编译、测试、依赖验证），集成到 CI 门禁；详见 `docs/ARCHETYPE-TESTING-IMPROVEMENT-PLAN.md`
- [x] **Problem Details（RFC 7807）**：已在 `shop-common` 统一 `GlobalExceptionHandler` 为 RFC 7807 `ProblemDetail`，服务配置启用 Problem Details，并保留 `code` / `message` / `traceId` 兼容字段
- [x] **Testcontainers @ServiceConnection**：`AbstractMySqlIntegrationTest` 基类已在 order / wallet / promotion / marketplace / loyalty / profile / activity / notification 8 个 MySQL 服务落地，@ServiceConnection 推广完成
- [x] **HTTP Interfaces（@HttpExchange）**：buyer-bff 搜索调用 + search-service marketplace internal client 已切换为声明式 HTTP 客户端
- [ ] 业务指标埋点：已在 buyer-bff / search-service / order-service / promotion-service / wallet-service / loyalty-service / activity-service 落地 `shop_*` Counter/Timer，仍待 seller-bff / marketplace / notification / webhook / subscription 补齐
- [x] 关键链路契约测试（BFF → Domain Service）：buyer-bff MarketplaceContractTest 4 个 WireMock 契约测试已落地
- [x] 异常语义收敛（`src/main/java` 中 `catch (Exception)` 已清零，buyer-bff / search-service / api-gateway / webhook-service / promotion-service / notification-service / loyalty-service / activity-service / subscription-service 已按边界收窄异常）

**Phase 4 剩余：**
- [ ] Meilisearch 搜索增强（向量搜索、语义搜索、多语言分词）
- [ ] AI 推荐引擎（依赖搜索增强 + 行为数据沉淀）

**Phase 4 长期（最低优先级）：**
- [ ] activity-service：QuizPlugin（答题）/ SlashPricePlugin（砍价）/ GroupBuyPlugin（拼团）

**Phase 5 剩余：**
- [ ] 商家入驻申请审核流程
- [ ] 直播带货 Beta
- [ ] 内容社区（买家秀 + KOL）
- [ ] 跨境电商（i18n + 多货币 + 物流 API）

**Phase 6 剩余：**
- [ ] AI 购物助手（自然语言搜索 + AI 客服）
- [ ] 开放平台（OAuth 2.0 + 开发者门户）
- [ ] 订阅制深化（order-service 自动下单 + loyalty 权益）

### 当前未落地项的设计入口

| 任务 | 当前状态 | 设计入口 |
|------|----------|----------|
| **可观测性平台**（Garage S3 + Loki + Tempo + Grafana） | 📐 有设计文档 | `specs/2026-03-23-observability-stack-design.md` |
| **弹性治理**（R4j 全量 + 补偿持久化 + Kafka 幂等） | 📐 有设计文档 | `specs/2026-03-23-resilience-strategy-design.md` |
| **Spring Boot 3.5 升级**（Problem Details / CDS / HTTP Interfaces） | 📐 有设计文档 | `specs/2026-03-23-spring-boot-35-upgrades-design.md` |
| **Phase 3/4 业务功能**（注册 / 裂变 / SMS OTP / PayPal / Klarna） | 📐 有设计文档 | `specs/2026-03-23-phase3-business-features-design.md` |
| Apple Sign-In | 📐 有设计文档 | `specs/2026-03-23-apple-signin-design.md` |
| 关键链路契约测试 | 未实现 | 优先为 `buyer-bff → order/promotion/loyalty/marketplace` 建 consumer/provider contract |
| 搜索增强 | 未实现 | `search-service + Meilisearch` 语义 / 向量 / 多语言，依赖行为数据沉淀 |
| Phase 5 / 6 能力 | 未实现 | 保持为 roadmap / design backlog |

---

## 三、Roadmap 全景（v5.0 依赖驱动）

```
Phase 1          Phase 2          Phase 3          Phase 4          Phase 5          Phase 6
核心购物闭环  →  通知服务      →  用户增长      →  参与 & 变现  →  平台扩张      →  AI & 生态
(P0) ✅          (P0) ✅          (P1) 部分✅      (P2) 部分✅      (P3) 部分✅      (P3) 部分✅
```

### v5.0 变更说明（2026-03-23）

| 变化 | 说明 |
|------|------|
| 新增 5 个设计文档 + 实施计划 | 弹性治理策略、可观测性平台、Spring Boot 3.5 升级、Phase 3/4 业务功能、Apple Sign-In 均已有设计入口 |
| Apple Sign-In 设计入口补齐 | 单独补充 Apple Sign-In spec + plan，避免 Phase 3 收尾只剩短信 OTP/注册裂变的描述缺口 |
| 弹性治理细化 | R4j 全量标准化（TimeLimiter + Bulkhead + Retry）+ 补偿持久化 + Kafka 幂等 ArchUnit 规则拆分为独立任务 |
| Spring Boot 3.5 升级独立为 track | Problem Details / @ServiceConnection / HTTP Interfaces / CDS 独立拆出，不再混入可观测性 track |
| Phase 3 支付扩展状态修正 | Apple Pay / Google Pay 已通过 Stripe Web 支持；Phase 3 收尾仅剩 PayPal + Klarna BNPL |
| 设计入口表简化 | 有 spec 的任务直接指向文档路径，无 spec 的保留原有描述 |

### v4.0 变更说明（2026-03-23）

| 变化 | 说明 |
|------|------|
| 新增基础设施 & 可观测性 track | Garage S3 + Loki/Tempo/Grafana 独立为并行交付项；当前 OTEL 只有 debug exporter，Grafana/Tempo/Loki 未部署 |
| activity-service 前端状态修正 | 活动广场/详情页前端已实现，恢复到已完成项；未完成的是 Quiz / SlashPrice / GroupBuy 新玩法 |
| 新增补偿持久化任务 | 优惠券核销、库存回补、积分退还当前 best-effort，需升级为 outbox + 定时重试 |
| 游戏新类型降为最低优先级 | Quiz/SlashPrice/GroupBuy 后端插件降至 Phase 4 长期项，先做活动广场前端 |
| 已完成项补充 | 游客购物车、loyalty 积分抵扣/商城、activity 前端、Gateway 增强、promotion coupon 核心拆分已标记完成 |

### v3.0 变更说明

| 变化 | 说明 |
|------|------|
| notification-service 独立为 Phase 2 | 从 Phase 1 子任务升级为独立 Phase，有完整设计文档 |
| Kind + mirrord 开发环境 | 新增 Phase 1 前置项，所有后续开发受益 |
| 去除具体日期 | 按依赖关系和优先级排列，不绑定时间 |
| 端口调整 | notification-service 使用 8092，search-service 移至 8091，activity-service 使用 8089 |
| 邮件方案调整 | Spring Mail + SMTP + Mailpit（替代 SendGrid/SES） |

---

## 四、Phase 1 — 核心购物闭环（P0）

**目标**：打通完整购物链路，游客无需注册即可购买；完善订单生命周期

**交付标准**：游客可完成 浏览→加购→结账→支付→追踪订单

### Epic 1.0：Kind + mirrord 开发环境 🔑 ✅ DONE

> **已完成实现**：
> - `kind/setup.sh` — 一键创建集群 + 构建镜像 + 部署全部服务
> - `kind/teardown.sh` — 一键清理集群
> - `.mirrord/` — mirrord 示例目标配置
> - `scripts/mirrord-debug.sh` — 本地运行服务接入集群
> - `kind/mirrord-run.sh` — 兼容包装入口
> - `kind/cluster-config.yaml` — 增强端口映射（8080/8025/9090）
> - 部署所有 15 个服务（含 activity-service、loyalty-service、notification-service）
> - 更新 local-deployment.md 文档

| 任务 | 状态 |
|------|------|
| Kind 集群部署全部基础设施 | ✅ |
| 部署已有 15 个服务到 Kind | ✅ |
| 配置 mirrord profile | ✅ |
| 更新开发文档 | ✅ |

### Epic 1.1：订单状态机完善（P0）✅ DONE

> 无外部依赖，优先实现

| 任务 | 说明 |
|------|------|
| ✅ 完整状态机实现（PENDING→PAID→SHIPPED→COMPLETED→CANCELLED） | 10 状态 + 验证转换 |
| ✅ 订单超时自动取消（30 分钟，定时任务） | OrderScheduler, 60s 间隔 |
| ✅ 卖家发货接口（录入运单号） | ShipmentEntity + InternalOrderController |
| ✅ 确认收货（手动 + 7 天自动） | auto-complete 1hr 间隔 |
| ✅ 退款流程（REFUND_REQUESTED → REFUNDED） | RefundEntity + 状态机 |

### Epic 1.2：Stripe 支付回调（P0）✅ DONE

> 依赖：Epic 1.1（订单状态机）

| 任务 | 说明 |
|------|------|
| ✅ Stripe Webhook handler | /internal/orders/payment-confirm |
| ✅ 支付成功 → 订单状态推进 PENDING→PAID | InternalOrderController |

### Epic 1.3：游客购物流 🔑（P0）✅ DONE

> 依赖：Epic 1.1（订单状态机）
> 
> **已完成实现**：
> - Gateway `/public/**` 公开路由白名单
> - 订单 GUEST 类型 + `order_token` 生成（V3 迁移）
> - 游客结账 `POST /buyer/v1/guest/checkout`（buyer-bff → order-service）
> - 公开订单追踪 `GET /buyer/v1/guest/order/track?token=`
> - 游客结账页 + 下单成功页 + 订单追踪页（buyer-portal Thymeleaf）
> 
> **当前实现说明**：游客可在 buyer-portal 以 guest session 浏览与加购；购物车由 buyer-bff 持久化到 Redis，登录成功后自动 merge 到已登录买家在 order-service 中的正式购物车；Gateway 已补齐 `/api/**` 与 `/public/**` 的 CORS 基线。

| 任务 | 服务 | 状态 |
|------|------|------|
| Gateway 公开路由白名单 `/public/**` | api-gateway | ✅ |
| 订单 GUEST 类型 + `order_token` 生成 | order-service | ✅ |
| 游客结账接口 `POST /buyer/v1/guest/checkout` | buyer-bff | ✅ |
| 公开订单追踪 `GET /buyer/v1/guest/order/track?token=` | buyer-bff | ✅ |
| 游客结账页 + 下单成功页（含注册引导） | buyer-portal | ✅ |
| 订单追踪页（无需登录） | buyer-portal | ✅ |
| 游客购物车（Redis）| buyer-bff | ✅ |
| 购物车合并 `POST /buyer/v1/cart/merge`（登录后） | buyer-bff | ✅ |
| CORS 策略配置 | api-gateway | ✅ |

### Epic 1.4：事件发布补充（P0）✅ DONE

> 依赖：Epic 1.1（订单状态机）；为 Phase 2 notification-service 提供数据源

| 任务 | 服务 |
|------|------|
| ✅ Outbox 模式 + 发布 `order.events.v1` | order-service |
| ✅ 注册时发布 `buyer.registered.v1`（含 email） | profile-service |
| ✅ WalletTransactionEventData 追加 email + balance 字段 | wallet-service |
| ✅ 新增 `BuyerRegisteredEventData`、`OrderEventData` DTO | shop-contracts |

### Epic 1.5：商品基础搜索（P1）✅

> search-service 已完整实现，Meilisearch 驱动

| 任务 | 说明 |
|------|------|
| ✅ 关键词搜索（Meilisearch，已有 search-service） | `GET /search?q=` |
| ✅ 分类筛选、价格排序、分页 | categoryId filter + sort + page/hitsPerPage |
| ✅ "仅剩 N 件"实时库存提示 | inventory 字段已包含在搜索结果中 |

---

## 五、Phase 2 — 通知服务（P0）

**目标**：关键业务动作有邮件触达

**依赖**：Phase 1 Epic 1.4（事件发布）

> 设计文档：`docs/superpowers/specs/2026-03-21-notification-service-design.md`

### Epic 2.1：notification-service 核心（P0）✅ DONE

| 任务 | 说明 |
|------|------|
| ✅ 新建 `notification-service`（:8092）+ `shop_notification` 数据库 | Spring Boot + Kafka Consumer |
| ✅ `notification_log` 表（ULID 主键） | Flyway 迁移 |
| ✅ Channel SPI 接口 + `EmailChannel`（Spring Mail + Thymeleaf） | |
| ✅ `NotificationRouter` 路由规则（7 种事件 → 模板 + 渠道） | |
| ✅ Kafka Listeners：`BuyerRegisteredListener`、`OrderEventListener`、`WalletTransactionListener` | |
| ✅ 幂等发送（event_id + channel 唯一约束） | |
| ✅ 失败重试（`@Scheduled`，最多 3 次） | |

### Epic 2.2：邮件模板（P0）✅ DONE

| 模板 | 变量 |
|------|------|
| ✅ `welcome-email` | username |
| ✅ `order-confirmed` | username, orderId, totalAmount, items |
| ✅ `order-shipped` | username, orderId, trackingNumber |
| ✅ `order-completed` | username, orderId |
| ✅ `order-cancelled` | username, orderId, reason |
| ✅ `wallet-deposit` | username, amount, balance |
| ✅ `wallet-withdrawal` | username, amount, balance |

### Epic 2.3：开发环境集成（P0）✅ DONE

| 任务 | 说明 |
|------|------|
| ✅ Kind / K8s 添加 Mailpit | Web UI :8025 + SMTP :1025 |
| ✅ notification-service 部署到 Kind 集群 | K8s manifest |
| ✅ api-gateway 路由（如需健康检查端点） | |

---

## 六、Phase 3 — 用户增长（P1）

**目标**：注册用户留存率 > 40%；注册 7 天首单转化率 > 30%

**依赖**：Phase 1 Epic 1.4（事件发布）+ Phase 2（通知服务）

### Epic 3.0：API Gateway 现代化 — Virtual Threads 迁移（P0）✅ DONE

> 设计文档：`docs/superpowers/specs/2026-03-22-api-gateway-virtual-threads-migration-design.md`
> 依赖：无（独立基础设施任务，建议优先完成）
>
> **目标**：用 `spring-cloud-starter-gateway-server-webmvc` + JDK 25 Virtual Threads 替换 WebFlux，降低响应式维护成本，统一编程模型，并为后续 Phase 3 任务（CORS、游客购物车 Redis）打好基础。

> **已完成实现**：
> - `api-gateway` 迁移至 `spring-cloud-starter-gateway-server-webmvc`
> - 开启 `spring.threads.virtual.enabled=true`，边缘层统一采用 Servlet + Virtual Threads
> - 路由迁移至 `application.yml`，新增 buyer/seller canary 路由
> - `GatewaySecurityConfig` 切换到 `HttpSecurity` + `NimbusJwtDecoder`
> - `TrustedHeadersFilter` 改为 `OncePerRequestFilter`，并通过 `TrustedHeadersRequestWrapper` 剥离不可信 header
> - `RateLimitingFilter` 实现 Redis Lua 原子限流（Player-Id / IP，fail-open）
> - `CanaryRequestPredicates` 实现 Redis Set 白名单灰度路由
> - 补齐 `GatewayContextTest`、`TrustedHeadersFilterTest`、`RateLimitingFilterTest`、`CanaryRequestPredicatesTest`、`GatewayRoutingIntegrationTest`
>
> **验证**：
> - `./mvnw -pl api-gateway -am clean test`

| 任务 | 说明 | 状态 |
|------|------|------|
| 替换 `gateway-server-webflux` → `gateway-server-webmvc` | pom.xml 依赖切换，边缘层改为 Servlet 栈 | ✅ |
| 开启 Virtual Threads | `spring.threads.virtual.enabled=true` | ✅ |
| 路由迁移至 YAML | 删除 `RouteConfig.java`，路由写入 `application.yml` | ✅ |
| `GatewaySecurityConfig` 改为 servlet 栈 | `NimbusJwtDecoder` + `HttpSecurity` | ✅ |
| `TrustedHeadersFilter` 改为 `OncePerRequestFilter` | `TrustedHeadersRequestWrapper` 剥离不可信 header | ✅ |
| 新增 `RateLimitingFilter` | Redis Lua 原子脚本限流，100 req/min per Player-Id/IP，fail-open | ✅ |
| 新增 `CanaryRequestPredicates` | SCG MVC `PredicateSupplier` + Redis Set 白名单灰度路由 | ✅ |
| 更新 `GatewayProperties` | 新增嵌套 `RateLimit` record | ✅ |
| 补充测试 | `GatewayContextTest` / `TrustedHeadersFilterTest` / `RateLimitingFilterTest` / `CanaryRequestPredicatesTest` / `GatewayRoutingIntegrationTest`（Testcontainers Redis + 内嵌 HTTP upstream） | ✅ |

**技术收益：**
- 编程模型与其他 15 个 Spring MVC 服务统一，无需 Reactor 知识维护 Gateway
- Virtual Threads 下阻塞 I/O 吞吐量与 Netty event loop 接近
- 新增路由只需追加 YAML 条目，无需重新编译
- Canary 谓词支持接口迁移灰度验证（内部账号先行 → 全量切流），Redis 热更新无需重启

**外部实践参考（同类方案）：**
- Netflix Zuul 2 canary filter（本方案原型）
- Apache APISIX `traffic-split` 插件（etcd 热更新）
- Nginx Ingress `canary-by-header` 注解
- Spring Cloud Alibaba SCG + Nacos 灰度（白名单存 Nacos，本方案改用已有 Redis）

### Epic 3.1：loyalty-service ✅（P0）

> 设计文档：`docs/services/loyalty-service.md`
> 依赖：Epic 1.4（order.events.v1、buyer.registered.v1）
> **状态：核心已实现** — 积分账户、签到、兑换、新手任务、Kafka 消费、api-gateway 路由

| 任务 | 说明 |
|------|------|
| ✅ 新建 `loyalty-service`（:8088）+ `shop_loyalty` 数据库 | |
| ✅ 8 张核心表 | Flyway 迁移（含 onboarding 表） |
| ✅ 购物积分：消费 `order.completed.v1`，按规则发积分 | |
| ✅ 签到系统：连签 7 天奖励 + 补签 | |
| ✅ 积分兑换：COUPON / PHYSICAL / VIRTUAL | |
| ✅ 积分到期年度批量任务 | `PointsExpiryScheduler` 已落地 |
| ✅ api-gateway 新增路由 `/loyalty/**` | |
| ✅ buyer-bff Loyalty Hub 并发聚合积分账户 / 任务 / 奖励 / 流水 / 兑换 | Virtual Threads |
| ✅ 结账流程积分抵扣（最多 20% 订单金额） | buyer-portal 购物车页可输入 `pointsToUse` |

**Kafka 新增 Topics：**
```
loyalty.points.earned.v1    loyalty.points.expiring.v1
loyalty.tier.upgraded.v1    loyalty.checkin.v1
loyalty.redemption.v1
```

### Epic 3.2：新用户注册福利 ✅（P0）

> 设计文档：`docs/services/new-user-onboarding.md`
> 依赖：Epic 3.1（loyalty-service）+ Phase 2（notification-service）
> **状态：核心已实现** — 积分奖励、新手任务、欢迎优惠券、欢迎邮件

| 任务 | 说明 |
|------|------|
| ✅ `LoyaltyOnboardingListener`：+100 pts + 初始化 7 项任务 | loyalty-service |
| ✅ 2 张任务表：`onboarding_task_template` / `onboarding_task_progress` | |
| ✅ `OnboardingTaskService.completeTask()`（幂等） | |
| ✅ `WelcomeCouponListener`：发放 3 张新人礼包券 | promotion-service |
| ⬚ 注册成功页 `/buyer/welcome`（礼包展示 + 任务清单） | buyer-portal；当前仍无注册 northbound 入口 |
| ✅ 欢迎邮件（含礼包清单 + 任务引导） | notification-service |
| ⬚ 邀请好友裂变链接（含 referrer_id） | buyer-portal |

### Epic 3.3：社交登录（P0）✅ 核心已实现

> 当前剩余项有外部依赖（Apple Developer / SMS Provider）
> Apple Sign-In 设计文档：`docs/superpowers/specs/2026-03-23-apple-signin-design.md`

| 任务 | 说明 |
|------|------|
| ✅ Google OAuth2 登录 | Google ID Token 验证 + 自动注册/登录 |
| ✅ 社交账号与已有账号绑定 | `/auth/v1/social/bind` + `/auth/v1/social/list` |
| ✅ user_account + social_account 持久化 | auth-server 新增 JPA + Flyway + shop_auth DB |
| ⬜ Apple 登录 | Apple ID JWT 验证 + client secret / nonce（需 Apple Developer 账号） |
| ⬜ 手机号 + SMS OTP 登录 | 短信 provider 适配 + OTP challenge store + 频控（需外部 SMS 服务） |

### Epic 3.4：促销引擎升级 ✅（P1）

> 设计文档：`docs/services/promotion-service.md`
> 依赖：Epic 3.1（loyalty-service，积分加倍需要积分系统）
> **状态：核心已实现** — Strategy 模式引擎、条件评估器、折扣计算器、calculate API、coupon template/instance 核心拆分

| 任务 | 说明 |
|------|------|
| ✅ `promotion_offer` 表新增 `conditions` / `benefits` JSON 列 | Flyway V3 ALTER |
| ✅ 新建 `coupon_template` + `coupon_instance` 表 | V4 已落地；欢迎券已使用新模型，legacy `coupon` 仍在兼容现有验券/核销链路 |
| ✅ 实现 `PromotionEngine`（Strategy 模式） | |
| ✅ 核心 Evaluator + Calculator 实现 | MinAmount / TimeWindow / Discount |
| ✅ `POST /promotion/v1/calculate`（结账时计算折扣） | |

### Epic 3.5：商品能力增强（P1）✅ 核心已实现

> **状态：核心已实现** — 多规格 SKU、买家评价（星级+文字+图片）、商品评分汇总

| 任务 | 说明 |
|------|------|
| ✅ 多规格 SKU（颜色 + 尺寸矩阵） | `product_variant` 表 + API |
| ⬚ 商品图片/视频上传（S3/OSS） | 对象存储集成 |
| ⬚ 到货提醒 | marketplace-service + notification-service |
| ✅ 买家评价（图文 + 星级） | `product_review` 表 + 评分汇总 |
| ✅ 积分商城页面 `/buyer/loyalty` | buyer-portal |
| ✅ 积分流水、签到、兑换记录页面 | buyer-portal |

---

## 七、Phase 4 — 参与 & 变现（P2）

**目标**：游戏参与率 > 15% DAU；AI 推荐点击 CTR > 8%

**依赖**：Phase 3（loyalty-service，奖励派发积分）

### Epic 4.1：activity-service 互动小游戏 🔑（P0）✅ 核心已实现

> 设计文档：`docs/services/activity-service.md`
> 依赖：Epic 3.1（loyalty-service）
> **状态：核心框架 + `RedEnvelopePlugin` + `CollectCardPlugin` + `VirtualFarmPlugin` 已实现** — GamePlugin SPI、GameEngine、InstantLotteryPlugin、RedEnvelopePlugin、CollectCardPlugin、VirtualFarmPlugin、Admin/Buyer API、RewardDispatcher、GameScheduler、AntiCheatGuard

| 任务 | 说明 |
|------|------|
| ✅ 新建 `activity-service`（:8089）+ `shop_activity` 数据库 | |
| ✅ 4 张核心表 + 扩展表机制 | Flyway V1 |
| ✅ GamePlugin SPI 接口 + GamePluginRegistry | Spring 自动发现 |
| ✅ `InstantLotteryPlugin`（砸金蛋 / 大转盘 / 刮刮乐） | 加权随机算法 |
| ✅ `RedEnvelopePlugin`（抢红包） | Redis Lua 原子脚本 + Redis Hash 防重复领取 + Kind 冒烟已验证 |
| ✅ `CollectCardPlugin`（集卡 / 集碎片） | `activity_collect_card_def` + `activity_player_card`，加权抽卡、重复抽卡检测、集齐判定，Kind 冒烟已验证 |
| ✅ `VirtualFarmPlugin`（虚拟农场） | `activity_virtual_farm` 状态表，通用 `participate` 默认浇水、`payload.action=HARVEST` 收获奖励，Kind 冒烟已验证 |
| ✅ RewardDispatcher（基础版） | 补偿任务 + 派发桩 |
| ✅ 防作弊：IP 限速 + 设备指纹 + 每日上限 | `AntiCheatGuard` + Redis 限速 + `GameEngine` 每日次数校验 + Kind 冒烟已验证 |
| ✅ api-gateway 新增路由 `/api/activity/**` | |
| ✅ 活动广场页 + 游戏详情页 | buyer-portal |

> **验证**：
> - `./mvnw -pl activity-service -am test`
> - Kind 冒烟：`ROLE_ADMIN` 创建并激活红包活动、seller 参与返回 `403`、buyer 首次领取成功、重复领取返回已领取、红包抢空后返回已抢完、`my-history` 可见中奖快照、同设备跨账号返回 `403`、第 6 次突发请求返回 `429`、`COLLECT_CARD` 单卡活动首次参与返回 `fullSet=true`、重复参与返回 duplicate、`my-history.rewardStatus=SKIPPED`、`VIRTUAL_FARM` 两次浇水后成熟、`payload.action=HARVEST` 成功返回 `POINTS`、历史中 2 条 `PROGRESS/SKIPPED` + 1 条 `POINTS/PENDING`

**Kafka 新增 Topics：**
```
activity.prize.won.v1    activity.game.ended.v1
activity.participated.v1  activity.groupbuy.formed.v1
```

### Epic 4.2：Meilisearch 搜索能力增强（P1）

> search-service 已实现基础搜索，此 Epic 为能力升级

| 任务 | 说明 |
|------|------|
| ⬚ 向量搜索（语义相似商品） | Meilisearch vector search |
| 🟡 多语言分词优化（中/英/日） | localized attributes + `locales` 查询参数已落地，词典/语义层优化待继续 |
| ✅ 搜索联想词 + 热门搜索榜 | prefix suggestions + query analytics |
| ✅ 搜索结果个性化排序 | `inventory:desc` custom ranking baseline，显式 `sort` 仍可覆盖默认排序 |

### Epic 4.3：AI 推荐引擎（P1）

> 依赖：Epic 4.2（Meilisearch 向量搜索）

| 功能 | 技术 |
|------|------|
| 猜你喜欢 | 协同过滤 |
| 相关推荐 | Item-CF |
| 搜索个性化 | Learning to Rank |
| 推荐服务 | Python FastAPI |

### Epic 4.4：支付扩展（P1）

> Apple Pay / Google Pay 已通过 Stripe Web + `wallet-service` PaymentIntent 支持；剩余 PayPal / Klarna 依赖外部 provider 集成。

| 支付方式 | 当前状态 | 说明 |
|---------|----------|------|
| Apple Pay / Google Pay（Stripe Web） | ✅ 已支持 | `wallet-service` 已暴露 `APPLE_PAY` / `GOOGLE_PAY` PaymentMethod，并复用 Stripe PaymentIntent |
| PayPal | ⬜ 待实现 | Provider SPI 已预留，当前仅 mock/占位逻辑 |
| 先买后付 Klarna | ⬜ 待实现 | 规划中，建议复用 PayPal provider 扩展框架 |

### Epic 4.5：移动端优化（P1）

| 功能 | 说明 |
|------|------|
| PWA 支持 | Service Worker |
| 极速购买（一键购买） | |
| 前端迁移 Next.js 15（buyer-portal） | ISR + CSR 混合 |

---

## 八、Phase 5 — 平台扩张（P3）

**目标**：多商家市场化；直播带货 Beta；跨境电商上线

### Epic 5.1：多商家市场化（P0）✅

| 功能 | 说明 |
|------|------|
| ✅ 店铺主页（自定义 Logo / Banner / 介绍） | seller-profile 增加 shop 字段，V3 migration |
| ✅ 商家评分（服务分、质量分、物流分） | avg_rating + total_sales 聚合字段 |
| ✅ 商家店铺管理 | KMP seller-app Shop Settings 页面 |
| ✅ 买家浏览商家店铺 | buyer-portal 店铺展示页面 |
| ⬜ 商家入驻在线申请 → 审核 → 开店 | 需额外审核工作流 |

### Epic 5.2：直播带货 Beta（P1）

> 依赖：Epic 5.1

| 功能 | 说明 |
|------|------|
| WebRTC / RTMP 推流 → CDN 分发 | |
| 实时互动：弹幕、点赞、商品上架 | |
| 直播购物车 + 直播间专属折扣码 | |

### Epic 5.3：内容社区（P1）

> 依赖：Epic 5.1

| 功能 | 说明 |
|------|------|
| 买家秀（图文 + 挂商品） | |
| KOL 入驻 + 分销佣金 | |

### Epic 5.4：跨境电商（P1）

| 功能 | 说明 |
|------|------|
| 多语言（i18n：中/英/日/韩/西） | |
| 多货币（实时汇率 + 结算） | |
| 物流 API 对接（UPS / FedEx / 顺丰） | |

---

## 九、Phase 6 — AI & 生态（P3）

**目标**：AI 原生购物体验；开放平台生态

### Epic 6.1：AI 购物助手

| 功能 | 说明 |
|------|------|
| 自然语言搜索 | LLM + 向量检索 |
| AI 客服（处理 80% 常见问题） | RAG + 知识库 |
| 尺码推荐 + 穿搭建议 | 基于历史购买行为 |

### Epic 6.2：开放平台 ✅ 核心已实现

| 功能 | 说明 |
|------|------|
| ✅ Webhook 服务（webhook-service :8093） | 事件订阅 + HMAC-SHA256 签名推送 |
| ✅ Webhook CRUD API（创建/列表/更新/删除端点） | 卖家管理 webhook 订阅 |
| ✅ 事件投递 + 指数退避重试（最多 5 次） | 消费 Kafka 事件并推送 |
| ✅ 投递日志查询 | 查看每次推送的状态和响应 |
| ⬜ 开发者门户（API 文档 + SDK + 沙箱） | 需前端页面 |
| ⬜ OAuth 2.0（第三方应用接入） | 需 auth-server 扩展 |

### Epic 6.3：订阅制 ✅ 核心已实现

| 功能 | 说明 |
|------|------|
| ✅ subscription-service（:8094） | 订阅计划管理 + 订阅生命周期（创建/暂停/恢复/取消） |
| ✅ 订阅计划 CRUD API | 管理端创建/更新/删除订阅计划 |
| ✅ 用户订阅管理 | 订阅/暂停/恢复/取消 + 状态机校验 |
| ✅ 自动续费处理 | 定时任务检查到期订阅并触发续费 |
| ✅ 续费记录日志 | 每次续费产生 subscription_order_log |
| ⬜ 商品定期配送订阅 | 需对接 order-service 自动下单 |
| ⬜ 会员订阅专属权益（月费/年费） | 需对接 loyalty-service 权益体系 |

---

## 十、依赖关系总览

```
Phase 1: 核心购物闭环 (P0)
  1.0 Kind + mirrord 开发环境 ──────────────────────────────── 所有后续开发受益
  1.1 订单状态机 ─────┬──→ 1.2 Stripe 支付回调
                      ├──→ 1.3 游客购物流
                      └──→ 1.4 事件发布补充 ─────┬──→ Phase 2: 通知服务
  1.5 商品基础搜索（独立）                       │
                                                  │
Phase 2: 通知服务 (P0)                            │
  2.1 notification-service ◄─────────────────────┘
  2.2 邮件模板
  2.3 开发环境集成
         │
         ├──→ Phase 3: 用户增长 (P1)
         │      3.1 loyalty-service ◄── 1.4 事件发布
         │      3.2 新用户注册福利 ◄── 3.1 + 2.1
         │      3.3 社交登录（独立）
         │      3.4 促销引擎升级 ◄── 3.1
         │      3.5 商品能力增强
         │
         └──→ Phase 4: 参与 & 变现 (P2)
                4.1 activity-service ◄── 3.1
                4.2 Meilisearch 增强（独立）
                4.3 AI 推荐 ◄── 4.2
                4.4 支付扩展（独立）
                4.5 移动端优化

Phase 5: 平台扩张 (P3)  ──→  Phase 6: AI & 生态 (P3)
```

---

## 十一、端口分配

| 服务 | 外部端口 | 说明 |
|------|----------|------|
| api-gateway | 8080 | 唯一对外入口 |
| buyer-bff | — | 仅通过 gateway 访问 |
| seller-bff | — | 仅通过 gateway 访问 |
| profile-service | — | 仅通过 gateway 访问 |
| marketplace-service | — | 仅通过 gateway 访问 |
| order-service | — | 仅通过 gateway 访问 |
| wallet-service | — | 仅通过 gateway 访问 |
| promotion-service | — | 仅通过 gateway 访问 |
| loyalty-service | 8088 | dev 直连 |
| activity-service | 8089 | dev 直连 |
| auth-server | 8090 | dev 直连 |
| search-service | 8091 | dev 直连 |
| **notification-service** | **8092** | dev 直连 |
| webhook-service | 8093 | dev 直连 |
| subscription-service | 8094 | dev 直连 |
| buyer-portal | — | 仅通过 gateway 访问（SSR，SEO 友好） |
| KMP seller-app | — | Web WASM / Android / iOS（经 gateway `/api/seller/**`） |

> 所有服务容器内均监听 8080（应用）+ 8081（management/actuator）。外部端口仅供本地开发直接访问，生产环境通过 api-gateway 统一入口。

---

## 十二、技术演进规划

### 12.1 前端现代化

```
Phase 1 (当前)：
  - buyer-portal：保留 Kotlin + Thymeleaf SSR，SEO 友好，支持游客模式
  - seller-app：KMP (Compose Multiplatform) 覆盖 Web WASM / Android / iOS，卖家无 SEO 需求

Phase 3 (Q3)：buyer-portal 可选 Next.js 15 ISR + CSR 混合（进一步增强 SEO）
Phase 4 (Q4)：buyer KMP app 完善 iOS 和 Android 的原生体验
```

### 12.2 搜索演进

```
当前：Meilisearch（search-service 已实现：全文检索、分类筛选、价格排序、分页）
Phase 4：Meilisearch 增强（向量搜索、多语言分词、个性化排序、联想词）
Phase 6：AI 语义搜索（LLM + 向量检索，Meilisearch vector search）
```

### 12.3 存储扩展

```
当前：MySQL（关系型）+ Redis + Kafka
Phase 2：S3/OSS（图片/视频）
Phase 4：ClickHouse（数据分析）
Phase 4：多云（AWS + 阿里云）
Phase 5：向量数据库 Milvus（AI 推荐）
```

### 12.4 基础设施成熟化

```
Phase 1：Kind + mirrord（本地开发）
Phase 2：Kubernetes + Helm Charts（测试/预发）
Phase 3：ArgoCD GitOps 持续部署
Phase 4：多云部署（AWS EKS + 阿里云 ACK）
Phase 5：Service Mesh（Istio）
```

---

## 十三、竞品对标

| 功能 | Shopify | Amazon | TikTok Shop | Shop Platform |
|------|---------|--------|-------------|---------------|
| 游客下单 | ✅ | ✅ | ✅ | ✅ |
| 通知服务 | ✅ | ✅ | ✅ | ✅ |
| 积分忠诚度 | 部分 | ✅ | 部分 | ✅ |
| 新用户礼包 | 部分 | ✅ | ✅ | ✅ |
| 互动小游戏 | ❌ | ❌ | ✅ | 🔄 Phase 4 |
| 多商家市场 | ✅ | ✅ | ✅ | ✅ |
| 订阅制 | ✅ | ✅ | ❌ | ✅ |
| 开放 API (Webhook) | ✅ | ✅ | ✅ | ✅ |
| AI 推荐 | ✅ | ✅ | ✅ | 🔄 Phase 4 |
| 社交登录 | ✅ | ✅ | ✅ | 🔄 Phase 3 |
| 直播带货 | 部分 | 部分 | ✅ | 🔄 Phase 5 |
| 先买后付 | ✅ | ✅ | 部分 | 🔄 Phase 4 |
| 多语言 | ✅ | ✅ | ✅ | 🔄 Phase 5 |

✅ 已实现 | 🔄 规划中 | ❌ 无

---

## 十四、平台工程与标准化改进任务池

> 目标：统一技术栈、统一 scaffold、统一质量门禁，降低新增服务与持续演进成本。  
> 标准文档：`docs/ENGINEERING-STANDARDS-2026.md`

### 14.1 P0（立即推进）

- [x] 建立 `shop-archetypes`（Maven Archetype）并提供 6 类模板：
  - [x] gateway-service-archetype
  - [x] auth-service-archetype
  - [x] bff-service-archetype
  - [x] domain-service-archetype
  - [x] event-worker-archetype
  - [x] portal-service-archetype
  - [x] 已通过样板工程生成与 `mvn test` 验证模板可用
- [x] 引入 Maven Wrapper（`mvnw`/`mvnw.cmd`）保证构建一致性
- [x] 父 POM 引入 Maven Enforcer（JDK/Maven 版本、依赖收敛、禁止快照越界）
- [x] 建立 `.github/workflows/ci.yml`（编译 + 单测 + 基础检查 + docs-site build）
- [x] 引入 OpenAPI 基线（springdoc），为 BFF/auth-server 引入 springdoc-openapi
- [x] `buyer-bff` checkout 完成 CircuitBreaker 解耦：`promotion-service` / `loyalty-service` 不可用时不阻断下单，`marketplace-service` 库存扣减改为 fail-fast
- [x] `buyer-bff` / `seller-bff` 补齐 RestClient `connectTimeout` / `readTimeout` 基线

### 14.2 P1（近期）

- [x] BFF 下游调用统一接入 Resilience4j（已在 `shop-common` 抽出程序化四层防护 helper，`seller-bff` / `buyer-bff` 主要下游读写与 checkout 相关路径均已统一接入）
- [x] Domain/Event 服务引入 Testcontainers 集成测试模板
- [ ] 关键链路增加契约测试（consumer/provider）
- [x] 统一异常语义：收敛 `catch (Exception)`，按边界抛出业务/下游/系统错误（`src/main/java` 中 broad catch 已清零，并已切换到 RFC 7807 兼容错误响应）
- [x] 统一指标命名与 SLO 草案（支付成功率、下单耗时、事件延迟）
- [x] 新增 `docs/SOURCE-OF-TRUTH-MATRIX.md`，建立主题级唯一权威文档映射
- [x] 新增 `docs/DELIVERY-PRIORITY-AND-DEPENDENCY-MAP.md`，建立跨任务依赖图
- [x] 补充 Kafka 规范：consumer 分型、重试策略、DLQ 回放与演练流程
- [x] 补充幂等规范：HTTP/Kafka 统一幂等 key 与处理状态机
- [x] 建立 K8s / Spring Boot 3.5 Feature Toggle 基线（OpenFeature + ConfigMap + Configuration Watcher 热刷新）
- [x] 新增 2026 技术栈最佳实践与演进指南，并同步 docs-site / 安全 / 可观测 / 架构文档
- [x] 落地 ArchUnit 架构规则测试（5 大类 19 条规则：编码规范、分层约束、命名规范、Spring 专项、幂等契约；参见 `docs/ARCHUNIT-RULES.md`）

### 14.3 P2（中期）

- [ ] 引入 OpenRewrite 作为批量升级与规范收敛工具（Boot/Cloud/API 变更）
- [x] 建立“脚手架版本化策略”（模板 v1/v2）与变更公告流程
- [x] 建立服务准入清单（Architecture Decision Checklist）
- [x] 建立平台工程看板：模板采纳率、服务合规率、发布失败率
- [x] 新增 `docs/OBSERVABILITY-ALERTING-SLO.md`（SLO/Error Budget/告警分级/Runbook 模板）
- [x] 新增 `docs/SECURITY-BASELINE-2026.md`（北南向/东西向安全边界与演进路线）
- [x] 新增 `docs/deployment/APPLICATION-CONTRACT-K8S.md`（应用交付契约）

### 14.4 DoD（Definition of Done）

- [ ] 新增任意微服务可在 10 分钟内由模板生成并通过 CI 基线
- [ ] 新增服务默认具备：健康检查、指标、追踪、统一错误模型、最小测试集
- [ ] 旧服务完成分批对齐，不阻塞业务功能迭代
