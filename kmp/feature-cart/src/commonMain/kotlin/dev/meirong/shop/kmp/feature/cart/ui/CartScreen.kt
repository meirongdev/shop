package dev.meirong.shop.kmp.feature.cart.ui

import androidx.compose.runtime.Composable
import dev.meirong.shop.kmp.ui.components.FeaturePlaceholderScreen

@Composable
fun CartScreen(
    title: String = "Cart feature foundation",
    description: String = "Buyer cart flows will reuse shared pricing components and order hand-off from this module.",
    highlights: List<String> = listOf(
        "This destination is buyer-only, matching the product design in docs/superpowers.",
        "It is ready to host cart aggregation and checkout preparation UI.",
        "Order placement can later bridge directly into the order feature graph."
    )
) = FeaturePlaceholderScreen(
    title = title,
    description = description,
    highlights = highlights
)
