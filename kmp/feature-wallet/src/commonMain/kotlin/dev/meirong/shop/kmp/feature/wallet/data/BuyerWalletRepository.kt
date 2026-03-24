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

private const val buyerWalletGetPath = "/buyer/v1/wallet/get"
private const val buyerWalletDepositPath = "/buyer/v1/wallet/deposit"

class BuyerWalletRepository(
    tokenStorage: TokenStorage = NoOpTokenStorage,
    private val client: HttpClient = HttpClientFactory.create(tokenStorage),
    private val baseUrl: String = gatewayApiBaseUrl()
) {

    suspend fun getWallet(buyerId: String): WalletAccount {
        val response = client.post("$baseUrl$buyerWalletGetPath") {
            header(HttpHeaders.ContentType, ContentType.Application.Json.toString())
            setBody(BuyerContextRequestDto(playerId = buyerId))
        }.body<ApiResponse<WalletAccountDto>>()

        return response.requireWallet().toModel()
    }

    suspend fun deposit(
        buyerId: String,
        amountInCents: Long,
        currency: String = "usd"
    ): WalletTransaction {
        val response = client.post("$baseUrl$buyerWalletDepositPath") {
            header(HttpHeaders.ContentType, ContentType.Application.Json.toString())
            setBody(
                DepositRequestDto(
                    playerId = buyerId,
                    amount = amountInCents.toDouble() / 100.0,
                    currency = currency
                )
            )
        }.body<ApiResponse<WalletTransactionDto>>()

        return response.requireTransaction().toModel()
    }

    fun close() {
        client.close()
    }
}

private fun ApiResponse<WalletAccountDto>.requireWallet(): WalletAccountDto {
    return data ?: error(message.ifBlank { "Wallet response did not include data." })
}

private fun ApiResponse<WalletTransactionDto>.requireTransaction(): WalletTransactionDto {
    return data ?: error(message.ifBlank { "Wallet transaction response did not include data." })
}

private fun WalletAccountDto.toModel(): WalletAccount = WalletAccount(
    playerId = playerId,
    balanceInCents = (balance * 100.0).roundToLong(),
    updatedAt = updatedAt,
    recentTransactions = recentTransactions.map { it.toModel() }
)

private fun WalletTransactionDto.toModel(): WalletTransaction = WalletTransaction(
    transactionId = transactionId,
    playerId = playerId,
    type = type,
    amountInCents = (amount * 100.0).roundToLong(),
    currency = currency,
    status = status,
    providerReference = providerReference,
    createdAt = createdAt
)

@Serializable
private data class BuyerContextRequestDto(
    val playerId: String
)

@Serializable
private data class DepositRequestDto(
    val playerId: String,
    val amount: Double,
    val currency: String
)

@Serializable
private data class WalletAccountDto(
    val playerId: String,
    val balance: Double,
    val updatedAt: String,
    val recentTransactions: List<WalletTransactionDto> = emptyList()
)

@Serializable
private data class WalletTransactionDto(
    val transactionId: String,
    val playerId: String,
    val type: String,
    val amount: Double,
    val currency: String,
    val status: String,
    val providerReference: String? = null,
    val createdAt: String
)
