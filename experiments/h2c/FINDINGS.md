# HTTP/2 h2c 实验结论

## 实验条件

- **日期**：2026-03-25 ~ 2026-03-26
- **集群**：Kind（单节点，OrbStack，macOS）
- **并发**：10 VUs，60s steady state（总时长 90s；直连 buyer-bff，绕过 api-gateway）
- **端点**：POST /buyer/v1/marketplace/list、POST /buyer/v1/category/list
- **h2c 链路**：buyer-bff (`BUYER_BFF_HTTP_VERSION=HTTP_2`) → marketplace-service (Undertow + `SERVER_HTTP2_ENABLED=true`)
- **基线链路**：buyer-bff (`BUYER_BFF_HTTP_VERSION=HTTP_1_1`) → marketplace-service (同一 Undertow 实例，仍开启 h2c 能力但客户端保持 HTTP/1.1)

> **注意**：由于 OrbStack docker restart 后 NodePort 端口映射失效，测试通过 `kubectl port-forward deployment/buyer-bff` 直连 buyer-bff，规避了 api-gateway 层的路由开销，测量的是服务间协议切换本身的影响。

## 指标对比

| 指标 | HTTP/1.1 基线（Undertow） | h2c 初次实验（Tomcat 10.1） | h2c + Tomcat 11.0.20 + TomcatHttp2AC | h2c + Undertow（修复后） |
|------|---------------------------|----------------------------|--------------------------------------|------------------------|
| p50 latency | 6.19 ms | 5.26 ms | 3.59 ms | 6.17 ms |
| p95 latency | 16.42 ms | 209.83 ms | 15.92 ms（仅含快速失败） | 18.69 ms |
| avg latency | 8.55 ms | 47.79 ms | 10.65 ms（含快速失败） | 7.64 ms |
| 吞吐量 (req/s) | 142.8 req/s | 77.6 req/s | ~82 req/s（成功率仅24%） | 145.2 req/s |
| 错误率 | **0%** | **42.7%** | **75.8%** ⚠️（更差） | **0%** |
| 成功率 | 100% | 57.3% | 24.2% | 100% |

> **说明**：
> - Tomcat 路径失败的根因没有变化：并发流下仍会触发 FLOW_CONTROL_ERROR。
> - 改为 Undertow 后，h2c 已实现 **0% 错误率**，并且通过独立 JDK `HttpClient` 探针验证 `response.version() == HTTP_2`。
> - 在当前 workload（单下游调用、小 payload、10 VUs）下，h2c **吞吐量与平均延迟略优于 HTTP/1.1**，但 **p95 仍略差**。
>
> Tomcat 11 + TomcatHttp2AutoConfiguration 测试中，75.8% 错误率比初次实验更高。原因是：
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

## h2c 确认

- `otel-collector` 在测试环境未启动，无法依赖 Tempo 做最终核验
- 改用独立 JDK `HttpClient.Version.HTTP_2` 直连 `marketplace-service`，返回：

```text
status=200
version=HTTP_2
```

- 因此可以确认修复后的实验链路确实是 **buyer-bff → marketplace-service 的 h2c / HTTP_2**

## 最终结论

**更新结论：Tomcat 路径仍不建议启用；Undertow 路径已实现 0 错误 h2c。**

| 维度 | 结论 |
|------|------|
| 可靠性 | **Undertow + h2c：0% 错误率**；Tomcat 10.1 / 11.0 路径依旧存在 FLOW_CONTROL_ERROR |
| 吞吐量 | h2c 145.2 req/s，较 HTTP/1.1 的 142.8 req/s **+1.65%** |
| 平均延迟 | h2c 7.64 ms，较 HTTP/1.1 的 8.55 ms **-10.59%** |
| p50 延迟 | 基本持平（6.17 ms vs 6.19 ms） |
| p95 延迟 | h2c 18.69 ms，较 HTTP/1.1 的 16.42 ms **+13.84%**，尾延迟略差 |
| 若 h2c 仍不比 1.1 明显更好，原因 | 当前压测接口每个请求只触发 **1 次** 下游调用，且请求体只有 `{}`，多路复用与头压缩收益很有限；同时 JDK `HttpClient` 的 h2c 采用 **HTTP/1.1 Upgrade**，并把并发流集中到一个共享 HTTP/2 连接上，尾部延迟更容易受流调度/排队抖动影响 |
| SO 结论是否正确 | **正确，但只对一部分 workload 成立。** h2c 并不会自动快于 HTTP/1.1；收益取决于是否真的命中了多路复用的优势区间 |
| 建议 | **保留 HTTP/1.1 作为默认值**，继续把 h2c 作为可切换实验能力；若未来要默认开启 h2c，建议优先用于“单请求 fan-out 多下游”“同源高并发”“连接预算受限”等场景，而不是当前这类单下游、小报文接口 |

## SO 结论验证：一个 h2c 明显更优的正例

Stack Overflow 上“h2c 可能比 HTTP/1.1 更慢”的结论，放到本仓库里是**成立**的：上面的真实业务接口（单下游、小 payload）已经证明 h2c 不会天然获胜。

但这不是“h2c 一定更慢”，而是“**workload 依赖**”。为验证这一点，本轮补了一个专门放大多路复用收益的正例实验。

### 正例实验设计

- **端点**：`POST /buyer/v1/experiments/h2c/marketplace-burst`
- **可见性**：仅在 `load-test` profile 下启用，不影响默认运行态
- **行为**：buyer-bff 在一次外部请求内，并发发起 **4 个** 对同一 `marketplace-service` 的 `CATEGORY_LIST` 调用
- **客户端约束**：通过 `BUYER_JDK_JAVA_OPTIONS='-Djdk.httpclient.connectionPoolSize=1'` 把 JDK `HttpClient` 的 HTTP/1.1 连接池预算限制为 1；该属性**只影响 HTTP/1.1，不影响 HTTP/2 多路复用**
- **服务端**：仍使用 `marketplace-service` Undertow + `SERVER_HTTP2_ENABLED=true`
- **压测窗口**：2 VUs，`5s ramp + 20s steady`，总计 30s

这会构造一个“**同源 fan-out + 连接预算受限**”的场景：

- HTTP/1.1 需要在很小的连接预算里排队完成多个同源请求
- h2c 可以在单连接上复用多个 stream，同一外部请求里的 4 个下游调用不再被同样的连接预算卡死

### 正例实验结果（0 错误）

| 指标 | HTTP/1.1 (`connectionPoolSize=1`) | h2c (`connectionPoolSize=1`) | 变化 |
|------|-----------------------------------|------------------------------|------|
| p50 latency | 6.09 ms | 3.48 ms | -42.92% |
| avg latency | 8.56 ms | 4.30 ms | -49.70% |
| p90 latency | 12.95 ms | 7.17 ms | -44.63% |
| p95 latency | 22.93 ms | 8.92 ms | -61.09% |
| 吞吐量 (req/s) | 201.88 | 397.90 | +97.10% |
| 错误率 | 0.00% | 0.00% | 0 |

> **说明**：
> - 这组数据不再测“单次下游调用的小报文接口”，而是刻意测“同源 fan-out + 单连接预算”的条件。
> - 在这个条件下，h2c 的核心价值就是 **single connection multiplexing**，因此吞吐量接近翻倍、延迟接近减半。
> - 我们也尝试过更长时间、更高压的连接受限实验，但会把 HTTP/1.1 一侧推入 JDK `HttpClient` 的请求取消区间，污染“0 错误对比”。因此最终采用上面这个**已验证 0 错误**的 30s 窗口作为正例。

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
# 1. kubectl set env 激活 load-test profile，并切换 BUYER_BFF_HTTP_VERSION
# 2. marketplace-service 自动切到 SERVER_HTTP2_ENABLED=true
# 3. 建立 port-forward
# 4. 运行 k6
# 5. 退出时恢复生产配置（trap cleanup EXIT）

# 基线（HTTP/1.1）
bash experiments/h2c/run-baseline.sh

# h2c 实验
bash experiments/h2c/run-experiment.sh

# 复现“连接预算受限”正例（HTTP/1.1）
BUYER_JDK_JAVA_OPTIONS='-Djdk.httpclient.connectionPoolSize=1' \
LOAD_TEST_SCRIPT=experiments/h2c/marketplace-burst-test.js \
HEALTHCHECK_PATH=/buyer/v1/experiments/h2c/marketplace-burst \
HEALTHCHECK_BODY='{"fanout":4,"headerBytes":0}' \
FANOUT=4 TARGET_VUS=2 RAMP_SECONDS=5 STEADY_SECONDS=20 SLEEP_SECONDS=0 \
bash experiments/h2c/run-baseline.sh

# 复现“连接预算受限”正例（h2c）
BUYER_JDK_JAVA_OPTIONS='-Djdk.httpclient.connectionPoolSize=1' \
LOAD_TEST_SCRIPT=experiments/h2c/marketplace-burst-test.js \
HEALTHCHECK_PATH=/buyer/v1/experiments/h2c/marketplace-burst \
HEALTHCHECK_BODY='{"fanout":4,"headerBytes":0}' \
FANOUT=4 TARGET_VUS=2 RAMP_SECONDS=5 STEADY_SECONDS=20 SLEEP_SECONDS=0 \
bash experiments/h2c/run-experiment.sh
```

### 指标对比（无 CB 干扰，load-test profile）

| 指标 | HTTP/1.1（无 CB） | h2c（无 CB） | 变化 |
|------|-------------------|-------------|------|
| p50 latency | 6.19 ms | 6.17 ms | -0.25% |
| avg latency | 8.55 ms | 7.64 ms | -10.59% |
| p90 latency | 12.12 ms | 14.17 ms | +16.85% |
| p95 latency | 16.42 ms | 18.69 ms | +13.84% |
| 吞吐量 (req/s) | 142.8 | 145.2 | +1.65% |
| 错误率 | 0.00% | 0.00% | 0 |

> **说明**：
> - 本轮基线与 h2c 均在 `load-test` profile 下完成，CB / Bulkhead / TimeLimiter / Retry 均不会干扰结果。
> - h2c 已实现 **0% 错误率**，说明本轮修复解决的是**真实 transport error**，而不是只把错误“隐藏掉”。
> - h2c 在 **avg latency / throughput** 上略优，但在 **p90 / p95** 上略差；这表明当前 workload 对 h2c 的多路复用优势利用不足。

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
3. **✅ 改用 Undertow**：已验证有效，buyer-bff → marketplace-service h2c 可稳定跑到 0% 错误率
4. **✅ 保留 HTTP/1.1 默认值**：buyer-bff 现已支持 `BUYER_BFF_HTTP_VERSION` 配置切换，无需再改代码/重建镜像

## 回滚方案

h2c 当前作为**可切换实验能力**保留；如需恢复完全默认的 HTTP/1.1 运行态：

```bash
# buyer-bff 默认就是 HTTP_1_1；只需清理实验态 env
kubectl -n shop set env deployment/buyer-bff BUYER_BFF_HTTP_VERSION- SPRING_PROFILES_ACTIVE- JDK_JAVA_OPTIONS- MARKETPLACE_SERVICE_URL-
kubectl -n shop set env deployment/marketplace-service SERVER_HTTP2_ENABLED-
kubectl -n shop rollout status deployment/buyer-bff deployment/marketplace-service --timeout=180s
```

## 原始数据

**HTTP/1.1 基线（Undertow, 无 CB）**：
```
bff_latency_ms: avg=8.55ms  p(50)=6.19ms  p(90)=12.12ms  p(95)=16.42ms
error_rate: 0.00%
http_reqs: 12866  (142.8 req/s)
```

**h2c 修复后实验（Undertow, 无 CB）**：
```
bff_latency_ms: avg=7.64ms  p(50)=6.17ms  p(90)=14.17ms  p(95)=18.69ms
error_rate: 0.00%
http_reqs: 13076  (145.2 req/s)
```

**正例：HTTP/1.1（`connectionPoolSize=1`, `fanout=4`, `2 VUs`, 30s）**：
```
bff_latency_ms: avg=8.56ms  p(50)=6.09ms  p(90)=12.95ms  p(95)=22.93ms
error_rate: 0.00%
http_reqs: 6057  (201.88 req/s)
```

**正例：h2c（`connectionPoolSize=1`, `fanout=4`, `2 VUs`, 30s）**：
```
bff_latency_ms: avg=4.30ms  p(50)=3.48ms  p(90)=7.17ms  p(95)=8.92ms
error_rate: 0.00%
http_reqs: 11938  (397.90 req/s)
```

**历史：HTTP/1.1 基线（Tomcat 路径，早期）**：
```
bff_latency_ms: avg=8.46ms  p(50)=6.79ms  p(90)=12.41ms  p(95)=16.49ms
error_rate: 0.00%
http_reqs: 7786  (129.7 req/s)
```

**历史：h2c 初次实验**（10 VUs, 60s，Tomcat 10.1.x）：
```
bff_latency_ms: avg=47.79ms  p(50)=5.26ms  p(90)=207.36ms  p(95)=209.83ms
error_rate: 42.66%
http_reqs: 4662  (77.6 req/s)
```

**历史：h2c 修复验证实验**（10 VUs, 60s，Tomcat 11.0.20 + TomcatHttp2AutoConfiguration）：
```
bff_latency_ms: avg=10.65ms  p(50)=3.59ms  p(90)=11.22ms  p(95)=15.92ms（含快速失败请求）
error_rate: 75.81%（CB 多次 OPEN，marketplace-service 4次崩溃重启）
http_reqs: 9814  (163.6 req/s，绝大多数为即时失败的 CB 拒绝）
```
