---
title: 订阅服务设计文档
---

# subscription-service — 订阅服务设计文档

> 版本：1.0 | 日期：2026-03-23 | 状态：核心已实现（计划管理 + 生命周期 + 定时续费）

---

## 一、服务定位

`subscription-service` 负责：

- 订阅计划管理
- 买家订阅创建、暂停、恢复、取消
- 定时续费批处理
- 续费日志沉淀

---

## 二、当前实现概览

核心模型：

- `subscription_plan`
- `subscription`
- `subscription_order_log`

当前续费任务由 `SubscriptionRenewalService#processRenewals` 触发，按计划频率推进下次续费时间，并写入续费日志。

---

## 三、Redisson 适配点（2026-03-23）

### 3.1 为什么这里适合使用 Redisson

续费任务属于典型的批处理调度入口：

- 在单实例环境里逻辑简单
- 在多实例环境里，如果没有全局协调，多个节点会同时扫描并续费同一批订阅

这会带来：

- 重复续费日志
- 重复触发后续订单/支付链路
- 重复业务副作用

### 3.2 当前落地方式

`SubscriptionRenewalService` 现在会先尝试获取：

```text
shop:subscription:scheduler:renewal
```

拿到锁的实例执行本轮批处理；未拿到锁的实例直接跳过本轮。

这个锁的职责是“多实例协调”，不是替代数据库事务。  
数据库仍然负责持久化一致性；Redisson 负责避免多个节点重复进入同一个批处理入口。

---

## 四、运行要求

服务通过以下配置连接 Redis：

```yaml
spring:
  data:
    redis:
      host: ${SUBSCRIPTION_REDIS_HOST:redis}
      port: ${SUBSCRIPTION_REDIS_PORT:6379}
```

在当前仓库的 Kind / Kubernetes 基线中，默认 `redis` 服务即可满足要求。
