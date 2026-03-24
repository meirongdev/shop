# 项目当前状态

## 1. 仓库与提交状态

- 正式工作仓库是 `/Users/matthew/projects/meirongdev/shop`，当前不再使用任何 fallback / staged 副本。
- 近两次关键功能提交已经在正式仓库落地：
  - `8d0fb43 feat: ship buyer guest cart and activity hub`
  - `ff6596f feat: harden bff downstream resilience`
- 当前这轮 `docs-site` 重组已完成：已按 5 大分区重建导航与目录，并同步修正文档引用。

---

## 2. 当前已落地能力

### 北向入口

- `auth-server`：JWT 登录、Google 社交登录、guest buyer token
- `api-gateway`：YAML 路由、JWT 校验、Trusted Headers、Redis Lua 限流、Canary、`/api/**` 与 `/public/**` CORS
- `buyer-portal` / `seller-portal`：Kotlin + Thymeleaf 门户

### 购物主链路

- `marketplace-service`：商品、分类、评价、SKU
- `order-service`：订单状态机、游客直购、订单追踪、已登录买家购物车
- `buyer-bff`：搜索、店铺页、购物车、结账、订单、积分 Hub 聚合
- `buyer-bff` Redis 游客购物车：`guest-buyer-*` 前缀 + 登录后 merge
- `wallet-service`：充值、提现、支付意图、Outbox 事件

### 用户增长

- `loyalty-service`：积分账户、签到、奖励兑换、新手任务、积分到期批处理
- `buyer-portal`：`/buyer/loyalty` 页面，支持签到、兑换、查看最近流水/兑换记录
- `promotion-service`：促销引擎、calculate API、欢迎券监听器、`coupon_template` / `coupon_instance` 核心拆分
- `activity-service`：`INSTANT_LOTTERY` / `RED_ENVELOPE` / `COLLECT_CARD` / `VIRTUAL_FARM`
- `buyer-portal`：`/buyer/activities` 与详情页已经可玩当前 4 种已实现玩法

### 解耦与韧性

- `buyer-bff` checkout 已把 `promotion-service` / `loyalty-service` 视为**可降级非核心依赖**
- `marketplace-service` 库存扣减保持**核心快速失败**
- `buyer-bff` / `seller-bff` 已统一补齐 RestClient `connectTimeout` / `readTimeout`

---

## 3. 当前真实剩余项

### Phase 3 收尾

- Apple Sign-In
- SMS OTP 登录
- 注册成功页
- 邀请好友裂变链接

### 平台工程 P1

- 补偿持久化（优惠券核销、库存回补、积分退还）
- 关键链路契约测试
- 异常语义收敛
- 业务指标埋点
- Kafka 幂等规范 + ArchUnit

### 基础设施 / Phase 4+

- Garage S3 + Loki / Tempo / Grafana + Alert Rules
- Search 增强（向量 / 语义 / 多语言）
- AI 推荐
- 支付扩展（Apple Pay / Google Pay / PayPal / BNPL）

---

## 4. 权威文档入口

继续开发前，优先看这些：

- `docs/SOURCE-OF-TRUTH-MATRIX.md`
- `docs/ROADMAP-2026.md`
- `docs/DELIVERY-PRIORITY-AND-DEPENDENCY-MAP.md`
- `docs/services/*.md`
- `docs-site/docs/architecture/index.md`
- `docs-site/docs/services/*.md`
- `docs-site/docs/getting-started/quick-start.md`
- `docs-site/docs/roadmap.md`

---

## 5. 建议接手顺序

1. `git --no-pager status`
2. `git --no-pager log --oneline -5`
3. 读 SSOT 与 Roadmap，确认要继续的是哪条 track
4. 只改相关模块，并执行受影响模块验证
5. 同步更新 `docs/` 与 `docs-site/`

---

## 6. 快速验证入口

- Maven 受影响模块测试：`./mvnw -q -pl <module>[,<module>...] -am test`
- 文档站验证：`cd docs-site && npm run build`
- Kind / K8s 变更：按 `scripts/` 与 `k8s/` 做 smoke test

---

## 7. Demo 账号

- buyer：`buyer.demo / password`
- buyer：`buyer.vip / password`
- seller：`seller.demo / password`
