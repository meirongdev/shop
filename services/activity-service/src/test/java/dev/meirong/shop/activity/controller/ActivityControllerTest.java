package dev.meirong.shop.activity.controller;

import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.meirong.shop.activity.domain.ActivityParticipation;
import dev.meirong.shop.activity.domain.GameType;
import dev.meirong.shop.activity.engine.GameEngine;
import dev.meirong.shop.activity.engine.ParticipateResult;
import dev.meirong.shop.activity.domain.PrizeType;
import dev.meirong.shop.activity.service.ActivityQueryService;
import dev.meirong.shop.common.http.TrustedHeaderNames;
import dev.meirong.shop.common.web.GlobalExceptionHandler;
import dev.meirong.shop.contracts.activity.ActivityApi;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(ActivityController.class)
@Import(GlobalExceptionHandler.class)
class ActivityControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private GameEngine gameEngine;

    @MockBean
    private ActivityQueryService queryService;

    @Test
    void participate_withSellerRole_returnsForbidden() throws Exception {
        mockMvc.perform(post(ActivityApi.PARTICIPATE.replace("{gameId}", "game-1"))
                        .header(TrustedHeaderNames.BUYER_ID, "seller-2001")
                        .header(TrustedHeaderNames.ROLES, "ROLE_SELLER")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new ActivityApi.ParticipateRequest(null))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("SC_FORBIDDEN"))
                .andExpect(jsonPath("$.message").value("Activity participation requires a signed-in buyer account"));

        verifyNoInteractions(gameEngine);
    }

    @Test
    void participate_withBuyerRole_returnsWin() throws Exception {
        when(gameEngine.participate("game-1", "player-1001", "{\"tap\":true}", null, null))
                .thenReturn(new ParticipateResult(true, null, "Red Envelope", PrizeType.POINTS,
                        BigDecimal.valueOf(3.25), "{\"amount\":\"3.25\"}", "You claimed 3.25 points"));

        mockMvc.perform(post(ActivityApi.PARTICIPATE.replace("{gameId}", "game-1"))
                        .header(TrustedHeaderNames.BUYER_ID, "player-1001")
                        .header(TrustedHeaderNames.ROLES, "ROLE_BUYER")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new ActivityApi.ParticipateRequest("{\"tap\":true}"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SC_OK"))
                .andExpect(jsonPath("$.data.win").value(true))
                .andExpect(jsonPath("$.data.prizeType").value("POINTS"))
                .andExpect(jsonPath("$.data.prizeValue").value(3.25))
                .andExpect(jsonPath("$.data.message").value("You claimed 3.25 points"));
    }

    @Test
    void myHistory_withGuestRole_returnsForbidden() throws Exception {
        mockMvc.perform(get(ActivityApi.MY_HISTORY.replace("{gameId}", "game-1"))
                        .header(TrustedHeaderNames.BUYER_ID, "guest-buyer-123")
                        .header(TrustedHeaderNames.ROLES, "ROLE_BUYER_GUEST"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("SC_FORBIDDEN"))
                .andExpect(jsonPath("$.message").value("Activity history requires a signed-in buyer account"));

        verifyNoInteractions(queryService);
    }

    @Test
    void myHistory_withBuyerRole_returnsRecords() throws Exception {
        ActivityParticipation participation = new ActivityParticipation("p1", "game-1", GameType.RED_ENVELOPE, "player-1001");
        participation.markWin(null, "{\"prizeType\":\"POINTS\",\"prizeValue\":3.25}");
        when(queryService.getParticipationHistory("game-1", "player-1001"))
                .thenReturn(List.of(participation));

        mockMvc.perform(get(ActivityApi.MY_HISTORY.replace("{gameId}", "game-1"))
                        .header(TrustedHeaderNames.BUYER_ID, "player-1001")
                        .header(TrustedHeaderNames.ROLES, "ROLE_BUYER"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SC_OK"))
                .andExpect(jsonPath("$.data[0].result").value("WIN"))
                .andExpect(jsonPath("$.data[0].prizeSnapshot").value("{\"prizeType\":\"POINTS\",\"prizeValue\":3.25}"));
    }
}
