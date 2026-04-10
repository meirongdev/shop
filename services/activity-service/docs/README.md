---
title: 互动活动中心设计文档
---

# activity-service — 互动活动中心设计文档

> 版本：2.5 | 日期：2026-03-22 | 端口：:8089

## 0. 当前集成状态（2026-03-22）

- buyer-portal 已提供 `/buyer/activities` 活动广场与 `/buyer/activities/{gameId}` 详情页。
- 当前前端已覆盖 `INSTANT_LOTTERY`、`RED_ENVELOPE`、`COLLECT_CARD`、`VIRTUAL_FARM` 四类现有玩法，并直接消费 Gateway 暴露的 activity API。
- 门户会根据 `animationHint` 渲染抽奖停位、红包金额、抽卡结果和农场进度；虚拟农场通过 `payload.action=HARVEST` 触发收获动作。
- 活动浏览允许 guest session；真正参与仍要求已登录买家。

---

## 一、模块归属与设计原则

### 1.1 为什么独立为 activity-service

| 维度 | promotion-service | loyalty-service | **activity-service** |
|------|-------------------|-----------------|----------------------|
| 核心职责 | 折扣规则计算 | 积分账务 | **游戏状态机 + 并发控制** |
| 并发特征 | 低，无竞争 | 中，串行写 | **极高，万人同抢** |
| 随机性 | 无 | 无 | **核心逻辑** |
| 弹性扩容 | 不需要 | 不需要 | **大促独立扩容 50×** |
| 奖励发放 | 是输出 | 是输出 | **调用其他服务，自身不记账** |

奖励发放是输出而非归属依据：activity-service 只做决策，实际发放委托给 loyalty-service（积分）、promotion-service（优惠券）、wallet-service（余额）。

### 1.2 两大设计原则

**可扩展（Extensibility）**：新增游戏类型 = 实现 `GamePlugin` 接口 + 注册 Bean，不修改引擎核心代码。

**可扩容（Scalability）**：游戏热状态全部在 Redis，DB 只做持久化；activity-service 实例无状态，可从 2 Pod 水平扩展到 50 Pod。

---

## 二、整体内部架构

```
┌─────────────────────────────────────────────────────────────────────┐
│                        activity-service                              │
│                                                                     │
│  ┌──────────────────────────────────────────────────────────────┐  │
│  │                    HTTP 入口层                                │  │
│  │  ActivityController   AdminGameController                     │  │
│  │  （参与 / 查询）        （运营管理）                           │  │
│  └─────────────────────────┬────────────────────────────────────┘  │
│                             │                                       │
│  ┌──────────────────────────▼────────────────────────────────────┐ │
│  │                    GameEngine（核心调度器）                    │ │
│  │  validateEntry() → getPlugin() → plugin.participate()         │ │
│  │  → recordParticipation() → dispatchReward()                   │ │
│  └──────────┬──────────────────────────────────────┬─────────────┘ │
│             │                                      │               │
│  ┌──────────▼────────────┐            ┌───────────▼─────────────┐ │
│  │   GamePluginRegistry   │            │    横切关注点            │ │
│  │  Map<type, GamePlugin> │            │  AntiCheatGuard（防作弊）│ │
│  │                        │            │  RateLimiter（限速）     │ │
│  │  InstantLotteryPlugin  │            │  IdempotencyGuard（幂等）│ │
│  │  RedEnvelopePlugin     │            │  CircuitBreaker（熔断）  │ │
│  │  CollectCardPlugin     │            └─────────────────────────┘ │
│  │  VirtualFarmPlugin     │                                        │
│  │  QuizPlugin            │            ┌─────────────────────────┐ │
│  │  SlashPricePlugin      │            │   RewardDispatcher      │ │
│  │  GroupBuyPlugin        │            │   loyalty-service       │ │
│  │  [NewPlugin...]        │            │   promotion-service     │ │
│  └────────────────────────┘            │   wallet-service        │ │
│                                        │   + 补偿 Job            │ │
│  ┌─────────────────────────────────────▼───────────────────────┐ │
│  │                     基础设施层                               │ │
│  │  Redis（热状态）   MySQL（持久化）   Kafka（异步事件）        │ │
│  └──────────────────────────────────────────────────────────────┘ │
└─────────────────────────────────────────────────────────────────────┘
```

---

## 三、可扩展引擎：GamePlugin SPI

### 3.1 核心接口

```java
public interface GamePlugin {

    GameType supportedType();

    /**
     * 活动上线或调度激活时初始化热状态。
     * 当前实现通过插件自身注入的 Redis 客户端完成，不把 Redis 句柄透传到接口签名中。
     */
    default void initialize(ActivityGame game) {}

    /**
     * 处理一次参与请求。
     */
    ParticipateResult participate(ParticipateContext ctx);

    /**
     * 活动结束时做对账与清理。
     */
    default void settle(ActivityGame game) {}

    default Optional<String> extensionTablePrefix() { return Optional.empty(); }
}
```

### 3.2 参与上下文与结果

```java
/** 参与请求上下文，由 GameEngine 组装后传入 Plugin */
public record ParticipateContext(
    String   gameId,
    GameType gameType,
    String   buyerId,
    String   sessionId,
    String   gameConfig,   // activity_game.config 原始 JSON
    String   payload,      // 玩家请求体原始 JSON
    Instant  requestTime
) {}

/** 参与结果，GameEngine 据此写 DB 和触发奖励发放 */
public record ParticipateResult(
    boolean      win,
    String       prizeId,        // null 表示未中奖
    String       prizeName,
    PrizeType    prizeType,
    BigDecimal   prizeValue,
    JsonNode     animationHint,  // 前端动效参数（转盘停哪格、砸哪个蛋等）
    String       message         // 展示给玩家的文案
) {
    public static ParticipateResult miss(String message) {
        return new ParticipateResult(false, null, null, null, null, null, message);
    }
}
```

### 3.3 插件注册中心

```java
/** Spring 自动收集所有 GamePlugin Bean，按 type 路由 */
@Component
public class GamePluginRegistry {

    private final Map<GameType, GamePlugin> plugins;

    public GamePluginRegistry(List<GamePlugin> allPlugins) {
        this.plugins = allPlugins.stream()
            .collect(Collectors.toMap(GamePlugin::supportedType, p -> p));
    }

    public GamePlugin get(GameType type) {
        return Optional.ofNullable(plugins.get(type))
            .orElseThrow(() -> new UnsupportedGameTypeException(type));
    }

    public Set<GameType> registeredTypes() { return plugins.keySet(); }
}
```

### 3.4 GameEngine 核心调度

```java
@Service
@RequiredArgsConstructor
public class GameEngine {

    private final GamePluginRegistry       registry;
    private final AntiCheatGuard           antiCheat;
    private final IdempotencyGuard         idempotency;
    private final ActivityParticipationRepo participationRepo;
    private final ActivityOutboxRepo        outboxRepo;
    private final RewardDispatcher         dispatcher;

    @Transactional
    public ParticipateResult participate(ParticipateRequest req) {

        ActivityGame game = loadAndValidateGame(req.gameId());  // 状态 & 时间窗口校验

        // 1. 横切：防作弊 & 限速
        antiCheat.check(req, game);   // 超限抛 TooManyRequestsException

        // 2. 横切：幂等
        return idempotency.executeOnce(req.idempotencyKey(), () -> {

            // 3. 路由到对应 Plugin
            GamePlugin plugin = registry.get(game.getType());
            ParticipateContext ctx = buildContext(req, game);
            ParticipateResult result = plugin.participate(ctx);

            // 4. 持久化参与记录（MySQL，同一事务）
            ActivityParticipation record = saveParticipation(req, game, result);

            // 5. 写 Outbox 事件（同一事务，保证原子性）
            if (result.win()) {
                outboxRepo.save(buildPrizeWonEvent(record, result));
            }
            outboxRepo.save(buildParticipatedEvent(record));

            // 6. 异步发放奖励（Kafka → RewardDispatcher，不阻塞响应）
            //    即使下游暂时不可用，补偿 Job 会保证最终发放

            return result;
        });
    }
}
```

---

## 四、已实现的游戏插件

### 4.1 GameType 枚举

```java
public enum GameType {
    INSTANT_LOTTERY,   // 即时抽奖：砸金蛋 / 大转盘 / 刮刮乐 / 九宫格
    RED_ENVELOPE,      // 抢红包
    COLLECT_CARD,      // 集卡 / 集碎片
    VIRTUAL_FARM,      // 虚拟养成（农场 / 宠物 / 小树）
    QUIZ,              // 答题竞猜
    SLASH_PRICE,       // 砍价
    GROUP_BUY,         // 拼团
    // 预留扩展槽（实现 GamePlugin 即可加入）
}
```

### 4.2 InstantLotteryPlugin（即时抽奖系列）

用同一个 Plugin 承载砸金蛋、大转盘、刮刮乐、九宫格——它们的核心算法相同（加权随机），差异仅在 `animation_hint` 字段。

```java
@Component
public class InstantLotteryPlugin implements GamePlugin {

    @Override
    public GameType supportedType() { return GameType.INSTANT_LOTTERY; }

    @Override
    public void initialize(ActivityGame game, RedisTemplate<String, String> redis) {
        // 预加载奖品库存到 Redis（原子 DECR 防超发）
        List<ActivityRewardPrize> prizes = prizeRepo.findByGameId(game.getId());
        prizes.stream()
            .filter(p -> p.getTotalStock() > 0)
            .forEach(p -> redis.opsForValue()
                .set("prize:stock:" + p.getId(), String.valueOf(p.getTotalStock())));
    }

    @Override
    public ParticipateResult participate(ParticipateContext ctx) {
        List<ActivityRewardPrize> prizes = prizeRepo.findByGameId(ctx.gameId());

        // 加权随机 + Redis 原子扣减（Lua Script）
        ActivityRewardPrize prize = drawWithAtomicStock(prizes, ctx.redis());

        // 根据子类型（从 config 读取）生成 animationHint
        String subType = ctx.gameConfig().path("sub_type").asText("GOLDEN_EGG");
        JsonNode hint = buildAnimationHint(subType, prizes.indexOf(prize));

        return new ParticipateResult(
            true, prize.getId(), prize.getName(), prize.getType(),
            prize.getValue(), hint, "恭喜获得 " + prize.getName()
        );
    }

    private ActivityRewardPrize drawWithAtomicStock(
            List<ActivityRewardPrize> prizes, RedisTemplate<String, String> redis) {

        // 1. 加权随机确定候选奖品（按概率）
        ActivityRewardPrize candidate = weightedRandom(prizes);

        // 2. 若有库存限制，Redis 原子 DECR
        if (candidate.getTotalStock() > 0) {
            Long remaining = redis.execute(decrIfPositiveLua,
                List.of("prize:stock:" + candidate.getId()));
            if (remaining < 0) {
                // 库存耗尽，降级到下一个有库存的奖品或 NOTHING
                candidate = fallback(prizes, candidate);
            }
        }
        return candidate;
    }
}
```

**config JSON（砸金蛋 vs 大转盘 通过 sub_type 区分）：**

```json
// 砸金蛋
{ "sub_type": "GOLDEN_EGG", "egg_count": 9, "per_user_daily": 1 }

// 大转盘
{ "sub_type": "LUCKY_WHEEL", "slot_count": 8, "spin_rounds": 5 }

// 刮刮乐
{ "sub_type": "SCRATCH_CARD", "grid": "3x3", "reveal_threshold": 5,
  "entry_cost_points": 20 }

// 九宫格
{ "sub_type": "NINE_GRID", "grid_count": 9 }
```

**animationHint 示例（后端决定结果，前端仅播放动画）：**

```json
// 大转盘：停在第 3 格，转 5 圈
{ "target_index": 3, "rounds": 5 }

// 砸金蛋：砸第 2 个蛋
{ "target_egg": 2 }

// 九宫格：走 24 步停在第 2 格
{ "target_grid": 2, "steps": 24 }
```

---

### 4.3 RedEnvelopePlugin（抢红包）

**当前实现状态（2026-03-22）**：已落地并完成模块测试 + Kind 冒烟验证。

**实现要点**：

- `spring-boot-starter-data-redis` 已接入 `activity-service`
- 使用 `ACTIVITY_REDIS_HOST` / `ACTIVITY_REDIS_PORT` 连接 Redis
- 初始化阶段把预分配金额写入 `List`
- 领取阶段通过 Lua 原子完成“防重复领取 + 弹出红包 + 记录已领金额”
- 领取记录写入 Redis `Hash`，活动结束时与 `activity_participation` 的中奖记录做对账
- buyer 参与接口现在要求 `ROLE_BUYER`；admin 管理接口要求 `ROLE_ADMIN`

```java
@Component
public class RedEnvelopePlugin implements GamePlugin {

    private final StringRedisTemplate redisTemplate;
    private final ActivityParticipationRepository participationRepository;

    @Override
    public void initialize(ActivityGame game) {
        EnvelopeConfig config = parseConfig(game.getConfig());
        List<String> packetValues = generatePackets(config.totalAmount(), config.packetCount());
        redisTemplate.delete(List.of(packetsKey(game.getId()), claimsKey(game.getId())));
        redisTemplate.opsForList().rightPushAll(packetsKey(game.getId()), packetValues);
    }

    @Override
    public ParticipateResult participate(ParticipateContext ctx) {
        List<Object> result = redisTemplate.execute(grabPacketLua,
            List.of(packetsKey(ctx.gameId()), claimsKey(ctx.gameId())),
            ctx.buyerId());

        int code = toInt(result.get(0));
        return switch (code) {
            case -1 -> ParticipateResult.miss("You have already claimed this red envelope");
            case -2 -> ParticipateResult.miss("All red envelopes have been claimed");
            default -> {
                BigDecimal amount = toBigDecimal(result.get(1));
                yield new ParticipateResult(true, null, "Red Envelope",
                    PrizeType.POINTS, amount, "{\"amount\":\"" + amount + "\"}",
                    "You claimed " + amount + " points");
            }
        };
    }

    @Override
    public void settle(ActivityGame game) {
        long redisClaimed = redisTemplate.opsForHash().size(claimsKey(game.getId()));
        long persistedWins = participationRepository.countWinningParticipationsByGameId(game.getId());
        if (redisClaimed != persistedWins) {
            log.warn("Red envelope reconcile mismatch: game={}, redisClaimed={}, persistedWins={}",
                game.getId(), redisClaimed, persistedWins);
        }
        redisTemplate.delete(List.of(packetsKey(game.getId()), claimsKey(game.getId())));
    }
}
```

**Kind 验证结果**：

- 非 `ROLE_ADMIN` 创建活动返回 `403`
- `ROLE_ADMIN` 可创建并激活 `RED_ENVELOPE` 活动
- seller 参与活动返回 `403`
- buyer 首次参与中奖，重复参与返回“already claimed”
- 红包抢空后其他 buyer 返回“have been claimed”
- `my-history` 可看到中奖快照

---

### 4.4 CollectCardPlugin（集卡 / 集碎片，当前实现）

```java
@Component
public class CollectCardPlugin implements GamePlugin {

    @Override
    public GameType supportedType() { return GameType.COLLECT_CARD; }

    @Override
    public void initialize(ActivityGame game) {
        List<ActivityCollectCardDefinition> definitions = parseDefinitions(game.getConfig());
        definitionRepository.deleteByGameId(game.getId());
        definitionRepository.saveAll(definitions);
    }

    @Override
    public ParticipateResult participate(ParticipateContext ctx) {
        List<ActivityCollectCardDefinition> definitions = definitionRepository.findByGameIdOrderByCardNameAsc(ctx.gameId());
        ActivityCollectCardDefinition drawn = weightedRandom(definitions);

        long uniqueBefore = playerCardRepository.countDistinctCardIdByGameIdAndPlayerId(ctx.gameId(), ctx.buyerId());
        boolean duplicate = playerCardRepository.countByGameIdAndPlayerIdAndCardId(
            ctx.gameId(), ctx.buyerId(), drawn.getId()) > 0;

        playerCardRepository.save(new ActivityPlayerCard(
            UUID.randomUUID().toString(), ctx.gameId(), ctx.buyerId(), drawn.getId(), "DROP"));

        long uniqueAfter = duplicate ? uniqueBefore : uniqueBefore + 1;
        boolean fullSet = uniqueAfter >= definitions.size();

        return new ParticipateResult(
            true,
            drawn.getId(),
            drawn.getCardName(),
            PrizeType.CARD,
            null,
            buildAnimationHint(drawn, duplicate, fullSet, uniqueAfter, definitions.size()),
            fullSet && !duplicate
                ? "Full set completed with card: " + drawn.getCardName()
                : duplicate
                    ? "Collected duplicate card: " + drawn.getCardName()
                    : "Collected new card: " + drawn.getCardName() + " (" + uniqueAfter + "/" + definitions.size() + ")"
        );
    }

    @Override
    public Optional<String> extensionTablePrefix() {
        return Optional.of("collect_card");
    }
}
```

**当前实现边界：**

- 管理端在 `game.config` 中提供 `cards` 数组，元素字段为 `id? / name / rarity? / probability`
- 若未显式提供 `id`，服务会用 `gameId + cardName` 生成稳定 cardId
- 每次抽卡都会写入 `activity_player_card`，集齐判定基于 **去重后的 card_id 数量**
- 当前使用通用 `POST /activity/v1/games/{id}/participate` 入口；`/cards`、`/gift`、`/redeem` 仍属于后续扩展接口
- `PrizeType.CARD` 中奖不会进入奖励补偿队列，`reward_status` 会直接标记为 `SKIPPED`

**Kind 验证结果：**

- `ROLE_ADMIN` 可创建并激活 `COLLECT_CARD` 活动
- 单卡配置首次参与返回 `fullSet=true`
- 同一 buyer 再次参与返回 duplicate 提示
- `my-history` 中两条记录的 `rewardStatus` 均为 `SKIPPED`，且 `prizeSnapshot.prizeType = CARD`

---

### 4.5 VirtualFarmPlugin（虚拟养成，当前实现）

```java
@Component
public class VirtualFarmPlugin implements GamePlugin {

    @Override
    public GameType supportedType() { return GameType.VIRTUAL_FARM; }

    @Override
    public void initialize(ActivityGame game) {
        parseConfig(game.getConfig());
        requireHarvestReward(game.getId());
    }

    @Override
    public ParticipateResult participate(ParticipateContext ctx) {
        FarmConfig config = parseConfig(ctx.gameConfig());
        FarmAction action = parseAction(ctx.payload());
        ActivityVirtualFarm farm = farmRepository.findByGameIdAndPlayerId(ctx.gameId(), ctx.buyerId())
            .orElseGet(() -> new ActivityVirtualFarm(
                UUID.randomUUID().toString(), ctx.gameId(), ctx.buyerId(),
                config.maxStage(), config.stageProgress()));

        if (action == FarmAction.HARVEST) {
            return harvest(ctx.gameId(), farm);
        }
        if (farm.isHarvested()) return ParticipateResult.miss("Farm reward has already been claimed");
        if (!farm.isMatured()) {
            farm.water(config.waterProgress());
            farmRepository.save(farm);
        }

        return new ParticipateResult(
            true,
            null,
            "Virtual Farm",
            PrizeType.PROGRESS,
            null,
            buildAnimationHint(farm, FarmAction.WATER),
            farm.isMatured()
                ? "Farm matured. Use action HARVEST to claim reward"
                : "Farm progress +" + config.waterProgress()
        );
    }

    @Override
    public Optional<String> extensionTablePrefix() {
        return Optional.of("virtual_farm");
    }
}
```

**当前实现边界：**

- `game.config` 需提供：
  - `max_stage`
  - `stage_progress`
  - `water_progress`
- 激活活动时会校验至少存在一个非 `NOTHING` 的 harvest 奖励
- 当前使用通用 `POST /activity/v1/games/{id}/participate`：
  - `payload = null` 或缺省 `action` → 浇水
  - `payload = {"action":"HARVEST"}` → 收获成熟奖励
- 浇水阶段返回 `PrizeType.PROGRESS`，因此 `reward_status` 会自动标记为 `SKIPPED`
- 收获阶段复用 `activity_reward_prize`，按 `display_order` 选择第一个可用的非 `NOTHING` 奖励
- 购物/签到事件自动推进进度、专用 `/farm` / `/farm/harvest` API 仍属于后续扩展

**Kind 验证结果：**

- `ROLE_ADMIN` 可创建 `VIRTUAL_FARM` 活动并添加 harvest 奖励
- buyer 第 1 次参与返回 `Farm progress +50`
- buyer 第 2 次参与返回 `Farm matured. Use action HARVEST to claim reward`
- buyer 发送 `payload={"action":"HARVEST"}` 后返回 `POINTS`
- `my-history` 中 2 条浇水记录的 `rewardStatus=SKIPPED`，最终收获记录为 `rewardStatus=PENDING`

---

### 4.6 QuizPlugin / SlashPricePlugin / GroupBuyPlugin

结构与上述相同，篇幅略。关键差异：

| Plugin | participate() 核心逻辑 | onEvent() | 扩展表 |
|--------|----------------------|-----------|--------|
| `QuizPlugin` | 对比答案 → 计时加分 | ❌ | `quiz_question`, `quiz_answer` |
| `SlashPricePlugin` | 随机减价（越后越少） | ❌ | `slash_session`, `slash_help` |
| `GroupBuyPlugin` | 加入拼团 → 满员触发 | ❌ | `group_session`, `group_member` |

---

## 五、新增游戏类型：三步指南

> 以新增「摇一摇」游戏（手机摇动抽积分）为例

**Step 1：在 GameType 枚举新增值**

```java
// GameType.java
SHAKE_TO_WIN,   // 摇一摇抽奖（新增）
```

**Step 2：实现 GamePlugin**

```java
@Component   // ← Spring 自动发现，无需修改任何注册代码
public class ShakeToWinPlugin implements GamePlugin {

    @Override
    public GameType supportedType() { return GameType.SHAKE_TO_WIN; }

    @Override
    public void initialize(ActivityGame game) {
        // 与 InstantLotteryPlugin 相同：预加载奖品库存
        prizeRepo.findByGameId(game.getId()).forEach(p ->
            preloadPrizeStock(p));
    }

    @Override
    public ParticipateResult participate(ParticipateContext ctx) {
        JsonNode payload = parsePayload(ctx.payload());
        double shakeIntensity = payload.path("intensity").asDouble(1.0);
        if (shakeIntensity < MINIMUM_SHAKE) {
            return ParticipateResult.miss("摇动力度不够，再使劲一点！");
        }
        ActivityRewardPrize prize = drawWithAtomicStock(prizeRepo.findByGameId(ctx.gameId()));
        return new ParticipateResult(
            true,
            prize.getId(),
            prize.getName(),
            prize.getType(),
            prize.getValue(),
            "{\"vibrationMs\":300}",
            "摇出了 " + prize.getName() + "！");
    }

    @Override
    public void settle(ActivityGame game) {
        prizeRepo.findByGameId(game.getId()).forEach(this::clearPrizeStock);
    }
}
```

**Step 3：运营在管理后台创建活动 JSON，无需发布代码**

```json
{
  "type": "SHAKE_TO_WIN",
  "name": "618 摇一摇抽大奖",
  "start_at": "2026-06-18T20:00:00Z",
  "end_at":   "2026-06-18T20:59:59Z",
  "config": {
    "minimum_shake_intensity": 0.5,
    "per_user_daily": 3
  },
  "per_user_daily_limit": 3
}
```

**完成**。GameEngine、AntiCheatGuard、RewardDispatcher、Kafka 事件发布全部复用，新游戏只需关注自身业务逻辑。

---

## 六、数据库设计（shop_activity）

### 6.1 核心表（所有游戏共用）

```sql
-- 游戏活动表
CREATE TABLE activity_game (
    id                   VARCHAR(36)    NOT NULL PRIMARY KEY,
    type                 VARCHAR(32)    NOT NULL,
    name                 VARCHAR(128)   NOT NULL,
    description          TEXT,
    banner_url           VARCHAR(512),
    status               VARCHAR(20)    NOT NULL DEFAULT 'DRAFT',
    -- DRAFT / SCHEDULED / ACTIVE / ENDED / CANCELLED
    config               JSON           NOT NULL,  -- 游戏参数（Plugin 自定义）
    start_at             TIMESTAMP(6)   NOT NULL,
    end_at               TIMESTAMP(6)   NOT NULL,
    max_participants     INT,
    per_user_daily_limit INT            NOT NULL DEFAULT 1,
    per_user_total_limit INT            NOT NULL DEFAULT 3,
    entry_condition      JSON,
    -- {"min_order_amount":100, "require_tier":"SILVER", "entry_cost_points":50}
    participant_count    INT            NOT NULL DEFAULT 0,
    created_by           VARCHAR(64)    NOT NULL,
    created_at           TIMESTAMP(6)   NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at           TIMESTAMP(6)   NOT NULL DEFAULT CURRENT_TIMESTAMP(6)
                         ON UPDATE CURRENT_TIMESTAMP(6),
    INDEX idx_status_time (status, start_at, end_at)
);

-- 奖品池表（InstantLottery / RedEnvelope / ShakeToWin 等共用）
CREATE TABLE activity_reward_prize (
    id                   VARCHAR(36)    NOT NULL PRIMARY KEY,
    game_id              VARCHAR(36)    NOT NULL,
    name                 VARCHAR(128)   NOT NULL,
    type                 VARCHAR(32)    NOT NULL,
    -- POINTS / COUPON / CASH / PHYSICAL / VIRTUAL / CARD / PROGRESS / NOTHING
    value                DECIMAL(19,2),
    coupon_template_id   VARCHAR(36),
    product_id           VARCHAR(36),
    total_stock          INT            NOT NULL DEFAULT -1,  -- -1=无限
    remaining_stock      INT            NOT NULL DEFAULT -1,
    probability          DECIMAL(9,8)   NOT NULL,            -- 0.00000001~1.0
    display_order        INT            NOT NULL DEFAULT 0,
    image_url            VARCHAR(512),
    FOREIGN KEY (game_id) REFERENCES activity_game(id),
    INDEX idx_game (game_id)
);

-- 参与记录表（所有游戏共用，Plugin 特有数据存 extra_data JSON）
CREATE TABLE activity_participation (
    id                   VARCHAR(36)    NOT NULL PRIMARY KEY,
    game_id              VARCHAR(36)    NOT NULL,
    game_type            VARCHAR(32)    NOT NULL,
    buyer_id            VARCHAR(64),
    session_id           VARCHAR(128),
    ip_address           VARCHAR(64),
    device_fingerprint   VARCHAR(256),
    participated_at      TIMESTAMP(6)   NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    result               VARCHAR(20)    NOT NULL DEFAULT 'PENDING',
    -- WIN / MISS / PENDING
    prize_id             VARCHAR(36),
    prize_snapshot       JSON,          -- 中奖时奖品快照（防后续修改）
    reward_status        VARCHAR(20)    NOT NULL DEFAULT 'PENDING',
    -- PENDING / DISPATCHED / FAILED / SKIPPED(NOTHING/CARD/PROGRESS)
    reward_ref           VARCHAR(128),  -- 发放凭据
    extra_data           JSON,          -- Plugin 特有字段（卡片 ID、养成进度等）
    INDEX idx_game_player (game_id, buyer_id),
    INDEX idx_reward_status (reward_status),  -- 补偿 Job 用
    INDEX idx_participated_at (participated_at)
);

-- Outbox 事件表
CREATE TABLE activity_outbox_event (
    id           BIGINT         NOT NULL AUTO_INCREMENT PRIMARY KEY,
    game_id      VARCHAR(36)    NOT NULL,
    topic        VARCHAR(128)   NOT NULL,
    event_type   VARCHAR(64)    NOT NULL,
    payload      TEXT           NOT NULL,
    status       VARCHAR(20)    NOT NULL DEFAULT 'PENDING',
    created_at   TIMESTAMP(6)   NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    published_at TIMESTAMP(6),
    INDEX idx_status (status)
);
```

### 6.2 Plugin 扩展表（按需创建）

```sql
-- RedEnvelopePlugin 扩展：预分配红包明细
CREATE TABLE activity_red_envelope_packet (
    id          VARCHAR(36)    NOT NULL PRIMARY KEY,
    game_id     VARCHAR(36)    NOT NULL,
    amount      DECIMAL(10,2)  NOT NULL,
    claimed_by  VARCHAR(64),
    claimed_at  TIMESTAMP(6),
    INDEX idx_game (game_id)
);

-- CollectCardPlugin 扩展：卡片定义
CREATE TABLE activity_collect_card_def (
    id          VARCHAR(36)    NOT NULL PRIMARY KEY,
    game_id     VARCHAR(36)    NOT NULL,
    card_name   VARCHAR(64)    NOT NULL,
    rarity      VARCHAR(16)    NOT NULL,  -- COMMON/RARE/LEGENDARY
    probability DECIMAL(9,8)   NOT NULL
);

-- CollectCardPlugin 扩展：玩家持有的卡片
CREATE TABLE activity_player_card (
    id          VARCHAR(36)    NOT NULL PRIMARY KEY,
    game_id     VARCHAR(36)    NOT NULL,
    buyer_id   VARCHAR(64)    NOT NULL,
    card_id     VARCHAR(36)    NOT NULL,
    source      VARCHAR(32)    NOT NULL,  -- DROP/GIFT
    created_at  TIMESTAMP(6)   NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    INDEX idx_player_game (buyer_id, game_id)
);

-- VirtualFarmPlugin 扩展：玩家农场状态
CREATE TABLE activity_virtual_farm (
    id           VARCHAR(36)   NOT NULL PRIMARY KEY,
    game_id      VARCHAR(36)   NOT NULL,
    buyer_id    VARCHAR(64)   NOT NULL,
    stage        INT           NOT NULL DEFAULT 1,
    progress     INT           NOT NULL DEFAULT 0,
    max_stage    INT           NOT NULL,
    max_progress INT           NOT NULL,
    last_water_at TIMESTAMP(6),
    harvested_at  TIMESTAMP(6),
    created_at   TIMESTAMP(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    UNIQUE KEY uk_activity_virtual_farm_game_player (game_id, buyer_id)
);

-- SlashPricePlugin 扩展：砍价会话
CREATE TABLE activity_slash_session (
    id           VARCHAR(36)    NOT NULL PRIMARY KEY,
    game_id      VARCHAR(36)    NOT NULL,
    buyer_id    VARCHAR(64)    NOT NULL,
    product_id   VARCHAR(36)    NOT NULL,
    original_price DECIMAL(19,2) NOT NULL,
    current_price  DECIMAL(19,2) NOT NULL,
    target_price   DECIMAL(19,2) NOT NULL DEFAULT 0.00,
    helper_count   INT           NOT NULL DEFAULT 0,
    status         VARCHAR(20)   NOT NULL DEFAULT 'IN_PROGRESS',
    share_token    VARCHAR(64)   NOT NULL UNIQUE,
    created_at     TIMESTAMP(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    INDEX idx_share_token (share_token)
);

-- GroupBuyPlugin 扩展：拼团会话
CREATE TABLE activity_group_session (
    id              VARCHAR(36)   NOT NULL PRIMARY KEY,
    game_id         VARCHAR(36)   NOT NULL,
    product_id      VARCHAR(36)   NOT NULL,
    leader_id       VARCHAR(64)   NOT NULL,
    required_size   INT           NOT NULL,
    current_size    INT           NOT NULL DEFAULT 1,
    group_price     DECIMAL(19,2) NOT NULL,
    status          VARCHAR(20)   NOT NULL DEFAULT 'FORMING',
    -- FORMING / FORMED / FAILED / EXPIRED
    share_token     VARCHAR(64)   NOT NULL UNIQUE,
    expires_at      TIMESTAMP(6)  NOT NULL,
    created_at      TIMESTAMP(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    INDEX idx_share_token (share_token)
);
```

---

## 七、可扩容设计（Scalability）

### 7.1 无状态实例 + Redis 热状态

```
activity-service 实例本身完全无状态：
  • 游戏库存     → Redis（DECR 原子操作）
  • 参与防重     → Redis（SET NX）
  • 限速计数     → Redis（INCR + EXPIRE）
  • 红包池       → Redis List（LPOP）
  • 游戏元数据   → Redis Hash（10min 缓存，降低 DB 读压力）

任意实例宕机 → 其他实例无缝接管，无数据丢失
```

### 7.2 Redis 集群分片策略

```
按 gameId 分片，同一游戏的所有 key 落同一 slot：
  Key 命名使用 hashtag：{gameId}:packets, {gameId}:stock:prize-001

Redis Cluster 3 主 3 从：
  Slot 0-5460    → Master A（平日游戏）
  Slot 5461-10922 → Master B（平日游戏）
  Slot 10923-16383 → Master C（大促游戏，独立节点避免干扰）

大促前临时扩节点：ElastiCache 支持热添加节点，数据自动 rebalance
```

### 7.3 HPA 弹性扩容配置

```yaml
# k8s/activity-service-hpa.yaml
apiVersion: autoscaling/v2
kind: HorizontalPodAutoscaler
metadata:
  name: activity-service-hpa
spec:
  scaleTargetRef:
    kind: Deployment
    name: activity-service
  minReplicas: 2
  maxReplicas: 50
  metrics:
    - type: Resource
      resource:
        name: cpu
        target:
          type: Utilization
          averageUtilization: 60        # CPU 60% 触发扩容
    - type: Pods
      pods:
        metric:
          name: activity_rps            # 自定义指标：每秒请求数
        target:
          type: AverageValue
          averageValue: "1000"          # 每 Pod 超过 1000 RPS 扩容
  behavior:
    scaleUp:
      stabilizationWindowSeconds: 30    # 扩容决策窗口短（快速响应）
      policies:
        - type: Pods
          value: 10
          periodSeconds: 60             # 每分钟最多加 10 Pod
    scaleDown:
      stabilizationWindowSeconds: 300   # 缩容窗口长（防震荡）
```

### 7.4 扩容前置预热（大促 SOP）

```
T-1天：运营在管理后台创建活动，状态 SCHEDULED
T-1天 22:00：Ops 手动将 activity-service replicas 调为 10
T-0天 00:00：GameScheduler 调用 plugin.initialize() 预加载 Redis
T-0天 08:00：活动状态自动切换 ACTIVE（GameStatusJob）
T-0天 08:00+：HPA 根据实际流量自动扩容至最多 50 Pod
T-0天 20:00：活动结束，plugin.settle() 异步对账，HPA 开始缩容
```

### 7.5 Kafka 异步奖励发放（削峰）

```
高并发参与时（万人/秒）：

activity-service（热路径）：
  Redis Lua → 记录 MySQL（异步写，via Outbox）→ 返回结果给用户（< 50ms）

Kafka（奖励队列）：
  topic: activity.prize.won.v1，分区数 = 64（支持 64 倍并行消费）

RewardConsumer（独立进程，可独立扩容）：
  消费 activity.prize.won.v1 → 调用 loyalty/promotion/wallet 发放
  消费速度 ≠ 生产速度，自然形成削峰
  发放失败 → 写 reward_status=FAILED → 补偿 Job 重试

PodDisruptionBudget（大促期间禁止 Pod 驱逐）：
  minAvailable: 3（保证至少 3 个 Pod 处理奖励）
```

### 7.6 奖励补偿 Job（最终一致性保障）

```java
@Scheduled(fixedDelay = 60_000)  // 每分钟
@Transactional
public void compensatePendingRewards() {
    // 查找 2 分钟前仍未发放的中奖记录（避免与正常异步发放竞争）
    Instant threshold = Instant.now().minus(2, MINUTES);
    List<ActivityParticipation> pending = participationRepo
        .findByRewardStatusAndWinAndParticipatedAtBefore("PENDING", true, threshold);

    for (ActivityParticipation p : pending) {
        try {
            dispatcher.dispatch(p);    // 重新发放
            p.setRewardStatus("DISPATCHED");
        } catch (Exception e) {
            p.setRewardStatus("FAILED");
            log.error("reward compensation failed for participation {}", p.getId(), e);
            // 连续 3 次失败 → 告警 + 人工介入
        }
        participationRepo.save(p);
    }
}
```

---

## 八、防作弊设计（当前实现）

```java
@Component
public class AntiCheatGuard {

    public void check(ActivityGame game, String buyerId, String ipAddress, String deviceFingerprint) {
        checkPlayerRateLimit(game.getId(), buyerId);
        checkIpRateLimit(game.getId(), ipAddress);
        checkDeviceReuse(game.getId(), buyerId, deviceFingerprint);
    }

    private void checkPlayerRateLimit(String gameId, String buyerId) {
        long count = incrementWithinWindow("activity:ac:player:%s:%s".formatted(gameId, buyerId));
        if (count > properties.antiCheat().playerRequestsPerWindow()) {
            meterRegistry.counter("activity_anti_cheat_blocked_total", "reason", "player_rate_limit").increment();
            throw new BusinessException(CommonErrorCode.TOO_MANY_REQUESTS,
                "Too many participation attempts for this player");
        }
    }

    private void checkIpRateLimit(String gameId, String ipAddress) {
        String normalizedIp = normalizeIp(ipAddress);
        if (normalizedIp == null) return;
        long count = incrementWithinWindow("activity:ac:ip:%s:%s".formatted(gameId, normalizedIp));
        if (count > properties.antiCheat().ipRequestsPerWindow()) {
            meterRegistry.counter("activity_anti_cheat_blocked_total", "reason", "ip_rate_limit").increment();
            throw new BusinessException(CommonErrorCode.TOO_MANY_REQUESTS,
                "Too many participation attempts from this IP");
        }
    }

    private void checkDeviceReuse(String gameId, String buyerId, String deviceFingerprint) {
        if (!properties.antiCheat().deviceFingerprintEnabled() || isBlank(deviceFingerprint)) return;
        String key = "activity:ac:device:%s:%s".formatted(gameId, deviceFingerprint);
        String existingPlayer = redis.opsForValue().get(key);
        if (existingPlayer == null) {
            redis.opsForValue().set(key, buyerId, Duration.ofHours(properties.antiCheat().deviceFingerprintTtlHours()));
            return;
        }
        if (!existingPlayer.equals(buyerId)) {
            meterRegistry.counter("activity_anti_cheat_blocked_total", "reason", "device_reuse").increment();
            throw new BusinessException(CommonErrorCode.FORBIDDEN,
                "Device fingerprint is already bound to another participant");
        }
    }
}
```

**当前落地边界：**

- **已实现**
  - 单玩家突发限速：默认 `5` 次 / `10s`
  - 同 IP 突发限速：默认 `20` 次 / `10s`
  - 同设备跨账号参与拦截：按 `gameId + deviceFingerprint` 绑定 player
  - 指标：`activity_anti_cheat_blocked_total{reason}`
  - 每日次数 / 总次数限制：继续由 `GameEngine` + MySQL 参与记录做强校验

- **尚未实现**
  - 设备指纹前端统一采集 SDK
  - 中奖率异常检测自动告警
  - 更细粒度风控（UA / 行为序列 / 黑白名单）

**Kind 验证结果：**

- 同设备不同 buyer 参与同一活动：第 2 个账号返回 `403`
- 同一 buyer 对同一活动第 6 次突发请求：返回 `429`
- 以上拦截不影响正常 buyer 首次参与与中奖快照落库

---

## 九、RewardDispatcher 与熔断

当前代码仍是**基础版派发桩**：

- `RewardDispatcher` 每分钟扫描 `reward_status=PENDING` 且 `result=WIN` 的参与记录
- 当前 `dispatch()` 仅记录日志，后续再对接 loyalty / promotion / wallet 内部接口
- `GameEngine` 已在热路径上区分**无需外部分发**的奖品：
  - `PrizeType.CARD`
  - `PrizeType.PROGRESS`
  - 这两类中奖记录会直接标记为 `reward_status=SKIPPED`

因此，集卡与虚拟农场的浇水阶段可以复用统一参与入口，但不会错误进入积分/优惠券补偿队列；真正的 harvest 奖励则继续走 `PENDING -> RewardDispatcher` 路径。

---

## 十、API 设计（当前实现 vs 预留扩展）

```
# 当前已实现：公开（无需 JWT）
GET  /activity/v1/games                          # 活跃游戏列表
GET  /activity/v1/games/{id}/info                # 游戏详情（含奖池展示）

# 当前已实现：买家（JWT 鉴权 + ROLE_BUYER）
POST /activity/v1/games/{id}/participate         # 通用参与入口
#   - CollectCard: 默认抽卡
#   - VirtualFarm: 默认浇水；payload={"action":"HARVEST"} 时收获
GET  /activity/v1/games/{id}/my-history          # 我的参与记录

# 当前已实现：运营管理（Admin，需 ROLE_ADMIN）
POST /activity/v1/admin/games                    # 创建游戏
PUT  /activity/v1/admin/games/{id}               # 更新配置
POST /activity/v1/admin/games/{id}/activate      # 上线
POST /activity/v1/admin/games/{id}/end           # 强制结束
GET  /activity/v1/admin/games/{id}/stats         # 实时统计

# 当前已实现：内部（X-Internal-Token）
GET  /internal/activity/games/active             # buyer-bff 查询当前活跃游戏

# 预留扩展接口（设计已完成，尚未实现）
GET  /activity/v1/games/{id}/leaderboard         # 排行榜（部分游戏）
GET  /activity/v1/games/{id}/cards               # 我的集卡（CollectCard）
POST /activity/v1/games/{id}/cards/gift          # 赠送卡片
POST /activity/v1/games/{id}/redeem              # 集齐兑换
GET  /activity/v1/games/{id}/farm                # 农场状态（VirtualFarm）
POST /activity/v1/games/{id}/farm/harvest        # 领取成熟奖励
GET  /activity/v1/slash/{shareToken}             # 砍价进度（分享链接页）
POST /activity/v1/slash/{shareToken}/help        # 好友助力
GET  /activity/v1/group/{shareToken}             # 拼团详情
POST /activity/v1/group/{shareToken}/join        # 参团
GET  /activity/v1/admin/games/{id}/reconcile     # 触发对账
POST /internal/activity/event                    # 推送业务事件（VirtualFarm 进度）
```

---

## 十一、Kafka 事件

| Topic 发布 | 触发时机 | 消费方 |
|-----------|---------|--------|
| `activity.participated.v1` | 每次参与 | 数据分析 |
| `activity.prize.won.v1` | 中奖 | RewardConsumer（发奖）、notification-service |
| `activity.game.ended.v1` | 活动结束 | 结算 Job |
| `activity.groupbuy.formed.v1` | 拼团成功 | order-service（自动下单） |

| 预留 Topic 消费（尚未实现） | 来源 | 处理 |
|--------------------------|------|------|
| `order.completed.v1` | order-service | VirtualFarmPlugin 自动推进养成进度 |
| `loyalty.checkin.v1` | loyalty-service | VirtualFarmPlugin 自动浇水 |

---

## 十二、可扩展性检查清单

新增游戏类型时，只需改动以下内容，其余均自动复用：

| 需要改动 | 说明 |
|---------|------|
| ✅ `GameType` 枚举新增值 | 一行代码 |
| ✅ 实现 `GamePlugin` 接口 | 核心逻辑，其余横切关注点自动注入 |
| ✅ 可选：创建扩展 DB 表 | 游戏特有状态，通过 `extensionTablePrefix()` 声明 |
| ✅ 运营配置 JSON | 无需发布代码，管理后台创建 |

**不需要改动**（对扩展封闭）：

| 不需要改动 | 说明 |
|-----------|------|
| ❌ `GameEngine` | 核心调度器，新游戏自动路由 |
| ❌ `GamePluginRegistry` | Spring 自动发现新 Bean |
| ❌ `AntiCheatGuard` | 横切关注点，自动应用 |
| ❌ `RewardDispatcher` | 按奖品类型分发；`CARD/PROGRESS` 由 `GameEngine` 自动标记 `SKIPPED` |
| ❌ API Controller | 通用参与入口 `/games/{id}/participate` 覆盖所有 |
| ❌ Kafka 事件发布 | Outbox Pattern 统一处理 |
| ❌ 补偿 Job | 仅扫描 `reward_status=PENDING` 的可分发奖励，与游戏类型无关 |
| ❌ 可观测性 | Metrics / Traces / Logs 全部通用 |
