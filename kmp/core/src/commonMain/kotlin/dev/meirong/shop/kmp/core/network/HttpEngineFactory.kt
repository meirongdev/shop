package dev.meirong.shop.kmp.core.network

import io.ktor.client.engine.HttpClientEngine

expect fun createHttpEngine(): HttpClientEngine
