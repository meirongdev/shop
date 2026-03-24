# 新用户优惠 & 新手引导设计文档

> 版本：1.0 | 日期：2026-03-20

---

## 一、模块归属决策

新用户优惠不是一个功能，而是多个系统协作的**编排结果**。核心原则：**用事件驱动解耦，奖励由各自的权威服务发放**。

```
user.registered.v1（profile-service 发布）
        │
        ├──→ loyalty-service    发放欢迎积分 + 创建新人任务进度
        └──→ promotion-service  发放欢迎券包（$5 无门槛 + 首单免邮 + 首单折扣）
```

### 各功能归属决策

| 功能 | 归属服务 | 理由 |
|------|---------|------|
| 欢迎积分（100 pts） | `loyalty-service` | 积分是忠诚度货币，REGISTER 赚取规则已设计 |
| 欢迎优惠券包 | `promotion-service` | 优惠券是促销工具，由 promotion-service 发放 |
| 首单专属折扣 | `promotion-service` | `NEW_USER` 促销类型，条件：`IS_FIRST_ORDER=true` |
| 新人任务系统 | `loyalty-service` | 任务完成均发积分，行为事件（购物/签到/评价）loyalty-service 已消费 |
| 新人专区商品 | `marketplace-service` | 商品展示逻辑，按用户是否首购过滤 |
| 触发编排 | `profile-service` 发布事件 | 注册是用户档案事件，由档案服务发布 |

**不需要新建服务**：新用户优惠是现有服务能力的**组合**，通过 Kafka 事件解耦编排即可。

---

## 二、触发机制：user.registered.v1

```java
// profile-service: BuyerProfileApplicationService.java
@Transactional
public CreateProfileResponse createBuyerProfile(CreateProfileRequest req) {
    // 1. 创建档案
    BuyerProfileEntity profile = new BuyerProfileEntity(req);
    profileRepository.save(profile);

    // 2. 写 Outbox 事件（与档案创建同一事务，保证原子性）
    outboxRepository.save(new ProfileOutboxEvent(
        profile.getPlayerId(),
        "profile.events.v1",
        "user.registered.v1",
        buildRegisteredPayload(profile)
    ));

    return toResponse(profile);
}
```

**事件 Payload：**

```json
{
  "id": "evt-uuid",
  "source": "profile-service",
  "type": "user.registered.v1",
  "timestamp": "2026-03-20T10:00:00Z",
  "data": {
    "player_id":      "player-new-001",
    "username":       "new_user",
    "email":          "user@example.com",
    "register_source": "ORGANIC",
    // ORGANIC / REFERRAL / GAME_REWARD / SOCIAL_LOGIN
    "referrer_id":    "player-1001",   // REFERRAL 时有值
    "registered_at":  "2026-03-20T10:00:00Z"
  }
}
```

---

## 三、新人礼包设计

### 3.1 即时到账（注册成功后 < 5 分钟内发放）

| 奖励 | 内容 | 有效期 | 发放服务 |
|------|------|--------|---------|
| 欢迎积分 | 100 pts | 当年有效 | loyalty-service |
| 无门槛新人券 | $5 off，无最低消费 | 14 天 | promotion-service |
| 首单免邮券 | 免运费 1 次 | 30 天 | promotion-service |
| 首单折扣券 | 9 折，最高减 $20 | 14 天 | promotion-service |

> 如果是被邀请注册（`register_source=REFERRAL`），额外再发一张 **$3 礼品券**，邀请人同时获得 50 pts。

### 3.2 当前消费异常策略补充（2026-03-23）

- `promotion-service` 与 `loyalty-service` 现已把 `user.registered.v1` 监听器改成“坏消息直送 DLT、瞬时依赖失败有限重试”的模式。
- 也就是说：JSON/契约错误不会再被简单 `catch + log` 吞掉；数据库瞬时故障则允许 Kafka retry topic 做补偿。
- 这让“注册事件已发布但欢迎券/欢迎积分静默丢失”的问题更容易被发现、回放与修复。

### 3.2 新人礼包发放实现

**loyalty-service 消费 user.registered.v1：**

```java
// LoyaltyOnboardingListener.java
@KafkaListener(topics = "profile.events.v1",
               groupId = "loyalty-service-onboarding")
public void onUserRegistered(EventEnvelope<UserRegisteredData> event) {
    if (!"user.registered.v1".equals(event.type())) return;

    String playerId = event.data().playerId();

    // 防重（幂等）
    if (loyaltyAccountRepo.existsById(playerId)) return;

    // 1. 创建积分账户（balance=0，tier=SILVER）
    loyaltyAccountRepo.save(new LoyaltyAccount(playerId));

    // 2. 发放欢迎积分（走标准 earnPoints 流程，写流水）
    earnPoints(playerId, 100L, "REGISTER", event.id());

    // 3. 创建新人任务进度（7 个任务，14 天到期）
    onboardingTaskService.createTasksForPlayer(playerId,
        event.data().registeredAt().plus(14, DAYS));
}
```

**promotion-service 消费 user.registered.v1：**

```java
// PromotionOnboardingListener.java
@KafkaListener(topics = "profile.events.v1",
               groupId = "promotion-service-onboarding")
public void onUserRegistered(EventEnvelope<UserRegisteredData> event) {
    if (!"user.registered.v1".equals(event.type())) return;

    String playerId = event.data().playerId();

    // 防重（检查该用户是否已收到新人礼包）
    if (couponInstanceRepo.existsByPlayerAndSource(playerId, "NEW_USER_WELCOME")) return;

    // 批量发放三张券
    issueCoupon(playerId, "TMPL-WELCOME-5OFF",    "NEW_USER_WELCOME", 14);
    issueCoupon(playerId, "TMPL-WELCOME-FREESHIP", "NEW_USER_WELCOME", 30);
    issueCoupon(playerId, "TMPL-WELCOME-90PCT",    "NEW_USER_WELCOME", 14);

    // 被邀请用户额外礼包
    if ("REFERRAL".equals(event.data().registerSource())) {
        issueCoupon(playerId, "TMPL-REFERRAL-3OFF", "REFERRAL_REWARD", 30);
        // 同时奖励邀请人
        loyaltyClient.earnPoints(event.data().referrerId(), 50L, "REFERRAL_REWARD",
                                 event.id() + "-referrer");
    }
}
```

---

## 四、新人任务系统（loyalty-service）

### 4.1 设计思路

新人任务是**有时限的成就系统**，帮助新用户快速探索平台功能，同时通过积分奖励建立黏性。任务归属 loyalty-service，因为：
- 所有任务奖励均为积分
- loyalty-service 已消费用户行为事件（购物、签到、评价）
- 与积分账户、流水的写入天然在同一服务内

### 4.2 任务列表

| 任务 ID | 描述 | 触发事件 | 积分奖励 | 是否必做 |
|---------|------|---------|---------|---------|
| `FIRST_PURCHASE` | 完成首次购物 | `order.completed.v1` | +200 pts | ✅ 核心 |
| `FIRST_CHECKIN` | 完成首次签到 | 签到接口调用 | +20 pts | ✅ 核心 |
| `FIRST_REVIEW` | 完成首次商品评价 | `review.submitted.v1` | +50 pts | ✅ 核心 |
| `COMPLETE_PROFILE` | 上传头像 + 填写生日 | 档案更新接口 | +30 pts | 推荐 |
| `BIND_PHONE` | 绑定手机号 | 手机绑定接口 | +50 pts | 推荐 |
| `ADD_ADDRESS` | 添加收货地址 | 地址创建接口 | +20 pts | 推荐 |
| `FOLLOW_SHOP` | 收藏任意一个店铺 | 收藏接口 | +20 pts | 可选 |

**全部完成额外奖励**：全员任务完成后再得 **100 pts 成就奖励**

### 4.3 数据库（shop_loyalty 追加）

```sql
-- 新人任务模板（平台运营配置）
CREATE TABLE onboarding_task_template (
    id              VARCHAR(36)   NOT NULL PRIMARY KEY,
    task_code       VARCHAR(64)   NOT NULL UNIQUE,   -- FIRST_PURCHASE 等
    name            VARCHAR(128)  NOT NULL,
    description     VARCHAR(256),
    trigger_event   VARCHAR(64),                     -- Kafka 事件类型，NULL=手动触发
    points_reward   BIGINT        NOT NULL,
    sort_order      INT           NOT NULL DEFAULT 0,
    active          TINYINT(1)    NOT NULL DEFAULT 1
);

-- 玩家任务进度
CREATE TABLE onboarding_task_progress (
    id              VARCHAR(36)   NOT NULL PRIMARY KEY,
    player_id       VARCHAR(64)   NOT NULL,
    task_code       VARCHAR(64)   NOT NULL,
    status          VARCHAR(20)   NOT NULL DEFAULT 'PENDING',
    -- PENDING / COMPLETED / REWARDED / EXPIRED
    completed_at    TIMESTAMP(6),
    rewarded_at     TIMESTAMP(6),
    expire_at       TIMESTAMP(6)  NOT NULL,           -- 注册 + 14 天
    created_at      TIMESTAMP(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    UNIQUE KEY uq_player_task (player_id, task_code),
    INDEX idx_player (player_id),
    INDEX idx_expire (expire_at)
);
```

### 4.4 任务完成检测

```java
// OnboardingTaskService.java
@KafkaListener(topics = {"order.events.v1", "profile.events.v1", "review.events.v1"})
public void onBusinessEvent(EventEnvelope<?> event) {
    switch (event.type()) {
        case "order.completed.v1"      -> completeTask(event.data().playerId(), "FIRST_PURCHASE");
        case "review.submitted.v1"     -> completeTask(event.data().playerId(), "FIRST_REVIEW");
        case "profile.phone_bound.v1"  -> completeTask(event.data().playerId(), "BIND_PHONE");
        case "profile.updated.v1"      -> checkProfileCompletion(event);
        // 签到、收藏、地址添加由对应接口直接调用 completeTask()
    }
}

@Transactional
public void completeTask(String playerId, String taskCode) {
    OnboardingTaskProgress task = taskProgressRepo
        .findByPlayerIdAndTaskCode(playerId, taskCode).orElse(null);

    // 任务不存在（非新用户或已过期）、已完成、已过期，均跳过
    if (task == null || task.isCompleted() || task.isExpired()) return;

    task.complete(Instant.now());
    taskProgressRepo.save(task);

    // 发放积分（走标准 earnPoints，写 loyalty_transaction）
    long points = templateRepo.findByCode(taskCode).getPointsReward();
    earnPoints(playerId, points, "ONBOARDING_TASK", task.getId());

    // 检查是否全部完成，触发成就奖励
    checkAllTasksCompleted(playerId);
}

private void checkAllTasksCompleted(String playerId) {
    long total    = taskProgressRepo.countByPlayerId(playerId);
    long completed = taskProgressRepo.countCompletedByPlayerId(playerId);
    if (total > 0 && total == completed) {
        earnPoints(playerId, 100L, "ONBOARDING_COMPLETE", playerId + "-all");
    }
}
```

### 4.5 任务到期处理

```java
// 每日凌晨 2 点执行
@Scheduled(cron = "0 0 2 * * *")
public void expireOnboardingTasks() {
    List<OnboardingTaskProgress> expired = taskProgressRepo
        .findByStatusAndExpireAtBefore("PENDING", Instant.now());

    expired.forEach(t -> {
        t.setStatus("EXPIRED");
        taskProgressRepo.save(t);
    });
    // 发 Kafka 事件通知 notification-service 发送"任务已过期"邮件（可选）
}
```

---

## 五、首单专属折扣（promotion-service）

### 5.1 NEW_USER Condition Evaluator

```java
// NewUserConditionEvaluator.java
@Component
public class NewUserConditionEvaluator implements ConditionEvaluator {

    @Override
    public String supportedType() { return "IS_FIRST_ORDER"; }

    @Override
    public boolean evaluate(ConditionRule rule, PromotionContext ctx) {
        if (ctx.playerId() == null) return false;   // 游客不享受首单折扣
        // 调用 order-service 内部接口查询历史订单数
        long orderCount = orderClient.countCompletedOrders(ctx.playerId());
        return orderCount == 0;
    }
}
```

### 5.2 首单折扣活动配置（运营配置 JSON，无需改代码）

```json
{
  "type": "THRESHOLD_AMOUNT",
  "name": "新用户首单专享 9 折",
  "stacking_policy": "ADDITIVE",
  "priority": 100,
  "conditions": {
    "operator": "AND",
    "rules": [
      { "type": "IS_FIRST_ORDER" },
      { "type": "MIN_ORDER_AMOUNT", "value": 0.01 }
    ]
  },
  "benefits": [
    {
      "type": "PERCENTAGE_OFF",
      "value": 0.10,
      "apply_to": "ORDER_TOTAL",
      "max_discount": 20.00
    }
  ],
  "usage_limit": null,
  "per_user_limit": 1,
  "start_at": "2026-01-01T00:00:00Z",
  "end_at": "2099-12-31T23:59:59Z"
}
```

---

## 六、邀请裂变（Referral）

### 6.1 流程

```
老用户生成邀请链接
    → 分享给朋友
    → 朋友点击链接注册（register_source=REFERRAL, referrer_id=老用户ID）
    → 朋友完成首次购物后双方获得奖励

奖励触发条件：被邀请人完成首单（防止羊毛党仅注册不购物）
```

### 6.2 奖励配置

| 对象 | 奖励 | 触发条件 |
|------|------|---------|
| 被邀请人（新用户） | 额外 $3 券 + 50 pts | 注册即得 |
| 邀请人（老用户） | 50 pts | 被邀请人完成首单 |
| 双方（大促期间可调高） | 运营动态配置 | 同上 |

### 6.3 防刷设计

```
每个账号每月最多获得邀请奖励 10 次（上限可运营配置）
同设备/IP 注册的账号视为同一人（防多号薅羊毛）
被邀请人必须完成首单才触发邀请人奖励（防仅注册不购物）
邀请链接有效期 30 天
```

---

## 七、新人专区（marketplace-service）

### 7.1 功能设计

```
「新人专区」入口（首页 + 注册成功页）：
  仅显示对新用户开放专属价的商品
  新用户：注册后 7 天内、该商品首次购买

商品标签：
  [新人专享价 $XX]  ← 与普通价格并排展示，普通价划线

API 设计：
  GET /public/products?new_user_only=true     # 新人专区商品列表
  GET /public/products/{id}                    # 详情页展示新人价（若满足条件）
```

### 7.2 marketplace-service 实现

```sql
-- marketplace_product 表新增字段
ALTER TABLE marketplace_product
  ADD COLUMN new_user_price    DECIMAL(19,2),   -- NULL = 无新人价
  ADD COLUMN new_user_days     INT DEFAULT 7;    -- 注册后几天内有效
```

```java
// 结账时 buyer-bff 传入 is_new_user 标志（由 order-service 查订单数判断）
// marketplace-service 返回 effective_price 字段
public BigDecimal getEffectivePrice(ProductSku sku, boolean isNewUser) {
    if (isNewUser && sku.getProduct().getNewUserPrice() != null) {
        return sku.getProduct().getNewUserPrice();
    }
    return sku.getPrice();
}
```

---

## 八、完整注册 → 首购用户体验流程

```
注册成功页面（< 5 分钟内异步发放完成）：

  🎉 欢迎加入！您已获得：

  ⭐ 100 积分  已到账
  🎫 $5 无门槛券  14 天有效
  🚚 首单免邮券  30 天有效
  💸 首单 9 折券  14 天有效（最高减 $20）

  [去使用优惠券]  [查看积分]

  ─────────────────────────────────
  🗂️ 新人任务（14 天内完成，可额外获得 470 积分）

  ○ 完成首次购物   +200 pts
  ○ 完成首次签到   +20 pts
  ○ 完成首次评价   +50 pts
  ○ 完善个人资料   +30 pts
  ○ 绑定手机号     +50 pts
  ○ 添加收货地址   +20 pts
  ○ 收藏一个店铺   +20 pts
  ── 全部完成额外  +100 pts ──

  进度：0 / 7   [去完成]
```

---

## 九、Kafka 事件

| Topic 发布 | 发布方 | 消费方 |
|-----------|--------|--------|
| `profile.events.v1` (user.registered.v1) | profile-service | loyalty-service、promotion-service、notification-service |
| `profile.events.v1` (user.phone_bound.v1) | profile-service | loyalty-service（任务完成检测） |
| `loyalty.onboarding.task_completed.v1` | loyalty-service | notification-service（可选推送） |
| `loyalty.onboarding.all_completed.v1` | loyalty-service | notification-service（恭喜完成所有任务） |

---

## 十、数据指标

| 指标 | 定义 | 目标 |
|------|------|------|
| 礼包领取率 | 注册后 24h 内查看礼包 / 注册数 | > 70% |
| 首单转化率 | 完成首单 / 注册数（7 天内） | > 30% |
| 新人任务完成率 | 完成 ≥ 3 个任务 / 注册数 | > 40% |
| 全任务完成率 | 全部完成 / 注册数 | > 10% |
| 邀请转化率 | 邀请链接注册 / 链接点击 | > 20% |
| 邀请首单率 | 被邀请人完成首单 / 被邀请人注册 | > 40% |
| 礼包 ROI | 被新人礼包带动的 GMV / 礼包成本 | > 8× |
