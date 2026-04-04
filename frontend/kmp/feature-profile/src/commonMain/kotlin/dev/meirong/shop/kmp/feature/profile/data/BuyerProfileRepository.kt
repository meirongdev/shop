package dev.meirong.shop.kmp.feature.profile.data

import dev.meirong.shop.kmp.core.model.BuyerProfile
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

private const val buyerProfileGetPath = "/buyer/v1/profile/get"
private const val buyerProfileUpdatePath = "/buyer/v1/profile/update"

class BuyerProfileRepository(
    tokenStorage: TokenStorage = NoOpTokenStorage,
    private val client: HttpClient = HttpClientFactory.create(tokenStorage),
    private val baseUrl: String = gatewayApiBaseUrl()
) {

    suspend fun getProfile(buyerId: String): BuyerProfile {
        val response = client.post("$baseUrl$buyerProfileGetPath") {
            header(HttpHeaders.ContentType, ContentType.Application.Json.toString())
            setBody(BuyerProfileContextRequestDto(buyerId = buyerId))
        }.body<ApiResponse<BuyerProfileDto>>()

        return response.requireProfile().toModel()
    }

    suspend fun updateProfile(
        buyerId: String,
        displayName: String,
        email: String,
        tier: String
    ): BuyerProfile {
        val response = client.post("$baseUrl$buyerProfileUpdatePath") {
            header(HttpHeaders.ContentType, ContentType.Application.Json.toString())
            setBody(
                BuyerUpdateProfileRequestDto(
                    buyerId = buyerId,
                    displayName = displayName,
                    email = email,
                    tier = tier
                )
            )
        }.body<ApiResponse<BuyerProfileDto>>()

        return response.requireProfile().toModel()
    }

    fun close() {
        client.close()
    }
}

private fun ApiResponse<BuyerProfileDto>.requireProfile(): BuyerProfileDto {
    return data ?: error(message.ifBlank { "Buyer profile response did not include data." })
}

private fun BuyerProfileDto.toModel(): BuyerProfile = BuyerProfile(
    buyerId = buyerId,
    username = username,
    displayName = displayName,
    email = email,
    tier = tier,
    createdAt = createdAt,
    updatedAt = updatedAt
)

@Serializable
private data class BuyerProfileContextRequestDto(
    val buyerId: String
)

@Serializable
private data class BuyerUpdateProfileRequestDto(
    val buyerId: String,
    val displayName: String,
    val email: String,
    val tier: String
)

@Serializable
private data class BuyerProfileDto(
    val buyerId: String,
    val username: String,
    val displayName: String,
    val email: String,
    val tier: String,
    val createdAt: String,
    val updatedAt: String
)
