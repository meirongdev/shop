# shop-archetypes

内部 Maven Archetype 模板集合，用于在 `shop-platform` 仓库内快速生成统一风格的新模块。

## 已提供模板

- `gateway-service-archetype`
- `auth-service-archetype`
- `bff-service-archetype`
- `domain-service-archetype`
- `event-worker-archetype`
- `portal-service-archetype`

## 本地安装

```bash
./mvnw -pl shop-common,shop-contracts,shop-archetypes/gateway-service-archetype,shop-archetypes/auth-service-archetype,shop-archetypes/bff-service-archetype,shop-archetypes/domain-service-archetype,shop-archetypes/event-worker-archetype,shop-archetypes/portal-service-archetype -am install
```

## 生成示例

```bash
mkdir -p /tmp/shop-archetype-sandbox
cd /tmp/shop-archetype-sandbox

/Users/matthew/projects/meirongdev/shop/mvnw archetype:generate \
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

## 验证样板工程

如果只是想在仓库外验证样板是否可编译/可测试，可以在生成后把父 POM 的 `relativePath` 改成空值，让 Maven 从本地仓库解析已经安装好的 `shop-platform` 父 POM：

```bash
perl -0pi -e 's#<relativePath>\.\./pom\.xml</relativePath>#<relativePath/>#g' inventory-service/pom.xml
/Users/matthew/projects/meirongdev/shop/mvnw -f inventory-service/pom.xml test
```

如果目标是把新模块正式落到当前仓库，则保留默认 `relativePath`，并在根 `pom.xml` 中手工补充对应的 `<module>` 声明。

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
