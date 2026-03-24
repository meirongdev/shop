# Shop Platform — Delivery Priority and Dependency Map

> 版本：1.2 | 更新时间：2026-03-23

---

## 一、交付排序原则

- 先补齐低阶段（Phase 1/2/3）的主链路缺口，再推进高阶段扩展能力。
- 平台工程任务可以并行推进，但不能削弱购物主链路的验证优先级。
- 每个任务完成时都必须同时落地：实现、验证、文档更新。
- 任何“可模板化/可标准化”的能力，优先沉淀到平台基线（脚手架、规范、测试模板、运维文档）。

---

## 二、当前阶段状态

| 交付层 | 状态 | 已完成 | 主要剩余 |
|--------|------|--------|----------|
| 平台工程 P0 | ✅ | Maven Wrapper、Enforcer、CI、OpenAPI、Kind 开发环境、`shop-archetypes` | 无 |
| 基础设施 & 可观测性增强 | 🟡 | Prometheus、OTEL Collector（采集层）、结构化日志、Meilisearch、Mailpit | Garage S3、Loki、Tempo、Grafana、Alert Rules、Kafka lag 监控 |
| Phase 1 核心购物闭环 | ✅ | 订单状态机、游客下单、支付回调、订单追踪、游客购物车 Redis + 合并 + CORS | 无 |
| Phase 2 通知服务 | ✅ | `notification-service`、模板邮件、幂等重试 | 无 |
| Phase 3 用户增长 | 🟡 | Google 社交登录、新用户福利、签到/兑换、积分到期批处理、积分抵扣（含 CB 降级）、积分 Hub 页（含商城）、coupon_template/instance 拆分、订阅与 webhook 已就位 | Apple/SMS 登录、注册成功页/邀请裂变 |
| 平台工程 P1 | 🟡 | Testcontainers 模板、SSOT/依赖图文档、Resilience4j 4 服务 CB + BFF 全局超时 | 契约测试、异常语义收敛、业务指标埋点、补偿持久化、Kafka 幂等规范、ArchUnit |
| Phase 4 参与与变现 | 🟡 | `activity-service` 4 种已实现玩法 + AntiCheatGuard、活动广场 + 详情页前端、多商家市场、搜索基础能力 + Feature Toggle | 搜索增强、AI 推荐、支付扩展、Quiz/SlashPrice/GroupBuy |
| Phase 5 平台扩张 | ⬜ | 仅基础能力预留 | 商家入驻、直播、内容社区、跨境能力 |
| Phase 6 AI & 生态 | ⬜ | 仅基础能力预留 | AI 购物助手、开放平台、订阅深化 |

---

## 三、主题级依赖图

```text
平台工程 P0（✅ 已完成）
│
├─> [并行 A] 基础设施 & 可观测性增强（独立 track，不阻塞业务）
│   ├─> Garage S3 部署（Kind infra）
│   ├─> Loki（接 Garage S3）+ Tempo（接 Garage S3）部署
│   ├─> Grafana 部署（接 Prometheus + Loki + Tempo）
│   ├─> OTEL Collector：debug → Tempo exporter 替换
│   ├─> Prometheus alert rules（P1/P2 告警落地）
│   └─> Kafka consumer lag 监控（consumergroup_lag 指标）
│
├─> [并行 B] 平台工程 P1（质量/治理）
│   ├─> 业务指标埋点（shop_* Counter/Timer，按 OBSERVABILITY-ALERTING-SLO.md）
│   ├─> 补偿持久化（coupon 核销 / 库存回补 / 积分退还 → outbox + 定时重试）
│   ├─> 关键链路契约测试
│   ├─> 异常语义收敛（CommonErrorCode 标准化）
│   ├─> Kafka 幂等规范 + ArchUnit 规则
│   └─> Resilience4j 剩余（Retry / Bulkhead / 持久化补偿）
│
├─> Phase 3 收尾（业务优先级最高）
│   ├─> 社交登录补齐（Google ✅，Apple Sign-In / SMS OTP 待补 — 无任何实现）
│   └─> 注册成功页 + 邀请裂变（无 /register endpoint，无 referral 代码）
│
└─> Phase 4 扩展（Phase 3 收尾后全面推进）
    ├─> 搜索增强（向量/语义/多语言）→ Kafka → Garage S3 行为数据沉淀 → AI 推荐
    ├─> 支付扩展（Apple Pay / Google Pay / PayPal / BNPL）
    └─> [最低优先级] 游戏新类型（Quiz / SlashPrice / GroupBuy）
```

---

## 四、推荐执行队列

### 4.1 第一优先级：基础设施 & 可观测性闭环（并行 track，不阻塞业务）

1. **Garage S3** — 部署到 Kind（`k8s/infra/base.yaml`），提供 S3 兼容 API
2. **Loki + Tempo** — 接入 Garage S3 后端，替换 OTEL Collector debug exporter
3. **Grafana** — 接入 Prometheus（指标）+ Loki（日志）+ Tempo（链路），统一可视化
4. **Prometheus Alert Rules** — 落地 OBSERVABILITY-ALERTING-SLO.md P1/P2 告警
5. **Kafka consumer lag 监控** — 暴露 consumergroup_lag 指标并接入 Grafana

> 原则：Garage S3 同时为 Loki/Tempo 提供存储后端、为 Phase 4 AI 推荐数据湖奠基、为商品图片上传提供基础，应尽早落地。

### 4.2 第二优先级：补齐 Phase 3 业务主链路

1. 社交登录剩余（Apple Sign-In / SMS OTP）—— 当前 SocialLoginService 只处理 "google"，其余抛异常
2. 注册成功页 + 邀请裂变链接（buyer-portal）—— 无 /register endpoint，referral 全项目零实现

> 原则：这条队列直接影响注册转化与留存，应优先于 Phase 4 扩展。

### 4.3 第三优先级：平台工程 P1 治理（并行）

1. 业务指标埋点（`shop_*` Counter/Timer，各服务补充 Micrometer）
2. 补偿持久化（coupon 核销、库存回补、积分退还 → outbox + `@Scheduled` 重试）
3. 关键链路契约测试（BFF → Domain Service）
4. 异常语义收敛（CommonErrorCode 全局标准化）
5. Kafka 幂等规范 + ArchUnit 规则

> 原则：降低后续改造成本，可与业务开发并行，但不能替代业务验证。

### 4.4 第四优先级：Phase 4 规模化能力

1. Kafka → Garage S3 行为数据归档（为 AI 推荐沉淀数据）
2. search-service 搜索增强（向量/语义/多语言分词）
3. AI 推荐引擎（依赖搜索增强 + 行为数据沉淀）
4. 支付方式扩展（Apple Pay / Google Pay / PayPal / BNPL）

> 原则：活动广场前端（列表页 + 详情页）已完成，Phase 4 主要缺口为搜索增强和支付扩展。

### 4.5 最低优先级：游戏新类型

- QuizPlugin（答题竞猜）
- SlashPricePlugin（砍价）
- GroupBuyPlugin（拼团）

> 原则：现有 4 种已实现游戏已覆盖主要玩法，新类型依赖产品验证结论再实现，不抢占工程资源。

---

## 五、任务完成定义（执行层）

每个任务在进入 Done 前至少满足以下条件：

1. **实现完成**：代码、配置、迁移脚本、模板或文档全部落地
2. **验证完成**：单测 / 集成测试 / Kind smoke check 至少覆盖核心路径
3. **文档完成**：更新对应 SSOT 与相关教程/运行文档
4. **依赖对齐**：如果任务改变了后续队列依赖，必须同步更新本文件和 `docs/ROADMAP-2026.md`
