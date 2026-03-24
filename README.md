# Shop Platform

基于 **Java 25 + Spring Boot 3.5.11 + Spring Cloud 2025.0.1 + Kotlin 2.3** 的云原生微服务技术验证平台。

## 目标

- 验证 Gateway + Thin BFF + Domain Service 架构在 2026 技术基线下可行
- 验证 Spring Security 认证中心、Kotlin Portal、Kafka 事件驱动、Stripe 支付接入
- Buyer 与 Seller 分离入口
- Docker Compose / Kind 本地部署验证

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
| `buyer-portal` / `seller-portal` | Kotlin + Thymeleaf 门户 |

## 快速开始（Docker Compose）

```bash
# 1. 构建
mvn -DskipTests verify

# 2. 环境变量
cp .env.example .env

# 3. 构建镜像并启动
./scripts/build-images.sh
docker compose up -d

# 4. 访问
# Buyer Portal: http://localhost:8080/buyer/login
# Seller Portal: http://localhost:8080/seller/login
# Prometheus:    http://localhost:9090
```

> Redis 在当前仓库中不只是缓存：除了 gateway 限流、OTP、guest cart、活动防作弊与 Bloom Filter 幂等外，`marketplace-service`、`order-service`、`subscription-service`、`promotion-service` 现在也通过 Redisson 做库存与批处理协调。因此本地和 Kind 验证都不应跳过 `redis`。

> Kafka consumer 的失败处理现在按场景区分：`search-service` 走 projection 型 `retry + DLT`；`promotion-service` / `loyalty-service` 走幂等业务型“瞬时错误有限重试、毒消息直送 DLT”；`notification-service` / `webhook-service` 只把解析/契约错误送入 DLT，真实投递失败继续由各自的数据库重试调度器接管。

## Kind/Kubernetes 部署

```bash
mvn -DskipTests verify
./scripts/build-images.sh
./scripts/kind-up.sh
./scripts/load-images-kind.sh
./scripts/deploy-kind.sh
# 访问 http://localhost:8080/buyer/login
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

## 运行测试

```bash
mvn test
```
