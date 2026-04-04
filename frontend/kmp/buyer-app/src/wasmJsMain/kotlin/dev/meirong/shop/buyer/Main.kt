package dev.meirong.shop.buyer

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.window.ComposeViewport
import dev.meirong.shop.kmp.core.model.AuthSession
import kotlinx.browser.document
import kotlinx.browser.window
import org.w3c.dom.HTMLElement

@OptIn(ExperimentalComposeUiApi::class)
fun main() {
    val viewport = requireNotNull(document.getElementById("buyerApp")) {
        "Buyer viewport element was not found."
    }
    val query = queryParameters()
    val e2eEnabled = query["e2e"] == "1" || query["e2e"] == "true"
    val e2eMarker = if (e2eEnabled) ensureE2eMarker() else null
    if (e2eMarker != null) {
        updateE2eMarker(
            e2eMarker,
            BuyerAppE2eState(
                route = query["e2eRoute"] ?: "marketplace",
                status = "booting"
            )
        )
    }
    ComposeViewport(viewport) {
        val e2eConfig = if (e2eEnabled && e2eMarker != null) {
            BuyerAppE2eConfig(
                enabled = true,
                autoLogin = query["e2eAutoLogin"] == "1" || query["e2eAutoLogin"] == "true",
                guestLogin = query["e2eGuestLogin"] == "1" || query["e2eGuestLogin"] == "true",
                initialRoute = query["e2eRoute"],
                session = query.toE2eSession(),
                onStateChange = { state -> updateE2eMarker(e2eMarker, state) }
            )
        } else {
            BuyerAppE2eConfig()
        }
        BuyerApp(e2e = e2eConfig)
    }
}

private fun queryParameters(): Map<String, String> {
    val rawQuery = window.location.search.removePrefix("?")
    if (rawQuery.isBlank()) {
        return emptyMap()
    }
    return rawQuery.split("&")
        .mapNotNull { segment ->
            if (segment.isBlank()) {
                null
            } else {
                val parts = segment.split("=", limit = 2)
                parts[0] to parts.getOrElse(1) { "" }
            }
        }
        .toMap()
}

private fun Map<String, String>.toE2eSession(): AuthSession? {
    val accessToken = this["e2eAccessToken"] ?: return null
    val username = this["e2eUsername"] ?: return null
    val principalId = this["e2ePrincipalId"] ?: return null
    val roles = this["e2eRoles"]?.split(",") ?: listOf("ROLE_BUYER")
    return AuthSession(
        accessToken = accessToken,
        username = username,
        displayName = this["e2eDisplayName"] ?: username,
        principalId = principalId,
        roles = roles,
        portal = "buyer"
    )
}

private fun ensureE2eMarker(): HTMLElement {
    val existing = document.getElementById("buyer-app-e2e") as? HTMLElement
    if (existing != null) {
        return existing
    }
    val marker = document.createElement("div") as HTMLElement
    marker.id = "buyer-app-e2e"
    marker.setAttribute("hidden", "hidden")
    requireNotNull(document.body) { "Document body was not found." }.appendChild(marker)
    return marker
}

private fun updateE2eMarker(marker: HTMLElement, state: BuyerAppE2eState) {
    marker.setAttribute("data-route", state.route)
    marker.setAttribute("data-status", state.status)
    marker.setAttribute("data-user", state.username ?: "")
    marker.setAttribute("data-message", state.message ?: "")
    marker.textContent = listOf(
        state.route,
        state.status,
        state.username ?: "",
        state.message ?: ""
    ).joinToString("|")
}
