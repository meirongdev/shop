# order-service — 订单服务设计文档

> 版本：1.1 | 日期：2026-03-20 | 状态：核心已实现（状态机 + Outbox + 超时取消 + 自动确认）

---

## 一、服务定位

```
order-service（:8085）

职责：
  1. 订单创建    — 注册用户 / 游客 / 积分兑换三种来源
  2. 订单状态机  — PENDING → PAID → SHIPPED → COMPLETED
  3. 发货管理    — 录入运单号、对接物流查询
  4. 退款申请    — 发起退款流程，通知 wallet-service
  5. 超时取消    — 未付款订单 30 分钟自动取消
  6. 事件发布    — Outbox Pattern 向 Kafka 发布订单事件
```

---

## 二、数据库设计（shop_order）

### 2.1 订单主表

```sql
CREATE TABLE `order` (
    id                VARCHAR(36)    NOT NULL PRIMARY KEY,
    order_no          VARCHAR(32)    NOT NULL UNIQUE,        -- 业务单号 ORD-2026-00123456
    type              VARCHAR(32)    NOT NULL DEFAULT 'STANDARD',  -- STANDARD/GUEST/POINTS_REDEMPTION
    player_id         VARCHAR(64),                            -- 注册用户 ID（GUEST 为 NULL）
    guest_email       VARCHAR(256),                           -- GUEST 类型专用
    order_token       VARCHAR(64),                            -- GUEST 追踪 token
    status            VARCHAR(32)    NOT NULL DEFAULT 'PENDING_PAYMENT',
    subtotal          DECIMAL(19,2)  NOT NULL,
    shipping_fee      DECIMAL(19,2)  NOT NULL DEFAULT 0.00,
    discount_amount   DECIMAL(19,2)  NOT NULL DEFAULT 0.00,
    total_amount      DECIMAL(19,2)  NOT NULL,
    currency          VARCHAR(8)     NOT NULL DEFAULT 'USD',
    payment_method    VARCHAR(32),        -- WALLET / CARD / PAYPAL
    payment_reference VARCHAR(256),       -- Stripe payment_intent_id
    coupon_codes      JSON,               -- 使用的优惠券码列表
    points_used       BIGINT         NOT NULL DEFAULT 0,      -- 积分抵扣
    shipping_name     VARCHAR(128),
    shipping_phone    VARCHAR(32),
    shipping_address  JSON           NOT NULL,
    seller_id         VARCHAR(64),        -- 单一卖家（多卖家拆单扩展时使用）
    remark            VARCHAR(512),
    paid_at           TIMESTAMP(6),
    shipped_at        TIMESTAMP(6),
    delivered_at      TIMESTAMP(6),
    completed_at      TIMESTAMP(6),
    cancelled_at      TIMESTAMP(6),
    cancel_reason     VARCHAR(256),
    created_at        TIMESTAMP(6)   NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at        TIMESTAMP(6)   NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    INDEX idx_player_created (player_id, created_at DESC),
    INDEX idx_order_token (order_token),
    INDEX idx_status (status),
    INDEX idx_seller (seller_id)
);
```

### 2.2 订单明细表

```sql
CREATE TABLE order_item (
    id              VARCHAR(36)    NOT NULL PRIMARY KEY,
    order_id        VARCHAR(36)    NOT NULL,
    product_id      VARCHAR(36)    NOT NULL,
    sku             VARCHAR(64)    NOT NULL,
    product_name    VARCHAR(256)   NOT NULL,   -- 快照（防商品修改）
    product_image   VARCHAR(512),
    unit_price      DECIMAL(19,2)  NOT NULL,   -- 快照
    quantity        INT            NOT NULL,
    subtotal        DECIMAL(19,2)  NOT NULL,
    discount        DECIMAL(19,2)  NOT NULL DEFAULT 0.00,
    FOREIGN KEY (order_id) REFERENCES `order`(id)
);
```

### 2.3 物流信息表

```sql
CREATE TABLE order_shipment (
    id              VARCHAR(36)   NOT NULL PRIMARY KEY,
    order_id        VARCHAR(36)   NOT NULL UNIQUE,
    carrier         VARCHAR(64),               -- UPS / FEDEX / SHUNFENG
    tracking_no     VARCHAR(128),
    tracking_url    VARCHAR(512),
    estimated_delivery DATE,
    actual_delivery TIMESTAMP(6),
    status          VARCHAR(32)   NOT NULL DEFAULT 'PENDING',  -- PENDING/SHIPPED/IN_TRANSIT/DELIVERED
    raw_events      JSON,                      -- 物流轨迹原始数据
    updated_at      TIMESTAMP(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    FOREIGN KEY (order_id) REFERENCES `order`(id)
);
```

### 2.4 退款表

```sql
CREATE TABLE order_refund (
    id              VARCHAR(36)   NOT NULL PRIMARY KEY,
    order_id        VARCHAR(36)   NOT NULL,
    refund_no       VARCHAR(32)   NOT NULL UNIQUE,
    reason          VARCHAR(512)  NOT NULL,
    amount          DECIMAL(19,2) NOT NULL,
    status          VARCHAR(32)   NOT NULL DEFAULT 'PENDING',  -- PENDING/APPROVED/REJECTED/COMPLETED
    requester       VARCHAR(64),   -- player_id 或 'SYSTEM'
    reviewer        VARCHAR(64),   -- 审核人（卖家/平台）
    review_remark   VARCHAR(512),
    refund_method   VARCHAR(32),   -- WALLET / ORIGINAL_PAYMENT
    completed_at    TIMESTAMP(6),
    created_at      TIMESTAMP(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    FOREIGN KEY (order_id) REFERENCES `order`(id)
);
```

### 2.5 Outbox 事件表

```sql
CREATE TABLE order_outbox_event (
    id              BIGINT        NOT NULL AUTO_INCREMENT PRIMARY KEY,
    order_id        VARCHAR(36)   NOT NULL,
    topic           VARCHAR(128)  NOT NULL,
    event_type      VARCHAR(64)   NOT NULL,
    payload         TEXT          NOT NULL,
    status          VARCHAR(20)   NOT NULL DEFAULT 'PENDING',
    created_at      TIMESTAMP(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    published_at    TIMESTAMP(6),
    INDEX idx_status (status)
);
```

---

## 三、订单状态机

```
                    ┌─────────────────────────────┐
                    │                             │
              [创建订单]                     [超时 30min]
                    │                             │
                    ▼                             │
          PENDING_PAYMENT ──────────────────→ CANCELLED
                    │
           [支付成功 Webhook]
                    │
                    ▼
                  PAID
                    │
          [商家确认接单]
                    │
                    ▼
              PROCESSING ──────────── [用户申请退款] ──→ REFUND_REQUESTED
                    │                                          │
          [商家录入运单号]                          [商家同意/拒绝/平台介入]
                    │                                          │
                    ▼                               REFUND_APPROVED / REFUND_REJECTED
                SHIPPED
                    │
          [物流签收 / 用户确认]
                    │
                    ▼
               DELIVERED
                    │
          [7天后自动 / 用户点击确认收货]
                    │
                    ▼
               COMPLETED ←──── 终态：触发购物积分发放事件
```

### 状态转换规则

| 当前状态 | 触发事件 | 新状态 | 发布事件 |
|---------|---------|--------|---------|
| PENDING_PAYMENT | payment.succeeded | PAID | order.paid.v1 |
| PENDING_PAYMENT | 超时 30min | CANCELLED | order.cancelled.v1 |
| PAID | seller confirms | PROCESSING | - |
| PROCESSING | refund request | REFUND_REQUESTED | order.refund_requested.v1 |
| PROCESSING | ship | SHIPPED | order.shipped.v1 |
| SHIPPED | delivered | DELIVERED | order.delivered.v1 |
| DELIVERED | confirm / 7d timeout | COMPLETED | order.completed.v1 |

---

## 四、订单创建流程

### 4.1 标准用户下单

```java
@Transactional
public CreateOrderResponse createOrder(CreateOrderRequest req) {
    // 1. 校验库存（调用 marketplace-service）
    inventoryClient.lockInventory(req.items());

    // 2. 计算促销（调用 promotion-service）
    PromotionResult promo = promotionClient.calculate(req.playerId(), req.items(), req.couponCode());

    // 3. 积分抵扣（如有，调用 loyalty-service 预锁定）
    long pointsToDeduct = req.pointsToUse();
    if (pointsToDeduct > 0) {
        loyaltyClient.lockPoints(req.playerId(), pointsToDeduct, "ORDER_PENDING");
    }

    // 4. 创建订单（DB 事务内）
    OrderEntity order = new OrderEntity(
        type = STANDARD,
        playerId = req.playerId(),
        items = req.items(),
        subtotal = promo.originalTotal(),
        discountAmount = promo.discountAmount(),
        totalAmount = promo.finalTotal() - pointsValue(pointsToDeduct),
        shippingAddress = req.shippingAddress(),
        couponCodes = promo.appliedCoupons(),
        pointsUsed = pointsToDeduct
    );
    orderRepository.save(order);
    saveItems(order, req.items(), promo);

    // 5. 写 Outbox 事件
    publishOutboxEvent(order, "order.created.v1");

    return new CreateOrderResponse(order.getId(), order.getOrderNo(), order.getTotalAmount());
}
```

### 4.2 游客下单

```java
@Transactional
public CreateOrderResponse createGuestOrder(CreateGuestOrderRequest req) {
    // 与标准下单基本相同，差异点：
    //   - type = GUEST
    //   - player_id = NULL
    //   - guest_email = req.email()
    //   - order_token = UUID.randomUUID().toString()  ← 游客追踪 token
    //   - 不支持积分抵扣（游客无积分账户）
    //   - 优惠码支持（promotion-service 不校验用户 ID）
}
```

### 4.3 积分兑换实物下单

```java
// 由 loyalty-service 调用（内部接口）
@PostMapping("/internal/orders/redemption")
public CreateOrderResponse createRedemptionOrder(CreateRedemptionOrderRequest req) {
    // type = POINTS_REDEMPTION
    // total_amount = 0（全积分支付，无实际货币）
    // payment_method = POINTS
    // 状态直接设为 PAID（积分已在 loyalty-service 扣除）
}
```

---

## 五、超时取消（定时任务）

```java
@Scheduled(fixedDelay = 60_000)   // 每分钟检查
@Transactional
public void cancelExpiredOrders() {
    Instant threshold = Instant.now().minus(30, MINUTES);
    List<OrderEntity> expired = orderRepository
        .findByStatusAndCreatedAtBefore("PENDING_PAYMENT", threshold);

    for (OrderEntity order : expired) {
        order.setStatus("CANCELLED");
        order.setCancelReason("Payment timeout - auto cancelled");
        orderRepository.save(order);

        // 释放锁定库存
        inventoryClient.releaseInventory(order.getId());

        // 释放锁定积分（如有）
        if (order.getPointsUsed() > 0) {
            loyaltyClient.releasePoints(order.getPlayerId(), order.getPointsUsed());
        }

        // Outbox 事件
        publishOutboxEvent(order, "order.cancelled.v1");
    }
}
```

### 5.1 多实例定时任务协调（2026-03-23）

当前实际实现中，`OrderScheduler` 已通过 Redisson 为两个批处理入口加分布式锁：

- `shop:order:scheduler:cancel-expired`
- `shop:order:scheduler:auto-complete`

目的不是替代数据库事务，而是防止多实例部署下多个节点同时执行同一批状态迁移：

- 重复超时取消
- 重复自动确认收货
- 重复写 Outbox 事件

锁获取失败时会记录日志并跳过当前轮次，由其他实例继续执行该批处理。

---

## 六、物流集成

```java
// 物流查询（轮询更新）
@Scheduled(fixedDelay = 300_000)   // 每 5 分钟
public void syncShipmentStatus() {
    List<OrderShipmentEntity> inTransit = shipmentRepository.findByStatus("IN_TRANSIT");
    for (OrderShipmentEntity shipment : inTransit) {
        TrackingResult result = logisticsClient.track(shipment.getCarrier(), shipment.getTrackingNo());
        if (result.isDelivered()) {
            shipment.setStatus("DELIVERED");
            shipment.setActualDelivery(result.deliveredAt());
            updateOrderStatus(shipment.getOrderId(), "DELIVERED");
        }
        shipment.setRawEvents(result.rawEvents());
        shipmentRepository.save(shipment);
    }
}
```

---

## 七、API 设计

```
# 买家（通过 buyer-bff）
POST /order/v1/orders                       # 创建订单（注册用户）
POST /order/v1/orders/guest                 # 游客下单
GET  /order/v1/orders                       # 我的订单列表
GET  /order/v1/orders/{id}                  # 订单详情
POST /order/v1/orders/{id}/cancel           # 取消订单
POST /order/v1/orders/{id}/confirm          # 确认收货
GET  /order/v1/orders/track?token={token}   # 游客追踪（无需 JWT）
POST /order/v1/orders/{id}/refunds          # 申请退款

# 卖家（通过 seller-bff）
GET  /order/v1/seller/orders                # 卖家订单列表
POST /order/v1/seller/orders/{id}/ship      # 发货（录入运单号）
POST /order/v1/seller/orders/{id}/refunds/{refundId}/review  # 审核退款

# 内部接口（X-Internal-Token）
POST /internal/orders/payment-confirm       # 支付成功回调（wallet-service 调用）
POST /internal/orders/redemption            # loyalty-service 调用创建兑换订单
GET  /internal/orders/{id}/summary          # BFF 聚合查询
```

---

## 八、Kafka 事件（Outbox 发布）

| Event Type | 触发时机 | 消费方 |
|-----------|---------|--------|
| `order.created.v1` | 订单创建 | 无（内部通知） |
| `order.paid.v1` | 支付成功 | wallet-service（确认扣款）、notification-service |
| `order.shipped.v1` | 商家发货 | notification-service（通知买家） |
| `order.delivered.v1` | 物流签收 | 无（可触发自动确认倒计时） |
| `order.completed.v1` | 订单完成 | loyalty-service（发放购物积分）、promotion-service |
| `order.cancelled.v1` | 订单取消 | wallet-service（退款）、loyalty-service（释放积分） |
| `order.refund_requested.v1` | 申请退款 | notification-service（通知卖家） |
| `order.refunded.v1` | 退款完成 | notification-service（通知买家） |

---

## 九、幂等性设计

```
支付回调的幂等处理：
  POST /internal/orders/payment-confirm
  Header: Idempotency-Key: {stripe_payment_intent_id}

  处理逻辑：
    1. 检查 order.payment_reference = stripe_payment_intent_id
    2. 若 order.status 已是 PAID → 直接返回成功（幂等）
    3. 若 order.status 是 PENDING_PAYMENT → 更新状态，发布 Outbox 事件
    4. 若 order.status 是 CANCELLED → 返回失败（已超时取消），触发 Stripe 退款
```
