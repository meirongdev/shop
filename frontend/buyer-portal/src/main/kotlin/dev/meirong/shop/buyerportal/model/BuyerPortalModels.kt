package dev.meirong.shop.buyerportal.model

import java.io.Serializable

data class BuyerSession(
    val token: String,
    val principalId: String,
    val username: String,
    val displayName: String,
    val portal: String,
    val roles: List<String> = emptyList(),
    val guest: Boolean = false,
    val newUser: Boolean = false
) : Serializable

data class LoginForm(
    val username: String = "buyer.demo",
    val password: String = "password"
)

data class BuyerRegistrationForm(
    val username: String = "",
    val email: String = "",
    val password: String = "",
    val confirmPassword: String = "",
    val inviteCode: String = ""
)

data class OtpPhoneForm(
    val phoneNumber: String = "",
    val locale: String = "zh-CN"
)

data class OtpVerifyForm(
    val phoneNumber: String = "",
    val otp: String = ""
)

data class AppleLoginForm(
    val idToken: String = "",
    val nonce: String = ""
)

data class ProfileForm(
    val displayName: String = "",
    val email: String = "",
    val tier: String = "SILVER"
)

data class WalletForm(
    val amount: String = "10.00",
    val currency: String = "usd"
)

data class AddToCartForm(
    val productId: String = "",
    val quantity: Int = 1
)

data class UpdateCartForm(
    val productId: String = "",
    val quantity: Int = 1
)

data class CheckoutForm(
    val couponCode: String = "",
    val paymentMethod: String = "WALLET",
    val pointsToUse: Long? = null
)

data class CancelOrderForm(
    val orderId: String = ""
)

data class GuestCheckoutForm(
    val guestEmail: String = "",
    val productId: String = "",
    val productName: String = "",
    val productPrice: String = "0.00",
    val sellerId: String = "",
    val quantity: Int = 1
)

data class OrderTrackForm(
    val token: String = ""
)

data class RedeemRewardForm(
    val rewardItemId: String = "",
    val quantity: Int = 1
)
