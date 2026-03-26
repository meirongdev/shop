---
slug: /
sidebar_position: 1
title: 项目概览
---

# Shop Platform

基于 **Java 25 + Spring Boot 3.5 + Spring Cloud 2025.0** 的云原生微服务电商技术验证平台，用来验证一套可以持续扩展的电商工程底座：**Gateway → Thin BFF → Domain Service → Event / Worker → Portal**。

## 这套项目重点验证什么

- 交易主链路如何保持清晰的服务边界，而不是把所有逻辑都塞进单体应用。
- BFF 如何只做聚合编排，不持有领域事实。
- Outbox + Kafka 如何替代跨服务分布式事务。
- Redis / Redisson / Lua 如何解决限流、库存、批处理抢占等共享协调问题。
- 指标、链路、日志、profile 如何统一进一套可观测栈，而不是事后补洞。

## 分层全景

| 层 | 代表服务 | 主技术 | 解决的问题 |
|---|---|---|---|
| Edge | `api-gateway` | Spring Cloud Gateway MVC、Spring Security、Redis Lua | 统一鉴权、限流、灰度、追踪关联 |
| Identity | `auth-server` | Spring Security、JWT、JPA、Redis | 集中处理登录、社交登录、OTP、guest token |
| BFF | `buyer-bff`、`seller-bff` | RestClient、Virtual Threads、局部 Resilience4j | 面向端侧聚合下游接口，但不持有领域事实 |
| Domain | `profile`、`marketplace`、`order`、`wallet`、`promotion`、`loyalty`、`activity`、`subscription` | Spring MVC、JPA、Flyway、MySQL、Kafka、Redis/Redisson | 各自独立拥有业务事实、状态机、规则与调度 |
| Worker / Integration | `notification`、`webhook`、`search` | Kafka、Thymeleaf、Meilisearch、重试任务 | 异步通知、对外回调、搜索投影、开放能力 |
| Portal | `buyer-portal` | Kotlin、Thymeleaf | 用 SSR 快速交付买家门户，SEO 友好，支持游客模式 |

## 技术如何解决架构问题

| 问题 | 当前技术组合 | 结果 |
|---|---|---|
| 外部流量如何统一鉴权和限流 | Gateway + JWT + Trusted Headers + Redis Lua | 下游服务不必重复做 northbound 安全治理 |
| 多下游接口聚合如何既可读又扛并发 | RestClient + Virtual Threads + timeout | 保持同步代码模型，同时减少线程阻塞成本 |
| 跨服务事务如何保证可恢复 | Outbox Pattern + Kafka + consumer 幂等 | 业务写库和事件发布解耦，但仍可重放、可补偿 |
| 高并发库存与批处理如何多实例协调 | Redis / Redisson / RLock / Lua | 避免超卖、重复续费、重复批处理 |
| 促销、活动、通知这类规则如何扩展 | Strategy / Plugin / Channel 接口 | 新玩法、新规则、新渠道以新实现接入，不重写核心应用服务 |
| 如何统一排障视角 | Prometheus + OTLP Collector + Tempo + Loki + Grafana + Pyroscope | 能从慢请求直接关联到 metrics、trace、log、profile |

## 读文档的推荐路径

- 先看 [`架构设计`](/architecture) 了解边界和同步/异步分层。
- 再看 [`入口与聚合服务`](/services/edge-and-entry) 与 [`核心交易服务`](/services/core) 了解主链路。
- 如果你要做二次开发，直接看 [`扩展能力与二次开发`](/services/extensibility)。
- 想理解为什么要这样选型，看 [`技术栈`](/tech-stack) 和 [`技术栈最佳实践`](/tech-stack/best-practices)。
- 想排障或补监控，看 [`可观测性`](/engineering/observability)。
