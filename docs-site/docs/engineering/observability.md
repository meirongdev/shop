---
title: 可观测性
---

# 可观测性

## 本地访问

启动集群后，通过 `make local-access` 开启端口转发：

```bash
make local-access &
```

| 工具 | 地址 | 用途 |
|------|------|------|
| Grafana | http://127.0.0.1:13000 | 日志、指标、链路追踪统一看板 |
| Prometheus | http://127.0.0.1:19090 | 原始指标查询 |

Grafana 预置了以下数据源（无需额外配置）：
- **Prometheus** — `http://prometheus:9090`
- **Loki** — `http://loki:3100`
- **Tempo** — `http://tempo:3200`
- **Pyroscope** — `http://pyroscope:4040`

## 当前已落地的观测栈

| 组件 | 作用 | 当前状态 |
|---|---|---|
| Prometheus | 指标抓取、SLI/SLO、告警规则 | 已启用 exemplar storage |
| OpenTelemetry Collector | 接收 OTLP traces / logs、补充 k8s 属性、过滤健康噪音 | 已在 `k8s/infra/base.yaml` 中落地 |
| Tempo | Trace 存储与查询 | 已部署，本地 filesystem 存储（开发环境） |
| Loki | 日志聚合与查询 | 已通过 OTLP 原生接入，filesystem 存储（开发环境） |
| Grafana | 统一看板与跨信号跳转 | 已预置 Prometheus / Loki / Tempo / Pyroscope datasource |
| Pyroscope | 持续性能分析 | 已接入 Grafana |
| Kafka Exporter | consumer lag 指标 | 已纳入观测清单 |

## 数据流详解

本节说明 Grafana 如何获取所有可观测性数据——指标（Metrics）、日志（Logs）、链路追踪（Traces）和性能剖析（Profiles）。

### 总体架构

```
                         ┌───────────────────────────────────────────────────────────┐
                         │                    Grafana (3000)                         │
                         │  ┌──────────┐  ┌──────┐  ┌──────┐  ┌─────────┐           │
                         │  │Prometheus│  │ Loki │  │Tempo │  │Pyroscope│           │
                         │  │datasource│  │  ds  │  │  ds  │  │   ds    │           │
                         │  └────┬─────┘  └──┬───┘  └──┬───┘  └────┬────┘           │
                         └───────┼───────────┼─────────┼───────────┼────────────────┘
                                 │           │         │           │
                    ┌────────────▼──┐   ┌────▼────┐  ┌─▼──────┐  ┌▼────────┐
                    │  Prometheus   │   │  Loki   │  │ Tempo  │  │Pyroscope│
                    │  (9090)       │   │ (3100)  │  │ (3200) │  │ (4040)  │
                    └───┬───────┬──┘   └────▲────┘  └───▲────┘  └─────────┘
                        │       │           │           │
                   Pull │  Pull │      Push │      Push │
                   /actuator    │   (OTLP HTTP)  (OTLP gRPC)
                   /prometheus  │           │           │
                        │       │      ┌────┴───────────┴────┐
                        │       │      │  OTEL Collector     │
                        │       │      │  (4317 gRPC)        │
                        │       │      │  (4318 HTTP)        │
                        │       │      └──────────▲──────────┘
                        │       │                 │
                        │       │            OTLP │ (traces + logs)
                ┌───────▼──┐  ┌─▼─────────┐  ┌───┴──────────────┐
                │ Spring    │  │ Kafka     │  │  Spring Boot     │
                │ Boot Apps │  │ Exporter  │  │  Apps            │
                │ :8081     │  │ :9308     │  │  (logback OTLP   │
                │           │  │           │  │   + OTLP tracing)│
                └───────────┘  └───────────┘  └──────────────────┘
```

### 指标（Metrics）— Prometheus 拉取模式

**数据流**: Spring Boot Apps → Prometheus → Grafana

1. **Spring Boot 暴露指标**：每个服务通过 `management.server.port: 8081` 暴露 `/actuator/prometheus`，提供 Micrometer 指标。
2. **Prometheus 主动拉取**：Prometheus 按 `scrape_interval: 15s` 拉取所有服务的指标端点。
3. **Grafana 查询 Prometheus**：Dashboard 面板通过 PromQL 查询 Prometheus。

**Prometheus 抓取目标**（定义在 `k8s/infra/base.yaml`）：

| Job 名称 | 目标 | 端口 | 路径 |
|---|---|---|---|
| `shop-apps` | 15 个微服务 | 8081 | `/actuator/prometheus` |
| `kafka-exporter` | kafka-exporter | 9308 | `/metrics` |
| `observability` | tempo, loki, grafana, pyroscope | 各自端口 | `/metrics` |

**指标分类**：

| 类别 | 指标示例 | 来源 |
|---|---|---|
| HTTP 请求 | `http_server_requests_seconds_count/bucket` | Spring Boot Micrometer 自动生成 |
| JVM | `jvm_memory_used_bytes`, `jvm_threads_live_threads`, `jvm_gc_pause_seconds_max` | Micrometer JVM Metrics |
| 连接池 | `hikaricp_connections_active/max/pending` | HikariCP Micrometer |
| 断路器 | `resilience4j_circuitbreaker_state` | Resilience4j Micrometer |
| Kafka | `kafka_consumergroup_lag` | Kafka Exporter |
| 业务指标 | `shop_order_created_total`, `shop_payment_success_total`, `shop_wallet_deposit_total` | 各服务自定义 Counter/Timer |

**已注册的自定义业务指标**：

| 指标名称 | 所在服务 | 类型 | 描述 |
|---|---|---|---|
| `shop_order_created_total` | order-service | Counter | 创建订单数 |
| `shop_payment_confirm_total` | order-service | Counter | 支付确认（带 `result` tag：success/failure/rejected/idempotent） |
| `shop_payment_success_total` | wallet-service | Counter | 钱包支付成功数 |
| `shop_payment_duration_seconds` | wallet-service | Timer | 支付处理耗时 |
| `shop_wallet_deposit_total` | wallet-service | Counter | 钱包充值数 |
| `shop_coupon_validation_total` | promotion-service | Counter | 优惠券验证（带 `result` tag：success/failure） |
| `shop_loyalty_points_earned_total` | loyalty-service | Counter | 积分获取数 |
| `shop_loyalty_points_redeemed_total` | loyalty-service | Counter | 积分兑换数 |
| `shop_activity_participations_total` | activity-service | Counter | 活动参与数 |
| `shop_activity_wins_total` | activity-service | Counter | 活动中奖数 |
| `shop_order_checkout_duration_seconds` | buyer-bff | Timer | 结账全链路耗时 |
| `shop_search_query_total` | search-service | Counter | 搜索查询数 |
| `shop_search_query_duration_seconds` | search-service | Timer | 搜索查询耗时 |

### 日志（Logs）— OTLP 推送模式

**数据流**: Spring Boot Apps → OTEL Collector → Loki → Grafana

1. **Logback OTLP Appender**：每个 Spring Boot 服务通过 `opentelemetry-logback-appender` 将结构化日志以 OTLP 协议发送到 OTEL Collector（`otel-collector:4318`）。
2. **OTEL Collector 处理**：Collector 接收 OTLP 日志，通过 `attributes` processor 过滤健康检查噪音日志，然后通过 `otlphttp/loki` exporter 转发到 Loki。
3. **Loki 存储与索引**：Loki 在 `/otlp` 端点接收 OTLP 日志，按 `service.name` 和 `deployment.environment.name` 建立标签索引。
4. **Grafana 查询 Loki**：通过 LogQL 查询特定服务的日志，支持按 `traceId` 关联 Tempo 中的链路。

**配置关键点**：
- OTEL Collector 配置：`k8s/infra/base.yaml` 中 `otel-collector-config` ConfigMap
- Loki OTLP 配置：`k8s/observability/loki/loki-configmap.yaml` 中 `otlp_config` 部分
- 服务端 Logback 配置：`shop-common` 依赖 `opentelemetry-logback-appender-1.0`

**日志格式**：
```json
{
  "timestamp": "2024-01-01T00:00:00.000Z",
  "level": "INFO",
  "logger": "dev.meirong.shop.order.service.OrderApplicationService",
  "message": "Order created",
  "traceId": "abc123...",
  "spanId": "def456...",
  "service.name": "order-service"
}
```

### 链路追踪（Traces）— OTLP 推送模式

**数据流**: Spring Boot Apps → OTEL Collector → Tempo → Grafana

1. **Spring Boot OTLP Tracing**：每个服务通过 `micrometer-tracing-bridge-otel` + `opentelemetry-exporter-otlp` 自动为 HTTP 请求、数据库查询、Kafka 消息等生成 span，发送到 OTEL Collector。
2. **OTEL Collector 处理**：Collector 通过 `otlp/tempo` exporter 将 trace 转发到 Tempo（gRPC 4317 端口）。
3. **Tempo 存储**：Tempo 存储完整的 trace 数据，并通过 `metrics-generator` 生成 service-graph 和 span-metrics 指标写入 Prometheus。
4. **Grafana 查询 Tempo**：支持按 TraceID 查找、Service Map 可视化、Node Graph 拓扑图。

**Trace 关联配置**（`k8s/observability/grafana/grafana-datasources-configmap.yaml`）：
- **Tempo → Loki**：点击 trace 中的 span，自动跳转到该时间窗口 + service 的日志
- **Tempo → Pyroscope**：点击 span，查看对应时间段的 CPU profile
- **Prometheus → Tempo**：metric 面板中的 exemplar 标记可直接跳转到对应 trace
- **Loki → Tempo**：日志中提取 `traceId`，可跳转到完整链路

### 性能剖析（Profiles）— Pyroscope

**数据流**: Spring Boot Apps → Pyroscope → Grafana

- 服务通过 `PYROSCOPE_ENABLED=true` 环境变量启用（默认关闭）。
- 启用后持续上送 CPU / heap profile 到 Pyroscope。
- Grafana 通过 Pyroscope datasource 查看火焰图。

### Kafka 指标 — Kafka Exporter

**数据流**: Kafka → Kafka Exporter → Prometheus → Grafana

- `kafka-exporter` 定期连接 Kafka 集群，暴露 consumer lag 等指标。
- Prometheus 在 `kafka-exporter:9308` 抓取。
- Dashboard 面板查询 `kafka_consumergroup_lag` 监控消费延迟。

## 预置 Dashboard

Grafana 预置了三个 Dashboard（定义在 `k8s/observability/grafana/grafana-dashboards-configmap.yaml`）：

### Service Health（服务健康）

| 面板 | 指标 | 说明 |
|---|---|---|
| HTTP 5xx Error Rate | `http_server_requests_seconds_count` (status=5xx) | 各服务 5xx 错误率 |
| P99 Latency | `http_server_requests_seconds_bucket` | HTTP 请求 P99 延迟 |
| JVM Heap Usage | `jvm_memory_used_bytes / jvm_memory_max_bytes` | 堆内存使用率 |
| Kafka Consumer Lag | `kafka_consumergroup_lag` | 消费者组延迟 |
| Circuit Breaker State | `resilience4j_circuitbreaker_state` | 断路器状态 |
| HikariCP Connections | `hikaricp_connections_active/max/pending` | 数据库连接池 |
| GC Pause Max | `jvm_gc_pause_seconds_max` | GC 最大暂停时间 |
| Thread Starts Rate | `jvm_threads_started_total` | 线程创建速率 |

### Business Overview（业务大盘）

| 面板 | 指标 | 说明 |
|---|---|---|
| Orders Created | `shop_order_created_total` | 订单创建速率 |
| Payment Success Rate | `shop_payment_success_total` / `shop_payment_confirm_total` | 支付成功率 |
| Checkout P99 Latency | `shop_order_checkout_duration_seconds_bucket` | 结账 P99 延迟 |
| Coupon Validations | `shop_coupon_validation_total` (result=success) | 优惠券验证成功速率 |
| Loyalty Points Earned | `shop_loyalty_points_earned_total` | 积分获取速率 |
| Activity Participations | `shop_activity_participations_total` | 活动参与速率 |
| Wallet Deposits | `shop_wallet_deposit_total` | 钱包充值速率 |

### JVM & Virtual Threads（JVM 与虚拟线程）

| 面板 | 指标 | 说明 |
|---|---|---|
| Live Threads | `jvm_threads_live_threads` | 活跃线程数（按 daemon 类型） |
| HikariCP Active Ratio | `hikaricp_connections_active / hikaricp_connections_max` | 连接池利用率 |
| GC Pause Max | `jvm_gc_pause_seconds_max` | GC 暂停时间 |
| Memory Pool Usage | `jvm_memory_used_bytes` (G1 Eden / Old Gen / Metaspace) | 内存池分布 |
| Pending Connections | `hikaricp_connections_pending` | 等待连接数 |
| Thread Start Rate | `jvm_threads_started_total` | 线程创建速率（含虚拟线程） |

## 一次请求如何被串起来

1. Gateway 生成并回传 `X-Request-Id`、`X-Trace-Id`。
2. 应用统一输出结构化 JSON 日志，并通过 logback appender 发送 OTLP logs。
3. 应用统一通过 OTLP 发送 traces，必要时再通过 `shop.profiling.*` 上送 profile。
4. Collector 把 traces 发到 Tempo，把 logs 发到 Loki，并补充 k8s metadata。
5. Grafana 把 metrics、trace、log、profile 关联起来，便于从一张图继续下钻。

## 平台默认要求

- 所有服务都暴露 `:8081/actuator/prometheus`。
- 新增关键链路至少补一个业务指标和一个延迟指标。
- 高基数字段放进日志/trace，不要直接做成 Prometheus label。
- 如果接口要给调用方排障，优先暴露 `X-Trace-Id` / `X-Request-Id` 这类关联头，而不是返回内部实现细节。

## 新增自定义指标指南

在对应 `@Service` 类中注入 `MeterRegistry`，使用 Micrometer API 注册 Counter / Timer：

```java
// Counter 示例
Counter.builder("shop_my_business_total")
        .description("描述信息")
        .tag("service", "my-service")
        .register(meterRegistry).increment();

// Timer 示例
Timer.Sample sample = Timer.start(meterRegistry);
// ... 业务逻辑 ...
sample.stop(Timer.builder("shop_my_operation_duration_seconds")
        .description("描述信息")
        .register(meterRegistry));
```

命名规范：
- 前缀统一用 `shop_`
- Counter 后缀 `_total`
- Timer 后缀 `_duration_seconds`（Micrometer 自动加 `_bucket` / `_count` / `_sum`）
- Prometheus 标签避免高基数（不要用 userId / orderId 这类无界值）

## 仍要继续演进的方向

- Alertmanager 与值班通知闭环。
- 多环境保留周期、容量与成本治理。
- 更丰富的 Kafka lag、虚拟线程、JVM 和业务大盘。

## 参考

- 权威：`docs/OBSERVABILITY-ALERTING-SLO.md`
- Spring Observability: https://docs.spring.io/spring-boot/reference/actuator/observability.html
