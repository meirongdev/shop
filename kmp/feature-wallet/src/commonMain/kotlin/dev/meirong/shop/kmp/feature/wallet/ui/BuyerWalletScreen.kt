package dev.meirong.shop.kmp.feature.wallet.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.meirong.shop.kmp.core.model.WalletAccount
import dev.meirong.shop.kmp.core.model.WalletTransaction
import dev.meirong.shop.kmp.feature.wallet.data.BuyerWalletRepository
import dev.meirong.shop.kmp.ui.components.ErrorScreen
import dev.meirong.shop.kmp.ui.components.LoadingIndicator
import dev.meirong.shop.kmp.ui.components.PriceTag
import dev.meirong.shop.kmp.ui.components.formatPriceInCents
import kotlinx.coroutines.launch

@Composable
fun BuyerWalletScreen(
    repository: BuyerWalletRepository,
    buyerId: String,
    onOpenCart: () -> Unit
) {
    var refreshKey by remember { mutableIntStateOf(0) }
    var uiState by remember { mutableStateOf(BuyerWalletUiState()) }
    var isSubmitting by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    suspend fun reloadWallet() {
        uiState = uiState.copy(isLoading = true, errorMessage = null)
        runCatching { repository.getWallet(buyerId) }
            .onSuccess { wallet ->
                uiState = uiState.copy(
                    isLoading = false,
                    wallet = wallet,
                    errorMessage = null
                )
            }
            .onFailure { error ->
                uiState = uiState.copy(
                    isLoading = false,
                    errorMessage = error.message ?: "Failed to load wallet."
                )
            }
    }

    LaunchedEffect(buyerId, refreshKey) {
        reloadWallet()
    }

    if (uiState.isLoading && uiState.wallet == null) {
        LoadingIndicator()
        return
    }

    if (uiState.errorMessage != null && uiState.wallet == null) {
        ErrorScreen(
            message = uiState.errorMessage ?: "Failed to load wallet.",
            onRetry = { refreshKey += 1 }
        )
        return
    }

    val wallet = uiState.wallet ?: return
    fun deposit(amountInCents: Long) {
        scope.launch {
            if (isSubmitting) return@launch
            isSubmitting = true
            uiState = uiState.copy(isLoading = true, actionMessage = null)
            runCatching {
                repository.deposit(buyerId, amountInCents)
                repository.getWallet(buyerId)
            }
                .onSuccess { refreshedWallet ->
                    uiState = uiState.copy(
                        isLoading = false,
                        wallet = refreshedWallet,
                        errorMessage = null,
                        actionMessage = "Deposited $${formatPriceInCents(amountInCents)} into the current buyer wallet."
                    )
                }
                .onFailure { error ->
                    uiState = uiState.copy(
                        isLoading = false,
                        actionMessage = error.message ?: "Wallet deposit failed."
                    )
                }
            isSubmitting = false
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(text = "Buyer Wallet", style = MaterialTheme.typography.headlineSmall)
        Text(
            text = "This wallet is scoped to the current authenticated buyer session.",
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
                uiState.actionMessage?.let { message ->
                    Text(
                        text = message,
                        color = if (message.startsWith("Deposited")) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }
        Text(
            text = "Quick top-ups",
            style = MaterialTheme.typography.titleMedium
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { deposit(1_000) }, enabled = !isSubmitting) {
                Text("+$10")
            }
            OutlinedButton(onClick = { deposit(2_500) }, enabled = !isSubmitting) {
                Text("+$25")
            }
            OutlinedButton(onClick = { deposit(5_000) }, enabled = !isSubmitting) {
                Text("+$50")
            }
        }
        Button(onClick = onOpenCart, enabled = !isSubmitting) {
            Text("Open cart checkout")
        }
        if (wallet.recentTransactions.isEmpty()) {
            Surface(
                tonalElevation = 1.dp,
                shape = MaterialTheme.shapes.medium
            ) {
                Text(
                    text = "No wallet transactions yet. Use a quick top-up, then return to cart to checkout.",
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
                    WalletTransactionCard(transaction = transaction)
                }
            }
        }
    }
}

@Composable
private fun WalletTransactionCard(transaction: WalletTransaction) {
    val isDebit = transaction.type == "WITHDRAW" || transaction.type == "ORDER_PAYMENT"
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

private data class BuyerWalletUiState(
    val isLoading: Boolean = true,
    val wallet: WalletAccount? = null,
    val errorMessage: String? = null,
    val actionMessage: String? = null
)
