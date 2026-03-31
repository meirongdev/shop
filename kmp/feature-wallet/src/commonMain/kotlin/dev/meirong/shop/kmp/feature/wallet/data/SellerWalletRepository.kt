package dev.meirong.shop.kmp.feature.wallet.data

import dev.meirong.shop.kmp.core.model.WalletAccount
import dev.meirong.shop.kmp.core.model.WalletTransaction
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
import kotlin.math.roundToLong
import kotlinx.serialization.Serializable

private const val sellerWalletGetPath = "/seller/v1/wallet/get"

class SellerWalletRepository(
    tokenStorage: TokenStorage = NoOpTokenStorage,
    private val client: HttpClient = HttpClientFactory.create(tokenStorage),
    private val baseUrl: String = gatewayApiBaseUrl()
) {

    suspend fun getWallet(sellerId: String): WalletAccount {
        val response = client.post("$baseUrl$sellerWalletGetPath") {
            header(HttpHeaders.ContentType, ContentType.Application.Json.toString())
            setBody(SellerContextRequestDto(sellerId = sellerId))
        }.body<ApiResponse<SellerWalletAccountDto>>()

        return response.requireWallet().toModel()
    }

    fun close() {
        client.close()
    }
}

private fun ApiResponse<SellerWalletAccountDto>.requireWallet(): SellerWalletAccountDto {
    return data ?: error(message.ifBlank { "Seller wallet response did not include data." })
}

private fun SellerWalletAccountDto.toModel(): WalletAccount = WalletAccount(
    buyerId = buyerId,
    balanceInCents = (balance * 100.0).roundToLong(),
    updatedAt = updatedAt,
    recentTransactions = recentTransactions.map { it.toModel() }
)

private fun SellerWalletTransactionDto.toModel(): WalletTransaction = WalletTransaction(
    transactionId = transactionId,
    buyerId = buyerId,
    type = type,
    amountInCents = (amount * 100.0).roundToLong(),
    currency = currency,
    status = status,
    providerReference = providerReference,
    createdAt = createdAt
)

@Serializable
private data class SellerContextRequestDto(
    val sellerId: String
)

@Serializable
private data class SellerWalletAccountDto(
    val buyerId: String,
    val balance: Double,
    val updatedAt: String,
    val recentTransactions: List<SellerWalletTransactionDto> = emptyList()
)

@Serializable
private data class SellerWalletTransactionDto(
    val transactionId: String,
    val buyerId: String,
    val type: String,
    val amount: Double,
    val currency: String,
    val status: String,
    val providerReference: String? = null,
    val createdAt: String
)
