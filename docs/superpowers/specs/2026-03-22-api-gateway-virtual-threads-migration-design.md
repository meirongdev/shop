# API Gateway — WebFlux → Virtual Threads 迁移设计

> 状态：已实现（代码与测试已验证）
> 日期：2026-03-22
> 作者：设计评审通过

> 实施说明：
> - 最终实现采用 `spring-cloud-starter-gateway-server-webmvc` + `spring.threads.virtual.enabled=true`
> - 路由配置位于 `spring.cloud.gateway.server.webmvc.routes`
> - SCG MVC 的自定义 YAML 谓词最终通过 `PredicateSupplier` + 静态 `RequestPredicate` 方法落地，而不是 `AbstractRoutePredicateFactory`
> - 内置保留 Host 过滤器名称实际为 `PreserveHost`
> - 集成测试使用 Testcontainers Redis + JDK `HttpServer` 模拟上游，而不是 WireMock

---

## 1. 背景与目标

当前 `api-gateway` 使用 `spring-cloud-starter-gateway-server-webflux`，基于 Netty + Reactor 响应式栈运行。维护成本偏高：过滤器须用 `Mono`/`Flux` 编写，安全层使用 `@EnableWebFluxSecurity` / `ServerHttpSecurity`，与项目其他服务（Spring MVC）的编程模型不一致。

**目标：**

1. 用 `spring-cloud-starter-gateway-server-webmvc` + JDK 25 Virtual Threads 替换 WebFlux，降低响应式心智负担。
2. 路由配置迁移至 YAML，新增路由无需改动 Java 代码。
3. 新增基于 Redis 的 Per-Player-Id 限流（Rate Limiting）。
4. 新增通用 Canary 请求谓词，支持接口灰度迁移（按 Player-Id 热更新白名单）。
5. 补充完整单元/集成测试覆盖。

---

## 2. 技术选型依据

### 2.1 Spring Cloud Gateway MVC vs WebFlux

| 维度 | WebFlux（现状） | MVC + Virtual Threads（目标） |
|------|----------------|-------------------------------|
| 编程模型 | 响应式（`Mono`/`Flux`） | 命令式（普通方法调用） |
| 线程模型 | Netty event loop | Tomcat + JDK 25 VirtualThread |
| 过滤器 API | `GlobalFilter` + `GatewayFilterChain` | `OncePerRequestFilter` |
| 安全 API | `ServerHttpSecurity` | `HttpSecurity` |
| 与其他服务一致性 | ❌ 不一致 | ✅ 一致 |
| 吞吐量（高并发 I/O 密集） | ✅ 极佳 | ✅ 接近（VT park 代替阻塞） |
| 维护成本 | 高 | 低 |

Spring Boot 3.2+ / Spring Cloud 2023.x 正式支持 Gateway MVC 分支（`spring-cloud-starter-gateway-server-webmvc`），为 2026 年推荐方案。

### 2.2 Virtual Threads 原理

JDK 21（LTS）Project Loom 引入 Virtual Threads，本项目使用 JDK 25。Tomcat 将每个请求分配到一个 Virtual Thread，I/O 等待时由 JVM 自动 park（不占用 OS 线程），唤醒后继续执行。效果与 Netty event loop 非阻塞接近，但代码为普通命令式风格。

开启方式：`spring.threads.virtual.enabled=true`（Spring Boot 3.2+）。

Spring Security `SecurityContextHolder` 与 Micrometer `ObservationRegistry` 均已兼容 Virtual Threads（Spring 6.x+），无 ThreadLocal 钉扎（pinning）风险。

> **注意：** `spring-boot-starter-oauth2-resource-server` 同时包含 servlet 与 reactive JWT decoder，Spring Boot 会根据运行时栈自动选择 servlet 版本（`NimbusJwtDecoder`），无需额外配置。

---

## 3. 架构概览

```
Client
  │
  ▼
┌─────────────────────────────────────────┐
│           api-gateway (port 8080)        │
│   Tomcat + JDK 25 Virtual Threads        │
│                                          │
│  ┌─────────────────────────────────────┐ │
│  │   SecurityFilterChain  @Order(-100) │ │  ← Spring Security DEFAULT_FILTER_ORDER
│  │   (JWT Bearer, HttpSecurity)        │ │
│  └─────────────────────────────────────┘ │
│  ┌─────────────────────────────────────┐ │
│  │   TrustedHeadersFilter  @Order(-90) │ │  ← 在 Security 之后，可读取已解析的 JWT
│  │   注入 X-Player-Id / X-Roles 等     │ │
│  └─────────────────────────────────────┘ │
│  ┌─────────────────────────────────────┐ │
│  │   RateLimitingFilter    @Order(-80) │ │  ← 在 TrustedHeaders 之后，读取 X-Player-Id
│  │   (Redis Lua, 100 req/min/key)      │ │
│  └─────────────────────────────────────┘ │
│  ┌─────────────────────────────────────┐ │
│  │   SCG MVC Route Matching            │ │
│  │   YAML routes (含 Canary 路由)      │ │
│  └─────────────────────────────────────┘ │
└─────────────────────────────────────────┘
  │                    │
  ▼ 普通路由            ▼ Canary 路由（Redis 白名单命中）
upstream-v1          upstream-v2 (canary endpoint)
```

**Filter 执行顺序原则：**
1. `SecurityFilterChain`（-100）：解析 JWT，填充 `SecurityContext`
2. `TrustedHeadersFilter`（-90）：从 `SecurityContext` 读取 JWT，注入可信 header
3. `RateLimitingFilter`（-80）：读取 `X-Player-Id`（已由上一步注入）限流

---

## 4. 依赖变更

### api-gateway/pom.xml

**移除：**
```xml
<dependency>
    <groupId>org.springframework.cloud</groupId>
    <artifactId>spring-cloud-starter-gateway-server-webflux</artifactId>
</dependency>
```

**新增：**
```xml
<!-- SCG MVC：正确 artifact ID 为 gateway-server-webmvc（非 gateway-server-mvc） -->
<dependency>
    <groupId>org.springframework.cloud</groupId>
    <artifactId>spring-cloud-starter-gateway-server-webmvc</artifactId>
</dependency>
<!-- spring-boot-starter-web 已由上面的 starter 传递引入，无需显式声明 -->
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-data-redis</artifactId>
</dependency>

<!-- 测试依赖 -->
<dependency>
    <groupId>org.springframework.security</groupId>
    <artifactId>spring-security-test</artifactId>
    <scope>test</scope>
</dependency>
<dependency>
    <groupId>org.testcontainers</groupId>
    <artifactId>testcontainers</artifactId>
    <scope>test</scope>
</dependency>
<dependency>
    <groupId>org.testcontainers</groupId>
    <artifactId>junit-jupiter</artifactId>
    <scope>test</scope>
</dependency>
```

---

## 5. 配置变更（application.yml）

> 以下为**新增/变更项**，在现有 `application.yml`（含 `server.port`、`management.*`、`logging.structured.*`、`management.otlp.*`）基础上追加，不替换已有配置。

```yaml
spring:
  threads:
    virtual:
      enabled: true          # 开启 Virtual Threads（Tomcat 线程池全部改为 VirtualThreadExecutor）

  data:
    redis:
      host: ${GATEWAY_REDIS_HOST:redis}
      port: ${GATEWAY_REDIS_PORT:6379}

  cloud:
    gateway:
      server:
        webmvc:
          routes:
            # ── Canary 路由（须在正式路由前定义，首个命中即路由）──
            # 新增灰度验证路由时，统一在此区块追加
            - id: seller-api-canary
              uri: ${SELLER_BFF_V2_URI:http://seller-bff-v2:8080}
              predicates:
                - Path=/api/seller/**
                - name: Canary
                  args:
                    routeId: seller-api   # Redis key: gateway:canary:seller-api
              filters:
                - StripPrefix=1

            - id: buyer-api-canary
              uri: ${BUYER_BFF_V2_URI:http://buyer-bff-v2:8080}
              predicates:
                - Path=/api/buyer/**
                - name: Canary
                  args:
                    routeId: buyer-api
              filters:
                - StripPrefix=1

            # ── 正式路由 ──
            - id: auth-server
              uri: ${AUTH_SERVER_URI:http://auth-server:8080}
              predicates:
                - Path=/auth/**

            - id: buyer-portal
              uri: ${BUYER_PORTAL_URI:http://buyer-portal:8080}
              predicates:
                - Path=/buyer/**
              filters:
                - PreserveHost

            - id: seller-portal
              uri: ${SELLER_PORTAL_URI:http://seller-portal:8080}
              predicates:
                - Path=/seller/**
              filters:
                - PreserveHost

            - id: buyer-api
              uri: ${BUYER_BFF_URI:http://buyer-bff:8080}
              predicates:
                - Path=/api/buyer/**
              filters:
                - StripPrefix=1

            - id: seller-api
              uri: ${SELLER_BFF_URI:http://seller-bff:8080}
              predicates:
                - Path=/api/seller/**
              filters:
                - StripPrefix=1

            - id: loyalty-api
              uri: ${LOYALTY_SERVICE_URI:http://loyalty-service:8080}
              predicates:
                - Path=/api/loyalty/**
              filters:
                - StripPrefix=1

            - id: activity-api
              uri: ${ACTIVITY_SERVICE_URI:http://activity-service:8080}
              predicates:
                - Path=/api/activity/**
              filters:
                - StripPrefix=1

            - id: webhook-api
              uri: ${WEBHOOK_SERVICE_URI:http://webhook-service:8080}
              predicates:
                - Path=/api/webhook/**
              filters:
                - StripPrefix=1

            - id: subscription-api
              uri: ${SUBSCRIPTION_SERVICE_URI:http://subscription-service:8080}
              predicates:
                - Path=/api/subscription/**
              filters:
                - StripPrefix=1

            - id: public-buyer-api
              uri: ${BUYER_BFF_URI:http://buyer-bff:8080}
              predicates:
                - Path=/public/buyer/**
              filters:
                - StripPrefix=1

shop:
  gateway:
    jwt-secret: ${SHOP_AUTH_JWT_SECRET:change-this-to-a-32-byte-demo-secret}
    internal-token: ${SHOP_INTERNAL_TOKEN:local-dev-internal-token-change-me}
    rate-limit:
      requests-per-minute: ${GATEWAY_RATE_LIMIT_RPM:100}
      # burst 预留，Token Bucket 扩展时使用
      burst: ${GATEWAY_RATE_LIMIT_BURST:20}
```

---

## 6. 组件设计

### 6.1 GatewayProperties（更新）

最终实现只保留网关自身需要的强类型配置：`jwtSecret`、`internalToken` 和嵌套 `RateLimit`。

> Kubernetes 运行说明：网关 Redis 环境变量使用 `GATEWAY_REDIS_HOST` / `GATEWAY_REDIS_PORT`，并在 Deployment 上设置 `enableServiceLinks: false`，避免被 K8s 自动注入的 `REDIS_PORT=tcp://...` 服务链接变量污染。

```java
@ConfigurationProperties(prefix = "shop.gateway")
public record GatewayProperties(
        String jwtSecret,
        String internalToken,
        RateLimit rateLimit) {

    public GatewayProperties {
        rateLimit = rateLimit == null ? new RateLimit(100, 20) : rateLimit;
    }

    public record RateLimit(long requestsPerMinute, long burst) {
        public RateLimit {
            requestsPerMinute = requestsPerMinute <= 0 ? 100 : requestsPerMinute;
            burst = burst <= 0 ? 20 : burst;
        }
    }
}
```

---

### 6.2 GatewaySecurityConfig

WebFlux 响应式 API → 标准 Servlet API。

```java
@Configuration(proxyBeanMethods = false)
@EnableWebSecurity
@EnableConfigurationProperties(GatewayProperties.class)
public class GatewaySecurityConfig {

    @Bean
    JwtDecoder jwtDecoder(GatewayProperties properties) {
        SecretKey key = new SecretKeySpec(
            properties.jwtSecret().getBytes(StandardCharsets.UTF_8), "HmacSHA256");
        return NimbusJwtDecoder.withSecretKey(key)
            .macAlgorithm(MacAlgorithm.HS256).build();
    }

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http, JwtDecoder jwtDecoder)
            throws Exception {
        return http
            .csrf(AbstractHttpConfigurer::disable)
            // CORS 策略将在 Phase 3 游客购物车任务中集中配置
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/actuator/**", "/auth/**",
                                 "/buyer/**", "/seller/**", "/public/**").permitAll()
                .requestMatchers("/api/**").authenticated()
                .anyRequest().permitAll())
            .oauth2ResourceServer(o -> o.jwt(j -> j.decoder(jwtDecoder)))
            .build();
    }
}
```

变更点：`NimbusReactiveJwtDecoder` → `NimbusJwtDecoder`，`ServerHttpSecurity` → `HttpSecurity`，`SecurityWebFilterChain` → `SecurityFilterChain`。

---

### 6.3 TrustedHeadersFilter

`GlobalFilter` + `ServerWebExchange` → `OncePerRequestFilter` + `TrustedHeadersRequestWrapper`。

**顺序：`@Order(-90)`**（在 `SecurityFilterChain` `-100` 之后执行，`SecurityContext` 已填充）。

```java
@Component
@Order(-90)
public class TrustedHeadersFilter extends OncePerRequestFilter {

    private final GatewayProperties properties;

    public TrustedHeadersFilter(GatewayProperties properties) {
        this.properties = properties;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        if (!request.getRequestURI().startsWith("/api/")) {
            chain.doFilter(request, response);
            return;
        }
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof Jwt jwt)) {
            chain.doFilter(request, response);
            return;
        }
        String incoming = request.getHeader("X-Request-Id");
        String requestId = (incoming == null || incoming.isBlank())
            ? UUID.randomUUID().toString() : incoming;
        List<String> roles = Optional.ofNullable(jwt.getClaimAsStringList("roles"))
            .orElse(List.of());

        chain.doFilter(
            new TrustedHeadersRequestWrapper(request, jwt, requestId, roles, properties.internalToken()),
            response);
    }
}
```

**`TrustedHeadersRequestWrapper` 实现要点：**

```java
public class TrustedHeadersRequestWrapper extends HttpServletRequestWrapper {

    // 不可信 header（客户端传入的需剥离）
    private static final Set<String> STRIPPED = Set.of(
        "x-player-id", "x-username", "x-roles", "x-portal", "x-internal-token");

    private final Map<String, String> injected; // 注入的可信 header

    public TrustedHeadersRequestWrapper(HttpServletRequest request, Jwt jwt,
                                        String requestId, List<String> roles,
                                        String internalToken) {
        super(request);
        this.injected = Map.of(
            "X-Request-Id",     requestId,
            "X-Player-Id",      nullToEmpty(jwt.getClaimAsString("principalId")),
            "X-Username",       nullToEmpty(jwt.getClaimAsString("username")),
            "X-Roles",          String.join(",", roles),
            "X-Portal",         nullToEmpty(jwt.getClaimAsString("portal")),
            "X-Internal-Token", internalToken
        );
    }

    @Override
    public String getHeader(String name) {
        if (STRIPPED.contains(name.toLowerCase())) return injected.get(canonicalize(name));
        return injected.getOrDefault(canonicalize(name), super.getHeader(name));
    }

    @Override
    public Enumeration<String> getHeaders(String name) {
        String val = getHeader(name);
        return val != null ? Collections.enumeration(List.of(val)) : super.getHeaders(name);
    }
    // ... getHeaderNames() 同样需要覆写以合并原始 + 注入的 header 名称
}
```

---

### 6.4 RateLimitingFilter

**顺序：`@Order(-80)`**（在 `TrustedHeadersFilter` 之后，`X-Player-Id` 已注入）。

使用 **原子 Lua 脚本**解决 INCR + EXPIRE 的竞态问题：

```java
@Component
@Order(-80)
public class RateLimitingFilter extends OncePerRequestFilter {

    private static final String KEY_PREFIX = "rl:";
    // 原子脚本：INCR + 仅首次设置 TTL，避免两条命令之间的竞态
    private static final RedisScript<Long> RATE_LIMIT_SCRIPT = RedisScript.of("""
        local count = redis.call('INCR', KEYS[1])
        if count == 1 then redis.call('EXPIRE', KEYS[1], tonumber(ARGV[1])) end
        return count
        """, Long.class);

    private final StringRedisTemplate redis;
    private final GatewayProperties properties;

    public RateLimitingFilter(StringRedisTemplate redis, GatewayProperties properties) {
        this.redis = redis;
        this.properties = properties;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        if (!request.getRequestURI().startsWith("/api/")) {
            chain.doFilter(request, response);
            return;
        }
        try {
            String playerId = request.getHeader("X-Player-Id");
            String key = KEY_PREFIX
                + (playerId != null && !playerId.isBlank() ? playerId : request.getRemoteAddr())
                + ":" + currentMinuteBucket();
            Long count = redis.execute(RATE_LIMIT_SCRIPT, List.of(key), "120");
            if (count != null && count > properties.rateLimit().requestsPerMinute()) {
                response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
                response.setHeader("Retry-After", "60");
                return;
            }
        } catch (Exception e) {
            // fail-open：Redis 不可用时放行，避免限流逻辑影响核心链路
            log.warn("Rate limit check failed, failing open: {}", e.getMessage());
        }
        chain.doFilter(request, response);
    }

    private static String currentMinuteBucket() {
        return String.valueOf(System.currentTimeMillis() / 60_000);
    }
}
```

限流策略：按 `X-Player-Id`（已登录）或 IP（匿名）区分，默认 100 req/min，通过 `GATEWAY_RATE_LIMIT_RPM` 调整。Redis 不可用时 fail-open 放行。

---

### 6.5 CanaryRequestPredicates（灰度迁移谓词）

最终实现说明：

- SCG MVC 的 property route 自定义谓词是通过 `PredicateSupplier` 暴露 `RequestPredicate` 操作方法发现的
- YAML 中的 `name: Canary` 会绑定到 `CanaryRequestPredicates.canary(String routeId)`
- 因为 SCG MVC 对 property route 操作匹配要求“YAML 参数个数 == 方法参数个数”，实现中不能把 `StringRedisTemplate` 作为方法参数注入
- 因此 Redis 依赖由单例组件构造器注入后保存为静态引用，静态 `canary(...)` 方法只接收 `routeId`

```java
@Component
public class CanaryRequestPredicates implements PredicateSupplier {

    private static final Logger log = LoggerFactory.getLogger(CanaryRequestPredicates.class);
    private static volatile StringRedisTemplate redisTemplate;

    public CanaryRequestPredicates(StringRedisTemplate redisTemplate) {
        CanaryRequestPredicates.redisTemplate = redisTemplate;
    }

    public static RequestPredicate canary(String routeId) {
        return request -> {
            String playerId = request.servletRequest().getHeader("X-Player-Id");
            if (playerId == null || playerId.isBlank() || redisTemplate == null) {
                return false;
            }
            try {
                return Boolean.TRUE.equals(
                    redisTemplate.opsForSet().isMember("gateway:canary:" + routeId, playerId));
            } catch (Exception ex) {
                log.warn("Canary lookup failed for route {} and player {}, routing to stable: {}",
                    routeId, playerId, ex.getMessage());
                return false;
            }
        };
    }
}
```

**运维操作（热更新，无需重启）：**

```bash
# 加入灰度白名单
SADD gateway:canary:seller-api "seller-001" "seller-002"
SADD gateway:canary:buyer-api  "buyer-test-001"

# 移除单个账号
SREM gateway:canary:seller-api "seller-001"

# 查看当前白名单
SMEMBERS gateway:canary:seller-api

# 全量迁移完成后，清空白名单（所有流量回归正式路由）
DEL gateway:canary:seller-api
```

---

## 7. 删除的文件

| 文件 | 原因 |
|------|------|
| `RouteConfig.java` | 路由迁移至 YAML，不再需要 |

---

## 8. 测试策略

测试依赖：`MockMvc`（路由/过滤器测试）、**Testcontainers `redis:7`**（限流 & Canary 测试，与项目其他服务保持一致）、JDK `HttpServer`（上游模拟）。

| 测试类 | 覆盖内容 |
|--------|----------|
| `GatewayContextTest` | Spring 上下文冒烟测试，验证所有 Bean 正常加载 |
| `TrustedHeadersFilterTest` | JWT 存在时注入正确 header；`/auth/**` 路径跳过注入；缺少 JWT 时 chain 直通；`TrustedHeadersRequestWrapper` 剥离不可信 header |
| `RateLimitingFilterTest` | 正常请求通过；超过阈值返回 429 + `Retry-After`；非 `/api/` 路径跳过；Redis 不可用时 fail-open |
| `CanaryRequestPredicatesTest` | Redis 白名单命中返回 `true`；未命中返回 `false`；`X-Player-Id` 缺失返回 `false`；Redis 异常时降级到稳定路由 |
| `GatewayRoutingIntegrationTest` | JDK `HttpServer` 模拟上游，验证路由转发、`StripPrefix`、`PreserveHost`、Canary 路由优先级 |

---

## 9. 外部实践参考

### 同类开源实现

| 项目 | 实现方式 | 参考链接 |
|------|----------|---------|
| **Nginx Ingress Controller** | `canary-by-header` 注解，按 Header 值灰度 | https://kubernetes.github.io/ingress-nginx/user-guide/nginx-configuration/annotations/ |
| **Apache APISIX** | `traffic-split` 插件，按 consumer ID 路由，规则热更新存 etcd | https://apisix.apache.org/docs/apisix/plugins/traffic-split/ |
| **Kong** | `canary` 插件，按 Header/consumer 分流 | https://docs.konghq.com/hub/kong-inc/canary/ |
| **Istio VirtualService** | `match.headers` + `weight` 流量治理 | https://istio.io/latest/docs/reference/config/networking/virtual-service/ |
| **Netflix Zuul 2** | `canary` Filter，按 userId 查内部配置决策路由（本方案原型） | https://github.com/Netflix/zuul |
| **Spring Cloud Alibaba 灰度** | SCG + Nacos metadata 灰度，结构与本方案相同，白名单存 Nacos | https://github.com/alibaba/spring-cloud-alibaba |

### Spring Cloud Gateway MVC 官方文档

- Spring Cloud Gateway Server MVC 参考：https://docs.spring.io/spring-cloud-gateway/reference/spring-cloud-gateway-server-mvc.html
- Spring Boot Virtual Threads 配置：https://docs.spring.io/spring-boot/reference/features/spring-application.html#features.spring-application.virtual-threads

### 延伸阅读

- Netflix Tech Blog — *Zuul 2: The Netflix Journey to Asynchronous, Non-Blocking Systems*（说明 Zuul 从阻塞迁移到异步，反向印证 VT 方向的合理性）
- InfoQ — *阿里巴巴 Spring Cloud Gateway 灰度路由实践*（Redis Set 白名单模式的中文案例）

---

## 10. Roadmap 影响

本迁移纳入 **Phase 3 平台基础设施**，与以下已有任务关联：

- 游客购物车（Redis）——复用本次新增的 Redis 连接
- CORS 策略——**延后至 Phase 3 游客购物车任务**，届时在 `SecurityFilterChain` 中集中配置
- 支付扩展（Phase 4）——新 `/api/payment/**` 路由只需追加 YAML 条目

---

## 11. 风险与缓解

| 风险 | 缓解措施 |
|------|----------|
| Canary 路由顺序配置错误导致流量误判 | YAML 注释标注"Canary 路由必须在正式路由前"；`GatewayRoutingIntegrationTest` 验证路由优先级 |
| Redis 不可用导致限流/Canary 逻辑抛异常 | `RateLimitingFilter` fail-open；`CanaryRequestPredicates.canary()` 捕获异常并返回 `false`（降级走正式路由） |
| Virtual Threads 与 ThreadLocal 依赖冲突 | Spring Security `SecurityContextHolder` 与 Micrometer `ObservationRegistry` 均已兼容 VT（Spring 6.x+，JDK 25 Loom 稳定） |
| SCG MVC 与 WebFlux 过渡期兼容 | 两者 artifact ID 不同（`webmvc` vs `webflux`），不会同时存在；迁移在独立 PR 一次完成后切换 |
| Filter 顺序错误导致 TrustedHeaders 拿不到 JWT | `@Order` 值严格按 Security(-100) → TrustedHeaders(-90) → RateLimiting(-80) 排列；`TrustedHeadersFilterTest` 验证 SecurityContext 已填充时的注入行为 |
