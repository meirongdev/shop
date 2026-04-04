package dev.meirong.shop.kmp.core.network

import dev.meirong.shop.kmp.core.session.TokenStorage
import io.ktor.client.HttpClient
import io.ktor.client.plugins.auth.providers.BearerTokens
import kotlin.test.Test
import kotlin.test.assertNotNull

class HttpClientFactoryTest {

    @Test
    fun shouldCreateHttpClient() {
        val storage = object : TokenStorage {
            override suspend fun loadTokens(): BearerTokens? = null
            override suspend fun refreshTokens(client: HttpClient): BearerTokens? = null
            override suspend fun saveTokens(access: String, refresh: String) = Unit
            override suspend fun clear() = Unit
        }
        val client = HttpClientFactory.create(storage)
        assertNotNull(client)
        client.close()
    }
}
