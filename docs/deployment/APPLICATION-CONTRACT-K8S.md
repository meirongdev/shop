# Shop Platform — Application Contract for Kubernetes Delivery

> 版本：1.0 | 更新时间：2026-03-22

---

## 一、目的

本文档定义新的应用服务接入 `shop-platform` Kubernetes 交付体系时必须满足的最低契约，保证：

- 统一端口与探针
- 统一配置注入方式
- 统一观测与安全基线
- 统一 Kind / K8s 验证流程

---

## 二、容器运行契约

### 2.1 端口

所有应用服务默认使用：

- `8080`：业务端口
- `8081`：management / actuator 端口

K8s Deployment 与 Service 必须同时声明这两个端口。

### 2.2 健康检查

Deployment 必须在 `8081` 端口配置探针：

- readiness：`/actuator/health/readiness`
- liveness：`/actuator/health/liveness`

当前清单中的基础参数为：

- `readinessProbe.initialDelaySeconds: 20`
- `readinessProbe.periodSeconds: 10`
- `livenessProbe.initialDelaySeconds: 30`
- `livenessProbe.periodSeconds: 15`

如果某服务启动更慢，可以在此基础上显式调整，但不能移除探针。

### 2.3 配置注入

应用配置必须来自：

- `env`
- `envFrom.configMapRef`
- `envFrom.secretRef`

禁止依赖镜像内写死环境差异配置。

---

## 三、应用配置契约

每个服务至少需要以下运行时基线：

1. `management.server.port=8081`
2. 暴露 `health,info,prometheus`
3. 开启 readiness / liveness probes
4. OTLP tracing 导出端点
5. 结构化日志输出
6. 如果存在内部调用面，则启用 internal security 配置

对于 HTTP 服务，建议默认具备：

- OpenAPI / Swagger（如果是对外或对内 API 服务）
- 统一错误模型
- 最小测试集

---

## 四、安全契约

### 4.1 北向暴露

- 对外流量默认通过 `api-gateway`
- 应用服务不应直接暴露给外部入口，除非有明确例外说明

### 4.2 东西向调用

- 服务间内部调用安全受 Kubernetes NetworkPolicy (Cilium) 保护。
- 业务上下文（如用户身份、请求 ID）通过 Gateway 注入的 Trusted Headers 传播。
- 内部接口不应直接暴露给外部流量。

---

## 五、可观测性契约

所有服务默认必须具备：

- `:8081/actuator/prometheus`
- `:8081/actuator/health/readiness`
- `:8081/actuator/health/liveness`
- OTLP tracing 导出
- 结构化日志

新增关键链路时，应同步补充业务指标与对应 runbook / SLO 定义。

---

## 六、交付清单

新服务接入 K8s 前，至少确认以下事项：

> 注意：这里的勾选框是**每个新服务接入时都要重新过一遍的交付模板**，不是“当前仓库里所有服务都还没做完”的统一 backlog。

- [ ] 已在 root `pom.xml` / 模块结构中接入
- [ ] 已提供 `application.yml` 默认值与环境变量映射
- [ ] 已在 `k8s/apps/base/platform.yaml`（并通过 `k8s/apps/overlays/dev`）声明 Deployment / Service
- [ ] 已声明 `8080` / `8081` 端口
- [ ] 已配置 readiness / liveness probes
- [ ] 已接入 metrics / tracing / structured logging
- [ ] 已配置对应的 NetworkPolicy 允许可信调用方接入
- [ ] 已补充本地或 Kind 验证步骤

---

## 七、Kind / K8s 验证要求

服务交付完成后，至少需要完成一次验证：

1. `kubectl -n shop get deploy`
2. `kubectl -n shop get pods`
3. 如果是北向功能，经 `api-gateway` 做一次 smoke test
4. 如果是内部服务，验证 readiness / liveness 与下游依赖联通

推荐本地验证入口：

```bash
make platform-validate
./scripts/deploy-kind.sh dev
kubectl -n shop port-forward svc/api-gateway 18080:8080
```

然后通过 `http://127.0.0.1:18080` 做 Gateway smoke test。
