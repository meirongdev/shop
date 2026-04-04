package dev.meirong.shop.kmp.feature.order.ui

import androidx.compose.runtime.Composable
import dev.meirong.shop.kmp.ui.components.FeaturePlaceholderScreen

@Composable
fun OrderScreen(
    title: String = "Order feature foundation",
    description: String = "Shared order history and detail flows can now be routed from both apps.",
    highlights: List<String> = listOf(
        "Buyer and seller apps can diverge inside the same feature module with dedicated screens.",
        "The app shell now reserves a stable route for order-service integration.",
        "Future work can add list, detail, and action subgraphs without changing app wiring."
    )
) = FeaturePlaceholderScreen(
    title = title,
    description = description,
    highlights = highlights
)
