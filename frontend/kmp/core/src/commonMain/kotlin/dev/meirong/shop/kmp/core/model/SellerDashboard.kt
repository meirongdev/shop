package dev.meirong.shop.kmp.core.model

import kotlinx.serialization.Serializable

@Serializable
data class SellerDashboard(
    val productCount: Long,
    val activePromotionCount: Long,
    val products: List<Product>
)
