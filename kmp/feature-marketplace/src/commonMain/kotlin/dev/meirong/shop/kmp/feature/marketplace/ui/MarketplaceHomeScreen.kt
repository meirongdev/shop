package dev.meirong.shop.kmp.feature.marketplace.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.meirong.shop.kmp.core.model.Product
import dev.meirong.shop.kmp.ui.components.ProductCard
import dev.meirong.shop.kmp.ui.components.SearchBar
import dev.meirong.shop.kmp.ui.components.formatPriceInCents

@Composable
fun MarketplaceHomeScreen(
    title: String,
    subtitle: String,
    products: List<Product>,
    highlights: List<String> = emptyList(),
    emptyStateMessage: String = "No products match your current search.",
    productCaption: (Product) -> String? = { it.categoryName }
) {
    var query by remember { mutableStateOf("") }
    val filteredProducts = remember(query, products) {
        val keyword = query.trim()
        if (keyword.isEmpty()) {
            products
        } else {
            products.filter { product ->
                product.name.contains(keyword, ignoreCase = true) ||
                    product.description.contains(keyword, ignoreCase = true) ||
                    (product.categoryName?.contains(keyword, ignoreCase = true) == true)
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(text = title, style = MaterialTheme.typography.headlineSmall)
        Text(text = subtitle, style = MaterialTheme.typography.bodyMedium)
        SearchBar(query = query, onQueryChange = { query = it }, onSearch = {})
        if (highlights.isNotEmpty()) {
            Surface(
                tonalElevation = 2.dp,
                shape = MaterialTheme.shapes.medium
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = "Current shared wiring",
                        style = MaterialTheme.typography.titleMedium
                    )
                    highlights.forEach { highlight ->
                        Text(
                            text = "- $highlight",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }
        }
        LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            items(filteredProducts, key = { it.id }) { product ->
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    ProductCard(
                        name = product.name,
                        price = formatPriceInCents(product.priceInCents),
                        imageUrl = product.imageUrl,
                        onClick = {}
                    )
                    productCaption(product)?.takeIf { it.isNotBlank() }?.let { caption ->
                        Text(
                            text = caption,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(horizontal = 8.dp)
                        )
                    }
                }
            }
            if (filteredProducts.isEmpty()) {
                item {
                    Text(
                        text = emptyStateMessage,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(8.dp)
                    )
                }
            }
        }
    }
}
