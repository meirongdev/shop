package dev.meirong.shop.kmp.feature.wallet.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.meirong.shop.kmp.core.model.WalletAccount
import dev.meirong.shop.kmp.core.model.WalletTransaction
import dev.meirong.shop.kmp.feature.wallet.data.SellerWalletRepository
import dev.meirong.shop.kmp.ui.components.ErrorScreen
import dev.meirong.shop.kmp.ui.components.LoadingIndicator
import dev.meirong.shop.kmp.ui.components.PriceTag
import dev.meirong.shop.kmp.ui.components.formatPriceInCents

@Composable
fun SellerWalletScreen(
    repository: SellerWalletRepository,
    sellerId: String,
    onE2eStateChanged: (String, String?) -> Unit = { _, _ -> }
) {
    var refreshKey by remember { mutableIntStateOf(0) }
    var uiState by remember { mutableStateOf(SellerWalletUiState()) }

    LaunchedEffect(sellerId, refreshKey) {
        onE2eStateChanged("loading", null)
        uiState = uiState.copy(isLoading = true, errorMessage = null)
        runCatching { repository.getWallet(sellerId) }
            .onSuccess { wallet ->
                uiState = uiState.copy(
                    isLoading = false,
                    wallet = wallet,
                    errorMessage = null
                )
                onE2eStateChanged("ready", null)
            }
            .onFailure { error ->
                uiState = uiState.copy(
                    isLoading = false,
                    errorMessage = error.message ?: "Failed to load seller wallet."
                )
                onE2eStateChanged("error", uiState.errorMessage)
            }
    }

    if (uiState.isLoading && uiState.wallet == null) {
        LoadingIndicator()
        return
    }

    if (uiState.errorMessage != null && uiState.wallet == null) {
        ErrorScreen(
            message = uiState.errorMessage ?: "Failed to load seller wallet.",
            onRetry = { refreshKey += 1 }
        )
        return
    }

    val wallet = uiState.wallet ?: return
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(text = "Seller Wallet", style = MaterialTheme.typography.headlineSmall)
        Text(
            text = "Live seller balance now comes from wallet-service through seller-bff. Settlement payout actions are still a later backend slice.",
            style = MaterialTheme.typography.bodyMedium
        )
        if (uiState.isLoading) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        }
        Surface(
            tonalElevation = 2.dp,
            shape = MaterialTheme.shapes.medium
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(text = "Available balance", style = MaterialTheme.typography.titleMedium)
                PriceTag(price = formatPriceInCents(wallet.balanceInCents))
                Text(
                    text = "Updated: ${wallet.updatedAt.take(19)}",
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
        Button(onClick = { refreshKey += 1 }, enabled = !uiState.isLoading) {
            Text("Refresh wallet")
        }
        if (wallet.recentTransactions.isEmpty()) {
            Surface(
                tonalElevation = 1.dp,
                shape = MaterialTheme.shapes.medium
            ) {
                Text(
                    text = "No wallet transactions yet. Seller settlement and payout actions are not exposed yet, so this view is currently read-only.",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(16.dp)
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(wallet.recentTransactions, key = { it.transactionId }) { transaction ->
                    SellerWalletTransactionCard(transaction = transaction)
                }
            }
        }
    }
}

@Composable
private fun SellerWalletTransactionCard(transaction: WalletTransaction) {
    val isDebit = transaction.type == "WITHDRAW"
    val signedAmount = if (isDebit) {
        "-$${formatPriceInCents(transaction.amountInCents)}"
    } else {
        "+$${formatPriceInCents(transaction.amountInCents)}"
    }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(text = transaction.type, style = MaterialTheme.typography.titleMedium)
            Text(text = signedAmount, style = MaterialTheme.typography.bodyLarge)
            Text(text = "Status: ${transaction.status}", style = MaterialTheme.typography.bodyMedium)
            Text(text = "Created: ${transaction.createdAt.take(19)}", style = MaterialTheme.typography.bodySmall)
        }
    }
}

private data class SellerWalletUiState(
    val isLoading: Boolean = true,
    val wallet: WalletAccount? = null,
    val errorMessage: String? = null
)
