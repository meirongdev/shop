package ${package}.controller;

import static org.mockito.BDDMockito.given;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ${package}.service.TemplateAggregationService;
import ${package}.service.TemplateAggregationService.DashboardCard;
import ${package}.service.TemplateAggregationService.TemplateBffResponse;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(BffPingController.class)
class BffPingControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private TemplateAggregationService service;

    @Test
    void pingReturnsTemplatePayload() throws Exception {
        given(service.buildResponse()).willReturn(new TemplateBffResponse(
                "bff-template", List.of(new DashboardCard("downstream", "http://localhost:8080"))));

        mockMvc.perform(get("/bff/v1/ping").with(user("tester")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SC_OK"))
                .andExpect(jsonPath("$.data.service").value("bff-template"));
    }
}
