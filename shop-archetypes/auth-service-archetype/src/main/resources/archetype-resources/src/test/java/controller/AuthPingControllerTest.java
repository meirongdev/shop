package ${package}.controller;

import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ${package}.config.SecurityConfig;
import ${package}.service.TemplateAuthService;
import ${package}.service.TemplateAuthService.TemplateAuthResponse;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(AuthPingController.class)
@Import(SecurityConfig.class)
class AuthPingControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private TemplateAuthService service;

    @Test
    void pingReturnsTemplatePayload() throws Exception {
        given(service.buildResponse()).willReturn(new TemplateAuthResponse(
                "auth-template", "Implement JWT issuing", Instant.parse("2026-01-01T00:00:00Z")));

        mockMvc.perform(get("/auth/v1/ping"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SC_OK"))
                .andExpect(jsonPath("$.data.service").value("auth-template"));
    }
}
