package com.contentplatform.auth.unit.security

import com.contentplatform.auth.security.MdcPopulationFilter
import jakarta.servlet.FilterChain
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.slf4j.MDC
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.mock.web.MockHttpServletResponse

@Tag("unit")
class MdcPopulationFilterTest {

    private val filter = MdcPopulationFilter()

    @AfterEach
    fun clearMdc() {
        MDC.clear()
    }

    @Test
    fun `populates request_id and user_id MDC keys during filter chain`() {
        val request = MockHttpServletRequest().apply {
            addHeader("X-Request-Id", "req-abc-123")
            addHeader("X-User-Id", "user-xyz-456")
        }
        val response = MockHttpServletResponse()

        var capturedRequestId: String? = null
        var capturedUserId: String? = null

        val chain = FilterChain { _, _ ->
            capturedRequestId = MDC.get("request_id")
            capturedUserId = MDC.get("user_id")
        }

        filter.doFilter(request, response, chain)

        assertThat(capturedRequestId).isEqualTo("req-abc-123")
        assertThat(capturedUserId).isEqualTo("user-xyz-456")
    }

    @Test
    fun `clears MDC after request completes`() {
        val request = MockHttpServletRequest().apply {
            addHeader("X-Request-Id", "req-to-clear")
            addHeader("X-User-Id", "user-to-clear")
        }
        val response = MockHttpServletResponse()

        filter.doFilter(request, response, FilterChain { _, _ -> })

        assertThat(MDC.get("request_id")).isNull()
        assertThat(MDC.get("user_id")).isNull()
    }

    @Test
    fun `handles missing X-Request-Id gracefully`() {
        val request = MockHttpServletRequest()
        val response = MockHttpServletResponse()

        var capturedRequestId: String? = "sentinel"

        val chain = FilterChain { _, _ ->
            capturedRequestId = MDC.get("request_id")
        }

        filter.doFilter(request, response, chain)

        assertThat(capturedRequestId).isNull()
    }

    @Test
    fun `clears MDC even when filter chain throws`() {
        val request = MockHttpServletRequest().apply {
            addHeader("X-Request-Id", "req-error")
        }
        val response = MockHttpServletResponse()

        runCatching {
            filter.doFilter(request, response, FilterChain { _, _ ->
                error("simulated downstream failure")
            })
        }

        assertThat(MDC.get("request_id")).isNull()
    }
}
