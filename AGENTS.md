# AGENTS.md — Coding Agent Quality Gates

> 版本：1.0 | 日期：2026-04-04
> 适用范围：所有 coding agents（Claude Code, Qwen Code, Cursor, Copilot 等）

---

## 一、Agent 类型与技术栈映射

本仓库使用 monorepo 结构，不同目录对应不同技术栈。Agent 应根据文件路径自动识别适用的质量门禁。

| Agent 类型 | 目录范围 | 技术栈 | 质量门禁 |
|-----------|---------|--------|---------|
| **Java 后端** | `services/`, `shared/`, `tooling/` | Java 25, Spring Boot 3.5, Maven | ArchUnit, Maven verify, spotless |
| **Kotlin 前端** | `frontend/kmp/` | Kotlin 2.3, Compose Multiplatform, Gradle | Kotlin compile, Gradle test |
| **前端 E2E** | `frontend/e2e-tests/` | TypeScript, Playwright | Playwright test, tsc |
| **平台工程** | `platform/` | Shell, YAML (K8s/Docker/Tilt) | shellcheck, kustomize, tilt |
| **文档** | `docs/`, `docs-site/` | Markdown, Docusaurus | docs-build, link check |

---

## 二、全局质量门禁（所有 Agent 必须遵守）

### 2.1 提交前检查（Pre-commit）

所有 agent 在执行代码修改后，提交前必须通过以下检查：

```bash
# 自动触发的检查（由 .githooks/pre-commit 执行）
# 1. 合并冲突标记检测
# 2. 尾随空格检测（Java/Kotlin/XML/YAML/Shell）
# 3. 大文件检测（>1MB 警告）
# 4. Shell 脚本语法验证
```

### 2.2 推送前检查（Pre-push）

所有 agent 在推送到远程前必须通过：

```bash
make local-checks        # 相对于 origin/main 的变更检查
# 或
make local-checks-all    # 全量检查（忽略变更检测）
```

这会运行：
- Maven verify（Java/Kotlin 文件变更时）
- 文档构建（docs-site/ 变更时）
- 平台验证（k8s/docker/Tiltfile 变更时）

### 2.3 CI 门禁（Push/PR 时自动触发）

| Job | 触发条件 | 验证内容 |
|-----|---------|---------|
| `platform-validate` | `platform/`, `Tiltfile`, `.mirrord/` | shell/Kustomize/Tilt/mirrord 一致性 |
| `maven-verify` | `**/*.java`, `**/*.kt`, `pom.xml` | Maven verify（编译 + 测试） |
| `archetype-test` | `shop-archetypes/` 变更 | Archetype 生成 + 编译 + 测试 |
| `docs-site` | `docs-site/` 变更 | Docusaurus 构建 |

---

## 三、Agent 专属质量门禁

### 3.1 Java 后端 Agent（`services/`, `shared/`, `tooling/`）

**必须遵守的规则：**

1. **ArchUnit 19 条规则**（见 `docs/ARCHUNIT-RULES.md`）
   - ❌ 禁止字段注入（必须使用构造器注入）
   - ❌ 禁止使用 `RestTemplate`（使用 `RestClient`）
   - ❌ 禁止 `System.out/err`（使用 SLF4J）
   - ❌ 禁止 `catch (Exception e)`（收窄异常类型）
   - ✅ Kafka 监听器必须幂等

2. **API 契约**
   - ✅ 所有 DTO 使用 `record` 类型
   - ✅ 所有 Controller 返回 `ApiResponse<T>`
   - ✅ 错误码使用 `SC_*` 常量（来自 `shop-contracts`）
   - ✅ API 路径使用 `*Api.java` 常量

3. **可观测性**
   - ✅ 所有服务必须暴露 `/actuator/prometheus`
   - ✅ 业务指标使用 `shop_` 前缀
   - ✅ 关键操作必须记录 Counter/Timer 指标

4. **弹性治理**
   - ✅ BFF 层下游调用必须使用 `ResilienceHelper`
   - ✅ 配置 CircuitBreaker + Retry + Bulkhead + TimeLimiter

**提交前验证命令：**

```bash
# 快速编译检查
./mvnw compile -pl <module> -am -DskipTests -q

# 运行模块测试
./mvnw test -pl <module> -am

# 运行架构测试
make arch-test
```

### 3.2 Kotlin 前端 Agent（`frontend/kmp/`）

**必须遵守的规则：**

1. **构建规范**
   - ✅ 使用 `./gradlew` 从 `frontend/` 目录执行
   - ✅ 模块引用使用 `project(":kmp:module-name")`
   - ✅ WASM 构建输出到 `frontend/kmp/<app>/build/dist/wasmJs/`

2. **代码规范**
   - ✅ 使用 Compose Multiplatform API
   - ✅ 网络请求使用 Ktor Client
   - ✅ 状态管理使用 Kotlin Coroutines + Flow

**提交前验证命令：**

```bash
# Kotlin 编译检查
(cd frontend && ./gradlew :kmp:core:compileKotlinWasmJs --quiet)

# 运行测试
(cd frontend && ./gradlew :kmp:core:test)
```

### 3.3 前端 E2E Agent（`frontend/e2e-tests/`）

**必须遵守的规则：**

1. **测试规范**
   - ✅ 使用 `@playwright/test` 框架
   - ✅ 测试文件按项目组织（`tests/buyer/`, `tests/seller/`, `tests/buyer-app/`）
   - ✅ 使用 E2E token 注入进行认证

2. **运行条件**
   - ⚠️ 需要 Gateway 在 18080 端口运行（`make local-access &`）
   - ⚠️ Seller 测试需要 seller proxy 在 18181 端口

**提交前验证命令：**

```bash
# 运行单个项目测试
npx playwright test --project=buyer
npx playwright test --project=seller
npx playwright test --project=buyer-app

# 运行所有测试
npx playwright test
```

### 3.4 平台工程 Agent（`platform/`）

**必须遵守的规则：**

1. **Shell 脚本规范**
   - ✅ 所有脚本以 `#!/usr/bin/env bash` 开头
   - ✅ 使用 `set -euo pipefail`
   - ✅ 使用 `$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)` 获取脚本目录
   - ✅ 路径引用使用相对于仓库根目录的路径

2. **K8s 清单规范**
   - ✅ 使用 Kustomize 管理配置
   - ✅ Overlay 结构：`base/` → `overlays/dev/`
   - ✅ 所有 Deployment 必须包含 `startupProbe`

3. **Docker 规范**
   - ✅ 使用多阶段构建
   - ✅ 非 root 用户运行
   - ✅ 健康检查端点配置

**提交前验证命令：**

```bash
# 平台资产验证
make platform-validate

# 本地全量检查
make local-checks-all
```

---

## 四、Git Hooks 配置

### 4.1 安装 Hooks

```bash
make install-hooks
# 或手动执行
git config core.hooksPath .githooks
```

### 4.2 Hooks 执行流程

```
git commit
  └→ .githooks/pre-commit
       ├→ [1/4] 合并冲突标记检测
       ├→ [2/4] 尾随空格检测
       ├→ [3/4] 大文件检测（>1MB 警告）
       └→ [4/4] Shell 脚本语法验证

git push
  └→ .githooks/pre-push
       └→ platform/scripts/run-local-checks.sh --since-main
            ├→ 检测变更文件类型
            ├→ Java/Kotlin 变更 → Maven verify
            ├→ docs-site 变更 → 文档构建
            └→ 平台文件变更 → 平台验证
```

### 4.3 自定义 Hooks 行为

```bash
# 跳过推送前检查（危险，仅用于紧急修复）
export HOOK_LOCAL_CHECK_MODE=--skip
git push

# 运行全量检查（不限于 origin/main）
export HOOK_LOCAL_CHECK_MODE=--all
git push
```

---

## 五、CI/CD Quality Gate

### 5.1 GitHub Actions 工作流

```yaml
# .github/workflows/ci.yml
jobs:
  changes:          # 路径过滤检测
  platform-validate # shell/Kustomize/Tilt 验证
  maven-verify      # Maven 全量验证
  archetype-test    # Archetype 生成测试
  docs-site         # 文档站构建
```

### 5.2 本地 CI 等价命令

```bash
# 等价于 CI 的 maven-verify job
./mvnw -B -ntp verify

# 等价于 CI 的 archetype-test job
./mvnw -pl shared/shop-common,shared/shop-contracts,tooling/shop-archetypes -am install -B -ntp
./mvnw -pl tooling/archetype-tests test -B -ntp

# 等价于 CI 的 platform-validate job
bash platform/scripts/validate-platform-assets.sh

# 等价于 CI 的 docs-site job
cd docs-site && npm ci && npm run build
```

---

## 六、常见错误与修复

### 6.1 Maven 模块解析失败

**症状：** `Could not find the selected project in the reactor`

**原因：** 模块路径不正确或 `pom.xml` 中 `<relativePath>` 错误

**修复：**
```bash
# 检查模块是否在 reactor 中
./mvnw help:effective-pom | grep -A1 "<module>"

# 验证 relativePath
cat services/<module>/pom.xml | grep relativePath
# 应该是 ../../pom.xml（对于 services/ 下的模块）
```

### 6.2 Gradle 项目找不到

**症状：** `Project with path ':kmp:core' could not be found`

**原因：** `settings.gradle.kts` 中 `projectDir` 映射不正确

**修复：**
```bash
# 检查 settings.gradle.kts
cat settings.gradle.kts | grep projectDir

# 应该是：project(":kmp:core").projectDir = file("frontend/kmp/core")
```

### 6.3 ArchUnit 测试失败

**症状：** `ArchitectureRulesTest FAILED`

**原因：** 代码违反架构规则（如使用 RestTemplate、字段注入等）

**修复：**
```bash
# 查看详细失败信息
./mvnw -pl tooling/architecture-tests test

# 查看规则文档
cat docs/ARCHUNIT-RULES.md
```

---

## 七、Agent 行为准则

1. **先读后写** — 修改代码前必须先理解现有代码结构
2. **遵循约定** — 使用项目现有的代码风格和架构模式
3. **测试先行** — 新功能必须伴随测试代码
4. **质量门禁** — 提交前必须通过所有质量检查
5. **文档同步** — 代码变更必须同步更新相关文档

---

## 八、参考链接

- [ArchUnit 规则](docs/ARCHUNIT-RULES.md)
- [工程标准](docs/ENGINEERING-STANDARDS-2026.md)
- [开发者体验标准](docs/DEVELOPER-EXPERIENCE-STANDARD-2026.md)
- [兼容性开发规范](docs/COMPATIBILITY-DEVELOPMENT-STANDARD-2026.md)
- [技术栈最佳实践](docs/TECH-STACK-BEST-PRACTICES-2026.md)
