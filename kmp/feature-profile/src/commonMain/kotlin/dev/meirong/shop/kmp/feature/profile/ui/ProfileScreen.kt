package dev.meirong.shop.kmp.feature.profile.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
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
import dev.meirong.shop.kmp.core.model.BuyerProfile
import dev.meirong.shop.kmp.core.model.SellerProfile
import dev.meirong.shop.kmp.feature.profile.data.BuyerProfileRepository
import dev.meirong.shop.kmp.feature.profile.data.SellerProfileRepository
import dev.meirong.shop.kmp.ui.components.ErrorScreen
import dev.meirong.shop.kmp.ui.components.FeaturePlaceholderScreen
import dev.meirong.shop.kmp.ui.components.LoadingIndicator
import kotlinx.coroutines.launch

@Composable
fun ProfileScreen(
    title: String = "Profile feature foundation",
    description: String = "Profile and account-management flows are now attached to both app shells.",
    highlights: List<String> = listOf(
        "This route is ready for profile-service backed user details.",
        "Buyer and seller variants can share base components while diverging on actions.",
        "It also gives the app shell a natural home for account preferences."
    )
) = FeaturePlaceholderScreen(
    title = title,
    description = description,
    highlights = highlights
)

@Composable
fun BuyerProfileScreen(
    repository: BuyerProfileRepository,
    buyerId: String
) {
    var refreshKey by remember { mutableIntStateOf(0) }
    var uiState by remember { mutableStateOf(BuyerProfileUiState()) }
    var displayName by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var isSaving by remember { mutableStateOf(false) }
    var statusMessage by remember { mutableStateOf<String?>(null) }
    var isErrorMessage by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    suspend fun loadProfile() {
        uiState = uiState.copy(isLoading = true, errorMessage = null)
        runCatching { repository.getProfile(buyerId) }
            .onSuccess { profile ->
                uiState = uiState.copy(
                    isLoading = false,
                    profile = profile,
                    errorMessage = null
                )
                displayName = profile.displayName
                email = profile.email
            }
            .onFailure { error ->
                uiState = uiState.copy(
                    isLoading = false,
                    errorMessage = error.message ?: "Failed to load buyer profile."
                )
            }
    }

    LaunchedEffect(buyerId, refreshKey) {
        loadProfile()
    }

    if (uiState.isLoading && uiState.profile == null) {
        LoadingIndicator()
        return
    }

    if (uiState.errorMessage != null && uiState.profile == null) {
        ErrorScreen(
            message = uiState.errorMessage ?: "Failed to load buyer profile.",
            onRetry = { refreshKey += 1 }
        )
        return
    }

    val profile = uiState.profile ?: return
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(text = "Buyer Profile", style = MaterialTheme.typography.headlineSmall)
        Text(
            text = "Buyer identity and contact data now load from profile-service through buyer-bff.",
            style = MaterialTheme.typography.bodyMedium
        )
        if (uiState.isLoading || isSaving) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        }
        Surface(
            tonalElevation = 2.dp,
            shape = MaterialTheme.shapes.medium
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(text = "Buyer ID: ${profile.buyerId}", style = MaterialTheme.typography.bodySmall)
                Text(text = "Username: ${profile.username}", style = MaterialTheme.typography.bodyMedium)
                Text(text = "Tier: ${profile.tier}", style = MaterialTheme.typography.bodyMedium)
                Text(text = "Created: ${profile.createdAt.take(19)}", style = MaterialTheme.typography.bodySmall)
                Text(text = "Updated: ${profile.updatedAt.take(19)}", style = MaterialTheme.typography.bodySmall)
            }
        }
        OutlinedTextField(
            value = displayName,
            onValueChange = { displayName = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Display name") },
            singleLine = true,
            enabled = !isSaving
        )
        OutlinedTextField(
            value = email,
            onValueChange = { email = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Contact email") },
            singleLine = true,
            enabled = !isSaving
        )
        statusMessage?.let { message ->
            Text(
                text = message,
                style = MaterialTheme.typography.bodySmall,
                color = if (isErrorMessage) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = {
                    scope.launch {
                        isSaving = true
                        statusMessage = null
                        isErrorMessage = false
                        runCatching {
                            repository.updateProfile(
                                buyerId = profile.buyerId,
                                displayName = displayName.trim(),
                                email = email.trim(),
                                tier = profile.tier
                            )
                        }
                            .onSuccess { updatedProfile ->
                                uiState = uiState.copy(profile = updatedProfile, errorMessage = null)
                                displayName = updatedProfile.displayName
                                email = updatedProfile.email
                                statusMessage = "Buyer profile updated."
                            }
                            .onFailure { error ->
                                isErrorMessage = true
                                statusMessage = error.message ?: "Buyer profile update failed."
                            }
                        isSaving = false
                    }
                },
                enabled = !isSaving && displayName.isNotBlank() && email.isNotBlank()
            ) {
                Text("Save profile")
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

@Composable
fun SellerProfileScreen(
    repository: SellerProfileRepository,
    sellerId: String
) {
    var refreshKey by remember { mutableIntStateOf(0) }
    var uiState by remember { mutableStateOf(SellerProfileUiState()) }
    var displayName by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var isSaving by remember { mutableStateOf(false) }
    var statusMessage by remember { mutableStateOf<String?>(null) }
    var isErrorMessage by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    suspend fun loadProfile() {
        uiState = uiState.copy(isLoading = true, errorMessage = null)
        runCatching { repository.getProfile(sellerId) }
            .onSuccess { profile ->
                uiState = uiState.copy(
                    isLoading = false,
                    profile = profile,
                    errorMessage = null
                )
                displayName = profile.displayName
                email = profile.email
            }
            .onFailure { error ->
                uiState = uiState.copy(
                    isLoading = false,
                    errorMessage = error.message ?: "Failed to load seller profile."
                )
            }
    }

    LaunchedEffect(sellerId, refreshKey) {
        loadProfile()
    }

    if (uiState.isLoading && uiState.profile == null) {
        LoadingIndicator()
        return
    }

    if (uiState.errorMessage != null && uiState.profile == null) {
        ErrorScreen(
            message = uiState.errorMessage ?: "Failed to load seller profile.",
            onRetry = { refreshKey += 1 }
        )
        return
    }

    val profile = uiState.profile ?: return
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(text = "Seller Profile", style = MaterialTheme.typography.headlineSmall)
        Text(
            text = "Seller identity and contact data now load from profile-service through seller-bff.",
            style = MaterialTheme.typography.bodyMedium
        )
        if (uiState.isLoading || isSaving) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        }
        Surface(
            tonalElevation = 2.dp,
            shape = MaterialTheme.shapes.medium
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(text = "Seller ID: ${profile.sellerId}", style = MaterialTheme.typography.bodySmall)
                Text(text = "Username: ${profile.username}", style = MaterialTheme.typography.bodyMedium)
                Text(text = "Tier: ${profile.tier}", style = MaterialTheme.typography.bodyMedium)
                Text(text = "Created: ${profile.createdAt.take(19)}", style = MaterialTheme.typography.bodySmall)
                Text(text = "Updated: ${profile.updatedAt.take(19)}", style = MaterialTheme.typography.bodySmall)
            }
        }
        OutlinedTextField(
            value = displayName,
            onValueChange = { displayName = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Display name") },
            singleLine = true,
            enabled = !isSaving
        )
        OutlinedTextField(
            value = email,
            onValueChange = { email = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Contact email") },
            singleLine = true,
            enabled = !isSaving
        )
        statusMessage?.let { message ->
            Text(
                text = message,
                style = MaterialTheme.typography.bodySmall,
                color = if (isErrorMessage) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = {
                    scope.launch {
                        isSaving = true
                        statusMessage = null
                        isErrorMessage = false
                        runCatching {
                            repository.updateProfile(
                                sellerId = profile.sellerId,
                                displayName = displayName.trim(),
                                email = email.trim(),
                                tier = profile.tier
                            )
                        }
                            .onSuccess { updatedProfile ->
                                uiState = uiState.copy(profile = updatedProfile, errorMessage = null)
                                displayName = updatedProfile.displayName
                                email = updatedProfile.email
                                statusMessage = "Seller profile updated."
                            }
                            .onFailure { error ->
                                isErrorMessage = true
                                statusMessage = error.message ?: "Seller profile update failed."
                            }
                        isSaving = false
                    }
                },
                enabled = !isSaving && displayName.isNotBlank() && email.isNotBlank()
            ) {
                Text("Save profile")
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

private data class SellerProfileUiState(
    val isLoading: Boolean = true,
    val profile: SellerProfile? = null,
    val errorMessage: String? = null
)

private data class BuyerProfileUiState(
    val isLoading: Boolean = true,
    val profile: BuyerProfile? = null,
    val errorMessage: String? = null
)
