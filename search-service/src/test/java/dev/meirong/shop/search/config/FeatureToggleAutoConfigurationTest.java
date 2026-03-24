package dev.meirong.shop.search.config;

import static org.assertj.core.api.Assertions.assertThat;

import dev.meirong.shop.common.feature.FeatureToggleAutoConfiguration;
import dev.meirong.shop.common.feature.FeatureToggleProperties;
import dev.meirong.shop.common.feature.FeatureToggleService;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class FeatureToggleAutoConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(FeatureToggleAutoConfiguration.class));

    @Test
    void featureToggleService_readsConfiguredFlags() {
        contextRunner
                .withPropertyValues(
                        "shop.features.flags.search-autocomplete=true",
                        "shop.features.flags.search-trending=false")
                .run(context -> {
                    FeatureToggleService service = context.getBean(FeatureToggleService.class);

                    assertThat(service.isEnabled(SearchFeatureFlags.AUTOCOMPLETE, false)).isTrue();
                    assertThat(service.isEnabled(SearchFeatureFlags.TRENDING, true)).isFalse();
                    assertThat(service.isEnabled("missing-flag", true)).isTrue();
                });
    }

    @Test
    void featureToggleService_readsLatestMutablePropertyValues() {
        contextRunner
                .withPropertyValues("shop.features.flags.search-autocomplete=true")
                .run(context -> {
                    FeatureToggleService service = context.getBean(FeatureToggleService.class);
                    FeatureToggleProperties properties = context.getBean(FeatureToggleProperties.class);

                    assertThat(service.isEnabled(SearchFeatureFlags.AUTOCOMPLETE, false)).isTrue();

                    properties.setFlags(Map.of(SearchFeatureFlags.AUTOCOMPLETE, false));

                    assertThat(service.isEnabled(SearchFeatureFlags.AUTOCOMPLETE, true)).isFalse();
                });
    }
}
