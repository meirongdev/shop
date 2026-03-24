# Shop Platform — Bloom Filter 幂等增强 设计文档

> 版本：1.1 | 日期：2026-03-23 | 状态：待批准

---

## 一、概述

### 1.1 目标

在现有 DB 幂等方案基础上，引入 **Redis Bloom Filter 快速路径**，减少高吞吐场景下的 DB 查询压力，同时保持幂等正确性不降级。

覆盖范围：
- **HTTP 命令接口**（wallet-service 等写操作的 `Idempotency-Key` 防重）
- **Kafka Consumer**（`compensation_task.uk_idempotency` 防重）

### 1.2 问题背景

现有幂等方案完全依赖 DB 查询：

- `wallet_idempotency_key` 表：每次 HTTP 请求先做主键查询
- `compensation_task.uk_idempotency` 唯一约束：每条 Kafka 消息先查一次 DB

在 Kafka 高吞吐场景（如 order.completed 广播给多个消费者）或支付重试风暴中，这些幂等 DB 查询会成为热点，影响整体吞吐量。

### 1.3 设计原则

| 编号 | 原则 | 说明 |
|------|------|------|
| P1 | **DB 永远是最终裁判** | Bloom Filter 只做快速短路，不替代 DB 幂等记录 |
| P2 | **降级安全** | Redis 不可用时自动退回纯 DB 路径，幂等正确性不受影响 |
| P3 | **业务成功后才写入 BF** | action 抛出异常时不写 BF 和 DB，防止失败被标记为已处理 |
| P4 | **BF 与 DB 可以短暂不一致** | BF.add 是 best-effort，DB 才是权威来源；BF 落后 DB 是安全的（下次走 DB 确认） |
| P5 | **统一封装，各服务透明使用** | `shop-common` 提供 `IdempotencyGuard`，消除重复防重代码 |

---

## 二、方案选型与 Trade-off

### 2.1 候选方案

#### 方案 A：Redis Bloom Filter 旁路加速（**选定**）

在 DB 查询前插入 Redis BF 快速路径：
- BF 未命中（`mightContain=false`）→ 确认未处理，直接执行业务
- BF 命中（`mightContain=true`）→ 可能重复，走 DB 确认

**优点：**
- 用极小固定内存覆盖海量 key（1 亿 key @ 0.1% 误判率 ≈ 172MB）
- 降级路径安全：Redis 宕机 → 全走 DB，正确性不变
- 与现有 `compensation_task` + `wallet_idempotency_key` DB 兜底完全兼容
- `shop-common` 统一封装，所有服务受益

**缺点 / 限制：**
- Redisson `RBloomFilter` 不支持逐 key 过期，历史 key 永久占位（需预估容量上限）
- BF 本身不存结果，命中时仍需 DB 查询（对真正重复请求无法省去这次 DB 查询）
- 需要添加 `redisson-spring-boot-starter` 依赖（项目已有 Redis，成本低）

#### 方案 B：Redis BF + JVM 本地 Cache 双层

在方案 A 基础上，JVM 内加 `ConcurrentHashMap` 做极短 TTL 的本地热点 key 缓存。

**放弃理由：**
- 多实例间本地 cache 不一致，幂等兜底仍靠 DB，正确性无法提升
- 与方案 A 相比仅节省一次 Redis 网络 RTT（≈1ms），收益与复杂度不成比例

#### 方案 C：Redisson RMapCache 替代 BF

用 `RMapCache`（带 TTL）直接存 idempotency key，命中即返回，不命中走 DB。

**放弃理由：**
- 内存随 key 数量线性增长，无法像 BF 以固定内存覆盖海量 key
- 若 Redis 宕机，整条链路回退纯 DB，降级行为与方案 A 相同，但内存效率远低于 BF

### 2.2 误判率策略决策

**决策：全服务统一 STRICT 模式**（BF 误判时仍走 DB 兜底，绝不丢业务）

曾考虑按服务分级（金融类 STRICT，非金融类容忍误判直接跳过），最终统一为 STRICT，原因：
- 降低认知负担：只有一种行为模式，无需区分服务类型
- 误判时多一次 DB 查询的代价可接受，而丢业务的代价不可接受
- 即便是 notification/search 等非金融场景，误判导致消息漏处理也会影响用户体验

---

## 三、架构

### 3.1 核心流程

```
idempotencyKey
      │
      ▼
BF.mightContain(key)?
      │
   false ──────────────────────────► 直接执行 action()
      │                                     │
     true                     事务内：DB 写幂等记录（提交）
      │                                     │
      ▼                        事务后：try { BF.add(key) }
   DB 查询确认                           catch(任何异常) { WARN 日志，不向上抛 }
      │                                  // BF.add 异常永不传播到调用方
  DB 存在 ──► 调用 fallback()        （BF 命中为真正重复）
  DB 不存在 ──► 执行 action()        （BF 误判，安全兜底）
                    │
     事务内：DB 写幂等记录（提交）
     事务后：try { BF.add(key) }
             catch(任何异常) { WARN 日志，不向上抛 }
      │
  并发写冲突（DataIntegrityViolationException）
      │
  捕获后调用 fallback()              （DB 中已有记录，由并发请求写入）
      │
  guard 内部监控/日志代码异常          ← 事务已提交，BF 已处理
      │
  catch(任何异常) { WARN 日志，不向上抛；返回 action/fallback 已得结果 }
```

**BF 与 DB 一致性说明：**
- DB 写幂等记录在**事务内**；BF.add 在**事务提交后**执行，二者不耦合
- 若 BF.add 失败，DB 中已有记录，下次同 key 请求会走 DB 确认路径，正确性不受影响
- BF 落后 DB 是安全的；DB 落后 BF 不存在（BF 在 DB 写入后才 add）

### 3.2 组件清单

| 组件 | 位置 | 职责 |
|-----|------|------|
| `IdempotencyGuard` | `shop-common` | 统一入口：BF 快速路径 + DB 兜底逻辑 |
| `BloomFilterProperties` | `shop-common` | Redis BF 配置（容量、误判率、key 命名空间） |
| `IdempotencyRepository` | 各服务 | 现有 DB 幂等表的 Spring Data 接口（不变） |
| Redis BF 实例 | 每服务独立命名空间 | `shop:{service-name}:idempotency:bf` |

### 3.3 降级策略

Redis 不可用时，`IdempotencyGuard` 捕获 `RedisException`，自动跳过 BF，直接走 DB 路径。降级事件上报 `shop.idempotency.bf.fallback` Micrometer 计数器。

`shop.idempotency.bloom-filter.enabled=false` 可在任意时刻通过 ConfigMap 热更新关闭 BF，强制走纯 DB 路径，用于灰度发布或紧急降级。

### 3.4 优雅停机 & 健康检查

- `IdempotencyGuard` 通过 Spring Boot Actuator 上报 Redis BF 连通性，纳入 `/actuator/health`
- 优雅停机期间，若 Redis 响应超过 100ms（可配置），自动退化为纯 DB 路径，避免 in-flight 请求因等待 BF 而超时
- 已在处理中的请求继续执行至完成，不强制中断

---

## 四、数据模型 & 配置规范

### 4.1 服务幂等需求分类

| 服务类型 | 代表服务 | 是否需要 IdempotencyGuard |
|---------|---------|--------------------------|
| 支付 / 交易 | wallet, order, promotion, marketplace, loyalty | **必须** |
| 通知 / 集成 | notification, webhook | **必须** |
| 搜索 / 投影 | search, activity | **必须** |
| BFF | buyer-bff, seller-bff | **按需**（含写操作时必须） |
| 门户（SSR） | buyer-portal, seller-portal | **不需要**（无写操作） |
| 认证 | auth-server | **按需**（OAuth 回调幂等见 OAuth 安全规范） |
| 网关 | api-gateway | **不需要**（流量代理，无业务写操作） |

### 4.2 Redis Bloom Filter 参数

| 服务 | Redis Key | 预估容量 | 误判率 | 预估内存 |
|-----|-----------|---------|-------|--------|
| wallet-service | `shop:wallet:idempotency:bf` | 10M | 0.1% | ~17MB |
| order-service | `shop:order:idempotency:bf` | 50M | 0.1% | ~86MB |
| promotion-service | `shop:promotion:idempotency:bf` | 100M | 0.1% | ~172MB |
| marketplace-service | `shop:marketplace:idempotency:bf` | 50M | 0.1% | ~86MB |
| loyalty-service | `shop:loyalty:idempotency:bf` | 50M | 0.1% | ~86MB |
| notification-service | `shop:notification:idempotency:bf` | 20M | 0.1% | ~34MB |
| search-service | `shop:search:idempotency:bf` | 10M | 0.1% | ~17MB |

**容量管理：** BF 无逐 key TTL，历史 key 永久占位。当实际插入量接近 `expectedInsertions` 时误判率上升。需通过 `BF.INFO <key>` 监控实际插入量，在达到 80% 容量前提前扩容（重建 BF）。

### 4.3 `application.yml` 配置规范

`shop-common` 提供默认值，各服务按需覆盖：

```yaml
shop:
  idempotency:
    bloom-filter:
      enabled: true                               # false → 纯 DB 路径（灰度/降级用，支持热更新）
      redis-key: "shop:{service}:idempotency:bf"  # 各服务必须覆盖
      expected-insertions: 50_000_000
      false-probability: 0.001
      redis-timeout-ms: 100                       # 超过此阈值视为 Redis 降级
```

### 4.4 依赖要求

`shop-common/pom.xml` 需新增：

```xml
<dependency>
    <groupId>org.redisson</groupId>
    <artifactId>redisson-spring-boot-starter</artifactId>
    <!-- 版本由 shop-parent BOM 管理 -->
</dependency>
```

各服务若已直接使用 spring-boot-starter-data-redis，需确认与 Redisson 无冲突（Redisson 提供 Spring Data Redis 兼容实现，通常可共存）。

### 4.5 现有 DB 幂等表（不变）

| 表 | 所在服务 | 用途 |
|---|---------|------|
| `wallet_idempotency_key` | wallet-service | HTTP 接口防重 |
| `compensation_task`（`uk_idempotency`） | promotion/marketplace/loyalty | Kafka consumer 防重 |

新增服务需 HTTP 接口幂等时，统一建 `{service}_idempotency_key` 表，结构与 wallet-service 保持一致。

---

## 五、API 设计 & 编程规范

### 5.1 `IdempotencyGuard` 接口

```java
/**
 * 幂等执行保护。
 * 内部实现：Redis BF 快速路径 + DB 兜底（STRICT 模式，全服务统一）。
 * Redis 不可用或响应超时时自动降级为纯 DB 路径。
 */
public interface IdempotencyGuard {

    /**
     * 执行一次幂等保护的业务操作。
     *
     * <p>仅在确认 key 未被处理时执行 action；否则调用 fallback 返回已有结果。
     *
     * @param key      幂等 key（全局唯一，格式见 5.3 节）；长度不超过 128 字符
     * @param action   实际业务逻辑；仅在确认未处理时调用；抛出异常时不写幂等记录
     * @param fallback DB 中已存在幂等记录时调用，用于重建已有结果；
     *                 允许返回 null（调用方负责处理 null 返回值）；
     *                 若结果无法重建，建议抛出 IllegalStateException 而非返回 null；
     *                 若 fallback 抛出异常，异常透传给调用方，不写幂等记录
     * @return action 的返回值（首次执行）或 fallback 的返回值（重复请求）；可为 null
     * @throws RuntimeException action 或 fallback 抛出的异常直接透传
     */
    <T> T executeOnce(String key, Supplier<T> action, Supplier<T> fallback);
}
```

**并发安全保证：**
两个并发请求同时通过 BF miss 并尝试写 DB 时，先写入的成功，后写入的触发唯一键冲突（`DataIntegrityViolationException`），`IdempotencyGuard` 捕获后自动调用 `fallback()` 返回已有结果。调用方无需额外处理并发场景。

### 5.2 调用规范

**Kafka Consumer：**

```java
@KafkaListener(topics = "order.completed")
@Transactional
public void onOrderCompleted(ConsumerRecord<String, String> record) {
    // key 必须来自消息 payload 的稳定业务字段，不能用 partition/offset
    OrderCompletedEvent event = deserialize(record);
    String key = "COUPON_REDEEM:" + event.getOrderId() + ":" + event.getCouponInstanceId();

    idempotencyGuard.executeOnce(
        key,
        () -> couponService.redeem(event.getOrderId(), event.getCouponInstanceId()),
        () -> couponService.findResultByIdempotencyKey(key)
    );
}
```

**HTTP 命令接口：**

```java
@PostMapping("/deposit")
public TransactionResponse deposit(
        @RequestHeader("Idempotency-Key") String key,
        @RequestBody DepositRequest req) {
    return idempotencyGuard.executeOnce(
        key,
        () -> walletService.deposit(req),
        () -> walletService.findByIdempotencyKey(key)
    );
}
```

### 5.3 幂等 Key 命名规范

| 场景 | 格式 | 示例 |
|-----|------|------|
| Kafka consumer | `{TASK_TYPE}:{aggregateId}:{subId}` | `COUPON_REDEEM:order-123:coupon-456` |
| HTTP 命令接口 | 客户端传入 UUID v4，服务端透传 | `550e8400-e29b-41d4-a716-446655440000` |
| 补偿任务重试 | `COMPENSATE:{taskType}:{taskId}` | `COMPENSATE:POINTS_EARN:01ARZ3NDektsV4rrffq69G5FAF` |

**规则：**
1. Kafka consumer 的幂等 key **必须**来自消息 payload 中的稳定业务字段（aggregate_id、sub-entity id 等）；禁止使用 `{topic}:{partition}:{offset}`（DLQ 回放场景下 offset 会复用）
2. 禁止使用时间戳作为幂等 key 的唯一组成部分
3. key 长度不超过 128 字符
4. key 在同一服务内必须全局唯一，跨服务的相同业务操作各自独立命名

### 5.4 ArchUnit 规则（新增）

在现有 ArchUnit 测试基础上追加：

```
规则 3：所有 @KafkaListener 方法所在类必须注入 IdempotencyGuard
         例外：已通过其他等价幂等机制保护的类可通过 @IdempotencyExempt 注解豁免，
               并须在注解中说明替代方案（如基于业务状态机的天然幂等）
规则 4：所有调用 IdempotencyGuard.executeOnce 的方法必须标注 @Transactional
```

**现有 Kafka Listener 迁移策略：**

已有自定义幂等逻辑的 listener（如基于业务字段唯一约束的 `WelcomeCouponListener`）有两种选项：
- **重构**：将现有幂等检查逻辑替换为 `IdempotencyGuard.executeOnce`，推荐
- **豁免**：在类上添加 `@IdempotencyExempt(reason = "业务状态检查天然幂等")`，绕过 ArchUnit 规则 3

豁免需在 code review 阶段由服务负责人评估并记录理由，禁止批量豁免。

---

## 六、错误处理

| 场景 | 行为 |
|-----|------|
| Redis 不可用（网络/宕机） | 捕获 `RedisException`，跳过 BF，直接走 DB；上报 `shop.idempotency.bf.fallback` |
| Redis 响应超时（> `redis-timeout-ms`） | 同上，降级为纯 DB 路径 |
| BF.add 失败（事务提交后） | 记录 WARN 日志，不阻断；DB 幂等记录已写入，下次同 key 走 DB 确认，正确性不受影响 |
| DB 幂等写冲突（唯一键，并发场景） | 捕获 `DataIntegrityViolationException`，调用 `fallback()` 返回已有结果 |
| `action` 抛出业务异常 | 异常透传，**不写入** BF 和 DB 幂等记录，事务回滚 |
| `action` 抛出非预期异常 | 同上，不写幂等记录，事务回滚 |
| `fallback` 抛出异常 | 异常透传给调用方；不重试 |
| `fallback` 返回 null | 合法，透传 null 给调用方；调用方负责处理（建议不能重建时抛 IllegalStateException） |
| guard 内部监控/日志代码异常（DB 已提交后） | 内部 catch 捕获，WARN 日志，不向上抛；调用方正常收到 action/fallback 结果 |
| `IdempotencyGuard` 初始化时 Redis 连接池耗尽 | 捕获 `RedisException`，降级为纯 DB 路径；上报 bf.fallback 指标 |

**核心原则：**
1. 只有 `action` 成功完成且事务提交后，才写 DB 幂等记录；DB 提交后才 BF.add
2. BF.add 是 best-effort，失败不阻断，不回滚 DB
3. 并发冲突由 `IdempotencyGuard` 内部处理，调用方透明

---

## 七、可观测性

### 7.1 Micrometer 指标

| 指标名 | 类型 | Tag | 说明 |
|--------|------|-----|------|
| `shop.idempotency.bf.hit` | Counter | `service`, `result=[duplicate\|false_positive]` | BF 命中次数及真/误判分布 |
| `shop.idempotency.bf.miss` | Counter | `service` | BF 未命中，直接执行 |
| `shop.idempotency.bf.fallback` | Counter | `service` | Redis 不可用或超时，降级为纯 DB |

### 7.2 误判率监控

```
# Grafana 公式（两个 counter 均为 rate）
false_positive_rate =
    rate(shop_idempotency_bf_hit{result="false_positive"}[5m])
  / (rate(shop_idempotency_bf_hit{result="duplicate"}[5m])
   + rate(shop_idempotency_bf_hit{result="false_positive"}[5m]))
```

**告警阈值：**

| 阈值 | 含义 | 动作 |
|-----|------|------|
| FPR > 0.5% | 实际误判率达到设计值 5 倍，BF 可能接近容量上限 | 检查 `BF.INFO` 插入量，计划扩容 |
| FPR > 1% | BF 严重过载 | 立即重建 BF（服务停止写入后重建，或滚动重建） |

**BF 容量监控：**
通过定期执行 `BF.INFO shop:{service}:idempotency:bf` 获取 `Number of items inserted`，当超过 `expectedInsertions` 的 80% 时触发扩容告警。

---

## 八、测试规范

### 8.1 单元测试场景（每个使用 `IdempotencyGuard` 的服务必须覆盖）

| 测试场景 | 验证点 |
|---------|-------|
| 首次请求 | BF miss → action 执行 → DB 写幂等记录 → BF.add |
| 重复请求（BF hit + DB 确认存在） | action 不执行，返回 fallback 结果 |
| BF 误判（BF hit，DB 无记录） | action 执行，DB 写幂等记录，BF.add |
| Redis 不可用 | 纯 DB 路径，结果正确，bf.fallback 指标上报 |
| action 抛出异常 | BF 和 DB 均不写入，事务回滚，异常透传 |
| fallback 抛出异常 | 异常透传，事务回滚，不写幂等记录 |
| 并发重复请求（两个请求同时 BF miss） | 先到者成功写 DB，后到者捕获唯一键冲突，调用 fallback，不重复执行 action |
| BF.add 失败（Redis 在事务提交后宕机） | DB 幂等记录已写入；下次同 key 走 DB 路径，幂等正确 |
| BF 容量饱和（插入量 > expectedInsertions） | 误判率上升，DB 查询增加；bf.hit{result=false_positive} 计数器上升 |
| DB 提交后 guard 内部代码抛异常（如 Micrometer 注册失败） | action 结果已返回；guard 内部 catch 捕获，WARN 日志，不向上抛 |

### 8.2 集成测试

`shop-common` 提供 `IdempotencyGuardIntegrationTest` 抽象基类，使用 Testcontainers（Redis + MySQL），各服务继承并提供具体的 `IdempotencyRepository` 实现即可。

---

## 九、迁移路径

现有服务的幂等代码**不需要立即迁移**，可按以下顺序渐进接入：

1. `shop-common` 实现并发布 `IdempotencyGuard`（含 `BloomFilterProperties` AutoConfiguration、`redisson-spring-boot-starter` 依赖）
2. `wallet-service` 率先接入（HTTP 接口场景验证）
3. `promotion-service` 接入（Kafka consumer 场景验证）
4. 其余服务按优先级逐步替换旧防重代码；有自定义幂等逻辑的 listener 评估重构或豁免

**迁移等价性：** 旧代码直接 DB 查询，新代码 BF 快路径 + DB 兜底，外部可见行为不变。
