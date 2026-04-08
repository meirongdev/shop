# notification-service — 通知服务设计文档

> 版本：1.0 | 日期：2026-03-23 | 状态：核心已实现（模板通知 + 持久化重试）

---

## 一、服务定位

`notification-service` 负责：

- 消费核心业务事件并渲染通知模板
- 将通知发送到邮件等渠道
- 基于 `notification_log` 保留投递状态、失败原因与重试次数
- 由 `NotificationRetryScheduler` 对失败记录做后续补投

---

## 二、当前实现概览

当前已消费的核心 Topic：

- `buyer.registered.v1`
- `order.events.v1`
- `wallet.transactions.v1`

Listener 解析事件后，会调用 `NotificationApplicationService#processEvent(...)`：

- 使用 `(event_id, channel)` 做幂等去重
- 成功发送时写入 `SENT`
- 发送失败时写入 `FAILED`，保留错误信息与重试计数

---

## 三、Kafka 异常处理策略（2026-03-23）

`notification-service` 属于 **Persisted Delivery / Integration** 类型，不适合把所有失败都交给 Kafka 重试。

### 3.1 直接进入 DLT 的场景

- JSON 反序列化失败
- `eventId` 缺失
- 关键业务字段缺失（如 `buyerId`、`transactionId`、`status`）

这类消息说明契约已经坏掉，继续热重试只会反复失败，因此 listener 会抛出 `NonRetryableKafkaConsumerException`，直接进入 DLT。

### 3.2 允许 Kafka 有限重试的场景

- 还没成功写入 `notification_log` 前发生数据库瞬时故障
- listener 还没把合法消息交给 `NotificationApplicationService` 前发生基础设施故障

这类问题会抛出 `RetryableKafkaConsumerException`，交给 `@RetryableTopic` 做有限重试。

### 3.3 不走 Kafka 热重试的场景

- SMTP/渠道调用失败
- 模板发送过程中出现的真实投递失败

这类失败已经被 `NotificationApplicationService` 持久化为 `FAILED` 记录，后续由 `NotificationRetryScheduler` 继续补投。  
也就是说：**Kafka retry 负责“消费入口没落稳之前”的问题，DB scheduler 负责“通知已建单后的送达问题”。**
