---
title: 快速开始
---

# 快速开始

本页提供最快可运行路径（Kind），适合首次体验。

## 前置条件

- Docker Desktop 或 [OrbStack](https://orbstack.dev/)（macOS 推荐，性能更好）
- [Kind](https://kind.sigs.k8s.io/)
- kubectl
- （可选）[mirrord](https://mirrord.dev/) — IDE 调试用

## 首次启动（全量构建 + 部署）

```bash
# 终端 A：一键创建集群 + 构建所有镜像 + 部署 + 验证
make e2e
# 缓存热（.m2 + Docker 层）约 4–5 min，首次约 10–20 min
# 结束时会打印每个阶段的耗时

# 终端 B（保持运行）：建立稳定访问入口
make local-access
```

访问入口（已验证）：

| 服务 | 地址 |
|------|------|
| Buyer Portal | http://127.0.0.1:18080/buyer/login |
| **Seller Portal** | **http://127.0.0.1:18080/seller/** |
| Gateway OpenAPI | http://127.0.0.1:18080/v3/api-docs/gateway |
| Mailpit（邮件） | http://127.0.0.1:18025 |
| Prometheus | http://127.0.0.1:19090 |
| **Grafana** | **http://127.0.0.1:13000** |

演示账号：

| 角色 | 用户名 | 密码 |
|------|--------|------|
| Buyer | buyer.demo | password |
| **Seller** | **seller.demo** | **password** |

## 推荐使用流程：卖家先行

为保证买家能找到商品，建议先以卖家身份登录管理后台确认库存，再以买家身份下单。

### 1. 卖家登录管理后台

访问 **Seller Portal**：http://127.0.0.1:18080/seller/

使用账号 `seller.demo / password` 登录，可查看和管理：
- 商品列表（系统已预置 **Ionic Hair Dryer**、**Beauty Starter Kit** 两款商品）
- 店铺信息（Demo Store，Slug: demo-store）
- 订单与收益报表

### 2. 买家浏览下单

访问 **Buyer Portal**：http://127.0.0.1:18080/buyer/login

使用账号 `buyer.demo / password` 登录后：
- 在首页搜索或浏览商品
- 加入购物车并结账
- 在「Orders」页查看订单状态

### 3. 通过 Grafana 观测系统

访问 **Grafana**：http://127.0.0.1:13000

Grafana 预置了以下数据源：
- **Prometheus** — 服务指标（请求率、延迟、错误率）
- **Loki** — 结构化日志聚合（来自所有 Pod）
- **Tempo** — 分布式链路追踪（与 OTLP 对接）
- **Pyroscope** — 持续性能剖析

下单后可在 Grafana 中搜索 TraceID、查看 BFF 调用链、观察服务指标变化。

## 日常开发（集群已启动后）

集群已运行时，按场景选择最快路径：

| 场景 | 命令 | 耗时 |
|------|------|------|
| 修改某个服务，快速重新部署 | `make redeploy MODULE=buyer-bff` | ~30s |
| 批量重部署所有变更模块 | `make build-changed && make load-changed` | 按变更数量 |
| IDE 断点调试（无需 rebuild） | `make mirrord-run MODULE=buyer-bff` | 秒级启动 |
| 频繁改代码 + 自动热更新 | `make tilt-up` | 持续监听 |
| 完整验证含 Seller WASM UI | `E2E_FULL_UI=1 make e2e` | ~15 min（首次 Gradle） |

### 修改一个服务后重新部署

```bash
# 修改代码后执行（~30 秒完成单服务的重建 + 加载 + 滚动重启）
make redeploy MODULE=buyer-bff

# 验证
kubectl -n shop logs -f deployment/buyer-bff
make smoke-test
```

### IDE 断点调试（推荐：无需 rebuild 镜像）

```bash
# 集群中已有 buyer-bff 在跑，本地进程接管流量并可在 IDE 中打断点
make mirrord-run MODULE=buyer-bff

# 附加远程调试端口（5005）：
./scripts/mirrord-debug.sh buyer-bff -- \
  ./mvnw -pl buyer-bff -am spring-boot:run \
  -Dspring-boot.run.jvmArguments="-agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=5005"
```

> 安装 mirrord：`brew install metalbear-co/mirrord/mirrord`

## 快速健康检查

```bash
kubectl -n shop get pods
curl -fsS http://127.0.0.1:18080/actuator/health | jq -r '.status'
curl -fsS "http://127.0.0.1:18080/public/buyer/v1/product/search?q=hair" | head
```

## 常见问题

- **服务未就绪**：`kubectl -n shop get pods` 与 `kubectl -n shop logs <pod>`
- **访问不通**：确认另一个终端有 `make local-access` 在跑
- **数据重置**：`./kind/teardown.sh && make e2e`
- **make redeploy 报错**：确认 KIND 集群在线 `kind get clusters`

## 进阶

需要了解分步部署、Tilt 热更新、ArgoCD GitOps 或更多冒烟步骤，继续阅读 [`本地部署`](/getting-started/local-deployment)。


集群已运行时，按场景选择最快路径：

| 场景 | 命令 | 耗时 |
|------|------|------|
| 修改某个服务，快速重新部署 | `make redeploy MODULE=buyer-bff` | ~30s |
| 批量重部署所有变更模块 | `make build-changed && make load-changed` | 按变更数量 |
| IDE 断点调试（无需 rebuild） | `make mirrord-run MODULE=buyer-bff` | 秒级启动 |
| 频繁改代码 + 自动热更新 | `make tilt-up` | 持续监听 |
| 完整验证含 Seller WASM UI | `E2E_FULL_UI=1 make e2e` | ~15 min（首次 Gradle） |

### 修改一个服务后重新部署

```bash
# 修改代码后执行（~30 秒完成单服务的重建 + 加载 + 滚动重启）
make redeploy MODULE=buyer-bff

# 验证
kubectl -n shop logs -f deployment/buyer-bff
make smoke-test
```

### IDE 断点调试（推荐：无需 rebuild 镜像）

```bash
# 集群中已有 buyer-bff 在跑，本地进程接管流量并可在 IDE 中打断点
make mirrord-run MODULE=buyer-bff

# 附加远程调试端口（5005）：
./scripts/mirrord-debug.sh buyer-bff -- \
  ./mvnw -pl buyer-bff -am spring-boot:run \
  -Dspring-boot.run.jvmArguments="-agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=5005"
```

> 安装 mirrord：`brew install metalbear-co/mirrord/mirrord`

## 快速健康检查

```bash
kubectl -n shop get pods
curl -fsS http://127.0.0.1:18080/actuator/health | jq -r '.status'
curl -fsS "http://127.0.0.1:18080/public/buyer/v1/product/search?q=hair" | head
```

## 常见问题

- **服务未就绪**：`kubectl -n shop get pods` 与 `kubectl -n shop logs <pod>`
- **访问不通**：确认另一个终端有 `make local-access` 在跑
- **数据重置**：`./kind/teardown.sh && make e2e`
- **make redeploy 报错**：确认 KIND 集群在线 `kind get clusters`

## 进阶

需要了解分步部署、Tilt 热更新、ArgoCD GitOps 或更多冒烟步骤，继续阅读 [`本地部署`](/getting-started/local-deployment)。
