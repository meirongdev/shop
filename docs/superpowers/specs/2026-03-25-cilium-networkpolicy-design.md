# Cilium CNI + NetworkPolicy 替换 Internal Token 设计

> 版本：1.3 | 日期：2026-03-25

---

## 背景与目标

当前 shop-platform 使用共享密钥 `X-Internal-Token` 作为东西向服务调用的访问控制手段。由于所有 BFF 和 Domain Service 均以 `ClusterIP` 暴露，外部流量天然无法绕过 `api-gateway`，Internal Token 的核心价值退化为防集群内横向渗透。

本方案通过引入 **Cilium CNI（完整模式，替代 kube-proxy）** 并实施 **精确的 Kubernetes NetworkPolicy**，在网络层强制服务调用图，消除对共享密钥的依赖。

**目标：**
- Kind 集群使用 Cilium 替换 kindnet 及 kube-proxy
- 实施完整服务调用图 NetworkPolicy（精确定义每个服务允许的调用方）
- 统一关闭所有服务的 `shop.security.internal.enabled`
- 保持 mirrord 本地开发工作流可用

---

## 一、Kind + Cilium 安装

### 1.1 `kind/cluster-config.yaml` 变更

```yaml
kind: Cluster
apiVersion: kind.x-k8s.io/v1alpha4
networking:
  disableDefaultCNI: true    # 禁用 kindnet
  kubeProxyMode: none        # Cilium 替代 kube-proxy
nodes:
  - role: control-plane
    # 保留现有 extraPortMappings（30080、30025、30090 等）
```

### 1.2 `kind/setup.sh` 变更

在集群创建后、namespace/infra/apps manifest 应用前，插入 Cilium 安装步骤：

```bash
echo ">>> Installing Cilium CNI..."
cilium install \
  --set kubeProxyReplacement=true \
  --set k8sServiceHost=kind-control-plane \
  --set k8sServicePort=6443
cilium status --wait
```

**前提依赖：** 本地需安装 `cilium` CLI（`brew install cilium-cli`）。建议锁定版本以确保可重现：

```bash
# 推荐固定版本，避免 CLI 与 Cilium chart 版本不匹配
CILIUM_CLI_VERSION=v0.16.22
```

**注意：** Kind 的 control-plane 节点带有 `node-role.kubernetes.io/control-plane:NoSchedule` taint。`cilium install` 通常会自动添加对应 toleration，但如果 `cilium status --wait` 超时，可以执行以下命令确认 Cilium DaemonSet 是否已在 control-plane 上调度：

```bash
kubectl -n kube-system get ds cilium -o jsonpath='{.spec.template.spec.tolerations}'
```

若缺少 toleration，需手动补充：`kubectl -n kube-system patch ds cilium --type=json -p '[{"op":"add","path":"/spec/template/spec/tolerations/-","value":{"key":"node-role.kubernetes.io/control-plane","operator":"Exists","effect":"NoSchedule"}}]'`

---

## 二、前置条件

### 2.1 order-service manifest 缺失

`k8s/apps/platform.yaml` 当前**没有** `order-service` 的 Deployment 和 Service 资源（只存在于其他服务的 `ORDER_SERVICE_URL` 环境变量中）。若直接应用 NetworkPolicy，所有引用 `app: order-service` 的策略将选中空集，既不保护也不限制任何 Pod。

**本方案实施前，必须先在 `platform.yaml` 中补充 `order-service` 的 Deployment + Service。** 这是独立的前置任务，不在本方案范围内，但属于硬性依赖。

---

## 三、NetworkPolicy 设计

### 3.1 App Pod 标签约定

为区分应用 Pod 与基础设施 Pod（MySQL、Kafka 等），所有应用 Pod 在 `platform.yaml` 的 Deployment `template.labels` 中新增：

```yaml
tier: app
```

这样 NetworkPolicy 可以用 `tier: app` 精确选定所有应用 Pod，避免 `allow-from-gateway` 策略意外覆盖基础设施 Pod。

### 3.2 策略总览

| 策略名 | 位置 | 作用 |
|--------|------|------|
| `allow-infra-from-shop` | `infra/base.yaml` | MySQL/Kafka/Redis/Meilisearch/Mailpit/otel-collector 对所有 shop Pod 开放 |
| `allow-from-gateway` | `apps/platform.yaml` | 所有 **app** Pod（`tier: app`）接受来自 `api-gateway` 的 ingress（限端口 8080） |
| `allow-from-buyer-bff` | `apps/platform.yaml` | buyer-bff 调用 domain service（限端口 8080） |
| `allow-from-seller-bff` | `apps/platform.yaml` | seller-bff 调用 domain service（限端口 8080） |
| `allow-portals-to-auth` | `apps/platform.yaml` | Portal 直接调用 auth-server |
| `allow-order-to-wallet` | `apps/platform.yaml` | order-service → wallet-service |
| `allow-activity-to-loyalty` | `apps/platform.yaml` | activity-service → loyalty-service |
| `allow-subscription-to-order` | `apps/platform.yaml` | subscription-service → order-service |
| `allow-config-watcher` | `infra/base.yaml` | config-watcher POST `/actuator/refresh` 到所有 app Pod（端口 8080） |
| `allow-prometheus-scrape` | `infra/base.yaml` | Prometheus 访问所有 Pod 的 `:8081` management 端口 |
| `allow-mirrord-agent` | `infra/base.yaml` | mirrord agent Pod 访问所有 Pod（本地开发） |

移除现有 `allow-shop-internal`（放行所有）。

### 3.3 服务调用图

```
外部
  ├── buyer-portal ──────────┐
  └── seller-portal ─────────┤
                             ▼
                        api-gateway
                        ├── /auth/**             → auth-server
                        ├── /buyer/**            → buyer-portal（SSR 路由）
                        ├── /seller/**           → seller-portal（SSR 路由）
                        ├── /api/buyer/**        → buyer-bff
                        ├── /api/seller/**       → seller-bff
                        ├── /api/loyalty/**      → loyalty-service
                        ├── /api/activity/**     → activity-service
                        ├── /api/webhook/**      → webhook-service
                        ├── /api/subscription/** → subscription-service
                        └── /v3/api-docs/**      → 所有服务（Swagger 聚合）

buyer-bff  → profile-service, marketplace-service, order-service,
             wallet-service, search-service, promotion-service

seller-bff → marketplace-service, order-service, profile-service,
             wallet-service, promotion-service, search-service

buyer-portal, seller-portal → auth-server（OAuth 回调）

order-service        → wallet-service
activity-service     → loyalty-service
subscription-service → order-service

notification-service：纯 Kafka 消费，无 HTTP ingress
```

### 3.4 具体 NetworkPolicy 资源

**① 基础设施开放**（放入 `infra/base.yaml`，替换 `allow-shop-internal`）

> **设计权衡：** Policy ① 允许 `shop` namespace 内所有 Pod 访问基础设施（MySQL、Kafka 等）。严格来说，Portal 和 BFF 无需直接连接 MySQL，但维护逐服务的精确 infra 权限清单成本过高且易出错。当前选择以运维简洁性为优先，接受这一宽松策略。
```yaml
apiVersion: networking.k8s.io/v1
kind: NetworkPolicy
metadata:
  name: allow-infra-from-shop
  namespace: shop
spec:
  podSelector:
    matchExpressions:
      - key: app
        operator: In
        values: [mysql, kafka, redis, meilisearch, mailpit, otel-collector]
  ingress:
    - from:
        - namespaceSelector:
            matchLabels:
              kubernetes.io/metadata.name: shop
  policyTypes: [Ingress]
```

**② Gateway 可达所有 App Pod**（`tier: app` 精确选定，不覆盖基础设施 Pod；限端口 8080，防止 gateway 访问 actuator）
```yaml
apiVersion: networking.k8s.io/v1
kind: NetworkPolicy
metadata:
  name: allow-from-gateway
  namespace: shop
spec:
  podSelector:
    matchLabels:
      tier: app
  ingress:
    - from:
        - podSelector:
            matchLabels:
              app: api-gateway
      ports:
        - port: 8080
  policyTypes: [Ingress]
```

**③ buyer-bff → domain services**（限端口 8080）
```yaml
apiVersion: networking.k8s.io/v1
kind: NetworkPolicy
metadata:
  name: allow-from-buyer-bff
  namespace: shop
spec:
  podSelector:
    matchExpressions:
      - key: app
        operator: In
        values: [profile-service, marketplace-service, order-service,
                 wallet-service, search-service, promotion-service]
  ingress:
    - from:
        - podSelector:
            matchLabels:
              app: buyer-bff
      ports:
        - port: 8080
  policyTypes: [Ingress]
```

**④ seller-bff → domain services**（限端口 8080）
```yaml
apiVersion: networking.k8s.io/v1
kind: NetworkPolicy
metadata:
  name: allow-from-seller-bff
  namespace: shop
spec:
  podSelector:
    matchExpressions:
      - key: app
        operator: In
        values: [marketplace-service, order-service, profile-service,
                 wallet-service, promotion-service, search-service]
  ingress:
    - from:
        - podSelector:
            matchLabels:
              app: seller-bff
      ports:
        - port: 8080
  policyTypes: [Ingress]
```

**⑤ Portal → auth-server**（限端口 8080）
```yaml
apiVersion: networking.k8s.io/v1
kind: NetworkPolicy
metadata:
  name: allow-portals-to-auth
  namespace: shop
spec:
  podSelector:
    matchLabels:
      app: auth-server
  ingress:
    - from:
        - podSelector:
            matchLabels:
              app: buyer-portal
        - podSelector:
            matchLabels:
              app: seller-portal
      ports:
        - port: 8080
  policyTypes: [Ingress]
```

**⑥ 服务间直接调用（精确配对，独立策略防止交叉权限，限端口 8080）**
```yaml
# order-service → wallet-service only
apiVersion: networking.k8s.io/v1
kind: NetworkPolicy
metadata:
  name: allow-order-to-wallet
  namespace: shop
spec:
  podSelector:
    matchLabels:
      app: wallet-service
  ingress:
    - from:
        - podSelector:
            matchLabels:
              app: order-service
      ports:
        - port: 8080
  policyTypes: [Ingress]
---
# activity-service → loyalty-service only
apiVersion: networking.k8s.io/v1
kind: NetworkPolicy
metadata:
  name: allow-activity-to-loyalty
  namespace: shop
spec:
  podSelector:
    matchLabels:
      app: loyalty-service
  ingress:
    - from:
        - podSelector:
            matchLabels:
              app: activity-service
      ports:
        - port: 8080
  policyTypes: [Ingress]
---
# subscription-service → order-service only
apiVersion: networking.k8s.io/v1
kind: NetworkPolicy
metadata:
  name: allow-subscription-to-order
  namespace: shop
spec:
  podSelector:
    matchLabels:
      app: order-service
  ingress:
    - from:
        - podSelector:
            matchLabels:
              app: subscription-service
      ports:
        - port: 8080
  policyTypes: [Ingress]
```

**⑦ Prometheus scrape**

> 前提：Prometheus 部署在 `shop` namespace（`app: prometheus`，见 `infra/base.yaml`）。若将来迁移到独立 namespace，需同时添加 `namespaceSelector` 并与 `podSelector` 置于同一 `from` 条目（AND 语义）。

```yaml
apiVersion: networking.k8s.io/v1
kind: NetworkPolicy
metadata:
  name: allow-prometheus-scrape
  namespace: shop
spec:
  podSelector: {}
  ingress:
    - from:
        - podSelector:
            matchLabels:
              app: prometheus
      ports:
        - port: 8081
  policyTypes: [Ingress]
```

**⑧ config-watcher → 所有 app Pod**（ConfigMap/Secret 变更时 POST `/actuator/refresh`）

> config-watcher 通过 `SPRING_CLOUD_KUBERNETES_CONFIGURATION_WATCHER_ACTUATORPORT: "8081"` 配置调用 management 端口 8081，因此这里开放的是 **8081**，而非业务端口 8080。

```yaml
apiVersion: networking.k8s.io/v1
kind: NetworkPolicy
metadata:
  name: allow-config-watcher
  namespace: shop
spec:
  podSelector:
    matchLabels:
      tier: app
  ingress:
    - from:
        - podSelector:
            matchLabels:
              app: spring-cloud-kubernetes-configuration-watcher
      ports:
        - port: 8081
  policyTypes: [Ingress]
```

**⑨ mirrord agent（本地开发）**
```yaml
apiVersion: networking.k8s.io/v1
kind: NetworkPolicy
metadata:
  name: allow-mirrord-agent
  namespace: shop
spec:
  podSelector: {}
  ingress:
    - from:
        - podSelector:
            matchLabels:
              app.kubernetes.io/name: mirrord
  policyTypes: [Ingress]
```

---

## 四、关闭 Internal Token

`k8s/apps/platform.yaml` 的 `shop-shared-config` ConfigMap 新增：

```yaml
data:
  SHOP_SECURITY_INTERNAL_ENABLED: "false"
```

**实现说明：** `InternalAccessFilterConfiguration` 的条件注解为：

```java
@ConditionalOnProperty(prefix = "shop.security.internal", name = "enabled", havingValue = "true")
```

当 `shop.security.internal.enabled` 不为 `true` 时，`InternalAccessFilter` Bean **不会被注册**（而非运行时跳过校验）。设置环境变量为 `"false"` 与不设置效果相同，但显式设置可以作为意图说明，让代码审查者清楚这是主动关闭而非遗漏。

`shop-shared-secret` 中的 `SHOP_INTERNAL_TOKEN` 保留（Gateway 继续注入 header 无影响）。

---

## 五、Egress 策略

本方案不限制 Egress（保持 `egress: - {}`）。服务需要访问集群外部（Stripe webhook、SMTP relay 等），强制 Egress 策略会带来额外维护成本，超出本方案范围。

---

## 六、变更文件清单

| 文件 | 变更类型 | 说明 |
|------|---------|------|
| `kind/cluster-config.yaml` | 修改 | 禁用 kindnet，`kubeProxyMode: none` |
| `kind/setup.sh` | 修改 | 新增 Cilium 安装步骤 |
| `k8s/infra/base.yaml` | 修改 | 移除 `allow-shop-internal`；新增策略 ①⑧⑨（infra、config-watcher、mirrord）；新增 Prometheus scrape ⑦ |
| `k8s/apps/platform.yaml` | 修改 | 所有 app Deployment 新增 `tier: app` label；shop-shared-config 新增 `SHOP_SECURITY_INTERNAL_ENABLED: "false"`；新增 NetworkPolicy ②③④⑤⑥ |
| `k8s/apps/platform.yaml`（前置） | **新增** | `order-service` Deployment + Service（硬性前置条件，独立任务） |
| `docs/SECURITY-BASELINE-2026.md` | 已更新 | 已记录 Cilium/NetworkPolicy 演进说明 |

---

## 七、本地开发注意事项

- 重建集群前需先 `kind delete cluster`，再执行更新后的 `kind/setup.sh`
- `cilium-cli` 需提前安装，建议锁定版本（`brew install cilium-cli` 或指定版本）
- mirrord agent Pod 带有 `app.kubernetes.io/name: mirrord` label，已通过 `allow-mirrord-agent` 策略放行
- Prometheus 当前在 `shop` namespace，若迁移到独立 namespace 需更新 `allow-prometheus-scrape` 策略（添加 `namespaceSelector` AND `podSelector` 组合）
