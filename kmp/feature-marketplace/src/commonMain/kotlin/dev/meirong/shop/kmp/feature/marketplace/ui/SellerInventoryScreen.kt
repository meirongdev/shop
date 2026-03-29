package dev.meirong.shop.kmp.feature.marketplace.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
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
import dev.meirong.shop.kmp.core.model.SellerDashboard
import dev.meirong.shop.kmp.feature.marketplace.data.SellerDashboardRepository
import dev.meirong.shop.kmp.ui.components.ErrorScreen
import dev.meirong.shop.kmp.ui.components.LoadingIndicator

@Composable
fun SellerInventoryScreen(
    repository: SellerDashboardRepository,
    sellerId: String,
    onE2eStateChanged: (String, String?) -> Unit = { _, _ -> }
) {
    var refreshKey by remember { mutableIntStateOf(0) }
    var dashboard by remember { mutableStateOf<SellerDashboard?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(sellerId, refreshKey) {
        onE2eStateChanged("loading", null)
        isLoading = true
        errorMessage = null
        runCatching { repository.loadDashboard(sellerId) }
            .onSuccess {
                dashboard = it
                isLoading = false
                onE2eStateChanged("ready", null)
            }
            .onFailure { error ->
                errorMessage = error.message ?: "Failed to load seller inventory."
                isLoading = false
                onE2eStateChanged("error", errorMessage)
            }
    }

    if (isLoading && dashboard == null) {
        LoadingIndicator()
        return
    }

    if (errorMessage != null && dashboard == null) {
        ErrorScreen(
            message = errorMessage ?: "Failed to load seller inventory.",
            onRetry = { refreshKey += 1 }
        )
        return
    }

    val currentDashboard = dashboard ?: return
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = "Seller Inventory",
            style = MaterialTheme.typography.headlineSmall
        )
        Text(
            text = "Live seller inventory now comes from the existing seller dashboard aggregation.",
            style = MaterialTheme.typography.bodyMedium
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
                Text(
                    text = "${currentDashboard.productCount} products",
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    text = "${currentDashboard.activePromotionCount} active promotions",
                    style = MaterialTheme.typography.bodyMedium
                )
                errorMessage?.let { message ->
                    Text(
                        text = message,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }
        MarketplaceHomeScreen(
            title = "Inventory Catalog",
            subtitle = "Search and inspect the seller-owned catalog snapshot already returned by the BFF.",
            products = currentDashboard.products,
            emptyStateMessage = "No seller inventory items match your current filter.",
            productCaption = { product ->
                "SKU: ${product.sku} | Inventory: ${product.inventory} | Status: ${product.status}"
            }
        )
    }
}
