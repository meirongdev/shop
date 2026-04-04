package dev.meirong.shop.kmp.feature.promotion.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.meirong.shop.kmp.core.model.SellerCoupon
import dev.meirong.shop.kmp.core.model.SellerOffer
import dev.meirong.shop.kmp.core.model.SellerPromotionWorkspace
import dev.meirong.shop.kmp.feature.promotion.data.SellerPromotionRepository
import dev.meirong.shop.kmp.ui.components.ErrorScreen
import dev.meirong.shop.kmp.ui.components.LoadingIndicator
import dev.meirong.shop.kmp.ui.components.formatPriceInCents
import kotlin.math.roundToLong
import kotlinx.coroutines.launch

@Composable
fun SellerPromotionScreen(
    repository: SellerPromotionRepository,
    sellerId: String,
    onE2eStateChanged: (String, String?) -> Unit = { _, _ -> }
) {
    var refreshKey by remember { mutableIntStateOf(0) }
    var uiState by remember { mutableStateOf(SellerPromotionsUiState()) }
    var offerForm by remember { mutableStateOf(OfferFormState()) }
    var couponForm by remember { mutableStateOf(CouponFormState()) }
    val scope = rememberCoroutineScope()

    suspend fun reloadWorkspace() {
        onE2eStateChanged("loading", null)
        uiState = uiState.copy(isLoading = true, errorMessage = null)
        runCatching { repository.loadWorkspace(sellerId) }
            .onSuccess { workspace ->
                uiState = uiState.copy(
                    isLoading = false,
                    workspace = workspace,
                    errorMessage = null
                )
                onE2eStateChanged("ready", null)
            }
            .onFailure { error ->
                uiState = uiState.copy(
                    isLoading = false,
                    errorMessage = error.message ?: "Failed to load seller promotions."
                )
                onE2eStateChanged("error", uiState.errorMessage)
            }
    }

    LaunchedEffect(sellerId, refreshKey) {
        reloadWorkspace()
    }

    if (uiState.isLoading && uiState.workspace == null) {
        LoadingIndicator()
        return
    }

    if (uiState.errorMessage != null && uiState.workspace == null) {
        ErrorScreen(
            message = uiState.errorMessage ?: "Failed to load seller promotions.",
            onRetry = { refreshKey += 1 }
        )
        return
    }

    val workspace = uiState.workspace ?: SellerPromotionWorkspace(
        activePromotionCount = 0,
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
        Text(text = "Seller Promotions", style = MaterialTheme.typography.headlineSmall)
        Text(
            text = "Create offers and coupons with the live seller BFF. Coupons use a dedicated list endpoint; offers come from dashboard aggregation.",
            style = MaterialTheme.typography.bodyMedium
        )
        if (uiState.isLoading) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        }
        uiState.actionMessage?.let { message ->
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = if (message.startsWith("Created")) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
            )
        }
        SummaryCard(workspace = workspace)
        OfferFormCard(
            form = offerForm,
            isSubmitting = uiState.isCreatingOffer,
            onFormChange = { offerForm = it },
            onSubmit = {
                scope.launch {
                    uiState = uiState.copy(isCreatingOffer = true, actionMessage = null)
                    val rewardAmount = parseRequiredAmount(offerForm.rewardAmount, "Reward amount")
                    val result = rewardAmount.fold(
                        onSuccess = { amount ->
                            runCatching {
                                repository.createOffer(
                                    sellerId = sellerId,
                                    code = offerForm.code.trim(),
                                    title = offerForm.title.trim(),
                                    description = offerForm.description.trim(),
                                    rewardAmountInCents = amount
                                )
                                repository.loadWorkspace(sellerId)
                            }
                        },
                        onFailure = { Result.failure(it) }
                    )
                    result
                        .onSuccess { refreshedWorkspace ->
                            uiState = uiState.copy(
                                isCreatingOffer = false,
                                workspace = refreshedWorkspace,
                                errorMessage = null,
                                actionMessage = "Created offer ${offerForm.code.trim()}."
                            )
                            offerForm = OfferFormState()
                        }
                        .onFailure { error ->
                            uiState = uiState.copy(
                                isCreatingOffer = false,
                                actionMessage = error.message ?: "Failed to create offer."
                            )
                        }
                }
            }
        )
        CouponFormCard(
            form = couponForm,
            isSubmitting = uiState.isCreatingCoupon,
            onFormChange = { couponForm = it },
            onSelectDiscountType = { discountType ->
                couponForm = couponForm.copy(discountType = discountType)
            },
            onSubmit = {
                scope.launch {
                    uiState = uiState.copy(isCreatingCoupon = true, actionMessage = null)
                    val discountValue = parseRequiredAmount(couponForm.discountValue, "Discount value")
                    val minOrderAmount = parseOptionalAmount(couponForm.minOrderAmount, "Minimum order amount")
                    val maxDiscount = parseOptionalAmount(couponForm.maxDiscount, "Maximum discount")
                    val usageLimit = parseUsageLimit(couponForm.usageLimit)
                    val result = listOf(discountValue, minOrderAmount, maxDiscount, usageLimit).firstOrNull { it.isFailure }
                        ?.exceptionOrNull()
                        ?.let { Result.failure(it) }
                        ?: runCatching {
                            repository.createCoupon(
                                sellerId = sellerId,
                                code = couponForm.code.trim(),
                                discountType = couponForm.discountType,
                                discountValueInCents = discountValue.getOrThrow(),
                                minOrderAmountInCents = minOrderAmount.getOrThrow(),
                                maxDiscountInCents = maxDiscount.getOrThrow(),
                                usageLimit = usageLimit.getOrThrow()
                            )
                            repository.loadWorkspace(sellerId)
                        }
                    result
                        .onSuccess { refreshedWorkspace ->
                            uiState = uiState.copy(
                                isCreatingCoupon = false,
                                workspace = refreshedWorkspace,
                                errorMessage = null,
                                actionMessage = "Created coupon ${couponForm.code.trim()}."
                            )
                            couponForm = CouponFormState()
                        }
                        .onFailure { error ->
                            uiState = uiState.copy(
                                isCreatingCoupon = false,
                                actionMessage = error.message ?: "Failed to create coupon."
                            )
                        }
                }
            }
        )
        CouponListCard(coupons = workspace.coupons)
        OfferListCard(offers = workspace.offers)
        OutlinedButton(onClick = { refreshKey += 1 }, enabled = !uiState.isCreatingOffer && !uiState.isCreatingCoupon) {
            Text("Refresh promotions")
        }
    }
}

@Composable
private fun SummaryCard(workspace: SellerPromotionWorkspace) {
    Surface(
        tonalElevation = 2.dp,
        shape = MaterialTheme.shapes.medium
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(text = "Active offers", style = MaterialTheme.typography.bodyMedium)
                Text(
                    text = workspace.activePromotionCount.toString(),
                    style = MaterialTheme.typography.headlineSmall
                )
            }
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(text = "Coupons", style = MaterialTheme.typography.bodyMedium)
                Text(text = workspace.coupons.size.toString(), style = MaterialTheme.typography.headlineSmall)
            }
        }
    }
}

@Composable
private fun OfferFormCard(
    form: OfferFormState,
    isSubmitting: Boolean,
    onFormChange: (OfferFormState) -> Unit,
    onSubmit: () -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(text = "Create Offer", style = MaterialTheme.typography.titleMedium)
            OutlinedTextField(
                value = form.code,
                onValueChange = { onFormChange(form.copy(code = it)) },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Code") },
                singleLine = true
            )
            OutlinedTextField(
                value = form.title,
                onValueChange = { onFormChange(form.copy(title = it)) },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Title") },
                singleLine = true
            )
            OutlinedTextField(
                value = form.description,
                onValueChange = { onFormChange(form.copy(description = it)) },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Description") }
            )
            OutlinedTextField(
                value = form.rewardAmount,
                onValueChange = { onFormChange(form.copy(rewardAmount = it)) },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Reward amount") },
                singleLine = true
            )
            Button(
                onClick = onSubmit,
                enabled = !isSubmitting && form.code.isNotBlank() && form.title.isNotBlank() && form.description.isNotBlank() && form.rewardAmount.isNotBlank()
            ) {
                Text(if (isSubmitting) "Creating offer..." else "Create offer")
            }
        }
    }
}

@Composable
private fun CouponFormCard(
    form: CouponFormState,
    isSubmitting: Boolean,
    onFormChange: (CouponFormState) -> Unit,
    onSelectDiscountType: (String) -> Unit,
    onSubmit: () -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(text = "Create Coupon", style = MaterialTheme.typography.titleMedium)
            OutlinedTextField(
                value = form.code,
                onValueChange = { onFormChange(form.copy(code = it)) },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Code") },
                singleLine = true
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = { onSelectDiscountType("FIXED_AMOUNT") },
                    enabled = !isSubmitting
                ) {
                    Text(if (form.discountType == "FIXED_AMOUNT") "Fixed Amount ✓" else "Fixed Amount")
                }
                OutlinedButton(
                    onClick = { onSelectDiscountType("PERCENTAGE") },
                    enabled = !isSubmitting
                ) {
                    Text(if (form.discountType == "PERCENTAGE") "Percentage ✓" else "Percentage")
                }
            }
            OutlinedTextField(
                value = form.discountValue,
                onValueChange = { onFormChange(form.copy(discountValue = it)) },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Discount value") },
                singleLine = true
            )
            OutlinedTextField(
                value = form.minOrderAmount,
                onValueChange = { onFormChange(form.copy(minOrderAmount = it)) },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Min order amount (optional)") },
                singleLine = true
            )
            OutlinedTextField(
                value = form.maxDiscount,
                onValueChange = { onFormChange(form.copy(maxDiscount = it)) },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Max discount (optional)") },
                singleLine = true
            )
            OutlinedTextField(
                value = form.usageLimit,
                onValueChange = { onFormChange(form.copy(usageLimit = it)) },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Usage limit") },
                singleLine = true
            )
            Button(
                onClick = onSubmit,
                enabled = !isSubmitting && form.code.isNotBlank() && form.discountValue.isNotBlank() && form.usageLimit.isNotBlank()
            ) {
                Text(if (isSubmitting) "Creating coupon..." else "Create coupon")
            }
        }
    }
}

@Composable
private fun CouponListCard(coupons: List<SellerCoupon>) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(text = "Coupons", style = MaterialTheme.typography.titleMedium)
            if (coupons.isEmpty()) {
                Text(
                    text = "No coupons yet.",
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
                                text = "${coupon.discountType} • ${couponValueLabel(coupon)}",
                                style = MaterialTheme.typography.bodyMedium
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
                                text = "Usage: ${coupon.usedCount}/${coupon.usageLimit} • ${if (coupon.active) "Active" else "Inactive"}",
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
private fun OfferListCard(offers: List<SellerOffer>) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(text = "Offers", style = MaterialTheme.typography.titleMedium)
            if (offers.isEmpty()) {
                Text(
                    text = "No offers yet.",
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
                                text = "Reward: $${formatPriceInCents(offer.rewardAmountInCents)} • ${if (offer.active) "Active" else "Inactive"}",
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun couponValueLabel(coupon: SellerCoupon): String {
    return if (coupon.discountType == "PERCENTAGE") {
        "${coupon.discountValueInCents / 100.0}%"
    } else {
        "$${formatPriceInCents(coupon.discountValueInCents)}"
    }
}

private fun parseRequiredAmount(value: String, fieldName: String): Result<Long> {
    val amount = value.trim().toDoubleOrNull()
        ?: return Result.failure(IllegalArgumentException("$fieldName must be a valid amount."))
    return Result.success((amount * 100.0).roundToLong())
}

private fun parseOptionalAmount(value: String, fieldName: String): Result<Long?> {
    if (value.isBlank()) {
        return Result.success(null)
    }
    return parseRequiredAmount(value, fieldName).map { it }
}

private fun parseUsageLimit(value: String): Result<Int> {
    val usageLimit = value.trim().toIntOrNull()
        ?: return Result.failure(IllegalArgumentException("Usage limit must be a whole number."))
    if (usageLimit <= 0) {
        return Result.failure(IllegalArgumentException("Usage limit must be greater than zero."))
    }
    return Result.success(usageLimit)
}

private data class SellerPromotionsUiState(
    val isLoading: Boolean = true,
    val workspace: SellerPromotionWorkspace? = null,
    val errorMessage: String? = null,
    val actionMessage: String? = null,
    val isCreatingOffer: Boolean = false,
    val isCreatingCoupon: Boolean = false
)

private data class OfferFormState(
    val code: String = "",
    val title: String = "",
    val description: String = "",
    val rewardAmount: String = ""
)

private data class CouponFormState(
    val code: String = "",
    val discountType: String = "FIXED_AMOUNT",
    val discountValue: String = "",
    val minOrderAmount: String = "",
    val maxDiscount: String = "",
    val usageLimit: String = ""
)
