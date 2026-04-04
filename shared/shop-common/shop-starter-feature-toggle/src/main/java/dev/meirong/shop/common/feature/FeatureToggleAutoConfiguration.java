package dev.meirong.shop.common.feature;

import dev.openfeature.sdk.Client;
import dev.openfeature.sdk.OpenFeatureAPI;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

@AutoConfiguration
@EnableConfigurationProperties(FeatureToggleProperties.class)
public class FeatureToggleAutoConfiguration {

    static final String OPENFEATURE_DOMAIN = "shop-platform";

    @Bean
    OpenFeaturePropertyProvider openFeaturePropertyProvider(FeatureToggleProperties properties) {
        return new OpenFeaturePropertyProvider(properties);
    }

    @Bean
    Client featureToggleClient(OpenFeaturePropertyProvider provider) {
        OpenFeatureAPI api = OpenFeatureAPI.getInstance();
        api.setProvider(OPENFEATURE_DOMAIN, provider);
        return api.getClient(OPENFEATURE_DOMAIN);
    }

    @Bean
    FeatureToggleService featureToggleService(Client client) {
        return new FeatureToggleService(client);
    }

    @Bean
    DisposableBean openFeatureShutdownHook() {
        return () -> OpenFeatureAPI.getInstance().shutdown();
    }
}
