package dev.meirong.shop.kmp.core.model

import kotlinx.serialization.Serializable

@Serializable
data class SearchResult(
    val hits: List<ProductHit>,
    val totalHits: Long,
    val page: Int,
    val totalPages: Int
)

@Serializable
data class ProductHit(
    val id: String,
    val sellerId: String,
    val name: String,
    val description: String,
    val price: Double,
    val inventory: Int,
    val categoryId: String? = null,
    val categoryName: String? = null,
    val imageUrl: String? = null,
    val status: String? = null
)
