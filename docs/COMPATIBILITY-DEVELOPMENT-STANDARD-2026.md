# Compatibility Development Standard 2026

> 适用范围：所有 `shop-platform` 模块在做版本升级、接口扩展、消息演进、数据库变更与灰度发布时统一遵循  
> 技术基线：Spring Boot 3.5.x / Spring Cloud 2025.0.x / Cloud Native / Kubernetes / Kafka / MySQL

---

## 1. 目标

兼容性不是“尽量不出问题”，而是要让模块升级、新特性开发、分批发布、回滚与多版本共存都变成**可设计、可验证、可观测**的工程动作。

本仓库当前已经具备不错的基础设施，但过去更偏“架构可扩展”，本次补齐的是“演进安全带”。

---

## 2. 2026 最佳实践下的兼容性规范

## 2.1 HTTP / API 契约

- 路径版本必须显式化，统一采用 `/.../vN/...`，禁止隐式版本漂移。
- 对当前仓库中的 trusted internal API，允许继续使用 `/.../internal/...`；一旦发生破坏性变更，应演进为新的 internal contract（如 `/.../internal/v2/...` 或等价的新内部路径），不能静默覆盖旧语义。
- 向后兼容的变更优先采用 **additive-only**：
  - 只新增可选字段
  - 不删除旧字段
  - 不改变字段语义
  - 不收紧校验规则
- 破坏性变更必须进入新版本路径，不得直接覆盖 `v1`。
- 服务响应必须暴露版本信息，方便 Portal、BFF、外部集成和观测系统识别当前契约版本。
- 旧接口进入弃用期时，必须输出弃用元数据：
  - `Deprecation`
  - `Sunset`
  - 替代接口/路径
- 反序列化必须对未知字段容忍，保证消费者对“上游新增字段”具备前向兼容。

## 2.2 Kafka / 事件契约

- 所有跨服务事件必须使用统一 envelope。
- envelope 至少包含：
  - `eventId`
  - `type`
  - `source`
  - `timestamp`
  - `schemaVersion`
  - `contentType`
  - `data`
- 事件新增字段时必须保持 additive-only，禁止直接复用旧字段做新语义。
- 消费者必须显式校验支持的 `schemaVersion`；不支持的 schema 进入非重试失败路径/DLT，避免无限重试。
- Event DTO 必须容忍未知字段，保证旧消费者可以安全忽略新字段。
- 主题名可以保持稳定，但 schema 演进必须可识别、可审计、可告警。

## 2.3 数据库兼容

- Flyway 迁移统一使用 **expand -> migrate -> contract** 模式：
  - 先加新列/新表/新索引
  - 再灰度写入/读兼容
  - 最后再清理旧结构
- 禁止在单次上线内同时做“删旧列 + 新代码强依赖新结构”。
- 涉及跨服务链路的字段变更必须先完成事件/API 兼容层，再做存储收敛。

## 2.4 配置与运行时兼容

- 配置升级必须保持旧环境变量仍可工作，新增配置必须给默认值或显式 fail-fast。
- 共享基础库升级要优先通过 `shop-common` / `shop-contracts` 统一下发，而不是各服务私自分叉。
- Virtual Threads、Resilience4j、OpenFeature、OTEL 这类横切能力，应通过共享基线统一治理。

## 2.5 发布与回滚兼容

- 新特性默认走 Feature Flag 或灰度路由，不直接全量切换。
- 灰度时必须观察：
  - 错误率
  - 延迟
  - DLT/重试堆积
  - schema mismatch / 反序列化失败
- 回滚必须优先保证旧消费者、旧 BFF、旧 Portal 仍能读懂新的响应和事件。

## 2.6 测试与治理

- 至少覆盖三类测试：
  - 共享契约测试
  - 兼容性架构测试
  - 关键链路集成测试
- 对 future-proof 的规范，不只写文档，要放进自动化校验中。

---

## 3. 当前项目已经怎么支持兼容性

以下能力在仓库中已经存在，并且应继续保留：

- **集中契约**：`shop-contracts` 统一管理 API 路径、DTO、事件契约，避免各模块各自定义协议。
- **统一响应**：`shop-common` 的 `ApiResponse<T>` 保持一致的成功响应模型。
- **统一错误模型**：`GlobalExceptionHandler` + `ProblemDetail` 让失败语义稳定。
- **版本化路径**：当前 API 常量普遍使用 `/v1`。
- **数据库演进基础**：每个域服务自持 schema + Flyway migration。
- **消息可靠性基础**：Outbox + Kafka + DLT/retry + 幂等守护。
- **网关/内部调用约定**：Trusted Headers + Internal Token，降低跨模块升级时的隐式耦合。
- **灰度基础**：Gateway 已有 canary predicate，Feature Toggle 已有 OpenFeature 试点。
- **观测基础**：Actuator + Prometheus + OTEL + 结构化日志，为兼容性问题定位提供基础。

---

## 4. 本次发现的不足

在这次梳理前，仓库仍有这些明显缺口：

- HTTP 响应没有统一输出 API 版本头。
- 旧接口弃用没有统一的元数据模型。
- 事件 envelope 没有 `schemaVersion` / `contentType`。
- Event DTO 没有显式声明“忽略未知字段”。
- Kafka 消费者没有统一拒绝“不支持的 schemaVersion”。
- 兼容性规范没有通过自动化测试固化。
- 未来新模块的 archetype 样例没有把 schema 校验体现出来。

---

## 5. 本次已经补上的设计与实现

## 5.1 共享层实现

- 在 `shop-common` 新增 `JacksonCompatibilityAutoConfiguration`
  - 显式关闭 `FAIL_ON_UNKNOWN_PROPERTIES`
  - 让 REST / Kafka / 内部调用在升级阶段对“新增字段”更稳健

- 在 `shop-common` 新增 API 兼容拦截器
  - 自动从 `/vN` 路径提取并输出 `X-API-Version`
  - 支持用 `@ApiDeprecation` 为接口声明：
    - `Deprecation`
    - `Sunset`
    - `X-API-Deprecated-Since`
    - `X-API-Replacement`

## 5.2 事件契约实现

- `EventEnvelope` 已补充：
  - `schemaVersion`
  - `contentType`
  - legacy 5 参数构造仍然保留，避免现有生产者代码破坏
  - 老消息缺失新字段时自动回填默认值
- 所有 event DTO 已显式 `@JsonIgnoreProperties(ignoreUnknown = true)`。
- 关键 Kafka 消费者已在处理前调用 `assertSupportedSchema(...)`。
- `search-service` 已把 schema/格式错误归为非重试失败，避免无效消息反复重放。
- `webhook-service` 已从 envelope 层读取 `eventId/type`，不再绕开 schema 兼容校验。

## 5.3 自动化治理

- 新增 `CompatibilityConventionsTest`
  - 强制 API `BASE_PATH` 使用显式版本段
  - 强制事件契约允许忽略未知字段
  - 校验 legacy event payload 仍能安全反序列化
- `shop-common` 增加兼容性单元测试，覆盖：
  - Jackson ignore-unknown 行为
  - HTTP 版本/弃用响应头行为

---

## 6. 开发规范：以后每次升级或新特性开发怎么做

建议把下面这份清单作为 PR 自检模板：

1. 这是 additive change 还是 breaking change？
2. 如果是 breaking change，是否已经新开 `v2` 路径/新 schemaVersion？
3. DTO/Event 是否仍能容忍未知字段？
4. 是否保留了旧字段、旧 topic 语义、旧响应结构？
5. 是否需要 feature flag / canary / 分批放量？
6. 是否有对应的兼容性测试与回滚策略？
7. 这次改动是否会让旧 Portal、旧 BFF、旧消费者直接失败？

---

## 7. 仍建议进入下一阶段的增强项

本次已经把共享层安全带补上，但如果要进一步做到“企业级兼容治理”，建议后续继续做：

- OpenAPI diff / schema diff 检查接入 CI
- Kafka schema 兼容报告或 schema registry
- Consumer-driven contract tests
- 灰度放量与 rollback runbook 自动化
- 兼容性指标与告警（schema mismatch、deprecated API traffic、DLT surge）

---

## 8. 结论

当前项目的基础架构本身是兼容性友好的；真正的短板在于“缺少显式演进约束”。  
本次已经把共享层、事件契约、消费者校验、架构测试和文档规范补上，后续各模块做版本升级或新特性开发时，就可以按照统一规则演进，而不是每个团队各自判断。
