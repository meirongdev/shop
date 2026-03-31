package dev.meirong.shop.kmp.feature.profile.data

import dev.meirong.shop.kmp.core.model.SellerProfile
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
