package dev.meirong.shop.kmp.core.model

import kotlinx.serialization.Serializable

@Serializable
data class BuyerOffer(
    val id: String,
    val code: String,
    val title: String,
    val description: String,
    val rewardAmountInCents: Long,
    val active: Boolean,
    val source: String,
    val createdAt: String
)

@Serializable
data class BuyerCoupon(
    val id: String,
    val sellerId: String,
    val code: String,
    val discountType: String,
    val discountValueInCents: Long,
    val minOrderAmountInCents: Long?,
    val maxDiscountInCents: Long?,
    val usageLimit: Int,
    val usedCount: Int,
    val expiresAt: String?,
    val active: Boolean,
    val createdAt: String
)

@Serializable
data class BuyerPromotionCatalog(
    val offers: List<BuyerOffer>,
    val coupons: List<BuyerCoupon>
)
