package dev.meirong.shop.kmp.feature.auth.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import dev.meirong.shop.kmp.core.model.AuthSession
import kotlinx.coroutines.launch

@Composable
fun AuthScreen(
    title: String,
    description: String,
    portal: String,
    session: AuthSession?,
    onSignIn: suspend (String, String) -> Result<AuthSession>,
    onContinueAsGuest: (suspend () -> Result<AuthSession>)? = null,
    onSignOut: suspend () -> Unit
) {
    var username by remember { mutableStateOf(defaultUsername(portal)) }
    var password by remember { mutableStateOf("password") }
    var message by remember { mutableStateOf<String?>(null) }
    var isErrorMessage by remember { mutableStateOf(false) }
    var isSubmitting by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(text = title, style = MaterialTheme.typography.headlineSmall)
        Text(text = description, style = MaterialTheme.typography.bodyLarge)
        session?.let { activeSession ->
            Surface(
                tonalElevation = 2.dp,
                shape = MaterialTheme.shapes.medium
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "Signed in as ${activeSession.displayName}",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        text = "${activeSession.username} • ${activeSession.principalId}",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text(
                        text = "Portal: ${activeSession.portal} • Roles: ${activeSession.roles.joinToString()}",
                        style = MaterialTheme.typography.bodySmall
                    )
                    Button(
                        onClick = {
                            scope.launch {
                                isSubmitting = true
                                message = null
                                isErrorMessage = false
                                message = runCatching { onSignOut() }.fold(
                                    onSuccess = { "Signed out of the $portal portal." },
                                    onFailure = {
                                        isErrorMessage = true
                                        it.message ?: "Sign-out failed."
                                    }
                                )
                                isSubmitting = false
                            }
                        },
                        enabled = !isSubmitting
                    ) {
                        if (isSubmitting) {
                            CircularProgressIndicator()
                        } else {
                            Text("Sign out")
                        }
                    }
                }
            }
        } ?: run {
            Surface(
                tonalElevation = 2.dp,
                shape = MaterialTheme.shapes.medium
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = "Demo credentials",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        text = "${defaultUsername(portal)} / password",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    OutlinedTextField(
                        value = username,
                        onValueChange = { username = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Username") },
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = password,
                        onValueChange = { password = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Password") },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation()
                    )
                    Button(
                        onClick = {
                            scope.launch {
                                isSubmitting = true
                                message = null
                                isErrorMessage = false
                                val result = onSignIn(username.trim(), password)
                                message = result.fold(
                                    onSuccess = { "Signed in to the $portal portal." },
                                    onFailure = {
                                        isErrorMessage = true
                                        it.message ?: "Sign-in failed."
                                    }
                                )
                                isSubmitting = false
                            }
                        },
                        enabled = !isSubmitting && username.isNotBlank() && password.isNotBlank()
                    ) {
                        if (isSubmitting) {
                            CircularProgressIndicator()
                        } else {
                            Text("Sign in")
                        }
                    }
                    onContinueAsGuest?.let { continueAsGuest ->
                        Button(
                            onClick = {
                                scope.launch {
                                    isSubmitting = true
                                    message = null
                                    isErrorMessage = false
                                    val result = continueAsGuest()
                                    message = result.fold(
                                        onSuccess = { "Continuing as guest in the $portal portal." },
                                        onFailure = {
                                            isErrorMessage = true
                                            it.message ?: "Guest access failed."
                                        }
                                    )
                                    isSubmitting = false
                                }
                            },
                            enabled = !isSubmitting
                        ) {
                            if (isSubmitting) {
                                CircularProgressIndicator()
                            } else {
                                Text("Continue as guest")
                            }
                        }
                    }
                }
            }
        }
        message?.let { status ->
            Text(
                text = status,
                style = MaterialTheme.typography.bodyMedium,
                color = if (isErrorMessage) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
            )
        }
    }
}

private fun defaultUsername(portal: String): String = when (portal) {
    "buyer" -> "buyer.demo"
    "seller" -> "seller.demo"
    else -> ""
}
