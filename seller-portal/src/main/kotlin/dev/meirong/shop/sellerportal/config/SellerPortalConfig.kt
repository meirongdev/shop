package dev.meirong.shop.sellerportal.config

import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.web.client.RestClient

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(SellerPortalProperties::class)
class SellerPortalConfig {

    @Bean
    fun restClientBuilder(): RestClient.Builder = RestClient.builder()
}
