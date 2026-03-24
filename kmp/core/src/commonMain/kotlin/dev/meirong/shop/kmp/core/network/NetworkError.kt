package dev.meirong.shop.kmp.core.network

sealed interface NetworkError {
    data object Unauthorized : NetworkError
    data object Forbidden : NetworkError
    data object NotFound : NetworkError
    data class Server(val code: Int, val message: String? = null) : NetworkError
    data class Unknown(val throwable: Throwable? = null) : NetworkError
}
