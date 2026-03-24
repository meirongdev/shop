# Shop Platform — 弹性治理策略 设计文档

> 版本：1.0 | 日期：2026-03-23 | 状态：已批准

---

## 一、概述

### 1.1 目标

本文档定义 Shop Platform 微服务架构的**弹性治理（Resilience）全局策略**，覆盖：

1. **BFF 层**：为所有下游调用提供完整的四层防御（TimeLimiter → Bulkhead → CircuitBreaker → Retry）
2. **Domain Service 层**：为三个存在 best-effort 补偿缺口的服务（promotion-service、marketplace-service、loyalty-service）建立持久化补偿任务（Compensation Task Outbox），确保终态一致性
3. **Kafka Consumer 层**：强制消费幂等 key 规范，防止重复处理造成业务数据错误

当前问题：

- buyer-bff 仅有 4 个 CircuitBreaker，缺少 Retry / Bulkhead / TimeLimiter
- seller-bff 仅有 1 个 CircuitBreaker（searchService），缺少其余下游防护
- 优惠券核销、库存扣减、积分退还三个补偿路径在服务崩溃时无法保证最终一致性
- Kafka consumer 没有统一幂等 key 规范，存在重复消费风险

### 1.2 设计原则

| 编号 | 原则 | 说明 |
|------|------|------|
| P1 | **快速失败，不雪崩** | TimeLimiter 限制单次调用耗时上限，防止虚拟线程因等待 IO 而堆积 |
| P2 | **隔离资源，不踩踏** | Bulkhead 将各下游限制在独立的并发桶，防止单个慢服务耗尽公共资源 |
| P3 | **熔断恢复，分级容忍** | CircuitBreaker 按服务重要性分级：核心服务（order/marketplace）熔断阈值更低，降级更快 |
| P4 | **仅对幂等接口重试** | Retry 仅装饰 GET 及显式幂等 POST（如搜索、查询），禁止对写操作重试 |
| P5 | **补偿必须持久化** | 任何 best-effort 的 Kafka 消息补偿均替换为 Outbox 模式，保证 at-least-once 投递 |
| P6 | **幂等消费是底线** | 所有 Kafka consumer 必须通过 `idempotencyKey` 防重；ArchUnit 规则保证不遗漏 |

### 1.3 技术选型

| 组件 | 选型 | 说明 |
|------|------|------|
| 熔断 / 限时 / 重试 / 舱壁 | Resilience4j 2.x（Spring Boot Starter） | 与 Spring Boot 3.x AOP 深度集成，支持注解组合 |
| 补偿持久化 | JPA + Flyway（Outbox 模式） | 与各服务现有 MySQL + Flyway 方案一致，不引入额外中间件 |
| Kafka 幂等验证 | ArchUnit 4.x | 编译期/CI 静态规则检查，零运行时开销 |
| 配置共享 | shop-common AutoConfiguration | 使用 `spring.factories` / `AutoConfiguration.imports` 提供默认值，各服务可覆盖 |
| ID 生成 | ULID（CHAR(26)） | 全局统一，compensation_task 表 id 列使用 ULID |

---

## 二、架构

### 2.1 BFF 层分层防御模型

下游调用的执行路径（洋葱模型，从外到内）：

```
HTTP Request
    │
    ▼
[1] @TimeLimiter          ← 硬超时（如 3s），超时直接 TimeoutException
    │
    ▼
[2] @Bulkhead             ← 并发限制（如 10 并发），超出 BulkheadFullException
    │
    ▼
[3] @CircuitBreaker       ← 熔断（失败率 ≥ 50% 时断路，等待 20s 后半开）
    │
    ▼
[4] @Retry                ← 重试（仅幂等接口，最多 2 次，指数退避 200ms）
    │
    ▼
Downstream HTTP Call (RestClient / Virtual Thread)
```

**注意：** Resilience4j 注解的执行顺序由 `resilience4j.spring.annotations.aspectorder.*` 决定，默认顺序为 `Retry(3) → CircuitBreaker(2) → RateLimiter(1) → TimeLimiter(?) → Bulkhead(?)`。为保证上述洋葱顺序，**在 shop-common AutoConfiguration 中显式配置 AOP 执行顺序**：

```yaml
resilience4j:
  circuitbreaker:
    circuit-breaker-aspect-order: 1
  retry:
    retry-aspect-order: 2
  bulkhead:
    bulkhead-aspect-order: 3
  timelimiter:
    time-limiter-aspect-order: 4   # 最外层先执行
```

> Spring AOP order 数值越大，aspect 越先执行（越靠外层）

### 2.2 注解顺序与语义

对一个方法同时标注四个注解时，**标注顺序不影响执行顺序**（由 aspect order 决定），但推荐保持与执行顺序一致的书写顺序以便阅读：

```java
@TimeLimiter(name = "promotionService")
@Bulkhead(name = "promotionService", type = Bulkhead.Type.SEMAPHORE)
@CircuitBreaker(name = "promotionService", fallbackMethod = "listPromotionsFallback")
@Retry(name = "promotionService")          // 仅对幂等查询接口
public List<PromotionApi.OfferResponse> listPromotions() { ... }
```

**写操作（POST 变更）** 仅保留前三层，不加 `@Retry`：

```java
@TimeLimiter(name = "marketplaceService")
@Bulkhead(name = "marketplaceService", type = Bulkhead.Type.SEMAPHORE)
@CircuitBreaker(name = "marketplaceService", fallbackMethod = "deductInventoryFallback")
public void deductInventoryForCheckout(String productId, int quantity) { ... }
```

### 2.3 补偿持久化架构（CompensationTask Outbox）

三个服务各自维护一张 `compensation_task` 表，替代当前 best-effort Kafka 消费中的直接状态更新：

```
order-service
    │ Kafka: order.completed (已有 Outbox 发布)
    │
    ├──▶ promotion-service consumer
    │        └─ 核销 coupon_instance.status = USED
    │           ❌ 当前：直接 UPDATE，失败则丢失
    │           ✅ 改为：写入 compensation_task，Scheduler 异步重试
    │
    ├──▶ marketplace-service consumer
    │        └─ 扣减 SKU 库存 product_variant.inventory_count -= N
    │           ❌ 当前：直接 UPDATE，失败则丢失
    │           ✅ 改为：写入 compensation_task，Scheduler 异步重试
    │
    └──▶ loyalty-service consumer
             └─ 增加 loyalty_account.balance / loyalty_transaction
                ❌ 当前：直接 INSERT/UPDATE，失败则丢失
                ✅ 改为：写入 compensation_task，Scheduler 异步重试
```

**CompensationTask 状态机：**

```
PENDING → PROCESSING → DONE
                    ↘ FAILED (retry_count >= max_retries)
```

---

## 三、数据模型

### 3.1 compensation_task 表

以下 DDL 适用于 promotion-service、marketplace-service、loyalty-service 三个服务，**表名相同，各自独立存在于各自数据库中**：

```sql
CREATE TABLE compensation_task (
    id              CHAR(26)     NOT NULL PRIMARY KEY COMMENT 'ULID',
    task_type       VARCHAR(64)  NOT NULL COMMENT '任务类型，如 COUPON_REDEEM / INVENTORY_DEDUCT / POINTS_EARN',
    aggregate_id    VARCHAR(64)  NOT NULL COMMENT '业务主键，如 orderId / couponInstanceId',
    idempotency_key VARCHAR(128) NOT NULL COMMENT '幂等 key，防止重复执行',
    payload         TEXT         NOT NULL COMMENT 'JSON 格式的任务参数',
    status          VARCHAR(16)  NOT NULL DEFAULT 'PENDING'
                                          COMMENT 'PENDING | PROCESSING | DONE | FAILED',
    retry_count     INT          NOT NULL DEFAULT 0,
    max_retries     INT          NOT NULL DEFAULT 3,
    last_error      TEXT         NULL COMMENT '最近一次失败的异常信息（截断至 2000 字符）',
    scheduled_at    TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6)
                                          COMMENT '最早可执行时间（指数退避写入）',
    created_at      TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at      TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6)
                                          ON UPDATE CURRENT_TIMESTAMP(6),
    UNIQUE KEY uk_idempotency (idempotency_key),
    INDEX idx_status_scheduled (status, scheduled_at),
    INDEX idx_aggregate (aggregate_id)
) COMMENT '补偿任务持久化表（Outbox 变体）';
```

**幂等 key 构成规范：**

| 服务 | task_type | idempotency_key 格式 |
|------|-----------|---------------------|
| promotion-service | `COUPON_REDEEM` | `COUPON_REDEEM:{orderId}:{couponInstanceId}` |
| marketplace-service | `INVENTORY_DEDUCT` | `INVENTORY_DEDUCT:{orderId}:{skuId}` |
| loyalty-service | `POINTS_EARN` | `POINTS_EARN:{orderId}:{buyerId}` |
| loyalty-service | `POINTS_REFUND` | `POINTS_REFUND:{orderId}:{buyerId}` |

---

## 四、配置矩阵

### 4.1 各下游服务 R4j 参数表（按服务重要性分级）

#### 服务重要性分级

| 级别 | 服务 | 说明 |
|------|------|------|
| **Critical** | order-service, marketplace-service (库存扣减) | 写操作，失败直接影响下单流程，不允许静默降级 |
| **Important** | loyalty-service, promotion-service (核销路径) | 写操作，允许有限降级（记录补偿任务后返回） |
| **Optional** | promotion-service (查询), search-service | 只读操作，允许返回空/降级数据 |
| **Advisory** | profile-service, wallet-service (查询) | 读多写少，失败可返回缓存或空数据 |

#### buyer-bff CircuitBreaker 参数

| 实例名 | sliding-window-size | failure-rate-threshold | wait-duration-in-open-state | permitted-half-open-calls | 级别 |
|--------|---------------------|------------------------|----------------------------|--------------------------|------|
| `orderService` | 10 | 40% | 15s | 2 | Critical |
| `marketplaceService` | 20 | 40% | 10s | 3 | Critical |
| `loyaltyService` | 20 | 50% | 20s | 3 | Important |
| `promotionService` | 20 | 50% | 20s | 3 | Important |
| `searchService` | 10 | 50% | 30s | 3 | Optional |
| `profileService` | 20 | 60% | 30s | 5 | Advisory |
| `walletService` | 20 | 50% | 20s | 3 | Important |

#### seller-bff CircuitBreaker 参数

| 实例名 | sliding-window-size | failure-rate-threshold | wait-duration-in-open-state | permitted-half-open-calls | 级别 |
|--------|---------------------|------------------------|----------------------------|--------------------------|------|
| `orderService` | 10 | 40% | 15s | 2 | Critical |
| `marketplaceService` | 20 | 40% | 10s | 3 | Critical |
| `promotionService` | 20 | 50% | 20s | 3 | Important |
| `walletService` | 20 | 50% | 20s | 3 | Important |
| `searchService` | 10 | 50% | 30s | 3 | Optional |
| `profileService` | 20 | 60% | 30s | 5 | Advisory |

### 4.2 Retry 策略（仅幂等接口）

**原则：仅对 GET 类方法和显式标记为幂等的查询 POST 添加 `@Retry`。** 以下操作**禁止**添加 Retry：库存扣减、积分扣减、优惠券核销、创建订单。

```yaml
resilience4j:
  retry:
    instances:
      # 查询类（Optional / Advisory 级别）共用此配置
      searchService:
        max-attempts: 3
        wait-duration: 200ms
        exponential-backoff-multiplier: 2.0
        retry-exceptions:
          - org.springframework.web.client.ResourceAccessException
          - java.net.ConnectException
        ignore-exceptions:
          - dev.meirong.shop.common.error.BusinessException
      promotionService:    # 仅用于 listPromotions / listCoupons
        max-attempts: 2
        wait-duration: 300ms
        retry-exceptions:
          - org.springframework.web.client.ResourceAccessException
        ignore-exceptions:
          - dev.meirong.shop.common.error.BusinessException
      profileService:
        max-attempts: 2
        wait-duration: 200ms
        retry-exceptions:
          - org.springframework.web.client.ResourceAccessException
        ignore-exceptions:
          - dev.meirong.shop.common.error.BusinessException
```

**不配置 Retry 的实例：** `orderService`、`marketplaceService`（写操作）、`loyaltyService`（写操作）、`walletService`（写操作）

### 4.3 Bulkhead 容量配置

采用 `SEMAPHORE` 类型（Virtual Thread 环境下与 `THREADPOOL` 等效，且不引入额外线程池）：

```yaml
resilience4j:
  bulkhead:
    instances:
      orderService:
        max-concurrent-calls: 8
        max-wait-duration: 0ms     # 满舱立即失败，不排队
      marketplaceService:
        max-concurrent-calls: 15
        max-wait-duration: 0ms
      loyaltyService:
        max-concurrent-calls: 10
        max-wait-duration: 0ms
      promotionService:
        max-concurrent-calls: 10
        max-wait-duration: 0ms
      searchService:
        max-concurrent-calls: 20
        max-wait-duration: 50ms    # 搜索可稍作等待
      profileService:
        max-concurrent-calls: 15
        max-wait-duration: 0ms
      walletService:
        max-concurrent-calls: 8
        max-wait-duration: 0ms
```

### 4.4 TimeLimiter 超时配置

```yaml
resilience4j:
  timelimiter:
    instances:
      orderService:
        timeout-duration: 3s
        cancel-running-future: true
      marketplaceService:
        timeout-duration: 3s
        cancel-running-future: true
      loyaltyService:
        timeout-duration: 4s
        cancel-running-future: true
      promotionService:
        timeout-duration: 4s
        cancel-running-future: true
      searchService:
        timeout-duration: 5s
        cancel-running-future: true
      profileService:
        timeout-duration: 4s
        cancel-running-future: true
      walletService:
        timeout-duration: 3s
        cancel-running-future: true
```

> `TimeLimiter` 在同步 RestClient 场景下需包装为 `CompletableFuture`。当前 BFF 已使用 Virtual Thread executor，可通过 `CompletableFuture.supplyAsync()` 包装后套用 `@TimeLimiter`；或在 shop-common 提供统一的 `ResilienceHelper.timed()` 工具方法。

---

## 五、Kafka 幂等规范

### 5.1 Consumer 幂等 key 标准

所有 Kafka consumer 方法**必须**在处理消息前检查幂等 key，防止 at-least-once 语义下的重复处理。

**规范：**

1. 幂等 key 优先从消息 header `X-Idempotency-Key` 读取
2. 若 header 不存在，则由消费方自行构造，格式为 `{topic}:{partition}:{offset}` 或业务 key
3. 检查方式：查询 compensation_task 或业务表中的唯一约束（`uk_idempotency`）
4. 已处理则跳过（幂等返回），未处理则在**同一事务**内写入业务数据并标记已处理

**Kafka Listener 示例模式：**

```java
@KafkaListener(topics = "order.completed", groupId = "promotion-service")
@Transactional
public void onOrderCompleted(ConsumerRecord<String, String> record) {
    String idempotencyKey = "COUPON_REDEEM:" + extractOrderId(record) + ":" + extractCouponId(record);
    // 检查并写入 compensation_task（status=PROCESSING）
    // 若 uk_idempotency 冲突则幂等跳过
    compensationTaskService.executeOrSkip(idempotencyKey, TaskType.COUPON_REDEEM, payload);
}
```

### 5.2 ArchUnit 规则要求

在 `shop-archetypes` 模块（或各服务的 `src/test/java` 中）添加 ArchUnit 测试，验证：

**规则 1：所有 @KafkaListener 方法所在类必须存在幂等保护**

```java
// 规则：标注了 @KafkaListener 的方法，其所在类必须依赖
// CompensationTaskService 或 IdempotencyCheckService（按接口识别）
ArchRule kafkaListenerIdempotencyRule = methods()
    .that().areAnnotatedWith(KafkaListener.class)
    .should(haveOwnerThatDependsOnIdempotencyService());
```

**规则 2：@KafkaListener 方法禁止直接操作 JPA Repository 而不经过幂等检查层**

**规则 3（可选）：所有 CompensationTaskService 实现类的 executeOrSkip 方法必须标注 @Transactional**

---

## 六、测试策略

### 6.1 单元测试

| 测试对象 | 测试工具 | 测试要点 |
|---------|---------|---------|
| BFF CircuitBreaker fallback | JUnit 5 + Mockito | 验证 fallback 返回值符合降级预期 |
| BFF Retry 不触发写操作 | JUnit 5 + Mockito | 确认库存扣减方法无 @Retry 注解 |
| CompensationTaskService.executeOrSkip | JUnit 5 + @DataJpaTest | 幂等 key 重复插入时不抛出异常，直接跳过 |
| CompensationTaskScheduler | JUnit 5 + Mockito | 验证 PENDING 任务被捞取并执行 |

### 6.2 集成测试

| 场景 | 方式 | 验证点 |
|------|------|--------|
| CircuitBreaker 熔断触发 | WireMock 注入 5xx 错误 | CB 在达到阈值后返回 fallback |
| TimeLimiter 超时触发 | WireMock 注入延迟 | 超时抛出 `TimeoutException`，fallback 执行 |
| Bulkhead 满舱 | 并发线程超过 max-concurrent-calls | 抛出 `BulkheadFullException` |
| 补偿任务幂等重复执行 | 直接调用 executeOrSkip 两次相同 key | 第二次无副作用 |
| 补偿任务 Scheduler 端到端 | @SpringBootTest + TestContainers (MySQL) | PENDING → DONE 状态流转 |

### 6.3 混沌工程（可选，Phase 2）

- 使用 Chaos Monkey for Spring Boot（`chaos-monkey-spring-boot`）在 CI 环境注入延迟和异常
- 验证 BFF 的 Bulkhead + CircuitBreaker 组合在 50% 下游错误率下仍能正常提供服务

---

## 七、实施路径（分阶段）

### Phase 1（本 Sprint）：BFF 层 Resilience 补全

**目标：** 两个 BFF 的所有下游调用均有完整四层防护

1. shop-common 提取 R4j 默认配置（AutoConfiguration）
2. buyer-bff 补全所有下游的 Retry / Bulkhead / TimeLimiter 注解 + yml 配置
3. seller-bff 补全所有下游的完整 R4j 套件
4. ArchUnit：验证写操作方法不包含 @Retry 注解

**验收：** buyer-bff 和 seller-bff 的每个 `@CircuitBreaker` 方法都同时有 `@TimeLimiter` 和 `@Bulkhead`；写操作无 `@Retry`

### Phase 2（下一 Sprint）：Domain Service 补偿持久化

**目标：** 消除三个服务的 best-effort 补偿缺口

5. promotion-service：compensation_task 表 + Flyway + CompensationTaskService + Scheduler
6. marketplace-service：同上，task_type = INVENTORY_DEDUCT
7. loyalty-service：同上，task_type = POINTS_EARN / POINTS_REFUND

**验收：** 关闭 promotion-service 后，order.completed 事件中的优惠券核销在服务恢复后能自动补偿完成

### Phase 3（随后）：幂等规范 + 监控

8. ArchUnit 规则验证 Kafka consumer 幂等保护
9. Grafana Dashboard 添加 R4j 指标（circuitbreaker.state, retry.calls, bulkhead.available）
10. 告警：CircuitBreaker OPEN 状态持续 > 5min 触发 PagerDuty

---

## 附录 A：不在本文档范围内

- api-gateway 层的 Rate Limiting（见 `2026-03-22-api-gateway-virtual-threads-migration-design.md`）
- ULID 迁移（补偿任务表新建时直接使用 ULID，存量表迁移另行计划）
- Kafka Producer Outbox 发布重试（order-service 已有，其他服务参照执行）

## 附录 B：已知约束

1. `@TimeLimiter` 与同步 `RestClient` 的配合需要 `CompletableFuture` 包装，在当前 Virtual Thread 环境下需要额外的包装层，具体实现见实施计划 Task 1
2. `SEMAPHORE` 类型 Bulkhead 在 Virtual Thread 中无死锁风险，但 `max-wait-duration: 0ms` 可能在突发流量下产生较多 `BulkheadFullException`，上线后需根据监控数据调整
3. compensation_task Scheduler 的 `findTop20ByStatusAndScheduledAtBefore` 查询需要 `(status, scheduled_at)` 复合索引（DDL 中已包含）
