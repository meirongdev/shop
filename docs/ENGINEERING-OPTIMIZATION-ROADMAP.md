# Shop Platform — Engineering Optimization Roadmap

> 版本：1.0 | 日期：2026-04-12
> 适用范围：开发效率（Inner Loop）+ 架构质量 + 云原生运维
> 状态：提案阶段 — 待团队评审

---

## 一、背景

截至 2026 Q2，项目已经具备成熟的工程实践：

- **技术栈**：Java 25 + Spring Boot 3.5.11 + Spring Cloud 2025.0.1 + Kotlin 2.3
- **质量门禁**：19 条 ArchUnit 规则 + Git Hooks + CI path-filtered jobs
- **测试基座**：Testcontainers @ServiceConnection + WireMock 契约测试（13 个）
- **弹性治理**：Resilience4j 全量标准化（CircuitBreaker + Retry + Bulkhead + TimeLimiter）
- **可观测性**：Actuator + Prometheus + OTLP + Loki + Tempo + Grafana 全栈
- **事件驱动**：Transactional Outbox + Kafka + IdempotencyGuard + Bloom Filter
- **开发体验**：Tilt + mirrord + Kind 集群 + Makefile 统一入口

**本文件聚焦"从 90 分到 95 分"的增量优化**，而非基础能力建设。

---

## 二、优化全景

```
效率优先 (P0-P1)          质量深化 (P1-P2)           云原生进阶 (P2+)
══════════════════        ══════════════════         ═══════════════════
Maven Build Cache    →    Spring Modulith PoC    →    SBOM + 镜像签名
OpenAPI → KMP 代码生成    SCC 契约测试落地            Error Prone + NullAway
Inner Loop 优化           Pitest 核心域              Playwright 视觉回归
```

---

## 三、效率优先（P0-P1）

### 3.1 Maven Build Cache

**痛点**：15 个服务模块 + 2 个 shared 模块，全量 `./mvnw verify` 耗时 3-5 分钟。

**方案**：开启 Maven Build Cache 的本地缓存（免费版本）。

```xml
<!-- .mvn/maven-build-cache-config.xml -->
<?xml version="1.0" encoding="UTF-8"?>
<cache xmlns="http://maven.apache.org/BUILD-CACHE-CONFIG/1.0.0">
    <configuration>
        <enabled>true</enabled>
        <hashAlgorithm>SHA-256</hashAlgorithm>
        <cacheLocation>${user.home}/.m2/build-cache</cacheLocation>
        <maxCacheSizeInMB>500</maxCacheSizeInMB>
    </configuration>
</cache>
```

```bash
# 启用方式（Spring Boot 3.5 已内置支持）
./mvnw verify -Dmaven.buildCache.enabled=true

# 开发时增量构建
./mvnw test -pl services/order-service -am \
    -Dmaven.buildCache.enabled=true \
    -Dmaven.buildCache.save=true
```

**预期收益**：未变更模块跳过编译/测试，本地构建提速 50%+。

**工作量**：0.5 天（配置 + 验证）

---

### 3.2 OpenAPI → KMP 代码生成

**痛点**：`shop-contracts` 是纯 Java 模块，KMP WASM 前端无法直接使用 DTO，导致前端手写 DTO 与后端漂移。

**方案**：通过 springdoc-openapi 生成规范，再用 openapi-generator 生成 KMP Kotlin 数据类。

```
架构流：
  Domain Service (springdoc) → OpenAPI YAML
    └→ openapi-generator (Kotlin Multiplatform)
         └→ KMP 前端直接消费 DTO
```

**实施步骤**：

**Step 1：Domain Service 生成 OpenAPI spec（CI 中自动执行）**

```xml
<!-- services/order-service/pom.xml -->
<plugin>
    <groupId>org.springdoc</groupId>
    <artifactId>springdoc-openapi-maven-plugin</artifactId>
    <executions>
        <execution>
            <id>generate-openapi</id>
            <phase>prepare-package</phase>
            <goals><goal>generate</goal></goals>
            <configuration>
                <outputFileName>openapi.yaml</outputFileName>
                <outputDir>${project.build.directory}</outputDir>
            </configuration>
        </execution>
    </executions>
</plugin>
```

**Step 2：KMP 构建时生成 DTO**

```kotlin
// frontend/kmp/core/build.gradle.kts
tasks.register<org.openapitools.generator.gradle.plugin.tasks.GenerateTask>("generateApi") {
    generatorName.set("kotlin")
    library.set("multiplatform")
    inputSpec.set(rootProject.file("../../services/order-service/target/openapi.yaml"))
    outputDir.set(layout.buildDirectory.dir("generated/openapi"))
    generateModelsOnly.set(true)
    packageName.set("dev.meirong.shop.kmp.contracts.order")
}

kotlin.sourceSets["commonMain"].kotlin.srcDir(tasks.named("generateApi"))
```

**Step 3：CI 流水线集成**

```yaml
# .github/workflows/ci.yml 新增
- name: Generate KMP Contracts
  run: |
    ./mvnw -pl services/order-service springdoc-openapi:generate -DskipTests
    (cd frontend && ./gradlew :kmp:core:generateApi)
```

**权衡**：
- ✅ 类型安全端到端，后端改 DTO 编译期暴露
- ⚠️ 多一层生成步骤，CI 时间 +30 秒
- ⚠️ 需要先启动服务或跑 springdoc 测试才能生成 spec

**工作量**：2 周（PoC 1 个服务 → 验证 → 推广）

**触发条件**：当 KMP 前端 DTO 维护成本 > 代码生成复杂度时立即启动。

---

### 3.3 Inner Loop 优化：spring-boot-docker-compose 深度集成

**现状**：已有 Tilt + Kind + `make local-access`，但开发时仍需理解 K8s 部署模型。

**增量优化**：为简单场景提供"无 K8s"的开发模式。

```xml
<!-- 任意 domain service pom.xml -->
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-docker-compose</artifactId>
    <scope>runtime</scope>
    <optional>true</optional>
</dependency>
```

```yaml
# services/order-service/src/main/docker-compose.yml
services:
  mysql:
    image: mysql:8.4
    environment:
      MYSQL_ROOT_PASSWORD: root
      MYSQL_DATABASE: shop_order
    ports:
      - "3306:3306"
  kafka:
    image: apache/kafka-native:3.9
    ports:
      - "9092:9092"
  redis:
    image: redis:7
    ports:
      - "6379:6379"
```

```bash
# 开发时直接启动
./mvnw -pl services/order-service spring-boot:run
# Spring Boot 自动发现 docker-compose.yml → 启动容器 → 注入连接属性
```

**适用场景**：新人首次跑服务、单服务调试、不需要 K8s 特性的场景。

**不替代**：Tilt + Kind 仍是完整的内循环方案。docker-compose 是"轻量快捷方式"。

**工作量**：1 天（为每个服务添加 docker-compose.yml）

---

## 四、质量深化（P1-P2）

### 4.1 Spring Modulith — 领域边界治理

**痛点**：随着服务业务逻辑增长，domain service 内部可能退化为"大泥潭"（big ball of mud）。

**方案**：在复杂 domain service 中引入 Spring Modulith，验证子模块边界。

**适用服务**（优先级排序）：

| 服务 | 子领域 | 推荐理由 |
|------|--------|----------|
| marketplace-service | product / inventory / category / review | 4 个子领域，边界清晰 |
| order-service | order / order-item / payment / refund / shipment | 5 个子领域，状态机复杂 |
| promotion-service | coupon / promotion / strategy / evaluator | 策略模式 + 条件评估 |

**不建议的服务**：auth-server、notification-service（窄职责，Modulith 是过度设计）。

**实施**：

```java
// marketplace-service 应用入口
@SpringBootApplication
@ApplicationModule(
    name = "marketplace",
    allowedBasePackages = {
        "dev.meirong.shop.marketplace.product",
        "dev.meirong.shop.marketplace.inventory",
        "dev.meirong.shop.marketplace.category",
        "dev.meirong.shop.marketplace.review"
    }
)
class MarketplaceServiceApplication { }

// 模块边界验证测试
@Test
void verifyModuleStructure() {
    Modules modules = Modules.of(MarketplaceServiceApplication.class);
    modules.verify();
}

// 领域事件文档
@Test
void documentEvents() {
    EventPublicationRegistry registry = ...;
    DocumentedEvents docs = DocumentedEvents.of(registry);
    docs.writeTo(Paths.get("target/modulith/events.md"));
}
```

**与现有 Outbox 的增强关系**：

```java
// 当前：手动写出库记录
@Transactional
public void createOrder(OrderCommand cmd) {
    orderRepository.save(order);
    outboxRepository.save(new OutboxRecord("order.created", order));
}

// Modulith 增强后：自动捕获 @TransactionalEventListener
@Transactional
public void createOrder(OrderCommand cmd) {
    orderRepository.save(order);
    applicationEventPublisher.publishEvent(new OrderCreatedEvent(order));
}

@TransactionalEventListener(phase = AFTER_COMMIT)
void on(OrderCreatedEvent event) {
    // 自动进入 Outbox，由现有 5 秒轮询发布器处理
    outboxRepository.save(OutboxRecord.from(event));
}
```

**权衡**：
- ✅ 自动检测包级循环依赖、领域边界违规
- ✅ 领域事件文档化，新成员一目了然
- ⚠️ 增加依赖、学习曲线
- ⚠️ 对窄服务是负担

**工作量**：3 周（PoC 2 个服务 → 验证收益 → 决定是否推广）

---

### 4.2 Spring Cloud Contract — 契约测试落地

> 详见：`docs/CONTRACT-TESTING-GUIDE.md`（实操手册）

**痛点**：BFF → Domain Service 接口变更导致生产事故，当前依赖 WireMock 手写 stub。

**方案**：Spring Cloud Contract Producer-Driven 模式。

**为什么选 SCC 而非 Pact**：

| 维度 | Spring Cloud Contract | Pact |
|------|----------------------|------|
| 驱动模式 | Producer 驱动（与 shop-contracts 理念一致） | Consumer 驱动 |
| 与 Spring 集成 | `@AutoConfigureStubRunner` 一行搞定 | 需要手动配置 |
| Stub 发布 | 直接发布到 Maven 仓库 | 需要 Pact Broker |
| 多语言支持 | 主要 Java | Java/JS/Go/Rust |
| 学习曲线 | Groovy DSL | JSON 契约 |

**决策依据**：你们是纯 Java/Spring 技术栈 + `shop-contracts` 已经是集中式契约 + Domain Service 是"真相来源"，这正是 SCC Producer-Driven 的理想场景。

**首期范围**（2-3 周）：

```
Phase 1：为 3 个核心服务写契约（order, marketplace, profile）
Phase 2：配置 stub jar 发布到 Maven 仓库
Phase 3：BFF 集成 @AutoConfigureStubRunner 验证
Phase 4：CI 流水线集成
```

**详细实施指南**：参见 [`docs/CONTRACT-TESTING-GUIDE.md`](./CONTRACT-TESTING-GUIDE.md)

---

### 4.3 Error Prone + NullAway + JSpecify

**痛点**：运行时 NPE 仍然可能发生，尤其在边界层（Controller 入参、RPC 响应、Kafka 消息解析）。

**方案**：编译期 nullability 检查。

```xml
<!-- 父 pom.xml -->
<plugin>
    <groupId>org.apache.maven.plugins</groupId>
    <artifactId>maven-compiler-plugin</artifactId>
    <configuration>
        <annotationProcessorPaths>
            <path>
                <groupId>com.uber.nullaway</groupId>
                <artifactId>nullaway</artifactId>
                <version>0.12.0</version>
            </path>
        </annotationProcessorPaths>
        <compilerArgs>
            <arg>-XDcompilePolicy=simple</arg>
            <arg>--should-stop=ifError=FLOW</arg>
            <arg>-Xplugin:ErrorProne</arg>
            <arg>-Xep:NullAway:ERROR</arg>
            <arg>-XepOpt:NullAway:AnnotatedPackages=dev.meirong.shop</arg>
            <arg>-XepOpt:NullAway:JSpecifyMode=true</arg>
        </compilerArgs>
    </configuration>
</plugin>
```

```java
// shop-contracts 中使用 JSpecify 注解
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

public record OrderDto(
    @NonNull String orderId,
    @NonNull String status,
    @Nullable String trackingNumber,  // 发货前为 null
    @Nullable Instant shippedAt
) { }
```

**渐进式迁移策略**：

```
Week 1：接入 Error Prone + NullAway，先只加到新模块
Week 2：为边界层（Controller 入参、DTO、Client 响应）加 @Nullable
Week 3：逐步扩展到 service 层
Week 4：全量启用，CI 门禁
```

**权衡**：
- ✅ Java 25 + Spring Boot 3.5 对 JSpecify 支持已成熟
- ✅ 你们的 `record` DTO + 构造器注入风格已大幅减少可变状态，迁移成本低
- ⚠️ 对已有代码库需要渐进式迁移
- ⚠️ Spring 的依赖注入框架对 `@Nullable` 支持有限（构造器注入不受影响）

**工作量**：2 周（渐进式接入）

---

### 4.4 Pitest Mutation Testing — 核心域

**痛点**：部分核心逻辑（wallet 对账、order 状态机、activity 防作弊）"行覆盖率高但断言弱"。

**方案**：在关键模块运行 Pitest，验证测试的"杀伤力"。

```xml
<!-- 仅在关键模块启用，不要全量跑 -->
<plugin>
    <groupId>org.pitest</groupId>
    <artifactId>pitest-maven</artifactId>
    <version>1.17.0</version>
    <configuration>
        <targetClasses>
            <param>dev.meirong.shop.wallet.domain.*</param>
            <param>dev.meirong.shop.order.domain.*</param>
            <param>dev.meirong.shop.activity.anticheat.*</param>
        </targetClasses>
        <targetTests>
            <param>dev.meirong.shop.wallet.*Test</param>
            <param>dev.meirong.shop.order.*Test</param>
            <param>dev.meirong.shop.activity.*Test</param>
        </targetTests>
        <mutationThreshold>80</mutationThreshold>
        <threads>4</threads>
    </configuration>
</plugin>
```

```bash
# 运行（耗时较长，不要默认执行）
./mvnw -pl services/wallet-service pitest:mutationCoverage

# 输出报告
# services/wallet-service/target/pit-reports/index.html
```

**重要**：Pitest 增加 5-10 倍测试执行时间。**只加到 `verify` 的 profile 里，不要默认执行。**

```xml
<profile>
    <id>mutation-test</id>
    <build>
        <plugins>
            <plugin>
                <groupId>org.pitest</groupId>
                <artifactId>pitest-maven</artifactId>
            </plugin>
        </plugins>
    </build>
</profile>
```

```bash
# CI 中按需触发
./mvnw -pl services/wallet-service -Pmutation-test verify
```

**工作量**：2 周（wallet + order 核心域）

---

## 五、云原生进阶（P2+）

### 5.1 SBOM + 镜像签名

**背景**：欧盟 CRA 2026 年 6 月生效，软件物料清单（SBOM）基本是合规硬要求。

```yaml
# .github/workflows/security.yml
jobs:
  sbom-and-sign:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      
      - name: Generate SBOM (CycloneDX)
        run: |
          ./mvnw org.cyclonedx:cyclonedx-maven-plugin:makeAggregateBom \
              -DoutputName=bom -DoutputFormat=json
      
      - name: Sign SBOM with cosign
        run: |
          cosign sign-blob \
              --key env://COSIGN_PRIVATE_KEY \
              --tlog-upload=false \
              target/bom.json > bom.json.sig
```

```dockerfile
# Dockerfile 中嵌入 SBOM
FROM eclipse-temurin:25-jre
COPY bom.json /sbom/bom.json
LABEL org.opencontainers.image.documentation="https://github.com/org/shop/blob/main/docs/deployment/SBOM.md"
```

**工作量**：1 周

---

### 5.2 Playwright 视觉回归测试

**背景**：KMP WASM 渲染在不同浏览器/分辨率下可能产生微妙差异，普通 DOM 断言覆盖不了。

```typescript
// frontend/e2e-tests/tests/buyer-app/product-list.spec.ts
import { test, expect } from '@playwright/test';

test('product list visual regression', async ({ page }) => {
  await page.goto('/buyer-app/products');
  
  // 等待 KMP WASM 渲染完成
  await page.waitForSelector('.kmp-root[aria-label="ProductList"]');
  
  // 视觉对比（首次运行建立 baseline）
  await expect(page).toHaveScreenshot('product-list.png', {
    fullPage: true,
    maxDiffPixels: 100,  // 允许少量抗锯齿差异
  });
});
```

**注意事项**：
- WASM 渲染可能因 GPU/字体/系统差异产生不同结果
- CI 中需要固定 Docker 镜像（`mcr.microsoft.com/playwright:v1.50.0-jammy`）
- 首次运行后需要人工审查 baseline，之后才是自动回归

**工作量**：1 周（关键页面覆盖）

---

## 六、明确不推荐的事项

### 6.1 ❌ GraalVM Native Image

**原因**：
1. `startupProbe` 给了 5 分钟预算（`failureThreshold=30, periodSeconds=10`）
2. K8s HPA 是预测性扩容，不是 Serverless 按需启动
3. GraalVM Native 的代价：构建时间 30s → 5min，不支持所有 Spring 特性，运行时性能通常比 JVM C2 慢 10-20%
4. 你们的服务类型（K8s Deployment + Virtual Threads）不适合 Native Image

**触发条件**：需要 < 1s 冷启动（Serverless）或内存 < 128MB。当前场景不满足。

### 6.2 ❌ Kafka Schema Registry（当前阶段）

**原因**：
1. 事件 DTO 通过 `shop-contracts` Java 模块共享，在纯 Java 微服务下是最简单有效的方案
2. KMP 不直接消费 Kafka（通过 BFF → REST/WebSocket → KMP Client）
3. 引入 Schema Registry 的触发条件是有非 Java 消费者直接消费 Kafka 事件

**触发条件**：当有非 Java 服务直接消费 Kafka 事件时再评估。

### 6.3 ❌ Kubernetes Gateway API + KEDA（当前规模）

**原因**：
1. 只有 1 个 gateway，金丝雀权重路由从代码迁移到 K8s 配置的收益很小
2. 事件消费是 Outbox 定时拉取（5 秒轮询），不是 push-based consumer group，KEDA 不适用
3. 架构复杂度增加超过当前规模的需求

**触发条件**：服务数 > 30 需要 GitOps 管理路由，或 Kafka 消费者从 Outbox 迁移到直接消费。

---

## 七、执行优先级与时间线

| 优先级 | 动作 | 预期时间 | 依赖 | 风险 |
|--------|------|----------|------|------|
| **P0** | Maven Build Cache | 0.5 天 | 无 | 低 |
| **P0** | SBOM + cosign 签名 | 1 周 | 无 | 低 |
| **P0** | Error Prone + NullAway 渐进接入 | 2 周 | 无 | 中（需迁移已有代码） |
| **P1** | SCC 契约测试 PoC（2 条路径） | 3 周 | BFF 契约稳定 | 中（维护成本） |
| **P1** | OpenAPI → KMP 代码生成 | 2 周 | springdoc 全服务覆盖 | 中（生成脚本调试） |
| **P1** | Spring Modulith PoC（2 服务） | 3 周 | 服务内部结构稳定 | 低 |
| **P2** | Pitest — wallet + order 核心域 | 2 周 | 测试覆盖充分 | 低 |
| **P2** | Playwright 视觉回归 | 1 周 | 无 | 低 |
| **延后** | Develocity 企业版 | 模块数 > 30 时 | CI > 10 分钟 | — |
| **跳过** | GraalVM Native | — | 不适用 | — |
| **跳过** | Schema Registry | — | 非 Java 消费者出现 | — |
| **跳过** | Gateway API + KEDA | — | 规模扩大后 | — |

---

## 八、与现有文档的关系

| 本文档 | 关联文档 | 关系 |
|--------|---------|------|
| 本文档 | `ROADMAP-2026.md` | 本文档聚焦工程优化，Roadmap 聚焦业务能力 |
| 本文档 | `ENGINEERING-STANDARDS-2026.md` | 本文档是标准的"下一步行动计划" |
| 本文档 | `TECH-STACK-BEST-PRACTICES-2026.md` | 本文档实践最佳实践中的"演进方向" |
| 本文档 | `DEVELOPER-EXPERIENCE-STANDARD-2026.md` | 本文档扩展 DX 标准（Build Cache、代码生成） |
| 本文档 | `ARCHUNIT-RULES.md` | NullAway 是 ArchUnit 的编译期补充 |
| [CONTRACT-TESTING-GUIDE.md](./CONTRACT-TESTING-GUIDE.md) | 本文档 4.2 节 | CDC 实操手册 |

---

## 九、决策记录（ADR）

### ADR-001: 选择 Spring Cloud Contract 而非 Pact

**背景**：需要在 BFF ↔ Domain Service 之间建立契约测试。

**决策**：采用 Spring Cloud Contract Producer-Driven 模式。

**理由**：
1. 纯 Java/Spring 技术栈，无多语言消费者需求
2. `shop-contracts` 已经是集中式契约，SCC 与理念一致
3. `@AutoConfigureStubRunner` 集成成本远低于 Pact
4. Stub jar 直接发布到 Maven 仓库，与现有构建流程一致

**后果**：
- 如果未来有非 Java 消费者需要相同契约，需额外适配
- Groovy DSL 需要团队学习（但只写一次）

### ADR-002: 不引入 GraalVM Native Image

**背景**：api-gateway 和 auth-server 冷启动场景。

**决策**：继续 JVM + Virtual Threads，不引入 Native Image。

**理由**：
1. K8s HPA 预测性扩容，冷启动不是瓶颈
2. 5 分钟 startupProbe 预算足够
3. Native Image 增加构建复杂度且运行时性能通常更差

**触发重新评估**：Serverless 迁移或 < 1s 冷启动需求出现时。

### ADR-003: OpenAPI 代码生成仅用于 KMP 前端

**背景**：是否用 openapi-generator 自动生成 BFF 的 `@HttpExchange` 客户端。

**决策**：BFF 侧继续手写 `@HttpExchange` 接口，仅为 KMP 前端生成 DTO。

**理由**：
1. `*Api.java` 常量 + `@HttpExchange` 已经是类型安全的契约
2. 生成代码的可读性和可调试性下降
3. KMP 前端手写 DTO 成本确实高，生成收益明确

---

## 十、参考依据

- Spring Modulith: https://spring.io/projects/spring-modulith
- Spring Cloud Contract: https://spring.io/projects/spring-cloud-contract
- Error Prone: https://errorprone.info/
- NullAway: https://github.com/uber/NullAway
- JSpecify: https://jspecify.dev/
- Pitest: https://pitest.org/
- CycloneDX: https://cyclonedx.org/
- cosign: https://github.com/sigstore/cosign
- Playwright: https://playwright.dev/
- OpenAPI Generator: https://openapi-generator.tech/
- Maven Build Cache: https://maven.apache.org/extensions/maven-build-cache-extension/
