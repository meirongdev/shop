package dev.meirong.shop.kmp.feature.promotion.data

import dev.meirong.shop.kmp.core.model.SellerCoupon
import dev.meirong.shop.kmp.core.model.SellerOffer
import dev.meirong.shop.kmp.core.model.SellerPromotionWorkspace
import dev.meirong.shop.kmp.core.network.ApiResponse
import dev.meirong.shop.kmp.core.network.HttpClientFactory
import dev.meirong.shop.kmp.core.network.gatewayApiBaseUrl
import dev.meirong.shop.kmp.core.session.NoOpTokenStorage
import dev.meirong.shop.kmp.core.session.TokenStorage
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import kotlin.math.roundToLong
import kotlinx.serialization.Serializable

private const val sellerDashboardPath = "/seller/v1/dashboard/get"
private const val sellerPromotionCreatePath = "/seller/v1/promotion/create"
private const val sellerCouponCreatePath = "/seller/v1/coupon/create"
private const val sellerCouponListPath = "/seller/v1/coupon/list"

class SellerPromotionRepository(
    tokenStorage: TokenStorage = NoOpTokenStorage,
    private val client: HttpClient = HttpClientFactory.create(tokenStorage),
    private val baseUrl: String = gatewayApiBaseUrl()
) {

    suspend fun loadWorkspace(sellerId: String): SellerPromotionWorkspace {
        val dashboardResponse = client.post("$baseUrl$sellerDashboardPath") {
            header(HttpHeaders.ContentType, ContentType.Application.Json.toString())
            setBody(SellerContextRequestDto(sellerId = sellerId))
        }.body<ApiResponse<SellerPromotionDashboardDto>>()

        val couponResponse = client.post("$baseUrl$sellerCouponListPath") {
            header(HttpHeaders.ContentType, ContentType.Application.Json.toString())
            setBody(SellerContextRequestDto(sellerId = sellerId))
        }.body<ApiResponse<List<SellerCouponDto>>>()

        val offers = dashboardResponse.requireDashboard()
            .promotions
            .filter { it.source == sellerId }
            .map { it.toModel() }
        val coupons = couponResponse.requireCoupons().map { it.toModel() }

        return SellerPromotionWorkspace(
            activePromotionCount = offers.count { it.active }.toLong(),
            offers = offers,
            coupons = coupons
        )
    }

    suspend fun createOffer(
        sellerId: String,
        code: String,
        title: String,
        description: String,
        rewardAmountInCents: Long
    ): SellerOffer {
        val response = client.post("$baseUrl$sellerPromotionCreatePath") {
            header(HttpHeaders.ContentType, ContentType.Application.Json.toString())
            setBody(
                CreateOfferRequestDto(
                    sellerId = sellerId,
                    code = code,
                    title = title,
                    description = description,
                    rewardAmount = rewardAmountInCents.toAmount()
                )
            )
        }.body<ApiResponse<SellerOfferDto>>()

        return response.requireOffer().toModel()
    }

    suspend fun createCoupon(
        sellerId: String,
        code: String,
        discountType: String,
        discountValueInCents: Long,
        minOrderAmountInCents: Long?,
        maxDiscountInCents: Long?,
        usageLimit: Int
    ): SellerCoupon {
        val response = client.post("$baseUrl$sellerCouponCreatePath") {
            header(HttpHeaders.ContentType, ContentType.Application.Json.toString())
            setBody(
                CreateCouponRequestDto(
                    sellerId = sellerId,
                    code = code,
                    discountType = discountType,
                    discountValue = discountValueInCents.toAmount(),
                    minOrderAmount = minOrderAmountInCents?.toAmount(),
                    maxDiscount = maxDiscountInCents?.toAmount(),
                    usageLimit = usageLimit,
                    expiresAt = null
                )
            )
        }.body<ApiResponse<SellerCouponDto>>()

        return response.requireCoupon().toModel()
    }

    fun close() {
        client.close()
    }
}

private fun Long.toAmount(): Double = this / 100.0

private fun ApiResponse<SellerPromotionDashboardDto>.requireDashboard(): SellerPromotionDashboardDto {
    return data ?: error(message.ifBlank { "Seller promotion dashboard response did not include data." })
}

private fun ApiResponse<List<SellerCouponDto>>.requireCoupons(): List<SellerCouponDto> {
    return data ?: error(message.ifBlank { "Seller coupon list response did not include data." })
}

private fun ApiResponse<SellerOfferDto>.requireOffer(): SellerOfferDto {
    return data ?: error(message.ifBlank { "Seller offer response did not include data." })
}

private fun ApiResponse<SellerCouponDto>.requireCoupon(): SellerCouponDto {
    return data ?: error(message.ifBlank { "Seller coupon response did not include data." })
}

private fun SellerOfferDto.toModel(): SellerOffer = SellerOffer(
    id = id,
    code = code,
    title = title,
    description = description,
    rewardAmountInCents = (rewardAmount * 100.0).roundToLong(),
    active = active,
    source = source,
    createdAt = createdAt
)

private fun SellerCouponDto.toModel(): SellerCoupon = SellerCoupon(
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
private data class SellerContextRequestDto(
    val sellerId: String
)

@Serializable
private data class CreateOfferRequestDto(
    val sellerId: String,
    val code: String,
    val title: String,
    val description: String,
    val rewardAmount: Double
)

@Serializable
private data class CreateCouponRequestDto(
    val sellerId: String,
    val code: String,
    val discountType: String,
    val discountValue: Double,
    val minOrderAmount: Double? = null,
    val maxDiscount: Double? = null,
    val usageLimit: Int,
    val expiresAt: String? = null
)

@Serializable
private data class SellerPromotionDashboardDto(
    val activePromotionCount: Long,
    val promotions: List<SellerOfferDto> = emptyList()
)

@Serializable
private data class SellerOfferDto(
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
private data class SellerCouponDto(
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
