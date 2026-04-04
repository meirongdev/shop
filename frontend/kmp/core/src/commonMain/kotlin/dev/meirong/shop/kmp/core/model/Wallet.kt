package dev.meirong.shop.kmp.core.model

import kotlinx.serialization.Serializable

@Serializable
data class WalletAccount(
    val buyerId: String,
    val balanceInCents: Long,
    val updatedAt: String,
    val recentTransactions: List<WalletTransaction>
)

@Serializable
data class WalletTransaction(
    val transactionId: String,
    val buyerId: String,
    val type: String,
    val amountInCents: Long,
    val currency: String,
    val status: String,
    val providerReference: String?,
    val createdAt: String
)
