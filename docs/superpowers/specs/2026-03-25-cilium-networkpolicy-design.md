# Cilium CNI + NetworkPolicy 替换 Internal Token 设计

> 版本：1.0 | 日期：2026-03-25

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

**前提依赖：** 本地需安装 `cilium` CLI（`brew install cilium-cli`）。

---

## 二、NetworkPolicy 设计

### 2.1 策略总览

| 策略名 | 作用 |
|--------|------|
| `allow-infra-from-shop` | MySQL/Kafka/Redis/Meilisearch/Mailpit 对所有 shop Pod 开放 |
| `allow-from-gateway` | 所有 app Pod 接受来自 `api-gateway` 的 ingress（含 Swagger 聚合） |
| `allow-bff-to-domain` | BFF 调用 domain service 的 specific 规则 |
| `allow-domain-to-domain` | 服务间直接调用规则（order→wallet、activity→loyalty 等） |
| `allow-prometheus-scrape` | Prometheus 访问所有 Pod 的 `:8081` management 端口 |
| `allow-mirrord-agent` | mirrord agent Pod 访问所有 Pod（本地开发） |

移除现有 `allow-shop-internal`（放行所有）。

### 2.2 服务调用图

```
外部
  ├── buyer-portal ──────────┐
  └── seller-portal ─────────┤
                             ▼
                        api-gateway
                        ├── /auth/**        → auth-server
                        ├── /buyer/**       → buyer-portal（SSR 路由）
                        ├── /seller/**      → seller-portal（SSR 路由）
                        ├── /api/buyer/**   → buyer-bff
                        ├── /api/seller/**  → seller-bff
                        ├── /api/loyalty/** → loyalty-service
                        ├── /api/activity/** → activity-service
                        ├── /api/webhook/** → webhook-service
                        ├── /api/subscription/** → subscription-service
                        └── /v3/api-docs/** → 所有服务（Swagger 聚合）

buyer-bff → profile-service, marketplace-service, order-service,
            wallet-service, search-service, promotion-service

seller-bff → marketplace-service, order-service, profile-service

buyer-portal, seller-portal → auth-server（独立 OAuth 回调）

order-service       → wallet-service
activity-service    → loyalty-service
subscription-service → order-service

notification-service：纯 Kafka 消费，无 HTTP ingress
```

### 2.3 具体 NetworkPolicy 资源

**① 基础设施开放**
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

**② Gateway 可达所有 App Pod**
```yaml
apiVersion: networking.k8s.io/v1
kind: NetworkPolicy
metadata:
  name: allow-from-gateway
  namespace: shop
spec:
  podSelector: {}
  ingress:
    - from:
        - podSelector:
            matchLabels:
              app: api-gateway
  policyTypes: [Ingress]
```

**③ buyer-bff → domain services**
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
  policyTypes: [Ingress]
```

**④ seller-bff → domain services**
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
        values: [marketplace-service, order-service, profile-service]
  ingress:
    - from:
        - podSelector:
            matchLabels:
              app: seller-bff
  policyTypes: [Ingress]
```

**⑤ Portal → auth-server**
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
  policyTypes: [Ingress]
```

**⑥ 服务间直接调用**
```yaml
apiVersion: networking.k8s.io/v1
kind: NetworkPolicy
metadata:
  name: allow-domain-to-domain
  namespace: shop
spec:
  podSelector:
    matchExpressions:
      - key: app
        operator: In
        values: [wallet-service, loyalty-service, order-service]
  ingress:
    - from:
        - podSelector:
            matchLabels:
              app: order-service      # order → wallet
    - from:
        - podSelector:
            matchLabels:
              app: activity-service   # activity → loyalty
    - from:
        - podSelector:
            matchLabels:
              app: subscription-service  # subscription → order
  policyTypes: [Ingress]
```

**⑦ Prometheus scrape**
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

**⑧ mirrord agent（本地开发）**
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

## 三、关闭 Internal Token

`k8s/apps/platform.yaml` 的 `shop-shared-config` ConfigMap 新增一个 key：

```yaml
data:
  SHOP_SECURITY_INTERNAL_ENABLED: "false"
```

所有服务通过 `envFrom.configMapRef` 继承该值，`InternalAccessFilter` 自动跳过校验，无需修改任何服务代码。

`shop-shared-secret` 中的 `SHOP_INTERNAL_TOKEN` 保留（Gateway 继续注入 header 也无影响，服务不再校验）。文档更新见 `docs/SECURITY-BASELINE-2026.md`。

---

## 四、Egress 策略

本方案不限制 Egress（保持 `egress: - {}`）。服务需要访问集群外部（Stripe webhook、SMTP relay 等），强制 Egress 策略会带来额外维护成本，超出本方案范围。

---

## 五、变更文件清单

| 文件 | 变更类型 | 说明 |
|------|---------|------|
| `kind/cluster-config.yaml` | 修改 | 禁用 kindnet，kubeProxyMode: none |
| `kind/setup.sh` | 修改 | 新增 Cilium 安装步骤 |
| `k8s/infra/base.yaml` | 修改 | 移除 `allow-shop-internal`，新增 ①⑦⑧ |
| `k8s/apps/platform.yaml` | 修改 | shop-shared-config 新增 `SHOP_SECURITY_INTERNAL_ENABLED: "false"`；新增 NetworkPolicy ②③④⑤⑥ |
| `docs/SECURITY-BASELINE-2026.md` | 已更新 | 已记录 Cilium/NetworkPolicy 演进说明 |

---

## 六、本地开发注意事项

- 重建集群前需先 `kind delete cluster`，再执行更新后的 `kind/setup.sh`
- `cilium-cli` 需提前安装：`brew install cilium-cli`
- mirrord 运行时会创建带 `app.kubernetes.io/name: mirrord` label 的 agent Pod，已通过 `allow-mirrord-agent` 策略放行
