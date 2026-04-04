package dev.meirong.shop.buyerportal.config

import java.net.http.HttpClient
import java.time.Duration
import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "shop.portal")
data class BuyerPortalProperties(
    val authBaseUrl: String,
    val gatewayBaseUrl: String,
    val appleClientId: String,
    val appleRedirectUri: String,
    val httpVersion: HttpClient.Version = HttpClient.Version.HTTP_1_1,
    val connectTimeout: Duration = Duration.ofSeconds(2),
    val readTimeout: Duration = Duration.ofSeconds(5)
)
