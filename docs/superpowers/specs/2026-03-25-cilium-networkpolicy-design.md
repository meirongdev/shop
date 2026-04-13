# Cilium CNI + NetworkPolicy 替换 Internal Token 设计

> 版本：1.4 | 日期：2026-04-13 | 状态：已完成

---

## 背景与目标

本方案通过引入 **Cilium CNI（完整模式，替代 kube-proxy）** 并实施 **精确的 Kubernetes NetworkPolicy**，在网络层强制服务调用图，消除了对共享密钥 `X-Internal-Token` 的依赖。

**已达成目标：**
- Kind 集群使用 Cilium 替换 kindnet 及 kube-proxy。
- 实施完整服务调用图 NetworkPolicy（精确定义每个服务允许的调用方）。
- **完全移除** 了 `InternalAccessFilter` 相关代码及其配置（`shop.security.internal.enabled`）。
- 保持 mirrord 本地开发工作流可用。

---

## 一、架构状态

### 1.1 安全模型变更
- **旧模式**：基于 HTTP Header `X-Internal-Token` 的共享密钥校验。
- **新模式**：基于 Cilium 网络层身份的 NetworkPolicy 强制隔离。
- **优点**：真正的“零信任”起步，防止集群内横向渗透，且业务代码不再感知安全传输细节。

### 1.2 身份传播
虽然不再用于鉴权，但 Gateway 注入的 **Trusted Headers**（`X-Request-Id`, `X-Buyer-Id`, `X-Username`, `X-Roles`, `X-Portal`）仍然保留，用于在服务间传播用户身份上下文。

---

## 二、NetworkPolicy 实施细节

### 2.1 策略分布

- **基础设施策略**（`platform/k8s/infra/base.yaml`）：允许所有应用 Pod 访问 MySQL, Kafka, Redis, Meilisearch, Mailpit, otel-collector。
- **应用间策略**（`platform/k8s/apps/base/network-policies.yaml`）：
    - `api-gateway-ingress`：允许外部流量进入网关。
    - `auth-server-ingress`：仅允许来自网关的流量。
    - `buyer-bff-ingress` / `seller-bff-ingress`：仅允许来自网关的流量。
    - `*-service-ingress`：根据服务调用图，仅允许特定的 BFF 或领域服务进入。

### 2.2 监控与审计
利用 Cilium Hubble 可以实时观察服务间的流量是否符合预期，以及是否有非法尝试被 NetworkPolicy 拦截。

---

## 三、代码清理记录

### 3.1 移除的组件
- `shared/shop-common` 中的 `InternalAccessFilter` 和 `InternalAccessFilterConfiguration` 已删除。
- `TrustedHeaderNames` 中的 `INTERNAL_TOKEN` 常量已删除。

### 3.2 移除的配置
- `platform/k8s/apps/base/platform.yaml` 中的 `SHOP_INTERNAL_TOKEN` 密钥已删除。
- `platform/k8s/apps/base/platform.yaml` 中的 `SHOP_SECURITY_INTERNAL_ENABLED` 环境变量已删除。
- 各个 `application.yml` 中的相关配置项已清理。

---

## 四、结论

`InternalAccessFilter` 及其相关配套设施已正式退出 `shop-platform` 历史舞台。后续所有新增服务必须通过配置相应的 `NetworkPolicy` 来获得访问权限。
