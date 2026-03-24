# 积分到期系统设计文档

> 版本：1.2 | 日期：2026-03-22
> 状态：Draft（未来 rolling-window / FIFO 方案） | 所属服务：loyalty-service

> 当前代码说明：
> - `loyalty-service` 已有 `PointsExpiryScheduler`
> - 当前实现使用 `loyalty_transaction.expire_at` + 每日 2 AM 批处理过期
> - 本文描述的是**未来更复杂的 rolling-window / FIFO 设计草案**，不是当前代码的精确实现说明

---

## 一、行业现状与最佳实践分析

### 1.1 主流电商积分到期策略对比

| 平台 | 策略 | 优点 | 缺点 |
|------|------|------|------|
| 京东 | 年度清零（次年3月31日清除上一自然年积分） | 实现简单 | 年底焦虑感强，用户体验差 |
| 天猫 | 年度清零（次年1月31日） | 同上 | 同上 |
| Shopify积分插件 | 滚动窗口（每笔积分独立 + FIFO消耗） | 精确公平，减少浪费 | 实现复杂 |
| Amazon积分 | 永不过期（Prime会员）/ 3年滚动（普通） | 用户友好 | 负债成本高 |
| Starbucks Rewards | 活跃度延期（购买后重置倒计时） | 驱动复购 | 逻辑较复杂 |

### 1.2 2026 最佳实践：滚动窗口 + FIFO 消耗 + 活跃延期

**推荐方案组合：**

1. **滚动窗口（Rolling Window）**：每笔积分在赚取时设定独立到期日（默认获得日 + 12 个月），而非统一年底清零。
2. **FIFO 消耗（First-In First-Out）**：用户兑换积分时，优先消耗最早到期的积分，自动延长较新积分的有效期。
3. **活跃度延期（Activity Extension）**：用户在积分到期前 30 天内完成一笔购买，该批积分自动延期 12 个月（Phase 3 实现）。
4. **分级预警通知**：到期前 30 天、7 天分别推送提醒，结合"即将到期积分展示"在 App 首页触达。

**与现有设计的对齐：**
- `loyalty_transaction.expire_at` 字段已存在，继续作为流水级别的展示字段
- 新增 `points_ledger` 表作为到期管理的权威数据源
- 现有 `loyalty_account.balance` 字段保持与 ledger 剩余积分总和的实时一致性

---

## 二、数据库设计

### 2.1 积分台账表（points_ledger）

积分台账是 FIFO 消耗与到期管理的核心，每笔赚取事件对应一条台账记录。

```sql
CREATE TABLE points_ledger (
    id                CHAR(26)     NOT NULL PRIMARY KEY,  -- ULID
    player_id         VARCHAR(64)  NOT NULL,
    transaction_id    VARCHAR(36)  NOT NULL,              -- 关联 loyalty_transaction.id
                                                          -- 注：loyalty_transaction.id 当前为 VARCHAR(36)，
                                                          -- 若未来迁移为 ULID CHAR(26) 则同步调整此列
    original_amount   BIGINT       NOT NULL,              -- 原始积分数
    remaining_amount  BIGINT       NOT NULL,              -- 剩余可用积分（FIFO 消耗后递减）
    expire_at         DATE         NOT NULL,              -- 到期日
    status            VARCHAR(16)  NOT NULL DEFAULT 'ACTIVE',
                                                          -- ACTIVE / EXPIRED / CONSUMED
    notified_30d      TINYINT(1)   NOT NULL DEFAULT 0,    -- 已发 30 天预警
    notified_7d       TINYINT(1)   NOT NULL DEFAULT 0,    -- 已发 7 天预警
    extended_count    TINYINT      NOT NULL DEFAULT 0,    -- 活跃延期次数（上限 2 次）
    created_at        TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    INDEX idx_player_expire    (player_id, expire_at),
    INDEX idx_expire_status    (expire_at, status),       -- 批量到期扫描
    INDEX idx_player_fifo      (player_id, status, expire_at ASC)  -- FIFO 消耗查询
);
```

**字段说明：**
- `remaining_amount`：初始等于 `original_amount`，FIFO 消耗时递减至 0（此时 `status=CONSUMED`）
- `status`：`ACTIVE`（有效）、`EXPIRED`（已到期被批量任务清零）、`CONSUMED`（积分已全部被消耗）
- `notified_30d` / `notified_7d`：预警通知幂等标志，避免重复发送
- `extended_count`：活跃延期次数，最多延期 2 次（即最长有效期 = 初始 12 个月 + 24 个月 = 36 个月）

### 2.2 到期任务日志表（expiry_job_log）

```sql
CREATE TABLE expiry_job_log (
    id               CHAR(26)     NOT NULL PRIMARY KEY,   -- ULID
    job_date         DATE         NOT NULL,               -- 作业日期
    job_type         VARCHAR(32)  NOT NULL,               -- EXPIRE / NOTIFY_30D / NOTIFY_7D
    status           VARCHAR(16)  NOT NULL,               -- RUNNING / COMPLETED / FAILED
    processed_records INT         NOT NULL DEFAULT 0,     -- 处理 ledger 记录数
    total_points     BIGINT       NOT NULL DEFAULT 0,     -- 涉及积分总量
    affected_users   INT          NOT NULL DEFAULT 0,     -- 受影响用户数
    started_at       TIMESTAMP(6) NOT NULL,
    completed_at     TIMESTAMP(6),
    error_message    VARCHAR(512),
    UNIQUE KEY uq_date_type (job_date, job_type)          -- 幂等：同日同类型只执行一次
);
```

**说明**：每种 job_type（EXPIRE / NOTIFY_30D / NOTIFY_7D）在同一天只执行一条记录，由 `UNIQUE KEY uq_date_type` 约束保证幂等，应用层先查后插同样使用 `(job_date, job_type)` 组合键。

### 2.3 现有表变更

```sql
-- loyalty_account 追加 email 字段，用于事件发布时直接携带收件地址，
-- 避免 loyalty-service 在批量任务中运行时依赖 profile-service
ALTER TABLE loyalty_account
  ADD COLUMN email VARCHAR(255) AFTER player_id;
```

`loyalty_transaction` 无需修改，`expire_at DATE` 字段继续使用：
- EARN 类型流水：写入 `expire_at`（与 `points_ledger.expire_at` 保持一致）
- EXPIRE 类型流水：`expire_at = NULL`（到期事件本身不再过期）

---

## 三、FIFO 消耗机制

### 3.1 积分扣减流程（兑换 / 消耗）

```java
@Transactional
public void deductPoints(String playerId, long amount, String source, String referenceId) {
    // 1. 账户行锁（所有积分变更必须经过账户行锁，保证同一用户操作串行化）
    LoyaltyAccount account = accountRepository.findByPlayerIdForUpdate(playerId);
    if (account.getBalance() < amount) {
        throw new BusinessException(INSUFFICIENT_POINTS,
            "Need %d, available %d".formatted(amount, account.getBalance()));
    }

    // 2. FIFO：按 expire_at ASC 获取 ACTIVE 台账并加行锁，逐条消耗
    //    使用 SELECT FOR UPDATE 确保与并发的到期任务互斥
    List<PointsLedger> ledgers = ledgerRepository
        .findActiveByPlayerIdOrderByExpireAtAscForUpdate(playerId);

    long remaining = amount;
    List<PointsLedger> toUpdate = new ArrayList<>();
    for (PointsLedger ledger : ledgers) {
        if (remaining <= 0) break;
        long consume = Math.min(ledger.getRemainingAmount(), remaining);
        ledger.consume(consume);   // remaining_amount -= consume; if 0 → status=CONSUMED
        toUpdate.add(ledger);
        remaining -= consume;
    }
    ledgerRepository.saveAll(toUpdate);

    // 3. 写 loyalty_transaction（DEDUCT）
    long balanceAfter = account.getBalance() - amount;
    loyaltyTransactionRepository.save(new LoyaltyTransaction(
        playerId, "DEDUCT", source, -amount, balanceAfter, referenceId
    ));

    // 4. 更新账户余额
    account.setBalance(balanceAfter);
    account.setUsedPoints(account.getUsedPoints() + amount);
    accountRepository.save(account);
}
```

**并发安全说明**：`findByPlayerIdForUpdate`（账户行锁）是同一用户所有积分写操作的入口，保证同一 `player_id` 的 deductPoints 与 expireSingleLedger 不会并发执行。台账的 `FOR UPDATE` 提供额外的行级互斥，防止极端情况下的 over-consume。

### 3.2 积分赚取时创建台账

```java
@Transactional
public void earnPoints(String playerId, long amount, String source, String referenceId) {
    // 幂等：referenceId 已处理则跳过
    if (loyaltyTransactionRepository.existsByReferenceId(referenceId)) return;

    LoyaltyAccount account = accountRepository.findByPlayerIdForUpdate(playerId);

    LocalDate expireAt = calculateExpireAt(source);

    // 写流水
    long balanceAfter = account.getBalance() + amount;
    LoyaltyTransaction tx = loyaltyTransactionRepository.save(new LoyaltyTransaction(
        playerId, "EARN", source, amount, balanceAfter, referenceId, expireAt
    ));

    // 创建台账记录
    ledgerRepository.save(PointsLedger.create(
        playerId, tx.getId(), amount, expireAt
    ));

    // 更新账户
    account.setBalance(balanceAfter);
    account.setTotalPoints(account.getTotalPoints() + amount);
    account.setTierPoints(account.getTierPoints() + amount);
    accountRepository.save(account);

    // 发 Kafka 事件（EventEnvelope 包装）
    eventPublisher.publish("loyalty.points.earned.v1",
        EventEnvelope.of("loyalty-service", "POINTS_EARNED",
            new PointsEarnedEventData(playerId, account.getEmail(), amount, source, balanceAfter, expireAt)));
}

private LocalDate calculateExpireAt(String source) {
    // REGISTER / ONBOARDING_TASK 积分有效期 24 个月（新用户激励）
    int months = Set.of("REGISTER", "ONBOARDING_TASK").contains(source) ? 24 : 12;
    return LocalDate.now().plusMonths(months);
}
```

**说明**：`account.getEmail()` 从 `loyalty_account.email` 字段读取，该字段在用户注册时由 `user.registered.v1` 事件写入（见 §八 3）。

---

## 四、批量到期任务

### 4.1 任务调度

三个独立的定时任务，凌晨低峰期执行：

| 任务 | Cron | 说明 |
|------|------|------|
| `PointsExpiryJob` | `0 0 2 * * *` | 每日凌晨 2 点：清零到期积分 |
| `ExpiryNotify30DJob` | `0 0 9 * * *` | 每日上午 9 点：扫描 30 天预警 |
| `ExpiryNotify7DJob`  | `0 30 9 * * *`| 每日上午 9:30：扫描 7 天预警 |

所有任务使用 `@SchedulerLock`（ShedLock）防止多实例重复执行。

### 4.2 到期清零任务（PointsExpiryJob）

```java
@Scheduled(cron = "0 0 2 * * *")
@SchedulerLock(name = "PointsExpiryJob", lockAtMostFor = "PT2H")
public void runExpiryJob() {
    LocalDate today = LocalDate.now();

    // 幂等：今天 EXPIRE 任务已执行则跳过
    if (jobLogRepository.existsByJobDateAndJobType(today, "EXPIRE")) return;

    ExpiryJobLog log = jobLogRepository.save(ExpiryJobLog.start(today, "EXPIRE"));
    int processedRecords = 0;
    long totalPoints = 0;
    Set<String> affectedUsers = new HashSet<>();

    try {
        // 分页处理，避免大事务（每页独立事务）
        // 注意：expireSingleLedger 必须在独立的 Spring Bean（ExpiryJobProcessor）上声明 @Transactional，
        // 不能作为同一 Bean 的 private 方法——Spring AOP 代理不拦截 self-invocation。
        int page = 0;
        int pageSize = 500;
        List<PointsLedger> batch;

        do {
            batch = ledgerRepository.findExpiredActive(today, PageRequest.of(page, pageSize));
            // expire_at <= today AND status = 'ACTIVE' AND remaining_amount > 0

            for (PointsLedger ledger : batch) {
                long amount = ledger.getRemainingAmount();
                expiryJobProcessor.expireSingleLedger(ledger);  // 跨 Bean 调用，@Transactional 生效
                processedRecords++;
                totalPoints += amount;
                affectedUsers.add(ledger.getPlayerId());
            }
            page++;
        } while (batch.size() == pageSize);

        log.complete(processedRecords, totalPoints, affectedUsers.size());
        jobLogRepository.save(log);

    } catch (Exception e) {
        log.fail(e.getMessage());
        jobLogRepository.save(log);
        throw e;
    }
}
```

`expireSingleLedger` 须定义在独立的 `@Service` Bean（`ExpiryJobProcessor`）中：

```java
// ExpiryJobProcessor.java（独立 @Service，确保 @Transactional 通过 AOP 代理生效）
@Service
public class ExpiryJobProcessor {

    @Transactional
    public void expireSingleLedger(PointsLedger ledger) {
        long expiredAmount = ledger.getRemainingAmount();
        String playerId = ledger.getPlayerId();

        // 1. 账户行锁（与 deductPoints 互斥，保证余额正确）
        LoyaltyAccount account = accountRepository.findByPlayerIdForUpdate(playerId);

        // 2. 台账标记 EXPIRED（互斥由上方账户行锁保证；saveAndFlush 确保写入在事务提交前可见）
        ledger.expire();
        ledgerRepository.saveAndFlush(ledger);

        // 3. 更新账户余额
        long balanceAfter = account.getBalance() - expiredAmount;
        account.setBalance(balanceAfter);
        accountRepository.save(account);

        // 4. 写 EXPIRE 流水
        loyaltyTransactionRepository.save(new LoyaltyTransaction(
            playerId, "EXPIRE", "EXPIRY_JOB", -expiredAmount, balanceAfter, ledger.getId()
        ));

        // 5. 发 Kafka 事件（EventEnvelope 包装）
        eventPublisher.publish("loyalty.points.expired.v1",
            EventEnvelope.of("loyalty-service", "POINTS_EXPIRED",
                new PointsExpiredEventData(
                    playerId, account.getEmail(), account.getUsername(),
                    expiredAmount, ledger.getExpireAt(), balanceAfter
                )));
    }
}
```

### 4.3 预警通知任务

```java
@Scheduled(cron = "0 0 9 * * *")
@SchedulerLock(name = "ExpiryNotify30DJob", lockAtMostFor = "PT1H")
public void runNotify30DJob() {
    runNotifyJob(30, "NOTIFY_30D");
}

@Scheduled(cron = "0 30 9 * * *")
@SchedulerLock(name = "ExpiryNotify7DJob", lockAtMostFor = "PT1H")
public void runNotify7DJob() {
    runNotifyJob(7, "NOTIFY_7D");
}

private void runNotifyJob(int daysAhead, String jobType) {
    LocalDate today = LocalDate.now();
    if (jobLogRepository.existsByJobDateAndJobType(today, jobType)) return;

    ExpiryJobLog log = jobLogRepository.save(ExpiryJobLog.start(today, jobType));
    LocalDate targetDate = today.plusDays(daysAhead);

    try {
        // 按用户聚合：同一用户当天只发一次通知（汇总多批积分）
        // SELECT player_id, SUM(remaining_amount) AS total_expiring
        // FROM points_ledger
        // WHERE expire_at = :targetDate AND status = 'ACTIVE'
        //   AND (notified_30d = 0 OR notified_7d = 0)  -- 按 daysAhead 选字段
        // GROUP BY player_id
        List<ExpiryNotifySummary> summaries = ledgerRepository.sumExpiringByUser(targetDate, daysAhead);

        for (ExpiryNotifySummary summary : summaries) {
            LoyaltyAccount account = accountRepository.findById(summary.playerId()).orElseThrow();

            eventPublisher.publish("loyalty.points.expiring.v1",
                EventEnvelope.of("loyalty-service", daysAhead == 30 ? "POINTS_EXPIRING_30D" : "POINTS_EXPIRING_7D",
                    new PointsExpiringEventData(
                        summary.playerId(), account.getEmail(), account.getUsername(),
                        summary.totalExpiring(), targetDate, daysAhead
                    )));

            // 标记已通知，防重
            ledgerRepository.markNotified(summary.playerId(), targetDate, daysAhead);
        }

        log.complete(summaries.size(), 0, summaries.size());
        jobLogRepository.save(log);

    } catch (Exception e) {
        log.fail(e.getMessage());
        jobLogRepository.save(log);
        throw e;
    }
}
```

---

## 五、活跃度延期（Phase 3）

用户在积分到期前 30 天内发生消费行为，该批积分自动延期 12 个月（每批最多延期 2 次）。

```java
// 在现有 order.events.v1 消费逻辑中追加（LoyaltyEventListener.java）
@KafkaListener(topics = "order.events.v1")
public void onOrderCompleted(EventEnvelope<OrderEventData> event) {
    if (!"ORDER_COMPLETED".equals(event.type())) return;

    // 现有：发放购物积分
    // ...

    // Phase 3 新增：活跃度延期
    String playerId = event.data().buyerId();
    LocalDate today = LocalDate.now();
    LocalDate threshold = today.plusDays(30);

    List<PointsLedger> nearExpiry = ledgerRepository
        .findNearExpiryEligible(playerId, today, threshold, 2);
        // expire_at BETWEEN today AND threshold AND status='ACTIVE' AND extended_count < 2

    for (PointsLedger ledger : nearExpiry) {
        ledger.extend(12);  // expire_at += 12 months, extended_count++
    }
    ledgerRepository.saveAll(nearExpiry);
}
```

---

## 六、Kafka 事件

### 6.1 发布事件

所有事件均使用 `EventEnvelope<T>` 包装（项目统一规范），`EventEnvelope.of(source, type, data)` 自动生成 ULID eventId。

| Topic | 事件类型（`event.type()`）| 触发时机 | 消费方 |
|-------|------------------------|---------|--------|
| `loyalty.points.expiring.v1` | `POINTS_EXPIRING_30D` | 到期前 30 天预警任务 | notification-service |
| `loyalty.points.expiring.v1` | `POINTS_EXPIRING_7D` | 到期前 7 天预警任务 | notification-service |
| `loyalty.points.expired.v1` | `POINTS_EXPIRED` | 到期清零任务逐条处理 | notification-service |

### 6.2 notification-service 集成

#### 新增 Kafka Listener

遵循现有 listener 模式（`String payload` + `ObjectMapper`，无 `Acknowledgment`）：

```java
// LoyaltyEventListener.java（notification-service 内新建）
@Component
public class LoyaltyEventListener {

    private final NotificationApplicationService notificationService;
    private final ObjectMapper objectMapper;

    @KafkaListener(
        topics = {"${shop.notification.loyalty-expiring-topic:loyalty.points.expiring.v1}",
                  "${shop.notification.loyalty-expired-topic:loyalty.points.expired.v1}"},
        groupId = "${spring.application.name}"
    )
    public void onLoyaltyEvent(String payload) throws Exception {
        EventEnvelope<Map<String, Object>> event = objectMapper.readValue(payload,
            new TypeReference<>() {});

        String recipientId    = (String) event.data().get("playerId");
        String recipientEmail = (String) event.data().get("email");
        Map<String, Object> variables = buildVariables(event.type(), event.data());

        notificationService.processEvent(
            event.eventId(),
            event.type(),         // POINTS_EXPIRING_30D / POINTS_EXPIRING_7D / POINTS_EXPIRED
            recipientId,
            recipientEmail,
            variables
        );
    }

    private Map<String, Object> buildVariables(String type, Map<String, Object> data) {
        return switch (type) {
            case "POINTS_EXPIRING_30D", "POINTS_EXPIRING_7D" -> Map.of(
                "username",       data.get("username"),
                "expiringPoints", data.get("expiringPoints"),
                "expireDate",     data.get("expireDate"),
                "daysLeft",       data.get("daysAhead")
            );
            case "POINTS_EXPIRED" -> Map.of(
                "username",       data.get("username"),
                "expiredPoints",  data.get("expiredPoints"),
                "expiredDate",    data.get("expireDate"),
                "currentBalance", data.get("balanceAfter")
            );
            default -> Map.of();
        };
    }
}
```

#### NotificationRouter 路由规则补充

`ROUTES` 是 `Map.of(...)` 不可变 Map，必须**在源码的 `Map.of(...)` 字面量中**添加新条目（当前 7 条 + 新增 3 条 = 10 条，恰好达到 `Map.of` 重载上限；若日后超出 10 条需改为 `Map.ofEntries(...)`）：

```java
// NotificationRouter.java — 修改 ROUTES 字面量，在 Map.of() 中追加 3 条
private static final Map<String, NotificationConfig> ROUTES = Map.of(
    // ... 现有 7 条保持不变 ...
    "POINTS_EXPIRING_30D", new NotificationConfig("points-expiring",        "EMAIL", "Your Points Are Expiring Soon"),
    "POINTS_EXPIRING_7D",  new NotificationConfig("points-expiring-urgent", "EMAIL", "Points Expiring in 7 Days!"),
    "POINTS_EXPIRED",      new NotificationConfig("points-expired",         "EMAIL", "Points Have Expired")
);
```

#### 新增邮件模板

```
resources/templates/email/       (notification-service)
├── points-expiring.html          # 30 天预警
├── points-expiring-urgent.html   # 7 天预警
└── points-expired.html           # 已过期通知
```

| 模板 | 变量 |
|------|------|
| `points-expiring` | `username`, `expiringPoints`, `expireDate`, `daysLeft` |
| `points-expiring-urgent` | 同上 |
| `points-expired` | `username`, `expiredPoints`, `expiredDate`, `currentBalance` |

---

## 七、事件 DTO（shop-contracts）

所有 DTO 是 `EventEnvelope<T>` 中 `data()` 的 payload 类型，不是顶层 Kafka 消息。

```java
// 积分赚取（现有，补充 expireAt 字段）
public record PointsEarnedEventData(
    String    playerId,
    String    email,        // 收件邮箱（来自 loyalty_account.email）
    long      amount,
    String    source,
    long      balanceAfter,
    LocalDate expireAt      // 新增
) {}

// 积分即将到期
public record PointsExpiringEventData(
    String    playerId,
    String    email,          // 来自 loyalty_account.email
    String    username,       // 来自 loyalty_account.username
    long      expiringPoints,
    LocalDate expireDate,
    int       daysAhead       // 30 or 7
) {}

// 积分已过期
public record PointsExpiredEventData(
    String    playerId,
    String    email,          // 来自 loyalty_account.email
    String    username,       // 来自 loyalty_account.username
    long      expiredPoints,
    LocalDate expireDate,
    long      balanceAfter
) {}
```

**说明**：`email` 和 `username` 从 `loyalty_account` 表读取，loyalty_account 在 `user.registered.v1` 消费时写入（见 §八）。这一设计避免了批量任务在运行时对 profile-service 的同步依赖。

---

## 八、上游服务补充

### 8.1 loyalty-service：初始化账户时持久化 email/username

在 `LoyaltyOnboardingListener.onUserRegistered()` 中，已有账户初始化逻辑，追加 email/username 写入：

```java
@KafkaListener(topics = "user.registered.v1")
public void onUserRegistered(EventEnvelope<UserRegisteredEventData> event) {
    UserRegisteredEventData data = event.data();

    // 创建账户时写入 email 和 username（已有 getOrCreate 逻辑中补充）
    LoyaltyAccount account = accountRepository.findById(data.playerId())
        .orElse(new LoyaltyAccount(data.playerId()));
    account.setEmail(data.email());
    account.setUsername(data.username());
    accountRepository.save(account);

    // 发放注册积分 & 初始化新人任务（现有逻辑不变）
    accountService.earnPoints(data.playerId(), 100, "REGISTER", event.eventId());
    // ...
}
```

---

## 九、API 补充

```
# 买家接口（新增）
GET /loyalty/v1/expiring-points          # 即将到期积分汇总

Response:
{
  "expiring_soon": [
    { "expireDate": "2026-04-21", "amount": 350 },
    { "expireDate": "2026-05-15", "amount": 120 }
  ],
  "total_expiring_30d": 350,
  "total_expiring_90d": 470
}
```

---

## 十、数据库迁移方案

### 10.1 V2 Migration 文件

新增 Flyway migration `V2__points_expiry.sql`：

```sql
-- 1. 创建积分台账表
CREATE TABLE points_ledger ( ... );   -- 见 §2.1

-- 2. 创建任务日志表
CREATE TABLE expiry_job_log ( ... );  -- 见 §2.2

-- 3. loyalty_account 追加 email 和 username 字段
ALTER TABLE loyalty_account
  ADD COLUMN email    VARCHAR(255) AFTER player_id,
  ADD COLUMN username VARCHAR(64)  AFTER email;

-- 4. 索引：loyalty_transaction 补充 expire_at 索引（历史数据回填查询用）
ALTER TABLE loyalty_transaction
  ADD INDEX idx_expire_at (expire_at);
```

### 10.2 历史数据回填（V2 Java Migration）

`points_ledger` 的历史回填 **必须使用 Java Migration**（`BaseJavaMigration`），因为 ULID 生成无法在纯 SQL 中实现。

```java
// db/migration/V2_1__BackfillPointsLedger.java
public class V2_1__BackfillPointsLedger extends BaseJavaMigration {
    @Override
    public void migrate(Context context) throws Exception {
        // 查询所有 EARN 类型且 expire_at 不为 NULL 的流水
        // 为每条记录创建对应的 points_ledger 行（ULID 生成）
        // remaining_amount 初始 = original_amount（保守处理，见 §10.3）
        String select = "SELECT id, player_id, amount, expire_at, created_at " +
                        "FROM loyalty_transaction WHERE type = 'EARN' AND expire_at IS NOT NULL";

        String insert = "INSERT INTO points_ledger " +
                        "(id, player_id, transaction_id, original_amount, remaining_amount, expire_at, status) " +
                        "VALUES (?, ?, ?, ?, ?, ?, ?)";

        try (var stmt = context.getConnection().prepareStatement(select);
             var ins  = context.getConnection().prepareStatement(insert);
             var rs   = stmt.executeQuery()) {

            while (rs.next()) {
                String status = rs.getDate("expire_at").toLocalDate()
                    .isBefore(LocalDate.now()) ? "EXPIRED" : "ACTIVE";
                ins.setString(1, UlidCreator.getUlid().toString());
                ins.setString(2, rs.getString("player_id"));
                ins.setString(3, rs.getString("id"));         // transaction_id
                ins.setLong(4,   rs.getLong("amount"));       // original_amount
                ins.setLong(5,   rs.getLong("amount"));       // remaining_amount（保守值）
                ins.setDate(6,   rs.getDate("expire_at"));
                ins.setString(7, status);
                ins.addBatch();
            }
            ins.executeBatch();
        }
    }
}
```

### 10.3 迁移后一致性校验

回填完成后，执行以下 SQL 检查台账余额与账户余额是否一致；不一致的用户需人工修正 `remaining_amount`：

```sql
SELECT
    la.player_id,
    la.balance                                           AS account_balance,
    COALESCE(SUM(pl.remaining_amount), 0)               AS ledger_balance,
    la.balance - COALESCE(SUM(pl.remaining_amount), 0)  AS diff
FROM loyalty_account la
LEFT JOIN points_ledger pl ON pl.player_id = la.player_id AND pl.status = 'ACTIVE'
GROUP BY la.player_id, la.balance
HAVING diff != 0;
```

差值来源：用户历史上消耗过部分积分，但对应台账 `remaining_amount` 被保守地初始化为 `original_amount`。修正方式：以 `loyalty_account.balance` 为准，按 FIFO 顺序从最旧的台账逐步扣减 diff，直至差值归零。此修正逻辑封装为一次性修正脚本（`V2_2__ReconcileLedgerBalance.java`）。

---

## 十一、非功能设计

### 11.1 并发安全

- 所有积分写操作（earn/deduct/expire）必须先获取 `loyalty_account` 行锁（`SELECT FOR UPDATE`）
- `expireSingleLedger` 对 `points_ledger` 行也加锁，与 FIFO `deductPoints` 互斥
- 批量任务使用 `@SchedulerLock`（ShedLock），防止多实例重复执行
- 幂等：`expiry_job_log.(job_date, job_type)` 唯一约束，任务重跑安全

### 11.2 性能设计

- 批量到期任务分页处理（500 条/页），每页独立事务，避免大事务锁表
- `points_ledger` 索引：`(expire_at, status)` 覆盖批量扫描；`(player_id, status, expire_at ASC)` 覆盖 FIFO 查询
- 预警通知按用户聚合后发 Kafka，notification-service 异步处理，不阻塞批量任务

### 11.3 可观测性

- 每次批量任务写入 `expiry_job_log`，可通过管理接口查询执行历史
- 关键指标：`expiry.job.duration`, `expiry.job.records`, `expiry.job.points`, `expiry.notify.sent`

---

## 十二、扩展路径

| Phase | 扩展 | 说明 |
|-------|------|------|
| Phase 3 | 活跃度延期 | 消费 `order.events.v1`，为即将到期积分自动延期（§五已设计） |
| Phase 3 | App 到期提醒横幅 | buyer-bff `/loyalty/v1/expiring-points` 接口已设计，前端展示即可 |
| Phase 4 | 积分负债报表 | 统计全平台未到期积分总量，用于财务成本预估 |
| Phase 4 | 动态到期规则 | 运营可配置不同来源积分的有效期（如购物积分 18 个月，签到积分 6 个月）|
