package dev.meirong.shop.seller

import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import dev.meirong.shop.kmp.core.model.AuthSession
import dev.meirong.shop.kmp.core.session.MutableTokenStorage
import dev.meirong.shop.kmp.feature.auth.data.AuthRepository
import dev.meirong.shop.kmp.feature.auth.ui.AuthScreen
import dev.meirong.shop.kmp.feature.marketplace.data.SellerDashboardRepository
import dev.meirong.shop.kmp.feature.marketplace.ui.SellerInventoryScreen
import dev.meirong.shop.kmp.feature.order.data.SellerOrderRepository
import dev.meirong.shop.kmp.feature.order.ui.SellerOrderDetailScreen
import dev.meirong.shop.kmp.feature.order.ui.SellerOrderListScreen
import dev.meirong.shop.kmp.feature.profile.data.SellerProfileRepository
import dev.meirong.shop.kmp.feature.profile.ui.SellerProfileScreen
import dev.meirong.shop.kmp.feature.promotion.data.SellerPromotionRepository
import dev.meirong.shop.kmp.feature.promotion.ui.SellerPromotionScreen
import dev.meirong.shop.kmp.feature.wallet.data.SellerWalletRepository
import dev.meirong.shop.kmp.feature.wallet.ui.SellerWalletScreen
import dev.meirong.shop.kmp.ui.components.AuthRequiredScreen
import dev.meirong.shop.kmp.ui.navigation.ShopAppDestination
import dev.meirong.shop.kmp.ui.navigation.ShopAppScaffold
import dev.meirong.shop.kmp.ui.theme.ShopTheme
import kotlinx.serialization.Serializable

@Composable
fun SellerApp() {
    ShopTheme {
        val navController = rememberNavController()
        val tokenStorage = remember { MutableTokenStorage() }
        val authRepository = remember { AuthRepository() }
        val dashboardRepository = remember { SellerDashboardRepository(tokenStorage = tokenStorage) }
        val orderRepository = remember { SellerOrderRepository(tokenStorage = tokenStorage) }
        val promotionRepository = remember { SellerPromotionRepository(tokenStorage = tokenStorage) }
        val walletRepository = remember { SellerWalletRepository(tokenStorage = tokenStorage) }
        val profileRepository = remember { SellerProfileRepository(tokenStorage = tokenStorage) }
        var sellerSession by remember { mutableStateOf<AuthSession?>(null) }
        val navBackStackEntry by navController.currentBackStackEntryAsState()
        val currentRoute = navBackStackEntry?.destination?.route ?: sellerDestinations.first().route

        DisposableEffect(authRepository, dashboardRepository, orderRepository, promotionRepository, walletRepository, profileRepository) {
            onDispose {
                authRepository.close()
                dashboardRepository.close()
                orderRepository.close()
                promotionRepository.close()
                walletRepository.close()
                profileRepository.close()
            }
        }

        ShopAppScaffold(
            appTitle = "Shop Seller",
            destinations = sellerDestinations,
            currentRoute = currentRoute,
            onDestinationSelected = { destination ->
                navController.navigate(destination.route) {
                    launchSingleTop = true
                    restoreState = true
                    popUpTo(navController.graph.findStartDestination().id) {
                        saveState = true
                    }
                }
            }
        ) { padding ->
            NavHost(
                navController = navController,
                startDestination = sellerDestinations.first().route,
                modifier = Modifier.padding(padding)
            ) {
                composable(SellerRoutes.Marketplace) {
                    sellerSession?.let { session ->
                        SellerInventoryScreen(
                            repository = dashboardRepository,
                            sellerId = session.principalId
                        )
                    } ?: SellerAuthRequired(
                        onNavigateToAuth = {
                            navController.navigate(SellerRoutes.Auth) { launchSingleTop = true }
                        }
                    )
                }
                composable(SellerRoutes.Orders) {
                    sellerSession?.let { session ->
                        SellerOrderListScreen(
                            repository = orderRepository,
                            sellerId = session.principalId,
                            onOrderSelected = { orderId ->
                                navController.navigate(SellerOrderDetail(orderId))
                            }
                        )
                    } ?: SellerAuthRequired(
                        onNavigateToAuth = {
                            navController.navigate(SellerRoutes.Auth) { launchSingleTop = true }
                        }
                    )
                }
                composable<SellerOrderDetail> { backStackEntry ->
                    val session = sellerSession
                    if (session == null) {
                        SellerAuthRequired(
                            onNavigateToAuth = {
                                navController.navigate(SellerRoutes.Auth) { launchSingleTop = true }
                            }
                        )
                    } else {
                        val route = backStackEntry.toRoute<SellerOrderDetail>()
                        SellerOrderDetailScreen(
                            orderId = route.orderId,
                            repository = orderRepository,
                            onBack = { navController.popBackStack() }
                        )
                    }
                }
                composable(SellerRoutes.Wallet) {
                    sellerSession?.let { session ->
                        SellerWalletScreen(
                            repository = walletRepository,
                            sellerId = session.principalId
                        )
                    } ?: SellerAuthRequired(
                        onNavigateToAuth = {
                            navController.navigate(SellerRoutes.Auth) { launchSingleTop = true }
                        }
                    )
                }
                composable(SellerRoutes.Promotions) {
                    sellerSession?.let { session ->
                        SellerPromotionScreen(
                            repository = promotionRepository,
                            sellerId = session.principalId
                        )
                    } ?: SellerAuthRequired(
                        onNavigateToAuth = {
                            navController.navigate(SellerRoutes.Auth) { launchSingleTop = true }
                        }
                    )
                }
                composable(SellerRoutes.Profile) {
                    sellerSession?.let { session ->
                        SellerProfileScreen(
                            repository = profileRepository,
                            sellerId = session.principalId
                        )
                    } ?: SellerAuthRequired(
                        onNavigateToAuth = {
                            navController.navigate(SellerRoutes.Auth) { launchSingleTop = true }
                        }
                    )
                }
                composable(SellerRoutes.Auth) {
                    AuthScreen(
                        title = "Seller Sign In",
                        description = "Seller KMP now uses a real auth-server session so `/api/seller/**` routes can reach the authenticated gateway and seller BFF.",
                        portal = "seller",
                        session = sellerSession,
                        onSignIn = { username, password ->
                            val result = runCatching { authRepository.login(username, password, "seller") }
                            result.getOrNull()?.let { session ->
                                sellerSession = session
                                tokenStorage.saveTokens(session.accessToken, session.accessToken)
                            }
                            result
                        },
                        onSignOut = {
                            sellerSession = null
                            tokenStorage.clear()
                        }
                    )
                }
            }
        }
    }
}

private object SellerRoutes {
    const val Marketplace = "marketplace"
    const val Orders = "orders"
    const val Wallet = "wallet"
    const val Promotions = "promotions"
    const val Profile = "profile"
    const val Auth = "auth"
}

@Serializable
private data class SellerOrderDetail(val orderId: String)

@Composable
private fun SellerAuthRequired(
    onNavigateToAuth: () -> Unit
) {
    AuthRequiredScreen(
        title = "Seller sign-in required",
        description = "Seller inventory and order flows now use authenticated `/api/seller/**` routes behind the gateway.",
        actionLabel = "Open seller sign-in",
        onAction = onNavigateToAuth
    )
}

private val sellerDestinations = listOf(
    ShopAppDestination(
        route = SellerRoutes.Marketplace,
        label = "Inventory",
        summary = "Browse seller inventory, stock, and publish state."
    ),
    ShopAppDestination(
        route = SellerRoutes.Orders,
        label = "Orders",
        summary = "Manage seller orders and fulfillment flows."
    ),
    ShopAppDestination(
        route = SellerRoutes.Wallet,
        label = "Wallet",
        summary = "Track seller balance and settlement operations."
    ),
    ShopAppDestination(
        route = SellerRoutes.Promotions,
        label = "Promotions",
        summary = "Reach campaign management and statistics shells."
    ),
    ShopAppDestination(
        route = SellerRoutes.Profile,
        label = "Profile",
        summary = "Manage seller identity and account preferences."
    ),
    ShopAppDestination(
        route = SellerRoutes.Auth,
        label = "Auth",
        summary = "Sign in and recover seller sessions."
    )
)
