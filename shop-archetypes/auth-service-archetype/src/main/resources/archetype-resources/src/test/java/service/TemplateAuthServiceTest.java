package ${package}.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class TemplateAuthServiceTest {

    private final TemplateAuthService service = new TemplateAuthService();

    @Test
    void buildResponseMentionsJwt() {
        var response = service.buildResponse();

        assertThat(response.service()).isEqualTo("${artifactId}");
        assertThat(response.nextStep()).contains("JWT");
    }
}
