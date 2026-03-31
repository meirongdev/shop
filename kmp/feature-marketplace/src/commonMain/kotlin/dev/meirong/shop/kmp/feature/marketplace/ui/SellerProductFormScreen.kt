package dev.meirong.shop.kmp.feature.marketplace.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import dev.meirong.shop.kmp.core.model.Product
import dev.meirong.shop.kmp.feature.marketplace.data.SellerDashboardRepository
import dev.meirong.shop.kmp.ui.components.formatPriceInCents
import kotlinx.coroutines.launch

@Composable
fun SellerProductFormScreen(
    repository: SellerDashboardRepository,
    sellerId: String,
    editProduct: Product? = null,
    onBack: () -> Unit,
    onSaved: () -> Unit
) {
    val isEditMode = editProduct != null
    var name by remember { mutableStateOf(editProduct?.name ?: "") }
    var sku by remember { mutableStateOf(editProduct?.sku ?: "") }
    var description by remember { mutableStateOf(editProduct?.description ?: "") }
    var priceText by remember {
        mutableStateOf(
            if (editProduct != null) formatPriceInCents(editProduct.priceInCents) else ""
        )
    }
    var inventoryText by remember {
        mutableStateOf(editProduct?.inventory?.toString() ?: "")
    }
    var published by remember { mutableStateOf(editProduct?.published ?: false) }
    var isSubmitting by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var successMessage by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        OutlinedButton(onClick = onBack) {
            Text("← Back to inventory")
        }
        Text(
            text = if (isEditMode) "Edit Product" else "Create Product",
            style = MaterialTheme.typography.headlineSmall
        )
        if (isSubmitting) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        }
        OutlinedTextField(
            value = name,
            onValueChange = { name = it },
            label = { Text("Product Name") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )
        OutlinedTextField(
            value = sku,
            onValueChange = { sku = it },
            label = { Text("SKU") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )
        OutlinedTextField(
            value = description,
            onValueChange = { description = it },
            label = { Text("Description") },
            modifier = Modifier.fillMaxWidth(),
            minLines = 3,
            maxLines = 5
        )
        OutlinedTextField(
            value = priceText,
            onValueChange = { priceText = it },
            label = { Text("Price (e.g. 19.99)") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
        )
        OutlinedTextField(
            value = inventoryText,
            onValueChange = { inventoryText = it },
            label = { Text("Inventory") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
        )
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text("Published", style = MaterialTheme.typography.bodyLarge)
            Switch(checked = published, onCheckedChange = { published = it })
        }

        errorMessage?.let { msg ->
            Text(
                text = msg,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium
            )
        }
        successMessage?.let { msg ->
            Text(
                text = msg,
                color = MaterialTheme.colorScheme.primary,
                style = MaterialTheme.typography.bodyMedium
            )
        }

        Button(
            onClick = {
                scope.launch {
                    errorMessage = null
                    successMessage = null
                    val price = priceText.toDoubleOrNull()
                    val inventory = inventoryText.toIntOrNull()
                    if (name.isBlank() || sku.isBlank() || description.isBlank()) {
                        errorMessage = "Name, SKU, and description are required."
                        return@launch
                    }
                    if (price == null || price <= 0) {
                        errorMessage = "Please enter a valid price."
                        return@launch
                    }
                    if (inventory == null || inventory < 0) {
                        errorMessage = "Please enter a valid inventory count."
                        return@launch
                    }
                    isSubmitting = true
                    runCatching {
                        if (isEditMode) {
                            repository.updateProduct(
                                productId = editProduct!!.id,
                                sellerId = sellerId,
                                sku = sku,
                                name = name,
                                description = description,
                                price = price,
                                inventory = inventory,
                                published = published
                            )
                        } else {
                            repository.createProduct(
                                sellerId = sellerId,
                                sku = sku,
                                name = name,
                                description = description,
                                price = price,
                                inventory = inventory,
                                published = published
                            )
                        }
                    }
                        .onSuccess {
                            successMessage = if (isEditMode) "Product updated!" else "Product created!"
                            isSubmitting = false
                            onSaved()
                        }
                        .onFailure { error ->
                            errorMessage = error.message ?: "Failed to save product."
                            isSubmitting = false
                        }
                }
            },
            enabled = !isSubmitting,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(if (isEditMode) "Update Product" else "Create Product")
        }
    }
}
