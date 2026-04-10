---
title: 钱包服务设计文档
---

# wallet-service — 钱包服务设计文档

> 版本：1.0 | 日期：2026-03-20 | 基于现有代码整理与扩展

---

## 一、服务定位

```
wallet-service（:8086）专注于"真实货币"资产管理：

职责范围（IN）：
  ✅ 钱包账户（余额）
  ✅ 充值（Stripe 信用卡）
  ✅ 提现（Stripe 转账）
  ✅ 订单支付（余额扣减）
  ✅ 退款（余额退还）
  ✅ 交易流水
  ✅ Outbox Pattern 发布交易事件

职责范围（OUT，归其他服务）：
  ❌ 积分 → loyalty-service
  ❌ 优惠券 → promotion-service
  ❌ 会员等级 → loyalty-service（tier_points 驱动）
```

**关键原则**：wallet-service 是金融属性服务，处理真实货币，需要严格的事务一致性和幂等性。积分是"虚拟货币"，规则更灵活，独立于 wallet-service。

---

## 二、现有代码结构

```
wallet-service/src/main/java/dev/meirong/shop/wallet/
├── WalletServiceApplication.java
├── controller/
│   └── WalletController.java            # REST 接口
├── service/
│   ├── WalletApplicationService.java    # 业务逻辑（充值/提现/支付/退款）
│   ├── WalletOutboxPublisher.java       # Outbox 定时发布（5s 轮询）
│   ├── StripeGateway.java               # Stripe 接口抽象
│   ├── StripePaymentGateway.java        # Stripe 真实实现
│   └── MockPaymentGateway.java          # 测试 Mock
├── domain/
│   ├── WalletAccountEntity.java         # 钱包账户（余额）
│   ├── WalletTransactionEntity.java     # 交易记录
│   ├── WalletOutboxEventEntity.java     # Outbox 事件表
│   └── *Repository.java
└── config/
    └── WalletProperties.java            # kafka topic 配置
```

---

## 三、数据库设计（shop_wallet）

### 3.1 现有表（保留）

```sql
-- 钱包账户（已存在）
CREATE TABLE wallet_account (
    buyer_id   VARCHAR(64)    NOT NULL PRIMARY KEY,
    balance     DECIMAL(19,2)  NOT NULL DEFAULT 0.00,
    updated_at  TIMESTAMP(6)   NOT NULL DEFAULT CURRENT_TIMESTAMP(6)
);

-- 交易流水（已存在，扩展字段）
CREATE TABLE wallet_transaction (
    id                VARCHAR(36)    NOT NULL PRIMARY KEY,
    buyer_id         VARCHAR(64)    NOT NULL,
    type              VARCHAR(32)    NOT NULL,  -- DEPOSIT/WITHDRAW/ORDER_PAYMENT/ORDER_REFUND/ADJUSTMENT
    amount            DECIMAL(19,2)  NOT NULL,
    currency          VARCHAR(8)     NOT NULL DEFAULT 'USD',
    status            VARCHAR(20)    NOT NULL,  -- PENDING/COMPLETED/FAILED
    provider_reference VARCHAR(256),            -- Stripe charge_id / transfer_id
    reference_id      VARCHAR(64),              -- order_id（支付/退款时）
    reference_type    VARCHAR(32),              -- ORDER / WITHDRAWAL
    balance_after     DECIMAL(19,2),            -- 【新增】事后余额快照（对账用）
    created_at        TIMESTAMP(6)   NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    INDEX idx_player_created (buyer_id, created_at DESC)
);

-- Outbox 事件表（已存在）
CREATE TABLE wallet_outbox_event (
    id              BIGINT         NOT NULL AUTO_INCREMENT PRIMARY KEY,
    transaction_id  VARCHAR(36)    NOT NULL,
    topic           VARCHAR(128)   NOT NULL,
    event_type      VARCHAR(64)    NOT NULL,
    payload         TEXT           NOT NULL,
    status          VARCHAR(20)    NOT NULL DEFAULT 'PENDING',  -- PENDING/PUBLISHED
    created_at      TIMESTAMP(6)   NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    published_at    TIMESTAMP(6),
    INDEX idx_status (status)
);
```

### 3.2 新增表（扩展）

```sql
-- 支付方式（绑定信用卡，避免每次都输卡号）
CREATE TABLE wallet_payment_method (
    id                VARCHAR(36)   NOT NULL PRIMARY KEY,
    buyer_id         VARCHAR(64)   NOT NULL,
    type              VARCHAR(32)   NOT NULL,   -- CARD / APPLE_PAY / GOOGLE_PAY / PAYPAL
    provider          VARCHAR(32)   NOT NULL,   -- STRIPE / PAYPAL
    provider_method_id VARCHAR(256) NOT NULL,   -- Stripe PaymentMethod ID
    card_brand        VARCHAR(32),              -- VISA / MASTERCARD
    card_last4        VARCHAR(4),
    card_exp_month    INT,
    card_exp_year     INT,
    is_default        TINYINT(1)    NOT NULL DEFAULT 0,
    created_at        TIMESTAMP(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    INDEX idx_player (buyer_id)
);

-- 幂等键（防止重复支付）
CREATE TABLE wallet_idempotency_key (
    idempotency_key  VARCHAR(128)  NOT NULL PRIMARY KEY,
    transaction_id   VARCHAR(36)   NOT NULL,
    created_at       TIMESTAMP(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6)
);
```

---

## 四、业务逻辑

### 4.1 现有操作（已实现）

| 操作 | 方法 | 说明 |
|------|------|------|
| 查询钱包 | `getWallet()` | 返回余额 + 最近 10 笔流水 |
| 充值 | `deposit()` | 调用 Stripe，credit 余额，写 Outbox |
| 提现 | `withdraw()` | 校验余额 ≥ 提现额，调用 Stripe，debit 余额 |
| 订单支付 | `payForOrder()` | 余额扣减，关联 order_id |
| 订单退款 | `refundOrder()` | 余额退还，关联 order_id |

### 4.2 新增操作

#### 幂等充值（防重复）

```java
@Transactional
public TransactionResponse deposit(DepositRequest request) {
    // 幂等检查
    if (request.idempotencyKey() != null) {
        Optional<WalletIdempotencyKey> existing = idempotencyRepo.findById(request.idempotencyKey());
        if (existing.isPresent()) {
            return transactionRepository.findById(existing.get().transactionId())
                .map(this::toTransactionResponse)
                .orElseThrow();
        }
    }

    // ... 现有充值逻辑 ...

    // 记录幂等键（与事务同步提交）
    if (request.idempotencyKey() != null) {
        idempotencyRepo.save(new WalletIdempotencyKey(request.idempotencyKey(), transaction.getId()));
    }
    return toTransactionResponse(transaction);
}
```

#### Stripe Webhook 处理（异步支付确认）

```java
// 部分支付方式（BNPL、银行转账）是异步确认的
@PostMapping("/internal/wallet/webhook/stripe")
public void handleStripeWebhook(@RequestBody String payload,
                                 @RequestHeader("Stripe-Signature") String sig) {
    Event event = stripeGateway.parseWebhook(payload, sig);

    switch (event.getType()) {
        case "payment_intent.succeeded" -> confirmPendingDeposit(event);
        case "payment_intent.payment_failed" -> failPendingDeposit(event);
        case "transfer.created" -> confirmWithdrawal(event);
    }
}
```

#### 绑定支付方式

```java
POST /wallet/v1/payment-methods
Body: { "stripe_setup_intent_id": "seti_xxx" }
// 调用 Stripe API 获取 PaymentMethod 详情，存入 wallet_payment_method

GET  /wallet/v1/payment-methods
// 返回用户绑定的卡列表（仅返回 last4，不返回完整卡号）
```

---

## 五、API 设计

```
# 买家（通过 buyer-bff 聚合，Gateway 路由）
GET  /wallet/v1/account                          # 钱包概览（余额 + 最近流水）
GET  /wallet/v1/transactions?page=0&size=20      # 完整流水列表
POST /wallet/v1/deposit                          # 充值
POST /wallet/v1/withdraw                         # 提现
GET  /wallet/v1/payment-methods                  # 已绑定支付方式
POST /wallet/v1/payment-methods                  # 绑定新支付方式
DELETE /wallet/v1/payment-methods/{id}           # 解绑

# 内部接口（X-Internal-Token，服务间调用）
POST /internal/wallet/pay                        # order-service 调用扣款
POST /internal/wallet/refund                     # order-service 调用退款
GET  /internal/wallet/account/{buyerId}         # buyer-bff 聚合查询

# Webhook（Stripe 回调，不走 Gateway）
POST /internal/wallet/webhook/stripe
```

---

## 六、Outbox 发布的 Kafka 事件

```
Topic: wallet.transactions.v1

EventEnvelope<WalletTransactionEventData> {
  id:        "evt-uuid",
  source:    "wallet-service",
  type:      "wallet.transaction.completed",
  timestamp: "2026-03-20T10:00:00Z",
  data: {
    transaction_id:   "txn-uuid",
    buyer_id:        "buyer-1001",
    type:             "DEPOSIT",          // DEPOSIT/WITHDRAW/ORDER_PAYMENT/ORDER_REFUND
    amount:           100.00,
    currency:         "USD",
    status:           "COMPLETED",
    created_at:       "2026-03-20T10:00:00Z"
  }
}

消费方：
  → promotion-service：充值触发奖励券（现有）
  → loyalty-service：首次充值发积分（规划）
  → notification-service：发送支付确认邮件（规划）
```

---

## 七、安全考量

| 风险 | 措施 |
|------|------|
| 重复充值 | 幂等键 + wallet_idempotency_key 表 |
| 超卖余额 | `SELECT FOR UPDATE` 锁行 + 余额校验 |
| Stripe 回调伪造 | Stripe-Signature HMAC 验证 |
| 直接调用内部接口 | X-Internal-Token 中间件校验 |
| 敏感数据泄露 | 只存 card_last4，不存完整卡号 |
| 金额精度 | DECIMAL(19,2)，Java BigDecimal，严禁 float/double |

---

## 八、与 loyalty-service 的协作

```
wallet-service 发布事件 →（Kafka）→ loyalty-service 消费

wallet-service 不直接感知积分：
  ✓ 充值成功 → 发布 wallet.transaction.completed（type=DEPOSIT）
  ✓ loyalty-service 监听 → 按规则发放积分
  ✓ 解耦，wallet-service 无需知道 loyalty-service 存在
```
