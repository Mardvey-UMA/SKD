package com.contentplatform.user.unit.filter

import com.contentplatform.user.infrastructure.filter.MdcPopulationFilter
import io.mockk.mockk
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
    fun `populates request_id and user_id in MDC during request`() {
        val request = MockHttpServletRequest()
        request.addHeader("X-Request-Id", "test-request-id")
        request.addHeader("X-User-Id", "test-user-id")
        val response = MockHttpServletResponse()

        var requestIdDuringFilter: String? = null
        var userIdDuringFilter: String? = null

        val chain = FilterChain { _, _ ->
            requestIdDuringFilter = MDC.get("request_id")
            userIdDuringFilter = MDC.get("user_id")
        }

        filter.doFilter(request, response, chain)

        assertThat(requestIdDuringFilter).isEqualTo("test-request-id")
        assertThat(userIdDuringFilter).isEqualTo("test-user-id")
    }

    @Test
    fun `clears MDC after request completes`() {
        val request = MockHttpServletRequest()
        request.addHeader("X-Request-Id", "test-request-id")
        request.addHeader("X-User-Id", "test-user-id")
        val response = MockHttpServletResponse()

        val chain = FilterChain { _, _ -> }

        filter.doFilter(request, response, chain)

        assertThat(MDC.get("request_id")).isNull()
        assertThat(MDC.get("user_id")).isNull()
    }

    @Test
    fun `handles missing headers gracefully`() {
        val request = MockHttpServletRequest()
        val response = MockHttpServletResponse()

        val chain = FilterChain { _, _ -> }

        filter.doFilter(request, response, chain)

        assertThat(MDC.get("request_id")).isNull()
        assertThat(MDC.get("user_id")).isNull()
    }

    @Test
    fun `clears MDC even when filter chain throws exception`() {
        val request = MockHttpServletRequest()
        request.addHeader("X-Request-Id", "test-request-id")
        val response = MockHttpServletResponse()

        val chain = FilterChain { _, _ -> throw RuntimeException("test error") }

        try {
            filter.doFilter(request, response, chain)
        } catch (_: RuntimeException) {}

        assertThat(MDC.get("request_id")).isNull()
        assertThat(MDC.get("user_id")).isNull()
    }
}
