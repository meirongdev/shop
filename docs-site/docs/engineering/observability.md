---
title: 可观测性
---

# 可观测性

## 当前已落地

- ✅ Prometheus 指标采集
- ✅ OTEL Collector 采集链路
- ✅ 结构化日志（logstash 格式）

## 当前未完成

- ⚠️ OTEL 导出目前以 debug exporter 为主，真实 traces/logs 汇聚仍在推进
- ⬜ Loki / Tempo / Grafana 统一可视化栈
- ⬜ Alert Rules 与 runbook 联动
- ⬜ Kafka consumer lag 面板

## 指标基线

服务统一在 `:8081/actuator/prometheus` 暴露指标，建议至少监控：

- HTTP 延迟与错误率
- JVM 内存与 GC
- 连接池使用率
- Kafka 消费吞吐与积压

## 追踪基线

- OTLP HTTP 导出
- 统一 TraceId 透传到日志/响应
- 关键链路（checkout、支付回调、订单事件）优先保证可追踪

## SLO 与告警

详细阈值与告警分级以 `docs/OBSERVABILITY-ALERTING-SLO.md` 为权威文档。

## 参考

- 权威：`docs/OBSERVABILITY-ALERTING-SLO.md`
- Spring Observability: https://docs.spring.io/spring-boot/reference/actuator/observability.html
