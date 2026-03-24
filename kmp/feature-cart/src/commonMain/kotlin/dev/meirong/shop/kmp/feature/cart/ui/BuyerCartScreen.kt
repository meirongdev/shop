package dev.meirong.shop.kmp.feature.cart.ui

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
import androidx.compose.material3.OutlinedTextField
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
import dev.meirong.shop.kmp.core.model.CartItem
import dev.meirong.shop.kmp.core.model.ShoppingCart
import dev.meirong.shop.kmp.feature.cart.data.BuyerCartRepository
import dev.meirong.shop.kmp.ui.components.ErrorScreen
import dev.meirong.shop.kmp.ui.components.LoadingIndicator
import dev.meirong.shop.kmp.ui.components.PriceTag
import dev.meirong.shop.kmp.ui.components.formatPriceInCents
import kotlinx.coroutines.launch

@Composable
fun BuyerCartScreen(
    repository: BuyerCartRepository,
    buyerId: String,
    allowCheckout: Boolean,
    onBrowseMarketplace: () -> Unit,
    onOpenWallet: () -> Unit,
    onRequireSignedInBuyer: () -> Unit,
    onCheckoutSuccess: () -> Unit
) {
    var refreshKey by remember { mutableIntStateOf(0) }
    var uiState by remember { mutableStateOf(BuyerCartUiState()) }
    var pendingProductId by remember { mutableStateOf<String?>(null) }
    var isMutating by remember { mutableStateOf(false) }
    var couponCode by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()

    suspend fun reloadCart() {
        uiState = uiState.copy(isLoading = true, errorMessage = null)
        runCatching { repository.listCart(buyerId) }
            .onSuccess { cart ->
                uiState = uiState.copy(
                    isLoading = false,
                    cart = cart,
                    errorMessage = null
                )
            }
            .onFailure { error ->
                uiState = uiState.copy(
                    isLoading = false,
                    errorMessage = error.message ?: "Failed to load your cart."
                )
            }
    }

    LaunchedEffect(buyerId, refreshKey) {
        reloadCart()
    }

    if (uiState.isLoading && uiState.cart.items.isEmpty()) {
        LoadingIndicator()
        return
    }

    if (uiState.errorMessage != null && uiState.cart.items.isEmpty()) {
        ErrorScreen(
            message = uiState.errorMessage ?: "Failed to load your cart.",
            onRetry = { refreshKey += 1 }
        )
        return
    }

    fun mutateCart(productId: String, action: suspend () -> Unit) {
        scope.launch {
            if (isMutating) return@launch
            isMutating = true
            pendingProductId = productId
            uiState = uiState.copy(isLoading = true, actionMessage = null)
            runCatching {
                action()
                repository.listCart(buyerId)
            }
                .onSuccess { cart ->
                    uiState = uiState.copy(
                        isLoading = false,
                        cart = cart,
                        errorMessage = null,
                        actionMessage = "Cart updated for this buyer session."
                    )
                }
                .onFailure { error ->
                    uiState = uiState.copy(
                        isLoading = false,
                        actionMessage = error.message ?: "Cart update failed."
                    )
                }
            pendingProductId = null
            isMutating = false
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(text = "Buyer Cart", style = MaterialTheme.typography.headlineSmall)
        Text(
            text = if (allowCheckout) {
                "This cart is scoped to the current authenticated buyer session."
            } else {
                "Guest buyers can build a cart, but wallet funding and checkout still require a signed-in buyer account."
            },
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
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = "${uiState.cart.items.size} line items",
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    text = "Subtotal: $${formatPriceInCents(uiState.cart.subtotalInCents)}",
                    style = MaterialTheme.typography.bodyLarge
                )
                Text(
                    text = if (allowCheckout) {
                        "Top up your wallet before checkout, then this buyer cart can create real orders."
                    } else {
                        "Sign in with a buyer account when you're ready to fund the wallet and complete checkout."
                    },
                    style = MaterialTheme.typography.bodySmall
                )
                uiState.actionMessage?.let { message ->
                    Text(
                        text = message,
                        style = MaterialTheme.typography.bodySmall,
                        color = if (message.startsWith("Cart updated") || message.startsWith("Checkout paid")) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.error
                        }
                    )
                }
                if (allowCheckout) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = couponCode,
                            onValueChange = { couponCode = it },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text("Coupon code (optional)") },
                            singleLine = true,
                            enabled = !isMutating
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(onClick = onOpenWallet, enabled = !isMutating) {
                                Text("Open wallet")
                            }
                            Button(
                                onClick = {
                                    scope.launch {
                                        if (isMutating) return@launch
                                        isMutating = true
                                        uiState = uiState.copy(isLoading = true, actionMessage = null)
                                        runCatching {
                                            val checkout = repository.checkout(
                                                buyerId = buyerId,
                                                couponCode = couponCode.trim().ifBlank { null }
                                            )
                                            val refreshedCart = repository.listCart(buyerId)
                                            refreshedCart to checkout
                                        }
                                            .onSuccess { (refreshedCart, checkout) ->
                                                couponCode = ""
                                                uiState = uiState.copy(
                                                    isLoading = false,
                                                    cart = refreshedCart,
                                                    errorMessage = null,
                                                    actionMessage = "Checkout paid $${formatPriceInCents(checkout.totalPaidInCents)} across ${checkout.orders.size} orders."
                                                )
                                                onCheckoutSuccess()
                                            }
                                            .onFailure { error ->
                                                uiState = uiState.copy(
                                                    isLoading = false,
                                                    actionMessage = error.message ?: "Checkout failed."
                                                )
                                            }
                                        pendingProductId = null
                                        isMutating = false
                                    }
                                },
                                enabled = !isMutating && uiState.cart.items.isNotEmpty()
                            ) {
                                Text("Checkout")
                            }
                        }
                    }
                } else {
                    Button(onClick = onRequireSignedInBuyer, enabled = !isMutating) {
                        Text("Sign in to checkout")
                    }
                }
            }
        }
        if (uiState.cart.items.isEmpty()) {
            Surface(
                tonalElevation = 1.dp,
                shape = MaterialTheme.shapes.medium
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "Your cart is empty.",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        text = "Browse the marketplace and add products to start a buyer transaction flow.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Button(onClick = onBrowseMarketplace) {
                        Text("Browse marketplace")
                    }
                    OutlinedButton(
                        onClick = if (allowCheckout) onOpenWallet else onRequireSignedInBuyer
                    ) {
                        Text(if (allowCheckout) "Open wallet" else "Sign in for checkout")
                    }
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(uiState.cart.items, key = { it.id }) { item ->
                    CartItemCard(
                        item = item,
                        isPending = isMutating || pendingProductId == item.productId,
                        onDecrease = {
                            mutateCart(item.productId) {
                                if (item.quantity <= 1) {
                                    repository.removeFromCart(buyerId, item.productId)
                                } else {
                                    repository.updateQuantity(buyerId, item.productId, item.quantity - 1)
                                }
                            }
                        },
                        onIncrease = {
                            mutateCart(item.productId) {
                                repository.updateQuantity(buyerId, item.productId, item.quantity + 1)
                            }
                        },
                        onRemove = {
                            mutateCart(item.productId) {
                                repository.removeFromCart(buyerId, item.productId)
                            }
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun CartItemCard(
    item: CartItem,
    isPending: Boolean,
    onDecrease: () -> Unit,
    onIncrease: () -> Unit,
    onRemove: () -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(text = item.productName, style = MaterialTheme.typography.titleMedium)
            Text(text = "Seller: ${item.sellerId}", style = MaterialTheme.typography.bodySmall)
            PriceTag(price = formatPriceInCents(item.productPriceInCents))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Quantity: ${item.quantity}",
                    style = MaterialTheme.typography.bodyMedium
                )
                Text(
                    text = "Line total: $${formatPriceInCents(item.productPriceInCents * item.quantity)}",
                    style = MaterialTheme.typography.bodyMedium
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onDecrease, enabled = !isPending) {
                    Text("-")
                }
                OutlinedButton(onClick = onIncrease, enabled = !isPending) {
                    Text("+")
                }
                OutlinedButton(onClick = onRemove, enabled = !isPending) {
                    Text("Remove")
                }
            }
        }
    }
}

private data class BuyerCartUiState(
    val isLoading: Boolean = true,
    val cart: ShoppingCart = ShoppingCart(items = emptyList(), subtotalInCents = 0),
    val errorMessage: String? = null,
    val actionMessage: String? = null
)
