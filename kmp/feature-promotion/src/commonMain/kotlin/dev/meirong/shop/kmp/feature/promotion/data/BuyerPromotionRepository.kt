package dev.meirong.shop.kmp.feature.promotion.data

import dev.meirong.shop.kmp.core.model.BuyerCoupon
import dev.meirong.shop.kmp.core.model.BuyerOffer
import dev.meirong.shop.kmp.core.model.BuyerPromotionCatalog
import dev.meirong.shop.kmp.core.network.ApiResponse
import dev.meirong.shop.kmp.core.network.HttpClientFactory
import dev.meirong.shop.kmp.core.network.gatewayApiBaseUrl
import dev.meirong.shop.kmp.core.session.NoOpTokenStorage
import dev.meirong.shop.kmp.core.session.TokenStorage
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.post
import kotlin.math.roundToLong
import kotlinx.serialization.Serializable

private const val buyerPromotionListPath = "/buyer/v1/promotion/list"
private const val buyerCouponListPath = "/buyer/v1/coupon/list"

class BuyerPromotionRepository(
    tokenStorage: TokenStorage = NoOpTokenStorage,
    private val client: HttpClient = HttpClientFactory.create(tokenStorage),
    private val baseUrl: String = gatewayApiBaseUrl()
) {

    suspend fun loadCatalog(): BuyerPromotionCatalog {
        val promotionsResponse = client.post("$baseUrl$buyerPromotionListPath")
            .body<ApiResponse<List<BuyerOfferDto>>>()
        val couponsResponse = client.post("$baseUrl$buyerCouponListPath")
            .body<ApiResponse<List<BuyerCouponDto>>>()

        return BuyerPromotionCatalog(
            offers = promotionsResponse.requireOffers().map { it.toModel() },
            coupons = couponsResponse.requireCoupons().filter { it.active }.map { it.toModel() }
        )
    }

    fun close() {
        client.close()
    }
}

private fun ApiResponse<List<BuyerOfferDto>>.requireOffers(): List<BuyerOfferDto> {
    return data ?: error(message.ifBlank { "Buyer promotion response did not include data." })
}

private fun ApiResponse<List<BuyerCouponDto>>.requireCoupons(): List<BuyerCouponDto> {
    return data ?: error(message.ifBlank { "Buyer coupon response did not include data." })
}

private fun BuyerOfferDto.toModel(): BuyerOffer = BuyerOffer(
    id = id,
    code = code,
    title = title,
    description = description,
    rewardAmountInCents = (rewardAmount * 100.0).roundToLong(),
    active = active,
    source = source,
    createdAt = createdAt
)

private fun BuyerCouponDto.toModel(): BuyerCoupon = BuyerCoupon(
    id = id,
    sellerId = sellerId,
    code = code,
    discountType = discountType,
    discountValueInCents = (discountValue * 100.0).roundToLong(),
    minOrderAmountInCents = minOrderAmount?.let { (it * 100.0).roundToLong() },
    maxDiscountInCents = maxDiscount?.let { (it * 100.0).roundToLong() },
    usageLimit = usageLimit,
    usedCount = usedCount,
    expiresAt = expiresAt,
    active = active,
    createdAt = createdAt
)

@Serializable
private data class BuyerOfferDto(
    val id: String,
    val code: String,
    val title: String,
    val description: String,
    val rewardAmount: Double,
    val active: Boolean,
    val source: String,
    val createdAt: String
)

@Serializable
private data class BuyerCouponDto(
    val id: String,
    val sellerId: String,
    val code: String,
    val discountType: String,
    val discountValue: Double,
    val minOrderAmount: Double? = null,
    val maxDiscount: Double? = null,
    val usageLimit: Int,
    val usedCount: Int,
    val expiresAt: String? = null,
    val active: Boolean,
    val createdAt: String
)
