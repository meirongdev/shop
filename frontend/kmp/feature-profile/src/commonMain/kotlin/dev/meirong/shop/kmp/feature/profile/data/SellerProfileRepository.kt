package dev.meirong.shop.kmp.feature.profile.data

import dev.meirong.shop.kmp.core.model.SellerProfile
import dev.meirong.shop.kmp.core.model.SellerStorefront
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
import kotlinx.serialization.Serializable

private const val sellerProfileGetPath = "/seller/v1/profile/get"
private const val sellerProfileUpdatePath = "/seller/v1/profile/update"
private const val sellerShopGetPath = "/seller/v1/shop/get"
private const val sellerShopUpdatePath = "/seller/v1/shop/update"

class SellerProfileRepository(
    tokenStorage: TokenStorage = NoOpTokenStorage,
    private val client: HttpClient = HttpClientFactory.create(tokenStorage),
    private val baseUrl: String = gatewayApiBaseUrl()
) {

    suspend fun getProfile(sellerId: String): SellerProfile {
        val response = client.post("$baseUrl$sellerProfileGetPath") {
            header(HttpHeaders.ContentType, ContentType.Application.Json.toString())
            setBody(SellerProfileContextRequestDto(sellerId = sellerId))
        }.body<ApiResponse<SellerProfileDto>>()

        return response.requireProfile().toModel()
    }

    suspend fun updateProfile(
        sellerId: String,
        displayName: String,
        email: String,
        tier: String
    ): SellerProfile {
        val response = client.post("$baseUrl$sellerProfileUpdatePath") {
            header(HttpHeaders.ContentType, ContentType.Application.Json.toString())
            setBody(
                SellerUpdateProfileRequestDto(
                    buyerId = sellerId,
                    displayName = displayName,
                    email = email,
                    tier = tier
                )
            )
        }.body<ApiResponse<SellerProfileDto>>()

        return response.requireProfile().toModel()
    }

    fun close() {
        client.close()
    }

    suspend fun getShop(sellerId: String): SellerStorefront {
        val response = client.post("$baseUrl$sellerShopGetPath") {
            header(HttpHeaders.ContentType, ContentType.Application.Json.toString())
            setBody(SellerProfileContextRequestDto(sellerId = sellerId))
        }.body<ApiResponse<SellerStorefrontDto>>()

        return response.requireStorefront().toModel()
    }

    suspend fun updateShop(
        sellerId: String,
        shopName: String?,
        shopSlug: String?,
        shopDescription: String?,
        logoUrl: String?,
        bannerUrl: String?
    ): SellerStorefront {
        val response = client.post("$baseUrl$sellerShopUpdatePath") {
            header(HttpHeaders.ContentType, ContentType.Application.Json.toString())
            setBody(
                UpdateShopRequestDto(
                    sellerId = sellerId,
                    shopName = shopName,
                    shopSlug = shopSlug,
                    shopDescription = shopDescription,
                    logoUrl = logoUrl,
                    bannerUrl = bannerUrl
                )
            )
        }.body<ApiResponse<SellerStorefrontDto>>()

        return response.requireStorefront().toModel()
    }
}

private fun ApiResponse<SellerProfileDto>.requireProfile(): SellerProfileDto {
    return data ?: error(message.ifBlank { "Seller profile response did not include data." })
}

private fun SellerProfileDto.toModel(): SellerProfile = SellerProfile(
    sellerId = buyerId,
    username = username,
    displayName = displayName,
    email = email,
    tier = tier,
    createdAt = createdAt,
    updatedAt = updatedAt
)

@Serializable
private data class SellerProfileContextRequestDto(
    val sellerId: String
)

@Serializable
private data class SellerUpdateProfileRequestDto(
    val buyerId: String,
    val displayName: String,
    val email: String,
    val tier: String
)

private fun ApiResponse<SellerStorefrontDto>.requireStorefront(): SellerStorefrontDto {
    return data ?: error(message.ifBlank { "Seller storefront response did not include data." })
}

private fun SellerStorefrontDto.toModel(): SellerStorefront = SellerStorefront(
    sellerId = sellerId,
    username = username,
    displayName = displayName,
    shopName = shopName,
    shopSlug = shopSlug,
    shopDescription = shopDescription,
    logoUrl = logoUrl,
    bannerUrl = bannerUrl,
    avgRating = avgRating,
    totalSales = totalSales,
    tier = tier,
    createdAt = createdAt
)

@Serializable
private data class SellerProfileDto(
    val buyerId: String,
    val username: String,
    val displayName: String,
    val email: String,
    val tier: String,
    val createdAt: String,
    val updatedAt: String
)

@Serializable
private data class UpdateShopRequestDto(
    val sellerId: String,
    val shopName: String? = null,
    val shopSlug: String? = null,
    val shopDescription: String? = null,
    val logoUrl: String? = null,
    val bannerUrl: String? = null
)

@Serializable
private data class SellerStorefrontDto(
    val sellerId: String,
    val username: String = "",
    val displayName: String = "",
    val shopName: String? = null,
    val shopSlug: String? = null,
    val shopDescription: String? = null,
    val logoUrl: String? = null,
    val bannerUrl: String? = null,
    val avgRating: Double = 0.0,
    val totalSales: Int = 0,
    val tier: String = "",
    val createdAt: String = ""
)
