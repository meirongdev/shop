# 后续继续完成清单

## P0：开始前先确认

在正式仓库执行：

```bash
cd /Users/matthew/projects/meirongdev/shop
git --no-pager status
git --no-pager log --oneline -5
```

然后阅读：

- `docs/SOURCE-OF-TRUTH-MATRIX.md`
- `docs/ROADMAP-2026.md`
- `docs/DELIVERY-PRIORITY-AND-DEPENDENCY-MAP.md`

> 说明：历史上的 fallback / staged 目录说明已经失效，本仓库现在直接在正式目录开发与验证。

---

## P1：当前建议优先队列

> 进展更新：`docs-site` 文档重组已完成（目录重分类、导航重构、旧 URL 重定向、构建验证通过）。

### 1. Phase 3 收尾（业务优先级最高）

- Apple Sign-In
- SMS OTP 登录
- 注册成功页 `/buyer/welcome`
- 邀请好友裂变链接 / referral

### 2. 平台工程 P1（并行）

- 补偿持久化：coupon 核销 / 库存回补 / 积分退还升级为 outbox + 定时重试
- 分布式锁统一抽象：当锁场景继续增长到 6+ 服务时，再评估提炼 `DistributedLockExecutor`
- 异常语义收敛：`CommonErrorCode` 与下游错误映射统一
- 业务指标埋点：按 `docs/OBSERVABILITY-ALERTING-SLO.md`
- Kafka 幂等规范 + ArchUnit 规则

### 3. 基础设施与可观测性

- Garage S3
- Loki / Tempo / Grafana
- OTEL Collector exporter 切换
- Alert Rules
- Kafka consumer lag 指标

### 4. Phase 4 之后

- 搜索增强
- AI 推荐
- 支付扩展

---

## P2：继续开发时的关键事实

- 已登录买家购物车仍在 `order-service`；**游客购物车在 `buyer-bff` Redis**，不是 `order-service`
- 登录后购物车合并发生在 `buyer-portal` 登录成功后，由 `buyer-bff /buyer/v1/cart/merge` 执行
- `buyer-bff` checkout 已具备韧性边界：
  - `promotion-service` / `loyalty-service` 系统故障可降级
  - `marketplace-service` 库存扣减必须快速失败
- `activity-service` 当前已实现 4 种玩法；`RewardDispatcher` 仍是外部奖励派发桩，不应文档化为“已完整对接 loyalty/promotion/wallet”
- `promotion-service` 目前是**双轨过渡态**：`coupon_template/coupon_instance` 已上线，但 legacy `coupon/coupon_usage` 仍在兼容现有校验/核销链路

---

## P3：每次改动后的最低验证要求

### 代码改动

- 跑受影响模块测试：

```bash
./mvnw -q -pl <module>[,<module>...] -am test
```

### docs / docs-site 改动

```bash
cd docs-site && npm run build
```

### K8s / infra 改动

- `kubectl -n shop get deploy,pods`
- 必要时 `port-forward` 到 Gateway 做 smoke test

---

## P4：完成定义

一个任务只有在以下条件都满足后才算 Done：

1. 代码 / 配置 / 迁移脚本已落地
2. 受影响模块已验证
3. `docs/` 与 `docs-site/` 已同步
4. Roadmap / 依赖图状态已更新
