package dev.meirong.shop.kmp.core.model

import kotlinx.serialization.Serializable

@Serializable
data class ShopOrder(
    val id: String,
    val buyerId: String,
    val sellerId: String,
    val status: String,
    val subtotalInCents: Long,
    val discountAmountInCents: Long,
    val totalAmountInCents: Long,
    val couponId: String? = null,
    val couponCode: String? = null,
    val paymentTransactionId: String? = null,
    val items: List<OrderLineItem>,
    val createdAt: String,
    val updatedAt: String
)

@Serializable
data class OrderLineItem(
    val id: String,
    val orderId: String,
    val productId: String,
    val productName: String,
    val productPriceInCents: Long,
    val quantity: Int,
    val lineTotalInCents: Long
)
