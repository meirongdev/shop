# HTTP/2 h2c 性能实验实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 测量将 buyer-bff → domain service 的内部通信从 HTTP/1.1 切换到 h2c（HTTP/2 cleartext）后，端到端延迟和吞吐量是否有可观测提升。

**Architecture:** 当前两个 BFF 均使用 `SimpleClientHttpRequestFactory`（不支持 HTTP/2）。实验通过将其替换为共享的 `JdkClientHttpRequestFactory` bean（Java 内置 `HttpClient`，原生支持 h2c），并在目标 domain service Deployment 的 `env` 块中逐个启用 `SERVER_HTTP2_ENABLED: "true"`（不影响 gateway/auth/portal 等其他服务，保持实验隔离）。h2c 生效的验证以 Tempo span attribute `http.flavor: 2.0` 为准，而非 gateway 侧的 curl 检查。k6 向 api-gateway 施压，Tempo + Prometheus + Grafana 采集对比数据。

**Tech Stack:** Spring Boot 3.5 / Java 25, `JdkClientHttpRequestFactory`, k6, Prometheus, Grafana, Tempo

---

## 执行结果（2026-03-26）

本计划已完成，但执行过程中有几个重要偏差，必须补充说明：

- **Tomcat h2c 路径不可用**：JDK `HttpClient` 并发流下会稳定打出 `FLOW_CONTROL_ERROR`，Tomcat 10.1 与 11.0.20 均复现，因此最终实验服务端改为 **Undertow**
- **原始真实业务 workload 结论是“h2c 不一定赢”**：在 `buyer-bff -> marketplace-service` 的单下游、小 payload 请求上，修复后的 Undertow+h2c 虽然实现了 `0%` 错误率，但只在 `avg latency / throughput` 上略优，`p95` 反而略差
- **正例实验也已补齐**：通过 `load-test` 专用 `marketplace-burst` fan-out 端点，加上 `BUYER_JDK_JAVA_OPTIONS='-Djdk.httpclient.connectionPoolSize=1'` 构造“同源 fan-out + 连接预算受限”场景，得到一个 `0%` 错误、h2c 明显优于 HTTP/1.1 的稳定结果
- **结论归档位置**：以 `experiments/h2c/FINDINGS.md` 为最终权威结论文档；本文档保留为实施计划与落地偏差记录

最终正例参数为：

- `fanout=4`
- `TARGET_VUS=2`
- `5s ramp + 20s steady`
- `BUYER_JDK_JAVA_OPTIONS='-Djdk.httpclient.connectionPoolSize=1'`

最终正例结果为：

| 指标 | HTTP/1.1 | h2c | 变化 |
|------|----------|-----|------|
| avg latency | 8.56 ms | 4.30 ms | -49.70% |
| p95 latency | 22.93 ms | 8.92 ms | -61.09% |
| 吞吐量 | 201.88 req/s | 397.90 req/s | +97.10% |
| 错误率 | 0.00% | 0.00% | 0 |

---

## 文件清单

| 操作 | 文件 | 说明 |
|------|------|------|
| 新建 | `experiments/h2c/load-test.js` | k6 负载测试脚本 |
| 新建 | `experiments/h2c/run-baseline.sh` | 运行基线测试并保存结果 |
| 新建 | `experiments/h2c/run-experiment.sh` | 运行 h2c 实验并保存结果 |
| 新建 | `experiments/h2c/FINDINGS.md` | 实验结论文档 |
| 修改 | `k8s/apps/platform.yaml` | 目标 domain service Deployment 各自新增 `SERVER_HTTP2_ENABLED: "true"` env |
| 修改 | `buyer-bff/src/main/java/dev/meirong/shop/buyerbff/config/BuyerBffConfig.java` | 提取共享 `JdkClientHttpRequestFactory` bean，切换到 HTTP/2 |
| 修改 | `seller-bff/src/main/java/dev/meirong/shop/sellerbff/config/SellerBffConfig.java` | 同上 |

---

## Task 1：搭建实验目录结构

**Files:**
- Create: `experiments/h2c/` (directory)

- [ ] **Step 1: 创建实验目录**

```bash
mkdir -p experiments/h2c
touch experiments/h2c/.gitkeep
```

- [ ] **Step 2: Commit**

```bash
git add experiments/h2c/.gitkeep
git commit -m "chore: create h2c experiment directory"
```

---

## Task 2：编写 k6 负载测试脚本

**Files:**
- Create: `experiments/h2c/load-test.js`

**端点选择：** 使用无需认证的端点以简化压测流程。`POST /api/buyer/marketplace/list` 和 `POST /api/buyer/category/list` 均不需要 JWT，且会触发 buyer-bff → marketplace-service 的下游调用，足以观测 h2c 效果。若需测试多路复用收益（buyer-bff 并发调 5+ 服务），可升级到 `POST /api/buyer/dashboard/get`（需在 k6 setup 中先获取 JWT）。

- [ ] **Step 1: 写 k6 脚本**

```javascript
// experiments/h2c/load-test.js
import http from 'k6/http';
import { check, sleep } from 'k6';
import { Trend, Rate } from 'k6/metrics';

const latencyTrend = new Trend('bff_latency_ms', true);
const errorRate = new Rate('error_rate');

export const options = {
  stages: [
    { duration: '15s', target: 30 },  // ramp-up
    { duration: '60s', target: 30 },  // steady state
    { duration: '15s', target: 0  },  // ramp-down
  ],
  thresholds: {
    bff_latency_ms: ['p(95)<500'],
    error_rate: ['rate<0.01'],
  },
};

const BASE_URL = __ENV.BASE_URL || 'http://localhost:30080';

export default function () {
  const marketplaceRes = http.post(
    `${BASE_URL}/api/buyer/marketplace/list`,
    JSON.stringify({}),
    { headers: { 'Content-Type': 'application/json' } }
  );
  latencyTrend.add(marketplaceRes.timings.duration, { endpoint: 'marketplace' });
  errorRate.add(marketplaceRes.status !== 200);
  check(marketplaceRes, { 'marketplace 200': (r) => r.status === 200 });

  const categoryRes = http.post(
    `${BASE_URL}/api/buyer/category/list`,
    JSON.stringify({}),
    { headers: { 'Content-Type': 'application/json' } }
  );
  latencyTrend.add(categoryRes.timings.duration, { endpoint: 'category' });
  errorRate.add(categoryRes.status !== 200);
  check(categoryRes, { 'category 200': (r) => r.status === 200 });

  sleep(0.1);
}
```

- [ ] **Step 2: Commit**

```bash
git add experiments/h2c/load-test.js
git commit -m "test(experiment): add k6 load test script for h2c experiment"
```

---

## Task 3：编写运行脚本

**Files:**
- Create: `experiments/h2c/run-baseline.sh`
- Create: `experiments/h2c/run-experiment.sh`

- [ ] **Step 1: 写基线脚本**

```bash
#!/usr/bin/env bash
# experiments/h2c/run-baseline.sh
set -euo pipefail

RESULTS_FILE="experiments/h2c/baseline-results.json"
BASE_URL="${BASE_URL:-http://localhost:30080}"

echo "=== 验证服务可用 ==="
# 用实际 API 端点做可用性探测，避免 actuator 未在 NodePort 暴露的问题
HTTP_STATUS=$(curl -s -o /dev/null -w "%{http_code}" -X POST \
  -H 'Content-Type: application/json' -d '{}' \
  "${BASE_URL}/api/buyer/marketplace/list")
if [ "${HTTP_STATUS}" != "200" ]; then
  echo "❌ marketplace/list 返回 ${HTTP_STATUS}，请确认 Kind 集群和所有 Pod 已就绪"
  exit 1
fi

echo "=== 运行 HTTP/1.1 基线测试（~90s） ==="
k6 run \
  --env BASE_URL="${BASE_URL}" \
  --summary-export="${RESULTS_FILE}" \
  experiments/h2c/load-test.js

echo ""
echo "✅ 基线测试完成，结果已写入 ${RESULTS_FILE}"
echo "请在 Grafana 截取以下面板快照并保存到 experiments/h2c/grafana-baseline/："
echo "  - http_server_requests_seconds p95（buyer-bff）"
echo "  - JVM threads live（buyer-bff）"
echo "  - Tempo: buyer-bff → marketplace-service span，关注 http.flavor attribute"
```

- [ ] **Step 2: 写实验脚本**

```bash
#!/usr/bin/env bash
# experiments/h2c/run-experiment.sh
set -euo pipefail

RESULTS_FILE="experiments/h2c/h2c-results.json"
BASE_URL="${BASE_URL:-http://localhost:30080}"

echo "=== 验证服务可用 ==="
HTTP_STATUS=$(curl -s -o /dev/null -w "%{http_code}" -X POST \
  -H 'Content-Type: application/json' -d '{}' \
  "${BASE_URL}/api/buyer/marketplace/list")
if [ "${HTTP_STATUS}" != "200" ]; then
  echo "❌ 服务不可达，返回 ${HTTP_STATUS}"; exit 1
fi

echo "=== 验证 h2c 是否生效（通过 Tempo） ==="
echo "请在 Grafana Tempo 中搜索一条近期的 buyer-bff trace，"
echo "确认 marketplace-service span 的 http.flavor attribute 值为 '2.0'"
echo "确认后按回车继续，Ctrl+C 放弃..."
read -r

echo "=== 运行 HTTP/2 h2c 实验测试（~90s） ==="
k6 run \
  --env BASE_URL="${BASE_URL}" \
  --summary-export="${RESULTS_FILE}" \
  experiments/h2c/load-test.js

echo ""
echo "✅ 实验测试完成，结果已写入 ${RESULTS_FILE}"
```

- [ ] **Step 3: 赋权并 Commit**

```bash
chmod +x experiments/h2c/run-baseline.sh experiments/h2c/run-experiment.sh
git add experiments/h2c/
git commit -m "test(experiment): add baseline and experiment run scripts"
```

---

## Task 4：运行 HTTP/1.1 基线

- [ ] **Step 1: 确认所有 Pod Running**

```bash
kubectl get pods -n shop | grep -v Running | grep -v Completed | grep -v NAME
# 期望：无输出
```

- [ ] **Step 2: 运行基线压测**

```bash
bash experiments/h2c/run-baseline.sh
# 期望：k6 正常完成，baseline-results.json 生成
```

- [ ] **Step 3: 记录 Grafana 快照**

```bash
mkdir -p experiments/h2c/grafana-baseline
```

打开 Grafana（`http://localhost:30090`），截取：
- **buyer-bff**：`http_server_requests_seconds` p50/p95/p99
- **buyer-bff**：`jvm_threads_live`
- **Tempo**：buyer-bff → marketplace-service span，记录 `http.flavor` attribute 的值（应为 `1.1`）

- [ ] **Step 4: Commit 基线结果**

```bash
git add experiments/h2c/baseline-results.json experiments/h2c/grafana-baseline/
git commit -m "test(experiment): record HTTP/1.1 baseline results"
```

---

## Task 5：启用服务端 h2c（精确隔离：仅目标服务）

**Files:**
- Modify: `k8s/apps/platform.yaml`

> **重要：** 不修改 `shop-shared-config`，而是在每个目标 Deployment 的 `env` 块中单独添加。这样 api-gateway、auth-server、buyer/seller-portal 等不参与实验的服务不受影响，保证实验隔离性。

需要启用 h2c 的 domain service：`marketplace-service`、`profile-service`、`wallet-service`、`promotion-service`、`search-service`、`order-service`、`loyalty-service`。

- [ ] **Step 1: 在每个目标 Deployment 的 `env` 块中添加**

对 `k8s/apps/platform.yaml` 中以下每个 Deployment，在其 `containers[0].env` 下新增（若无 `env` 块则新建）：

```yaml
- name: SERVER_HTTP2_ENABLED
  value: "true"
```

涉及 Deployment：`marketplace-service`、`profile-service`、`wallet-service`、`promotion-service`、`search-service`、`order-service`（当 Deployment 存在时）、`loyalty-service`。

同时为 `buyer-bff` Deployment 也添加此 env（server 侧也需开启）：
```yaml
- name: SERVER_HTTP2_ENABLED
  value: "true"
```

- [ ] **Step 2: Apply 并滚动重启目标服务**

```bash
kubectl apply -f k8s/apps/platform.yaml -n shop
kubectl rollout restart deployment/buyer-bff \
  deployment/marketplace-service deployment/profile-service \
  deployment/wallet-service deployment/promotion-service \
  deployment/search-service deployment/loyalty-service -n shop
kubectl rollout status deployment/buyer-bff -n shop --timeout=120s
```

- [ ] **Step 3: 确认服务端 h2c 生效（port-forward 直连 buyer-bff）**

```bash
# 直连 buyer-bff，跳过 gateway，验证服务端 HTTP/2
kubectl port-forward -n shop svc/buyer-bff 18080:8080 &
PF_PID=$!
sleep 2

curl -s -o /dev/null -w "HTTP 版本: %{http_version}\n" \
  --http2-prior-knowledge \
  -X POST -H 'Content-Type: application/json' -d '{}' \
  "http://localhost:18080/api/buyer/marketplace/list"

kill $PF_PID
# 期望：HTTP 版本: 2
# 若仍显示 1.1，检查 SERVER_HTTP2_ENABLED 是否正确注入
```

- [ ] **Step 4: Commit**

```bash
git add k8s/apps/platform.yaml
git commit -m "feat(experiment): enable server-side h2c on buyer-bff and target domain services"
```

---

## Task 6：升级 buyer-bff 客户端到 HTTP/2（共享 HttpClient bean）

**Files:**
- Modify: `buyer-bff/src/main/java/dev/meirong/shop/buyerbff/config/BuyerBffConfig.java`

当前 `requestFactory()` 方法被调用两次，产生两个独立的 `HttpClient` 实例和两个连接池，削减 h2c 多路复用收益。重构为单一 `JdkClientHttpRequestFactory` bean，两处共用。

- [ ] **Step 1: 修改 BuyerBffConfig.java**

完整替换文件内容：

```java
package dev.meirong.shop.buyerbff.config;

import dev.meirong.shop.buyerbff.client.SearchServiceClient;
import java.net.http.HttpClient;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.support.RestClientAdapter;
import org.springframework.web.service.invoker.HttpServiceProxyFactory;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(BuyerClientProperties.class)
public class BuyerBffConfig {

    @Bean
    JdkClientHttpRequestFactory jdkClientHttpRequestFactory(BuyerClientProperties properties) {
        HttpClient httpClient = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_2)
                .connectTimeout(properties.connectTimeout())
                .build();
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(httpClient);
        factory.setReadTimeout(properties.readTimeout());
        return factory;
    }

    @Bean
    RestClient.Builder restClientBuilder(JdkClientHttpRequestFactory jdkClientHttpRequestFactory) {
        return RestClient.builder()
                .requestFactory(jdkClientHttpRequestFactory);
    }

    @Bean
    SearchServiceClient searchServiceClient(BuyerClientProperties properties,
                                            JdkClientHttpRequestFactory jdkClientHttpRequestFactory) {
        RestClient searchRestClient = RestClient.builder()
                .requestFactory(jdkClientHttpRequestFactory)
                .baseUrl(properties.searchServiceUrl())
                .defaultHeader("X-Internal-Token", properties.internalToken())
                .build();
        HttpServiceProxyFactory factory = HttpServiceProxyFactory
                .builderFor(RestClientAdapter.create(searchRestClient))
                .build();
        return factory.createClient(SearchServiceClient.class);
    }
}
```

- [ ] **Step 2: 验证编译通过**

```bash
cd buyer-bff && ../mvnw compile -q && cd ..
# 期望：BUILD SUCCESS
```

- [ ] **Step 3: Commit**

```bash
git add buyer-bff/src/main/java/dev/meirong/shop/buyerbff/config/BuyerBffConfig.java
git commit -m "feat(experiment): switch buyer-bff to shared JdkClientHttpRequestFactory with HTTP_2"
```

---

## Task 7：升级 seller-bff 客户端到 HTTP/2（共享 HttpClient bean）

**Files:**
- Modify: `seller-bff/src/main/java/dev/meirong/shop/sellerbff/config/SellerBffConfig.java`

- [ ] **Step 1: 修改 SellerBffConfig.java**

完整替换文件内容：

```java
package dev.meirong.shop.sellerbff.config;

import java.net.http.HttpClient;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(SellerClientProperties.class)
public class SellerBffConfig {

    @Bean
    JdkClientHttpRequestFactory jdkClientHttpRequestFactory(SellerClientProperties properties) {
        HttpClient httpClient = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_2)
                .connectTimeout(properties.connectTimeout())
                .build();
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(httpClient);
        factory.setReadTimeout(properties.readTimeout());
        return factory;
    }

    @Bean
    RestClient.Builder restClientBuilder(JdkClientHttpRequestFactory jdkClientHttpRequestFactory) {
        return RestClient.builder()
                .requestFactory(jdkClientHttpRequestFactory);
    }

    @Bean
    RestClient searchRestClient(SellerClientProperties properties,
                                JdkClientHttpRequestFactory jdkClientHttpRequestFactory) {
        return RestClient.builder()
                .requestFactory(jdkClientHttpRequestFactory)
                .baseUrl(properties.searchServiceUrl())
                .defaultHeader("X-Internal-Token", properties.internalToken())
                .build();
    }
}
```

- [ ] **Step 2: 验证编译通过**

```bash
cd seller-bff && ../mvnw compile -q && cd ..
```

- [ ] **Step 3: Commit**

```bash
git add seller-bff/src/main/java/dev/meirong/shop/sellerbff/config/SellerBffConfig.java
git commit -m "feat(experiment): switch seller-bff to shared JdkClientHttpRequestFactory with HTTP_2"
```

---

## Task 8：重建镜像并部署实验版本

- [ ] **Step 1: 构建镜像**

```bash
make build-images
```

- [ ] **Step 2: 加载到 Kind**

```bash
make load-images
```

- [ ] **Step 3: 滚动重启 BFF**

```bash
kubectl rollout restart deployment/buyer-bff deployment/seller-bff -n shop
kubectl rollout status deployment/buyer-bff deployment/seller-bff -n shop --timeout=120s
```

- [ ] **Step 4: 确认 Pod 正常**

```bash
kubectl get pods -n shop -l 'app in (buyer-bff,seller-bff)'
# 期望：STATUS = Running
```

---

## Task 9：验证端到端 h2c 全链路

h2c 的验证以 Tempo trace 为权威来源，因为 curl 只能检查被测端点的协议版本，无法看到 BFF 与下游服务之间的内部协议。

- [ ] **Step 1: 触发一次真实请求并在 Tempo 中检查**

```bash
curl -s -X POST -H 'Content-Type: application/json' -d '{}' \
  http://localhost:30080/api/buyer/marketplace/list | jq .
```

- [ ] **Step 2: 在 Grafana Tempo 中查找 span**

打开 `http://localhost:30090` → Explore → Tempo，搜索 Service = `buyer-bff`，找到最近一条 trace，展开到 `marketplace-service` span。

确认该 span 的 attributes 中：
- `http.flavor` = `2.0`（HTTP/2）

若仍为 `1.1`，说明服务端 h2c 或客户端配置未生效，不要继续运行实验。

> **注意：** Resilience4j `TimeLimiter` 在取消超时 Future 时对 `JdkClientHttpRequestFactory` 的行为与 `SimpleClientHttpRequestFactory` 略有不同（HttpClient 的中断语义不同）。实验过程中需关注 Grafana 中的 `resilience4j_timelimiter_calls_total{outcome="timeout"}` 指标，若超时率异常上升，需终止实验排查。

---

## Task 10：运行 h2c 实验并采集数据

- [ ] **Step 1: 运行实验压测**

```bash
bash experiments/h2c/run-experiment.sh
# 期望：手动确认 Tempo h2c 验证后，k6 完成 90s 测试
```

- [ ] **Step 2: 记录 Grafana 快照**

```bash
mkdir -p experiments/h2c/grafana-h2c
```

截取与基线相同的面板，保存到 `experiments/h2c/grafana-h2c/`。

- [ ] **Step 3: Commit 实验结果**

```bash
git add experiments/h2c/h2c-results.json experiments/h2c/grafana-h2c/
git commit -m "test(experiment): record HTTP/2 h2c experiment results"
```

---

## Task 11：分析结果并撰写结论

**Files:**
- Create: `experiments/h2c/FINDINGS.md`

- [ ] **Step 1: 提取 k6 关键指标**

`--summary-export` 输出标准 JSON 对象，可直接用 jq 提取：

```bash
echo "=== 基线 (HTTP/1.1) ===" && \
jq '.metrics.bff_latency_ms | {p50: .values["p(50)"], p95: .values["p(95)"], p99: .values["p(99)"]}' \
  experiments/h2c/baseline-results.json

echo "=== 实验 (h2c) ===" && \
jq '.metrics.bff_latency_ms | {p50: .values["p(50)"], p95: .values["p(95)"], p99: .values["p(99)"]}' \
  experiments/h2c/h2c-results.json
```

- [ ] **Step 2: 写结论文档**

```markdown
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
```

- [ ] **Step 3: Commit**

```bash
git add experiments/h2c/FINDINGS.md
git commit -m "docs(experiment): add h2c experiment findings"
```

---

## 回滚方案

若 h2c 引入超时增加或异常，按以下步骤回滚：

```bash
# 1. 移除目标 Deployment 中的 SERVER_HTTP2_ENABLED env（编辑 platform.yaml）
#    或直接通过 kubectl patch 移除（需逐个 Deployment 操作）
git checkout k8s/apps/platform.yaml
kubectl apply -f k8s/apps/platform.yaml -n shop

# 2. 回滚 BFF 镜像
kubectl rollout undo deployment/buyer-bff deployment/seller-bff -n shop
kubectl rollout status deployment/buyer-bff -n shop --timeout=120s
```

---

## 进阶扩展（可选）

若基础实验结果正向，可进一步测试：

1. **Dashboard 端点**（5+ 并发下游调用，多路复用收益最大）
   - k6 `setup()` 先用 auth-server 获取 JWT，然后压 `POST /api/buyer/dashboard/get`

2. **TimeLimiter 取消问题深挖**
   - 在 k6 运行期间用 `kubectl exec` 进入 buyer-bff 查看 `jstack`，对比两种 factory 下线程状态

3. **虚拟线程 + h2c 组合观察**
   - buyer-bff 已启用 `spring.threads.virtual.enabled: true`，在 Tempo 中用 `thread.name` attribute 观察虚拟线程 + h2c 对 tail latency 的联合影响
