package dev.meirong.shop.kmp.core.session

import io.ktor.client.HttpClient
import io.ktor.client.plugins.auth.providers.BearerTokens

object NoOpTokenStorage : TokenStorage {
    override suspend fun loadTokens(): BearerTokens? = null

    override suspend fun refreshTokens(client: HttpClient): BearerTokens? = null

    override suspend fun saveTokens(access: String, refresh: String) = Unit

    override suspend fun clear() = Unit
}
