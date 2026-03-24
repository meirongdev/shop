package dev.meirong.shop.authserver.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.meirong.shop.authserver.config.SecurityConfig;
import dev.meirong.shop.authserver.domain.UserAccountRepository;
import dev.meirong.shop.authserver.service.AuthenticationApplicationService;
import dev.meirong.shop.authserver.service.BuyerAccountProvisioningService;
import dev.meirong.shop.authserver.service.DemoUserDirectory;
import dev.meirong.shop.authserver.service.GuestSessionService;
import dev.meirong.shop.authserver.service.JwtTokenService;
import dev.meirong.shop.authserver.service.OtpChallengeService;
import dev.meirong.shop.authserver.service.SocialLoginService;
import dev.meirong.shop.common.web.GlobalExceptionHandler;
import dev.meirong.shop.contracts.api.AuthApi;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import org.hamcrest.Matchers;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AuthController.class)
@Import({SecurityConfig.class, DemoUserDirectory.class, GuestSessionService.class, JwtTokenService.class, GlobalExceptionHandler.class})
@TestPropertySource(properties = {
        "shop.auth.issuer=test-issuer",
        "shop.auth.secret=change-this-to-a-32-byte-demo-secret",
        "shop.auth.token-ttl=PT1H",
        "shop.auth.google-client-id=test-google-client-id",
        "shop.auth.apple-client-id=test-apple-client-id",
        "shop.auth.profile-service-url=http://profile-service:8080",
        "shop.auth.internal-token=test-internal-token",
        "shop.auth.user-registered-topic=user.registered.v1"
})
class AuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JwtTokenService jwtTokenService;

    @Autowired
    private DemoUserDirectory userDirectory;

    @org.springframework.test.context.bean.override.mockito.MockitoBean
    private SocialLoginService socialLoginService;

    @org.springframework.test.context.bean.override.mockito.MockitoBean
    private UserAccountRepository userAccountRepository;

    @org.springframework.test.context.bean.override.mockito.MockitoBean
    private AuthenticationApplicationService authenticationApplicationService;

    @org.springframework.test.context.bean.override.mockito.MockitoBean
    private BuyerAccountProvisioningService buyerAccountProvisioningService;

    @org.springframework.test.context.bean.override.mockito.MockitoBean
    private OtpChallengeService otpChallengeService;

    @BeforeEach
    void setUp() {
        when(userAccountRepository.findByUsername(anyString())).thenReturn(Optional.empty());
        when(authenticationApplicationService.issueLoginToken("buyer.demo", "buyer"))
                .thenReturn(jwtTokenService.issueToken(userDirectory.requirePortalAccess("buyer.demo", "buyer")));
    }

    @Test
    void login_withValidBuyerCredentials_returns200WithToken() throws Exception {
        AuthApi.LoginRequest request = new AuthApi.LoginRequest("buyer.demo", "password", "buyer");

        mockMvc.perform(post(AuthApi.LOGIN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SC_OK"))
                .andExpect(jsonPath("$.data.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.data.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.data.username").value("buyer.demo"))
                .andExpect(jsonPath("$.data.displayName").value("Buyer Demo"))
                .andExpect(jsonPath("$.data.principalId").value("player-1001"))
                .andExpect(jsonPath("$.data.portal").value("buyer"));
    }

    @Test
    void login_withInvalidCredentials_returnsError() throws Exception {
        AuthApi.LoginRequest request = new AuthApi.LoginRequest("buyer.demo", "wrong-password", "buyer");

        mockMvc.perform(post(AuthApi.LOGIN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(jsonPath("$.code").value("SC_INTERNAL_ERROR"))
                .andExpect(jsonPath("$.message").value("Bad credentials"));
    }

    @Test
    void guest_withBuyerPortal_returnsGuestToken() throws Exception {
        AuthApi.GuestTokenRequest request = new AuthApi.GuestTokenRequest("buyer");

        mockMvc.perform(post(AuthApi.GUEST)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SC_OK"))
                .andExpect(jsonPath("$.data.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.data.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.data.username").value(org.hamcrest.Matchers.startsWith("guest.")))
                .andExpect(jsonPath("$.data.displayName").value("Guest Buyer"))
                .andExpect(jsonPath("$.data.principalId").value(org.hamcrest.Matchers.startsWith("guest-buyer-")))
                .andExpect(jsonPath("$.data.roles", Matchers.contains("ROLE_BUYER_GUEST")))
                .andExpect(jsonPath("$.data.portal").value("buyer"));
    }

    @Test
    void guest_withSellerPortal_returnsForbidden() throws Exception {
        AuthApi.GuestTokenRequest request = new AuthApi.GuestTokenRequest("seller");

        mockMvc.perform(post(AuthApi.GUEST)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(jsonPath("$.code").value("SC_FORBIDDEN"))
                .andExpect(jsonPath("$.message").value("Guest access is only available for buyer portal"));
    }

    @Test
    void me_withoutToken_returns401() throws Exception {
        mockMvc.perform(get(AuthApi.ME))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void me_withValidJwt_returnsUserInfo() throws Exception {
        mockMvc.perform(get(AuthApi.ME)
                        .with(jwt().jwt(j -> j
                                .claim("username", "buyer.demo")
                                .claim("displayName", "Buyer Demo")
                                .claim("principalId", "player-1001")
                                .claim("roles", List.of("ROLE_BUYER"))
                                .claim("portal", "buyer"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SC_OK"))
                .andExpect(jsonPath("$.data.username").value("buyer.demo"))
                .andExpect(jsonPath("$.data.displayName").value("Buyer Demo"))
                .andExpect(jsonPath("$.data.principalId").value("player-1001"))
                .andExpect(jsonPath("$.data.roles[0]").value("ROLE_BUYER"))
                .andExpect(jsonPath("$.data.portal").value("buyer"));
    }
}
