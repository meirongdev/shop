package dev.meirong.shop.sellerbff.config;

import java.net.http.HttpClient;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(SellerClientProperties.class)
public class SellerBffConfig {

    @Bean
    JdkClientHttpRequestFactory jdkClientHttpRequestFactory(SellerClientProperties properties) {
        HttpClient httpClient = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_2)
                .connectTimeout(properties.connectTimeout())
                .build();
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(httpClient);
        factory.setReadTimeout(properties.readTimeout());
        return factory;
    }

    @Bean
    RestClient.Builder restClientBuilder(JdkClientHttpRequestFactory jdkClientHttpRequestFactory) {
        return RestClient.builder()
                .requestFactory(jdkClientHttpRequestFactory);
    }

    @Bean
    RestClient searchRestClient(SellerClientProperties properties,
                                JdkClientHttpRequestFactory jdkClientHttpRequestFactory) {
        return RestClient.builder()
                .requestFactory(jdkClientHttpRequestFactory)
                .baseUrl(properties.searchServiceUrl())
                .build();
    }
}
