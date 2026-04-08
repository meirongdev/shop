package dev.meirong.shop.common.http;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.web.client.RestClientCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.web.client.RestClient;

@AutoConfiguration
@ConditionalOnClass(RestClient.class)
public class HeaderPropagationAutoConfiguration {

    @Bean
    public RestClientCustomizer headerPropagationRestClientCustomizer() {
        return restClientBuilder -> restClientBuilder.requestInterceptor(new HeaderPropagationInterceptor());
    }
}
