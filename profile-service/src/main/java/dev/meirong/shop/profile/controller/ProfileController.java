package dev.meirong.shop.profile.controller;

import dev.meirong.shop.common.api.ApiResponse;
import dev.meirong.shop.contracts.api.ProfileApi;
import dev.meirong.shop.profile.service.ProfileApplicationService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(ProfileApi.BASE_PATH)
public class ProfileController {

    private final ProfileApplicationService service;

    public ProfileController(ProfileApplicationService service) {
        this.service = service;
    }

    @PostMapping("/profile/get")
    public ApiResponse<ProfileApi.ProfileResponse> getProfile(@Valid @RequestBody ProfileApi.GetProfileRequest request) {
        return ApiResponse.success(service.getProfile(request));
    }

    @PostMapping("/profile/update")
    public ApiResponse<ProfileApi.ProfileResponse> updateProfile(@Valid @RequestBody ProfileApi.UpdateProfileRequest request) {
        return ApiResponse.success(service.updateProfile(request));
    }

    @PostMapping("/seller/get")
    public ApiResponse<ProfileApi.ProfileResponse> getSellerProfile(@Valid @RequestBody ProfileApi.GetProfileRequest request) {
        return ApiResponse.success(service.getSellerProfile(request));
    }

    @PostMapping("/seller/update")
    public ApiResponse<ProfileApi.ProfileResponse> updateSellerProfile(@Valid @RequestBody ProfileApi.UpdateProfileRequest request) {
        return ApiResponse.success(service.updateSellerProfile(request));
    }

    @PostMapping("/seller/storefront")
    public ApiResponse<ProfileApi.SellerStorefrontResponse> getSellerStorefront(@Valid @RequestBody ProfileApi.GetProfileRequest request) {
        return ApiResponse.success(service.getSellerStorefront(request.playerId()));
    }

    @PostMapping("/seller/shop/update")
    public ApiResponse<ProfileApi.SellerStorefrontResponse> updateShop(@Valid @RequestBody ProfileApi.UpdateShopRequest request) {
        return ApiResponse.success(service.updateShop(request));
    }
}
