package com.skd.userinteractions.unit

import com.skd.userinteractions.configuration.MdcPopulationFilter
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import jakarta.servlet.FilterChain
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.slf4j.MDC
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.mock.web.MockHttpServletResponse

@Tag("unit")
class MdcPopulationFilterTest {

    private val filter = MdcPopulationFilter()

    @Test
    fun `populates MDC with request_id and user_id from headers`() {
        val request = MockHttpServletRequest().apply {
            addHeader("X-Request-Id", "test-request-123")
            addHeader("X-User-Id", "test-user-456")
        }
        val response = MockHttpServletResponse()

        var capturedRequestId: String? = null
        var capturedUserId: String? = null

        val chain = mockk<FilterChain>(relaxed = true)
        every { chain.doFilter(any(), any()) } answers {
            capturedRequestId = MDC.get("request_id")
            capturedUserId = MDC.get("user_id")
        }

        filter.doFilter(request, response, chain)

        assertThat(capturedRequestId).isEqualTo("test-request-123")
        assertThat(capturedUserId).isEqualTo("test-user-456")
        assertThat(MDC.get("request_id")).isNull()
        assertThat(MDC.get("user_id")).isNull()
    }

    @Test
    fun `clears MDC even when filter chain throws`() {
        val request = MockHttpServletRequest().apply {
            addHeader("X-Request-Id", "req-id")
        }
        val response = MockHttpServletResponse()

        val chain = mockk<FilterChain>()
        every { chain.doFilter(any(), any()) } throws RuntimeException("test error")

        try {
            filter.doFilter(request, response, chain)
        } catch (_: RuntimeException) {}

        assertThat(MDC.get("request_id")).isNull()
        assertThat(MDC.get("user_id")).isNull()
    }

    @Test
    fun `handles missing headers gracefully`() {
        val request = MockHttpServletRequest()
        val response = MockHttpServletResponse()
        val chain = mockk<FilterChain>(relaxed = true)

        filter.doFilter(request, response, chain)

        verify { chain.doFilter(request, response) }
        assertThat(MDC.get("request_id")).isNull()
        assertThat(MDC.get("user_id")).isNull()
    }
}
