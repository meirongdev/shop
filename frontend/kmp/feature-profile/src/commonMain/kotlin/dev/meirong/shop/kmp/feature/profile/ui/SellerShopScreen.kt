package dev.meirong.shop.kmp.feature.profile.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
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
import dev.meirong.shop.kmp.core.model.SellerStorefront
import dev.meirong.shop.kmp.feature.profile.data.SellerProfileRepository
import dev.meirong.shop.kmp.ui.components.ErrorScreen
import dev.meirong.shop.kmp.ui.components.LoadingIndicator
import kotlinx.coroutines.launch

@Composable
fun SellerShopScreen(
    repository: SellerProfileRepository,
    sellerId: String,
    onE2eStateChanged: (String, String?) -> Unit = { _, _ -> }
) {
    var refreshKey by remember { mutableIntStateOf(0) }
    var storefront by remember { mutableStateOf<SellerStorefront?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var shopName by remember { mutableStateOf("") }
    var shopSlug by remember { mutableStateOf("") }
    var shopDescription by remember { mutableStateOf("") }
    var logoUrl by remember { mutableStateOf("") }
    var bannerUrl by remember { mutableStateOf("") }
    var isSaving by remember { mutableStateOf(false) }
    var statusMessage by remember { mutableStateOf<String?>(null) }
    var isErrorStatus by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(sellerId, refreshKey) {
        onE2eStateChanged("loading", null)
        isLoading = true
        errorMessage = null
        runCatching { repository.getShop(sellerId) }
            .onSuccess { shop ->
                storefront = shop
                shopName = shop.shopName ?: ""
                shopSlug = shop.shopSlug ?: ""
                shopDescription = shop.shopDescription ?: ""
                logoUrl = shop.logoUrl ?: ""
                bannerUrl = shop.bannerUrl ?: ""
                isLoading = false
                onE2eStateChanged("ready", null)
            }
            .onFailure { error ->
                errorMessage = error.message ?: "Failed to load shop details."
                isLoading = false
                onE2eStateChanged("error", errorMessage)
            }
    }

    if (isLoading && storefront == null) {
        LoadingIndicator()
        return
    }

    if (errorMessage != null && storefront == null) {
        ErrorScreen(
            message = errorMessage ?: "Failed to load shop details.",
            onRetry = { refreshKey += 1 }
        )
        return
    }

    val shop = storefront ?: return
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(text = "Shop Management", style = MaterialTheme.typography.headlineSmall)
        if (isLoading || isSaving) {
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
                Text(text = "Owner: ${shop.displayName}", style = MaterialTheme.typography.bodyMedium)
                Text(text = "Rating: ${shop.avgRating} ★ | Sales: ${shop.totalSales}", style = MaterialTheme.typography.bodyMedium)
                Text(text = "Tier: ${shop.tier}", style = MaterialTheme.typography.bodySmall)
            }
        }
        OutlinedTextField(
            value = shopName,
            onValueChange = { shopName = it },
            label = { Text("Shop Name") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            enabled = !isSaving
        )
        OutlinedTextField(
            value = shopSlug,
            onValueChange = { shopSlug = it },
            label = { Text("Shop Slug (URL-friendly)") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            enabled = !isSaving
        )
        OutlinedTextField(
            value = shopDescription,
            onValueChange = { shopDescription = it },
            label = { Text("Shop Description") },
            modifier = Modifier.fillMaxWidth(),
            minLines = 3,
            maxLines = 5,
            enabled = !isSaving
        )
        OutlinedTextField(
            value = logoUrl,
            onValueChange = { logoUrl = it },
            label = { Text("Logo URL") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            enabled = !isSaving
        )
        OutlinedTextField(
            value = bannerUrl,
            onValueChange = { bannerUrl = it },
            label = { Text("Banner URL") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            enabled = !isSaving
        )
        statusMessage?.let { msg ->
            Text(
                text = msg,
                style = MaterialTheme.typography.bodySmall,
                color = if (isErrorStatus) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = {
                    scope.launch {
                        isSaving = true
                        statusMessage = null
                        isErrorStatus = false
                        runCatching {
                            repository.updateShop(
                                sellerId = sellerId,
                                shopName = shopName.trim().ifBlank { null },
                                shopSlug = shopSlug.trim().ifBlank { null },
                                shopDescription = shopDescription.trim().ifBlank { null },
                                logoUrl = logoUrl.trim().ifBlank { null },
                                bannerUrl = bannerUrl.trim().ifBlank { null }
                            )
                        }
                            .onSuccess { updated ->
                                storefront = updated
                                statusMessage = "Shop updated successfully."
                            }
                            .onFailure { error ->
                                isErrorStatus = true
                                statusMessage = error.message ?: "Failed to update shop."
                            }
                        isSaving = false
                    }
                },
                enabled = !isSaving
            ) {
                Text("Save Shop")
            }
            Button(
                onClick = { refreshKey += 1 },
                enabled = !isSaving
            ) {
                Text("Refresh")
            }
        }
    }
}
