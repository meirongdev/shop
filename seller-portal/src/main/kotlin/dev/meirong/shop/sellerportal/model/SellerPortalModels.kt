package dev.meirong.shop.sellerportal.model

data class SellerSession(
    val token: String,
    val principalId: String,
    val username: String,
    val displayName: String,
    val portal: String
)

data class SellerLoginForm(
    val username: String = "seller.demo",
    val password: String = "password"
)

data class ProductForm(
    val sku: String = "",
    val name: String = "",
    val description: String = "",
    val price: String = "99.00",
    val inventory: Int = 10,
    val published: Boolean = true
)

data class PromotionForm(
    val code: String = "",
    val title: String = "",
    val description: String = "",
    val rewardAmount: String = "10.00"
)

data class CouponForm(
    val code: String = "",
    val discountType: String = "FIXED_AMOUNT",
    val discountValue: String = "10.00",
    val minOrderAmount: String = "50.00",
    val maxDiscount: String = "",
    val usageLimit: Int = 100
)

data class ShipOrderForm(
    val orderId: String = ""
)

data class ShopForm(
    val shopName: String = "",
    val shopDescription: String = "",
    val logoUrl: String = "",
    val bannerUrl: String = ""
)
