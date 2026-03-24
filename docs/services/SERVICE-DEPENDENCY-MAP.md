# 服务间依赖关系总览

> 版本：1.0 | 日期：2026-03-20

---

## 一、模块归属决策汇总

| 功能模块 | 归属服务 | 理由 |
|---------|---------|------|
| 真实货币余额 & 充值/提现 | `wallet-service` | 金融属性，Stripe 集成 |
| 订单支付 & 退款 | `wallet-service` | 货币扣减，金融事务 |
| **积分账户 & 流水** | **`loyalty-service`（新）** | 虚拟货币，独立规则 |
| **签到系统** | **`loyalty-service`（新）** | 签到产出积分，与积分系统高度耦合 |
| **积分兑换目录 & 兑换订单** | **`loyalty-service`（新）** | 积分的消耗场景，由积分系统管理 |
| 优惠券 & 折扣活动 | `promotion-service` | 营销活动，独立于用户资产 |
| 充值奖励券自动发放 | `promotion-service` | 已有 Kafka 驱动机制 |
| **抢红包 / 砸金蛋 / 大转盘** | **`activity-service`（新）** | 高并发竞争 + 随机奖励，独立并发模型 |
| **集卡 / 虚拟养成 / 拼团 / 砍价** | **`activity-service`（新）** | 社交裂变机制，独立游戏状态机 |
| 商品目录 & 库存 | `marketplace-service` | 商品领域 |
| 订单生命周期 | `order-service` | 订单领域 |
| 用户基本信息 & 地址簿 | `profile-service` | 用户档案领域 |
| JWT 认证 | `auth-server` | 安全领域 |
| 路由 & 鉴权 | `api-gateway` | 边缘层 |

---

## 二、完整服务依赖图

```
┌────────────────────────────────────────────────────────────────────────┐
│                         客户端层                                        │
│         buyer-portal          seller-portal        外部 API             │
└──────────────────┬───────────────────┬─────────────────────────────────┘
                   │                   │
                   ▼                   ▼
┌──────────────────────────────────────────────────────────────────────┐
│                     api-gateway (:8080)                               │
│    路由 / JWT 校验 / 公开白名单 / 游客 session / 限流                  │
└──────┬───────────────────────────────────────┬────────────────────────┘
       │                                       │
       ▼                                       ▼
┌─────────────┐                      ┌──────────────────┐
│  buyer-bff  │                      │   seller-bff     │
│  (:8081)    │                      │   (:8082)        │
│  Virtual    │                      │   Virtual        │
│  Threads    │                      │   Threads        │
└──┬──┬──┬───┘                      └──┬──┬────────────┘
   │  │  │                             │  │
   │  │  └── profile-service           │  └── promotion-service
   │  └───── loyalty-service (NEW)     └────── marketplace-service
   │
   ├──── wallet-service
   ├──── promotion-service
   ├──── marketplace-service
   ├──── order-service
   └──── loyalty-service (NEW)

┌──────────────────────────────────────────────────────────────────────┐
│                    领域服务层 (Domain Services)                        │
│                                                                       │
│  profile-service (:8083)     marketplace-service (:8084)             │
│  buyer_profile               marketplace_product                      │
│  seller_profile              product_sku                             │
│  buyer_address               product_category                        │
│  buyer_favorite              inventory_log                           │
│                                                                       │
│  order-service (:8085)       wallet-service (:8086)                  │
│  order                       wallet_account                          │
│  order_item                  wallet_transaction                      │
│  order_shipment              wallet_outbox_event                     │
│  order_refund                wallet_payment_method                   │
│  order_outbox_event                                                  │
│                                                                       │
│  promotion-service (:8087)   loyalty-service (:8088) [NEW]           │
│  promotion_offer             loyalty_account                         │
│  coupon_template             loyalty_transaction                     │
│  coupon_instance             loyalty_checkin                         │
│  coupon_usage                loyalty_reward_item                     │
│                              loyalty_redemption                      │
│                              loyalty_earn_rule                       │
└──────────────────────────────────────────────────────────────────────┘
```

---

## 三、服务间 HTTP 调用关系

```
调用方              →  被调方              接口
─────────────────────────────────────────────────────────────────────
buyer-bff           →  profile-service     GET /internal/profile/buyer/{id}
buyer-bff           →  wallet-service      GET /internal/wallet/account/{id}
buyer-bff           →  loyalty-service     GET /internal/loyalty/account/{id}  [NEW]
buyer-bff           →  promotion-service   GET 优惠券列表
buyer-bff           →  marketplace-service GET 商品列表/详情
buyer-bff           →  order-service       POST 创建订单 / GET 订单列表

order-service       →  marketplace-service POST /internal/marketplace/inventory/lock
order-service       →  marketplace-service POST /internal/marketplace/inventory/deduct
order-service       →  wallet-service      POST /internal/wallet/pay
order-service       →  promotion-service   POST /internal/promotion/coupons/rollback
order-service       →  loyalty-service     POST /internal/loyalty/points/lock  [NEW]

loyalty-service     →  promotion-service   POST /internal/promotion/coupons/issue  [NEW]
loyalty-service     →  order-service       POST /internal/orders/redemption  [NEW]

promotion-service   →  profile-service     GET /internal/profile/buyer/{id}  (生日评估)

profile-service     →  loyalty-service     POST /internal/loyalty/onboarding/task  (资料完善/绑定手机任务) [NEW]
buyer-bff           →  loyalty-service     POST /internal/loyalty/onboarding/task  (加购触发任务) [NEW]
```

---

## 四、Kafka 事件总线

```
Topic                           Publisher           Consumers
──────────────────────────────────────────────────────────────────────────
user.registered.v1              profile-service     loyalty-service（100pts + 任务初始化）[NEW]
                                                    promotion-service（新人礼包优惠券）[NEW]
                                                    notification-service（欢迎邮件）[NEW]

wallet.transactions.v1          wallet-service      promotion-service（奖励）
                                                    loyalty-service（首充积分）[NEW]
                                                    notification-service

order.created.v1                order-service       —
order.paid.v1                   order-service       notification-service
order.shipped.v1                order-service       notification-service
order.completed.v1              order-service       loyalty-service（购物积分）[NEW]
                                                    promotion-service（发券）
                                                    marketplace-service（更新销量）
order.cancelled.v1              order-service       marketplace-service（释放库存）
                                                    loyalty-service（释放积分）[NEW]
order.refunded.v1               order-service       notification-service

marketplace.product.published   marketplace-service 搜索索引同步
marketplace.inventory.low       marketplace-service notification-service（补货提醒）
marketplace.inventory.restocked marketplace-service notification-service（到货提醒）

loyalty.points.earned.v1        loyalty-service     notification-service [NEW]
loyalty.points.expiring.v1      loyalty-service     notification-service [NEW]
loyalty.tier.upgraded.v1        loyalty-service     profile-service [NEW]
loyalty.checkin.v1              loyalty-service     数据分析 [NEW]
loyalty.redemption.v1           loyalty-service     notification-service, order-service [NEW]

promotion.coupon.issued.v1      promotion-service   notification-service
promotion.coupon.used.v1        promotion-service   数据分析
```

---

## 五、资产对象总览（买家视角）

```
买家资产（通过 buyer-bff Dashboard 聚合）：

┌─────────────────────────────────────────────────────────┐
│                   我的资产 (My Assets)                   │
│                                                         │
│  [wallet-service]      [loyalty-service]                │
│  💰 钱包余额            ⭐ 积分余额                       │
│  $250.00               1,250 pts                        │
│                        (GOLD 会员)                       │
│                                                         │
│  [promotion-service]                                    │
│  🎫 可用优惠券 3 张                                      │
│  ├─ 满100减10 (到期: 04/30)                             │
│  ├─ 免邮券     (到期: 05/15)                             │
│  └─ 9折券      (到期: 06/01)                             │
└─────────────────────────────────────────────────────────┘
```

---

## 六、loyalty-service 数据流

```
赚积分：
  购物完成 → order.completed.v1 → loyalty-service
              → 计算积分（金额 × 规则 × 等级加成）
              → loyalty_account.balance += N
              → loyalty_transaction (EARN, PURCHASE)
              → loyalty.points.earned.v1

  签到 → POST /loyalty/v1/checkin
       → 查连签 streak
       → loyalty_account.balance += 签到奖励
       → loyalty_transaction (EARN, CHECKIN)
       → loyalty_checkin

用积分：
  兑换 → POST /loyalty/v1/redemptions
       → loyalty_account.balance -= points_required
       → loyalty_transaction (DEDUCT, REDEEM)
       → loyalty_redemption (PROCESSING)
       → 调用 promotion-service（COUPON）或 order-service（PHYSICAL）
       → loyalty_redemption (COMPLETED)
```

---

## 七、新增服务对现有系统的影响

### 对 buyer-bff 的影响
- Dashboard 聚合新增 `loyaltyClient.getAccount(playerId)` 并发调用
- 结账流程新增积分抵扣参数 `points_to_use`

### 对 order-service 的影响
- 新增 `POINTS_REDEMPTION` 订单类型
- 新增 `/internal/orders/redemption` 内部接口
- 订单创建需检查并锁定积分

### 对 promotion-service 的影响
- 新增 `/internal/promotion/coupons/issue` 供 loyalty-service 兑换时调用
- 新增 `loyalty.redemption.v1` Kafka 消费（COUPON 类型兑换）

### 对 api-gateway 的影响
- 新增路由：`/loyalty/**` → `loyalty-service`
- 公开路由无需变动

### 对本地 / 集群运行面的影响
- Kind / K8s 清单需要包含 `loyalty-service` Deployment + Service
- MySQL 初始化脚本需要创建 `shop_loyalty` 数据库
- 如需弹性验证，可继续为 `loyalty-service` 增补 HPA 配置

---

## 八、新用户注册事件流（user.registered.v1）

```
用户完成注册
    │
    ▼
profile-service
    ├── 写 buyer_profile（referrer_id 记入来源）
    └── 发布 user.registered.v1
            │
            ├── loyalty-service
            │       ├── earnPoints(+100, REGISTER)
            │       └── initOnboardingTasks(14d TTL × 7 项)
            │
            ├── promotion-service
            │       ├── issue WELCOME-5-NODOOR 券
            │       ├── issue WELCOME-FREESHIP 券
            │       ├── issue WELCOME-90PCT 券
            │       └── (referrerId != null) → issue REFERRAL-BONUS-2 给邀请人
            │
            └── notification-service
                    └── 发送欢迎邮件（含礼包清单 + 任务引导）
```

### 新用户任务完成事件链

```
任务 Key             触发来源                    loyalty-service 动作
──────────────────────────────────────────────────────────────────────
COMPLETE_PROFILE   profile-service 内部调用     completeTask → +20 pts
BIND_PHONE         profile-service 内部调用     completeTask → +30 pts
FIRST_CHECKIN      loyalty-service 自身         completeTask → +10 pts
FIRST_ADD_CART     buyer-bff 内部调用           completeTask → +20 pts
FIRST_ORDER        order.completed.v1 消费      completeTask → +100 pts + $3 券
FIRST_REVIEW       review.events.v1 消费        completeTask → +30 pts
FIRST_REFERRAL     user.registered.v1 消费      completeTask（邀请人）→ +50 pts

全部完成 → 额外 +100 pts 成就奖励
```
