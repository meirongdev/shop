package dev.meirong.shop.kmp.core.model

data class SellerStorefront(
    val sellerId: String,
    val username: String,
    val displayName: String,
    val shopName: String?,
    val shopSlug: String?,
    val shopDescription: String?,
    val logoUrl: String?,
    val bannerUrl: String?,
    val avgRating: Double,
    val totalSales: Int,
    val tier: String,
    val createdAt: String
)
