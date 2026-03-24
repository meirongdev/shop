package dev.meirong.shop.sellerportal.service

import dev.meirong.shop.common.api.ApiResponse
import dev.meirong.shop.contracts.api.*
import dev.meirong.shop.sellerportal.config.SellerPortalProperties
import dev.meirong.shop.sellerportal.model.*
import org.springframework.core.ParameterizedTypeReference
import org.springframework.stereotype.Service
import org.springframework.web.client.RestClient
import java.math.BigDecimal

@Service
class SellerPortalApiClient(
    builder: RestClient.Builder,
    private val properties: SellerPortalProperties
) {
    private val restClient = builder.build()

    fun login(username: String, password: String): SellerSession {
        val response = restClient.post()
            .uri(properties.authBaseUrl + AuthApi.LOGIN)
            .body(AuthApi.LoginRequest(username, password, "seller"))
            .retrieve()
            .body(object : ParameterizedTypeReference<ApiResponse<AuthApi.TokenResponse>>() {})
            ?: error("Empty auth response")
        val data = response.data() ?: error("Missing auth payload")
        return SellerSession(
            token = data.accessToken(),
            principalId = data.principalId(),
            username = data.username(),
            displayName = data.displayName(),
            portal = data.portal()
        )
    }

    fun dashboard(session: SellerSession): SellerApi.DashboardResponse =
        authedPost(session, SellerApi.DASHBOARD, SellerApi.SellerContextRequest(session.principalId),
            object : ParameterizedTypeReference<ApiResponse<SellerApi.DashboardResponse>>() {})

    fun createProduct(session: SellerSession, form: ProductForm): MarketplaceApi.ProductResponse =
        authedPost(session, SellerApi.PRODUCT_CREATE,
            MarketplaceApi.UpsertProductRequest(null, session.principalId, form.sku, form.name,
                form.description, BigDecimal(form.price), form.inventory, form.published),
            object : ParameterizedTypeReference<ApiResponse<MarketplaceApi.ProductResponse>>() {})

    fun createPromotion(session: SellerSession, form: PromotionForm): PromotionApi.OfferResponse =
        authedPost(session, SellerApi.PROMOTION_CREATE,
            PromotionApi.CreateOfferRequest(session.principalId, form.code, form.title, form.description, BigDecimal(form.rewardAmount)),
            object : ParameterizedTypeReference<ApiResponse<PromotionApi.OfferResponse>>() {})

    // ── New e-commerce methods ──

    fun listOrders(session: SellerSession): List<OrderApi.OrderResponse> =
        authedPost(session, SellerApi.ORDER_LIST, SellerApi.SellerContextRequest(session.principalId),
            object : ParameterizedTypeReference<ApiResponse<List<OrderApi.OrderResponse>>>() {})

    fun getOrder(session: SellerSession, orderId: String): OrderApi.OrderResponse =
        authedPost(session, SellerApi.ORDER_GET, OrderApi.GetOrderRequest(orderId),
            object : ParameterizedTypeReference<ApiResponse<OrderApi.OrderResponse>>() {})

    fun shipOrder(session: SellerSession, orderId: String): OrderApi.OrderResponse =
        authedPost(session, SellerApi.ORDER_SHIP, OrderApi.ShipOrderRequest(orderId, null, "PENDING"),
            object : ParameterizedTypeReference<ApiResponse<OrderApi.OrderResponse>>() {})

    fun deliverOrder(session: SellerSession, orderId: String): OrderApi.OrderResponse =
        authedPost(session, SellerApi.ORDER_DELIVER, OrderApi.ConfirmOrderRequest(orderId),
            object : ParameterizedTypeReference<ApiResponse<OrderApi.OrderResponse>>() {})

    fun createCoupon(session: SellerSession, form: CouponForm): PromotionApi.CouponResponse {
        val maxDiscount = if (form.maxDiscount.isBlank()) null else BigDecimal(form.maxDiscount)
        return authedPost(session, SellerApi.COUPON_CREATE,
            PromotionApi.CreateCouponRequest(session.principalId, form.code, form.discountType,
                BigDecimal(form.discountValue), BigDecimal(form.minOrderAmount), maxDiscount, form.usageLimit, null),
            object : ParameterizedTypeReference<ApiResponse<PromotionApi.CouponResponse>>() {})
    }

    fun listCoupons(session: SellerSession): List<PromotionApi.CouponResponse> =
        authedPost(session, SellerApi.COUPON_LIST, SellerApi.SellerContextRequest(session.principalId),
            object : ParameterizedTypeReference<ApiResponse<List<PromotionApi.CouponResponse>>>() {})

    fun getShop(session: SellerSession): ProfileApi.SellerStorefrontResponse =
        authedPost(session, SellerApi.SHOP_GET, SellerApi.SellerContextRequest(session.principalId),
            object : ParameterizedTypeReference<ApiResponse<ProfileApi.SellerStorefrontResponse>>() {})

    fun updateShop(session: SellerSession, shopName: String, shopDescription: String?,
                   logoUrl: String?, bannerUrl: String?): ProfileApi.SellerStorefrontResponse =
        authedPost(session, SellerApi.SHOP_UPDATE,
            ProfileApi.UpdateShopRequest(session.principalId, shopName, null, shopDescription, logoUrl, bannerUrl),
            object : ParameterizedTypeReference<ApiResponse<ProfileApi.SellerStorefrontResponse>>() {})

    private fun <T> authedPost(session: SellerSession, path: String, body: Any,
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
