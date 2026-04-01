package dev.meirong.shop.common.json;

import com.fasterxml.jackson.databind.DeserializationFeature;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;

@AutoConfiguration
public class JacksonCompatibilityAutoConfiguration {

    @Bean
    Jackson2ObjectMapperBuilderCustomizer compatibilityObjectMapperCustomizer() {
        return builder -> builder.featuresToDisable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
    }
}
