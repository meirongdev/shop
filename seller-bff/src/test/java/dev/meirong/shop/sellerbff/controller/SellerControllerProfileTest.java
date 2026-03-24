package dev.meirong.shop.sellerbff.controller;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.meirong.shop.common.web.GlobalExceptionHandler;
import dev.meirong.shop.contracts.api.ProfileApi;
import dev.meirong.shop.contracts.api.SellerApi;
import dev.meirong.shop.sellerbff.service.SellerAggregationService;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(SellerController.class)
@Import(GlobalExceptionHandler.class)
class SellerControllerProfileTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private SellerAggregationService service;

    @Test
    void getProfile_usesResolvedSellerId() throws Exception {
        ProfileApi.ProfileResponse response = new ProfileApi.ProfileResponse(
                "seller-2001",
                "seller.demo",
                "Seller Demo",
                "seller.demo@example.com",
                "SILVER",
                Instant.parse("2026-03-20T00:00:00Z"),
                Instant.parse("2026-03-20T00:00:00Z")
        );
        when(service.getProfile("seller-2001")).thenReturn(response);

        mockMvc.perform(post(SellerApi.PROFILE_GET)
                        .header("X-Player-Id", "seller-2001")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new SellerApi.SellerContextRequest("seller-body"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SC_OK"))
                .andExpect(jsonPath("$.data.playerId").value("seller-2001"))
                .andExpect(jsonPath("$.data.username").value("seller.demo"));
    }

    @Test
    void updateProfile_overridesBodySellerIdFromHeader() throws Exception {
        ProfileApi.UpdateProfileRequest body = new ProfileApi.UpdateProfileRequest(
                "seller-body",
                "Seller Studio",
                "studio@example.com",
                "GOLD"
        );
        ProfileApi.ProfileResponse response = new ProfileApi.ProfileResponse(
                "seller-2001",
                "seller.demo",
                "Seller Studio",
                "studio@example.com",
                "GOLD",
                Instant.parse("2026-03-20T00:00:00Z"),
                Instant.parse("2026-03-21T00:00:00Z")
        );
        when(service.updateProfile(new ProfileApi.UpdateProfileRequest(
                "seller-2001",
                "Seller Studio",
                "studio@example.com",
                "GOLD"
        ))).thenReturn(response);

        mockMvc.perform(post(SellerApi.PROFILE_UPDATE)
                        .header("X-Player-Id", "seller-2001")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SC_OK"))
                .andExpect(jsonPath("$.data.playerId").value("seller-2001"))
                .andExpect(jsonPath("$.data.displayName").value("Seller Studio"));
    }
}
