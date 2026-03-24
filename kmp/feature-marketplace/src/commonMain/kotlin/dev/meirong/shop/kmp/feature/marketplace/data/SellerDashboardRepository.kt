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

    fun close() {
        client.close()
    }
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
