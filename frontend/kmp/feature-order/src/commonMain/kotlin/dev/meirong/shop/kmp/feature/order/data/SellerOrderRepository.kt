package dev.meirong.shop.kmp.feature.order.data

import dev.meirong.shop.kmp.core.model.OrderLineItem
import dev.meirong.shop.kmp.core.model.ShopOrder
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

private const val sellerOrderListPath = "/seller/v1/order/list"
private const val sellerOrderGetPath = "/seller/v1/order/get"
private const val sellerOrderShipPath = "/seller/v1/order/ship"
private const val sellerOrderDeliverPath = "/seller/v1/order/deliver"
private const val sellerOrderCancelPath = "/seller/v1/order/cancel"

class SellerOrderRepository(
    tokenStorage: TokenStorage = NoOpTokenStorage,
    private val client: HttpClient = HttpClientFactory.create(tokenStorage),
    private val baseUrl: String = gatewayApiBaseUrl()
) {

    suspend fun listOrders(sellerId: String): List<ShopOrder> {
        val response = client.post("$baseUrl$sellerOrderListPath") {
            header(HttpHeaders.ContentType, ContentType.Application.Json.toString())
            setBody(SellerContextRequestDto(sellerId = sellerId))
        }.body<ApiResponse<List<SellerOrderDto>>>()

        return response.requireOrders().map { it.toModel() }
    }

    suspend fun getOrder(orderId: String): ShopOrder {
        val response = client.post("$baseUrl$sellerOrderGetPath") {
            header(HttpHeaders.ContentType, ContentType.Application.Json.toString())
            setBody(SellerGetOrderRequestDto(orderId = orderId))
        }.body<ApiResponse<SellerOrderDto>>()

        return response.requireOrder().toModel()
    }

    suspend fun shipOrder(orderId: String): ShopOrder {
        val response = client.post("$baseUrl$sellerOrderShipPath") {
            header(HttpHeaders.ContentType, ContentType.Application.Json.toString())
            setBody(SellerUpdateOrderStatusRequestDto(orderId = orderId, status = "SHIPPED"))
        }.body<ApiResponse<SellerOrderDto>>()

        return response.requireOrder().toModel()
    }

    suspend fun deliverOrder(orderId: String): ShopOrder {
        val response = client.post("$baseUrl$sellerOrderDeliverPath") {
            header(HttpHeaders.ContentType, ContentType.Application.Json.toString())
            setBody(SellerUpdateOrderStatusRequestDto(orderId = orderId, status = "DELIVERED"))
        }.body<ApiResponse<SellerOrderDto>>()

        return response.requireOrder().toModel()
    }

    suspend fun cancelOrder(orderId: String, reason: String): ShopOrder {
        val response = client.post("$baseUrl$sellerOrderCancelPath") {
            header(HttpHeaders.ContentType, ContentType.Application.Json.toString())
            setBody(SellerCancelOrderRequestDto(orderId = orderId, reason = reason))
        }.body<ApiResponse<SellerOrderDto>>()

        return response.requireOrder().toModel()
    }

    fun close() {
        client.close()
    }
}

private fun ApiResponse<List<SellerOrderDto>>.requireOrders(): List<SellerOrderDto> {
    return data ?: error(message.ifBlank { "Seller order list response did not include data." })
}

private fun ApiResponse<SellerOrderDto>.requireOrder(): SellerOrderDto {
    return data ?: error(message.ifBlank { "Seller order response did not include data." })
}

private fun SellerOrderDto.toModel(): ShopOrder = ShopOrder(
    id = id,
    buyerId = buyerId,
    sellerId = sellerId,
    status = status,
    subtotalInCents = (subtotal * 100.0).roundToLong(),
    discountAmountInCents = (discountAmount * 100.0).roundToLong(),
    totalAmountInCents = (totalAmount * 100.0).roundToLong(),
    couponId = couponId,
    couponCode = couponCode,
    paymentTransactionId = paymentTransactionId,
    items = items.map { it.toModel() },
    createdAt = createdAt,
    updatedAt = updatedAt
)

private fun SellerOrderItemDto.toModel(): OrderLineItem = OrderLineItem(
    id = id,
    orderId = orderId,
    productId = productId,
    productName = productName,
    productPriceInCents = (productPrice * 100.0).roundToLong(),
    quantity = quantity,
    lineTotalInCents = (lineTotal * 100.0).roundToLong()
)

@Serializable
private data class SellerContextRequestDto(
    val sellerId: String
)

@Serializable
private data class SellerGetOrderRequestDto(
    val orderId: String
)

@Serializable
private data class SellerUpdateOrderStatusRequestDto(
    val orderId: String,
    val status: String
)

@Serializable
private data class SellerCancelOrderRequestDto(
    val orderId: String,
    val reason: String
)

@Serializable
private data class SellerOrderDto(
    val id: String,
    val buyerId: String,
    val sellerId: String,
    val status: String,
    val subtotal: Double,
    val discountAmount: Double,
    val totalAmount: Double,
    val couponId: String? = null,
    val couponCode: String? = null,
    val paymentTransactionId: String? = null,
    val items: List<SellerOrderItemDto> = emptyList(),
    val createdAt: String,
    val updatedAt: String
)

@Serializable
private data class SellerOrderItemDto(
    val id: String,
    val orderId: String,
    val productId: String,
    val productName: String,
    val productPrice: Double,
    val quantity: Int,
    val lineTotal: Double
)
