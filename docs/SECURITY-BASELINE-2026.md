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
- 东西向调用当前以 `X-Internal-Token` + Trusted Headers 为主，`shop-common` 提供统一过滤器
- 本地 / Kind 基线没有默认启用 mTLS、service mesh、workload identity；这些仍属于未来增强

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
- `X-Internal-Token`

客户端如果伪造这些头，Gateway 必须先清除，再按认证结果重新注入。

### 2.3 Portal / BFF / Domain 侧约束

- Portal 不直接信任浏览器传来的身份头。
- BFF / Domain / Worker 只信任来自 Gateway 或内部调用链的可信头。
- 管理端口 `8081` 用于健康检查与指标，不作为业务入口。

---

## 三、东西向安全边界

### 3.1 Internal Token

- 服务间内部调用统一使用 `X-Internal-Token`。
- `shop-common` 提供 `InternalAccessFilter` 进行统一校验。
- 当 `shop.security.internal.enabled=true` 时，内部接口必须校验 token。

### 3.2 Header 传播规则

- Gateway → BFF / Domain：传播可信身份头 + `X-Internal-Token`
- BFF → Domain / Worker：传播 `X-Internal-Token`，按需要传递 `X-Buyer-Id`
- Worker / Internal Controller：只接受内部 token，不面向公网暴露

### 3.3 例外路径

- `/actuator/**` 通常从 internal filter 中排除，便于平台探针和指标抓取
- 任何例外路径都必须显式配置，不能依赖默认放开

---

## 四、认证与授权基线

### 4.1 认证

- 当前生产基线为 JWT
- JWT 至少携带：
  - `principalId`
  - `username`
  - `roles`
  - `portal`

### 4.2 授权

- 北向授权以角色和 portal 边界为主
- 东西向授权以“是否来自可信内部调用链”为主
- 任何对买家 / 卖家身份敏感的接口，必须从可信头或 token claim 中读取身份，而不是依赖请求体

---

## 五、密钥与配置管理

- Secrets 通过环境变量或 Kubernetes Secret 注入
- 严禁把真实密钥、SMTP 密码、支付密钥、内部 token 写入源码
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
- 不允许把内部 token 写死在镜像或 manifest 文本中
- 新服务接入前必须明确：
  - 是否需要外部暴露
  - 是否需要 JWT
  - 是否需要内部 token 校验
  - 是否存在后台任务或 Kafka 消费面

---

## 七、后续演进方向

- JWT 从共享密钥逐步演进到非对称签名 + JWKS
- 东西向从共享 internal token 演进到服务身份 / mTLS
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
