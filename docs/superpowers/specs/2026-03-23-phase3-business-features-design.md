# Shop Platform — Phase 3/4 业务功能 设计文档

> 版本：1.0 | 日期：2026-03-23

---

## 一、概述

### 1.1 目标与范围

本文档覆盖 Shop Platform Phase 3 收尾与 Phase 4 部分业务功能，核心目标是完成用户注册漏斗闭环、裂变增长机制、SMS OTP 多因素认证以及支付方式扩展。

**范围内（IN）：**

- 买家注册 Endpoint（auth-server）+ 注册成功页（buyer-portal）
- 邀请裂变链接 + 奖励发放机制
- SMS OTP 登录（auth-server SPI 扩展）
- PayPal Order API v2 支付集成
- Klarna BNPL（Pay Later）集成

**范围外（OUT）：**

- Apple Sign-In（独立 spec，需 Apple Developer 账号）
- 卖家邀请体系（另立 spec）
- 积分等级晋升规则调整（现有 loyalty-service 已覆盖）

### 1.2 功能优先级一览

| 功能 | 优先级 | 依赖 | 预估工作量 |
|------|--------|------|-----------|
| 买家注册 Endpoint + 注册成功页 | P0 | profile-service 字段扩展 | 3d |
| 邀请裂变 | P0 | 买家注册完成 | 4d |
| SMS OTP 登录 | P1 | Twilio 账号 | 5d |
| PayPal 支付扩展 | P1 | wallet-service PaymentProvider SPI | 4d |
| Klarna BNPL | P2 | PayPal 完成后复用框架 | 3d |

---

## 二、注册完善流 + 邀请裂变

### 2.1 买家注册 Endpoint（auth-server 新增 POST /auth/buyer/register）

#### 现状分析

当前 auth-server 已有 Google OAuth 社交登录，但缺少标准的邮箱 + 密码买家自注册流程。用户注册后没有专属 landing page，直接跳到登录页或首页，注册福利（积分、欢迎券）虽已有触发逻辑但用户无感知。

#### 接口设计

```
POST /auth/buyer/register
Content-Type: application/json

Request:
{
  "username":   "alice123",
  "email":      "alice@example.com",
  "password":   "Passw0rd!",
  "invite_code": "INV-XXXXXXXX"    // 可选，邀请码
}

Response 201:
{
  "buyer_id":   "01HX...",
  "username":    "alice123",
  "email":       "alice@example.com",
  "access_token":  "eyJ...",        // 注册即登录，减少用户摩擦
  "token_type":    "Bearer",
  "expires_in":    3600
}

Error codes:
  409 DUPLICATE_EMAIL    — 邮箱已注册
  409 DUPLICATE_USERNAME — 用户名已被使用
  400 INVALID_INVITE_CODE — 邀请码不存在或已过期
```

#### 注册流程（事务边界）

```
1. 校验 email / username 唯一性
2. 若携带 invite_code：
   a. 查找 referral_record 表获取 referrer_id（邀请码有效期 30 天）
   b. invite_code 无效 → 400 INVALID_INVITE_CODE
3. 在 auth-server 创建账号（bcrypt 密码哈希，cost=12）
4. 调用 profile-service 内部接口 POST /internal/profile/buyer 创建档案
   - 写入 invite_code（新生成的）和 referrer_id（若有）
5. 发布 Kafka 事件 buyer.registered.v1（通过 auth-server Outbox Pattern）
6. 签发 JWT，返回 201
```

#### 密码强度校验

```
最小长度 8 位
必须包含：大写字母、小写字母、数字
可选：特殊字符（@$!%*?&）
使用 Passay 库（Spring Boot 3.x 兼容）
```

#### 邮箱验证（异步，不阻塞注册成功）

注册成功后通过 Kafka 事件触发 notification-service 发送邮件验证链接。`buyer_profile.email_verified` 字段默认 `false`，验证后更新为 `true`。未验证用户仍可正常使用，但购物满 $100 时提示验证邮箱。

### 2.2 注册成功页 /buyer/welcome（Thymeleaf）

#### 页面功能

注册成功页聚合以下信息，展示新用户福利全貌：

```
/buyer/welcome?buyerId={buyerId}
（由注册成功后客户端带 JWT 访问，buyer-bff 聚合数据）
```

**页面数据聚合（buyer-bff GET /bff/buyer/welcome-summary）：**

```java
// WelcomeSummaryService.java — 并发聚合
try (var scope = new StructuredTaskScope.ShutdownOnFailure()) {
    var loyaltyFuture  = scope.fork(() -> loyaltyClient.getAccount(buyerId));
    var couponsFuture  = scope.fork(() -> promotionClient.getNewUserCoupons(buyerId));
    var tasksFuture    = scope.fork(() -> loyaltyClient.getOnboardingTasks(buyerId));
    var inviteFuture   = scope.fork(() -> profileClient.getInviteCode(buyerId));
    scope.join();

    return WelcomeSummaryResponse.builder()
        .pointsBalance(loyaltyFuture.get().getBalance())
        .welcomeCoupons(couponsFuture.get())
        .onboardingTasks(tasksFuture.get())
        .inviteCode(inviteFuture.get())
        .build();
}
```

**Thymeleaf 页面结构（`/buyer/welcome.html`）：**

```html
<!-- 欢迎横幅 -->
<section class="welcome-hero">
  欢迎加入！您已获得：
  <div class="points-badge">⭐ 100 积分 已到账</div>
</section>

<!-- 欢迎券包 -->
<section class="welcome-coupons">
  <div th:each="coupon : ${welcomeSummary.welcomeCoupons}">
    <span th:text="${coupon.name}"></span>
    <span th:text="${coupon.expireDays} + '天有效'"></span>
  </div>
</section>

<!-- 新人任务进度 -->
<section class="onboarding-tasks">
  <div th:each="task : ${welcomeSummary.onboardingTasks}">
    <span th:text="${task.title}"></span>
    <span th:text="'+' + ${task.pointsReward} + ' pts'"></span>
  </div>
</section>

<!-- 邀请好友入口 -->
<section class="invite-section">
  <a href="/buyer/invite">邀请好友，双方得 50 积分</a>
</section>
```

#### 路由保护

`/buyer/welcome` 仅允许已登录买家访问（buyer-portal 拦截器检查 JWT），未登录跳转到 `/buyer/login`。

### 2.3 邀请裂变设计

#### invite_code 生成（ULID-based 短码）

```java
// InviteCodeGenerator.java
public static String generate() {
    // ULID 前 10 位（时间部分）取 Base32 编码，加 4 位随机后缀
    // 格式：INV-XXXXXXXXXX（共 14 位，避免混淆字符 O/0/I/l）
    String ulid = UlidCreator.getUlid().toString();
    return "INV-" + ulid.substring(0, 10).toUpperCase();
}
// 碰撞概率：Crockford Base32 ^10 = 1T 种，实际用户量下碰撞率极低
// 数据库唯一索引兜底：INSERT 失败时自动重试一次
```

#### 数据模型（referral_record 表）

```sql
CREATE TABLE referral_record (
    id                CHAR(26)      NOT NULL PRIMARY KEY,   -- ULID
    invite_code       VARCHAR(20)   NOT NULL,               -- INV-XXXXXXXXXX
    referrer_id       CHAR(26)      NOT NULL,               -- 邀请人 buyer_id
    invitee_id        CHAR(26),                             -- 被邀请人 buyer_id（注册后写入）
    status            VARCHAR(20)   NOT NULL DEFAULT 'PENDING',
                                                            -- PENDING / REGISTERED / REWARDED / EXPIRED
    registered_at     TIMESTAMP(6),                         -- 被邀请人完成注册的时间
    first_order_at    TIMESTAMP(6),                         -- 被邀请人完成首单的时间
    reward_issued_at  TIMESTAMP(6),                         -- 邀请人奖励发放时间
    invite_code_expire_at TIMESTAMP(6) NOT NULL,            -- 邀请码有效期（注册时 + 30 天）
    created_at        TIMESTAMP(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at        TIMESTAMP(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6)
                      ON UPDATE CURRENT_TIMESTAMP(6),

    INDEX idx_referrer (referrer_id, created_at DESC),
    INDEX idx_invite_code (invite_code),
    INDEX idx_invitee (invitee_id)
);
```

**buyer_profile 表追加字段（Flyway 迁移）：**

```sql
ALTER TABLE buyer_profile
  ADD COLUMN invite_code    VARCHAR(20)  UNIQUE,        -- 该用户自己的邀请码（注册时生成）
  ADD COLUMN referrer_id    CHAR(26),                   -- 邀请人 buyer_id（可空）
  ADD COLUMN email_verified TINYINT(1)   NOT NULL DEFAULT 0;
```

#### 注册时 referrer_id 回传逻辑

```
注册请求携带 invite_code=INV-XXXXXXXXXX
  ↓
auth-server 查询 referral_record WHERE invite_code = ? AND status = 'PENDING'
  AND invite_code_expire_at > NOW()
  ↓
找到 → referrer_id = referral_record.referrer_id
未找到 → 400 INVALID_INVITE_CODE（仅当明确传了无效码时报错；不传 invite_code 则忽略）
  ↓
注册成功后：
  1. 更新 referral_record：invitee_id = 新用户 ID，status = 'REGISTERED'，registered_at = NOW()
  2. buyer.registered.v1 事件 payload 中携带 referrer_id 和 register_source = 'REFERRAL'
```

#### 裂变奖励触发（基于首单完成）

```
策略：被邀请人完成首单后触发奖励（防仅注册不购买的薅羊毛）

触发链路：
  order.events.v1 (ORDER_COMPLETED)
    → loyalty-service 消费
    → 判断 data.buyerId 是否有关联的 referral_record（status=REGISTERED）
    → 若有且为首单：
        a. 向邀请人发放 50 pts（earnPoints, source=REFERRAL_REWARD）
        b. 更新 referral_record.status = REWARDED，first_order_at / reward_issued_at 写入
        c. 发布 Kafka 事件 referral.completed.v1
    → loyalty-service 同时触发邀请人的 FIRST_REFERRAL 新人任务（若邀请人也是新用户）

被邀请人注册即时奖励（通过 promotion-service 消费 buyer.registered.v1）：
  - 发放 $3 礼品券（TMPL-REFERRAL-3OFF）
  - 注册额外 50 pts 由 loyalty-service 消费 buyer.registered.v1 时发放
```

#### 防刷设计

| 规则 | 实现 |
|------|------|
| 每个邀请人每月最多获得邀请奖励 10 次 | loyalty-service 查询当月 referral_record 已 REWARDED 数量 |
| 邀请码有效期 30 天 | referral_record.invite_code_expire_at |
| 被邀请人只奖励一次 | referral_record UNIQUE 约束：(invite_code, invitee_id) |
| 同 IP/设备注册视为同一人 | 注册时记录 IP，24h 内同 IP 注册超 3 次触发人工审核标记 |

#### buyer-portal 邀请页面（/buyer/invite）

**路径：** `/buyer/invite.html`

**功能：**
1. 展示该用户专属邀请码和邀请链接（`https://shop.example.com/register?invite={invite_code}`）
2. 一键复制链接 / 分享到社交媒体
3. 展示邀请记录列表（已邀请人数、待首单、已获奖励次数）
4. 展示本月剩余可获奖励次数（10 - 已获奖励数）

**buyer-bff 聚合接口：**

```
GET /bff/buyer/invite-stats
Response:
{
  "invite_code": "INV-01HX...",
  "invite_link": "https://shop.example.com/register?invite=INV-01HX...",
  "total_invited": 5,
  "total_rewarded": 3,
  "monthly_reward_count": 2,
  "monthly_reward_limit": 10,
  "records": [
    { "invitee_nickname": "张***", "status": "REWARDED", "registered_at": "..." },
    { "invitee_nickname": "李***", "status": "REGISTERED", "registered_at": "..." }
  ]
}
```

---

## 三、SMS OTP 登录

### 3.1 架构（auth-server SPI 扩展 SmsOtpProvider）

auth-server 已有 `SocialLoginProvider` SPI（Google 实现）。SMS OTP 不是社交登录，需要新增独立 SPI：

```
auth-server
└── spi/
    ├── SocialLoginProvider.java        (已有)
    │   └── GoogleOAuthProvider.java    (已有)
    ├── SmsOtpProvider.java             (新增接口)
    │   └── TwilioSmsOtpProvider.java   (新增实现)
    └── SmsGateway.java                 (新增抽象，解耦运营商)
        └── TwilioSmsGateway.java       (新增实现)
```

**SmsOtpProvider 接口：**

```java
public interface SmsOtpProvider {
    /**
     * 发送 OTP 到指定手机号。
     * 实现负责频控检查（60s 内不重发）和 Redis 存储。
     * @throws OtpRateLimitException 频控触发
     * @throws OtpSendFailedException 下游短信网关失败
     */
    void sendOtp(String phoneNumber, String locale) throws OtpRateLimitException;

    /**
     * 验证 OTP 并返回 phone-bound 的用户（或新建用户）。
     * @return 验证通过的 buyer_id
     * @throws OtpInvalidException OTP 错误
     * @throws OtpExpiredException OTP 已过期
     * @throws OtpLockedOutException 失败次数超限
     */
    String verifyOtp(String phoneNumber, String otp) throws OtpInvalidException;
}
```

### 3.2 OTP 流程（发送 → Redis 存储 → 验证）

```
POST /auth/otp/send
{ "phone": "+8613800138000", "locale": "zh-CN" }

  ↓ SmsOtpProvider.sendOtp()

1. 生成 6 位随机 OTP（SecureRandom，数字，避免 0O 混淆）
2. Redis SETEX  otp:{phone}:code   {otp}    TTL=300s（5 分钟有效）
3. Redis SETEX  otp:{phone}:cooldown  "1"   TTL=60s（冷却期）
4. Redis INCR   otp:{phone}:attempt              （失败计数，TTL=1800s）
5. 调用 SmsGateway.send(phone, otp, locale)
6. 返回 202 { "expires_in": 300, "cooldown_remaining": 60 }

---

POST /auth/otp/verify
{ "phone": "+8613800138000", "otp": "123456" }

  ↓ SmsOtpProvider.verifyOtp()

1. 检查锁定状态：Redis EXISTS otp:{phone}:lockout → 429 LOCKED_OUT
2. 读取 Redis otp:{phone}:code
   - KEY 不存在 → 423 OTP_EXPIRED
   - VALUE 不匹配：
       INCR otp:{phone}:attempt
       attempt >= 5 → SET otp:{phone}:lockout "1" TTL=1800s → 429 LOCKED_OUT
       else → 400 OTP_INVALID（返回剩余次数）
   - VALUE 匹配：
       DEL otp:{phone}:code otp:{phone}:attempt
3. 查询是否有绑定该手机号的买家账号
   - 有 → 返回 buyer_id，签发 JWT
   - 无 → 创建新买家账号（phone-first 注册，username 自动生成）
         → 返回 buyer_id + is_new_user=true
4. 返回 200 { "access_token": "eyJ...", "is_new_user": false }
```

### 3.3 频控设计（同号码 60s 冷却 + 5 次失败锁定）

| 场景 | Redis Key | TTL | 行为 |
|------|-----------|-----|------|
| 发送冷却 | `otp:{phone}:cooldown` | 60s | EXISTS 时返回 429，Body 含 `retry_after` |
| 失败计数 | `otp:{phone}:attempt` | 1800s | 累计 ≥ 5 次触发锁定 |
| 账号锁定 | `otp:{phone}:lockout` | 1800s（30 分钟）| 锁定期间拒绝验证 |
| 每日发送上限 | `otp:{phone}:daily` | 至次日 0 点 | INCR > 10 则拒绝发送（防短信轰炸） |

**全局频控（防 DDoS 短信轰炸）：**

```
在 API Gateway 层对 POST /auth/otp/send 做速率限制：
  同 IP：每分钟最多 5 次
  全局：每分钟最多 1000 次
（使用 Spring Cloud Gateway + Bucket4j 实现）
```

### 3.4 外部 SMS 服务接入（抽象 SmsGateway SPI，默认实现 Twilio）

```java
public interface SmsGateway {
    /**
     * 发送短信。
     * @param to      E.164 格式手机号（如 +8613800138000）
     * @param message 短信内容（OTP 模板由实现类控制）
     */
    void send(String to, String message) throws SmsGatewayException;
}

// TwilioSmsGateway 实现
@Component
@ConditionalOnProperty("sms.gateway", havingValue = "twilio", matchIfMissing = true)
public class TwilioSmsGateway implements SmsGateway {
    // 使用 Twilio Java SDK 7.x
    // 配置：sms.twilio.account-sid / auth-token / from-number
    // OTP 短信模板（支持 locale 本地化）：
    //   en: "Your Shop verification code: {otp}. Valid for 5 minutes."
    //   zh-CN: "您的 Shop 验证码：{otp}，5 分钟内有效，请勿泄露。"
}
```

**多供应商切换策略（便于灾备）：**

```yaml
# application.yml
sms:
  gateway: twilio            # twilio / aliyun / aws-sns
  twilio:
    account-sid: ${TWILIO_ACCOUNT_SID}
    auth-token:  ${TWILIO_AUTH_TOKEN}
    from-number: "+1XXXXXXXXXX"
```

**短信内容模板存放于 notification-service 的模板引擎中（统一管理），auth-server 通过内部接口请求模板渲染后调用 SmsGateway。**

### 3.5 buyer-portal SMS 登录页面

**路径：** `/buyer/login.html`（扩展现有登录页，增加 SMS 登录 Tab）

**交互流程：**

```
Tab：[密码登录] | [短信登录]

短信登录 Tab：
  手机号输入框（带国家码选择器，默认 +1）
  [发送验证码] 按钮（点击后按钮禁用 60s，倒计时显示）
  验证码输入框（6 位，自动聚焦）
  [登录] 按钮

  新用户（is_new_user=true）→ 跳转到 /buyer/welcome
  老用户 → 跳转到 /buyer/dashboard 或来源页
```

**前端 JS 逻辑（Thymeleaf + Vanilla JS）：**

```javascript
// sms-login.js
async function sendOtp() {
  const phone = buildE164(countryCode, phoneInput.value);
  const res = await fetch('/auth/otp/send', {
    method: 'POST',
    body: JSON.stringify({ phone, locale: navigator.language })
  });
  if (res.status === 429) {
    const data = await res.json();
    showError(`请等待 ${data.retry_after} 秒后重试`);
  } else {
    startCooldownTimer(60);
  }
}
```

---

## 四、支付扩展（PayPal + BNPL Klarna）

### 4.1 wallet-service PaymentProvider SPI 扩展

wallet-service 当前有 `StripeGateway` 接口（Stripe 专用）。扩展为通用 `PaymentProvider` SPI，支持多供应商：

```java
// PaymentProvider.java — 通用支付供应商 SPI
public interface PaymentProvider {
    String providerType();   // STRIPE / PAYPAL / KLARNA

    /**
     * 发起充值/付款意图，返回客户端需要的 redirect URL 或 client secret。
     */
    PaymentIntentResponse createPaymentIntent(PaymentIntentRequest request);

    /**
     * 确认支付（同步回调或 Webhook 触发）。
     */
    PaymentConfirmation confirmPayment(String providerReference);

    /**
     * 发起退款。
     */
    RefundResponse refund(String providerReference, BigDecimal amount);

    /**
     * 验证 Webhook 签名（各供应商实现）。
     */
    WebhookEvent parseWebhook(String payload, Map<String, String> headers);
}
```

**注册方式（Spring @Qualifier + Map 注入）：**

```java
@Service
public class PaymentProviderRegistry {
    private final Map<String, PaymentProvider> providers;

    public PaymentProviderRegistry(List<PaymentProvider> providerList) {
        this.providers = providerList.stream()
            .collect(Collectors.toMap(PaymentProvider::providerType, p -> p));
    }

    public PaymentProvider get(String type) {
        return Optional.ofNullable(providers.get(type))
            .orElseThrow(() -> new UnsupportedProviderException(type));
    }
}
```

### 4.2 PayPal 集成（Order API v2）

#### 流程

```
客户端选择 PayPal →
  buyer-bff POST /bff/buyer/checkout（payment_method=PAYPAL）→
  wallet-service PayPalProvider.createPaymentIntent() →
    调用 PayPal Orders API v2：POST /v2/checkout/orders
    返回 order_id + payer_action_url →
  wallet-service 写 wallet_transaction（status=PENDING, provider=PAYPAL）→
  buyer-portal 打开 PayPal 支付页面（payer_action_url）→
  用户完成支付 →
  PayPal 回调 Webhook（payment_capture.completed）→
  wallet-service /internal/wallet/webhook/paypal →
  确认交易，wallet_transaction.status → COMPLETED →
  发布 wallet.transactions.v1 事件
```

#### PayPal Orders API v2 请求示例

```json
POST https://api-m.paypal.com/v2/checkout/orders
Authorization: Bearer {access_token}

{
  "intent": "CAPTURE",
  "purchase_units": [{
    "amount": {
      "currency_code": "USD",
      "value": "29.99"
    },
    "custom_id": "{wallet_transaction_id}"   // 用于 Webhook 回调关联
  }],
  "application_context": {
    "return_url": "https://shop.example.com/buyer/payment/success",
    "cancel_url":  "https://shop.example.com/buyer/payment/cancel",
    "user_action": "PAY_NOW"
  }
}
```

#### PayPal 配置

```yaml
payment:
  paypal:
    client-id:     ${PAYPAL_CLIENT_ID}
    client-secret: ${PAYPAL_CLIENT_SECRET}
    mode:          sandbox    # sandbox / live
    webhook-id:    ${PAYPAL_WEBHOOK_ID}
```

#### AccessToken 缓存

PayPal Bearer Token 有效期约 9 小时，通过 Redis 缓存（key: `paypal:access_token`，TTL = expires_in - 300s），避免每次请求都换 Token。

### 4.3 Klarna BNPL 集成（Pay Later）

#### 产品选型

使用 Klarna **Pay Later（支付后付款）**，适合大额订单；集成 Klarna Payments API（而非 Klarna Checkout，保留平台自有结账页）。

#### 流程

```
客户端选择 Klarna Pay Later →
  wallet-service KlarnaProvider.createPaymentIntent() →
    POST https://api.klarna.com/payments/v1/sessions
    返回 client_token（前端 SDK 需要）和 session_id →
  buyer-portal 加载 Klarna JS SDK，渲染 Klarna 支付组件 →
  用户完成授权 →
  buyer-portal 收到 authorization_token →
  POST /wallet/v1/klarna/authorize（携带 authorization_token）→
  wallet-service KlarnaProvider 调用 POST /payments/v1/authorizations/{authToken}/order →
  Klarna 异步发货后触发 capture（由 order-service 调用 POST /payments/v1/orders/{orderId}/captures）→
  Klarna Webhook 通知支付完成 →
  wallet-service 更新交易状态
```

#### Klarna 配置

```yaml
payment:
  klarna:
    api-username: ${KLARNA_API_USERNAME}
    api-password: ${KLARNA_API_PASSWORD}
    region:       eu          # eu / na / oc
    webhook-secret: ${KLARNA_WEBHOOK_SECRET}
```

#### BNPL 风控提示

- 订单金额限制：Klarna 要求 $10–$10,000（超出范围在 UI 上隐藏 Klarna 选项）
- 展示 Klarna 分期说明文案（监管合规）：`"Pay in 4 interest-free payments"`
- 不支持部分退款积分抵扣（Klarna 退款全额返回给用户）

### 4.4 buyer-portal 支付方式选择 UI

**结账页 `/buyer/checkout.html` 扩展：**

```
支付方式
  ○ 钱包余额（余额：$45.00）
  ○ 信用卡（已绑定的卡，via Stripe）
  ○ PayPal
  ○ Klarna — 4期免息分期
       "分4期，每期 $X.XX，无额外费用"
       [需订单金额 $10-$10,000]

[下单并支付]
```

**前端逻辑：**
- 选择 PayPal → 隐藏「下单」按钮，显示 PayPal Smart Button（PayPal JS SDK）
- 选择 Klarna → 动态加载 Klarna Widget（Klarna JS SDK），替换原支付按钮
- 选择钱包/Stripe → 原有流程不变

### 4.5 Webhook 对接（PayPal IPN / Klarna callback）

#### Webhook 路由

所有支付 Webhook 不经过 API Gateway（绕过 JWT 鉴权），直接到达 wallet-service：

```
POST /internal/wallet/webhook/paypal   （PayPal Webhook，验证 PayPal-Transmission-Sig 头）
POST /internal/wallet/webhook/klarna   （Klarna Webhook，验证 Klarna-Idempotency-Key 头）
```

**Nginx/外网入口配置：**
```
/internal/wallet/webhook/* → 不经过 API Gateway，直接路由到 wallet-service:8086
```

#### PayPal Webhook 处理

```java
@PostMapping("/internal/wallet/webhook/paypal")
public ResponseEntity<Void> handlePayPalWebhook(
    @RequestBody String payload,
    @RequestHeader HttpHeaders headers) {

    paypalProvider.parseWebhook(payload, headers.toSingleValueMap());
    // event.type 映射：
    // PAYMENT.CAPTURE.COMPLETED → confirmPendingTransaction(customId)
    // PAYMENT.CAPTURE.DENIED    → failPendingTransaction(customId)
    // PAYMENT.CAPTURE.REFUNDED  → processRefund(customId)
    return ResponseEntity.ok().build();
}
```

#### Klarna Webhook 处理

```java
@PostMapping("/internal/wallet/webhook/klarna")
public ResponseEntity<Void> handleKlarnaWebhook(
    @RequestBody String payload,
    @RequestHeader("Klarna-Idempotency-Key") String idempotencyKey) {

    klarnaProvider.parseWebhook(payload, Map.of("Klarna-Idempotency-Key", idempotencyKey));
    // event_type 映射：
    // order_completed  → confirmCapture(orderId)
    // refund_completed → processRefund(orderId, amount)
    return ResponseEntity.ok().build();
}
```

#### 幂等性保证

所有 Webhook 处理前先检查 `wallet_idempotency_key` 表（key = `{provider}:{event_id}`），防止重复处理。

---

## 五、数据模型变更

### 5.1 profile_service：buyer 表增加字段

```sql
-- Flyway: V20260323_1__buyer_profile_invite_fields.sql
ALTER TABLE buyer_profile
  ADD COLUMN invite_code    VARCHAR(20)   UNIQUE COMMENT '该用户的专属邀请码（注册时生成）',
  ADD COLUMN referrer_id    CHAR(26)      NULL   COMMENT '邀请人 buyer_id（ULID）',
  ADD COLUMN email_verified TINYINT(1)    NOT NULL DEFAULT 0 COMMENT '邮箱是否已验证';

CREATE INDEX idx_buyer_invite_code ON buyer_profile (invite_code);
CREATE INDEX idx_buyer_referrer    ON buyer_profile (referrer_id);
```

### 5.2 referral_record 表（新建，归属 profile-service 数据库 shop_profile）

```sql
-- Flyway: V20260323_2__referral_record.sql
CREATE TABLE referral_record (
    id                    CHAR(26)      NOT NULL PRIMARY KEY COMMENT 'ULID',
    invite_code           VARCHAR(20)   NOT NULL             COMMENT '邀请码',
    referrer_id           CHAR(26)      NOT NULL             COMMENT '邀请人 buyer_id',
    invitee_id            CHAR(26)      NULL                 COMMENT '被邀请人 buyer_id（注册后写入）',
    status                VARCHAR(20)   NOT NULL DEFAULT 'PENDING'
                                                             COMMENT 'PENDING/REGISTERED/REWARDED/EXPIRED',
    registered_at         TIMESTAMP(6)  NULL,
    first_order_at        TIMESTAMP(6)  NULL,
    reward_issued_at      TIMESTAMP(6)  NULL,
    invite_code_expire_at TIMESTAMP(6)  NOT NULL,
    created_at            TIMESTAMP(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at            TIMESTAMP(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6)
                          ON UPDATE CURRENT_TIMESTAMP(6),

    INDEX idx_referrer_id  (referrer_id, created_at DESC),
    INDEX idx_invite_code  (invite_code),
    INDEX idx_invitee_id   (invitee_id)
) COMMENT '邀请裂变记录';
```

**归属决策**：referral_record 是用户关系数据，归属 profile-service 的 shop_profile 数据库，由 profile-service 维护读写 API，loyalty-service 通过内部接口查询。

### 5.3 wallet_payment_method 表（扩展 provider_type 枚举）

```sql
-- Flyway: V20260323_3__wallet_payment_method_extend.sql
-- 原有 provider 字段为 VARCHAR(32)，无需 DDL 变更，直接支持新值
-- 新增 provider_data JSON 字段（存储各供应商专属数据）
ALTER TABLE wallet_payment_method
  ADD COLUMN provider_data JSON NULL COMMENT '供应商专属扩展数据（PayPal billing agreement ID / Klarna customer token 等）';

-- wallet_transaction 表扩展 provider 字段（记录支付供应商）
ALTER TABLE wallet_transaction
  MODIFY COLUMN provider_reference VARCHAR(512) COMMENT '供应商侧交易 ID（Stripe charge_id / PayPal order_id / Klarna order_id）';
```

**支持的 provider 值：**

| provider | 说明 |
|----------|------|
| `STRIPE` | 现有，Stripe 信用卡 |
| `WALLET` | 现有，钱包余额直扣 |
| `PAYPAL` | 新增，PayPal Order API v2 |
| `KLARNA` | 新增，Klarna Payments API |

---

## 六、Kafka 事件扩展

### 6.1 buyer.registered.v1（新增）

由 auth-server 在买家注册成功后通过 Outbox Pattern 发布（替代 / 补充现有 `buyer.registered.v1`）：

```
Topic:   buyer.events.v1
Key:     {buyer_id}
Headers: eventType=buyer.registered.v1

Payload:
{
  "id":               "evt-01HX...",
  "source":           "auth-server",
  "type":             "buyer.registered.v1",
  "specVersion":      "1.0",
  "timestamp":        "2026-03-23T10:00:00Z",
  "data": {
    "buyer_id":        "01HX...",
    "username":         "alice123",
    "email":            "alice@example.com",
    "phone":            null,
    "register_source":  "ORGANIC",      // ORGANIC / REFERRAL / SMS_OTP / GOOGLE / APPLE
    "referrer_id":      "01HY...",       // REFERRAL 时有值，否则 null
    "invite_code_used": "INV-01HX...",   // REFERRAL 时有值，否则 null
    "registered_at":    "2026-03-23T10:00:00Z"
  }
}

消费方（Consumer Group）：
  loyalty-service-buyer-onboarding    → 发放欢迎积分 + 创建新人任务
  promotion-service-buyer-onboarding  → 发放欢迎券包（+ 裂变额外礼包）
  notification-service-buyer          → 发送欢迎邮件（USER_REGISTERED 模板）
```

**与 `profile.events.v1 (buyer.registered.v1)` 的关系：**

profile-service 现有事件继续保留（profile-service 主动发布时），auth-server 的 `buyer.registered.v1` 是在注册 Endpoint 新建后额外发布的业务语义事件。两者 `buyer_id` 相同，消费方需做幂等处理。

### 6.2 referral.completed.v1（新增）

由 loyalty-service 在确认裂变奖励发放后发布：

```
Topic:   loyalty.events.v1
Key:     {referrer_id}
Headers: eventType=referral.completed.v1

Payload:
{
  "id":            "evt-01HZ...",
  "source":        "loyalty-service",
  "type":          "referral.completed.v1",
  "timestamp":     "2026-03-23T12:00:00Z",
  "data": {
    "referral_record_id": "01HA...",
    "referrer_id":        "01HY...",
    "invitee_id":         "01HX...",
    "points_awarded":     50,
    "trigger":            "FIRST_ORDER",    // 奖励触发条件
    "completed_at":       "2026-03-23T12:00:00Z"
  }
}

消费方：
  notification-service  → 发送"恭喜！您的邀请奖励已到账"通知（邀请人）
  profile-service       → 更新 referral_record.status = REWARDED
```

---

## 七、实施路径

```
Week 1：注册基础（profile-service 字段扩展 → auth-server 注册 Endpoint → buyer-portal 注册页/欢迎页）
Week 2：邀请裂变（referral_record 表 → 裂变奖励逻辑 → buyer-portal 邀请页）
Week 3：SMS OTP（SPI 框架 → Redis OTP → Twilio Adapter → buyer-portal 登录页扩展）
Week 4：PayPal 支付扩展（PaymentProvider SPI 重构 → PayPal Order API v2 → Webhook）
Week 5：Klarna BNPL（KlarnaProvider → buyer-portal Klarna Widget → Webhook）
```

**关键依赖顺序：**

1. profile-service 字段扩展（Flyway 迁移）是所有注册/裂变功能的先决条件
2. auth-server 注册 Endpoint 需要 profile-service 内部 API 就绪
3. buyer-portal 注册成功页需要 buyer-bff welcome-summary 接口
4. 邀请裂变奖励需要 loyalty-service 具备 referral_record 查询能力
5. PaymentProvider SPI 重构完成后，PayPal 和 Klarna 可并行实施

---

## 附录 A：服务间接口汇总（新增）

| 来源 | 目标 | 接口 | 用途 |
|------|------|------|------|
| auth-server | profile-service | `POST /internal/profile/buyer` | 注册时创建档案 |
| auth-server | profile-service | `GET /internal/profile/referral?invite_code=X` | 验证邀请码 |
| buyer-bff | profile-service | `GET /internal/profile/buyer/{id}/invite-stats` | 邀请统计聚合 |
| buyer-bff | loyalty-service | `GET /internal/loyalty/onboarding/tasks/{id}` | 欢迎页任务聚合 |
| loyalty-service | profile-service | `GET /internal/profile/referral?invitee_id=X` | 查询裂变记录 |
| loyalty-service | profile-service | `PUT /internal/profile/referral/{id}/status` | 更新裂变状态 |
| wallet-service | PayPal API | PayPal Orders v2 | 支付意图创建 |
| wallet-service | Klarna API | Klarna Payments v1 | 支付会话创建 |

---

## 附录 B：安全考量

| 风险 | 措施 |
|------|------|
| OTP 暴力破解 | 5 次失败锁定 30 分钟 + 每日 10 次上限 |
| 短信轰炸 | 60s 冷却 + IP 速率限制 + 每日上限 |
| 邀请码枚举 | 邀请码 14 位随机，碰撞率极低；无效码不暴露是否存在 |
| 裂变羊毛 | 首单触发奖励 + 月度上限 + IP/设备检测 |
| PayPal 回调伪造 | PayPal-Transmission-Sig HMAC-SHA256 验证 |
| Klarna 回调伪造 | Klarna Signature 头验证 |
| Webhook 重放 | idempotency_key 表去重，TTL 48 小时 |
| 密码存储 | bcrypt cost=12，禁止明文存储 |
