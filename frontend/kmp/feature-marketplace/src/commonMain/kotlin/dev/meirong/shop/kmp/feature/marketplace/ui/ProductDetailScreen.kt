package dev.meirong.shop.kmp.feature.marketplace.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
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
import dev.meirong.shop.kmp.core.model.Product
import dev.meirong.shop.kmp.feature.marketplace.data.BuyerMarketplaceRepository
import dev.meirong.shop.kmp.ui.components.ErrorScreen
import dev.meirong.shop.kmp.ui.components.LoadingIndicator
import dev.meirong.shop.kmp.ui.components.formatPriceInCents
import kotlinx.coroutines.launch

@Composable
fun ProductDetailScreen(
    productId: String,
    repository: BuyerMarketplaceRepository,
    onBack: () -> Unit,
    onAddToCart: suspend (Product) -> Result<String>
) {
    var refreshKey by remember { mutableIntStateOf(0) }
    var product by remember { mutableStateOf<Product?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var isAddingToCart by remember { mutableStateOf(false) }
    var addToCartMessage by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(productId, refreshKey) {
        isLoading = true
        errorMessage = null
        runCatching { repository.getProduct(productId) }
            .onSuccess {
                product = it
                isLoading = false
            }
            .onFailure { error ->
                errorMessage = error.message ?: "Failed to load product details."
                isLoading = false
            }
    }

    if (isLoading && product == null) {
        LoadingIndicator()
        return
    }

    if (errorMessage != null && product == null) {
        ErrorScreen(
            message = errorMessage ?: "Failed to load product details.",
            onRetry = { refreshKey += 1 }
        )
        return
    }

    val currentProduct = product ?: return
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Button(onClick = onBack) {
            Text("Back to results")
        }
        Text(text = currentProduct.name, style = MaterialTheme.typography.headlineSmall)
        Text(text = currentProduct.description, style = MaterialTheme.typography.bodyLarge)
        Surface(
            tonalElevation = 2.dp,
            shape = MaterialTheme.shapes.medium
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = "Price: ${formatPriceInCents(currentProduct.priceInCents)}",
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    text = "Category: ${currentProduct.categoryName ?: "General"}",
                    style = MaterialTheme.typography.bodyMedium
                )
                Text(
                    text = "Inventory: ${currentProduct.inventory}",
                    style = MaterialTheme.typography.bodyMedium
                )
                Text(
                    text = "Status: ${currentProduct.status ?: "UNKNOWN"}",
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
        Button(
            onClick = {
                scope.launch {
                    isAddingToCart = true
                    addToCartMessage = null
                    val result = onAddToCart(currentProduct)
                    addToCartMessage = result.fold(
                        onSuccess = { it },
                        onFailure = { it.message ?: "Failed to add the product to cart." }
                    )
                    isAddingToCart = false
                }
            },
            enabled = !isAddingToCart
        ) {
            Text(if (isAddingToCart) "Adding..." else "Add to cart")
        }
        addToCartMessage?.let { message ->
            Text(
                text = message,
                color = if (message.startsWith("Added")) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}
