package ${package}.controller;

import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ${package}.service.TemplateDomainService;
import ${package}.service.TemplateDomainService.TemplateDomainResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(DomainPingController.class)
class DomainPingControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private TemplateDomainService service;

    @Test
    void pingReturnsTemplatePayload() throws Exception {
        given(service.buildResponse()).willReturn(new TemplateDomainResponse(
                "domain-template", "sample_aggregate", "ready"));

        mockMvc.perform(get("/domain/v1/ping"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SC_OK"))
                .andExpect(jsonPath("$.data.tableName").value("sample_aggregate"));
    }
}
