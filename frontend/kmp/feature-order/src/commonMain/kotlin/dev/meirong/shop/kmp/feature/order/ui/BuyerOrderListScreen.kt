package dev.meirong.shop.kmp.feature.order.ui

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
import dev.meirong.shop.kmp.core.model.ShopOrder
import dev.meirong.shop.kmp.feature.order.data.BuyerOrderRepository
import dev.meirong.shop.kmp.ui.components.ErrorScreen
import dev.meirong.shop.kmp.ui.components.LoadingIndicator
import dev.meirong.shop.kmp.ui.components.formatPriceInCents

@Composable
fun BuyerOrderListScreen(
    repository: BuyerOrderRepository,
    buyerId: String,
    onOrderSelected: (String) -> Unit
) {
    var refreshKey by remember { mutableIntStateOf(0) }
    var uiState by remember { mutableStateOf(BuyerOrdersUiState()) }

    LaunchedEffect(buyerId, refreshKey) {
        uiState = uiState.copy(isLoading = true, errorMessage = null)
        runCatching { repository.listOrders(buyerId) }
            .onSuccess { orders ->
                uiState = uiState.copy(
                    isLoading = false,
                    orders = orders,
                    errorMessage = null
                )
            }
            .onFailure { error ->
                uiState = uiState.copy(
                    isLoading = false,
                    errorMessage = error.message ?: "Failed to load buyer orders."
                )
            }
    }

    if (uiState.isLoading && uiState.orders.isEmpty()) {
        LoadingIndicator()
        return
    }

    if (uiState.errorMessage != null && uiState.orders.isEmpty()) {
        ErrorScreen(
            message = uiState.errorMessage ?: "Failed to load buyer orders.",
            onRetry = { refreshKey += 1 }
        )
        return
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(text = "Buyer Orders", style = MaterialTheme.typography.headlineSmall)
        Text(
            text = "Order history is scoped to the current authenticated buyer session.",
            style = MaterialTheme.typography.bodyMedium
        )
        if (uiState.isLoading) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        }
        if (uiState.orders.isEmpty()) {
            Surface(
                tonalElevation = 1.dp,
                shape = MaterialTheme.shapes.medium
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "No orders yet.",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        text = "Once checkout is wired for the same session, created orders will appear here.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Button(onClick = { refreshKey += 1 }) {
                        Text("Refresh")
                    }
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(uiState.orders, key = { it.id }) { order ->
                    OrderSummaryCard(
                        order = order,
                        onOpen = { onOrderSelected(order.id) }
                    )
                }
            }
        }
    }
}

@Composable
private fun OrderSummaryCard(
    order: ShopOrder,
    onOpen: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        onClick = onOpen
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = "Order ${order.id.take(8)}",
                style = MaterialTheme.typography.titleMedium
            )
            Text(
                text = "Status: ${order.status}",
                style = MaterialTheme.typography.bodyMedium
            )
            Text(
                text = "Items: ${order.items.sumOf { it.quantity }}",
                style = MaterialTheme.typography.bodyMedium
            )
            Text(
                text = "Total: $${formatPriceInCents(order.totalAmountInCents)}",
                style = MaterialTheme.typography.bodyMedium
            )
            Text(
                text = "Updated: ${order.updatedAt.take(19)}",
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}

private data class BuyerOrdersUiState(
    val isLoading: Boolean = true,
    val orders: List<ShopOrder> = emptyList(),
    val errorMessage: String? = null
)
