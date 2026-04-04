package dev.meirong.shop.common.feature;

import dev.openfeature.sdk.ErrorCode;
import dev.openfeature.sdk.EvaluationContext;
import dev.openfeature.sdk.FeatureProvider;
import dev.openfeature.sdk.ImmutableMetadata;
import dev.openfeature.sdk.Metadata;
import dev.openfeature.sdk.ProviderEvaluation;
import dev.openfeature.sdk.Value;

public class OpenFeaturePropertyProvider implements FeatureProvider {

    private static final Metadata METADATA = () -> "shop-k8s-property-provider";

    private final FeatureToggleProperties properties;

    public OpenFeaturePropertyProvider(FeatureToggleProperties properties) {
        this.properties = properties;
    }

    @Override
    public Metadata getMetadata() {
        return METADATA;
    }

    @Override
    public ProviderEvaluation<Boolean> getBooleanEvaluation(String key, Boolean defaultValue, EvaluationContext context) {
        if (!properties.contains(key)) {
            return missing(key, defaultValue);
        }
        Boolean value = properties.get(key);
        return ProviderEvaluation.<Boolean>builder()
                .value(value)
                .variant(Boolean.TRUE.equals(value) ? "enabled" : "disabled")
                .reason("STATIC")
                .flagMetadata(ImmutableMetadata.builder()
                        .addString("source", "spring-config")
                        .addString("key", key)
                        .build())
                .build();
    }

    @Override
    public ProviderEvaluation<String> getStringEvaluation(String key, String defaultValue, EvaluationContext context) {
        return typeMismatch(key, defaultValue);
    }

    @Override
    public ProviderEvaluation<Integer> getIntegerEvaluation(String key, Integer defaultValue, EvaluationContext context) {
        return typeMismatch(key, defaultValue);
    }

    @Override
    public ProviderEvaluation<Double> getDoubleEvaluation(String key, Double defaultValue, EvaluationContext context) {
        return typeMismatch(key, defaultValue);
    }

    @Override
    public ProviderEvaluation<Value> getObjectEvaluation(String key, Value defaultValue, EvaluationContext context) {
        return typeMismatch(key, defaultValue);
    }

    private <T> ProviderEvaluation<T> missing(String key, T defaultValue) {
        return ProviderEvaluation.<T>builder()
                .value(defaultValue)
                .reason("DEFAULT")
                .errorCode(ErrorCode.FLAG_NOT_FOUND)
                .errorMessage("Feature flag '" + key + "' is not defined")
                .flagMetadata(ImmutableMetadata.builder()
                        .addString("source", "spring-config")
                        .addString("key", key)
                        .build())
                .build();
    }

    private <T> ProviderEvaluation<T> typeMismatch(String key, T defaultValue) {
        return ProviderEvaluation.<T>builder()
                .value(defaultValue)
                .reason("ERROR")
                .errorCode(ErrorCode.TYPE_MISMATCH)
                .errorMessage("Feature flag '" + key + "' is not a boolean flag")
                .flagMetadata(ImmutableMetadata.builder()
                        .addString("source", "spring-config")
                        .addString("key", key)
                        .build())
                .build();
    }
}
