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
| Tempo | Trace 存储与查询 | 已部署，接 Garage S3 |
| Loki | 日志聚合与查询 | 已通过 OTLP 原生接入，替代 Promtail 路线 |
| Grafana | 统一看板与跨信号跳转 | 已预置 Prometheus / Loki / Tempo / Pyroscope datasource |
| Pyroscope | 持续性能分析 | 已接入 Grafana |
| Kafka Exporter | consumer lag 指标 | 已纳入观测清单 |

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

## 仍要继续演进的方向

- Alertmanager 与值班通知闭环。
- 多环境保留周期、容量与成本治理。
- 更丰富的 Kafka lag、虚拟线程、JVM 和业务大盘。

## 参考

- 权威：`docs/OBSERVABILITY-ALERTING-SLO.md`
- Spring Observability: https://docs.spring.io/spring-boot/reference/actuator/observability.html
