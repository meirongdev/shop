package ${package}.controller;

import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ${package}.service.TemplateWorkerService;
import ${package}.service.TemplateWorkerService.WorkerStatusResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(WorkerPingController.class)
class WorkerPingControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private TemplateWorkerService service;

    @Test
    void pingReturnsWorkerPayload() throws Exception {
        given(service.buildResponse()).willReturn(new WorkerStatusResponse(
                "worker-template", "worker.events.v1", "record checkpoints"));

        mockMvc.perform(get("/worker/v1/ping"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SC_OK"))
                .andExpect(jsonPath("$.data.topic").value("worker.events.v1"));
    }
}
