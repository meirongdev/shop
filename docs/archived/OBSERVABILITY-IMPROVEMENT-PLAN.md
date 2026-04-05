# Observability Improvement Plan — Shop Platform

> 基于 2026 年 Spring Boot 3.5 + Virtual Thread + Kind K8s 最佳实践
> 不使用 Java Agent，完全依赖 Micrometer + Spring Boot Auto-Configuration

## 现状评估

### 已有能力 ✅

| 能力 | 实现方式 |
|------|---------|
| Distributed Tracing | Micrometer Tracing Bridge OTel → OTEL Collector → Tempo |
| Structured Logging | Spring Boot 3.5 logstash JSON，traceId/spanId 自动注入 MDC |
| Metrics | Micrometer → Prometheus `/actuator/prometheus` |
| Log Aggregation | Promtail DaemonSet → Loki (Garage S3) |
| Visualization | Grafana (Prometheus + Loki + Tempo 三数据源互联) |
| Alerting | Prometheus P1/P2 告警规则 (ServiceDown, 5xx, Kafka Lag 等) |
| Dashboards | Service Health + Business Overview 两个面板 |
| OTEL Collector | K8s 部署，traces→Tempo, logs→Loki, memory_limiter+batch |
| Tempo Metrics Generator | service-graphs + span-metrics → remote_write → Prometheus |

### 缺陷清单

| # | 问题 | 严重程度 | 影响 |
|---|------|---------|------|
| G1 | **Promtail 是 legacy 组件**，且将 `traceId` 设为高基数 Loki label | **P0** | Promtail 已被 Grafana 标记 legacy；traceId 作为 label 导致 Loki 索引膨胀 |
| G2 | 日志采集存在两条重叠路径 (Promtail + OTEL Collector logs pipeline) | **P0** | 架构冗余，维护成本高，两条路径可能出现数据不一致 |
| G3 | Prometheus 未启用 `exemplar-storage` | **P1** | Grafana Metrics→Traces 关联断链，exemplar 不可用 |
| G4 | `TraceIdExtractor` fallback 返回 `UUID.randomUUID()` | **P1** | 无法关联到任何真实 trace，误导排查 |
| G5 | Gateway 的 `X-Request-Id` 与 OTel `traceId` 独立 | **P1** | API 消费者无法拿到 traceId，跨团队排查困难 |
| G6 | OTEL Collector 无 filter/sampling processor | **P2** | 健康检查、actuator 等低价值 trace 浪费 Tempo 存储 |
| G7 | 无 Continuous Profiling (第四支柱) | **P2** | trace 定位"哪里慢"，但无法回答"为什么慢" |
| G8 | 无 W3C Baggage 业务上下文传播 | **P2** | 无法按 userId/orderId 在 Tempo 中过滤 traces |
| G9 | 无 SLO / Error Budget 告警 | **P2** | 固定阈值告警噪声大，缺少燃烧率多窗口告警 |
| G10 | OTEL Collector 缺少 `k8sattributes` processor | **P2** | Trace span 无 pod/node/namespace 元数据 |
| G11 | Grafana 缺少 JVM / Virtual Thread 专项面板 | **P3** | 无法观测 VT pinning、HikariCP 瓶颈、GC pause |

---

## Phase 1：删除 Promtail，启用 Spring Boot OTLP 原生日志导出（P0）

### 1.1 架构变更概述

**Before (两条路径冗余)：**
```
App stdout (JSON) → Pod log file → Promtail DaemonSet → Loki     # 路径 A
App → OTLP → OTEL Collector → Loki                                # 路径 B (已配但未启用)
```

**After (统一 OTLP 通道)：**
```
App → OTLP (traces + logs) → OTEL Collector → Tempo / Loki
App → /actuator/prometheus → Prometheus scrape
```

**优势：**
- 日志天然携带 OTel resource attributes (service.name, traceId, spanId)，无需 Promtail JSON 解析
- OTEL Collector loki exporter 直接将 OTel attributes 映射为 Loki structured_metadata，彻底解决高基数 label 问题
- 删除 Promtail DaemonSet + RBAC + ConfigMap，减少 3 个 K8s 资源 + 节点级特权 pod
- 日志与 trace 共享同一个 OTLP 通道，correlation 天然一致

### 1.2 Spring Boot 应用配置 — 启用 OTLP 日志导出

Spring Boot 3.5 通过 `management.otlp.logging` 原生支持 OTLP 日志导出，无需额外依赖（`opentelemetry-exporter-otlp` 已存在于所有服务 pom.xml 中）。

**各服务 `application.yml` 变更：**

```yaml
# 当前配置
management:
  tracing:
    sampling:
      probability: 1.0
  otlp:
    tracing:
      endpoint: ${OTEL_EXPORTER_OTLP_ENDPOINT:http://otel-collector:4318/v1/traces}
logging:
  structured:
    format:
      console: logstash

# 改为
management:
  tracing:
    sampling:
      probability: 1.0
  otlp:
    tracing:
      endpoint: ${OTEL_EXPORTER_OTLP_TRACES_ENDPOINT:http://otel-collector:4318/v1/traces}
    logging:
      endpoint: ${OTEL_EXPORTER_OTLP_LOGS_ENDPOINT:http://otel-collector:4318/v1/logs}
logging:
  structured:
    format:
      console: logstash    # 保留：Kind 环境可 kubectl logs 直接看 JSON
```

**说明：**
- `management.otlp.logging.endpoint` 是 Spring Boot 3.4+ 新增配置
- console 输出保留 logstash format，作为 `kubectl logs` 的人工可读备份
- 日志通过 OTLP HTTP 发送到 OTEL Collector，由 Collector 的 logs pipeline 转发到 Loki

### 1.3 K8s ConfigMap 变更

**文件：** `k8s/apps/platform.yaml` — `shop-shared-config`

```yaml
# Before
data:
  OTEL_EXPORTER_OTLP_ENDPOINT: http://otel-collector:4318/v1/traces

# After
data:
  OTEL_EXPORTER_OTLP_TRACES_ENDPOINT: http://otel-collector:4318/v1/traces
  OTEL_EXPORTER_OTLP_LOGS_ENDPOINT: http://otel-collector:4318/v1/logs
```

### 1.4 OTEL Collector loki exporter 增强 — structured_metadata

**文件：** `k8s/infra/base.yaml` — `otel-collector-config` ConfigMap

当前 loki exporter 配置：
```yaml
loki:
  endpoint: http://loki.shop.svc.cluster.local:3100/loki/api/v1/push
  default_labels_enabled:
    exporter: false
    job: true
```

**改为：**
```yaml
loki:
  endpoint: http://loki.shop.svc.cluster.local:3100/loki/api/v1/push
  default_labels_enabled:
    exporter: false
    job: true
    level: true           # 低基数 label ✅
  # traceId/spanId 作为 structured_metadata（Loki 3.x），不作为 label
  # OTEL Collector loki exporter 默认将 OTel resource attributes 映射到 Loki labels
  # 需要用 resource/attributes 配置控制哪些进 label、哪些进 structured_metadata
```

**补充 `resource` processor 控制 label 映射：**
```yaml
processors:
  # ... 已有 ...
  resource/loki:
    attributes:
      - action: insert
        key: loki.resource.labels
        value: service.name, deployment.environment
      # 只有 service.name 和 environment 进入 Loki label（低基数）
      # traceId, spanId 等自动成为 structured_metadata
```

更新 logs pipeline：
```yaml
service:
  pipelines:
    logs:
      receivers: [otlp]
      processors: [resource/loki, batch]
      exporters: [loki]
```

### 1.5 Loki 配置 — 确认 structured_metadata 已启用

**文件：** `k8s/observability/loki/loki-configmap.yaml`

在 `limits_config` 段增加：
```yaml
limits_config:
  reject_old_samples: true
  reject_old_samples_max_age: 168h
  ingestion_rate_mb: 16
  ingestion_burst_size_mb: 32
  allow_structured_metadata: true    # ← 新增，Loki 3.x 支持
```

### 1.6 Grafana Loki datasource — 更新 derived field

**文件：** `k8s/observability/grafana/grafana-datasources-configmap.yaml`

OTLP 日志经过 OTEL Collector 后，traceId 不再嵌在 JSON body 中，而是作为 structured_metadata。Grafana derived field 的正则需更新：

```yaml
- name: Loki
  type: loki
  uid: loki
  url: http://loki.shop.svc.cluster.local:3100
  jsonData:
    derivedFields:
      - name: TraceID
        matcherRegex: '"traceId":"(\w+)"'       # 保留：兼容 console JSON 格式
        url: "$${__value.raw}"
        datasourceUid: tempo
        urlDisplayLabel: "View in Tempo"
      - name: TraceID_metadata                   # 新增：从 structured_metadata 提取
        matcherType: label                        # Loki 3.x label/metadata matcher
        matcherRegex: 'traceId'
        datasourceUid: tempo
        urlDisplayLabel: "View in Tempo"
```

### 1.7 删除 Promtail 资源

**删除文件：**
- `k8s/observability/promtail/promtail-rbac.yaml`
- `k8s/observability/promtail/promtail-configmap.yaml`
- `k8s/observability/promtail/promtail-daemonset.yaml`
- `k8s/observability/promtail/` 目录

**更新 `k8s/observability/kustomization.yaml`：**
```yaml
# 删除以下三行
- promtail/promtail-rbac.yaml
- promtail/promtail-configmap.yaml
- promtail/promtail-daemonset.yaml
```

### 1.8 验证步骤

```bash
# 1. 部署更新
kubectl apply -k k8s/observability/

# 2. 确认 Promtail pods 已消失
kubectl get pods -n shop -l app=promtail  # 应无结果

# 3. 触发一个请求产生日志
curl http://localhost:8080/api/marketplace/products

# 4. 在 Grafana Loki Explore 中验证
#    查询: {service_name="marketplace-service"}
#    确认: 日志出现，且 structured_metadata 中包含 traceId/spanId

# 5. 点击 traceId → 跳转 Tempo → 确认 trace 完整
```

---

## Phase 2：修复关联与数据质量（P1）

### 2.1 Prometheus — 启用 exemplar-storage

**问题：** Tempo metrics_generator 已配 `send_exemplars: true`，Grafana datasource 已配 `exemplarTraceIdDestinations`，但 Prometheus 未启用存储，链路断在中间。

**文件：** `k8s/infra/base.yaml` — Prometheus Deployment

```yaml
# 新增一行 args
args:
  - --config.file=/etc/prometheus/prometheus.yml
  - --web.enable-remote-write-receiver
  - --storage.tsdb.retention.time=7d
  - --enable-feature=exemplar-storage    # ← 新增
```

**验证：** Grafana Service Health 面板 → P99 HTTP Response Time → 开启 Exemplars → 出现橙色小点 → 点击跳转 Tempo。

### 2.2 TraceIdExtractor — 修复 UUID fallback

**文件：** `shop-common/src/main/java/dev/meirong/shop/common/trace/TraceIdExtractor.java`

```java
// Before
public static String currentTraceId() {
    String traceId = MDC.get("traceId");
    if (traceId != null && !traceId.isBlank()) {
        return traceId;
    }
    return UUID.randomUUID().toString();  // ← 生成孤立 ID，无法关联
}

// After
public static String currentTraceId() {
    String traceId = MDC.get("traceId");
    return (traceId != null && !traceId.isBlank()) ? traceId : "";
}
```

调用方需处理空字符串。若某些场景确需 fallback ID（如无 trace 上下文的异步任务），应在调用点显式生成并注释原因。

### 2.3 Gateway — 将 OTel traceId 暴露给 API 消费者

**问题：** `X-Request-Id` 是 Gateway 生成的 UUID，与 OTel 无关。前端/移动端拿到的 ID 无法在 Grafana 中搜索。

**新增文件：** `api-gateway/src/main/java/dev/meirong/shop/gateway/filter/TraceIdResponseFilter.java`

```java
package dev.meirong.shop.gateway.filter;

import io.micrometer.tracing.Tracer;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 1)
public class TraceIdResponseFilter extends OncePerRequestFilter {

    private final Tracer tracer;

    public TraceIdResponseFilter(Tracer tracer) {
        this.tracer = tracer;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain)
            throws ServletException, IOException {
        chain.doFilter(request, response);
        var span = tracer.currentSpan();
        if (span != null) {
            response.setHeader("X-Trace-Id", span.context().traceId());
        }
    }
}
```

**同步修改 `TrustedHeadersFilter`** — 将 `X-Request-Id` 放入 MDC：

```java
// doFilterInternal 中
MDC.put("requestId", requestId);
try {
    chain.doFilter(wrapped, response);
} finally {
    MDC.remove("requestId");
}
```

**验证：** `curl -i http://localhost:8080/api/...` → 响应头包含 `X-Trace-Id: <32hex>` → Grafana Tempo 中搜索可找到。

---

## Phase 3：OTEL Collector 增强（P2）

### 3.1 filter processor — 过滤低价值 traces

**文件：** `k8s/infra/base.yaml` — `otel-collector-config`

```yaml
processors:
  memory_limiter:
    check_interval: 1s
    limit_mib: 256
  batch:
    timeout: 5s
    send_batch_size: 512
  # ── 新增 ──
  filter/health:
    error_mode: ignore
    traces:
      span:
        - 'IsMatch(attributes["url.path"], "/actuator.*")'
        - 'IsMatch(attributes["url.path"], "/healthz")'
        - 'IsMatch(attributes["http.route"], "/actuator.*")'

service:
  pipelines:
    traces:
      receivers: [otlp]
      processors: [memory_limiter, filter/health, batch]
      exporters: [otlp/tempo]
```

> **注意：** 部署后用 `debug` exporter 确认 Spring Boot 3.5 实际使用的 attribute name (`url.path` vs `http.target`)，再微调。

### 3.2 k8sattributes processor — 注入 K8s 元数据

```yaml
processors:
  k8sattributes:
    auth_type: "serviceAccount"
    extract:
      metadata:
        - k8s.pod.name
        - k8s.namespace.name
        - k8s.node.name
        - k8s.deployment.name
    pod_association:
      - sources:
          - from: resource_attribute
            name: k8s.pod.ip

service:
  pipelines:
    traces:
      receivers: [otlp]
      processors: [memory_limiter, k8sattributes, filter/health, batch]
      exporters: [otlp/tempo]
    logs:
      receivers: [otlp]
      processors: [k8sattributes, resource/loki, batch]
      exporters: [loki]
```

**前置条件 — OTEL Collector RBAC：**

新增 `k8s/observability/otel-collector/otel-collector-rbac.yaml`：

```yaml
apiVersion: v1
kind: ServiceAccount
metadata:
  name: otel-collector
  namespace: shop
---
apiVersion: rbac.authorization.k8s.io/v1
kind: ClusterRole
metadata:
  name: otel-collector
rules:
  - apiGroups: [""]
    resources: ["pods", "namespaces"]
    verbs: ["get", "watch", "list"]
---
apiVersion: rbac.authorization.k8s.io/v1
kind: ClusterRoleBinding
metadata:
  name: otel-collector
roleRef:
  apiGroup: rbac.authorization.k8s.io
  kind: ClusterRole
  name: otel-collector
subjects:
  - kind: ServiceAccount
    name: otel-collector
    namespace: shop
```

OTEL Collector Deployment (`k8s/infra/base.yaml`) 增加 `serviceAccountName: otel-collector`。

---

## Phase 4：第四支柱 — Continuous Profiling（P2）

### 4.1 方案选型

| 方案 | 需要 Agent? | Spring Boot 兼容性 | 推荐 |
|------|------------|-------------------|------|
| ~~OTel Java Agent + Pyroscope~~ | 需要 | 与 Spring Boot auto-config 冲突 | ❌ |
| Pyroscope Java SDK (programmatic) | 不需要 | 完全兼容 | **✅** |
| JFR + Grafana Alloy | 不需要 | JVM 内建 | 备选 |

### 4.2 K8s 部署 Pyroscope

新增 `k8s/observability/pyroscope/pyroscope-deployment.yaml`：

```yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: pyroscope
  namespace: shop
spec:
  replicas: 1
  selector:
    matchLabels:
      app: pyroscope
  template:
    metadata:
      labels:
        app: pyroscope
    spec:
      containers:
        - name: pyroscope
          image: grafana/pyroscope:1.12.0
          ports:
            - containerPort: 4040
          args: [server]
          resources:
            requests:
              cpu: 100m
              memory: 256Mi
            limits:
              cpu: 500m
              memory: 512Mi
---
apiVersion: v1
kind: Service
metadata:
  name: pyroscope
  namespace: shop
spec:
  selector:
    app: pyroscope
  ports:
    - port: 4040
      targetPort: 4040
```

### 4.3 Java 应用集成（无 Agent）

**`shop-common/pom.xml` 新增：**

```xml
<dependency>
    <groupId>io.pyroscope</groupId>
    <artifactId>agent</artifactId>
    <version>0.15.2</version>
</dependency>
```

**`shop-common` 新增自动配置：**

```java
package dev.meirong.shop.common.profiling;

import io.pyroscope.http.Format;
import io.pyroscope.javaagent.EventType;
import io.pyroscope.javaagent.PyroscopeAgent;
import io.pyroscope.javaagent.config.Config;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "shop.profiling.enabled", havingValue = "true")
public class PyroscopeAutoConfiguration {

    @Value("${spring.application.name}")
    private String appName;

    @Value("${shop.profiling.server-address:http://pyroscope:4040}")
    private String serverAddress;

    @EventListener(ApplicationReadyEvent.class)
    public void startProfiling() {
        PyroscopeAgent.start(
            new Config.Builder()
                .setApplicationName(appName)
                .setProfilingEvent(EventType.ITIMER)
                .setProfilingAlloc("512k")
                .setFormat(Format.JFR)
                .setServerAddress(serverAddress)
                .build()
        );
    }
}
```

**各服务 application.yml：**

```yaml
shop:
  profiling:
    enabled: ${PYROSCOPE_ENABLED:false}
    server-address: ${PYROSCOPE_SERVER_ADDRESS:http://pyroscope:4040}
```

K8s `shop-shared-config` ConfigMap 统一设 `PYROSCOPE_ENABLED: "true"`。

### 4.4 Grafana 集成

**`grafana-datasources-configmap.yaml` 新增：**

```yaml
- name: Pyroscope
  type: grafana-pyroscope-datasource
  uid: pyroscope
  url: http://pyroscope.shop.svc.cluster.local:4040
```

**Tempo datasource 增加 Trace → Profile 跳转：**

```yaml
- name: Tempo
  # ... 已有 ...
  jsonData:
    # ... 已有 tracesToLogsV2, serviceMap, nodeGraph ...
    tracesToProfilesV2:
      datasourceUid: pyroscope
      profileTypeId: "process_cpu:cpu:nanoseconds:cpu:nanoseconds"
      customQuery: true
      query: '${__span.tags["service.name"]}'
```

**验证：** Grafana Tempo → 查看慢 span → 右侧出现 "Profiles for this span" → flame graph 可见。

---

## Phase 5：业务上下文传播（P2）

### 5.1 W3C Baggage — userId/orderId 注入 Trace

**所有服务 application.yml：**

```yaml
management:
  tracing:
    baggage:
      remote-fields:
        - x-user-id
        - x-order-id
      correlation-fields:
        - x-user-id
        - x-order-id
```

Spring Boot 3.5 `management.tracing.baggage` 自动实现：
1. 作为 W3C Baggage header 跨服务传播
2. 放入 MDC（OTLP 日志自动包含）
3. 作为 span tag 写入 trace

**Gateway 注入（`TrustedHeadersFilter` 已有 principalId）：**

```java
// 在 TrustedHeadersRequestWrapper 构造中增加 header
wrapped.addHeader("x-user-id", jwt.getClaimAsString("principalId"));
```

**验证：** Grafana Tempo → Search → Tag `x-user-id` → 按用户过滤 traces。

---

## Phase 6：SLO / Error Budget 告警（P2）

### 6.1 Prometheus Recording Rules — 多窗口燃烧率

新增 `k8s/observability/prometheus/slo-rules.yaml`：

```yaml
apiVersion: v1
kind: ConfigMap
metadata:
  name: prometheus-slo-rules
  namespace: shop
data:
  slo-rules.yaml: |
    groups:
      - name: shop.slo.recording
        interval: 30s
        rules:
          # Checkout Availability SLO: 99.9%
          - record: shop:checkout_errors:ratio_rate5m
            expr: |
              sum(rate(http_server_requests_seconds_count{job="buyer-bff",uri=~"/api/orders.*",status=~"5.."}[5m]))
              / sum(rate(http_server_requests_seconds_count{job="buyer-bff",uri=~"/api/orders.*"}[5m]))

          - record: shop:checkout_errors:ratio_rate30m
            expr: |
              sum(rate(http_server_requests_seconds_count{job="buyer-bff",uri=~"/api/orders.*",status=~"5.."}[30m]))
              / sum(rate(http_server_requests_seconds_count{job="buyer-bff",uri=~"/api/orders.*"}[30m]))

          - record: shop:checkout_errors:ratio_rate1h
            expr: |
              sum(rate(http_server_requests_seconds_count{job="buyer-bff",uri=~"/api/orders.*",status=~"5.."}[1h]))
              / sum(rate(http_server_requests_seconds_count{job="buyer-bff",uri=~"/api/orders.*"}[1h]))

          - record: shop:checkout_errors:ratio_rate6h
            expr: |
              sum(rate(http_server_requests_seconds_count{job="buyer-bff",uri=~"/api/orders.*",status=~"5.."}[6h]))
              / sum(rate(http_server_requests_seconds_count{job="buyer-bff",uri=~"/api/orders.*"}[6h]))

      - name: shop.slo.alerts
        rules:
          # 快速燃烧 (5m/30m 双窗口) — 2% budget consumed in 1h
          - alert: CheckoutSLOFastBurn
            expr: |
              shop:checkout_errors:ratio_rate5m > (14.4 * 0.001)
              and
              shop:checkout_errors:ratio_rate30m > (14.4 * 0.001)
            for: 2m
            labels:
              severity: critical
              slo: checkout-availability
            annotations:
              summary: "Checkout SLO fast burn — error budget depleting rapidly"
              description: "5m error rate {{ $value | humanizePercentage }} exceeds 14.4x budget (SLO 99.9%)"

          # 慢速燃烧 (30m/6h 双窗口) — 5% budget consumed in 6h
          - alert: CheckoutSLOSlowBurn
            expr: |
              shop:checkout_errors:ratio_rate30m > (6 * 0.001)
              and
              shop:checkout_errors:ratio_rate6h > (6 * 0.001)
            for: 15m
            labels:
              severity: warning
              slo: checkout-availability
            annotations:
              summary: "Checkout SLO slow burn — error budget steadily depleting"
              description: "30m error rate {{ $value | humanizePercentage }} exceeds 6x budget (SLO 99.9%)"
```

---

## Phase 7：Grafana Dashboard 增强（P3）

### 7.1 JVM + Virtual Thread 面板

Virtual Thread 在 JDK 25 + Spring Boot 3.5 下的关键可观测指标：

```promql
# Virtual Thread 相关
jvm_threads_started_total
jvm_threads_live_threads{daemon="true"}

# VT pinning 检测（JFR jdk.VirtualThreadPinned 事件）
# application.yml:
#   management.metrics.enable.jvm.threads: true

# GC pause 对 VT 影响
jvm_gc_pause_seconds_max
jvm_gc_pause_seconds_count

# HikariCP 连接池（VT 下最常见瓶颈）
hikaricp_connections_active
hikaricp_connections_pending
hikaricp_connections_idle
hikaricp_connections_max
```

**新增 dashboard `jvm-virtual-threads.json` 要点：**
- Virtual Thread 并发数时序图
- HikariCP 连接池利用率 gauge（VT 下连接池最易成瓶颈）
- GC Pause vs HTTP P99 叠加对比
- Heap 分代用量 (Young/Old/Metaspace)

---

## 实施路线图

```
Phase 1 — 删除 Promtail + OTLP 日志导出 (P0, 1-2 天)
  ├─ 1.2 各服务 application.yml 增加 management.otlp.logging.endpoint
  ├─ 1.3 K8s ConfigMap 增加 OTEL_EXPORTER_OTLP_LOGS_ENDPOINT
  ├─ 1.4 OTEL Collector loki exporter 增加 resource/loki processor
  ├─ 1.5 Loki limits_config 增加 allow_structured_metadata
  ├─ 1.6 Grafana Loki datasource 更新 derived field
  ├─ 1.7 删除 Promtail (3 个 YAML + kustomization.yaml)
  └─ 1.8 验证

Phase 2 — 关联与数据质量 (P1, 1 天)
  ├─ 2.1 Prometheus --enable-feature=exemplar-storage
  ├─ 2.2 TraceIdExtractor fallback 修复
  └─ 2.3 Gateway X-Trace-Id response header

Phase 3 — OTEL Collector 增强 (P2, 0.5 天)
  ├─ 3.1 filter/health processor
  └─ 3.2 k8sattributes processor + RBAC

Phase 4 — Continuous Profiling (P2, 1-2 天)
  ├─ 4.2 Pyroscope K8s 部署
  ├─ 4.3 shop-common SDK 集成 (无 Agent)
  └─ 4.4 Grafana Pyroscope datasource + Trace→Profile

Phase 5 — 业务上下文传播 (P2, 0.5 天)
  └─ 5.1 W3C Baggage x-user-id / x-order-id

Phase 6 — SLO 告警 (P2, 0.5 天)
  └─ 6.1 Recording rules + 多窗口燃烧率告警

Phase 7 — Dashboard 增强 (P3, 1 天)
  └─ 7.1 JVM / Virtual Thread / HikariCP 面板
```

## 改进后架构

```
                            ┌─────────────────────────────────┐
     Frontend/Mobile        │            Grafana              │
     ← X-Trace-Id (new)    │  ┌─────────┐                    │
                            │  │ Metrics │←── Prometheus      │
     Spring Boot 3.5        │  │  Logs   │←── Loki            │
     (Virtual Threads)      │  │ Traces  │←── Tempo           │
           │                │  │Profiles │←── Pyroscope (new) │
           │ OTLP           │  └─────────┘                    │
           │ traces + logs  │  Exemplars: Metrics ↔ Traces    │
           ▼                │  Baggage: userId in all spans   │
     ┌──────────────────┐   │  SLO: burn-rate alerts          │
     │  OTEL Collector  │   └─────────────────────────────────┘
     │  + k8sattr (new) │
     │  + filter  (new) │
     │  + batch         │
     └──┬──────────┬────┘
        │          │
        ▼          ▼
      Tempo      Loki (structured_metadata, no Promtail)
      (S3)       (S3)

     ❌ Promtail DaemonSet — DELETED
```
