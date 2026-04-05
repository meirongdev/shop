# Shop Platform — Monorepo 目录重组方案 2026

> 版本：1.0 | 日期：2026-04-04
> 状态：方案设计（待执行）
> 执行方式：由 agent 在 feature branch 上原子执行

---

## 一、背景与目标

当前仓库根目录有 30+ 平铺目录，前后端混杂，Java（Maven）/ Kotlin Multiplatform（Gradle）/ Node.js 三种构建体系交错排列。主要问题：

1. **认知负担**——新开发者或 agent 进入仓库时，无法快速区分后端服务、前端应用、基础设施、构建工具
2. **Agent skill 无法按技术栈拆分**——前后端代码在同一层级，无法为 Java 后端和 KMP 前端配置不同的 agent 行为
3. **维护边界不清晰**——Docker 配置、K8s 部署、脚本散落在与业务服务同级的位置
4. **与 2026 年 monorepo 最佳实践差距**——主流开源项目（Spring Cloud Samples、JHipster、Nx）均采用分层目录

---

## 二、当前结构（平铺）

```
shop/                                   # 30+ 目录平铺
├── pom.xml                             # Maven 根 POM
├── build.gradle.kts / settings.gradle.kts  # Gradle（KMP）
├── Makefile / Tiltfile                  # 开发入口
│
├── shop-common/                        # 共享库（6 个子模块）
├── shop-contracts/                     # API 契约
├── auth-server/                        # ─┐
├── api-gateway/                        #  │
├── buyer-bff/ / seller-bff/            #  │ 15 个后端服务（Maven）
├── marketplace-service/                #  │
├── order-service/                      #  │
├── ... (11 more *-service)             # ─┘
├── buyer-portal/                       # 前端 SSR（Kotlin, Maven）
├── kmp/                                # 前端 KMP（Gradle, 13 个子模块）
├── e2e-tests/                          # 前端 E2E（Node.js/Playwright）
├── architecture-tests/                 # ArchUnit
├── archetype-tests/                    # Archetype 验证
├── shop-archetypes/                    # Maven archetypes
├── k8s/ / kind/ / docker/ / scripts/   # 基础设施
├── docs/ / docs-site/                  # 文档
└── experiments/                        # 实验
```

---

## 三、目标结构（分层）

```
shop/
├── pom.xml                              # 根 POM（不动，module 路径更新）
├── build.gradle.kts / settings.gradle.kts  # Gradle（不动，projectDir 重映射）
├── Makefile / Tiltfile / CLAUDE.md      # 开发入口（不动，内容更新）
├── .mirrord/                            # mirrord 配置（不动）
│
├── shared/                              # ── 共享后端库 ──
│   ├── shop-common/                     #   multi-module starters
│   │   ├── shop-common-bom/
│   │   ├── shop-common-core/
│   │   ├── shop-starter-idempotency/
│   │   ├── shop-starter-resilience/
│   │   ├── shop-starter-api-compat/
│   │   └── shop-starter-feature-toggle/
│   └── shop-contracts/                  #   API path constants + event DTOs
│
├── services/                            # ── 所有后端微服务（Maven）──
│   ├── api-gateway/                     #   Edge/Gateway
│   ├── auth-server/                     #   认证中心
│   ├── buyer-bff/                       #   买家 BFF
│   ├── seller-bff/                      #   卖家 BFF
│   ├── profile-service/                 #   ─┐
│   ├── promotion-service/               #    │
│   ├── wallet-service/                  #    │
│   ├── marketplace-service/             #    │ Domain Services
│   ├── order-service/                   #    │
│   ├── search-service/                  #    │
│   ├── notification-service/            #    │
│   ├── loyalty-service/                 #    │
│   ├── activity-service/                #    │
│   ├── webhook-service/                 #    │
│   └── subscription-service/            #   ─┘
│
├── frontend/                            # ── 所有前端 ──
│   ├── buyer-portal/                    #   Kotlin SSR（Maven 模块，过渡态）
│   ├── kmp/                             #   KMP/Compose Multiplatform（Gradle）
│   │   ├── core/                        #     共享核心
│   │   ├── ui-shared/                   #     共享 UI 组件
│   │   ├── feature-marketplace/         #     ─┐
│   │   ├── feature-cart/                #      │
│   │   ├── feature-order/               #      │ Feature 模块
│   │   ├── feature-wallet/              #      │
│   │   ├── feature-profile/             #      │
│   │   ├── feature-promotion/           #      │
│   │   ├── feature-auth/                #     ─┘
│   │   ├── buyer-app/                   #     买家 WASM App
│   │   ├── seller-app/                  #     卖家 WASM App
│   │   ├── buyer-android-app/           #     买家 Android
│   │   └── seller-android-app/          #     卖家 Android
│   └── e2e-tests/                       #   Playwright E2E（Node.js）
│
├── platform/                            # ── 基础设施与部署 ──
│   ├── k8s/                             #   Kubernetes manifests
│   │   ├── apps/                        #     应用 Kustomize base + overlays
│   │   ├── infra/                       #     基础设施（Kafka, MySQL, Redis...）
│   │   ├── observability/               #     可观测（Grafana, Prometheus, Loki...）
│   │   └── namespace.yaml
│   ├── kind/                            #   Kind 集群配置
│   ├── docker/                          #   Dockerfiles + nginx 配置
│   │   ├── Dockerfile.module            #     Maven 服务通用 Dockerfile
│   │   ├── Dockerfile.fast              #     快速构建 Dockerfile
│   │   ├── Dockerfile.seller-portal     #     卖家 WASM Dockerfile
│   │   ├── Dockerfile.buyer-app         #     买家 WASM Dockerfile
│   │   ├── nginx-seller.conf
│   │   ├── nginx-buyer.conf
│   │   └── mysql-init/
│   └── scripts/                         #   构建/部署/验证脚本
│       ├── build-images.sh
│       ├── deploy-kind.sh
│       ├── local-cicd-modules.sh
│       ├── run-local-checks.sh
│       ├── validate-platform-assets.sh
│       └── ... (15+ 其他脚本)
│
├── tooling/                             # ── 构建质量与脚手架 ──
│   ├── architecture-tests/              #   ArchUnit 19 条规则（Maven）
│   ├── archetype-tests/                 #   Archetype 生成验证（Maven）
│   └── shop-archetypes/                 #   6 个 Maven archetypes
│       ├── gateway-service-archetype/
│       ├── auth-service-archetype/
│       ├── bff-service-archetype/
│       ├── domain-service-archetype/
│       ├── event-worker-archetype/
│       └── portal-service-archetype/
│
├── docs/                                # 内部文档（不动）
├── docs-site/                           # Docusaurus 公开文档站（不动）
└── experiments/                         # 实验（不动）
```

---

## 四、关键设计决策

### 4.1 不引入中间聚合 POM

根 POM 直接用路径引用子模块：

```xml
<modules>
  <module>shared/shop-common</module>
  <module>shared/shop-contracts</module>
  <module>services/auth-server</module>
  <module>services/api-gateway</module>
  <!-- ... -->
  <module>frontend/buyer-portal</module>
  <module>tooling/architecture-tests</module>
  <module>tooling/shop-archetypes</module>
  <module>tooling/archetype-tests</module>
</modules>
```

不创建 `services/pom.xml` 等聚合 POM。理由：

- 所有模块的 `<parent>` 仍指向根 POM，只需改 `<relativePath>`
- 避免引入新的 POM 文件和依赖层级
- `mvn -pl services/auth-server -am` 正常工作

### 4.2 Gradle include 路径不变

KMP 模块通过 `projectDir` 重映射，所有 `build.gradle.kts` 内的 `project(":kmp:core")` 引用零修改：

```kotlin
// settings.gradle.kts — 只加一行
include(":kmp:core")
// ... 所有 include 保持不变 ...

// 重映射文件系统位置
project(":kmp").projectDir = file("frontend/kmp")
```

### 4.3 Docker MODULE 参数拆分

当前 `Dockerfile.module` 用 `MODULE` 同时作为目录路径和 jar 名。模块移到 `services/` 后路径变了但 jar 名不变，需要拆分：

```dockerfile
ARG MODULE
ARG MODULE_DIR=services/${MODULE}

FROM maven:3.9-eclipse-temurin-25 AS builder
ARG MODULE_DIR
WORKDIR /workspace
COPY . .
RUN --mount=type=cache,target=/root/.m2 mvn -pl ${MODULE_DIR} -am clean package -DskipTests -q

FROM eclipse-temurin:25-jre
WORKDIR /app
ARG MODULE
ARG MODULE_DIR=services/${MODULE}
COPY --from=builder /workspace/${MODULE_DIR}/target/${MODULE}-0.1.0-SNAPSHOT.jar app.jar
# ... 其余不变
```

调用方传参：`docker build --build-arg MODULE=auth-server`（`MODULE_DIR` 自动推导），buyer-portal 等例外可传 `--build-arg MODULE_DIR=frontend/buyer-portal`。

### 4.4 保持不动的目录

| 目录 | 理由 |
|------|------|
| `docs/` | 已清晰，移动无收益 |
| `docs-site/` | Docusaurus 独立项目，已隔离 |
| `experiments/` | 一次性实验，无交叉引用 |
| `.mirrord/` | mirrord 配置引用 K8s deployment 名，不依赖文件路径 |

### 4.5 原子迁移策略

**不采用增量迁移**——增量迁移会产生中间态（部分模块在旧位置，部分在新位置），导致 `mvn verify` / 脚本 / CI 全部失败。

采用原子迁移：
1. 创建 feature branch
2. 一个 commit 完成所有 `git mv`（保留 git history）
3. 下一个 commit 完成所有文件内容修复
4. 验证通过后 merge

---

## 五、需要修改的文件清单（38 个文件）

### 5.1 构建配置（6 个文件）

| 文件 | 修改内容 |
|------|----------|
| `pom.xml`（根） | 所有 `<module>` 路径加前缀（`services/`、`shared/`、`frontend/`、`tooling/`） |
| `settings.gradle.kts` | 加 `project(":kmp").projectDir = file("frontend/kmp")` |
| `Makefile` | `ARCHETYPE_MODULES` 变量、所有 `scripts/` → `platform/scripts/`、`e2e-tests/` → `frontend/e2e-tests/`、`kind/` → `platform/kind/` |
| `Tiltfile` | `k8s/` → `platform/k8s/`、`watched_paths()` 函数、dockerfile 路径、MODULE build arg 加 `services/` 前缀 |
| `.dockerignore` | 所有 allow-list 路径更新 |
| `.github/workflows/ci.yml` | 脚本路径、Maven `-pl` 参数 |

### 5.2 Maven POM relativePath（21 个文件）

所有移入子目录的模块 `pom.xml` 的 `<relativePath>` 从 `../pom.xml` 改为 `../../pom.xml`：

- `services/` 下 15 个服务的 `pom.xml`
- `shared/shop-common/pom.xml`
- `shared/shop-contracts/pom.xml`
- `frontend/buyer-portal/pom.xml`
- `tooling/architecture-tests/pom.xml`
- `tooling/archetype-tests/pom.xml`
- `tooling/shop-archetypes/pom.xml`

**不需要修改的 POM：**
- `shop-common` 的 6 个子模块（`shop-common-core` 等）——它们的 parent 是 `shop-common`，相对位置不变
- `shop-archetypes` 的 6 个子 archetype——它们的 parent 是 `shop-archetypes`，相对位置不变
- archetype 内的 `archetype-resources/pom.xml`——这些是**模板**，不是本仓库的模块

### 5.3 Docker 文件（3 个文件）

| 文件 | 修改内容 |
|------|----------|
| `platform/docker/Dockerfile.module` | 引入 `MODULE_DIR` arg，分离目录路径和 artifact 名 |
| `platform/docker/Dockerfile.seller-portal` | `DIST_DIR` 默认值 `kmp/` → `frontend/kmp/`、nginx COPY 路径 `docker/` → `platform/docker/` |
| `platform/docker/Dockerfile.buyer-app` | 同 seller-portal |

### 5.4 脚本（8 个文件）

| 文件 | 修改内容 |
|------|----------|
| `platform/scripts/local-cicd-modules.sh` | `SHARED_PATHS`（`shop-common` → `shared/shop-common`）、`module_extra_paths()`（`kmp/` → `frontend/kmp/`、`docker/` → `platform/docker/`）、`module_jar_path()`（加 `services/` 前缀）、`detect_changed_modules()` 路径匹配逻辑 |
| `platform/scripts/build-images.sh` | `dist_dir` 路径（`kmp/` → `frontend/kmp/`）、gradlew 调用（`cd kmp` → 从根运行 `./gradlew`）、dockerfile 路径（`docker/` → `platform/docker/`） |
| `platform/scripts/kmp-e2e.sh` | WASM dist 目录路径 |
| `platform/scripts/deploy-kind.sh` | k8s manifest 路径（`k8s/` → `platform/k8s/`） |
| `platform/scripts/test-archetypes.sh` | Maven `-pl` 参数（加 `shared/`、`tooling/` 前缀） |
| `platform/scripts/validate-platform-assets.sh` | 所有 `scripts/`、`k8s/`、`docker/`、`kind/` 路径加 `platform/` 前缀 |
| `platform/scripts/run-local-checks.sh` | case 模式匹配 `docker/*\|k8s/*\|kind/*` → `platform/docker/*\|platform/k8s/*\|platform/kind/*` |
| `platform/scripts/smoke-test.sh` | 检查并更新路径引用 |

### 5.5 文档（1 个文件）

| 文件 | 修改内容 |
|------|----------|
| `CLAUDE.md` | 所有示例命令中的模块路径（`./mvnw -pl auth-server` → `./mvnw -pl services/auth-server`）、架构描述中的目录引用 |

---

## 六、`git mv` 命令清单

```bash
git checkout -b restructure/group-by-domain

# 创建目标目录
mkdir -p shared services frontend platform tooling

# ── 共享库 ──
git mv shop-common shared/shop-common
git mv shop-contracts shared/shop-contracts

# ── 后端服务（15 个）──
git mv auth-server services/auth-server
git mv api-gateway services/api-gateway
git mv buyer-bff services/buyer-bff
git mv seller-bff services/seller-bff
git mv profile-service services/profile-service
git mv promotion-service services/promotion-service
git mv wallet-service services/wallet-service
git mv marketplace-service services/marketplace-service
git mv order-service services/order-service
git mv search-service services/search-service
git mv notification-service services/notification-service
git mv loyalty-service services/loyalty-service
git mv activity-service services/activity-service
git mv webhook-service services/webhook-service
git mv subscription-service services/subscription-service

# ── 前端 ──
git mv buyer-portal frontend/buyer-portal
git mv kmp frontend/kmp
git mv e2e-tests frontend/e2e-tests

# ── 基础设施 ──
git mv k8s platform/k8s
git mv kind platform/kind
git mv docker platform/docker
git mv scripts platform/scripts

# ── 构建工具 ──
git mv architecture-tests tooling/architecture-tests
git mv archetype-tests tooling/archetype-tests
git mv shop-archetypes tooling/shop-archetypes

# 提交纯重命名（保留 git history）
git commit -m "refactor: restructure monorepo into shared/services/frontend/platform/tooling"
```

---

## 七、验证步骤

迁移完成后按顺序执行：

| 步骤 | 命令 | 验证目标 |
|------|------|----------|
| 1 | `./mvnw -q verify` | Maven 全量编译 + 测试通过 |
| 2 | `./gradlew :kmp:core:build` | Gradle projectDir 重映射正确 |
| 3 | `make platform-validate` | shell / Kustomize / Tilt / Dockerfile 路径正确 |
| 4 | `make arch-test` | ArchUnit 模块解析正确 |
| 5 | `make docs-build` | 文档站不受影响 |
| 6 | `make build-images` (单模块) | Docker 构建 MODULE_DIR 参数正确 |
| 7 | `make local-checks-all` | 本地全量检查通过 |

---

## 八、风险评估

| 风险 | 可能性 | 影响 | 缓解措施 |
|------|--------|------|----------|
| Maven 模块解析失败 | 中 | 高（阻断所有构建） | `git mv` 后立即 `mvn verify`，失败立即修复 |
| Dockerfile COPY 路径不匹配 | 中 | 中（阻断镜像构建） | 拆分 MODULE/MODULE_DIR 参数，测试单模块构建 |
| Gradle 项目重映射失败 | 低 | 中（阻断 KMP 构建） | 单行 `projectDir` 修改，立即验证 |
| Git history 丢失 | 低 | 低（仅影响 blame） | 全部使用 `git mv`，不手动 move + add |
| CI path filter 遗漏 | 低 | 低（CI 跑多而非跑少） | 当前 filter 用 `**/*.java` 通配，不受目录影响 |
| 脚本自引用路径断裂 | 低 | 中 | 所有脚本用 `$(dirname "${BASH_SOURCE[0]}")` 动态解析，不受影响 |
| 开发者 / agent 肌肉记忆 | 中 | 低 | 更新 CLAUDE.md + AGENTS.md，Makefile targets 名称不变 |

---

## 九、Agent Skill 分离收益

重组完成后，可以为前后端配置不同的 agent 行为：

### 9.1 后端 Agent Skill（`services/` + `shared/`）

- 语言：Java 25
- 构建：Maven
- 测试：JUnit 5 + Testcontainers + WireMock + ArchUnit
- 格式化：Spotless (Google Java Format)
- 规范：构造器注入、`RestClient`、`ApiResponse<T>`、Outbox Pattern
- 禁止模式：ArchUnit 19 条规则

### 9.2 前端 Agent Skill（`frontend/kmp/`）

- 语言：Kotlin Multiplatform
- 构建：Gradle + Compose Multiplatform
- 目标平台：WASM (Web) + Android + iOS
- UI 框架：Compose Multiplatform
- 网络：Ktor Client
- 状态管理：Kotlin Coroutines + Flow
- 测试：Kotlin Test

### 9.3 前端 E2E Skill（`frontend/e2e-tests/`）

- 语言：TypeScript
- 框架：Playwright
- 运行条件：需要 Kind 集群 + port-forward

### 9.4 平台 Agent Skill（`platform/`）

- 语言：Shell / YAML
- 工具：Kustomize、Kind、Tilt、mirrord、ArgoCD
- 关注点：K8s manifest、Docker 构建、CI/CD 脚本

---

## 十、与现有文档的关系

| 文档 | 影响 |
|------|------|
| `CLAUDE.md` | 需要更新所有路径引用 |
| `docs/DEVELOPER-EXPERIENCE-STANDARD-2026.md` | 需要更新 Makefile target 说明中的路径 |
| `docs/ENGINEERING-STANDARDS-2026.md` | 路径引用少，影响小 |
| `docs/ARCHITECTURE-DESIGN.md` | 服务拓扑不变，可能需要更新目录引用 |
| `docs/HARNESS-ENGINEERING-ROADMAP-2026.md` | 需要补充"重组后 agent skill 拆分"部分 |
| `docs/ARCHUNIT-RULES.md` | 不受影响（ArchUnit 分析的是 class path，不是 file path） |
| `docs-site/` 内容 | 需要审查是否引用了模块文件路径 |

---

## 十一、执行建议

1. **在 feature branch 上执行**——不在 main 上直接操作
2. **先 `git mv`，再修复内容**——分两个 commit，便于 review
3. **CI 验证后再 merge**——确保 `maven-verify` + `archetype-test` + `docs-site` + `platform-validate` 全部通过
4. **通知团队**——这是一个破坏性变更，所有开发者的本地分支需要 rebase
5. **优先使用 worktree**——建议执行 agent 在 git worktree 中操作，避免影响主工作区
