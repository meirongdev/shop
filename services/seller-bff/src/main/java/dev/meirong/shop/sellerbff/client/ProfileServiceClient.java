package dev.meirong.shop.sellerbff.client;

import dev.meirong.shop.common.api.ApiResponse;
import dev.meirong.shop.contracts.profile.ProfileApi;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.service.annotation.HttpExchange;
import org.springframework.web.service.annotation.PostExchange;

@HttpExchange
public interface ProfileServiceClient {

    @PostExchange(ProfileApi.SELLER_GET)
    ApiResponse<ProfileApi.ProfileResponse> getSellerProfile(
            @RequestBody ProfileApi.GetProfileRequest request);

    @PostExchange(ProfileApi.SELLER_UPDATE)
    ApiResponse<ProfileApi.ProfileResponse> updateSellerProfile(
            @RequestBody ProfileApi.UpdateProfileRequest request);

    @PostExchange(ProfileApi.SELLER_STOREFRONT)
    ApiResponse<ProfileApi.SellerStorefrontResponse> getSellerStorefront(
            @RequestBody ProfileApi.GetProfileRequest request);

    @PostExchange(ProfileApi.SELLER_SHOP_UPDATE)
    ApiResponse<ProfileApi.SellerStorefrontResponse> updateSellerShop(
            @RequestBody ProfileApi.UpdateShopRequest request);
}
