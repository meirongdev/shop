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

private const val buyerOrderListPath = "/buyer/v1/order/list"
private const val buyerOrderGetPath = "/buyer/v1/order/get"
private const val buyerOrderCancelPath = "/buyer/v1/order/cancel"

class BuyerOrderRepository(
    tokenStorage: TokenStorage = NoOpTokenStorage,
    private val client: HttpClient = HttpClientFactory.create(tokenStorage),
    private val baseUrl: String = gatewayApiBaseUrl()
) {

    suspend fun listOrders(buyerId: String): List<ShopOrder> {
        val response = client.post("$baseUrl$buyerOrderListPath") {
            header(HttpHeaders.ContentType, ContentType.Application.Json.toString())
            setBody(BuyerContextRequestDto(buyerId = buyerId))
        }.body<ApiResponse<List<OrderDto>>>()

        return response.requireOrders().map { it.toModel() }
    }

    suspend fun getOrder(orderId: String): ShopOrder {
        val response = client.post("$baseUrl$buyerOrderGetPath") {
            header(HttpHeaders.ContentType, ContentType.Application.Json.toString())
            setBody(GetOrderRequestDto(orderId = orderId))
        }.body<ApiResponse<OrderDto>>()

        return response.requireOrder().toModel()
    }

    suspend fun cancelOrder(
        buyerId: String,
        orderId: String
    ): ShopOrder {
        val response = client.post("$baseUrl$buyerOrderCancelPath") {
            header(HttpHeaders.ContentType, ContentType.Application.Json.toString())
            setBody(CancelOrderRequestDto(orderId = orderId))
        }.body<ApiResponse<OrderDto>>()

        return response.requireOrder().toModel()
    }

    fun close() {
        client.close()
    }
}

private fun ApiResponse<List<OrderDto>>.requireOrders(): List<OrderDto> {
    return data ?: error(message.ifBlank { "Order list response did not include data." })
}

private fun ApiResponse<OrderDto>.requireOrder(): OrderDto {
    return data ?: error(message.ifBlank { "Order response did not include data." })
}

private fun OrderDto.toModel(): ShopOrder = ShopOrder(
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

private fun OrderItemDto.toModel(): OrderLineItem = OrderLineItem(
    id = id,
    orderId = orderId,
    productId = productId,
    productName = productName,
    productPriceInCents = (productPrice * 100.0).roundToLong(),
    quantity = quantity,
    lineTotalInCents = (lineTotal * 100.0).roundToLong()
)

@Serializable
private data class BuyerContextRequestDto(
    val buyerId: String
)

@Serializable
private data class GetOrderRequestDto(
    val orderId: String
)

@Serializable
private data class CancelOrderRequestDto(
    val orderId: String,
    val reason: String? = null
)

@Serializable
private data class OrderDto(
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
    val items: List<OrderItemDto> = emptyList(),
    val createdAt: String,
    val updatedAt: String
)

@Serializable
private data class OrderItemDto(
    val id: String,
    val orderId: String,
    val productId: String,
    val productName: String,
    val productPrice: Double,
    val quantity: Int,
    val lineTotal: Double
)
