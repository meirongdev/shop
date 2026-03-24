---
title: 技术栈
---

# 技术栈

本页只回答两件事：**用了什么**、**为什么这么选**。

## 分层全景

Client（Portal） → Gateway → BFF → Domain Services → Infra（MySQL/Redis/Kafka/Meilisearch/Observability）

## 选型决策

| 类别 | 选择 | 为什么选 | 没选什么 |
|---|---|---|---|
| 运行时并发 | Java 25 Virtual Threads | 同步代码可读性好，I/O 并发成本低 | 全量响应式编程 |
| 网关 | Spring Cloud Gateway MVC | 与 Servlet/Virtual Threads 栈一致 | WebFlux Gateway |
| BFF/服务层 | Spring MVC + RestClient | 与团队主技术栈一致，治理简单 | 多语言 BFF |
| 持久化 | MySQL 8.4 + Flyway | 强事务、可迁移、易治理 | NoSQL 作为主存储 |
| 缓存 | Redis 7.4 | 限流、游客购物车、活动原子操作 | Memcached |
| 消息 | Kafka 3.9（KRaft） | Outbox 模式契合、消费组生态成熟 | RabbitMQ |
| 搜索 | Meilisearch 1.12 | 运维轻、开发体验友好 | Elasticsearch（当前阶段过重） |
| 特性开关 | OpenFeature | 供应商无关，SPI 标准化 | 业务代码直接读配置 |
| 弹性 | Resilience4j | Spring 生态集成好，易落地 | Hystrix（停更） |
| 可观测 | Micrometer + OTLP + Prometheus | 指标/追踪/日志统一采集 | 专有 APM 绑定 |

## 继续阅读

- 代码级实践与模板：[`最佳实践`](/tech-stack/best-practices)
- 架构与边界：[`架构设计`](/architecture)
