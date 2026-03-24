package dev.meirong.shop.kmp.feature.promotion.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import dev.meirong.shop.kmp.core.model.BuyerCoupon
import dev.meirong.shop.kmp.core.model.BuyerOffer
import dev.meirong.shop.kmp.core.model.BuyerPromotionCatalog
import dev.meirong.shop.kmp.feature.promotion.data.BuyerPromotionRepository
import dev.meirong.shop.kmp.ui.components.ErrorScreen
import dev.meirong.shop.kmp.ui.components.FeaturePlaceholderScreen
import dev.meirong.shop.kmp.ui.components.LoadingIndicator
import dev.meirong.shop.kmp.ui.components.formatPriceInCents
import kotlin.math.roundToLong

@Composable
fun PromotionScreen(
    title: String = "Promotion feature foundation",
    description: String = "The promotion route is now available for buyer discovery and seller campaign management.",
    highlights: List<String> = listOf(
        "Buyer and seller apps can land in different promotion experiences from the same module.",
        "This route is ready for promotion-service backed lists and management actions.",
        "The shared shell now exposes campaigns as a first-class destination."
    )
) = FeaturePlaceholderScreen(
    title = title,
    description = description,
    highlights = highlights
)

@Composable
fun BuyerPromotionScreen(
    repository: BuyerPromotionRepository
) {
    var refreshKey by remember { mutableIntStateOf(0) }
    var uiState by remember { mutableStateOf(BuyerPromotionsUiState()) }

    suspend fun reloadCatalog() {
        uiState = uiState.copy(isLoading = true, errorMessage = null)
        runCatching { repository.loadCatalog() }
            .onSuccess { catalog ->
                uiState = uiState.copy(
                    isLoading = false,
                    catalog = catalog,
                    errorMessage = null
                )
            }
            .onFailure { error ->
                uiState = uiState.copy(
                    isLoading = false,
                    errorMessage = error.message ?: "Failed to load buyer promotions."
                )
            }
    }

    LaunchedEffect(refreshKey) {
        reloadCatalog()
    }

    if (uiState.isLoading && uiState.catalog == null) {
        LoadingIndicator()
        return
    }

    if (uiState.errorMessage != null && uiState.catalog == null) {
        ErrorScreen(
            message = uiState.errorMessage ?: "Failed to load buyer promotions.",
            onRetry = { refreshKey += 1 }
        )
        return
    }

    val catalog = uiState.catalog ?: BuyerPromotionCatalog(
        offers = emptyList(),
        coupons = emptyList()
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(text = "Buyer Promotions", style = MaterialTheme.typography.headlineSmall)
        Text(
            text = "Browse live offers and active coupon codes from the buyer BFF. Coupons can be entered during checkout when you are ready to place an order.",
            style = MaterialTheme.typography.bodyMedium
        )
        if (uiState.isLoading) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        }
        PromotionSummaryCard(catalog = catalog)
        BuyerCouponListCard(coupons = catalog.coupons)
        BuyerOfferListCard(offers = catalog.offers)
        OutlinedButton(onClick = { refreshKey += 1 }, enabled = !uiState.isLoading) {
            Text("Refresh promotions")
        }
    }
}

@Composable
private fun PromotionSummaryCard(catalog: BuyerPromotionCatalog) {
    Surface(
        tonalElevation = 2.dp,
        shape = MaterialTheme.shapes.medium
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = "${catalog.offers.count { it.active }} active offers",
                style = MaterialTheme.typography.titleMedium
            )
            Text(
                text = "${catalog.coupons.size} active coupons ready for checkout",
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}

@Composable
private fun BuyerCouponListCard(coupons: List<BuyerCoupon>) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(text = "Available coupons", style = MaterialTheme.typography.titleMedium)
            if (coupons.isEmpty()) {
                Text(
                    text = "No active coupons are available right now.",
                    style = MaterialTheme.typography.bodyMedium
                )
            } else {
                coupons.forEach { coupon ->
                    Surface(
                        tonalElevation = 1.dp,
                        shape = MaterialTheme.shapes.medium
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(text = coupon.code, style = MaterialTheme.typography.titleMedium)
                            Text(
                                text = "${coupon.discountType} • ${buyerCouponValueLabel(coupon)}",
                                style = MaterialTheme.typography.bodyMedium
                            )
                            Text(
                                text = "Seller: ${coupon.sellerId}",
                                style = MaterialTheme.typography.bodySmall
                            )
                            Text(
                                text = "Min order: ${coupon.minOrderAmountInCents?.let { "$${formatPriceInCents(it)}" } ?: "None"}",
                                style = MaterialTheme.typography.bodyMedium
                            )
                            coupon.maxDiscountInCents?.let { maxDiscount ->
                                Text(
                                    text = "Max discount: $${formatPriceInCents(maxDiscount)}",
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            }
                            Text(
                                text = "Used ${coupon.usedCount}/${coupon.usageLimit} times${coupon.expiresAt?.let { " • Expires $it" } ?: ""}",
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun BuyerOfferListCard(offers: List<BuyerOffer>) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(text = "Campaign offers", style = MaterialTheme.typography.titleMedium)
            if (offers.isEmpty()) {
                Text(
                    text = "No active offers are available right now.",
                    style = MaterialTheme.typography.bodyMedium
                )
            } else {
                offers.forEach { offer ->
                    Surface(
                        tonalElevation = 1.dp,
                        shape = MaterialTheme.shapes.medium
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(text = offer.title, style = MaterialTheme.typography.titleMedium)
                            Text(text = offer.code, style = MaterialTheme.typography.bodySmall)
                            Text(text = offer.description, style = MaterialTheme.typography.bodyMedium)
                            Text(
                                text = "Reward: $${formatPriceInCents(offer.rewardAmountInCents)}",
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun buyerCouponValueLabel(coupon: BuyerCoupon): String {
    return if (coupon.discountType == "PERCENTAGE") {
        "${(coupon.discountValueInCents / 100.0).roundToLong()}%"
    } else {
        "$${formatPriceInCents(coupon.discountValueInCents)}"
    }
}

private data class BuyerPromotionsUiState(
    val isLoading: Boolean = true,
    val catalog: BuyerPromotionCatalog? = null,
    val errorMessage: String? = null
)
