package dev.meirong.shop.authserver.controller;

import dev.meirong.shop.authserver.service.AuthenticationApplicationService;
import dev.meirong.shop.authserver.service.GuestSessionService;
import dev.meirong.shop.authserver.service.JwtTokenService;
import dev.meirong.shop.authserver.service.BuyerAccountProvisioningService;
import dev.meirong.shop.authserver.service.OtpChallengeService;
import dev.meirong.shop.authserver.service.SocialLoginService;
import dev.meirong.shop.common.api.ApiResponse;
import dev.meirong.shop.contracts.auth.AuthApi;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(AuthApi.BASE_PATH)
@Tag(name = "Authentication")
public class AuthController {

    private final AuthenticationManager authenticationManager;
    private final AuthenticationApplicationService authenticationApplicationService;
    private final GuestSessionService guestSessionService;
    private final JwtTokenService jwtTokenService;
    private final SocialLoginService socialLoginService;
    private final BuyerAccountProvisioningService buyerAccountProvisioningService;
    private final OtpChallengeService otpChallengeService;

    public AuthController(AuthenticationManager authenticationManager,
                          AuthenticationApplicationService authenticationApplicationService,
                          GuestSessionService guestSessionService,
                          JwtTokenService jwtTokenService,
                          SocialLoginService socialLoginService,
                          BuyerAccountProvisioningService buyerAccountProvisioningService,
                          OtpChallengeService otpChallengeService) {
        this.authenticationManager = authenticationManager;
        this.authenticationApplicationService = authenticationApplicationService;
        this.guestSessionService = guestSessionService;
        this.jwtTokenService = jwtTokenService;
        this.socialLoginService = socialLoginService;
        this.buyerAccountProvisioningService = buyerAccountProvisioningService;
        this.otpChallengeService = otpChallengeService;
    }

    @PostMapping("/token/login")
    @Operation(summary = "用户名密码登录")
    @SecurityRequirements
    public ApiResponse<AuthApi.TokenResponse> login(@Valid @RequestBody AuthApi.LoginRequest request) {
        authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.username(), request.password())
        );
        return ApiResponse.success(authenticationApplicationService.issueLoginToken(request.username(), request.portal()));
    }

    @PostMapping("/token/guest")
    @Operation(summary = "签发访客令牌")
    @SecurityRequirements
    public ApiResponse<AuthApi.TokenResponse> guest(@Valid @RequestBody AuthApi.GuestTokenRequest request) {
        return ApiResponse.success(jwtTokenService.issueToken(guestSessionService.issueGuestProfile(request.portal())));
    }

    @PostMapping("/token/oauth2/google")
    @Operation(summary = "Google OAuth2 登录")
    @SecurityRequirements
    public ApiResponse<AuthApi.TokenResponse> googleLogin(@Valid @RequestBody AuthApi.OAuth2GoogleRequest request) {
        return ApiResponse.success(socialLoginService.loginWithGoogle(request.idToken(), request.portal()));
    }

    @PostMapping("/token/oauth2/apple")
    @Operation(summary = "Apple Sign-In 登录")
    @SecurityRequirements
    public ApiResponse<AuthApi.TokenResponse> appleLogin(@Valid @RequestBody AuthApi.OAuth2AppleRequest request) {
        return ApiResponse.success(socialLoginService.loginWithApple(request.idToken(), request.nonce(), request.portal()));
    }

    @PostMapping("/buyer/register")
    @Operation(summary = "买家注册并自动登录")
    @SecurityRequirements
    public ApiResponse<AuthApi.TokenResponse> registerBuyer(@Valid @RequestBody AuthApi.BuyerRegisterRequest request) {
        return ApiResponse.success(jwtTokenService.issueToken(buyerAccountProvisioningService.registerBuyer(request), true));
    }

    @PostMapping("/otp/send")
    @Operation(summary = "发送短信验证码")
    @SecurityRequirements
    public ApiResponse<AuthApi.OtpSendResponse> sendOtp(@Valid @RequestBody AuthApi.OtpSendRequest request) {
        return ApiResponse.success(otpChallengeService.sendOtp(request));
    }

    @PostMapping("/otp/verify")
    @Operation(summary = "校验短信验证码并登录")
    @SecurityRequirements
    public ApiResponse<AuthApi.TokenResponse> verifyOtp(@Valid @RequestBody AuthApi.OtpVerifyRequest request) {
        return ApiResponse.success(otpChallengeService.verifyOtp(request));
    }

    @PostMapping("/social/bind")
    public ApiResponse<Void> bindSocialAccount(@Valid @RequestBody AuthApi.SocialBindRequest request,
                                               JwtAuthenticationToken auth) {
        String principalId = auth.getToken().getClaimAsString("principalId");
        socialLoginService.bindSocialAccount(principalId, request.provider(), request.idToken());
        return ApiResponse.success(null);
    }

    @GetMapping("/social/list")
    public ApiResponse<List<AuthApi.SocialAccountInfo>> listSocialAccounts(JwtAuthenticationToken auth) {
        String principalId = auth.getToken().getClaimAsString("principalId");
        return ApiResponse.success(socialLoginService.listLinkedAccounts(principalId));
    }

    @GetMapping("/user/me")
    public ApiResponse<AuthApi.MeResponse> me(JwtAuthenticationToken authenticationToken) {
        return ApiResponse.success(new AuthApi.MeResponse(
                authenticationToken.getToken().getClaimAsString("username"),
                authenticationToken.getToken().getClaimAsString("displayName"),
                authenticationToken.getToken().getClaimAsString("principalId"),
                authenticationToken.getToken().getClaimAsStringList("roles"),
                authenticationToken.getToken().getClaimAsString("portal")
        ));
    }
}
