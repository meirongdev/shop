# Shop Platform — Maven Archetype 使用教程

> 版本：1.0 | 最后更新：2026-04-01
> 适用范围：Shop Platform 全体开发人员

---

## 一、什么是 Archetype

**Maven Archetype** 是 Maven 的项目模板工具，用于按照预定义的模式生成项目骨架。在 Shop Platform 中，Archetype 不是"代码生成玩具"，而是**把平台基线直接下发到新模块的工程化工具**。

### 1.1 Archetype 解决什么问题

| 问题 | Archetype 方案 |
|------|---------------|
| 新服务目录结构不统一 | 预定义标准目录结构 |
| 依赖配置重复劳动 | 预置标准依赖清单 |
| 测试基座缺失 | 内置 Testcontainers 集成测试样板 |
| 配置模板手抄易错 | 统一 `application.yml`、K8s 部署清单 |
| 技术栈实现漂移 | 从生成阶段强制对齐平台标准 |

### 1.2 核心理念

> **把平台标准前置到生成阶段，而不是等代码写完再回头治理。**

---

## 二、Archetype 类型与选型指南

Shop Platform 提供 6 种标准 Archetype，覆盖所有服务类型：

### 2.1 选型决策树

```
需要创建新服务？
│
├─ 需要统一流量入口、路由、JWT 校验？
│  └─→ gateway-service-archetype
│
├─ 需要登录、认证、令牌管理？
│  └─→ auth-service-archetype
│
├─ 需要为前端聚合多个领域服务接口？
│  └─→ bff-service-archetype
│
├─ 需要独立领域模型、数据库、事务？
│  └─→ domain-service-archetype
│
├─ 需要异步处理、Kafka 消费、事件驱动？
│  └─→ event-worker-archetype
│
└─ 需要 SSR 门户、SEO 友好页面？
   └─→ portal-service-archetype (Kotlin + Thymeleaf)
```

### 2.2 详细对比表

| Archetype | 适用场景 | 预置技术栈 | 生成文件数 |
|-----------|---------|-----------|-----------|
| `gateway-service-archetype` | API 网关、边缘服务 | Spring Cloud Gateway WebFlux + OAuth2 RS + Redis | ~12 |
| `auth-service-archetype` | 认证、身份、SSO | Spring Security + JWT + JPA | ~15 |
| `bff-service-archetype` | BFF 聚合层 | Web MVC + RestClient + Resilience4j | ~14 |
| `domain-service-archetype` | 领域服务（事务型） | Web + JPA + Flyway + MySQL | ~18 |
| `event-worker-archetype` | 事件处理、异步任务 | Kafka + JPA + Flyway + MySQL | ~16 |
| `portal-service-archetype` | SSR 门户 | Kotlin + Thymeleaf + Web MVC | ~14 |

---

## 三、快速开始

### 3.1 前置要求

- **JDK 25+**（必须，与父 POM 一致）
- **Maven 3.9+**（或使用 `./mvnw`）
- **本地 Maven 仓库**（用于安装 archetype）

### 3.2 步骤 1：安装 Archetype 到本地仓库

在项目根目录执行：

```bash
make archetypes-install
```

等价手动命令：

```bash
./mvnw -pl shop-common,shop-contracts,shop-archetypes/gateway-service-archetype,shop-archetypes/auth-service-archetype,shop-archetypes/bff-service-archetype,shop-archetypes/domain-service-archetype,shop-archetypes/event-worker-archetype,shop-archetypes/portal-service-archetype -am install
```

**预期输出**：

```
[INFO] BUILD SUCCESS
[INFO] Total time:  15.420 s
```

### 3.3 步骤 2：生成新服务

> **重要**：在**空目录**中执行生成，避免 Maven 把临时样板工程自动加入当前 reactor。

```bash
# 设置仓库路径
SHOP_REPO=/Users/matthew/projects/meirongdev/shop

# 创建临时沙箱目录
mkdir -p /tmp/shop-archetype-sandbox
cd /tmp/shop-archetype-sandbox
```

#### 示例 1：生成领域服务（最常用）

```bash
"${SHOP_REPO}/mvnw" archetype:generate \
  -DarchetypeCatalog=local \
  -DarchetypeGroupId=dev.meirong.shop \
  -DarchetypeArtifactId=domain-service-archetype \
  -DarchetypeVersion=0.1.0-SNAPSHOT \
  -DgroupId=dev.meirong.shop \
  -DartifactId=inventory-service \
  -Dpackage=dev.meirong.shop.inventory \
  -DshopPlatformVersion=0.1.0-SNAPSHOT \
  -DinteractiveMode=false
```

#### 示例 2：生成 BFF 服务

```bash
"${SHOP_REPO}/mvnw" archetype:generate \
  -DarchetypeCatalog=local \
  -DarchetypeGroupId=dev.meirong.shop \
  -DarchetypeArtifactId=bff-service-archetype \
  -DarchetypeVersion=0.1.0-SNAPSHOT \
  -DgroupId=dev.meirong.shop \
  -DartifactId=buyer-bff \
  -Dpackage=dev.meirong.shop.buyer.bff \
  -DshopPlatformVersion=0.1.0-SNAPSHOT \
  -DinteractiveMode=false
```

#### 示例 3：生成事件处理 Worker

```bash
"${SHOP_REPO}/mvnw" archetype:generate \
  -DarchetypeCatalog=local \
  -DarchetypeGroupId=dev.meirong.shop \
  -DarchetypeArtifactId=event-worker-archetype \
  -DarchetypeVersion=0.1.0-SNAPSHOT \
  -DgroupId=dev.meirong.shop \
  -DartifactId=order-event-processor \
  -Dpackage=dev.meirong.shop.order.worker \
  -DshopPlatformVersion=0.1.0-SNAPSHOT \
  -DinteractiveMode=false
```

#### 参数说明

| 参数 | 说明 | 示例值 |
|------|------|--------|
| `archetypeCatalog` | 固定为 `local`（使用本地安装的模板） | `local` |
| `archetypeGroupId` | 固定为 `dev.meirong.shop` | `dev.meirong.shop` |
| `archetypeArtifactId` | archetype 类型 | `domain-service-archetype` |
| `archetypeVersion` | archetype 版本 | `0.1.0-SNAPSHOT` |
| `groupId` | 新服务的 Maven groupId | `dev.meirong.shop` |
| `artifactId` | 新服务的模块名 | `inventory-service` |
| `package` | Java 包名（建议 4 段式） | `dev.meirong.shop.inventory` |
| `shopPlatformVersion` | 父 POM 版本 | `0.1.0-SNAPSHOT` |
| `interactiveMode` | 固定为 `false`（非交互模式） | `false` |

### 3.4 步骤 3：验证生成的服务

```bash
# 查看生成的目录结构
tree inventory-service

# 编译并运行测试
cd inventory-service
"${SHOP_REPO}/mvnw" test
```

**预期输出**：

```
[INFO] Tests run: 3, Failures: 0, Errors: 0, Skipped: 0
[INFO] BUILD SUCCESS
```

### 3.5 步骤 4：集成到项目仓库

```bash
# 移动生成的服务到项目目录
mv inventory-service /Users/matthew/projects/meirongdev/shop/

# 编辑根 pom.xml，添加新模块
cd /Users/matthew/projects/meirongdev/shop
# 在 <modules> 标签中添加：
# <module>inventory-service</module>
```

### 3.6 步骤 5：完整验证

```bash
# 运行完整构建验证
make verify

# 或仅测试新模块
./mvnw -pl inventory-service -am test
```

---

## 四、各 Archetype 详解

### 4.1 domain-service-archetype（领域服务）

**适用场景**：需要独立数据库、领域模型、事务管理的核心业务服务。

**典型用例**：
- `order-service`（订单服务）
- `product-service`（商品服务）
- `customer-service`（客户服务）
- `inventory-service`（库存服务）

**生成的目录结构**：

```
inventory-service/
├── pom.xml                          # Maven 配置（含 JPA/Flyway/MySQL）
├── README.md                        # 服务说明文档
├── k8s/
│   ├── deployment.yaml              # K8s 部署配置（含健康检查）
│   ├── service.yaml                 # K8s Service 配置
│   └── hpa.yaml                     # K8s 自动扩缩容配置
└── src/
    ├── main/
    │   ├── java/dev/meirong/shop/inventory/
    │   │   ├── InventoryServiceApplication.java  # 启动类
    │   │   ├── controller/
    │   │   │   └── DomainPingController.java     # 示例 Controller
    │   │   ├── domain/
    │   │   │   ├── SampleAggregateEntity.java    # 示例聚合根
    │   │   │   └── SampleAggregateRepository.java
    │   │   └── service/
    │   │       └── TemplateDomainService.java    # 示例 Service
    │   └── resources/
    │       ├── application.yml                    # 应用配置（含 Flyway/OTLP）
    │       └── db/migration/
    │           └── V1__init.sql                   # Flyway 迁移脚本
    └── test/
        ├── java/dev/meirong/shop/inventory/
        │   ├── controller/
        │   │   └── DomainPingControllerTest.java
        │   ├── service/
        │   │   └── TemplateDomainServiceTest.java
        │   └── support/
        │       └── AbstractMySqlIntegrationTest.java  # Testcontainers 基座
        └── resources/
```

**预置依赖**：

```xml
<!-- Web + JPA + Flyway + MySQL -->
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-web</artifactId>
</dependency>
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-data-jpa</artifactId>
</dependency>
<dependency>
    <groupId>org.flywaydb</groupId>
    <artifactId>flyway-core</artifactId>
</dependency>
<dependency>
    <groupId>org.flywaydb</groupId>
    <artifactId>flyway-mysql</artifactId>
</dependency>

<!-- 可观测性 -->
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-actuator</artifactId>
</dependency>
<dependency>
    <groupId>io.micrometer</groupId>
    <artifactId>micrometer-registry-prometheus</artifactId>
</dependency>
<dependency>
    <groupId>io.micrometer</groupId>
    <artifactId>micrometer-tracing-bridge-otel</artifactId>
</dependency>

<!-- 测试 -->
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-testcontainers</artifactId>
    <scope>test</scope>
</dependency>
<dependency>
    <groupId>org.testcontainers</groupId>
    <artifactId>mysql</artifactId>
    <scope>test</scope>
</dependency>
```

**关键特性**：
- ✅ 预置 Flyway 迁移目录（`db/migration/`）
- ✅ Testcontainers MySQL 集成测试基座
- ✅ 健康检查探针（readiness/liveness）
- ✅ OTLP Tracing 配置
- ✅ 内部调用安全（`shop.security.internal.token`）

---

### 4.2 bff-service-archetype（BFF 聚合服务）

**适用场景**：为前端聚合多个领域服务接口，提供端侧适配层。

**典型用例**：
- `buyer-bff`（买家聚合层）
- `seller-bff`（卖家聚合层）
- `mobile-bff`（移动端聚合层）

**生成的目录结构**：

```
buyer-bff/
├── pom.xml                          # Maven 配置（含 Resilience4j）
├── README.md
├── k8s/
│   ├── deployment.yaml
│   ├── service.yaml
│   └── hpa.yaml
└── src/
    ├── main/
    │   ├── java/dev/meirong/shop/buyer/bff/
    │   │   ├── BuyerBffApplication.java
    │   │   ├── controller/
    │   │   │   └── BffPingController.java
    │   │   ├── client/
    │   │   │   └── TemplateServiceClient.java  # RestClient 客户端模板
    │   │   └── config/
    │   │       └── BffClientConfig.java        # RestClient 配置
    │   └── resources/
    │       └── application.yml
    └── test/
        └── java/dev/meirong/shop/buyer/bff/
            └── controller/
                └── BffPingControllerTest.java
```

**预置依赖**：

```xml
<!-- BFF 核心 -->
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-web</artifactId>
</dependency>
<dependency>
    <groupId>io.github.resilience4j</groupId>
    <artifactId>resilience4j-spring-boot3</artifactId>
</dependency>

<!-- 可观测性 -->
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-actuator</artifactId>
</dependency>
<dependency>
    <groupId>io.micrometer</groupId>
    <artifactId>micrometer-registry-prometheus</artifactId>
</dependency>
<dependency>
    <groupId>io.micrometer</groupId>
    <artifactId>micrometer-tracing-bridge-otel</artifactId>
</dependency>
```

**关键特性**：
- ✅ Resilience4j 弹性治理（CircuitBreaker/Retry/Bulkhead）
- ✅ RestClient 客户端模板（Virtual Threads 就绪）
- ✅  bounded timeout 配置
- ✅ 降级策略样板

---

### 4.3 event-worker-archetype（事件处理 Worker）

**适用场景**：Kafka 事件消费、异步后台处理、最终一致性补偿。

**典型用例**：
- `order-event-processor`（订单事件处理）
- `notification-worker`（通知发送 Worker）
- `points-expiry-processor`（积分过期处理）

**生成的目录结构**：

```
order-event-processor/
├── pom.xml                          # Maven 配置（含 Kafka/Testcontainers）
├── README.md
├── k8s/
│   ├── deployment.yaml
│   ├── service.yaml
│   └── hpa.yaml
└── src/
    ├── main/
    │   ├── java/dev/meirong/shop/order/worker/
    │   │   ├── EventWorkerApplication.java
    │   │   ├── listener/
    │   │   │   └── TemplateEventListener.java  # Kafka Listener 模板
    │   │   ├── domain/
    │   │   │   └── ProcessedEventEntity.java   # 事件记录实体
    │   │   └── repository/
    │   │       └── ProcessedEventRepository.java
    │   └── resources/
    │       ├── application.yml
    │       └── db/migration/
    │           └── V1__init.sql
    └── test/
        ├── java/dev/meirong/shop/order/worker/
        │   ├── listener/
        │   │   └── TemplateEventListenerTest.java
        │   └── support/
        │       └── AbstractKafkaIntegrationTest.java
        └── resources/
```

**预置依赖**：

```xml
<!-- Kafka + JPA -->
<dependency>
    <groupId>org.springframework.kafka</groupId>
    <artifactId>spring-kafka</artifactId>
</dependency>
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-data-jpa</artifactId>
</dependency>
<dependency>
    <groupId>org.flywaydb</groupId>
    <artifactId>flyway-core</artifactId>
</dependency>

<!-- 测试（Kafka + MySQL） -->
<dependency>
    <groupId>org.testcontainers</groupId>
    <artifactId>kafka</artifactId>
    <scope>test</scope>
</dependency>
<dependency>
    <groupId>org.testcontainers</groupId>
    <artifactId>mysql</artifactId>
    <scope>test</scope>
</dependency>
```

**关键特性**：
- ✅ Kafka Listener 样板代码（含幂等处理）
- ✅ Testcontainers Kafka + MySQL 集成测试
- ✅ 事件记录表结构（用于去重/审计）
- ✅ 批量处理配置模板

---

### 4.4 gateway-service-archetype（网关服务）

**适用场景**：统一流量入口、路由转发、JWT 校验、限流、灰度发布。

**典型用例**：
- `api-gateway`（主网关）
- `partner-gateway`（合作伙伴网关）
- `admin-gateway`（管理后台网关）

**生成的目录结构**：

```
partner-gateway/
├── pom.xml                          # Maven 配置（WebFlux + OAuth2 RS）
├── README.md
├── k8s/
│   ├── deployment.yaml
│   ├── service.yaml
│   └── hpa.yaml
└── src/
    ├── main/
    │   ├── java/dev/meirong/shop/partner/gateway/
    │   │   ├── PartnerGatewayApplication.java
    │   │   ├── config/
    │   │   │   ├── GatewaySecurityConfig.java    # 安全配置
    │   │   │   ├── GatewayProperties.java        # 配置绑定
    │   │   │   └── RateLimitingFilter.java       # 限流过滤器
    │   │   └── filter/
    │   │       └── TrustedHeaderFilter.java      # 可信头注入
    │   └── resources/
    │       ├── application.yml
    │       └── application.yml                    # Gateway 路由配置
    └── test/
        └── java/dev/meirong/shop/partner/gateway/
            └── filter/
                └── RateLimitingFilterTest.java
```

**预置依赖**：

```xml
<!-- Spring Cloud Gateway WebFlux -->
<dependency>
    <groupId>org.springframework.cloud</groupId>
    <artifactId>spring-cloud-starter-gateway-server-webflux</artifactId>
</dependency>

<!-- OAuth2 Resource Server（JWT 校验） -->
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-oauth2-resource-server</artifactId>
</dependency>

<!-- 可观测性 -->
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-actuator</artifactId>
</dependency>
<dependency>
    <groupId>io.micrometer</groupId>
    <artifactId>micrometer-registry-prometheus</artifactId>
</dependency>
<dependency>
    <groupId>io.micrometer</groupId>
    <artifactId>micrometer-tracing-bridge-otel</artifactId>
</dependency>
```

**关键特性**：
- ✅ Spring Cloud Gateway WebFlux 基础配置
- ✅ OAuth2 Resource Server（JWT 校验）
- ✅ 限流过滤器模板（Redis Lua）
- ✅ 可信头注入过滤器
- ✅ YAML 路由配置模板

---

### 4.5 auth-service-archetype（认证服务）

**适用场景**：用户认证、令牌管理、社交登录、OTP 验证。

**典型用例**：
- `auth-server`（主认证服务）
- `sso-service`（单点登录服务）
- `identity-service`（身份管理服务）

**生成的目录结构**：

```
auth-server/
├── pom.xml                          # Maven 配置（Security + JWT）
├── README.md
├── k8s/
│   ├── deployment.yaml
│   ├── service.yaml
│   └── hpa.yaml
└── src/
    ├── main/
    │   ├── java/dev/meirong/shop/auth/
    │   │   ├── AuthServerApplication.java
    │   │   ├── controller/
    │   │   │   └── AuthController.java           # 认证接口
    │   │   ├── service/
    │   │   │   ├── JwtTokenService.java          # JWT 服务
    │   │   │   └── UserService.java              # 用户服务
    │   │   ├── config/
    │   │   │   ├── SecurityConfig.java           # Spring Security 配置
    │   │   │   └── JwtConfig.java                # JWT 配置
    │   │   └── domain/
    │   │       └── UserEntity.java               # 用户实体
    │   └── resources/
    │       ├── application.yml
    │       └── db/migration/
    │           └── V1__init.sql
    └── test/
        └── java/dev/meirong/shop/auth/
            └── service/
                └── JwtTokenServiceTest.java
```

**预置依赖**：

```xml
<!-- Spring Security -->
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-security</artifactId>
</dependency>

<!-- JWT（通过 shop-common 传递依赖） -->
<dependency>
    <groupId>dev.meirong.shop</groupId>
    <artifactId>shop-common</artifactId>
    <version>${shopPlatformVersion}</version>
</dependency>

<!-- 可观测性 -->
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-actuator</artifactId>
</dependency>
<dependency>
    <groupId>io.micrometer</groupId>
    <artifactId>micrometer-registry-prometheus</artifactId>
</dependency>
<dependency>
    <groupId>io.micrometer</groupId>
    <artifactId>micrometer-tracing-bridge-otel</artifactId>
</dependency>
```

**关键特性**：
- ✅ Spring Security 配置模板
- ✅ JWT Token 服务样板
- ✅ 用户实体与 Repository
- ✅ Flyway 数据库迁移

---

### 4.6 portal-service-archetype（SSR 门户）

**适用场景**：SEO 友好的服务端渲染页面、Kotlin + Thymeleaf 门户。

**典型用例**：
- `buyer-portal`（买家门户）
- `seller-portal`（卖家门户）
- `marketing-portal`（营销门户）

**生成的目录结构**：

```
buyer-portal/
├── pom.xml                          # Maven 配置（Kotlin + Thymeleaf）
├── README.md
├── k8s/
│   ├── deployment.yaml
│   ├── service.yaml
│   └── hpa.yaml
└── src/
    ├── main/
    │   ├── kotlin/dev/meirong/shop/buyer/portal/
    │   │   ├── BuyerPortalApplication.kt
    │   │   ├── controller/
    │   │   │   └── BuyerPortalController.kt    # Thymeleaf Controller
    │   │   ├── client/
    │   │   │   └── BuyerApiClient.kt           # BFF 客户端
    │   │   └── config/
    │   │       └── PortalConfig.kt
    │   └── resources/
    │       ├── application.yml
    │       └── templates/
    │           ├── index.html                   # Thymeleaf 模板
    │           └── fragments/
    │               └── header.html
    └── test/
        └── kotlin/dev/meirong/shop/buyer/portal/
            └── controller/
                └── BuyerPortalControllerTest.kt
```

**预置依赖**：

```xml
<!-- Kotlin -->
<dependency>
    <groupId>org.jetbrains.kotlin</groupId>
    <artifactId>kotlin-stdlib</artifactId>
</dependency>
<dependency>
    <groupId>org.jetbrains.kotlin</groupId>
    <artifactId>kotlin-reflect</artifactId>
</dependency>
<dependency>
    <groupId>com.fasterxml.jackson.module</groupId>
    <artifactId>jackson-module-kotlin</artifactId>
</dependency>

<!-- Thymeleaf -->
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-thymeleaf</artifactId>
</dependency>

<!-- 可观测性 -->
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-actuator</artifactId>
</dependency>
<dependency>
    <groupId>io.micrometer</groupId>
    <artifactId>micrometer-registry-prometheus</artifactId>
</dependency>
<dependency>
    <groupId>io.micrometer</groupId>
    <artifactId>micrometer-tracing-bridge-otel</artifactId>
</dependency>
```

**关键特性**：
- ✅ Kotlin 源码目录（`src/main/kotlin/`）
- ✅ Thymeleaf 模板目录（`templates/`）
- ✅ Kotlin Maven 插件配置（含 `all-open`）
- ✅ JVM Target 25 配置

---

## 五、开发最佳实践

### 5.1 生成后必做清单

生成新服务后，按以下顺序完成初始化：

```
□ 1. 更新 README.md（服务职责、接口说明）
□ 2. 修改 application.yml（服务名、数据库配置）
□ 3. 编写 Flyway 迁移脚本（V1__init.sql）
□ 4. 实现业务 Controller/Service/Repository
□ 5. 补充单元测试（目标覆盖率 > 80%）
□ 6. 更新 K8s 部署配置（副本数、资源限制）
□ 7. 添加 OpenAPI 文档（@Schema 注解）
□ 8. 在根 pom.xml 添加 <module>
```

### 5.2 命名规范

| 类型 | 命名规范 | 示例 |
|------|---------|------|
| artifactId | `kebab-case`，以 `-service` 或 `-bff` 结尾 | `inventory-service` |
| package | 4 段式，`dev.meirong.shop.<domain>` | `dev.meirong.shop.inventory` |
| 类名 | `PascalCase`，语义清晰 | `InventoryService` |
| 方法名 | `camelCase`，动词开头 | `getInventory` |
| 数据库名 | `shop_<domain>` | `shop_inventory` |

### 5.3 配置管理

**不要硬编码配置**，使用环境变量占位符：

```yaml
# ✅ 推荐
spring:
  datasource:
    url: jdbc:mysql://${DB_HOST:localhost}:${DB_PORT:3306}/shop_inventory
    username: ${DB_USERNAME:shop}
    password: ${DB_PASSWORD:shop-secret}

# ❌ 避免
spring:
  datasource:
    url: jdbc:mysql://localhost:3306/shop_inventory
    username: shop
    password: shop-secret
```

### 5.4 测试策略

```java
// 单元测试（快速、无外部依赖）
@ExtendWith(MockitoExtension.class)
class InventoryServiceTest { ... }

// 切片测试（Web 层）
@WebMvcTest(InventoryController.class)
class InventoryControllerTest { ... }

// 集成测试（Testcontainers）
@SpringBootTest
@Testcontainers
class InventoryIntegrationTest extends AbstractMySqlIntegrationTest { ... }
```

---

## 六、故障排查

### 6.1 常见问题

#### 问题 1：Archetype 未找到

```
[ERROR] No archetype found.
```

**解决方案**：

```bash
# 重新安装 archetype
make archetypes-install

# 验证已安装
ls ~/.m2/repository/dev/meirong/shop/
```

#### 问题 2：生成后编译失败

```
[ERROR] Could not resolve dependencies for project dev.meirong.shop:inventory-service:jar:0.1.0-SNAPSHOT
```

**解决方案**：

```bash
# 确保 shop-common 和 shop-contracts 已安装
./mvnw -pl shop-common,shop-contracts -am install

# 重新编译新服务
./mvnw -pl inventory-service -am compile
```

#### 问题 3：Testcontainers 启动失败

```
java.lang.IllegalStateException: Could not find a valid Docker environment
```

**解决方案**：

```bash
# 确保 Docker Desktop/OrbStack 已启动
docker ps

# macOS 检查权限
ls -l /var/run/docker.sock
```

#### 问题 4：K8s 部署失败

```
Error from server (NotFound): deployments.apps "inventory-service" not found
```

**解决方案**：

```bash
# 确保已构建并加载镜像
make build-images MODULE=inventory-service
make load-images MODULE=inventory-service

# 应用 K8s 配置
kubectl apply -f inventory-service/k8s/
```

### 6.2 调试技巧

#### 查看 Archetype 生成日志

```bash
"${SHOP_REPO}/mvnw" archetype:generate -X \
  -DarchetypeArtifactId=domain-service-archetype \
  ...
```

#### 验证生成的 POM

```bash
cd inventory-service
./mvnw help:effective-pom
```

#### 检查依赖树

```bash
./mvnw dependency:tree
```

---

## 七、进阶主题

### 7.1 自定义 Archetype

如需创建自定义 Archetype，参考以下结构：

```
my-custom-archetype/
├── pom.xml
└── src/main/resources/
    └── META-INF/maven/
        └── archetype-metadata.xml    # 定义生成文件清单
    └── archetype-resources/
        ├── pom.xml                   # 模板 POM（使用 ${artifactId} 等占位符）
        └── src/...                   # 模板源码
```

### 7.2 模板版本管理

Archetype 版本与 `shop-platform` 版本绑定：

| 版本变更类型 | 触发条件 | 示例 |
|-------------|---------|------|
| Major | 技术栈不兼容升级（如 JDK 25→26） | `1.0.0` → `2.0.0` |
| Minor | 新增依赖/配置，向后兼容 | `1.0.0` → `1.1.0` |
| Patch | 修复占位符/文档错误 | `1.0.0` → `1.0.1` |

### 7.3 与 CI/CD 集成

在 GitHub Actions 中验证 Archetype：

```yaml
- name: Test Archetype Generation
  run: |
    make archetypes-install
    mkdir -p /tmp/test-gen
    cd /tmp/test-gen
    ./mvnw archetype:generate ...
    cd test-service
    ./mvnw test
```

---

## 七、自动化测试

### 7.4 测试架构

项目包含完整的 archetype 自动化测试体系，确保所有模板都能正确生成可编译、可测试的项目。

**测试模块**：`archetype-tests/`

**测试覆盖**：
- ✅ `domain-service-archetype`
- ✅ `bff-service-archetype`
- ✅ `event-worker-archetype`
- ✅ `gateway-service-archetype`
- ✅ `auth-service-archetype`
- ✅ `portal-service-archetype`

### 7.5 本地运行测试

```bash
# 使用 Makefile（推荐）
make archetype-test

# 或直接运行脚本
bash ./scripts/test-archetypes.sh
```

**预期输出**：

```
========================================
  Shop Platform Archetype Tests
========================================

Step 1: Installing archetypes to local Maven repository...
✓ Archetypes installed successfully

Step 2: Running archetype integration tests...
[INFO] BUILD SUCCESS
[INFO] Tests run: 24, Failures: 0, Errors: 0

========================================
  All archetype tests passed!
========================================
```

### 7.6 测试验证内容

每个 archetype 测试验证：

| 验证项 | 说明 |
|--------|------|
| 目录结构 | 标准的 Java/Kotlin 目录结构 |
| K8s 配置 | deployment.yaml, service.yaml, hpa.yaml |
| 编译通过 | `mvn compile` 成功 |
| 测试通过 | `mvn test` 成功 |
| 依赖完整 | 关键依赖存在于 pom.xml |

### 7.7 CI 集成

Archetype 测试已集成到 GitHub Actions CI：

```yaml
archetype-test:
  runs-on: ubuntu-latest
  steps:
    - name: Install archetypes
      run: ./mvnw -pl shop-common,shop-contracts,shop-archetypes -am install
      
    - name: Run archetype tests
      run: ./mvnw -pl archetype-tests test
```

**触发条件**：
- Maven 相关文件变更时自动触发
- PR 提交时自动运行
- 可手动触发（workflow_dispatch）

### 7.8 添加新的 Archetype 测试

为新的 archetype 添加测试：

```java
class NewArchetypeTest extends AbstractArchetypeTest {

    private static final String ARCHETYPE = "new-service-archetype";
    private static final String ARTIFACT = "test-new-service";

    @Override
    protected String getArchetypeArtifactId() {
        return ARCHETYPE;
    }

    @Override
    protected String getArtifactId() {
        return ARTIFACT;
    }

    @Test
    void shouldGenerateCompilableProject() throws Exception {
        Path projectDir = generateProject();
        compileProject(projectDir);
        assertThat(projectDir.resolve("target/classes")).exists();
    }
}
```

---

## 九、参考文档

| 文档 | 说明 |
|------|------|
| `shop-archetypes/README.md` | Archetype 安装与生成说明 |
| `docs/ENGINEERING-STANDARDS-2026.md` | 2026 统一技术栈标准 |
| `docs/DEVELOPER-EXPERIENCE-STANDARD-2026.md` | DX 工具链规范 |
| `docs/API-DOCUMENTATION-SPRINGDOC-2026.md` | OpenAPI 文档规范 |
| `docs/ARCHETYPE-TESTING-IMPROVEMENT-PLAN.md` | Archetype 测试改进方案 |
| [Maven Archetype 官方文档](https://maven.apache.org/archetype/) | Maven Archetype 参考 |

---

## 十、快速参考卡片

```
┌─────────────────────────────────────────────────────────────────┐
│           Shop Platform Archetype 快速参考                       │
├─────────────────────────────────────────────────────────────────┤
│ 1. 安装：make archetypes-install                                 │
│                                                                 │
│ 2. 生成：./mvnw archetype:generate \                            │
│           -DarchetypeArtifactId=<type>-archetype \              │
│           -DartifactId=<your-service-name>                      │
│                                                                 │
│ 3. 集成：mv <service> /path/to/shop/                           │
│          编辑 pom.xml 添加 <module>                              │
│                                                                 │
│ 4. 验证：make verify                                            │
│                                                                 │
│ 选型指南：                                                       │
│   • 领域服务 → domain-service-archetype                         │
│   • BFF 聚合 → bff-service-archetype                            │
│   • 事件处理 → event-worker-archetype                           │
│   • 网关 → gateway-service-archetype                            │
│   • 认证 → auth-service-archetype                               │
│   • SSR 门户 → portal-service-archetype                         │
└─────────────────────────────────────────────────────────────────┘
```

---

**最后更新**：2026-04-01  
**维护者**：Shop Platform Team  
**反馈**：请在 PR 中提出改进建议
