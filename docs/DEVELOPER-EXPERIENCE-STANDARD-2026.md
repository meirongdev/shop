# Developer Experience Standard 2026

> 适用范围：`shop-platform` 仓库内的 CI、日常开发命令、git hooks、编辑器格式约定、archetype 辅助开发流程  
> 目标：让本地开发体验和 GitHub Actions CI 使用同一套真实命令，而不是维护两套平行流程

---

## 1. DX 目标

当前项目已经有成熟的 Maven 多模块结构、`docs-site`、`scripts/` 和 `shop-archetypes/`，但过去缺少统一的入口层。  
本规范要解决的是：

- CI 跑什么、本地该跑什么，入口保持一致
- 新同学拿到仓库后可以通过 `make` 和文档快速上手
- Git hooks 做“快反馈”，CI 做“可信兜底”
- 编辑器行为尽量统一，减少无意义格式漂移
- 新模块优先从 archetype 起步，而不是手写脚手架

---

## 2. GitHub Actions CI 规范

## 2.1 当前采用的 CI 形态

当前仓库适合采用**轻量但真实**的 CI，而不是一次性把镜像构建、Kind smoke、长链路集成测试全部强塞进 PR 门禁。  
原因很简单：项目已经有 Maven 全量测试与 docs-site 构建这两个稳定入口，而 Kind / 镜像 / 平台集成链路还更适合作为后续增强项，而不是当前默认阻塞项。

因此当前 CI 的推荐基线是：

- `maven-verify`：`./mvnw -B -ntp verify`
- `docs-site`：`cd docs-site && npm ci && npm run build`
- PR 场景按变更路径决定是否需要对应 job
- `push main` / `workflow_dispatch` 默认跑完整 job
- 开启 concurrency，避免同一 PR 的过期流水线继续占资源

## 2.2 为什么这样设计

- Maven `verify` 直接复用仓库真实构建入口，避免“CI 通过，本地命令不同”的偏差
- docs-site 独立成 job，Node 依赖缓存和失败定位都更清晰
- path-aware CI 能减少 docs-site-only 或纯文档 PR 的无意义全仓构建
- 当前先不把 Kind / Docker image build 作为默认门禁，是为了避免在平台链路完全稳定前把 PR 反馈时间拉得过长

## 2.3 后续可逐步加入的增强项

后续如果需要继续强化 CI，可以按顺序增加：

1. OpenAPI diff / 契约变更门禁
2. Archetype 生成 smoke 验证
3. 镜像构建 smoke
4. Kind / Kubernetes smoke
5. 更细粒度的 changed-module 测试矩阵

---

## 3. 本地开发命令规范

## 3.1 统一入口

本仓库新增根目录 `Makefile` 作为**开发入口层**，但它不是新的构建系统，只是把现有真实命令整理成稳定别名。

推荐先执行：

```bash
make help
```

常用目标：

- `make test`：全仓 Maven 测试
- `make build`：全仓构建（跳过测试）
- `make verify`：本地完整验证（Maven verify + docs-site build）
- `make arch-test`：仅跑架构测试
- `make docs-install`：安装 docs-site 依赖
- `make docs-build`：构建 docs-site
- `make docs-start`：本地启动 docs-site
- `make archetypes-install`：安装 archetype 到本地 Maven 仓库
- `make install-hooks`：启用仓库 git hooks
- `make local-checks`：按当前改动范围做本地检查

## 3.2 命令设计原则

- `Makefile` 只封装仓库已经存在并已验证的命令
- 不在 `Makefile` 中偷偷引入新的格式化器或 lint 工具
- 对 docs-site 保持独立入口，不把 Node 工作流硬塞进 Maven

---

## 4. Git Hooks 规范

## 4.1 安装方式

首次 clone 后建议执行：

```bash
make install-hooks
```

它会把仓库的 hooks 路径配置为 `.githooks/`。

## 4.2 当前 hooks 设计

### `pre-commit`

- 保持轻量
- 当前只检查 staged 文件中是否遗留 merge conflict markers
- 目的不是替代 CI，而是尽量在最早阶段拦住明显错误

### `pre-push`

- 使用 `scripts/run-local-checks.sh`
- 默认按 `origin/main` 以来的改动范围判断需要跑什么
- Java / Maven / workflow / DX 相关改动会触发 Maven `verify`
- `docs-site/` 改动会触发 docs-site build

可通过环境变量覆盖模式：

```bash
HOOK_LOCAL_CHECK_MODE=--all .githooks/pre-push
```

可选模式：

- `--since-main`
- `--staged`
- `--all`

## 4.3 Hook 设计原则

- pre-commit 快，pre-push 稍重
- 本地 hook 只做最有价值的拦截，不做“全量慢检查大礼包”
- CI 仍然是最终可信门禁

---

## 5. 编辑器与格式规范

仓库根目录新增 `.editorconfig`，用于统一这些基础行为：

- UTF-8
- LF 换行
- 文件结尾换行
- 常见文件类型的缩进宽度

当前建议：

- Java / Kotlin / XML / properties：4 spaces
- YAML / JSON / 前端静态资源：2 spaces
- Markdown：保留尾随空格（避免破坏显式换行）
- `Makefile`：tab

注意：`.editorconfig` 是**编辑器默认行为约定**，不是一次性重写全仓格式的工具。

---

## 6. Archetype 辅助开发规范

`shop-archetypes/` 是当前项目的新模块起点，而不是可选附属品。

## 6.1 Archetype 解决的问题

- 减少新模块的手工搭建成本
- 把父 POM、包名、依赖、配置模板、测试基座前置统一
- 让新模块从第一天起就带着平台约定，而不是后期返工治理

## 6.2 推荐流程

1. 在仓库根目录执行 `make archetypes-install`
2. 在空目录生成样板工程
3. 先在 sandbox 验证结构与职责
4. 正式落仓后把模块加入根 `pom.xml`
5. 运行 `make verify` 或模块级测试
6. 在 archetype 骨架上开发业务代码

## 6.3 什么时候不要跳过 archetype

以下情况不建议从空目录手写模块：

- 新增 BFF / Domain / Worker / Portal
- 需要继承现有统一配置、测试基座或部署模板
- 团队希望模块结构与已有服务保持一致

只有在 archetype 明显不适配新模块类型时，才应该先扩展 archetype，再落地新模块。

---

## 7. 当前项目的 DX 入口建议

对于日常开发，推荐最小工作流：

1. `make install-hooks`
2. `make help`
3. 按需使用 `make test` / `make verify`
4. 新模块优先 `make archetypes-install`
5. PR 前至少跑一遍与改动匹配的本地检查

---

## 8. 结论

DX 的核心不是“多加工具”，而是让**现有真实命令、CI、hooks、编辑器和脚手架形成一套可解释、可执行、可复用的统一流程**。  
当前这套方案已经能覆盖仓库最常见的日常开发场景，并为后续 CI 增强留出空间。
