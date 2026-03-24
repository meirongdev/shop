package ${package}.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class GatewayTemplateServiceTest {

    private final GatewayTemplateService service = new GatewayTemplateService();

    @Test
    void buildResponseIncludesRoutePing() {
        var response = service.buildResponse();

        assertThat(response.status()).isEqualTo("ready");
        assertThat(response.sampleRoutes()).contains("/gateway/v1/route-ping");
    }
}
