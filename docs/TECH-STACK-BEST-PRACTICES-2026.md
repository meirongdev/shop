# Shop Platform — 2026 技术栈最佳实践与复用指南

> 版本：1.1 | 更新时间：2026-03-24

---

## 一、目标

本文面向架构师、平台工程师和后续复用本仓库经验的团队，回答三个问题：

1. 当前仓库实际落地了什么技术栈  
2. 这些选择是否符合 2026 年 `Spring Boot 3.5 + Java 25 + Cloud Native` 最佳实践  
3. 未来在其他项目中应该如何复用，以及下一步如何演进

本文聚焦**当前代码和清单已经实现的事实**，并明确区分：

- **当前基线**：仓库中已经可运行、可验证、可复用的实现
- **演进方向**：已经进入 Roadmap，但尚未在所有模块统一落地的能力

配套权威文档：

- 工程标准：`docs/ENGINEERING-STANDARDS-2026.md`
- 架构与边界：`docs/ARCHITECTURE-DESIGN.md`
- 服务技术栈与扩展面：`docs/SERVICE-TECH-STACK-AND-EXTENSIBILITY.md`
- 安全基线：`docs/SECURITY-BASELINE-2026.md`
- 可观测基线：`docs/OBSERVABILITY-ALERTING-SLO.md`
- Feature Toggle：`docs/FEATURE-TOGGLE-AND-CONFIG-RELOAD.md`
- API 文档规范：`docs/API-DOCUMENTATION-SPRINGDOC-2026.md`

---

## 二、当前实际技术栈快照

### 2.1 平台与语言基线

| 维度 | 当前实现 | 说明 |
|------|----------|------|
| JDK | Java 25 | 父 POM 统一管理，Maven Enforcer 校验最低版本 |
| Spring Boot | 3.5.11 | 所有 Java 服务继承统一 parent |
| Spring Cloud | 2025.0.1 | 与 Boot 3.5 基线配套 |
| Kotlin | 2.3.20 | Portal 与 KMP 模块使用 |
| 构建 | Maven + Maven Wrapper + Enforcer | Java 主体仓库标准；KMP 侧仍使用 Gradle |
| 文档站 | Docusaurus | `docs-site/` 对外展示运行/架构/模块说明 |

### 2.2 服务模型

| 层次 | 当前实现 | 复用建议 |
|------|----------|----------|
| Gateway | Spring Cloud Gateway Server Web MVC + Virtual Threads + Redis 令牌桶限流/Canary | 适合作为统一入口、JWT 校验、Trusted Headers、基础流量治理层 |
| Auth | Spring Security + OAuth2 Resource Server + JWT | 当前为 HS256 对称签名；未来可演进到非对称 + JWKS |
| BFF | Spring MVC + Virtual Threads + 局部 Resilience4j | 聚合编排优先；禁止承载复杂领域规则 |
| Domain Service | Spring MVC + JPA + Flyway + MySQL | 数据所有权归属单服务，避免跨库写入 |
| Event / Worker | Spring Boot + Kafka + Outbox Pattern | 用于事件发布、消费、通知、投影、补偿任务 |
| Portal | Kotlin + Thymeleaf | 适合 SSR 过渡阶段；后续可演进成前后端分离 |

### 2.3 基础设施与云原生能力

| 维度 | 当前实现 | 当前状态 |
|------|----------|----------|
| Kubernetes | Kind + `k8s/**` manifests | 本地/验证环境已实测可用 |
| 容器构建 | `platform/docker/Dockerfile.fast` | 分层 JAR 镜像构建（host build + Docker COPY） |
| 数据库 | MySQL 8.4 | 每服务独立 schema + Flyway |
| 消息队列 | Kafka 3.9（KRaft） | 订单、钱包、搜索、通知等链路已使用 |
| 缓存 | Redis 7.4 | 本地/Kind 基线为单实例模式 |
| 搜索 | Meilisearch 1.12 | `search-service` 事件驱动同步商品索引 |
| 可观测 | Prometheus + OTLP Collector + Tempo + Loki + Grafana + Pyroscope + 结构化日志 | Kind/K8s 验证栈已落地；本地 compose 与其保持 OTLP/Prometheus 基线对齐 |
| 配置热更新 | ConfigMap + Configuration Watcher + `/actuator/refresh` | 已在 `search-service` Feature Toggle 方案中验证 |

### 2.4 共享能力

| 能力 | 当前实现 | 说明 |
|------|----------|------|
| 统一响应模型 | `ApiResponse<T>` | 位于 `shop-common` |
| 全局异常模型 | `BusinessException` + `CommonErrorCode` | 服务统一返回 `SC_*` 错误语义 |
| 内部安全 | `X-Internal-Token` + `InternalAccessFilter` | 服务间可信调用链校验 |
| 契约集中化 | `shop-contracts` | API path 常量、DTO、event envelope 统一管理 |
| Feature Toggle | OpenFeature + property provider | 当前已在 `search-service` 试点 |
| 可观测自举 | `shop-common` + OTLP logback appender + `shop.profiling` | Java 服务默认具备 metrics / traces / logs / profiling 接入点 |
| 脚手架 | `shop-archetypes` | 已实现 6 类 Maven Archetype，并完成样板工程验证 |

---

## 三、哪些地方符合 2026 最佳实践

### 3.1 技术栈版本与升级策略是健康的

- Java 25、Spring Boot 3.5.11、Spring Cloud 2025.0.1 处于 2026 年的主流可维护组合
- 通过父 POM、Maven Wrapper、Enforcer 把版本治理变成平台基线，而不是模块自行漂移
- Kotlin 与 Java 分工清晰：Java 负责主服务，Kotlin 主要用于 Portal / KMP 场景

### 3.2 云原生交付方式是正确的

- 所有服务统一 `8080/8081` 端口模型，便于平台治理
- 健康检查、Prometheus、OTLP、结构化日志已纳入默认配置模板
- Kind / K8s manifest / mirrord 可以形成从本地验证到集群交付的连续路径
- 配置与密钥通过环境变量、ConfigMap、Secret 注入，而不是硬编码到镜像

### 3.3 服务边界与共享层次是健康的

- Gateway / BFF / Domain / Worker / Portal 的职责划分清晰
- `shop-common` 与 `shop-contracts` 的存在，避免了“每个服务都自己定义响应、错误、header、path 常量”
- 数据所有权按服务划分，配合 Flyway 与 Outbox Pattern，适合持续演进

### 3.4 现代 Java 能力使用方向正确

- Virtual Threads 已在 `api-gateway`、BFF 和多数 Java 服务配置中启用，符合 I/O 密集型微服务趋势
- 网关迁移到 Servlet + Virtual Threads 后，边缘层与 BFF / Domain 的编程模型已经统一
- `record` / `ConfigurationProperties` / typed config 的使用方向总体正确
- 但需要注意：**可热刷新的 `@ConfigurationProperties` 不宜使用 Java record**，仓库已在 Feature Toggle 场景中规避该问题

### 3.5 Feature Toggle 方案符合 2026 云原生实践

- 使用 OpenFeature 保留 vendor 无关的 API 抽象
- 第一阶段选择仓库内 property provider，而不是一开始就引入 `flagd` / Unleash 控制面
- 用 ConfigMap 目录挂载 + Configuration Watcher + `/actuator/refresh` 处理热更新，符合 K8s 原生实践

### 3.6 技术选型与架构问题是一一对应的

| 架构/设计问题 | 当前技术选择 | 为什么是这组技术 |
|------|----------|------|
| 外部流量如何统一鉴权、限流和追踪 | Spring Cloud Gateway MVC + Spring Security + Redis 令牌桶（Token Bucket Lua 脚本） | 让 northbound 规则集中落在 edge 层，而不是散在每个服务里 |
| 多下游聚合如何既保留同步代码可读性，又承受高并发 I/O | RestClient + Virtual Threads + timeout / CircuitBreaker | 代码模型简单，可并发读多个下游，失败边界也更清晰 |
| 服务数据所有权如何保持清晰 | 每服务独立 schema + Flyway | 事实边界和迁移节奏都能独立演进 |
| 跨服务事务怎么避免分布式事务 | Outbox Pattern + Kafka + consumer 幂等 | 保住本地事务原子性，同时保留重放与补偿能力 |
| 高并发库存、续费、定时扫描如何协调 | Redis / Redisson / Lua / RLock | 在共享缓存平面里解决高频原子操作和多实例抢占 |
| 经常变动的促销、活动、通知规则如何持续扩展 | Strategy / Plugin / Channel 接口 | 通过新增实现扩容业务能力，避免核心应用服务越来越臃肿 |
| 搜索、联想词、热词为什么不直接查交易库 | Meilisearch + Kafka 投影 | 让读模型独立优化，减少对交易库的检索压力 |
| 如何让新服务默认可观测 | Micrometer + OTLP + Collector + Tempo/Loki/Grafana/Pyroscope | 把 metrics / traces / logs / profiles 一次性接入统一平台 |

---

## 四、必须诚实说明的现状与缺口

### 4.1 OpenAPI 基线已经引入，但还没有“全栈统一”

当前事实：

- 父 POM 已统一管理 `springdoc` 版本
- `auth-server`、`buyer-bff` 已引入 `springdoc-openapi`
- archetype 也已经把 OpenAPI 依赖和 `swagger-ui` 路径纳入模板

但当前仍然**不是**“所有服务都已 OpenAPI-first”：

- 并非所有服务都引入了 springdoc 依赖
- 控制器注解、统一输出、生成产物和发布流程还没有在所有模块完成统一

结论：

- **当前状态应表述为：OpenAPI 基线已建立，尚未全仓统一落地。**

### 4.2 Resilience4j 是“局部落地”，不是“统一完成”

当前事实：

- `buyer-bff` / `seller-bff` 已引入 Resilience4j 依赖
- 目前主要用于 `search-service` 下游调用的 CircuitBreaker

但仍缺：

- Retry / Bulkhead 没有形成统一默认策略
- 其他下游调用链路尚未全量覆盖
- gateway 侧也还没有形成统一的容错策略

结论：

- **当前状态应表述为：Resilience4j 已试点，但仍是 P1 待统一。**

### 4.3 开发 / 验证环境的可观测平台已经落地

当前事实：

- K8s 默认清单已经包含 Prometheus、OTEL Collector、Tempo、Loki、Grafana、Pyroscope、Alert Rules、SLO Rules 和 Garage S3 后端
- Collector 已按链路把 traces 发往 Tempo、logs 发往 Loki OTLP，并补充 `k8sattributes` 与健康流量过滤
- Java 服务已经统一接入结构化 JSON 日志、OTLP logback appender、`shop.profiling` 配置与 `X-Trace-Id` 关联头

仍需要继续演进的主要是：

- Alertmanager / runbook 自动化联动
- 多环境保留策略、容量规划与成本治理
- 跨集群或生产级高可用部署模板

结论：

- **当前状态应表述为：开发 / 验证环境的完整 observability platform 已落地，生产级治理仍有演进空间。**

### 4.4 安全基线是“共享 internal token + trusted headers”，不是服务网格

当前事实：

- JWT、Trusted Headers、`X-Internal-Token` 已落地
- 大多数服务通过 `scanBasePackages = "dev.meirong.shop"` 加载 `shop-common` 中的共享安全过滤器和异常处理

但当前仍未统一落地：

- mTLS
- workload identity
- SPIFFE / SPIRE
- 非对称签名 + JWKS

结论：

- **当前状态是适合本地 / 演示 / 早期云原生平台的安全基线，不应误写成已完成 service mesh 级安全。**

### 4.5 CI 已具备基础校验，但尚未覆盖镜像与 Kind 验证

当前事实：

- GitHub Actions 已执行 `./mvnw verify`
- docs-site build 已进入 CI

仍缺：

- Docker 镜像构建与扫描
- Kind 部署验收
- smoke test
- contract test / architecture rule test

结论：

- **当前 CI 是“编译 + 测试 + docs build”基线，不应误写成完整云原生交付流水线。**

---

## 五、给未来项目复用时的工程实践建议

### 5.1 先固化平台骨架，再做业务扩展

推荐优先复用：

1. 父 POM（Java / Boot / Cloud / Kotlin / Enforcer）
2. `shop-common`
3. `shop-contracts`
4. `shop-archetypes`
5. `k8s` 应用交付契约

不要先复制业务代码，再回头补平台基线。

### 5.2 服务默认要自带这些“最小能力”

新项目 / 新服务建议默认具备：

- `8080/8081`
- readiness / liveness
- Prometheus + OTLP
- 结构化日志
- 统一异常模型
- 最小测试集
- internal security
- docs-site 或 README 中的运行说明

### 5.3 BFF 聚合优先使用 Virtual Threads，但要控制边界

推荐：

- BFF 的多下游聚合、并发读取、轻量编排
- I/O 密集型、需要大量等待下游响应的场景

不推荐：

- 把复杂的事务编排、跨域状态机放在 BFF
- 在未确认依赖库是否可安全阻塞的前提下盲目扩大使用范围

### 5.4 事务到事件统一采用 Outbox Pattern

适用：

- 钱包、订单、积分、搜索投影、通知投递等事件链路

复用建议：

- 业务写入与 outbox 写入同事务
- 明确 poller 间隔、失败重试和 replay 策略
- 在文档中声明 consumer 类型（金融 / 状态流转 / 投影 / 通知）

### 5.5 配置热更新只用于“适合动态变化”的内容

推荐动态化：

- Feature Toggle
- 某些限流阈值、降级开关、实验参数

不推荐动态化：

- 数据源 URL
- 安全关键配置
- 依赖拓扑

原则：

- 把“可刷新配置”和“启动时固定配置”分开治理
- 不要对所有配置一视同仁地要求热更新

### 5.6 文档治理要把“当前实现”和“目标态”分开写

这是当前仓库最值得复用的一条经验：

- 规范文档可以写目标态
- 但必须明确哪些已经落地，哪些还在 Roadmap
- 如果文档描述与代码实现不一致，以权威文档 + 验证结果为准

---

## 六、建议的未来演进方向

### P0：补齐“已立标准但未完全统一”的能力

- 统一 OpenAPI 注解、产物生成和发布
- 在 BFF 全量推进 Resilience4j（CircuitBreaker + Retry + Bulkhead）
- 将契约测试纳入关键链路
- 将 ArchUnit 规则测试纳入平台门禁

### P1：把“本地验证”推进到“CI 验证”

- Docker 镜像构建与安全扫描
- Kind 部署 + smoke test
- 更系统的 Testcontainers 策略
- 对 Feature Toggle 方案补齐自动化回归

### P2：把“平台基线”推进到“平台产品”

- OpenRewrite 批量升级配方
- 更多可复用 archetype 版本
- Alertmanager / runbook / retention / 多环境观测治理
- 更成熟的 feature management（`flagd` / Unleash）

### P3：面向生产安全与大规模治理

- JWT → 非对称签名 + JWKS（`auth-server` 暴露 `/jwks.json`，Gateway 切换 `jwk-set-uri`，支持过渡期双密钥）
- internal token → workload identity / mTLS
- 更完善的供应链安全（SBOM / 依赖扫描 / 镜像签名）
- 对 BFF / Gateway 扩展更细粒度的熔断、审计、风控与差异化限流策略
- Java 25 `ScopedValue` 替代 MDC 做链路上下文传播（需要配合支持 ScopedValue 的 Logback MDCAdapter）

---

## 七、官方参考链接

### 平台与框架

- Java 25 Docs: https://docs.oracle.com/en/java/javase/25/
- Spring Boot Reference: https://docs.spring.io/spring-boot/reference/
- Spring Cloud: https://spring.io/projects/spring-cloud
- Spring Cloud Gateway: https://spring.io/projects/spring-cloud-gateway
- Spring Security Reference: https://docs.spring.io/spring-security/reference/

### 云原生与配置

- Kubernetes ConfigMap: https://kubernetes.io/docs/concepts/configuration/configmap/
- Kubernetes Secrets: https://kubernetes.io/docs/concepts/configuration/secret/
- Spring Cloud Kubernetes Configuration Watcher: https://docs.spring.io/spring-cloud-kubernetes/reference/spring-cloud-kubernetes-configuration-watcher.html
- Kind: https://kind.sigs.k8s.io/

### 观测与质量

- Micrometer: https://micrometer.io/
- OpenTelemetry: https://opentelemetry.io/docs/
- Prometheus: https://prometheus.io/docs/introduction/overview/
- Testcontainers for Java: https://testcontainers.com/guides/getting-started-with-testcontainers-for-java/

### 生态与演进

- OpenFeature: https://openfeature.dev/docs/reference/technologies/server/java/
- Unleash: https://docs.getunleash.io/reference/sdks/java
- Meilisearch: https://www.meilisearch.com/docs
- OpenRewrite: https://docs.openrewrite.org/
- Docusaurus: https://docusaurus.io/docs

---

## 八、结论

从 2026 年 `Spring Boot 3.5 + Java 25 + Cloud Native` 的角度看，当前仓库的**底座是健康且先进的**：

- 版本选择正确
- 平台骨架清晰
- 云原生运行方式正确
- 文档治理已经形成体系

真正需要持续补齐的，不是“换栈”，而是把**已经立下的标准继续统一到底**：

- OpenAPI
- Resilience4j
- 契约测试 / 架构规则
- CI 中的 Kind 验证
- 更成熟的安全与多环境观测治理
