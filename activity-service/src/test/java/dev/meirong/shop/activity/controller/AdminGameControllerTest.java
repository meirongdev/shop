package dev.meirong.shop.activity.controller;

import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.meirong.shop.activity.domain.ActivityGame;
import dev.meirong.shop.activity.domain.GameType;
import dev.meirong.shop.activity.service.ActivityAdminService;
import dev.meirong.shop.activity.service.ActivityQueryService;
import dev.meirong.shop.common.http.TrustedHeaderNames;
import dev.meirong.shop.common.web.GlobalExceptionHandler;
import dev.meirong.shop.contracts.api.ActivityApi;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(AdminGameController.class)
@Import(GlobalExceptionHandler.class)
class AdminGameControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private ActivityAdminService adminService;

    @MockBean
    private ActivityQueryService queryService;

    @Test
    void createGame_withoutAdminRole_returnsForbidden() throws Exception {
        ActivityApi.CreateGameRequest request = new ActivityApi.CreateGameRequest(
                "RED_ENVELOPE", "Flash Red Envelope", "{\"packet_count\":3,\"total_amount\":6.00}", 10, 10);

        mockMvc.perform(post(ActivityApi.ADMIN_CREATE_GAME)
                        .header(TrustedHeaderNames.ROLES, "ROLE_BUYER")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("SC_FORBIDDEN"))
                .andExpect(jsonPath("$.message").value("Activity admin requires ADMIN role"));

        verifyNoInteractions(adminService);
    }

    @Test
    void createGame_withAdminRole_returnsGame() throws Exception {
        ActivityApi.CreateGameRequest request = new ActivityApi.CreateGameRequest(
                "RED_ENVELOPE", "Flash Red Envelope", "{\"packet_count\":3,\"total_amount\":6.00}", 10, 10);
        ActivityGame game = new ActivityGame("game-1", GameType.RED_ENVELOPE, "Flash Red Envelope");
        when(adminService.createGame(eq(GameType.RED_ENVELOPE), eq("Flash Red Envelope"),
                eq("{\"packet_count\":3,\"total_amount\":6.00}"), anyInt(), anyInt(), eq("admin")))
                .thenReturn(game);

        mockMvc.perform(post(ActivityApi.ADMIN_CREATE_GAME)
                        .header(TrustedHeaderNames.ROLES, "ROLE_ADMIN")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SC_OK"))
                .andExpect(jsonPath("$.data.id").value("game-1"))
                .andExpect(jsonPath("$.data.type").value("RED_ENVELOPE"))
                .andExpect(jsonPath("$.data.name").value("Flash Red Envelope"));
    }
}
