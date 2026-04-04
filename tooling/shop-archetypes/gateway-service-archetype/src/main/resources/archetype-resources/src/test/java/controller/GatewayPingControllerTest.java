package ${package}.controller;

import static org.mockito.BDDMockito.given;
import static org.springframework.security.test.web.reactive.server.SecurityMockServerConfigurers.mockUser;

import ${package}.service.GatewayTemplateService;
import ${package}.service.GatewayTemplateService.GatewayStatusResponse;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.WebFluxTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.reactive.server.WebTestClient;

@WebFluxTest(GatewayPingController.class)
class GatewayPingControllerTest {

    @Autowired
    private WebTestClient webTestClient;

    @MockBean
    private GatewayTemplateService service;

    @Test
    void pingReturnsTemplatePayload() {
        given(service.buildResponse()).willReturn(new GatewayStatusResponse(
                "gateway-service", "ready", Instant.parse("2026-01-01T00:00:00Z"), List.of("/gateway/v1/route-ping")));

        webTestClient.mutateWith(mockUser())
                .get()
                .uri("/internal/gateway/ping")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.service").isEqualTo("gateway-service")
                .jsonPath("$.status").isEqualTo("ready");
    }
}
