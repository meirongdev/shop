# Kafka 生产者分区策略组件设计

> 版本：1.1 | 日期：2026-03-22

---

## 一、背景与问题

项目所有服务均使用 Outbox 模式通过 `KafkaTemplate<String, String>` 发布领域事件。调查发现当前分区行为不一致：

| Publisher | 调用方式 | 实际分区行为 |
|-----------|---------|------------|
| `MarketplaceOutboxPublisher` | `send(topic, aggregateId, payload)` | 按 aggregateId hash 分区 ✅ |
| `ActivityOutboxPublisher` | `send(topic, gameId, payload)` | 按 gameId hash 分区 ✅ |
| `OrderOutboxPublisher` | `send(topic, payload)` | 无 key，sticky 随机分区 ❌ |
| `WalletOutboxPublisher` | `send(topic, payload)` | 无 key，sticky 随机分区 ❌ |

`OrderOutboxEventEntity` 持有 `orderId`，但发布时未传入，导致同一订单的事件可能落到不同分区，下游消费者无法保证聚合级别有序。

此外：
- `WalletOutboxEventEntity` 有 `aggregateId` 字段但未暴露 getter，导致 Publisher 无法读取。
- `WalletOutboxPublisher` 无论是否有事件都调用 `repository.saveAll(events)`，存在无效 DB 调用。

---

## 二、需求

- 同一聚合（相同 `orderId`、`aggregateId` 等）的事件始终路由到同一分区（聚合级别有序）
- 不需要有序的 topic 交由 Kafka `UniformStickyPartitioner` 处理（Spring Kafka 3.x 默认行为）
- 策略通过 YAML 配置声明，无需改 Java 代码即可切换
- 配置中出现未知策略名称时，**应用启动时立即抛出异常**（fail-fast），不允许静默降级
- 对 keyed topic 漏传 key 时 fail-fast，不允许悄悄进错分区
- 扩展新策略不需要修改框架代码

---

## 三、技术选型

### 为什么不用自定义 `Partitioner` 接口

Apache Kafka 3.3+ 已将 `org.apache.kafka.clients.producer.Partitioner` 标记为 deprecated，Spring Kafka 3.x 对应的 2026 最佳实践是在应用层管控 key 的传入，而不是在 Partitioner 层拦截。

此外，自定义 Partitioner 接收到的是调用方已传入的 key，若 Publisher 调用 `send(topic, payload)` 不传 key，Partitioner 拿到 `null`，无法自动补全 aggregate key，根本无法解决 `OrderOutboxPublisher` 的问题。

### 结论

在 `shop-common` 中实现应用层 `KafkaEventPublisher` 包装器 + 策略模式，直接修复漏传 key 的根因。

---

## 四、组件设计

### 4.1 `shop-common/pom.xml` 依赖变更

`KafkaPartitioningAutoConfiguration` 引用了 `KafkaTemplate`，需要将 `spring-kafka` 以 `optional` 方式加入 `shop-common` 依赖，确保：
- 编译期可见 `KafkaTemplate` 类
- 不使用 Kafka 的下游服务不会被传递引入 `spring-kafka`

```xml
<dependency>
    <groupId>org.springframework.kafka</groupId>
    <artifactId>spring-kafka</artifactId>
    <optional>true</optional>
</dependency>
```

`@ConditionalOnClass(KafkaTemplate.class)` 保证只有 classpath 上存在 `spring-kafka` 的服务才会装配此 Bean。

### 4.2 文件结构

```
shop-common/src/main/java/dev/meirong/shop/common/kafka/
├── TopicPartitionStrategy.java
├── SendSpec.java
├── strategy/
│   ├── KeyedStrategy.java
│   └── UnkeyedStrategy.java
├── KafkaPartitioningProperties.java
├── KafkaEventPublisher.java
└── KafkaPartitioningAutoConfiguration.java
```

### 4.3 `SendSpec`

不可变值对象，承载一次发送所需的全部信息。

```java
public record SendSpec(String topic, @Nullable String key, String payload) {}
```

### 4.4 `TopicPartitionStrategy`（SPI）

```java
@FunctionalInterface
public interface TopicPartitionStrategy {
    SendSpec apply(String topic, @Nullable String suggestedKey, String payload);
}
```

设计为 `@FunctionalInterface`，支持 lambda 内联定义自定义策略。

### 4.5 内置策略

**`KeyedStrategy`（有序）：**

```java
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

**`UnkeyedStrategy`（无序）：**

```java
public final class UnkeyedStrategy implements TopicPartitionStrategy {
    @Override
    public SendSpec apply(String topic, @Nullable String suggestedKey, String payload) {
        return new SendSpec(topic, null, payload);
    }
}
```

### 4.6 `KafkaPartitioningProperties`

```java
@ConfigurationProperties(prefix = "shop.kafka.partitioning")
public class KafkaPartitioningProperties {
    private String defaultStrategy = "unkeyed";
    private Map<String, String> topics = new LinkedHashMap<>();
    // getters / setters
}
```

### 4.7 `KafkaEventPublisher`

`send()` 返回 `CompletableFuture<SendResult<String, String>>`，调用方可选择 `.join()` 等待 broker 确认或直接忽略（fire-and-forget）。这保留了 `MarketplaceOutboxPublisher` 现有的同步确认语义，同时不强制其他 Publisher 阻塞。

```java
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

### 4.8 `KafkaPartitioningAutoConfiguration`

**未知策略名称在启动时 fail-fast**（不静默降级）：

```java
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
            // 用户自定义策略通过 @Bean Map<String, TopicPartitionStrategy> 注入
            ObjectProvider<Map<String, TopicPartitionStrategy>> customStrategiesProvider) {

        Map<String, TopicPartitionStrategy> allStrategies = new LinkedHashMap<>(BUILT_INS);
        customStrategiesProvider.ifAvailable(allStrategies::putAll);

        TopicPartitionStrategy defaultStrategy = resolve(allStrategies,
            props.getDefaultStrategy(), "<default>");

        Map<String, TopicPartitionStrategy> registry = new LinkedHashMap<>();
        props.getTopics().forEach((topic, name) ->
            registry.put(topic, resolve(allStrategies, name, topic))
        );

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

注册到 `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`。

---

## 五、YAML 配置示例

各服务在自己的 `application.yml` 或 K8s ConfigMap 中声明：

```yaml
shop:
  kafka:
    partitioning:
      default-strategy: unkeyed
      topics:
        order.events.v1: keyed
        wallet.transaction.events.v1: keyed
        marketplace.product.events.v1: keyed
        # 未声明的 topic 走 default-strategy（unkeyed）
```

---

## 六、现有代码改动

### 6.1 前置修复（必须先于 6.2 完成）

**`WalletOutboxEventEntity`** — 补充缺失的 `getAggregateId()` getter（`WalletOutboxPublisher` 迁移依赖此变更，需先完成）：

```java
public String getAggregateId() { return aggregateId; }
```

### 6.2 Publisher 改动

所有 Publisher 将 `KafkaTemplate<String, String>` 替换为 `KafkaEventPublisher`：

| Publisher | key 字段 | 确认语义 | 改动说明 |
|-----------|---------|---------|---------|
| `OrderOutboxPublisher` | `event.getOrderId()` | fire-and-forget（不 join） | 增加 key 参数 |
| `WalletOutboxPublisher` | `event.getAggregateId()` | fire-and-forget | 增加 key 参数；`saveAll()` 加 `if (!events.isEmpty())` 保护（与 OrderOutboxPublisher 对齐） |
| `MarketplaceOutboxPublisher` | `event.getAggregateId()` | **同步确认**（`.join()`）| 保留 `.join()` 调用，使用返回的 `CompletableFuture` |
| `ActivityOutboxPublisher` | `event.getGameId()` | fire-and-forget | 增加 key 参数；**保留**现有逐条 `outboxRepository.save(event)` 和 `break`-on-error 控制流，仅替换 send 调用 |

**`MarketplaceOutboxPublisher` 示例（保留 join）：**

```java
kafkaEventPublisher.send(event.getTopic(), event.getAggregateId(), event.getPayload()).join();
```

**`OrderOutboxPublisher` 示例（fire-and-forget）：**

```java
kafkaEventPublisher.send(event.getTopic(), event.getOrderId(), event.getPayload());
```

---

## 七、测试策略

| 测试类 | 位置 | 验证点 |
|--------|------|--------|
| `KeyedStrategyTest` | `shop-common` | key 非空正常返回；key 为 null 抛 `IllegalArgumentException`；key 为空白串抛异常 |
| `UnkeyedStrategyTest` | `shop-common` | 始终返回 null key，忽略传入的 suggestedKey |
| `KafkaEventPublisherTest` | `shop-common` | keyed topic 带 key 调用 template；unkeyed topic 不带 key 调用；未注册 topic 走 defaultStrategy；返回的 `CompletableFuture` 可 join |
| `KafkaPartitioningAutoConfigurationTest` | `shop-common` | 属性绑定正确；Bean 装配；`@ConditionalOnClass` 缺 Kafka 时不装配；**未知策略名称在启动时抛 `IllegalArgumentException`**；自定义策略通过 `@Bean` 注入后可被引用 |
| 各 Publisher 现有测试 | 各服务 | 将 `KafkaTemplate` mock 替换为 `KafkaEventPublisher` mock；补充 key 参数断言；`MarketplaceOutboxPublisher` 验证 `.join()` 被调用 |

---

## 八、自定义策略扩展

新增策略无需修改框架代码，在应用服务中声明 `@Bean` 即可：

```java
@Configuration
public class CustomKafkaStrategyConfig {

    @Bean
    public Map<String, TopicPartitionStrategy> customPartitionStrategies() {
        return Map.of(
            // 复合 key 策略：tenantId:aggregateId，用于多租户场景
            "tenant-keyed", (topic, key, payload) ->
                new SendSpec(topic, resolveTenantId() + ":" + key, payload)
        );
    }
}
```

在 YAML 中即可按名称引用：

```yaml
shop.kafka.partitioning.topics:
  tenant.events.v1: tenant-keyed
```

`KafkaPartitioningAutoConfiguration` 通过 `ObjectProvider<Map<String, TopicPartitionStrategy>>` 接收外部注入，与内置策略合并后统一管理，若名称与内置冲突则外部覆盖内置。

---

## 九、不在范围内

- Consumer 端分区分配策略（`@KafkaListener` 的 `concurrency`、`topicPartitions` 等）
- Topic 创建与分区数管理（由运维 / K8s Job 负责）
- 消息序列化格式（当前保持 `String`/JSON，不引入 Schema Registry）
- ConfigMap 热更新分区策略（分区策略属于启动期配置，变更需重启；与 feature toggle 的热更新机制独立）
