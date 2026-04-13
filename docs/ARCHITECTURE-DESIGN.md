# Shop Platform — 架构设计文档

> 版本：2.2 | 日期：2026-03-24 | 技术基线：Java 25 / Spring Boot 3.5 / Spring Cloud 2025.0
> 工程标准：`docs/ENGINEERING-STANDARDS-2026.md`
> 技术栈权威文档：`docs/TECH-STACK-BEST-PRACTICES-2026.md`

> 状态说明：
> - 本文档同时包含**当前实现**与**目标态蓝图**
> - 凡带 `← NEW` 标记、或在 `docs/ROADMAP-2026.md` 中尚未勾选完成的能力，均视为规划中
> - 如果本文与代码、运行清单、或权威技术栈文档冲突，以代码 + `docs/TECH-STACK-BEST-PRACTICES-2026.md` 为准

---

## 二、系统全景

### 2.1 设计目标

| 目标 | 说明 |
|------|------|
| 高扩展性 | 微服务独立部署、独立扩容；插件化游戏引擎支持零代码新增游戏类型 |
| 高可用 | 多副本、健康检查、优雅关闭；活动服务大促可独立扩容 50× |
| 安全边界清晰 | Gateway 统一鉴权，内部服务信任链 |
| 可观测 | 全链路追踪、结构化日志、指标暴露 |
| 游客友好 | 无需注册登录即可浏览、搜索、下单 |

### 2.2 角色隔离策略（重要架构约束）

**本平台严格实施 Buyer/Seller 角色隔离，禁止 Seller 进行购物。**

| 角色 | 能力范围 | 入口 | 限制 |
|------|---------|------|------|
| **Buyer** | 浏览商品、加购物车、结算、订单管理、积分、活动游戏 | `/buyer/**`, `/buyer-app/**`, `/api/buyer/**` | 仅可购物 |
| **Seller** | 商品管理、订单履约、促销/优惠券创建、钱包提现、店铺管理 | `/seller/**`, `/api/seller/**` | **禁止购物** |
| **Guest** | 浏览商品、游客下单（无需注册） | `/buyer/**` (未登录) | 仅游客通道 |

**架构实施细节：**
- 用户 JWT 中携带单一角色声明（`ROLE_BUYER` 或 `ROLE_SELLER`）
- Gateway 根据角色路由至不同 BFF：`/buyer/**` → buyer-bff，`/seller/**` → seller-bff
- Seller API 合约（`SellerApi.java`）**不包含**任何购物车、结算、下单端点
- Seller BFF 不暴露任何购物相关聚合接口
- 不存在角色切换机制，一个账号只能是 Buyer 或 Seller 之一

**为什么禁止 Seller 购物？**
- 简化系统边界，降低测试复杂度
- 避免 Seller 权限滥用（如自买自卖、刷单）
- 清晰的职责分离便于审计和合规

---

### 1.2 整体架构图

```
┌──────────────────────────────────────────────────────────────────────────────┐
│                            客户端层 (Client Layer)                            │
│  ┌─────────────────┐  ┌───────────────────────┐  ┌───────────────────────────┐    │
│  │  Buyer Portal   │  │    Seller App (KMP)   │  │ Mobile / External Clients │    │
│  │ Kotlin+Thymeleaf│  │ Compose Multiplatform │  │   (渐进接入，非统一基线)   │    │
│  └────────┬────────┘  └───────────┬───────────┘  └────────────┬──────────────┘    │
└───────────┼───────────────────────┼───────────────────────────┼──────────────────┘
            └───────────────────────┴───────────────────────────┘
                                 │ HTTPS
                                 ▼
┌──────────────────────────────────────────────────────────────────────────────┐
│                        边缘层 (Edge Layer) :8080                              │
│  ┌────────────────────────────────────────────────────────────────────────┐  │
│  │                           API Gateway                                   │  │
│  │  Spring Cloud Gateway MVC + Virtual Threads                             │  │
│  │  • JWT 校验 & Trusted Headers 注入                                      │  │
│  │  • 游客通道白名单（/public/**, /activity/v1/games/**）                  │  │
│  │  • Redis Lua 限流（Player-Id/IP）+ Redis 白名单 Canary 路由已落地       │  │
│  │  • 更细粒度风控 / 审计 / CORS 统一策略仍属后续演进方向                  │  │
│  │  • 路由: /buyer/** → buyer-bff  /seller/** → seller-bff                │  │
│  │          /activity/** → activity-service（直连，BFF 不聚合游戏）        │  │
│  └─────────────────────────────────┬──────────────────────────────────────┘  │
└─────────────────────────────────────┼────────────────────────────────────────┘
                                      │ Trusted Headers (NetworkPolicy Enforced)
              ┌───────────────────────┴────────────────────┐
              ▼                                             ▼
┌───────────────────────┐                     ┌────────────────────────────┐
│      Buyer BFF        │                     │       Seller BFF           │
│  Virtual Threads      │                     │   Virtual Threads          │
│  并发聚合多域数据      │                     │   商品 & 促销管理           │
│  游客购物流聚合        │                     │                            │
└────────┬──────────────┘                     └────────────┬───────────────┘
         │                                                 │
┌────────┴─────────────────────────────────────────────────┴────────────────────┐
│                          领域服务层 (Domain Services)                           │
│                                                                                │
│  ┌──────────────────┐  ┌───────────────────┐  ┌───────────────────────────┐  │
│  │  profile-service  │  │ marketplace-svc   │  │     order-service         │  │
│  │  用户档案 & 地址簿 │  │  商品目录/SKU/库存 │  │     订单状态机 & 退款     │  │
│  │  shop_profile     │  │  shop_marketplace  │  │     shop_order            │  │
│  └──────────────────┘  └───────────────────┘  └───────────────────────────┘  │
│                                                                                │
│  ┌──────────────────┐  ┌───────────────────┐  ┌───────────────────────────┐  │
│  │  wallet-service   │  │ promotion-svc     │  │   loyalty-service         │  │
│  │  钱包(真实货币)   │  │  促销引擎(策略模式) │  │   积分 & 签到 & 兑换      │  │
│  │  Outbox + Stripe  │  │  Kafka Consumer   │  │   shop_loyalty            │  │
│  │  shop_wallet      │  │  shop_promotion   │  │                           │  │
│  └──────────────────┘  └───────────────────┘  └───────────────────────────┘  │
│                                                                                │
│  ┌──────────────────────────────────────────────────────────────────────────┐ │
│  │                      activity-service（互动活动中心）                     │ │
│  │  插件化游戏引擎（GamePlugin SPI）                                         │ │
│  │  ┌────────────┐ ┌────────────┐ ┌────────────┐ ┌────────────┐            │ │
│  │  │InstantLottery│ │RedEnvelope│ │CollectCard │ │VirtualFarm │  ...       │ │
│  │  │砸金蛋/转盘  │ │  抢红包   │ │   集卡    │ │  虚拟养成  │            │ │
│  │  └────────────┘ └────────────┘ └────────────┘ └────────────┘            │ │
│  │  Redis（游戏热状态）  Kafka（异步奖励）  shop_activity（持久化）          │ │
│  └──────────────────────────────────────────────────────────────────────────┘ │
└────────────────────────────────────────────────────────────────────────────────┘
         │                              │
┌────────┴────────────┐   ┌────────────┴─────────────────────────────────────┐
│   基础设施 (Infra)   │   │         消息 & 缓存 (Messaging & Cache)           │
│                      │   │                                                  │
│  MySQL 8.4           │   │  Apache Kafka 3.9 (KRaft)                        │
│  （按服务独立 DB /   │   │  • wallet.transactions.v1                        │
│   schema 管理）      │   │  • order.events.v1                               │
│                      │   │  • marketplace.product.events.v1                 │
│  Redis 7.4           │   │  • 其他 activity / loyalty 主题按 Roadmap 演进   │
│  （本地/Kind 基线为  │   │                                                  │
│   单实例模式）       │   │                                                  │
│  Meilisearch 1.12    │   │                                                  │
│  （search-service）  │   │                                                  │
│                      │   │                                                  │
│  Garage S3            │   │                                                  │
│  S3 兼容对象存储      │   │                                                  │
│  • Tempo / Loki 后端  │   │                                                  │
│  • 商品图片与文件存储 │   │                                                  │
│  • 未来数据冷归档     │   │                                                  │
└──────────────────────┘   └──────────────────────────────────────────────────┘
          │
┌────────┴──────────────────────────────────────────────────┐
│                   可观测层 (Observability)                   │
│  ✅ Prometheus（指标采集 + exemplar storage）               │
│  ✅ OpenTelemetry Collector（traces/logs + k8s enrich）     │
│  ✅ Tempo（Trace 存储，接 Garage S3）                       │
│  ✅ Loki（OTLP 原生日志接入 + structured metadata）          │
│  ✅ Grafana（Prometheus + Loki + Tempo + Pyroscope）        │
│  ✅ Pyroscope（持续分析，和 Trace / Log 双向关联）            │
│  ✅ Prometheus Alert + SLO Rules（P1/P2 + burn-rate）       │
└────────────────────────────────────────────────────────────┘
```

---

## 二、服务清单

| 服务 | 端口 | 语言 | 职责 |
|------|------|------|------|
| `api-gateway` | 8080 | Java 25 | 统一入口、YAML 路由、JWT 校验、Trusted Headers、Redis 令牌桶限流、Canary 灰度 |
| `buyer-bff` | — | Java 25 | 买家聚合层、游客购物流 |
| `seller-bff` | — | Java 25 | 卖家聚合层 |
| `profile-service` | — | Java 25 | 用户档案、地址簿、收藏夹 |
| `marketplace-service` | — | Java 25 | 商品目录、搜索、库存、SKU |
| `order-service` | — | Java 25 | 订单 CRUD、状态机、退款 |
| `wallet-service` | — | Java 25 | 钱包余额、充值、支付（真实货币） |
| `promotion-service` | — | Java 25 | 促销活动、优惠券（Strategy 可扩展引擎） |
| `loyalty-service` | 8088 | Java 25 | 积分账户、签到、积分兑换 |
| `activity-service` | 8089 | Java 25 | 互动活动中心：砸金蛋/抢红包/集卡/拼团（插件化引擎） |
| `auth-server` | 8090 | Java 25 | JWT 签发、用户认证 |
| `search-service` | 8091 | Java 25 | Meilisearch 集成、商品全文搜索、Feature Toggle 试点 |
| `notification-service` | 8092 | Java 25 | 消息通知：邮件（Channel SPI，可扩展 SMS/WhatsApp） |
| `webhook-service` | 8093 | Java 25 | 开放平台 Webhook：事件订阅、HMAC 签名、重试推送 |
| `subscription-service` | 8094 | Java 25 | 订阅计划管理、自动续费 |
| `buyer-portal` | — | Kotlin | 买家门户 SSR（经 api-gateway 访问） |
| `kmp/seller-app` | — | Kotlin | 卖家 Compose Multiplatform 应用 (WASM/Android/iOS) |

> 端口为外部（host）映射端口，仅供开发直连。生产环境所有流量经 api-gateway (:8080) 统一入口。
> 详细模块归属说明见 `docs/services/SERVICE-DEPENDENCY-MAP.md`
> 服务技术栈与扩展点速查见 `docs/SERVICE-TECH-STACK-AND-EXTENSIBILITY.md`

---

## 三、核心架构决策

### 3.1 游客 (Guest) 通道设计

**需求**：买家无需注册/登录即可浏览商品、加入购物车、完成支付，以及参与部分公开活动游戏。

```
游客请求流：

Browser → API Gateway → buyer-bff → marketplace-service
                     ↘ (公开白名单路由，跳过 JWT 校验)

购物车：存入 Redis，以 session_id (cookie) 为 key，TTL = 7 天

游客游戏参与（限每日 1 次免费转盘等）：
  Browser → API Gateway → activity-service
  以 X-Guest-Session-Id 标识，防重 key = game:{id}:guest:{session_id}
```

**Gateway 路由白名单（无需鉴权）：**

```yaml
public-paths:
  - /public/**
  - /buyer/browse/**
  - /buyer/search
  - /buyer/product/**
  - /buyer/cart/**
  - /buyer/checkout/guest
  - /buyer/order/track
  - /auth/v1/token/**
  - /activity/v1/games              # 游戏列表（公开）
  - /activity/v1/games/*/info       # 游戏详情（公开）
```

### 3.2 安全信任链

```
外部请求
  → Gateway: 验证 JWT → 注入 Trusted Headers
           (当前：X-Request-Id, X-Buyer-Id, X-Username, X-Roles, X-Portal)
           (Gateway 响应默认返回 X-Request-Id / X-Trace-Id 供客户端关联排障)
  → 各服务: 信任 Trusted Headers (由 Kubernetes NetworkPolicy / Cilium 强制执行东西向安全)

activity-service 特殊规则：
  → 游戏参与接口检查 X-Buyer-Id（登录用户）或 X-Guest-Session-Id（游客）
  → 奖励发放内部调用 loyalty/promotion/wallet 传播身份上下文
```

### 3.3 事件驱动架构

```
Kafka Topics（完整）：

wallet.transactions.v1      → promotion-service（充值奖励）
                            → loyalty-service（首充积分）

order.events.v1             → loyalty-service（购物积分）
                            → promotion-service（优惠券核销后处理）
                            → activity-service（虚拟养成进度）  ← NEW
                            → notification-service

loyalty.checkin.v1          → activity-service（虚拟养成浇水）  ← NEW

activity.prize.won.v1       → notification-service（中奖推送） ← NEW
activity.game.ended.v1      → 数据分析、结算服务              ← NEW
activity.participated.v1    → 数据分析                        ← NEW
activity.groupbuy.formed.v1 → order-service（拼团自动下单）   ← NEW
```

**Outbox Pattern（所有产生业务事件的服务均采用）：**

```
Service DB Transaction:
  1. 写业务数据（order / wallet_account / activity_participation 等）
  2. 写 outbox_event 表（同一事务，保证原子性）

Outbox Poller (5s):
  3. 读取 PENDING 事件
  4. 发布到 Kafka Topic
  5. 标记为 PUBLISHED
```

### 3.4 核心 / 非核心依赖解耦原则（当前实现）

- **核心同步依赖**：`auth-server`、`api-gateway`、`marketplace-service`、`order-service`、`wallet-service`
  - 这些依赖直接决定“能否浏览、加购、下单、支付、查单”。
  - 当前要求是 **快速失败而非无限等待**：BFF 统一配置 `connectTimeout` / `readTimeout`，关键库存扣减路径已补 `CircuitBreaker`，避免单个慢调用拖垮 checkout 线程。
- **非核心同步依赖**：`promotion-service`、`loyalty-service`、`search-service`
  - 这些能力提升转化或留存，但不应该阻断核心交易。
  - 当前 buyer 侧策略：
    - `search-service` 不可用时，降级到 `marketplace-service` 搜索。
    - checkout 中 `promotion-service` 不可用时跳过优惠券抵扣；显式“券无效/已过期”仍返回业务错误。
    - checkout 中 `loyalty-service` 不可用时跳过积分抵扣；显式“积分不足”仍返回业务错误。
- **天然异步非核心依赖**：`notification-service`、`webhook-service`、购物积分发放链路中的 `loyalty-service`
  - 通过 Kafka / Outbox 解耦，失败不会反向阻断订单完成。
- **仍待继续完善的点**：
  - 优惠券核销补录、库存回补、积分退还目前仍以 best-effort 补偿为主，持久化 retry / outbox 仍属于后续平台工程任务。

### 3.5 数据库隔离

每个领域服务拥有独立的 MySQL Database：

| Database | 所属服务 | 核心表 |
|----------|---------|--------|
| `shop_profile` | profile-service | `buyer_profile`, `seller_profile`, `buyer_address` |
| `shop_marketplace` | marketplace-service | `marketplace_product`, `product_sku`, `product_category` |
| `shop_order` | order-service | `order`, `order_item`, `order_shipment`, `order_refund` |
| `shop_wallet` | wallet-service | `wallet_account`, `wallet_transaction`, `wallet_outbox_event` |
| `shop_promotion` | promotion-service | `promotion_offer`, `coupon_template`, `coupon_instance` |
| `shop_loyalty` | loyalty-service | `loyalty_account`, `loyalty_checkin`, `loyalty_redemption` |
| **`shop_activity`** | **activity-service** | **`activity_game`, `activity_participation`, `activity_reward_prize`** |

### 3.6 缓存策略（Redis）

| 数据 | Key 模式 | TTL | 策略 |
|------|---------|-----|------|
| 商品详情 | `cache:product:{id}` | 10 分钟 | Cache-Aside |
| 商品列表 | `cache:products:{hash}` | 2 分钟 | Cache-Aside |
| 游客购物车 | `cart:{session_id}` | 7 天 | Session Key |
| 登录购物车 | `cart:{buyer_id}` | 30 天 | Player Key |
| 库存（热商品） | `cache:stock:{sku_id}` | 30 秒 | Write-Through |
| JWT 黑名单 | `jwt:blacklist:{jti}` | Token TTL | Set |
| **游戏活跃状态** | **`game:state:{game_id}`** | **活动时长** | **Write-Through** |
| **游戏奖品库存** | **`prize:stock:{prize_id}`** | **活动时长** | **Atomic DECR** |
| **红包池** | **`re:packets:{game_id}`** | **活动时长** | **Redis List LPOP** |
| **参与防重** | **`game:joined:{game_id}:{buyer_id}`** | **24h / 活动时长** | **SET NX** |
| **限速** | **`ratelimit:game:{game_id}:{buyer_id}`** | **滑动窗口** | **INCR + EXPIRE** |

---

### 3.7 API 合约与内部模型 (DTO vs. Domain Model)

**当前实践**：
在 `order-service` 和 `marketplace-service` 等领域服务中，Controller 和 Application Service 直接使用 `shop-contracts` 中定义的 Record 对象（DTO）作为方法参数和返回值。

**架构权衡**：
- **优点**：极高的开发效率，减少了 DTO -> Domain -> Entity 的冗余转换，代码结构简洁，适合 CRUD 较多或业务逻辑尚在演进初期的服务。
- **缺点**：内部业务逻辑与外部接口强耦合。若合约发生破坏性变更，需同步大规模重构内部逻辑；且复杂的业务规则（如多版本 API 支持）难以在单一模型中优雅实现。

**演进原则**：
1. **充血实体（Rich Entity）**：尽管没有独立的 POJO 领域层，但必须将状态流转和核心业务逻辑留在 JPA Entity 中（如 `order.markPaid()`），避免 Application Service 变成臃肿的“过程式脚本”。
2. **触发隔离的信号**：当出现以下情况时，必须引入独立的内部领域模型（Domain Model）：
   - 服务需要同时支持多个不兼容的 API 版本（如 v1 和 v2）。
   - 业务逻辑复杂度导致单个 Entity 无法承载，需要多个聚合根协作。
   - DTO 的字段变更频繁导致非相关的业务计算代码被迫修改。
3. **查询路径建议**：对于纯查询（Read-only）路径，鼓励继续直接映射到合约 DTO，以保持极致的简洁性。

---

## 四、关键流程

### 4.1 游客下单流程

```
1. 游客浏览商品 → GET /public/products/{id}
2. 加入购物车   → POST /buyer/cart/items (cookie: session_id)
3. 进入结账     → POST /buyer/checkout/guest
   Body: { email, shipping_address, payment_method }
4. buyer-bff 验证库存 → marketplace-service
5. order-service 创建订单 (type=GUEST, buyer_id=null, order_token=UUID)
6. 前端跳转支付 → wallet-service / Stripe
7. 支付成功 → Kafka: order.events.v1 (PAID)
8. notification-service 发邮件给 guest_email（含 order_token 追踪链接）
```

### 4.2 活动游戏参与流程（以砸金蛋为例）

```
1. 买家进入游戏页 → GET /activity/v1/games/{id}/info（公开）
2. 点击参与       → POST /activity/v1/games/{id}/participate
   Gateway 注入 X-Buyer-Id
3. activity-service → AntiCheatGuard 检查（限速 + 每日次数）
4. activity-service → GamePlugin.participate()（由 InstantLotteryPlugin 处理）
   → Redis Lua Script: DECR prize:stock + 加权随机 → 得到 prizeId
5. 写 activity_participation（MySQL）+ outbox 事件
6. RewardDispatcher 异步发放（Kafka → 消费者调用 loyalty/promotion）
7. 返回 { prize_name, animation_hint: { target_egg: 2 } }
8. notification-service 消费 activity.prize.won.v1 → 推送中奖通知
```

### 4.3 抢红包并发流程

```
活动开始前（GameScheduler）：
  → 二倍均值算法预生成红包金额列表
  → LPUSH re:packets:{gameId} [1.23, 0.50, ...]
  → SET re:count:{gameId} 1000

用户抢包：
  → POST /activity/v1/games/{id}/participate
  → Redis Lua Script（原子执行）：
      CHECK 防重 → LPOP 弹出金额 → SET 已抢标记 → return amount
  → Kafka: activity.participated.v1（异步持久化）

活动结束后（5分钟内）：
  → 对账 Job：Redis claimed 数量 vs MySQL participation 数量
  → 差异记录告警，触发人工审核
```

### 4.4 卖家上架商品流程

```
1. 卖家登录 → JWT
2. 创建商品草稿 → POST /seller/products（type=DRAFT）
3. 上传图片 → POST /seller/products/{id}/images（→ 对象存储 S3/OSS）
4. 提交审核 → PUT /seller/products/{id}/submit
5. 平台审核通过 → marketplace-service 标记 status=PUBLISHED
6. Kafka: marketplace.product.published.v1（触发搜索索引更新）
```

---

## 五、activity-service 架构专节

### 5.1 插件化游戏引擎（核心扩展点）

```
GamePlugin SPI（Service Provider Interface）：

  新增一种游戏 = 实现 GamePlugin 接口 + 注册 Spring Bean
  无需修改 GameEngine 核心代码（开闭原则）

GamePluginRegistry
  ├── InstantLotteryPlugin   → 砸金蛋 / 大转盘 / 刮刮乐 / 九宫格  ✅ 已实现
  ├── RedEnvelopePlugin      → 抢红包                              ✅ 已实现
  ├── CollectCardPlugin      → 集卡 / 集碎片                       ✅ 已实现
  ├── VirtualFarmPlugin      → 虚拟养成 / 小树                     ✅ 已实现
  ├── QuizPlugin             → 答题竞猜                            🔲 规划中
  ├── SlashPricePlugin       → 砍价                               🔲 规划中
  ├── GroupBuyPlugin         → 拼团                               🔲 规划中
  └── [新游戏] → 实现接口即可接入
```

### 5.2 弹性扩容模型

```
正常期（日常）：      activity-service × 2 Pod
大促预热（T-1天）：   activity-service × 10 Pod（预置扩容）
开门红（T+0瞬间）：   activity-service × 50 Pod（HPA burst）
活动结束后（T+2h）：  activity-service × 2 Pod（自动缩容）

扩容触发指标：
  • custom.metric: activity_rps > 5000 → scale out
  • CPU usage > 60% → scale out
  • Redis connection pool > 80% → scale out

Redis 集群（活动期间）：
  主从 3+3，按 gameId hash 分片
  游戏库存 key 使用 hashtag {gameId} 保证同 slot
```

### 5.3 熔断与降级

```
activity-service 调用下游（loyalty/promotion/wallet）：
  Resilience4j CircuitBreaker:
    CLOSED → OPEN: 5秒内 > 50% 失败
    OPEN → HALF_OPEN: 30秒后试探
    降级策略: 奖励发放异步入队，稍后重试（不阻塞游戏体验）

游戏参与接口（对玩家）：
  即使奖励发放失败，游戏结果已记录在 MySQL
  定时补偿 Job（每分钟）扫描 reward_status=PENDING 的记录重试
```

---

## 六、部署架构

### 6.1 Kind / Kubernetes（本地开发基线）

```
统一入口：
  ./kind/setup.sh     → 创建 Kind 集群 + 构建镜像 + 加载镜像 + 应用清单
  ./kind/teardown.sh  → 清理本地集群

基础设施：
  mysql / redis / kafka / meilisearch / garage / observability stack

默认访问入口：
  api-gateway  → :8080
  mailpit      → :8025
  prometheus   → :9090
```

### 6.2 Kubernetes（生产）

```
Namespace: shop

常规服务（2 副本）：
  Deployment (replicas: 2)  → profile / marketplace / order / wallet / promotion / loyalty
  HPA: CPU 70% → max 8 replicas

activity-service（弹性配置）：
  Deployment (replicas: 2)
  HPA:
    minReplicas: 2
    maxReplicas: 50
    metrics:
      - type: Resource
        resource: { name: cpu, target: { averageUtilization: 60 } }
      - type: Pods
        pods: { metric: activity_rps, target: 5000 }
  PodDisruptionBudget: minAvailable: 1（大促期间禁止驱逐）

Ingress:
  shop.example.com          → buyer-portal
  api.shop.example.com      → api-gateway

Storage:
  MySQL (7 DBs)   → RDS / Cloud SQL（生产）
  Redis Cluster   → ElastiCache（生产，活动期间临时扩容节点）
```

---

## 七、可观测性

### 7.1 指标 (Metrics)

- 每个服务暴露 `:8081/actuator/prometheus`
- 通用指标：`http_server_requests_seconds`, `jvm_memory_used_bytes`, `kafka_consumer_lag`
- activity-service 专属指标：

```
activity_game_participants_total{game_id, game_type}    # 参与总人数
activity_prize_dispatched_total{prize_type, status}     # 奖励发放数
activity_redis_lua_duration_seconds{operation}          # Lua 脚本耗时
activity_anti_cheat_blocked_total{reason}               # 防作弊拦截数
activity_reward_pending_gauge                           # 待补偿奖励数量（告警用）
```

### 7.2 追踪 (Traces)

- OpenTelemetry SDK 100% 采样（大促期间可调至 10% 减负）
- Trace 贯穿：Gateway → activity-service → Redis Lua → Kafka → loyalty-service
- 关键 Span 属性：`game.id`, `game.type`, `player.id`, `prize.id`

### 7.3 日志

```json
{
  "timestamp": "2026-03-20T20:00:00.123Z",
  "level": "INFO",
  "service": "activity-service",
  "traceId": "abc123",
  "game_id": "game-redenvelope-618",
  "game_type": "RED_ENVELOPE",
  "buyer_id": "buyer-1001",
  "result": "WIN",
  "prize_type": "POINTS",
  "prize_value": 88,
  "redis_lua_ms": 2,
  "message": "red envelope claimed"
}
```

---

## 八、非功能性需求

| 指标 | 目标值 |
|------|--------|
| 商品列表 P99 响应 | < 200ms |
| 下单接口 P99 响应 | < 500ms |
| 支付成功率 | > 99.9% |
| 系统可用性 | 99.9% (8.7h/year) |
| **抢红包接口 P99 响应** | **< 50ms**（Redis Lua，不触碰 DB） |
| **砸金蛋接口 P99 响应** | **< 100ms** |
| **活动服务最大 QPS** | **> 50,000**（50 Pod × 1000 QPS/Pod） |
| **奖励补偿 SLA** | **< 5 分钟**（定时 Job 每分钟扫描） |
| 游客购物车持久化 | 7 天 |
| 数据库备份 | 每日增量 + 每周全量 |
