# 项目模块地图

## 1. 根目录

- `pom.xml`：Maven 多模块父工程
- `README.md`：项目总说明
- `docs/`：SSOT / 设计 / Roadmap / 交接文档
- `docs-site/`：Docusaurus 文档站（`docs/` 的站点化入口）
- `docker/`：统一 Docker 构建模板
- `kind/` / `k8s/`：本地 Kind 与 K8s 部署资产
- `scripts/`：镜像构建、Kind 部署、辅助脚本

---

## 2. 共享模块

### `shop-common`

主要职责：

- `ApiResponse`
- `CommonErrorCode` / `BusinessException`
- `TrustedHeaderNames`
- `InternalAccessFilter`
- 全局异常处理

关键文件：

- `shop-common/src/main/java/dev/meirong/shop/common/api/ApiResponse.java`
- `shop-common/src/main/java/dev/meirong/shop/common/error/CommonErrorCode.java`
- `shop-common/src/main/java/dev/meirong/shop/common/web/InternalAccessFilterConfiguration.java`

### `shop-contracts`

主要职责：

- 所有 northbound / east-west API path 常量与 DTO
- Kafka 事件 envelope 与事件 payload

关键文件：

- `shop-contracts/src/main/java/dev/meirong/shop/contracts/api/`
- `shop-contracts/src/main/java/dev/meirong/shop/contracts/event/`

### `shop-archetypes`

主要职责：

- 新服务脚手架
- Domain / event-worker 测试模板

---

## 3. 平台入口

### `auth-server`

职责：

- 用户登录
- Google 社交登录
- guest buyer token
- JWT 签发与 `me`

关键文件：

- `config/SecurityConfig.java`
- `service/JwtTokenService.java`
- `service/GuestSessionService.java`
- `service/SocialLoginService.java`
- `controller/AuthController.java`

### `api-gateway`

职责：

- YAML 路由
- JWT 校验
- Trusted Headers 注入
- Redis Lua 限流
- Canary
- `/api/**` + `/public/**` CORS

关键文件：

- `config/GatewaySecurityConfig.java`
- `config/GatewayProperties.java`
- `filter/TrustedHeadersFilter.java`
- `filter/RateLimitingFilter.java`
- `src/main/resources/application.yml`

---

## 4. BFF

### `buyer-bff`

职责：

- Buyer dashboard 聚合
- 商品搜索 / 商品详情 / 店铺页
- 已登录购物车 + Redis 游客购物车
- 登录后购物车合并
- checkout / orders / payment intent
- loyalty Hub 聚合
- checkout 下游韧性边界

关键文件：

- `config/BuyerClientProperties.java`
- `config/BuyerBffConfig.java`
- `service/BuyerAggregationService.java`
- `service/GuestCartStore.java`
- `controller/BuyerController.java`

### `seller-bff`

职责：

- Seller dashboard 聚合
- 商品 / 促销 / 优惠券 / 店铺设置
- 订单履约
- Seller 侧查询聚合

关键文件：

- `config/SellerClientProperties.java`
- `config/SellerBffConfig.java`
- `service/SellerAggregationService.java`
- `controller/SellerController.java`

---

## 5. 领域服务

### 核心购物

- `profile-service`：买家/卖家档案、店铺信息
- `marketplace-service`：商品、分类、评论、SKU、商品 outbox
- `order-service`：购物车、订单状态机、游客直购、订单 outbox
- `wallet-service`：余额、充值/提现、支付 provider、wallet outbox
- `promotion-service`：promotion engine、calculate API、coupon legacy + template/instance 双轨过渡

### 用户增长 / 参与

- `loyalty-service`：积分账户、签到、兑换、新手任务、积分到期批处理
- `activity-service`：4 种已实现玩法、AntiCheatGuard、RewardDispatcher（当前外部派发仍为桩）

### 扩展能力

- `search-service`：Meilisearch 搜索 + autocomplete + trending + feature toggle
- `notification-service`：Kafka 事件通知、模板邮件、幂等重试
- `webhook-service`：卖家 Webhook 订阅与投递重试
- `subscription-service`：订阅计划与生命周期

---

## 6. 门户

### `buyer-portal`

功能：

- guest session 浏览
- 首页 / 商品详情 / 店铺页
- 游客购物车与登录后 merge
- 已登录 checkout / orders / wallet / profile
- `/buyer/loyalty`
- `/buyer/activities`

关键文件：

- `service/BuyerPortalApiClient.kt`
- `controller/BuyerPortalController.kt`
- `templates/buyer-home.html`
- `templates/buyer-cart.html`
- `templates/buyer-loyalty.html`
- `templates/buyer-activities.html`
- `templates/buyer-activity-detail.html`

### `seller-portal`

功能：

- seller 登录
- dashboard
- 商品管理
- 订单履约
- promotion / coupon 管理
- 店铺设置

关键文件：

- `service/SellerPortalApiClient.kt`
- `controller/SellerPortalController.kt`
- `templates/seller-dashboard.html`
- `templates/seller-products.html`
- `templates/seller-orders.html`
- `templates/seller-promotions.html`
- `templates/seller-shop.html`

---

## 7. 部署与运行资产

### Docker / Kind / K8s

- `docker/Dockerfile.module`
- `kind/cluster-config.yaml`
- `k8s/infra/base.yaml`
- `k8s/apps/platform.yaml`

### 脚本

- `scripts/build-images.sh`
- `scripts/load-images-kind.sh`
- `scripts/kind-up.sh`
- `scripts/deploy-kind.sh`

> `scripts/sync-into-target-repo.sh` 仍保留在仓库中，但它已不是当前常规开发路径的一部分。

---

## 8. 后续优先关注的文件

如果下一位开发者要继续完成，建议按顺序看：

1. `README.md`
2. `docs/SOURCE-OF-TRUTH-MATRIX.md`
3. `docs/ROADMAP-2026.md`
4. `docs/DELIVERY-PRIORITY-AND-DEPENDENCY-MAP.md`
5. `docs-site/docs/architecture/index.md`
6. `buyer-bff` / `seller-bff`
7. `promotion-service` / `loyalty-service` / `activity-service`
8. `k8s/` 与 `scripts/`
