package com.contentplatform.feed.unit.security

import com.contentplatform.feed.security.MdcPopulationFilter
import io.mockk.mockk
import io.mockk.verify
import jakarta.servlet.FilterChain
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.slf4j.MDC
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.mock.web.MockHttpServletResponse
import java.util.UUID

@Tag("unit")
class MdcPopulationFilterTest {

    private val filter = MdcPopulationFilter()

    @AfterEach
    fun clearMdc() {
        MDC.clear()
    }

    @Test
    fun `populates request_id and user_id in MDC during filter chain execution`() {
        val requestId = UUID.randomUUID().toString()
        val userId = UUID.randomUUID().toString()

        val request = MockHttpServletRequest().apply {
            addHeader("X-Request-Id", requestId)
            addHeader("X-User-Id", userId)
        }
        val response = MockHttpServletResponse()

        var mdcRequestId: String? = null
        var mdcUserId: String? = null

        val chain = FilterChain { _, _ ->
            mdcRequestId = MDC.get("request_id")
            mdcUserId = MDC.get("user_id")
        }

        filter.doFilter(request, response, chain)

        assertThat(mdcRequestId).isEqualTo(requestId)
        assertThat(mdcUserId).isEqualTo(userId)
    }

    @Test
    fun `clears MDC after filter chain completes`() {
        val request = MockHttpServletRequest().apply {
            addHeader("X-Request-Id", UUID.randomUUID().toString())
            addHeader("X-User-Id", UUID.randomUUID().toString())
        }
        val response = MockHttpServletResponse()

        filter.doFilter(request, response) { _, _ -> }

        assertThat(MDC.get("request_id")).isNull()
        assertThat(MDC.get("user_id")).isNull()
    }

    @Test
    fun `clears MDC even when filter chain throws`() {
        val request = MockHttpServletRequest().apply {
            addHeader("X-Request-Id", UUID.randomUUID().toString())
        }
        val response = MockHttpServletResponse()

        runCatching {
            filter.doFilter(request, response) { _, _ ->
                throw RuntimeException("downstream error")
            }
        }

        assertThat(MDC.get("request_id")).isNull()
        assertThat(MDC.get("user_id")).isNull()
    }

    @Test
    fun `does not set MDC keys when headers are absent`() {
        val request = MockHttpServletRequest() // no headers
        val response = MockHttpServletResponse()

        var mdcRequestId: String? = "sentinel"
        var mdcUserId: String? = "sentinel"

        filter.doFilter(request, response) { _, _ ->
            mdcRequestId = MDC.get("request_id")
            mdcUserId = MDC.get("user_id")
        }

        assertThat(mdcRequestId).isNull()
        assertThat(mdcUserId).isNull()
    }

    @Test
    fun `sets only request_id when user_id header is absent`() {
        val requestId = UUID.randomUUID().toString()
        val request = MockHttpServletRequest().apply {
            addHeader("X-Request-Id", requestId)
        }
        val response = MockHttpServletResponse()

        var mdcRequestId: String? = null
        var mdcUserId: String? = "sentinel"

        filter.doFilter(request, response) { _, _ ->
            mdcRequestId = MDC.get("request_id")
            mdcUserId = MDC.get("user_id")
        }

        assertThat(mdcRequestId).isEqualTo(requestId)
        assertThat(mdcUserId).isNull()
    }
}
