# HTTP/2 h2c 实验结论

## 实验条件

- **日期**：2026-03-25
- **集群**：Kind（单节点，OrbStack，macOS）
- **并发**：10 VUs，60s steady state（直连 buyer-bff，绕过 api-gateway）
- **端点**：POST /buyer/v1/marketplace/list、POST /buyer/v1/category/list
- **h2c 链路**：buyer-bff (JDK HttpClient HTTP_2) → marketplace-service (SERVER_HTTP2_ENABLED=true)
- **基线链路**：buyer-bff (JDK HttpClient HTTP_1_1) → marketplace-service (SERVER_HTTP2_ENABLED=true)

> **注意**：由于 OrbStack docker restart 后 NodePort 端口映射失效，测试通过 `kubectl port-forward` 直连 buyer-bff Pod，规避了 api-gateway 层的路由开销。marketplace-service 和 buyer-bff 两端都已开启 h2c，测量的是服务间 HTTP/2 连接的实际影响。

## 指标对比

| 指标 | HTTP/1.1 基线 | h2c 实验 | 变化 |
|------|-------------|---------|------|
| p50 latency | 6.79 ms | 5.26 ms | -23% |
| p95 latency | 16.49 ms | 209.83 ms | **+1172%** ⚠️ |
| avg latency | 8.46 ms | 47.79 ms | **+465%** ⚠️ |
| 吞吐量 (req/s) | 129.7 req/s | 77.6 req/s | **-40%** ⚠️ |
| 错误率 | **0%** | **42.7%** | +42.7 pp ⚠️ |
| 成功率 | 100% | 57.3% | -42.7 pp |

## 失败模式分析

### 根本原因：Tomcat h2c HTTP/2 流控窗口错误

marketplace-service 日志出现：
```
Connection [faf], Stream [3] Closed due to error
org.apache.coyote.http2.StreamException: Connection [faf],
  Client sent more data than stream window allowed
```

**触发规律**：每3个请求约有1个失败（33%），精确命中 HTTP/2 stream ID 3（奇数流，客户端发起的第2条流）。

**机制推断**：
- JDK HttpClient HTTP_2 使用 Prior Knowledge h2c（直接发送 CLIENT_PREFACE，无 Upgrade 握手）
- 第1条流（stream 1）成功建立并完成
- 第2条流（stream 3）在 Tomcat 处理完初始 SETTINGS 前，客户端已发送 DATA 帧
- Tomcat 检测到 DATA 超出 stream 初始窗口（此时服务端 SETTINGS 尚未 ack），返回 RST_STREAM
- JDK HttpClient 连接重置，新连接重走同样流程

### 失败率随并发升高

| 测试场景 | VUs | 成功率 |
|---------|-----|--------|
| 50 次顺序请求（无并发） | 1 | 100% |
| 100 次顺序请求（slight concurrency） | ~3 | 67% |
| k6 10 VUs steady state | 10 | 57% |
| k6 30 VUs steady state | 30 | 5% |

## Tempo 确认

- otel-collector 在测试环境未启动，无法获取 Tempo trace
- 通过 marketplace-service 日志中 HTTP/2 stream error 间接确认 h2c 连接已建立

## 结论

**❌ 不建议在当前环境生产启用 h2c。**

| 维度 | 结论 |
|------|------|
| 延迟（p50） | 略有改善（-23%），但被 p95 大幅恶化掩盖 |
| 延迟（p95） | 严重退化（+1172%），超出 500ms SLO |
| 吞吐量 | 下降 40% |
| 可靠性 | 42.7% 错误率，不可接受 |
| 根因 | JDK HttpClient Prior Knowledge h2c 与 Tomcat stream 窗口初始化时序冲突 |

## 修复方向（如需继续调研 h2c）

1. **换用 HTTP Upgrade 模式**：让 JDK HttpClient 先用 HTTP/1.1 发送 Upgrade 头，协商成功后再升级 HTTP/2
2. **增大 Tomcat 初始流窗口**：在 server.properties 配置 `server.http2.initial-window-size=65536`，确保 stream 3 的窗口在客户端发送前已就绪
3. **改用 Jetty 或 Undertow**：其 h2c Prior Knowledge 实现与 JDK HttpClient 兼容性更好
4. **切回 HTTP/1.1**：当前 Kind 单节点环境 TCP 开销本身极低，h2c 连接复用优势无法体现

## 回滚方案

h2c 实验结论为**不启用**，需恢复 HTTP/1.1 基线配置：

```bash
# 1. 恢复 BuyerBffConfig.java（HTTP_2 → HTTP_1_1）
#    已在 BuyerBffConfig.java 中保留 HTTP_2，如需回滚：
#    将 HttpClient.Version.HTTP_2 改为 HttpClient.Version.HTTP_1_1

# 2. 移除 platform.yaml 中的 SERVER_HTTP2_ENABLED env
git checkout k8s/apps/platform.yaml
kubectl apply -f k8s/apps/platform.yaml -n shop
kubectl rollout restart deployment/buyer-bff deployment/seller-bff -n shop
kubectl rollout status deployment/buyer-bff -n shop --timeout=120s
```

## 原始数据

**HTTP/1.1 基线**（10 VUs, 60s）：
```
bff_latency_ms: avg=8.46ms  p(50)=6.79ms  p(90)=12.41ms  p(95)=16.49ms
error_rate: 0.00%
http_reqs: 7786  (129.7 req/s)
```

**h2c 实验**（10 VUs, 60s）：
```
bff_latency_ms: avg=47.79ms  p(50)=5.26ms  p(90)=207.36ms  p(95)=209.83ms
error_rate: 42.66%
http_reqs: 4662  (77.6 req/s)
```
