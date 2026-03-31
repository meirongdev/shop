package dev.meirong.shop.kmp.feature.cart.data

import dev.meirong.shop.kmp.core.model.CartItem
import dev.meirong.shop.kmp.core.model.CheckoutResult
import dev.meirong.shop.kmp.core.model.OrderLineItem
import dev.meirong.shop.kmp.core.model.Product
import dev.meirong.shop.kmp.core.model.ShopOrder
import dev.meirong.shop.kmp.core.model.ShoppingCart
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

private const val buyerCartListPath = "/buyer/v1/cart/list"
private const val buyerCartAddPath = "/buyer/v1/cart/add"
private const val buyerCartUpdatePath = "/buyer/v1/cart/update"
private const val buyerCartRemovePath = "/buyer/v1/cart/remove"
private const val buyerCheckoutPath = "/buyer/v1/checkout/create"

class BuyerCartRepository(
    tokenStorage: TokenStorage = NoOpTokenStorage,
    private val client: HttpClient = HttpClientFactory.create(tokenStorage),
    private val baseUrl: String = gatewayApiBaseUrl()
) {

    suspend fun listCart(buyerId: String): ShoppingCart {
        val response = client.post("$baseUrl$buyerCartListPath") {
            header(HttpHeaders.ContentType, ContentType.Application.Json.toString())
            setBody(BuyerContextRequestDto(buyerId = buyerId))
        }.body<ApiResponse<CartViewDto>>()

        return response.requireData().toModel()
    }

    suspend fun addToCart(
        buyerId: String,
        product: Product,
        quantity: Int = 1
    ): CartItem {
        val response = client.post("$baseUrl$buyerCartAddPath") {
            header(HttpHeaders.ContentType, ContentType.Application.Json.toString())
            setBody(
                AddToCartRequestDto(
                    buyerId = buyerId,
                    productId = product.id,
                    productName = product.name,
                    productPrice = product.priceInCents.toDouble() / 100.0,
                    sellerId = product.sellerId,
                    quantity = quantity
                )
            )
        }.body<ApiResponse<CartItemDto>>()

        return response.requireCartItem().toModel()
    }

    suspend fun updateQuantity(
        buyerId: String,
        productId: String,
        quantity: Int
    ): CartItem {
        val response = client.post("$baseUrl$buyerCartUpdatePath") {
            header(HttpHeaders.ContentType, ContentType.Application.Json.toString())
            setBody(UpdateCartRequestDto(buyerId = buyerId, productId = productId, quantity = quantity))
        }.body<ApiResponse<CartItemDto>>()

        return response.requireCartItem().toModel()
    }

    suspend fun removeFromCart(
        buyerId: String,
        productId: String
    ) {
        client.post("$baseUrl$buyerCartRemovePath") {
            header(HttpHeaders.ContentType, ContentType.Application.Json.toString())
            setBody(RemoveCartRequestDto(buyerId = buyerId, productId = productId))
        }.body<ApiResponse<Unit>>()
    }

    suspend fun checkout(
        buyerId: String,
        couponCode: String? = null
    ): CheckoutResult {
        val response = client.post("$baseUrl$buyerCheckoutPath") {
            header(HttpHeaders.ContentType, ContentType.Application.Json.toString())
            setBody(CheckoutRequestDto(buyerId = buyerId, couponCode = couponCode))
        }.body<ApiResponse<CheckoutResponseDto>>()

        return response.requireCheckout().toModel()
    }

    fun close() {
        client.close()
    }
}

private fun ApiResponse<CartViewDto>.requireData(): CartViewDto {
    return data ?: error(message.ifBlank { "Cart response did not include data." })
}

private fun ApiResponse<CartItemDto>.requireCartItem(): CartItemDto {
    return data ?: error(message.ifBlank { "Cart mutation did not include data." })
}

private fun ApiResponse<CheckoutResponseDto>.requireCheckout(): CheckoutResponseDto {
    return data ?: error(message.ifBlank { "Checkout response did not include data." })
}

private fun CartViewDto.toModel(): ShoppingCart = ShoppingCart(
    items = items.map { it.toModel() },
    subtotalInCents = (subtotal * 100.0).roundToLong()
)

private fun CartItemDto.toModel(): CartItem = CartItem(
    id = id,
    buyerId = buyerId,
    productId = productId,
    productName = productName,
    productPriceInCents = (productPrice * 100.0).roundToLong(),
    sellerId = sellerId,
    quantity = quantity,
    createdAt = createdAt
)

private fun CheckoutResponseDto.toModel(): CheckoutResult = CheckoutResult(
    orders = orders.map { it.toModel() },
    totalPaidInCents = (totalPaid * 100.0).roundToLong()
)

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
private data class AddToCartRequestDto(
    val buyerId: String,
    val productId: String,
    val productName: String,
    val productPrice: Double,
    val sellerId: String,
    val quantity: Int
)

@Serializable
private data class UpdateCartRequestDto(
    val buyerId: String,
    val productId: String,
    val quantity: Int
)

@Serializable
private data class RemoveCartRequestDto(
    val buyerId: String,
    val productId: String
)

@Serializable
private data class CheckoutRequestDto(
    val buyerId: String,
    val couponCode: String? = null
)

@Serializable
private data class CartViewDto(
    val items: List<CartItemDto> = emptyList(),
    val subtotal: Double
)

@Serializable
private data class CartItemDto(
    val id: String,
    val buyerId: String,
    val productId: String,
    val productName: String,
    val productPrice: Double,
    val sellerId: String,
    val quantity: Int,
    val createdAt: String
)

@Serializable
private data class CheckoutResponseDto(
    val orders: List<OrderDto> = emptyList(),
    val totalPaid: Double
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
