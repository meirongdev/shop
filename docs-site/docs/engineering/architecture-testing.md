---
title: 架构守护测试（ArchUnit）
---

# 架构守护测试（ArchUnit）

Shop Platform 使用 [ArchUnit](https://www.archunit.org/) 将架构约束编码为可执行的 JUnit 测试，确保代码库持续符合既定的设计原则。

## 快速运行

```bash
./mvnw -pl architecture-tests -am test
```

## 规则概览

所有规则集中在 `architecture-tests/` 模块，分为 5 个测试类：

| 测试类 | 类别 | 关键规则 |
|--------|------|----------|
| `ArchitectureRulesTest` | 基础编码与幂等 | 禁止字段注入、禁用 RestTemplate、禁用 System.out、Kafka 幂等守护 |
| `CodingRulesTest` | 编码规范 | 禁止 printStackTrace、统一 SLF4J、禁止 Gson、禁止 JDK 内部 API |
| `LayeringRulesTest` | 分层约束 | Controller 不访问 Repository、Service 不依赖 Controller、无包循环 |
| `NamingRulesTest` | 命名规范 | @RestController 必须以 Controller 结尾、@Entity 必须在 domain 包 |
| `SpringRulesTest` | Spring 特定 | BFF 无 JPA 实体、shop-contracts 保持轻量 |

## 核心理念

```
规则按目标架构定义 → CI 执行 → 新违规立即被拦截
                                ↓
               已知历史违规通过 FreezingArchRule 临时豁免（违规只减不增）
```

## 项目包结构约定

规则基于以下包结构约定编写：

```
dev.meirong.shop.{service}/
├── config/      → Spring 配置类
├── controller/  → REST 控制器（必须以 Controller 结尾）
├── service/     → 业务逻辑（含 engine、index 等专用子包）
└── domain/      → @Entity + JpaRepository（同一包，本项目约定）
```

## 规则详情

### 禁止字段注入（ARCH-01）
```java
// ❌ 禁止
@Autowired
private OrderService orderService;

// ✅ 正确
private final OrderService orderService;

public OrderController(OrderService orderService) {
    this.orderService = orderService;
}
```

### Kafka 幂等守护（ARCH-05/06）

所有 `@KafkaListener` 类必须：
- 注入 `IdempotencyGuard`（推荐），**或**
- 类级别标注 `@IdempotencyExempt`（需在注解中说明等效机制）

### Controller 不访问 Repository（LAYER-02）

Controller 必须通过 Service 层获取数据，不得直接注入 `*Repository`。

```java
// ❌ 禁止
@RestController
class ProductController {
    private final ProductRepository productRepository; // 直接注入 Repository
}

// ✅ 正确
@RestController
class ProductController {
    private final ProductService productService; // 通过 Service 访问
}
```

### shop-contracts 轻量化（SPRING-02）

`shop-contracts` 模块只允许包含 DTO、路径常量和校验注解，禁止引入 Spring Web/Data/Kafka/JPA 运行时依赖。

## CI 集成

架构测试通过 `./mvnw verify` 或单独执行，任何新违规将导致构建失败。

## 完整文档

权威规范：[docs/ARCHUNIT-RULES.md](https://github.com/meirongdev/shop/blob/main/docs/ARCHUNIT-RULES.md)
