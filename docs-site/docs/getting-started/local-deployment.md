---
sidebar_position: 3
title: 本地部署
---

# 本地部署指南

## 前置要求

- Docker Desktop（或任意兼容 Docker 的运行时）
- Kind
- kubectl

## 唯一支持的本地运行方式：Kind / Kubernetes

当前仓库不再维护 `docker-compose.yml` 路径；本地开发、联调、热更新验证统一以 Kind + Kubernetes 清单为准。

### 1. 一键部署（推荐）

```bash
./kind/setup.sh
```

此脚本会自动：创建 Kind 集群 → 构建全部镜像 → 加载镜像 → 部署基础设施与平台服务。

> `kind/setup.sh` 会为 Meilisearch 注入符合生产模式要求的 16+ 字节密钥，并在 MySQL 首次启动时初始化各服务所需的数据库与授权。

### 2. 清理环境

```bash
./kind/teardown.sh
```

### 3. 分步部署

```bash
# 构建所有 Docker 镜像（镜像内部会完成 Maven 打包）
./scripts/build-images.sh

# 创建 Kind 集群
kind create cluster --name shop-kind --config kind/cluster-config.yaml

# 加载镜像到集群
./scripts/load-images-kind.sh shop-kind

# 部署 K8s 清单
kubectl apply -f k8s/namespace.yaml
kubectl apply -f k8s/infra/base.yaml
kubectl apply -f k8s/apps/platform.yaml
```

### 4. 访问与基本验证

- **Buyer Portal**: [http://localhost:8080/buyer/login](http://localhost:8080/buyer/login)
- **Seller Portal**: [http://localhost:8080/seller/login](http://localhost:8080/seller/login)
- **Guest Checkout**: [http://localhost:8080/buyer/guest/track](http://localhost:8080/buyer/guest/track)
- **Mailpit（邮件）**: [http://localhost:8025](http://localhost:8025)
- **Prometheus**: [http://localhost:9090](http://localhost:9090)

```bash
kubectl -n shop get pods
```

如果当前环境没有直接暴露 `localhost:8080`，可以通过端口转发稳定访问网关：

```bash
kubectl -n shop port-forward svc/api-gateway 18080:8080
```

> 通过网关访问 `buyer-bff`、`subscription-service`、`webhook-service` 等路由时，需要保留服务自身的基础路径，例如：`/api/buyer/v1/...`、`/api/subscription/v1/...`、`/api/webhook/v1/...`。Gateway 只会剥离最前面的 `/api` 段。
> Redis 在 Kind 路径中仍然是必选基础设施：guest cart、限流、OTP、防作弊、Bloom Filter 幂等以及 Redisson 分布式锁都会依赖它。

### 4.1 验证 Search Service Feature Toggle 热更新


当前仓库在 Kind 下使用：

- `search-service-feature-toggles` ConfigMap
- Spring Cloud Kubernetes Configuration Watcher
- `search-service` 的 `/actuator/refresh`
- `X-Internal-Token` 保护直接访问的内部服务接口

验证建议：

```bash
# 端口转发 search-service（业务 + 管理端口）
kubectl -n shop port-forward svc/search-service 18091:8080 18092:8081

# 读取内部调用 token
INTERNAL_TOKEN=$(kubectl -n shop get secret shop-shared-secret -o jsonpath='{.data.SHOP_INTERNAL_TOKEN}' | base64 --decode)

# 1) 确认 autocomplete 当前可用
curl -H "X-Internal-Token: ${INTERNAL_TOKEN}" \
  "http://127.0.0.1:18091/search/v1/products/suggestions?q=hair"

# 2) 关闭 autocomplete
kubectl -n shop patch configmap search-service-feature-toggles \
  --type merge \
  -p '{"data":{"feature-toggles.yaml":"shop:\n  features:\n    flags:\n      search-autocomplete: false\n      search-trending: true\n      search-locale-aware: true\n"}}'

# 3) 等待 watcher 触发 refresh（当前 Kind 建议等待约 40~50 秒）后再次访问，应返回 503
curl -i -H "X-Internal-Token: ${INTERNAL_TOKEN}" \
  "http://127.0.0.1:18091/search/v1/products/suggestions?q=hair"

# 4) 恢复开关
kubectl -n shop patch configmap search-service-feature-toggles \
  --type merge \
  -p '{"data":{"feature-toggles.yaml":"shop:\n  features:\n    flags:\n      search-autocomplete: true\n      search-trending: true\n      search-locale-aware: true\n"}}'
```

> 注意：需要热更新的 ConfigMap 文件必须以**目录挂载**方式注入 Pod，不能使用 `subPath`，否则 K8s 不会把更新后的文件内容投影到容器内。
> 由于 `search-service` 直接端口转发绕过了 gateway/BFF，访问业务接口时要显式带上 `X-Internal-Token`。

### 4.1.1 验证 Search Service 默认排序与显式排序

当前仓库已在 Kind 中验证：

- 默认搜索结果会优先返回库存更充足的商品（`inventory:desc` custom ranking baseline）
- 显式 `sort=priceInCents:asc` 仍会覆盖默认库存优先顺序
- `POST /search/v1/products/_reindex` 会等待 Meilisearch swap / delete 任务完成后再返回

```bash
# 分别端口转发 marketplace-service 与 search-service
kubectl -n shop port-forward svc/marketplace-service 38080:8080
kubectl -n shop port-forward svc/search-service 38091:8080 38092:8081

INTERNAL_TOKEN=$(kubectl -n shop get secret shop-shared-secret -o jsonpath='{.data.SHOP_INTERNAL_TOKEN}' | base64 --decode)
STAMP=$(date +%s)
QUERY="ranking-smoke-${STAMP}"

# 1) 创建两条同名商品：A 更便宜但缺货，B 更贵但有库存
curl -H "X-Internal-Token: ${INTERNAL_TOKEN}" \
  -H 'Content-Type: application/json' \
  -X POST http://127.0.0.1:38080/marketplace/v1/product/create \
  -d "{\"sellerId\":\"seller-smoke\",\"sku\":\"RANK-${STAMP}-A\",\"name\":\"${QUERY}\",\"description\":\"search ranking smoke A\",\"price\":9.99,\"inventory\":0,\"published\":true}"

curl -H "X-Internal-Token: ${INTERNAL_TOKEN}" \
  -H 'Content-Type: application/json' \
  -X POST http://127.0.0.1:38080/marketplace/v1/product/create \
  -d "{\"sellerId\":\"seller-smoke\",\"sku\":\"RANK-${STAMP}-B\",\"name\":\"${QUERY}\",\"description\":\"search ranking smoke B\",\"price\":29.99,\"inventory\":30,\"published\":true}"

# 2) 触发全量重建索引；返回 SC_OK 后即可直接继续查询
curl -H "X-Internal-Token: ${INTERNAL_TOKEN}" \
  -X POST http://127.0.0.1:38091/search/v1/products/_reindex

# 3) 默认搜索：应先返回有库存商品
curl -H "X-Internal-Token: ${INTERNAL_TOKEN}" --get \
  --data-urlencode "q=${QUERY}" \
  http://127.0.0.1:38091/search/v1/products

# 4) 显式价格升序：应先返回更便宜商品
curl -H "X-Internal-Token: ${INTERNAL_TOKEN}" --get \
  --data-urlencode "q=${QUERY}" \
  --data-urlencode 'sort=priceInCents:asc' \
  http://127.0.0.1:38091/search/v1/products
```

### 4.2 验证 Activity Service 红包活动

当前仓库已在 Kind 中验证 `RedEnvelopePlugin`、`CollectCardPlugin`、`VirtualFarmPlugin` 与活动防作弊链路：

- `ROLE_ADMIN` 才能创建/激活活动
- seller 参与返回 `403`
- buyer 首次参与成功
- 重复参与返回 `already claimed`
- 红包抢空后返回 `have been claimed`
- `COLLECT_CARD` 单卡活动首次参与返回 `fullSet=true`
- 同一 buyer 再次参与同一集卡活动返回 duplicate 提示
- `COLLECT_CARD` 历史记录中 `rewardStatus=SKIPPED`
- `VIRTUAL_FARM` 两次浇水后成熟
- `payload.action=HARVEST` 收获返回 `POINTS`
- `VIRTUAL_FARM` 历史记录中 2 条 `PROGRESS/SKIPPED` + 1 条最终奖励 `POINTS/PENDING`
- 同设备跨账号参与返回 `403`
- 同一 buyer 第 6 次突发参与返回 `429`

```bash
# 端口转发 activity-service（业务 + 管理端口）
kubectl -n shop port-forward svc/activity-service 28080:8080 28081:8081

# 读取内部调用 token
INTERNAL_TOKEN=$(kubectl -n shop get secret shop-shared-secret -o jsonpath='{.data.SHOP_INTERNAL_TOKEN}' | base64 --decode)

# 1) 创建一个单红包活动（买家角色会被拒绝）
curl -i -X POST http://127.0.0.1:28080/activity/v1/admin/games \
  -H "X-Internal-Token: ${INTERNAL_TOKEN}" \
  -H 'X-Roles: ROLE_ADMIN' \
  -H 'Content-Type: application/json' \
  -d '{"type":"RED_ENVELOPE","name":"Kind Red Envelope","config":"{\"packet_count\":1,\"total_amount\":5.00}","perUserDailyLimit":10,"perUserTotalLimit":10}'

# 2) 激活活动
curl -X POST http://127.0.0.1:28080/activity/v1/admin/games/<GAME_ID>/activate \
  -H "X-Internal-Token: ${INTERNAL_TOKEN}" \
  -H 'X-Roles: ROLE_ADMIN'

# 3) 通过 gateway 登录 buyer，获取 JWT
curl -X POST http://127.0.0.1:18080/auth/v1/token/login \
  -H 'Content-Type: application/json' \
  -d '{"username":"buyer.demo","password":"password","portal":"buyer"}'

# 4) buyer 首次参与成功
curl -X POST http://127.0.0.1:18080/api/activity/v1/games/<GAME_ID>/participate \
  -H 'Authorization: Bearer <BUYER_JWT>' \
  -H 'Content-Type: application/json' \
  -d '{"payload":null}'

# 5) 再次参与，返回 already claimed
curl -X POST http://127.0.0.1:18080/api/activity/v1/games/<GAME_ID>/participate \
  -H 'Authorization: Bearer <BUYER_JWT>' \
  -H 'Content-Type: application/json' \
  -d '{"payload":null}'

# 6) 同设备不同账号参与，返回 403（需要另一个 buyer JWT）
curl -i -X POST http://127.0.0.1:18080/api/activity/v1/games/<GAME_ID>/participate \
  -H 'Authorization: Bearer <BUYER_VIP_JWT>' \
  -H 'X-Device-Fingerprint: shared-device' \
  -H 'Content-Type: application/json' \
  -d '{"payload":null}'

# 7) 同一 buyer 连续 6 次突发参与，最后一次返回 429
for i in 1 2 3 4 5 6; do
  curl -s -o /dev/null -w '%{http_code}\n' \
    -X POST http://127.0.0.1:18080/api/activity/v1/games/<RATE_LIMIT_GAME_ID>/participate \
    -H 'Authorization: Bearer <BUYER_JWT>' \
    -H 'X-Device-Fingerprint: rate-limit-device' \
    -H 'Content-Type: application/json' \
    -d '{"payload":null}'
done

# 8) 创建单卡 CollectCard 活动（首次参与即 full set）
curl -X POST http://127.0.0.1:28080/activity/v1/admin/games \
  -H "X-Internal-Token: ${INTERNAL_TOKEN}" \
  -H 'X-Roles: ROLE_ADMIN' \
  -H 'Content-Type: application/json' \
  -d '{"type":"COLLECT_CARD","name":"Kind Collect Card","config":"{\"cards\":[{\"id\":\"card-mascot\",\"name\":\"Mascot\",\"rarity\":\"COMMON\",\"probability\":1.0}]}","perUserDailyLimit":3,"perUserTotalLimit":3}'

# 9) buyer 首次参与返回 fullSet=true，再次参与返回 duplicate 提示
curl -X POST http://127.0.0.1:18080/api/activity/v1/games/<COLLECT_GAME_ID>/participate \
  -H 'Authorization: Bearer <BUYER_JWT>' \
  -H 'Content-Type: application/json' \
  -d '{"payload":null}'

curl -X POST http://127.0.0.1:18080/api/activity/v1/games/<COLLECT_GAME_ID>/participate \
  -H 'Authorization: Bearer <BUYER_JWT>' \
  -H 'Content-Type: application/json' \
  -d '{"payload":null}'

# 10) 查看历史记录，确认 rewardStatus=SKIPPED 且 prizeSnapshot.prizeType=CARD
curl http://127.0.0.1:18080/api/activity/v1/games/<COLLECT_GAME_ID>/my-history \
  -H 'Authorization: Bearer <BUYER_JWT>'

# 11) 创建 VirtualFarm 活动
curl -X POST http://127.0.0.1:28080/activity/v1/admin/games \
  -H "X-Internal-Token: ${INTERNAL_TOKEN}" \
  -H 'X-Roles: ROLE_ADMIN' \
  -H 'Content-Type: application/json' \
  -d '{"type":"VIRTUAL_FARM","name":"Kind Virtual Farm","config":"{\"max_stage\":2,\"stage_progress\":50,\"water_progress\":50}","perUserDailyLimit":5,"perUserTotalLimit":5}'

# 12) 为 VirtualFarm 添加 harvest 奖励并激活活动
curl -X POST http://127.0.0.1:28080/activity/v1/admin/games/<FARM_GAME_ID>/prizes \
  -H "X-Internal-Token: ${INTERNAL_TOKEN}" \
  -H 'X-Roles: ROLE_ADMIN' \
  -H 'Content-Type: application/json' \
  -d '{"name":"Farm Points","type":"POINTS","value":20,"probability":1.0,"totalStock":-1,"displayOrder":0,"imageUrl":null,"couponTemplateId":null}'

curl -X POST http://127.0.0.1:28080/activity/v1/admin/games/<FARM_GAME_ID>/activate \
  -H "X-Internal-Token: ${INTERNAL_TOKEN}" \
  -H 'X-Roles: ROLE_ADMIN'

# 13) buyer 连续浇水两次，第二次返回 matured=true
curl -X POST http://127.0.0.1:18080/api/activity/v1/games/<FARM_GAME_ID>/participate \
  -H 'Authorization: Bearer <BUYER_JWT>' \
  -H 'Content-Type: application/json' \
  -d '{"payload":null}'

curl -X POST http://127.0.0.1:18080/api/activity/v1/games/<FARM_GAME_ID>/participate \
  -H 'Authorization: Bearer <BUYER_JWT>' \
  -H 'Content-Type: application/json' \
  -d '{"payload":null}'

# 14) 使用 payload.action=HARVEST 收获奖励
curl -X POST http://127.0.0.1:18080/api/activity/v1/games/<FARM_GAME_ID>/participate \
  -H 'Authorization: Bearer <BUYER_JWT>' \
  -H 'Content-Type: application/json' \
  -d '{"payload":"{\"action\":\"HARVEST\"}"}'

# 15) 查看历史记录，确认 2 条 PROGRESS/SKIPPED + 1 条 POINTS/PENDING
curl http://127.0.0.1:18080/api/activity/v1/games/<FARM_GAME_ID>/my-history \
  -H 'Authorization: Bearer <BUYER_JWT>'
```

### 5. mirrord 本地开发

使用 [mirrord](https://mirrord.dev) 将本地服务接入 Kind 集群网络，实现本地开发 + 远程调试：

```bash
# 安装 mirrord
brew install metalbear-co/mirrord/mirrord

# 在本地运行 order-service，拦截集群中的流量
./kind/mirrord-run.sh order-service
```

mirrord 配置文件：`kind/mirrord.json`

### 6. 清理

```bash
./kind/teardown.sh
# 或手动
kind delete cluster --name shop-kind
```

## 服务端口映射

| 服务 | 应用端口 | 管理端口 | Kind 默认访问方式 |
|------|---------|---------|----------------------|
| api-gateway | 8080 | 8081 | 8080 |
| auth-server | 8080 | 8081 | 8090 |
| loyalty-service | 8080 | 8081 | 8088 |
| activity-service | 8080 | 8081 | 8089 |
| search-service | 8080 | 8081 | 8091 |
| notification-service | 8080 | 8081 | 8092 |
| webhook-service | 8080 | 8081 | 8093 |
| subscription-service | 8080 | 8081 | 8094 |
| 其他服务 | 8080 | 8081 | 仅内部访问 |
| MySQL | 3306 | - | 3306 |
| Redis | 6379 | - | 6379 |
| Kafka | 9092 | - | 9092 |
| Meilisearch | 7700 | - | 7700 |
| Mailpit (Web UI) | 8025 | - | 8025 |
| Mailpit (SMTP) | 1025 | - | 1025 |
| Prometheus | 9090 | - | 9090 |

## 常见问题

### 服务启动失败

检查 Pod 是否 ready，并查看 MySQL 日志：

```bash
kubectl -n shop get pods
kubectl -n shop logs deployment/mysql --tail=100
```

### Kafka 连接超时

Kafka 启动较慢，等待 broker ready 并查看日志：

```bash
kubectl -n shop get pods -l app=kafka
kubectl -n shop logs deployment/kafka --tail=50
```

### 数据库迁移失败

检查服务启动日志中的 Flyway 输出：

```bash
kubectl -n shop logs deployment/profile-service --tail=200 | grep -i flyway
```

### Feature Toggle 改了但服务没刷新

优先检查：

```bash
kubectl -n shop get pods | grep spring-cloud-kubernetes-configuration-watcher
kubectl -n shop describe configmap search-service-feature-toggles
kubectl -n shop logs deployment/spring-cloud-kubernetes-configuration-watcher --tail=100
```

确认点：

- ConfigMap 带有 `spring.cloud.kubernetes.config=true` 标签
- `search-service` Service 带有 `boot.spring.io/actuator` 注解
- 开关文件不是通过 `subPath` 挂载
