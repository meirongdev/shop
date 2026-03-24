package dev.meirong.shop.buyer

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.window.ComposeViewport
import kotlinx.browser.document

@OptIn(ExperimentalComposeUiApi::class)
fun main() {
    val viewport = requireNotNull(document.getElementById("buyerApp")) {
        "Buyer viewport element was not found."
    }
    ComposeViewport(viewport) {
        BuyerApp()
    }
}
