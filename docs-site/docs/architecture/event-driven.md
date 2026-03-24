---
title: 事件驱动架构
---

# 事件驱动架构

## 总体模式

平台采用 **Outbox Pattern + Kafka**：

1. 业务事务与 outbox 记录同事务提交
2. Poller 定时扫描并投递 Kafka
3. 消费者按业务类型做幂等处理

## 核心 Topic

- `order.events.v1`
- `wallet.transactions.v1`
- `marketplace.product.events.v1`
- `buyer.registered.v1`

## 适用边界

- 同步调用：核心交易路径（需要快速失败与实时反馈）
- 事件驱动：通知、奖励、投影、统计、外部推送等天然异步场景

## 幂等基线

- 以 `eventId` + 业务主键（如 `orderId`）作为幂等键
- 消费落库前先查重；重复事件只记录并跳过
- 重试策略与 DLQ 策略按消费者类型（金融/状态流转/通知）区分

## 当前实现状态

- `wallet-service`、`order-service` 已稳定使用 outbox 发布事件
- `promotion-service`、`loyalty-service`、`notification-service` 已消费核心事件
- `activity-service` 的 `RewardDispatcher` 目前仍为外部派发桩实现，文档中已按“当前实现/目标态”区分

## 当前仓库消费者策略（2026-03-23）

- **Projection / Rebuildable**：`search-service` 的 `ProductEventConsumer` 使用 `@RetryableTopic + DLT`，索引失败可以有限重试，最终仍可通过回放时间窗或全量重建恢复。
- **Idempotent Business State**：`promotion-service` 与 `loyalty-service` 通过幂等表防重；反序列化/契约错误直接入 DLT，数据库或下游服务的瞬时失败才交给 Kafka 重试。
- **Persisted Delivery / Integration**：`notification-service` 与 `webhook-service` 先把合法消息转换为持久化投递记录；真正的邮件/HTTP 推送失败交给各自的数据库重试调度器，而不是反复依赖 Kafka 热重试。

## 设计建议

- 事件契约统一放在 `shop-contracts`
- 新增消费者必须先声明幂等键与失败处理策略
- 对跨服务补偿链路优先使用持久化重试，而非内存 best-effort
