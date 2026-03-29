package dev.meirong.shop.buyerportal.config

import java.net.http.HttpClient
import java.time.Duration
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.http.client.JdkClientHttpRequestFactory

class BuyerPortalConfigTest {

    private val config = BuyerPortalConfig()

    @Test
    fun `jdk request factory defaults to HTTP 1_1 with short timeouts`() {
        val properties = BuyerPortalProperties(
            authBaseUrl = "http://auth-server:8080",
            gatewayBaseUrl = "http://api-gateway:8080",
            appleClientId = "apple-client-id",
            appleRedirectUri = "http://localhost:8080/buyer/login"
        )

        val factory = config.jdkClientHttpRequestFactory(properties)
        val httpClient = factory.readPrivateField<HttpClient>("httpClient")
        val readTimeout = factory.readPrivateField<Duration>("readTimeout")

        assertThat(httpClient.version()).isEqualTo(HttpClient.Version.HTTP_1_1)
        assertThat(httpClient.connectTimeout()).contains(Duration.ofSeconds(2))
        assertThat(readTimeout).isEqualTo(Duration.ofSeconds(5))
        assertThat(config.restClientBuilder(factory).readPrivateField<Any>("requestFactory")).isSameAs(factory)
    }

    @Test
    fun `jdk request factory honors configured version and timeouts`() {
        val properties = BuyerPortalProperties(
            authBaseUrl = "http://auth-server:8080",
            gatewayBaseUrl = "http://api-gateway:8080",
            appleClientId = "apple-client-id",
            appleRedirectUri = "http://localhost:8080/buyer/login",
            httpVersion = HttpClient.Version.HTTP_2,
            connectTimeout = Duration.ofSeconds(4),
            readTimeout = Duration.ofSeconds(7)
        )

        val factory = config.jdkClientHttpRequestFactory(properties)
        val httpClient = factory.readPrivateField<HttpClient>("httpClient")
        val readTimeout = factory.readPrivateField<Duration>("readTimeout")

        assertThat(httpClient.version()).isEqualTo(HttpClient.Version.HTTP_2)
        assertThat(httpClient.connectTimeout()).contains(Duration.ofSeconds(4))
        assertThat(readTimeout).isEqualTo(Duration.ofSeconds(7))
    }

    private fun <T> Any.readPrivateField(name: String): T {
        val field = javaClass.getDeclaredField(name)
        field.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        return field.get(this) as T
    }
}
