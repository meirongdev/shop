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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.meirong.shop.kmp.core.model.OrderLineItem
import dev.meirong.shop.kmp.core.model.ShopOrder
import dev.meirong.shop.kmp.feature.order.data.SellerOrderRepository
import dev.meirong.shop.kmp.ui.components.ErrorScreen
import dev.meirong.shop.kmp.ui.components.LoadingIndicator
import dev.meirong.shop.kmp.ui.components.formatPriceInCents
import kotlinx.coroutines.launch

@Composable
fun SellerOrderDetailScreen(
    orderId: String,
    repository: SellerOrderRepository,
    onBack: () -> Unit
) {
    var refreshKey by remember { mutableIntStateOf(0) }
    var order by remember { mutableStateOf<ShopOrder?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var actionMessage by remember { mutableStateOf<String?>(null) }
    var isSubmitting by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(orderId, refreshKey) {
        isLoading = true
        errorMessage = null
        runCatching { repository.getOrder(orderId) }
            .onSuccess {
                order = it
                isLoading = false
            }
            .onFailure { error ->
                errorMessage = error.message ?: "Failed to load seller order details."
                isLoading = false
            }
    }

    if (isLoading && order == null) {
        LoadingIndicator()
        return
    }

    if (errorMessage != null && order == null) {
        ErrorScreen(
            message = errorMessage ?: "Failed to load seller order details.",
            onRetry = { refreshKey += 1 }
        )
        return
    }

    val currentOrder = order ?: return
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Button(onClick = onBack) {
            Text("Back to orders")
        }
        Text(
            text = "Seller Order ${currentOrder.id.take(8)}",
            style = MaterialTheme.typography.headlineSmall
        )
        if (isLoading) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        }
        Surface(
            tonalElevation = 2.dp,
            shape = MaterialTheme.shapes.medium
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(text = "Buyer: ${currentOrder.buyerId}", style = MaterialTheme.typography.bodyMedium)
                Text(text = "Status: ${currentOrder.status}", style = MaterialTheme.typography.titleMedium)
                Text(text = "Subtotal: $${formatPriceInCents(currentOrder.subtotalInCents)}", style = MaterialTheme.typography.bodyMedium)
                Text(text = "Discount: $${formatPriceInCents(currentOrder.discountAmountInCents)}", style = MaterialTheme.typography.bodyMedium)
                Text(text = "Total: $${formatPriceInCents(currentOrder.totalAmountInCents)}", style = MaterialTheme.typography.bodyLarge)
            }
        }
        sellerAction(currentOrder.status)?.let { action ->
            Button(
                onClick = {
                    scope.launch {
                        isSubmitting = true
                        actionMessage = null
                        runCatching {
                            when (action) {
                                SellerOrderAction.SHIP -> repository.shipOrder(currentOrder.id)
                                SellerOrderAction.DELIVER -> repository.deliverOrder(currentOrder.id)
                            }
                        }
                            .onSuccess {
                                order = it
                                actionMessage = when (action) {
                                    SellerOrderAction.SHIP -> "Order marked as shipped."
                                    SellerOrderAction.DELIVER -> "Order marked as delivered."
                                }
                            }
                            .onFailure { error ->
                                actionMessage = error.message ?: "Failed to update seller order."
                            }
                        isSubmitting = false
                    }
                },
                enabled = !isSubmitting
            ) {
                Text(
                    when {
                        isSubmitting -> "Updating..."
                        action == SellerOrderAction.SHIP -> "Mark shipped"
                        else -> "Mark delivered"
                    }
                )
            }
        }
        actionMessage?.let { message ->
            Text(
                text = message,
                color = if (message.startsWith("Order marked")) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium
            )
        }
        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(currentOrder.items, key = { it.id }) { item ->
                SellerOrderLineItemCard(item = item)
            }
        }
    }
}

private fun sellerAction(status: String): SellerOrderAction? = when (status) {
    "PAID" -> SellerOrderAction.SHIP
    "SHIPPED" -> SellerOrderAction.DELIVER
    else -> null
}

private enum class SellerOrderAction {
    SHIP,
    DELIVER
}

@Composable
private fun SellerOrderLineItemCard(item: OrderLineItem) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(text = item.productName, style = MaterialTheme.typography.titleMedium)
            Text(text = "Quantity: ${item.quantity}", style = MaterialTheme.typography.bodyMedium)
            Text(text = "Unit price: $${formatPriceInCents(item.productPriceInCents)}", style = MaterialTheme.typography.bodyMedium)
            Text(text = "Line total: $${formatPriceInCents(item.lineTotalInCents)}", style = MaterialTheme.typography.bodyMedium)
        }
    }
}
