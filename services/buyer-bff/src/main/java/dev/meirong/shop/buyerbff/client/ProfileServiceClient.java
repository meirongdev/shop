package dev.meirong.shop.buyerbff.client;

import dev.meirong.shop.common.api.ApiResponse;
import dev.meirong.shop.contracts.profile.ProfileApi;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.service.annotation.HttpExchange;
import org.springframework.web.service.annotation.PostExchange;

@HttpExchange
public interface ProfileServiceClient {

    @PostExchange(ProfileApi.GET)
    ApiResponse<ProfileApi.ProfileResponse> getProfile(
            @RequestBody ProfileApi.GetProfileRequest request);

    @PostExchange(ProfileApi.UPDATE)
    ApiResponse<ProfileApi.ProfileResponse> updateProfile(
            @RequestBody ProfileApi.UpdateProfileRequest request);

    @PostExchange(ProfileApi.SELLER_STOREFRONT)
    ApiResponse<ProfileApi.SellerStorefrontResponse> getSellerStorefront(
            @RequestBody ProfileApi.GetProfileRequest request);
}
