package dev.meirong.shop.profile.controller;

import dev.meirong.shop.common.api.ApiResponse;
import dev.meirong.shop.contracts.profile.ProfileInternalApi;
import dev.meirong.shop.profile.service.ProfileReferralService;
import io.swagger.v3.oas.annotations.Hidden;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Hidden
@RestController
@RequestMapping(ProfileInternalApi.BASE_PATH)
public class ProfileInternalController {

    private final ProfileReferralService service;

    public ProfileInternalController(ProfileReferralService service) {
        this.service = service;
    }

    @PostMapping("/buyer/register")
    public ApiResponse<Void> registerBuyer(@Valid @RequestBody ProfileInternalApi.RegisterBuyerRequest request) {
        service.registerBuyer(request);
        return ApiResponse.success(null);
    }

    @PostMapping("/invite/stats")
    public ApiResponse<ProfileInternalApi.InviteStatsResponse> inviteStats(
            @Valid @RequestBody ProfileInternalApi.InviteStatsRequest request) {
        return ApiResponse.success(service.getInviteStats(request.buyerId()));
    }

    @PostMapping("/referral/first-order")
    public ApiResponse<ProfileInternalApi.ReferralRewardResult> markReferralFirstOrder(
            @Valid @RequestBody ProfileInternalApi.ReferralFirstOrderRequest request) {
        return ApiResponse.success(service.markReferralFirstOrder(request.inviteeId()));
    }
}
