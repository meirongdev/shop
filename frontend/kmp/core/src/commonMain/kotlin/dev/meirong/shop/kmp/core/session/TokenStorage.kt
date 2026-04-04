package dev.meirong.shop.kmp.core.session

import io.ktor.client.HttpClient
import io.ktor.client.plugins.auth.providers.BearerTokens

interface TokenStorage {
    suspend fun loadTokens(): BearerTokens?
    suspend fun refreshTokens(client: HttpClient): BearerTokens?
    suspend fun saveTokens(access: String, refresh: String)
    suspend fun clear()
}
