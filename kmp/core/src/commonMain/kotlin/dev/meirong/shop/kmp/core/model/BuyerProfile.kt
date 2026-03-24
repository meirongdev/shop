package dev.meirong.shop.kmp.core.model

data class BuyerProfile(
    val buyerId: String,
    val username: String,
    val displayName: String,
    val email: String,
    val tier: String,
    val createdAt: String,
    val updatedAt: String
)
