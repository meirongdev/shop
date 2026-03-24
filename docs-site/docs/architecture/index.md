---
title: 架构设计
---

# 架构设计

## 三层模型不是口号，而是约束

```text
Portal (Buyer / Seller)
        |
        v
API Gateway
        |
   +----+----+
   |         |
Buyer BFF  Seller BFF
   |         |
   +---- Domain Services ----+
            |
          Kafka
```

| 层 | 不变职责 | 允许变化 | 不应该做什么 |
|---|---|---|---|
| Gateway | 统一鉴权、Trusted Headers、限流、灰度、追踪头 | 路由、过滤器、阈值、暴露头 | 不保存领域事实，不实现交易规则 |
| BFF | 面向端侧做聚合与轻量编排 | 下游组合、超时、降级、视图模型 | 不写跨域事实，不拥有订单/库存/积分真相 |
| Domain Service | 拥有 schema、状态机、规则、事件 | 领域模型、批处理、事件发布、插件/策略 | 不直接依赖端侧 UI，不跨库写别人的事实 |
| Worker / Integration | 消费事件、做投影、通知、对外回调 | 监听 topic、模板、重试策略 | 不变成新的事实源 |

## 同步与异步边界

| 场景 | 采用方式 | 原因 |
|---|---|---|
| 浏览、加购、下单、支付确认 | 同步调用 + 快速失败 | 用户当下就要结果，错误需要立刻反馈 |
| 通知、搜索投影、欢迎券、积分奖励 | Kafka 事件驱动 | 解耦核心交易链路，支持重试与重放 |
| 定时取消订单、订阅续费、积分过期 | `@Scheduled` + Redisson 抢占 | 多实例部署下仍只让一个节点执行当前批次 |

## 常见架构问题与当前解法

| 问题 | 当前选择 | 为什么 | 代价 |
|---|---|---|---|
| 为什么不是全链路 reactive | Gateway/BFF 用 Virtual Threads，领域服务保留 Spring MVC | 保持同步代码可读性，降低团队心智负担 | 需要认真治理 timeout 与阻塞点 |
| 为什么不是 Saga 编排一切 | 以 Outbox + Kafka 为主，核心交易保持本地事务 | 更容易重放与补偿，避免分布式事务 | 最终一致而非即时一致 |
| 为什么 BFF 不能持有领域事实 | 事实只在领域服务内持久化 | 边界更清晰，服务可以独立演进 | 可能多一次下游调用 |
| 为什么大量使用 Redis / Redisson | 限流、库存、guest cart、批处理锁都需要共享协调平面 | 简化高并发原子操作和多实例协调 | 需要控制 key 设计与过期策略 |
| 为什么搜索单独成服务 | 检索需求和交易库优化方向不同 | 允许独立索引、重建和开关实验 | 需要接受读模型投影延迟 |

## 安全与可观测如何贯穿全链路

- Gateway 对外统一做 JWT 校验，并注入 `X-Request-Id`、`X-Buyer-Id`、`X-Buyer-Id`、`X-Internal-Token` 等可信头。
- Gateway 默认对客户端返回 `X-Request-Id` 与 `X-Trace-Id`，方便从一次请求直接关联到 Grafana / Loki / Tempo。
- 服务统一暴露 `:8081/actuator/prometheus`，并通过 OTLP 把 traces / logs 送到 Collector。
- K8s 观测清单已经包含 Prometheus、Tempo、Loki、Grafana、Pyroscope、Alert/SLO rules。

## 扩展时优先复用的平台积木

- `shop-common`：响应、异常、安全过滤、可观测自举。
- `shop-contracts`：path 常量、共享 DTO、event envelope。
- `shop-archetypes`：新服务脚手架。
- [`扩展能力与二次开发`](/services/extensibility)：各服务已经预留的 SPI / strategy / template / config 点位。

## 子页导航

- [`API Gateway`](/architecture/api-gateway)
- [`BFF 模式`](/architecture/bff-pattern)
- [`事件驱动架构`](/architecture/event-driven)
