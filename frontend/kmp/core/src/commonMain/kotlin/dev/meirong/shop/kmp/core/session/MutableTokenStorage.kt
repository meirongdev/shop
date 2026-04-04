package dev.meirong.shop.kmp.core.session

import io.ktor.client.HttpClient
import io.ktor.client.plugins.auth.providers.BearerTokens
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class MutableTokenStorage : TokenStorage {
    private val lock = Mutex()
    private var currentTokens: BearerTokens? = null

    override suspend fun loadTokens(): BearerTokens? = lock.withLock { currentTokens }

    override suspend fun refreshTokens(client: HttpClient): BearerTokens? = lock.withLock { currentTokens }

    override suspend fun saveTokens(access: String, refresh: String) {
        lock.withLock {
            currentTokens = BearerTokens(access, refresh)
        }
    }

    override suspend fun clear() {
        lock.withLock {
            currentTokens = null
        }
    }
}
