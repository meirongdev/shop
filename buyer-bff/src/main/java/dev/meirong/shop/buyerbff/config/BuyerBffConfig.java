package dev.meirong.shop.buyerbff.config;

import dev.meirong.shop.buyerbff.client.SearchServiceClient;
import java.net.http.HttpClient;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.support.RestClientAdapter;
import org.springframework.web.service.invoker.HttpServiceProxyFactory;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(BuyerClientProperties.class)
public class BuyerBffConfig {

    @Bean
    JdkClientHttpRequestFactory jdkClientHttpRequestFactory(BuyerClientProperties properties) {
        HttpClient httpClient = HttpClient.newBuilder()
                .version(properties.httpVersion())
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
    SearchServiceClient searchServiceClient(BuyerClientProperties properties,
                                            JdkClientHttpRequestFactory jdkClientHttpRequestFactory) {
        RestClient searchRestClient = RestClient.builder()
                .requestFactory(jdkClientHttpRequestFactory)
                .baseUrl(properties.searchServiceUrl())
                .defaultHeader("X-Internal-Token", properties.internalToken())
                .build();
        HttpServiceProxyFactory factory = HttpServiceProxyFactory
                .builderFor(RestClientAdapter.create(searchRestClient))
                .build();
        return factory.createClient(SearchServiceClient.class);
    }
}
