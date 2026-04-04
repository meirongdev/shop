package dev.meirong.shop.authserver.service;

import dev.meirong.shop.common.api.ApiResponse;
import dev.meirong.shop.common.error.BusinessException;
import dev.meirong.shop.common.error.CommonErrorCode;
import dev.meirong.shop.contracts.api.ProfileInternalApi;
import dev.meirong.shop.authserver.config.AuthProperties;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class ProfileRegistrationClient {

    private final RestClient restClient;
    private final AuthProperties properties;

    public ProfileRegistrationClient(RestClient.Builder builder, AuthProperties properties) {
        this.restClient = builder.build();
        this.properties = properties;
    }

    public void registerBuyer(ProfileInternalApi.RegisterBuyerRequest request) {
        ApiResponse<Void> response = restClient.post()
                .uri(properties.profileServiceUrl() + ProfileInternalApi.BUYER_REGISTER)
                .body(request)
                .retrieve()
                .body(new ParameterizedTypeReference<>() {});
        if (response == null) {
            throw new BusinessException(CommonErrorCode.DOWNSTREAM_ERROR, "Profile service returned empty response");
        }
        if (!"SC_OK".equals(response.status())) {
            throw new BusinessException(CommonErrorCode.DOWNSTREAM_ERROR, response.message());
        }
    }
}
