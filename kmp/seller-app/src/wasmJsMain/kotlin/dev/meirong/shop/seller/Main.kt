package dev.meirong.shop.seller

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.window.ComposeViewport
import kotlinx.browser.document

@OptIn(ExperimentalComposeUiApi::class)
fun main() {
    val viewport = requireNotNull(document.getElementById("sellerApp")) {
        "Seller viewport element was not found."
    }
    ComposeViewport(viewport) {
        SellerApp()
    }
}
