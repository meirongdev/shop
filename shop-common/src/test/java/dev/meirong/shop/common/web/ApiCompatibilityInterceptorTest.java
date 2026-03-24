package dev.meirong.shop.common.web;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

class ApiCompatibilityInterceptorTest {

    private final MockMvc mockMvc = MockMvcBuilders.standaloneSetup(new CompatibilityTestController())
            .addInterceptors(new ApiCompatibilityInterceptor())
            .build();

    @Test
    void versionedEndpoint_addsApiVersionHeader() throws Exception {
        mockMvc.perform(get("/buyer/v1/health"))
                .andExpect(status().isOk())
                .andExpect(header().string(CompatibilityHeaderNames.API_VERSION, "1"))
                .andExpect(header().doesNotExist(CompatibilityHeaderNames.DEPRECATION));
    }

    @Test
    void deprecatedEndpoint_addsCompatibilityLifecycleHeaders() throws Exception {
        mockMvc.perform(get("/buyer/v1/legacy"))
                .andExpect(status().isOk())
                .andExpect(header().string(CompatibilityHeaderNames.API_VERSION, "1"))
                .andExpect(header().string(CompatibilityHeaderNames.DEPRECATION, "true"))
                .andExpect(header().string(CompatibilityHeaderNames.DEPRECATED_SINCE, "2026.03"))
                .andExpect(header().string(CompatibilityHeaderNames.SUNSET, "Fri, 31 Jul 2026 00:00:00 GMT"))
                .andExpect(header().string(CompatibilityHeaderNames.REPLACEMENT, "/buyer/v2/legacy"));
    }

    @RestController
    static class CompatibilityTestController {

        @GetMapping("/buyer/v1/health")
        ResponseEntity<String> health() {
            return ResponseEntity.ok("ok");
        }

        @ApiDeprecation(since = "2026.03", sunsetAt = "2026-07-31", replacement = "/buyer/v2/legacy")
        @GetMapping("/buyer/v1/legacy")
        ResponseEntity<String> legacy() {
            return ResponseEntity.ok("legacy");
        }
    }
}
