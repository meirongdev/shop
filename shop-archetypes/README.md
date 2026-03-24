# shop-archetypes

内部 Maven Archetype 模板集合，用于在 `shop-platform` 仓库内快速生成统一风格的新模块。

## Archetype 怎么辅助开发

Archetype 在当前项目里不是“代码生成玩具”，而是把平台基线直接下发到新模块的工程化工具，主要解决这几类重复劳动：

- 新模块起手时统一目录结构、依赖、测试基座、配置模板
- 避免每个服务重复手抄 `application.yml`、探针、OTEL、内网调用安全等样板
- 让新模块从第一天起就符合 `shop-platform` 的包名、父 POM、模块职责和测试风格
- 降低“新服务能跑，但和现有仓库习惯不一致”的实现漂移

换句话说，archetype 的价值在于：**把平台标准前置到生成阶段，而不是等代码写完再回头治理。**

## 已提供模板

- `gateway-service-archetype`
- `auth-service-archetype`
- `bff-service-archetype`
- `domain-service-archetype`
- `event-worker-archetype`
- `portal-service-archetype`

## 什么时候用哪个 Archetype

| Archetype | 适用场景 | 默认帮助你生成什么 |
|-----------|----------|--------------------|
| `gateway-service-archetype` | 新的边缘接入 / 聚合网关 | Gateway 基础依赖、配置模板、探针、文档骨架 |
| `auth-service-archetype` | 登录、身份、令牌类服务 | Spring Security / JWT 服务骨架、统一父 POM 与配置模板 |
| `bff-service-archetype` | Buyer / Seller / App 侧聚合接口 | Web + RestClient + BFF 风格结构与测试样板 |
| `domain-service-archetype` | 事务型领域服务 | JPA + Flyway + MySQL + Testcontainers 基座 |
| `event-worker-archetype` | Kafka 消费 / 异步后台处理 | Worker 结构、Kafka listener 样板、DB migration 目录 |
| `portal-service-archetype` | Kotlin + Thymeleaf 门户 | Portal 模块结构、Kotlin 配置与视图服务样板 |

## 本地安装

```bash
make archetypes-install
```

等价命令仍然是：

```bash
./mvnw -pl shop-common,shop-contracts,shop-archetypes/gateway-service-archetype,shop-archetypes/auth-service-archetype,shop-archetypes/bff-service-archetype,shop-archetypes/domain-service-archetype,shop-archetypes/event-worker-archetype,shop-archetypes/portal-service-archetype -am install
```

## 生成示例

```bash
SHOP_REPO=/path/to/shop-platform
mkdir -p /tmp/shop-archetype-sandbox
cd /tmp/shop-archetype-sandbox

"${SHOP_REPO}/mvnw" archetype:generate \
  -DarchetypeCatalog=local \
  -DarchetypeGroupId=dev.meirong.shop \
  -DarchetypeArtifactId=domain-service-archetype \
  -DarchetypeVersion=0.1.0-SNAPSHOT \
  -DgroupId=dev.meirong.shop \
  -DartifactId=inventory-service \
  -Dpackage=dev.meirong.shop.inventory \
  -DshopPlatformVersion=0.1.0-SNAPSHOT \
  -DinteractiveMode=false
```

> 建议在空目录中执行 `archetype:generate`，避免 Maven 把临时样板工程自动加入当前 reactor。

> 生成的模块默认假设它最终会作为 `shop-platform` 根目录下的子模块落地，因此父 POM 使用 `../pom.xml`。

## 推荐开发流程

1. 在仓库根目录执行 `make archetypes-install`，把当前模板安装到本地 Maven 仓库。
2. 在空目录生成样板工程，先验证模块职责和骨架是否匹配。
3. 如果只是“试生成”，把生成结果留在 sandbox 中验证；如果要正式开发，再移动/复制回仓库。
4. 将模块加入根 `pom.xml` 的 `<modules>`。
5. 运行 `make verify` 或至少运行该模块的 `mvn -pl <module> -am test`。
6. 在生成骨架上补业务代码，而不是从空目录自己重新搭结构。

这个流程的重点是：**先用 archetype 对齐平台基线，再在统一骨架上开发业务能力。**

## 验证样板工程

如果只是想在仓库外验证样板是否可编译/可测试，可以在生成后把父 POM 的 `relativePath` 改成空值，让 Maven 从本地仓库解析已经安装好的 `shop-platform` 父 POM：

```bash
SHOP_REPO=/path/to/shop-platform
perl -0pi -e 's#<relativePath>\.\./pom\.xml</relativePath>#<relativePath/>#g' inventory-service/pom.xml
"${SHOP_REPO}/mvnw" -f inventory-service/pom.xml test
```

如果目标是把新模块正式落到当前仓库，则保留默认 `relativePath`，并在根 `pom.xml` 中手工补充对应的 `<module>` 声明。

## 和当前 DX 工具链怎么配合

- 用 `make archetypes-install` 安装模板
- 用 `make verify` 做生成后验证
- 用 `make install-hooks` 启用本仓库 git hooks
- 统一编辑器格式由根目录 `.editorconfig` 管理

统一 DX 规范说明见：`docs/DEVELOPER-EXPERIENCE-STANDARD-2026.md`

## 模板版本化策略

- `shop-archetypes` 模块版本即模板族版本。
- **Major**：模板默认技术栈或目录结构发生不兼容变化。
- **Minor**：新增模板能力、默认依赖、测试基座、部署模板，且对既有生成结果兼容。
- **Patch**：占位符修复、文档修正、测试样例修复、无破坏性默认值调整。

### 变更公告流程

每次模板版本变化至少同步以下位置：

1. `shop-archetypes/README.md`：更新安装/生成/验证说明
2. `docs/ENGINEERING-STANDARDS-2026.md`：如果平台基线或 scaffold 规范变化
3. `docs/ROADMAP-2026.md`：如果影响平台工程任务状态或交付顺序

如果变更会影响既有服务的生成结果，应在 PR 描述中明确：

- 影响的 archetype
- 是否破坏兼容
- 迁移动作
- 验证方式
