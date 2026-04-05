# Docs-Site 重组实现计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task.

**Goal:** 将 docs-site 从 10 页平铺结构重组为 5 分区 19 页的层次化文档站，成为项目对外唯一的权威展示层。

**Architecture:** 手动显式 sidebar 取代自动生成；现有文件重组到 getting-started/、tech-stack/、architecture/、services/、engineering/ 五个子目录；新建 4 页（quick-start、tech-stack/index、event-driven、roadmap）；重写 intro.md；所有旧 URL 通过 redirect plugin 保持可访问。

**Tech Stack:** Docusaurus 3.9.2、TypeScript、`@docusaurus/plugin-client-redirects`、Markdown/MDX

**Spec:** `docs/superpowers/specs/2026-03-23-docs-site-reorganization-design.md`

**工作目录:** `docs-site/`（所有 npm 命令在此目录执行）

**执行状态：** ✅ 已完成（docs-site 结构重组、迁移与构建验证）

---

## Task 1: 安装 redirect plugin，配置重定向和 navbar/footer

**Files:**
- Modify: `docs-site/package.json`
- Modify: `docs-site/docusaurus.config.ts`

- [x] **Step 1: 安装 redirect plugin**

```bash
cd docs-site && npm install @docusaurus/plugin-client-redirects
```

预期输出：`added 1 package`（或 `up to date`，如已安装）

- [x] **Step 2: 更新 docusaurus.config.ts — 加入 redirect plugin 和新 navbar/footer**

将 `docs-site/docusaurus.config.ts` 替换为：

```typescript
import {themes as prismThemes} from 'prism-react-renderer';
import type {Config} from '@docusaurus/types';
import type * as Preset from '@docusaurus/preset-classic';

const config: Config = {
  title: 'Shop Platform',
  tagline: '云原生微服务电商技术验证平台',
  favicon: 'img/favicon.ico',

  future: {
    v4: true,
  },

  url: 'https://meirongdev.github.io',
  baseUrl: '/shop/',

  organizationName: 'meirongdev',
  projectName: 'shop',

  onBrokenLinks: 'throw',

  i18n: {
    defaultLocale: 'zh-Hans',
    locales: ['zh-Hans'],
  },

  plugins: [
    [
      '@docusaurus/plugin-client-redirects',
      {
        redirects: [
          { from: '/local-deployment',         to: '/getting-started/local-deployment' },
          { from: '/observability',             to: '/engineering/observability' },
          { from: '/engineering-standards',     to: '/engineering/standards' },
          { from: '/tech-stack-best-practices', to: '/tech-stack/best-practices' },
          { from: '/modules/auth-server',       to: '/services/core' },
          { from: '/modules/api-gateway',       to: '/architecture/api-gateway' },
          { from: '/modules/bff-portals',       to: '/architecture/bff-pattern' },
          { from: '/modules/domain-services',   to: '/services/core' },
        ],
      },
    ],
  ],

  presets: [
    [
      'classic',
      {
        docs: {
          sidebarPath: './sidebars.ts',
          routeBasePath: '/',
        },
        blog: false,
        theme: {
          customCss: './src/css/custom.css',
        },
      } satisfies Preset.Options,
    ],
  ],

  themeConfig: {
    colorMode: {
      respectPrefersColorScheme: true,
    },
    navbar: {
      title: 'Shop Platform',
      items: [
        {
          type: 'docSidebar',
          sidebarId: 'tutorialSidebar',
          position: 'left',
          label: '文档',
        },
        {
          href: 'https://github.com/meirongdev/shop',
          label: 'GitHub',
          position: 'right',
        },
      ],
    },
    footer: {
      style: 'dark',
      links: [
        {
          title: '开始',
          items: [
            { label: '项目概览',   to: '/' },
            { label: '快速开始',   to: '/getting-started/quick-start' },
            { label: '本地部署',   to: '/getting-started/local-deployment' },
          ],
        },
        {
          title: '架构',
          items: [
            { label: '架构概览',   to: '/architecture' },
            { label: 'API Gateway', to: '/architecture/api-gateway' },
            { label: '事件驱动',   to: '/architecture/event-driven' },
          ],
        },
        {
          title: '参考',
          items: [
            { label: '技术栈',   to: '/tech-stack' },
            { label: '服务模块', to: '/services/core' },
            { label: 'Roadmap',  to: '/roadmap' },
          ],
        },
      ],
      copyright: `Copyright ${new Date().getFullYear()} Shop Platform. Built with Docusaurus.`,
    },
    prism: {
      theme: prismThemes.github,
      darkTheme: prismThemes.dracula,
      additionalLanguages: ['java', 'kotlin', 'yaml', 'bash', 'sql'],
    },
  } satisfies Preset.ThemeConfig,
};

export default config;
```

- [x] **Step 3: 验证构建通过**

```bash
cd docs-site && npm run build 2>&1 | tail -5
```

预期：`Generated static files in "build".` 或类似成功信息，无报错。

- [x] **Step 4: Commit**

```bash
git add docs-site/package.json docs-site/package-lock.json docs-site/docusaurus.config.ts
git commit -m "chore(docs-site): install redirect plugin, update navbar/footer"
```

---

## Task 2: 更新 sidebars.ts 为手动显式结构

**Files:**
- Modify: `docs-site/sidebars.ts`

- [x] **Step 1: 替换 sidebars.ts**

```typescript
import type {SidebarsConfig} from '@docusaurus/plugin-content-docs';

const sidebars: SidebarsConfig = {
  tutorialSidebar: [
    'intro',
    {
      type: 'category',
      label: '🚀 快速开始',
      collapsed: false,
      items: [
        'getting-started/quick-start',
        'getting-started/local-deployment',
      ],
    },
    {
      type: 'category',
      label: '🛠 技术栈',
      items: [
        'tech-stack/index',
        'tech-stack/best-practices',
      ],
    },
    {
      type: 'category',
      label: '🏗 架构设计',
      items: [
        'architecture/index',
        'architecture/api-gateway',
        'architecture/bff-pattern',
        'architecture/event-driven',
      ],
    },
    {
      type: 'category',
      label: '📦 服务模块',
      items: [
        'services/core',
        'services/growth',
        'services/platform',
      ],
    },
    {
      type: 'category',
      label: '⚙️ 工程实践',
      items: [
        'engineering/standards',
        'engineering/observability',
      ],
    },
    'roadmap',
  ],
};

export default sidebars;
```

- [x] **Step 2: 创建所有子目录和占位文件（让 Docusaurus 能构建）**

```bash
cd docs-site/docs
mkdir -p getting-started tech-stack architecture services engineering

# 占位文件（每个只需 frontmatter，内容后续填充）
for f in \
  "getting-started/quick-start.md|🚀 快速开始" \
  "tech-stack/index.md|🛠 技术栈" \
  "architecture/index.md|🏗 架构设计" \
  "architecture/event-driven.md|事件驱动架构" \
  "services/core.md|核心交易服务" \
  "services/growth.md|用户增长服务" \
  "services/platform.md|平台扩展服务" \
  "roadmap.md|Roadmap"; do
  path="${f%%|*}"
  title="${f##*|}"
  echo "---\ntitle: ${title}\n---\n\n> 内容整理中..." > "$path"
done
```

- [x] **Step 3: 移动现有文件到新路径**

```bash
cd docs-site/docs

# getting-started/
mv local-deployment.md getting-started/local-deployment.md

# tech-stack/
mv tech-stack-best-practices.md tech-stack/best-practices.md

# architecture/
mv architecture.md architecture/index.md
mv modules/api-gateway.md architecture/api-gateway.md
mv modules/bff-portals.md architecture/bff-pattern.md

# engineering/
mv observability.md engineering/observability.md
mv engineering-standards.md engineering/standards.md

# modules/auth-server.md 内容将并入 services/core.md，暂保留备用
# modules/domain-services.md 内容将拆分，暂保留备用
```

- [x] **Step 4: 验证构建通过**

```bash
cd docs-site && npm run build 2>&1 | tail -10
```

预期：构建成功，无 broken links 报错（占位文件已存在）。

- [x] **Step 5: Commit**

```bash
git add docs-site/sidebars.ts docs-site/docs/
git commit -m "chore(docs-site): restructure directories, explicit sidebar, move existing files"
```

---

## Task 3: 重写 intro.md

**Files:**
- Modify: `docs-site/docs/intro.md`

- [x] **Step 1: 替换 intro.md**

```markdown
---
slug: /
sidebar_position: 1
title: 项目概览
---

# Shop Platform

> 基于 **Java 25 + Spring Boot 3.5 + Spring Cloud 2025.0** 的云原生微服务电商技术验证平台

## 这是什么

Shop Platform 验证 **Gateway → Thin BFF → Domain Service** 三层架构在 2026 技术基线下的工程可行性，覆盖完整的电商购物闭环：浏览、搜索、加购、结账、支付、通知、积分、活动。

## 服务全景

| 服务 | 端口 | 职责 |
|------|------|------|
| api-gateway | 8080 | 统一入口：YAML 路由、JWT 校验、限流、Canary |
| buyer-bff | 8081 | 买家聚合层（购物车、下单、搜索聚合） |
| seller-bff | 8082 | 卖家聚合层（商品管理、订单处理） |
| buyer-portal | 8100 | Kotlin + Thymeleaf 买家门户 |
| seller-portal | 8101 | Kotlin + Thymeleaf 卖家门户 |
| auth-server | 8090 | JWT 签发、Google OAuth2、Guest Token |
| profile-service | 8083 | 用户档案、地址簿 |
| marketplace-service | 8084 | 商品目录、SKU、评价 |
| order-service | 8085 | 订单状态机、游客订单、Outbox 事件 |
| wallet-service | 8086 | 余额、充值、Stripe 支付 |
| promotion-service | 8087 | 促销、优惠券（template/instance 模型） |
| loyalty-service | 8088 | 积分账户、签到、兑换 |
| activity-service | 8089 | 互动游戏（砸金蛋/抢红包/集卡/虚拟农场） |
| search-service | 8091 | Meilisearch 商品搜索 + Feature Toggle |
| notification-service | 8092 | Channel SPI 消息通知（邮件） |
| webhook-service | 8093 | 开放平台 Webhook + HMAC 签名 |
| subscription-service | 8094 | 订阅计划 + 自动续费 |

## 快速导航

| | |
|---|---|
| [🚀 5 分钟跑起来](/getting-started/quick-start) | Docker Compose 一键启动 |
| [🏗 架构设计](/architecture) | 三层模型、解耦原则 |
| [🛠 技术栈](/tech-stack) | 选型决策与权衡 |
| [📦 服务模块](/services/core) | 各服务职责与 API |
| [⚙️ 工程实践](/engineering/standards) | 规范、弹性、可观测性 |
```

- [x] **Step 2: 验证**

```bash
cd docs-site && npm run build 2>&1 | grep -E "error|Error|broken" || echo "Build OK"
```

- [x] **Step 3: Commit**

```bash
git add docs-site/docs/intro.md
git commit -m "docs(docs-site): rewrite intro.md with elevator pitch and service map"
```

---

## Task 4: 新建 getting-started/quick-start.md

**Files:**
- Modify: `docs-site/docs/getting-started/quick-start.md`

- [x] **Step 1: 写入 quick-start.md**

```markdown
---
sidebar_position: 1
title: 快速开始
---

# 快速开始

5 分钟用 Docker Compose 跑起完整的 Shop Platform。

## 前置条件

| 工具 | 版本要求 | 说明 |
|------|---------|------|
| Docker Desktop | ≥ 4.x | 包含 Docker Compose v2 |
| Java | 25 | 构建服务 JAR |
| Maven Wrapper | 内置 | 项目已包含 `./mvnw` |

## 启动步骤

### 1. 克隆并配置环境

```bash
git clone https://github.com/meirongdev/shop.git
cd shop
cp .env.example .env          # 按需修改 Stripe Key 等
```

### 2. 构建所有服务

```bash
./mvnw -q package -DskipTests
```

首次构建约 3-5 分钟（下载依赖）。

### 3. 启动所有容器

```bash
docker compose up -d
```

等待约 30 秒，MySQL / Kafka / Redis 健康检查通过后服务陆续就绪。

### 4. 验证启动成功

```bash
# Gateway 健康检查
curl -s http://localhost:8080/actuator/health | jq .status
# 预期: "UP"

# 搜索商品
curl -s "http://localhost:8080/api/buyer/search?q=phone" | jq .total
# 预期: 数字（>= 0）
```

浏览器打开 **http://localhost:8080/buyer** 即可进入买家门户。

## Smoke Check

```bash
# 1. 游客 Token
GUEST_TOKEN=$(curl -s -X POST http://localhost:8080/auth/v1/token/guest \
  -H "Content-Type: application/json" | jq -r .token)

# 2. 搜索商品
curl -s "http://localhost:8080/api/buyer/search?q=shirt" \
  -H "Authorization: Bearer $GUEST_TOKEN" | jq '.hits | length'

# 3. 查看促销列表
curl -s http://localhost:8080/api/buyer/promotions \
  -H "Authorization: Bearer $GUEST_TOKEN" | jq length
```

## 常见问题

**`REDIS_PORT=tcp://...` 导致服务启动失败**

Kind / Kubernetes 会自动注入 `REDIS_PORT` 环境变量污染 Spring 配置。Docker Compose 无此问题，但如果同时运行 Kind 集群，先执行：

```bash
unset REDIS_PORT
```

**Kafka 消费者报 `Leader Not Available`**

Kafka KRaft 单节点启动有短暂延迟。等待 10-15 秒后服务会自动重连，无需手动干预。

**构建时 Maven 报 `Could not resolve dependencies`**

确认 Java 版本为 25：

```bash
java -version
# openjdk version "25" ...
```

## 进阶：Kind + mirrord

需要在 Kubernetes 环境中开发调试？参见 [本地部署（完整版）](/getting-started/local-deployment)。
```

- [x] **Step 2: 验证**

```bash
cd docs-site && npm run build 2>&1 | grep -E "error|Error|broken" || echo "Build OK"
```

- [x] **Step 3: Commit**

```bash
git add docs-site/docs/getting-started/quick-start.md
git commit -m "docs(docs-site): add quick-start guide (Docker Compose path)"
```

---

## Task 5: 新建 tech-stack/index.md

**Files:**
- Modify: `docs-site/docs/tech-stack/index.md`

> 此页控制在 ≤400 词正文，无代码片段——代码示例在 best-practices.md。

- [x] **Step 1: 写入 tech-stack/index.md**

```markdown
---
sidebar_position: 1
title: 技术栈
---

# 技术栈

## 技术全景

```
┌─────────────────────────────────────────────┐
│  客户端层                                     │
│  Buyer Portal (Kotlin+Thymeleaf :8100)       │
│  Seller Portal (Kotlin+Thymeleaf :8101)      │
└──────────────────┬──────────────────────────┘
                   │ HTTP
┌──────────────────▼──────────────────────────┐
│  边缘层                                       │
│  API Gateway (Spring Cloud Gateway MVC :8080) │
│  JWT 校验 · YAML 路由 · Redis Lua 限流        │
│  Virtual Threads · Canary 灰度               │
└──────┬──────────────────────┬───────────────┘
       │                      │
┌──────▼──────┐        ┌──────▼──────┐
│  Buyer BFF  │        │  Seller BFF │
│    :8081    │        │    :8082    │
│ Resilience4j│        │ Resilience4j│
└──────┬──────┘        └──────┬──────┘
       │                      │
┌──────▼──────────────────────▼───────────────┐
│  领域服务层（各自独立 MySQL Schema + Kafka）    │
│  auth · profile · marketplace · order        │
│  wallet · promotion · loyalty · activity     │
│  search · notification · webhook · subscription│
└─────────────────────────────────────────────┘
┌─────────────────────────────────────────────┐
│  基础设施层                                    │
│  MySQL 8.4 · Redis 7.4 · Kafka KRaft 3.9    │
│  Meilisearch 1.12 · Stripe SDK               │
│  Prometheus · OpenTelemetry Collector        │
└─────────────────────────────────────────────┘
```

## 选型决策

| 类别 | 选择 | 为什么选 | 没选什么 |
|------|------|---------|---------|
| **运行时并发** | Java 25 Virtual Threads | 同步代码可读性 + 高吞吐，无需响应式编程范式 | WebFlux / Reactive |
| **网关** | Spring Cloud Gateway MVC | 与 Virtual Threads 栈一致，Servlet 生态兼容，YAML 路由零 Java | WebFlux Gateway |
| **Portal 渲染** | Kotlin + Thymeleaf | SSR 降低前端复杂度，Kotlin 与 Java 混编零摩擦 | React / Vue SPA |
| **持久化** | MySQL 8.4 + Flyway | 事务完整性，版本化迁移，每服务独立 schema | MongoDB、JdbcTemplate-only |
| **缓存** | Redis 7.4 | 原子 Lua 脚本（限流、红包池），SET NX 防重 | Memcached |
| **搜索** | Meilisearch 1.12 | 零配置开箱即用，毫秒级响应，本地 Kind 友好 | Elasticsearch（运维复杂） |
| **消息** | Apache Kafka 3.9 KRaft | Outbox Pattern 天然契合，无 ZooKeeper | RabbitMQ |
| **支付** | Stripe SDK | Webhook 回调成熟，本地测试工具链完善 | PayPal（Phase 4 扩展项） |
| **特性开关** | OpenFeature | 标准化 SPI，运行时热更新，无厂商锁定 | LaunchDarkly |
| **弹性** | Resilience4j | Spring Boot 原生集成，注解驱动，BFF 层统一配置 | Hystrix（已停维护） |
| **可观测性** | Micrometer + OTLP | 三支柱（指标/链路/日志）统一采集，Prometheus 生态 | 专有 APM |
| **ID 策略** | ULID（CHAR 26） | 有序、唯一、无数据库依赖，替代自增 ID | UUID（无序）、自增（分布式不友好） |

## 深度参考

- [最佳实践 & 代码模式](/tech-stack/best-practices) — 关键技术的实战配置与代码片段
- [架构设计](/architecture) — 三层模型与解耦原则
- [事件驱动架构](/architecture/event-driven) — Outbox Pattern 与 Kafka 集成
```

- [x] **Step 2: 验证**

```bash
cd docs-site && npm run build 2>&1 | grep -E "error|Error|broken" || echo "Build OK"
```

- [x] **Step 3: Commit**

```bash
git add docs-site/docs/tech-stack/index.md
git commit -m "docs(docs-site): add tech-stack overview with decision table"
```

---

## Task 6: 更新 tech-stack/best-practices.md（整合 + 代码片段）

**Files:**
- Modify: `docs-site/docs/tech-stack/best-practices.md`

- [x] **Step 1: 在现有内容基础上，补充以下各节代码片段**

在文件末尾追加（或整合到对应技术节下）：

````markdown
## Virtual Threads 配置

```yaml
# application.yml
spring:
  threads:
    virtual:
      enabled: true  # Spring Boot 3.2+ 自动为 Tomcat/Jetty 使用 VT
```

Gateway 下只需此一行，无需任何 `Executor` Bean。

## Resilience4j Circuit Breaker（BFF 标准配置）

```yaml
resilience4j:
  circuitbreaker:
    instances:
      promotionService:
        sliding-window-size: 20
        failure-rate-threshold: 50
        wait-duration-in-open-state: 20s
      loyaltyService:
        sliding-window-size: 20
        failure-rate-threshold: 50
        wait-duration-in-open-state: 20s
```

```java
@CircuitBreaker(name = "promotionService", fallbackMethod = "validateCouponFallback")
public CouponValidateResponse validateCoupon(String couponCode, BigDecimal amount) {
    // 正常路径
}

private CouponValidateResponse validateCouponFallback(String couponCode,
        BigDecimal amount, Exception ex) {
    log.warn("promotion-service unavailable, skipping coupon validation");
    return CouponValidateResponse.skipped(); // 降级：跳过优惠券
}
```

## Outbox Pattern（事务写 + 异步发布）

```java
@Transactional
public Order createOrder(CreateOrderRequest req) {
    Order order = orderRepository.save(Order.from(req));          // 1. 写业务数据
    outboxRepository.save(OutboxEvent.of("order.created.v1",      // 2. 同一事务写 outbox
            order.getId(), OrderCreatedPayload.of(order)));
    return order;
}
```

```java
@Scheduled(fixedDelay = 5000)
public void publishPendingEvents() {                              // 3. Poller 异步发布
    outboxRepository.findPending().forEach(event -> {
        kafkaTemplate.send(event.getTopic(), event.getPayload());
        event.markPublished();
    });
}
```

## OpenFeature 特性开关

```java
// shop-common 自动配置，任何服务直接注入使用
@Autowired
private FeatureToggleService featureToggle;

if (featureToggle.isEnabled(SearchFeatureFlags.AUTOCOMPLETE)) {
    return searchClient.autocomplete(query);
}
return Collections.emptyList(); // 降级：返回空
```

```yaml
# feature-toggles.yaml（运行时热加载）
flags:
  search-autocomplete: true
  search-trending: true
  search-locale-aware: false
```

## ULID 主键

```java
// 所有 Entity 统一使用
@Id
@Column(length = 26, nullable = false, updatable = false)
private String id = UlidCreator.getUlid().toString();
```

ULID 有序（前 10 字节时间戳），索引效率接近自增 ID，同时全局唯一无碰撞风险。

## Redis Lua 原子限流（Gateway）

```lua
-- RateLimitingFilter 内嵌 Lua 脚本
local key = KEYS[1]           -- e.g. "ratelimit:player:player-001:2026032314"
local limit = tonumber(ARGV[1]) -- RPM 上限，默认 100
local current = redis.call('INCR', key)
if current == 1 then
    redis.call('EXPIRE', key, 60)
end
return current
```

Redis 故障时 fail-open（Gateway 不拒绝请求），避免 Redis 单点影响核心链路。
````

- [x] **Step 2: 验证**

```bash
cd docs-site && npm run build 2>&1 | grep -E "error|Error|broken" || echo "Build OK"
```

- [x] **Step 3: Commit**

```bash
git add docs-site/docs/tech-stack/best-practices.md
git commit -m "docs(docs-site): add code patterns to tech-stack best-practices"
```

---

## Task 7: 新建 architecture/event-driven.md

**Files:**
- Modify: `docs-site/docs/architecture/event-driven.md`

- [x] **Step 1: 写入 event-driven.md**

```markdown
---
sidebar_position: 4
title: 事件驱动架构
---

# 事件驱动架构

## Outbox Pattern

核心链路（下单、支付、充值）使用 Outbox Pattern 保证业务写入与事件发布的原子性：

```
┌──────────────────────────────────────┐
│  业务服务（同一数据库事务）            │
│  1. INSERT order / wallet_transaction │
│  2. INSERT outbox_event (PENDING)     │
└─────────────────┬────────────────────┘
                  │ @Scheduled 5s
┌─────────────────▼────────────────────┐
│  Outbox Poller                        │
│  3. SELECT WHERE status = PENDING     │
│  4. kafkaTemplate.send(topic, payload)│
│  5. UPDATE status = PUBLISHED         │
└─────────────────┬────────────────────┘
                  │ Kafka
┌─────────────────▼────────────────────┐
│  消费者（非核心服务，天然异步）        │
│  loyalty-service  → 发放购物积分      │
│  notification-service → 发送邮件      │
│  webhook-service  → 推送第三方        │
└──────────────────────────────────────┘
```

**为什么用 Outbox 而不是直接 `kafkaTemplate.send`？**

直接发送时，业务事务成功但 Kafka 发布失败，会导致事件丢失。Outbox 将事件写入同一数据库事务，Poller 保证最终一定发布（at-least-once）。

## 当前 Kafka Topic

| Topic | 生产者 | 消费者 | 用途 |
|-------|--------|--------|------|
| `order.events.v1` | order-service | loyalty-service, notification-service, webhook-service | 订单全生命周期事件 |
| `wallet.transactions.v1` | wallet-service | promotion-service, notification-service, webhook-service | 充值/提现事件 |
| `marketplace.product.events.v1` | marketplace-service | search-service | 商品变更 → 索引更新 |
| `buyer.registered.v1` | auth-server | loyalty-service, notification-service, promotion-service | 新用户注册 → 积分/欢迎邮件/欢迎券 |

## Consumer 幂等规范

Kafka 消费者必须保证幂等（at-least-once 下同一消息可能重复投递）：

```java
// 推荐模式：唯一约束 + 重复检测
@KafkaListener(topics = "${shop.loyalty.order-events-topic}")
@Transactional
public void onOrderEvent(OrderEvent event) {
    if (loyaltyTransactionRepo.existsByEventId(event.getEventId())) {
        log.debug("Duplicate event {}, skipping", event.getEventId());
        return;   // 幂等跳过
    }
    loyaltyService.earnPoints(event);
}
```

数据库层以 `(event_id, channel)` 唯一约束作为兜底。

## 核心 vs 非核心解耦原则

| 调用类型 | 适用场景 | 实现 | 失败策略 |
|---------|---------|------|---------|
| **同步（快速失败）** | 核心交易：库存扣减、支付创建 | RestClient + 全局超时 | 立即抛错，事务回滚 |
| **同步 + Circuit Breaker** | 非核心增强：积分抵扣、优惠券校验 | `@CircuitBreaker` + fallback | 降级跳过，不阻断下单 |
| **Kafka 异步** | 天然异步：通知、积分发放、Webhook | Outbox + KafkaListener | 消费失败不影响主链路 |

> **当前实现说明**：`activity-service` 的 `RewardDispatcher`（将游戏奖励派发到 loyalty/wallet）
> 目前为桩实现（`reward_status=PENDING` 记录留存，实际积分/余额派发仍在规划中）。
> 下单/支付链路不受此影响。
```

- [x] **Step 2: 验证**

```bash
cd docs-site && npm run build 2>&1 | grep -E "error|Error|broken" || echo "Build OK"
```

- [x] **Step 3: Commit**

```bash
git add docs-site/docs/architecture/event-driven.md
git commit -m "docs(docs-site): add event-driven architecture page (Outbox + Kafka)"
```

---

## Task 8: 重写 architecture/bff-pattern.md

**Files:**
- Modify: `docs-site/docs/architecture/bff-pattern.md`

- [x] **Step 1: 重写为模式说明（非 API 参考）**

```markdown
---
sidebar_position: 3
title: BFF 模式
---

# BFF 模式（Backend for Frontend）

## 为什么需要 BFF

领域服务（order-service、wallet-service 等）对外暴露细粒度 API，单个页面往往需要聚合多个服务的数据。**BFF 层作为前端专属的聚合适配器**，屏蔽内部服务拓扑，减少前端请求次数。

```
❌ 没有 BFF：前端直连多个服务
Browser → order-service (获取订单)
Browser → wallet-service (获取余额)
Browser → loyalty-service (获取积分)
Browser → promotion-service (获取优惠券)
= 4 次请求，前端耦合后端拓扑

✅ 有 BFF：一次请求聚合
Browser → buyer-bff /api/buyer/dashboard
  → order-service
  → wallet-service      并行聚合
  → loyalty-service
  → promotion-service
= 1 次请求，前端无感知后端变化
```

## Thin BFF 原则

BFF **只做聚合和适配，不放业务逻辑**：

| 应该在 BFF | 不应该在 BFF |
|-----------|------------|
| 多服务数据聚合 | 促销折扣计算逻辑 |
| 请求/响应格式适配 | 积分累计规则 |
| 降级 fallback（CB） | 库存扣减 |
| 认证 Header 透传 | 订单状态流转 |

## Buyer / Seller 分离

项目有两个独立 BFF：

| BFF | 端口 | Portal | 服务对象 |
|-----|------|--------|---------|
| buyer-bff | 8081 | buyer-portal (8100) | 注册买家 + 游客 |
| seller-bff | 8082 | seller-portal (8101) | 注册卖家 |

分离原因：两类用户的聚合需求、权限模型、UI 交互差异显著，共用一个 BFF 会导致条件分支膨胀。

## 弹性降级策略

BFF 对非核心下游服务配置 Circuit Breaker，服务不可用时降级而非失败：

| 下游服务 | 触发条件 | 降级行为 |
|---------|---------|---------|
| search-service | 失败率 ≥ 50% / 30s 窗口 | 回退到 marketplace-service 基础搜索 |
| promotion-service | 失败率 ≥ 50% / 20s 窗口 | checkout 跳过优惠券抵扣（显式"券无效"仍返回业务错误） |
| loyalty-service | 失败率 ≥ 50% / 20s 窗口 | checkout 跳过积分抵扣（显式"积分不足"仍返回业务错误） |
| marketplace-service | 失败率 ≥ 50% / 10s 窗口 | 库存扣减失败立即抛错（核心链路，不降级） |

## Portal 与 BFF 的关系

Buyer / Seller Portal 是 Thymeleaf SSR 应用，它们**通过内部 API 调用同一 BFF**，而非直接访问领域服务：

```
Browser
  → buyer-portal (Thymeleaf 渲染 HTML)
      → buyer-bff /api/buyer/** (JSON API)
          → 各领域服务
```

Portal 持有用户 Session，每次服务端渲染时从 BFF 获取数据，不存在跨域问题。
```

- [x] **Step 2: 验证**

```bash
cd docs-site && npm run build 2>&1 | grep -E "error|Error|broken" || echo "Build OK"
```

- [x] **Step 3: Commit**

```bash
git add docs-site/docs/architecture/bff-pattern.md
git commit -m "docs(docs-site): rewrite bff-pattern as pattern explanation (not API reference)"
```

---

## Task 9: 拆分 domain-services.md → services/core + growth + platform

**Files:**
- Modify: `docs-site/docs/services/core.md`
- Modify: `docs-site/docs/services/growth.md`
- Modify: `docs-site/docs/services/platform.md`

> 从 `modules/domain-services.md`（暂保留）和 `modules/auth-server.md` 提取内容，
> 补充 Profile Service（原在 domain-services.md 开头，此前未归类）。

- [x] **Step 1: 写入 services/core.md**

```markdown
---
sidebar_position: 1
title: 核心交易服务
---

# 核心交易服务

直接决定"能否浏览、加购、下单、支付、查单"的服务群。任意服务不可用均影响核心购物链路。

## Auth Server（:8090）

JWT 签发与认证中心。

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/auth/v1/token/login` | 账密登录，返回 JWT |
| POST | `/auth/v1/token/guest` | 游客 Token（ROLE_BUYER_GUEST） |
| POST | `/auth/v1/token/oauth2/google` | Google OAuth2 登录（自动注册） |
| GET  | `/auth/v1/user/me` | 当前用户信息 |
| POST | `/auth/v1/social/bind` | 绑定社交账号 |

JWT Claim 包含：`principalId`、`username`、`roles`、`portal`，由 Gateway 提取注入 Trusted Headers。

## Profile Service（:8083）

用户档案与地址管理，买家和卖家的基础身份数据。

| 方法 | 路径 | 说明 |
|------|------|------|
| GET  | `/profile/v1/buyer/me` | 买家档案 |
| PUT  | `/profile/v1/buyer/me` | 更新档案 |
| GET  | `/profile/v1/buyer/addresses` | 地址列表 |
| POST | `/profile/v1/buyer/addresses` | 新增地址 |
| GET  | `/profile/v1/seller/me` | 卖家档案 + 店铺信息 |

## Marketplace Service（:8084）

商品目录、多规格 SKU 和买家评价。

| 方法 | 路径 | 说明 |
|------|------|------|
| GET  | `/marketplace/v1/products` | 商品列表（分页、筛选） |
| GET  | `/marketplace/v1/products/{id}` | 商品详情 + SKU |
| POST | `/marketplace/v1/products` | 创建商品（卖家） |
| PUT  | `/marketplace/v1/products/{id}` | 更新商品 |
| POST | `/marketplace/v1/products/{id}/reviews` | 提交评价（买家） |

商品变更事件通过 `marketplace.product.events.v1` 发布到 Kafka，search-service 消费更新索引。

## Order Service（:8085）

完整订单生命周期，含游客订单和 Outbox 事件发布。

### 订单状态机

```
PENDING_PAYMENT
    │ 支付成功
    ▼
  PAID
    │ 卖家发货
    ▼
 SHIPPED
    │ 买家确认 / 7 天自动
    ▼
DELIVERED
    │ 自动
    ▼
COMPLETED ←─── 任意阶段取消 ──→ CANCELLED
                              │
                    退款申请 ─▼
                    REFUND_REQUESTED
                              │
                              ▼
                          REFUNDED
```

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/order/v1/cart/add` | 加购 |
| GET  | `/order/v1/cart` | 查看购物车 |
| POST | `/order/v1/checkout` | 创建订单（标准/游客） |
| GET  | `/order/v1/orders/{id}` | 订单详情 |
| POST | `/order/v1/orders/{id}/cancel` | 取消订单 |
| GET  | `/order/v1/public/track` | 游客订单追踪（无需认证） |

## Wallet Service（:8086）

余额账户、充值、提现和 Stripe 支付集成。

| 方法 | 路径 | 说明 |
|------|------|------|
| GET  | `/wallet/v1/account` | 账户余额 |
| POST | `/wallet/v1/deposit` | 充值（创建 Stripe PaymentIntent） |
| POST | `/wallet/v1/withdraw` | 提现 |
| POST | `/internal/wallet/payment-confirm` | Stripe Webhook 回调（内部） |

充值成功后通过 `wallet.transactions.v1` 发布事件，触发促销奖励和通知。
```

- [x] **Step 2: 写入 services/growth.md**

```markdown
---
sidebar_position: 2
title: 用户增长服务
---

# 用户增长服务

提升用户留存与转化，不直接阻断下单链路（均有 Circuit Breaker 降级保护）。

## Loyalty Service（:8088）

积分账户、签到、兑换和新手任务。

### 积分生命周期

```
注册 ──→ +100 pts（注册奖励）
签到 ──→ +10 pts/天，连续 7 天 +50 pts bonus
下单完成 ──→ +1 pt / $1（消费积分，Kafka 消费 order.completed.v1）
积分兑换 ──→ 100 pts = $1 抵扣
到期批量任务 ──→ 每日 2 AM 扫描过期积分
```

| 方法 | 路径 | 说明 |
|------|------|------|
| GET  | `/loyalty/v1/account` | 积分账户 + 会员等级 |
| POST | `/loyalty/v1/checkin` | 每日签到 |
| GET  | `/loyalty/v1/checkin/calendar` | 签到日历 |
| GET  | `/loyalty/v1/redemptions` | 可兑换商品列表 |
| POST | `/loyalty/v1/redemptions/{id}/redeem` | 积分兑换 |
| GET  | `/loyalty/v1/tasks` | 新手任务列表 |

会员等级：SILVER → GOLD（≥500 pts）→ PLATINUM（≥2000 pts）

## Promotion Service（:8087）

促销活动与优惠券，采用 template/instance 双轨模型。

### 数据模型

```
coupon_template（规则定义，不可变）
  ├── discount_type: FIXED / PERCENTAGE
  ├── total_limit: 发放总量上限
  └── per_user_limit: 每人上限

coupon_instance（每位用户的券实例）
  ├── status: UNUSED / USED / EXPIRED
  ├── template_id → coupon_template
  └── buyer_id
```

旧 `coupon` 表仍用于 checkout validate/apply（legacy 过渡期），新发放走 template/instance。

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/promotion/v1/offer/list` | 促销活动列表 |
| POST | `/promotion/v1/coupon/validate` | 结账前验券 |
| POST | `/promotion/v1/coupon/apply` | 核销优惠券 |
| POST | `/promotion/v1/calculate` | 购物车折扣计算 |

## Notification Service（:8092）

Channel SPI 消息通知，当前实现 EmailChannel。

### 架构

```
Kafka（order / wallet / user 事件）
    ↓ @KafkaListener
EventListener（per topic）
    ↓
NotificationRouter（事件 → 模板 + 渠道）
    ↓
ChannelDispatcher
    └── EmailChannel（Spring Mail + Thymeleaf 模板）
              ↓ 本地: Mailpit (SMTP :1025, Web UI :8025)
```

**幂等**：`(event_id, channel)` 唯一约束，同一事件同渠道只发一次。
**重试**：`@Scheduled` 每 60 秒扫描 `FAILED` 记录，最多重试 3 次。

已实现的 7 种邮件模板：欢迎邮件、下单确认、已发货、已完成、已取消、充值到账、提现到账。

扩展新渠道（SMS、WhatsApp）只需实现 `NotificationChannel` 接口并注册为 Spring Bean。
```

- [x] **Step 3: 写入 services/platform.md**

```markdown
---
sidebar_position: 3
title: 平台扩展服务
---

# 平台扩展服务

基础设施能力，为业务服务提供横向支撑。

> Search Service 归入此分区原因：不在 API Gateway 路由链上，由 BFF 直接以内部地址调用，
> 定位更接近"基础设施能力"而非"核心交易链路"。

## Activity Service（:8089）

互动游戏平台，GamePlugin SPI 插件架构。

### 已实现游戏类型

| 类型 | 说明 | 实现亮点 |
|------|------|---------|
| `INSTANT_LOTTERY` | 砸金蛋 / 大转盘 / 刮刮乐 / 九宫格 | 加权概率抽奖 |
| `RED_ENVELOPE` | 抢红包 | Redis Lua 原子操作，List + Hash 管理金额池 |
| `COLLECT_CARD` | 集卡 / 集碎片 | MySQL 卡片定义表 + 加权抽卡 + 集齐判定 |
| `VIRTUAL_FARM` | 虚拟农场 | MySQL 农场状态表 + 浇水 / 收获流程 |

规划中（最低优先级）：Quiz（答题）、SlashPrice（砍价）、GroupBuy（拼团）

### AntiCheatGuard

- 买家突发限速（Redis INCR + EXPIRE 滑动窗口）
- IP 限速
- 设备指纹跨账号拦截（同一设备 ID 对应多账号时触发）
- 命中返回 `403`（设备复用）或 `429`（突发限速）

| 方法 | 路径 | 说明 | 权限 |
|------|------|------|------|
| GET  | `/activity/v1/games` | 活跃活动列表 | 公开 |
| GET  | `/activity/v1/games/{id}` | 活动详情 | 公开 |
| POST | `/activity/v1/games/{id}/participate` | 参与活动 | ROLE_BUYER |
| GET  | `/activity/v1/games/{id}/my-history` | 参与记录 | ROLE_BUYER |
| POST | `/activity/v1/admin/games` | 创建活动 | ROLE_ADMIN |
| GET  | `/activity/v1/admin/games/{id}/stats` | 活动统计 | ROLE_ADMIN |

## Search Service（:8091）

Meilisearch 商品搜索 + OpenFeature 特性开关试点。

> Search Service **不经过 API Gateway**，由 buyer-bff / seller-bff 以内部地址直接调用。

### 特性开关

| Flag | 关闭后行为 |
|------|-----------|
| `search-autocomplete` | 联想词接口返回 `503 SC_FEATURE_DISABLED` |
| `search-trending` | 热门搜索榜接口返回 `503 SC_FEATURE_DISABLED` |
| `search-locale-aware` | 忽略 `locales` 参数，回退到默认搜索 |

```yaml
# feature-toggles.yaml（热加载，无需重启）
flags:
  search-autocomplete: true
  search-trending: true
  search-locale-aware: false
```

| 方法 | 路径 | 说明 |
|------|------|------|
| GET  | `/search/v1/products` | 商品全文搜索（facet + sort） |
| GET  | `/search/v1/queries/autocomplete` | 联想词 |
| GET  | `/search/v1/queries/trending` | 热门搜索榜 |
| POST | `/search/v1/products/_reindex` | 触发全量重建（等待完成后返回） |

## Webhook Service（:8093）

开放平台 Webhook，供第三方订阅业务事件。

- HMAC-SHA256 签名（每次推送带 `X-Shop-Signature`）
- 指数退避重试（最多 5 次，间隔 1 / 2 / 4 / 8 / 16 分钟）
- 订阅事件类型：order、wallet、user

## Subscription Service（:8094）

订阅计划管理与自动续费。

- 订阅计划（MONTHLY / QUARTERLY / ANNUAL）
- 订阅生命周期：ACTIVE → PAUSED → CANCELLED
- `@Scheduled` 自动续费任务，通过 wallet-service 扣款
```

- [x] **Step 4: 验证**

```bash
cd docs-site && npm run build 2>&1 | grep -E "error|Error|broken" || echo "Build OK"
```

- [x] **Step 5: Commit**

```bash
git add docs-site/docs/services/
git commit -m "docs(docs-site): split domain-services into core/growth/platform (with Profile Service)"
```

---

## Task 10: 新建 roadmap.md

**Files:**
- Modify: `docs-site/docs/roadmap.md`

- [x] **Step 1: 写入 roadmap.md**

```markdown
---
sidebar_position: 1
title: Roadmap
---

# Roadmap

## 当前进度

| Phase | 主题 | 状态 |
|-------|------|------|
| Phase 1 | 核心购物闭环（订单、支付、游客流） | ✅ 完成 |
| Phase 2 | 通知服务（Channel SPI + 7 种邮件模板） | ✅ 完成 |
| Phase 3 | 用户增长（loyalty、promotion、社交登录） | 🟡 90% |
| Phase 4 | 参与 & 变现（activity 后端、搜索、支付扩展） | 🟡 60% |
| Phase 5 | 平台扩张（商家入驻、直播、跨境） | ⬜ 规划中 |
| Phase 6 | AI & 生态（AI 推荐、开放平台） | ⬜ 规划中 |

## 下一步 Top 5

1. **Apple Sign-In / SMS OTP** — 社交登录补全（当前仅 Google）
2. **注册成功页 + 邀请裂变链接** — buyer-portal 缺失（无 /register endpoint）
3. **Garage S3 + Loki/Tempo/Grafana** — 可观测性可视化闭环（当前 OTEL 仅 debug exporter）
4. **搜索增强** — Meilisearch 向量搜索、语义搜索、多语言分词
5. **支付扩展** — Apple Pay / Google Pay / PayPal / BNPL

## 不在计划内

- 新游戏类型（Quiz / SlashPrice / GroupBuy）：依赖产品验证，不抢占工程资源
- 实时推送（WebSocket）：SSE 满足当前需求
- 多租户架构：超出当前验证范围

详细优先级和依赖图：[DELIVERY-PRIORITY-AND-DEPENDENCY-MAP](https://github.com/meirongdev/shop/blob/main/docs/DELIVERY-PRIORITY-AND-DEPENDENCY-MAP.md)
```

- [x] **Step 2: 验证**

```bash
cd docs-site && npm run build 2>&1 | grep -E "error|Error|broken" || echo "Build OK"
```

- [x] **Step 3: Commit**

```bash
git add docs-site/docs/roadmap.md
git commit -m "docs(docs-site): add roadmap page with phase progress and next steps"
```

---

## Task 11: 丰富 engineering/observability.md 和 engineering/standards.md 内容

**Files:**
- Modify: `docs-site/docs/engineering/observability.md`
- Modify: `docs-site/docs/engineering/standards.md`

> 这两个文件在 Task 2 中已从原路径移入 `engineering/` 子目录。
> 本 Task 按 spec 4.11 要求丰富 observability 内容，并为 standards 补充摘要。

- [x] **Step 1: 更新 engineering/observability.md**

将 `docs-site/docs/engineering/observability.md` 替换为：

```markdown
---
sidebar_position: 2
title: 可观测性
---

# 可观测性

## 当前落地状态

| 组件 | 状态 | 说明 |
|------|------|------|
| Prometheus | ✅ | 所有服务暴露 `/actuator/prometheus`；Kind 已配置抓取 |
| OTEL Collector | ✅ | 已部署，trace 接入；⚠️ **当前仅 debug exporter，trace 未持久化** |
| 结构化日志（Logstash JSON） | ✅ | 所有服务已统一，便于聚合分析 |
| Grafana | 🔲 | 待部署（接 Prometheus + Loki + Tempo） |
| Tempo（链路存储） | 🔲 | 待部署（需 Garage S3 作后端） |
| Loki（日志存储） | 🔲 | 待部署（需 Garage S3 作后端） |
| Garage S3 | 🔲 | Kind 内 S3 兼容存储，同时为 Loki/Tempo 提供后端 |

> OTEL Collector 当前使用 debug exporter，trace 数据输出到日志但不持久化。
> 完整链路查询需先完成 Garage S3 → Tempo 接入。

## 待部署路线图

部署顺序（各步骤前置依赖见下）：

1. **Garage S3** — 单节点 Kind 对象存储（`replication_mode = "none"`，`db_engine = "lmdb"`）
2. **Tempo** — 接入 Garage S3 作 block store，替换 OTEL Collector debug exporter
3. **Loki** — 接入 Garage S3 作 chunks 存储
4. **Grafana** — 配置 Datasources：Prometheus + Tempo + Loki
5. **Prometheus Alert Rules** — 落地 P1/P2 告警（参考 `docs/OBSERVABILITY-ALERTING-SLO.md`）
6. **Kafka consumer lag 监控** — 补充 `consumergroup_lag` 指标 + Grafana Panel

## SLO 草案

| 服务 | 指标 | SLO 目标 | 当前状态 |
|------|------|---------|---------|
| api-gateway | p99 延迟 | < 200 ms | ⚠️ 无 Grafana，不可观测 |
| order-service | 下单成功率 | ≥ 99.5% | ⚠️ 无业务指标埋点 |
| search-service | 搜索 p95 | < 500 ms | ⚠️ 无 Tempo |
| notification-service | 邮件送达率 | ≥ 99% | ⚠️ 无业务指标 |

> SLO 目标值为草案，待 Grafana + Alert Rules 部署后校准。

## Metrics（指标）

所有服务在 `/actuator/prometheus` 暴露 Prometheus 格式的指标。

### 常用指标

| 指标 | 说明 |
|------|------|
| `http_server_requests_seconds` | HTTP 请求延迟（p50/p95/p99） |
| `jvm_memory_used_bytes` | JVM 内存使用 |
| `hikaricp_connections_active` | 数据库连接池活跃连接数 |
| `kafka_consumer_records_consumed_total` | Kafka 消费消息数 |
| `resilience4j_circuitbreaker_state` | Circuit Breaker 状态（4 个 CB） |

## Tracing（追踪）

使用 OpenTelemetry + Micrometer Tracing Bridge。

| 配置项 | 值 |
|--------|---|
| 采样率 | 100% |
| 导出协议 | OTLP HTTP |
| 导出端点 | `http://otel-collector:4318/v1/traces` |
| 当前存储 | debug log（⚠️ 不持久化） |

Trace ID 通过 `TraceIdExtractor` 从 MDC 注入到 API 响应的 `traceId` 字段。

## Logging（日志）

结构化日志格式（Logstash JSON），便于 Loki 聚合：

```yaml
logging:
  structured:
    format:
      console: logstash
```

## Health Checks

| 端点 | 说明 |
|------|------|
| `/actuator/health/readiness` | 就绪检查（含 DB 连接） |
| `/actuator/health/liveness` | 存活检查 |

## 权威文档

- 完整 SLO 细节与告警规则：[`docs/OBSERVABILITY-ALERTING-SLO.md`](https://github.com/meirongdev/shop/blob/main/docs/OBSERVABILITY-ALERTING-SLO.md)
```

- [x] **Step 2: 更新 engineering/standards.md — 补充摘要内容**

在 `docs-site/docs/engineering/standards.md` 开头的 frontmatter 之后，确认文件包含以下节（已存在则跳过）：

当前文件如果与原 `engineering-standards.md` 相同，直接追加以下 frontmatter 修改（`sidebar_position: 1`）并确认已包含当前结论表格和治理缺口列表。如内容不足，补充以下章节：

```markdown
---
sidebar_position: 1
title: 工程规范
---
```

> **验证要点**：打开文件确认至少包含：当前结论、技术栈表格、治理缺口三节。
> 如原文件内容已完整（来自 engineering-standards.md 迁移），只需修改 frontmatter 的 sidebar_position 为 1。

- [x] **Step 3: 构建验证**

```bash
cd docs-site && npm run build 2>&1 | grep -E "error|Error|broken" || echo "Build OK"
```

- [x] **Step 4: Commit**

```bash
git add docs-site/docs/engineering/observability.md docs-site/docs/engineering/standards.md
git commit -m "docs(docs-site): enrich engineering/observability with deployment roadmap and SLO draft"
```

---

## Task 12: 清理旧文件，最终验证

**Files:**
- Delete: `docs-site/docs/modules/auth-server.md`（内容已并入 services/core.md）
- Delete: `docs-site/docs/modules/domain-services.md`（内容已拆分）
- Delete: `docs-site/docs/modules/` 目录（如已空）

- [x] **Step 1: 删除已迁移的旧文件**

```bash
cd docs-site/docs
rm modules/auth-server.md
rm modules/domain-services.md
rmdir modules 2>/dev/null || echo "modules dir not empty, check remaining files"
```

- [x] **Step 2: 完整构建验证**

```bash
cd docs-site && npm run build
```

预期：`Generated static files in "build".`，零 broken links，零报错。

- [x] **Step 3: 本地预览验证（可选）**

```bash
cd docs-site && npm run serve
# 浏览器打开 http://localhost:3000/shop/
# 检查：sidebar 5 分区显示正确 / 所有链接可点 / 旧 URL 重定向正常
```

- [x] **Step 4: Commit**

```bash
git add -A
git commit -m "docs(docs-site): remove migrated legacy module files, final cleanup"
```

---

## Task 13: 更新 architecture/index.md（从旧 architecture.md 精简）

**Files:**
- Modify: `docs-site/docs/architecture/index.md`

> `architecture/index.md` 已在 Task 2 从原 `architecture.md` 移入。
> 此 task 在现有内容基础上确认以下章节存在，必要时补充。

- [x] **Step 1: 确认 architecture/index.md 包含以下内容，不足则补充**

需包含的节：
1. 整体架构图（三层：Edge / BFF / Domain）
2. 数据库隔离原则（每服务独立 schema）
3. 核心/非核心解耦原则（一句话 + 链接 event-driven.md）
4. 子页导航（→ API Gateway / → BFF 模式 / → 事件驱动）

若现有内容已覆盖以上四节，跳过修改。

- [x] **Step 2: 验证**

```bash
cd docs-site && npm run build 2>&1 | grep -E "error|Error|broken" || echo "Build OK"
```

- [x] **Step 3: Commit（若有修改）**

```bash
git add docs-site/docs/architecture/index.md
git commit -m "docs(docs-site): finalize architecture index with nav links"
```

---

## 验收标准

全部 Task 完成后，以下条件均须满足：

- [x] `npm run build` 零报错、零 broken links
- [x] Sidebar 显示 5 个分区（快速开始 / 技术栈 / 架构设计 / 服务模块 / 工程实践）
- [x] 旧 URL（如 `/local-deployment`）重定向到新路径
- [x] `intro.md` 包含完整服务全景表（17 个服务含端口）
- [x] `tech-stack/index.md` 包含选型决策表，无代码片段
- [x] `services/core.md` 包含 Profile Service
- [x] `architecture/event-driven.md` 包含 RewardDispatcher 桩实现说明
- [x] `architecture/bff-pattern.md` 为模式说明而非 API 参考手册
