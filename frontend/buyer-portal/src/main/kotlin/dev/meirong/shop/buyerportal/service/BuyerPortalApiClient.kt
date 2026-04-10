package dev.meirong.shop.buyerportal.service

import dev.meirong.shop.buyerportal.config.BuyerPortalProperties
import dev.meirong.shop.buyerportal.model.BuyerRegistrationForm
import dev.meirong.shop.buyerportal.model.BuyerSession
import dev.meirong.shop.buyerportal.model.ProfileForm
import dev.meirong.shop.common.api.ApiResponse
import dev.meirong.shop.contracts.activity.ActivityApi
import dev.meirong.shop.contracts.auth.AuthApi
import dev.meirong.shop.contracts.buyer.BuyerApi
import dev.meirong.shop.contracts.loyalty.LoyaltyApi
import dev.meirong.shop.contracts.marketplace.MarketplaceApi
import dev.meirong.shop.contracts.order.OrderApi
import dev.meirong.shop.contracts.profile.ProfileApi
import dev.meirong.shop.contracts.search.SearchApi
import dev.meirong.shop.contracts.wallet.WalletApi
import org.springframework.core.ParameterizedTypeReference
import org.springframework.stereotype.Service
import org.springframework.web.client.RestClient
import java.math.BigDecimal

@Service
class BuyerPortalApiClient(
    builder: RestClient.Builder,
    private val properties: BuyerPortalProperties
) {
    private val restClient = builder.build()

    fun login(username: String, password: String): BuyerSession {
        return authPost(
            AuthApi.LOGIN,
            AuthApi.LoginRequest(username, password, "buyer")
        )
    }

    fun register(form: BuyerRegistrationForm): BuyerSession {
        return authPost(
            AuthApi.BUYER_REGISTER,
            AuthApi.BuyerRegisterRequest(
                form.username,
                form.email,
                form.password,
                form.inviteCode.ifBlank { null }
            )
        )
    }

    fun sendOtp(phoneNumber: String, locale: String): AuthApi.OtpSendResponse {
        val response = restClient.post()
            .uri(properties.authBaseUrl + AuthApi.OTP_SEND)
            .body(AuthApi.OtpSendRequest(phoneNumber, locale))
            .retrieve()
            .body(object : ParameterizedTypeReference<ApiResponse<AuthApi.OtpSendResponse>>() {})
            ?: error("Empty auth response")
        return response.data() ?: error("Missing auth payload")
    }

    fun verifyOtp(phoneNumber: String, otp: String): BuyerSession {
        return authPost(
            AuthApi.OTP_VERIFY,
            AuthApi.OtpVerifyRequest(phoneNumber, otp, "buyer")
        )
    }

    fun loginWithApple(idToken: String, nonce: String): BuyerSession {
        return authPost(
            AuthApi.OAUTH2_APPLE,
            AuthApi.OAuth2AppleRequest(idToken, nonce, "buyer")
        )
    }

    fun issueGuestSession(): BuyerSession {
        return authPost(AuthApi.GUEST, AuthApi.GuestTokenRequest("buyer"))
    }

    fun mergeGuestCart(session: BuyerSession, guestPlayerId: String): OrderApi.CartView =
        authedPost(session, BuyerApi.CART_MERGE, BuyerApi.MergeGuestCartRequest(guestPlayerId),
            object : ParameterizedTypeReference<ApiResponse<OrderApi.CartView>>() {})

    fun getWelcomeSummary(session: BuyerSession): BuyerApi.WelcomeSummaryResponse =
        authedGet(session, BuyerApi.WELCOME_SUMMARY,
            object : ParameterizedTypeReference<ApiResponse<BuyerApi.WelcomeSummaryResponse>>() {})

    fun getInviteStats(session: BuyerSession): BuyerApi.InviteStatsResponse =
        authedGet(session, BuyerApi.INVITE_STATS,
            object : ParameterizedTypeReference<ApiResponse<BuyerApi.InviteStatsResponse>>() {})

    private fun toSession(data: AuthApi.TokenResponse): BuyerSession {
        val roles = data.roles()
        return BuyerSession(
            token = data.accessToken(),
            principalId = data.principalId(),
            username = data.username(),
            displayName = data.displayName(),
            portal = data.portal(),
            roles = roles,
            guest = roles.contains("ROLE_BUYER_GUEST"),
            newUser = data.newUser()
        )
    }

    private fun authPost(path: String, body: Any): BuyerSession {
        val response = restClient.post()
            .uri(properties.authBaseUrl + path)
            .body(body)
            .retrieve()
            .body(object : ParameterizedTypeReference<ApiResponse<AuthApi.TokenResponse>>() {})
            ?: error("Empty auth response")
        val data = response.data() ?: error("Missing auth payload")
        return toSession(data)
    }

    fun dashboard(session: BuyerSession): BuyerApi.DashboardResponse =
        authedPost(session, BuyerApi.DASHBOARD, BuyerApi.BuyerContextRequest(session.principalId),
            object : ParameterizedTypeReference<ApiResponse<BuyerApi.DashboardResponse>>() {})

    fun updateProfile(session: BuyerSession, form: ProfileForm): ProfileApi.ProfileResponse =
        authedPost(session, BuyerApi.PROFILE_UPDATE,
            ProfileApi.UpdateProfileRequest(session.principalId, form.displayName, form.email, form.tier),
            object : ParameterizedTypeReference<ApiResponse<ProfileApi.ProfileResponse>>() {})

    fun deposit(session: BuyerSession, amount: BigDecimal, currency: String): WalletApi.TransactionResponse =
        authedPost(session, BuyerApi.DEPOSIT, WalletApi.DepositRequest(session.principalId, amount, currency),
            object : ParameterizedTypeReference<ApiResponse<WalletApi.TransactionResponse>>() {})

    fun withdraw(session: BuyerSession, amount: BigDecimal, currency: String): WalletApi.TransactionResponse =
        authedPost(session, BuyerApi.WITHDRAW, WalletApi.WithdrawRequest(session.principalId, amount, currency),
            object : ParameterizedTypeReference<ApiResponse<WalletApi.TransactionResponse>>() {})

    // ── New e-commerce methods ──

    fun searchProducts(session: BuyerSession, query: String?, categoryId: String?, page: Int = 0): SearchApi.SearchProductsResponse =
        authedPost(session, BuyerApi.PRODUCT_SEARCH,
            MarketplaceApi.SearchProductsRequest(query, categoryId, page, 12),
            object : ParameterizedTypeReference<ApiResponse<SearchApi.SearchProductsResponse>>() {})

    fun getProduct(session: BuyerSession, productId: String): MarketplaceApi.ProductResponse =
        authedPost(session, BuyerApi.PRODUCT_GET, MarketplaceApi.GetProductRequest(productId),
            object : ParameterizedTypeReference<ApiResponse<MarketplaceApi.ProductResponse>>() {})

    fun listCategories(session: BuyerSession): List<MarketplaceApi.CategoryResponse> =
        authedPost(session, BuyerApi.CATEGORY_LIST, mapOf<String, Any>(),
            object : ParameterizedTypeReference<ApiResponse<List<MarketplaceApi.CategoryResponse>>>() {})

    fun getSellerShop(session: BuyerSession, sellerId: String): ProfileApi.SellerStorefrontResponse =
        authedPost(session, BuyerApi.SELLER_SHOP, ProfileApi.GetProfileRequest(sellerId),
            object : ParameterizedTypeReference<ApiResponse<ProfileApi.SellerStorefrontResponse>>() {})

    fun listCart(session: BuyerSession): OrderApi.CartView =
        authedPost(session, BuyerApi.CART_LIST, BuyerApi.BuyerContextRequest(session.principalId),
            object : ParameterizedTypeReference<ApiResponse<OrderApi.CartView>>() {})

    fun getLoyaltyAccount(session: BuyerSession): LoyaltyApi.AccountResponse =
        authedGet(session, BuyerApi.LOYALTY_ACCOUNT,
            object : ParameterizedTypeReference<ApiResponse<LoyaltyApi.AccountResponse>>() {})

    fun getLoyaltyHub(session: BuyerSession): BuyerApi.LoyaltyHubResponse =
        authedGet(session, BuyerApi.LOYALTY_HUB,
            object : ParameterizedTypeReference<ApiResponse<BuyerApi.LoyaltyHubResponse>>() {})

    fun listActivityGames(session: BuyerSession): List<ActivityApi.GameResponse> =
        authedGet(session, ActivityApi.GAMES,
            object : ParameterizedTypeReference<ApiResponse<List<ActivityApi.GameResponse>>>() {})

    fun getActivityGame(session: BuyerSession, gameId: String): ActivityApi.GameDetailResponse =
        authedGet(session, ActivityApi.GAME_INFO.replace("{gameId}", gameId),
            object : ParameterizedTypeReference<ApiResponse<ActivityApi.GameDetailResponse>>() {})

    fun getActivityHistory(session: BuyerSession, gameId: String): List<ActivityApi.ParticipationResponse> =
        authedGet(session, ActivityApi.MY_HISTORY.replace("{gameId}", gameId),
            object : ParameterizedTypeReference<ApiResponse<List<ActivityApi.ParticipationResponse>>>() {})

    fun participateInActivity(session: BuyerSession, gameId: String, payload: String?): ActivityApi.ParticipateResponse =
        authedPost(session, ActivityApi.PARTICIPATE.replace("{gameId}", gameId), ActivityApi.ParticipateRequest(payload),
            object : ParameterizedTypeReference<ApiResponse<ActivityApi.ParticipateResponse>>() {})

    fun loyaltyCheckin(session: BuyerSession): LoyaltyApi.CheckinResponse =
        authedPost(session, BuyerApi.LOYALTY_CHECKIN, mapOf<String, Any>(),
            object : ParameterizedTypeReference<ApiResponse<LoyaltyApi.CheckinResponse>>() {})

    fun redeemLoyaltyReward(session: BuyerSession, rewardItemId: String, quantity: Int): LoyaltyApi.RedemptionResponse =
        authedPost(session, BuyerApi.LOYALTY_REDEEM, LoyaltyApi.RedeemRequest(rewardItemId, quantity),
            object : ParameterizedTypeReference<ApiResponse<LoyaltyApi.RedemptionResponse>>() {})

    fun addToCart(session: BuyerSession, productId: String, productName: String, productPrice: BigDecimal,
                  sellerId: String, quantity: Int): OrderApi.CartItemResponse =
        authedPost(session, BuyerApi.CART_ADD,
            OrderApi.AddToCartRequest(session.principalId, productId, productName, productPrice, sellerId, quantity),
            object : ParameterizedTypeReference<ApiResponse<OrderApi.CartItemResponse>>() {})

    fun updateCart(session: BuyerSession, productId: String, quantity: Int): OrderApi.CartItemResponse =
        authedPost(session, BuyerApi.CART_UPDATE,
            OrderApi.UpdateCartRequest(session.principalId, productId, quantity),
            object : ParameterizedTypeReference<ApiResponse<OrderApi.CartItemResponse>>() {})

    fun removeFromCart(session: BuyerSession, productId: String) {
        authedPost(session, BuyerApi.CART_REMOVE,
            OrderApi.RemoveFromCartRequest(session.principalId, productId),
            object : ParameterizedTypeReference<ApiResponse<Void>>() {})
    }

    fun checkout(session: BuyerSession, couponCode: String?, paymentMethod: String?, pointsToUse: Long? = null): BuyerApi.CheckoutResponse =
        authedPost(session, BuyerApi.CHECKOUT_CREATE,
            BuyerApi.CheckoutRequest(session.principalId, couponCode, paymentMethod, pointsToUse),
            object : ParameterizedTypeReference<ApiResponse<BuyerApi.CheckoutResponse>>() {})

    fun listPaymentMethods(session: BuyerSession): List<WalletApi.PaymentMethodInfo> =
        authedGet(session, BuyerApi.PAYMENT_METHODS,
            object : ParameterizedTypeReference<ApiResponse<List<WalletApi.PaymentMethodInfo>>>() {})

    fun listOrders(session: BuyerSession): List<OrderApi.OrderResponse> =
        authedPost(session, BuyerApi.ORDER_LIST, BuyerApi.BuyerContextRequest(session.principalId),
            object : ParameterizedTypeReference<ApiResponse<List<OrderApi.OrderResponse>>>() {})

    fun getOrder(session: BuyerSession, orderId: String): OrderApi.OrderResponse =
        authedPost(session, BuyerApi.ORDER_GET, OrderApi.GetOrderRequest(orderId),
            object : ParameterizedTypeReference<ApiResponse<OrderApi.OrderResponse>>() {})

    fun cancelOrder(session: BuyerSession, orderId: String): OrderApi.OrderResponse =
        authedPost(session, BuyerApi.ORDER_CANCEL,
            OrderApi.CancelOrderRequest(orderId, "User cancelled"),
            object : ParameterizedTypeReference<ApiResponse<OrderApi.OrderResponse>>() {})

    fun getWallet(session: BuyerSession): WalletApi.WalletAccountResponse =
        authedPost(session, BuyerApi.WALLET, BuyerApi.BuyerContextRequest(session.principalId),
            object : ParameterizedTypeReference<ApiResponse<WalletApi.WalletAccountResponse>>() {})

    // ── Guest Shopping ──

    fun guestCheckout(guestEmail: String, productId: String, productName: String,
                      productPrice: BigDecimal, sellerId: String, quantity: Int): OrderApi.OrderResponse {
        val response = restClient.post()
            .uri(properties.gatewayBaseUrl + "/public" + BuyerApi.GUEST_CHECKOUT)
            .body(OrderApi.GuestCheckoutRequest(guestEmail, productId, productName, productPrice, sellerId, quantity))
            .retrieve()
            .body(object : ParameterizedTypeReference<ApiResponse<OrderApi.OrderResponse>>() {})
            ?: error("Empty response from guest checkout")
        return response.data() ?: error("Missing payload from guest checkout")
    }

    fun trackOrder(orderToken: String): OrderApi.OrderResponse {
        val response = restClient.get()
            .uri(properties.gatewayBaseUrl + "/public" + BuyerApi.GUEST_ORDER_TRACK + "?token=$orderToken")
            .retrieve()
            .body(object : ParameterizedTypeReference<ApiResponse<OrderApi.OrderResponse>>() {})
            ?: error("Empty response from order track")
        return response.data() ?: error("Missing payload from order track")
    }

    private fun <T> authedGet(session: BuyerSession, path: String,
                              typeRef: ParameterizedTypeReference<ApiResponse<T>>): T {
        val response = restClient.get()
            .uri(properties.gatewayBaseUrl + "/api" + path)
            .header("Authorization", "Bearer ${session.token}")
            .retrieve()
            .body(typeRef)
            ?: error("Empty response from $path")
        return response.data() ?: error("Missing payload from $path")
    }

    private fun <T> authedPost(session: BuyerSession, path: String, body: Any,
                               typeRef: ParameterizedTypeReference<ApiResponse<T>>): T {
        val response = restClient.post()
            .uri(properties.gatewayBaseUrl + "/api" + path)
            .header("Authorization", "Bearer ${session.token}")
            .body(body)
            .retrieve()
            .body(typeRef)
            ?: error("Empty response from $path")
        return response.data() ?: error("Missing payload from $path")
    }
}
