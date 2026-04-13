# Spring Boot 3.5 Best Practices Evolution Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Evolve the Shop Platform to 2026 Spring Boot 3.5 best practices by implementing Redis→Redisson unification, @HttpExchange declarative clients, JWT RS256+JWKS migration, and AppCDS startup acceleration.

**Architecture:** Four independent streams executed sequentially. Redis→Redisson is lowest risk (simple dependency swap). @HttpExchange eliminates ~300 lines of RestClient boilerplate in BFFs. JWT RS256+JWKS replaces symmetric HS256 shared secret with asymmetric key pair + JWKS endpoint. CDS adds a training stage to Dockerfile.fast for 20-40% cold startup improvement.

**Tech Stack:** Java 25, Spring Boot 3.5.11, Redisson 3.44.0, Spring Security OAuth2 (Nimbus JOSE), AppCDS (Eclipse Temurin 25)

---

## File Structure

### Stream 1: Redis → Redisson Unification

| File | Action | Responsibility |
|------|--------|---------------|
| `services/buyer-bff/pom.xml` | Modify | Replace `spring-boot-starter-data-redis` with `redisson-spring-boot-starter` |
| `services/auth-server/pom.xml` | Modify | Replace `spring-boot-starter-data-redis` with `redisson-spring-boot-starter` |
| `services/activity-service/pom.xml` | Modify | Replace `spring-boot-starter-data-redis` with `redisson-spring-boot-starter` |
| `services/api-gateway/pom.xml` | Modify | Replace `spring-boot-starter-data-redis` with `redisson-spring-boot-starter` |
| `services/buyer-bff/src/main/java/.../service/GuestCartStore.java` | Modify | Replace `StringRedisTemplate` with `RedissonClient` (RBucket) |
| `services/auth-server/src/main/java/.../service/OtpChallengeService.java` | Modify | Replace `StringRedisTemplate` with `RedissonClient` (RBucket + RAtomicLong) |
| `services/activity-service/src/main/java/.../service/AntiCheatGuard.java` | Modify | Replace `StringRedisTemplate` with `RedissonClient` (RBucket + RAtomicLong) |
| `services/activity-service/src/main/java/.../engine/RedEnvelopePlugin.java` | Modify | Replace `StringRedisTemplate` + `DefaultRedisScript` with `RedissonClient` (RScript + RList) |
| `services/api-gateway/src/main/java/.../predicate/CanaryRequestPredicates.java` | Modify | Replace `StringRedisTemplate.opsForSet()` with `RedissonClient` (RSet) |
| `services/api-gateway/src/main/java/.../filter/RateLimitingFilter.java` | Keep | No change (Lua script via StringRedisTemplate is already optimal) |

### Stream 2: @HttpExchange Expansion

| File | Action | Responsibility |
|------|--------|---------------|
| `services/buyer-bff/src/main/java/.../client/ProfileServiceClient.java` | Create | @HttpExchange interface for profile-service |
| `services/buyer-bff/src/main/java/.../client/ProfileInternalServiceClient.java` | Create | @HttpExchange interface for profile internal API |
| `services/buyer-bff/src/main/java/.../client/WalletServiceClient.java` | Create | @HttpExchange interface for wallet-service |
| `services/buyer-bff/src/main/java/.../client/PromotionServiceClient.java` | Create | @HttpExchange interface for promotion-service |
| `services/buyer-bff/src/main/java/.../client/PromotionInternalServiceClient.java` | Create | @HttpExchange interface for promotion internal API |
| `services/buyer-bff/src/main/java/.../client/MarketplaceServiceClient.java` | Create | @HttpExchange interface for marketplace-service |
| `services/buyer-bff/src/main/java/.../client/OrderServiceClient.java` | Create | @HttpExchange interface for order-service |
| `services/buyer-bff/src/main/java/.../client/LoyaltyServiceClient.java` | Create | @HttpExchange interface for loyalty-service |
| `services/buyer-bff/src/main/java/.../config/BuyerBffConfig.java` | Modify | Register all @HttpExchange proxy beans |
| `services/buyer-bff/src/main/java/.../service/BuyerAggregationService.java` | Modify | Replace RestClient calls with injected client interfaces |
| `services/seller-bff/src/main/java/.../client/ProfileServiceClient.java` | Create | @HttpExchange for profile (seller) |
| `services/seller-bff/src/main/java/.../client/MarketplaceServiceClient.java` | Create | @HttpExchange for marketplace (seller) |
| `services/seller-bff/src/main/java/.../client/OrderServiceClient.java` | Create | @HttpExchange for order (seller) |
| `services/seller-bff/src/main/java/.../client/WalletServiceClient.java` | Create | @HttpExchange for wallet (seller) |
| `services/seller-bff/src/main/java/.../client/PromotionServiceClient.java` | Create | @HttpExchange for promotion (seller) |
| `services/seller-bff/src/main/java/.../client/SearchServiceClient.java` | Create | @HttpExchange for search (seller) |
| `services/seller-bff/src/main/java/.../config/SellerBffConfig.java` | Modify | Register seller client proxy beans |
| `services/seller-bff/src/main/java/.../service/SellerAggregationService.java` | Modify | Replace RestClient calls with injected client interfaces |

### Stream 3: JWT RS256 + JWKS

| File | Action | Responsibility |
|------|--------|---------------|
| `services/auth-server/src/main/java/.../config/AuthProperties.java` | Modify | Add RSA key Resource fields |
| `services/auth-server/src/main/java/.../service/JwtTokenService.java` | Modify | Replace HS256 with RS256 signing |
| `services/auth-server/src/main/java/.../controller/JwksController.java` | Create | Serve `/.well-known/jwks.json` |
| `services/auth-server/src/main/java/.../config/SecurityConfig.java` | Modify | Permit JWKS endpoint |
| `services/auth-server/src/main/resources/application.yml` | Modify | RSA key paths replace secret |
| `services/auth-server/src/main/resources/dev-keys/` | Create | Dev RSA key pair (PEM files) |
| `services/api-gateway/src/main/java/.../config/GatewayProperties.java` | Modify | Replace `jwtSecret` with `jwksUri` |
| `services/api-gateway/src/main/java/.../config/GatewaySecurityConfig.java` | Modify | Use `NimbusJwtDecoder.withJwkSetUri()` |
| `services/api-gateway/src/main/resources/application.yml` | Modify | Replace `jwt-secret` with `jwks-uri` |
| `platform/k8s/apps/base/platform.yaml` | Modify | Update env vars |

### Stream 4: AppCDS Startup Acceleration

| File | Action | Responsibility |
|------|--------|---------------|
| `platform/docker/Dockerfile.fast` | Modify | Add CDS training stage + production stage |

---

## Task 1: Redis → Redisson — buyer-bff GuestCartStore

**Files:**
- Modify: `services/buyer-bff/pom.xml`
- Modify: `services/buyer-bff/src/main/java/dev/meirong/shop/buyerbff/service/GuestCartStore.java`

- [ ] **Step 1: Replace Redis dependency in buyer-bff pom.xml**

In `services/buyer-bff/pom.xml`, find the `spring-boot-starter-data-redis` dependency and replace it with `redisson-spring-boot-starter`:

```xml
<!-- REMOVE -->
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-data-redis</artifactId>
</dependency>

<!-- ADD -->
<dependency>
    <groupId>org.redisson</groupId>
    <artifactId>redisson-spring-boot-starter</artifactId>
</dependency>
```

Note: `redisson-spring-boot-starter` transitively includes `spring-boot-starter-data-redis`, so `StringRedisTemplate` still works. The version is managed in root `pom.xml` (`<redisson.version>3.44.0</redisson.version>`).

- [ ] **Step 2: Migrate GuestCartStore from StringRedisTemplate to RedissonClient**

Replace the full content of `services/buyer-bff/src/main/java/dev/meirong/shop/buyerbff/service/GuestCartStore.java`:

```java
package dev.meirong.shop.buyerbff.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.meirong.shop.buyerbff.config.BuyerClientProperties;
import dev.meirong.shop.common.error.BusinessException;
import dev.meirong.shop.common.error.CommonErrorCode;
import dev.meirong.shop.contracts.order.OrderApi;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.redisson.api.RBucket;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Service;

@Service
public class GuestCartStore {

    private static final String GUEST_BUYER_PREFIX = "guest-buyer-";
    private static final String CART_KEY_PREFIX = "buyer:guest:cart:";
    private static final TypeReference<List<OrderApi.CartItemResponse>> CART_LIST_TYPE =
            new TypeReference<>() {};

    private final RedissonClient redissonClient;
    private final ObjectMapper objectMapper;
    private final Duration guestCartTtl;

    public GuestCartStore(RedissonClient redissonClient,
                          ObjectMapper objectMapper,
                          BuyerClientProperties properties) {
        this.redissonClient = redissonClient;
        this.objectMapper = objectMapper;
        this.guestCartTtl = properties.guestCartTtl();
    }

    public boolean isGuestBuyer(String buyerId) {
        return buyerId != null && buyerId.startsWith(GUEST_BUYER_PREFIX);
    }

    public OrderApi.CartView listCart(String buyerId) {
        return toCartView(loadItems(buyerId));
    }

    public OrderApi.CartItemResponse addToCart(OrderApi.AddToCartRequest request) {
        List<OrderApi.CartItemResponse> items = loadItems(request.buyerId());
        int existingIndex = findItemIndex(items, request.productId());
        OrderApi.CartItemResponse response;
        if (existingIndex >= 0) {
            OrderApi.CartItemResponse existing = items.get(existingIndex);
            response = new OrderApi.CartItemResponse(
                    existing.id(),
                    existing.buyerId(),
                    existing.productId(),
                    existing.productName(),
                    existing.productPrice(),
                    existing.sellerId(),
                    existing.quantity() + request.quantity(),
                    existing.createdAt());
            items.set(existingIndex, response);
        } else {
            response = new OrderApi.CartItemResponse(
                    UUID.randomUUID(),
                    request.buyerId(),
                    request.productId(),
                    request.productName(),
                    request.productPrice(),
                    request.sellerId(),
                    request.quantity(),
                    Instant.now());
            items.add(response);
        }
        saveItems(request.buyerId(), items);
        return response;
    }

    public OrderApi.CartItemResponse updateCart(OrderApi.UpdateCartRequest request) {
        List<OrderApi.CartItemResponse> items = loadItems(request.buyerId());
        int existingIndex = findItemIndex(items, request.productId());
        if (existingIndex < 0) {
            throw new BusinessException(CommonErrorCode.NOT_FOUND, "Cart item not found");
        }
        OrderApi.CartItemResponse existing = items.get(existingIndex);
        if (request.quantity() <= 0) {
            items.remove(existingIndex);
            saveItems(request.buyerId(), items);
            return existing;
        }
        OrderApi.CartItemResponse updated = new OrderApi.CartItemResponse(
                existing.id(),
                existing.buyerId(),
                existing.productId(),
                existing.productName(),
                existing.productPrice(),
                existing.sellerId(),
                request.quantity(),
                existing.createdAt());
        items.set(existingIndex, updated);
        saveItems(request.buyerId(), items);
        return updated;
    }

    public void removeFromCart(OrderApi.RemoveFromCartRequest request) {
        List<OrderApi.CartItemResponse> items = loadItems(request.buyerId());
        items.removeIf(item -> item.productId().equals(request.productId()));
        saveItems(request.buyerId(), items);
    }

    public void clearCart(String buyerId) {
        requireGuestBuyer(buyerId);
        redissonClient.getBucket(cartKey(buyerId)).delete();
    }

    private OrderApi.CartView toCartView(List<OrderApi.CartItemResponse> items) {
        BigDecimal subtotal = items.stream()
                .map(item -> item.productPrice().multiply(BigDecimal.valueOf(item.quantity())))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        return new OrderApi.CartView(List.copyOf(items), subtotal);
    }

    private List<OrderApi.CartItemResponse> loadItems(String buyerId) {
        requireGuestBuyer(buyerId);
        RBucket<String> bucket = redissonClient.getBucket(cartKey(buyerId));
        String payload = bucket.get();
        if (payload == null || payload.isBlank()) {
            return new ArrayList<>();
        }
        try {
            List<OrderApi.CartItemResponse> items = objectMapper.readValue(payload, CART_LIST_TYPE);
            bucket.expire(guestCartTtl);
            return new ArrayList<>(items);
        } catch (IOException exception) {
            throw new BusinessException(CommonErrorCode.INTERNAL_ERROR,
                    "Failed to read guest cart for " + buyerId, exception);
        }
    }

    private void saveItems(String buyerId, List<OrderApi.CartItemResponse> items) {
        requireGuestBuyer(buyerId);
        RBucket<String> bucket = redissonClient.getBucket(cartKey(buyerId));
        if (items.isEmpty()) {
            bucket.delete();
            return;
        }
        try {
            bucket.set(objectMapper.writeValueAsString(items), guestCartTtl.toMillis(), TimeUnit.MILLISECONDS);
        } catch (IOException exception) {
            throw new BusinessException(CommonErrorCode.INTERNAL_ERROR,
                    "Failed to store guest cart for " + buyerId, exception);
        }
    }

    private int findItemIndex(List<OrderApi.CartItemResponse> items, String productId) {
        for (int i = 0; i < items.size(); i++) {
            if (items.get(i).productId().equals(productId)) {
                return i;
            }
        }
        return -1;
    }

    private void requireGuestBuyer(String buyerId) {
        if (!isGuestBuyer(buyerId)) {
            throw new BusinessException(CommonErrorCode.FORBIDDEN,
                    "Guest cart only supports guest buyer identities");
        }
    }

    private String cartKey(String buyerId) {
        return CART_KEY_PREFIX + buyerId;
    }
}
```

- [ ] **Step 3: Compile buyer-bff to verify**

Run: `./mvnw -pl services/buyer-bff -am compile -DskipTests -q`
Expected: BUILD SUCCESS

- [ ] **Step 4: Run buyer-bff tests**

Run: `./mvnw -pl services/buyer-bff -am test -q`
Expected: All tests pass. `GuestCartStore` is mocked in `BuyerAggregationServiceTest` (line 62), so no test changes needed.

- [ ] **Step 5: Commit**

```bash
git add services/buyer-bff/pom.xml services/buyer-bff/src/main/java/dev/meirong/shop/buyerbff/service/GuestCartStore.java
git commit -m "refactor(buyer-bff): migrate GuestCartStore from StringRedisTemplate to Redisson RBucket

Replace spring-boot-starter-data-redis with redisson-spring-boot-starter.
GuestCartStore now uses RedissonClient.getBucket() for all key-value
operations instead of StringRedisTemplate.opsForValue().

Co-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>"
```

---

## Task 2: Redis → Redisson — auth-server OtpChallengeService

**Files:**
- Modify: `services/auth-server/pom.xml`
- Modify: `services/auth-server/src/main/java/dev/meirong/shop/authserver/service/OtpChallengeService.java`

- [ ] **Step 1: Replace Redis dependency in auth-server pom.xml**

In `services/auth-server/pom.xml`, find the `spring-boot-starter-data-redis` dependency and replace:

```xml
<!-- REMOVE -->
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-data-redis</artifactId>
</dependency>

<!-- ADD -->
<dependency>
    <groupId>org.redisson</groupId>
    <artifactId>redisson-spring-boot-starter</artifactId>
</dependency>
```

- [ ] **Step 2: Migrate OtpChallengeService to Redisson**

Replace the full content of `services/auth-server/src/main/java/dev/meirong/shop/authserver/service/OtpChallengeService.java`:

```java
package dev.meirong.shop.authserver.service;

import dev.meirong.shop.authserver.config.AuthOtpProperties;
import dev.meirong.shop.authserver.domain.UserAccountEntity;
import dev.meirong.shop.authserver.domain.UserAccountRepository;
import dev.meirong.shop.common.error.BusinessException;
import dev.meirong.shop.common.error.CommonErrorCode;
import dev.meirong.shop.contracts.auth.AuthApi;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.concurrent.TimeUnit;
import org.redisson.api.RAtomicLong;
import org.redisson.api.RBucket;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Service;

@Service
public class OtpChallengeService {

    private final SecureRandom secureRandom = new SecureRandom();
    private final RedissonClient redissonClient;
    private final AuthOtpProperties properties;
    private final SmsGateway smsGateway;
    private final UserAccountRepository userAccountRepository;
    private final BuyerAccountProvisioningService buyerAccountProvisioningService;
    private final JwtTokenService jwtTokenService;

    public OtpChallengeService(RedissonClient redissonClient,
                               AuthOtpProperties properties,
                               SmsGateway smsGateway,
                               UserAccountRepository userAccountRepository,
                               BuyerAccountProvisioningService buyerAccountProvisioningService,
                               JwtTokenService jwtTokenService) {
        this.redissonClient = redissonClient;
        this.properties = properties;
        this.smsGateway = smsGateway;
        this.userAccountRepository = userAccountRepository;
        this.buyerAccountProvisioningService = buyerAccountProvisioningService;
        this.jwtTokenService = jwtTokenService;
    }

    public AuthApi.OtpSendResponse sendOtp(AuthApi.OtpSendRequest request) {
        String phone = request.phoneNumber();
        RBucket<String> cooldown = redissonClient.getBucket(cooldownKey(phone));
        if (cooldown.isExists()) {
            throw new BusinessException(CommonErrorCode.VALIDATION_ERROR, "OTP cooldown active");
        }
        RAtomicLong daily = redissonClient.getAtomicLong(dailyKey(phone));
        long dailyCount = daily.incrementAndGet();
        if (dailyCount == 1L) {
            daily.expire(Duration.ofDays(1));
        }
        if (dailyCount > properties.dailyLimit()) {
            throw new BusinessException(CommonErrorCode.FORBIDDEN, "OTP daily limit exceeded");
        }
        String otp = String.format("%06d", secureRandom.nextInt(1_000_000));
        RBucket<String> code = redissonClient.getBucket(codeKey(phone));
        code.set(otp, properties.codeTtl().toMillis(), TimeUnit.MILLISECONDS);
        cooldown.set("1", properties.cooldownTtl().toMillis(), TimeUnit.MILLISECONDS);
        smsGateway.send(phone, localizedMessage(otp, request.locale()));
        return new AuthApi.OtpSendResponse((int) properties.codeTtl().toSeconds(), (int) properties.cooldownTtl().toSeconds());
    }

    public AuthApi.TokenResponse verifyOtp(AuthApi.OtpVerifyRequest request) {
        String phone = request.phoneNumber();
        RBucket<String> lockout = redissonClient.getBucket(lockoutKey(phone));
        if (lockout.isExists()) {
            throw new BusinessException(CommonErrorCode.FORBIDDEN, "OTP verification locked");
        }
        RBucket<String> code = redissonClient.getBucket(codeKey(phone));
        String expectedCode = code.get();
        if (expectedCode == null) {
            throw new BusinessException(CommonErrorCode.VALIDATION_ERROR, "OTP expired");
        }
        if (!expectedCode.equals(request.otp())) {
            RAtomicLong attempts = redissonClient.getAtomicLong(attemptKey(phone));
            long attemptCount = attempts.incrementAndGet();
            if (attemptCount == 1L) {
                attempts.expire(properties.lockoutTtl());
            }
            if (attemptCount >= properties.maxAttempts()) {
                lockout.set("1", properties.lockoutTtl().toMillis(), TimeUnit.MILLISECONDS);
                throw new BusinessException(CommonErrorCode.FORBIDDEN, "OTP verification locked");
            }
            throw new BusinessException(CommonErrorCode.VALIDATION_ERROR, "OTP invalid");
        }
        code.delete();
        redissonClient.getAtomicLong(attemptKey(phone)).delete();
        UserAccountEntity account = userAccountRepository.findByPhoneNumber(phone).orElse(null);
        boolean newUser = false;
        if (account == null) {
            account = buyerAccountProvisioningService.provisionPhoneBuyer(phone);
            account.setPhoneNumber(phone);
            newUser = true;
        }
        return jwtTokenService.issueToken(account, newUser);
    }

    private String localizedMessage(String otp, String locale) {
        if ("zh-CN".equalsIgnoreCase(locale)) {
            return "您的 Meirong Shop 验证码：" + otp + "，5 分钟内有效，请勿泄露。";
        }
        return "Your Meirong Shop verification code: " + otp + ". Valid for 5 minutes.";
    }

    private String codeKey(String phone) { return "otp:" + phone + ":code"; }
    private String cooldownKey(String phone) { return "otp:" + phone + ":cooldown"; }
    private String attemptKey(String phone) { return "otp:" + phone + ":attempt"; }
    private String lockoutKey(String phone) { return "otp:" + phone + ":lockout"; }
    private String dailyKey(String phone) { return "otp:" + phone + ":daily"; }
}
```

- [ ] **Step 3: Compile and test auth-server**

Run: `./mvnw -pl services/auth-server -am test -q`
Expected: All tests pass. Tests that mock `StringRedisTemplate` for OTP need updating — `OtpChallengeService` now injects `RedissonClient`.

- [ ] **Step 4: Commit**

```bash
git add services/auth-server/pom.xml services/auth-server/src/main/java/dev/meirong/shop/authserver/service/OtpChallengeService.java
git commit -m "refactor(auth-server): migrate OtpChallengeService from StringRedisTemplate to Redisson

Use RBucket for key-value OTP storage and RAtomicLong for counters.
Replaces opsForValue().set/get/increment/hasKey patterns with native
Redisson types for consistent Redis client usage across services.

Co-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>"
```

---

## Task 3: Redis → Redisson — activity-service AntiCheatGuard + RedEnvelopePlugin

**Files:**
- Modify: `services/activity-service/pom.xml`
- Modify: `services/activity-service/src/main/java/dev/meirong/shop/activity/service/AntiCheatGuard.java`
- Modify: `services/activity-service/src/main/java/dev/meirong/shop/activity/engine/RedEnvelopePlugin.java`

- [ ] **Step 1: Replace Redis dependency in activity-service pom.xml**

In `services/activity-service/pom.xml`, replace `spring-boot-starter-data-redis` with `redisson-spring-boot-starter`.

```xml
<!-- REMOVE -->
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-data-redis</artifactId>
</dependency>

<!-- ADD -->
<dependency>
    <groupId>org.redisson</groupId>
    <artifactId>redisson-spring-boot-starter</artifactId>
</dependency>
```

- [ ] **Step 2: Migrate AntiCheatGuard to Redisson**

Replace the full content of `services/activity-service/src/main/java/dev/meirong/shop/activity/service/AntiCheatGuard.java`:

```java
package dev.meirong.shop.activity.service;

import dev.meirong.shop.activity.config.ActivityProperties;
import dev.meirong.shop.activity.domain.ActivityGame;
import dev.meirong.shop.common.error.BusinessException;
import dev.meirong.shop.common.error.CommonErrorCode;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Duration;
import org.redisson.api.RAtomicLong;
import org.redisson.api.RBucket;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Component;

@Component
public class AntiCheatGuard {

    private final RedissonClient redissonClient;
    private final ActivityProperties properties;
    private final MeterRegistry meterRegistry;

    public AntiCheatGuard(RedissonClient redissonClient,
                          ActivityProperties properties,
                          MeterRegistry meterRegistry) {
        this.redissonClient = redissonClient;
        this.properties = properties;
        this.meterRegistry = meterRegistry;
    }

    public void check(ActivityGame game, String buyerId, String ipAddress, String deviceFingerprint) {
        if (buyerId == null || buyerId.isBlank()) {
            return;
        }
        checkPlayerRateLimit(game.getId(), buyerId);
        checkIpRateLimit(game.getId(), ipAddress);
        checkDeviceReuse(game.getId(), buyerId, deviceFingerprint);
    }

    private void checkPlayerRateLimit(String gameId, String buyerId) {
        long count = incrementWithinWindow("activity:ac:player:%s:%s".formatted(gameId, buyerId));
        if (count > properties.antiCheat().playerRequestsPerWindow()) {
            recordBlock("player_rate_limit");
            throw new BusinessException(CommonErrorCode.TOO_MANY_REQUESTS,
                    "Too many participation attempts for this player");
        }
    }

    private void checkIpRateLimit(String gameId, String ipAddress) {
        String normalizedIp = normalizeIp(ipAddress);
        if (normalizedIp == null) {
            return;
        }
        long count = incrementWithinWindow("activity:ac:ip:%s:%s".formatted(gameId, normalizedIp));
        if (count > properties.antiCheat().ipRequestsPerWindow()) {
            recordBlock("ip_rate_limit");
            throw new BusinessException(CommonErrorCode.TOO_MANY_REQUESTS,
                    "Too many participation attempts from this IP");
        }
    }

    private void checkDeviceReuse(String gameId, String buyerId, String deviceFingerprint) {
        if (!properties.antiCheat().deviceFingerprintEnabled()
                || deviceFingerprint == null
                || deviceFingerprint.isBlank()) {
            return;
        }
        String key = "activity:ac:device:%s:%s".formatted(gameId, deviceFingerprint);
        RBucket<String> bucket = redissonClient.getBucket(key);
        String existingPlayer = bucket.get();
        if (existingPlayer == null) {
            bucket.set(buyerId, Duration.ofHours(properties.antiCheat().deviceFingerprintTtlHours()));
            return;
        }
        if (!existingPlayer.equals(buyerId)) {
            recordBlock("device_reuse");
            throw new BusinessException(CommonErrorCode.FORBIDDEN,
                    "Device fingerprint is already bound to another participant");
        }
    }

    private long incrementWithinWindow(String key) {
        RAtomicLong counter = redissonClient.getAtomicLong(key);
        long count = counter.incrementAndGet();
        if (count == 1L) {
            counter.expire(Duration.ofSeconds(properties.antiCheat().windowSeconds()));
        }
        return count;
    }

    private void recordBlock(String reason) {
        meterRegistry.counter("activity_anti_cheat_blocked_total", "reason", reason).increment();
    }

    private String normalizeIp(String ipAddress) {
        if (ipAddress == null || ipAddress.isBlank()) {
            return null;
        }
        return ipAddress.split(",")[0].trim();
    }
}
```

- [ ] **Step 3: Migrate RedEnvelopePlugin to Redisson**

Replace the full content of `services/activity-service/src/main/java/dev/meirong/shop/activity/engine/RedEnvelopePlugin.java`:

```java
package dev.meirong.shop.activity.engine;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.meirong.shop.activity.domain.ActivityGame;
import dev.meirong.shop.activity.domain.ActivityParticipationRepository;
import dev.meirong.shop.activity.domain.GameType;
import dev.meirong.shop.activity.domain.PrizeType;
import dev.meirong.shop.common.error.BusinessException;
import dev.meirong.shop.common.error.CommonErrorCode;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.redisson.api.RList;
import org.redisson.api.RMap;
import org.redisson.api.RScript;
import org.redisson.api.RedissonClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class RedEnvelopePlugin implements GamePlugin {

    private static final Logger log = LoggerFactory.getLogger(RedEnvelopePlugin.class);
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final int MIN_PACKET_CENTS = 1;

    private static final String GRAB_PACKET_LUA = """
            if redis.call('HEXISTS', KEYS[2], ARGV[1]) == 1 then
              return {-1, redis.call('HGET', KEYS[2], ARGV[1])}
            end
            local amount = redis.call('LPOP', KEYS[1])
            if not amount then
              return {-2}
            end
            redis.call('HSET', KEYS[2], ARGV[1], amount)
            return {1, amount}
            """;

    private final RedissonClient redissonClient;
    private final ObjectMapper objectMapper;
    private final ActivityParticipationRepository participationRepository;

    public RedEnvelopePlugin(RedissonClient redissonClient,
                             ObjectMapper objectMapper,
                             ActivityParticipationRepository participationRepository) {
        this.redissonClient = redissonClient;
        this.objectMapper = objectMapper;
        this.participationRepository = participationRepository;
    }

    @Override
    public GameType supportedType() {
        return GameType.RED_ENVELOPE;
    }

    @Override
    public void initialize(ActivityGame game) {
        EnvelopeConfig config = parseConfig(game.getConfig());
        List<String> packetValues = generatePackets(config.totalAmount(), config.packetCount()).stream()
                .map(amount -> amount.setScale(2, RoundingMode.UNNECESSARY).toPlainString())
                .toList();
        String packetsKey = packetsKey(game.getId());
        String claimsKey = claimsKey(game.getId());
        RList<String> packetsList = redissonClient.getList(packetsKey);
        RMap<String, String> claimsMap = redissonClient.getMap(claimsKey);
        packetsList.delete();
        claimsMap.delete();
        if (!packetValues.isEmpty()) {
            packetsList.addAll(packetValues);
        }
        log.info("Initialized red envelope game {} with {} packets totaling {}",
                game.getId(), config.packetCount(), config.totalAmount());
    }

    @Override
    public ParticipateResult participate(ParticipateContext ctx) {
        List<Object> result = redissonClient.getScript().eval(
                RScript.Mode.READ_WRITE,
                GRAB_PACKET_LUA,
                RScript.ReturnType.MULTI,
                List.of(packetsKey(ctx.gameId()), claimsKey(ctx.gameId())),
                ctx.buyerId());
        if (result == null || result.isEmpty()) {
            throw new BusinessException(CommonErrorCode.INTERNAL_ERROR, "Red envelope claim returned empty result");
        }
        int code = toInt(result.get(0));
        return switch (code) {
            case -1 -> ParticipateResult.miss("You have already claimed this red envelope");
            case -2 -> ParticipateResult.miss("All red envelopes have been claimed");
            case 1 -> {
                if (result.size() < 2) {
                    throw new BusinessException(CommonErrorCode.INTERNAL_ERROR,
                            "Red envelope claim succeeded without amount");
                }
                BigDecimal amount = toBigDecimal(result.get(1));
                String animationHint = "{\"amount\":\"%s\"}".formatted(amount.toPlainString());
                yield new ParticipateResult(true, null, "Red Envelope", PrizeType.POINTS, amount,
                        animationHint, "You claimed %s points".formatted(amount.toPlainString()));
            }
            default -> throw new BusinessException(CommonErrorCode.INTERNAL_ERROR,
                    "Unexpected red envelope result code: " + code);
        };
    }

    @Override
    public void settle(ActivityGame game) {
        RMap<String, String> claimsMap = redissonClient.getMap(claimsKey(game.getId()));
        long redisClaimed = claimsMap.size();
        long persistedWins = participationRepository.countWinningParticipationsByGameId(game.getId());
        if (redisClaimed != persistedWins) {
            log.warn("Red envelope reconcile mismatch: game={}, redisClaimed={}, persistedWins={}",
                    game.getId(), redisClaimed, persistedWins);
        }
        redissonClient.getList(packetsKey(game.getId())).delete();
        claimsMap.delete();
    }

    private EnvelopeConfig parseConfig(String configJson) {
        if (configJson == null || configJson.isBlank()) {
            throw new BusinessException(CommonErrorCode.VALIDATION_ERROR,
                    "Red envelope config must define packet_count and total_amount");
        }
        try {
            JsonNode config = objectMapper.readTree(configJson);
            int packetCount = positiveInt(config, "packet_count", "packetCount");
            BigDecimal totalAmount = positiveAmount(config, "total_amount", "totalAmount");
            return new EnvelopeConfig(packetCount, totalAmount);
        } catch (BusinessException exception) {
            throw exception;
        } catch (IOException exception) {
            throw new BusinessException(CommonErrorCode.VALIDATION_ERROR,
                    "Invalid red envelope config", exception);
        }
    }

    private int positiveInt(JsonNode config, String primaryField, String fallbackField) {
        JsonNode node = config.path(primaryField);
        if (node.isMissingNode()) {
            node = config.path(fallbackField);
        }
        int value = node.asInt(0);
        if (value <= 0) {
            throw new BusinessException(CommonErrorCode.VALIDATION_ERROR,
                    primaryField + " must be greater than zero");
        }
        return value;
    }

    private BigDecimal positiveAmount(JsonNode config, String primaryField, String fallbackField) {
        JsonNode node = config.path(primaryField);
        if (node.isMissingNode()) {
            node = config.path(fallbackField);
        }
        if (node.isMissingNode() || node.isNull()) {
            throw new BusinessException(CommonErrorCode.VALIDATION_ERROR,
                    primaryField + " must be provided");
        }
        BigDecimal value = node.decimalValue();
        if (value.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException(CommonErrorCode.VALIDATION_ERROR,
                    primaryField + " must be greater than zero");
        }
        try {
            return value.setScale(2, RoundingMode.UNNECESSARY);
        } catch (ArithmeticException exception) {
            throw new BusinessException(CommonErrorCode.VALIDATION_ERROR,
                    primaryField + " must use at most 2 decimal places", exception);
        }
    }

    private List<BigDecimal> generatePackets(BigDecimal totalAmount, int packetCount) {
        int remainingCents = totalAmount.movePointRight(2).intValueExact();
        if (remainingCents < packetCount * MIN_PACKET_CENTS) {
            throw new BusinessException(CommonErrorCode.VALIDATION_ERROR,
                    "total_amount must be at least 0.01 per packet");
        }
        List<BigDecimal> packets = new ArrayList<>(packetCount);
        for (int remainingPackets = packetCount; remainingPackets > 1; remainingPackets--) {
            int maxByAverage = Math.max(MIN_PACKET_CENTS, (remainingCents * 2) / remainingPackets);
            int maxAllowed = remainingCents - (remainingPackets - 1) * MIN_PACKET_CENTS;
            int upperBound = Math.max(MIN_PACKET_CENTS, Math.min(maxByAverage, maxAllowed));
            int packetCents = upperBound == MIN_PACKET_CENTS
                    ? MIN_PACKET_CENTS
                    : MIN_PACKET_CENTS + RANDOM.nextInt(upperBound - MIN_PACKET_CENTS + 1);
            packets.add(BigDecimal.valueOf(packetCents, 2));
            remainingCents -= packetCents;
        }
        packets.add(BigDecimal.valueOf(remainingCents, 2));
        Collections.shuffle(packets, RANDOM);
        return packets;
    }

    private static int toInt(Object value) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value instanceof String str) {
            return Integer.parseInt(str);
        }
        return Integer.parseInt(String.valueOf(value));
    }

    private static BigDecimal toBigDecimal(Object value) {
        if (value instanceof String str) {
            return new BigDecimal(str);
        }
        return new BigDecimal(String.valueOf(value));
    }

    private static String packetsKey(String gameId) {
        return "re:packets:" + gameId;
    }

    private static String claimsKey(String gameId) {
        return "re:claims:" + gameId;
    }

    private record EnvelopeConfig(int packetCount, BigDecimal totalAmount) {}
}
```

- [ ] **Step 4: Compile and test activity-service**

Run: `./mvnw -pl services/activity-service -am test -q`
Expected: All tests pass. Update mocks in test files if needed (replace `StringRedisTemplate` mocks with `RedissonClient` mocks).

- [ ] **Step 5: Commit**

```bash
git add services/activity-service/
git commit -m "refactor(activity-service): migrate AntiCheatGuard and RedEnvelopePlugin to Redisson

AntiCheatGuard: Replace StringRedisTemplate opsForValue/increment/expire
with RedissonClient RBucket and RAtomicLong.

RedEnvelopePlugin: Replace DefaultRedisScript + StringRedisTemplate.execute
with RedissonClient RScript.eval, RList for packet queue, and RMap for
claims tracking.

Co-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>"
```

---

## Task 4: Redis → Redisson — api-gateway CanaryRequestPredicates

**Files:**
- Modify: `services/api-gateway/pom.xml`
- Modify: `services/api-gateway/src/main/java/dev/meirong/shop/gateway/predicate/CanaryRequestPredicates.java`

- [ ] **Step 1: Replace Redis dependency in api-gateway pom.xml**

In `services/api-gateway/pom.xml`, replace `spring-boot-starter-data-redis` with `redisson-spring-boot-starter`.

**Important:** `RateLimitingFilter` continues to use `StringRedisTemplate` for its Lua token-bucket script (still available via Redisson's Spring Data Redis bridge). No changes needed to `RateLimitingFilter.java`.

```xml
<!-- REMOVE -->
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-data-redis</artifactId>
</dependency>

<!-- ADD -->
<dependency>
    <groupId>org.redisson</groupId>
    <artifactId>redisson-spring-boot-starter</artifactId>
</dependency>
```

- [ ] **Step 2: Migrate CanaryRequestPredicates to Redisson RSet**

Replace the full content of `services/api-gateway/src/main/java/dev/meirong/shop/gateway/predicate/CanaryRequestPredicates.java`:

```java
package dev.meirong.shop.gateway.predicate;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import java.lang.reflect.Method;
import java.time.Duration;
import java.util.Arrays;
import java.util.Collection;
import java.util.concurrent.atomic.AtomicReference;
import org.redisson.api.RSet;
import org.redisson.api.RedissonClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.gateway.server.mvc.predicate.PredicateSupplier;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.function.RequestPredicate;

@Component
public class CanaryRequestPredicates implements PredicateSupplier {

    private static final Logger log = LoggerFactory.getLogger(CanaryRequestPredicates.class);
    private static final String BUYER_ID = "X-Buyer-Id";
    private static final String KEY_PREFIX = "gateway:canary:";
    private static final int CACHE_TTL_SECONDS = 10;

    private static final AtomicReference<RedissonClient> redissonRef = new AtomicReference<>();
    static final Cache<String, Boolean> localCache = Caffeine.newBuilder()
            .maximumSize(10_000)
            .expireAfterWrite(Duration.ofSeconds(CACHE_TTL_SECONDS))
            .build();

    public CanaryRequestPredicates(RedissonClient redissonClient) {
        CanaryRequestPredicates.redissonRef.set(redissonClient);
    }

    public static RequestPredicate canary(String routeId) {
        return request -> {
            String buyerId = request.servletRequest().getHeader(BUYER_ID);
            if (buyerId == null || buyerId.isBlank() || redissonRef.get() == null) {
                return false;
            }
            String cacheKey = routeId + ':' + buyerId;
            Boolean cached = localCache.getIfPresent(cacheKey);
            if (cached != null) {
                return cached;
            }
            try {
                RSet<String> canarySet = redissonRef.get().getSet(KEY_PREFIX + routeId);
                boolean member = canarySet.contains(buyerId);
                localCache.put(cacheKey, member);
                return member;
            } catch (Exception exception) {
                log.warn("Canary lookup failed for route {} and buyer {}, routing to stable: {}",
                        routeId, buyerId, exception.getMessage());
                return false;
            }
        };
    }

    @Override
    public Collection<Method> get() {
        return Arrays.stream(CanaryRequestPredicates.class.getMethods())
                .filter(method -> method.getReturnType().equals(RequestPredicate.class))
                .toList();
    }
}
```

- [ ] **Step 3: Compile and test api-gateway**

Run: `./mvnw -pl services/api-gateway -am test -q`
Expected: All tests pass. `RateLimitingFilter` tests should still pass since `StringRedisTemplate` is available via `redisson-spring-boot-starter`. Update `CanaryRequestPredicatesTest` mocks if needed (replace `StringRedisTemplate` mock with `RedissonClient` mock).

- [ ] **Step 4: Commit**

```bash
git add services/api-gateway/
git commit -m "refactor(api-gateway): migrate CanaryRequestPredicates to Redisson RSet

Replace StringRedisTemplate.opsForSet().isMember() with
RedissonClient.getSet().contains() for canary routing membership check.
RateLimitingFilter intentionally kept on StringRedisTemplate (Lua script).

Co-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>"
```

---

## Task 5: Redis → Redisson — full verification

- [ ] **Step 1: Run full Maven verify for all 4 modified services**

Run: `./mvnw -B -ntp verify -pl services/buyer-bff,services/auth-server,services/activity-service,services/api-gateway -am`
Expected: BUILD SUCCESS

- [ ] **Step 2: Run architecture tests**

Run: `make arch-test`
Expected: All 19 ArchUnit rules pass

---

## Task 6: @HttpExchange — buyer-bff client interfaces

**Files:**
- Create: `services/buyer-bff/src/main/java/dev/meirong/shop/buyerbff/client/ProfileServiceClient.java`
- Create: `services/buyer-bff/src/main/java/dev/meirong/shop/buyerbff/client/ProfileInternalServiceClient.java`
- Create: `services/buyer-bff/src/main/java/dev/meirong/shop/buyerbff/client/WalletServiceClient.java`
- Create: `services/buyer-bff/src/main/java/dev/meirong/shop/buyerbff/client/PromotionServiceClient.java`
- Create: `services/buyer-bff/src/main/java/dev/meirong/shop/buyerbff/client/PromotionInternalServiceClient.java`
- Create: `services/buyer-bff/src/main/java/dev/meirong/shop/buyerbff/client/MarketplaceServiceClient.java`
- Create: `services/buyer-bff/src/main/java/dev/meirong/shop/buyerbff/client/OrderServiceClient.java`
- Create: `services/buyer-bff/src/main/java/dev/meirong/shop/buyerbff/client/LoyaltyServiceClient.java`

Each interface maps to the exact contract API paths and request/response types used in `BuyerAggregationService`.

- [ ] **Step 1: Create ProfileServiceClient**

Create `services/buyer-bff/src/main/java/dev/meirong/shop/buyerbff/client/ProfileServiceClient.java`:

```java
package dev.meirong.shop.buyerbff.client;

import dev.meirong.shop.common.api.ApiResponse;
import dev.meirong.shop.contracts.profile.ProfileApi;
import org.springframework.web.service.annotation.HttpExchange;
import org.springframework.web.service.annotation.PostExchange;

@HttpExchange
public interface ProfileServiceClient {

    @PostExchange(ProfileApi.GET)
    ApiResponse<ProfileApi.ProfileResponse> getProfile(ProfileApi.GetProfileRequest request);

    @PostExchange(ProfileApi.UPDATE)
    ApiResponse<ProfileApi.ProfileResponse> updateProfile(ProfileApi.UpdateProfileRequest request);

    @PostExchange(ProfileApi.SELLER_STOREFRONT)
    ApiResponse<ProfileApi.SellerStorefrontResponse> getSellerStorefront(ProfileApi.GetProfileRequest request);
}
```

- [ ] **Step 2: Create ProfileInternalServiceClient**

Create `services/buyer-bff/src/main/java/dev/meirong/shop/buyerbff/client/ProfileInternalServiceClient.java`:

```java
package dev.meirong.shop.buyerbff.client;

import dev.meirong.shop.common.api.ApiResponse;
import dev.meirong.shop.contracts.profile.ProfileInternalApi;
import org.springframework.web.service.annotation.HttpExchange;
import org.springframework.web.service.annotation.PostExchange;

@HttpExchange
public interface ProfileInternalServiceClient {

    @PostExchange(ProfileInternalApi.INVITE_STATS)
    ApiResponse<ProfileInternalApi.InviteStatsResponse> getInviteStats(ProfileInternalApi.InviteStatsRequest request);
}
```

- [ ] **Step 3: Create WalletServiceClient**

Create `services/buyer-bff/src/main/java/dev/meirong/shop/buyerbff/client/WalletServiceClient.java`:

```java
package dev.meirong.shop.buyerbff.client;

import dev.meirong.shop.common.api.ApiResponse;
import dev.meirong.shop.contracts.wallet.WalletApi;
import java.util.List;
import org.springframework.web.service.annotation.GetExchange;
import org.springframework.web.service.annotation.HttpExchange;
import org.springframework.web.service.annotation.PostExchange;

@HttpExchange
public interface WalletServiceClient {

    @PostExchange(WalletApi.GET)
    ApiResponse<WalletApi.WalletAccountResponse> getWallet(WalletApi.GetWalletRequest request);

    @PostExchange(WalletApi.DEPOSIT)
    ApiResponse<WalletApi.TransactionResponse> deposit(WalletApi.DepositRequest request);

    @PostExchange(WalletApi.WITHDRAW)
    ApiResponse<WalletApi.TransactionResponse> withdraw(WalletApi.WithdrawRequest request);

    @GetExchange(WalletApi.PAYMENT_METHODS)
    ApiResponse<List<WalletApi.PaymentMethodInfo>> listPaymentMethods();

    @PostExchange(WalletApi.PAYMENT_INTENT)
    ApiResponse<WalletApi.PaymentIntentResponse> createPaymentIntent(WalletApi.CreatePaymentIntentRequest request);

    @PostExchange(WalletApi.PAYMENT_CREATE)
    ApiResponse<WalletApi.TransactionResponse> createPayment(WalletApi.CreatePaymentRequest request);

    @PostExchange(WalletApi.PAYMENT_REFUND)
    ApiResponse<WalletApi.TransactionResponse> refund(WalletApi.CreateRefundRequest request);
}
```

- [ ] **Step 4: Create PromotionServiceClient**

Create `services/buyer-bff/src/main/java/dev/meirong/shop/buyerbff/client/PromotionServiceClient.java`:

```java
package dev.meirong.shop.buyerbff.client;

import dev.meirong.shop.common.api.ApiResponse;
import dev.meirong.shop.contracts.promotion.PromotionApi;
import java.util.List;
import org.springframework.web.service.annotation.HttpExchange;
import org.springframework.web.service.annotation.PostExchange;

@HttpExchange
public interface PromotionServiceClient {

    @PostExchange(PromotionApi.LIST)
    ApiResponse<PromotionApi.OffersView> listOffers(PromotionApi.ListOffersRequest request);

    @PostExchange(PromotionApi.COUPON_LIST)
    ApiResponse<List<PromotionApi.CouponResponse>> listCoupons(PromotionApi.ListCouponsRequest request);

    @PostExchange(PromotionApi.COUPON_VALIDATE)
    ApiResponse<PromotionApi.CouponValidationResponse> validateCoupon(PromotionApi.ValidateCouponRequest request);

    @PostExchange(PromotionApi.COUPON_APPLY)
    ApiResponse<Void> applyCoupon(PromotionApi.ApplyCouponRequest request);
}
```

- [ ] **Step 5: Create PromotionInternalServiceClient**

Create `services/buyer-bff/src/main/java/dev/meirong/shop/buyerbff/client/PromotionInternalServiceClient.java`:

```java
package dev.meirong.shop.buyerbff.client;

import dev.meirong.shop.common.api.ApiResponse;
import dev.meirong.shop.contracts.promotion.PromotionInternalApi;
import org.springframework.web.service.annotation.HttpExchange;
import org.springframework.web.service.annotation.PostExchange;

@HttpExchange
public interface PromotionInternalServiceClient {

    @PostExchange(PromotionInternalApi.BUYER_AVAILABLE_COUPONS)
    ApiResponse<PromotionInternalApi.BuyerCouponsResponse> getBuyerAvailableCoupons(
            PromotionInternalApi.BuyerCouponsRequest request);
}
```

- [ ] **Step 6: Create MarketplaceServiceClient**

Create `services/buyer-bff/src/main/java/dev/meirong/shop/buyerbff/client/MarketplaceServiceClient.java`:

```java
package dev.meirong.shop.buyerbff.client;

import dev.meirong.shop.common.api.ApiResponse;
import dev.meirong.shop.contracts.marketplace.MarketplaceApi;
import java.util.List;
import java.util.Map;
import org.springframework.web.service.annotation.HttpExchange;
import org.springframework.web.service.annotation.PostExchange;

@HttpExchange
public interface MarketplaceServiceClient {

    @PostExchange(MarketplaceApi.LIST)
    ApiResponse<MarketplaceApi.ProductsView> listProducts(MarketplaceApi.ListProductsRequest request);

    @PostExchange(MarketplaceApi.GET)
    ApiResponse<MarketplaceApi.ProductResponse> getProduct(MarketplaceApi.GetProductRequest request);

    @PostExchange(MarketplaceApi.INVENTORY_DEDUCT)
    ApiResponse<Void> deductInventory(MarketplaceApi.DeductInventoryRequest request);

    @PostExchange(MarketplaceApi.INVENTORY_RESTORE)
    ApiResponse<Void> restoreInventory(MarketplaceApi.RestoreInventoryRequest request);

    @PostExchange(MarketplaceApi.CATEGORY_LIST)
    ApiResponse<List<MarketplaceApi.CategoryResponse>> listCategories(Map<String, Object> request);

    @PostExchange(MarketplaceApi.SEARCH)
    ApiResponse<MarketplaceApi.ProductsPageView> searchProducts(MarketplaceApi.SearchProductsRequest request);
}
```

- [ ] **Step 7: Create OrderServiceClient**

Create `services/buyer-bff/src/main/java/dev/meirong/shop/buyerbff/client/OrderServiceClient.java`:

```java
package dev.meirong.shop.buyerbff.client;

import dev.meirong.shop.common.api.ApiResponse;
import dev.meirong.shop.contracts.order.OrderApi;
import java.util.List;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.service.annotation.GetExchange;
import org.springframework.web.service.annotation.HttpExchange;
import org.springframework.web.service.annotation.PostExchange;

@HttpExchange
public interface OrderServiceClient {

    @PostExchange(OrderApi.CART_LIST)
    ApiResponse<OrderApi.CartView> listCart(OrderApi.ListCartRequest request);

    @PostExchange(OrderApi.CART_ADD)
    ApiResponse<OrderApi.CartItemResponse> addToCart(OrderApi.AddToCartRequest request);

    @PostExchange(OrderApi.CART_UPDATE)
    ApiResponse<OrderApi.CartItemResponse> updateCart(OrderApi.UpdateCartRequest request);

    @PostExchange(OrderApi.CART_REMOVE)
    ApiResponse<Void> removeFromCart(OrderApi.RemoveFromCartRequest request);

    @PostExchange(OrderApi.CART_CLEAR)
    ApiResponse<Void> clearCart(OrderApi.ClearCartRequest request);

    @PostExchange(OrderApi.ORDER_LIST)
    ApiResponse<List<OrderApi.OrderResponse>> listOrders(OrderApi.ListOrdersRequest request);

    @PostExchange(OrderApi.ORDER_GET)
    ApiResponse<OrderApi.OrderResponse> getOrder(OrderApi.GetOrderRequest request);

    @PostExchange(OrderApi.ORDER_CANCEL)
    ApiResponse<OrderApi.OrderResponse> cancelOrder(OrderApi.CancelOrderRequest request);

    @PostExchange(OrderApi.CHECKOUT_CREATE)
    ApiResponse<OrderApi.OrderResponse> createOrder(OrderApi.CreateOrderRequest request);

    @PostExchange(OrderApi.GUEST_CHECKOUT)
    ApiResponse<OrderApi.OrderResponse> guestCheckout(OrderApi.GuestCheckoutRequest request);

    @GetExchange(OrderApi.ORDER_TRACK)
    ApiResponse<OrderApi.OrderResponse> trackOrder(@RequestParam("token") String token);
}
```

- [ ] **Step 8: Create LoyaltyServiceClient**

Create `services/buyer-bff/src/main/java/dev/meirong/shop/buyerbff/client/LoyaltyServiceClient.java`:

```java
package dev.meirong.shop.buyerbff.client;

import dev.meirong.shop.common.api.ApiResponse;
import dev.meirong.shop.buyerbff.service.PageResponse;
import dev.meirong.shop.contracts.loyalty.LoyaltyApi;
import java.util.List;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.service.annotation.GetExchange;
import org.springframework.web.service.annotation.HttpExchange;
import org.springframework.web.service.annotation.PostExchange;

@HttpExchange
public interface LoyaltyServiceClient {

    @GetExchange(LoyaltyApi.INTERNAL_BALANCE + "/{buyerId}")
    ApiResponse<LoyaltyApi.AccountResponse> getAccount(@PathVariable String buyerId);

    @PostExchange(LoyaltyApi.CHECKIN)
    ApiResponse<LoyaltyApi.CheckinResponse> checkin();

    @GetExchange(LoyaltyApi.CHECKIN_CALENDAR)
    ApiResponse<List<LoyaltyApi.CheckinResponse>> checkinCalendar(
            @RequestParam("year") int year, @RequestParam("month") int month);

    @GetExchange(LoyaltyApi.TRANSACTIONS)
    ApiResponse<PageResponse<LoyaltyApi.TransactionResponse>> getTransactions(
            @RequestParam("page") int page, @RequestParam("size") int size);

    @GetExchange(LoyaltyApi.REWARDS)
    ApiResponse<List<LoyaltyApi.RewardItemResponse>> listRewards();

    @PostExchange(LoyaltyApi.REDEEM)
    ApiResponse<LoyaltyApi.RedemptionResponse> redeemReward(LoyaltyApi.RedeemRequest request);

    @GetExchange(LoyaltyApi.REDEMPTIONS)
    ApiResponse<PageResponse<LoyaltyApi.RedemptionResponse>> getRedemptions(
            @RequestParam("page") int page, @RequestParam("size") int size);

    @GetExchange(LoyaltyApi.ONBOARDING_TASKS)
    ApiResponse<List<LoyaltyApi.OnboardingTaskResponse>> getOnboardingTasks();

    @PostExchange(LoyaltyApi.INTERNAL_DEDUCT)
    ApiResponse<LoyaltyApi.TransactionResponse> deductPoints(LoyaltyApi.DeductPointsRequest request);

    @PostExchange(LoyaltyApi.INTERNAL_EARN)
    ApiResponse<LoyaltyApi.TransactionResponse> earnPoints(LoyaltyApi.EarnPointsRequest request);
}
```

**Note:** Methods that need `X-Buyer-Id` header (checkin, checkinCalendar, getTransactions, getRedemptions, getOnboardingTasks, redeemReward) will be called through a `LoyaltyServiceClient` proxy built with a `RestClient` that adds the header dynamically. This is configured in Task 7 (BuyerBffConfig).

- [ ] **Step 9: Compile to verify interfaces**

Run: `./mvnw -pl services/buyer-bff -am compile -DskipTests -q`
Expected: BUILD SUCCESS

- [ ] **Step 10: Commit**

```bash
git add services/buyer-bff/src/main/java/dev/meirong/shop/buyerbff/client/
git commit -m "feat(buyer-bff): add @HttpExchange client interfaces for all downstream services

Create 8 declarative HTTP client interfaces:
- ProfileServiceClient, ProfileInternalServiceClient
- WalletServiceClient, PromotionServiceClient, PromotionInternalServiceClient
- MarketplaceServiceClient, OrderServiceClient, LoyaltyServiceClient

Each interface maps to contract API path constants and request/response
types, replacing boilerplate RestClient calls in BuyerAggregationService.

Co-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>"
```

---

## Task 7: @HttpExchange — buyer-bff Config registration + BuyerAggregationService rewrite

**Files:**
- Modify: `services/buyer-bff/src/main/java/dev/meirong/shop/buyerbff/config/BuyerBffConfig.java`
- Modify: `services/buyer-bff/src/main/java/dev/meirong/shop/buyerbff/service/BuyerAggregationService.java`
- Modify: `services/buyer-bff/src/test/java/dev/meirong/shop/buyerbff/service/BuyerAggregationServiceTest.java`

This is the largest task. BuyerBffConfig registers all proxy beans. BuyerAggregationService is rewritten to inject client interfaces and remove the 5 private helper methods (`post`, `get`, `getWithHeader`, `postWithHeader`, `postVoid`).

- [ ] **Step 1: Rewrite BuyerBffConfig to register all client proxies**

Replace the full content of `services/buyer-bff/src/main/java/dev/meirong/shop/buyerbff/config/BuyerBffConfig.java`:

```java
package dev.meirong.shop.buyerbff.config;

import dev.meirong.shop.buyerbff.client.LoyaltyServiceClient;
import dev.meirong.shop.buyerbff.client.MarketplaceServiceClient;
import dev.meirong.shop.buyerbff.client.OrderServiceClient;
import dev.meirong.shop.buyerbff.client.ProfileInternalServiceClient;
import dev.meirong.shop.buyerbff.client.ProfileServiceClient;
import dev.meirong.shop.buyerbff.client.PromotionInternalServiceClient;
import dev.meirong.shop.buyerbff.client.PromotionServiceClient;
import dev.meirong.shop.buyerbff.client.SearchServiceClient;
import dev.meirong.shop.buyerbff.client.WalletServiceClient;
import java.net.http.HttpClient;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.support.RestClientAdapter;
import org.springframework.web.service.invoker.HttpServiceProxyFactory;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(BuyerClientProperties.class)
public class BuyerBffConfig {

    @Bean
    JdkClientHttpRequestFactory jdkClientHttpRequestFactory(BuyerClientProperties properties) {
        HttpClient httpClient = HttpClient.newBuilder()
                .version(properties.httpVersion())
                .connectTimeout(properties.connectTimeout())
                .build();
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(httpClient);
        factory.setReadTimeout(properties.readTimeout());
        return factory;
    }

    @Bean
    RestClient.Builder restClientBuilder(JdkClientHttpRequestFactory factory) {
        return RestClient.builder().requestFactory(factory);
    }

    @Bean
    SearchServiceClient searchServiceClient(BuyerClientProperties properties,
                                            JdkClientHttpRequestFactory factory) {
        return createClient(properties.searchServiceUrl(), factory, SearchServiceClient.class);
    }

    @Bean
    ProfileServiceClient profileServiceClient(BuyerClientProperties properties,
                                              JdkClientHttpRequestFactory factory) {
        return createClient(properties.profileServiceUrl(), factory, ProfileServiceClient.class);
    }

    @Bean
    ProfileInternalServiceClient profileInternalServiceClient(BuyerClientProperties properties,
                                                              JdkClientHttpRequestFactory factory) {
        return createClient(properties.profileServiceUrl(), factory, ProfileInternalServiceClient.class);
    }

    @Bean
    WalletServiceClient walletServiceClient(BuyerClientProperties properties,
                                            JdkClientHttpRequestFactory factory) {
        return createClient(properties.walletServiceUrl(), factory, WalletServiceClient.class);
    }

    @Bean
    PromotionServiceClient promotionServiceClient(BuyerClientProperties properties,
                                                  JdkClientHttpRequestFactory factory) {
        return createClient(properties.promotionServiceUrl(), factory, PromotionServiceClient.class);
    }

    @Bean
    PromotionInternalServiceClient promotionInternalServiceClient(BuyerClientProperties properties,
                                                                  JdkClientHttpRequestFactory factory) {
        return createClient(properties.promotionServiceUrl(), factory, PromotionInternalServiceClient.class);
    }

    @Bean
    MarketplaceServiceClient marketplaceServiceClient(BuyerClientProperties properties,
                                                      JdkClientHttpRequestFactory factory) {
        return createClient(properties.marketplaceServiceUrl(), factory, MarketplaceServiceClient.class);
    }

    @Bean
    OrderServiceClient orderServiceClient(BuyerClientProperties properties,
                                          JdkClientHttpRequestFactory factory) {
        return createClient(properties.orderServiceUrl(), factory, OrderServiceClient.class);
    }

    @Bean
    LoyaltyServiceClient loyaltyServiceClient(BuyerClientProperties properties,
                                              JdkClientHttpRequestFactory factory) {
        return createClient(properties.loyaltyServiceUrl(), factory, LoyaltyServiceClient.class);
    }

    private <T> T createClient(String baseUrl, JdkClientHttpRequestFactory factory, Class<T> clientType) {
        RestClient restClient = RestClient.builder()
                .requestFactory(factory)
                .baseUrl(baseUrl)
                .build();
        return HttpServiceProxyFactory
                .builderFor(RestClientAdapter.create(restClient))
                .build()
                .createClient(clientType);
    }
}
```

- [ ] **Step 2: Rewrite BuyerAggregationService to use client interfaces**

Key changes:
1. Replace `RestClient restClient` with 8 client interface fields
2. Remove 5 helper methods: `post`, `postVoid`, `get`, `getWithHeader`, `postWithHeader`
3. Remove `translateDownstreamException` and `resolveCommonErrorCode`
4. Add `requireData(ApiResponse<T>)` helper
5. Each downstream call becomes a one-liner via the client interface
6. `ResilienceHelper` wrapping stays unchanged
7. Keep a `RestClient restClient` field ONLY for loyalty calls that need dynamic `X-Buyer-Id` header injection and for checkout compensation catch-blocks

Each method body changes from:
```java
// Before
return call("profileService", true,
    () -> post(properties.profileServiceUrl() + ProfileApi.GET,
        new ProfileApi.GetProfileRequest(buyerId),
        new ParameterizedTypeReference<ApiResponse<ProfileApi.ProfileResponse>>() {}),
    "Profile service is temporarily unavailable");

// After
return call("profileService", true,
    () -> requireData(profileServiceClient.getProfile(new ProfileApi.GetProfileRequest(buyerId))),
    "Profile service is temporarily unavailable");
```

- [ ] **Step 3: Update BuyerAggregationServiceTest**

Update the test constructor call to inject mock client interfaces instead of `RestClient.Builder`. The `searchProducts_rejectsEmptyHttpExchangePayload` test needs to mock `searchServiceClient.searchProducts(...)` instead of the current approach.

- [ ] **Step 4: Compile and test**

Run: `./mvnw -pl services/buyer-bff -am test -q`
Expected: All tests pass

- [ ] **Step 5: Commit**

```bash
git add services/buyer-bff/
git commit -m "refactor(buyer-bff): replace RestClient boilerplate with @HttpExchange clients

Rewrite BuyerBffConfig to register 9 HttpServiceProxyFactory client beans.
Rewrite BuyerAggregationService to inject client interfaces, eliminating
5 helper methods (post/get/getWithHeader/postWithHeader/postVoid) and
~300 lines of boilerplate. ResilienceHelper wrapping preserved.

Co-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>"
```

---

## Task 8: @HttpExchange — seller-bff client interfaces + rewrite

**Files:**
- Create: `services/seller-bff/src/main/java/dev/meirong/shop/sellerbff/client/ProfileServiceClient.java`
- Create: `services/seller-bff/src/main/java/dev/meirong/shop/sellerbff/client/MarketplaceServiceClient.java`
- Create: `services/seller-bff/src/main/java/dev/meirong/shop/sellerbff/client/OrderServiceClient.java`
- Create: `services/seller-bff/src/main/java/dev/meirong/shop/sellerbff/client/WalletServiceClient.java`
- Create: `services/seller-bff/src/main/java/dev/meirong/shop/sellerbff/client/PromotionServiceClient.java`
- Create: `services/seller-bff/src/main/java/dev/meirong/shop/sellerbff/client/SearchServiceClient.java`
- Modify: `services/seller-bff/src/main/java/dev/meirong/shop/sellerbff/config/SellerBffConfig.java`
- Modify: `services/seller-bff/src/main/java/dev/meirong/shop/sellerbff/service/SellerAggregationService.java`

- [ ] **Step 1: Create seller-bff ProfileServiceClient**

Create `services/seller-bff/src/main/java/dev/meirong/shop/sellerbff/client/ProfileServiceClient.java`:

```java
package dev.meirong.shop.sellerbff.client;

import dev.meirong.shop.common.api.ApiResponse;
import dev.meirong.shop.contracts.profile.ProfileApi;
import org.springframework.web.service.annotation.HttpExchange;
import org.springframework.web.service.annotation.PostExchange;

@HttpExchange
public interface ProfileServiceClient {

    @PostExchange(ProfileApi.SELLER_GET)
    ApiResponse<ProfileApi.ProfileResponse> getProfile(ProfileApi.GetProfileRequest request);

    @PostExchange(ProfileApi.SELLER_UPDATE)
    ApiResponse<ProfileApi.ProfileResponse> updateProfile(ProfileApi.UpdateProfileRequest request);

    @PostExchange(ProfileApi.SELLER_STOREFRONT)
    ApiResponse<ProfileApi.SellerStorefrontResponse> getSellerStorefront(ProfileApi.GetProfileRequest request);

    @PostExchange(ProfileApi.SELLER_SHOP_UPDATE)
    ApiResponse<ProfileApi.SellerStorefrontResponse> updateShop(ProfileApi.UpdateShopRequest request);
}
```

- [ ] **Step 2: Create seller-bff MarketplaceServiceClient**

Create `services/seller-bff/src/main/java/dev/meirong/shop/sellerbff/client/MarketplaceServiceClient.java`:

```java
package dev.meirong.shop.sellerbff.client;

import dev.meirong.shop.common.api.ApiResponse;
import dev.meirong.shop.contracts.marketplace.MarketplaceApi;
import org.springframework.web.service.annotation.HttpExchange;
import org.springframework.web.service.annotation.PostExchange;

@HttpExchange
public interface MarketplaceServiceClient {

    @PostExchange(MarketplaceApi.LIST)
    ApiResponse<MarketplaceApi.ProductsView> listProducts(MarketplaceApi.ListProductsRequest request);

    @PostExchange(MarketplaceApi.CREATE)
    ApiResponse<MarketplaceApi.ProductResponse> createProduct(MarketplaceApi.UpsertProductRequest request);

    @PostExchange(MarketplaceApi.UPDATE)
    ApiResponse<MarketplaceApi.ProductResponse> updateProduct(MarketplaceApi.UpsertProductRequest request);

    @PostExchange(MarketplaceApi.SEARCH)
    ApiResponse<MarketplaceApi.ProductsPageView> searchProducts(MarketplaceApi.SearchProductsRequest request);
}
```

- [ ] **Step 3: Create seller-bff OrderServiceClient**

Create `services/seller-bff/src/main/java/dev/meirong/shop/sellerbff/client/OrderServiceClient.java`:

```java
package dev.meirong.shop.sellerbff.client;

import dev.meirong.shop.common.api.ApiResponse;
import dev.meirong.shop.contracts.order.OrderApi;
import java.util.List;
import org.springframework.web.service.annotation.HttpExchange;
import org.springframework.web.service.annotation.PostExchange;

@HttpExchange
public interface OrderServiceClient {

    @PostExchange(OrderApi.ORDER_LIST)
    ApiResponse<List<OrderApi.OrderResponse>> listOrders(OrderApi.ListOrdersRequest request);

    @PostExchange(OrderApi.ORDER_GET)
    ApiResponse<OrderApi.OrderResponse> getOrder(OrderApi.GetOrderRequest request);

    @PostExchange(OrderApi.ORDER_SHIP)
    ApiResponse<OrderApi.OrderResponse> shipOrder(OrderApi.ShipOrderRequest request);

    @PostExchange(OrderApi.ORDER_DELIVER)
    ApiResponse<OrderApi.OrderResponse> deliverOrder(OrderApi.ConfirmOrderRequest request);

    @PostExchange(OrderApi.ORDER_CANCEL)
    ApiResponse<OrderApi.OrderResponse> cancelOrder(OrderApi.CancelOrderRequest request);
}
```

- [ ] **Step 4: Create seller-bff WalletServiceClient**

Create `services/seller-bff/src/main/java/dev/meirong/shop/sellerbff/client/WalletServiceClient.java`:

```java
package dev.meirong.shop.sellerbff.client;

import dev.meirong.shop.common.api.ApiResponse;
import dev.meirong.shop.contracts.wallet.WalletApi;
import org.springframework.web.service.annotation.HttpExchange;
import org.springframework.web.service.annotation.PostExchange;

@HttpExchange
public interface WalletServiceClient {

    @PostExchange(WalletApi.GET)
    ApiResponse<WalletApi.WalletAccountResponse> getWallet(WalletApi.GetWalletRequest request);

    @PostExchange(WalletApi.WITHDRAW)
    ApiResponse<WalletApi.TransactionResponse> withdraw(WalletApi.WithdrawRequest request);
}
```

- [ ] **Step 5: Create seller-bff PromotionServiceClient**

Create `services/seller-bff/src/main/java/dev/meirong/shop/sellerbff/client/PromotionServiceClient.java`:

```java
package dev.meirong.shop.sellerbff.client;

import dev.meirong.shop.common.api.ApiResponse;
import dev.meirong.shop.contracts.promotion.PromotionApi;
import java.util.List;
import org.springframework.web.service.annotation.HttpExchange;
import org.springframework.web.service.annotation.PostExchange;

@HttpExchange
public interface PromotionServiceClient {

    @PostExchange(PromotionApi.CREATE)
    ApiResponse<PromotionApi.OfferResponse> createOffer(PromotionApi.CreateOfferRequest request);

    @PostExchange(PromotionApi.COUPON_CREATE)
    ApiResponse<PromotionApi.CouponResponse> createCoupon(PromotionApi.CreateCouponRequest request);

    @PostExchange(PromotionApi.COUPON_LIST)
    ApiResponse<List<PromotionApi.CouponResponse>> listCoupons(PromotionApi.ListCouponsRequest request);

    @PostExchange(PromotionApi.LIST)
    ApiResponse<PromotionApi.OffersView> listOffers(PromotionApi.ListOffersRequest request);
}
```

- [ ] **Step 6: Create seller-bff SearchServiceClient**

Create `services/seller-bff/src/main/java/dev/meirong/shop/sellerbff/client/SearchServiceClient.java`:

```java
package dev.meirong.shop.sellerbff.client;

import dev.meirong.shop.common.api.ApiResponse;
import dev.meirong.shop.contracts.search.SearchApi;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.service.annotation.GetExchange;
import org.springframework.web.service.annotation.HttpExchange;

@HttpExchange
public interface SearchServiceClient {

    @GetExchange(SearchApi.SEARCH)
    ApiResponse<SearchApi.SearchProductsResponse> searchProducts(
            @RequestParam("q") String query,
            @RequestParam("categoryId") String categoryId,
            @RequestParam("page") int page,
            @RequestParam("size") int size);
}
```

- [ ] **Step 7: Rewrite SellerBffConfig to register all client proxies**

Replace the full content of `services/seller-bff/src/main/java/dev/meirong/shop/sellerbff/config/SellerBffConfig.java`:

```java
package dev.meirong.shop.sellerbff.config;

import dev.meirong.shop.sellerbff.client.MarketplaceServiceClient;
import dev.meirong.shop.sellerbff.client.OrderServiceClient;
import dev.meirong.shop.sellerbff.client.ProfileServiceClient;
import dev.meirong.shop.sellerbff.client.PromotionServiceClient;
import dev.meirong.shop.sellerbff.client.SearchServiceClient;
import dev.meirong.shop.sellerbff.client.WalletServiceClient;
import java.net.http.HttpClient;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.support.RestClientAdapter;
import org.springframework.web.service.invoker.HttpServiceProxyFactory;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(SellerClientProperties.class)
public class SellerBffConfig {

    @Bean
    JdkClientHttpRequestFactory jdkClientHttpRequestFactory(SellerClientProperties properties) {
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(properties.connectTimeout())
                .build();
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(httpClient);
        factory.setReadTimeout(properties.readTimeout());
        return factory;
    }

    @Bean
    RestClient.Builder restClientBuilder(JdkClientHttpRequestFactory factory) {
        return RestClient.builder().requestFactory(factory);
    }

    @Bean
    ProfileServiceClient profileServiceClient(SellerClientProperties properties,
                                              JdkClientHttpRequestFactory factory) {
        return createClient(properties.profileServiceUrl(), factory, ProfileServiceClient.class);
    }

    @Bean
    MarketplaceServiceClient marketplaceServiceClient(SellerClientProperties properties,
                                                      JdkClientHttpRequestFactory factory) {
        return createClient(properties.marketplaceServiceUrl(), factory, MarketplaceServiceClient.class);
    }

    @Bean
    OrderServiceClient orderServiceClient(SellerClientProperties properties,
                                          JdkClientHttpRequestFactory factory) {
        return createClient(properties.orderServiceUrl(), factory, OrderServiceClient.class);
    }

    @Bean
    WalletServiceClient walletServiceClient(SellerClientProperties properties,
                                            JdkClientHttpRequestFactory factory) {
        return createClient(properties.walletServiceUrl(), factory, WalletServiceClient.class);
    }

    @Bean
    PromotionServiceClient promotionServiceClient(SellerClientProperties properties,
                                                  JdkClientHttpRequestFactory factory) {
        return createClient(properties.promotionServiceUrl(), factory, PromotionServiceClient.class);
    }

    @Bean
    SearchServiceClient searchServiceClient(SellerClientProperties properties,
                                            JdkClientHttpRequestFactory factory) {
        return createClient(properties.searchServiceUrl(), factory, SearchServiceClient.class);
    }

    private <T> T createClient(String baseUrl, JdkClientHttpRequestFactory factory, Class<T> clientType) {
        RestClient restClient = RestClient.builder()
                .requestFactory(factory)
                .baseUrl(baseUrl)
                .build();
        return HttpServiceProxyFactory
                .builderFor(RestClientAdapter.create(restClient))
                .build()
                .createClient(clientType);
    }
}
```

- [ ] **Step 8: Rewrite SellerAggregationService to use client interfaces**

Same pattern as buyer-bff Task 7 Step 2. Key changes:
1. Inject 6 client interfaces instead of using `RestClient restClient`
2. Remove the `post()` helper method
3. Add `requireData(ApiResponse<T>)` helper
4. Each method body becomes: `requireData(client.method(request))`
5. Keep `ResilienceHelper` wrapping and `MetricsHelper` timing unchanged

- [ ] **Step 9: Compile and test seller-bff**

Run: `./mvnw -pl services/seller-bff -am test -q`
Expected: All tests pass

- [ ] **Step 10: Commit**

```bash
git add services/seller-bff/
git commit -m "refactor(seller-bff): replace RestClient boilerplate with @HttpExchange clients

Create 6 declarative HTTP client interfaces for seller-bff downstream
services and register via HttpServiceProxyFactory. Rewrite
SellerAggregationService to use injected client interfaces.

Co-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>"
```

---

## Task 9: @HttpExchange — full BFF verification

- [ ] **Step 1: Run full verify for both BFFs**

Run: `./mvnw -B -ntp verify -pl services/buyer-bff,services/seller-bff -am`
Expected: BUILD SUCCESS

- [ ] **Step 2: Run architecture tests**

Run: `make arch-test`
Expected: All 19 ArchUnit rules pass

---

## Task 10: JWT RS256 — auth-server RSA key pair + JWKS endpoint

**Files:**
- Modify: `services/auth-server/src/main/java/dev/meirong/shop/authserver/config/AuthProperties.java`
- Modify: `services/auth-server/src/main/java/dev/meirong/shop/authserver/service/JwtTokenService.java`
- Create: `services/auth-server/src/main/java/dev/meirong/shop/authserver/controller/JwksController.java`
- Modify: `services/auth-server/src/main/java/dev/meirong/shop/authserver/config/SecurityConfig.java`
- Modify: `services/auth-server/src/main/resources/application.yml`
- Create: `services/auth-server/src/main/resources/dev-keys/rsa-private.pem`
- Create: `services/auth-server/src/main/resources/dev-keys/rsa-public.pem`

- [ ] **Step 1: Generate dev RSA key pair**

Run these commands to generate a 2048-bit RSA key pair for local development:

```bash
mkdir -p services/auth-server/src/main/resources/dev-keys
openssl genpkey -algorithm RSA -out services/auth-server/src/main/resources/dev-keys/rsa-private.pem -pkeyopt rsa_keygen_bits:2048
openssl rsa -in services/auth-server/src/main/resources/dev-keys/rsa-private.pem -pubout -out services/auth-server/src/main/resources/dev-keys/rsa-public.pem
```

- [ ] **Step 2: Update AuthProperties to add RSA key paths**

Replace `services/auth-server/src/main/java/dev/meirong/shop/authserver/config/AuthProperties.java`:

```java
package dev.meirong.shop.authserver.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.core.io.Resource;

@ConfigurationProperties(prefix = "shop.auth")
public record AuthProperties(String issuer,
                             Resource rsaPrivateKey,
                             Resource rsaPublicKey,
                             Duration tokenTtl,
                             String googleClientId,
                             String appleClientId,
                             String profileServiceUrl,
                             String buyerRegisteredTopic) {
}
```

The `secret` field is removed. RSA keys use Spring's `Resource` type for flexible classpath/file loading.

- [ ] **Step 3: Rewrite JwtTokenService for RS256**

Replace `services/auth-server/src/main/java/dev/meirong/shop/authserver/service/JwtTokenService.java`:

```java
package dev.meirong.shop.authserver.service;

import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import dev.meirong.shop.authserver.config.AuthProperties;
import dev.meirong.shop.authserver.domain.UserAccountEntity;
import dev.meirong.shop.contracts.auth.AuthApi;
import java.security.KeyFactory;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.UUID;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.stereotype.Service;

@Service
public class JwtTokenService {

    private final AuthProperties properties;
    private final JwtEncoder jwtEncoder;
    private final RSAPublicKey publicKey;
    private final String keyId;

    public JwtTokenService(AuthProperties properties) {
        this.properties = properties;
        try {
            this.publicKey = loadPublicKey(properties.rsaPublicKey());
            RSAPrivateKey privateKey = loadPrivateKey(properties.rsaPrivateKey());
            this.keyId = UUID.nameUUIDFromBytes(publicKey.getEncoded()).toString();
            RSAKey rsaKey = new RSAKey.Builder(publicKey)
                    .privateKey(privateKey)
                    .keyID(keyId)
                    .build();
            this.jwtEncoder = new NimbusJwtEncoder(new ImmutableJWKSet<>(new JWKSet(rsaKey)));
        } catch (Exception exception) {
            throw new IllegalStateException("Failed to initialize RSA keys for JWT signing", exception);
        }
    }

    public AuthApi.TokenResponse issueToken(DemoUserDirectory.UserProfile profile) {
        return issueTokenInternal(profile.principalId(), profile.username(),
                profile.displayName(), profile.roles(), profile.portal(), false);
    }

    public AuthApi.TokenResponse issueToken(UserAccountEntity account) {
        return issueTokenInternal(account.getPrincipalId(), account.getUsername(),
                account.getDisplayName(), account.getRoleList(), account.getPortal(), false);
    }

    public AuthApi.TokenResponse issueToken(UserAccountEntity account, boolean newUser) {
        return issueTokenInternal(account.getPrincipalId(), account.getUsername(),
                account.getDisplayName(), account.getRoleList(), account.getPortal(), newUser);
    }

    private AuthApi.TokenResponse issueTokenInternal(String principalId, String username,
                                                     String displayName, List<String> roles,
                                                     String portal, boolean newUser) {
        Instant issuedAt = Instant.now();
        Instant expiresAt = issuedAt.plus(properties.tokenTtl());
        JwtClaimsSet claimsSet = JwtClaimsSet.builder()
                .issuer(properties.issuer())
                .issuedAt(issuedAt)
                .expiresAt(expiresAt)
                .subject(principalId)
                .claim("username", username)
                .claim("displayName", displayName)
                .claim("principalId", principalId)
                .claim("roles", roles)
                .claim("portal", portal)
                .build();
        JwsHeader jwsHeader = JwsHeader.with(SignatureAlgorithm.RS256)
                .keyId(keyId)
                .type("JWT")
                .build();
        String token = jwtEncoder.encode(JwtEncoderParameters.from(jwsHeader, claimsSet)).getTokenValue();
        return new AuthApi.TokenResponse(
                token, "Bearer", expiresAt, username, displayName,
                principalId, List.copyOf(roles), portal, newUser);
    }

    public JwtDecoder jwtDecoder() {
        return NimbusJwtDecoder.withPublicKey(publicKey)
                .signatureAlgorithm(SignatureAlgorithm.RS256)
                .build();
    }

    public JWKSet jwkSet() {
        RSAKey rsaPublicKey = new RSAKey.Builder(publicKey).keyID(keyId).build();
        return new JWKSet(rsaPublicKey);
    }

    private static RSAPublicKey loadPublicKey(org.springframework.core.io.Resource resource) throws Exception {
        String pem = new String(resource.getInputStream().readAllBytes())
                .replace("-----BEGIN PUBLIC KEY-----", "")
                .replace("-----END PUBLIC KEY-----", "")
                .replaceAll("\\s", "");
        byte[] keyBytes = Base64.getDecoder().decode(pem);
        return (RSAPublicKey) KeyFactory.getInstance("RSA").generatePublic(new X509EncodedKeySpec(keyBytes));
    }

    private static RSAPrivateKey loadPrivateKey(org.springframework.core.io.Resource resource) throws Exception {
        String pem = new String(resource.getInputStream().readAllBytes())
                .replace("-----BEGIN PRIVATE KEY-----", "")
                .replace("-----END PRIVATE KEY-----", "")
                .replaceAll("\\s", "");
        byte[] keyBytes = Base64.getDecoder().decode(pem);
        return (RSAPrivateKey) KeyFactory.getInstance("RSA").generatePrivate(new PKCS8EncodedKeySpec(keyBytes));
    }
}
```

- [ ] **Step 4: Create JwksController**

Create `services/auth-server/src/main/java/dev/meirong/shop/authserver/controller/JwksController.java`:

```java
package dev.meirong.shop.authserver.controller;

import dev.meirong.shop.authserver.service.JwtTokenService;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class JwksController {

    private final JwtTokenService jwtTokenService;

    public JwksController(JwtTokenService jwtTokenService) {
        this.jwtTokenService = jwtTokenService;
    }

    @GetMapping("/.well-known/jwks.json")
    public Map<String, Object> jwks() {
        return jwtTokenService.jwkSet().toJSONObject();
    }
}
```

- [ ] **Step 5: Update SecurityConfig to permit JWKS endpoint**

In `services/auth-server/src/main/java/dev/meirong/shop/authserver/config/SecurityConfig.java`, add `"/.well-known/jwks.json"` to the list of `.requestMatchers(...)`.permitAll() paths.

- [ ] **Step 6: Update auth-server application.yml**

Replace the `secret` property with RSA key paths:

```yaml
shop:
  auth:
    issuer: shop-auth-server
    rsa-private-key: ${SHOP_AUTH_RSA_PRIVATE_KEY:classpath:dev-keys/rsa-private.pem}
    rsa-public-key: ${SHOP_AUTH_RSA_PUBLIC_KEY:classpath:dev-keys/rsa-public.pem}
    token-ttl: PT8H
```

Remove: `secret: ${SHOP_AUTH_JWT_SECRET:change-this-to-a-32-byte-demo-secret}`

- [ ] **Step 7: Copy dev-keys to test resources and update test property sources**

```bash
cp -r services/auth-server/src/main/resources/dev-keys services/auth-server/src/test/resources/
```

Update `AuthControllerTest`'s `@TestPropertySource` to use RSA properties instead of `shop.auth.secret`.

- [ ] **Step 8: Compile and test auth-server**

Run: `./mvnw -pl services/auth-server -am test -q`
Expected: All tests pass

- [ ] **Step 9: Commit**

```bash
git add services/auth-server/
git commit -m "feat(auth-server): migrate JWT signing from HS256 to RS256 with JWKS endpoint

Replace symmetric HMAC-SHA256 signing with RSA-2048 asymmetric key pair.
Add /.well-known/jwks.json endpoint serving the public key for gateway
validation. Dev RSA keys included for local development.

BREAKING: Existing HS256 tokens will be invalid after deployment.
Deploy auth-server and gateway together during rollout.

Co-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>"
```

---

## Task 11: JWT RS256 — gateway JWKS validation

**Files:**
- Modify: `services/api-gateway/src/main/java/dev/meirong/shop/gateway/config/GatewayProperties.java`
- Modify: `services/api-gateway/src/main/java/dev/meirong/shop/gateway/config/GatewaySecurityConfig.java`
- Modify: `services/api-gateway/src/main/resources/application.yml`
- Modify: `platform/k8s/apps/base/platform.yaml`

- [ ] **Step 1: Update GatewayProperties**

Replace `jwtSecret` with `jwksUri` in `GatewayProperties.java`. Keep the `RateLimit` and `Cors` inner records unchanged.

- [ ] **Step 2: Update GatewaySecurityConfig to use JWKS URI**

Replace the `jwtDecoder` bean:

```java
@Bean
JwtDecoder jwtDecoder(GatewayProperties properties) {
    return NimbusJwtDecoder.withJwkSetUri(properties.jwksUri()).build();
}
```

Remove unused imports: `javax.crypto.SecretKey`, `javax.crypto.spec.SecretKeySpec`, `MacAlgorithm`.

- [ ] **Step 3: Update gateway application.yml**

Replace `jwt-secret` with `jwks-uri`:

```yaml
shop:
  gateway:
    jwks-uri: ${SHOP_AUTH_JWKS_URI:http://auth-server:8080/.well-known/jwks.json}
```

- [ ] **Step 4: Update gateway tests**

Tests that use `@TestPropertySource` with `shop.gateway.jwt-secret` must be updated. For integration tests, either:
- Mock the `JwtDecoder` bean directly, OR
- Use WireMock to serve the JWKS endpoint with the dev public key

- [ ] **Step 5: Compile and test gateway**

Run: `./mvnw -pl services/api-gateway -am test -q`
Expected: All tests pass

- [ ] **Step 6: Update K8s manifests**

In `platform/k8s/apps/base/platform.yaml`:
- auth-server: Add `SHOP_AUTH_RSA_PRIVATE_KEY` and `SHOP_AUTH_RSA_PUBLIC_KEY` env vars (pointing to Secret-mounted PEM files or classpath defaults)
- api-gateway: Replace `SHOP_AUTH_JWT_SECRET` with `SHOP_AUTH_JWKS_URI: http://auth-server:8080/.well-known/jwks.json`
- Remove `SHOP_AUTH_JWT_SECRET` from infra ConfigMap if present

- [ ] **Step 7: Commit**

```bash
git add services/api-gateway/ platform/k8s/
git commit -m "feat(api-gateway): switch JWT validation from shared secret to JWKS URI

Replace NimbusJwtDecoder.withSecretKey(HS256) with
NimbusJwtDecoder.withJwkSetUri() pointing to auth-server's
/.well-known/jwks.json endpoint. Gateway no longer needs the
JWT signing secret — only the public key via JWKS.

Co-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>"
```

---

## Task 12: AppCDS — Dockerfile.fast training stage

**Files:**
- Modify: `platform/docker/Dockerfile.fast`

- [ ] **Step 1: Rewrite Dockerfile.fast with CDS training stage**

Replace the full content of `platform/docker/Dockerfile.fast`:

```dockerfile
# Stage 1: reusable base with curl + non-root user (layer cached separately from app)
FROM eclipse-temurin:25-jre AS base
RUN apt-get update && apt-get install -y --no-install-recommends curl && rm -rf /var/lib/apt/lists/*
RUN addgroup --system appgroup && adduser --system --ingroup appgroup appuser

# Stage 2: CDS training — run app briefly to generate shared class archive
FROM base AS cds-training
ARG JAR_FILE
WORKDIR /app
COPY ${JAR_FILE} app.jar
RUN java -XX:ArchiveClassesAtExit=/app/app.jsa \
    -Dspring.context.exit=onRefresh \
    -jar /app/app.jar || true

# Stage 3: production image with AppCDS archive
FROM base
ARG JAR_FILE
WORKDIR /app
COPY ${JAR_FILE} app.jar
COPY --from=cds-training /app/app.jsa /app/app.jsa

EXPOSE 8080
EXPOSE 8081
USER appuser
ENTRYPOINT ["java", \
  "-XX:SharedArchiveFile=/app/app.jsa", \
  "-XX:MaxRAMPercentage=70.0", \
  "-XX:+HeapDumpOnOutOfMemoryError", \
  "-XX:HeapDumpPath=/tmp/heapdump.hprof", \
  "-jar", "/app/app.jar"]
```

**How it works:**
1. Stage 2 runs the app with `-XX:ArchiveClassesAtExit` to record all loaded classes into a `.jsa` archive. `-Dspring.context.exit=onRefresh` causes Spring Boot to exit after context refresh (no running infrastructure needed). `|| true` ensures the build continues even if the app exits with non-zero code.
2. Stage 3 copies both the JAR and `.jsa`, using `-XX:SharedArchiveFile` at runtime for 20-40% cold startup improvement.

- [ ] **Step 2: Test the build locally**

Build one service to verify:

```bash
./mvnw -pl services/auth-server -am package -DskipTests -q
docker build \
  --build-arg JAR_FILE=services/auth-server/target/auth-server-*.jar \
  -f platform/docker/Dockerfile.fast \
  -t shop/auth-server:cds-test .
```

Expected: Build succeeds with 3 stages. CDS training stage logs Spring Boot startup and class recording.

- [ ] **Step 3: Commit**

```bash
git add platform/docker/Dockerfile.fast
git commit -m "perf(docker): add AppCDS training stage to Dockerfile.fast

Add a CDS training stage that generates a shared class archive (.jsa)
during Docker build. The production stage uses -XX:SharedArchiveFile
to load pre-verified classes, reducing cold startup time by 20-40%.

Training uses -Dspring.context.exit=onRefresh to exit after context
initialization without requiring running infrastructure.

Co-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>"
```

---

## Task 13: Full integration verification

- [ ] **Step 1: Run full Maven verify**

Run: `./mvnw -B -ntp verify`
Expected: BUILD SUCCESS across all modules

- [ ] **Step 2: Run architecture tests**

Run: `make arch-test`
Expected: All 19 ArchUnit rules pass

- [ ] **Step 3: Run local checks**

Run: `make local-checks-all`
Expected: All checks pass
