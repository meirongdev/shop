# Shop Platform — Source of Truth Matrix

> 版本：1.0 | 更新时间：2026-03-22

---

## 一、使用规则

> 注意：归档的文档已移动到 docs/archived/，请参见 docs/README.md 索引。

- 一个主题只能有一个权威文档（Single Source of Truth, SSOT）。
- 次级文档可以做导航、摘要、教程和阶段性说明，但不得替代权威文档。
- 当实现、接口、部署方式或交付优先级发生变化时，先更新权威文档，再同步次级文档。
- 涉及运行行为的变更，文档中必须补充验证方式（测试、命令或 smoke check）。

### 权威文档清单（SSOT）

以下文档在本仓库被视为主题权威文档（位于 docs/ 根目录），如需调整请更新本文件：

- docs/API-DOCUMENTATION-SPRINGDOC-2026.md
- docs/ARCHUNIT-RULES.md
- docs/COMPATIBILITY-DEVELOPMENT-STANDARD-2026.md
- docs/CONTRACT-TESTING-GUIDE.md
- docs/DEVELOPER-EXPERIENCE-STANDARD-2026.md
- docs/ENGINEERING-OPTIMIZATION-ROADMAP.md
- docs/SERVICE-TECH-STACK-AND-EXTENSIBILITY.md

---

## 二、主题级权威文档矩阵

| 主题 | 权威文档 | 次级/派生文档 | 何时必须更新 |
|------|----------|---------------|--------------|
| 产品范围、阶段状态、任务完成度 | `docs/ROADMAP-2026.md` | `docs/handoff/PROJECT-STATUS.md` | 新功能完成、任务状态变化、阶段范围调整 |
| 平台工程基线、脚手架标准、质量门禁 | `docs/ENGINEERING-STANDARDS-2026.md` | `shop-archetypes/README.md` | 技术栈、模板标准、测试/质量基线变化 |
| 技术栈选型、最佳实践与演进方向 | `docs/TECH-STACK-BEST-PRACTICES-2026.md` | `docs-site/docs/tech-stack/best-practices.md`、`docs-site/docs/engineering/standards.md` | 技术栈版本、平台能力现状、最佳实践判断、演进方向变化 |
| 系统架构与服务拓扑 | `docs-site/docs/architecture/index.md` | `docs/ARCHITECTURE-DESIGN.md`、`docs/services/SERVICE-DEPENDENCY-MAP.md` | 新增服务、边界调整、网关路由变化 |
| 本地开发、Kind/K8s 部署、mirrord 调试与 smoke 流程 | `docs-site/docs/getting-started/local-deployment.md` | `kind/setup.sh`、`kind/teardown.sh`、`k8s/**`、`Tiltfile`、`.mirrord/**`、`docs/superpowers/specs/2026-03-28-local-cicd-concepts.md` | 部署步骤、环境变量、访问方式、端口、调试命令或验证命令变化 |
| 可观测性基线（指标、追踪、SLO、告警） | `docs/OBSERVABILITY-ALERTING-SLO.md` | `docs-site/docs/engineering/observability.md`、`docs/ENGINEERING-STANDARDS-2026.md` | 新指标、Tracing 方案、Prometheus/OTLP 暴露方式变化 |
| 安全边界（北南向 / 东西向） | `docs/SECURITY-BASELINE-2026.md` | `docs-site/docs/architecture/index.md`、`docs/ARCHITECTURE-DESIGN.md` | JWT、Trusted Headers、内部 token、安全边界变化 |
| Feature Toggle 与配置热更新基线 | `docs/ENGINEERING-STANDARDS-2026.md` | `docs/ENGINEERING-STANDARDS-2026.md`、`docs-site/docs/getting-started/local-deployment.md`、`k8s/**` | 开关框架、ConfigMap 注入方式、refresh 链路、vendor 选择变化 |
| K8s 应用交付契约 | `docs/deployment/APPLICATION-CONTRACT-K8S.md` | `docs-site/docs/getting-started/local-deployment.md`、`k8s/apps/base/platform.yaml`、`k8s/apps/overlays/dev` | 新服务接入 K8s、探针、端口、配置注入方式变化 |
| 服务依赖关系 | `docs/services/SERVICE-DEPENDENCY-MAP.md` | `docs-site/docs/architecture/index.md` | 新增同步/异步调用、事件订阅关系变化 |
| 服务级功能说明 | `docs/services/*.md`（对应服务文档） | `docs-site/docs/services/*.md` | 服务职责、接口、事件、数据流变化 |
| 新用户增长链路 | `docs/services/new-user-onboarding.md` | `docs/services/loyalty-service.md`、`docs/services/promotion-service.md`、`docs/services/profile-service.md` | 注册赠礼、引导任务、欢迎券、欢迎邮件流程变化 |
| 交付优先级与依赖队列 | `docs/DELIVERY-PRIORITY-AND-DEPENDENCY-MAP.md` | `docs/ROADMAP-2026.md` | 新任务进入执行队列、依赖顺序调整 |
| 会话交接与执行上下文 | `docs/handoff/PROJECT-MAP.md` | `docs/handoff/PROJECT-STATUS.md`、`docs/handoff/CONTINUATION-CHECKLIST.md` | 交接对象、当前 workstream、执行约束变化 |
| Marketplace 搜索与商品列表架构 | `docs/MARKETPLACE-SEARCH-DESIGN.md` | `docs-site/docs/services/search.md` | 搜索架构、商品展示逻辑、筛选排序策略变化 |
| 运维 Runbooks - 高错误率 | `docs/runbooks/HIGH_ERROR_RATE.md` | N/A | 诊断流程、应急处理步骤变化 |
| 运维 Runbooks - Kafka 延迟 | `docs/runbooks/KAFKA_LAG.md` | N/A | 诊断流程、应急处理步骤变化 |
| 运维 Runbooks - 支付失败 | `docs/runbooks/PAYMENT_FAILURE.md` | N/A | 诊断流程、应急处理步骤变化 |
| 运维 Runbooks - 服务下线 | `docs/runbooks/SERVICE_DOWN.md` | N/A | 诊断流程、应急处理步骤变化 |
| 工程优化路线图（开发效率 + 架构质量 + 云原生） | `docs/ENGINEERING-OPTIMIZATION-ROADMAP.md` | `docs/ROADMAP-2026.md`、`docs/ENGINEERING-STANDARDS-2026.md` | 新优化项提出、优先级调整、ADR 变更 |
| 契约测试实施指南 | `docs/CONTRACT-TESTING-GUIDE.md` | `docs/ARCHUNIT-RULES.md`（相关规则补充） | 契约测试工具变更、流程调整、最佳实践更新 |
| 公共 Client 与 Tracing 传播设计 | `docs/superpowers/specs/2026-04-13-common-client-and-tracing-design.md` | `docs/ENGINEERING-STANDARDS-2026.md` | 客户端抽取、Tracing/Baggage 传播标准变化 |

---

## 三、文档冲突处理规则

### 3.1 冲突优先级

1. 主题权威文档优先  
2. 代码与验证结果优先于过期叙述  
3. `docs-site/` 中的教程类文档需要回指到权威文档，而不是复制规则

### 3.2 推荐更新顺序

1. 更新实现与验证
2. 更新主题权威文档
3. 更新导航/教程/派生文档
4. 在 `docs/ROADMAP-2026.md` 或交接文档中同步状态

---

## 四、落地约束

- 新增文档前，先确认是否已有同主题 SSOT；避免重复造文档。
- 如果一个主题需要多份文档，必须明确“权威文档”和“教程/示例文档”的角色分工。
- 对外暴露的运行步骤优先落在 `docs-site/docs/`，对内工程标准与执行规则优先落在 `docs/`。
