---
title: 工程标准
---

# 工程标准

本页给出工程实践摘要，完整规范以 `docs/ENGINEERING-STANDARDS-2026.md` 为准。

## 已统一基线

- Java 25 + Spring Boot 3.5 + Spring Cloud 2025.0
- Maven Wrapper + Enforcer
- 服务端口 `8080/8081`
- Actuator readiness/liveness/prometheus
- OTLP + 结构化日志
- Internal Token 与 Trusted Headers 安全模型

## 实施中的标准化

- BFF 全量弹性策略（Retry/Bulkhead）
- OpenAPI 统一产物与注解规范
- Feature Toggle 扩散到更多服务
- Testcontainers 集成测试模板化

## 架构守护测试（ArchUnit）

已落地 5 类测试规则，覆盖编码规范、分层约束、命名规范、Spring 特定约束和 Kafka 幂等守护。

- 详见 [架构守护测试](./architecture-testing)
- 权威规范：`docs/ARCHUNIT-RULES.md`

## 仍在 Roadmap

- 契约测试门禁
- 补偿持久化与重试标准

## 文档治理

- `docs/` 维护 SSOT（权威规则、设计、执行文档）
- `docs-site/` 维护对外展示与 onboarding 路径
- 任何行为变化必须先更新权威文档，再更新展示文档

## 参考

- 权威：`docs/ENGINEERING-STANDARDS-2026.md`
- 依赖队列：`docs/DELIVERY-PRIORITY-AND-DEPENDENCY-MAP.md`
