package dev.meirong.shop.seller

import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
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
fun SellerApp(e2e: SellerAppE2eConfig = SellerAppE2eConfig()) {
    ShopTheme {
        val navController = rememberNavController()
        val tokenStorage = remember { MutableTokenStorage() }
        val authRepository = remember { AuthRepository() }
        val dashboardRepository = remember { SellerDashboardRepository(tokenStorage = tokenStorage) }
        val orderRepository = remember { SellerOrderRepository(tokenStorage = tokenStorage) }
        val promotionRepository = remember { SellerPromotionRepository(tokenStorage = tokenStorage) }
        val walletRepository = remember { SellerWalletRepository(tokenStorage = tokenStorage) }
        val profileRepository = remember { SellerProfileRepository(tokenStorage = tokenStorage) }
        var sellerSession by remember { mutableStateOf(e2e.session) }
        var autoLoginAttempted by remember { mutableStateOf(false) }
        val knownRoutes = remember { sellerDestinations.map { it.route }.toSet() }
        val targetRoute = e2e.initialRoute?.takeIf { it in knownRoutes } ?: SellerRoutes.Marketplace
        val startDestination = if (e2e.enabled && !e2e.autoLogin) targetRoute else sellerDestinations.first().route
        val navBackStackEntry by navController.currentBackStackEntryAsState()
        val currentRoute = navBackStackEntry?.destination?.route ?: startDestination

        fun navigateToRoute(route: String) {
            navController.navigate(route) {
                launchSingleTop = true
                restoreState = true
                popUpTo(navController.graph.findStartDestination().id) {
                    saveState = true
                }
            }
        }

        fun reportE2e(route: String, status: String, message: String? = null) {
            if (e2e.enabled) {
                e2e.onStateChange(
                    SellerAppE2eState(
                        route = route,
                        status = status,
                        username = sellerSession?.username,
                        message = message
                    )
                )
            }
        }

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

        LaunchedEffect(e2e.session?.accessToken) {
            val session = e2e.session ?: return@LaunchedEffect
            sellerSession = session
            tokenStorage.saveTokens(session.accessToken, session.accessToken)
        }

        LaunchedEffect(e2e.enabled, e2e.autoLogin, targetRoute) {
            if (!e2e.enabled || !e2e.autoLogin || e2e.session != null || autoLoginAttempted || sellerSession != null) {
                return@LaunchedEffect
            }

            autoLoginAttempted = true
            reportE2e(SellerRoutes.Auth, "authenticating")
            val result = runCatching { authRepository.login("seller.demo", "password", "seller") }
            val session = result.getOrNull()
            if (session != null) {
                sellerSession = session
                tokenStorage.saveTokens(session.accessToken, session.accessToken)
                if (targetRoute != currentRoute) {
                    navigateToRoute(targetRoute)
                }
            } else {
                val error = result.exceptionOrNull()
                reportE2e(SellerRoutes.Auth, "error", error?.message ?: "Seller sign-in failed.")
            }
        }

        LaunchedEffect(e2e.enabled, e2e.autoLogin, targetRoute, currentRoute) {
            if (e2e.enabled && !e2e.autoLogin && targetRoute != currentRoute) {
                navigateToRoute(targetRoute)
            }
        }

        LaunchedEffect(currentRoute, sellerSession?.username, e2e.enabled, e2e.autoLogin) {
            if (!e2e.enabled) {
                return@LaunchedEffect
            }
            if (currentRoute == SellerRoutes.Auth && (!e2e.autoLogin || sellerSession != null)) {
                reportE2e(SellerRoutes.Auth, "ready")
                return@LaunchedEffect
            }
            if (sellerSession != null && currentRoute in knownRoutes) {
                reportE2e(currentRoute, "ready")
            }
        }

        ShopAppScaffold(
            appTitle = "Shop Seller",
            destinations = sellerDestinations,
            currentRoute = currentRoute,
            onDestinationSelected = { destination ->
                navigateToRoute(destination.route)
            }
        ) { padding ->
            NavHost(
                navController = navController,
                startDestination = startDestination,
                modifier = Modifier.padding(padding)
            ) {
                composable(SellerRoutes.Marketplace) {
                    sellerSession?.let { session ->
                        SellerInventoryScreen(
                            repository = dashboardRepository,
                            sellerId = session.principalId,
                            onE2eStateChanged = { status, message ->
                                reportE2e(SellerRoutes.Marketplace, status, message)
                            }
                        )
                    } ?: SellerAuthRequired(
                        onNavigateToAuth = {
                            navigateToRoute(SellerRoutes.Auth)
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
                            },
                            onE2eStateChanged = { status, message ->
                                reportE2e(SellerRoutes.Orders, status, message)
                            }
                        )
                    } ?: SellerAuthRequired(
                        onNavigateToAuth = {
                            navigateToRoute(SellerRoutes.Auth)
                        }
                    )
                }
                composable<SellerOrderDetail> { backStackEntry ->
                    val session = sellerSession
                    if (session == null) {
                        SellerAuthRequired(
                            onNavigateToAuth = {
                                navigateToRoute(SellerRoutes.Auth)
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
                            sellerId = session.principalId,
                            onE2eStateChanged = { status, message ->
                                reportE2e(SellerRoutes.Wallet, status, message)
                            }
                        )
                    } ?: SellerAuthRequired(
                        onNavigateToAuth = {
                            navigateToRoute(SellerRoutes.Auth)
                        }
                    )
                }
                composable(SellerRoutes.Promotions) {
                    sellerSession?.let { session ->
                        SellerPromotionScreen(
                            repository = promotionRepository,
                            sellerId = session.principalId,
                            onE2eStateChanged = { status, message ->
                                reportE2e(SellerRoutes.Promotions, status, message)
                            }
                        )
                    } ?: SellerAuthRequired(
                        onNavigateToAuth = {
                            navigateToRoute(SellerRoutes.Auth)
                        }
                    )
                }
                composable(SellerRoutes.Profile) {
                    sellerSession?.let { session ->
                        SellerProfileScreen(
                            repository = profileRepository,
                            sellerId = session.principalId,
                            onE2eStateChanged = { status, message ->
                                reportE2e(SellerRoutes.Profile, status, message)
                            }
                        )
                    } ?: SellerAuthRequired(
                        onNavigateToAuth = {
                            navigateToRoute(SellerRoutes.Auth)
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
