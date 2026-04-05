# Bloom Filter Idempotency Enhancement Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add Redis Bloom Filter as a fast-path accelerator in front of existing DB idempotency checks, delivered via a `IdempotencyGuard` abstraction in `shop-common`, validated in `wallet-service` (HTTP) and `promotion-service` (Kafka).

**Architecture:** `IdempotencyGuard.executeOnce(key, action, fallback)` checks the BF first; on miss the action runs and writes its own DB idempotency record within its transaction, then BF.add is called post-commit. On BF hit the DB is queried to confirm; false positives execute the action and catch `DataIntegrityViolationException` to call fallback. Redis failures auto-degrade to pure-DB path.

**Tech Stack:** Java 25, Spring Boot 3.5, Redisson `redisson-spring-boot-starter` 3.44.0, Micrometer, ArchUnit 4.x, JUnit 5 + Mockito

**Spec:** `docs/superpowers/specs/2026-03-23-bloom-filter-idempotency-design.md`

---

## File Map

### shop-common (new files)
| File | Responsibility |
|------|---------------|
| `shop-common/pom.xml` | Add `redisson-spring-boot-starter` dependency |
| `pom.xml` (root) | Add Redisson version to `<properties>` and `<dependencyManagement>` |
| `…/common/idempotency/IdempotencyRepository.java` | Interface: `existsByKey(String key): boolean` |
| `…/common/idempotency/IdempotencyGuard.java` | Interface: `executeOnce(key, action, fallback)` |
| `…/common/idempotency/IdempotencyExempt.java` | Marker annotation for ArchUnit exemption |
| `…/common/idempotency/BloomFilterProperties.java` | `@ConfigurationProperties("shop.idempotency.bloom-filter")` |
| `…/common/idempotency/RedisIdempotencyGuard.java` | Implementation: BF check → action → BF.add; Redis fallback |
| `…/common/idempotency/IdempotencyAutoConfiguration.java` | `@AutoConfiguration`, `@ConditionalOnClass(RedissonClient.class)` |
| `AutoConfiguration.imports` | Register `IdempotencyAutoConfiguration` |
| `…/common/idempotency/RedisIdempotencyGuardTest.java` | Unit tests (Mockito, 10 scenarios) |

### wallet-service (new/modified)
| File | Responsibility |
|------|---------------|
| `…/db/migration/V3__wallet_idempotency_key.sql` | DDL: `wallet_idempotency_key(idempotency_key PK, transaction_id, created_at)` |
| `…/wallet/domain/WalletIdempotencyKeyEntity.java` | JPA entity for the new table |
| `…/wallet/domain/WalletIdempotencyKeyRepository.java` | Extends `JpaRepository`; also implements `IdempotencyRepository` |
| `…/wallet/service/WalletApplicationService.java` | Add `depositWithIdempotency` + `findByIdempotencyKey`; existing `deposit` unchanged |
| `…/wallet/controller/WalletController.java` | Accept optional `Idempotency-Key` header; call `IdempotencyGuard` when present |
| `wallet-service/src/main/resources/application.yml` | Add `shop.idempotency.bloom-filter` config block |
| `…/wallet/service/WalletApplicationServiceTest.java` | Expand with idempotency scenarios |

### promotion-service (new/modified)
| File | Responsibility |
|------|---------------|
| `…/db/migration/V5__promotion_idempotency_key.sql` | DDL: `promotion_idempotency_key(idempotency_key PK, created_at)` |
| `…/promotion/domain/PromotionIdempotencyKeyEntity.java` | JPA entity |
| `…/promotion/domain/PromotionIdempotencyKeyRepository.java` | Implements `IdempotencyRepository`; also exposes `saveKey(String)` |
| `…/promotion/messaging/WelcomeCouponListener.java` | Replace code-based idempotency check with `IdempotencyGuard.executeOnce` |
| `promotion-service/src/main/resources/application.yml` | Add `shop.idempotency.bloom-filter` config block |
| `…/promotion/messaging/WelcomeCouponListenerTest.java` | Expand with idempotency scenarios |

### architecture-tests (modified)
| File | Responsibility |
|------|---------------|
| `…/architecture/ArchitectureRulesTest.java` | Add rules 3 (KafkaListener + IdempotencyGuard) and 4 (@Transactional enforcement) |

---

## Task 1: Add Redisson to Root BOM and shop-common

**Files:**
- Modify: `pom.xml` (root) — add Redisson version property + dependencyManagement entry
- Modify: `shop-common/pom.xml` — add `redisson-spring-boot-starter` dependency

- [ ] **Step 1: Add Redisson version to root pom.xml**

  In `pom.xml`, inside `<properties>`:
  ```xml
  <redisson.version>3.44.0</redisson.version>
  ```
  In `<dependencyManagement><dependencies>`:
  ```xml
  <dependency>
      <groupId>org.redisson</groupId>
      <artifactId>redisson-spring-boot-starter</artifactId>
      <version>${redisson.version}</version>
  </dependency>
  ```

- [ ] **Step 2: Add Redisson dependency to shop-common/pom.xml**

  In `shop-common/pom.xml`, inside `<dependencies>`:
  ```xml
  <dependency>
      <groupId>org.redisson</groupId>
      <artifactId>redisson-spring-boot-starter</artifactId>
      <optional>true</optional>
  </dependency>
  ```
  Mark as `<optional>true</optional>` so services only get it if they declare Redisson themselves (prevents transitive forced dependency).

- [ ] **Step 3: Verify compilation**

  Run: `./mvnw compile -pl shop-common -am -q`
  Expected: BUILD SUCCESS

- [ ] **Step 4: Commit**

  ```bash
  git add pom.xml shop-common/pom.xml
  git commit -m "build: add Redisson 3.44.0 to BOM and shop-common optional dependency"
  ```

---

## Task 2: IdempotencyRepository, IdempotencyGuard interfaces, and @IdempotencyExempt

**Files:**
- Create: `shop-common/src/main/java/dev/meirong/shop/common/idempotency/IdempotencyRepository.java`
- Create: `shop-common/src/main/java/dev/meirong/shop/common/idempotency/IdempotencyGuard.java`
- Create: `shop-common/src/main/java/dev/meirong/shop/common/idempotency/IdempotencyExempt.java`

- [ ] **Step 1: Write failing test for IdempotencyGuard (verifies interface exists)**

  Create `shop-common/src/test/java/dev/meirong/shop/common/idempotency/IdempotencyGuardContractTest.java`:
  ```java
  package dev.meirong.shop.common.idempotency;

  import static org.assertj.core.api.Assertions.assertThat;
  import org.junit.jupiter.api.Test;

  class IdempotencyGuardContractTest {

      @Test
      void interfaceExists() {
          // Compilation confirms the interface exists and has the right signature
          IdempotencyGuard guard = (key, action, fallback) -> action.get();
          assertThat(guard.executeOnce("k", () -> "result", () -> "fallback")).isEqualTo("result");
      }
  }
  ```

- [ ] **Step 2: Run test to verify it fails**

  Run: `./mvnw test -pl shop-common -Dtest=IdempotencyGuardContractTest -q`
  Expected: FAIL with compilation error

- [ ] **Step 3: Create IdempotencyRepository**

  Create `shop-common/src/main/java/dev/meirong/shop/common/idempotency/IdempotencyRepository.java`:
  ```java
  package dev.meirong.shop.common.idempotency;

  /**
   * Read-only view of the DB idempotency table used by {@link IdempotencyGuard}.
   * Each service provides one implementation backed by its own idempotency table.
   * Writing to the table is the responsibility of the {@code action} supplier
   * passed to {@link IdempotencyGuard#executeOnce}.
   */
  public interface IdempotencyRepository {

      /**
       * Returns true if {@code key} has a committed DB record.
       * Called after a Bloom Filter hit to confirm before calling {@code fallback}.
       */
      boolean existsByKey(String key);
  }
  ```

- [ ] **Step 4: Create IdempotencyGuard**

  Create `shop-common/src/main/java/dev/meirong/shop/common/idempotency/IdempotencyGuard.java`:
  ```java
  package dev.meirong.shop.common.idempotency;

  import java.util.function.Supplier;

  /**
   * Idempotency protection for write operations.
   *
   * <p>Internal flow: Redis BF fast-path → DB confirm on BF hit → STRICT mode (DB is final authority).
   * Redis failures auto-degrade to pure-DB path. All service tiers use STRICT mode uniformly.
   *
   * <p>The {@code action} supplier is responsible for writing its own DB idempotency record
   * (key + optional result reference) within its {@code @Transactional} boundary.
   * This guard calls {@code BF.add} after {@code action} returns, outside the transaction.
   */
  public interface IdempotencyGuard {

      /**
       * Executes {@code action} exactly once for the given {@code key}.
       *
       * @param key      idempotency key; max 128 chars; format per spec section 5.3
       * @param action   business logic; runs only when key is not yet processed;
       *                 MUST save the idempotency key to DB within its own {@code @Transactional}
       *                 boundary so that concurrent duplicate requests fail with
       *                 {@code DataIntegrityViolationException}; exceptions propagate to caller
       * @param fallback called when key is already processed (DB confirms); used to reconstruct
       *                 the previous result; may return null; if fallback throws, exception propagates
       * @return value from {@code action} (first execution) or {@code fallback} (replay); may be null
       * @throws RuntimeException any exception from {@code action} or {@code fallback} propagates
       */
      <T> T executeOnce(String key, Supplier<T> action, Supplier<T> fallback);
  }
  ```

- [ ] **Step 5: Create @IdempotencyExempt**

  Create `shop-common/src/main/java/dev/meirong/shop/common/idempotency/IdempotencyExempt.java`:
  ```java
  package dev.meirong.shop.common.idempotency;

  import java.lang.annotation.Documented;
  import java.lang.annotation.ElementType;
  import java.lang.annotation.Retention;
  import java.lang.annotation.RetentionPolicy;
  import java.lang.annotation.Target;

  /**
   * Exempts a {@code @KafkaListener} class from the ArchUnit rule requiring
   * {@link IdempotencyGuard} injection. Must document the alternative idempotency mechanism.
   *
   * <p>Use sparingly; exemptions require code-review justification.
   */
  @Target(ElementType.TYPE)
  @Retention(RetentionPolicy.RUNTIME)
  @Documented
  public @interface IdempotencyExempt {
      /** Describes the alternative idempotency mechanism in place. */
      String reason();
  }
  ```

- [ ] **Step 6: Run test to verify it passes**

  Run: `./mvnw test -pl shop-common -Dtest=IdempotencyGuardContractTest -q`
  Expected: PASS

- [ ] **Step 7: Commit**

  ```bash
  git add shop-common/src/main/java/dev/meirong/shop/common/idempotency/ \
          shop-common/src/test/java/dev/meirong/shop/common/idempotency/IdempotencyGuardContractTest.java
  git commit -m "feat(shop-common): add IdempotencyGuard interface, IdempotencyRepository, and @IdempotencyExempt"
  ```

---

## Task 3: BloomFilterProperties + RedisIdempotencyGuard implementation

**Files:**
- Create: `shop-common/src/main/java/dev/meirong/shop/common/idempotency/BloomFilterProperties.java`
- Create: `shop-common/src/main/java/dev/meirong/shop/common/idempotency/RedisIdempotencyGuard.java`
- Create: `shop-common/src/test/java/dev/meirong/shop/common/idempotency/RedisIdempotencyGuardTest.java`

- [ ] **Step 1: Write the failing tests**

  Create `shop-common/src/test/java/dev/meirong/shop/common/idempotency/RedisIdempotencyGuardTest.java`:
  ```java
  package dev.meirong.shop.common.idempotency;

  import static org.assertj.core.api.Assertions.assertThat;
  import static org.assertj.core.api.Assertions.assertThatThrownBy;
  import static org.mockito.Mockito.*;

  import io.micrometer.core.instrument.MeterRegistry;
  import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
  import org.junit.jupiter.api.BeforeEach;
  import org.junit.jupiter.api.Test;
  import org.junit.jupiter.api.extension.ExtendWith;
  import org.mockito.Mock;
  import org.mockito.junit.jupiter.MockitoExtension;
  import org.redisson.api.RBloomFilter;
  import org.springframework.dao.DataIntegrityViolationException;

  @ExtendWith(MockitoExtension.class)
  class RedisIdempotencyGuardTest {

      @Mock private RBloomFilter<String> bloomFilter;
      @Mock private IdempotencyRepository repo;
      private MeterRegistry meterRegistry;
      private RedisIdempotencyGuard guard;

      @BeforeEach
      void setUp() {
          meterRegistry = new SimpleMeterRegistry();
          guard = new RedisIdempotencyGuard(bloomFilter, repo, meterRegistry, "test-service");
      }

      @Test
      void bfMiss_executesAction_addsToBloomFilter() {
          when(bloomFilter.contains("key-1")).thenReturn(false);
          String result = guard.executeOnce("key-1", () -> "done", () -> "fallback");
          assertThat(result).isEqualTo("done");
          verify(bloomFilter).add("key-1");
          verify(repo, never()).existsByKey(any());
      }

      @Test
      void bfHit_dbConfirms_returnsFallback() {
          when(bloomFilter.contains("key-2")).thenReturn(true);
          when(repo.existsByKey("key-2")).thenReturn(true);
          String result = guard.executeOnce("key-2", () -> "should-not-run", () -> "cached");
          assertThat(result).isEqualTo("cached");
          verify(bloomFilter, never()).add(any());
      }

      @Test
      void bfHit_dbDenies_falsePositive_executesAction() {
          when(bloomFilter.contains("key-3")).thenReturn(true);
          when(repo.existsByKey("key-3")).thenReturn(false);
          String result = guard.executeOnce("key-3", () -> "executed", () -> "fallback");
          assertThat(result).isEqualTo("executed");
          verify(bloomFilter).add("key-3");
      }

      @Test
      void actionThrowsDataIntegrityViolation_returnsFallback() {
          when(bloomFilter.contains("key-4")).thenReturn(false);
          String result = guard.executeOnce("key-4",
              () -> { throw new DataIntegrityViolationException("dup"); },
              () -> "concurrent-result");
          assertThat(result).isEqualTo("concurrent-result");
          verify(bloomFilter).add("key-4"); // still adds to BF since key is now in DB
      }

      @Test
      void actionThrowsBusinessException_notWrittenToBfOrFallback() {
          when(bloomFilter.contains("key-5")).thenReturn(false);
          assertThatThrownBy(() ->
              guard.executeOnce("key-5",
                  () -> { throw new RuntimeException("business error"); },
                  () -> "fallback"))
              .isInstanceOf(RuntimeException.class)
              .hasMessage("business error");
          verify(bloomFilter, never()).add(any());
      }

      @Test
      void redisUnavailable_degradesToDbPath_bfFallbackMetricRecorded() {
          when(bloomFilter.contains("key-6")).thenThrow(new RuntimeException("Redis down"));
          when(repo.existsByKey("key-6")).thenReturn(false);
          String result = guard.executeOnce("key-6", () -> "db-path-result", () -> "fallback");
          assertThat(result).isEqualTo("db-path-result");
          assertThat(meterRegistry.counter("shop.idempotency.bf.fallback", "service", "test-service").count()).isEqualTo(1.0);
      }

      @Test
      void redisUnavailable_dbConfirms_returnsFallback() {
          when(bloomFilter.contains("key-7")).thenThrow(new RuntimeException("Redis down"));
          when(repo.existsByKey("key-7")).thenReturn(true);
          String result = guard.executeOnce("key-7", () -> "should-not-run", () -> "cached");
          assertThat(result).isEqualTo("cached");
      }

      @Test
      void bfAddFailsAfterAction_doesNotPropagateException() {
          when(bloomFilter.contains("key-8")).thenReturn(false);
          doThrow(new RuntimeException("Redis flap")).when(bloomFilter).add("key-8");
          String result = guard.executeOnce("key-8", () -> "ok", () -> "fallback");
          assertThat(result).isEqualTo("ok"); // action result returned despite BF.add failure
      }

      @Test
      void fallbackThrows_exceptionPropagates() {
          when(bloomFilter.contains("key-9")).thenReturn(true);
          when(repo.existsByKey("key-9")).thenReturn(true);
          assertThatThrownBy(() ->
              guard.executeOnce("key-9", () -> "action", () -> { throw new IllegalStateException("no result"); }))
              .isInstanceOf(IllegalStateException.class);
      }

      @Test
      void nullResultFromAction_isValid() {
          when(bloomFilter.contains("key-10")).thenReturn(false);
          String result = guard.executeOnce("key-10", () -> null, () -> "fallback");
          assertThat(result).isNull();
          verify(bloomFilter).add("key-10");
      }
  }
  ```

- [ ] **Step 2: Run tests to verify they fail**

  Run: `./mvnw test -pl shop-common -Dtest=RedisIdempotencyGuardTest -q`
  Expected: FAIL (compilation error — RedisIdempotencyGuard does not exist)

- [ ] **Step 3: Create BloomFilterProperties**

  Create `shop-common/src/main/java/dev/meirong/shop/common/idempotency/BloomFilterProperties.java`:
  ```java
  package dev.meirong.shop.common.idempotency;

  import org.springframework.boot.context.properties.ConfigurationProperties;

  @ConfigurationProperties(prefix = "shop.idempotency.bloom-filter")
  public record BloomFilterProperties(
          boolean enabled,
          String redisKey,
          long expectedInsertions,
          double falseProbability,
          long redisTimeoutMs
  ) {
      public BloomFilterProperties {
          if (redisKey == null || redisKey.isBlank()) {
              throw new IllegalArgumentException("shop.idempotency.bloom-filter.redis-key must be set");
          }
      }
  }
  ```

- [ ] **Step 4: Create RedisIdempotencyGuard**

  Create `shop-common/src/main/java/dev/meirong/shop/common/idempotency/RedisIdempotencyGuard.java`:
  ```java
  package dev.meirong.shop.common.idempotency;

  import io.micrometer.core.instrument.Counter;
  import io.micrometer.core.instrument.MeterRegistry;
  import java.util.function.Supplier;
  import org.redisson.api.RBloomFilter;
  import org.slf4j.Logger;
  import org.slf4j.LoggerFactory;
  import org.springframework.dao.DataIntegrityViolationException;

  public class RedisIdempotencyGuard implements IdempotencyGuard {

      private static final Logger log = LoggerFactory.getLogger(RedisIdempotencyGuard.class);

      private final RBloomFilter<String> bloomFilter;
      private final IdempotencyRepository repository;
      private final Counter hitDuplicate;
      private final Counter hitFalsePositive;
      private final Counter miss;
      private final Counter fallback;

      public RedisIdempotencyGuard(RBloomFilter<String> bloomFilter,
                                   IdempotencyRepository repository,
                                   MeterRegistry meterRegistry,
                                   String serviceName) {
          this.bloomFilter = bloomFilter;
          this.repository = repository;
          this.hitDuplicate  = meterRegistry.counter("shop.idempotency.bf.hit",
                  "service", serviceName, "result", "duplicate");
          this.hitFalsePositive = meterRegistry.counter("shop.idempotency.bf.hit",
                  "service", serviceName, "result", "false_positive");
          this.miss     = meterRegistry.counter("shop.idempotency.bf.miss", "service", serviceName);
          this.fallback = meterRegistry.counter("shop.idempotency.bf.fallback", "service", serviceName);
      }

      @Override
      public <T> T executeOnce(String key, Supplier<T> action, Supplier<T> fallbackSupplier) {
          boolean bfSaysExists;
          try {
              bfSaysExists = bloomFilter.contains(key);
          } catch (Exception e) {
              log.warn("BF unavailable for key={}, degrading to DB path: {}", key, e.getMessage());
              fallback.increment();
              bfSaysExists = false; // treat as miss; DB will confirm
              return executeViaDb(key, action, fallbackSupplier);
          }

          if (!bfSaysExists) {
              miss.increment();
              return executeAction(key, action, fallbackSupplier);
          }

          // BF says might exist — confirm with DB
          if (repository.existsByKey(key)) {
              hitDuplicate.increment();
              return fallbackSupplier.get();
          }

          // BF false positive
          hitFalsePositive.increment();
          return executeAction(key, action, fallbackSupplier);
      }

      private <T> T executeViaDb(String key, Supplier<T> action, Supplier<T> fallbackSupplier) {
          if (repository.existsByKey(key)) {
              return fallbackSupplier.get();
          }
          return executeAction(key, action, fallbackSupplier);
      }

      private <T> T executeAction(String key, Supplier<T> action, Supplier<T> fallbackSupplier) {
          try {
              T result = action.get();
              addToBfQuietly(key);
              return result;
          } catch (DataIntegrityViolationException e) {
              log.debug("Concurrent duplicate write for key={}, calling fallback", key);
              addToBfQuietly(key); // key is now in DB; update BF
              return fallbackSupplier.get();
          }
      }

      private void addToBfQuietly(String key) {
          try {
              bloomFilter.add(key);
          } catch (Exception e) {
              log.warn("BF.add failed for key={}: {}. DB record exists; next request will use DB path.",
                      key, e.getMessage());
          }
      }
  }
  ```

- [ ] **Step 5: Run tests to verify they pass**

  Run: `./mvnw test -pl shop-common -Dtest=RedisIdempotencyGuardTest -q`
  Expected: All 10 tests PASS

- [ ] **Step 6: Commit**

  ```bash
  git add shop-common/src/main/java/dev/meirong/shop/common/idempotency/BloomFilterProperties.java \
          shop-common/src/main/java/dev/meirong/shop/common/idempotency/RedisIdempotencyGuard.java \
          shop-common/src/test/java/dev/meirong/shop/common/idempotency/RedisIdempotencyGuardTest.java
  git commit -m "feat(shop-common): implement RedisIdempotencyGuard with BF fast-path and metrics"
  ```

---

## Task 4: IdempotencyAutoConfiguration and AutoConfiguration.imports

**Files:**
- Create: `shop-common/src/main/java/dev/meirong/shop/common/idempotency/IdempotencyAutoConfiguration.java`
- Modify: `shop-common/src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`

- [ ] **Step 1: Create IdempotencyAutoConfiguration**

  Create `shop-common/src/main/java/dev/meirong/shop/common/idempotency/IdempotencyAutoConfiguration.java`:
  ```java
  package dev.meirong.shop.common.idempotency;

  import io.micrometer.core.instrument.MeterRegistry;
  import org.redisson.api.RBloomFilter;
  import org.redisson.api.RedissonClient;
  import org.springframework.beans.factory.annotation.Value;
  import org.springframework.boot.autoconfigure.AutoConfiguration;
  import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
  import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
  import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
  import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
  import org.springframework.boot.context.properties.EnableConfigurationProperties;
  import org.springframework.context.annotation.Bean;

  @AutoConfiguration
  @ConditionalOnClass(RedissonClient.class)
  @ConditionalOnProperty(prefix = "shop.idempotency.bloom-filter", name = "enabled", havingValue = "true", matchIfMissing = false)
  @EnableConfigurationProperties(BloomFilterProperties.class)
  public class IdempotencyAutoConfiguration {

      @Bean
      @ConditionalOnMissingBean
      @ConditionalOnBean({IdempotencyRepository.class, MeterRegistry.class})
      IdempotencyGuard idempotencyGuard(RedissonClient redissonClient,
                                        BloomFilterProperties props,
                                        IdempotencyRepository idempotencyRepository,
                                        MeterRegistry meterRegistry,
                                        @Value("${spring.application.name:unknown}") String serviceName) {
          RBloomFilter<String> bf = redissonClient.getBloomFilter(props.redisKey());
          bf.tryInit(props.expectedInsertions(), props.falseProbability());
          return new RedisIdempotencyGuard(bf, idempotencyRepository, meterRegistry, serviceName);
      }
  }
  ```

- [ ] **Step 2: Register in AutoConfiguration.imports**

  Edit `shop-common/src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports` to append:
  ```
  dev.meirong.shop.common.idempotency.IdempotencyAutoConfiguration
  ```

- [ ] **Step 3: Verify compilation**

  Run: `./mvnw compile -pl shop-common -am -q`
  Expected: BUILD SUCCESS

- [ ] **Step 4: Commit**

  ```bash
  git add shop-common/src/main/java/dev/meirong/shop/common/idempotency/IdempotencyAutoConfiguration.java \
          shop-common/src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports
  git commit -m "feat(shop-common): add IdempotencyAutoConfiguration with conditional BF bean registration"
  ```

---

## Task 5: wallet-service DB migration and JPA layer

**Files:**
- Create: `wallet-service/src/main/resources/db/migration/V3__wallet_idempotency_key.sql`
- Create: `wallet-service/src/main/java/dev/meirong/shop/wallet/domain/WalletIdempotencyKeyEntity.java`
- Create: `wallet-service/src/main/java/dev/meirong/shop/wallet/domain/WalletIdempotencyKeyRepository.java`

- [ ] **Step 1: Write failing test that the entity is queryable**

  Create `wallet-service/src/test/java/dev/meirong/shop/wallet/domain/WalletIdempotencyKeyRepositoryTest.java`:
  ```java
  package dev.meirong.shop.wallet.domain;

  import static org.assertj.core.api.Assertions.assertThat;
  import static org.assertj.core.api.Assertions.assertThatThrownBy;

  import org.junit.jupiter.api.Test;
  import org.junit.jupiter.api.extension.ExtendWith;
  import org.mockito.Mock;
  import org.mockito.junit.jupiter.MockitoExtension;
  import java.util.Optional;
  import static org.mockito.Mockito.when;

  @ExtendWith(MockitoExtension.class)
  class WalletIdempotencyKeyRepositoryTest {

      @Mock WalletIdempotencyKeyRepository repo;

      @Test
      void existsByKey_returnsTrueWhenPresent() {
          when(repo.existsByIdempotencyKey("key-abc")).thenReturn(true);
          assertThat(repo.existsByIdempotencyKey("key-abc")).isTrue();
      }

      @Test
      void existsByKey_returnsFalseWhenAbsent() {
          when(repo.existsByIdempotencyKey("unknown")).thenReturn(false);
          assertThat(repo.existsByIdempotencyKey("unknown")).isFalse();
      }
  }
  ```

- [ ] **Step 2: Run test to verify it fails**

  Run: `./mvnw test -pl wallet-service -Dtest=WalletIdempotencyKeyRepositoryTest -q`
  Expected: FAIL (compilation error)

- [ ] **Step 3: Create V3 migration**

  Create `wallet-service/src/main/resources/db/migration/V3__wallet_idempotency_key.sql`:
  ```sql
  CREATE TABLE IF NOT EXISTS wallet_idempotency_key (
      idempotency_key VARCHAR(128) NOT NULL PRIMARY KEY,
      transaction_id  VARCHAR(36)  NOT NULL,
      created_at      TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6)
  ) COMMENT 'Idempotency key store for wallet write operations';
  ```

- [ ] **Step 4: Create WalletIdempotencyKeyEntity**

  Create `wallet-service/src/main/java/dev/meirong/shop/wallet/domain/WalletIdempotencyKeyEntity.java`:
  ```java
  package dev.meirong.shop.wallet.domain;

  import jakarta.persistence.Column;
  import jakarta.persistence.Entity;
  import jakarta.persistence.Id;
  import jakarta.persistence.Table;
  import java.time.Instant;

  @Entity
  @Table(name = "wallet_idempotency_key")
  public class WalletIdempotencyKeyEntity {

      @Id
      @Column(name = "idempotency_key", length = 128)
      private String idempotencyKey;

      @Column(name = "transaction_id", length = 36, nullable = false)
      private String transactionId;

      @Column(name = "created_at", nullable = false)
      private Instant createdAt = Instant.now();

      protected WalletIdempotencyKeyEntity() {}

      public WalletIdempotencyKeyEntity(String idempotencyKey, String transactionId) {
          this.idempotencyKey = idempotencyKey;
          this.transactionId = transactionId;
      }

      public String getIdempotencyKey() { return idempotencyKey; }
      public String getTransactionId()  { return transactionId; }
  }
  ```

- [ ] **Step 5: Create WalletIdempotencyKeyRepository**

  Create `wallet-service/src/main/java/dev/meirong/shop/wallet/domain/WalletIdempotencyKeyRepository.java`:
  ```java
  package dev.meirong.shop.wallet.domain;

  import dev.meirong.shop.common.idempotency.IdempotencyRepository;
  import java.util.Optional;
  import org.springframework.data.jpa.repository.JpaRepository;

  public interface WalletIdempotencyKeyRepository
          extends JpaRepository<WalletIdempotencyKeyEntity, String>, IdempotencyRepository {

      Optional<WalletIdempotencyKeyEntity> findByIdempotencyKey(String idempotencyKey);

      /** Satisfies {@link IdempotencyRepository#existsByKey}. */
      @Override
      default boolean existsByKey(String key) {
          return existsByIdempotencyKey(key);
      }

      boolean existsByIdempotencyKey(String idempotencyKey);
  }
  ```

- [ ] **Step 6: Run tests to verify they pass**

  Run: `./mvnw test -pl wallet-service -Dtest=WalletIdempotencyKeyRepositoryTest -q`
  Expected: PASS

- [ ] **Step 7: Commit**

  ```bash
  git add wallet-service/src/main/resources/db/migration/V3__wallet_idempotency_key.sql \
          wallet-service/src/main/java/dev/meirong/shop/wallet/domain/ \
          wallet-service/src/test/java/dev/meirong/shop/wallet/domain/
  git commit -m "feat(wallet-service): add wallet_idempotency_key table, entity and repository"
  ```

---

## Task 6: wallet-service — WalletApplicationService and WalletController integration

**Files:**
- Modify: `wallet-service/src/main/java/dev/meirong/shop/wallet/service/WalletApplicationService.java`
- Modify: `wallet-service/src/main/java/dev/meirong/shop/wallet/controller/WalletController.java`
- Modify: `wallet-service/src/main/resources/application.yml`
- Expand: `wallet-service/src/test/java/dev/meirong/shop/wallet/service/WalletApplicationServiceTest.java`

- [ ] **Step 1: Write new failing tests for idempotency flows**

  Add to `WalletApplicationServiceTest.java` (after existing tests):
  ```java
  // --- Idempotency tests ---

  @Test
  void depositWithIdempotency_firstCall_savesKeyAndReturnsResult() {
      String idempotencyKey = "idem-key-001";
      WalletAccountEntity account = new WalletAccountEntity("player-1", BigDecimal.valueOf(100));
      when(accountRepository.findById("player-1")).thenReturn(Optional.of(account));
      when(accountRepository.save(any())).thenReturn(account);
      WalletTransactionEntity tx = new WalletTransactionEntity(
              "player-1", "DEPOSIT", BigDecimal.TEN, "USD", "COMPLETED", "ref-001");
      when(transactionRepository.save(any())).thenReturn(tx);
      when(stripeGateway.createDeposit(any(), any(), any()))
              .thenReturn(new StripeGateway.PaymentReference("ref-001"));
      when(idempotencyKeyRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

      WalletApi.TransactionResponse result = service.depositWithIdempotency(
              new WalletApi.DepositRequest("player-1", BigDecimal.TEN, "USD"), idempotencyKey);

      assertThat(result).isNotNull();
      verify(idempotencyKeyRepository).save(argThat(e ->
              e.getIdempotencyKey().equals(idempotencyKey) &&
              e.getTransactionId().equals(tx.getId())));
  }

  @Test
  void findByIdempotencyKey_returnsExistingTransaction() {
      WalletIdempotencyKeyEntity keyEntity = new WalletIdempotencyKeyEntity("idem-key-002", "tx-001");
      WalletTransactionEntity tx = new WalletTransactionEntity(
              "player-1", "DEPOSIT", BigDecimal.TEN, "USD", "COMPLETED", "ref");
      when(idempotencyKeyRepository.findByIdempotencyKey("idem-key-002"))
              .thenReturn(Optional.of(keyEntity));
      when(transactionRepository.findById("tx-001")).thenReturn(Optional.of(tx));

      WalletApi.TransactionResponse result = service.findByIdempotencyKey("idem-key-002");
      assertThat(result).isNotNull();
  }
  ```

  Also add `@Mock WalletIdempotencyKeyRepository idempotencyKeyRepository;` field and update the `setUp()` to pass it to the service constructor.

- [ ] **Step 2: Run new tests to verify they fail**

  Run: `./mvnw test -pl wallet-service -Dtest=WalletApplicationServiceTest -q`
  Expected: FAIL (depositWithIdempotency method not found)

- [ ] **Step 3: Add idempotency methods to WalletApplicationService**

  Add the following to `WalletApplicationService.java`:

  1. Add `WalletIdempotencyKeyRepository idempotencyKeyRepository` field and constructor parameter.

  2. Add method `depositWithIdempotency`:
  ```java
  @Transactional
  public WalletApi.TransactionResponse depositWithIdempotency(WalletApi.DepositRequest request, String idempotencyKey) {
      WalletAccountEntity account = accountRepository.findById(request.buyerId())
              .orElseGet(() -> accountRepository.save(new WalletAccountEntity(request.buyerId(), BigDecimal.ZERO)));
      StripeGateway.PaymentReference ref = stripeGateway.createDeposit(request.buyerId(), request.amount(), request.currency());
      account.credit(request.amount());
      accountRepository.save(account);
      WalletTransactionEntity transaction = transactionRepository.save(
              new WalletTransactionEntity(request.buyerId(), "DEPOSIT", request.amount(), request.currency(), "COMPLETED", ref.providerReference()));
      // Save idempotency key within same transaction — DIVE on duplicate signals concurrent request
      idempotencyKeyRepository.save(new WalletIdempotencyKeyEntity(idempotencyKey, transaction.getId()));
      publishableEvent(transaction);
      return toTransactionResponse(transaction);
  }
  ```

  3. Add method `findByIdempotencyKey`:
  ```java
  @Transactional(readOnly = true)
  public WalletApi.TransactionResponse findByIdempotencyKey(String idempotencyKey) {
      return idempotencyKeyRepository.findByIdempotencyKey(idempotencyKey)
              .flatMap(key -> transactionRepository.findById(key.getTransactionId()))
              .map(this::toTransactionResponse)
              .orElseThrow(() -> new BusinessException(CommonErrorCode.NOT_FOUND,
                      "No transaction found for idempotency key: " + idempotencyKey));
  }
  ```

- [ ] **Step 4: Run tests to verify they pass**

  Run: `./mvnw test -pl wallet-service -Dtest=WalletApplicationServiceTest -q`
  Expected: PASS

- [ ] **Step 5: Update WalletController to accept Idempotency-Key header**

  In `WalletController.java`, modify the `deposit` endpoint:
  ```java
  @PostMapping("/deposit/create")
  public ApiResponse<WalletApi.TransactionResponse> deposit(
          @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
          @Valid @RequestBody WalletApi.DepositRequest request) {
      if (idempotencyKey != null && !idempotencyKey.isBlank()) {
          return ApiResponse.success(
              idempotencyGuard.executeOnce(
                  idempotencyKey,
                  () -> service.depositWithIdempotency(request, idempotencyKey),
                  () -> service.findByIdempotencyKey(idempotencyKey)));
      }
      return ApiResponse.success(service.deposit(request));
  }
  ```

  Add `IdempotencyGuard idempotencyGuard` field + constructor parameter. Import `dev.meirong.shop.common.idempotency.IdempotencyGuard`.

- [ ] **Step 6: Add BF config to wallet-service application.yml**

  Append to `wallet-service/src/main/resources/application.yml`:
  ```yaml
    idempotency:
      bloom-filter:
        enabled: true
        redis-key: "shop:wallet:idempotency:bf"
        expected-insertions: 10_000_000
        false-probability: 0.001
        redis-timeout-ms: 100
  ```
  (Nested under the existing `shop:` key)

- [ ] **Step 7: Verify wallet-service compiles and tests pass**

  Run: `./mvnw test -pl wallet-service -am -q`
  Expected: BUILD SUCCESS

- [ ] **Step 8: Commit**

  ```bash
  git add wallet-service/src/main/java/dev/meirong/shop/wallet/ \
          wallet-service/src/test/java/dev/meirong/shop/wallet/ \
          wallet-service/src/main/resources/application.yml
  git commit -m "feat(wallet-service): integrate IdempotencyGuard for deposit endpoint"
  ```

---

## Task 7: promotion-service — DB migration, repository, WelcomeCouponListener refactor

**Files:**
- Create: `promotion-service/src/main/resources/db/migration/V5__promotion_idempotency_key.sql`
- Create: `promotion-service/src/main/java/dev/meirong/shop/promotion/domain/PromotionIdempotencyKeyEntity.java`
- Create: `promotion-service/src/main/java/dev/meirong/shop/promotion/domain/PromotionIdempotencyKeyRepository.java`
- Modify: `promotion-service/src/main/java/dev/meirong/shop/promotion/messaging/WelcomeCouponListener.java`
- Modify: `promotion-service/src/main/resources/application.yml`
- Expand: `promotion-service/src/test/java/dev/meirong/shop/promotion/messaging/WelcomeCouponListenerTest.java`

- [ ] **Step 1: Write new failing tests**

  Add to `WelcomeCouponListenerTest.java`:
  ```java
  @Mock private PromotionIdempotencyKeyRepository promotionIdempotencyKeyRepository;
  @Mock private IdempotencyGuard idempotencyGuard;

  // Update setUp() to inject new mocks into WelcomeCouponListener

  @Test
  void onUserRegistered_firstCall_executesViaIdempotencyGuard() throws Exception {
      BuyerRegisteredEventData data = new BuyerRegisteredEventData("player-123", "user", "e@e.com");
      EventEnvelope<BuyerRegisteredEventData> event = new EventEnvelope<>(
              UUID.randomUUID().toString(), "auth", "USER_REGISTERED", Instant.now(), data);
      String payload = objectMapper.writeValueAsString(event);

      when(idempotencyGuard.executeOnce(anyString(), any(), any()))
              .thenAnswer(inv -> ((java.util.function.Supplier<?>) inv.getArgument(1)).get());
      when(couponTemplateService.createTemplate(any(), any(), any(), any(), any(), any(), any(), anyInt(), anyInt(), anyInt()))
              .thenReturn(new CouponTemplateEntity());

      listener.onUserRegistered(payload);

      verify(idempotencyGuard).executeOnce(
              eq("WELCOME_COUPON:player-123"), any(), any());
  }

  @Test
  void onUserRegistered_duplicateCall_usesIdempotencyGuardFallback() throws Exception {
      BuyerRegisteredEventData data = new BuyerRegisteredEventData("player-999", "user", "e@e.com");
      EventEnvelope<BuyerRegisteredEventData> event = new EventEnvelope<>(
              UUID.randomUUID().toString(), "auth", "USER_REGISTERED", Instant.now(), data);
      String payload = objectMapper.writeValueAsString(event);

      // Guard returns fallback (null for fire-and-forget) — action not called
      when(idempotencyGuard.executeOnce(anyString(), any(), any())).thenReturn(null);

      listener.onUserRegistered(payload);

      verify(couponRepository, never()).save(any());
  }
  ```

- [ ] **Step 2: Run new tests to verify they fail**

  Run: `./mvnw test -pl promotion-service -Dtest=WelcomeCouponListenerTest -q`
  Expected: FAIL

- [ ] **Step 3: Create V5 migration**

  Create `promotion-service/src/main/resources/db/migration/V5__promotion_idempotency_key.sql`:
  ```sql
  CREATE TABLE IF NOT EXISTS promotion_idempotency_key (
      idempotency_key VARCHAR(128) NOT NULL PRIMARY KEY,
      created_at      TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6)
  ) COMMENT 'Idempotency key store for promotion Kafka consumers';
  ```

- [ ] **Step 4: Create PromotionIdempotencyKeyEntity**

  Create `promotion-service/src/main/java/dev/meirong/shop/promotion/domain/PromotionIdempotencyKeyEntity.java`:
  ```java
  package dev.meirong.shop.promotion.domain;

  import jakarta.persistence.Column;
  import jakarta.persistence.Entity;
  import jakarta.persistence.Id;
  import jakarta.persistence.Table;
  import java.time.Instant;

  @Entity
  @Table(name = "promotion_idempotency_key")
  public class PromotionIdempotencyKeyEntity {

      @Id
      @Column(name = "idempotency_key", length = 128)
      private String idempotencyKey;

      @Column(name = "created_at", nullable = false)
      private Instant createdAt = Instant.now();

      protected PromotionIdempotencyKeyEntity() {}

      public PromotionIdempotencyKeyEntity(String idempotencyKey) {
          this.idempotencyKey = idempotencyKey;
      }

      public String getIdempotencyKey() { return idempotencyKey; }
  }
  ```

- [ ] **Step 5: Create PromotionIdempotencyKeyRepository**

  Create `promotion-service/src/main/java/dev/meirong/shop/promotion/domain/PromotionIdempotencyKeyRepository.java`:
  ```java
  package dev.meirong.shop.promotion.domain;

  import dev.meirong.shop.common.idempotency.IdempotencyRepository;
  import org.springframework.data.jpa.repository.JpaRepository;

  public interface PromotionIdempotencyKeyRepository
          extends JpaRepository<PromotionIdempotencyKeyEntity, String>, IdempotencyRepository {

      @Override
      default boolean existsByKey(String key) {
          return existsById(key);
      }

      /** Saves the key; throws {@code DataIntegrityViolationException} on duplicate. */
      default void saveKey(String key) {
          save(new PromotionIdempotencyKeyEntity(key));
      }
  }
  ```

- [ ] **Step 6: Refactor WelcomeCouponListener**

  Replace the current `WelcomeCouponListener.java` with:
  ```java
  package dev.meirong.shop.promotion.messaging;

  import com.fasterxml.jackson.core.type.TypeReference;
  import com.fasterxml.jackson.databind.ObjectMapper;
  import dev.meirong.shop.common.error.BusinessException;
  import dev.meirong.shop.common.idempotency.IdempotencyGuard;
  import dev.meirong.shop.contracts.event.EventEnvelope;
  import dev.meirong.shop.contracts.event.BuyerRegisteredEventData;
  import dev.meirong.shop.promotion.domain.CouponEntity;
  import dev.meirong.shop.promotion.domain.CouponRepository;
  import dev.meirong.shop.promotion.domain.PromotionIdempotencyKeyRepository;
  import dev.meirong.shop.promotion.service.CouponTemplateService;
  import java.io.IOException;
  import java.math.BigDecimal;
  import java.time.Instant;
  import java.time.temporal.ChronoUnit;
  import org.slf4j.Logger;
  import org.slf4j.LoggerFactory;
  import org.springframework.dao.DataAccessException;
  import org.springframework.kafka.annotation.KafkaListener;
  import org.springframework.stereotype.Component;
  import org.springframework.transaction.annotation.Transactional;

  @Component
  public class WelcomeCouponListener {

      private static final Logger log = LoggerFactory.getLogger(WelcomeCouponListener.class);
      private static final String SYSTEM_SELLER = "SYSTEM";

      private final ObjectMapper objectMapper;
      private final CouponRepository couponRepository;
      private final CouponTemplateService couponTemplateService;
      private final PromotionIdempotencyKeyRepository idempotencyKeyRepository;
      private final IdempotencyGuard idempotencyGuard;

      public WelcomeCouponListener(ObjectMapper objectMapper,
                                   CouponRepository couponRepository,
                                   CouponTemplateService couponTemplateService,
                                   PromotionIdempotencyKeyRepository idempotencyKeyRepository,
                                   IdempotencyGuard idempotencyGuard) {
          this.objectMapper = objectMapper;
          this.couponRepository = couponRepository;
          this.couponTemplateService = couponTemplateService;
          this.idempotencyKeyRepository = idempotencyKeyRepository;
          this.idempotencyGuard = idempotencyGuard;
      }

      @KafkaListener(topics = "${shop.kafka.buyer-registered-topic}", groupId = "${spring.application.name}")
      @Transactional
      public void onUserRegistered(String payload) {
          try {
              EventEnvelope<BuyerRegisteredEventData> envelope = objectMapper.readValue(
                      payload, new TypeReference<>() {});
              String buyerId = envelope.data().buyerId();
              String key = "WELCOME_COUPON:" + buyerId;

              idempotencyGuard.executeOnce(key,
                  () -> {
                      // Save idempotency key first — DIVE signals duplicate to the guard
                      idempotencyKeyRepository.saveKey(key);
                      issueWelcomeCoupons(buyerId);
                      return null;
                  },
                  () -> {
                      log.info("Welcome coupons already issued for player={}, skipping", buyerId);
                      return null;
                  });
          } catch (IOException | BusinessException | DataAccessException e) {
              log.error("Failed to process user registered event: {}", e.getMessage(), e);
          }
      }

      private void issueWelcomeCoupons(String buyerId) {
          String baseCode = "WELCOME-" + buyerId.substring(0, Math.min(8, buyerId.length())).toUpperCase();
          Instant now = Instant.now();

          couponRepository.save(new CouponEntity(SYSTEM_SELLER, baseCode + "-5OFF", "FIXED",
                  new BigDecimal("5.00"), BigDecimal.ZERO, null, 1, now.plus(14, ChronoUnit.DAYS)));
          couponRepository.save(new CouponEntity(SYSTEM_SELLER, baseCode + "-SHIP", "FIXED",
                  new BigDecimal("10.00"), BigDecimal.ZERO, null, 1, now.plus(30, ChronoUnit.DAYS)));
          couponRepository.save(new CouponEntity(SYSTEM_SELLER, baseCode + "-9PCT", "PERCENTAGE",
                  new BigDecimal("9.00"), BigDecimal.ZERO, new BigDecimal("20.00"), 1,
                  now.plus(14, ChronoUnit.DAYS)));

          createWelcomeInstances(buyerId, baseCode, now);
          log.info("Issued welcome coupons for player={}", buyerId);
      }

      private void createWelcomeInstances(String buyerId, String baseCode, Instant now) {
          try {
              var t1 = couponTemplateService.createTemplate(SYSTEM_SELLER, "WELCOME-5OFF",
                      "$5 Off Welcome", "FIXED", new BigDecimal("5.00"), BigDecimal.ZERO, null, 0, 1, 14);
              couponTemplateService.issueToBuyerWithCode(t1.getId(), buyerId,
                      baseCode + "-5OFF-I", now.plus(14, ChronoUnit.DAYS));
              var t2 = couponTemplateService.createTemplate(SYSTEM_SELLER, "WELCOME-SHIP",
                      "Free Shipping", "FIXED", new BigDecimal("10.00"), BigDecimal.ZERO, null, 0, 1, 30);
              couponTemplateService.issueToBuyerWithCode(t2.getId(), buyerId,
                      baseCode + "-SHIP-I", now.plus(30, ChronoUnit.DAYS));
              var t3 = couponTemplateService.createTemplate(SYSTEM_SELLER, "WELCOME-9PCT",
                      "9% Off Welcome", "PERCENTAGE", new BigDecimal("9.00"), BigDecimal.ZERO,
                      new BigDecimal("20.00"), 0, 1, 14);
              couponTemplateService.issueToBuyerWithCode(t3.getId(), buyerId,
                      baseCode + "-9PCT-I", now.plus(14, ChronoUnit.DAYS));
          } catch (BusinessException | DataAccessException e) {
              log.debug("Welcome coupon instance creation note: {}", e.getMessage());
          }
      }
  }
  ```

- [ ] **Step 7: Add BF config to promotion-service application.yml**

  Append to `promotion-service/src/main/resources/application.yml` under the `shop:` key:
  ```yaml
    idempotency:
      bloom-filter:
        enabled: true
        redis-key: "shop:promotion:idempotency:bf"
        expected-insertions: 100_000_000
        false-probability: 0.001
        redis-timeout-ms: 100
  ```

- [ ] **Step 8: Run all promotion-service tests**

  Run: `./mvnw test -pl promotion-service -am -q`
  Expected: PASS

- [ ] **Step 9: Commit**

  ```bash
  git add promotion-service/src/main/resources/db/migration/ \
          promotion-service/src/main/java/dev/meirong/shop/promotion/domain/ \
          promotion-service/src/main/java/dev/meirong/shop/promotion/messaging/ \
          promotion-service/src/main/resources/application.yml \
          promotion-service/src/test/java/dev/meirong/shop/promotion/messaging/
  git commit -m "feat(promotion-service): refactor WelcomeCouponListener to use IdempotencyGuard"
  ```

---

## Task 8: ArchUnit rules 3 and 4

**Files:**
- Modify: `architecture-tests/src/test/java/dev/meirong/shop/architecture/ArchitectureRulesTest.java`

- [ ] **Step 1: Write new ArchUnit rules**

  Add the following imports to `ArchitectureRulesTest.java`:
  ```java
  import com.tngtech.archunit.base.DescribedPredicate;
  import com.tngtech.archunit.core.domain.JavaClass;
  import dev.meirong.shop.common.idempotency.IdempotencyExempt;
  import dev.meirong.shop.common.idempotency.IdempotencyGuard;
  import org.springframework.kafka.annotation.KafkaListener;
  import org.springframework.transaction.annotation.Transactional;
  import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
  import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.methods;
  ```

  Add the following rules after the existing ones in `ArchitectureRulesTest.java`:
  ```java
  @ArchTest
  static final ArchRule kafka_listeners_must_use_idempotency_guard =
      classes()
          .that(new DescribedPredicate<JavaClass>(
                  "have @KafkaListener methods and are not annotated with @IdempotencyExempt") {
              @Override
              public boolean test(JavaClass javaClass) {
                  return javaClass.getMethods().stream()
                          .anyMatch(m -> m.isAnnotatedWith(KafkaListener.class))
                      && !javaClass.isAnnotatedWith(IdempotencyExempt.class);
              }
          })
          .should().dependOnClassesThat().haveFullyQualifiedName(IdempotencyGuard.class.getName())
          .because("All @KafkaListener classes must inject IdempotencyGuard unless annotated with @IdempotencyExempt")
          .allowEmptyShould(true);

  @ArchTest
  static final ArchRule kafka_listener_methods_must_be_transactional =
      methods()
          .that().areAnnotatedWith(KafkaListener.class)
          .should().beAnnotatedWith(Transactional.class)
          .because("@KafkaListener methods must be @Transactional so idempotency DB writes are atomic with business writes")
          .allowEmptyShould(true);
  ```

- [ ] **Step 2: Run ArchUnit tests**

  Run: `./mvnw test -pl architecture-tests -am -q`
  Expected: PASS (WelcomeCouponListener now injects IdempotencyGuard and is @Transactional)

- [ ] **Step 3: Commit**

  ```bash
  git add architecture-tests/src/test/java/dev/meirong/shop/architecture/ArchitectureRulesTest.java
  git commit -m "feat(architecture-tests): add ArchUnit rules for IdempotencyGuard and @Transactional enforcement on KafkaListeners"
  ```

---

## Task 9: Final verification across all modules

- [ ] **Step 1: Build all modules**

  Run: `./mvnw clean verify -q`
  Expected: BUILD SUCCESS across all modules

- [ ] **Step 2: Run architecture tests explicitly**

  Run: `./mvnw test -pl architecture-tests -am`
  Expected: All 6 ArchUnit rules PASS (4 existing + 2 new)

- [ ] **Step 3: Commit if any fixes needed, then tag**

  ```bash
  git commit -m "chore: final cross-module verification for bloom filter idempotency" --allow-empty
  ```
