---
title: 架构设计
---

# 架构设计

## 三层模型

```text
Portal (Buyer/Seller)
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

## 核心原则

- Gateway 负责 northbound 安全与流量治理
- BFF 做聚合编排，不持有领域事实
- Domain Service 独立 schema、独立迁移、独立边界

## 同步与异步边界

- 核心交易链路采用同步调用（快速失败）
- 扩展能力采用事件驱动（解耦、削峰、可重放）

## 安全边界

- 外部流量统一经 Gateway
- Trusted Headers 由 Gateway 注入，服务端校验内部 token
- 角色与能力控制在 BFF/领域服务继续收敛

## 子页导航

- [`API Gateway`](/architecture/api-gateway)
- [`BFF 模式`](/architecture/bff-pattern)
- [`事件驱动架构`](/architecture/event-driven)
