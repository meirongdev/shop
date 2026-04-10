package dev.meirong.shop.buyerportal.controller

import com.fasterxml.jackson.databind.ObjectMapper
import dev.meirong.shop.buyerportal.config.BuyerPortalProperties
import dev.meirong.shop.buyerportal.model.*
import dev.meirong.shop.buyerportal.service.BuyerPortalApiClient
import dev.meirong.shop.contracts.activity.ActivityApi
import jakarta.servlet.http.HttpSession
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Controller
import org.springframework.ui.Model
import org.springframework.web.bind.annotation.*
import org.springframework.web.client.RestClientResponseException
import org.springframework.web.servlet.mvc.support.RedirectAttributes
import org.springframework.web.servlet.view.RedirectView
import java.math.BigDecimal
import java.util.UUID

@Controller
class BuyerPortalController(
    private val apiClient: BuyerPortalApiClient,
    private val objectMapper: ObjectMapper,
    private val properties: BuyerPortalProperties
) {
    private fun currentSession(session: HttpSession): BuyerSession? =
        session.getAttribute("buyerSession") as? BuyerSession

    private fun shoppingSession(session: HttpSession): BuyerSession {
        return currentSession(session)
            ?: apiClient.issueGuestSession().also { session.setAttribute("buyerSession", it) }
    }

    private fun signedInSession(session: HttpSession): BuyerSession? =
        currentSession(session)?.takeUnless { it.guest }

    private fun seeOther(url: String) = RedirectView(url).apply { setStatusCode(HttpStatus.SEE_OTHER) }

    private fun completeAuthentication(session: HttpSession, buyerSession: BuyerSession): RedirectView {
        currentSession(session)
            ?.takeIf { it.guest }
            ?.let { apiClient.mergeGuestCart(buyerSession, it.principalId) }
        session.setAttribute("buyerSession", buyerSession)
        return seeOther(if (buyerSession.newUser) "/buyer/welcome" else "/buyer/home")
    }

    private fun paymentOptions(session: BuyerSession) =
        apiClient.listPaymentMethods(session)
            .filter { it.enabled() && it.method() in setOf("WALLET", "PAYPAL", "KLARNA") }

    private fun prepareLoginModel(
        model: Model,
        session: HttpSession,
        loginForm: LoginForm = LoginForm(),
        appleForm: AppleLoginForm = AppleLoginForm()
    ) {
        val existing = currentSession(session)
        val appleNonce = UUID.randomUUID().toString().replace("-", "")
        session.setAttribute("appleNonce", appleNonce)
        model.addAttribute("loginForm", loginForm)
        model.addAttribute("appleLoginForm", appleForm.copy(nonce = appleNonce))
        model.addAttribute("guestActive", existing?.guest == true)
        model.addAttribute("appleNonce", appleNonce)
        model.addAttribute("appleClientId", properties.appleClientId)
        model.addAttribute("appleRedirectUri", properties.appleRedirectUri)
        model.addAttribute("appleEnabled", !properties.appleClientId.contains("placeholder"))
    }

    private fun prepareRegisterModel(model: Model, form: BuyerRegistrationForm) {
        model.addAttribute("registrationForm", form)
    }

    private fun prepareOtpModel(
        model: Model,
        phoneForm: OtpPhoneForm = OtpPhoneForm(),
        verifyForm: OtpVerifyForm = OtpVerifyForm(phoneNumber = phoneForm.phoneNumber)
    ) {
        model.addAttribute("otpPhoneForm", phoneForm)
        model.addAttribute("otpVerifyForm", verifyForm)
    }

    private fun errorMessage(exception: Exception): String {
        if (exception is RestClientResponseException) {
            val body = exception.responseBodyAsString
            val detail = "\"detail\"\\s*:\\s*\"([^\"]+)\"".toRegex().find(body)?.groupValues?.getOrNull(1)
            val message = "\"message\"\\s*:\\s*\"([^\"]+)\"".toRegex().find(body)?.groupValues?.getOrNull(1)
            return (detail ?: message ?: exception.statusText).replace("\\\"", "\"")
        }
        return exception.message ?: "Request failed, please try again."
    }

    // ── Auth ──

    @GetMapping("/buyer")
    fun entry(): String = "redirect:/buyer/home"

    @GetMapping("/buyer/login")
    fun loginPage(model: Model, session: HttpSession): String {
        val existing = currentSession(session)
        if (existing != null && !existing.guest) {
            return "redirect:/buyer/home"
        }
        prepareLoginModel(model, session)
        return "buyer-login"
    }

    @PostMapping("/buyer/login")
    fun login(@ModelAttribute loginForm: LoginForm, model: Model, session: HttpSession): Any {
        return try {
            completeAuthentication(session, apiClient.login(loginForm.username, loginForm.password))
        } catch (exception: Exception) {
            prepareLoginModel(model, session, loginForm)
            model.addAttribute("errorMessage", errorMessage(exception))
            "buyer-login"
        }
    }

    @GetMapping("/buyer/register")
    fun registerPage(
        @RequestParam(required = false) invite: String?,
        model: Model,
        session: HttpSession
    ): String {
        val existing = currentSession(session)
        if (existing != null && !existing.guest) {
            return "redirect:/buyer/home"
        }
        prepareRegisterModel(model, BuyerRegistrationForm(inviteCode = invite ?: ""))
        return "buyer-register"
    }

    @PostMapping("/buyer/register")
    fun register(
        @ModelAttribute registrationForm: BuyerRegistrationForm,
        model: Model,
        session: HttpSession
    ): Any {
        if (registrationForm.password != registrationForm.confirmPassword) {
            prepareRegisterModel(model, registrationForm)
            model.addAttribute("errorMessage", "Passwords do not match.")
            return "buyer-register"
        }
        return try {
            completeAuthentication(session, apiClient.register(registrationForm))
        } catch (exception: Exception) {
            prepareRegisterModel(model, registrationForm)
            model.addAttribute("errorMessage", errorMessage(exception))
            "buyer-register"
        }
    }

    @GetMapping("/buyer/login/otp")
    fun otpLoginPage(model: Model, session: HttpSession): String {
        val existing = currentSession(session)
        if (existing != null && !existing.guest) {
            return "redirect:/buyer/home"
        }
        prepareOtpModel(model)
        return "buyer-login-otp"
    }

    @PostMapping("/buyer/login/otp/send")
    fun sendOtp(@ModelAttribute otpPhoneForm: OtpPhoneForm, model: Model, session: HttpSession): String {
        val existing = currentSession(session)
        if (existing != null && !existing.guest) {
            return "redirect:/buyer/home"
        }
        return try {
            val response = apiClient.sendOtp(otpPhoneForm.phoneNumber, otpPhoneForm.locale)
            prepareOtpModel(model, otpPhoneForm)
            model.addAttribute("otpResponse", response)
            model.addAttribute("successMessage", "OTP sent. It will expire in ${response.expiresIn()} seconds.")
            "buyer-login-otp"
        } catch (exception: Exception) {
            prepareOtpModel(model, otpPhoneForm)
            model.addAttribute("errorMessage", errorMessage(exception))
            "buyer-login-otp"
        }
    }

    @PostMapping("/buyer/login/otp/verify")
    fun verifyOtp(@ModelAttribute otpVerifyForm: OtpVerifyForm, model: Model, session: HttpSession): Any {
        return try {
            completeAuthentication(session, apiClient.verifyOtp(otpVerifyForm.phoneNumber, otpVerifyForm.otp))
        } catch (exception: Exception) {
            prepareOtpModel(model, OtpPhoneForm(phoneNumber = otpVerifyForm.phoneNumber))
            model.addAttribute("errorMessage", errorMessage(exception))
            "buyer-login-otp"
        }
    }

    @PostMapping("/buyer/login/apple")
    fun appleLogin(@ModelAttribute appleLoginForm: AppleLoginForm, model: Model, session: HttpSession): Any {
        val sessionNonce = session.getAttribute("appleNonce") as? String
        if (sessionNonce == null || sessionNonce != appleLoginForm.nonce) {
            prepareLoginModel(model, session)
            model.addAttribute("errorMessage", "Apple sign-in nonce is invalid. Please try again.")
            return "buyer-login"
        }
        return try {
            completeAuthentication(session, apiClient.loginWithApple(appleLoginForm.idToken, appleLoginForm.nonce))
        } catch (exception: Exception) {
            prepareLoginModel(model, session)
            model.addAttribute("errorMessage", errorMessage(exception))
            "buyer-login"
        }
    }

    @GetMapping("/buyer/logout")
    fun logout(session: HttpSession): String {
        session.invalidate()
        return "redirect:/buyer/home"
    }

    // ── Home / Marketplace ──

    @GetMapping("/buyer/home")
    fun home(model: Model, session: HttpSession,
             @RequestParam(required = false) q: String?,
             @RequestParam(required = false) category: String?,
             @RequestParam(defaultValue = "0") page: Int): String {
        val s = shoppingSession(session)
        val products = apiClient.searchProducts(s, q, category, page)
        val categories = apiClient.listCategories(s)
        val cart = apiClient.listCart(s)
        model.addAttribute("sessionUser", s)
        model.addAttribute("products", products)
        model.addAttribute("categories", categories)
        model.addAttribute("cartCount", cart.items().size)
        model.addAttribute("query", q ?: "")
        model.addAttribute("selectedCategory", category ?: "")
        return "buyer-home"
    }

    @GetMapping("/buyer/product/{id}")
    fun productDetail(@PathVariable id: String, model: Model, session: HttpSession): String {
        val s = shoppingSession(session)
        val product = apiClient.getProduct(s, id)
        val cart = apiClient.listCart(s)
        model.addAttribute("sessionUser", s)
        model.addAttribute("product", product)
        model.addAttribute("cartCount", cart.items().size)
        model.addAttribute("addToCartForm", AddToCartForm(productId = id))
        return "buyer-product"
    }

    // ── Seller Storefront ──

    @GetMapping("/buyer/shop/{sellerId}")
    fun sellerShop(@PathVariable sellerId: String, model: Model, session: HttpSession,
                   @RequestParam(required = false) q: String?,
                   @RequestParam(defaultValue = "0") page: Int): String {
        val s = shoppingSession(session)
        val shop = apiClient.getSellerShop(s, sellerId)
        val products = apiClient.searchProducts(s, q, null, page)
        val cart = apiClient.listCart(s)
        model.addAttribute("sessionUser", s)
        model.addAttribute("shop", shop)
        model.addAttribute("products", products)
        model.addAttribute("cartCount", cart.items().size)
        model.addAttribute("sellerId", sellerId)
        return "buyer-seller-shop"
    }

    // ── Cart ──

    @GetMapping("/buyer/cart")
    fun cart(model: Model, session: HttpSession): String {
        val s = shoppingSession(session)
        val cart = apiClient.listCart(s)
        val loyaltyAccount = if (s.guest) null else apiClient.getLoyaltyAccount(s)
        val paymentMethods = if (s.guest) emptyList() else paymentOptions(s)
        model.addAttribute("sessionUser", s)
        model.addAttribute("cart", cart)
        model.addAttribute("cartCount", cart.items().size)
        model.addAttribute("guestBuyer", s.guest)
        model.addAttribute("checkoutAllowed", !s.guest)
        model.addAttribute("loyaltyAccount", loyaltyAccount)
        model.addAttribute("paymentMethods", paymentMethods)
        model.addAttribute("checkoutForm", CheckoutForm(
            paymentMethod = paymentMethods.firstOrNull()?.method() ?: "WALLET"
        ))
        return "buyer-cart"
    }

    @PostMapping("/buyer/cart/add")
    fun addToCart(@RequestParam productId: String, @RequestParam productName: String,
                  @RequestParam productPrice: String, @RequestParam sellerId: String,
                  @RequestParam(defaultValue = "1") quantity: Int, session: HttpSession): RedirectView {
        val s = shoppingSession(session)
        apiClient.addToCart(s, productId, productName, BigDecimal(productPrice), sellerId, quantity)
        return seeOther("/buyer/cart")
    }

    @PostMapping("/buyer/cart/update")
    fun updateCart(@RequestParam productId: String, @RequestParam quantity: Int, session: HttpSession): RedirectView {
        val s = shoppingSession(session)
        if (quantity <= 0) {
            apiClient.removeFromCart(s, productId)
        } else {
            apiClient.updateCart(s, productId, quantity)
        }
        return seeOther("/buyer/cart")
    }

    @PostMapping("/buyer/cart/remove")
    fun removeFromCart(@RequestParam productId: String, session: HttpSession): RedirectView {
        val s = shoppingSession(session)
        apiClient.removeFromCart(s, productId)
        return seeOther("/buyer/cart")
    }

    // ── Checkout ──

    @PostMapping("/buyer/checkout/confirm")
    fun checkout(@ModelAttribute form: CheckoutForm, session: HttpSession, redirectAttributes: RedirectAttributes): RedirectView {
        val s = signedInSession(session) ?: return seeOther("/buyer/login")
        val couponCode = form.couponCode.ifBlank { null }
        val pointsToUse = form.pointsToUse?.takeIf { it > 0 }
        val checkout = apiClient.checkout(s, couponCode, form.paymentMethod.ifBlank { "WALLET" }, pointsToUse)
        checkout.paymentRedirectUrl()
            ?.takeIf { it.isNotBlank() }
            ?.let { return seeOther(it) }
        if (!checkout.paymentIntentClientSecret().isNullOrBlank()) {
            redirectAttributes.addFlashAttribute(
                "infoMessage",
                "Payment intent created for ${checkout.paymentMethod()}. This web portal currently auto-supports wallet and redirect providers."
            )
        }
        return seeOther("/buyer/orders")
    }

    // ── Loyalty ──

    @GetMapping("/buyer/loyalty")
    fun loyalty(model: Model, session: HttpSession): String {
        val s = signedInSession(session) ?: return "redirect:/buyer/login"
        val loyalty = apiClient.getLoyaltyHub(s)
        val cart = apiClient.listCart(s)
        model.addAttribute("sessionUser", s)
        model.addAttribute("loyalty", loyalty)
        model.addAttribute("cartCount", cart.items().size)
        model.addAttribute("redeemForm", RedeemRewardForm())
        return "buyer-loyalty"
    }

    @PostMapping("/buyer/loyalty/checkin")
    fun loyaltyCheckin(session: HttpSession): RedirectView {
        val s = signedInSession(session) ?: return seeOther("/buyer/login")
        apiClient.loyaltyCheckin(s)
        return seeOther("/buyer/loyalty")
    }

    @PostMapping("/buyer/loyalty/redeem")
    fun redeemLoyaltyReward(@ModelAttribute form: RedeemRewardForm, session: HttpSession): RedirectView {
        val s = signedInSession(session) ?: return seeOther("/buyer/login")
        apiClient.redeemLoyaltyReward(s, form.rewardItemId, form.quantity)
        return seeOther("/buyer/loyalty")
    }

    @GetMapping("/buyer/welcome")
    fun welcome(model: Model, session: HttpSession): String {
        val s = signedInSession(session) ?: return "redirect:/buyer/login"
        val cart = apiClient.listCart(s)
        val summary = apiClient.getWelcomeSummary(s)
        model.addAttribute("sessionUser", s)
        model.addAttribute("cartCount", cart.items().size)
        model.addAttribute("summary", summary)
        return "buyer-welcome"
    }

    @GetMapping("/buyer/invite")
    fun invite(model: Model, session: HttpSession): String {
        val s = signedInSession(session) ?: return "redirect:/buyer/login"
        val cart = apiClient.listCart(s)
        val inviteStats = apiClient.getInviteStats(s)
        model.addAttribute("sessionUser", s)
        model.addAttribute("cartCount", cart.items().size)
        model.addAttribute("inviteStats", inviteStats)
        return "buyer-invite"
    }

    // ── Activities ──

    @GetMapping("/buyer/activities")
    fun activities(model: Model, session: HttpSession): String {
        val s = shoppingSession(session)
        val games = apiClient.listActivityGames(s)
        val cart = apiClient.listCart(s)
        model.addAttribute("sessionUser", s)
        model.addAttribute("games", games)
        model.addAttribute("cartCount", cart.items().size)
        return "buyer-activities"
    }

    @GetMapping("/buyer/activities/{gameId}")
    fun activityDetail(
        @PathVariable gameId: String,
        @ModelAttribute("gameResult") gameResult: ActivityApi.ParticipateResponse?,
        model: Model,
        session: HttpSession
    ): String {
        val s = shoppingSession(session)
        val game = apiClient.getActivityGame(s, gameId)
        val cart = apiClient.listCart(s)
        val history = if (s.guest) emptyList() else apiClient.getActivityHistory(s, gameId)
        model.addAttribute("sessionUser", s)
        model.addAttribute("game", game)
        model.addAttribute("history", history)
        model.addAttribute("guestPlayer", s.guest)
        model.addAttribute("cartCount", cart.items().size)
        model.addAttribute("gameResult", gameResult)
        model.addAttribute("gameHint", parseAnimationHint(gameResult?.animationHint()))
        return "buyer-activity-detail"
    }

    @PostMapping("/buyer/activities/{gameId}/play")
    fun participateInActivity(
        @PathVariable gameId: String,
        @RequestParam gameType: String,
        @RequestParam(required = false) action: String?,
        redirectAttributes: RedirectAttributes,
        session: HttpSession
    ): RedirectView {
        val s = signedInSession(session) ?: return seeOther("/buyer/login")
        val result = apiClient.participateInActivity(s, gameId, buildActivityPayload(gameType, action))
        redirectAttributes.addFlashAttribute("gameResult", result)
        return seeOther("/buyer/activities/$gameId")
    }

    // ── Orders ──

    @GetMapping("/buyer/orders")
    fun orders(model: Model, session: HttpSession): String {
        val s = signedInSession(session) ?: return "redirect:/buyer/login"
        val orders = apiClient.listOrders(s)
        val cart = apiClient.listCart(s)
        model.addAttribute("sessionUser", s)
        model.addAttribute("orders", orders)
        model.addAttribute("cartCount", cart.items().size)
        return "buyer-orders"
    }

    @GetMapping("/buyer/order/{id}")
    fun orderDetail(@PathVariable id: String, model: Model, session: HttpSession): String {
        val s = signedInSession(session) ?: return "redirect:/buyer/login"
        val order = apiClient.getOrder(s, id)
        val cart = apiClient.listCart(s)
        model.addAttribute("sessionUser", s)
        model.addAttribute("order", order)
        model.addAttribute("cartCount", cart.items().size)
        return "buyer-order-detail"
    }

    @PostMapping("/buyer/order/cancel")
    fun cancelOrder(@RequestParam orderId: String, session: HttpSession): RedirectView {
        val s = signedInSession(session) ?: return seeOther("/buyer/login")
        apiClient.cancelOrder(s, orderId)
        return seeOther("/buyer/orders")
    }

    // ── Wallet ──

    @GetMapping("/buyer/wallet")
    fun wallet(model: Model, session: HttpSession): String {
        val s = signedInSession(session) ?: return "redirect:/buyer/login"
        val wallet = apiClient.getWallet(s)
        val cart = apiClient.listCart(s)
        model.addAttribute("sessionUser", s)
        model.addAttribute("wallet", wallet)
        model.addAttribute("cartCount", cart.items().size)
        model.addAttribute("walletForm", WalletForm())
        return "buyer-wallet"
    }

    @PostMapping("/buyer/wallet/deposit")
    fun deposit(@ModelAttribute form: WalletForm, session: HttpSession): RedirectView {
        val s = signedInSession(session) ?: return seeOther("/buyer/login")
        apiClient.deposit(s, BigDecimal(form.amount), form.currency)
        return seeOther("/buyer/wallet")
    }

    @PostMapping("/buyer/wallet/withdraw")
    fun withdraw(@ModelAttribute form: WalletForm, session: HttpSession): RedirectView {
        val s = signedInSession(session) ?: return seeOther("/buyer/login")
        apiClient.withdraw(s, BigDecimal(form.amount), form.currency)
        return seeOther("/buyer/wallet")
    }

    // ── Profile ──

    @GetMapping("/buyer/profile")
    fun profile(model: Model, session: HttpSession): String {
        val s = signedInSession(session) ?: return "redirect:/buyer/login"
        val dashboard = apiClient.dashboard(s)
        val cart = apiClient.listCart(s)
        model.addAttribute("sessionUser", s)
        model.addAttribute("profile", dashboard.profile())
        model.addAttribute("cartCount", cart.items().size)
        model.addAttribute("profileForm", ProfileForm(dashboard.profile().displayName(), dashboard.profile().email(), dashboard.profile().tier()))
        return "buyer-profile"
    }

    @PostMapping("/buyer/profile")
    fun updateProfile(@ModelAttribute form: ProfileForm, session: HttpSession): RedirectView {
        val s = signedInSession(session) ?: return seeOther("/buyer/login")
        apiClient.updateProfile(s, form)
        return seeOther("/buyer/profile")
    }

    // Keep old dashboard route as redirect
    @GetMapping("/buyer/dashboard")
    fun dashboard(): String = "redirect:/buyer/home"

    // ── Guest Shopping ──

    @GetMapping("/buyer/guest/checkout")
    fun guestCheckoutPage(@RequestParam productId: String, @RequestParam productName: String,
                          @RequestParam productPrice: String, @RequestParam sellerId: String,
                          model: Model): String {
        model.addAttribute("guestCheckoutForm", GuestCheckoutForm(
            productId = productId, productName = productName,
            productPrice = productPrice, sellerId = sellerId
        ))
        return "buyer-guest-checkout"
    }

    @PostMapping("/buyer/guest/checkout")
    fun guestCheckout(@ModelAttribute form: GuestCheckoutForm, model: Model): String {
        val order = apiClient.guestCheckout(
            form.guestEmail, form.productId, form.productName,
            BigDecimal(form.productPrice), form.sellerId, form.quantity
        )
        model.addAttribute("order", order)
        model.addAttribute("orderToken", order.orderToken())
        return "buyer-guest-order-confirmation"
    }

    @GetMapping("/buyer/guest/track")
    fun trackOrderPage(model: Model): String {
        model.addAttribute("trackForm", OrderTrackForm())
        return "buyer-guest-track"
    }

    @PostMapping("/buyer/guest/track")
    fun trackOrder(@ModelAttribute form: OrderTrackForm, model: Model): String {
        val order = apiClient.trackOrder(form.token)
        model.addAttribute("order", order)
        return "buyer-guest-order-detail"
    }

    private fun buildActivityPayload(gameType: String, action: String?): String? {
        return when (gameType) {
            "VIRTUAL_FARM" -> objectMapper.writeValueAsString(mapOf("action" to (action ?: "WATER")))
            else -> null
        }
    }

    private fun parseAnimationHint(animationHint: String?): Map<String, Any?>? {
        if (animationHint.isNullOrBlank()) {
            return null
        }
        return objectMapper.readValue(animationHint, Map::class.java) as Map<String, Any?>
    }
}
