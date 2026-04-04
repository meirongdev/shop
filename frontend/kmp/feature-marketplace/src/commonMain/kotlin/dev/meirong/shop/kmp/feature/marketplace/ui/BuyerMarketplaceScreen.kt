package dev.meirong.shop.kmp.feature.marketplace.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.meirong.shop.kmp.core.model.ProductHit
import dev.meirong.shop.kmp.feature.marketplace.data.BuyerMarketplaceRepository
import dev.meirong.shop.kmp.ui.components.ErrorScreen
import dev.meirong.shop.kmp.ui.components.LoadingIndicator
import dev.meirong.shop.kmp.ui.components.ProductCard
import dev.meirong.shop.kmp.ui.components.SearchBar
import dev.meirong.shop.kmp.ui.components.formatPriceDecimal
import kotlinx.coroutines.delay

@Composable
fun BuyerMarketplaceScreen(
    repository: BuyerMarketplaceRepository,
    onProductSelected: (String) -> Unit
) {
    var query by rememberSaveable { mutableStateOf("") }
    // A blank submitted query intentionally loads the default published catalog on first render.
    var submittedQuery by rememberSaveable { mutableStateOf("") }
    var refreshKey by remember { mutableIntStateOf(0) }
    var uiState by remember { mutableStateOf(BuyerMarketplaceUiState()) }

    LaunchedEffect(submittedQuery, refreshKey) {
        uiState = uiState.copy(isLoading = true, errorMessage = null)
        delay(250)
        runCatching { repository.searchProducts(query = submittedQuery) }
            .onSuccess { result ->
                uiState = uiState.copy(
                    isLoading = false,
                    products = result.hits,
                    totalHits = result.totalHits,
                    page = result.page,
                    totalPages = result.totalPages,
                    errorMessage = null
                )
            }
            .onFailure { error ->
                uiState = uiState.copy(
                    isLoading = false,
                    errorMessage = error.message ?: "Failed to load marketplace products."
                )
            }
    }

    if (uiState.isLoading && uiState.products.isEmpty()) {
        LoadingIndicator()
        return
    }

    if (uiState.errorMessage != null && uiState.products.isEmpty()) {
        ErrorScreen(
            message = uiState.errorMessage ?: "Marketplace search failed.",
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
        Text(
            text = "Buyer Marketplace",
            style = MaterialTheme.typography.headlineSmall
        )
        Text(
            text = "Live buyer search now talks to the existing BFF and search-service stack.",
            style = MaterialTheme.typography.bodyMedium
        )
        SearchBar(
            query = query,
            onQueryChange = { query = it },
            onSearch = {
                val normalizedQuery = query.trim()
                if (normalizedQuery == submittedQuery) {
                    refreshKey += 1
                } else {
                    submittedQuery = normalizedQuery
                }
            }
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
                    text = "${uiState.totalHits} results",
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    text = "Page ${uiState.page} of ${uiState.totalPages}",
                    style = MaterialTheme.typography.bodySmall
                )
                uiState.errorMessage?.let { message ->
                    Text(
                        text = message,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }
        LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            items(uiState.products, key = { it.id }) { product ->
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    ProductCard(
                        name = product.name,
                        price = formatPriceDecimal(product.price),
                        imageUrl = product.imageUrl,
                        onClick = { onProductSelected(product.id) }
                    )
                    Text(
                        text = buildProductCaption(product),
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(horizontal = 8.dp)
                    )
                }
            }
            if (uiState.products.isEmpty()) {
                item {
                    Text(
                        text = "No products match your current search.",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(8.dp)
                    )
                }
            }
        }
    }
}

private data class BuyerMarketplaceUiState(
    val isLoading: Boolean = true,
    val products: List<ProductHit> = emptyList(),
    val totalHits: Long = 0,
    val page: Int = 1,
    val totalPages: Int = 1,
    val errorMessage: String? = null
)

private fun buildProductCaption(product: ProductHit): String {
    val category = product.categoryName ?: "General"
    return "Category: $category | Inventory: ${product.inventory}"
}
