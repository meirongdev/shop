package dev.meirong.shop.kmp.feature.marketplace.data

import dev.meirong.shop.kmp.core.model.Product
import dev.meirong.shop.kmp.core.model.SellerDashboard
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

private const val sellerDashboardPath = "/seller/v1/dashboard/get"
private const val productCreatePath = "/seller/v1/product/create"
private const val productUpdatePath = "/seller/v1/product/update"

class SellerDashboardRepository(
    tokenStorage: TokenStorage = NoOpTokenStorage,
    private val client: HttpClient = HttpClientFactory.create(tokenStorage),
    private val baseUrl: String = gatewayApiBaseUrl()
) {

    suspend fun loadDashboard(sellerId: String): SellerDashboard {
        val response = client.post("$baseUrl$sellerDashboardPath") {
            header(HttpHeaders.ContentType, ContentType.Application.Json.toString())
            setBody(SellerContextRequestDto(sellerId = sellerId))
        }.body<ApiResponse<SellerDashboardDto>>()

        return response.requireDashboard().toModel()
    }

    suspend fun createProduct(
        sellerId: String,
        sku: String,
        name: String,
        description: String,
        price: Double,
        inventory: Int,
        published: Boolean
    ): Product {
        val response = client.post("$baseUrl$productCreatePath") {
            header(HttpHeaders.ContentType, ContentType.Application.Json.toString())
            setBody(UpsertProductRequestDto(
                sellerId = sellerId,
                sku = sku,
                name = name,
                description = description,
                price = price,
                inventory = inventory,
                published = published
            ))
        }.body<ApiResponse<ProductDto>>()

        return response.requireProduct().toModel()
    }

    suspend fun updateProduct(
        productId: String,
        sellerId: String,
        sku: String,
        name: String,
        description: String,
        price: Double,
        inventory: Int,
        published: Boolean
    ): Product {
        val response = client.post("$baseUrl$productUpdatePath") {
            header(HttpHeaders.ContentType, ContentType.Application.Json.toString())
            setBody(UpsertProductRequestDto(
                productId = productId,
                sellerId = sellerId,
                sku = sku,
                name = name,
                description = description,
                price = price,
                inventory = inventory,
                published = published
            ))
        }.body<ApiResponse<ProductDto>>()

        return response.requireProduct().toModel()
    }

    fun close() {
        client.close()
    }
}

private fun ApiResponse<ProductDto>.requireProduct(): ProductDto {
    return data ?: error(message.ifBlank { "Product response did not include data." })
}

private fun ApiResponse<SellerDashboardDto>.requireDashboard(): SellerDashboardDto {
    return data ?: error(message.ifBlank { "Seller dashboard response did not include data." })
}

private fun SellerDashboardDto.toModel(): SellerDashboard = SellerDashboard(
    productCount = productCount,
    activePromotionCount = activePromotionCount,
    products = products.map { it.toModel() }
)

private fun ProductDto.toModel(): Product = Product(
    id = id,
    sellerId = sellerId,
    sku = sku,
    name = name,
    description = description,
    priceInCents = (price * 100.0).roundToLong(),
    inventory = inventory ?: 0,
    published = published,
    categoryId = categoryId,
    categoryName = categoryName,
    imageUrl = imageUrl,
    status = status
)

@Serializable
private data class SellerContextRequestDto(
    val sellerId: String
)

@Serializable
private data class SellerDashboardDto(
    val productCount: Long,
    val activePromotionCount: Long,
    val products: List<ProductDto> = emptyList()
)

@Serializable
private data class UpsertProductRequestDto(
    val productId: String? = null,
    val sellerId: String,
    val sku: String,
    val name: String,
    val description: String,
    val price: Double,
    val inventory: Int,
    val published: Boolean
)

@Serializable
private data class ProductDto(
    val id: String,
    val sellerId: String,
    val sku: String,
    val name: String,
    val description: String,
    val price: Double,
    val inventory: Int? = null,
    val published: Boolean,
    val categoryId: String? = null,
    val categoryName: String? = null,
    val imageUrl: String? = null,
    val status: String? = null
)
