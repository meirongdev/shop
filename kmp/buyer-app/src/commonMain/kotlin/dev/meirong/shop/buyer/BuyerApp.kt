package dev.meirong.shop.buyer

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
import androidx.navigation.toRoute
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import dev.meirong.shop.kmp.feature.auth.ui.AuthScreen
import dev.meirong.shop.kmp.feature.cart.data.BuyerCartRepository
import dev.meirong.shop.kmp.feature.cart.ui.BuyerCartScreen
import dev.meirong.shop.kmp.feature.auth.data.AuthRepository
import dev.meirong.shop.kmp.core.model.AuthSession
import dev.meirong.shop.kmp.core.session.MutableTokenStorage
import dev.meirong.shop.kmp.feature.marketplace.data.BuyerMarketplaceRepository
import dev.meirong.shop.kmp.feature.marketplace.ui.BuyerMarketplaceScreen
import dev.meirong.shop.kmp.feature.marketplace.ui.ProductDetailScreen
import dev.meirong.shop.kmp.feature.order.data.BuyerOrderRepository
import dev.meirong.shop.kmp.feature.order.ui.BuyerOrderDetailScreen
import dev.meirong.shop.kmp.feature.order.ui.BuyerOrderListScreen
import dev.meirong.shop.kmp.feature.profile.data.BuyerProfileRepository
import dev.meirong.shop.kmp.feature.profile.ui.BuyerProfileScreen
import dev.meirong.shop.kmp.feature.promotion.data.BuyerPromotionRepository
import dev.meirong.shop.kmp.feature.promotion.ui.BuyerPromotionScreen
import dev.meirong.shop.kmp.feature.wallet.data.BuyerWalletRepository
import dev.meirong.shop.kmp.feature.wallet.ui.BuyerWalletScreen
import dev.meirong.shop.kmp.ui.components.AuthRequiredScreen
import dev.meirong.shop.kmp.ui.navigation.ShopAppDestination
import dev.meirong.shop.kmp.ui.navigation.ShopAppScaffold
import dev.meirong.shop.kmp.ui.theme.ShopTheme
import kotlinx.serialization.Serializable

@Composable
fun BuyerApp(e2e: BuyerAppE2eConfig = BuyerAppE2eConfig()) {
    ShopTheme {
        val navController = rememberNavController()
        val tokenStorage = remember { MutableTokenStorage() }
        val authRepository = remember { AuthRepository() }
        val marketplaceRepository = remember { BuyerMarketplaceRepository(tokenStorage = tokenStorage) }
        val cartRepository = remember { BuyerCartRepository(tokenStorage = tokenStorage) }
        val orderRepository = remember { BuyerOrderRepository(tokenStorage = tokenStorage) }
        val walletRepository = remember { BuyerWalletRepository(tokenStorage = tokenStorage) }
        val profileRepository = remember { BuyerProfileRepository(tokenStorage = tokenStorage) }
        val promotionRepository = remember { BuyerPromotionRepository(tokenStorage = tokenStorage) }
        var buyerSession by remember { mutableStateOf(e2e.session) }
        var autoLoginAttempted by remember { mutableStateOf(false) }
        val knownRoutes = remember { buyerDestinations.map { it.route }.toSet() }
        val targetRoute = e2e.initialRoute?.takeIf { it in knownRoutes } ?: BuyerRoutes.Marketplace
        val startDestination = if (e2e.enabled && !e2e.autoLogin && !e2e.guestLogin) targetRoute else buyerDestinations.first().route
        val navBackStackEntry by navController.currentBackStackEntryAsState()
        val currentRoute = navBackStackEntry?.destination?.route ?: startDestination
        val signedInBuyerSession = buyerSession?.takeUnless { it.isGuestBuyer() }
        val visibleDestinations = remember(buyerSession) {
            if (buyerSession.isGuestBuyer()) {
                buyerDestinations.filterNot { it.route in buyerGuestRestrictedRoutes }
            } else {
                buyerDestinations
            }
        }

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
                    BuyerAppE2eState(
                        route = route,
                        status = status,
                        username = buyerSession?.username,
                        message = message
                    )
                )
            }
        }

        DisposableEffect(authRepository, marketplaceRepository, cartRepository, orderRepository, walletRepository, profileRepository, promotionRepository) {
            onDispose {
                authRepository.close()
                marketplaceRepository.close()
                cartRepository.close()
                orderRepository.close()
                walletRepository.close()
                profileRepository.close()
                promotionRepository.close()
            }
        }

        LaunchedEffect(e2e.session?.accessToken) {
            val session = e2e.session ?: return@LaunchedEffect
            buyerSession = session
            tokenStorage.saveTokens(session.accessToken, session.accessToken)
        }

        LaunchedEffect(e2e.enabled, e2e.autoLogin, e2e.guestLogin, targetRoute) {
            if (!e2e.enabled || (!e2e.autoLogin && !e2e.guestLogin) || e2e.session != null || autoLoginAttempted || buyerSession != null) {
                return@LaunchedEffect
            }

            autoLoginAttempted = true
            reportE2e(BuyerRoutes.Auth, "authenticating")
            val result = if (e2e.guestLogin) {
                runCatching { authRepository.loginGuest("buyer") }
            } else {
                runCatching { authRepository.login("buyer.demo", "password", "buyer") }
            }
            val session = result.getOrNull()
            if (session != null) {
                buyerSession = session
                tokenStorage.saveTokens(session.accessToken, session.accessToken)
                if (targetRoute != currentRoute) {
                    navigateToRoute(targetRoute)
                }
            } else {
                val error = result.exceptionOrNull()
                reportE2e(BuyerRoutes.Auth, "error", error?.message ?: "Buyer sign-in failed.")
            }
        }

        LaunchedEffect(e2e.enabled, e2e.autoLogin, e2e.guestLogin, targetRoute, currentRoute) {
            if (e2e.enabled && !e2e.autoLogin && !e2e.guestLogin && targetRoute != currentRoute) {
                navigateToRoute(targetRoute)
            }
        }

        LaunchedEffect(currentRoute, buyerSession?.username, e2e.enabled, e2e.autoLogin, e2e.guestLogin) {
            if (!e2e.enabled) {
                return@LaunchedEffect
            }
            if (currentRoute == BuyerRoutes.Auth && (!e2e.autoLogin && !e2e.guestLogin || buyerSession != null)) {
                reportE2e(BuyerRoutes.Auth, "ready")
                return@LaunchedEffect
            }
            if (buyerSession != null && currentRoute in knownRoutes) {
                reportE2e(currentRoute, "ready")
            }
        }

        LaunchedEffect(buyerSession, currentRoute) {
            if (buyerSession.isGuestBuyer() && currentRoute in buyerGuestRestrictedRoutes) {
                navController.navigate(BuyerRoutes.Cart) {
                    launchSingleTop = true
                    popUpTo(navController.graph.findStartDestination().id) {
                        saveState = true
                    }
                }
            }
        }

        ShopAppScaffold(
            appTitle = "Shop Buyer",
            destinations = visibleDestinations,
            currentRoute = currentRoute,
            onDestinationSelected = { destination ->
                navigateToRoute(destination.route)
            }
        ) { padding ->
            NavHost(
                navController = navController,
                startDestination = buyerDestinations.first().route,
                modifier = Modifier.padding(padding)
            ) {
                composable(BuyerRoutes.Marketplace) {
                    buyerSession?.let {
                        BuyerMarketplaceScreen(
                            repository = marketplaceRepository,
                            onProductSelected = { productId ->
                                navController.navigate(BuyerProductDetail(productId))
                            }
                        )
                    } ?: BuyerAuthRequired(
                        onNavigateToAuth = {
                            navController.navigate(BuyerRoutes.Auth) { launchSingleTop = true }
                        }
                    )
                }
                composable<BuyerProductDetail> { backStackEntry ->
                    val route = backStackEntry.toRoute<BuyerProductDetail>()
                    val session = buyerSession
                    if (session == null) {
                        BuyerAuthRequired(
                            onNavigateToAuth = {
                                navController.navigate(BuyerRoutes.Auth) { launchSingleTop = true }
                            }
                        )
                    } else {
                        ProductDetailScreen(
                            productId = route.productId,
                            repository = marketplaceRepository,
                            onBack = { navController.popBackStack() },
                            onAddToCart = { product ->
                                runCatching {
                                    cartRepository.addToCart(
                                        buyerId = session.principalId,
                                        product = product,
                                        quantity = 1
                                    )
                                }.map { "Added ${product.name} to your buyer cart." }
                            }
                        )
                    }
                }
                composable(BuyerRoutes.Cart) {
                    buyerSession?.let { session ->
                        BuyerCartScreen(
                            repository = cartRepository,
                            buyerId = session.principalId,
                            allowCheckout = !session.isGuestBuyer(),
                            onBrowseMarketplace = {
                                navController.navigate(BuyerRoutes.Marketplace) {
                                    launchSingleTop = true
                                }
                            },
                            onOpenWallet = {
                                navController.navigate(BuyerRoutes.Wallet) {
                                    launchSingleTop = true
                                }
                            },
                            onRequireSignedInBuyer = {
                                navController.navigate(BuyerRoutes.Auth) {
                                    launchSingleTop = true
                                }
                            },
                            onCheckoutSuccess = {
                                navController.navigate(BuyerRoutes.Orders) {
                                    launchSingleTop = true
                                }
                            }
                        )
                    } ?: BuyerAuthRequired(
                        onNavigateToAuth = {
                            navController.navigate(BuyerRoutes.Auth) { launchSingleTop = true }
                        }
                    )
                }
                composable(BuyerRoutes.Orders) {
                    signedInBuyerSession?.let { session ->
                        BuyerOrderListScreen(
                            repository = orderRepository,
                            buyerId = session.principalId,
                            onOrderSelected = { orderId ->
                                navController.navigate(BuyerOrderDetail(orderId))
                            }
                        )
                    } ?: if (buyerSession.isGuestBuyer()) {
                        BuyerSignInRequired(
                            onNavigateToAuth = {
                                navController.navigate(BuyerRoutes.Auth) { launchSingleTop = true }
                            }
                        )
                    } else {
                        BuyerAuthRequired(
                            onNavigateToAuth = {
                                navController.navigate(BuyerRoutes.Auth) { launchSingleTop = true }
                            }
                        )
                    }
                }
                composable<BuyerOrderDetail> { backStackEntry ->
                    val route = backStackEntry.toRoute<BuyerOrderDetail>()
                    signedInBuyerSession?.let { session ->
                        BuyerOrderDetailScreen(
                            orderId = route.orderId,
                            buyerId = session.principalId,
                            repository = orderRepository,
                            onBack = { navController.popBackStack() }
                        )
                    } ?: if (buyerSession.isGuestBuyer()) {
                        BuyerSignInRequired(
                            onNavigateToAuth = {
                                navController.navigate(BuyerRoutes.Auth) { launchSingleTop = true }
                            }
                        )
                    } else {
                        BuyerAuthRequired(
                            onNavigateToAuth = {
                                navController.navigate(BuyerRoutes.Auth) { launchSingleTop = true }
                            }
                        )
                    }
                }
                composable(BuyerRoutes.Wallet) {
                    signedInBuyerSession?.let { session ->
                        BuyerWalletScreen(
                            repository = walletRepository,
                            buyerId = session.principalId,
                            onOpenCart = {
                                navController.navigate(BuyerRoutes.Cart) {
                                    launchSingleTop = true
                                }
                            }
                        )
                    } ?: if (buyerSession.isGuestBuyer()) {
                        BuyerSignInRequired(
                            onNavigateToAuth = {
                                navController.navigate(BuyerRoutes.Auth) { launchSingleTop = true }
                            }
                        )
                    } else {
                        BuyerAuthRequired(
                            onNavigateToAuth = {
                                navController.navigate(BuyerRoutes.Auth) { launchSingleTop = true }
                            }
                        )
                    }
                }
                composable(BuyerRoutes.Promotions) {
                    buyerSession?.let {
                        BuyerPromotionScreen(
                            repository = promotionRepository
                        )
                    } ?: BuyerAuthRequired(
                        onNavigateToAuth = {
                            navController.navigate(BuyerRoutes.Auth) { launchSingleTop = true }
                        }
                    )
                }
                composable(BuyerRoutes.Profile) {
                    if (buyerSession.isGuestBuyer()) {
                        BuyerSignInRequired(
                            onNavigateToAuth = {
                                navController.navigate(BuyerRoutes.Auth) { launchSingleTop = true }
                            }
                        )
                    } else if (signedInBuyerSession != null) {
                        BuyerProfileScreen(
                            repository = profileRepository,
                            buyerId = signedInBuyerSession.principalId
                        )
                    } else {
                        BuyerAuthRequired(
                            onNavigateToAuth = {
                                navController.navigate(BuyerRoutes.Auth) { launchSingleTop = true }
                            }
                        )
                    }
                }
                composable(BuyerRoutes.Auth) {
                    AuthScreen(
                        title = "Buyer Sign In",
                        description = "Buyer KMP now supports both real sign-in and guest buyer access through auth-server issued bearer tokens. Guest buyers can browse marketplace content and manage a cart, while checkout, wallet, orders, and profile stay behind full buyer sign-in.",
                        portal = "buyer",
                        session = buyerSession,
                        onSignIn = { username, password ->
                            val result = runCatching { authRepository.login(username, password, "buyer") }
                            result.getOrNull()?.let { session ->
                                buyerSession = session
                                tokenStorage.saveTokens(session.accessToken, session.accessToken)
                            }
                            result
                        },
                        onContinueAsGuest = {
                            val result = runCatching { authRepository.loginGuest("buyer") }
                            result.getOrNull()?.let { session ->
                                buyerSession = session
                                tokenStorage.saveTokens(session.accessToken, session.accessToken)
                            }
                            result
                        },
                        onSignOut = {
                            buyerSession = null
                            tokenStorage.clear()
                        }
                    )
                }
            }
        }
    }
}

private object BuyerRoutes {
    const val Marketplace = "marketplace"
    const val Cart = "cart"
    const val Orders = "orders"
    const val Wallet = "wallet"
    const val Promotions = "promotions"
    const val Profile = "profile"
    const val Auth = "auth"
}

@Serializable
private data class BuyerProductDetail(val productId: String)

@Serializable
private data class BuyerOrderDetail(val orderId: String)

@Composable
private fun BuyerAuthRequired(
    onNavigateToAuth: () -> Unit
) {
    AuthRequiredScreen(
        title = "Buyer access required",
        description = "The buyer KMP app now talks to authenticated gateway routes, and the auth screen can either sign in or continue as a guest buyer for browsing and cart access.",
        actionLabel = "Open buyer access",
        onAction = onNavigateToAuth
    )
}

@Composable
private fun BuyerSignInRequired(
    onNavigateToAuth: () -> Unit
) {
    AuthRequiredScreen(
        title = "Buyer sign-in required",
        description = "Guest buyers can browse the catalog and build a cart, but wallet, checkout, orders, and profile need a signed-in buyer account.",
        actionLabel = "Sign in as buyer",
        onAction = onNavigateToAuth
    )
}

private fun AuthSession?.isGuestBuyer(): Boolean = this?.roles?.contains("ROLE_BUYER_GUEST") == true

private val buyerGuestRestrictedRoutes = setOf(
    BuyerRoutes.Orders,
    BuyerRoutes.Wallet,
    BuyerRoutes.Profile
)

private val buyerDestinations = listOf(
    ShopAppDestination(
        route = BuyerRoutes.Marketplace,
        label = "Marketplace",
        summary = "Browse catalog foundations and search behavior."
    ),
    ShopAppDestination(
        route = BuyerRoutes.Cart,
        label = "Cart",
        summary = "Build a cart, then sign in for checkout."
    ),
    ShopAppDestination(
        route = BuyerRoutes.Orders,
        label = "Orders",
        summary = "Track current and historical signed-in buyer orders."
    ),
    ShopAppDestination(
        route = BuyerRoutes.Wallet,
        label = "Wallet",
        summary = "Manage signed-in buyer balance and payment state."
    ),
    ShopAppDestination(
        route = BuyerRoutes.Promotions,
        label = "Promotions",
        summary = "Discover active campaigns and coupons."
    ),
    ShopAppDestination(
        route = BuyerRoutes.Profile,
        label = "Profile",
        summary = "Reach account preferences and profile details."
    ),
    ShopAppDestination(
        route = BuyerRoutes.Auth,
        label = "Auth",
        summary = "Host buyer sign-in and guest browsing entry points."
    )
)
