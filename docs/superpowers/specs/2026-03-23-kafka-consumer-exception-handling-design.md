# Kafka Consumer 异常处理分型与落地设计

> 日期：2026-03-23  
> 状态：Design Approved for Implementation

---

## 1. 背景

当前仓库中 Kafka consumer 已覆盖：

- 用户注册奖励与欢迎券发放
- 订单完成后的积分发放
- 搜索索引同步
- 邮件通知
- 对外 Webhook 投递

但异常处理方式并不统一：

- `search-service` 已经使用 `@RetryableTopic + DLT`
- `notification-service` / `webhook-service` 依赖数据库持久化重试
- `promotion-service` / `loyalty-service` 具备幂等保护，但部分 listener 仍然直接吞掉异常

问题不在于“有没有 DLQ”，而在于**不同 consumer 的失败语义不同**：

- 有些失败是瞬时依赖故障，应该重试
- 有些是 poison pill / schema 错误，应该尽快转 DLT
- 有些是投递失败，但业务侧已经把失败持久化，应该由服务内 retry scheduler 接管，而不是再走 Kafka 重试

---

## 2. 当前项目中的 Kafka Consumers

### 2.1 Projection / Rebuildable

#### `search-service`

- `ProductEventConsumer`
- Topic: `marketplace.product.events.v1`
- 职责：维护 Meilisearch 商品索引
- 特点：重放安全、可全量重建、失败不会直接产生金融或状态机副作用

### 2.2 Idempotent Business State / Reward

#### `promotion-service`

- `WelcomeCouponListener`
- `WalletRewardListener`
- Topic:
  - `user.registered.v1`
  - `wallet.transactions.v1`
- 特点：有业务副作用（发券、创建活动），依赖 `IdempotencyGuard`

#### `loyalty-service`

- `UserRegisteredListener`
- `OrderEventListener`
- Topic:
  - `user.registered.v1`
  - `order.events.v1`
- 特点：有业务副作用（送积分、初始化任务、首单任务推进），依赖 `IdempotencyGuard`

### 2.3 Persisted Delivery / Integration

#### `notification-service`

- `UserRegisteredListener`
- `OrderEventListener`
- `WalletTransactionListener`
- Topic:
  - `user.registered.v1`
  - `order.events.v1`
  - `wallet.transactions.v1`
- 特点：真正的“发送”失败会被持久化为 `notification_log`，后续由 `NotificationRetryScheduler` 重试

#### `webhook-service`

- `WebhookEventListener`
- Topic:
  - `order.events.v1`
  - `wallet.transactions.v1`
  - `user.registered.v1`
- 特点：真实投递失败会落库为 `webhook_delivery`，后续由 `WebhookDeliveryService.retryFailedDeliveries()` 重试

---

## 3. 2026 最佳实践：按 Consumer 类型分型处理异常

### 3.1 Projection / Rebuildable（投影 / 可重建型）

典型：`search-service`

策略：

- 对瞬时错误积极重试
- 超过上限后进入 DLT
- DLT 不是终态存储，而是人工检查与回放入口
- 补充 DLT 可观测性（日志 / metrics）

适用原因：

- 搜索索引可重放、可重建
- 不需要把“索引更新失败”变成数据库里的失败记录

### 3.2 Idempotent Business State（幂等业务副作用型）

典型：`promotion-service`、`loyalty-service`

策略：

- **重复事件**：幂等跳过，不抛异常
- **schema / 反序列化 / 明显业务无效**：直接进入 DLT，不做热重试
- **瞬时数据库 / 下游依赖错误**：允许 Kafka retry
- **超过 retry 上限**：进入 DLT，后续人工回放或补偿

适用原因：

- 这类 consumer 会真正修改业务状态
- 不能继续吞异常，否则会造成奖励/发券静默丢失
- 也不能把所有失败都当成“死信”，因为 DB / 下游短暂故障经常可恢复

### 3.3 Persisted Delivery / Integration（持久化投递型）

典型：`notification-service`、`webhook-service`

策略：

- **解析 / 契约错误**：直接进入 DLT
- **实际发送 / 投递失败**：由服务内持久化 retry 机制处理，不再让 Kafka 反复重试
- **重复事件**：由 `notification_log` / `webhook_delivery` 唯一约束或查重逻辑跳过

适用原因：

- 这些服务已经把失败状态显式落库
- 再让 Kafka 做热重试只会重复插入/竞争相同 delivery rows
- DB retry scheduler 更适合做指数退避、人工追踪与重放

---

## 4. 异常分类矩阵

| 异常类型 | 典型来源 | search-service | promotion / loyalty | notification / webhook |
|----------|----------|----------------|---------------------|------------------------|
| `JsonProcessingException` / schema mismatch | payload 不合法 | 进 DLT | 进 DLT | 进 DLT |
| 幂等重复 / 已处理 | 唯一键、幂等 guard fallback | 直接覆盖或跳过 | 跳过 | 跳过 |
| `DataAccessException`（非重复约束） | DB 短暂不可用 | retry + DLT | retry + DLT | 一般不由 listener 处理；若发生在落库前可 retry |
| 下游 IO / HTTP 短暂故障 | Meilisearch / profile / SMTP / Webhook 目标服务 | retry + DLT | retry + DLT | 由 delivery log / scheduler 接管 |
| 明确业务拒绝 | 无效事件类型、缺失关键业务字段 | 可 DLT 或跳过（按可重建性） | DLT 或跳过（必须有清晰日志） | 跳过或 DLT（取决于是否 payload 契约错误） |

---

## 5. 本次代码落地范围

### 5.1 `search-service`

- 保留现有 `@RetryableTopic + @DltHandler`
- 补充 DLT metrics / 更清晰日志

### 5.2 `promotion-service`

- 为 `WelcomeCouponListener` / `WalletRewardListener` 增加统一的异常分类
- 使用 `@RetryableTopic`：
  - 非可恢复异常直接进 DLT
  - 可恢复异常走 Kafka retry
- 保持 `IdempotencyGuard` 作为重复事件保护

### 5.3 `loyalty-service`

- 为 `UserRegisteredListener` / `OrderEventListener` 去掉“吃掉所有异常”的模式
- 对反序列化错误、明显业务无效错误直送 DLT
- 对数据库或 profile-service 等下游瞬时错误允许 Kafka retry

### 5.4 `notification-service`

- listener 只负责：
  - 解析 payload
  - 对 poison pill 直送 DLT
  - 正常消息交给 `NotificationApplicationService`
- `NotificationApplicationService` 已经负责把实际发送失败标记为 `FAILED` 并由 scheduler 重试，继续保留

### 5.5 `webhook-service`

- listener 只负责：
  - 解析 envelope
  - 契约错误直送 DLT
  - 合法消息交给 `WebhookDeliveryService`
- `WebhookDeliveryService` 继续作为真正的异步投递重试机制

---

## 6. 实现原则

### 6.1 不重复造轮子

优先复用现有能力：

- `IdempotencyGuard`
- `NotificationRetryScheduler`
- `WebhookDeliveryService.retryFailedDeliveries()`
- `@RetryableTopic`

### 6.2 listener 负责分类，service 负责业务

listener 层只做三件事：

1. payload 解析
2. 异常分类
3. 决定 ack / retry / DLT 路径

真实业务副作用仍下沉到 service 层。

### 6.3 DLT 只接不可恢复问题

DLT 主要接收：

- 反序列化失败
- schema / payload 契约错误
- 明确不可恢复的业务无效消息

而不是把所有发送失败、依赖抖动都丢到 DLT。

---

## 7. 预期收益

- 消除当前多处 listener 的“只记日志不重试”静默丢失风险
- 保留通知 / webhook 现有 DB 持久化重试优势
- 让 search / promotion / loyalty / notification / webhook 的失败路径更可观测、更一致
- 形成一套可以写回工程规范与 docs-site 的 consumer 异常处理矩阵
