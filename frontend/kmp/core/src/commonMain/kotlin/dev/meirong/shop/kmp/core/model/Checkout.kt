package dev.meirong.shop.kmp.core.model

import kotlinx.serialization.Serializable

@Serializable
data class CheckoutResult(
    val orders: List<ShopOrder>,
    val totalPaidInCents: Long
)
