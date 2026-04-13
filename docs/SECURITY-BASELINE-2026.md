# Shop Platform — Security Baseline 2026

> 版本：1.0 | 更新时间：2026-03-22

---

## 一、目标

定义 `shop-platform` 当前阶段的最低安全基线，覆盖：

- 北南向（外部用户 → 平台）
- 东西向（服务 → 服务）
- 配置与密钥管理
- 交付与运行时约束

本文档聚焦“当前已落地 + 当前必须遵守”的最小安全边界，不替代未来的 mTLS / JWKS / 细粒度策略演进。

### 1.1 当前实现快照

- 北南向认证当前以 JWT 为主，`auth-server` 负责签发，当前实现为共享密钥（HS256）模式
- Google 社交登录已经接入 `auth-server`，但统一身份联邦仍是后续演进方向
- 东西向安全由 Kubernetes NetworkPolicy (Cilium) 强制执行，Identity context 通过 Gateway 注入的 Trusted Headers 传播
- 本地 / Kind 基线已启用 Cilium NetworkPolicy 以实现微服务间的精确访问控制

---

## 二、北南向安全边界

### 2.1 Gateway 是默认入口

- 外部请求默认先进入 `api-gateway`。
- `/api/**` 路径统一执行 JWT 校验。
- `auth-server` 承担登录与 token 签发职责。

### 2.2 Trusted Headers 只能由 Gateway 注入

以下头由 Gateway 生成/覆盖，下游服务不得信任客户端原始值：

- `X-Request-Id`
- `X-Buyer-Id`
- `X-Username`
- `X-Roles`
- `X-Portal`

客户端如果伪造这些头，Gateway 必须先清除，再按认证结果重新注入。

**实现保障**：`TrustedHeadersRequestWrapper` 同时覆盖了 `getHeader(String)`、`getHeaders(String)` 和 `getHeaderNames()` 三个方法。Tomcat/Spring 内部通过这三个路径读取请求头，任意单一方法的覆盖都不足以阻断伪造头的泄漏。

### 2.3 限流策略

- `/api/**` 路由启用 Redis 令牌桶（Token Bucket）限流，由 `RateLimitingFilter` 实现。
- 限流键：已认证请求用 `X-Buyer-Id`；未认证请求（公开路由）用客户端 IP。
- 令牌桶参数：`requestsPerMinute`（填充速率）与 `burst`（桶容量），均通过环境变量配置。
- Redis 不可达时失败放行（fail-open），防止限流组件成为可用性瓶颈。

### 2.4 Portal / BFF / Domain 侧约束

- Portal 不直接信任浏览器传来的身份头。
- BFF / Domain / Worker 只信任来自 Gateway 或内部调用链的可信头。
- 管理端口 `8081` 用于健康检查与指标，不作为业务入口。

## 三、东西向安全边界

### 3.1 基于 NetworkPolicy 的东西向安全

- 东西向调用不再依赖共享密钥（如 `X-Internal-Token`）。
- 安全性通过 Kubernetes NetworkPolicy (Cilium) 在网络层强制执行。
- 仅允许经过身份验证的路径（如 Gateway -> BFF, BFF -> Domain Service）进行通信。

**网络隔离与可信身份头**

身份上下文（Identity context）通过 Gateway 注入的可信头（Trusted Headers）向下游传播。由于 NetworkPolicy 确保了只有 Gateway 或授权的服务可以发起请求，下游服务可以安全地信任这些头。

### 3.2 Header 传播规则

- Gateway → BFF / Domain：注入可信身份头
- BFF → Domain / Worker：传播 `X-Buyer-Id` 等必要身份上下文
- Worker / Internal Controller：按需处理传播的身份上下文

### 3.3 例外路径

- `/actuator/**` 通常从业务安全策略中排除，便于平台探针和指标抓取
- 任何例外路径都必须显式配置，不能依赖默认放开

---

## 四、认证与授权基线

### 4.1 认证

- 当前生产基线为 JWT（HS256 对称密钥），由 `auth-server` 签发，`api-gateway` 通过 `NimbusJwtDecoder` 校验。
- JWT 至少携带：
  - `principalId`
  - `username`
  - `roles`
  - `portal`

**JWT 签名算法演进路径（P3 Roadmap）**

当前的 HS256 对称密钥（`SHOP_AUTH_JWT_SECRET`）需要在 `auth-server` 和 `api-gateway` 之间共享，存在密钥分发风险，且难以无停机轮换。计划迁移路径：

1. `auth-server` 生成 RSA/EC 密钥对，通过 `/.well-known/jwks.json` 暴露公钥。
2. `api-gateway` 配置 `spring.security.oauth2.resourceserver.jwt.jwk-set-uri` 并限定算法白名单（`RS256` 或 `ES256`）。
3. 过渡期双算法支持：`auth-server` 在新旧密钥的 JWK Set 中均发布公钥，Gateway 通过 `kid` 自动选择验证密钥。
4. 淘汰旧的 `jwtSecret` 配置，移除 `GatewayProperties.jwtSecret` 字段。

### 4.2 授权

- 北向授权以角色和 portal 边界为主
- 东西向授权以“是否来自可信内部调用链（由 NetworkPolicy 保证）”为主
- 任何对买家 / 卖家身份敏感的接口，必须从可信头或 token claim 中读取身份，而不是依赖请求体

---

## 五、密钥与配置管理

- Secrets 通过环境变量或 Kubernetes Secret 注入
- 严禁把真实密钥、SMTP 密码、支付密钥写入源码
- 默认配置中的 `change-me` 仅可作为模板占位，不可进入真实环境
- 本地开发与 Kind 演示环境必须和生产使用同一套“变量注入 + 默认值”模式

---

## 六、应用与部署安全要求

### 6.1 服务默认要求

- 应用端口 `8080`，管理端口 `8081`
- 健康检查走 management 端口
- 指标与 tracing 默认开启
- 日志必须是结构化格式，便于审计

### 6.2 K8s 交付要求

- Deployment 只能从 ConfigMap / Secret / env 注入配置
- Service 只暴露必要端口
- 新服务接入前必须明确：
  - 是否需要外部暴露
  - 是否需要 JWT
  - 是否存在后台任务或 Kafka 消费面

---

## 七、后续演进方向

- JWT 从共享密钥逐步演进到非对称签名 + JWKS
- 东西向从 NetworkPolicy 演进到更细粒度的 mTLS（由 Cilium 或 Istio 提供）
- 为 BFF / Gateway 增加统一限流、风控与审计策略
- 为管理面增加更清晰的网络边界与最小暴露策略

---

## 八、官方参考链接

- Spring Security Reference: https://docs.spring.io/spring-security/reference/
- Spring Authorization Server: https://spring.io/projects/spring-authorization-server
- OAuth 2.0 / OIDC Overview: https://openid.net/developers/how-connect-works/
- Kubernetes Secrets: https://kubernetes.io/docs/concepts/configuration/secret/
- Kubernetes NetworkPolicy: https://kubernetes.io/docs/concepts/services-networking/network-policies/
- SPIFFE / SPIRE: https://spiffe.io/docs/latest/
