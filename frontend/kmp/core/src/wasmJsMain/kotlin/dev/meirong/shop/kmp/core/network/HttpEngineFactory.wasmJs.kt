package dev.meirong.shop.kmp.core.network

import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.js.Js

actual fun createHttpEngine(): HttpClientEngine = Js.create()
