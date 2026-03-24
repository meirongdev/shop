package dev.meirong.shop.kmp.feature.marketplace.data

import dev.meirong.shop.kmp.core.model.Product
import dev.meirong.shop.kmp.core.model.ProductHit
import dev.meirong.shop.kmp.core.model.SearchResult
import dev.meirong.shop.kmp.core.network.ApiResponse
import dev.meirong.shop.kmp.core.network.HttpClientFactory
import dev.meirong.shop.kmp.core.network.gatewayApiBaseUrl
import dev.meirong.shop.kmp.core.session.NoOpTokenStorage
import dev.meirong.shop.kmp.core.session.TokenStorage
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import kotlin.math.roundToLong
import kotlinx.serialization.Serializable

private const val buyerProductSearchPath = "/buyer/v1/product/search"
private const val buyerProductGetPath = "/buyer/v1/product/get"

class BuyerMarketplaceRepository(
    tokenStorage: TokenStorage = NoOpTokenStorage,
    private val client: HttpClient = HttpClientFactory.create(tokenStorage),
    private val baseUrl: String = gatewayApiBaseUrl()
) {

    suspend fun searchProducts(
        query: String,
        page: Int = 1,
        size: Int = 12
    ): SearchResult {
        val response = client.post("$baseUrl$buyerProductSearchPath") {
            header(HttpHeaders.ContentType, ContentType.Application.Json.toString())
            setBody(SearchProductsRequestDto(query = query, categoryId = null, page = page, size = size))
        }.body<ApiResponse<SearchProductsResponseDto>>()

        return response.requireData().toModel()
    }

    suspend fun getProduct(productId: String): Product {
        val response = client.post("$baseUrl$buyerProductGetPath") {
            header(HttpHeaders.ContentType, ContentType.Application.Json.toString())
            setBody(GetProductRequestDto(productId = productId))
        }.body<ApiResponse<ProductResponseDto>>()

        return response.requireData().toModel()
    }

    fun close() {
        client.close()
    }
}

private fun ApiResponse<SearchProductsResponseDto>.requireData(): SearchProductsResponseDto {
    return data ?: error(message.ifBlank { "Search response did not include data." })
}

private fun ApiResponse<ProductResponseDto>.requireData(): ProductResponseDto {
    return data ?: error(message.ifBlank { "Product response did not include data." })
}

private fun SearchProductsResponseDto.toModel(): SearchResult = SearchResult(
    hits = hits.map { hit ->
        ProductHit(
            id = hit.id,
            sellerId = hit.sellerId,
            name = hit.name,
            description = hit.description,
            price = hit.price,
            inventory = hit.inventory,
            categoryId = hit.categoryId,
            categoryName = hit.categoryName,
            imageUrl = hit.imageUrl,
            status = hit.status
        )
    },
    totalHits = totalHits,
    page = page,
    totalPages = totalPages
)

private fun ProductResponseDto.toModel(): Product = Product(
    id = id,
    sellerId = sellerId,
    sku = sku,
    name = name,
    description = description,
    priceInCents = (price * 100.0).roundToLong(),
    inventory = inventory,
    published = published,
    categoryId = categoryId,
    categoryName = categoryName,
    imageUrl = imageUrl,
    status = status
)

@Serializable
private data class SearchProductsRequestDto(
    val query: String,
    val categoryId: String? = null,
    val page: Int,
    val size: Int
)

@Serializable
private data class GetProductRequestDto(
    val productId: String
)

@Serializable
private data class SearchProductsResponseDto(
    val hits: List<ProductHitDto> = emptyList(),
    val totalHits: Long = 0,
    val page: Int = 1,
    val totalPages: Int = 1
)

@Serializable
private data class ProductHitDto(
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

@Serializable
private data class ProductResponseDto(
    val id: String,
    val sellerId: String,
    val sku: String,
    val name: String,
    val description: String,
    val price: Double,
    val inventory: Int,
    val published: Boolean,
    val categoryId: String? = null,
    val categoryName: String? = null,
    val imageUrl: String? = null,
    val status: String? = null
)
