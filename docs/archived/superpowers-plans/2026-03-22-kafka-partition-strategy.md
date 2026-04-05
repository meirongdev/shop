# Kafka Producer Partition Strategy Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a `KafkaEventPublisher` wrapper in `shop-common` that routes Kafka messages through a configurable per-topic partition strategy, fixing missing partition keys in `OrderOutboxPublisher` and `WalletOutboxPublisher`.

**Architecture:** A `TopicPartitionStrategy` SPI with built-in `KeyedStrategy` / `UnkeyedStrategy` is auto-configured in `shop-common`. `KafkaEventPublisher` wraps `KafkaTemplate` and dispatches each call through the per-topic strategy registry, fail-fast on missing keys and unknown strategy names. All four Outbox Publishers are migrated from `KafkaTemplate` to `KafkaEventPublisher`, and each service's `application.yml` is updated with the partitioning config in the same commit as the publisher change.

**Tech Stack:** Java 25, Spring Boot 3.5, Spring Kafka 3.x, JUnit 5, Mockito, AssertJ, `ApplicationContextRunner`

---

## File Map

**Create (shop-common):**
- `shop-common/src/main/java/dev/meirong/shop/common/kafka/SendSpec.java`
- `shop-common/src/main/java/dev/meirong/shop/common/kafka/TopicPartitionStrategy.java`
- `shop-common/src/main/java/dev/meirong/shop/common/kafka/strategy/KeyedStrategy.java`
- `shop-common/src/main/java/dev/meirong/shop/common/kafka/strategy/UnkeyedStrategy.java`
- `shop-common/src/main/java/dev/meirong/shop/common/kafka/KafkaPartitioningProperties.java`
- `shop-common/src/main/java/dev/meirong/shop/common/kafka/KafkaEventPublisher.java`
- `shop-common/src/main/java/dev/meirong/shop/common/kafka/KafkaPartitioningAutoConfiguration.java`
- `shop-common/src/test/java/dev/meirong/shop/common/kafka/strategy/KeyedStrategyTest.java`
- `shop-common/src/test/java/dev/meirong/shop/common/kafka/strategy/UnkeyedStrategyTest.java`
- `shop-common/src/test/java/dev/meirong/shop/common/kafka/KafkaEventPublisherTest.java`
- `shop-common/src/test/java/dev/meirong/shop/common/kafka/KafkaPartitioningAutoConfigurationTest.java`
- `order-service/src/test/java/dev/meirong/shop/order/service/OrderOutboxPublisherTest.java`
- `wallet-service/src/test/java/dev/meirong/shop/wallet/service/WalletOutboxPublisherTest.java`
- `activity-service/src/test/java/dev/meirong/shop/activity/service/ActivityOutboxPublisherTest.java`

**Modify:**
- `shop-common/pom.xml`
- `shop-common/src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`
- `wallet-service/src/main/java/dev/meirong/shop/wallet/domain/WalletOutboxEventEntity.java`
- `order-service/src/main/java/dev/meirong/shop/order/service/OrderOutboxPublisher.java`
- `order-service/src/main/resources/application.yml`
- `wallet-service/src/main/java/dev/meirong/shop/wallet/service/WalletOutboxPublisher.java`
- `wallet-service/src/main/resources/application.yml`
- `marketplace-service/src/main/java/dev/meirong/shop/marketplace/service/MarketplaceOutboxPublisher.java`
- `marketplace-service/src/main/resources/application.yml`
- `marketplace-service/src/test/java/dev/meirong/shop/marketplace/service/MarketplaceOutboxPublisherTest.java`
- `activity-service/src/main/java/dev/meirong/shop/activity/service/ActivityOutboxPublisher.java`
- `activity-service/src/main/resources/application.yml`

---

## Task 1: `shop-common/pom.xml` — 添加编译和测试依赖

**Files:**
- Modify: `shop-common/pom.xml`

- [ ] **Step 1: 在 `<dependencies>` 块中添加两个依赖**

```xml
<!-- spring-kafka: optional，仅 shop-common 编译期可见，不传递给下游 -->
<dependency>
    <groupId>org.springframework.kafka</groupId>
    <artifactId>spring-kafka</artifactId>
    <optional>true</optional>
</dependency>

<!-- 测试依赖：JUnit5 + Mockito + AssertJ + ApplicationContextRunner -->
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-test</artifactId>
    <scope>test</scope>
</dependency>
```

- [ ] **Step 2: 验证编译通过**

```bash
./mvnw compile -pl shop-common -q
```

Expected: BUILD SUCCESS，无报错

- [ ] **Step 3: Commit**

```bash
git add shop-common/pom.xml
git commit -m "build(shop-common): add spring-kafka optional + test deps for partition strategy"
```

---

## Task 2: SPI 核心类型 — `SendSpec` + `TopicPartitionStrategy`

**Files:**
- Create: `shop-common/src/main/java/dev/meirong/shop/common/kafka/SendSpec.java`
- Create: `shop-common/src/main/java/dev/meirong/shop/common/kafka/TopicPartitionStrategy.java`

- [ ] **Step 1: 创建 `SendSpec`**

```java
// shop-common/src/main/java/dev/meirong/shop/common/kafka/SendSpec.java
package dev.meirong.shop.common.kafka;

import org.springframework.lang.Nullable;

public record SendSpec(String topic, @Nullable String key, String payload) {}
```

- [ ] **Step 2: 创建 `TopicPartitionStrategy`**

```java
// shop-common/src/main/java/dev/meirong/shop/common/kafka/TopicPartitionStrategy.java
package dev.meirong.shop.common.kafka;

import org.springframework.lang.Nullable;

@FunctionalInterface
public interface TopicPartitionStrategy {
    SendSpec apply(String topic, @Nullable String suggestedKey, String payload);
}
```

- [ ] **Step 3: 验证编译**

```bash
./mvnw compile -pl shop-common -q
```

Expected: BUILD SUCCESS

- [ ] **Step 4: Commit**

```bash
git add shop-common/src/main/java/dev/meirong/shop/common/kafka/
git commit -m "feat(shop-common): add TopicPartitionStrategy SPI and SendSpec record"
```

---

## Task 3: `KeyedStrategy` (TDD)

**Files:**
- Create: `shop-common/src/main/java/dev/meirong/shop/common/kafka/strategy/KeyedStrategy.java`
- Create: `shop-common/src/test/java/dev/meirong/shop/common/kafka/strategy/KeyedStrategyTest.java`

- [ ] **Step 1: 写失败测试**

```java
// shop-common/src/test/java/dev/meirong/shop/common/kafka/strategy/KeyedStrategyTest.java
package dev.meirong.shop.common.kafka.strategy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.meirong.shop.common.kafka.SendSpec;
import org.junit.jupiter.api.Test;

class KeyedStrategyTest {

    private final KeyedStrategy strategy = new KeyedStrategy();

    @Test
    void apply_withValidKey_returnsSendSpecWithKey() {
        SendSpec spec = strategy.apply("order.events.v1", "order-123", "{}");

        assertThat(spec.topic()).isEqualTo("order.events.v1");
        assertThat(spec.key()).isEqualTo("order-123");
        assertThat(spec.payload()).isEqualTo("{}");
    }

    @Test
    void apply_withNullKey_throwsIllegalArgumentException() {
        assertThatThrownBy(() -> strategy.apply("order.events.v1", null, "{}"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("order.events.v1")
                .hasMessageContaining("strategy=keyed");
    }

    @Test
    void apply_withBlankKey_throwsIllegalArgumentException() {
        assertThatThrownBy(() -> strategy.apply("order.events.v1", "   ", "{}"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
```

- [ ] **Step 2: 运行测试，确认失败**

```bash
./mvnw test -pl shop-common -Dtest=KeyedStrategyTest -q
```

Expected: FAIL（`KeyedStrategy` 类不存在）

- [ ] **Step 3: 实现 `KeyedStrategy`**

```java
// shop-common/src/main/java/dev/meirong/shop/common/kafka/strategy/KeyedStrategy.java
package dev.meirong.shop.common.kafka.strategy;

import dev.meirong.shop.common.kafka.SendSpec;
import dev.meirong.shop.common.kafka.TopicPartitionStrategy;
import org.springframework.lang.Nullable;

public final class KeyedStrategy implements TopicPartitionStrategy {

    @Override
    public SendSpec apply(String topic, @Nullable String suggestedKey, String payload) {
        if (suggestedKey == null || suggestedKey.isBlank()) {
            throw new IllegalArgumentException(
                    "Topic '" + topic + "' requires a non-blank partition key (strategy=keyed)");
        }
        return new SendSpec(topic, suggestedKey, payload);
    }
}
```

- [ ] **Step 4: 运行测试，确认通过**

```bash
./mvnw test -pl shop-common -Dtest=KeyedStrategyTest -q
```

Expected: BUILD SUCCESS, Tests run: 3, Failures: 0

- [ ] **Step 5: Commit**

```bash
git add shop-common/src/main/java/dev/meirong/shop/common/kafka/strategy/KeyedStrategy.java \
        shop-common/src/test/java/dev/meirong/shop/common/kafka/strategy/KeyedStrategyTest.java
git commit -m "feat(shop-common): add KeyedStrategy with fail-fast key validation"
```

---

## Task 4: `UnkeyedStrategy` (TDD)

**Files:**
- Create: `shop-common/src/main/java/dev/meirong/shop/common/kafka/strategy/UnkeyedStrategy.java`
- Create: `shop-common/src/test/java/dev/meirong/shop/common/kafka/strategy/UnkeyedStrategyTest.java`

- [ ] **Step 1: 写失败测试**

```java
// shop-common/src/test/java/dev/meirong/shop/common/kafka/strategy/UnkeyedStrategyTest.java
package dev.meirong.shop.common.kafka.strategy;

import static org.assertj.core.api.Assertions.assertThat;

import dev.meirong.shop.common.kafka.SendSpec;
import org.junit.jupiter.api.Test;

class UnkeyedStrategyTest {

    private final UnkeyedStrategy strategy = new UnkeyedStrategy();

    @Test
    void apply_alwaysReturnsNullKey() {
        SendSpec spec = strategy.apply("some.topic.v1", "ignored-key", "{}");

        assertThat(spec.key()).isNull();
        assertThat(spec.topic()).isEqualTo("some.topic.v1");
        assertThat(spec.payload()).isEqualTo("{}");
    }

    @Test
    void apply_withNullSuggestedKey_returnsNullKey() {
        SendSpec spec = strategy.apply("some.topic.v1", null, "{}");

        assertThat(spec.key()).isNull();
    }
}
```

- [ ] **Step 2: 运行测试，确认失败**

```bash
./mvnw test -pl shop-common -Dtest=UnkeyedStrategyTest -q
```

Expected: FAIL

- [ ] **Step 3: 实现 `UnkeyedStrategy`**

```java
// shop-common/src/main/java/dev/meirong/shop/common/kafka/strategy/UnkeyedStrategy.java
package dev.meirong.shop.common.kafka.strategy;

import dev.meirong.shop.common.kafka.SendSpec;
import dev.meirong.shop.common.kafka.TopicPartitionStrategy;
import org.springframework.lang.Nullable;

public final class UnkeyedStrategy implements TopicPartitionStrategy {

    @Override
    public SendSpec apply(String topic, @Nullable String suggestedKey, String payload) {
        return new SendSpec(topic, null, payload);
    }
}
```

- [ ] **Step 4: 运行测试，确认通过**

```bash
./mvnw test -pl shop-common -Dtest=UnkeyedStrategyTest -q
```

Expected: BUILD SUCCESS, Tests run: 2, Failures: 0

- [ ] **Step 5: Commit**

```bash
git add shop-common/src/main/java/dev/meirong/shop/common/kafka/strategy/UnkeyedStrategy.java \
        shop-common/src/test/java/dev/meirong/shop/common/kafka/strategy/UnkeyedStrategyTest.java
git commit -m "feat(shop-common): add UnkeyedStrategy (delegates to Kafka UniformStickyPartitioner)"
```

---

## Task 5: `KafkaPartitioningProperties`

**Files:**
- Create: `shop-common/src/main/java/dev/meirong/shop/common/kafka/KafkaPartitioningProperties.java`

无独立测试（属性绑定在 Task 7 的 `ApplicationContextRunner` 中覆盖）。

- [ ] **Step 1: 创建属性类**

```java
// shop-common/src/main/java/dev/meirong/shop/common/kafka/KafkaPartitioningProperties.java
package dev.meirong.shop.common.kafka;

import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "shop.kafka.partitioning")
public class KafkaPartitioningProperties {

    private String defaultStrategy = "unkeyed";
    private Map<String, String> topics = new LinkedHashMap<>();

    public String getDefaultStrategy() {
        return defaultStrategy;
    }

    public void setDefaultStrategy(String defaultStrategy) {
        this.defaultStrategy = defaultStrategy;
    }

    public Map<String, String> getTopics() {
        return topics;
    }

    public void setTopics(Map<String, String> topics) {
        this.topics = topics == null ? new LinkedHashMap<>() : new LinkedHashMap<>(topics);
    }
}
```

- [ ] **Step 2: 验证编译**

```bash
./mvnw compile -pl shop-common -q
```

- [ ] **Step 3: Commit**

```bash
git add shop-common/src/main/java/dev/meirong/shop/common/kafka/KafkaPartitioningProperties.java
git commit -m "feat(shop-common): add KafkaPartitioningProperties for per-topic strategy config"
```

---

## Task 6: `KafkaEventPublisher` (TDD)

**Files:**
- Create: `shop-common/src/main/java/dev/meirong/shop/common/kafka/KafkaEventPublisher.java`
- Create: `shop-common/src/test/java/dev/meirong/shop/common/kafka/KafkaEventPublisherTest.java`

- [ ] **Step 1: 写失败测试**

```java
// shop-common/src/test/java/dev/meirong/shop/common/kafka/KafkaEventPublisherTest.java
package dev.meirong.shop.common.kafka;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dev.meirong.shop.common.kafka.strategy.KeyedStrategy;
import dev.meirong.shop.common.kafka.strategy.UnkeyedStrategy;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;

@ExtendWith(MockitoExtension.class)
class KafkaEventPublisherTest {

    @Mock
    private KafkaTemplate<String, String> kafkaTemplate;

    private KafkaEventPublisher publisher;

    @BeforeEach
    void setUp() {
        Map<String, TopicPartitionStrategy> registry = Map.of(
                "order.events.v1", new KeyedStrategy(),
                "notification.events.v1", new UnkeyedStrategy()
        );
        publisher = new KafkaEventPublisher(kafkaTemplate, registry, new UnkeyedStrategy());
    }

    @Test
    void send_keyedTopic_callsTemplateWithKey() {
        when(kafkaTemplate.send("order.events.v1", "order-123", "{}"))
                .thenReturn(CompletableFuture.completedFuture(null));

        publisher.send("order.events.v1", "order-123", "{}");

        verify(kafkaTemplate).send("order.events.v1", "order-123", "{}");
        verify(kafkaTemplate, never()).send(anyString(), anyString()); // 2-arg overload not called
    }

    @Test
    void send_unkeyedTopic_callsTemplateWithoutKey() {
        when(kafkaTemplate.send("notification.events.v1", "{}"))
                .thenReturn(CompletableFuture.completedFuture(null));

        publisher.send("notification.events.v1", "ignored-key", "{}");

        verify(kafkaTemplate).send("notification.events.v1", "{}");
        verify(kafkaTemplate, never()).send(anyString(), anyString(), anyString()); // 3-arg not called
    }

    @Test
    void send_unknownTopic_usesDefaultStrategy() {
        // default is UnkeyedStrategy, so no key
        when(kafkaTemplate.send("unknown.topic.v1", "{}"))
                .thenReturn(CompletableFuture.completedFuture(null));

        publisher.send("unknown.topic.v1", "some-key", "{}");

        verify(kafkaTemplate).send("unknown.topic.v1", "{}");
    }

    @Test
    void send_keyedTopicWithNullKey_throwsBeforeCallingTemplate() {
        assertThatThrownBy(() -> publisher.send("order.events.v1", null, "{}"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("order.events.v1");

        verify(kafkaTemplate, never()).send(anyString(), anyString(), anyString());
        verify(kafkaTemplate, never()).send(anyString(), anyString());
    }

    @Test
    void send_returnsCompletableFutureFromTemplate() {
        CompletableFuture<SendResult<String, String>> expected = CompletableFuture.completedFuture(null);
        when(kafkaTemplate.send("order.events.v1", "order-123", "{}")).thenReturn(expected);

        CompletableFuture<SendResult<String, String>> result =
                publisher.send("order.events.v1", "order-123", "{}");

        assertThat(result).isSameAs(expected);
    }
}
```

- [ ] **Step 2: 运行测试，确认失败**

```bash
./mvnw test -pl shop-common -Dtest=KafkaEventPublisherTest -q
```

Expected: FAIL（`KafkaEventPublisher` 类不存在）

- [ ] **Step 3: 实现 `KafkaEventPublisher`**

```java
// shop-common/src/main/java/dev/meirong/shop/common/kafka/KafkaEventPublisher.java
package dev.meirong.shop.common.kafka;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.lang.Nullable;

public class KafkaEventPublisher {

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final Map<String, TopicPartitionStrategy> registry;
    private final TopicPartitionStrategy defaultStrategy;

    public KafkaEventPublisher(KafkaTemplate<String, String> kafkaTemplate,
                               Map<String, TopicPartitionStrategy> registry,
                               TopicPartitionStrategy defaultStrategy) {
        this.kafkaTemplate = kafkaTemplate;
        this.registry = registry;
        this.defaultStrategy = defaultStrategy;
    }

    public CompletableFuture<SendResult<String, String>> send(
            String topic, @Nullable String key, String payload) {
        SendSpec spec = registry.getOrDefault(topic, defaultStrategy)
                                .apply(topic, key, payload);
        if (spec.key() != null) {
            return kafkaTemplate.send(spec.topic(), spec.key(), spec.payload());
        }
        return kafkaTemplate.send(spec.topic(), spec.payload());
    }
}
```

- [ ] **Step 4: 运行测试，确认通过**

```bash
./mvnw test -pl shop-common -Dtest=KafkaEventPublisherTest -q
```

Expected: BUILD SUCCESS, Tests run: 5, Failures: 0

- [ ] **Step 5: Commit**

```bash
git add shop-common/src/main/java/dev/meirong/shop/common/kafka/KafkaEventPublisher.java \
        shop-common/src/test/java/dev/meirong/shop/common/kafka/KafkaEventPublisherTest.java
git commit -m "feat(shop-common): add KafkaEventPublisher with per-topic strategy routing"
```

---

## Task 7: `KafkaPartitioningAutoConfiguration` (TDD)

**Files:**
- Create: `shop-common/src/main/java/dev/meirong/shop/common/kafka/KafkaPartitioningAutoConfiguration.java`
- Create: `shop-common/src/test/java/dev/meirong/shop/common/kafka/KafkaPartitioningAutoConfigurationTest.java`

- [ ] **Step 1: 写失败测试**

```java
// shop-common/src/test/java/dev/meirong/shop/common/kafka/KafkaPartitioningAutoConfigurationTest.java
package dev.meirong.shop.common.kafka;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import dev.meirong.shop.common.kafka.strategy.KeyedStrategy;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.FilteredClassLoader;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.kafka.core.KafkaTemplate;

class KafkaPartitioningAutoConfigurationTest {

    @SuppressWarnings("unchecked")
    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(KafkaPartitioningAutoConfiguration.class))
            .withBean("kafkaTemplate", KafkaTemplate.class, () -> mock(KafkaTemplate.class));

    @Test
    void kafkaEventPublisher_isCreatedByDefault() {
        runner.run(ctx -> assertThat(ctx).hasSingleBean(KafkaEventPublisher.class));
    }

    @Test
    void kafkaEventPublisher_notCreated_whenKafkaAbsentFromClasspath() {
        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(KafkaPartitioningAutoConfiguration.class))
                .withClassLoader(new FilteredClassLoader(KafkaTemplate.class))
                .run(ctx -> assertThat(ctx).doesNotHaveBean(KafkaEventPublisher.class));
    }

    @Test
    void unknownStrategyName_failsOnStartup() {
        runner.withPropertyValues("shop.kafka.partitioning.topics.order\\.events\\.v1=typo-strategy")
                .run(ctx -> {
                    assertThat(ctx).hasFailed();
                    assertThat(ctx.getStartupFailure().getRootCause())
                            .isInstanceOf(IllegalArgumentException.class)
                            .hasMessageContaining("typo-strategy");
                });
    }

    @Test
    void customStrategyBean_isUsedWhenReferenced() {
        runner.withBean("customPartitionStrategies", Map.class,
                        () -> Map.of("my-strategy",
                                (TopicPartitionStrategy) (t, k, p) -> new SendSpec(t, k, p)))
                .withPropertyValues("shop.kafka.partitioning.topics.foo\\.v1=my-strategy")
                .run(ctx -> assertThat(ctx).hasSingleBean(KafkaEventPublisher.class));
    }

    @Test
    void keyedTopicConfig_routesThroughKeyedStrategy() {
        runner.withPropertyValues("shop.kafka.partitioning.topics.order\\.events\\.v1=keyed")
                .run(ctx -> {
                    assertThat(ctx).hasSingleBean(KafkaEventPublisher.class);
                    // Null key on keyed topic must throw — verifies strategy was wired
                    KafkaEventPublisher publisher = ctx.getBean(KafkaEventPublisher.class);
                    org.assertj.core.api.Assertions.assertThatThrownBy(
                                    () -> publisher.send("order.events.v1", null, "{}"))
                            .isInstanceOf(IllegalArgumentException.class);
                });
    }
}
```

- [ ] **Step 2: 运行测试，确认失败**

```bash
./mvnw test -pl shop-common -Dtest=KafkaPartitioningAutoConfigurationTest -q
```

Expected: FAIL（`KafkaPartitioningAutoConfiguration` 类不存在）

- [ ] **Step 3: 实现 `KafkaPartitioningAutoConfiguration`**

```java
// shop-common/src/main/java/dev/meirong/shop/common/kafka/KafkaPartitioningAutoConfiguration.java
package dev.meirong.shop.common.kafka;

import dev.meirong.shop.common.kafka.strategy.KeyedStrategy;
import dev.meirong.shop.common.kafka.strategy.UnkeyedStrategy;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.kafka.core.KafkaTemplate;

@AutoConfiguration
@ConditionalOnClass(KafkaTemplate.class)
@EnableConfigurationProperties(KafkaPartitioningProperties.class)
public class KafkaPartitioningAutoConfiguration {

    private static final Map<String, TopicPartitionStrategy> BUILT_INS = Map.of(
            "keyed",   new KeyedStrategy(),
            "unkeyed", new UnkeyedStrategy()
    );

    @Bean
    KafkaEventPublisher kafkaEventPublisher(
            KafkaTemplate<String, String> kafkaTemplate,
            KafkaPartitioningProperties props,
            ObjectProvider<Map<String, TopicPartitionStrategy>> customStrategiesProvider) {

        Map<String, TopicPartitionStrategy> allStrategies = new LinkedHashMap<>(BUILT_INS);
        customStrategiesProvider.ifAvailable(allStrategies::putAll);

        TopicPartitionStrategy defaultStrategy = resolve(allStrategies,
                props.getDefaultStrategy(), "<default>");

        Map<String, TopicPartitionStrategy> registry = new LinkedHashMap<>();
        props.getTopics().forEach((topic, name) ->
                registry.put(topic, resolve(allStrategies, name, topic)));

        return new KafkaEventPublisher(kafkaTemplate, registry, defaultStrategy);
    }

    private static TopicPartitionStrategy resolve(
            Map<String, TopicPartitionStrategy> strategies, String name, String context) {
        TopicPartitionStrategy resolved = strategies.get(name);
        if (resolved == null) {
            throw new IllegalArgumentException(
                    "Unknown partition strategy '" + name + "' for '" + context + "'. " +
                    "Known strategies: " + strategies.keySet());
        }
        return resolved;
    }
}
```

- [ ] **Step 4: 运行全部 shop-common 测试**

```bash
./mvnw test -pl shop-common -q
```

Expected: BUILD SUCCESS，所有测试通过

- [ ] **Step 5: Commit**

```bash
git add shop-common/src/main/java/dev/meirong/shop/common/kafka/KafkaPartitioningAutoConfiguration.java \
        shop-common/src/test/java/dev/meirong/shop/common/kafka/KafkaPartitioningAutoConfigurationTest.java
git commit -m "feat(shop-common): add KafkaPartitioningAutoConfiguration with fail-fast strategy resolution"
```

---

## Task 8: 注册 Auto-Configuration

**Files:**
- Modify: `shop-common/src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`

当前文件已有一行：`dev.meirong.shop.common.feature.FeatureToggleAutoConfiguration`

- [ ] **Step 1: 追加一行**

```
dev.meirong.shop.common.kafka.KafkaPartitioningAutoConfiguration
```

文件应为：
```
dev.meirong.shop.common.feature.FeatureToggleAutoConfiguration
dev.meirong.shop.common.kafka.KafkaPartitioningAutoConfiguration
```

- [ ] **Step 2: 验证 shop-common 测试仍全部通过**

```bash
./mvnw test -pl shop-common -q
```

- [ ] **Step 3: Commit**

```bash
git add shop-common/src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports
git commit -m "feat(shop-common): register KafkaPartitioningAutoConfiguration in SPI"
```

---

## Task 9: 前置修复 — `WalletOutboxEventEntity.getAggregateId()`

**Files:**
- Modify: `wallet-service/src/main/java/dev/meirong/shop/wallet/domain/WalletOutboxEventEntity.java`

> **必须在 Task 11 之前完成**，否则 `WalletOutboxPublisher` 编译报错。

- [ ] **Step 1: 在现有 getter 块末尾添加 `getAggregateId()`**

找到文件末尾的 `getTopic()` getter（约第62行），在其后添加：

```java
public String getAggregateId() {
    return aggregateId;
}
```

- [ ] **Step 2: 验证编译**

```bash
./mvnw compile -pl wallet-service -q
```

- [ ] **Step 3: Commit**

```bash
git add wallet-service/src/main/java/dev/meirong/shop/wallet/domain/WalletOutboxEventEntity.java
git commit -m "fix(wallet-service): expose getAggregateId() on WalletOutboxEventEntity"
```

---

## Task 10: 迁移 `OrderOutboxPublisher`

**Files:**
- Create: `order-service/src/test/java/dev/meirong/shop/order/service/OrderOutboxPublisherTest.java`
- Modify: `order-service/src/main/java/dev/meirong/shop/order/service/OrderOutboxPublisher.java`
- Modify: `order-service/src/main/resources/application.yml`

- [ ] **Step 1: 写失败测试（新建文件）**

```java
// order-service/src/test/java/dev/meirong/shop/order/service/OrderOutboxPublisherTest.java
package dev.meirong.shop.order.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import dev.meirong.shop.common.kafka.KafkaEventPublisher;
import dev.meirong.shop.order.domain.OrderOutboxEventEntity;
import dev.meirong.shop.order.domain.OrderOutboxEventRepository;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class OrderOutboxPublisherTest {

    @Mock
    private OrderOutboxEventRepository repository;

    @Mock
    private KafkaEventPublisher kafkaEventPublisher;

    private OrderOutboxPublisher publisher;

    @BeforeEach
    void setUp() {
        publisher = new OrderOutboxPublisher(repository, kafkaEventPublisher);
    }

    @Test
    void publishPendingEvents_sendsWithOrderIdAsPartitionKey() {
        OrderOutboxEventEntity event = new OrderOutboxEventEntity(
                "order-abc", "order.events.v1", "ORDER_PLACED", "{}");

        when(repository.findTop20ByPublishedFalseOrderByCreatedAtAsc()).thenReturn(List.of(event));
        when(kafkaEventPublisher.send("order.events.v1", "order-abc", "{}"))
                .thenReturn(CompletableFuture.completedFuture(null));

        publisher.publishPendingEvents();

        verify(kafkaEventPublisher).send("order.events.v1", "order-abc", "{}");
        assertThat(event.isPublished()).isTrue();
        verify(repository).saveAll(List.of(event));
    }

    @Test
    void publishPendingEvents_emptyList_doesNotSave() {
        when(repository.findTop20ByPublishedFalseOrderByCreatedAtAsc()).thenReturn(List.of());

        publisher.publishPendingEvents();

        verifyNoInteractions(kafkaEventPublisher);
        verify(repository, org.mockito.Mockito.never()).saveAll(org.mockito.ArgumentMatchers.any());
    }
}
```

- [ ] **Step 2: 运行测试，确认失败**

```bash
./mvnw test -pl order-service -Dtest=OrderOutboxPublisherTest -q
```

Expected: FAIL（构造函数签名不匹配）

- [ ] **Step 3: 更新 `OrderOutboxPublisher` 实现**

替换 `KafkaTemplate` 注入为 `KafkaEventPublisher`，并传入 `orderId` 作为 key：

```java
package dev.meirong.shop.order.service;

import dev.meirong.shop.common.kafka.KafkaEventPublisher;
import dev.meirong.shop.order.domain.OrderOutboxEventEntity;
import dev.meirong.shop.order.domain.OrderOutboxEventRepository;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class OrderOutboxPublisher {

    private static final Logger log = LoggerFactory.getLogger(OrderOutboxPublisher.class);

    private final OrderOutboxEventRepository repository;
    private final KafkaEventPublisher kafkaEventPublisher;

    public OrderOutboxPublisher(OrderOutboxEventRepository repository,
                                KafkaEventPublisher kafkaEventPublisher) {
        this.repository = repository;
        this.kafkaEventPublisher = kafkaEventPublisher;
    }

    @Scheduled(fixedDelayString = "${shop.order.outbox-publish-delay-ms:5000}")
    @Transactional
    public void publishPendingEvents() {
        List<OrderOutboxEventEntity> events =
                repository.findTop20ByPublishedFalseOrderByCreatedAtAsc();
        for (OrderOutboxEventEntity event : events) {
            kafkaEventPublisher.send(event.getTopic(), event.getOrderId(), event.getPayload());
            event.markPublished();
            log.debug("Published order event: {} for order {}", event.getEventType(), event.getOrderId());
        }
        if (!events.isEmpty()) {
            repository.saveAll(events);
        }
    }
}
```

- [ ] **Step 4: 在 `order-service/src/main/resources/application.yml` 末尾添加分区配置**

```yaml
shop:
  kafka:
    partitioning:
      default-strategy: unkeyed
      topics:
        order.events.v1: keyed
```

> 注意：YAML 文件已有 `shop:` 节，将 `kafka.partitioning` 合并进去，不要重复 `shop:` 根节点。

- [ ] **Step 5: 运行测试，确认通过**

```bash
./mvnw test -pl order-service -Dtest=OrderOutboxPublisherTest -q
```

Expected: BUILD SUCCESS, Tests run: 2, Failures: 0

- [ ] **Step 6: Commit**

```bash
git add order-service/src/main/java/dev/meirong/shop/order/service/OrderOutboxPublisher.java \
        order-service/src/main/resources/application.yml \
        order-service/src/test/java/dev/meirong/shop/order/service/OrderOutboxPublisherTest.java
git commit -m "fix(order-service): route order events with orderId partition key via KafkaEventPublisher"
```

---

## Task 11: 迁移 `WalletOutboxPublisher`

> **依赖 Task 9**（`getAggregateId()` getter 必须已存在）。

**Files:**
- Create: `wallet-service/src/test/java/dev/meirong/shop/wallet/service/WalletOutboxPublisherTest.java`
- Modify: `wallet-service/src/main/java/dev/meirong/shop/wallet/service/WalletOutboxPublisher.java`
- Modify: `wallet-service/src/main/resources/application.yml`

- [ ] **Step 1: 写失败测试**

```java
// wallet-service/src/test/java/dev/meirong/shop/wallet/service/WalletOutboxPublisherTest.java
package dev.meirong.shop.wallet.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import dev.meirong.shop.common.kafka.KafkaEventPublisher;
import dev.meirong.shop.wallet.domain.WalletOutboxEventEntity;
import dev.meirong.shop.wallet.domain.WalletOutboxEventRepository;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class WalletOutboxPublisherTest {

    @Mock
    private WalletOutboxEventRepository repository;

    @Mock
    private KafkaEventPublisher kafkaEventPublisher;

    private WalletOutboxPublisher publisher;

    @BeforeEach
    void setUp() {
        publisher = new WalletOutboxPublisher(repository, kafkaEventPublisher);
    }

    @Test
    void publishPendingEvents_sendsWithAggregateIdAsPartitionKey() {
        WalletOutboxEventEntity event = new WalletOutboxEventEntity(
                "wallet-xyz", "wallet.transactions.v1", "WALLET_CREDITED", "{}");

        when(repository.findTop20ByPublishedFalseOrderByCreatedAtAsc()).thenReturn(List.of(event));
        when(kafkaEventPublisher.send("wallet.transactions.v1", "wallet-xyz", "{}"))
                .thenReturn(CompletableFuture.completedFuture(null));

        publisher.publishPendingEvents();

        verify(kafkaEventPublisher).send("wallet.transactions.v1", "wallet-xyz", "{}");
        assertThat(event.isPublished()).isTrue();
    }

    @Test
    void publishPendingEvents_emptyList_doesNotCallSaveAll() {
        when(repository.findTop20ByPublishedFalseOrderByCreatedAtAsc()).thenReturn(List.of());

        publisher.publishPendingEvents();

        verifyNoInteractions(kafkaEventPublisher);
        verify(repository, org.mockito.Mockito.never()).saveAll(org.mockito.ArgumentMatchers.any());
    }
}
```

- [ ] **Step 2: 运行测试，确认失败**

```bash
./mvnw test -pl wallet-service -Dtest=WalletOutboxPublisherTest -q
```

- [ ] **Step 3: 更新 `WalletOutboxPublisher` 实现**

```java
package dev.meirong.shop.wallet.service;

import dev.meirong.shop.common.kafka.KafkaEventPublisher;
import dev.meirong.shop.wallet.domain.WalletOutboxEventEntity;
import dev.meirong.shop.wallet.domain.WalletOutboxEventRepository;
import java.util.List;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class WalletOutboxPublisher {

    private final WalletOutboxEventRepository repository;
    private final KafkaEventPublisher kafkaEventPublisher;

    public WalletOutboxPublisher(WalletOutboxEventRepository repository,
                                 KafkaEventPublisher kafkaEventPublisher) {
        this.repository = repository;
        this.kafkaEventPublisher = kafkaEventPublisher;
    }

    @Scheduled(fixedDelayString = "${shop.wallet.outbox-publish-delay-ms:5000}")
    @Transactional
    public void publishPendingEvents() {
        List<WalletOutboxEventEntity> events =
                repository.findTop20ByPublishedFalseOrderByCreatedAtAsc();
        for (WalletOutboxEventEntity event : events) {
            kafkaEventPublisher.send(event.getTopic(), event.getAggregateId(), event.getPayload());
            event.markPublished();
        }
        if (!events.isEmpty()) {
            repository.saveAll(events);
        }
    }
}
```

- [ ] **Step 4: 在 `wallet-service/src/main/resources/application.yml` 中添加分区配置**

在现有 `shop:` 节下添加（`wallet-topic` 值为 `wallet.transactions.v1`）：

```yaml
  kafka:
    partitioning:
      default-strategy: unkeyed
      topics:
        wallet.transactions.v1: keyed
```

- [ ] **Step 5: 运行测试，确认通过**

```bash
./mvnw test -pl wallet-service -Dtest=WalletOutboxPublisherTest -q
```

- [ ] **Step 6: Commit**

```bash
git add wallet-service/src/main/java/dev/meirong/shop/wallet/service/WalletOutboxPublisher.java \
        wallet-service/src/main/resources/application.yml \
        wallet-service/src/test/java/dev/meirong/shop/wallet/service/WalletOutboxPublisherTest.java
git commit -m "fix(wallet-service): route wallet events with aggregateId partition key via KafkaEventPublisher"
```

---

## Task 12: 迁移 `MarketplaceOutboxPublisher`

**Files:**
- Modify: `marketplace-service/src/test/java/dev/meirong/shop/marketplace/service/MarketplaceOutboxPublisherTest.java`
- Modify: `marketplace-service/src/main/java/dev/meirong/shop/marketplace/service/MarketplaceOutboxPublisher.java`
- Modify: `marketplace-service/src/main/resources/application.yml`

- [ ] **Step 1: 更新现有测试（先改测试，再改实现）**

将 `@Mock KafkaTemplate<String, String> kafkaTemplate` 替换为 `@Mock KafkaEventPublisher kafkaEventPublisher`，并更新构造函数调用和 `when()` stub：

```java
// 关键变更部分（其余结构保持不变）

@Mock
private KafkaEventPublisher kafkaEventPublisher;  // 替换原来的 KafkaTemplate mock

@BeforeEach
void setUp() {
    publisher = new MarketplaceOutboxPublisher(repository, kafkaEventPublisher);  // 更新构造函数
}

@Test
void publishPendingEvents_marksEventPublishedAfterKafkaAck() {
    // ...
    when(kafkaEventPublisher.send(anyString(), anyString(), anyString())).thenReturn(future);
    // verify 同样更新为 kafkaEventPublisher
    // 去除 verify(repository).saveAll(...) 以外的 kafkaTemplate 相关验证
}
```

完整更新后的文件：

```java
package dev.meirong.shop.marketplace.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dev.meirong.shop.common.kafka.KafkaEventPublisher;
import dev.meirong.shop.marketplace.domain.MarketplaceOutboxEventEntity;
import dev.meirong.shop.marketplace.domain.MarketplaceOutboxEventRepository;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.support.SendResult;

@ExtendWith(MockitoExtension.class)
class MarketplaceOutboxPublisherTest {

    @Mock
    private MarketplaceOutboxEventRepository repository;

    @Mock
    private KafkaEventPublisher kafkaEventPublisher;

    private MarketplaceOutboxPublisher publisher;

    @BeforeEach
    void setUp() {
        publisher = new MarketplaceOutboxPublisher(repository, kafkaEventPublisher);
    }

    @Test
    void publishPendingEvents_marksEventPublishedAfterKafkaAck() {
        MarketplaceOutboxEventEntity event = new MarketplaceOutboxEventEntity(
                "product-1", "marketplace.product.events.v1",
                MarketplaceEventType.PRODUCT_CREATED.name(), "{}");
        CompletableFuture<SendResult<String, String>> future = CompletableFuture.completedFuture(null);

        when(repository.findTop100ByPublishedFalseOrderByCreatedAtAsc()).thenReturn(List.of(event));
        when(kafkaEventPublisher.send(anyString(), anyString(), anyString())).thenReturn(future);

        publisher.publishPendingEvents();

        assertThat(event.isPublished()).isTrue();
        verify(repository).saveAll(List.of(event));
    }

    @Test
    void publishPendingEvents_sendFailureDoesNotMarkEventPublished() {
        MarketplaceOutboxEventEntity event = new MarketplaceOutboxEventEntity(
                "product-2", "marketplace.product.events.v1",
                MarketplaceEventType.PRODUCT_UPDATED.name(), "{}");
        CompletableFuture<SendResult<String, String>> future = new CompletableFuture<>();
        future.completeExceptionally(new RuntimeException("broker unavailable"));

        when(repository.findTop100ByPublishedFalseOrderByCreatedAtAsc()).thenReturn(List.of(event));
        when(kafkaEventPublisher.send(anyString(), anyString(), anyString())).thenReturn(future);

        assertThatThrownBy(() -> publisher.publishPendingEvents())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Failed to publish marketplace outbox event");
        assertThat(event.isPublished()).isFalse();
        verify(repository, never()).saveAll(any());
    }
}
```

- [ ] **Step 2: 运行测试，确认失败**

```bash
./mvnw test -pl marketplace-service -Dtest=MarketplaceOutboxPublisherTest -q
```

- [ ] **Step 3: 更新 `MarketplaceOutboxPublisher` 实现**

```java
package dev.meirong.shop.marketplace.service;

import dev.meirong.shop.common.kafka.KafkaEventPublisher;
import dev.meirong.shop.marketplace.domain.MarketplaceOutboxEventEntity;
import dev.meirong.shop.marketplace.domain.MarketplaceOutboxEventRepository;
import java.util.List;
import java.util.concurrent.CompletionException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class MarketplaceOutboxPublisher {

    private final MarketplaceOutboxEventRepository repository;
    private final KafkaEventPublisher kafkaEventPublisher;

    public MarketplaceOutboxPublisher(MarketplaceOutboxEventRepository repository,
                                      KafkaEventPublisher kafkaEventPublisher) {
        this.repository = repository;
        this.kafkaEventPublisher = kafkaEventPublisher;
    }

    @Scheduled(fixedDelayString = "${shop.marketplace.outbox.publish-delay-ms:5000}")
    @Transactional
    public void publishPendingEvents() {
        List<MarketplaceOutboxEventEntity> events =
                repository.findTop100ByPublishedFalseOrderByCreatedAtAsc();
        for (MarketplaceOutboxEventEntity event : events) {
            try {
                kafkaEventPublisher.send(
                        event.getTopic(), event.getAggregateId(), event.getPayload()).join();
            } catch (CompletionException exception) {
                throw new IllegalStateException(
                        "Failed to publish marketplace outbox event " + event.getId(),
                        exception.getCause());
            }
            event.markPublished();
        }
        repository.saveAll(events);
    }
}
```

- [ ] **Step 4: 在 `marketplace-service/src/main/resources/application.yml` 中添加分区配置**

```yaml
shop:
  kafka:
    partitioning:
      default-strategy: unkeyed
      topics:
        marketplace.product.events.v1: keyed
```

- [ ] **Step 5: 运行测试，确认通过**

```bash
./mvnw test -pl marketplace-service -Dtest=MarketplaceOutboxPublisherTest -q
```

- [ ] **Step 6: Commit**

```bash
git add marketplace-service/src/main/java/dev/meirong/shop/marketplace/service/MarketplaceOutboxPublisher.java \
        marketplace-service/src/main/resources/application.yml \
        marketplace-service/src/test/java/dev/meirong/shop/marketplace/service/MarketplaceOutboxPublisherTest.java
git commit -m "feat(marketplace-service): route product events with aggregateId partition key via KafkaEventPublisher"
```

---

## Task 13: 迁移 `ActivityOutboxPublisher`

**Files:**
- Create: `activity-service/src/test/java/dev/meirong/shop/activity/service/ActivityOutboxPublisherTest.java`
- Modify: `activity-service/src/main/java/dev/meirong/shop/activity/service/ActivityOutboxPublisher.java`
- Modify: `activity-service/src/main/resources/application.yml`

> **注意：** `ActivityOutboxEvent.topic` 的值由创建事件时的调用方决定。由于 `ActivityOutboxPublisher` 已经正确传入 `gameId` 作为 key（不同于 Order/Wallet），此次迁移的目的是保持一致性。迁移前先运行以下命令找出实际使用的 topic 名称，替换下方 YAML 配置中的 `<ACTIVITY_TOPIC_NAME>`：
>
> ```bash
> grep -r "ActivityOutboxEvent(" activity-service/src/main/java --include="*.java" -h
> ```
>
> 若 activity-service 事件发布尚未实现（无调用方），使用占位符 `activity.events.v1`。

- [ ] **Step 1: 写失败测试**

```java
// activity-service/src/test/java/dev/meirong/shop/activity/service/ActivityOutboxPublisherTest.java
package dev.meirong.shop.activity.service;

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dev.meirong.shop.activity.domain.ActivityOutboxEvent;
import dev.meirong.shop.activity.domain.ActivityOutboxEventRepository;
import dev.meirong.shop.common.kafka.KafkaEventPublisher;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ActivityOutboxPublisherTest {

    @Mock
    private ActivityOutboxEventRepository outboxRepository;

    @Mock
    private KafkaEventPublisher kafkaEventPublisher;

    private ActivityOutboxPublisher publisher;

    @BeforeEach
    void setUp() {
        publisher = new ActivityOutboxPublisher(outboxRepository, kafkaEventPublisher);
    }

    @Test
    void publishPendingEvents_sendsWithGameIdAsPartitionKey() {
        ActivityOutboxEvent event = new ActivityOutboxEvent(
                "game-001", "activity.events.v1", "GAME_STARTED", "{}");

        when(outboxRepository.findByStatusOrderByCreatedAtAsc("PENDING")).thenReturn(List.of(event));
        when(kafkaEventPublisher.send("activity.events.v1", "game-001", "{}"))
                .thenReturn(CompletableFuture.completedFuture(null));

        publisher.publishPendingEvents();

        verify(kafkaEventPublisher).send("activity.events.v1", "game-001", "{}");
        // event should be marked published
        org.assertj.core.api.Assertions.assertThat(event.getStatus()).isEqualTo("PUBLISHED");
    }

    @Test
    void publishPendingEvents_sendFailure_breaksLoopAndDoesNotMarkPublished() {
        ActivityOutboxEvent event = new ActivityOutboxEvent(
                "game-002", "activity.events.v1", "GAME_ENDED", "{}");

        when(outboxRepository.findByStatusOrderByCreatedAtAsc("PENDING")).thenReturn(List.of(event));
        when(kafkaEventPublisher.send("activity.events.v1", "game-002", "{}"))
                .thenThrow(new RuntimeException("broker unavailable"));

        publisher.publishPendingEvents();

        org.assertj.core.api.Assertions.assertThat(event.getStatus()).isEqualTo("PENDING");
        verify(outboxRepository, never()).save(event);
    }
}
```

- [ ] **Step 2: 运行测试，确认失败**

```bash
./mvnw test -pl activity-service -Dtest=ActivityOutboxPublisherTest -q
```

- [ ] **Step 3: 更新 `ActivityOutboxPublisher` 实现**

保留原有逐条 `outboxRepository.save(event)` 和 `break`-on-error 控制流，仅替换 send 调用：

```java
package dev.meirong.shop.activity.service;

import dev.meirong.shop.activity.domain.ActivityOutboxEvent;
import dev.meirong.shop.activity.domain.ActivityOutboxEventRepository;
import dev.meirong.shop.common.kafka.KafkaEventPublisher;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ActivityOutboxPublisher {

    private static final Logger log = LoggerFactory.getLogger(ActivityOutboxPublisher.class);

    private final ActivityOutboxEventRepository outboxRepository;
    private final KafkaEventPublisher kafkaEventPublisher;

    public ActivityOutboxPublisher(ActivityOutboxEventRepository outboxRepository,
                                   KafkaEventPublisher kafkaEventPublisher) {
        this.outboxRepository = outboxRepository;
        this.kafkaEventPublisher = kafkaEventPublisher;
    }

    @Scheduled(fixedDelay = 5000)
    @Transactional
    public void publishPendingEvents() {
        List<ActivityOutboxEvent> events = outboxRepository.findByStatusOrderByCreatedAtAsc("PENDING");
        for (ActivityOutboxEvent event : events) {
            try {
                kafkaEventPublisher.send(event.getTopic(), event.getGameId(), event.getPayload());
                event.markPublished();
                outboxRepository.save(event);
                log.debug("Published event: topic={}, type={}", event.getTopic(), event.getEventType());
            } catch (Exception e) {
                log.error("Failed to publish event id={}", event.getId(), e);
                break;
            }
        }
    }
}
```

- [ ] **Step 4: 在 `activity-service/src/main/resources/application.yml` 中添加分区配置**

将 `<ACTIVITY_TOPIC_NAME>` 替换为 Step 0 中找到的真实 topic 名称（若未知则用 `activity.events.v1`）：

```yaml
shop:
  kafka:
    partitioning:
      default-strategy: unkeyed
      topics:
        activity.events.v1: keyed   # 替换为实际 topic 名
```

- [ ] **Step 5: 运行测试，确认通过**

```bash
./mvnw test -pl activity-service -Dtest=ActivityOutboxPublisherTest -q
```

- [ ] **Step 6: Commit**

```bash
git add activity-service/src/main/java/dev/meirong/shop/activity/service/ActivityOutboxPublisher.java \
        activity-service/src/main/resources/application.yml \
        activity-service/src/test/java/dev/meirong/shop/activity/service/ActivityOutboxPublisherTest.java
git commit -m "feat(activity-service): route activity events with gameId partition key via KafkaEventPublisher"
```

---

## Task 14: 全量回归验证

- [ ] **Step 1: 运行所有受影响模块的测试**

```bash
./mvnw test -pl shop-common,order-service,wallet-service,marketplace-service,activity-service -q
```

Expected: BUILD SUCCESS，所有测试通过，无 Failures

- [ ] **Step 2: 验证 `shop-common` 不会把 `spring-kafka` 传递给无 Kafka 的服务**

```bash
./mvnw dependency:tree -pl profile-service -Dincludes=org.springframework.kafka -q
```

Expected: 无 `spring-kafka` 依赖输出（`profile-service` 不使用 Kafka，不应受影响）

- [ ] **Step 3: Commit（若有任何漏掉的文件）**

```bash
git status
# 确认无遗漏文件后提交（若 Step 1-2 均无问题则无需额外 commit）
```
