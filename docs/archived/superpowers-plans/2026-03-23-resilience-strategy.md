# Shop Platform — 弹性治理策略 实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task.

**Goal:** 为 BFF 层所有下游补全四层 Resilience4j 防御，为三个 domain service 建立补偿持久化机制，以 ArchUnit 规则保障 Kafka 消费幂等规范。

**Spec:** `docs/superpowers/specs/2026-03-23-resilience-strategy-design.md`

**Tech Stack:** Spring Boot 3.5.11 / Java 25 / Resilience4j 2.x / JPA + Flyway / ArchUnit 4.x

---

## Phase 1：BFF 层 Resilience 补全

---

## Task 1: shop-common — 提取 R4j AOP 执行顺序 AutoConfiguration

**Goal:** 统一 `TimeLimiter(4) → Bulkhead(3) → CircuitBreaker(1) → Retry(2)` 的 AOP 执行顺序，避免各服务各自配置。

**Files:**
- Create: `shop-common/src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`
- Create: `shop-common/src/main/java/dev/meirong/shop/common/resilience/ResilienceAutoConfiguration.java`
- Create: `shop-common/src/main/java/dev/meirong/shop/common/resilience/ResilienceHelper.java`

- [ ] **Step 1: 创建 ResilienceAutoConfiguration**

```java
@AutoConfiguration
@ConditionalOnClass(CircuitBreakerRegistry.class)
public class ResilienceAutoConfiguration {
    // 无 Bean 定义，仅通过 @ImportAutoConfiguration 触发 yml 属性绑定
    // AOP 顺序通过 application.yml defaults 注入（各服务可覆盖）
}
```

- [ ] **Step 2: 在 shop-common 的 application.yml 中写入默认 R4j AOP 顺序**

```yaml
# shop-common/src/main/resources/application.yml（新建或合并）
resilience4j:
  circuitbreaker:
    circuit-breaker-aspect-order: 1
  retry:
    retry-aspect-order: 2
  bulkhead:
    bulkhead-aspect-order: 3
  timelimiter:
    time-limiter-aspect-order: 4
```

- [ ] **Step 3: 创建 ResilienceHelper 工具类（TimeLimiter 与同步 RestClient 的桥接）**

```java
public final class ResilienceHelper {
    private ResilienceHelper() {}

    /**
     * 将同步调用包装为 CompletableFuture，使 @TimeLimiter 可正常工作。
     * 使用当前线程池（Virtual Thread 环境下直接 supplyAsync）。
     */
    public static <T> CompletableFuture<T> timed(Supplier<T> supplier) {
        return CompletableFuture.supplyAsync(supplier);
    }
}
```

预期输出：`shop-common` 模块可正常编译，其他服务引入后 R4j AOP 顺序自动生效。

---

## Task 2: buyer-bff — 补全 Retry + Bulkhead + TimeLimiter 注解和配置

**Goal:** buyer-bff 的所有下游调用均有完整四层防护（现有 CircuitBreaker 保留，补充其余三层）。

**Files:**
- Modify: `buyer-bff/src/main/resources/application.yml`
- Modify: `buyer-bff/src/main/java/dev/meirong/shop/buyerbff/service/BuyerAggregationService.java`

- [ ] **Step 1: application.yml — 补充 Retry / Bulkhead / TimeLimiter 配置**

在现有 `resilience4j.circuitbreaker` 配置块下追加：

```yaml
resilience4j:
  retry:
    instances:
      searchService:
        max-attempts: 3
        wait-duration: 200ms
        exponential-backoff-multiplier: 2.0
        retry-exceptions:
          - org.springframework.web.client.ResourceAccessException
          - java.net.ConnectException
        ignore-exceptions:
          - dev.meirong.shop.common.error.BusinessException
      promotionService:
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
      # orderService / marketplaceService / loyaltyService / walletService 不配置 Retry（写操作）

  bulkhead:
    instances:
      orderService:
        max-concurrent-calls: 8
        max-wait-duration: 0ms
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
        max-wait-duration: 50ms
      profileService:
        max-concurrent-calls: 15
        max-wait-duration: 0ms
      walletService:
        max-concurrent-calls: 8
        max-wait-duration: 0ms

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

- [ ] **Step 2: BuyerAggregationService — 为查询类方法补加 @Retry + @Bulkhead + @TimeLimiter**

规则：
- 查询方法（GET/read）：`@TimeLimiter + @Bulkhead + @CircuitBreaker + @Retry`
- 写操作方法（POST/UPDATE）：`@TimeLimiter + @Bulkhead + @CircuitBreaker`（无 @Retry）

示例（查询）：
```java
@TimeLimiter(name = "promotionService")
@Bulkhead(name = "promotionService", type = Bulkhead.Type.SEMAPHORE)
@CircuitBreaker(name = "promotionService", fallbackMethod = "listPromotionsFallback")
@Retry(name = "promotionService")
public CompletableFuture<List<PromotionApi.OfferResponse>> listPromotions() {
    return ResilienceHelper.timed(() -> /* existing call */);
}
```

示例（写操作，无 @Retry）：
```java
@TimeLimiter(name = "marketplaceService")
@Bulkhead(name = "marketplaceService", type = Bulkhead.Type.SEMAPHORE)
@CircuitBreaker(name = "marketplaceService", fallbackMethod = "deductInventoryForCheckoutFallback")
public CompletableFuture<Void> deductInventoryForCheckout(String productId, int quantity) {
    return ResilienceHelper.timed(() -> { /* existing call */; return null; });
}
```

预期输出：`./mvnw test -pl buyer-bff` 通过，所有现有测试不因注解变更而失败。

---

## Task 3: seller-bff — 补全所有下游 CircuitBreaker + Retry + Bulkhead + TimeLimiter

**Goal:** seller-bff 从仅有 searchService 一个 CB 扩展到所有下游完整四层防护。

**Files:**
- Modify: `seller-bff/src/main/resources/application.yml`
- Modify: `seller-bff/src/main/java/dev/meirong/shop/sellerbff/service/SellerAggregationService.java`

- [ ] **Step 1: 检查 SellerAggregationService 中所有下游调用，列出方法清单**

查找调用了哪些服务：`order-service / marketplace-service / promotion-service / wallet-service / profile-service / search-service`

- [ ] **Step 2: application.yml — 添加完整 R4j 配置（参照 spec §4 配置矩阵 seller-bff 部分）**

- [ ] **Step 3: SellerAggregationService — 为每个下游调用方法补加对应注解**

与 buyer-bff 相同规则：写操作无 @Retry，查询可加 @Retry。

预期输出：`./mvnw test -pl seller-bff` 通过。

---

## Task 4: ArchUnit — 验证写操作不含 @Retry 注解（Phase 1 收尾）

**Goal:** CI 静态规则确保任何人不会误将 @Retry 加到写操作上。

**Files:**
- Create: `buyer-bff/src/test/java/dev/meirong/shop/buyerbff/arch/ResilienceArchTest.java`
- Create: `seller-bff/src/test/java/dev/meirong/shop/sellerbff/arch/ResilienceArchTest.java`

- [ ] **Step 1: 添加 ArchUnit 依赖到 buyer-bff 和 seller-bff 的 pom.xml**

```xml
<dependency>
    <groupId>com.tngtech.archunit</groupId>
    <artifactId>archunit-junit5</artifactId>
    <scope>test</scope>
</dependency>
```

- [ ] **Step 2: 编写 ResilienceArchTest**

```java
@AnalyzeClasses(packages = "dev.meirong.shop.buyerbff.service")
class ResilienceArchTest {

    @ArchTest
    static final ArchRule writeOperationsMustNotHaveRetry = methods()
        .that().areAnnotatedWith(CircuitBreaker.class)
        .and().haveNameMatching(".*(deduct|create|place|redeem|earn|pay|charge).*")
        .should().notBeAnnotatedWith(Retry.class)
        .because("写操作不应自动重试，防止幂等性破坏");
}
```

预期输出：`./mvnw test -pl buyer-bff,seller-bff` 通过，规则验证写操作无 @Retry。

---

## Phase 2：Domain Service 补偿持久化

---

## Task 5: promotion-service — compensation_task + CompensationTaskService + Scheduler

**Goal:** 优惠券核销从 best-effort 升级为持久化补偿任务，服务重启后自动恢复。

**Files:**
- Create: `promotion-service/src/main/resources/db/migration/V{next}__add_compensation_task.sql`
- Create: `promotion-service/src/main/java/dev/meirong/shop/promotion/compensation/CompensationTaskEntity.java`
- Create: `promotion-service/src/main/java/dev/meirong/shop/promotion/compensation/CompensationTaskRepository.java`
- Create: `promotion-service/src/main/java/dev/meirong/shop/promotion/compensation/CompensationTaskService.java`
- Create: `promotion-service/src/main/java/dev/meirong/shop/promotion/compensation/CompensationTaskScheduler.java`
- Modify: `promotion-service/src/main/java/dev/meirong/shop/promotion/service/CouponApplicationService.java`（或对应 Kafka Listener）

- [ ] **Step 1: Flyway 迁移 — 创建 compensation_task 表**

```sql
-- V{next}__add_compensation_task.sql
CREATE TABLE compensation_task (
    id              CHAR(26)     NOT NULL PRIMARY KEY COMMENT 'ULID',
    task_type       VARCHAR(64)  NOT NULL COMMENT 'COUPON_REDEEM',
    aggregate_id    VARCHAR(64)  NOT NULL COMMENT 'orderId',
    idempotency_key VARCHAR(128) NOT NULL,
    payload         TEXT         NOT NULL,
    status          VARCHAR(16)  NOT NULL DEFAULT 'PENDING',
    retry_count     INT          NOT NULL DEFAULT 0,
    max_retries     INT          NOT NULL DEFAULT 3,
    last_error      TEXT         NULL,
    scheduled_at    TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    created_at      TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at      TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6)
                                          ON UPDATE CURRENT_TIMESTAMP(6),
    UNIQUE KEY uk_idempotency (idempotency_key),
    INDEX idx_status_scheduled (status, scheduled_at),
    INDEX idx_aggregate (aggregate_id)
) COMMENT '优惠券核销补偿任务';
```

- [ ] **Step 2: 创建 CompensationTaskEntity（ULID id, 状态机字段）**

- [ ] **Step 3: 创建 CompensationTaskRepository**

```java
public interface CompensationTaskRepository extends JpaRepository<CompensationTaskEntity, String> {
    List<CompensationTaskEntity> findTop20ByStatusAndScheduledAtBeforeOrderByScheduledAtAsc(
        String status, Instant now);
    boolean existsByIdempotencyKey(String key);
}
```

- [ ] **Step 4: 创建 CompensationTaskService.executeOrSkip()**

```java
@Service
public class CompensationTaskService {
    @Transactional
    public boolean executeOrSkip(String idempotencyKey, String taskType,
                                  String aggregateId, String payload,
                                  Consumer<String> executor) {
        if (repository.existsByIdempotencyKey(idempotencyKey)) return false; // 幂等跳过
        CompensationTaskEntity task = new CompensationTaskEntity(
            UlidCreator.getUlid().toString(), taskType, aggregateId, idempotencyKey, payload);
        repository.save(task);
        try {
            executor.accept(payload);
            task.markDone();
        } catch (Exception e) {
            task.markFailed(e.getMessage());
        }
        return true;
    }
}
```

- [ ] **Step 5: 创建 CompensationTaskScheduler**

```java
@Component
public class CompensationTaskScheduler {
    @Scheduled(fixedDelayString = "${shop.compensation.retry-delay-ms:30000}")
    @Transactional
    public void retryPendingTasks() {
        List<CompensationTaskEntity> tasks = repository
            .findTop20ByStatusAndScheduledAtBeforeOrderByScheduledAtAsc("PENDING", Instant.now());
        for (var task : tasks) {
            try {
                task.markProcessing();
                executeTask(task);
                task.markDone();
            } catch (Exception e) {
                task.incrementRetry(e.getMessage()); // 指数退避更新 scheduled_at
            }
        }
    }
}
```

- [ ] **Step 6: 修改 Kafka Listener，将直接操作替换为 CompensationTaskService.executeOrSkip()**

预期输出：关闭 promotion-service 后投递的 order.completed 事件在服务重启后能被 Scheduler 自动处理。

---

## Task 6: marketplace-service — 库存扣减补偿持久化

**Goal:** 库存扣减从 best-effort 升级，task_type = INVENTORY_DEDUCT。

**Files:**（与 Task 5 结构相同）
- Create: `marketplace-service/src/main/resources/db/migration/V{next}__add_compensation_task.sql`
- Create: `marketplace-service/src/main/java/dev/meirong/shop/marketplace/compensation/`（同上四个类）
- Modify: 库存扣减的 Kafka Listener

- [ ] **Step 1-6:** 与 Task 5 步骤相同，`task_type = INVENTORY_DEDUCT`，`idempotency_key = INVENTORY_DEDUCT:{orderId}:{skuId}`

预期输出：库存扣减 Listener 在重复消费相同 orderId+skuId 时不会重复扣减。

---

## Task 7: loyalty-service — 积分补偿持久化

**Goal:** 积分增减从 best-effort 升级，支持 POINTS_EARN 和 POINTS_REFUND 两种任务类型。

**Files:**（与 Task 5 结构相同）
- Create: `loyalty-service/src/main/resources/db/migration/V{next}__add_compensation_task.sql`
- Create: `loyalty-service/src/main/java/dev/meirong/shop/loyalty/compensation/`（同上四个类）
- Modify: 积分 Kafka Listener

- [ ] **Step 1-6:** 与 Task 5 步骤相同，支持两种 `task_type = POINTS_EARN / POINTS_REFUND`

预期输出：积分退还在 loyalty-service 重启后自动补偿。

---

## Phase 3：幂等规范 + Resilience 监控

---

## Task 8: ArchUnit — Kafka Consumer 幂等保护规则

**Goal:** 确保所有 @KafkaListener 方法所在类都依赖 CompensationTaskService（或等价幂等检查）。

**Files:**
- Create: `promotion-service/src/test/java/.../arch/KafkaIdempotencyArchTest.java`
- Create: `marketplace-service/src/test/java/.../arch/KafkaIdempotencyArchTest.java`
- Create: `loyalty-service/src/test/java/.../arch/KafkaIdempotencyArchTest.java`

- [ ] **Step 1: 在三个服务的 test scope 添加 archunit-junit5 依赖**

- [ ] **Step 2: 编写 KafkaIdempotencyArchTest**

```java
@AnalyzeClasses(packages = "dev.meirong.shop.promotion")
class KafkaIdempotencyArchTest {

    @ArchTest
    static final ArchRule kafkaListenersMustUseCompensationService = classes()
        .that().containAnyMethodsThat().areAnnotatedWith(KafkaListener.class)
        .should().dependOnClassesThat().haveSimpleNameContaining("CompensationTaskService")
        .because("所有 Kafka consumer 必须通过 CompensationTaskService 保证幂等");
}
```

预期输出：CI 中 `./mvnw test` 对三个服务均通过幂等规则检查。

---

## Task 9: Grafana Dashboard — R4j 指标面板

**Goal:** 在现有 Grafana（待部署）中添加 Resilience4j 关键指标面板。

**Files:**
- Create: `docker/grafana/dashboards/resilience4j-dashboard.json`

- [ ] **Step 1: 在 Actuator 配置中暴露 R4j 指标**

```yaml
management:
  endpoints:
    web:
      exposure:
        include: health,prometheus,metrics
  metrics:
    tags:
      application: ${spring.application.name}
```

- [ ] **Step 2: 编写 Grafana Dashboard JSON，包含面板：**
  - `resilience4j_circuitbreaker_state`（每个实例状态：CLOSED/OPEN/HALF_OPEN）
  - `resilience4j_retry_calls_total`（按 kind=successful_without_retry/successful_with_retry/failed 分组）
  - `resilience4j_bulkhead_available_concurrent_calls`（舱壁剩余容量）
  - `resilience4j_timelimiter_calls_total`（按 kind=successful/timeout/failed 分组）

预期输出：导入 Grafana 后可看到所有服务的 R4j 状态总览。

---

## 验收标准

| 阶段 | 验收项 |
|------|--------|
| Phase 1 | buyer-bff 和 seller-bff 的每个 @CircuitBreaker 方法都有 @TimeLimiter + @Bulkhead；写操作无 @Retry |
| Phase 2 | 三个服务的 Kafka Listener 均通过 CompensationTaskService 写入补偿任务；Scheduler 能自动重试 |
| Phase 3 | ArchUnit 规则在 CI 中通过；Grafana 可看到 CB 状态和 Bulkhead 容量 |
