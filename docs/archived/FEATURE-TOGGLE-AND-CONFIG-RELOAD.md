# Shop Platform — Feature Toggle 与 K8s 配置热更新方案

> 版本：1.0 | 更新时间：2026-03-22

---

## 一、结论

当前仓库在 2026 年的推荐基线为：

- **特性开关抽象层**：OpenFeature API
- **首期 Provider**：仓库内自定义 Spring Property Provider
- **K8s 配置来源**：ConfigMap 挂载为文件
- **热更新链路**：Spring Cloud Kubernetes Configuration Watcher → `/actuator/refresh`
- **首个落地点**：`search-service`

该方案把“**特性开关求值**”和“**配置热加载**”拆开设计：

- OpenFeature 负责统一开关读取接口，避免后续切换 vendor 时改业务代码
- Spring/K8s 负责把 ConfigMap 变更可靠地送到应用实例

---

## 二、为什么不是直接上 `@ConditionalOnProperty`

`@ConditionalOnProperty` 适合：

- 启停某个 Bean
- 在应用启动时决定 mock / real provider

但它不适合作为平台级 feature toggle 基线，因为：

- 业务代码缺少统一的开关读取抽象
- 很难平滑切到 `flagd` / Unleash / 其他控制面
- 对运行中请求级别的细粒度判断不够友好

仓库中已有 `wallet-service`、`shop-common` 使用 `@ConditionalOnProperty` 的模式，这类场景继续保留；但**面向平台演进的动态开关**推荐统一走 OpenFeature。

---

## 三、候选方案 trade-off

### 3.1 OpenFeature + 仓库内 Property Provider（本次落地）

优点：

- 保留 OpenFeature 抽象，业务代码不绑死供应商
- 无需额外引入外部控制面，适合当前仓库体量
- 与 Spring Boot 3.5 / K8s ConfigMap / `/actuator/refresh` 非常贴合
- 便于先把“热更新链路”跑通

缺点：

- 暂不提供可视化控制台
- 暂不提供复杂分群、百分比放量、审计工作流

适用阶段：

- 平台刚建立统一开关基线
- 开关主要是服务级、环境级 rollout

### 3.2 OpenFeature + flagd

优点：

- 仍然保留 OpenFeature 标准接口
- 可把开关配置与应用代码彻底解耦
- 更适合后续引入远程拉取、文件/sidecar/provider 多模式

缺点：

- 需要增加 `flagd` 运行单元与运维面
- 对当前仓库来说，控制面收益暂时不足以覆盖复杂度

适用阶段：

- 需要多服务共享更复杂规则
- 希望继续坚持 OpenFeature 生态

### 3.3 Unleash

优点：

- 控制台成熟，支持分群、百分比、审计、策略
- Java SDK 成熟，社区广

缺点：

- 应用侧会更深地绑定 Unleash 语义与控制面
- 需要额外运维 Unleash Server / Edge / token 生命周期
- 对当前仓库来说是“能力过强”

适用阶段：

- 产品团队需要频繁做灰度、人群实验、运营自助开关

### 3.4 结论

**OpenFeature + Property Provider** 是当前最合适的第一阶段实现。  
当仓库未来需要人群定向、实验平台、审计与运营自助时，再把 Provider 切换到 `flagd` 或 Unleash。

---

## 四、Spring Boot 3.5 / 2026 最合适的 K8s 热更新方案

推荐链路：

1. ConfigMap 以**目录挂载**方式注入 Pod
2. Spring Boot 通过 `spring.config.import=optional:file:...` 读取该文件
3. Spring Cloud Kubernetes Configuration Watcher 监听被标记的 ConfigMap
4. Watcher 调用应用 `/actuator/refresh`
5. Spring Cloud Context 重新绑定可刷新的 `@ConfigurationProperties`
6. OpenFeature Property Provider 在下一次求值时读取最新值

当前仓库在 Kind 验证中将 watcher 的 `refreshDelay` 调整为 **30000ms**，用来覆盖 ConfigMap projected volume 的实际刷新延迟。

### 4.1 为什么不用环境变量热更新

不推荐 `envFrom` 作为热更新开关来源：

- 运行中的 Pod 环境变量不会随 ConfigMap 变更而更新
- 这会让“改了 ConfigMap 但应用没变”成为常见误判

### 4.2 为什么必须用目录挂载而不是 `subPath`

K8s ConfigMap 的投影文件更新要依赖卷整体刷新。  
如果把单文件通过 `subPath` 挂进去，文件内容不会随着 ConfigMap 变更自动更新。

因此本仓库规范是：

- **可热更新的配置文件一律目录挂载**
- **禁止用 `subPath` 挂载热更新配置**

### 4.3 为什么属性类不能用 Java record

Spring Cloud Context 的刷新机制会重新绑定 `@ConfigurationProperties`，  
但 Java `record` 不适合这类运行时刷新场景。

因此本次 feature toggle 属性类使用**可变 POJO**。

---

## 五、本仓库落地映射

### 5.1 共享层（`shop-common`）

- 新增 OpenFeature Property Provider
- 新增 `FeatureToggleService`
- 通过 Spring Boot auto-configuration 自动装配
- 新增统一错误码 `SC_FEATURE_DISABLED`

### 5.2 试点服务（`search-service`）

首批开关：

- `search-autocomplete`
- `search-trending`
- `search-locale-aware`

行为：

- 关闭 `search-autocomplete` 后，`/search/v1/products/suggestions` 返回 `503`
- 关闭 `search-trending` 后，`/search/v1/queries/trending` 返回 `503`
- 关闭 `search-locale-aware` 后，搜索接口会忽略 `locales` 参数，回退到默认搜索行为

### 5.3 K8s 资源

- `k8s/apps/platform.yaml`
  - 新增 `search-service-feature-toggles` ConfigMap
  - `search-service` 挂载目录 `/workspace/config/feature-toggles`
  - `search-service` Service 增加 actuator 注解，供 watcher 定位刷新入口
- `k8s/infra/base.yaml`
  - 新增 Spring Cloud Kubernetes Configuration Watcher Deployment / RBAC / Service
  - 显式设置 watcher 的 actuator `port/path`，避免依赖不同 watcher 镜像版本的默认解析行为

---

## 六、演进路线

### Phase 1（当前）

- 服务级布尔开关
- ConfigMap 文件驱动
- Watcher + `/actuator/refresh`

### Phase 2（需要更复杂规则时）

- 保持业务代码仍通过 `FeatureToggleService`
- 将 OpenFeature Provider 替换为：
  - `flagd` Provider，或
  - Unleash-backed adapter

### Phase 3（运营化）

- 引入审计、灰度、人群定向、变更审批与回滚面板

---

## 七、本仓库的目标运行环境

Feature Toggle 的**动态热更新链路**只在 Kind / Kubernetes 基线中受支持。

原因很简单：Configuration Watcher 需要通过 Kubernetes API 监听 ConfigMap 变更，并调用 `/actuator/refresh`；没有 K8s API，就没有这条完整链路。

因此当前仓库约定是：

- **本地开发 / 联调 / 验证统一走 Kind**
- **不再维护 `docker-compose.yml` 运行路径**
- 如需验证 feature flag，请直接使用 `./kind/setup.sh` 并参考 `docs-site/docs/getting-started/local-deployment.md` 中的 Search Service 热更新步骤

## 八、待办事项（低优先级，按需执行）

| # | 描述 | 优先级 |
|---|------|--------|
| T-1 | **验证 Configuration Watcher 镜像版本兼容性**：当前使用 `springcloud/spring-cloud-kubernetes-configuration-watcher:3.3.0`（对应 Spring Cloud 2023.0.x），项目基线为 Spring Cloud 2025.0。Watcher 与应用交互仅通过 HTTP `/actuator/refresh`，实际影响低，但需在 Kind smoke test 中确认行为，必要时升级镜像版本。 | 低 |
| T-2 | **补充其他服务接入指南**：目前只有 `search-service` 落地了 feature toggle。当其他服务需要接入时，需说明复制步骤（新增 `XxxFeatureFlags` 常量类、ConfigMap、volume 挂载、Service actuator 注解）。 | 低（按需补充） |

---

## 九、验证要求

每次 feature toggle 相关变更，至少完成以下验证：

1. 模块测试通过（`shop-common` / 使用方服务）
2. 文档同步更新
3. 若涉及 K8s 热更新链路，至少完成一次 Kind smoke：
   - 查询开关打开时的接口行为
   - 修改 ConfigMap
   - 等待 watcher 触发 refresh
   - 再次验证接口行为变化


---

## 十、官方参考链接

- OpenFeature Java SDK: https://openfeature.dev/docs/reference/technologies/server/java/
- Spring Cloud Kubernetes Configuration Watcher: https://docs.spring.io/spring-cloud-kubernetes/reference/spring-cloud-kubernetes-configuration-watcher.html
- Kubernetes ConfigMap: https://kubernetes.io/docs/concepts/configuration/configmap/
- Unleash Java SDK: https://docs.getunleash.io/reference/sdks/java
