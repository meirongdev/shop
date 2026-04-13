---
sidebar_position: 3
title: 本地部署
---

# 本地部署指南

## 前置要求

- Docker Desktop 或 [OrbStack](https://orbstack.dev/)（macOS 推荐）
- [Kind](https://kind.sigs.k8s.io/)
- kubectl
- （可选）[mirrord](https://mirrord.dev/) — IDE 调试
- （可选）[Tilt](https://tilt.dev/) — 热更新内循环

## 唯一支持的本地运行方式：Kind / Kubernetes

当前仓库不再维护 `docker-compose.yml` 路径；本地开发、联调、热更新验证统一以 Kind + Kubernetes 清单为准。

### 1. 一键部署（推荐）

```bash
make e2e
```

此命令会自动：创建 Kind 集群 → 构建变更模块镜像 → 推送到本地 registry → 部署基础设施与平台服务 → 并行等待所有 16 个部署就绪 → 运行冒烟测试 + Buyer UI 验证。

**预期耗时**（.m2 / Docker 层缓存热的情况下）：

| 阶段 | 耗时 |
|------|------|
| Kind 集群 + 基础设施就绪 | ~1 min |
| Maven clean package（16 模块，`-T 1C` 并行） | ~25–35s |
| Docker 镜像构建（4 并行，`Dockerfile.fast` 2-stage） | ~30–60s |
| Registry push（4 并行，增量层） | ~30–60s |
| K8s 滚动部署（16 服务并行等待） | ~90–120s |
| 冒烟测试 + Buyer UI e2e | ~20s |
| **总计** | **~4–5 min** |

> 首次运行（.m2 为空 / Docker 层缺失）耗时约 10–20 分钟，取决于网速。

> `./kind/setup.sh` 与 `make e2e` 等价，是同一入口的别名。
> `make e2e` 会为 Meilisearch 注入符合生产模式要求的 16+ 字节密钥，并在 MySQL 首次启动时初始化各服务所需的数据库与授权。
> 结束时会打印各阶段耗时，便于排查慢点。

### 2. 清理环境

```bash
# 删除 Kind 集群（保留本地 Docker 镜像缓存）
make kind-teardown

# 删除集群 + 清理所有本地 shop/* 开发镜像
make clean-all
```

### 3. 分步部署

```bash
# 构建所有 Docker 镜像（镜像内部会完成 Maven 打包）
./scripts/build-images.sh

# 仅构建相对 origin/main（或 --base 指定基线）发生变化的模块
./scripts/build-images.sh --changed -j 4

# 仅构建单个模块（快速）
./scripts/build-images.sh --fast --module buyer-bff

# 创建 Kind 集群
kind create cluster --name shop-kind --config kind/cluster-config.yaml

# 加载镜像到集群（fast path：registry sync）
./scripts/load-images-kind.sh shop-kind --registry

# 仅加载发生变化的模块镜像
./scripts/load-images-kind.sh shop-kind --changed --registry

# 仅加载单个模块镜像
./scripts/load-images-kind.sh shop-kind --registry --module buyer-bff

# 部署 K8s 清单
kubectl apply -f k8s/namespace.yaml
kubectl apply -f k8s/infra/base.yaml
./scripts/deploy-kind.sh dev

# 推荐本地链路
# 1. make registry（首次或重建 Kind 后执行一次）
# 2. make e2e（host Maven build + registry push + sequential wave deploy）
# 3. 验证入口：make local-access、make smoke-test、make ui-e2e

# 只验证平台资产（shell / overlay / mirrord / Tiltfile）
make platform-validate
```

## 日常开发工作流

集群已启动后，根据开发场景选择最快的路径：

| 场景 | 命令 | 说明 |
|------|------|------|
| 修改某个服务，快速重部署 | `make redeploy MODULE=buyer-bff` | 构建单模块 → 加载镜像 → 滚动重启（~30s） |
| 批量重部署所有变更模块 | `make build-changed && make load-changed` | 基于 git diff 识别变更模块 |
| IDE 断点调试（无需 rebuild） | `make mirrord-run MODULE=buyer-bff` | 本地 JVM 挂到 Kind 集群，秒级启动 |
| 高频改代码 + 自动热更新 | `make tilt-up` | Tilt 监听文件变化自动重建 |

### make redeploy — 单模块快速重部署

修改某个服务后，无需重建整个集群，直接：

```bash
# 修改代码后（约 30 秒完成：host Maven package → docker build → push registry → rollout restart）
make redeploy MODULE=buyer-bff

# 等价的分步操作
./scripts/build-images.sh --fast --module buyer-bff
./scripts/load-images-kind.sh shop-kind --registry --module buyer-bff
kubectl -n shop rollout restart deployment/buyer-bff
kubectl -n shop rollout status deployment/buyer-bff --timeout=300s
```

验证：

```bash
kubectl -n shop logs -f deployment/buyer-bff
make smoke-test
```

## 3.1 更快的镜像循环与内循环开发

如果你频繁修改 `api-gateway`、`buyer-bff`、`marketplace-service`，可以启用本地 registry 与 Tilt：

```bash
# 首次启用 localhost:5000 mirror 后，建议删除并重建 Kind 集群
make registry

# 先安装 Tilt：brew install tilt
make tilt-up
```

`Tiltfile` 会持续 watch 这三个核心服务及共享模块，并在 Kind 集群中自动重建/重部署。

**扩展 Tilt 到其他服务**：将你当前正在开发的模块加入 `Tiltfile` 的 `TILT_SERVICES` 数组，修改后热更新只需几秒。

## 3.2 使用 mirrord 做本地调试（推荐：零 rebuild）

当你不想 rebuild image / reload Pod，只想把**本地进程**挂到 Kind 集群里的现有 Deployment 上做断点调试时，可以用 mirrord。

安装：

```bash
brew install metalbear-co/mirrord/mirrord
```

最简单的方式：

```bash
make mirrord-run MODULE=api-gateway
```

这会等价于：

```bash
./scripts/mirrord-debug.sh api-gateway
```

默认行为：

- 目标是 `shop` namespace 下的 `deployment/<module>`
- 本地启动命令是 `./mvnw -pl <module> -am spring-boot:run`
- 如果仓库里存在 `.mirrord/mirrord.<module>.json`，脚本会自动带上该配置

**IDE 远程调试（断点）**：

```bash
./scripts/mirrord-debug.sh buyer-bff -- \
  ./mvnw -pl buyer-bff -am spring-boot:run \
  -Dspring-boot.run.jvmArguments="-agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=5005"
# 然后在 IntelliJ/VS Code 中 "Remote JVM Debug" 连接 localhost:5005
```

如果你用 IntelliJ / VS Code 的 mirrord 插件，也可以直接复用仓库内的 `.mirrord/` 示例配置。

## 3.3 可选：ArgoCD GitOps 验证

如果你想在本地演练 declarative sync / drift detection：

```bash
make argocd-bootstrap
```

默认 Application 指向 `k8s/apps/overlays/dev`。如需切换仓库地址或 revision，可在执行前设置 `ARGOCD_REPO_URL`、`ARGOCD_TARGET_REVISION`。

## 3.4 构建加速与内循环优化（2026 推荐）

以下优化已内置/默认开启，**无需额外配置**：

### 1) Maven `-T 1C` 并行编译（已内置）
仓库根目录的 `.mvn/maven.config` 已全局配置 `-T 1C --no-transfer-progress`，对所有 `./mvnw` 调用生效。16 模块约 25–35s（缓存热）。

### 2) 增量构建与并行化
- **增量构建**：始终优先使用 `make build-changed`（或 `scripts/build-images.sh --changed`）。它通过 `git diff` 自动识别受影响的模块，避免重复构建未变动的镜像。
- **并行 Docker 构建**：`build-images.sh` 默认支持 `-j` 参数，默认 `-j 4`。

### 3) 2-stage `Dockerfile.fast`（已内置）
- Stage 1：包含 JRE + 系统依赖的缓存基础层（几乎不变化）
- Stage 2：仅 COPY 应用 JAR
- 首次推送后，后续每个镜像只上传 ~50–130 MB 的应用层；基础层（~200 MB）只上传一次。

### 4) Registry push 并行化（已内置）
`load-images-kind.sh` 默认 4 并行 push，16 个镜像 ~30–60s。

### 5) 迁移到 OrbStack (macOS 推荐)
- 如果你还在使用 Docker Desktop，建议迁移到 **OrbStack**。
- **优势**：文件系统同步速度极快，内存占用极低，且能原生支持 Kind 和本项目的所有 `make` 脚本。

### 6) 扩展 Tilt Live Update
- `Tiltfile` 默认只为 3 个核心服务开启了 `live_update`。
- **进阶操作**：将你当前正在开发的模块（如 `order-service`）加入 `Tiltfile` 的 `TILT_SERVICES` 数组。
- **效果**：修改代码后，Tilt 会直接同步编译后的 `.jar` 到运行中的容器并重启应用，**跳过镜像构建与推送**，热更新仅需几秒。

### 7) 专注模式（Focus Mode）
- 如果你只关注某个业务链路（如"活动参与"），可以临时修改 `k8s/apps/overlays/dev/kustomization.yaml`，注释掉不相关的服务。
- **效果**：减少 Kind 的 CPU/内存压力，显著提升活跃服务的响应速度和构建效率。
### 4. 访问与基本验证

已验证的稳定访问路径是先跑完整集群，再通过 `make local-access` 建立端口转发：

```bash
# 终端 A
make e2e

# 终端 B（保持运行）
make local-access
```

- **Buyer Portal (SSR)**: [http://127.0.0.1:18080/buyer/login](http://127.0.0.1:18080/buyer/login)
- **Buyer App (KMP WASM)**: [http://127.0.0.1:18080/buyer-app/](http://127.0.0.1:18080/buyer-app/)
- **Seller Portal (KMP WASM)**: [http://127.0.0.1:18080/seller/](http://127.0.0.1:18080/seller/)
- **Guest Checkout**: [http://127.0.0.1:18080/buyer/guest/track](http://127.0.0.1:18080/buyer/guest/track)
- **Gateway OpenAPI**: [http://127.0.0.1:18080/v3/api-docs/gateway](http://127.0.0.1:18080/v3/api-docs/gateway)
- **Mailpit（邮件）**: [http://127.0.0.1:18025](http://127.0.0.1:18025)
- **Prometheus**: [http://127.0.0.1:19090](http://127.0.0.1:19090)
- **Grafana**: [http://127.0.0.1:13000](http://127.0.0.1:13000)（Logs / Traces / Metrics 统一看板）

```bash
kubectl -n shop get pods
```

`make smoke-test` 也已经和这条路径对齐：当 `localhost:8080` 无法稳定访问时，会自动临时 `port-forward` gateway，再执行 smoke。

如果你只想手工转发某个入口，也可以直接使用：

```bash
kubectl -n shop port-forward svc/api-gateway 18080:8080
kubectl -n shop port-forward svc/mailpit 18025:8025
kubectl -n shop port-forward svc/prometheus 19090:9090
```

> 在 Kind + Cilium（尤其 macOS + OrbStack）环境下，NodePort/hostPort 映射可能能建立 TCP 连接，但并不一定稳定返回业务响应。因此本文档把 `make local-access` 作为权威访问方式，而不是继续依赖 `localhost:8080` / `8025` / `9090` 直连。
> 通过网关访问 `buyer-bff`、`subscription-service`、`webhook-service` 等路由时，需要保留服务自身的基础路径，例如：`/api/buyer/v1/...`、`/api/subscription/v1/...`、`/api/webhook/v1/...`。Gateway 只会剥离最前面的 `/api` 段。
> Redis 在 Kind 路径中仍然是必选基础设施：guest cart、限流、OTP、防作弊、Bloom Filter 幂等以及 Redisson 分布式锁都会依赖它。
> 下文所有通过 gateway 的 `curl http://127.0.0.1:18080/...` 示例，都默认你已经在另一个终端运行了 `make local-access`。
> `make e2e` 走 host Maven build + registry push + sequential wave deploy。`make ui-e2e` / `make e2e` 的 seller Web 校验需要本机存在可执行的 Chrome / Chromium；脚本会优先查找 `CHROME_BIN`、`google-chrome`、`chromium`，在 macOS 上也会自动尝试 `/Applications/Google Chrome.app/Contents/MacOS/Google Chrome`。

### 4.1 手工预览 Seller Web（KMP WASM）

如果你想手工打开 seller Web，而不仅仅依赖自动化校验：

```bash
# 终端 A
make local-access

# 终端 B
(cd frontend && ./gradlew :kmp:seller-app:wasmJsBrowserDevelopmentExecutableDistribution)
node ./platform/scripts/seller-web-proxy.mjs \
  frontend/kmp/seller-app/build/dist/wasmJs/developmentExecutable \
  http://127.0.0.1:18080 \
  18181
```

然后访问：

- **Seller Web (KMP WASM)**: [http://127.0.0.1:18181/](http://127.0.0.1:18181/)
- 使用演示账号 `seller.demo / password`

### 4.2 Playwright E2E 自动化测试

`e2e-tests/` 目录包含基于 Playwright 的浏览器自动化测试，覆盖 Buyer Portal、Buyer App (KMP WASM) 和 Seller App 的主要页面。

```bash
# 首次安装依赖（仅需一次）
cd e2e-tests && npm install && npx playwright install chromium

# 运行 Buyer 测试（需 make local-access 保持运行）
# 包含：SSR 登录/访客/认证页面 + Buyer App WASM SPA shell 验证
make local-access &
make e2e-playwright

# 运行 Seller 测试（自动构建 WASM 并启动代理）
# 包含：Seller App 交互测试 + Seller Portal 网关 SPA shell 验证
make e2e-playwright-seller

# 直接运行全部测试
cd e2e-tests && npx playwright test

# 查看 HTML 报告
cd e2e-tests && npx playwright show-report
```

**Demo 账号**：`buyer.demo / password`（Buyer）、`seller.demo / password`（Seller）.

### 4.2 验证 Search Service Feature Toggle 热更新


当前仓库在 Kind 下使用：

- `search-service-feature-toggles` ConfigMap
- Spring Cloud Kubernetes Configuration Watcher
- `search-service` 的 `/actuator/refresh`


验证建议：

```bash
# 端口转发 search-service（业务 + 管理端口）
kubectl -n shop port-forward svc/search-service 18091:8080 18092:8081

# 读取内部调用 token

# 1) 确认 autocomplete 当前可用
curl \
  "http://127.0.0.1:18091/search/v1/products/suggestions?q=hair"

# 2) 关闭 autocomplete
kubectl -n shop patch configmap search-service-feature-toggles \
  --type merge \
  -p '{"data":{"feature-toggles.yaml":"shop:\n  features:\n    flags:\n      search-autocomplete: false\n      search-trending: true\n      search-locale-aware: true\n"}}'

# 3) 等待 watcher 触发 refresh（当前 Kind 建议等待约 40~50 秒）后再次访问，应返回 503
curl -i \
  "http://127.0.0.1:18091/search/v1/products/suggestions?q=hair"

# 4) 恢复开关
kubectl -n shop patch configmap search-service-feature-toggles \
  --type merge \
  -p '{"data":{"feature-toggles.yaml":"shop:\n  features:\n    flags:\n      search-autocomplete: true\n      search-trending: true\n      search-locale-aware: true\n"}}'
```

> 注意：需要热更新的 ConfigMap 文件必须以**目录挂载**方式注入 Pod，不能使用 `subPath`，否则 K8s 不会把更新后的文件内容投影到容器内。

### 4.1.1 验证 Search Service 默认排序与显式排序

当前仓库已在 Kind 中验证：

- 默认搜索结果会优先返回库存更充足的商品（`inventory:desc` custom ranking baseline）
- 显式 `sort=priceInCents:asc` 仍会覆盖默认库存优先顺序
- `POST /search/v1/products/_reindex` 会等待 Meilisearch swap / delete 任务完成后再返回

```bash
# 分别端口转发 marketplace-service 与 search-service
kubectl -n shop port-forward svc/marketplace-service 38080:8080
kubectl -n shop port-forward svc/search-service 38091:8080 38092:8081

STAMP=$(date +%s)
QUERY="ranking-smoke-${STAMP}"

# 1) 创建两条同名商品：A 更便宜但缺货，B 更贵但有库存
curl \
  -H 'Content-Type: application/json' \
  -X POST http://127.0.0.1:38080/marketplace/v1/product/create \
  -d "{\"sellerId\":\"seller-smoke\",\"sku\":\"RANK-${STAMP}-A\",\"name\":\"${QUERY}\",\"description\":\"search ranking smoke A\",\"price\":9.99,\"inventory\":0,\"published\":true}"

curl \
  -H 'Content-Type: application/json' \
  -X POST http://127.0.0.1:38080/marketplace/v1/product/create \
  -d "{\"sellerId\":\"seller-smoke\",\"sku\":\"RANK-${STAMP}-B\",\"name\":\"${QUERY}\",\"description\":\"search ranking smoke B\",\"price\":29.99,\"inventory\":30,\"published\":true}"

# 2) 触发全量重建索引；返回 SC_OK 后即可直接继续查询
curl \
  -X POST http://127.0.0.1:38091/search/v1/products/_reindex

# 3) 默认搜索：应先返回有库存商品
curl --get \
  --data-urlencode "q=${QUERY}" \
  http://127.0.0.1:38091/search/v1/products

# 4) 显式价格升序：应先返回更便宜商品
curl --get \
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

# 1) 创建一个单红包活动（买家角色会被拒绝）
curl -i -X POST http://127.0.0.1:28080/activity/v1/admin/games \
  \
  -H 'X-Roles: ROLE_ADMIN' \
  -H 'Content-Type: application/json' \
  -d '{"type":"RED_ENVELOPE","name":"Kind Red Envelope","config":"{\"packet_count\":1,\"total_amount\":5.00}","perUserDailyLimit":10,"perUserTotalLimit":10}'

# 2) 激活活动
curl -X POST http://127.0.0.1:28080/activity/v1/admin/games/<GAME_ID>/activate \
  \
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
  \
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
  \
  -H 'X-Roles: ROLE_ADMIN' \
  -H 'Content-Type: application/json' \
  -d '{"type":"VIRTUAL_FARM","name":"Kind Virtual Farm","config":"{\"max_stage\":2,\"stage_progress\":50,\"water_progress\":50}","perUserDailyLimit":5,"perUserTotalLimit":5}'

# 12) 为 VirtualFarm 添加 harvest 奖励并激活活动
curl -X POST http://127.0.0.1:28080/activity/v1/admin/games/<FARM_GAME_ID>/prizes \
  \
  -H 'X-Roles: ROLE_ADMIN' \
  -H 'Content-Type: application/json' \
  -d '{"name":"Farm Points","type":"POINTS","value":20,"probability":1.0,"totalStock":-1,"displayOrder":0,"imageUrl":null,"couponTemplateId":null}'

curl -X POST http://127.0.0.1:28080/activity/v1/admin/games/<FARM_GAME_ID>/activate \
  \
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

# 推荐：使用统一入口
make mirrord-run MODULE=order-service

# 或直接指定本地启动命令
./scripts/mirrord-debug.sh order-service -- ./mvnw -pl order-service -am spring-boot:run
```

兼容入口 `./kind/mirrord-run.sh <service>` 仍保留，但内部已经委托给 `scripts/mirrord-debug.sh`。

### 6. 清理

```bash
./kind/teardown.sh
# 或手动
kind delete cluster --name shop-kind
```

## 服务端口与推荐本地访问方式

| 服务 | 应用端口 | 管理端口 | 已验证的本地访问方式 |
|------|---------|---------|----------------------|
| api-gateway | 8080 | 8081 | `make local-access` → `127.0.0.1:18080` |
| buyer-portal (SSR) | 8080 | 8081 | 通过 gateway 访问：`127.0.0.1:18080/buyer/...` |
| buyer-app (KMP WASM) | 80 | - | 通过 gateway 访问：`127.0.0.1:18080/buyer-app/` |
| seller-portal (KMP WASM) | 80 | - | 通过 gateway 访问：`127.0.0.1:18080/seller/` |
| Mailpit (Web UI) | 8025 | - | `make local-access` → `127.0.0.1:18025` |
| Prometheus | 9090 | - | `make local-access` → `127.0.0.1:19090` |
| 其他 HTTP 服务 | 8080 | 8081 | `kubectl -n shop port-forward svc/<service> <local>:8080` |
| MySQL | 3306 | - | 仅集群内；需要时自行 `kubectl port-forward` |
| Redis | 6379 | - | 仅集群内；需要时自行 `kubectl port-forward` |
| Kafka | 9092 | - | 仅集群内；需要时自行 `kubectl port-forward` |
| Meilisearch | 7700 | - | 仅集群内；需要时自行 `kubectl port-forward` |
| Mailpit (SMTP) | 1025 | - | 仅集群内；需要时自行 `kubectl port-forward` |

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
