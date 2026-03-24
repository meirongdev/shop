package dev.meirong.shop.common.feature;

import dev.meirong.shop.common.error.BusinessException;
import dev.meirong.shop.common.error.CommonErrorCode;
import dev.openfeature.sdk.Client;

public class FeatureToggleService {

    private final Client client;

    public FeatureToggleService(Client client) {
        this.client = client;
    }

    public boolean isEnabled(String flagKey, boolean defaultValue) {
        return client.getBooleanValue(flagKey, defaultValue);
    }

    public void requireEnabled(String flagKey, boolean defaultValue, String message) {
        if (!isEnabled(flagKey, defaultValue)) {
            throw new BusinessException(CommonErrorCode.FEATURE_DISABLED, message);
        }
    }
}
