package dev.meirong.shop.kmp.feature.auth.data

import dev.meirong.shop.kmp.core.model.AuthSession
import dev.meirong.shop.kmp.core.network.ApiResponse
import dev.meirong.shop.kmp.core.network.HttpClientFactory
import dev.meirong.shop.kmp.core.network.gatewayApiBaseUrl
import dev.meirong.shop.kmp.core.session.NoOpTokenStorage
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import kotlinx.serialization.Serializable

private const val loginPath = "/auth/v1/token/login"
private const val guestPath = "/auth/v1/token/guest"

class AuthRepository(
    private val client: HttpClient = HttpClientFactory.create(NoOpTokenStorage),
    private val baseUrl: String = gatewayApiBaseUrl().removeSuffix("/api")
) {

    suspend fun login(
        username: String,
        password: String,
        portal: String
    ): AuthSession {
        val response = client.post("$baseUrl$loginPath") {
            header(HttpHeaders.ContentType, ContentType.Application.Json.toString())
            setBody(LoginRequestDto(username = username, password = password, portal = portal))
        }.body<ApiResponse<TokenResponseDto>>()

        return response.requireSession().toModel()
    }

    suspend fun loginGuest(portal: String): AuthSession {
        val response = client.post("$baseUrl$guestPath") {
            header(HttpHeaders.ContentType, ContentType.Application.Json.toString())
            setBody(GuestTokenRequestDto(portal = portal))
        }.body<ApiResponse<TokenResponseDto>>()

        return response.requireSession().toModel()
    }

    fun close() {
        client.close()
    }
}

private fun ApiResponse<TokenResponseDto>.requireSession(): TokenResponseDto {
    return data ?: error(message.ifBlank { "Auth response did not include data." })
}

private fun TokenResponseDto.toModel(): AuthSession = AuthSession(
    accessToken = accessToken,
    username = username,
    displayName = displayName,
    principalId = principalId,
    roles = roles,
    portal = portal
)

@Serializable
private data class LoginRequestDto(
    val username: String,
    val password: String,
    val portal: String
)

@Serializable
private data class GuestTokenRequestDto(
    val portal: String
)

@Serializable
private data class TokenResponseDto(
    val accessToken: String,
    val tokenType: String,
    val expiresAt: String,
    val username: String,
    val displayName: String,
    val principalId: String,
    val roles: List<String> = emptyList(),
    val portal: String
)
