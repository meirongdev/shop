package dev.meirong.shop.clients.profile;

import dev.meirong.shop.common.api.ApiResponse;
import dev.meirong.shop.contracts.profile.ProfileInternalApi;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.service.annotation.HttpExchange;
import org.springframework.web.service.annotation.PostExchange;

/**
 * {@code @HttpExchange} client for profile-service internal API.
 */
@HttpExchange
public interface ProfileInternalServiceClient {

    @PostExchange(ProfileInternalApi.INVITE_STATS)
    ApiResponse<ProfileInternalApi.InviteStatsResponse> getInviteStats(@RequestBody ProfileInternalApi.InviteStatsRequest request);
}
