# Shop Platform — Phase 3/4 业务功能 实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task.

**Goal:** 完成买家注册闭环（注册 Endpoint + 欢迎页 + 邀请裂变），扩展认证方式（SMS OTP），扩展支付渠道（PayPal + Klarna BNPL）。

**Spec:** `docs/superpowers/specs/2026-03-23-phase3-business-features-design.md`

**Tech Stack:** Spring Boot 3.5.11 / Java 25 / Kotlin 2.3.20 / Thymeleaf / Redis / Twilio / PayPal Orders API v2 / Klarna Payments v1

**依赖顺序：** Task 1（profile 字段）→ Task 2（注册 Endpoint）→ Task 3（欢迎页）→ Task 4（邀请裂变）→ Task 5（SMS OTP）→ Task 6（PayPal）→ Task 7（Klarna）

---

## Week 1：注册基础

---

## Task 1: profile-service — buyer 表添加 invite_code / referrer_id 字段

**Goal:** profile-service 的 buyer 账户支持邀请码生成和裂变记录，新建 referral_record 表。

**Files:**
- Create: `profile-service/src/main/resources/db/migration/V{next}__add_referral_fields.sql`
- Modify: `profile-service/src/main/java/dev/meirong/shop/profile/domain/BuyerEntity.java`
- Create: `profile-service/src/main/java/dev/meirong/shop/profile/domain/ReferralRecordEntity.java`
- Create: `profile-service/src/main/java/dev/meirong/shop/profile/domain/ReferralRecordRepository.java`

- [ ] **Step 1: Flyway 迁移**

```sql
-- V{next}__add_referral_fields.sql
ALTER TABLE buyer
  ADD COLUMN invite_code  VARCHAR(16)   NULL UNIQUE COMMENT '邀请码（注册时自动生成）',
  ADD COLUMN referrer_id  CHAR(26)      NULL COMMENT '邀请人 buyerId（ULID）',
  ADD INDEX idx_invite_code (invite_code),
  ADD INDEX idx_referrer_id (referrer_id);

CREATE TABLE referral_record (
    id              CHAR(26)     NOT NULL PRIMARY KEY COMMENT 'ULID',
    inviter_id      CHAR(26)     NOT NULL COMMENT '邀请人 buyerId',
    invitee_id      CHAR(26)     NOT NULL COMMENT '被邀请人 buyerId',
    status          VARCHAR(16)  NOT NULL DEFAULT 'PENDING'
                                          COMMENT 'PENDING | REWARDED | EXPIRED',
    rewarded_at     TIMESTAMP(6) NULL,
    created_at      TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    UNIQUE KEY uk_invitee (invitee_id),   -- 一个人只能被邀请一次
    INDEX idx_inviter (inviter_id)
) COMMENT '邀请裂变记录';
```

- [ ] **Step 2: 在 BuyerEntity 中添加 inviteCode / referrerId 字段**

- [ ] **Step 3: 注册时自动生成 invite_code（14 位随机 Base62 短码）**

```java
// InviteCodeGenerator.java
public static String generate() {
    return RandomStringUtils.randomAlphanumeric(14).toUpperCase();
}
```

- [ ] **Step 4: 新增内部接口（供 auth-server 注册时调用）**

```
POST /internal/profile/buyer          → 创建 buyer 档案，生成 invite_code
GET  /internal/profile/referral?invite_code={code}  → 验证邀请码，返回 inviterId
```

预期输出：`./mvnw test -pl profile-service` 通过；buyer 表新增两个字段，referral_record 表创建成功。

---

## Task 2: auth-server — 新增 POST /auth/buyer/register Endpoint

**Goal:** 提供标准的邮箱+密码注册流程，注册成功后发 `buyer.registered.v1` Kafka 事件（含 referral 信息）。

**Files:**
- Create: `auth-server/src/main/java/dev/meirong/shop/authserver/controller/BuyerRegistrationController.java`
- Create: `auth-server/src/main/java/dev/meirong/shop/authserver/service/BuyerRegistrationService.java`
- Create: `auth-server/src/main/java/dev/meirong/shop/authserver/dto/BuyerRegisterRequest.java`
- Modify: `auth-server/src/main/resources/application.yml`（Kafka producer 配置，如未有）

- [ ] **Step 1: 注册接口**

```java
// POST /auth/buyer/register
public record BuyerRegisterRequest(
    @Email @NotBlank String email,
    @Size(min=8, max=72) @NotBlank String password,
    @NotBlank String nickname,
    @Nullable String inviteCode   // 可选，邀请码
) {}
```

- [ ] **Step 2: BuyerRegistrationService 逻辑**

```java
@Transactional
public BuyerRegistrationResult register(BuyerRegisterRequest req) {
    // 1. 检查邮箱是否已注册
    if (accountRepository.existsByEmail(req.email())) throw new BusinessException("EMAIL_ALREADY_EXISTS");
    // 2. 验证邀请码（可选），获取 inviterId
    String inviterId = null;
    if (req.inviteCode() != null) {
        inviterId = profileClient.findInviterByCode(req.inviteCode()).orElse(null);
    }
    // 3. 调用 profile-service 创建档案（内部 API）
    String buyerId = profileClient.createBuyer(req.email(), req.nickname(), inviterId);
    // 4. 创建 account（bcrypt 密码）
    AccountEntity account = new AccountEntity(buyerId, req.email(), passwordEncoder.encode(req.password()));
    accountRepository.save(account);
    // 5. 发 Kafka 事件
    eventPublisher.publish("buyer.registered.v1", new BuyerRegisteredEvent(buyerId, req.email(), inviterId));
    return new BuyerRegistrationResult(buyerId, /* jwt token */ generateToken(buyerId));
}
```

- [ ] **Step 3: 单元测试和集成测试**

```bash
./mvnw test -pl auth-server
```

预期输出：注册接口可用；邮箱重复时返回 `422 UNPROCESSABLE_ENTITY`；成功时返回 JWT token。

---

## Task 3: buyer-portal — 注册页 + 注册成功欢迎页

**Goal:** 新建 `/buyer/register.html` 注册表单，注册成功后跳转到 `/buyer/welcome`，展示 loyalty onboarding 任务和欢迎优惠券。

**Files:**
- Create: `buyer-portal/src/main/resources/templates/buyer-register.html`
- Create: `buyer-portal/src/main/resources/templates/buyer-welcome.html`
- Create: `buyer-bff/src/main/java/dev/meirong/shop/buyerbff/controller/BuyerRegistrationController.java`（如需 BFF 聚合）
- Modify: `buyer-portal/src/main/kotlin/dev/meirong/shop/buyerportal/controller/BuyerAuthController.kt`

- [ ] **Step 1: buyer-register.html — 注册表单**

```html
<form th:action="@{/buyer/register}" method="post">
  <input type="email" name="email" placeholder="邮箱" required />
  <input type="password" name="password" placeholder="密码（8位以上）" required />
  <input type="text" name="nickname" placeholder="昵称" required />
  <input type="text" name="inviteCode" placeholder="邀请码（可选）" />
  <button type="submit">注册</button>
</form>
```

- [ ] **Step 2: buyer-welcome.html — 欢迎页（聚合 onboarding 任务 + 欢迎券）**

```html
<!-- 显示新人任务清单（来自 loyalty-service） -->
<div th:each="task : ${welcomeSummary.onboardingTasks}">
  <span th:text="${task.title}"></span>
  <span th:text="${task.pointsReward} + ' 积分'"></span>
</div>
<!-- 显示欢迎优惠券 -->
<div th:each="coupon : ${welcomeSummary.welcomeCoupons}">
  <span th:text="${coupon.name}"></span>
</div>
```

- [ ] **Step 3: buyer-portal BuyerAuthController 新增 /buyer/register 处理**

```kotlin
@PostMapping("/buyer/register")
fun register(req: BuyerRegisterFormRequest, session: HttpSession): String {
    val result = authServerClient.register(req)  // 调用 auth-server
    session.setAttribute("buyerId", result.buyerId)
    session.setAttribute("token", result.token)
    return "redirect:/buyer/welcome"
}

@GetMapping("/buyer/welcome")
fun welcome(model: Model, session: HttpSession): String {
    val buyerId = session.getAttribute("buyerId") as String
    model.addAttribute("welcomeSummary", buyerBffClient.getWelcomeSummary(buyerId))
    return "buyer-welcome"
}
```

预期输出：访问 `/buyer/register` 可注册，成功后自动跳转到 `/buyer/welcome` 展示 7 项任务和 3 张欢迎券。

---

## Week 2：邀请裂变

---

## Task 4: 邀请裂变 — 全链路实现

**Goal:** 实现邀请页面、注册时裂变绑定、首单触发奖励三个环节。

**Files:**
- Create: `buyer-portal/src/main/resources/templates/buyer-invite.html`
- Modify: `loyalty-service/src/main/java/dev/meirong/shop/loyalty/service/LoyaltyApplicationService.java`
- Create: `loyalty-service/src/main/java/dev/meirong/shop/loyalty/event/BuyerRegisteredListener.java`（如未有）

- [ ] **Step 1: buyer-invite.html — 邀请页面**

```html
<!-- 展示我的邀请码 + 分享链接 + 邀请进度 -->
<div>我的邀请码：<strong th:text="${inviteStats.inviteCode}"></strong></div>
<div>分享链接：<span th:text="${inviteStats.shareUrl}"></span></div>
<div>已邀请：<span th:text="${inviteStats.invitedCount}"></span> 人</div>
```

- [ ] **Step 2: loyalty-service 监听 buyer.registered.v1，处理裂变奖励**

```java
@KafkaListener(topics = "buyer.registered.v1", groupId = "loyalty-service")
@Transactional
public void onBuyerRegistered(ConsumerRecord<String, String> record) {
    BuyerRegisteredEvent event = deserialize(record.value());
    if (event.inviterId() == null) return;  // 无邀请码，跳过

    String idempotencyKey = "REFERRAL_REWARD:" + event.buyerId();
    compensationTaskService.executeOrSkip(idempotencyKey, "REFERRAL_REWARD",
        event.buyerId(), serialize(event), payload -> {
            // 1. 写入 referral_record（PENDING）
            referralRecordRepository.save(new ReferralRecord(event.inviterId(), event.buyerId()));
            // 2. 被邀请人注册奖励 +100 pts（onboarding task 已处理，此处为邀请人奖励）
            loyaltyService.addPoints(event.inviterId(), 50, "邀请好友奖励");
            // 3. 发欢迎邀请券
            promotionClient.issueCoupon(event.inviterId(), "REFERRAL_COUPON_TEMPLATE");
        });
}
```

- [ ] **Step 3: buyer-portal /buyer/invite 路由 + buyer-bff 邀请统计接口**

预期输出：注册时传入 inviteCode，loyalty-service 自动给邀请人发放 50 积分 + 1 张邀请券。

---

## Week 3：SMS OTP 登录

---

## Task 5: auth-server — SMS OTP 登录（SPI + Redis + Twilio）

**Goal:** 扩展 auth-server 社交登录 SPI，新增 SmsOtpProvider，Redis 存储 OTP，Twilio 发送短信，含频控逻辑。

**Files:**
- Create: `auth-server/src/main/java/dev/meirong/shop/authserver/sms/SmsGateway.java`（SPI 接口）
- Create: `auth-server/src/main/java/dev/meirong/shop/authserver/sms/TwilioSmsGateway.java`
- Create: `auth-server/src/main/java/dev/meirong/shop/authserver/sms/OtpService.java`
- Create: `auth-server/src/main/java/dev/meirong/shop/authserver/controller/SmsOtpController.java`
- Create: `buyer-portal/src/main/resources/templates/buyer-login-sms.html`

- [ ] **Step 1: SmsGateway SPI 接口**

```java
public interface SmsGateway {
    void send(String phone, String message);
}
```

- [ ] **Step 2: TwilioSmsGateway 实现**

```java
@Component
@ConditionalOnProperty("shop.sms.provider", havingValue = "twilio")
public class TwilioSmsGateway implements SmsGateway {
    // Twilio Java SDK: Message.creator(to, from, body).create()
}
```

- [ ] **Step 3: OtpService（Redis 存储，频控）**

```java
@Service
public class OtpService {
    private static final String KEY_PREFIX = "otp:";
    private static final String RATE_KEY_PREFIX = "otp:rate:";

    public void sendOtp(String phone) {
        // 频控：60s 内不重发
        String rateKey = RATE_KEY_PREFIX + phone;
        if (redisTemplate.hasKey(rateKey)) throw new BusinessException("OTP_TOO_FREQUENT");

        String otp = generateSixDigit();
        redisTemplate.opsForValue().set(KEY_PREFIX + phone, otp, Duration.ofMinutes(5));
        redisTemplate.opsForValue().set(rateKey, "1", Duration.ofSeconds(60));
        smsGateway.send(phone, "您的验证码：" + otp + "，5分钟内有效。");
    }

    public boolean verifyOtp(String phone, String code) {
        String stored = redisTemplate.opsForValue().get(KEY_PREFIX + phone);
        if (stored == null || !stored.equals(code)) return false;
        redisTemplate.delete(KEY_PREFIX + phone);  // 验证成功后删除
        return true;
    }
}
```

- [ ] **Step 4: SmsOtpController — 两个接口**

```
POST /auth/sms/send-otp    Body: {phone}         → 发送 OTP
POST /auth/sms/verify-otp  Body: {phone, code}   → 验证 OTP，返回 JWT
```

- [ ] **Step 5: buyer-login-sms.html — SMS 登录页面**

```html
<!-- Step 1: 输入手机号 -->
<form id="send-otp-form">
  <input type="tel" name="phone" placeholder="手机号" />
  <button type="submit">获取验证码</button>
</form>
<!-- Step 2: 输入验证码 -->
<form id="verify-otp-form" style="display:none">
  <input type="text" name="code" placeholder="6位验证码" maxlength="6" />
  <button type="submit">登录</button>
</form>
```

预期输出：手机号 + OTP 可完成登录，60s 内不能重复发送，5 次错误后锁定 30 分钟。

---

## Week 4：PayPal 支付扩展

---

## Task 6: wallet-service — PayPal Order API v2 集成

**Goal:** 在 wallet-service 的 PaymentProvider SPI 基础上实现 PayPalPaymentProvider，支持创建支付意图和处理 Webhook 回调。

**Files:**
- Create: `wallet-service/src/main/java/dev/meirong/shop/wallet/payment/PayPalPaymentProvider.java`
- Create: `wallet-service/src/main/java/dev/meirong/shop/wallet/payment/PayPalWebhookHandler.java`
- Create: `wallet-service/src/main/resources/db/migration/V{next}__add_paypal_payment.sql`
- Modify: `buyer-portal/src/main/resources/templates/buyer-checkout.html`（添加 PayPal 按钮）

- [ ] **Step 1: 添加 PayPal SDK 依赖**

```xml
<dependency>
    <groupId>com.paypal.sdk</groupId>
    <artifactId>paypalserversdk</artifactId>
    <version>0.6.1</version>
</dependency>
```

- [ ] **Step 2: PayPalPaymentProvider — 实现 PaymentProvider SPI**

```java
@Component
@ConditionalOnProperty("shop.payment.paypal.enabled", havingValue = "true")
public class PayPalPaymentProvider implements PaymentProvider {

    @Override
    public String providerType() { return "PAYPAL"; }

    @Override
    public PaymentIntent createIntent(CreatePaymentIntentRequest req) {
        // PayPal Orders API v2: POST /v2/checkout/orders
        OrderRequest orderRequest = new OrderRequest()
            .intent(CheckoutPaymentIntent.CAPTURE)
            .purchaseUnits(List.of(new PurchaseUnitRequest()
                .amount(new AmountWithBreakdown()
                    .currencyCode(req.currency())
                    .value(req.amount().toPlainString()))));
        Order order = ordersController.ordersCreate(/* ... */);
        return new PaymentIntent(order.getId(), "PAYPAL", order.getLinks()
            .stream().filter(l -> l.getRel().equals("approve"))
            .findFirst().map(LinkDescription::getHref).orElseThrow());
    }

    @Override
    public PaymentResult capture(String paymentIntentId) {
        Order captured = ordersController.ordersCapture(paymentIntentId, /* ... */);
        return new PaymentResult(captured.getId(),
            captured.getStatus() == OrderStatus.COMPLETED ? "SUCCESS" : "FAILED");
    }
}
```

- [ ] **Step 3: PayPalWebhookHandler — HMAC-SHA256 验证 + 事件处理**

```java
@PostMapping("/internal/payments/paypal/webhook")
public ResponseEntity<Void> handleWebhook(@RequestBody String body,
                                           @RequestHeader Map<String, String> headers) {
    // 验证 PayPal-Transmission-Sig
    if (!paypalWebhookVerifier.verify(headers, body)) return ResponseEntity.status(401).build();
    // 处理 CHECKOUT.ORDER.COMPLETED 事件
    paymentEventHandler.handle(body);
    return ResponseEntity.ok().build();
}
```

- [ ] **Step 4: buyer-portal checkout.html 添加 PayPal Smart Buttons**

```html
<div id="paypal-button-container"></div>
<script src="https://www.paypal.com/sdk/js?client-id=${paypalClientId}"></script>
<script>
paypal.Buttons({
    createOrder: function() {
        return fetch('/buyer/checkout/paypal/create-order', {method: 'POST'})
            .then(r => r.json()).then(d => d.orderId);
    },
    onApprove: function(data) {
        return fetch('/buyer/checkout/paypal/capture/' + data.orderID, {method: 'POST'})
            .then(() => window.location.href = '/buyer/orders');
    }
}).render('#paypal-button-container');
</script>
```

预期输出：buyer-portal checkout 页面出现 PayPal 按钮；沙箱环境完成支付并更新订单状态。

---

## Week 5：Klarna BNPL

---

## Task 7: wallet-service — Klarna Payments v1（先买后付）

**Goal:** 实现 KlarnaPaymentProvider，支持 Pay Later（BNPL）支付会话创建和授权回调。

**Files:**
- Create: `wallet-service/src/main/java/dev/meirong/shop/wallet/payment/KlarnaPaymentProvider.java`
- Create: `wallet-service/src/main/java/dev/meirong/shop/wallet/payment/KlarnaWebhookHandler.java`
- Modify: `buyer-portal/src/main/resources/templates/buyer-checkout.html`（添加 Klarna Widget）

- [ ] **Step 1: Klarna HTTP 客户端配置（使用 @HttpExchange 声明式接口）**

```java
@HttpExchange("https://api.klarna.com")
interface KlarnaApiClient {
    @PostExchange("/payments/v1/sessions")
    KlarnaSessionResponse createSession(@RequestBody KlarnaSessionRequest req);

    @PostExchange("/payments/v1/authorizations/{authorizationToken}/order")
    KlarnaOrderResponse createOrder(@PathVariable String authorizationToken,
                                     @RequestBody KlarnaOrderRequest req);
}
```

- [ ] **Step 2: KlarnaPaymentProvider — 实现 PaymentProvider SPI**

```java
@Override
public PaymentIntent createIntent(CreatePaymentIntentRequest req) {
    // Klarna Payments API v1: POST /payments/v1/sessions
    KlarnaSessionResponse session = klarnaClient.createSession(/* ... */);
    return new PaymentIntent(session.getSessionId(), "KLARNA", session.getClientToken());
}
```

- [ ] **Step 3: KlarnaWebhookHandler — Klarna Signature 验证**

- [ ] **Step 4: buyer-portal 添加 Klarna Widget**

```html
<!-- 在支付方式选择区域 -->
<div id="klarna-payments-container"></div>
<script>
Klarna.Payments.init({ client_token: '${klarnaClientToken}' });
Klarna.Payments.load({ container: '#klarna-payments-container',
                        payment_method_category: 'pay_later' });
</script>
```

预期输出：buyer-portal checkout 页面出现 Klarna "先买后付" 选项；测试环境完成授权流程。

---

## 验收标准

| Task | 验收项 |
|------|--------|
| Task 1 | buyer 表新增 invite_code / referrer_id；referral_record 表创建；内部 API 可用 |
| Task 2 | `POST /auth/buyer/register` 可用；重复邮箱 422；Kafka 事件发出 |
| Task 3 | 注册后自动跳转 `/buyer/welcome`；展示 7 项任务和 3 张欢迎券 |
| Task 4 | 使用邀请码注册后，邀请人获得 50 积分 + 1 张推荐券 |
| Task 5 | SMS OTP 登录可用；60s 频控生效；5 次失败后锁定 |
| Task 6 | checkout 页面出现 PayPal 按钮；沙箱支付完成后订单状态更新 |
| Task 7 | checkout 页面出现 Klarna BNPL 选项；授权流程可完成 |
