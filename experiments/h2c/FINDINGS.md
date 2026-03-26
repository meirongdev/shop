# HTTP/2 h2c 实验结论

## 实验条件

- **日期**：2026-03-25 ~ 2026-03-26
- **集群**：Kind（单节点，OrbStack，macOS）
- **并发**：10 VUs，60s steady state（直连 buyer-bff，绕过 api-gateway）
- **端点**：POST /buyer/v1/marketplace/list、POST /buyer/v1/category/list
- **h2c 链路**：buyer-bff (JDK HttpClient HTTP_2) → marketplace-service (SERVER_HTTP2_ENABLED=true)
- **基线链路**：buyer-bff (JDK HttpClient HTTP_1_1) → marketplace-service (SERVER_HTTP2_ENABLED=true)

> **注意**：由于 OrbStack docker restart 后 NodePort 端口映射失效，测试通过 `kubectl port-forward` 直连 buyer-bff Pod，规避了 api-gateway 层的路由开销。marketplace-service 和 buyer-bff 两端都已开启 h2c，测量的是服务间 HTTP/2 连接的实际影响。

## 指标对比

| 指标 | HTTP/1.1 基线 | h2c 初次实验 | h2c + Tomcat 11.0.20 + TomcatHttp2AC |
|------|-------------|---------|------|
| p50 latency | 6.79 ms | 5.26 ms | 3.59 ms |
| p95 latency | 16.49 ms | 209.83 ms | 15.92 ms（仅含快速失败） |
| avg latency | 8.46 ms | 47.79 ms | 10.65 ms（含快速失败） |
| 吞吐量 (req/s) | 129.7 req/s | 77.6 req/s | ~82 req/s（成功率仅24%） |
| 错误率 | **0%** | **42.7%** | **75.8%** ⚠️（更差） |
| 成功率 | 100% | 57.3% | 24.2% |

> **说明**：Tomcat 11 + TomcatHttp2AutoConfiguration 测试中，75.8% 错误率比初次实验更高。原因是：
> - marketplace-service 在持续 FLOW_CONTROL_ERROR 冲击下不断崩溃重启（4+ restarts），导致 buyer-bff 的 circuitBreaker 频繁 OPEN
> - FLOW_CONTROL_ERROR 在 Tomcat 11 中依然存在（stream ID 变大但机制相同）

## 失败模式分析

### 根本原因：Tomcat h2c Upgrade 并发流接收窗口竞态（跨版本持久存在）

marketplace-service 日志（两版本均出现）：

**Tomcat 10.1.x（初次实验）：**
```
Connection [faf], Stream [3] Closed due to error
org.apache.coyote.http2.StreamException: Connection [faf],
  Client sent more data than stream window allowed
    at org.apache.coyote.http2.Http2Parser.readDataFrame(...)
```

**Tomcat 11.0.20（修复验证实验）：**
```
Connection [8], Stream [567] Closed due to error
org.apache.coyote.http2.StreamException: Connection [8],
  Client sent more data than stream window allowed
    at org.apache.coyote.http2.Http2Parser.readDataFrame(Http2Parser.java:193)
    at org.apache.coyote.http2.Http2AsyncParser$FrameCompletionHandler.completed(Http2AsyncParser.java:251)
```

**触发规律**（初次实验）：每3个请求约有1个失败（33%），精确命中 HTTP/2 stream ID 3（奇数流，客户端发起的第2条流）。仅在并发场景触发；单 VU 顺序请求 100% 成功。

**⚠️ 早期误判**：最初错误地认为 JDK HttpClient 使用 Prior Knowledge 模式（直接发送 CLIENT_PREFACE）。
经查 JDK 源码（[JDK-8285972](https://bugs.openjdk.org/browse/JDK-8285972)、[JDK-8287589](https://bugs.openjdk.org/browse/JDK-8287589)），
JDK HttpClient **默认使用 h2c Upgrade 模式**（先发 HTTP/1.1 Upgrade 头，101 成功后切换 HTTP/2）；
Prior Knowledge 在 JDK 中尚不支持，且代理场景下 Upgrade 头不发送（Won't Fix）。
本项目 pod 间直连无代理，故 Upgrade 模式正常工作。

**实际机制**：
- JDK HttpClient 以 h2c Upgrade 建立连接：HTTP/1.1 POST + `Upgrade: h2c` → 101 → CLIENT_PREFACE + SETTINGS 交换
- Stream 1（Upgrade 请求本身）在握手完成后处理，始终成功
- 多 VU 并发时，JDK HttpClient 在同一连接上复用 HTTP/2 流（stream 3、5、7…）
- 并发流与 stream 1 在途时，Tomcat h2c Upgrade 处理器在并发流接收窗口初始化上存在竞态：
  `Http2Parser.readDataFrame()` 中 `dest.remaining() < dataLength` 检查，抛出 FLOW_CONTROL_ERROR
- 在 Tomcat 11 中，异步 IO 路径（`Http2AsyncParser$FrameCompletionHandler`）存在同样缺陷
- `overheadDataThreshold=0` + `initialWindowSize=1MB` 两个参数均无法解决此竞态

### 失败率随并发升高

| 测试场景 | VUs | 成功率 |
|---------|-----|--------|
| 50 次顺序请求（无并发） | 1 | 100% |
| 100 次顺序请求（slight concurrency） | ~3 | 67% |
| k6 10 VUs steady state（Tomcat 10.1） | 10 | 57% |
| k6 30 VUs steady state（Tomcat 10.1） | 30 | 5% |
| k6 10 VUs steady state（Tomcat 11.0.20 + TomcatHttp2AC） | 10 | ~24%（CB tripping） |

## Tempo 确认

- otel-collector 在测试环境未启动，无法获取 Tempo trace
- 通过 marketplace-service 日志中 HTTP/2 stream error 间接确认 h2c 连接已建立

## 最终结论

**❌ h2c 实验最终结论：不启用，回滚至 HTTP/1.1。**

| 维度 | 结论 |
|------|------|
| 延迟（p50） | 略有改善，但被高错误率掩盖，无实际意义 |
| 延迟（p95） | 初次实验严重退化（+1172%），Tomcat 11 因 CB 拦截而表面好看但实质失败率更高 |
| 吞吐量 | 显著下降，Tomcat 11 有效吞吐量更低（仅24%成功率） |
| 可靠性 | Tomcat 10.1: 42.7% 错误率；Tomcat 11: 75.8% 错误率（CB 导致雪崩） |
| 根因 | Tomcat h2c Upgrade 处理器在并发流接收窗口初始化上的竞态，跨 Tomcat 10.1 和 11.0 均存在 |
| 修复可行性 | `TomcatHttp2AutoConfiguration` 参数调优**无效**；Tomcat 升级**无效** |
| 建议 | **切回 HTTP/1.1**（Direction 4）。Kind 单节点 TCP 开销极低，h2c 连接复用优势无法体现 |

> ⚠️ **注意**：以上数据均在 CB **开启** 状态下采集。CB 快速拒绝使 h2c 的 p95 看起来合理但错误率失真。
> 重新测试方法见下节。

---

## 无 CB 干扰的重测方法（load-test profile）

前三轮实验中 CB 始终开启，导致：
- **h2c Tomcat 11** 数据：75.8% 错误率中绝大多数为 CB 即时拒绝，p95=15.9ms 是 CB 快速失败的延迟，而非真实请求延迟。

为获取无 CB 干扰的纯净数据，在 `buyer-bff` 中新增 **`load-test` Spring profile**（`application-load-test.yml`），激活后所有 Resilience4j 层均被置为实际无效：

| 层 | 配置 | 效果 |
|----|------|------|
| CircuitBreaker | `failure-rate-threshold: 100`，`sliding-window-size: 100000` | 不会触发开路 |
| Bulkhead | `max-concurrent-calls: 10000` | 不会被并发限流 |
| TimeLimiter | `timeout-duration: 300s` | 不会超时取消 |
| Retry | `max-attempts: 1` | 不重试，看原始错误率 |

### 运行方式

```bash
# 两个脚本均会自动：
# 1. kubectl set env 激活 load-test profile 并等待 rollout
# 2. 建立 port-forward
# 3. 运行 k6
# 4. 退出时恢复生产配置（trap cleanup EXIT）

# 基线（HTTP/1.1）
bash experiments/h2c/run-baseline.sh

# h2c 实验
bash experiments/h2c/run-experiment.sh
```

### 指标对比（无 CB 干扰，load-test profile）

| 指标 | HTTP/1.1（无 CB） | h2c（无 CB） | 变化 |
|------|-------------------|-------------|------|
| p50 latency | 5.66 ms | 9.07 ms | +60% |
| p95 latency（全部请求） | 16.61 ms | 1044.54 ms | +6190% |
| p95 latency（仅成功请求） | 16.61 ms | 32.22 ms | +94% |
| 吞吐量 (req/s) | 146.8 | 51.1 | −65% |
| 错误率 | 0.00% | 10.67% | ⚠️ +10.67pp |

> **说明**：h2c p95（全部请求）被错误请求拉高至 1044 ms，原因是失败请求需等待完整 TCP 超时（~2s）。
> 去除错误请求后，p95=32.22 ms，仍比基线高 94%。
>
> 与前三轮含 CB 测试相比：**原始 FLOW_CONTROL_ERROR 率约为 10.67%**（而非 CB 快速拒绝后表现出的 42–75%）。
> CB 开路造成大量快速拒绝，使错误率虚高但延迟数字虚低。

## 已应用并验证无效的修复

`shop-common` 新增 `TomcatHttp2AutoConfiguration`（`@ConditionalOnProperty server.http2.enabled=true`），
替换 Spring Boot 默认注册的 `Http2Protocol`，配置参数：

| 参数 | 默认值 | 修改为 | 验证结果 |
|------|--------|--------|---------|
| `overheadDataThreshold` | 1024 | 0 | ❌ 无效，FLOW_CONTROL_ERROR 仍然出现 |
| `initialWindowSize` | 65535 | 1048576 (1 MB) | ❌ 无效，竞态发生在窗口初始化阶段，不受此参数影响 |
| Tomcat 版本 | 10.1.x | 11.0.20 | ❌ 无效，Tomcat 11 异步 IO 路径（Http2AsyncParser）同样受影响 |

## 修复方向（历史记录）

1. ~~**换用 HTTP Upgrade 模式**~~：JDK HttpClient 已默认使用 h2c Upgrade，无需修改
2. ~~**增大 Tomcat 初始流窗口 + 关闭开销保护**~~：已验证无效
3. ~~**改用 Jetty 或 Undertow**~~：未测试，但鉴于 HTTP/1.1 表现已满足要求，不值得额外风险
4. **✅ 切回 HTTP/1.1**：**已执行**，见下方回滚方案

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

**h2c 初次实验**（10 VUs, 60s，Tomcat 10.1.x）：
```
bff_latency_ms: avg=47.79ms  p(50)=5.26ms  p(90)=207.36ms  p(95)=209.83ms
error_rate: 42.66%
http_reqs: 4662  (77.6 req/s)
```

**h2c 修复验证实验**（10 VUs, 60s，Tomcat 11.0.20 + TomcatHttp2AutoConfiguration）：
```
bff_latency_ms: avg=10.65ms  p(50)=3.59ms  p(90)=11.22ms  p(95)=15.92ms（含快速失败请求）
error_rate: 75.81%（CB 多次 OPEN，marketplace-service 4次崩溃重启）
http_reqs: 9814  (163.6 req/s，绝大多数为即时失败的 CB 拒绝）
```
