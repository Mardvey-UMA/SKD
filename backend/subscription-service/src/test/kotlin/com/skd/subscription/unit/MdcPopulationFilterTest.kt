package com.skd.subscription.unit

import com.skd.subscription.presentation.filter.MdcPopulationFilter
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.slf4j.MDC
import org.springframework.mock.web.MockFilterChain
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.mock.web.MockHttpServletResponse

@Tag("unit")
class MdcPopulationFilterTest {

    private val filter = MdcPopulationFilter()

    @Test
    fun `populates MDC with request_id and user_id from headers`() {
        val request = MockHttpServletRequest().apply {
            addHeader("X-Request-Id", "req-123")
            addHeader("X-User-Id", "user-456")
        }
        val response = MockHttpServletResponse()
        var requestIdDuringFilter: String? = null
        var userIdDuringFilter: String? = null
        val chain = FilterChain { _, _ ->
            requestIdDuringFilter = MDC.get("request_id")
            userIdDuringFilter = MDC.get("user_id")
        }

        filter.doFilter(request, response, chain)

        assertThat(requestIdDuringFilter).isEqualTo("req-123")
        assertThat(userIdDuringFilter).isEqualTo("user-456")
        assertThat(MDC.get("request_id")).isNull()
        assertThat(MDC.get("user_id")).isNull()
    }

    @Test
    fun `skips MDC entries when headers are absent`() {
        val request = MockHttpServletRequest()
        val response = MockHttpServletResponse()
        var requestIdDuringFilter: String? = "not-set"
        var userIdDuringFilter: String? = "not-set"
        val chain = FilterChain { _, _ ->
            requestIdDuringFilter = MDC.get("request_id")
            userIdDuringFilter = MDC.get("user_id")
        }

        filter.doFilter(request, response, chain)

        assertThat(requestIdDuringFilter).isNull()
        assertThat(userIdDuringFilter).isNull()
    }

    @Test
    fun `clears MDC even when filter chain throws`() {
        val request = MockHttpServletRequest().apply {
            addHeader("X-Request-Id", "req-err")
        }
        val response = MockHttpServletResponse()
        val chain = FilterChain { _, _ -> throw RuntimeException("boom") }

        try {
            filter.doFilter(request, response, chain)
        } catch (ignored: RuntimeException) {}

        assertThat(MDC.get("request_id")).isNull()
    }
}
