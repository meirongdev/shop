---
title: Webhook 推送服务设计文档
---

# webhook-service — Webhook 推送服务设计文档

> 版本：1.0 | 日期：2026-03-23 | 状态：核心已实现（签名投递 + 持久化重试）

---

## 一、服务定位

`webhook-service` 负责：

- 监听订单、钱包、注册等核心事件
- 按订阅关系生成 `webhook_delivery`
- 对外发送带 HMAC-SHA256 签名的 HTTP 回调
- 对失败回调做指数退避重试，并保留完整 delivery history

---

## 二、当前实现概览

当前消费的 Topic 包括：

- `order.events.v1`
- `wallet.transactions.v1`
- `buyer.registered.v1`

`WebhookEventListener` 只负责把消息解析为统一事件入口，随后交给 `WebhookDeliveryService#dispatchEvent(...)`：

- 对每个 endpoint 生成或复用 delivery 记录
- 实际 HTTP 调用失败时，把状态保留在 `webhook_delivery`
- `retryFailedDeliveries()` 定时扫描失败记录并继续补投

---

## 三、Kafka 异常处理策略（2026-03-23）

`webhook-service` 同样属于 **Persisted Delivery / Integration** 类型。

### 3.1 直接进入 DLT 的场景

- JSON 非法
- `eventId` 缺失
- `type` 缺失或为空

这类消息不是“对端临时不可用”，而是消息本身已经无法识别，因此 listener 会抛出 `NonRetryableKafkaConsumerException`，直接进入 DLT。

### 3.2 允许 Kafka 有限重试的场景

- 还没成功生成 delivery 记录前发生数据库瞬时故障
- listener 解析完成后，在进入持久化投递链路前出现基础设施故障

这类故障会抛出 `RetryableKafkaConsumerException`，交给 Kafka retry topic 做有限重试。

### 3.3 不走 Kafka 热重试的场景

- 目标 endpoint 超时、5xx、网络抖动
- 第三方系统短暂拒绝接收 webhook

这些都是**合法事件的投递失败**，已经由 `webhook_delivery` 保存上下文并可重放。  
因此正确做法是让 `retryFailedDeliveries()` 接管，而不是把原始 Kafka 消息反复热重试。
