package dev.meirong.shop.kmp.ui.navigation

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.NavigationDrawerItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

data class ShopAppDestination(
    val route: String,
    val label: String,
    val summary: String
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShopAppScaffold(
    appTitle: String,
    destinations: List<ShopAppDestination>,
    currentRoute: String?,
    onDestinationSelected: (ShopAppDestination) -> Unit,
    content: @Composable (PaddingValues) -> Unit
) {
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val currentDestination = destinations.firstOrNull { it.route == currentRoute } ?: destinations.first()

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet(
                modifier = Modifier
                    .widthIn(max = 360.dp)
                    .fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(text = appTitle, style = MaterialTheme.typography.titleLarge)
                    Text(
                        text = "Shared KMP feature modules are now routed from this app shell.",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
                HorizontalDivider()
                destinations.forEach { destination ->
                    NavigationDrawerItem(
                        label = {
                            Column {
                                Text(text = destination.label)
                                Text(
                                    text = destination.summary,
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        },
                        selected = destination.route == currentDestination.route,
                        onClick = {
                            onDestinationSelected(destination)
                            scope.launch { drawerState.close() }
                        },
                        modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
                    )
                }
            }
        }
    ) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Column {
                            Text(text = appTitle, style = MaterialTheme.typography.titleMedium)
                            Text(
                                text = currentDestination.label,
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    },
                    navigationIcon = {
                        TextButton(onClick = { scope.launch { drawerState.open() } }) {
                            Text("Menu")
                        }
                    }
                )
            },
            content = content
        )
    }
}
