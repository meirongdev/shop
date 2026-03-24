package dev.meirong.shop.common.json;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.DeserializationFeature;
import org.junit.jupiter.api.Test;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;

class JacksonCompatibilityAutoConfigurationTest {

    @Test
    void compatibilityCustomizer_disablesFailOnUnknownProperties() {
        Jackson2ObjectMapperBuilder builder = new Jackson2ObjectMapperBuilder();

        new JacksonCompatibilityAutoConfiguration().compatibilityObjectMapperCustomizer().customize(builder);

        assertThat(builder.build().isEnabled(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)).isFalse();
    }
}
