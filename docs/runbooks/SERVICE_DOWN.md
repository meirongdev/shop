# Runbook: Service Down

## 1. 现象
- Prometheus 告警: `ServiceDown` 或 `CheckoutServiceDown`
- 告警摘要: `{{ $labels.instance }} is DOWN`

## 2. 紧急排查步骤
1. **检查 K8s Pod 状态**:
   ```bash
   kubectl get pods -n shop -l app={{ $labels.instance }}
   ```
2. **检查 Pod 日志**:
   ```bash
   kubectl logs -n shop -l app={{ $labels.instance }} --tail=100
   ```
3. **检查事件**:
   ```bash
   kubectl get events -n shop --sort-by='.lastTimestamp'
   ```
4. **查看 Grafana 面板**:
   - [Service Health Dashboard](/grafana/d/shop-service-health)

## 3. 常见原因
- **OOMKilled**: JVM Heap 设置过小或存在内存泄漏。
- **Liveness Probe Failure**: 服务假死（死锁、线程池耗尽）。
- **Dependency Down**: 数据库或 Kafka 不可用导致启动失败。

## 4. 止血方案
- **重启**: `kubectl rollout restart deployment {{ $labels.instance }} -n shop`
- **扩容**: 如果是压力过大，尝试 `kubectl scale deployment {{ $labels.instance }} --replicas=3 -n shop`
- **回滚**: 如果是新发布导致，立即回滚。
