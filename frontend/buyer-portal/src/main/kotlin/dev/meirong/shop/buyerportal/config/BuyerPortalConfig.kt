package dev.meirong.shop.buyerportal.config

import java.net.http.HttpClient
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.client.JdkClientHttpRequestFactory
import org.springframework.web.client.RestClient

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(BuyerPortalProperties::class)
class BuyerPortalConfig {

    @Bean
    fun jdkClientHttpRequestFactory(properties: BuyerPortalProperties): JdkClientHttpRequestFactory {
        val httpClient = HttpClient.newBuilder()
            .version(properties.httpVersion)
            .connectTimeout(properties.connectTimeout)
            .build()
        return JdkClientHttpRequestFactory(httpClient).apply {
            setReadTimeout(properties.readTimeout)
        }
    }

    @Bean
    fun restClientBuilder(jdkClientHttpRequestFactory: JdkClientHttpRequestFactory): RestClient.Builder =
        RestClient.builder().requestFactory(jdkClientHttpRequestFactory)
}
