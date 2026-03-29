# Shop Platform

基于 **Java 25 + Spring Boot 3.5.11 + Spring Cloud 2025.0.1 + Kotlin 2.3** 的云原生微服务技术验证平台。

## 目标

- 验证 Gateway + Thin BFF + Domain Service 架构在 2026 技术基线下可行
- 验证 Spring Security 认证中心、Kotlin Portal、Kafka 事件驱动、Stripe 支付接入
- Buyer 与 Seller 分离入口
- 统一采用 Kind / Kubernetes 本地部署验证

## 模块

| 模块 | 说明 |
|------|------|
| `shop-common` | 通用响应封装、错误模型、内部认证过滤 |
| `shop-contracts` | API 路径常量、DTO、事件契约 |
| `auth-server` | JWT 认证服务 |
| `api-gateway` | 统一路由、JWT 校验、Trusted Headers |
| `buyer-bff` / `seller-bff` | 聚合层 (BFF) |
| `profile-service` | 用户档案服务 |
| `promotion-service` | 促销活动服务 |
| `wallet-service` | 钱包服务 (Stripe + Outbox) |
| `marketplace-service` | 商品市场服务 |
| `buyer-portal` / `kmp/*` apps | Buyer SSR 门户 + Compose Multiplatform Buyer/Seller 应用 |

## 快速开始（Kind）

```bash
./kind/setup.sh

# 另开一个终端，建立稳定的本地访问入口
make local-access

# Buyer Portal:  http://127.0.0.1:18080/buyer/login
# Gateway docs:  http://127.0.0.1:18080/v3/api-docs/gateway
# Mailpit:       http://127.0.0.1:18025
# Prometheus:    http://127.0.0.1:19090
```

清理环境：

```bash
./kind/teardown.sh
```

> Redis 在当前仓库中不只是缓存：除了 gateway 限流、OTP、guest cart、活动防作弊与 Bloom Filter 幂等外，`marketplace-service`、`order-service`、`subscription-service`、`promotion-service` 也通过 Redisson 做库存与批处理协调。因此本地 Kind 验证不应跳过 `redis`。

> Kafka consumer 的失败处理现在按场景区分：`search-service` 走 projection 型 `retry + DLT`；`promotion-service` / `loyalty-service` 走幂等业务型“瞬时错误有限重试、毒消息直送 DLT”；`notification-service` / `webhook-service` 只把解析/契约错误送入 DLT，真实投递失败继续由各自的数据库重试调度器接管。

## Kind/Kubernetes 部署

```bash
./scripts/build-images.sh

# 仅构建相对 origin/main（或 --base 指定基线）发生变化的模块
./scripts/build-images.sh --changed -j 4

kind create cluster --name shop-kind --config kind/cluster-config.yaml

./scripts/load-images-kind.sh shop-kind --registry

# 仅加载发生变化的模块镜像
./scripts/load-images-kind.sh shop-kind --changed --registry

kubectl apply -f k8s/namespace.yaml
kubectl apply -f k8s/infra/base.yaml
./scripts/deploy-kind.sh dev

# 推荐本地链路
# 1. make registry（首次或重建 Kind 后执行一次）
# 2. make e2e（默认走 fast：host Maven build + registry push + selective deploy）
# 3. 如需排障，使用 make e2e-legacy
# 4. 验证入口保持不变：make local-access、make smoke-test、make ui-e2e

# legacy 排障路径
./scripts/load-images-kind.sh shop-kind --kind-load

# 只做平台资产校验（shell / Kustomize / Tiltfile / mirrord / overlay 一致性）
make platform-validate
```

> 在 Kind + Cilium（尤其 macOS + OrbStack）环境下，`localhost:8080` / `8025` / `9090` 的直连映射不一定稳定。仓库当前**已验证**的访问路径是 `make local-access` 提供的 `18080` / `18025` / `19090` 端口。`make e2e` 默认走 fast（host Maven build + registry push + selective deploy），并继续执行 buyer SSR 页面与 seller KMP Web 页面回归；如需排障可切回 `make e2e-legacy`。其中 seller Web 校验依赖本机可用的 Chrome / Chromium。

## Inner-loop 与 GitOps 可选增强

```bash
# 启动本地 registry（首次修改 kind mirror 配置后建议重建集群）
make registry

# 安装 Tilt（brew install tilt）后启动内循环开发
make tilt-up

# 安装 mirrord（brew install metalbear-co/mirrord/mirrord）后把本地进程接到远端 deployment 上调试
make mirrord-run MODULE=api-gateway

# 可选：在 Kind 中安装 ArgoCD 并接管 dev overlay
make argocd-bootstrap
```

`make mirrord-run` 默认等价于：

```bash
./scripts/mirrord-debug.sh api-gateway
```

它会把本地 `spring-boot:run` 进程挂到 `shop` namespace 下的 `deployment/api-gateway`。如果你要调试其他服务或自定义命令，也可以直接运行：

```bash
./scripts/mirrord-debug.sh buyer-bff -- ./mvnw -pl buyer-bff -am spring-boot:run
```

## 本地 CI/CD 与多模块构建

```bash
# 仅构建/加载相对于基线发生变化的模块（极大提升开发效率）
./scripts/build-images.sh --changed --fast
./scripts/load-images-kind.sh shop-kind --changed --registry

# 完整本地流水线
make e2e

# legacy 排障
make e2e-legacy
```

## 演示账号

| 角色 | 用户名 | 密码 |
|------|--------|------|
| Buyer | buyer.demo | password |
| Buyer VIP | buyer.vip | password |
| Seller | seller.demo | password |

## 文档

详细文档请参见 `docs-site/`，使用 Docusaurus 构建：

```bash
cd docs-site && npm install && npm start
```

工程与技术栈统一标准见：

- `docs/ENGINEERING-STANDARDS-2026.md`（2026 统一技术栈与微服务 Scaffold 标准）
- `docs/COMPATIBILITY-DEVELOPMENT-STANDARD-2026.md`（版本升级 / 新特性开发兼容性规范与当前项目支持情况）
- `docs/DEVELOPER-EXPERIENCE-STANDARD-2026.md`（CI、Makefile、git hooks、.editorconfig、archetype 开发流程）

## Developer Experience

推荐先看：

```bash
make help
make install-hooks
```

常用入口：

- `make verify`
- `make test`
- `make docs-build`
- `make archetypes-install`

## 运行测试

```bash
./mvnw test
```
