package dev.meirong.shop.kmp.feature.wallet.ui

import androidx.compose.runtime.Composable
import dev.meirong.shop.kmp.ui.components.FeaturePlaceholderScreen

@Composable
fun WalletScreen(
    title: String = "Wallet feature foundation",
    description: String = "Wallet balance, recharge, and payment flows now have a routed shared home in both apps.",
    highlights: List<String> = listOf(
        "This module is positioned for wallet-service integration.",
        "Buyer and seller apps can share balance UI while keeping role-specific actions.",
        "The app shell no longer needs extra wiring when wallet screens become real."
    )
) = FeaturePlaceholderScreen(
    title = title,
    description = description,
    highlights = highlights
)
