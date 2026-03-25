# HTTP/2 h2c 实验结论

## 实验条件
- 日期：YYYY-MM-DD
- 集群：Kind（单节点）
- 并发：30 VUs，60s steady state
- 端点：POST /api/buyer/marketplace/list、POST /api/buyer/category/list

## 指标对比

| 指标 | HTTP/1.1 基线 | h2c 实验 | 变化 |
|------|-------------|---------|------|
| p50 latency | ___ ms | ___ ms | ___% |
| p95 latency | ___ ms | ___ ms | ___% |
| p99 latency | ___ ms | ___ ms | ___% |
| 吞吐量 (req/s) | ___ | ___ | ___% |
| 错误率 | ___% | ___% | - |
| TimeLimiter 超时数 | ___ | ___ | - |

## Grafana 观察
（插入截图或描述差异）

## Tempo 确认
- 基线 http.flavor：1.1
- 实验 http.flavor：2.0 ✅

## 结论
（是否有显著提升？哪个指标改善最多？是否推荐生产启用？）

## 注意事项
- Kind 单节点 TCP 开销远低于生产，h2c 连接复用收益可能被低估
- TimeLimiter 取消语义差异：JdkClientHttpRequestFactory 对 Future.cancel 的响应与 SimpleClientHttpRequestFactory 不同，若超时率上升需进一步排查
- 若 p95 无明显改善，进阶测试 `POST /api/buyer/dashboard/get`（需 JWT，buyer-bff 并发调 5+ 服务，多路复用收益更大）

## 提取 k6 指标

```bash
echo "=== 基线 (HTTP/1.1) ===" && \
jq '.metrics.bff_latency_ms | {p50: .values["p(50)"], p95: .values["p(95)"], p99: .values["p(99)"]}' \
  experiments/h2c/baseline-results.json

echo "=== 实验 (h2c) ===" && \
jq '.metrics.bff_latency_ms | {p50: .values["p(50)"], p95: .values["p(95)"], p99: .values["p(99)"]}' \
  experiments/h2c/h2c-results.json
```

## 回滚方案

若 h2c 引入超时增加或异常，按以下步骤回滚：

```bash
# 1. 移除目标 Deployment 中的 SERVER_HTTP2_ENABLED env
git checkout k8s/apps/platform.yaml
kubectl apply -f k8s/apps/platform.yaml -n shop

# 2. 回滚 BFF 镜像
kubectl rollout undo deployment/buyer-bff deployment/seller-bff -n shop
kubectl rollout status deployment/buyer-bff -n shop --timeout=120s
```
