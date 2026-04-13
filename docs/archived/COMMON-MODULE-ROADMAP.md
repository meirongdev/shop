# Shop Platform — 公共模块拆分与演进 Roadmap

> 版本：1.0 | 日期：2026-04-01
> 配套文档：`docs/ARCHITECTURE-DESIGN.md`、`docs/ENGINEERING-STANDARDS-2026.md`、`docs/SECURITY-BASELINE-2026.md`

---

## 一、背景与目标

当前 `shop-common` 承载了 7 个 AutoConfiguration、~35 个类，涵盖 API 响应、异常处理、内部安全、幂等性、弹性、Feature Toggle、可观测性、API 兼容性等不相关的功能域。所有 16 个服务统一依赖该模块，导致：

- **依赖膨胀**：域服务引入了不需要的 Resilience4j、Redisson、OpenFeature、Pyroscope 等重依赖
- **职责模糊**：修改幂等逻辑可能影响不相关的 API 兼容性代码
- **选择性缺失**：服务无法按需引入特定能力

**目标**：将 `shop-common` 拆分为 `core` + 多个可选 `starter`，并规划后续公共组件演进路线。

---

## 二、业界参考

| 项目 | 公共模块拆分策略 | 模块数 | 命名规范 |
|------|-----------------|--------|----------|
| **pig** (pig-mesh) | 最细粒度：core / security / feign / log / datasource / mybatis / oss / swagger / excel / seata / xss / websocket | 13 | `pig-common-{concern}` |
| **RuoYi-Cloud** | 中等粒度：core / datascope / datasource / log / redis / seata / security / sensitive / swagger | 9 | `ruoyi-common-{concern}` |
| **mall-swarm** | 扁平：common + security + mbg | 3 | `mall-{concern}` |
| **ShenYu** | 三层分离：common（零 Spring 依赖） → spi → spring-boot-starter-* | 10+ | `shenyu-spring-boot-starter-{concern}` |
| **Eventuate-Tram** | API/SPI/实现分离：messaging → consumer-common → consumer-kafka/rabbitmq/redis | 15+ | `eventuate-tram-{concern}-{transport}` |
| **JHipster** | 单一 jar：jhipster-framework（不拆分） | 1 | `jhipster-framework` |

**业界共识**：`{project}-common-{concern}` 模式最主流；`core` 模块全员引入，其余按需选择。

---

## 三、当前 shop-common 功能域分析

| 功能域 | 类 | 外部依赖 | 实际使用方 | 建议归属 |
|--------|---|----------|-----------|----------|
| ApiResponse / 异常体系 / ShopProblemDetails | 5 | spring-web, validation | 全部服务 | **core** |
| GlobalExceptionHandler / TomcatH2 | 2 | spring-webmvc, tomcat | 全部 Web 服务 | **core** |
| JacksonCompatibility | 1 | jackson | 全部服务 | **core** |
| TraceIdExtractor | 1 | micrometer | 全部服务 | **core** |
| TrustedHeaderNames | 1 | 无 | 全部服务 | **core** |
| Kafka 异常类 | 2 | 无 | Kafka 消费者 | **core**（轻量，无额外依赖） |
| IdempotencyGuard / BloomFilter | 6 | redisson, JPA | Kafka 消费者服务 | **starter-idempotency** |
| ResilienceHelper | 2 | resilience4j ×4 | BFF 层 | **starter-resilience** |
| ApiCompatibility 拦截器 | 4 | spring-webmvc | BFF 层 | **starter-api-compat** |
| FeatureToggle / OpenFeature | 4 | openfeature-sdk | 按需 | **starter-feature-toggle** |
| Observability / Pyroscope | 2 | pyroscope, otel-logback | 全部服务 | **core**（全员需要） |

---

## 四、拆分方案

### 4.1 模块结构

```
shop-common/                           ← 父 POM（聚合器）
├── shop-common-bom/                   ← BOM：统一管理所有 common 模块版本
├── shop-common-core/                  ← 最小公共基础，所有服务必须依赖
├── shop-starter-idempotency/          ← Kafka 幂等消费守卫
├── shop-starter-resilience/           ← BFF 弹性防护
├── shop-starter-api-compat/           ← API 版本协商
└── shop-starter-feature-toggle/       ← Feature Flag
```

### 4.2 各模块详细内容

#### `shop-common-core`（全员必选）

```
dev.meirong.shop.common/
├── api/
│   └── ApiResponse.java
├── error/
│   ├── BusinessErrorCode.java
│   ├── BusinessException.java
│   └── CommonErrorCode.java
├── http/
│   └── TrustedHeaderNames.java
├── json/
│   └── JacksonCompatibilityAutoConfiguration.java
├── kafka/
│   ├── NonRetryableKafkaConsumerException.java
│   └── RetryableKafkaConsumerException.java
├── observability/
│   ├── ObservabilityAutoConfiguration.java
│   └── ProfilingProperties.java
├── trace/
│   └── TraceIdExtractor.java
└── web/
    ├── GlobalExceptionHandler.java
    ├── ShopProblemDetails.java
    └── TomcatHttp2AutoConfiguration.java
```

**依赖**：spring-web, spring-webmvc, jackson, validation, micrometer, pyroscope, otel-logback, tomcat (optional)
**AutoConfiguration**：3 个（Jackson, Observability, TomcatH2）

#### `shop-starter-idempotency`（Kafka 消费者服务选用）

```
dev.meirong.shop.common.idempotency/
├── IdempotencyAutoConfiguration.java
├── IdempotencyGuard.java              ← 接口
├── IdempotencyRepository.java
├── DbIdempotencyGuard.java
├── RedisIdempotencyGuard.java
├── BloomFilterProperties.java
└── IdempotencyExempt.java
```

**依赖**：redisson (optional), spring-data-jpa
**使用方**：wallet-service, promotion-service, loyalty-service, order-service, notification-service, activity-service

#### `shop-starter-resilience`（BFF 层选用）

```
dev.meirong.shop.common.resilience/
├── ResilienceAutoConfiguration.java
└── ResilienceHelper.java
```

**依赖**：resilience4j-circuitbreaker, resilience4j-bulkhead, resilience4j-retry, resilience4j-timelimiter
**使用方**：buyer-bff, seller-bff

#### `shop-starter-api-compat`（BFF 层选用）

```
dev.meirong.shop.common.web/
├── ApiCompatibilityAutoConfiguration.java
├── ApiCompatibilityInterceptor.java
├── ApiDeprecation.java
└── CompatibilityHeaderNames.java
```

**依赖**：spring-webmvc
**使用方**：buyer-bff, seller-bff

#### `shop-starter-feature-toggle`（按需选用）

```
dev.meirong.shop.common.feature/
├── FeatureToggleAutoConfiguration.java
├── FeatureToggleProperties.java
├── FeatureToggleService.java
└── OpenFeaturePropertyProvider.java
```

**依赖**：openfeature-sdk
**使用方**：当前 search-service 试点



| 问题 | 说明 |
|------|------|
| 所有服务共享一个密钥 | 任何一个服务泄露，所有内部 API 都暴露 |
| 无身份区分 | marketplace-service 无法区分请求来自 buyer-bff 还是 seller-bff |
| 密钥轮换成本高 | 需要所有服务同时更新 |
| K8s 内多余 | 域服务没有 Ingress/NodePort，外部本来就无法直达 |

**替代方案**：K8s NetworkPolicy（零代码改动，网络层隔离）

```yaml
# 示例：限制 marketplace-service 只接受来自 BFF 的流量
apiVersion: networking.k8s.io/v1
kind: NetworkPolicy
metadata:
  name: marketplace-service-ingress
spec:
  podSelector:
    matchLabels:
      app: marketplace-service
  ingress:
    - from:
        - podSelector:
            matchLabels:
              app: buyer-bff
        - podSelector:
            matchLabels:
              app: seller-bff
      ports:
        - port: 8080
```

**迁移步骤**：
1. 为每个域服务编写 NetworkPolicy
2. 验证 BFF → 域服务调用正常
5. 更新 `docs/SECURITY-BASELINE-2026.md` 东西向安全章节

**中期演进**：引入 Service Mesh（Istio/Linkerd）后，自动获得 mTLS + ServiceAccount 级身份认证。

### 4.4 服务依赖矩阵（拆分后）

| 服务 | core | idempotency | resilience | api-compat | feature-toggle |
|------|:----:|:-----------:|:----------:|:----------:|:--------------:|
| api-gateway | ● | | | | |
| auth-server | ● | | | | |
| buyer-bff | ● | | ● | ● | |
| seller-bff | ● | | ● | ● | |
| buyer-portal | ● | | | | |
| marketplace-service | ● | ● | | | |
| order-service | ● | ● | | | |
| wallet-service | ● | ● | | | |
| promotion-service | ● | ● | | | |
| loyalty-service | ● | ● | | | |
| profile-service | ● | | | | |
| notification-service | ● | ● | | | |
| activity-service | ● | ● | | | |
| search-service | ● | | | | ● |
| webhook-service | ● | | | | |
| subscription-service | ● | | | | |

---

## 五、后续公共组件演进 Roadmap

### Phase 1 — 高价值缺失组件（业务刚需）

#### 1.1 `shop-starter-audit-log` — 操作审计日志

> 业界参考：pig `common-log`、RuoYi `common-log`

**解决什么问题**：电商平台需要记录"谁在什么时间对什么资源做了什么操作"，用于合规审计、问题排查和纠纷处理。当前没有统一的操作审计机制。

**设计要点**：
- `@AuditLog(module = "ORDER", operation = "CANCEL")` 注解 + AOP 切面
- 自动捕获操作人（从 TrustedHeaders 提取）、目标资源 ID、操作结果
- 异步发送到 Kafka topic `audit.log.v1`，由独立消费者写入存储
- 支持记录变更前后快照（`@AuditLog(snapshot = true)`）

**使用场景**：
- 卖家取消订单 → 审计记录
- 钱包提现 → 审计记录
- 商品上下架 → 审计记录

#### 1.2 `shop-starter-distributed-lock` — 分布式锁

> 业界参考：RuoYi `common-redis`、生产系统常见但开源项目较少独立模块化

**解决什么问题**：钱包扣款、库存扣减、订单状态变更等场景需要分布式锁，当前各服务自行实现 Redisson 锁逻辑，容易出现不一致的锁粒度、超时设置和错误处理。

**设计要点**：
- `@DistributedLock(key = "'wallet:' + #buyerId", waitTime = 3, leaseTime = 10)` 注解 + AOP
- 底层 Redisson `RLock`，支持可重入
- 锁获取失败抛出统一 `LockAcquisitionException`
- 支持 SpEL 表达式动态生成锁 key

**使用场景**：
- `wallet-service`：余额操作
- `marketplace-service`：库存扣减
- `order-service`：订单状态变更

#### 1.3 `shop-starter-openapi` — API 文档统一配置

> 业界参考：pig `common-swagger`、RuoYi `common-swagger`
> 已有设计文档：`docs/API-DOCUMENTATION-SPRINGDOC-2026.md`

**解决什么问题**：各服务 OpenAPI 配置分散，缺少统一的安全 scheme、服务信息、分组策略。`docs/ENGINEERING-STANDARDS-2026.md` 已规划但未落地统一 starter。

**设计要点**：
- 自动读取 `spring.application.name` 填充 API title
- 统一配置 Bearer JWT SecurityScheme
- 自动排除 actuator / internal 端点
- `shop.openapi.enabled` 开关（生产可关闭）

### Phase 2 — 安全与合规增强

#### 2.1 `shop-starter-sensitive` — 数据脱敏

> 业界参考：RuoYi `common-sensitive`

**解决什么问题**：买家手机号、收货地址、银行卡号等敏感数据在 API 返回时不应明文展示。当前没有统一脱敏机制，依赖各服务自行处理。

**设计要点**：
- `@Sensitive(strategy = SensitiveStrategy.PHONE)` 注解标记 DTO 字段
- 内置策略：`PHONE`（`138****1234`）、`EMAIL`（`m***@example.com`）、`ID_CARD`、`BANK_CARD`、`ADDRESS`
- 基于 Jackson `JsonSerializer` 实现，序列化时自动脱敏
- 支持自定义策略扩展

**使用场景**：
- profile-service 返回买家/卖家信息
- order-service 返回收货地址
- wallet-service 返回银行卡信息

#### 2.2 `shop-starter-xss` — XSS 过滤

> 业界参考：pig `common-xss`、RuoYi core 内置

**解决什么问题**：商品描述、店铺名称、评价内容等 UGC 内容是 XSS 高危区。当前没有统一的输入过滤。

**设计要点**：
- Servlet Filter 拦截所有 `POST`/`PUT` 请求体
- Jackson `JsonDeserializer` 双重防护，反序列化时过滤 `<script>` 等标签
- 可配置白名单路径（如富文本编辑器需要 HTML 标签的场景）
- 基于 OWASP Java HTML Sanitizer

#### 2.3 `shop-starter-oss` — 文件存储抽象

> 业界参考：pig `common-oss`

**解决什么问题**：商品图片、头像、凭证上传是电商核心功能。当前集群内已部署 Garage S3，但应用层缺少统一的存储抽象。

**设计要点**：
- `FileTemplate` 接口：`upload()` / `download()` / `delete()` / `getPresignedUrl()`
- 实现层：`S3FileTemplate`（MinIO/Garage S3）、`LocalFileTemplate`（本地开发）
- 通过 `shop.oss.type=s3|local` 自动切换
- 自动配置上传大小限制、Content-Type 白名单

### Phase 3 — 规模化后按需引入

| 组件 | 触发条件 | 说明 | 业界参考 |
|------|----------|------|----------|
| `shop-starter-ratelimit` | 开放 API / 高并发秒杀 | 注解式限流 `@RateLimit(key, qps)`，Redisson RRateLimiter 或 Sentinel | pig `common-feign` 内 Sentinel |
| `shop-starter-datascope` | 商家子账号 / 角色体系 | `@DataScope(deptAlias = "d")` 注解，MyBatis/JPA 层自动注入 WHERE 条件 | RuoYi `common-datascope` |
| `shop-starter-multi-tenant` | 平台化 / SaaS 化 | JPA 拦截器自动注入 `tenant_id`，请求上下文传递租户标识 | pig `common-mybatis` 内租户拦截器 |
| `shop-starter-id-generator` | 订单号全局唯一需求 | Snowflake / Leaf，替代 DB 自增 | 生产通用，pig 使用 MyBatis-Plus 内置 |
| `shop-starter-excel` | 后台运营导出 | EasyExcel 封装，流式导出大数据量 | pig `common-excel` |
| `shop-starter-notification-spi` | 通知渠道 > 1 种 | SMS/Email/Push 抽象层，notification-service 内的 Channel SPI 下沉为共享模块 | 通常为独立服务而非共享库 |

---

## 六、命名规范

| 前缀 | 含义 | 规则 |
|------|------|------|
| `shop-common-*` | 纯 POJO / 工具 / 极轻量 Spring 依赖 | 几乎所有服务都引入，零或极少可选依赖 |
| `shop-starter-*` | 带 AutoConfiguration 的可选 Starter | 服务按需引入，自带 `AutoConfiguration.imports` |
| `shop-contracts` | API 常量 + 事件 DTO | 保持不变，零 Spring 依赖 |

**AutoConfiguration 注册**：统一使用 Spring Boot 3.x `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`

**包名规范**：`dev.meirong.shop.common.{concern}`（不因模块拆分改变包结构）

---

## 七、实施优先级总览

```
                      当前                Phase 0              Phase 1              Phase 2              Phase 3
                   shop-common          拆分整理            高价值补齐            安全合规              按需扩展
                   (单体模块)          (模块化)            (刚需组件)           (数据保护)           (规模化)

shop-common    ──→  common-core     ─────────────────────────────────────────────────────────────────────────
                    common-bom
                    starter-idempotency
                    starter-resilience
                    starter-api-compat
                    starter-feature-toggle

新增组件                              starter-audit-log
                                      starter-distributed-lock
                                      starter-openapi
                                                            starter-sensitive
                                                            starter-xss
                                                            starter-oss
                                                                                  starter-ratelimit
                                                                                  starter-datascope
                                                                                  starter-multi-tenant
                                                                                  ...
```

**优先级判断标准**：不是"业界有所以要做"，而是当前项目的实际痛点驱动。Phase 0 解决依赖膨胀；Phase 1 补齐电商平台几乎必然需要的基础设施；Phase 2/3 等业务需求驱动再落地。

---

## 八、与现有文档的关系

| 文档 | 与本文关系 |
|------|-----------|
| `ROADMAP-2026.md` | 本文 Phase 0 对应 ROADMAP 中"平台工程 P1"并行 track |
| `ENGINEERING-STANDARDS-2026.md` | 本文遵循其技术栈基线和横切能力标准 |
| `API-DOCUMENTATION-SPRINGDOC-2026.md` | `shop-starter-openapi` 是其落地载体 |
| `ARCHITECTURE-DESIGN.md` | 拆分后的模块结构需同步更新架构图中的共享组件描述 |
| `SERVICE-TECH-STACK-AND-EXTENSIBILITY.md` | 各服务依赖矩阵变更后需同步更新 |
