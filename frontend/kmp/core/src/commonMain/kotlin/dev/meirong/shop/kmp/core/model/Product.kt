package dev.meirong.shop.kmp.core.model

import kotlinx.serialization.Serializable

@Serializable
data class Product(
    val id: String,
    val sellerId: String,
    val sku: String,
    val name: String,
    val description: String,
    val priceInCents: Long,
    val inventory: Int,
    val published: Boolean,
    val categoryId: String? = null,
    val categoryName: String? = null,
    val imageUrl: String? = null,
    val status: String? = null
)
