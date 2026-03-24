# Shop Platform — Observability, Alerting, and SLO Baseline

> 版本：1.1 | 更新时间：2026-03-23

---

## 一、适用范围

本文定义 `shop-platform` 的统一可观测性基线，包括：

- 指标命名约定
- 核心 SLI / SLO 草案
- 告警分级
- Runbook 最小模板

当前仓库的运行基线已经统一为：

- 应用端口：`8080`
- 管理端口：`8081`
- Prometheus 暴露：`/actuator/prometheus`
- 健康检查：`/actuator/health/readiness`、`/actuator/health/liveness`
- Trace 导出：OTLP HTTP → `http://otel-collector:4318/v1/traces`

当前本地 / Kind 默认清单已经落地：

- Prometheus（`k8s/infra/base.yaml`，scrape interval 15s）
- OpenTelemetry Collector（OTLP HTTP/gRPC 接收，**当前仅 debug exporter，trace 不持久化**）
- 结构化日志（Logstash JSON 格式，各服务统一）

当前默认清单尚未统一部署：

- **Garage S3** — Kind 内 S3 兼容对象存储，作为 Loki/Tempo 存储后端和文件存储基础
- **Tempo** — 分布式 Trace 存储与查询（接 Garage S3 backend）
- **Loki** — 日志聚合与查询（接 Garage S3 backend）
- **Grafana** — 统一可视化（接 Prometheus + Loki + Tempo）
- **Prometheus Alert Rules** — P1/P2 告警规则（本文第五节已定义，尚未写入 `prometheus.yml`）
- **Kafka consumer lag** — `kafka_consumergroup_lag` 指标未暴露

> **当前痛点**：OTEL Collector 的 trace 数据只输出到 debug 日志，链路追踪无法持久化查询。建议优先部署 Garage S3 → Tempo，再接 Grafana，完成三支柱（指标/日志/链路）闭环。

因此本文档中的 SLO / 告警 / runbook 基线是**当前可观测采集能力之上的平台标准**，不代表所有可视化组件已经在本仓库中默认交付。

---

## 零、部署路线（待实施）

### 0.1 目标架构

```
应用服务 (所有服务)
  ├─ /actuator/prometheus  →  Prometheus  →  Grafana
  ├─ OTLP traces           →  OTEL Collector  →  Tempo  →  Grafana
  └─ 结构化日志 (stdout)   →  Loki (Promtail)  →  Grafana

Tempo、Loki 数据持久化
  └─  Garage S3 (Kind 内)
      ├─ bucket: traces
      ├─ bucket: logs
      ├─ bucket: product-images      (商品图片)
      └─ bucket: datalake            (Kafka → S3 行为数据归档)
```

### 0.2 部署顺序

1. **Garage S3** — 加入 `k8s/infra/base.yaml`，暴露 S3 endpoint（`http://garage:3900`）
2. **Tempo** — StatefulSet，S3 backend 指向 Garage `traces` bucket
3. **Loki** — StatefulSet，S3 backend 指向 Garage `logs` bucket；Promtail DaemonSet 采集 stdout
4. **OTEL Collector** — 更新 `otel-collector-config` ConfigMap，追加 `otlp/tempo` exporter
5. **Grafana** — Deployment，数据源：Prometheus + Loki + Tempo
6. **Alert Rules** — 在 `prometheus-config` ConfigMap 中追加 `alerting_rules.yml`

### 0.3 Garage S3 关键配置（参考）

```yaml
# Garage 配置要点（garage.toml）
replication_mode = "none"   # Kind 单节点开发模式
db_engine = "lmdb"
metadata_dir = "/var/lib/garage/meta"
data_dir = "/var/lib/garage/data"

[s3_api]
s3_region = "garage"
api_bind_addr = "0.0.0.0:3900"

[admin]
api_bind_addr = "0.0.0.0:3903"
```

---

---

## 二、采集基线

### 2.1 Metrics

- 所有服务必须在 `:8081/actuator/prometheus` 暴露 Prometheus 指标。
- 所有服务必须开启 `health/info/prometheus` 暴露。
- 自定义业务指标统一使用 `shop_` 前缀，避免和 JVM / Spring / Kafka 内建指标混淆。

### 2.2 Tracing

- 所有服务统一接入 Micrometer Tracing + OTLP 导出。
- Trace ID 必须贯穿 Gateway → BFF → Domain / Worker。
- 所有核心链路异常日志必须带 Trace ID 或 Request ID。

### 2.3 Logging

- 统一使用结构化日志（Logstash 风格）。
- 生产问题定位优先通过：`requestId / traceId / playerId / orderId / subscriptionId` 等业务标识串联。

---

## 三、业务指标命名规范（草案）

### 3.1 命名规则

- Counter：`shop_<domain>_<action>_total`
- Timer / Duration：`shop_<domain>_<action>_duration_seconds`
- Gauge：`shop_<domain>_<resource>_<state>`
- Lag / Delay：`shop_<domain>_<pipeline>_lag_seconds`

### 3.2 标签（Tags）约定

必须优先使用低基数标签：

- `service`
- `operation`
- `result`（success / failure / timeout / retry）
- `channel`（email / webhook / subscription / payment）
- `provider`（stripe / paypal / google / apple）

禁止直接使用高基数字段作为标签：

- `playerId`
- `orderId`
- `requestId`
- `email`

这些字段应进入日志或 trace，而不是 Prometheus label。

### 3.3 推荐新增业务指标

| 指标 | 类型 | 说明 |
|------|------|------|
| `shop_payment_confirm_total` | Counter | 支付确认总数，按 provider/result 维度聚合 |
| `shop_order_checkout_duration_seconds` | Timer | 下单链路耗时 |
| `shop_order_outbox_lag_seconds` | Gauge | order outbox 未发布事件的最大延迟 |
| `shop_wallet_event_lag_seconds` | Gauge | wallet 事件从入库到消费的延迟 |
| `shop_webhook_delivery_total` | Counter | webhook 投递成功/失败/重试次数 |
| `shop_subscription_renew_total` | Counter | 订阅续费成功/失败次数 |
| `shop_search_query_total` | Counter | 搜索请求量 |
| `shop_search_query_duration_seconds` | Timer | 搜索接口耗时 |

---

## 四、核心 SLI / SLO 草案

### 4.1 北向用户链路

| 场景 | SLI | SLO 草案 |
|------|-----|----------|
| 登录 | 成功登录比例 | 30 天滚动成功率 ≥ 99.5% |
| 下单 | `POST /order/create` 成功率 | 30 天滚动成功率 ≥ 99.0% |
| 支付确认 | 支付回调成功率 | 30 天滚动成功率 ≥ 99.9% |
| 买家首页聚合 | `buyer-bff dashboard` P95 | P95 < 800ms |
| 搜索 | 搜索接口 P95 | P95 < 700ms |

### 4.2 南向异步链路

| 场景 | SLI | SLO 草案 |
|------|-----|----------|
| Order / Wallet 事件发布 | outbox 延迟 | P95 < 60s |
| Notification 投递 | 单次发送延迟 | P95 < 120s |
| Webhook 投递 | 首次投递成功率 | 30 天滚动 ≥ 99.0% |
| Subscription 自动续费 | 续费任务成功率 | 30 天滚动 ≥ 99.0% |

### 4.3 平台健康

| 场景 | SLI | SLO 草案 |
|------|-----|----------|
| 服务可用性 | readiness 成功比例 | 30 天滚动 ≥ 99.5% |
| 管理面暴露 | `/actuator/prometheus` 可抓取比例 | 30 天滚动 ≥ 99.9% |
| Kafka 消费积压 | 主题 consumer lag | 非高峰期 10 分钟内恢复到阈值内 |

> 说明：本文件是 v1 草案，先统一“看什么”，再按真实流量逐步收敛阈值。

---

## 五、告警分级

### P1（立即处理）

- 支付确认成功率显著下降
- 下单主链路连续失败
- Gateway / auth-server 大面积不可用
- Kafka 主链路消费者停止消费，且 backlog 持续增长

### P2（值班处理）

- 某单服务 readiness 持续失败
- webhook / notification 重试堆积
- 搜索延迟超过 SLO，持续 15 分钟以上

### P3（工作时间处理）

- 单个非核心后台任务失败
- 指标缺失、抓取配置漂移
- Dashboard 与命名规范未对齐

---

## 六、Runbook 最小模板

每个 P1 / P2 告警至少需要关联以下信息：

1. **触发条件**：哪个指标、哪个阈值、持续多久  
2. **影响范围**：用户下单、支付、搜索、通知还是内部任务  
3. **排查入口**：
   - Prometheus 查询
   - Grafana 面板
   - 关联 Trace
   - 关联日志关键字
4. **止血动作**：重试、扩容、降级、切流或暂停任务  
5. **恢复确认**：指标回落到哪个范围算恢复  

---

## 七、落地要求

- 新增服务必须默认具备指标、追踪、健康检查暴露。
- 新增关键链路必须补充至少一个业务 Counter 和一个延迟指标。
- 任何平台工程或业务交付，如果改变了链路关键行为，必须同步更新 SLO / 告警定义。

---

## 八、官方参考链接

- Spring Boot Observability: https://docs.spring.io/spring-boot/reference/actuator/observability.html
- Micrometer: https://micrometer.io/
- OpenTelemetry: https://opentelemetry.io/docs/
- Prometheus: https://prometheus.io/docs/introduction/overview/
- OpenTelemetry Collector: https://opentelemetry.io/docs/collector/
