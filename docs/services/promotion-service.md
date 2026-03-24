# promotion-service — 促销服务设计文档（可扩展架构）

> 版本：1.2 | 日期：2026-03-22 | 状态：核心引擎已实现（coupon 正处于双轨过渡）

## 0. 当前集成状态（2026-03-22）

- `PromotionEngine` + `POST /promotion/v1/calculate` 已上线，用于 checkout 折扣计算。
- `buyer-bff` checkout 当前会同步调用：
  - `/promotion/v1/coupon/validate`
  - `/promotion/v1/coupon/apply`
  - `/promotion/v1/calculate`
- `coupon_template` / `coupon_instance` 已通过 Flyway V4 落地，并由 `CouponTemplateService` 与欢迎券链路使用。
- `CouponTemplateService#issueToBuyer` 已按模板维度接入 Redisson 分布式锁，串行化 `perUserLimit` / `totalLimit` 校验与实例创建，避免多实例并发超发。
- 现有 seller coupon CRUD、checkout 验券与核销仍兼容 legacy `coupon` / `coupon_usage` 模型，因此 promotion-service 当前是**双轨过渡态**，不是“完全未拆分”也不是“旧模型已彻底移除”。
- `WalletRewardListener` 与 `WelcomeCouponListener` 已在线消费事件，并按 2026 基线显式区分 `RetryableKafkaConsumerException` / `NonRetryableKafkaConsumerException`：坏消息直接进 DLT，数据库瞬时失败才有限重试。
- `WelcomeCouponListener` 现在会优先复用已存在的欢迎券模板，再为新用户发放实例券，避免“模板已存在时直接吞异常，导致后续用户拿不到实例券”的静默丢失问题。

---

## 一、现状分析

当前 promotion-service 已实现：
- `PromotionOfferEntity`：促销实体，已支持 `conditions` / `benefits` JSON
- `PromotionEngine`：条件评估 + 权益计算 + calculate API
- `CouponEntity` / `CouponUsageEntity`：legacy coupon 创建、验券、核销
- `CouponTemplateEntity` / `CouponInstanceEntity`：模板/实例拆分模型，已用于欢迎券与按用户发放场景
- `WalletRewardListener`：监听钱包充值事件，自动创建奖励活动
- `WelcomeCouponListener`：监听注册事件，发放欢迎券

**当前真实状态**：促销引擎已经上线；coupon 处于从 legacy `coupon` 向 `coupon_template` / `coupon_instance` 迁移的兼容阶段。

---

## 二、可扩展促销引擎设计

### 2.1 设计思路

采用 **策略模式（Strategy Pattern）+ 责任链（Chain of Responsibility）** 实现多类型促销：

```
购物车请求
    │
    ▼
PromotionEngine
    │
    ├── ConditionEvaluator（条件判断）
    │       ├── MinOrderAmountEvaluator   满X元
    │       ├── MinQuantityEvaluator      满X件
    │       ├── CategoryEvaluator         指定品类
    │       ├── ProductEvaluator          指定商品
    │       ├── UserTierEvaluator         会员等级
    │       ├── TimeWindowEvaluator       限时（秒杀时间窗口）
    │       └── CouponCodeEvaluator       优惠券码验证
    │
    ├── BenefitCalculator（权益计算）
    │       ├── FixedAmountBenefit        减 $N
    │       ├── PercentageBenefit         N 折
    │       ├── FreeShippingBenefit       免运费
    │       ├── GiftItemBenefit           赠品
    │       ├── BuyXGetYBenefit           买X送Y
    │       └── PointsMultiplierBenefit   积分加倍（联动 loyalty-service）
    │
    └── StackingPolicy（叠加规则）
            ├── EXCLUSIVE     互斥（只取最优）
            ├── ADDITIVE      可叠加（满减 + 优惠券 同时生效）
            └── PRIORITY      优先级排序，依次应用直到上限
```

### 2.2 促销类型一览

| 类型 (type) | 描述 | 条件示例 | 权益示例 |
|-------------|------|---------|---------|
| `THRESHOLD_AMOUNT` | 满额优惠 | 订单金额 ≥ $100 | 减 $10 |
| `THRESHOLD_QUANTITY` | 满件优惠 | 购买 ≥ 3 件 | 打 9 折 |
| `BUY_X_GET_Y` | 买赠 | 买 2 件 | 送 1 件（最低价） |
| `FLASH_SALE` | 限时秒杀 | 时间窗口内 | 指定折扣 |
| `COUPON_CODE` | 优惠码 | 输入正确 code | 减固定金额/折扣 |
| `FREE_SHIPPING` | 免邮 | 金额 ≥ $50 | 运费为 $0 |
| `CATEGORY_DISCOUNT` | 品类折扣 | 品类 = "鞋类" | 9 折 |
| `PRODUCT_BUNDLE` | 商品组合 | 指定商品 A+B 同时购买 | 总价减 $20 |
| `NEW_USER` | 新用户专享 | 首次下单 | 减 $5 |
| `MEMBER_TIER` | 会员专属 | tier = GOLD | 额外 9.5 折 |
| `WALLET_REWARD` | 充值奖励 | Kafka 事件驱动 | 发放优惠券（现有逻辑） |
| `POINTS_MULTIPLIER` | 积分加倍 | 活动期间购物 | 积分 × 2 |
| `BIRTHDAY` | 生日礼 | 生日月下单 | 减 $8 |

---

## 三、数据库重设计（shop_promotion）

### 3.1 保留现有表（向后兼容）

```sql
-- 现有表保持不变，新增字段
ALTER TABLE promotion_offer
  ADD COLUMN type         VARCHAR(32)   NOT NULL DEFAULT 'WALLET_REWARD',
  ADD COLUMN conditions   JSON,           -- 条件配置（见下方 JSON Schema）
  ADD COLUMN benefits     JSON,           -- 权益配置
  ADD COLUMN stacking_policy VARCHAR(16) NOT NULL DEFAULT 'EXCLUSIVE',
  ADD COLUMN priority     INT           NOT NULL DEFAULT 0,
  ADD COLUMN start_at     TIMESTAMP(6),
  ADD COLUMN end_at       TIMESTAMP(6),
  ADD COLUMN usage_limit  INT,            -- NULL = 无限制
  ADD COLUMN usage_count  INT           NOT NULL DEFAULT 0,
  ADD COLUMN per_user_limit INT          NOT NULL DEFAULT 1;
```

### 3.2 条件 JSON Schema

```json
// conditions 字段格式
{
  "operator": "AND",            // AND / OR
  "rules": [
    {
      "type": "MIN_ORDER_AMOUNT",
      "value": 100.00
    },
    {
      "type": "USER_TIER",
      "value": ["GOLD", "PLATINUM"]
    },
    {
      "type": "TIME_WINDOW",
      "start": "2026-06-18T00:00:00Z",
      "end": "2026-06-18T23:59:59Z"
    },
    {
      "type": "CATEGORY",
      "value": ["electronics", "clothing"]
    },
    {
      "type": "PRODUCT_IDS",
      "value": ["prod-001", "prod-002"]
    }
  ]
}
```

### 3.3 权益 JSON Schema

```json
// benefits 字段格式
[
  {
    "type": "FIXED_AMOUNT_OFF",
    "value": 10.00,
    "apply_to": "ORDER_TOTAL"         // ORDER_TOTAL / SPECIFIC_ITEMS / SHIPPING
  },
  {
    "type": "PERCENTAGE_OFF",
    "value": 0.10,                    // 10% off
    "apply_to": "ORDER_TOTAL",
    "max_discount": 50.00             // 最多优惠 $50
  },
  {
    "type": "FREE_SHIPPING"
  },
  {
    "type": "FREE_ITEM",
    "product_id": "prod-gift-001",
    "quantity": 1
  },
  {
    "type": "BUY_X_GET_Y",
    "buy_quantity": 2,
    "get_quantity": 1,
    "get_rule": "CHEAPEST"            // CHEAPEST / SPECIFIC
  },
  {
    "type": "POINTS_MULTIPLIER",
    "multiplier": 2.0
  }
]
```

### 3.4 优惠券模板表（重构）

```sql
-- 将 coupon 拆分为 模板 + 实例
CREATE TABLE coupon_template (
    id              VARCHAR(36)   NOT NULL PRIMARY KEY,
    offer_id        VARCHAR(36)   NOT NULL,              -- 关联 promotion_offer
    name            VARCHAR(128)  NOT NULL,
    type            VARCHAR(32)   NOT NULL,              -- 同 promotion_offer.type
    face_value      DECIMAL(10,2),                       -- 面额（展示用）
    valid_days      INT           NOT NULL DEFAULT 30,   -- 领取后有效天数
    total_quota     INT           NOT NULL DEFAULT 1000, -- 总发行量
    issued_count    INT           NOT NULL DEFAULT 0,
    created_at      TIMESTAMP(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6)
);

-- 用户持有的优惠券实例
CREATE TABLE coupon_instance (
    id              VARCHAR(36)   NOT NULL PRIMARY KEY,
    template_id     VARCHAR(36)   NOT NULL,
    player_id       VARCHAR(64)   NOT NULL,
    code            VARCHAR(64)   NOT NULL UNIQUE,       -- 唯一券码
    status          VARCHAR(20)   NOT NULL DEFAULT 'AVAILABLE',  -- AVAILABLE/USED/EXPIRED
    source          VARCHAR(32)   NOT NULL,              -- CHECKIN_REWARD / REDEMPTION / CAMPAIGN / ADMIN
    expire_at       TIMESTAMP(6)  NOT NULL,
    used_at         TIMESTAMP(6),
    order_id        VARCHAR(36),                         -- 使用的订单 ID
    created_at      TIMESTAMP(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    INDEX idx_player_status (player_id, status),
    INDEX idx_code (code)
);
```

---

## 四、促销引擎实现

### 4.1 核心接口定义

```java
// ConditionEvaluator.java
public interface ConditionEvaluator {
    String supportedType();
    boolean evaluate(ConditionRule rule, PromotionContext context);
}

// BenefitCalculator.java
public interface BenefitCalculator {
    String supportedType();
    BenefitResult calculate(BenefitConfig config, PromotionContext context);
}

// PromotionContext.java — 携带所有判断所需的上下文
public record PromotionContext(
    String playerId,
    String userTier,         // SILVER / GOLD / PLATINUM
    boolean isFirstOrder,
    boolean isBirthdayMonth,
    BigDecimal orderTotal,
    List<CartItem> items,
    String couponCode,       // 用户输入的优惠码
    Instant requestTime
) {}

// BenefitResult.java
public record BenefitResult(
    String offerId,
    String offerName,
    BigDecimal discountAmount,
    boolean freeShipping,
    List<FreeItem> freeItems,
    double pointsMultiplier
) {}
```

### 4.2 引擎主流程

```java
@Service
public class PromotionEngine {

    // Spring 自动注入所有 ConditionEvaluator 实现（每种类型一个 Bean）
    private final Map<String, ConditionEvaluator> evaluators;
    private final Map<String, BenefitCalculator> calculators;
    private final PromotionOfferRepository offerRepository;

    public PromotionCalculationResult calculate(PromotionContext ctx) {
        // 1. 查询当前生效的所有活动（时间窗口内，active=true）
        List<PromotionOfferEntity> activeOffers = offerRepository.findActiveOffers(Instant.now());

        // 2. 筛选满足条件的活动
        List<PromotionOfferEntity> eligibleOffers = activeOffers.stream()
            .filter(offer -> evaluateConditions(offer, ctx))
            .sorted(Comparator.comparingInt(PromotionOfferEntity::getPriority).reversed())
            .toList();

        // 3. 根据叠加规则确定最终生效活动
        List<PromotionOfferEntity> appliedOffers = applyStackingPolicy(eligibleOffers);

        // 4. 计算折扣
        List<BenefitResult> results = appliedOffers.stream()
            .flatMap(offer -> offer.getBenefits().stream()
                .map(b -> calculators.get(b.type()).calculate(b, ctx)))
            .toList();

        // 5. 汇总
        return aggregate(results);
    }

    private boolean evaluateConditions(PromotionOfferEntity offer, PromotionContext ctx) {
        ConditionGroup group = offer.getConditions();
        if (group == null) return true;

        List<Boolean> results = group.rules().stream()
            .map(rule -> evaluators.get(rule.type()).evaluate(rule, ctx))
            .toList();

        return "AND".equals(group.operator())
            ? results.stream().allMatch(Boolean::booleanValue)
            : results.stream().anyMatch(Boolean::booleanValue);
    }

    private List<PromotionOfferEntity> applyStackingPolicy(List<PromotionOfferEntity> offers) {
        // EXCLUSIVE：只取第一个（优先级最高）
        // ADDITIVE：全部生效
        // PRIORITY：按优先级依次应用，直到折扣达到上限
        // 实现略（根据 stacking_policy 字段路由）
    }
}
```

### 4.3 具体 Evaluator 实现示例

```java
@Component
public class MinOrderAmountEvaluator implements ConditionEvaluator {
    @Override
    public String supportedType() { return "MIN_ORDER_AMOUNT"; }

    @Override
    public boolean evaluate(ConditionRule rule, PromotionContext ctx) {
        BigDecimal threshold = new BigDecimal(rule.value().toString());
        return ctx.orderTotal().compareTo(threshold) >= 0;
    }
}

@Component
public class TimeWindowEvaluator implements ConditionEvaluator {
    @Override
    public String supportedType() { return "TIME_WINDOW"; }

    @Override
    public boolean evaluate(ConditionRule rule, PromotionContext ctx) {
        Instant start = Instant.parse(rule.start());
        Instant end   = Instant.parse(rule.end());
        return !ctx.requestTime().isBefore(start) && !ctx.requestTime().isAfter(end);
    }
}

@Component
public class PercentageBenefitCalculator implements BenefitCalculator {
    @Override
    public String supportedType() { return "PERCENTAGE_OFF"; }

    @Override
    public BenefitResult calculate(BenefitConfig config, PromotionContext ctx) {
        BigDecimal rate = BigDecimal.valueOf(config.value());                   // e.g. 0.10
        BigDecimal discount = ctx.orderTotal().multiply(rate);
        if (config.maxDiscount() != null) {
            discount = discount.min(BigDecimal.valueOf(config.maxDiscount()));
        }
        return BenefitResult.discount(config.offerId(), discount);
    }
}
```

---

## 五、API 重设计

### 5.1 购物车促销计算（关键新接口）

```
POST /promotion/v1/calculate
Body:
{
  "player_id": "player-1001",
  "coupon_code": "SAVE10",        // 可选，用户输入的优惠码
  "items": [
    { "product_id": "prod-001", "category": "electronics", "price": 89.90, "quantity": 2 }
  ],
  "shipping_fee": 5.99
}

Response:
{
  "original_total": 179.80,
  "discount_breakdown": [
    {
      "offer_id": "offer-xxx",
      "offer_name": "满150减20",
      "discount_amount": 20.00,
      "type": "THRESHOLD_AMOUNT"
    }
  ],
  "shipping_discount": 5.99,      // 满额免邮
  "final_total": 153.81,
  "applied_coupons": ["SAVE10"],
  "points_multiplier": 1.0,       // 积分加倍活动时 > 1.0
  "eligible_but_not_applied": [   // 未生效（叠加限制）的优惠
    { "offer_id": "...", "reason": "STACKING_NOT_ALLOWED" }
  ]
}
```

### 5.2 优惠券 API

```
GET  /promotion/v1/coupons                    # 我的优惠券列表
GET  /promotion/v1/coupons?status=AVAILABLE   # 可用优惠券
POST /promotion/v1/coupons/verify             # 验证优惠码（结账前校验）
POST /promotion/v1/coupons/{id}/use           # 核销优惠券（下单时）

# 内部接口
POST /internal/promotion/coupons/issue        # 由 loyalty-service / 活动触发发券
POST /internal/promotion/coupons/rollback     # 下单失败时回滚已核销的优惠券
```

### 5.3 活动管理（卖家/运营）

```
# 卖家（通过 seller-bff 路由）
GET  /promotion/v1/offers                     # 活动列表
POST /promotion/v1/offers                     # 创建活动（body 含 conditions+benefits JSON）
PUT  /promotion/v1/offers/{id}                # 更新活动
DELETE /promotion/v1/offers/{id}              # 下架活动

# 买家（公开，通过 buyer-bff 路由）
GET  /promotion/v1/offers/active              # 当前有效活动（首页展示）
GET  /promotion/v1/offers/{id}                # 活动详情
```

---

## 六、新用户注册福利集成

### 6.1 PromotionOnboardingListener

promotion-service 独立消费 `user.registered.v1`，无需 loyalty-service 调用：

```java
// PromotionOnboardingListener.java

@KafkaListener(topics = "user.registered.v1")
public void onUserRegistered(EventEnvelope<UserRegisteredEventData> event) {
    String playerId = event.data().playerId();
    String referrerId = event.data().referrerId();

    // 发放新人礼包：3 张优惠券
    couponService.issueToPlayer(playerId, "WELCOME-5-NODOOR");   // $5 无门槛 14 天
    couponService.issueToPlayer(playerId, "WELCOME-FREESHIP");   // 免邮 30 天
    couponService.issueToPlayer(playerId, "WELCOME-90PCT");      // 9折 max$20 14天

    // 邀请人奖励（好友注册即发 $2 额外券；首单完成后再发积分，由 loyalty-service 处理）
    if (referrerId != null && referralGuard.isEligible(referrerId)) {
        couponService.issueToPlayer(referrerId, "REFERRAL-BONUS-2");
    }
}
```

### 6.2 新人优惠券模板配置

```sql
-- coupon_template 初始化数据
INSERT INTO coupon_template VALUES
('WELCOME-5-NODOOR',  '新人专享 $5 无门槛券',      'FIXED_AMOUNT',  5.00,  0,     14, 1),
('WELCOME-FREESHIP',  '新人免邮券',                 'FREE_SHIPPING', NULL,  NULL,  30, 1),
('WELCOME-90PCT',     '新人 9折券（最高减 $20）',   'PERCENTAGE',    0.90,  20.00, 14, 1),
('REFERRAL-BONUS-2',  '邀请好友额外 $2 无门槛券',   'FIXED_AMOUNT',  2.00,  0,     30, 1);
-- (id, name, type, value, max_discount, valid_days, active)
```

### 6.3 NewUserConditionEvaluator（首单专属活动条件）

支持促销引擎中 `IS_FIRST_ORDER` 条件类型，用于配置"首单减 $5"等活动：

```java
@Component
public class NewUserConditionEvaluator implements ConditionEvaluator {

    private final OrderClient orderClient;

    @Override
    public String supportedType() { return "IS_FIRST_ORDER"; }

    @Override
    public boolean evaluate(ConditionRule rule, PromotionContext ctx) {
        if (ctx.playerId() == null) return false;  // 游客不享受首单优惠
        return ctx.isFirstOrder();                 // PromotionContext 由 buyer-bff 注入
    }
}
```

> `PromotionContext.isFirstOrder` 由 buyer-bff 在调用 `/promotion/v1/calculate` 前查询 order-service 历史订单数注入，promotion-service 自身无需调用 order-service。

---

## 七、现有 WalletRewardListener 保留

```java
// WalletRewardListener.java（现有代码保持不变）
@KafkaListener(topics = "${shop.wallet-topic}")
public void onWalletTransaction(EventEnvelope<WalletTransactionEventData> event) {
    if (!"DEPOSIT".equals(event.data().type())) return;

    String code = "WALLET-REWARD-" + event.data().transactionId();
    BigDecimal rewardAmount = calculateReward(event.data().amount());

    // 沿用现有逻辑：创建 WALLET_REWARD 类型的 promotion_offer
    // 同时通过新的 coupon_template 给用户发 coupon_instance
    promotionApplicationService.createWalletRewardOffer(code, "充值奖励", "...", rewardAmount);
    couponService.issueToPlayer(event.data().playerId(), code);  // 新增发券逻辑
}
```

---

## 八、新增促销类型扩展指南

> 增加新促销类型只需三步，无需修改 PromotionEngine 核心代码

**步骤 1**：新增 Condition 规则（如需要）

```java
@Component
public class UserReferralEvaluator implements ConditionEvaluator {
    @Override
    public String supportedType() { return "IS_REFERRED_USER"; }

    @Override
    public boolean evaluate(ConditionRule rule, PromotionContext ctx) {
        return profileClient.isReferredUser(ctx.playerId());
    }
}
```

**步骤 2**：新增 Benefit 计算器（如需要）

```java
@Component
public class CashbackBenefitCalculator implements BenefitCalculator {
    @Override
    public String supportedType() { return "CASHBACK"; }

    @Override
    public BenefitResult calculate(BenefitConfig config, PromotionContext ctx) {
        // 返现逻辑：下单后返回钱包
        BigDecimal cashback = ctx.orderTotal().multiply(BigDecimal.valueOf(config.value()));
        return BenefitResult.cashback(config.offerId(), cashback);
    }
}
```

**步骤 3**：在管理后台配置活动 JSON，无需部署代码

---

## 九、Kafka 事件

| Topic 发布 | 触发时机 | 消费方 |
|-----------|---------|--------|
| `promotion.coupon.issued.v1` | 优惠券发放 | notification-service |
| `promotion.coupon.used.v1` | 优惠券核销 | 数据分析 |
| `promotion.offer.created.v1` | 新活动上线 | 数据分析 |

| Topic 消费 | 来源 | 处理 |
|-----------|------|------|
| `user.registered.v1` | profile-service | 发放新人礼包优惠券（3 张）+ 邀请人奖励券 |
| `wallet.transactions.v1` | wallet-service | 自动发放充值奖励（现有） |
| `order.events.v1 (COMPLETED)` | order-service | 满足购买条件后发券 |
| `loyalty.redemption.v1` | loyalty-service | COUPON 类型兑换时生成优惠券实例 |

---

## 十、与现有代码的迁移路径

```
Phase 1（当前 Sprint）：
  ✓ 保留现有 promotion_offer 表和逻辑不变
  ✓ 新增 coupon_template / coupon_instance 表
  ✓ WalletRewardListener 补充发券到 coupon_instance

Phase 2：
  + ALTER promotion_offer 表，新增 conditions/benefits JSON 列
  + 实现 PromotionEngine（Evaluators + Calculators）
  + 新增 POST /promotion/v1/calculate 接口
  + buyer-bff 结账流程调用此接口

Phase 3：
  + 活动管理 UI（seller-portal）
  + 秒杀活动（FlashSale + Redis 库存计数）
  + 积分加倍活动（与 loyalty-service 联动）
```
