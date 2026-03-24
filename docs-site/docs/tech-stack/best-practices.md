---
sidebar_position: 7
title: 技术栈最佳实践（2026）
---

# 技术栈最佳实践（2026）

## 平台底座优先

- 先统一 parent/BOM、构建与测试入口
- 用 `shop-common`/`shop-contracts` 收敛共享语义
- 服务模板优先于复制粘贴旧模块

## Virtual Threads 实践

```yaml
spring:
  threads:
    virtual:
      enabled: true
```

- 适合 BFF 聚合与 I/O 密集链路
- 仍需限制下游超时，避免“无限等待”

## Gateway 路由与治理

```yaml
spring:
  cloud:
    gateway:
      mvc:
        routes:
          - id: buyer-bff
            uri: ${BUYER_BFF_URL:http://buyer-bff:8080}
            predicates:
              - Path=/api/buyer/**
            filters:
              - StripPrefix=1
```

- 路由规则应声明式管理
- 限流/灰度与安全过滤放在 gateway 层统一处理

## Outbox 模式

```text
Business TX (write domain + outbox)
  -> Scheduled publisher
  -> Kafka topic
  -> Consumer with idempotency key
```

- 先保证“可重放+幂等”，再谈高吞吐优化

## Resilience4j 降级

```java
@CircuitBreaker(name = "promotion", fallbackMethod = "fallbackPromotion")
public ApplyCouponResult applyCoupon(...) { ... }
```

- 非核心依赖允许降级
- 核心依赖保持 fail-fast

## Feature Toggle 热更新

```yaml
shop:
  features:
    flags:
      search-autocomplete: true
      search-trending: true
      search-locale-aware: true
```

- 适合实验开关与渐进发布
- 不应把所有配置都做成热更新

## 现状边界

以下能力属于“部分落地或规划中”：契约测试、补偿持久化、ArchUnit、完整观测平台。

## 参考

- 权威技术文档：`docs/TECH-STACK-BEST-PRACTICES-2026.md`
- 工程标准：`docs/ENGINEERING-STANDARDS-2026.md`
