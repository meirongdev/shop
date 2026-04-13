# Contract Testing Guide — Spring Cloud Contract

> 版本：1.0 | 日期：2026-04-12
> 适用范围：BFF ↔ Domain Service 接口兼容性验证
> 模式：Producer-Driven（Domain Service 定义契约，BFF 消费 stub）

---

## 一、为什么需要契约测试

### 1.1 当前痛点

```
没有契约测试时：
  1. order-service 修改了 OrderDto（status: String → Enum）
  2. 修改了 Controller 路径（/v1/orders → /v2/orders）
  3. 本地同时启动 order-service + buyer-bff 才能验证 → Testcontainers 启动 30-60 秒
  4. 或更糟：部署到测试/生产环境才发现不兼容
```

### 1.2 契约测试解决什么

```
有契约测试后：
  1. order-service 修改 API → 运行契约测试 → 对比旧契约 → 发现破坏性变更
  2. 如果破坏性变更：
     a) 升级版本号（/v1 → /v2），或
     b) 保持向后兼容
  3. BFF 侧使用 stub 测试，不需要启动真实服务
  4. 契约断裂在编译/单元测试阶段就被捕获
```

### 1.3 与现有 WireMock 测试的关系

| 维度 | 现有 WireMock 测试 | Spring Cloud Contract |
|------|-------------------|----------------------|
| Stub 来源 | 手写 `stubFor(get(...))` | Producer 自动生成 |
| 同步机制 | 手动维护，容易与 Producer 脱节 | Producer 发版时自动更新 |
| 断裂检测 | 只在 BFF 侧运行 | Producer 侧也验证 |
| 维护成本 | 高（双端维护） | 低（Producer 定义一次） |

**结论**：SCC 不替代 WireMock，而是替代"手写 stub"的部分。复杂场景（异常响应、超时）仍可补充 WireMock。

---

## 二、架构概览

```
Domain Service (Producer)
  ├── src/test/resources/contracts/
  │   └── buyer-bff/
  │       ├── listOrders.groovy      ← 契约定义
  │       └── createOrder.groovy
  └── spring-cloud-contract-maven-plugin
       └── 生成 order-service-1.0.0-stubs.jar
            └→ 发布到 Maven 仓库

Buyer-BFF (Consumer)
  ├── pom.xml
  │   └── <dependency>order-service:stubs</dependency>
  └── src/test/java/
      └── OrderClientContractTest.java
           └── @AutoConfigureStubRunner(ids = "order-service:...:stubs:8080")
                └→ 自动启动 stub 服务器
                └→ @HttpExchange 客户端指向 stub
                └→ 验证响应结构与契约一致
```

---

## 三、实施步骤

### Phase 1: Domain Service 定义契约

**以 order-service 为例**：

#### 3.1.1 添加 SCC 插件

```xml
<!-- services/order-service/pom.xml -->
<plugin>
    <groupId>org.springframework.cloud</groupId>
    <artifactId>spring-cloud-contract-maven-plugin</artifactId>
    <version>${spring-cloud-contract.version}</version>
    <extensions>true</extensions>
    <configuration>
        <packageWithBaseClasses>dev.meirong.shop.order.contract</packageWithBaseClasses>
        <baseClassMappings>
            <baseClassMapping>
                <contractDirectoryRegex>.*buyer-bff.*</contractDirectoryRegex>
                <baseClass>dev.meirong.shop.order.contract.BuyerBffBase</baseClass>
            </baseClassMapping>
        </baseClassMappings>
    </configuration>
</plugin>
```

#### 3.1.2 编写契约定义

```groovy
// services/order-service/src/test/resources/contracts/buyer-bff/listOrders.groovy
import org.springframework.cloud.contract.spec.Contract

Contract.make {
    description("Buyer lists their orders with pagination")
    
    label("list_orders_success")
    
    request {
        method 'GET'
        url '/api/order/v1/orders' {
            queryParameters {
                parameter('buyerId', $(consumer(123), producer(regex('[0-9]+'))))
                parameter('page', $(consumer(0), producer(regex('[0-9]+'))))
                parameter('size', $(consumer(20), producer(regex('[0-9]+'))))
            }
        }
        headers {
            contentType(applicationJsonUtf8())
            header('X-Request-Id', $(consumer('req-001'), producer(regex(nonEmpty()))))
        }
    }
    
    response {
        status 200
        body([
            data: [
                items: [[
                    orderId: $(regex(nonEmpty())),
                    orderNo: $(regex(nonEmpty())),
                    status: $(regex('PENDING|PAID|SHIPPED|DELIVERED|CANCELLED')),
                    totalAmount: $(regex(number())),
                    currency: $(regex('CNY|USD|EUR')),
                    createdAt: $(regex(dateTime())),
                    updatedAt: $(regex(dateTime()))
                ]],
                total: $(regex(number())),
                page: $(consumer(0), producer(regex(number()))),
                size: $(consumer(20), producer(regex(number())))
            ],
            success: true
        ])
        headers {
            contentType(applicationJsonUtf8())
        }
    }
}
```

```groovy
// services/order-service/src/test/resources/contracts/buyer-bff/createOrder.groovy
import org.springframework.cloud.contract.spec.Contract

Contract.make {
    description("Buyer creates a new order")
    
    label("create_order_success")
    
    request {
        method 'POST'
        url '/api/order/v1/orders'
        body([
            buyerId: 123L,
            items: [[
                productId: "prod-001",
                skuId: "sku-001",
                quantity: 2
            ]],
            shippingAddressId: "addr-001"
        ])
        headers {
            contentType(applicationJsonUtf8())
        }
    }
    
    response {
        status 201
        body([
            data: [
                orderId: $(regex(nonEmpty())),
                orderNo: $(regex(nonEmpty())),
                status: "PENDING",
                totalAmount: $(regex(number())),
                items: [[
                    productId: "prod-001",
                    quantity: 2,
                    unitPrice: $(regex(number()))
                ]]
            ],
            success: true
        ])
        headers {
            contentType(applicationJsonUtf8())
        }
    }
}
```

```groovy
// services/order-service/src/test/resources/contracts/buyer-bff/getOrder.groovy
import org.springframework.cloud.contract.spec.Contract

Contract.make {
    description("Buyer gets order details by ID")
    
    label("get_order_success")
    
    request {
        method 'GET'
        url '/api/order/v1/orders/'$(regex(nonEmpty()))
    }
    
    response {
        status 200
        body([
            data: [
                orderId: $(regex(nonEmpty())),
                orderNo: $(regex(nonEmpty())),
                status: $(regex('PENDING|PAID|SHIPPED|DELIVERED|CANCELLED')),
                totalAmount: $(regex(number())),
                items: [[
                    productId: $(regex(nonEmpty())),
                    quantity: $(regex(number())),
                    unitPrice: $(regex(number()))
                ]],
                createdAt: $(regex(dateTime()))
            ],
            success: true
        ])
        headers {
            contentType(applicationJsonUtf8())
        }
    }
}
```

#### 3.1.3 错误响应契约

```groovy
// services/order-service/src/test/resources/contracts/buyer-bff/orderNotFound.groovy
import org.springframework.cloud.contract.spec.Contract

Contract.make {
    description("Order not found returns 404")
    
    label("get_order_not_found")
    
    request {
        method 'GET'
        url '/api/order/v1/orders/nonexistent-id'
    }
    
    response {
        status 404
        body([
            success: false,
            code: "SC_ORDER_NOT_FOUND",
            message: "Order not found",
            traceId: $(regex(nonEmpty())),
            // RFC 7807 Problem Details 兼容字段
            type: "about:blank",
            title: "Not Found",
            status: 404
        ])
        headers {
            contentType(applicationJsonUtf8())
        }
    }
}
```

#### 3.1.4 生成 Stub Base 类

```java
// services/order-service/src/test/java/.../contract/BuyerBffBase.java
package dev.meirong.shop.order.contract;

import io.restassured.module.mockmvc.RestAssuredMockMvc;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.web.context.WebApplicationContext;

@SpringBootTest
public abstract class BuyerBffBase {
    
    @Autowired
    private WebApplicationContext context;
    
    @BeforeEach
    void setup() {
        RestAssuredMockMvc.webAppContextSetup(context);
    }
}
```

#### 3.1.5 验证 Stub 生成

```bash
# 在 order-service 目录执行
./mvnw spring-cloud-contract:convert

# 生成 stub jar
./mvnw install -DskipTests

# 验证产物
ls ~/.m2/repository/dev/meirong/shop/order-service/1.0.0/
# 应该看到：
#   order-service-1.0.0.jar
#   order-service-1.0.0-stubs.jar    ← 这是 BFF 消费的 stub
```

---

### Phase 2: 发布 Stub 到 Maven 仓库

#### 3.2.1 配置分类器

SCC 插件会自动生成 `stubs` 分类器的 jar，发布到 Maven 仓库。确保 `distributionManagement` 配置正确：

```xml
<!-- 父 pom.xml 或 order-service/pom.xml -->
<distributionManagement>
    <repository>
        <id>github</id>
        <url>https://maven.pkg.github.com/org/shop</url>
    </repository>
</distributionManagement>
```

#### 3.2.2 CI 中自动发布

```yaml
# .github/workflows/ci.yml 的 maven-verify job 已有
- name: Deploy
  run: ./mvnw deploy -DskipTests
  # stub jar 会作为独立 artifact 发布
```

---

### Phase 3: BFF 消费 Stub

#### 3.3.1 添加 Stub 依赖

```xml
<!-- services/buyer-bff/pom.xml -->
<dependency>
    <groupId>dev.meirong.shop</groupId>
    <artifactId>order-service</artifactId>
    <classifier>stubs</classifier>
    <scope>test</scope>
</dependency>

<dependency>
    <groupId>org.springframework.cloud</groupId>
    <artifactId>spring-cloud-starter-contract-stub-runner</artifactId>
    <scope>test</scope>
</dependency>
```

#### 3.3.2 编写契约测试

```java
// services/buyer-bff/src/test/java/.../contract/OrderClientContractTest.java
package dev.meirong.shop.buyerbff.contract;

import dev.meirong.shop.buyerbff.client.OrderServiceClient;
import dev.meirong.shop.contracts.order.api.OrderApi;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.contract.stubrunner.spring.AutoConfigureStubRunner;
import org.springframework.cloud.contract.stubrunner.spring.StubRunnerProperties;
import org.springframework.test.context.TestPropertySource;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(
    properties = {
        "shop.client.order.base-url=http://localhost:8080"
    }
)
@AutoConfigureStubRunner(
    ids = "dev.meirong.shop:order-service:+:stubs:8080",
    stubsMode = StubRunnerProperties.StubsMode.LOCAL
)
class OrderClientContractTest {
    
    @Autowired
    private OrderServiceClient orderClient;
    
    @Test
    void shouldListOrdersSuccessfully() {
        var result = orderClient.listOrders(123L, 0, 20);
        
        assertThat(result).isNotNull();
        assertThat(result.success()).isTrue();
        assertThat(result.data()).isNotNull();
        assertThat(result.data().items()).isNotEmpty();
        
        var order = result.data().items().get(0);
        assertThat(order.orderId()).isNotBlank();
        assertThat(order.status()).isIn(
            "PENDING", "PAID", "SHIPPED", "DELIVERED", "CANCELLED"
        );
        assertThat(order.totalAmount()).isPositive();
    }
    
    @Test
    void shouldCreateOrderSuccessfully() {
        var request = new OrderApi.CreateOrderRequest(
            123L,
            List.of(new OrderApi.OrderItemRequest("prod-001", "sku-001", 2)),
            "addr-001"
        );
        
        var result = orderClient.createOrder(request);
        
        assertThat(result.success()).isTrue();
        assertThat(result.data().orderId()).isNotBlank();
        assertThat(result.data().status()).isEqualTo("PENDING");
        assertThat(result.data().totalAmount()).isPositive();
    }
    
    @Test
    void shouldHandleOrderNotFound() {
        var result = orderClient.getOrder("nonexistent-id");
        
        assertThat(result.success()).isFalse();
        assertThat(result.code()).isEqualTo("SC_ORDER_NOT_FOUND");
    }
}
```

#### 3.3.3 Stub Runner 工作原理

```
@AutoConfigureStubRunner 做的事情：
  1. 从 classpath 或 Maven 仓库查找 order-service-*-stubs.jar
  2. 解压其中的 WireMock stub 定义
  3. 在指定端口（8080）启动嵌入式 WireMock 服务器
  4. @HttpExchange 客户端指向 stub 服务器
  5. 测试运行 → 请求匹配 stub → 返回预定义响应
```

---

### Phase 4: CI 集成

#### 3.4.1 更新 CI 工作流

```yaml
# .github/workflows/ci.yml
jobs:
  contract-tests:
    name: Contract Tests
    runs-on: ubuntu-latest
    if: >
      contains(github.event.pull_request.labels.*.name, 'contract-test') ||
      github.event_name == 'push'
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-java@v4
        with:
          java-version: '25'
          distribution: 'temurin'
          cache: maven
      
      # Producer: 生成 stub
      - name: Generate Order Service Stubs
        run: ./mvnw -pl services/order-service spring-cloud-contract:convert -B
      
      # Consumer: 验证 BFF 能消费 stub
      - name: Buyer-BFF Contract Tests
        run: ./mvnw -pl services/buyer-bff test \
            -Dtest="**/contract/*ContractTest" -B
      
      # 发布 stub artifact
      - name: Upload Stub Artifacts
        if: github.event_name == 'push'
        run: ./mvnw -pl services/order-service deploy -DskipTests -B
```

#### 3.4.2 契约变更流程

```
当 Domain Service 需要修改 API 时：

1. 修改 Groovy 契约文件
2. 运行 ./mvnw spring-cloud-contract:generateTests
   → 如果旧契约被破坏，SCC 会报告兼容性错误
3. 如果需要破坏性变更：
   a) 创建新版本的契约（listOrdersV2.groovy）
   b) 同时保留旧契约（向后兼容过渡期）
   c) 通知 BFF 团队迁移
   d. 过渡期结束后删除旧契约
4. 正常发版流程
```

---

## 四、首期范围建议

### 4.1 推荐优先覆盖的路径

| Producer | 契约文件 | Consumer | 理由 |
|----------|---------|----------|------|
| order-service | `listOrders.groovy` | buyer-bff | 购物核心链路 |
| order-service | `createOrder.groovy` | buyer-bff | 下单流程 |
| marketplace-service | `getProduct.groovy` | buyer-bff | 商品展示 |
| marketplace-service | `checkInventory.groovy` | buyer-bff | 库存校验 |
| profile-service | `getBuyerProfile.groovy` | buyer-bff | 用户信息 |

### 4.2 暂不覆盖的路径

| 路径 | 理由 |
|------|------|
| 内部管理端点（`/internal/**`） | 不暴露给 BFF |
| Kafka 事件消费 | 通过 Outbox 模式验证，不用 SCC |
| 异常路径（超时、降级） | 用 WireMock 手写更灵活 |

---

## 五、最佳实践

### 5.1 契约文件组织

```
contracts/
├── buyer-bff/           # 按 Consumer 组织
│   ├── listOrders.groovy
│   ├── createOrder.groovy
│   └── orderNotFound.groovy
├── seller-bff/          # 另一个 Consumer
│   └── listOrdersForSeller.groovy
└── _common/             # 共享片段
    └── errorResponses.groovy
```

### 5.2 使用 `$(consumer(...), producer(...))`

```groovy
// 推荐：明确区分 consumer 期望和 producer 实现
parameter('page', $(consumer(0), producer(regex('[0-9]+'))))

// 不推荐：只有一个值，无法区分方向
parameter('page', 0)
```

### 5.3 使用 Regex 验证格式，而非硬编码值

```groovy
// 推荐：验证格式，允许任何合法值
orderId: $(regex(nonEmpty()))
status: $(regex('PENDING|PAID|SHIPPED|DELIVERED|CANCELLED'))
createdAt: $(regex(dateTime()))

// 不推荐：硬编码值限制了测试的泛化
orderId: "ord-123"
status: "PENDING"
createdAt: "2026-04-12T10:00:00Z"
```

### 5.4 定期重新生成 Stub

```bash
# 每周或发版前执行
./mvnw -pl services/order-service spring-cloud-contract:convert
./mvnw -pl services/buyer-bff test -Dtest=*ContractTest
```

### 5.5 与 ArchUnit 规则配合

```java
// tooling/architecture-tests/src/test/java/.../ContractRulesTest.java
@ArchTest
static final ArchRule ARCH_08_BFF_MUST_HAVE_CONTRACT_TESTS = 
    classes().that().haveSimpleNameEndingWith("BffConfig")
        .should().beTestMethodAnnotatedWith(AutoConfigureStubRunner.class)
        .as("BFF 下游配置必须有对应的契约测试");
```

---

## 六、故障排查

### 6.1 Stub 找不到

```
错误：StubRunnerException: No stubs found for order-service
```

**排查**：
```bash
# 1. 确认 stub jar 已生成
ls ~/.m2/repository/dev/meirong/shop/order-service/

# 2. 确认 classifier 是 stubs
# 应该看到：order-service-1.0.0-stubs.jar

# 3. 确认 consumer pom.xml 中依赖正确
cat services/buyer-bff/pom.xml | grep -A3 order-service
```

### 6.2 Stub 服务器端口冲突

```
错误：Port 8080 is already in use
```

**排查**：
```bash
# 修改 @AutoConfigureStubRunner 的端口
@AutoConfigureStubRunner(
    ids = "dev.meirong.shop:order-service:+:stubs:8081",  // 换端口
    stubsMode = StubRunnerProperties.StubsMode.LOCAL
)

# 同步更新测试属性
@SpringBootTest(properties = {
    "shop.client.order.base-url=http://localhost:8081"
})
```

### 6.3 契约不兼容

```
错误：WireMock 无法匹配请求 → 404
```

**排查**：
```bash
# 开启 stub runner 日志
logging.level.org.springframework.cloud.contract.stubrunner=DEBUG

# 检查请求路径/方法/参数是否匹配 Groovy 定义
# 常见原因：
# - URL 路径不匹配（检查 /v1 vs /v2）
# - HTTP 方法不匹配（GET vs POST）
# - Query 参数名不匹配
```

---

## 七、后续演进

### 7.1 Pact Broker 集成（可选）

如果未来需要跨团队协作或可视化兼容性矩阵：

```
Order Service ──publishes──→ Pact Broker ←──consumes── Buyer-BFF
                                                    ↑
                                            Seller-BFF (future)
```

### 7.2 自动生成契约（远期）

```
springdoc OpenAPI spec → 自动转换为 SCC Groovy DSL
```

减少手写契约的工作量，但会增加一层抽象。先验证手写契约的 ROI 再考虑。

---

## 八、参考依据

- Spring Cloud Contract Docs: https://docs.spring.io/spring-cloud-contract/reference/
- Stub Runner: https://docs.spring.io/spring-cloud-contract/reference/stub-runner.html
- Contract DSL: https://docs.spring.io/spring-cloud-contract/reference/contract.html
- 本项目契约测试现状：13 个 WireMock 测试覆盖 buyer-bff/seller-bff → Domain Service
- 本项目 ArchUnit 规则：`docs/ARCHUNIT-RULES.md`
