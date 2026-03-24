# Shop Platform — Observability, Alerting, and SLO Baseline

> 版本：1.2 | 更新时间：2026-03-24

---

## 一、适用范围

本文定义 `shop-platform` 的统一可观测性基线，包括：

- 指标命名约定
- 核心 SLI / SLO 草案
- 告警分级
- Runbook 最小模板
- traces / logs / metrics / profiles 的默认接入方式

当前仓库的运行基线已经统一为：

- 应用端口：`8080`
- 管理端口：`8081`
- Prometheus 暴露：`/actuator/prometheus`
- 健康检查：`/actuator/health/readiness`、`/actuator/health/liveness`
- Trace 导出：OTLP HTTP → `http://otel-collector:4318/v1/traces`
- Log 导出：OTLP HTTP → `http://otel-collector:4318/v1/logs`
- Profiling：`shop.profiling.*` → Pyroscope

当前 K8s / Kind 默认清单已经落地：

- Prometheus（启用 exemplar storage）
- OpenTelemetry Collector（接收 OTLP traces / logs，补充 `k8sattributes`，过滤健康噪音）
- Tempo（trace 存储，Garage S3 backend）
- Loki（日志聚合，OTLP 原生写入 + structured metadata）
- Grafana（Prometheus + Loki + Tempo + Pyroscope 数据源）
- Pyroscope（持续性能分析）
- Kafka Exporter（consumer lag 指标）
- Prometheus Alert Rules 与 SLO Rules

本地 `docker-compose.yml` 侧重点是让所有应用都具备统一的 OTLP 与 Prometheus 入口；K8s 清单则提供更完整的多信号观测平台。

> **当前重点**已经从“把日志/trace 采进来”转为“把告警、保留策略、容量和 runbook 治理做得更稳”。

---

## 零、当前部署拓扑

### 0.1 当前链路

```
应用服务 (所有服务)
  ├─ /actuator/prometheus  →  Prometheus  →  Grafana
  ├─ OTLP traces           →  OTEL Collector  →  Tempo  →  Grafana
  ├─ OTLP logs             →  OTEL Collector  →  Loki   →  Grafana
  └─ shop.profiling.*      →  Pyroscope       →  Grafana

Tempo、Loki 数据持久化
  └─ Garage S3
      ├─ bucket: tempo-traces
      ├─ bucket: loki-chunks
      └─ bucket: loki-ruler
```

### 0.2 当前实现要点

1. **应用侧统一配置**：所有 Java/Kotlin 运行模块都已拆分 traces / logs OTLP endpoint，并通过 logback appender 输出 OTLP 日志。
2. **关联字段统一**：Gateway 响应默认返回 `X-Request-Id` 与 `X-Trace-Id`；日志、trace、指标优先通过低基数标签 + 高价值业务字段关联排障。
3. **日志采集不再依赖 Promtail**：日志通过 OTLP 直接进 Collector，再写 Loki，减少 sidecar / daemonset 级复杂度。
4. **Prometheus 支持 exemplars**：可把 metrics 面板与 trace 直接串起来。
5. **Grafana 已预置 datasource / dashboard**：Prometheus、Loki、Tempo、Pyroscope 已联通，告警规则和 SLO 规则也在清单中。

### 0.3 仍需继续演进

- Alertmanager 接入与值班通知闭环
- 多环境保留周期、存储成本与容量治理
- 更多 Kafka lag / JVM / virtual thread / business dashboard 的精细化面板
- 生产级高可用部署拓扑与灾备策略

---

## 二、采集基线

### 2.1 Metrics

- 所有服务必须在 `:8081/actuator/prometheus` 暴露 Prometheus 指标。
- 所有服务必须开启 `health/info/prometheus` 暴露。
- 自定义业务指标统一使用 `shop_` 前缀，避免和 JVM / Spring / Kafka 内建指标混淆。

### 2.2 Tracing

- 所有服务统一接入 Micrometer Tracing + OTLP 导出。
- Trace ID 必须贯穿 Gateway → BFF → Domain / Worker。
- Gateway 对外响应统一返回 `X-Trace-Id` 与 `X-Request-Id`，方便客户端和运维人员拿着一次请求去 Grafana / Loki / Tempo 里串联排障。
- 核心链路建议继续透传低基数 baggage / context 字段，例如 `x-user-id`、`x-order-id`。

### 2.3 Logging

- 所有 Java/Kotlin 服务统一使用结构化 JSON 控制台日志，并通过 OpenTelemetry logback appender 发送 OTLP logs。
- Collector 负责把日志写入 Loki，并限制只把低基数 resource attributes 提升为 label。
- 生产问题定位优先通过：`requestId / traceId / playerId / userId / orderId / subscriptionId` 等业务标识串联。

### 2.4 Profiling 与跨信号关联

- `shop.profiling.enabled=true` 时，应用会把 profile 发送到 Pyroscope。
- Grafana 中已打通 trace ↔ log ↔ profile 的基础关联关系，可从一次慢请求继续下钻到日志与 profile。
- 只有对排障和容量判断有价值的字段才应该进入 profile / log / trace 上下文，避免高基数污染指标面。

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
