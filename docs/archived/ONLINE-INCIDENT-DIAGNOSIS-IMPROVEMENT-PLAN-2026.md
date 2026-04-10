# Online Incident Diagnosis Improvement Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 在现有 observability 平台基础上，补齐链路关联、场景化 dashboard、告警到 runbook 的闭环，以及 incident-oriented 质量门禁，把核心线上问题从“收到告警/报障”到“定位根因”的时间压缩到 10 分钟以内。

**Architecture:** 不重复建设 Loki / Tempo / Grafana / Prometheus 采集平台，而是在现有采集基线之上增加一层“诊断治理层”：统一诊断契约、按业务链路组织的 dashboard、告警元数据与 runbook 绑定、异步链路可定位性、以及防止诊断能力回退的自动化门禁。

**Tech Stack:** Spring Boot 3.5, Micrometer Tracing, OpenTelemetry, Prometheus, Loki, Tempo, Grafana, Bash, Git hooks, Maven, Gradle, Playwright

---

## 1. 背景

当前仓库已经具备较完整的采集基线：

- Gateway 已返回 `X-Request-Id` 与 `X-Trace-Id`
- 各服务已暴露 `/actuator/prometheus`、OTLP traces、OTLP logs
- `shared/shop-common` 已统一 RFC 7807 `ProblemDetail`
- Kind / Kubernetes 已部署 Prometheus、Loki、Tempo、Grafana、Pyroscope、告警规则和 SLO 规则
- Git hooks 与 `platform/scripts/run-local-checks.sh` 已提供基础质量门禁

当前瓶颈已经不是“看不到信号”，而是“拿到信号后仍然定位太慢”。平台已经能采集 logs / metrics / traces，但从“告警触发”到“工程师知道应该看哪个 dashboard、搜哪个 trace、判断哪个下游在坏、下一步如何止血”之间，仍有明显治理缺口。

---

## 2. 当前缺口

### 2.1 诊断契约还不完整

- Gateway 已有关联头，但北向错误返回体还缺少稳定的一线排障字段，例如 `requestId`、`service`、`operation`、`retryable`、`downstreamService`
- `ShopProblemDetails` 当前只有 `code`、`message`、`traceId`，对一线值班定位仍然不够
- HTTP 链路的关联性强于异步链路。Kafka consumer、scheduled job、retry job、compensation task、outbox pipeline 还没有统一的“如何找到失败工作单元”契约

### 2.2 Dashboard 和告警更偏平台视角，不够贴近故障视角

- 仓库已经有 service-health / business-overview dashboard 和一批 alert rules，但缺少真正按故障场景组织的排障 dashboard
- 目前没有强约束要求每个 P1/P2 告警必须带 dashboard 和 runbook
- Alertmanager / 值班通知闭环在现有文档里仍然属于后续演进项

### 2.3 异步与后台任务的定位成本高于同步 HTTP 链路

- 当前已有 lag 告警，但 outbox、DLQ、compensation retry、webhook retry、notification retry、subscription renewal 等场景还没有统一的根因面板
- 部分后台 worker 仍然存在 stub / 占位实现，这会削弱告警分型、runbook 和人工介入路径的质量

### 2.4 当前质量门禁更擅长保障“能编译、能构建”，不擅长保障“出了事能定位”

- 现有 hook 主要覆盖语法、空格、平台验证和模块 verify
- 当前平台验证只证明 Grafana / Prometheus / Loki / Tempo 可达，还不能证明一次失败请求能被完整串到 headers、logs、trace 和 dashboard
- 当前已有关键契约测试，但还没有专门保护 observability semantics 的回归门禁，例如关联头、problem-details 字段、告警元数据、runbook 链接

---

## 3. 目标状态

目标状态不是“再多堆几个面板”，而是形成一条更短的故障定位路径：

1. 用户报障、`requestId`、`traceId`、`orderId` 或告警名称中的任意一个，都足以在 10 分钟内定位到具体故障链路
2. 每个核心 P1/P2 告警都能直接指向 dashboard、runbook、owner 和推荐的第一条查询入口
3. 每条核心北向链路与重要异步链路都拥有清晰的诊断面：
   - headers
   - 结构化错误体
   - logs
   - trace
   - metrics
   - alert
   - runbook
4. 一旦关键链路丢失上述诊断面，CI 或本地门禁应直接失败，而不是等到线上再暴露

---

## 4. 优化工作流

### 4.1 工作流 A：统一诊断契约

**目的：** 让每次 incident 都从一个稳定的上下文对象开始，而不是靠临场猜测日志关键字。

**需要交付：**

- 扩展北向错误返回体，使一线排障能直接看到：
  - `traceId`
  - `requestId`
  - `code`
  - `service`
  - `operation`
  - `retryable`
  - `downstreamService`
- 统一 Gateway、BFF、domain service 的 MDC / structured log 字段：
  - `requestId`
  - `traceId`
  - `buyerId`
  - `sellerId`
  - `orderId`
  - `subscriptionId`
  - `eventId`
  - `jobName`
- 定义异步链路关联规则，至少覆盖：
  - Kafka producer headers
  - Kafka consumer logs
  - scheduled jobs
  - compensation tasks
  - outbox dispatchers

**建议落点：**

- `services/api-gateway/src/main/java/dev/meirong/shop/gateway/filter/TrustedHeadersFilter.java`
- `services/api-gateway/src/main/java/dev/meirong/shop/gateway/filter/TraceCorrelationResponseFilter.java`
- `shared/shop-common/shop-common-core/src/main/java/dev/meirong/shop/common/web/ShopProblemDetails.java`
- `shared/shop-common/shop-common-core/src/main/java/dev/meirong/shop/common/web/GlobalExceptionHandler.java`
- `shared/shop-common/shop-common-core/src/main/java/dev/meirong/shop/common/trace/`
- 各服务 `application.yml` 中的 correlation 字段配置

**验收标准：**

- 一次失败的 HTTP 请求，能从浏览器响应头直接串到 Grafana logs 和 Tempo trace
- 一次失败的异步工作单元，能通过 event ID 或业务 ID 直接检索到日志和对应面板

### 4.2 工作流 B：建设面向故障场景的 Dashboard 与告警元数据

**目的：** 从“平台是否健康可见”升级为“具体故障如何定位可见”。

**需要交付：**

- 按核心排障场景建设或重构 dashboard：
  - login / auth
  - buyer checkout
  - payment confirm
  - search
  - notification delivery
  - webhook delivery
  - subscription renewal
  - activity reward dispatch
- 为每个 P1/P2 告警补齐：
  - severity
  - owner
  - dashboard URL 或 dashboard UID
  - runbook URL
  - diagnosis hint
- 建立简洁清晰的告警分类：
  - 用户面同步故障
  - 异步积压或重试风暴
  - 下游依赖退化
  - 资源面饱和

**建议落点：**

- `platform/k8s/observability/prometheus/alert-rules.yaml`
- `platform/k8s/observability/prometheus/slo-rules.yaml`
- `platform/k8s/observability/grafana/`
- `docs/OBSERVABILITY-ALERTING-SLO.md`
- 新增 `docs/runbooks/`

**验收标准：**

- 每个 P1/P2 告警都能落到一个明确 dashboard 和一个明确 runbook
- 值班同学不需要仅凭服务名去猜该打开哪个面板

### 4.3 工作流 C：让异步和后台任务具备一等公民级诊断面

**目的：** 避免后台任务故障长期处于“只能看到 lag，不知道为什么坏”的状态。

**需要交付：**

- 为以下场景补齐诊断指标和面板：
  - outbox lag
  - compensation task queue depth 和 oldest age
  - webhook retry backlog
  - notification retry backlog
  - subscription renewal failures
  - activity reward dispatch backlog / failure count
  - Kafka DLQ message rate / oldest age
- 区分以下类型的告警：
  - consumer stopped
  - consumer slow
  - retry storm
  - compensation stuck
- 为 replay / skip / retry / manual reconcile 写清 runbook

**建议落点：**

- `services/activity-service/src/main/java/dev/meirong/shop/activity/service/RewardDispatcher.java`
- `services/subscription-service/src/main/java/dev/meirong/shop/subscription/service/SubscriptionRenewalService.java`
- `services/webhook-service/`
- `services/notification-service/`
- `shared/shop-common` metrics helper
- `platform/k8s/observability/prometheus/alert-rules.yaml`

**验收标准：**

- 每个关键异步 incident 都同时具备“队列是否积压”和“这批 item 为什么失败”两个视角
- 值班同学能在几分钟内区分是消费停滞、慢消费还是 poison-message 循环

### 4.4 工作流 D：补上面向 incident readiness 的质量门禁与测试

**目的：** 防止诊断能力在后续迭代中悄悄回退。

**需要交付：**

- 将平台验证从“组件可达”提升为“诊断链路可用”：
  - `verify-observability.sh` 至少验证一条 request-level correlation path
  - smoke test 断言 `X-Request-Id` 与 `X-Trace-Id`
  - 关键错误路径测试断言 problem-details 关联字段
- 新增以下回归覆盖：
  - correlation headers
  - error payload contract
  - Prometheus rule files 中关键 alerts 存在
  - 必需 dashboard 已 provisioning
  - P1/P2 alerts 带 runbook 链接
- 引入轻量级 incident readiness checklist，新关键链路必须具备：
  - metric
  - trace
  - structured logs
  - dashboard
  - alert
  - runbook
  - smoke / contract test

**建议落点：**

- `platform/scripts/verify-observability.sh`
- `platform/scripts/smoke-test.sh`
- `.githooks/pre-commit`
- `.githooks/pre-push`
- `platform/scripts/run-local-checks.sh`
- gateway / BFF contract tests
- 新增 incident readiness checklist 文档

**验收标准：**

- 删除关联头、破坏 problem-details 契约、或丢失必需 dashboard 的改动，必须在 merge 之前失败

---

## 5. 交付阶段

### Phase 0：基线盘点与优先级裁剪

**目标：** 明确第一批必须快速定位的 incident 集合。

**范围：**

- 盘点当前平台最常见或最昂贵的线上故障类型：
  - login failure
  - checkout failure
  - payment confirmation failure
  - search degradation
  - notification backlog
  - webhook backlog
  - subscription renewal failure
- 从中挑出首批 3 条 golden diagnosis set

**退出标准：**

- golden diagnosis set 已经明确写下并分配 owner

### Phase 1：强化关联契约

**目标：** 北向与异步故障都具备足够上下文，停止盲搜日志。

**范围：**

- 在 problem-details 中补齐 `requestId` 和 service metadata
- 统一 structured logs 字段和异步关联键
- 回填 gateway 关联头、错误返回契约相关测试

**退出标准：**

- 一次失败请求和一次失败异步 item 都能从稳定标识直接追到根因链路

### Phase 2：补齐 Dashboard 与告警闭环

**目标：** 告警本身就能指向行动。

**范围：**

- 为 golden diagnosis set 建立 incident dashboard
- 给所有 P1/P2 告警补 dashboard / runbook 元数据
- 对需要从原始 PromQL 才能看懂的场景，补充更清晰的 SLO 或 burn-rate 规则

**退出标准：**

- 值班同学能够一跳进入正确 dashboard 和 runbook

### Phase 3：深化异步与后台任务可定位性

**目标：** 后台任务故障不再是二等公民。

**范围：**

- 为 outbox、compensation、webhook、notification、subscription 等链路增加 backlog、retry、oldest-age、failure-cause 视图
- 为 replay、reconcile、skip、retry 编写 runbook

**退出标准：**

- 异步 incident 同时暴露“队列状态”和“item 失败原因”

### Phase 4：把 incident readiness 并入质量门禁

**目标：** 诊断能力成为 definition-of-done，而不是口头约定。

**范围：**

- 扩展 `verify-observability.sh`
- 在本地和 CI 中加入 observability semantics checks
- 将 incident readiness checklist 收敛到工程标准中

**退出标准：**

- 关联契约、dashboard provisioning、alert metadata 和 runbook 链接都受自动化门禁保护

---

## 6. 成功指标

| 指标 | 当前问题 | 目标 |
|------|------|------|
| 从告警到第一条有效 trace/log 的平均时间 | 仍依赖人工猜测 | golden diagnosis set < 5 分钟 |
| 从告警到隔离出故障组件的平均时间 | 容易陷入按服务名盲查 | golden diagnosis set < 10 分钟 |
| 带 dashboard 链接的 P1/P2 alerts 占比 | 不一致 | 100% |
| 带 runbook 链接的 P1/P2 alerts 占比 | 不一致 | 100% |
| 核心北向错误返回稳定 incident fields 的覆盖率 | 部分覆盖 | 100% |
| 关键异步链路具备 backlog + oldest-age + failure-reason 三类信号的覆盖率 | 部分覆盖 | 100% |
| 平台验证能够证明 request-level correlation path 可用 | 当前缺失 | 必须纳入平台验证 |

---

## 7. 建议修改的仓库落点

这份方案不要求再搭一套新 observability 平台，重点是对当前平台补治理和验证。

**建议优先纳入实施计划的文件：**

- `shared/shop-common/shop-common-core/src/main/java/dev/meirong/shop/common/web/ShopProblemDetails.java`
- `services/api-gateway/src/main/java/dev/meirong/shop/gateway/filter/TrustedHeadersFilter.java`
- `services/api-gateway/src/main/java/dev/meirong/shop/gateway/filter/TraceCorrelationResponseFilter.java`
- `platform/scripts/verify-observability.sh`
- `platform/scripts/smoke-test.sh`
- `platform/k8s/observability/prometheus/alert-rules.yaml`
- `platform/k8s/observability/prometheus/slo-rules.yaml`
- `platform/k8s/observability/grafana/`
- `.githooks/pre-push`
- `platform/scripts/run-local-checks.sh`
- 新增 `docs/runbooks/`

---

## 8. 非目标

除非直接服务于诊断速度，否则以下内容不应混入本次计划：

- 完整 service mesh 落地
- 多集群生产级 HA 架构
- 长期存储成本治理
- 无关业务功能开发
- 与 incident readiness 无关的大规模 CI/CD 重构

---

## 9. 实施建议

建议首批 golden diagnosis set 选择：

- `checkout`
- `payment confirmation`
- `notification/webhook backlog`

这个顺序更适合当前仓库，因为：

- 这几条链路已经有一定采集基础，能较快做出效果
- 同时覆盖了同步与异步 incident
- 横跨 Gateway、BFF、domain service 与 platform scripts，能验证方案是否真正打通
- 能最快减少值班同学的“凭经验猜测”成本

质量门禁轨道应在首批诊断契约落地后立刻跟进。如果新的诊断面不被测试和验证脚本保护，很快就会在后续迭代中再次漂移。

---

## 10. 已完成工作 (2026-04-06)

### 统一诊断契约 (Workflow A)
- [x] 更新 `TraceIdExtractor` 支持 `requestId` 提取。
- [x] 扩展 `ShopProblemDetails` 包含 `requestId`, `service`, `operation`, `retryable`, `downstreamService` 字段。
- [x] 实现 `CorrelationFilter` (shared) 统一生成/提取 `requestId` 并注入 MDC。
- [x] 实现 `TraceCorrelationResponseFilter` (shared) 统一在响应头返回关联 ID。
- [x] 更新 Gateway `TrustedHeadersFilter` 支持更多业务标识 (`buyerId`, `sellerId`, `username`, `portal`) 注入 MDC。
- [x] 实现 `TracingHeaderMdcFilter` (shared) 支持从下游 Header 还原 MDC。
- [x] 实现 `HeaderPropagationInterceptor` 支持 `RestClient` 自动透传关联 Header。
- [x] 更新 `ResilienceHelper` 支持在 MDC 中自动记录 `downstreamService` 和 `retryable` 状态。

### Dashboard 与告警闭环 (Workflow B)
- [x] 为 P1/P2 告警补齐 `owner`, `dashboard_url`, `runbook_url`, `diagnosis_hint` 元数据。
- [x] 创建核心 Runbook 模板: `docs/runbooks/SERVICE_DOWN.md`。

### 异步链路诊断 (Workflow C)
- [x] 扩展 `MetricsHelper` 支持 `gauge` 指标。
- [x] 在 `RewardDispatcher` 中集成指标监控，涵盖积压量与分型执行结果。

### 质量门禁 (Workflow D)
- [x] 更新 `smoke-test.sh` 自动化断言关联头与错误体契约。
- [x] 更新 `verify-observability.sh` 自动化验证告警规则元数据。
- [x] 在 `run-local-checks.sh` 中集成观测性回归检查。

