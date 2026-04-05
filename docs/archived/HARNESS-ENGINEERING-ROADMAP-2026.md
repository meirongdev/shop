# Shop Platform — Code Agent Harness Engineering Roadmap 2026

> 版本：2.0 | 日期：2026-04-04
> 适用范围：Claude Code 及同类 code agent 在 `shop-platform` 仓库中的工程化配置与治理

---

## 一、背景与目标

2026 年 code agent（Claude Code、Cursor、Copilot Workspace 等）已从"辅助补全"演进为**自主执行多步工程任务的 agent**。Harness Engineering 的目标不再是"让 agent 写代码"，而是：

1. **让 agent 按项目规范写代码**——输出质量 ≥ 人工 code review 后的水平
2. **让 agent 犯错时被拦截**——hooks / CI / ArchUnit / 格式化 / 静态分析构成多层防线
3. **让 agent 的行为可审计、可复现**——权限、命令、记忆系统形成闭环
4. **让 agent 与团队工作流融合**——PR 流程、测试策略、部署安全边界一致

---

## 二、当前状态评估

### 2.1 已有基础设施

| 维度 | 当前状态 | 成熟度 |
|------|----------|--------|
| `CLAUDE.md` | 已建立，覆盖构建命令、架构、约定、测试 | ★★★★☆ |
| `.claude/settings.local.json` | 已有较完整的权限白名单 | ★★★☆☆ |
| Git hooks (`.githooks/`) | pre-commit（冲突标记检查）+ pre-push（路径感知检查） | ★★★★☆ |
| ArchUnit 架构守护 | 19 条规则，CI 可执行 | ★★★★★ |
| Makefile 统一入口 | 完备的 `make` targets | ★★★★★ |
| 文档体系 | 工程标准 / 安全基线 / 可观测 / 技术栈 / DX 标准 | ★★★★☆ |
| 代码格式化 | 无自动格式化工具，仅 `.editorconfig` | ★★☆☆☆ |
| 静态分析 | 无编译期静态分析（仅 ArchUnit 运行时） | ★☆☆☆☆ |
| 覆盖率门禁 | 无 JaCoCo 覆盖率检查 | ☆☆☆☆☆ |
| 契约测试 | BFF 契约测试部分实现，未成体系 | ★★☆☆☆ |
| `.claude/commands/` | 未建立 | ☆☆☆☆☆ |
| Claude Code hooks (`settings.json` hooks) | 未建立 | ☆☆☆☆☆ |
| 多 Agent 指令 (`AGENTS.md` / Copilot) | 未建立 | ☆☆☆☆☆ |
| MCP Server 集成 | 未建立 | ☆☆☆☆☆ |
| Agent Memory 系统 | 基础 MEMORY.md 框架由 harness 提供 | ★★☆☆☆ |

### 2.2 主要差距

1. **缺少代码质量三板斧**——无 Spotless 格式化、无 Error Prone 静态分析、无 JaCoCo 覆盖率门禁。agent 产出的代码格式不统一是最常见的 review 返工原因。
2. **缺少 Claude Code hooks**——agent 创建文件、提交代码、编辑关键文件时没有自动化守护
3. **缺少项目级自定义 slash commands**——常见工作流（新建服务、添加 migration、创建 PR）没有封装
4. **缺少 `settings.json`（项目级可提交）**——当前只有 `settings.local.json`（个人，不提交）
5. **CLAUDE.md 可进一步增强**——缺少 agent 行为约束（如禁止修改哪些文件、PR 规范、commit message 格式）
6. **缺少 agent 专用的编码规范文档**——当前规范面向人类开发者，agent 需要更精确的约束
7. **缺少多 Agent 支持**——只有 `CLAUDE.md`，缺少 `AGENTS.md`（通用）和 `.github/copilot-instructions.md`（Copilot 专用）
8. **契约测试未体系化**——BFF↔Domain Service 的契约变更缺少 CI 门禁

---

## 三、Roadmap

### Phase 0：代码质量基线（Week 1-2）

> 目标：建立 agent 产出代码的格式化、静态分析和覆盖率门禁——这是一切 harness 的前提

#### 3.0.1 Spotless 自动格式化

在根 `pom.xml` 引入 `spotless-maven-plugin`，统一 Java 代码格式：

```xml
<plugin>
  <groupId>com.diffplug.spotless</groupId>
  <artifactId>spotless-maven-plugin</artifactId>
  <version>2.44.4</version>
  <configuration>
    <java>
      <googleJavaFormat>
        <version>1.34.0</version>
      </googleJavaFormat>
      <removeUnusedImports/>
      <trimTrailingWhitespace/>
      <endWithNewline/>
    </java>
  </configuration>
</plugin>
```

**Makefile 集成：**

```makefile
fmt:        ## Auto-fix Java code formatting
	$(MVNW) spotless:apply

fmt-check:  ## Validate formatting (CI gate)
	$(MVNW) spotless:check
```

**CI 集成：** 在 `ci.yml` 的 `maven-verify` job 前增加 `spotless:check` 步骤，格式不合规直接阻断 PR。

**Agent 影响：** agent 写完代码后可以运行 `make fmt` 自动修正格式，不再需要人工调整缩进和空格。

#### 3.0.2 Error Prone 静态分析

在根 `pom.xml` 引入 Error Prone，编译期自动检测常见 bug：

```xml
<plugin>
  <groupId>org.apache.maven.plugins</groupId>
  <artifactId>maven-compiler-plugin</artifactId>
  <configuration>
    <compilerArgs>
      <arg>-XDcompilePolicy=simple</arg>
      <arg>-Xplugin:ErrorProne</arg>
    </compilerArgs>
    <annotationProcessorPaths>
      <path>
        <groupId>com.google.errorprone</groupId>
        <artifactId>error_prone_core</artifactId>
        <version>2.48.0</version>
      </path>
    </annotationProcessorPaths>
  </configuration>
</plugin>
```

**与 ArchUnit 的互补关系：**

| 层次 | 工具 | 检测时机 | 检测范围 |
|------|------|----------|----------|
| 编译期 | Error Prone | `mvn compile` | 空指针、资源泄露、并发 bug、API 误用 |
| 运行时 | ArchUnit | `mvn test` | 分层约束、命名规范、Spring 最佳实践、Kafka 幂等 |

两者不替代彼此：Error Prone 抓的是代码级 bug，ArchUnit 抓的是架构级违规。

#### 3.0.3 JaCoCo 覆盖率门禁

```xml
<plugin>
  <groupId>org.jacoco</groupId>
  <artifactId>jacoco-maven-plugin</artifactId>
  <version>0.8.13</version>
  <executions>
    <execution>
      <goals><goal>prepare-agent</goal></goals>
    </execution>
    <execution>
      <id>check</id>
      <goals><goal>check</goal></goals>
      <configuration>
        <rules><rule>
          <element>BUNDLE</element>
          <limits><limit>
            <counter>LINE</counter>
            <value>COVEREDRATIO</value>
            <minimum>0.60</minimum>
          </limit></limits>
        </rule></rules>
      </configuration>
    </execution>
  </executions>
</plugin>
```

**分阶段策略：**

| 阶段 | 覆盖率要求 | 说明 |
|------|-----------|------|
| Phase 0 启动 | 30% | 当前多个模块接近 0，先设低门槛 |
| Phase 0 + 4 周 | 45% | agent 协助补充测试后提升 |
| Phase 4 完成 | 60% | 对齐行业最佳实践基线 |

**CI 集成：** JaCoCo 报告上传为 CI artifact，覆盖率不达标阻断 PR。

#### 3.0.4 OTel 验证脚本增强

当前 `scripts/verify-observability.sh` 已存在，建议增强为结构化报告输出：

- 输出 JSON 格式验证报告到 `build/reports/otel/verification-report.json`
- CI 中可解析报告判断 traces / metrics / logs 采集是否正常
- Makefile 增加 `make verify-otel-report` target

---

### Phase 1：Agent 行为守护层（Week 3-4）

> 目标：让 agent 在当前仓库中的行为被约束和守护

#### 3.1.1 建立项目级 `settings.json`（可提交到 Git）

创建 `.claude/settings.json`，与 `settings.local.json` 分离：

```jsonc
{
  // 项目级权限——团队共享
  "permissions": {
    "allow": [
      // 安全的只读/构建操作
      "Bash(make *)",
      "Bash(./mvnw *)",
      "Bash(git status*)",
      "Bash(git diff*)",
      "Bash(git log*)",
      "Bash(git branch*)"
    ],
    "deny": [
      // 禁止 agent 直接操作的危险命令
      "Bash(git push --force*)",
      "Bash(git reset --hard*)",
      "Bash(rm -rf*)",
      "Bash(kubectl delete namespace*)",
      "Bash(docker system prune*)"
    ]
  },
  // Claude Code hooks（agent 行为自动守护）
  "hooks": {
    "PreToolUse": [
      {
        "matcher": "Edit",
        "command": "bash -c 'FILE=\"$CLAUDE_FILE_PATH\"; case \"$FILE\" in */db/migration/*) echo \"BLOCK: Flyway migration 文件不可修改，只能新建。请创建新的 migration 文件。\" && exit 1;; esac'"
      },
      {
        "matcher": "Write",
        "command": "bash -c 'FILE=\"$CLAUDE_FILE_PATH\"; case \"$FILE\" in .github/workflows/*) echo \"BLOCK: CI workflow 修改需人工确认。\" && exit 1;; */application-prod.yml) echo \"BLOCK: 生产配置不可由 agent 直接修改。\" && exit 1;; esac'"
      }
    ],
    "PostToolUse": [
      {
        "matcher": "Write",
        "command": "bash -c 'FILE=\"$CLAUDE_FILE_PATH\"; case \"$FILE\" in *.java) if grep -q \"@Autowired\" \"$FILE\" 2>/dev/null && grep -c \"@Autowired\" \"$FILE\" | grep -qv \"^0$\"; then FIELDS=$(grep -n \"@Autowired\" \"$FILE\" | head -5); echo \"WARNING: 检测到 @Autowired 字段注入 (违反 ARCH-01):\\n$FIELDS\\n请改用构造器注入。\"; fi;; esac'"
      }
    ],
    "PostCommit": [
      {
        "command": "bash -c 'echo \"提示：请确认 commit message 遵循 Conventional Commits 格式 (feat/fix/refactor/chore/docs/test)\"'"
      }
    ]
  }
}
```

#### 3.1.2 Claude Code Hooks 策略

| Hook 类型 | 触发时机 | 守护目标 |
|-----------|----------|----------|
| `PreToolUse(Edit)` | 编辑文件前 | 阻止修改 Flyway migration、生产配置 |
| `PreToolUse(Write)` | 创建文件前 | 阻止在 `.github/workflows/` 直接创建 CI 配置 |
| `PostToolUse(Write)` | 创建 Java 文件后 | 检测 `@Autowired` 字段注入等 ArchUnit 规则子集 |
| `PostToolUse(Edit)` | 编辑文件后 | 检测 `RestTemplate`、`System.out`、`new ObjectMapper()` |
| `PostCommit` | 提交后 | 提示 Conventional Commits 格式检查 |
| `PreToolUse(Bash)` | 执行命令前 | 拦截破坏性 git/k8s/docker 操作 |

#### 3.1.3 增强 CLAUDE.md——Agent 行为约束

在现有 CLAUDE.md 中追加以下 section：

```markdown
## Agent Behavior Constraints

### 禁止直接修改的文件
- `**/db/migration/V*` — Flyway migration 一旦提交不可修改，只能新建
- `.github/workflows/*.yml` — CI 配置修改需人工审批
- `**/application-prod.yml` — 生产配置不可由 agent 写入
- `pom.xml`（根 POM）— 依赖版本升级需人工确认

### Commit Message 格式
遵循 Conventional Commits：
- `feat(module): 描述` — 新功能
- `fix(module): 描述` — Bug 修复
- `refactor(module): 描述` — 重构
- `test(module): 描述` — 测试
- `docs: 描述` — 文档
- `chore: 描述` — 工程维护

### PR 创建规范
- PR title 不超过 70 字符
- body 必须包含 `## Summary` 和 `## Test Plan`
- 如果涉及 DB migration，`## Migration` section 必须说明 schema 变更
- 如果涉及 API 变更，`## API Changes` section 必须列出变更的 endpoint

### 测试要求
- 新增 Controller → 必须有对应的集成测试（继承 AbstractMySqlIntegrationTest）
- 新增 Kafka Consumer → 必须有幂等性测试
- 修改 BFF 聚合逻辑 → 必须有 WireMock 测试
- 修改 shop-contracts → 运行 `make arch-test` 验证无破坏
- 代码修改后运行 `make fmt` 确保格式合规
```

#### 3.1.4 建立多 Agent 指令体系

从 `CLAUDE.md` 中分离出通用 agent 指令，建立三层结构：

| 文件 | 受众 | 内容 |
|------|------|------|
| `AGENTS.md` | 所有 agent（通用） | 项目结构、编码风格、命名约定、commit/PR 规范、测试约定、禁止模式 |
| `CLAUDE.md` | Claude Code（专用） | 构建命令、架构、运行时约定 + `include: AGENTS.md` 引用 |
| `.github/copilot-instructions.md` | GitHub Copilot | 精简版编码约定（~70 行），格式化/遥测/分层/测试要点 |

**`AGENTS.md` 关键 section：**

- **Coding Style** — 缩进、命名、import 顺序（与 Spotless 配置对齐）
- **Naming Conventions** — Controller/Service/Repository/Entity/DTO 命名规则
- **Prohibited Patterns** — ArchUnit 19 条规则的 agent 友好版
- **Commit & PR Guidelines** — Conventional Commits + PR template
- **Testing Conventions** — 测试基类、命名、幂等验证
- **Agent-Specific Instructions** — 不要修改已提交的 migration、不要猜测 API 路径（查 `*Api.java`）

**设计原则：** `AGENTS.md` 是通用的、agent 无关的；`CLAUDE.md` 和 `.github/copilot-instructions.md` 只放各 agent 特有的内容并引用 `AGENTS.md`。避免多份文件重复维护。

---

### Phase 2：自定义 Slash Commands（Week 5-6）

> 目标：将高频工程操作封装为可复用的 agent 命令

#### 3.2.1 命令目录结构

```
.claude/commands/
├── new-service.md           # 用 archetype 创建新服务
├── new-migration.md         # 创建 Flyway migration
├── add-api-endpoint.md      # 添加新 API endpoint（contracts → controller → test）
├── add-kafka-consumer.md    # 添加 Kafka consumer（含幂等守护）
├── run-module-tests.md      # 运行指定模块测试
├── pre-pr-check.md          # PR 提交前完整检查
├── review-changes.md        # 审查当前变更
└── add-bff-client.md        # 在 BFF 中添加新的下游服务调用
```

#### 3.2.2 关键命令设计

**`/new-service` — 创建新微服务**

```markdown
# New Service

根据 archetype 创建新的微服务模块。

## 步骤
1. 确认服务类型：domain / bff / gateway / event-worker / portal
2. 确认模块名（kebab-case）和包名
3. 运行 `make archetypes-install`
4. 使用对应 archetype 生成项目
5. 将模块加入根 `pom.xml` 的 `<modules>`
6. 创建 K8s manifests（`k8s/base/<module>/`）
7. 添加 Gateway 路由配置
8. 运行 `make build` 验证编译通过
9. 运行 `make arch-test` 验证架构规则通过
10. 运行 `make fmt` 确保格式合规
```

**`/new-migration` — 创建 Flyway Migration**

```markdown
# New Migration

为指定服务创建新的 Flyway migration 文件。

## 输入
- $ARGUMENTS: 服务名和 migration 描述，如 "order-service add_tracking_number_column"

## 步骤
1. 解析服务名和描述
2. 查找该服务现有 migration 的最大版本号
3. 生成下一个版本号的 migration 文件
4. 文件路径：`<service>/src/main/resources/db/migration/V<next>__<description>.sql`
5. 提醒：JPA DDL mode 为 validate，migration 必须与 Entity 保持一致
```

**`/pre-pr-check` — PR 前检查**

```markdown
# Pre-PR Check

执行 PR 提交前的完整检查清单。

## 步骤
1. `git diff origin/main --stat` — 确认变更范围
2. 判断变更涉及哪些模块
3. `make fmt-check` — 格式检查
4. 对涉及的模块运行 `./mvnw -pl <modules> -am test`
5. 如果涉及 shop-contracts 或 shop-common → `make arch-test`
6. 如果涉及 docs-site → `make docs-build`
7. 如果涉及 k8s/ / kind/ / Tiltfile → `make platform-validate`
8. 检查是否有遗留的 TODO/FIXME
9. 检查 commit message 格式
10. 输出检查报告
```

**`/add-api-endpoint` — 添加新 API Endpoint**

```markdown
# Add API Endpoint

按照项目约定添加新的 API endpoint，完整覆盖 contracts → controller → test。

## 输入
- $ARGUMENTS: 服务名、HTTP method、路径、描述

## 步骤
1. 在 `shop-contracts/contracts/api/<Service>Api.java` 中添加路径常量
2. 创建/更新 Request/Response DTO（如需要）
3. 在对应服务的 Controller 中添加方法，返回 `ApiResponse<T>`
4. 如果是 BFF 服务 → 在对应 `@HttpExchange` client 中添加方法
5. 创建集成测试（继承 AbstractMySqlIntegrationTest 或使用 WireMock）
6. 运行 `make fmt` 格式化
7. 运行模块测试验证
```

---

### Phase 3：Agent 编码规范文档（Week 7-8）

> 目标：提供 agent 可直接消费的、精确的编码规范

#### 3.3.1 创建 `docs/AGENT-CODING-STANDARDS.md`

面向 agent 的编码规范需要比人类文档**更精确、更具体**，因为 agent 不能"意会"。关键 section：

**A. 文件创建规范**

| 文件类型 | 包路径 | 命名规则 | 必须包含 |
|----------|--------|----------|----------|
| Controller | `*.controller` | `*Controller.java` | `@RestController`, 使用 `*Api.java` 路径常量, 返回 `ApiResponse<T>` |
| Service | `*.service` | `*Service.java` | 构造器注入, `@Service` |
| Repository | `*.repository` | `*Repository.java` | 继承 `JpaRepository` |
| Entity | `*.model` / `*.entity` | 与表名对应 | `@Entity`, `@Table`, 不使用 `@Data` |
| DTO | `shop-contracts` 内 | `*Request`/`*Response`/`*Event` | record 类型优先 |
| Config | `*.config` | `*Config.java` / `*Properties.java` | `@Configuration` / `@ConfigurationProperties` |
| Migration | `db/migration/` | `V<n>__<desc>.sql` | 纯 DDL，不含业务数据 |

**B. 禁止模式清单（与 ArchUnit 19 条规则 + Error Prone 对齐）**

```java
// ❌ 禁止（ArchUnit 拦截）
@Autowired private SomeService service;           // ARCH-01: 字段注入
new RestTemplate();                                // ARCH-02: 已废弃客户端
System.out.println(...);                           // ARCH-03: 非结构化输出
System.err.println(...);                           // ARCH-04: 非结构化输出
e.printStackTrace();                               // CODE-01: 非结构化异常
java.util.logging.Logger.getLogger(...);           // CODE-02: 非统一日志
new org.joda.time.DateTime();                      // CODE-03: 已废弃时间 API
new ObjectMapper();  // 在 config 包外              // CODE-04: 非托管实例
Executors.newFixedThreadPool(n);                   // CODE-05: 传统线程池
new Gson();                                        // CODE-06: 非统一序列化

// ✅ 替代
private final SomeService service;                 // 构造器注入
restClient.get()...                                // RestClient
log.info("...", args);                             // SLF4J
log.error("msg", e);                               // 替代 printStackTrace
java.time.LocalDateTime.now();                     // java.time
@Autowired ObjectMapper mapper;                    // 在 config 包外注入
Executors.newVirtualThreadPerTaskExecutor();        // 虚拟线程
```

**C. 测试编写规范**

```java
// 集成测试模板
class XxxControllerTest extends AbstractMySqlIntegrationTest {
    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;

    @Test
    void methodName_givenCondition_expectedResult() {
        // given / when / then
    }
}

// BFF 测试模板（WireMock）
@AutoConfigureWireMock(port = 0)
class XxxBffControllerTest extends AbstractMySqlIntegrationTest {
    // stub downstream services with WireMock
}

// Kafka Consumer 测试必须验证幂等性
@Test
void consume_duplicateEvent_processedOnlyOnce() {
    // 发送同一事件两次，验证业务效果只发生一次
}
```

**D. API 变更协议**

- 新增 endpoint → `shop-contracts` 先加路径常量和 DTO
- 修改 response 结构 → 确认 BFF / portal 侧不会 break
- 删除 endpoint → 确认无调用方后才可删除

---

### Phase 4：Agent 测试策略增强（Week 9-10）

> 目标：确保 agent 产出的代码通过多层测试验证

#### 3.4.1 Agent 必须运行的测试矩阵

| 变更范围 | 最低测试要求 | 命令 |
|----------|-------------|------|
| 单个 domain service | 格式检查 + 模块测试 | `make fmt-check && ./mvnw -pl <module> -am test` |
| shop-common / shop-contracts | 格式检查 + 架构测试 + 全量测试 | `make fmt-check && make arch-test && make test` |
| BFF 层 | 格式检查 + 模块测试 + WireMock + 契约测试 | `make fmt-check && ./mvnw -pl <bff> -am test` |
| Kafka consumer | 模块测试（含幂等验证） | `./mvnw -pl <module> -am test` |
| API Gateway | 模块测试 + smoke | `./mvnw -pl api-gateway -am test` |
| Flyway migration | 模块测试（Testcontainers 验证 schema） | `./mvnw -pl <module> -am test` |
| K8s manifests | 平台资产校验 | `make platform-validate` |
| docs-site | docs 构建 | `make docs-build` |
| 跨模块 / 不确定 | 全量 | `make verify` |

#### 3.4.2 Spring Cloud Contract 契约测试体系

采用 **Spring Cloud Contract**（而非 Pact）作为 BFF↔Domain Service 的契约测试框架：

**选型理由：**

| 维度 | Spring Cloud Contract | Pact |
|------|----------------------|------|
| 生态融合 | 原生 Spring Cloud 组件，与 Boot 3.5 / Cloud 2025.0.1 深度集成 | 独立生态，需额外 Pact Broker |
| Stub 分发 | **自动生成 WireMock stub JAR**，通过 Maven artifact 分发 | 手写 mock，依赖 Pact Broker |
| 现有对接 | BFF 已使用 `@AutoConfigureWireMock`，stub JAR 无缝替换手写 mock | 需要重写 client 集成 |
| 基础设施 | 零额外基础设施——Maven 仓库即可 | 需要部署和维护 Pact Broker |
| 语言约束 | JVM only | 多语言——但本项目服务端全为 Java/Kotlin |

**实施流程：**

```
1. Domain Service（Provider）编写 Contract DSL（Groovy / YAML）
   └→ 放在 src/test/resources/contracts/

2. Maven 插件自动生成：
   ├→ Provider 端：自动生成验证测试（确保实现符合契约）
   └→ Consumer 端：自动生成 WireMock stub JAR（发布到 Maven repo）

3. BFF（Consumer）引用 stub JAR
   └→ 替换现有手写 WireMock stub → 维护量下降

4. CI 门禁：Provider 修改 API → 契约测试失败 → PR 阻断
```

**Maven 配置示例（Provider 端）：**

```xml
<plugin>
  <groupId>org.springframework.cloud</groupId>
  <artifactId>spring-cloud-contract-maven-plugin</artifactId>
  <version>${spring-cloud-contract.version}</version>
  <extensions>true</extensions>
  <configuration>
    <testFramework>JUNIT5</testFramework>
    <baseClassForTests>
      com.shop.marketplace.BaseContractTest
    </baseClassForTests>
  </configuration>
</plugin>
```

**分阶段落地：**

| 阶段 | 覆盖范围 | 说明 |
|------|----------|------|
| Phase 4a | marketplace-service ↔ buyer-bff | 核心购物链路，调用最频繁 |
| Phase 4b | order-service ↔ buyer-bff / seller-bff | 订单链路 |
| Phase 4c | 全部 Domain Service ↔ BFF | 完整覆盖 |

#### 3.4.3 PostToolUse Hook 自动触发编译检查

```jsonc
{
  "hooks": {
    "PostToolUse": [
      {
        "matcher": "Edit|Write",
        "command": "bash -c 'FILE=\"$CLAUDE_FILE_PATH\"; case \"$FILE\" in *.java|*.kt) MODULE=$(echo \"$FILE\" | sed \"s|/src/.*||\" | xargs basename); echo \"提示：已修改 $MODULE 模块，建议运行 ./mvnw -pl $MODULE -am compile 验证编译\";; esac'"
      }
    ]
  }
}
```

#### 3.4.4 新增 ArchUnit 规则（agent 防护专项）

| 规则 ID | 名称 | 说明 |
|---------|------|------|
| AGENT-01 | `no_test_class_without_assertions` | 测试方法必须包含断言 |
| AGENT-02 | `controllers_must_have_tests` | Controller 类必须有对应 `*Test` 类 |
| AGENT-03 | `no_catch_all_exception` | 禁止 `catch (Exception e)` 不含日志或重抛 |
| AGENT-04 | `migration_files_are_immutable` | 已提交的 migration 文件不可修改（CI 层校验） |
| AGENT-05 | `dto_response_must_be_records` | DTO/Response 类型必须使用 record |

---

### Phase 5：Skills 与工作流集成（Week 11-12）

> 目标：将 agent 技能与团队工作流深度融合

#### 3.5.1 推荐使用的内置 Skills

| Skill | 用途 | 触发场景 |
|-------|------|----------|
| `test-driven-development` | TDD 流程 | 实现新功能或修复 bug 前 |
| `systematic-debugging` | 系统化调试 | 遇到测试失败或异常行为 |
| `verification-before-completion` | 完成前验证 | 声称任务完成前 |
| `brainstorming` | 需求澄清 | 创建新功能、修改行为前 |
| `writing-plans` | 实施规划 | 多步骤任务开始前 |
| `dispatching-parallel-agents` | 并行执行 | 多个独立子任务 |
| `finishing-a-development-branch` | 分支收尾 | 实现完成后决定 merge/PR |
| `requesting-code-review` | 代码审查 | 完成重要功能后 |
| `using-git-worktrees` | 隔离开发 | 需要在独立环境中实验 |

#### 3.5.2 项目专用 Skills（待开发）

建议为本项目创建以下自定义 skill：

1. **`shop-service-scaffold`** — 使用 archetype 创建新服务，包含完整的 K8s manifest、Gateway 路由、CI 配置
2. **`shop-outbox-event`** — 添加新的 outbox 事件（Event DTO + outbox writer + Kafka consumer + 幂等守护）
3. **`shop-bff-aggregation`** — 在 BFF 中添加新的聚合端点（RestClient + CircuitBreaker + WireMock test）
4. **`shop-flyway-migration`** — 创建 Flyway migration + 验证 Entity 一致性
5. **`shop-contract-test`** — 为 BFF↔Domain Service 接口创建 Spring Cloud Contract 契约

---

### Phase 6：MCP Server 与外部集成（Week 13-14）

> 目标：让 agent 可以查询和操作外部系统

#### 3.6.1 推荐的 MCP Server 集成

| MCP Server | 用途 | 优先级 |
|------------|------|--------|
| GitHub MCP | PR 审查、issue 查询、CI 状态检查 | P0 |
| Kubernetes MCP | 查询 Pod 状态、查看日志（只读） | P1 |
| Grafana MCP | 查询 dashboard、检查告警 | P1 |
| Database MCP（只读） | 查询 schema 信息辅助 migration 编写 | P2 |

#### 3.6.2 MCP 安全边界

- 所有 MCP Server **默认只读**
- 写操作（创建 PR、添加 comment）需要 hook 审批
- 禁止 MCP 直接操作生产环境资源
- MCP 查询结果中的敏感信息（token、密码）必须由 server 侧过滤

---

## 四、CLAUDE.md 增强 Checklist

以下是建议追加到 `CLAUDE.md` 的 section，按优先级排列：

| 优先级 | Section | 内容 |
|--------|---------|------|
| P0 | Agent Behavior Constraints | 禁止修改的文件、commit 格式、PR 规范 |
| P0 | Test Requirements | 变更→测试映射矩阵 |
| P0 | Formatting | `make fmt` / `make fmt-check` 使用说明 |
| P1 | Code Patterns (Do / Don't) | ArchUnit 规则 + Error Prone 的 agent 友好版 |
| P1 | Module Creation Protocol | 必须用 archetype，步骤明确 |
| P2 | Contract Test Protocol | Spring Cloud Contract 的创建流程 |
| P2 | Event / Outbox Protocol | 新增事件的完整流程 |
| P2 | Migration Protocol | Flyway migration 的创建规范 |
| P3 | Deployment Safety | 哪些操作需要人工确认 |

---

## 五、实施优先级与里程碑

```
Phase 0 (Week 1-2)   ████████░░░░░░░░░░░░░░░░░░░░  代码质量基线
  ├─ Spotless 格式化 + CI 门禁（make fmt / make fmt-check）
  ├─ Error Prone 静态分析（编译期自动启用）
  ├─ JaCoCo 覆盖率门禁（初始 30%，逐步提升到 60%）
  └─ OTel 验证脚本增强（JSON 报告输出）

Phase 1 (Week 3-4)   ░░░░░░░░████████░░░░░░░░░░░░  Agent 行为守护层
  ├─ .claude/settings.json（hooks + permissions）
  ├─ CLAUDE.md 增强（行为约束 section）
  ├─ AGENTS.md（通用 agent 指令）
  ├─ .github/copilot-instructions.md（Copilot 指令）
  └─ 基础 hook 上线并验证

Phase 2 (Week 5-6)   ░░░░░░░░░░░░░░░░████████░░░░  自定义 Slash Commands
  ├─ .claude/commands/ 目录建立
  ├─ 核心命令：new-service, new-migration, pre-pr-check, add-api-endpoint
  └─ 命令使用文档

Phase 3 (Week 7-8)   ░░░░░░░░░░░░░░░░░░░░░░░░████  Agent 编码规范
  ├─ docs/AGENT-CODING-STANDARDS.md
  ├─ ArchUnit 规则与 agent 规范对齐验证
  └─ 禁止模式清单 + 推荐模式模板

Phase 4 (Week 9-10)  ░░░░░░░░░░░░░░░░░░░░░░░░░░░░  测试策略 + 契约测试
  ├─ Spring Cloud Contract 引入（marketplace → buyer-bff 先行）
  ├─ PostToolUse 编译检查 hook
  ├─ 新增 agent 防护 ArchUnit 规则
  ├─ 测试矩阵集成到 hooks
  └─ JaCoCo 覆盖率提升到 60%

Phase 5 (Week 11-12) ░░░░░░░░░░░░░░░░░░░░░░░░░░░░  Skills + 工作流
  ├─ 评估并启用内置 skills
  ├─ 开发项目专用 skills（含 shop-contract-test）
  └─ 工作流文档

Phase 6 (Week 13-14) ░░░░░░░░░░░░░░░░░░░░░░░░░░░░  MCP + 外部集成
  ├─ GitHub MCP 接入
  ├─ Kubernetes MCP（只读）
  └─ Grafana MCP
```

---

## 六、代码质量多层防线总览

Phase 0 到 Phase 4 完成后，代码质量形成 **5 层防线**：

```
Layer 1 — 编辑时（Agent hooks）
  PostToolUse: 字段注入、RestTemplate、System.out 即时检测

Layer 2 — 格式化（Spotless）
  make fmt:       agent 写完代码自动修正格式
  make fmt-check: CI 门禁格式不合规阻断 PR

Layer 3 — 编译期（Error Prone）
  mvn compile: 自动检测空指针、资源泄露、并发 bug、API 误用

Layer 4 — 测试期（ArchUnit + JaCoCo + Spring Cloud Contract）
  make arch-test: 19+ 条架构规则
  make test:      覆盖率 ≥ 60%
  契约测试:        BFF↔Domain Service 接口变更自动验证

Layer 5 — 提交 / 推送（Git hooks + CI）
  pre-commit:  冲突标记检查
  pre-push:    路径感知检查（make local-checks）
  CI:          全量验证 + artifact 上传
```

---

## 七、度量与验证

### 7.1 Harness 有效性指标

| 指标 | 目标 | 测量方式 |
|------|------|----------|
| Agent 产出通过 CI 一次成功率 | ≥ 80% | CI 日志 |
| ArchUnit 违规被 hook 提前拦截率 | ≥ 90% | hook 日志 |
| Agent 产出需要人工修改的比例 | ≤ 20% | PR review 统计 |
| 新服务从 archetype 创建成功率 | 100% | archetype-tests |
| Agent 误操作（修改不该改的文件）发生率 | 0 | hook 拦截日志 |
| Spotless 格式化通过率 | 100%（agent 运行 `make fmt` 后） | CI 日志 |
| 覆盖率达标率 | ≥ 60%（Phase 4 后） | JaCoCo 报告 |
| 契约测试覆盖率 | 100% BFF↔Domain Service 接口 | Spring Cloud Contract 报告 |

### 7.2 定期审查

- **每月**：审查 CLAUDE.md / AGENTS.md 是否与代码库实际状态一致
- **每季度**：审查 hooks 拦截日志，移除无效规则，补充新规则
- **版本升级时**：Spring Boot / Java / Spring Cloud Contract 版本升级后同步更新 agent 规范

---

## 八、与现有 DX 标准的关系

本文档是 `docs/DEVELOPER-EXPERIENCE-STANDARD-2026.md` 的**延伸**，而非替代。

- DX 标准定义了人类开发者的工作流（CI、hooks、Makefile、archetype）
- Harness Engineering 定义了 agent 如何在**同一套工作流**内安全高效地工作
- 两者共享同一套 CI、Git hooks、ArchUnit 规则——agent 不应有"特殊通道"

关键原则：**agent 的守护层是叠加在现有 DX 之上的，不是平行的另一套体系。**

---

## 九、配套文档索引

| 文档 | 路径 | 关系 |
|------|------|------|
| DX 标准 | `docs/DEVELOPER-EXPERIENCE-STANDARD-2026.md` | 本文档的基础层 |
| 工程标准 | `docs/ENGINEERING-STANDARDS-2026.md` | 技术栈与测试基线 |
| ArchUnit 规则 | `docs/ARCHUNIT-RULES.md` | agent 禁止模式的来源 |
| 安全基线 | `docs/SECURITY-BASELINE-2026.md` | agent 安全边界 |
| 可观测基线 | `docs/OBSERVABILITY-ALERTING-SLO.md` | OTel 验证基准 |
| 技术栈最佳实践 | `docs/TECH-STACK-BEST-PRACTICES-2026.md` | 技术选型依据 |
| API 文档规范 | `docs/API-DOCUMENTATION-SPRINGDOC-2026.md` | OpenAPI 约定 |
