package dev.meirong.shop.kmp.core.model

import dev.meirong.shop.kmp.core.network.ApiResponse
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.serialization.json.Json

class ProductSerializationTest {

    @Test
    fun deserializeApiResponse() {
        val json =
            """{"traceId":"abc","status":"SC_OK","message":"Success","data":{"id":"1","sellerId":"s1","sku":"SKU1","name":"Test","description":"Desc","priceInCents":999,"inventory":10,"published":true}}"""
        val response = Json.decodeFromString<ApiResponse<Product>>(json)
        assertEquals("1", response.data?.id)
        assertEquals(999L, response.data?.priceInCents)
    }
}
