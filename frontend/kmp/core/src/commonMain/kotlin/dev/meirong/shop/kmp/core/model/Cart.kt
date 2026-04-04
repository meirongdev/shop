package dev.meirong.shop.kmp.core.model

import kotlinx.serialization.Serializable

@Serializable
data class ShoppingCart(
    val items: List<CartItem>,
    val subtotalInCents: Long
)

@Serializable
data class CartItem(
    val id: String,
    val buyerId: String,
    val productId: String,
    val productName: String,
    val productPriceInCents: Long,
    val sellerId: String,
    val quantity: Int,
    val createdAt: String
)
