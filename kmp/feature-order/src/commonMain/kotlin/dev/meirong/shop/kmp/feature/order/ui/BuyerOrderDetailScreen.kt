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
import dev.meirong.shop.kmp.feature.order.data.BuyerOrderRepository
import dev.meirong.shop.kmp.ui.components.ErrorScreen
import dev.meirong.shop.kmp.ui.components.LoadingIndicator
import dev.meirong.shop.kmp.ui.components.formatPriceInCents
import kotlinx.coroutines.launch

@Composable
fun BuyerOrderDetailScreen(
    orderId: String,
    buyerId: String,
    repository: BuyerOrderRepository,
    onBack: () -> Unit
) {
    var refreshKey by remember { mutableIntStateOf(0) }
    var order by remember { mutableStateOf<ShopOrder?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var actionMessage by remember { mutableStateOf<String?>(null) }
    var isCancelling by remember { mutableStateOf(false) }
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
                errorMessage = error.message ?: "Failed to load order details."
                isLoading = false
            }
    }

    if (isLoading && order == null) {
        LoadingIndicator()
        return
    }

    if (errorMessage != null && order == null) {
        ErrorScreen(
            message = errorMessage ?: "Failed to load order details.",
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
            text = "Order ${currentOrder.id.take(8)}",
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
                Text(text = "Status: ${currentOrder.status}", style = MaterialTheme.typography.titleMedium)
                Text(text = "Seller: ${currentOrder.sellerId}", style = MaterialTheme.typography.bodyMedium)
                Text(text = "Subtotal: $${formatPriceInCents(currentOrder.subtotalInCents)}", style = MaterialTheme.typography.bodyMedium)
                Text(text = "Discount: $${formatPriceInCents(currentOrder.discountAmountInCents)}", style = MaterialTheme.typography.bodyMedium)
                Text(text = "Total: $${formatPriceInCents(currentOrder.totalAmountInCents)}", style = MaterialTheme.typography.bodyLarge)
                Text(text = "Updated: ${currentOrder.updatedAt.take(19)}", style = MaterialTheme.typography.bodySmall)
                currentOrder.couponCode?.let { couponCode ->
                    Text(text = "Coupon: $couponCode", style = MaterialTheme.typography.bodySmall)
                }
            }
        }
        if (currentOrder.status == "PAID") {
            Button(
                onClick = {
                    scope.launch {
                        isCancelling = true
                        actionMessage = null
                        runCatching { repository.cancelOrder(buyerId, currentOrder.id) }
                            .onSuccess {
                                order = it
                                actionMessage = "Order cancelled for this buyer session."
                            }
                            .onFailure { error ->
                                actionMessage = error.message ?: "Failed to cancel order."
                            }
                        isCancelling = false
                    }
                },
                enabled = !isCancelling
            ) {
                Text(if (isCancelling) "Cancelling..." else "Cancel order")
            }
        }
        actionMessage?.let { message ->
            Text(
                text = message,
                color = if (message.startsWith("Order cancelled")) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium
            )
        }
        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(currentOrder.items, key = { it.id }) { item ->
                OrderLineItemCard(item = item)
            }
        }
    }
}

@Composable
private fun OrderLineItemCard(item: OrderLineItem) {
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
