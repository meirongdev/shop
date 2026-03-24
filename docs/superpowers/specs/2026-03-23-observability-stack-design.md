# Shop Platform — 可观测性平台 设计文档

> 版本：1.0 | 日期：2026-03-23

---

## 一、概述

### 1.1 目标

为 Shop Platform 微服务集群建立完整的可观测性闭环，实现以下核心能力：

1. **全链路追踪可视化**：所有服务的 Trace 数据从 OTEL Collector 写入 Tempo，在 Grafana 中可按 TraceID 定位请求路径。
2. **日志集中聚合**：所有 Pod 的结构化 JSON 日志通过 Promtail 汇入 Loki，支持按 service / traceId / level 过滤。
3. **业务指标可度量**：在技术指标（JVM、HTTP、DB）基础上补齐 `shop_*` 业务 Counter/Timer，在 Grafana Dashboard 呈现业务健康状况。
4. **告警规则落地**：Prometheus Alert Rules 覆盖 P1（高错误率、服务不可用）和 P2（高延迟、Kafka 消费积压）两级。
5. **Metrics-Logs-Traces 三维关联**：Grafana Explore 支持从 Metric 跳转到对应 Log，再跳转到 Trace，实现端到端故障定位。

当前最紧迫的 gap：
- OTEL Collector 仅使用 `debug` exporter，Trace 数据未落地。
- Tempo / Loki / Grafana / Garage S3 尚未部署。
- 业务指标 `shop_*` 缺失，Kafka consumer lag 未暴露。

### 1.2 设计原则

**Three Pillars 统一闭环原则：**

| 支柱 | 采集方式 | 存储 | 可视化 |
|------|----------|------|--------|
| Metrics | Micrometer → Prometheus scrape `/actuator/prometheus` | Prometheus（本地 TSDB） | Grafana |
| Logs | Spring Boot Structured Logging (logstash JSON) → Promtail → Loki | Loki + Garage S3 | Grafana Explore |
| Traces | Spring Boot OTEL SDK → OTEL Collector → Tempo (OTLP/gRPC) | Tempo + Garage S3 | Grafana Explore |

**其他原则：**

- **零代码侵入优先**：Structured Logging、TraceID 注入均通过 `application.yml` 配置，不修改业务代码。
- **统一 Namespace**：所有可观测性组件部署在 `shop` namespace，与应用服务共存，降低 Kind 本地环境复杂度。
- **成本优先于完备**：本地 Kind 环境，存储后端用 Garage S3（轻量自托管），不引入 Elasticsearch。
- **TraceID 贯穿三柱**：Loki 日志字段包含 `traceId`，Grafana 配置 Derived Fields，点击 traceId 直接跳转 Tempo。

### 1.3 技术选型表

| 组件 | 选型 | 版本 | 说明 |
|------|------|------|------|
| Metrics 采集 | Micrometer + Prometheus | Prometheus v3.4.2 | 已落地 |
| Trace 采集 | Spring Boot OTEL SDK (micrometer-tracing-bridge-otel) | 3.5.x | 已落地，sampling=1.0 |
| Trace 管道 | OpenTelemetry Collector Contrib | 0.127.0 | 已落地，需更换 exporter |
| Trace 存储 | Grafana Tempo | 2.7.x | 待部署 |
| Log 采集 | Promtail | 3.4.x | 待部署 |
| Log 存储 | Grafana Loki | 3.4.x | 待部署 |
| 对象存储 | Garage S3 | 1.0.x | 待部署，作为 Loki + Tempo 后端 |
| 可视化 | Grafana | 11.x | 待部署 |
| Kafka 监控 | kafka-exporter (danielqsj) | 1.7.x | 待部署 |
| Structured Logging | Spring Boot 3.4+ built-in (logstash format) | 3.5.x | 已落地（全服务已配置） |

---

## 二、架构

### 2.1 整体数据流

```
┌─────────────────────────────────────────────────────────────────┐
│                    Shop Platform Services                        │
│  api-gateway / buyer-bff / seller-bff / *-service (15 services) │
│                                                                  │
│  [Micrometer]     [Spring OTEL SDK]    [Structured Logging]     │
│       │                  │                      │               │
│  /actuator/prometheus   OTLP/HTTP           stdout (JSON)       │
└───────┼──────────────────┼──────────────────────┼───────────────┘
        │                  │                      │
        │           ┌──────▼──────┐         ┌─────▼──────┐
        │           │OTEL Collector│         │  Promtail   │
        │           │(0.127.0)    │         │ (DaemonSet) │
        │           └──────┬──────┘         └─────┬───────┘
        │                  │                      │
        │           OTLP/gRPC (4317)         HTTP push
        │                  │                      │
        ▼                  ▼                      ▼
┌──────────────┐  ┌──────────────┐    ┌──────────────────┐
│  Prometheus  │  │    Tempo     │    │       Loki        │
│  (scrape     │  │  (trace      │    │  (log aggregation)│
│   :9090)     │  │   storage)   │    │                  │
└──────┬───────┘  └──────┬───────┘    └────────┬─────────┘
       │                 │                      │
       │          ┌──────▼──────────────────────▼──────┐
       │          │           Garage S3                 │
       │          │  bucket: tempo-traces               │
       │          │  bucket: loki-chunks + loki-ruler   │
       │          └──────────────────────────────────────┘
       │
       └──────────────────────────┐
                                  ▼
                        ┌─────────────────┐
                        │     Grafana      │
                        │  Datasources:    │
                        │  - Prometheus    │
                        │  - Loki          │
                        │  - Tempo         │
                        └─────────────────┘
```

**端口映射（Kind NodePort / mirrord 访问）：**

| 服务 | 内部端口 | 访问路径 |
|------|----------|---------|
| Prometheus | 9090 | `http://prometheus:9090` |
| Grafana | 3000 | `http://grafana:3000` |
| Tempo OTLP gRPC | 4317 | `tempo:4317` (内部) |
| Tempo HTTP API | 3200 | `http://tempo:3200` |
| Loki HTTP API | 3100 | `http://loki:3100` |
| Garage S3 | 3900 | `http://garage:3900` |
| OTEL Collector gRPC | 4317 | `otel-collector:4317` |

### 2.2 Garage S3 作为 Loki + Tempo 后端存储

Garage 是一个轻量级自托管 S3 兼容对象存储，适合 Kind 本地环境，避免依赖 AWS S3 或 MinIO 复杂配置。

**Bucket 规划：**

| Bucket | 用途 | 预估数据量（本地开发） |
|--------|------|----------------------|
| `tempo-traces` | Tempo blocks 存储 | ~1-5 GB/day |
| `loki-chunks` | Loki log chunks | ~500 MB/day |
| `loki-ruler` | Loki ruler/alert rules | < 10 MB |

**Garage 部署模式：** 单节点（Kind 不支持多节点），数据目录挂载 `hostPath`，使用 `ReadWriteOnce` PVC。

**认证配置：**
- Access Key ID: `garageshop`
- Secret Access Key: 通过 K8s Secret 注入
- Endpoint: `http://garage:3900`
- Region: `garage` (自定义，Garage 支持任意 region 名)

### 2.3 Spring Boot 3.5 Structured Logging → Promtail → Loki

**当前状态**：所有 15 个服务的 `application.yml` 已配置：

```yaml
logging:
  structured:
    format:
      console: logstash
```

该配置使 Spring Boot 3.4+ 内置的 Logstash JSON 格式输出到 stdout，格式示例：

```json
{
  "@timestamp": "2026-03-23T10:00:00.000Z",
  "@version": "1",
  "message": "Order created successfully",
  "logger_name": "dev.meirong.shop.order.service.OrderService",
  "thread_name": "virtual-12",
  "level": "INFO",
  "level_value": 20000,
  "traceId": "4bf92f3577b34da6a3ce929d0e0e4736",
  "spanId": "00f067aa0ba902b7",
  "service": "order-service"
}
```

**Promtail 采集策略：**

Promtail 以 DaemonSet 部署，通过 Kubernetes Service Discovery 自动发现 `shop` namespace 下所有 Pod，采集 stdout/stderr。

**关键 Label 提取（pipeline_stages）：**
- `level` → 从 JSON 字段 `level` 提取，用于 Loki 日志级别过滤
- `traceId` → 提取后存入标签，Grafana Derived Fields 关联到 Tempo
- `service` → 从 Pod label `app` 获取（或从 JSON `service` 字段提取）

---

## 三、组件配置

### 3.1 OTEL Collector 配置（替换 debug exporter）

**当前配置**（`k8s/infra/base.yaml` 中 ConfigMap `otel-collector-config`）仅有 debug exporter，需替换为：

```yaml
receivers:
  otlp:
    protocols:
      grpc:
        endpoint: 0.0.0.0:4317
      http:
        endpoint: 0.0.0.0:4318

processors:
  batch:
    timeout: 5s
    send_batch_size: 512
  memory_limiter:
    check_interval: 1s
    limit_mib: 256

exporters:
  otlp/tempo:
    endpoint: tempo:4317
    tls:
      insecure: true
  loki:
    endpoint: http://loki:3100/loki/api/v1/push
    default_labels_enabled:
      exporter: false
      job: true
  debug:
    verbosity: basic

service:
  pipelines:
    traces:
      receivers: [otlp]
      processors: [memory_limiter, batch]
      exporters: [otlp/tempo]
    logs:
      receivers: [otlp]
      processors: [memory_limiter, batch]
      exporters: [loki]
```

> 注意：Spring Boot 的日志通过 Promtail 采集（从 Pod stdout），不经过 OTEL Collector logs pipeline。OTEL logs pipeline 预留给未来接入 OpenTelemetry Logs SDK 使用。

### 3.2 Promtail 配置（采集 Pod 日志 → Loki）

```yaml
# k8s/infra/promtail.yaml — ConfigMap: promtail-config
server:
  http_listen_port: 9080
  grpc_listen_port: 0

positions:
  filename: /tmp/positions.yaml

clients:
  - url: http://loki:3100/loki/api/v1/push

scrape_configs:
  - job_name: kubernetes-pods
    kubernetes_sd_configs:
      - role: pod
        namespaces:
          names: [shop]
    relabel_configs:
      - source_labels: [__meta_kubernetes_pod_label_app]
        target_label: app
      - source_labels: [__meta_kubernetes_namespace]
        target_label: namespace
      - source_labels: [__meta_kubernetes_pod_name]
        target_label: pod
      - source_labels: [__meta_kubernetes_pod_container_name]
        target_label: container
      # 仅采集主应用容器（排除 sidecar）
      - source_labels: [__meta_kubernetes_pod_annotation_shop_observe]
        action: keep
        regex: "true|"
    pipeline_stages:
      # 解析 Spring Boot logstash JSON 格式
      - json:
          expressions:
            level: level
            traceId: traceId
            spanId: spanId
            logger: logger_name
            message: message
      # 将 level 提升为 Loki label（高基数字段不做 label）
      - labels:
          level:
      # 使用日志中的 timestamp
      - timestamp:
          source: "@timestamp"
          format: RFC3339Nano
          fallback_formats:
            - "2006-01-02T15:04:05.000Z"
      # 将 traceId 写入日志结构化元数据（Loki 3.x structured metadata）
      - structured_metadata:
          traceId:
          spanId:
```

**DaemonSet RBAC：** Promtail 需要 `get/list/watch` pods 权限，需创建对应 ServiceAccount、ClusterRole、ClusterRoleBinding（限定 `shop` namespace）。

### 3.3 Loki 配置（Garage S3 后端）

```yaml
# k8s/infra/loki.yaml — ConfigMap: loki-config
auth_enabled: false

server:
  http_listen_port: 3100
  grpc_listen_port: 9095

common:
  ring:
    instance_addr: 127.0.0.1
    kvstore:
      store: inmemory
  replication_factor: 1
  path_prefix: /var/loki

schema_config:
  configs:
    - from: "2026-01-01"
      store: tsdb
      object_store: s3
      schema: v13
      index:
        prefix: loki_index_
        period: 24h

storage_config:
  tsdb_shipper:
    active_index_directory: /var/loki/tsdb-index
    cache_location: /var/loki/tsdb-cache
  aws:
    s3: http://garageshop:${GARAGE_SECRET_KEY}@garage:3900/loki-chunks
    region: garage
    s3forcepathstyle: true
    insecure: true

ruler:
  storage:
    type: s3
    s3:
      endpoint: http://garage:3900
      bucketnames: loki-ruler
      access_key_id: garageshop
      secret_access_key: ${GARAGE_SECRET_KEY}
      region: garage
      s3forcepathstyle: true
      insecure: true

compactor:
  working_directory: /var/loki/compactor
  delete_request_store: s3

limits_config:
  retention_period: 168h   # 7 days（本地开发）
  ingestion_rate_mb: 16
  ingestion_burst_size_mb: 32

query_range:
  results_cache:
    cache:
      embedded_cache:
        enabled: true
        max_size_mb: 100
```

### 3.4 Tempo 配置（Garage S3 后端）

```yaml
# k8s/infra/tempo.yaml — ConfigMap: tempo-config
stream_over_http_enabled: true

server:
  http_listen_port: 3200
  grpc_listen_port: 9096
  log_level: info

distributor:
  receivers:
    otlp:
      protocols:
        grpc:
          endpoint: 0.0.0.0:4317
        http:
          endpoint: 0.0.0.0:4318

ingester:
  max_block_duration: 5m

compactor:
  compaction:
    block_retention: 48h   # 本地开发保留 2 天

storage:
  trace:
    backend: s3
    s3:
      endpoint: garage:3900
      bucket: tempo-traces
      access_key: garageshop
      secret_key: ${GARAGE_SECRET_KEY}
      insecure: true
      forcepathstyle: true
    wal:
      path: /var/tempo/wal
    local:
      path: /var/tempo/blocks

querier:
  frontend_worker:
    frontend_address: tempo:9096

metrics_generator:
  registry:
    external_labels:
      source: tempo
      cluster: shop-kind
  storage:
    path: /var/tempo/generator/wal
    remote_write:
      - url: http://prometheus:9090/api/v1/write
        send_exemplars: true

overrides:
  defaults:
    metrics_generator:
      processors: [service-graphs, span-metrics]
```

### 3.5 Grafana Datasource 配置（Prometheus + Loki + Tempo）

```yaml
# k8s/infra/grafana.yaml — ConfigMap: grafana-datasources
# 文件路径：/etc/grafana/provisioning/datasources/datasources.yaml
apiVersion: 1
datasources:
  - name: Prometheus
    type: prometheus
    uid: prometheus
    url: http://prometheus:9090
    access: proxy
    isDefault: true
    jsonData:
      timeInterval: 15s
      exemplarTraceIdDestinations:
        - name: traceID
          datasourceUid: tempo

  - name: Loki
    type: loki
    uid: loki
    url: http://loki:3100
    access: proxy
    jsonData:
      derivedFields:
        - matcherRegex: '"traceId":"(\w+)"'
          name: TraceID
          url: "$${__value.raw}"
          datasourceUid: tempo
          urlDisplayLabel: "View Trace in Tempo"

  - name: Tempo
    type: tempo
    uid: tempo
    url: http://tempo:3200
    access: proxy
    jsonData:
      tracesToLogsV2:
        datasourceUid: loki
        filterByTraceID: true
        filterBySpanID: false
        customQuery: true
        query: '{app="${__span.tags.service.name}"} | json | traceId="${__trace.traceId}"'
      tracesToMetrics:
        datasourceUid: prometheus
        queries:
          - name: "Request Rate"
            query: 'rate(http_server_requests_seconds_count{job="${__span.tags.service.name}"}[1m])'
      serviceMap:
        datasourceUid: prometheus
      nodeGraph:
        enabled: true
      search:
        hide: false
      lokiSearch:
        datasourceUid: loki
```

---

## 四、业务指标埋点规范

### 4.1 命名规范 shop_*

所有业务指标遵循以下命名规范：

```
shop_<domain>_<action>_<unit_or_suffix>
```

规则：
- **前缀**：`shop_` —— 区分业务指标与技术指标（`jvm_`, `http_`, `spring_`）
- **domain**：服务领域，如 `order`, `payment`, `coupon`, `wallet`, `loyalty`
- **action**：动词，如 `created`, `failed`, `redeemed`, `expired`, `processed`
- **suffix**：Prometheus 约定后缀
  - Counter：`_total`
  - Timer：`_seconds`（Micrometer 自动添加 `_count`, `_sum`, `_bucket`）
  - Gauge：无后缀或 `_active`, `_size`

**标准 Tags：**

| Tag | 说明 | 示例值 |
|-----|------|--------|
| `service` | 服务名（自动由 `spring.application.name` 注入） | `order-service` |
| `status` | 操作结果 | `success`, `failed`, `timeout` |
| `payment_method` | 支付方式（仅支付相关） | `wallet`, `credit_card` |
| `coupon_type` | 优惠券类型（仅促销相关） | `fixed`, `percentage` |

**高基数标签警告**：禁止将 `userId`, `orderId`, `productId` 等 ID 类字段作为 Tag，防止 Prometheus TSDB 基数爆炸。

### 4.2 关键指标清单

**Order Service（order-service）：**

| 指标名 | 类型 | Tags | 说明 |
|--------|------|------|------|
| `shop_order_created_total` | Counter | `status` | 订单创建成功/失败总数 |
| `shop_order_payment_duration_seconds` | Timer | `payment_method` | 支付处理耗时 |
| `shop_order_cancelled_total` | Counter | `reason` | 订单取消总数（reason: timeout/user/system） |
| `shop_order_autocomplete_total` | Counter | - | 自动完成订单数 |

**Wallet Service（wallet-service）：**

| 指标名 | 类型 | Tags | 说明 |
|--------|------|------|------|
| `shop_payment_processed_total` | Counter | `status`, `method` | 支付处理总数 |
| `shop_payment_failed_total` | Counter | `reason` | 支付失败总数（reason: insufficient/timeout/fraud） |
| `shop_wallet_recharge_total` | Counter | `status` | 钱包充值总数 |
| `shop_wallet_balance_gauge` | Gauge | - | 当前活跃钱包总余额（用于异常检测） |

**Promotion Service（promotion-service）：**

| 指标名 | 类型 | Tags | 说明 |
|--------|------|------|------|
| `shop_coupon_redeemed_total` | Counter | `coupon_type`, `status` | 优惠券核销总数 |
| `shop_coupon_invalid_total` | Counter | `reason` | 无效券使用尝试（expired/not_found/already_used） |
| `shop_promotion_active_count` | Gauge | - | 当前进行中活动数 |

**Loyalty Service（loyalty-service）：**

| 指标名 | 类型 | Tags | 说明 |
|--------|------|------|------|
| `shop_points_awarded_total` | Counter | `event_type` | 积分发放总数 |
| `shop_points_redeemed_total` | Counter | `status` | 积分兑换总数 |
| `shop_points_expired_total` | Counter | - | 积分过期总数 |

**Notification Service（notification-service）：**

| 指标名 | 类型 | Tags | 说明 |
|--------|------|------|------|
| `shop_notification_sent_total` | Counter | `channel`, `status` | 通知发送总数（channel: email/sms/push） |
| `shop_notification_failed_total` | Counter | `channel`, `reason` | 通知发送失败总数 |

### 4.3 Micrometer 使用示例

**Counter 示例（订单创建）：**

```java
@Service
public class OrderService {
    private final Counter orderCreatedCounter;
    private final Counter orderCreatedFailedCounter;

    public OrderService(MeterRegistry meterRegistry) {
        this.orderCreatedCounter = Counter.builder("shop.order.created")
            .description("Total number of orders created successfully")
            .tag("status", "success")
            .register(meterRegistry);
        this.orderCreatedFailedCounter = Counter.builder("shop.order.created")
            .description("Total number of order creation failures")
            .tag("status", "failed")
            .register(meterRegistry);
    }

    public OrderResponse createOrder(CreateOrderRequest request) {
        try {
            // ... business logic
            orderCreatedCounter.increment();
            return response;
        } catch (Exception e) {
            orderCreatedFailedCounter.increment();
            throw e;
        }
    }
}
```

**Timer 示例（支付耗时）：**

```java
private final Timer paymentTimer;

public PaymentService(MeterRegistry meterRegistry) {
    this.paymentTimer = Timer.builder("shop.order.payment.duration")
        .description("Time taken to process payment")
        .publishPercentiles(0.5, 0.95, 0.99)
        .publishPercentileHistogram()
        .register(meterRegistry);
}

public void processPayment(Order order) {
    paymentTimer.record(() -> {
        // ... payment logic
    });
}
```

**Gauge 示例（活跃促销数）：**

```java
@PostConstruct
public void registerGauges() {
    Gauge.builder("shop.promotion.active.count", promotionRepository,
            repo -> repo.countByStatus(PromotionStatus.ACTIVE))
        .description("Number of currently active promotions")
        .register(meterRegistry);
}
```

> Micrometer 命名约定：Java 代码中使用点分隔（`shop.order.created`），Prometheus 输出时自动转为下划线（`shop_order_created_total`）。

---

## 五、Prometheus Alert Rules

### 5.1 P1 告警规则（高错误率、服务不可用）

```yaml
# k8s/infra/prometheus-alerts.yaml — PrometheusRule（或直接加入 prometheus.yml）
groups:
  - name: shop.p1.availability
    interval: 30s
    rules:
      # 服务 Up 检查（15 分钟内未上报指标）
      - alert: ShopServiceDown
        expr: up{job="shop-apps"} == 0
        for: 2m
        labels:
          severity: P1
        annotations:
          summary: "Service {{ $labels.instance }} is down"
          description: "Service {{ $labels.instance }} has been down for more than 2 minutes."

      # HTTP 5xx 错误率 > 5%
      - alert: ShopHighErrorRate
        expr: |
          (
            sum by (job, instance) (
              rate(http_server_requests_seconds_count{status=~"5..", job="shop-apps"}[5m])
            )
            /
            sum by (job, instance) (
              rate(http_server_requests_seconds_count{job="shop-apps"}[5m])
            )
          ) > 0.05
        for: 3m
        labels:
          severity: P1
        annotations:
          summary: "High 5xx error rate on {{ $labels.instance }}"
          description: "Error rate is {{ $value | humanizePercentage }} on {{ $labels.instance }}."

      # 支付失败率 > 10%
      - alert: ShopHighPaymentFailureRate
        expr: |
          (
            rate(shop_payment_failed_total[5m])
            /
            rate(shop_payment_processed_total[5m])
          ) > 0.10
        for: 5m
        labels:
          severity: P1
        annotations:
          summary: "High payment failure rate"
          description: "Payment failure rate is {{ $value | humanizePercentage }}."

      # JVM 堆内存使用 > 85%
      - alert: ShopHighJvmHeapUsage
        expr: |
          (
            jvm_memory_used_bytes{area="heap"}
            /
            jvm_memory_max_bytes{area="heap"}
          ) > 0.85
        for: 5m
        labels:
          severity: P1
        annotations:
          summary: "High JVM heap usage on {{ $labels.instance }}"
          description: "JVM heap is {{ $value | humanizePercentage }} full on {{ $labels.instance }}."
```

### 5.2 P2 告警规则（延迟、队列积压）

```yaml
  - name: shop.p2.performance
    interval: 60s
    rules:
      # HTTP P99 延迟 > 2s
      - alert: ShopHighP99Latency
        expr: |
          histogram_quantile(0.99,
            sum by (le, instance, job) (
              rate(http_server_requests_seconds_bucket{job="shop-apps"}[5m])
            )
          ) > 2.0
        for: 5m
        labels:
          severity: P2
        annotations:
          summary: "High P99 latency on {{ $labels.instance }}"
          description: "P99 latency is {{ $value }}s on {{ $labels.instance }}."

      # Kafka Consumer Lag > 1000
      - alert: ShopKafkaConsumerLagHigh
        expr: kafka_consumergroup_lag{namespace="shop"} > 1000
        for: 10m
        labels:
          severity: P2
        annotations:
          summary: "High Kafka consumer lag for {{ $labels.consumergroup }}"
          description: "Consumer group {{ $labels.consumergroup }} on topic {{ $labels.topic }} has lag {{ $value }}."

      # Kafka Consumer Lag > 5000（升级为 P1）
      - alert: ShopKafkaConsumerLagCritical
        expr: kafka_consumergroup_lag{namespace="shop"} > 5000
        for: 5m
        labels:
          severity: P1
        annotations:
          summary: "Critical Kafka consumer lag for {{ $labels.consumergroup }}"
          description: "Consumer group {{ $labels.consumergroup }} lag {{ $value }} is critically high."

      # 数据库连接池耗尽（HikariCP）
      - alert: ShopDbConnectionPoolExhausted
        expr: |
          hikaricp_connections_pending{job="shop-apps"} > 5
        for: 3m
        labels:
          severity: P2
        annotations:
          summary: "DB connection pool pending on {{ $labels.instance }}"
          description: "{{ $value }} threads are waiting for DB connections on {{ $labels.instance }}."

      # 优惠券核销异常激增（比 1h 前增长 300%）
      - alert: ShopCouponRedemptionSpike
        expr: |
          rate(shop_coupon_redeemed_total[5m])
          >
          3 * rate(shop_coupon_redeemed_total[1h] offset 1h)
        for: 5m
        labels:
          severity: P2
        annotations:
          summary: "Abnormal coupon redemption spike detected"
          description: "Coupon redemption rate is 3x higher than 1 hour ago."
```

---

## 六、Kafka Consumer Lag 监控

### 6.1 kafka_consumergroup_lag 暴露方式

**选型：kafka-exporter（danielqsj/kafka-exporter）**

选择理由：
- 轻量（单个 Go 二进制，镜像 < 20MB）
- 无需修改 Kafka 配置，直接连接 Kafka broker 查询 Consumer Group offset
- 支持 KRaft 模式（apache/kafka:3.9.0 已使用 KRaft）
- 原生暴露 `kafka_consumergroup_lag` 指标

**关键指标：**

| 指标 | 说明 |
|------|------|
| `kafka_consumergroup_lag{consumergroup, topic, partition}` | 消费者组分区级 lag |
| `kafka_consumergroup_lag_sum{consumergroup, topic}` | 消费者组 Topic 级总 lag |
| `kafka_consumergroup_members{consumergroup}` | 消费者组成员数 |
| `kafka_topic_partition_current_offset` | Topic 分区当前 offset |
| `kafka_topic_partition_oldest_offset` | Topic 分区最早可消费 offset |

**Prometheus Scrape 配置补充：**

```yaml
scrape_configs:
  # ... 现有 shop-apps job ...
  - job_name: kafka-exporter
    static_configs:
      - targets:
          - kafka-exporter:9308
    scrape_interval: 30s
```

**Shop 平台关键 Consumer Groups（需监控）：**

| Consumer Group | 服务 | 关键 Topics |
|----------------|------|-------------|
| `order-service` | order-service | `shop.order.placed`, `shop.payment.completed` |
| `notification-service` | notification-service | `shop.notification.requested` |
| `loyalty-service` | loyalty-service | `shop.order.completed`, `shop.points.award` |
| `webhook-service` | webhook-service | `shop.webhook.event` |
| `activity-service` | activity-service | `shop.activity.*` |
| `subscription-service` | subscription-service | `shop.subscription.*` |

---

## 七、Grafana Dashboard 设计

### 7.1 Business Overview Dashboard

**Dashboard ID**: `shop-business-overview`
**刷新频率**: 30s
**时间范围默认**: Last 1 hour

**Row 1: 核心业务 KPI（Stat Panels）**

| Panel | Query | 说明 |
|-------|-------|------|
| 订单创建量（1h） | `sum(increase(shop_order_created_total{status="success"}[1h]))` | 过去 1 小时成功订单数 |
| 支付成功率 | `sum(rate(shop_payment_processed_total{status="success"}[5m])) / sum(rate(shop_payment_processed_total[5m]))` | 实时支付成功率，阈值 < 90% 变红 |
| 优惠券核销量（1h） | `sum(increase(shop_coupon_redeemed_total{status="success"}[1h]))` | 优惠券使用热度 |
| 积分发放量（1h） | `sum(increase(shop_points_awarded_total[1h]))` | 用户活跃度指标 |

**Row 2: 订单漏斗（Time Series）**

- 订单创建 vs 支付完成 vs 自动完成：叠加折线图，观察漏斗转化率趋势

**Row 3: 服务调用 QPS + 错误率（Time Series）**

- 按服务分组的 HTTP QPS
- 按服务分组的 5xx 错误率

**Row 4: Kafka 消费健康（Bar Gauge）**

- 各 Consumer Group 的 lag 值，超过 1000 变黄，超过 5000 变红

### 7.2 Service Health Dashboard

**Dashboard ID**: `shop-service-health`
**刷新频率**: 15s

**Row 1: 服务可用性（State Timeline）**

- 15 个服务的 `up` 状态时间线，一眼看出哪段时间哪个服务曾 down

**Row 2: HTTP 延迟分布（Heatmap）**

- P50 / P95 / P99 延迟，按服务分列

**Row 3: JVM 健康（Time Series）**

- 堆内存使用率
- GC 次数（`jvm_gc_pause_seconds_count`）
- 线程数（Virtual Thread 场景下参考 `jvm_threads_live`）

**Row 4: 数据库连接池（Time Series）**

- HikariCP active / idle / pending connections，按服务分列

**Row 5: 日志错误率（Logs Panel + Time Series）**

- Loki 数据源：`{namespace="shop"} | json | level="ERROR"` 的近实时日志流
- ERROR 日志频率趋势图（聚合 Loki 指标）

---

## 八、实施路径（分阶段）

### Phase 1：存储基础设施（优先级最高，其他组件依赖它）

- 部署 Garage S3（单节点）
- 创建 `tempo-traces`, `loki-chunks`, `loki-ruler` buckets
- 验证：`aws s3 ls` 或 `garage bucket list`

### Phase 2：日志管道

- 部署 Loki（Garage S3 后端）
- 部署 Promtail（DaemonSet）
- 验证：Grafana Explore 查询到 `{namespace="shop"}` 日志

### Phase 3：追踪管道

- 部署 Tempo（Garage S3 后端，接收 OTLP gRPC :4317）
- 更新 OTEL Collector：debug exporter → otlp/tempo exporter
- 验证：Grafana Explore 查到 TraceID，能展开 Span 树

### Phase 4：可视化

- 部署 Grafana
- 配置 Prometheus + Loki + Tempo 三个 Datasource
- 导入 Business Overview + Service Health 两个 Dashboard

### Phase 5：业务指标与告警

- order-service / wallet-service / promotion-service 补充 `shop_*` Counter/Timer
- 部署 kafka-exporter
- 补全 Prometheus scrape config（新增 kafka-exporter 和缺失服务）
- 落地 Prometheus Alert Rules（P1/P2）

### 依赖关系图

```
Garage S3
├── Loki ──→ Promtail ──→ Grafana (Loki datasource)
└── Tempo
      ↑
  OTEL Collector (update exporter)
      ↑
  Spring Boot services (already configured)

Prometheus (already running) ──→ Grafana (Prometheus datasource)
kafka-exporter ──→ Prometheus ──→ Alert Rules
```
