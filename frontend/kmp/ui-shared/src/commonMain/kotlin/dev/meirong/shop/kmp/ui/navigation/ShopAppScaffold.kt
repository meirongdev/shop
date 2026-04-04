package dev.meirong.shop.kmp.ui.navigation

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.NavigationDrawerItemDefaults
import androidx.compose.material3.PermanentDrawerSheet
import androidx.compose.material3.PermanentNavigationDrawer
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

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
    val currentDestination = destinations.firstOrNull { it.route == currentRoute } ?: destinations.first()

    PermanentNavigationDrawer(
        drawerContent = {
            PermanentDrawerSheet(modifier = Modifier.width(260.dp).fillMaxSize()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(text = appTitle, style = MaterialTheme.typography.titleLarge)
                }
                HorizontalDivider()
                Spacer(modifier = Modifier.height(4.dp))
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
                ) {
                    destinations.forEach { destination ->
                        NavigationDrawerItem(
                            label = { Text(text = destination.label) },
                            selected = destination.route == currentDestination.route,
                            onClick = { onDestinationSelected(destination) },
                            modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
                        )
                    }
                }
            }
        }
    ) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Text(
                            text = currentDestination.label,
                            style = MaterialTheme.typography.titleMedium
                        )
                    }
                )
            },
            content = content
        )
    }
}
