package dev.meirong.shop.kmp.core.network

import kotlinx.serialization.Serializable

@Serializable
data class ApiResponse<T>(
    val traceId: String? = null,
    val status: String,
    val message: String,
    val data: T? = null
)
