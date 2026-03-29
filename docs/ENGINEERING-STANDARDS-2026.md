# Shop Platform — 2026 统一技术栈与微服务 Scaffold 标准

> 版本：1.1 | 日期：2026-03-22
> 适用范围：`api-gateway` / `auth-server` / `buyer-bff` / `seller-bff` / 各 Domain Service / Portal

---

## 一、审查结论（基于当前仓库代码与文档）

### 1.1 已统一且可保留

- 父 POM 已统一核心基线：`Java 25`、`Spring Boot 3.5.11`、`Spring Cloud 2025.0.1`、`Kotlin 2.3.20`。
- 绝大多数服务已统一接入：Actuator + Prometheus + OTEL（`micrometer-tracing-bridge-otel` + `opentelemetry-exporter-otlp`）。
- 领域服务基本统一：`Web + JPA + Flyway + MySQL`。
- `api-gateway` 与 BFF 已在关键 I/O 场景使用 Virtual Threads。

### 1.2 当前主要改进点（需要进入 roadmap）

1) **OpenAPI 基线已建立，统一规范已落地**
- 父 POM 已统一管理 `springdoc` 版本，`auth-server` / `buyer-bff` 已接入依赖，archetype 也已内置 OpenAPI 模板。
- 已制定”网关聚合 + 各服务 OpenAPI Bean + shop-contracts @Schema”统一方案，详见 `docs/API-DOCUMENTATION-SPRINGDOC-2026.md`。
- 待落地：各服务补充 `OpenApiConfig` Bean、关键 DTO 添加 `@Schema`、网关配置 api-docs 聚合路由。

2) **弹性治理已试点，但尚未标准化**
- `buyer-bff` / `seller-bff` 已接入 Resilience4j；当前 `buyer-bff` 已把 checkout 中的 `promotion-service` / `loyalty-service` / `marketplace-service` 纳入 CircuitBreaker 边界，`buyer-bff` / `seller-bff` 也都补齐了 RestClient connect/read timeout。
- 但 Retry / Bulkhead / 补偿持久化重试还没有覆盖所有下游调用。

3) **测试基线薄弱、模块差异较大**
- 当前测试覆盖仍然明显不均衡：`auth-server`、`search-service` 已有针对性测试，部分 domain service 只有少量测试，仍有多个模块接近 0。
- `mvn test` 当前可通过，但对核心链路（下单、补偿、事件一致性）覆盖不足。

4) **工程治理底座已就位，但 CI 仍偏“构建型”**
- Maven Wrapper、Enforcer、GitHub Actions CI、`shop-archetypes/` 都已落地。
- 当前 CI 以 `./mvnw verify` + `docs-site build` 为主，尚未扩展到镜像构建、Kind smoke、契约测试和架构规则测试。

5) **共享基线已成型，但仍需继续消除实现漂移**
- 例如 `buyer-bff` 存在多个 `catch (Exception ...)`，补偿逻辑可用，但错误边界与可观测语义仍需收敛。
- Feature Toggle 已在 `search-service` 试点，但尚未形成更大范围的统一 rollout 策略。

---

## 二、2026 统一技术栈（推荐基线）

## 2.1 平台基线

- JDK：Java 25（团队统一发行版与补丁策略）
- Framework：Spring Boot 3.5.x
- Cloud：Spring Cloud 2025.0.x（严格按兼容矩阵升级）
- Build：Maven + Maven Wrapper + Enforcer + GitHub Actions CI

## 2.2 服务分层标准

- **Edge/Gateway**：Spring Cloud Gateway Server Web MVC + Virtual Threads + Redis-backed 基础限流
- **Auth**：Spring Security + OAuth2 Resource Server + JWT（生产建议迁移到非对称签名与 JWKS）
- **BFF**：Spring MVC + Virtual Threads + Resilience4j + bounded RestClient timeout；非核心依赖默认必须可降级，核心依赖默认必须快速失败
- **Domain（事务型）**：Spring MVC + JPA + Flyway + MySQL（多数 Java 服务已启用 Virtual Threads）
- **Event/Worker（异步型）**：Spring Boot + Kafka + Outbox
- **Portal（过渡态）**：Kotlin + Thymeleaf（中期可分离前后端）

## 2.3 横切能力（所有服务必备）

- 可观测：Actuator + Micrometer + OTLP
- 安全：Trusted Headers + Internal Token（后续演进 mTLS/服务身份）
- 配置：统一 `application.yml` 模板（端口、健康检查、Tracing、内部调用安全）
- 动态特性开关：OpenFeature API + Spring Property Provider + ConfigMap 文件挂载 + Configuration Watcher 热刷新（当前已在 `search-service` 试点）
- API 规范：OpenAPI 3.1 baseline + 统一错误模型 + 版本化路径（`/v1`）；当前 springdoc 尚未在所有服务统一落地

## 2.4 当前仓库的实现状态（与目标态区分）

- **已经可视为平台基线**：Java 25 / Boot 3.5.11 / Cloud 2025.0.1、Maven Wrapper、Enforcer、Actuator、Prometheus、OTLP、结构化日志、Internal Token、Kind/K8s、本地 archetype、Feature Toggle 试点
- **已经开始落地但未统一完成**：springdoc / OpenAPI、Resilience4j、Testcontainers、Feature Toggle 扩散到更多服务
- **仍属演进方向**：OpenRewrite、ArchUnit、契约测试、镜像构建 + Kind smoke CI、mTLS / JWKS / workload identity、Grafana / Tempo / Loki 等完整观测平台

> 技术栈适配性、复用建议与演进方向的权威文档：`docs/TECH-STACK-BEST-PRACTICES-2026.md`
>
> 本仓库的 CI / Makefile / hooks / archetype 开发流程统一说明，见：`docs/DEVELOPER-EXPERIENCE-STANDARD-2026.md`

---

## 三、统一 Scaffold 规范（模块脚手架）

## 3.1 建议的服务类型（Archetype）

- `gateway-service-archetype`
- `auth-service-archetype`
- `bff-service-archetype`
- `domain-service-archetype`
- `event-worker-archetype`
- `portal-service-archetype`（过渡期）

## 3.2 每个 Archetype 必须生成的内容

1) **目录结构**
- `src/main/java|kotlin`, `src/main/resources`, `src/test/...`
- `src/main/resources/db/migration`（仅 domain / worker）

2) **依赖清单**
- 按服务类型预置（含 observability、validation、test）

3) **配置模板**
- `management.endpoints`, `readiness/liveness`, tracing/exporter, internal security

4) **质量与测试模板**
- 单元测试样例 + Slice 测试样例
- Domain/Worker 预置 Testcontainers 集成测试基座

5) **文档模板**
- 模块 README（职责、依赖、接口、本地运行）
- API OpenAPI 输出地址说明

6) **部署模板**
- Dockerfile module 参数
- K8s Deployment/Service/Probe/HPA 基础清单

## 3.3 生成工具链建议

- 以 `maven-archetype-plugin` 维护内部 archetype。
- 当前实现位于仓库 `shop-archetypes/`，包含 6 类 archetype，并已通过样板工程生成与 `mvn test` 验证。
- 以 `start.spring.io` 作为初始脚手架来源（定期刷新）。
- 以 OpenRewrite 配置升级配方，做跨仓批量演进（Boot/Cloud/依赖安全升级）。

---

## 四、落地优先级（摘要）

- **P0（立即）**：scaffold v1、Maven Wrapper、Enforcer、CI 基础流水线、OpenAPI 基线
- **P1（近期）**：BFF 全量 Resilience4j / Retry / Bulkhead 标准化、Testcontainers 基线、契约测试
- **P2（中期）**：OpenRewrite 自动升级、SLO/告警标准化、服务模板 v2

> 详细任务已写入 `docs/ROADMAP-2026.md` 的“平台工程与标准化改进任务池（新增）”。

---

## 五、可从 Meirong Guideline 直接补充的规范（映射到当前项目）

> 参考源：`/Users/matthew/projects/meirongdev/guideline/docs`

### 5.1 文档治理与SSOT（已补充）

- 已建立 `docs/SOURCE-OF-TRUTH-MATRIX.md`，明确“每个主题唯一权威文档”。
- 已建立 `docs/DELIVERY-PRIORITY-AND-DEPENDENCY-MAP.md`，让 Roadmap 从主题列表升级为执行队列。

可迁移来源：
- `docs/SOURCE-OF-TRUTH-MATRIX.md`
- `docs/DELIVERY-PRIORITY-AND-DEPENDENCY-MAP.md`

### 5.2 Parent/BOM 分层治理（建议补充）

- 从“单 parent”演进为“统一 BOM + 角色化 parent（bff/domain/worker/library/contract）”。
- 为 contract/library 限制运行时依赖，避免 Spring Boot 语义泄漏到契约库。

可迁移来源：
- `05-best-practices/meirong-parent-pom-governance-2026.md`

### 5.3 API 规范深化（建议补充）

- 在现有“统一路径/响应模型”基础上补齐：
  - API 版本演进与弃用策略
  - 统一异常分类与映射
  - DTO/校验规范（record + Bean Validation）

可迁移来源：
- `04-specifications/meirong-service-api-specification-2026.md`
- `05-best-practices/java-spring-boot-3.5-guideline.md`

> 兼容性开发规范与本仓库当前支持/缺口/已实现改造，见：`docs/COMPATIBILITY-DEVELOPMENT-STANDARD-2026.md`

### 5.4 可观测性从“采集”升级到“运营”（已补充）

- 除 traces/metrics/logs 采集外，补充：
  - SLO/SLI 模板
  - Error Budget 规则
  - 告警分级与 runbook 链接规范
  - Dashboard-as-Code 要求

可迁移来源：
- `04-specifications/meirong-observability-specification-2026.md`
- `04-specifications/meirong-observability-alerting-guideline-2026.md`

### 5.5 Kafka 与幂等规范体系化（已补充）

- 将“事件驱动”从设计描述升级为工程规范：
  - Consumer 场景分型（金融事务/聚合/通知/状态流转）
  - 重试与 DLQ 策略矩阵
  - DLQ 回放与演练流程
  - HTTP / Kafka 统一幂等 key 与状态机

#### 5.5.1 Consumer 场景分型

| 类型 | 典型服务/场景 | 处理目标 | 重试策略 | DLQ / 回放要求 |
|------|---------------|----------|----------|----------------|
| 金融事务型 | `wallet-service`、支付确认、余额变更 | 严格一致、禁止重复入账 | 只允许有限技术重试；业务冲突直接幂等返回 | 必须可人工审计与补偿，不能“无限自动重试” |
| 状态流转型 | `order-service`、`loyalty-service` 任务推进 | 推进状态机，不重复推进 | 幂等前提下有限重试 | 失败事件需要保留并支持按 key 回放 |
| 聚合 / 投影型 | `search-service`、Dashboard 聚合、缓存刷新 | 最终一致、可重建 | 可接受较积极重试 | 必须支持批量回放或全量重建 |
| 通知 / 集成型 | `notification-service`、`webhook-service` | 尽力送达、可追踪 | 指数退避 + 上限次数 | 必须记录投递历史，并支持重放 |

#### 5.5.2 Retry / DLQ 策略矩阵

| 类型 | 首次失败 | 自动重试 | 进入 DLQ 条件 | 回放方式 |
|------|----------|----------|---------------|----------|
| 金融事务型 | 记录失败原因 + 停止提交副作用 | 最多少量技术重试 | 达到上限或出现不可恢复业务错误 | 走补偿 / 人工审计流程 |
| 状态流转型 | 记录事件与当前状态 | 有限次数重试 | 状态不一致或依赖长期不可用 | 按 `eventId` / 聚合根回放 |
| 聚合 / 投影型 | 记录 offset / eventId | 可相对积极重试 | 结构错误、反序列化失败、长时间不可用 | 支持重放整个时间窗或重新构建索引 |
| 通知 / 集成型 | 记录 delivery log | 指数退避 | 超过最大重试次数 | 按 delivery / event 维度定向重放 |

#### 5.5.3 DLQ 回放与演练流程

最小流程：

1. 明确 DLQ 消息的 `topic / partition / offset / eventId / aggregateId`
2. 判断失败类型：数据错误、依赖不可用、代码缺陷还是配置漂移
3. 先修复根因，再执行回放，禁止“带病反复重放”
4. 回放后核对：
   - 幂等状态是否正确
   - 下游副作用是否只发生一次
   - 指标 / 日志 / Trace 是否闭环

建议每类关键 Consumer 至少保留一种演练路径：

- 单条按 `eventId` 回放
- 按时间窗批量回放
- 对于投影型服务，支持全量重建

#### 5.5.4 HTTP / Kafka 统一幂等规范

**HTTP 命令型接口：**

- 默认使用 `Idempotency-Key` 作为调用幂等键
- 幂等键作用域建议至少包含：`operation + principal + client-request`
- callee 端必须持久化幂等状态，禁止只靠内存防重

推荐状态机：

`RECEIVED -> PROCESSING -> SUCCEEDED / FAILED / EXPIRED`

规则：

- 若同 key 已 `SUCCEEDED`，直接返回已完成结果
- 若同 key 正在 `PROCESSING`，返回“处理中”或安全等待
- 若同 key 已 `FAILED`，由业务决定是否允许显式重试

**Kafka 消费型接口：**

- 默认使用 `eventId`（或 outbox 主键）作为消费幂等键
- 必须在“写入业务副作用”和“标记已处理”之间保证事务边界清晰
- 金融与状态流转型 Consumer 优先使用“幂等表 + 业务状态检查”双保险

推荐状态机：

`RECEIVED -> PROCESSING -> APPLIED / SKIPPED / FAILED`

规则：

- 已 `APPLIED`：重复消息直接跳过
- 已 `SKIPPED`：表示事件过期、顺序落后或已被更高版本状态覆盖
- `FAILED`：必须保留失败原因，供重试 / DLQ / 回放使用

#### 5.5.5 与当前仓库的落地点

- `order-service`、`wallet-service` 已采用 Outbox Pattern，适合作为金融事务型与状态流转型规范样板。
- `wallet-service`、`promotion-service`、`loyalty-service` 已通过 `*IdempotencyConfiguration` 标准化了基于 Redis 的分布式幂等拦截（SET NX 模式）。
- `notification-service`、`webhook-service` 适合作为通知 / 集成型重试样板。
- `search-service` 适合作为聚合 / 投影型回放样板。
- 后续新增 Kafka 消费者时，必须先声明其 consumer 类型，再决定 retry / DLQ / idempotency 策略。

#### 5.5.6 当前仓库的实际异常处理落地（2026-03-23）

- `shop-common` 提供统一的 `RetryableKafkaConsumerException` / `NonRetryableKafkaConsumerException` 语义，供业务 listener 显式声明“有限重试”与“直接 DLT”边界。
- `search-service` 的 `ProductEventConsumer` 继续作为 **聚合 / 投影型** 样板：索引失败走 `@RetryableTopic` 有限重试，最终进入 DLT；该类消息支持按时间窗重放或全量重建索引。
- `promotion-service`、`loyalty-service` 的 listener 现在归为 **幂等业务型 / 状态推进型**：
  - 反序列化失败、字段缺失、业务拒绝等 poison pill 直接抛 `NonRetryableKafkaConsumerException`，跳过热重试并进入 DLT
  - `DataAccessException`、`RestClientException` 等瞬时依赖失败抛 `RetryableKafkaConsumerException`，交给 Kafka 做有限重试
  - 业务副作用仍由幂等表与状态检查兜底，避免重试放大重复发券/重复积分
- `notification-service`、`webhook-service` 的 listener 现在归为 **持久化投递型 / 集成型**：
  - 解析错误、契约缺失（如 `eventId` / `type` / 关键业务字段为空）直接进入 DLT，避免静默丢失坏消息
  - 合法消息进入服务内持久化链路后，真实发送失败不再依赖 Kafka 热重试，而是分别落到 `notification_log` / `webhook_delivery`，再由 scheduler 按指数退避继续投递
  - 这类消费者的 Kafka retry 只覆盖“还没成功落库/建投递记录”之前的瞬时基础设施故障

可迁移来源：
- `03-architecture-patterns/meirong-idempotency-guideline-2026.md`
- `04-specifications/meirong-kafka-consumer-guideline-2026.md`
- `04-specifications/meirong-kafka-serialization-guideline-2026.md`

### 5.6 架构门禁自动化（建议补充）

- 以 ArchUnit 把编码/架构红线转成 CI 可执行测试。
- 使用 freeze 机制处理历史债务，避免新违规进入主干。

可迁移来源：
- `04-specifications/meirong-archunit-guideline-2026.md`

### 5.7 安全基线分层表达（已补充）

- 明确北南向与东西向安全边界：
  - Gateway trusted headers
  - workload identity + NetworkPolicy + least privilege
  - mTLS 作为增强而非一次性硬切

可迁移来源：
- `06-security/meirong-api-security-guideline-2026.md`

### 5.8 K8s 应用交付契约（已补充）

- 建立“应用团队 -> 平台团队”的交付契约（probes、资源、HPA/PDB/NetworkPolicy 输入）。
- 将 `startupProbe` 明确为按需启用，避免一刀切。

可迁移来源：
- `08-deployment/application-contract-k8s.md`

### 5.9 服务准入清单（已补充）

新增服务或拆分新模块前，至少回答以下问题：

1. **边界清晰吗？**  
   是否是真正的新 bounded context，而不是把已有服务再拆碎。

2. **入口类型是什么？**  
   是 Gateway / Auth / BFF / Domain / Worker / Portal 中的哪一类。

3. **北向暴露方式是什么？**  
   是否必须经 `api-gateway`；是否需要 JWT；是否只是内部面。

4. **东西向依赖有哪些？**  
   下游 HTTP / Kafka 依赖列表是否明确；是否会引入环形依赖。

5. **数据所有权归谁？**  
   是否有独立数据库 / schema；是否只允许本服务写本库。

6. **异步模型是什么？**  
   是否需要 outbox；消费属于哪类 consumer；重试 / DLQ / 幂等策略是否定义。

7. **运行契约是否齐备？**  
   是否满足 `8080/8081`、健康检查、Prometheus、OTLP、结构化日志、internal security 基线。

8. **质量门禁是否齐备？**  
   是否有最小测试集；是否可复用 archetype；是否需要契约测试 / Testcontainers。

9. **文档是否齐备？**  
   是否已更新 Roadmap、SSOT、部署文档、服务说明。

10. **验证路径是什么？**  
    是否能通过本地测试、Kind/K8s smoke check 或回放验证完成闭环。

> 如果以上问题有关键项无法回答，则不应直接创建新服务。

### 5.10 平台工程看板（已补充）

建议平台工程看板最少跟踪以下指标：

| 指标 | 说明 | 用途 |
|------|------|------|
| 模板采纳率 | 新模块中使用 `shop-archetypes` 生成的占比 | 判断 scaffold 是否真正落地 |
| 服务合规率 | 满足端口、探针、metrics、tracing、安全基线的服务占比 | 判断平台标准执行情况 |
| 发布失败率 | CI / Kind / K8s 发布失败占比 | 观察交付稳定性 |
| 文档漂移数 | 代码/运行方式变化但 SSOT 未更新的次数 | 约束文档治理 |
| 关键 SLO 违约次数 | 支付、下单、事件延迟等核心指标违约次数 | 连接平台治理与业务结果 |

看板更新建议：

- 周级：模板采纳率、文档漂移数
- 发布级：发布失败率、服务合规率
- 运行级：关键 SLO 违约次数

> 看板的目标不是“展示数据”，而是给平台工程任务排序提供依据。

### 5.11 Feature Toggle 与配置热更新基线（已补充）

- 统一特性开关抽象层为 **OpenFeature API**
- 第一阶段 Provider 使用仓库内 Spring Property Provider，避免过早引入外部控制面
- K8s 下的热更新链路统一为：
  - ConfigMap **目录挂载**
  - `spring.config.import=optional:file:...`
  - Spring Cloud Kubernetes Configuration Watcher
  - `/actuator/refresh`
  - mutable `@ConfigurationProperties` 重新绑定
- 禁止把需要热更新的配置通过 `envFrom` 暴露为环境变量
- 禁止用 `subPath` 挂载需要热更新的 ConfigMap 文件
- 当需要实验平台、分群、百分比放量、审计时，再将 OpenFeature Provider 演进为 `flagd` 或 Unleash

权威设计文档：`docs/FEATURE-TOGGLE-AND-CONFIG-RELOAD.md`

### 5.12 2026 技术栈最佳实践与复用指南（已补充）

- 新增 `docs/TECH-STACK-BEST-PRACTICES-2026.md` 作为技术栈选型、最佳实践判断与演进方向的权威文档
- 要求文档明确区分：
  - 当前代码已实现的基线
  - 已进入 roadmap 但尚未统一完成的能力
  - 仅作为未来目标态的方向
- 对外文档站同步提供 `docs-site/docs/tech-stack/best-practices.md` 作为学习入口

---

## 六、参考依据（可访问）

- Spring Boot Reference: https://docs.spring.io/spring-boot/reference/
- Java 25 Docs: https://docs.oracle.com/en/java/javase/25/
- Spring Boot Observability: https://docs.spring.io/spring-boot/reference/actuator/observability.html
- Spring Cloud 项目页: https://spring.io/projects/spring-cloud
- Spring Cloud Supported Versions: https://github.com/spring-cloud/spring-cloud-release/wiki/Supported-Versions
- Spring Cloud Gateway: https://spring.io/projects/spring-cloud-gateway
- Spring Security Reference: https://docs.spring.io/spring-security/reference/
- OpenAPI Specification: https://spec.openapis.org/oas/latest.html
- OpenAPI Initiative: https://www.openapis.org/
- springdoc: https://springdoc.org/
- OpenFeature: https://openfeature.dev/docs/reference/technologies/server/java/
- OpenTelemetry Docs: https://opentelemetry.io/docs/
- Micrometer: https://micrometer.io/
- Resilience4j: https://resilience4j.readme.io/docs
- Meilisearch Docs: https://www.meilisearch.com/docs
- Maven Enforcer Plugin: https://maven.apache.org/enforcer/maven-enforcer-plugin/
- Maven Archetype Plugin: https://maven.apache.org/archetype/maven-archetype-plugin/
- Spring Initializr: https://start.spring.io/
- Testcontainers Java Guide: https://testcontainers.com/guides/getting-started-with-testcontainers-for-java/
- OpenRewrite: https://docs.openrewrite.org/
- Spring Cloud Kubernetes Configuration Watcher: https://docs.spring.io/spring-cloud-kubernetes/reference/spring-cloud-kubernetes-configuration-watcher.html
- Kind: https://kind.sigs.k8s.io/
- Docusaurus: https://docusaurus.io/docs
- GitHub Actions Docs: https://docs.github.com/actions
