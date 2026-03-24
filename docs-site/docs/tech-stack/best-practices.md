---
sidebar_position: 7
title: 技术栈最佳实践（2026）
---

# 技术栈最佳实践（2026）

## 先统一平台骨架，再做业务扩展

- 父 POM / BOM / Maven Wrapper 先统一。
- `shop-common` / `shop-contracts` 先沉淀共享语义。
- 新服务优先从 `shop-archetypes` 起步，不要复制旧模块再手工删改。

## 适合长期复用的模式

### BFF：同步代码 + 轻量并发

```yaml
spring:
  threads:
    virtual:
      enabled: true
```

- 适合多下游读取、轻量编排、端侧视图拼装。
- 必须和 connect / read timeout 一起使用，避免“虚拟线程很多但全在无限等待”。

### 领域事实：JPA + Flyway + 每服务独立 schema

- 让数据边界、迁移节奏、回滚策略都跟着服务走。
- 不要让 BFF 或 Portal 直接改领域库。

### 跨服务传播：Outbox + Kafka

```text
Business TX (write domain + outbox)
  -> Scheduled publisher
  -> Kafka topic
  -> Consumer with idempotency key
```

- 先保证可重放、可补偿、可观测，再谈吞吐优化。

### 规则扩展：Strategy / Plugin / Channel

- 促销：`BenefitCalculator` / `ConditionEvaluator`
- 活动：`GamePlugin`
- 通知：`NotificationChannel`
- 支付：`StripeGateway`

这类扩展点优先通过“新增实现 + 自动装配”接入，不要给核心应用服务继续堆条件分支。

### 可观测：默认接上四类信号

- metrics：Prometheus
- traces：Tempo
- logs：Loki
- profiles：Pyroscope

服务初始化时就把 OTLP、结构化日志、追踪关联头接好，排障成本会显著低于上线后再回补。

## 当前仍在继续演进的边界

以下能力已经有方向，但仍要继续补齐：

- 契约测试与架构规则门禁
- Alertmanager / runbook 自动化
- 多环境 observability 保留策略与容量治理
- Kind / 镜像 / smoke test 的 CI 验证闭环

## 参考

- 权威技术文档：`docs/TECH-STACK-BEST-PRACTICES-2026.md`
- 服务扩展矩阵：`docs/SERVICE-TECH-STACK-AND-EXTENSIBILITY.md`
