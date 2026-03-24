# Notification Service — 设计文档

> 版本：1.1 | 日期：2026-03-21
> 状态：已实现 | 端口：8092 | 数据库：shop_notification

---

## 一、概述

### 1.1 目标

为 Shop Platform 提供统一的消息通知服务。通过 Kafka 消费业务事件，路由到对应渠道（邮件/短信/WhatsApp）发送通知。

### 1.2 设计原则

- **渠道抽象**：Channel SPI 接口，首个迭代仅实现 Email，SMS/WhatsApp 通过 SPI 预留
- **事件驱动**：作为 Kafka 消费者，不接受同步调用
- **幂等发送**：同一事件同一渠道只发送一次
- **渐进式扩展**：路由规则、渠道实现、模板均可独立扩展

### 1.3 技术选型

| 组件 | 选择 | 理由 |
|------|------|------|
| 邮件发送 | Spring Mail (SMTP) | 零额外依赖，生产通过配置切 SES/SendGrid |
| 模板引擎 | Thymeleaf | 项目已在 portal 层使用，技术栈一致 |
| 本地邮件测试 | Mailpit | 轻量，Web UI 可视化，替代 MailHog |
| ID 策略 | ULID | 全局一致，有序且唯一 |

---

## 二、架构

### 2.1 系统位置

```
profile-service ──→ Kafka (buyer.registered.v1) ──┐
order-service   ──→ Kafka (order.events.v1)    ──┤
wallet-service  ──→ Kafka (wallet.transactions.v1)┘
                                                  │
                                                  ▼
                          ┌─────────────────────────────────┐
                          │      notification-service:8092   │
                          │                                  │
                          │  EventListener (per topic)       │
                          │       │                          │
                          │       ▼                          │
                          │  NotificationRouter              │
                          │   (event → template + channel)   │
                          │       │                          │
                          │       ▼                          │
                          │  ChannelDispatcher               │
                          │   ├─ EmailChannel (Spring Mail)  │
                          │   ├─ SmsChannel (SPI, future)    │
                          │   └─ WhatsAppChannel (SPI, fut.) │
                          │       │                          │
                          │       ▼                          │
                          │  notification_log (MySQL)        │
                          └─────────────────────────────────┘
```

### 2.2 内部分层

| 层 | 类 | 职责 |
|---|---|---|
| **EventListener** | `BuyerRegisteredListener`, `OrderEventListener`, `WalletTransactionListener` | Kafka 消费者，反序列化事件，调用 Router |
| **NotificationRouter** | `NotificationRouter` | 根据事件类型决定模板、收件人、渠道 |
| **ChannelDispatcher** | `ChannelDispatcher` | 查找对应 Channel 实现并调用发送 |
| **Channel SPI** | `NotificationChannel` 接口 + `EmailChannel` | 具体发送实现 |
| **Persistence** | `NotificationLogRepository` | 记录发送状态，支持幂等和重试 |

---

## 三、数据模型

### 3.1 notification_log 表

```sql
CREATE TABLE notification_log (
    id              CHAR(26)     NOT NULL PRIMARY KEY,  -- ULID
    event_id        VARCHAR(64)  NOT NULL,              -- 事件唯一 ID（幂等键）
    event_type      VARCHAR(64)  NOT NULL,              -- e.g. ORDER_CONFIRMED
    recipient_id    VARCHAR(64)  NOT NULL,              -- buyer_id
    channel         VARCHAR(16)  NOT NULL,              -- EMAIL / SMS / WHATSAPP
    recipient_addr  VARCHAR(255) NOT NULL,              -- 邮箱/手机号
    template_code   VARCHAR(64)  NOT NULL,              -- e.g. welcome-email
    subject         VARCHAR(255),                       -- 邮件主题
    status          VARCHAR(16)  NOT NULL,              -- PENDING / SENT / FAILED
    retry_count     INT          DEFAULT 0,
    error_message   VARCHAR(512),
    created_at      DATETIME(3)  NOT NULL,
    sent_at         DATETIME(3),
    UNIQUE KEY uk_event_channel (event_id, channel)     -- 幂等：同事件同渠道只发一次
);
```

---

## 四、Channel SPI

### 4.1 接口定义

```java
public interface NotificationChannel {
    String channelType();                     // "EMAIL", "SMS", "WHATSAPP"
    void send(NotificationRequest request);   // 发送通知
}
```

`ChannelDispatcher` 通过 `channelType()` 匹配目标渠道，无需额外 `supports()` 方法。

### 4.2 NotificationRequest

```java
public record NotificationRequest(
    String recipientAddr,           // 收件地址
    String templateCode,            // 模板编码
    String subject,                 // 主题（邮件用）
    Map<String, Object> variables   // 模板变量
) {}
```

### 4.3 EmailChannel 实现

- 注入 `JavaMailSender` + `SpringTemplateEngine`（Thymeleaf）
- 模板文件位于 `resources/templates/email/{templateCode}.html`
- 渲染 HTML 后通过 `MimeMessage` 发送

### 4.4 未来扩展

新增渠道只需：
1. 实现 `NotificationChannel` 接口
2. 注册为 Spring Bean
3. `ChannelDispatcher` 通过 `channelType()` 自动匹配

---

## 五、事件消费与路由

### 5.1 消费的 Kafka Topics

| Topic | 事件类型 | 触发通知 | 模板 |
|---|---|---|---|
| `buyer.registered.v1` | USER_REGISTERED | 欢迎邮件 | `welcome-email` |
| `order.events.v1` | ORDER_CONFIRMED | 下单成功 | `order-confirmed` |
| `order.events.v1` | ORDER_SHIPPED | 已发货 | `order-shipped` |
| `order.events.v1` | ORDER_COMPLETED | 已完成 | `order-completed` |
| `order.events.v1` | ORDER_CANCELLED | 已取消 | `order-cancelled` |
| `wallet.transactions.v1` | DEPOSIT_COMPLETED | 充值到账 | `wallet-deposit` |
| `wallet.transactions.v1` | WITHDRAWAL_COMPLETED | 提现到账 | `wallet-withdrawal` |

### 5.1.1 Wallet 事件类型映射

现有 wallet-service 事件使用 `type`（DEPOSIT/WITHDRAW）+ `status`（COMPLETED）两个字段。
Listener 需要组合映射：
- `type=DEPOSIT` + `status=COMPLETED` → `DEPOSIT_COMPLETED`
- `type=WITHDRAW` + `status=COMPLETED` → `WITHDRAWAL_COMPLETED`
- 其他组合忽略（不发通知）

### 5.2 NotificationRouter 路由规则

```java
private static final Map<String, NotificationConfig> ROUTES = Map.of(
    "USER_REGISTERED",       new NotificationConfig("welcome-email",     "EMAIL", "Welcome to Shop!"),
    "ORDER_CONFIRMED",       new NotificationConfig("order-confirmed",   "EMAIL", "Order Confirmed"),
    "ORDER_SHIPPED",         new NotificationConfig("order-shipped",     "EMAIL", "Your Order Has Shipped"),
    "ORDER_COMPLETED",       new NotificationConfig("order-completed",   "EMAIL", "Order Completed"),
    "ORDER_CANCELLED",       new NotificationConfig("order-cancelled",   "EMAIL", "Order Cancelled"),
    "DEPOSIT_COMPLETED",     new NotificationConfig("wallet-deposit",    "EMAIL", "Deposit Received"),
    "WITHDRAWAL_COMPLETED",  new NotificationConfig("wallet-withdrawal", "EMAIL", "Withdrawal Processed")
);
```

Phase 1 全部走 EMAIL 渠道。后续可扩展为按用户偏好或渠道优先级选择。

### 5.3 收件人地址

事件 payload 中携带 recipient email，避免 notification-service 对 profile-service 的运行时依赖。

### 5.4 event_id 幂等键映射

`notification_log.event_id` 取自 `EventEnvelope.eventId`（ULID），由发布端在写入 outbox 时生成。每个事件有全局唯一的 eventId，直接作为幂等键使用。

---

## 六、幂等与重试

### 6.1 幂等

- `uk_event_channel(event_id, channel)` 唯一约束
- 消费事件时先查 notification_log，已存在则跳过
- Kafka 消费者手动 ACK（`ack-mode: manual`），确保处理成功后才提交 offset

### 6.2 重试

- 发送失败：`status=FAILED`，`retry_count++`，记录 `error_message`
- 定时任务（`@Scheduled`，每 60 秒）轮询 `status=FAILED AND retry_count < 3` 的记录重试
- 超过 3 次：保留 `FAILED` 状态，可人工处理或接入告警

---

## 七、邮件模板

### 7.1 模板文件结构

```
resources/templates/email/
├── welcome-email.html
├── order-confirmed.html
├── order-shipped.html
├── order-completed.html
├── order-cancelled.html
├── wallet-deposit.html
└── wallet-withdrawal.html
```

### 7.2 模板变量约定

| 模板 | 变量 |
|------|------|
| `welcome-email` | `username` |
| `order-confirmed` | `username`, `orderId`, `totalAmount`, `items` |
| `order-shipped` | `username`, `orderId`, `trackingNumber` |
| `order-completed` | `username`, `orderId` |
| `order-cancelled` | `username`, `orderId`, `reason` |
| `wallet-deposit` | `username`, `amount`, `balance` |
| `wallet-withdrawal` | `username`, `amount`, `balance` |

---

## 八、上游服务事件发布补充

当前仅 wallet-service 实现了 Outbox + Kafka 发布。以下服务需补充：

### 8.1 profile-service

- 注册时写 outbox，发布 `buyer.registered.v1`
- Payload：`BuyerRegisteredEventData(buyerId, username, email)`

### 8.2 order-service

- 订单状态变更时写 outbox，发布 `order.events.v1`
- Payload：`OrderEventData(orderId, buyerId, buyerEmail, status, totalAmount, items)`

### 8.3 wallet-service

- 已有 `wallet.transactions.v1`
- 向现有 `WalletTransactionEventData` 追加字段（向后兼容，新字段为可选）：

```java
// 在现有 record 末尾追加（Jackson 反序列化时缺失字段默认 null，向后兼容）
public record WalletTransactionEventData(
    String transactionId,   // 已有字段
    String buyerId,        // 已有字段
    String type,            // 已有字段：DEPOSIT / WITHDRAW
    BigDecimal amount,      // 已有字段
    String currency,        // 已有字段
    String status,          // 已有字段：COMPLETED / FAILED
    Instant occurredAt,     // 已有字段
    // ↓ 新增字段
    String email,           // 收件邮箱
    BigDecimal balance      // 操作后余额
) {}
```

### 8.4 事件 DTO（shop-contracts）

```java
public record BuyerRegisteredEventData(
    String buyerId,
    String username,
    String email
) {}

public record OrderEventData(
    String orderId,
    String buyerId,
    String buyerEmail,
    String status,
    BigDecimal totalAmount,
    List<OrderItemSummary> items,
    String trackingNumber,    // 发货时填写，其他状态为 null
    String cancelReason       // 取消时填写，其他状态为 null
) {}
```

---

## 九、部署配置

### 9.1 Docker Compose

```yaml
notification-service:
  build:
    context: .
    dockerfile: docker/Dockerfile.module
    args:
      MODULE: notification-service
  ports:
    - "8092:8080"
  environment:
    NOTIFICATION_DB_URL: jdbc:mysql://mysql:3306/shop_notification?createDatabaseIfNotExist=true&useSSL=false&allowPublicKeyRetrieval=true
    NOTIFICATION_DB_USERNAME: shop
    NOTIFICATION_DB_PASSWORD: shop
    SPRING_KAFKA_BOOTSTRAP_SERVERS: kafka:9092
    SPRING_MAIL_HOST: mailpit
    SPRING_MAIL_PORT: 1025
  depends_on:
    mysql:
      condition: service_healthy
    kafka:
      condition: service_healthy

mailpit:
  image: axllent/mailpit:latest
  ports:
    - "8025:8025"   # Web UI
    - "1025:1025"   # SMTP
```

### 9.2 MySQL

- 新增 schema：`shop_notification`
- 在 mysql init 脚本中添加 `CREATE DATABASE IF NOT EXISTS shop_notification`

### 9.3 Kubernetes

- 新增 Deployment + Service（port 8092）
- 无需 Ingress（内部服务，不对外暴露）

---

## 十、后续扩展路径

| Phase | 扩展 | 说明 |
|---|---|---|
| Phase 2 | 积分变动通知 | 消费 `loyalty.points.earned.v1` |
| Phase 2 | 等级升级通知 | 消费 `loyalty.tier.upgraded.v1` |
| Phase 2 | 优惠券到期提醒 | 定时任务扫描即将过期的 coupon_instance |
| Phase 3 | 活动中奖通知 | 消费 `activity.prize.won.v1` |
| Phase 3 | 拼团成团通知 | 消费 `activity.groupbuy.formed.v1` |
| Phase 2+ | SMS Channel | 实现 `SmsChannel`，接入 Twilio / 阿里云 |
| Phase 2+ | WhatsApp Channel | 实现 `WhatsAppChannel`，接入 WhatsApp Business API |
| Phase 3+ | 用户通知偏好 | notification_preference 表，按用户偏好选择渠道 |
