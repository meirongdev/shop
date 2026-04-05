# Shop Platform — 可观测性平台 实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task.

**Goal:** 部署 Garage S3 → Loki + Promtail → Tempo → Grafana 完整可观测性栈，替换 OTEL debug exporter，补齐业务指标与 Prometheus 告警规则，实现 Metrics-Logs-Traces 三维联动。

**Spec:** `docs/superpowers/specs/2026-03-23-observability-stack-design.md`

**Tech Stack:** Garage S3 / Loki 3.4.x / Promtail 3.4.x / Tempo 2.7.x / Grafana 12.x / OpenTelemetry Collector 0.127.x / Kind + Kubernetes

**依赖顺序：** Phase 1（Garage S3）→ Phase 2（Loki + 日志）→ Phase 3（Tempo + 追踪）→ Phase 4（Grafana）→ Phase 5（业务指标 + 告警）

---

## Phase 1：存储基础设施

---

## Task 1: 部署 Garage S3 到 Kind 集群

**Goal:** 在 `shop` namespace 启动单节点 Garage S3，创建 tempo-traces / loki-chunks / loki-ruler 三个 bucket。

**Files:**
- Create: `k8s/observability/garage/garage-configmap.yaml`
- Create: `k8s/observability/garage/garage-deployment.yaml`
- Create: `k8s/observability/garage/garage-service.yaml`
- Create: `k8s/observability/garage/garage-pvc.yaml`
- Create: `scripts/garage-init-buckets.sh`

- [ ] **Step 1: 创建 Garage ConfigMap（garage.toml）**

```yaml
# garage-configmap.yaml
apiVersion: v1
kind: ConfigMap
metadata:
  name: garage-config
  namespace: shop
data:
  garage.toml: |
    metadata_dir = "/var/lib/garage/meta"
    data_dir = "/var/lib/garage/data"
    db_engine = "lmdb"
    replication_factor = 1
    rpc_bind_addr = "[::]:3901"
    rpc_public_addr = "garage-service.shop.svc.cluster.local:3901"
    [s3_api]
    s3_region = "garage"
    api_bind_addr = "[::]:3900"
    [admin]
    api_bind_addr = "[::]:3903"
```

- [ ] **Step 2: 创建 PVC / Deployment / Service（garage-service:3900）**

- [ ] **Step 3: 初始化 Garage 节点并创建 Buckets**

```bash
# scripts/garage-init-buckets.sh
#!/bin/bash
set -e
GARAGE_POD=$(kubectl get pods -n shop -l app=garage -o jsonpath='{.items[0].metadata.name}')
NODE_ID=$(kubectl exec -n shop $GARAGE_POD -- garage node id -q)
kubectl exec -n shop $GARAGE_POD -- garage layout assign -z dc1 -c 1G $NODE_ID
kubectl exec -n shop $GARAGE_POD -- garage layout apply --version 1
kubectl exec -n shop $GARAGE_POD -- garage key create observability-key
kubectl exec -n shop $GARAGE_POD -- garage bucket create tempo-traces
kubectl exec -n shop $GARAGE_POD -- garage bucket create loki-chunks
kubectl exec -n shop $GARAGE_POD -- garage bucket create loki-ruler
kubectl exec -n shop $GARAGE_POD -- garage bucket allow --read --write --key observability-key tempo-traces
kubectl exec -n shop $GARAGE_POD -- garage bucket allow --read --write --key observability-key loki-chunks
kubectl exec -n shop $GARAGE_POD -- garage bucket allow --read --write --key observability-key loki-ruler
```

预期输出：`garage bucket list` 输出 `tempo-traces loki-chunks loki-ruler`。

---

## Phase 2：日志管道

---

## Task 2: 所有服务启用 Structured Logging（logstash JSON 格式）

**Goal:** 15 个服务统一输出 JSON 日志，零代码改动，仅 application.yml 加一行配置。

**Files:**
- Modify: 所有服务的 `src/main/resources/application.yml`（15 个文件）

- [ ] **Step 1: 在每个服务的 application.yml 追加**

```yaml
logging:
  structured:
    format:
      console: logstash
```

服务列表：api-gateway, auth-server, buyer-bff, seller-bff, profile-service, marketplace-service,
order-service, wallet-service, promotion-service, loyalty-service, activity-service,
search-service, notification-service, webhook-service, subscription-service

- [ ] **Step 2: 本地验证（单个服务）**

```bash
./mvnw spring-boot:run -pl order-service | head -5
# 预期：输出 {"@timestamp":"...","level":"INFO","message":"...","traceId":"...",...}
```

预期输出：所有服务日志格式统一为 logstash JSON，包含 `@timestamp / level / message / traceId / spanId` 字段。

---

## Task 3: 部署 Loki + Promtail

**Goal:** Loki 使用 Garage S3 后端存储日志；Promtail 作为 DaemonSet 采集所有 Pod 日志。

**Files:**
- Create: `k8s/observability/loki/loki-configmap.yaml`
- Create: `k8s/observability/loki/loki-deployment.yaml`
- Create: `k8s/observability/loki/loki-service.yaml`
- Create: `k8s/observability/promtail/promtail-configmap.yaml`
- Create: `k8s/observability/promtail/promtail-daemonset.yaml`
- Create: `k8s/observability/promtail/promtail-rbac.yaml`

- [ ] **Step 1: Loki ConfigMap（使用 Garage S3 作为 object_store）**

```yaml
# loki-configmap.yaml 中关键 storage_config 部分
storage_config:
  aws:
    s3: http://<ACCESS_KEY>:<SECRET_KEY>@garage-service.shop.svc.cluster.local:3900/loki-chunks
    s3forcepathstyle: true
    region: garage
  boltdb_shipper:
    active_index_directory: /loki/index
    cache_location: /loki/index_cache
    shared_store: s3
```

- [ ] **Step 2: Promtail ConfigMap（采集 /var/log/pods，解析 JSON，提取 traceId）**

```yaml
# promtail 关键 pipeline_stages
pipeline_stages:
  - json:
      expressions:
        level: level
        traceId: traceId
        message: message
  - labels:
      level:
      traceId:
```

- [ ] **Step 3: 部署 Loki + Promtail，验证**

```bash
kubectl apply -f k8s/observability/loki/
kubectl apply -f k8s/observability/promtail/
kubectl logs -n shop -l app=loki | grep "Loki started"
```

预期输出：Loki Pod Running，Promtail DaemonSet 每节点一个 Pod；通过 `loki-service:3100/ready` 返回 `ready`。

---

## Phase 3：追踪管道

---

## Task 4: 部署 Tempo

**Goal:** Tempo 使用 Garage S3 存储 Trace 数据，接收 OTEL Collector 的 OTLP gRPC 推送。

**Files:**
- Create: `k8s/observability/tempo/tempo-configmap.yaml`
- Create: `k8s/observability/tempo/tempo-deployment.yaml`
- Create: `k8s/observability/tempo/tempo-service.yaml`

- [ ] **Step 1: Tempo ConfigMap（Garage S3 后端）**

```yaml
storage:
  trace:
    backend: s3
    s3:
      bucket: tempo-traces
      endpoint: garage-service.shop.svc.cluster.local:3900
      access_key: <ACCESS_KEY>
      secret_key: <SECRET_KEY>
      insecure: true
      forcepathstyle: true
```

- [ ] **Step 2: 暴露 Service（gRPC :4317，HTTP :3200）**

- [ ] **Step 3: 验证 Tempo ready**

```bash
curl http://tempo-service.shop.svc.cluster.local:3200/ready
# 预期：200 OK
```

---

## Task 5: 更新 OTEL Collector — 替换 debug exporter

**Goal:** 将 OTEL Collector 的 `debug` exporter 替换为 `otlp/tempo`，同时添加 `loki` exporter 将日志推送到 Loki。

**Files:**
- Modify: `k8s/observability/otel-collector/otel-collector-configmap.yaml`（或对应路径）

- [ ] **Step 1: 修改 exporters 配置**

```yaml
exporters:
  otlp/tempo:
    endpoint: "tempo-service.shop.svc.cluster.local:4317"
    tls:
      insecure: true
  loki:
    endpoint: "http://loki-service.shop.svc.cluster.local:3100/loki/api/v1/push"
  prometheus:
    endpoint: "0.0.0.0:8889"

service:
  pipelines:
    traces:
      receivers: [otlp]
      processors: [memory_limiter, batch]
      exporters: [otlp/tempo]    # 替换 debug
    logs:
      receivers: [otlp]
      processors: [batch]
      exporters: [loki]
    metrics:
      receivers: [otlp, prometheus]
      processors: [batch]
      exporters: [prometheus]
```

- [ ] **Step 2: 重启 OTEL Collector，验证 Trace 到达 Tempo**

```bash
kubectl rollout restart deployment/otel-collector -n shop
# 触发一个请求后：
curl "http://tempo-service.shop.svc.cluster.local:3200/api/traces/<traceId>"
```

预期输出：Tempo 中可查到 Trace，Span 树完整。

---

## Phase 4：可视化

---

## Task 6: 部署 Grafana + 配置三个 Datasource

**Goal:** 部署 Grafana，配置 Prometheus + Loki + Tempo 三个 Datasource，配置 Loki Derived Fields（traceId → Tempo）。

**Files:**
- Create: `k8s/observability/grafana/grafana-deployment.yaml`
- Create: `k8s/observability/grafana/grafana-service.yaml`
- Create: `k8s/observability/grafana/grafana-datasources-configmap.yaml`

- [ ] **Step 1: Grafana Datasource ConfigMap**

```yaml
# grafana-datasources-configmap.yaml
apiVersion: v1
kind: ConfigMap
metadata:
  name: grafana-datasources
  namespace: shop
  labels:
    grafana_datasource: "1"
data:
  datasources.yaml: |
    apiVersion: 1
    datasources:
      - name: Prometheus
        type: prometheus
        url: http://prometheus-service.shop.svc.cluster.local:9090
        isDefault: true
      - name: Loki
        type: loki
        url: http://loki-service.shop.svc.cluster.local:3100
        jsonData:
          derivedFields:
            - name: TraceID
              matcherRegex: '"traceId":"(\w+)"'
              url: "$${__value.raw}"
              datasourceUid: tempo
      - name: Tempo
        type: tempo
        uid: tempo
        url: http://tempo-service.shop.svc.cluster.local:3200
```

- [ ] **Step 2: 部署 Grafana，访问 http://localhost:3000**

```bash
kubectl port-forward -n shop svc/grafana 3000:3000
```

预期输出：三个 Datasource 状态均为 `Data source connected and labels found`。

---

## Task 7: 导入 Business Overview + Service Health Dashboard

**Goal:** 两个 Grafana Dashboard 覆盖业务健康和服务运行状态。

**Files:**
- Create: `k8s/observability/grafana/dashboards/business-overview.json`
- Create: `k8s/observability/grafana/dashboards/service-health.json`

- [ ] **Step 1: Business Overview Dashboard 包含面板**
  - `shop_order_created_total`（下单速率）
  - `shop_payment_success_total / shop_payment_failed_total`（支付成功率）
  - `shop_coupon_redeemed_total`（优惠券核销量）
  - `shop_loyalty_points_earned_total`（积分发放量）

- [ ] **Step 2: Service Health Dashboard 包含面板**
  - 各服务 HTTP 5xx 错误率
  - 各服务 P99 响应时间
  - Resilience4j CircuitBreaker 状态（配合弹性治理计划）
  - JVM 堆内存使用

预期输出：两个 Dashboard 可正常加载，指标有数据（需 Phase 5 业务指标埋点后数据完整）。

---

## Phase 5：业务指标与告警

---

## Task 8: 补充 shop_* 业务指标埋点

**Goal:** 在 order-service / wallet-service / promotion-service 各补充 2-3 个 Micrometer Counter/Timer。

**Files:**
- Modify: `order-service/src/main/java/dev/meirong/shop/order/service/OrderApplicationService.java`
- Modify: `wallet-service/src/main/java/dev/meirong/shop/wallet/service/WalletApplicationService.java`
- Modify: `promotion-service/src/main/java/dev/meirong/shop/promotion/service/CouponApplicationService.java`

- [ ] **Step 1: order-service — 注入 MeterRegistry，添加指标**

```java
// 构造器注入
private final Counter orderCreatedCounter;
private final Counter orderFailedCounter;
private final Timer checkoutTimer;

public OrderApplicationService(MeterRegistry registry, ...) {
    this.orderCreatedCounter = Counter.builder("shop.order.created.total")
        .description("Total orders created").register(registry);
    this.orderFailedCounter = Counter.builder("shop.order.failed.total")
        .description("Total order creation failures").register(registry);
    this.checkoutTimer = Timer.builder("shop.checkout.duration")
        .description("Checkout processing time").register(registry);
}

// 在 createOrder() 中：
orderCreatedCounter.increment();
// 在异常处理中：
orderFailedCounter.increment();
```

- [ ] **Step 2: wallet-service — 支付指标**

```java
Counter.builder("shop.payment.success.total").tag("provider", provider).register(registry);
Counter.builder("shop.payment.failed.total").tag("provider", provider).tag("reason", reason).register(registry);
Timer.builder("shop.payment.duration").tag("provider", provider).register(registry);
```

- [ ] **Step 3: promotion-service — 优惠券指标**

```java
Counter.builder("shop.coupon.redeemed.total").register(registry);
Counter.builder("shop.coupon.redeem.failed.total").tag("reason", reason).register(registry);
```

预期输出：`/actuator/prometheus` 中出现 `shop_order_created_total`、`shop_payment_success_total` 等指标。

---

## Task 9: 部署 kafka-exporter + Kafka consumer lag 监控

**Goal:** 暴露 `kafka_consumergroup_lag` 指标，在 Grafana 中可见消费积压情况。

**Files:**
- Create: `k8s/observability/kafka-exporter/kafka-exporter-deployment.yaml`
- Modify: `k8s/observability/prometheus/prometheus-configmap.yaml`（添加 scrape job）

- [ ] **Step 1: 部署 kafka-exporter（danielqsj/kafka-exporter）**

```yaml
# kafka-exporter-deployment.yaml
containers:
  - name: kafka-exporter
    image: danielqsj/kafka-exporter:v1.8.0
    args:
      - "--kafka.server=kafka-service.shop.svc.cluster.local:9092"
    ports:
      - containerPort: 9308
```

- [ ] **Step 2: Prometheus scrape config 增加 kafka-exporter**

```yaml
- job_name: 'kafka-exporter'
  static_configs:
    - targets: ['kafka-exporter-service.shop.svc.cluster.local:9308']
```

预期输出：Prometheus 中可查到 `kafka_consumergroup_lag{consumergroup="promotion-service"}`。

---

## Task 10: 落地 Prometheus Alert Rules（P1 + P2）

**Goal:** 部署告警规则文件，P1 立即触发，P2 持续 5 分钟后触发。

**Files:**
- Create: `k8s/observability/prometheus/alert-rules.yaml`

- [ ] **Step 1: 编写告警规则 ConfigMap**

```yaml
groups:
  - name: shop.p1.alerts
    rules:
      - alert: ServiceDown
        expr: up{job=~"shop-.*"} == 0
        for: 1m
        labels: {severity: critical}
        annotations:
          summary: "{{ $labels.job }} is down"

      - alert: HighErrorRate
        expr: |
          rate(http_server_requests_seconds_count{status=~"5.."}[5m])
          / rate(http_server_requests_seconds_count[5m]) > 0.05
        for: 2m
        labels: {severity: critical}
        annotations:
          summary: "High 5xx rate on {{ $labels.job }}: {{ $value | humanizePercentage }}"

  - name: shop.p2.alerts
    rules:
      - alert: SlowCheckout
        expr: |
          histogram_quantile(0.99, rate(shop_checkout_duration_seconds_bucket[5m])) > 5
        for: 5m
        labels: {severity: warning}
        annotations:
          summary: "P99 checkout time > 5s"

      - alert: KafkaConsumerLag
        expr: kafka_consumergroup_lag > 1000
        for: 5m
        labels: {severity: warning}
        annotations:
          summary: "Kafka consumer lag > 1000 for {{ $labels.consumergroup }}"

      - alert: CircuitBreakerOpen
        expr: resilience4j_circuitbreaker_state{state="open"} == 1
        for: 5m
        labels: {severity: warning}
        annotations:
          summary: "CircuitBreaker {{ $labels.name }} is OPEN for > 5m"
```

- [ ] **Step 2: 挂载告警规则文件到 Prometheus，重启 Prometheus，在 /alerts 界面验证规则加载**

预期输出：Prometheus `/alerts` 页面显示所有规则状态为 `inactive`（正常无告警时）。

---

## 验收标准

| Phase | 验收项 |
|-------|--------|
| Phase 1 | `garage bucket list` 输出三个 bucket，S3 API 可用 |
| Phase 2 | Grafana Explore 查询 `{namespace="shop"}` 返回日志，日志中含 `traceId` 字段 |
| Phase 3 | 发起一次下单请求后，Grafana Tempo Explore 可按 traceId 看到完整 Span 树 |
| Phase 4 | Grafana 三个 Datasource 状态绿色；从 Loki 日志点击 traceId 能跳转到 Tempo |
| Phase 5 | `/actuator/prometheus` 含 `shop_*` 指标；Prometheus Alerts 页有规则；kafka_consumergroup_lag 可查 |
