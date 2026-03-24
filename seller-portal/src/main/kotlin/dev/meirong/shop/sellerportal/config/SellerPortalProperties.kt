package dev.meirong.shop.sellerportal.config

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "shop.portal")
data class SellerPortalProperties(
    val authBaseUrl: String,
    val gatewayBaseUrl: String
)
