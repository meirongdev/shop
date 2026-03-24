# Redisson 适配与首批落地设计

> 日期：2026-03-23  
> 状态：Design Approved for Implementation  
> 范围：`marketplace-service`、`order-service`、`subscription-service`、`promotion-service`

---

## 1. 背景

当前仓库已经具备 Redis 基础设施：

- `docker-compose.yml` 已提供 `redis:7.4`
- `k8s/infra/base.yaml` 已提供 Redis 部署
- `api-gateway`、`auth-server`、`buyer-bff`、`activity-service`、`promotion-service`、`wallet-service` 已有 Redis 配置或 Redis 依赖
- `shop-common` 已有基于 Redisson Bloom Filter 的幂等组件

说明问题已经不是“要不要引入 Redis”，而是“哪些关键业务路径应该从单机/单库假设升级为跨实例协同”。

---

## 2. 当前项目里适合使用 Redisson 的地方

### 2.1 已经在用 Redis / Redisson 的场景

当前项目已经有多类 Redis 使用方式：

- `api-gateway`：限流
- `auth-server`：OTP、冷却时间、锁定窗口
- `buyer-bff`：游客购物车
- `activity-service`：防作弊、红包 Lua 原子分发
- `wallet-service` / `promotion-service`：幂等 Bloom Filter

这些场景说明仓库已经接受 Redis 作为“高频、短时、跨实例共享状态”的基础设施。

### 2.2 适合继续引入 Redisson 的候选场景

#### A. `marketplace-service` 库存变更

`MarketplaceApplicationService#deductInventory` / `restoreInventory` 当前都属于同一商品库存的写路径。  
在并发下单、超时取消、人工恢复库存等场景同时发生时，多个请求可能同时读取同一商品库存，导致超卖或库存回写丢失。

适合使用：

- `RLock`
- 锁粒度：`shop:marketplace:inventory:mutate:{productId}`

#### B. `promotion-service` 优惠券模板发放

`CouponTemplateService#issueToBuyer` 当前先查用户已领取数量和模板总发放量，再写入实例。  
这属于典型 check-then-act，并发下可能突破 `perUserLimit` 和 `totalLimit`。

适合使用：

- `RLock`
- 锁粒度：`shop:promotion:coupon-template:{templateId}:issue`

> 后续如需要极限吞吐，可演进到 `RAtomicLong` / `RSemaphore`；本次优先用锁保证正确性。

#### C. `order-service` 定时任务

`OrderScheduler` 的自动取消、自动完成属于定时批处理。  
在多实例部署下，所有实例都会同时执行，容易造成重复状态迁移、重复写 Outbox、重复副作用。

适合使用：

- `RLock`
- 锁粒度：
  - `shop:order:scheduler:cancel-expired`
  - `shop:order:scheduler:auto-complete`

#### D. `subscription-service` 续费批处理

`SubscriptionRenewalService#processRenewals` 为周期性批处理。  
在多实例部署下，如果没有全局协调，存在重复续费、重复订单日志写入的问题。

适合使用：

- `RLock`
- 锁粒度：`shop:subscription:scheduler:renewal`

#### E. 其它中长期适配点

以下场景也适合 Redisson，但不作为本次首批落地范围：

- `activity-service` 的调度协调
- `wallet-service` 的高并发幂等串行化
- `notification-service` / `webhook-service` 的重试任务协调
- `search-service` 的跨实例共享热点统计

---

## 3. 本次首批落地范围

本次代码实现选择四个高价值点：

1. `marketplace-service`：库存扣减 / 恢复加分布式锁
2. `promotion-service`：优惠券发放加分布式锁
3. `order-service`：两个定时任务加分布式锁
4. `subscription-service`：续费批处理加分布式锁

选择理由：

- 都是“重复执行会产生业务错误”的关键路径
- 改动集中，容易验证
- 能直接复用现有 Redis 基础设施
- 对现有接口契约影响小

---

## 4. 设计原则

### 4.1 正确性优先于吞吐

本次优先解决并发正确性问题，而不是把 Redis 当作主存储。

### 4.2 只保护关键区

锁只覆盖真正需要串行化的逻辑：

- 扣减库存
- 发券校验 + 创建实例
- 批处理入口

避免把整个服务长时间串行化。

### 4.3 锁失败不能静默吞掉

- 对用户请求路径（库存扣减、发券），锁获取失败要明确报错
- 对定时任务路径，锁获取失败要记录日志并跳过本轮，由其他实例执行

### 4.4 锁命名统一前缀

统一使用：

```text
shop:{service}:{domain}:{action}[:id]
```

例如：

- `shop:marketplace:inventory:mutate:{productId}`
- `shop:promotion:coupon-template:issue:{templateId}`
- `shop:order:scheduler:cancel-expired`

---

## 5. 实现方式

### 5.1 服务依赖

为实际需要直接使用 `RedissonClient` 的服务引入：

```xml
<dependency>
    <groupId>org.redisson</groupId>
    <artifactId>redisson-spring-boot-starter</artifactId>
</dependency>
```

### 5.2 配置方式

沿用现有 Spring Boot Redis 配置模型：

```yaml
spring:
  data:
    redis:
      host: ${SERVICE_REDIS_HOST:redis}
      port: ${SERVICE_REDIS_PORT:6379}
```

不额外引入独立 Redisson YAML，先采用 starter 自动配置。

### 5.3 锁策略

#### 用户请求路径

- 使用 `tryLock(waitTime, leaseTime, TimeUnit)`
- 获取失败直接抛业务异常
- 保证 `finally` 解锁

#### 定时任务路径

- 使用较短等待时间
- 获取失败记录 `debug/info` 日志并跳过本轮
- 让其他实例成为实际执行者

---

## 6. 风险与边界

### 6.1 不是最终一致性银弹

Redisson 解决的是“多实例并发进入关键区”的问题，不能替代：

- 数据库唯一约束
- Outbox Pattern
- 幂等设计

因此现有数据库约束和事务仍然保留。

### 6.2 锁租期需要覆盖关键事务

租期过短会导致业务未完成就自动释放。  
因此用户请求路径和定时任务路径都要给出保守但有限的 lease time。

### 6.3 首批不做统一锁框架封装

本次直接在业务服务中注入 `RedissonClient`。  
如后续锁场景继续增多，再统一抽象 `DistributedLockExecutor`。

---

## 7. 预期收益

- 避免库存超卖
- 避免优惠券超发
- 避免多实例定时任务重复执行
- 为后续红包、秒杀、批处理协调提供一致的 Redisson 使用范式

---

## 8. 后续文档更新要求

代码完成后，同步更新：

- `docs/services/marketplace-service.md`
- `docs/services/order-service.md`
- `docs/services/promotion-service.md`
- `docs-site/docs/services/core.md`
- `docs-site/docs/services/growth.md`
- `docs-site/docs/getting-started/local-deployment.md`
- 必要时更新 `README.md`

重点说明：

- 为什么这里用 Redisson
- Redis 在本项目中的职责边界
- 新增环境变量与运行要求
- 多实例下的行为变化
