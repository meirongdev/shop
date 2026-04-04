package ${package}.service

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class PortalViewServiceTest {

    private val service = PortalViewService()

    @Test
    fun `homepage contains placeholder message`() {
        val page = service.homepage()

        assertThat(page.title).isEqualTo("${artifactId}")
        assertThat(page.message).contains("Replace this page")
    }
}
