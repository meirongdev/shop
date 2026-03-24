# loyalty-service — 积分与忠诚度服务设计文档

> 版本：1.2 | 日期：2026-03-22 | 状态：核心已实现

## 0. 当前集成状态（2026-03-22）

- `PointsExpiryScheduler` 已上线，负责年度过期积分批处理。
- `buyer-bff` 已提供 Loyalty Hub 聚合接口，把账户、任务、奖励、流水、兑换记录统一暴露给 buyer 侧。
- `buyer-portal` 已提供 `/buyer/loyalty` 页面，支持签到、积分商城兑换、查看最近流水与兑换记录。
- 购物车结账链路已支持 `pointsToUse`，由 buyer-bff 在下单时调用 loyalty-service 完成积分抵扣。
- `UserRegisteredListener` 与 `OrderEventListener` 现在通过 `@RetryableTopic` 区分异常语义：poison pill/契约错误直接进入 DLT，数据库与 `profile-service` 的瞬时失败才触发 Kafka 有限重试。

---

## 一、模块归属决策

### 为什么需要独立的 loyalty-service？

| 功能 | wallet-service | promotion-service | loyalty-service |
|------|---------------|-------------------|-----------------|
| 货币余额（真实货币） | ✅ 归属 | ❌ | ❌ |
| 优惠券 / 活动折扣 | ❌ | ✅ 归属 | ❌ |
| 积分账户 & 流水 | ❌ | ❌ | ✅ **归属** |
| **签到** | ❌ | ❌ | ✅ **归属** |
| **积分商品 & 兑换** | ❌ | ❌ | ✅ **归属** |

**结论：**
- `wallet-service` 专注真实货币（Stripe 集成、金融合规）
- `promotion-service` 专注营销活动（折扣、优惠券的发放与核销）
- `loyalty-service` 专注用户粘性（积分账户、签到、兑换），三者职责不重叠

**签到归属 loyalty-service**：签到是用户行为激励，产出是积分（loyalty-service 的核心货币），而非折扣券（promotion-service 的货币）。

**奖品兑换归属 loyalty-service**：兑换是积分的消耗场景，兑换目录（积分商品）是积分系统的一部分；实物商品兑换履约则调用 order-service 创建特殊订单。

---

## 二、服务定位

```
loyalty-service（:8088）

职责：
  1. 积分账户管理     — 余额、到期、历史流水
  2. 积分赚取规则     — 购物返积分、签到、评价、分享
  3. 签到系统         — 连签奖励、补签、签到日历
  4. 积分兑换目录     — 积分商品管理（实物/虚拟/优惠券）
  5. 积分兑换订单     — 创建兑换、库存占用、履约触发
  6. 积分到期清零     — 按年度批量清零过期积分
  7. 新用户引导任务   — 监听 user.registered.v1，管理 7 项新人任务进度
```

---

## 三、数据库设计（shop_loyalty）

### 3.1 积分账户表

```sql
CREATE TABLE loyalty_account (
    player_id     VARCHAR(64)  NOT NULL PRIMARY KEY,
    total_points  BIGINT       NOT NULL DEFAULT 0,   -- 累计获得积分（统计用）
    used_points   BIGINT       NOT NULL DEFAULT 0,   -- 累计消耗积分
    balance       BIGINT       NOT NULL DEFAULT 0,   -- 当前可用积分（= 未过期 earned - used）
    tier          VARCHAR(20)  NOT NULL DEFAULT 'SILVER', -- SILVER/GOLD/PLATINUM
    tier_points   BIGINT       NOT NULL DEFAULT 0,   -- 当年有效积分（决定等级）
    created_at    TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at    TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6)
);
```

### 3.2 积分流水表

```sql
CREATE TABLE loyalty_transaction (
    id            VARCHAR(36)  NOT NULL PRIMARY KEY,
    player_id     VARCHAR(64)  NOT NULL,
    type          VARCHAR(32)  NOT NULL,   -- EARN / DEDUCT / EXPIRE / ADJUST
    source        VARCHAR(32)  NOT NULL,   -- PURCHASE / CHECKIN / REVIEW / SHARE / REDEEM / ADMIN
    amount        BIGINT       NOT NULL,   -- 正数 = 赚取，负数 = 消耗
    balance_after BIGINT       NOT NULL,   -- 流水后余额（快照，便于对账）
    reference_id  VARCHAR(64),             -- 关联 order_id / checkin_id / redemption_id
    remark        VARCHAR(256),
    expire_at     DATE,                    -- 积分过期日（NULL = 永不过期）
    created_at    TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    INDEX idx_player_created (player_id, created_at DESC)
);
```

### 3.3 签到记录表

```sql
CREATE TABLE loyalty_checkin (
    id             VARCHAR(36)  NOT NULL PRIMARY KEY,
    player_id      VARCHAR(64)  NOT NULL,
    checkin_date   DATE         NOT NULL,
    streak_day     INT          NOT NULL DEFAULT 1,   -- 连续签到天数
    points_earned  BIGINT       NOT NULL,             -- 本次获得积分
    is_makeup      TINYINT(1)   NOT NULL DEFAULT 0,   -- 是否补签
    makeup_cost    BIGINT       NOT NULL DEFAULT 0,   -- 补签消耗积分
    created_at     TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    UNIQUE KEY uq_player_date (player_id, checkin_date)
);
```

### 3.4 积分商品表（兑换目录）

```sql
CREATE TABLE loyalty_reward_item (
    id              VARCHAR(36)   NOT NULL PRIMARY KEY,
    name            VARCHAR(128)  NOT NULL,
    description     VARCHAR(512),
    type            VARCHAR(32)   NOT NULL,    -- PHYSICAL / VIRTUAL / COUPON / DONATION
    points_required BIGINT        NOT NULL,    -- 兑换所需积分
    stock           INT           NOT NULL DEFAULT 0,
    image_url       VARCHAR(512),
    coupon_template_id VARCHAR(36),            -- type=COUPON 时关联 promotion-service 优惠券模板
    marketplace_product_id VARCHAR(36),        -- type=PHYSICAL 时关联实物商品 ID
    active          TINYINT(1)    NOT NULL DEFAULT 1,
    sort_order      INT           NOT NULL DEFAULT 0,
    start_at        TIMESTAMP(6),
    end_at          TIMESTAMP(6),
    created_at      TIMESTAMP(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at      TIMESTAMP(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6)
);
```

### 3.5 兑换订单表

```sql
CREATE TABLE loyalty_redemption (
    id              VARCHAR(36)   NOT NULL PRIMARY KEY,
    player_id       VARCHAR(64)   NOT NULL,
    reward_item_id  VARCHAR(36)   NOT NULL,
    reward_name     VARCHAR(128)  NOT NULL,   -- 快照（防商品修改）
    points_spent    BIGINT        NOT NULL,
    quantity        INT           NOT NULL DEFAULT 1,
    status          VARCHAR(32)   NOT NULL,   -- PENDING / PROCESSING / COMPLETED / FAILED / CANCELLED
    type            VARCHAR(32)   NOT NULL,   -- PHYSICAL / VIRTUAL / COUPON
    delivery_info   JSON,                     -- 收货地址（PHYSICAL）
    coupon_code     VARCHAR(64),              -- 发放的优惠券码（COUPON）
    order_id        VARCHAR(36),              -- 触发的履约订单 ID（PHYSICAL）
    remark          VARCHAR(256),
    created_at      TIMESTAMP(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at      TIMESTAMP(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    INDEX idx_player (player_id, created_at DESC)
);
```

### 3.6 积分规则配置表（可运营配置）

```sql
CREATE TABLE loyalty_earn_rule (
    id              VARCHAR(36)   NOT NULL PRIMARY KEY,
    source          VARCHAR(32)   NOT NULL UNIQUE,  -- PURCHASE / CHECKIN / REVIEW / FIRST_ORDER ...
    points_formula  VARCHAR(32)   NOT NULL,          -- FIXED / PER_DOLLAR / PERCENTAGE
    base_value      BIGINT        NOT NULL,           -- 基础值（固定积分 或 每元积分数）
    tier_multiplier JSON          NOT NULL,           -- {"SILVER":1.0,"GOLD":1.5,"PLATINUM":2.0}
    max_per_day     BIGINT,                           -- 每日上限（NULL=无限）
    active          TINYINT(1)    NOT NULL DEFAULT 1,
    updated_at      TIMESTAMP(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6)
);

-- 初始化数据
INSERT INTO loyalty_earn_rule VALUES
('rule-purchase', 'PURCHASE',    'PER_DOLLAR', 10,  '{"SILVER":1.0,"GOLD":1.5,"PLATINUM":2.0}', NULL,  1),
('rule-checkin',  'CHECKIN',     'FIXED',       5,  '{"SILVER":1.0,"GOLD":1.0,"PLATINUM":1.0}', 1,     1),
('rule-review',   'REVIEW',      'FIXED',      20,  '{"SILVER":1.0,"GOLD":1.0,"PLATINUM":1.0}', 1,     1),
('rule-share',    'SHARE',       'FIXED',       5,  '{"SILVER":1.0,"GOLD":1.0,"PLATINUM":1.0}', 3,     1),
('rule-register', 'REGISTER',    'FIXED',     100,  '{"SILVER":1.0,"GOLD":1.0,"PLATINUM":1.0}', NULL,  1);
```

---

## 四、新用户引导任务系统

### 4.0 模块归属说明

`user.registered.v1` 由 **profile-service** 在用户注册成功后发布。loyalty-service 和 promotion-service 独立消费此事件，各自处理积分/任务 和 优惠券 的发放，无需相互调用。

### 4.1 数据库表设计

```sql
-- 新人任务模板（运营可配置，通常固定 7 项）
CREATE TABLE onboarding_task_template (
    id            VARCHAR(36)   NOT NULL PRIMARY KEY,
    task_key      VARCHAR(64)   NOT NULL UNIQUE,  -- COMPLETE_PROFILE / BIND_PHONE / FIRST_CHECKIN /
                                                  -- FIRST_ADD_CART / FIRST_ORDER / FIRST_REVIEW / FIRST_REFERRAL
    title         VARCHAR(128)  NOT NULL,
    description   VARCHAR(256),
    points_reward BIGINT        NOT NULL,
    coupon_template_id VARCHAR(36),               -- 非 NULL 时额外发券（如首单 +$3 券）
    sort_order    INT           NOT NULL DEFAULT 0,
    active        TINYINT(1)    NOT NULL DEFAULT 1
);

-- 每个新用户的任务进度
CREATE TABLE onboarding_task_progress (
    id            VARCHAR(36)   NOT NULL PRIMARY KEY,
    player_id     VARCHAR(64)   NOT NULL,
    task_key      VARCHAR(64)   NOT NULL,
    status        VARCHAR(16)   NOT NULL DEFAULT 'PENDING',  -- PENDING / COMPLETED
    points_issued BIGINT        NOT NULL DEFAULT 0,
    completed_at  TIMESTAMP(6),
    expire_at     TIMESTAMP(6)  NOT NULL,                    -- 注册日 + 14 天
    created_at    TIMESTAMP(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    UNIQUE KEY uq_player_task (player_id, task_key),
    INDEX idx_player_expire (player_id, expire_at)
);
```

### 4.2 新用户注册事件监听

```java
// LoyaltyOnboardingListener.java

@KafkaListener(topics = "user.registered.v1")
public void onUserRegistered(EventEnvelope<UserRegisteredEventData> event) {
    String playerId = event.data().playerId();
    Instant expireAt = Instant.now().plus(14, ChronoUnit.DAYS);

    // 1. 发放注册积分（100 pts）
    accountService.earnPoints(playerId, 100, "REGISTER", event.eventId());

    // 2. 初始化 7 项新人任务进度
    List<OnboardingTaskTemplate> templates = templateRepository.findAllActive();
    List<OnboardingTaskProgress> progresses = templates.stream()
        .map(t -> OnboardingTaskProgress.init(playerId, t.getTaskKey(), expireAt))
        .toList();
    progressRepository.saveAll(progresses);
}
```

### 4.3 任务完成处理

```java
// OnboardingTaskService.java

public void completeTask(String playerId, String taskKey) {
    // 幂等：已完成则直接返回
    OnboardingTaskProgress progress = progressRepository
        .findByPlayerIdAndTaskKey(playerId, taskKey)
        .orElse(null);
    if (progress == null || progress.isCompleted() || progress.isExpired()) return;

    // 标记完成 & 发积分
    progress.complete();
    progressRepository.save(progress);

    OnboardingTaskTemplate template = templateRepository.findByTaskKey(taskKey);
    accountService.earnPoints(playerId, template.getPointsReward(), "ONBOARDING_TASK", taskKey);

    // 检查是否完成所有 7 项 → 额外奖励 100 pts
    long completedCount = progressRepository.countCompleted(playerId);
    if (completedCount == templateRepository.countActive()) {
        accountService.earnPoints(playerId, 100, "ONBOARDING_COMPLETE", playerId);
    }
}
```

### 4.4 任务触发点（各服务回调 or Kafka 消费）

| 任务 Key | 触发方式 |
|---------|---------|
| `COMPLETE_PROFILE` | profile-service 更新头像/昵称后调用内部接口 `POST /internal/loyalty/onboarding/task` |
| `BIND_PHONE` | profile-service 绑定手机成功后触发 |
| `FIRST_CHECKIN` | loyalty-service 签到逻辑内部直接调用 `completeTask` |
| `FIRST_ADD_CART` | buyer-bff 加购时调用 `POST /internal/loyalty/onboarding/task` |
| `FIRST_ORDER` | 消费 `order.completed.v1`，判断是否为首单 |
| `FIRST_REVIEW` | 消费 `review.events.v1 (SUBMITTED)`，判断是否为首评 |
| `FIRST_REFERRAL` | 消费 `user.registered.v1`，referrer_id 非空时为被邀请人触发邀请人任务 |

### 4.5 任务过期清理

```java
// @Scheduled(cron = "0 0 3 * * *")  每天凌晨 3 点
public void expireOnboardingTasks() {
    int deleted = progressRepository.deleteExpiredPending(Instant.now());
    log.info("Expired {} onboarding task records", deleted);
}
```

---

## 五、签到系统设计

### 4.1 连签奖励规则

```
Day 1:  基础积分 × 1  （5分）
Day 2:  基础积分 × 1  （5分）
Day 3:  基础积分 × 2  （10分）
Day 4:  基础积分 × 1  （5分）
Day 5:  基础积分 × 1  （5分）
Day 6:  基础积分 × 1  （5分）
Day 7:  基础积分 × 5  + 奖励券  （25分 + 优惠券）
Day 8+: 重置为 Day 1，进入新的 7 天周期
```

### 4.2 签到接口

```
POST /loyalty/v1/checkin
Header: X-Player-Id: player-1001

Response:
{
  "streak_day": 3,
  "points_earned": 10,
  "balance_after": 310,
  "bonus": { "type": "COUPON", "code": "STREAK7-XXXX" },   // 第7天额外奖励
  "next_day_preview": { "streak_day": 4, "points_preview": 5 },
  "calendar": [
    { "date": "2026-03-14", "checked": true,  "points": 5 },
    { "date": "2026-03-15", "checked": true,  "points": 5 },
    { "date": "2026-03-16", "checked": false, "makeup_cost": 20 },  // 漏签，可补签
    { "date": "2026-03-20", "checked": true,  "points": 10 }
  ]
}
```

### 4.3 补签机制

```
规则：
  - 每月最多补签 3 次
  - 补签需消耗积分（20 积分/次）
  - 补签不延续当前连签 streak（只补记录，不续 streak）

POST /loyalty/v1/checkin/makeup
Body: { "date": "2026-03-16" }
```

### 4.4 签到日历查询

```
GET /loyalty/v1/checkin/calendar?month=2026-03
Response: {
  "current_streak": 3,
  "longest_streak": 15,
  "total_checkins_this_month": 16,
  "checkins": [ { "date": "...", "checked": bool, "points": N } ]
}
```

---

## 五、积分兑换流程

### 5.1 兑换目录

```
GET /loyalty/v1/rewards?type=ALL&page=0&size=20

Response:
{
  "items": [
    {
      "id": "reward-001",
      "name": "10元优惠券",
      "type": "COUPON",
      "points_required": 500,
      "stock": 100,
      "image_url": "..."
    },
    {
      "id": "reward-002",
      "name": "品牌帆布袋",
      "type": "PHYSICAL",
      "points_required": 2000,
      "stock": 50
    }
  ]
}
```

### 5.2 发起兑换

```
POST /loyalty/v1/redemptions
Header: X-Player-Id: player-1001
Body:
{
  "reward_item_id": "reward-001",
  "quantity": 1,
  "delivery_info": {            // type=PHYSICAL 时必填
    "name": "张三",
    "phone": "138xxxx0001",
    "address": "..."
  }
}

处理流程：
  1. 校验积分余额 ≥ points_required × quantity
  2. 锁定商品库存（SELECT FOR UPDATE）
  3. 扣减积分（loyalty_account.balance -= points）
  4. 写 loyalty_transaction（DEDUCT, source=REDEEM）
  5. 创建 loyalty_redemption（status=PROCESSING）
  6. 根据类型履约：
     - COUPON  → 调用 promotion-service 生成优惠券
     - VIRTUAL → 直接写 coupon_code（PIN 码 / 兑换码）
     - PHYSICAL → 调用 order-service 创建 POINTS_REDEMPTION 订单
  7. 更新 loyalty_redemption（status=COMPLETED, coupon_code/order_id）
  8. 发送 Kafka 事件 loyalty.redemption.v1
```

### 5.3 实物兑换与 order-service 集成

```java
// loyalty-service 调用 order-service 内部 API
POST /internal/orders/redemption
{
  "player_id": "player-1001",
  "type": "POINTS_REDEMPTION",
  "items": [
    { "sku": "CANVAS-BAG-001", "quantity": 1, "points_price": 2000 }
  ],
  "shipping_address": { ... },
  "redemption_id": "redeem-xxx"
}
```

---

## 六、积分赚取事件驱动

```
Kafka Consumer Topics:

order.events.v1 (COMPLETED)
  → 计算购物积分
  → 公式：floor(order_amount × earn_rate × tier_multiplier)
  → 发布 loyalty.points.earned.v1

review.events.v1 (SUBMITTED)
  → 发放评价积分（20分）
  → 防重：reference_id = review_id，unique 约束

wallet.transactions.v1 (DEPOSIT，首次充值)
  → 由 promotion-service 处理（现有逻辑）
  → loyalty-service 也可监听，发放充值积分（可选）
```

```java
// LoyaltyEventListener.java

@KafkaListener(topics = "order.events.v1")
public void onOrderCompleted(EventEnvelope<OrderEventData> event) {
    if (!"ORDER_COMPLETED".equals(event.type())) return;

    OrderEventData data = event.data();
    LoyaltyAccount account = accountService.getOrCreate(data.playerId());
    EarnRule rule = earnRuleRepository.findBySource("PURCHASE");

    long points = rule.calculate(data.totalAmount(), account.getTier());
    accountService.earnPoints(data.playerId(), points, "PURCHASE", data.orderId());
}
```

---

## 七、积分到期策略

```
年度积分到期（参考京东、天猫）：
  - 每年 3 月 31 日，清零上一自然年（1月1日~12月31日）获得的积分
  - 实现：定时任务 @Scheduled(cron = "0 0 2 31 3 *")
  - 写 loyalty_transaction（EXPIRE 类型，amount 为负）
  - 通知用户（loyalty.points.expiring.v1 事件）

替代方案（更精细）：
  - 每笔积分单独设置 expire_at = 获得日 + 365 天
  - 定时任务每日扫描 loyalty_transaction.expire_at <= today
  - 从 balance 扣减到期积分
```

---

## 八、API 汇总

```
GET  /loyalty/v1/account                      # 积分账户总览（余额、等级、流水摘要）
GET  /loyalty/v1/transactions?page=0&size=20  # 积分流水列表
POST /loyalty/v1/checkin                       # 今日签到
POST /loyalty/v1/checkin/makeup               # 补签
GET  /loyalty/v1/checkin/calendar             # 签到日历
GET  /loyalty/v1/rewards                      # 兑换目录
GET  /loyalty/v1/rewards/{id}                 # 商品详情
POST /loyalty/v1/redemptions                   # 发起兑换
GET  /loyalty/v1/redemptions                  # 我的兑换记录
GET  /loyalty/v1/redemptions/{id}             # 兑换详情
GET  /loyalty/v1/onboarding/tasks             # 新人任务进度列表

# 内部接口（X-Internal-Token）
POST /internal/loyalty/earn                   # 服务间调用赚取积分
GET  /internal/loyalty/account/{playerId}     # 查询积分（BFF 聚合）
POST /internal/loyalty/onboarding/task        # 触发新人任务完成（profile-service / buyer-bff 调用）
```

---

## 九、买家资产总览聚合（buyer-bff）

buyer-bff Dashboard 聚合时同时查询 loyalty-service：

```java
// BuyerDashboardService.java 并发调用
try (var scope = new StructuredTaskScope.ShutdownOnFailure()) {
    var walletFuture   = scope.fork(() -> walletClient.getWallet(playerId));
    var loyaltyFuture  = scope.fork(() -> loyaltyClient.getAccount(playerId));    // 新增
    var profileFuture  = scope.fork(() -> profileClient.getProfile(playerId));
    var couponsFuture  = scope.fork(() -> promotionClient.getCoupons(playerId));
    scope.join();

    return DashboardResponse.builder()
        .wallet(walletFuture.get())
        .loyaltyPoints(loyaltyFuture.get())    // 积分资产
        .profile(profileFuture.get())
        .coupons(couponsFuture.get())
        .build();
}
```

---

## 十、Kafka 事件

| Topic | 触发时机 | 消费方 |
|-------|---------|--------|
| `loyalty.points.earned.v1` | 积分赚取 | notification-service（通知用户） |
| `loyalty.points.expiring.v1` | 积分即将到期（提前 7 天）| notification-service |
| `loyalty.checkin.v1` | 用户签到 | 数据分析 |
| `loyalty.redemption.v1` | 兑换成功 | notification-service、order-service（实物） |

---

## 十一、与其他服务的依赖关系

```
loyalty-service
  消费：user.registered.v1    (profile-service 发布) → 新用户注册积分 + 初始化任务
  消费：order.events.v1       (order-service 发布)   → 购物积分 + 首单任务完成
  消费：review.events.v1      (future review-service 发布) → 评价积分 + 首评任务完成
  调用：promotion-service      (COUPON 兑换时生成优惠券)
  调用：order-service          (PHYSICAL 兑换时创建履约订单)
  被调：profile-service        (触发资料完善/绑定手机任务)
  被调：buyer-bff              (Dashboard 聚合 / 加购触发任务)
  被调：api-gateway            (路由)
```
