# ArchUnit 架构守护规则

> **权威文档** | 版本：1.0 | 日期：2026-03-23  
> 对外展示入口：`docs-site/docs/engineering/architecture-testing.md`

---

## 一、定位与理念

ArchUnit 将架构约束编码为可执行的 JUnit 测试。代码违反规则时，测试失败——与业务 bug 一样被 CI 拦截。  
本项目在 `architecture-tests/` 模块集中管理所有跨服务架构规则，所有被分析的服务模块均作为编译依赖引入。

### 核心原则

- **规则按目标架构定义，不迁就现状**：新代码从一开始就必须合规。
- **历史违规通过 `FreezingArchRule` 临时豁免**：违规只能减少，不能增加。
- **`allowEmptyShould(true)`**：当被分析的类集中暂无匹配类时，规则自动通过（不报错）。

---

## 二、规则分类总览

| 类文件 | 类别 | 规则数 | 说明 |
|--------|------|--------|------|
| `ArchitectureRulesTest` | 基础编码与 Kafka 幂等 | 6 | 注入方式、HTTP 客户端、日志输出、Kafka 幂等 |
| `CodingRulesTest` | 编码规范扩展 | 7 | print、JUL、Joda、ObjectMapper、线程池、Gson、JDK 内部 API |
| `LayeringRulesTest` | 分层约束 | 3 | Service 不依赖 Controller、Controller 不访问 Repository、包循环 |
| `NamingRulesTest` | 命名规范 | 2 | @RestController 命名、@Entity 包位置 |
| `SpringRulesTest` | Spring 特定规则 | 2 | BFF 无 JPA Entity、shop-contracts 轻量级 |

---

## 三、各类规则详细说明

### 3.1 ArchitectureRulesTest（基础规则）

| 规则 ID | 规则名称 | 说明 |
|---------|----------|------|
| ARCH-01 | `no_field_injection` | 禁止 `@Autowired` 字段注入，使用构造器注入 |
| ARCH-02 | `no_rest_template` | 禁止依赖 `RestTemplate`，使用 `RestClient` 或 `@HttpExchange` |
| ARCH-03 | `no_system_out` | 禁止 `System.out`，使用结构化日志 |
| ARCH-04 | `no_system_err` | 禁止 `System.err`，使用结构化日志 |
| ARCH-05 | `kafka_listeners_require_idempotency_guard_or_exemption` | Kafka Listener 类必须注入 `IdempotencyGuard` 或声明 `@IdempotencyExempt` |
| ARCH-06 | `idempotency_guard_callers_must_be_transactional` | 调用 `IdempotencyGuard.executeOnce()` 的方法必须标注 `@Transactional` |

### 3.2 CodingRulesTest（编码规范扩展）

| 规则 ID | 规则名称 | 说明 |
|---------|----------|------|
| CODE-01 | `no_print_stack_trace` | 禁止 `e.printStackTrace()`，使用 `log.error("msg", e)` |
| CODE-02 | `use_slf4j_not_jul` | 禁止 `java.util.logging`，统一使用 SLF4J |
| CODE-03 | `no_jodatime` | 禁止 Joda-Time，使用 `java.time` API |
| CODE-04 | `no_scattered_object_mapper` | 禁止在 `config` 包外 `new ObjectMapper()`，注入 Spring 管理的 Bean |
| CODE-05 | `no_manual_thread_pool` | 禁止 `Executors.newFixedThreadPool()` 等传统线程池；虚拟线程 `newVirtualThreadPerTaskExecutor()` 允许 |
| CODE-06 | `no_gson` | 禁止使用 Gson，统一使用注入的 ObjectMapper（Jackson） |
| CODE-07 | `no_internal_api` | 禁止使用 `sun.*` / `com.sun.*` JDK 内部 API |

### 3.3 LayeringRulesTest（分层约束）

| 规则 ID | 规则名称 | 说明 |
|---------|----------|------|
| LAYER-01 | `services_must_not_depend_on_controllers` | `..service..` 包不得依赖 `..controller..` 包 |
| LAYER-02 | `controllers_must_not_access_repositories` | `..controller..` 包中的类不得直接访问以 `Repository` 结尾的类 |
| LAYER-03 | `no_package_cycles` | `dev.meirong.shop.*` 顶层包之间不得存在循环依赖 |

### 3.4 NamingRulesTest（命名规范）

| 规则 ID | 规则名称 | 说明 |
|---------|----------|------|
| NAME-01 | `rest_controllers_named_correctly` | `@RestController` 类名必须以 `Controller` 结尾 |
| NAME-02 | `entities_in_domain_package` | `@Entity` 类必须在 `..domain..` 包中 |

### 3.5 SpringRulesTest（Spring 特定规则）

| 规则 ID | 规则名称 | 说明 |
|---------|----------|------|
| SPRING-01 | `bff_must_not_have_jpa_entities` | BFF 模块（`..buyerbff..|..sellerbff..`）不得包含 `@Entity` 类；BFF 通过 HTTP 获取数据，不直接操作数据库 |
| SPRING-02 | `contracts_must_be_lightweight` | `shop-contracts`（`dev.meirong.shop.contracts`）不得依赖 Spring Web/Data/Kafka/JPA 运行时 |

---

## 四、项目结构约定（本规则的适配依据）

```
dev.meirong.shop.{service}/
├── config/         → Spring 配置类
├── controller/     → @RestController（名称必须以 Controller 结尾）
├── service/        → @Service 业务逻辑（也包含 engine、index 等专用子包）
├── domain/         → @Entity + JpaRepository（同一包，项目约定）
└── listener/ / kafka/ → Kafka Consumer
```

> **关键差异**：本项目 `domain` 包同时放置 Entity 和 Repository（不拆分为 `entity/repository`），这是项目约定而非强制规范。

---

## 五、运行方式

```bash
# 运行所有架构测试
./mvnw -pl architecture-tests -am test

# 只运行架构测试（跳过其他测试）
./mvnw -pl architecture-tests test
```

---

## 六、FreezingArchRule 使用指南

当发现新规则有历史违规需要临时豁免时：

```java
// 1. 在 architecture-tests/src/test/resources/archunit.properties 中配置
freeze.store.default.path=src/test/resources/archunit_store
freeze.store.default.allowStoreCreation=true
freeze.store.default.allowStoreUpdate=false

// 2. 用 FreezingArchRule.freeze() 包裹规则
@ArchTest
static final ArchRule my_rule = FreezingArchRule.freeze(
    noClasses().should()...
);

// 3. 首次运行（记录已知违规）
./mvnw -pl architecture-tests test \
  -Darchunit.freeze.store.default.allowStoreCreation=true \
  -Darchunit.freeze.store.default.allowStoreUpdate=true

// 4. 提交 archunit_store/ 目录（技术债清单）
// 5. 后续 CI 仅阻断新增违规
```

---

## 七、扩展规则参考（未来演进方向）

以下规则可在违规修复或新功能开发时按需引入：

| 规则 | 说明 | 参考 |
|------|------|------|
| `transactional_must_specify_manager` | 多数据源服务的 `@Transactional` 必须显式指定 `transactionManager` | 仅适用于多数据源模块 |
| `layered_architecture`（严格） | Controller→Service→Repository→Entity 严格分层，禁止跨层访问 | 需配合 FreezingArchRule |
| `services_in_service_package` | `@Service` 类必须在 `..service..|..engine..|..index..` 包中 | 需排除合法的 engine/index 包 |
| `no_transactional_on_interface` | `@Transactional` 不得标注在接口方法上（Spring 代理不支持接口级事务） | — |
| `config_in_config_package` | `@Configuration` 类（排除 `@SpringBootApplication`）必须在 `..config..` 包中 | shop-common 有合法例外 |

---

## 八、参考来源

- [ArchUnit 官方文档](https://www.archunit.org/userguide/html/000_Index.html)
- [Meirong ArchUnit Guideline 2026](../meirongdev/guideline/docs/04-specifications/meirong-archunit-guideline-2026.md)（内部参考）
- `architecture-tests/src/test/java/dev/meirong/shop/architecture/` — 所有规则实现
