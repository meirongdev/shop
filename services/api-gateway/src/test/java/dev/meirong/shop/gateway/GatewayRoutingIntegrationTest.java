package dev.meirong.shop.gateway;

import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.http.HttpHeaders.ACCESS_CONTROL_ALLOW_CREDENTIALS;
import static org.springframework.http.HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN;
import static org.springframework.http.HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD;
import static org.springframework.http.HttpHeaders.HOST;
import static org.springframework.http.HttpHeaders.ORIGIN;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.MOCK,
        properties = {"GATEWAY_RATE_LIMIT_RPM=1", "GATEWAY_RATE_LIMIT_BURST=1"})
@AutoConfigureMockMvc
@Testcontainers
class GatewayRoutingIntegrationTest {

    @Container
    @ServiceConnection(name = "redis")
    private static final GenericContainer<?> REDIS = new GenericContainer<>(DockerImageName.parse("redis:7.4"))
            .withExposedPorts(6379);
    private static final EchoServer STABLE_UPSTREAM = EchoServer.start("stable");
    private static final EchoServer CANARY_UPSTREAM = EchoServer.start("canary");

    @DynamicPropertySource
    static void gatewayProperties(DynamicPropertyRegistry registry) {
        registry.add("AUTH_SERVER_URI", STABLE_UPSTREAM::baseUrl);
        registry.add("BUYER_PORTAL_URI", STABLE_UPSTREAM::baseUrl);
        registry.add("SELLER_PORTAL_URI", STABLE_UPSTREAM::baseUrl);
        registry.add("BUYER_BFF_URI", STABLE_UPSTREAM::baseUrl);
        registry.add("SELLER_BFF_URI", STABLE_UPSTREAM::baseUrl);
        registry.add("LOYALTY_SERVICE_URI", STABLE_UPSTREAM::baseUrl);
        registry.add("ACTIVITY_SERVICE_URI", STABLE_UPSTREAM::baseUrl);
        registry.add("WEBHOOK_SERVICE_URI", STABLE_UPSTREAM::baseUrl);
        registry.add("SUBSCRIPTION_SERVICE_URI", STABLE_UPSTREAM::baseUrl);
        registry.add("BUYER_BFF_V2_URI", CANARY_UPSTREAM::baseUrl);
        registry.add("SELLER_BFF_V2_URI", CANARY_UPSTREAM::baseUrl);
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private StringRedisTemplate redisTemplate;

    @BeforeEach
    void clearRedis() {
        redisTemplate.execute((RedisCallback<Void>) connection -> {
            connection.serverCommands().flushDb();
            return null;
        });
    }

    @AfterAll
    static void shutdown() {
        STABLE_UPSTREAM.close();
        CANARY_UPSTREAM.close();
    }

    @Test
    void stableBuyerApiRouteStripsApiPrefixAndInjectsTrustedHeaders() throws Exception {
        mockMvc.perform(get("/api/buyer/orders")
                        .with(jwtFor("buyer-100", "alice", "buyer", List.of("ROLE_BUYER"))))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("\"server\":\"stable\"")))
                .andExpect(content().string(containsString("\"path\":\"/buyer/orders\"")))
                .andExpect(content().string(containsString("\"buyerId\":\"buyer-100\"")))
                .andExpect(content().string(containsString("\"requestId\":")))
                .andExpect(header().exists("X-Request-Id"))
                .andExpect(header().exists("X-Trace-Id"));
    }

    @Test
    void canaryRouteWinsBeforeStableRouteWhenPlayerIsWhitelisted() throws Exception {
        redisTemplate.opsForSet().add("gateway:canary:buyer-api", "buyer-canary");

        mockMvc.perform(get("/api/buyer/orders")
                        .with(jwtFor("buyer-canary", "beta-user", "buyer", List.of("ROLE_BUYER"))))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("\"server\":\"canary\"")))
                .andExpect(content().string(containsString("\"path\":\"/buyer/orders\"")));
    }

    @Test
    void buyerPortalRoutePreservesOriginalHostHeader() throws Exception {
        mockMvc.perform(get("/buyer/home")
                        .header(HOST, "shop.example.test"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("\"server\":\"stable\"")))
                .andExpect(content().string(containsString("\"host\":\"shop.example.test\"")));
    }

    @Test
    void apiRoutesRespondToConfiguredCorsPreflightRequests() throws Exception {
        mockMvc.perform(options("/api/buyer/cart/list")
                        .header(ORIGIN, "http://localhost:3000")
                        .header(ACCESS_CONTROL_REQUEST_METHOD, "POST"))
                .andExpect(status().isOk())
                .andExpect(header().string(ACCESS_CONTROL_ALLOW_ORIGIN, "http://localhost:3000"))
                .andExpect(header().string(ACCESS_CONTROL_ALLOW_CREDENTIALS, "true"));
    }

    @Test
    void rateLimitReturns429AfterThresholdIsExceeded() throws Exception {
        mockMvc.perform(get("/api/seller/orders")
                        .with(jwtFor("seller-100", "seller", "seller", List.of("ROLE_SELLER"))))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/seller/orders")
                        .with(jwtFor("seller-100", "seller", "seller", List.of("ROLE_SELLER"))))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().string("Retry-After", "60"));
    }

    private SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor jwtFor(String principalId,
                                                                                 String username,
                                                                                 String portal,
                                                                                 List<String> roles) {
        return jwt().jwt(jwt -> jwt
                .claim("principalId", principalId)
                .claim("username", username)
                .claim("portal", portal)
                .claim("roles", roles));
    }

    private static final class EchoServer {

        private final HttpServer server;
        private final String name;

        private EchoServer(HttpServer server, String name) {
            this.server = server;
            this.name = name;
        }

        private static EchoServer start(String name) {
            try {
                HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
                server.createContext("/", exchange -> {
                    String body = "{" +
                            "\"server\":\"" + name + "\"," +
                            "\"path\":\"" + exchange.getRequestURI().getPath() + "\"," +
                            "\"host\":\"" + value(exchange.getRequestHeaders().getFirst("Host")) + "\"," +
                            "\"buyerId\":\"" + value(exchange.getRequestHeaders().getFirst("X-Buyer-Id")) + "\"," +
                            "\"userId\":\"" + value(exchange.getRequestHeaders().getFirst("X-User-Id")) + "\"," +
                            "\"requestId\":\"" + value(exchange.getRequestHeaders().getFirst("X-Request-Id")) + "\"}";
                    byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
                    exchange.getResponseHeaders().add("Content-Type", "application/json");
                    exchange.sendResponseHeaders(200, bytes.length);
                    try (OutputStream outputStream = exchange.getResponseBody()) {
                        outputStream.write(bytes);
                    }
                });
                server.setExecutor(Executors.newVirtualThreadPerTaskExecutor());
                server.start();
                return new EchoServer(server, name);
            } catch (IOException ex) {
                throw new IllegalStateException("Failed to start upstream test server: " + name, ex);
            }
        }

        private String baseUrl() {
            return "http://127.0.0.1:" + server.getAddress().getPort();
        }

        private void close() {
            server.stop(0);
        }

        private static String value(String input) {
            return input == null ? "" : input.replace("\"", "'");
        }
    }
}
