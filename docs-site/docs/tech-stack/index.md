---
title: 技术栈
---

# 技术栈

这页回答三件事：**用了什么**、**解决什么问题**、**扩展时该沿哪条线继续做**。

## 分层技术栈总览

| 层 | 当前选择 | 为什么这样搭 |
|---|---|---|
| Edge | Spring Cloud Gateway Server MVC + Spring Security + Redis Lua | 统一做 northbound 安全与流量治理，同时保持 Servlet / Virtual Threads 编程模型一致 |
| BFF | Spring Boot + RestClient + Virtual Threads + 局部 Resilience4j | 面向端侧聚合下游接口，保持同步代码可读性 |
| Domain Service | Spring MVC + JPA + Flyway + MySQL + Kafka + Redis/Redisson | 让数据所有权、迁移、状态机、事件传播都有稳定基线 |
| Search / Integration | Meilisearch、Kafka、Thymeleaf、SMTP、HMAC Webhook | 用投影、通知、开放平台能力解耦交易主链路 |
| Portal | Kotlin + Thymeleaf | 用 SSR 快速交付页面，先复用现有后端能力 |
| Observability | Micrometer + OTLP Collector + Tempo + Loki + Grafana + Pyroscope | 统一指标、链路、日志与 profile 的排障视角 |

## 技术与架构问题的映射

| 问题 | 技术 | 设计效果 |
|---|---|---|
| 外部流量入口太分散 | Gateway + Trusted Headers | 安全与流量治理集中化 |
| 多下游接口聚合容易把代码写烂 | RestClient + Virtual Threads + 超时 | 聚合仍是同步代码，但并发更轻量 |
| 跨服务写操作难保证一致性 | Outbox + Kafka | 支持最终一致、重放、补偿 |
| 高并发库存 / 批处理抢占 | Redis / Lua / Redisson | 原子操作和多实例协调都在共享平面解决 |
| 经常变动的促销、活动、通知规则 | Strategy / Plugin / Channel 接口 | 用新增实现扩展业务，而不是重写核心服务 |
| 搜索和交易库负载模型不一致 | Meilisearch + Kafka 投影 | 读写分离，索引可以独立重建 |
| 线上排障缺少统一线索 | X-Trace-Id + OTLP + Grafana family | 一次请求能串起 metrics / trace / log / profile |

## 扩展时最常复用的技术积木

| 积木 | 主要出现在哪些模块 | 适合拿来干什么 |
|---|---|---|
| `@ConfigurationProperties` | 几乎所有服务 | 收敛配置，不让字符串配置散落代码 |
| `shop-contracts` | Gateway、BFF、Domain、Portal | 共享 API path、DTO、event envelope |
| `shop-common` | 大多数 Java 服务 | 统一响应、异常、安全、可观测能力 |
| `NotificationChannel` / `GamePlugin` / `BenefitCalculator` | 通知 / 活动 / 促销 | 用接口新增业务规则或渠道 |
| `shop.features.*` | search-service、Portal 等 | 做渐进实验和灰度，不污染业务代码 |

## 继续阅读

- 复用与演进建议：[`技术栈最佳实践`](/tech-stack/best-practices)
- 服务级扩展点：[`扩展能力与二次开发`](/services/extensibility)
- 权威技术文档：`docs/TECH-STACK-BEST-PRACTICES-2026.md`
