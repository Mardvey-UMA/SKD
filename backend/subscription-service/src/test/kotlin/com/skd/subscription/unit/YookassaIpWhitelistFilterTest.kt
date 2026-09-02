package com.skd.subscription.unit

import com.skd.subscription.configuration.YookassaProperties
import com.skd.subscription.presentation.filter.YookassaIpWhitelistFilter
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.springframework.mock.web.MockFilterChain
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.mock.web.MockHttpServletResponse

@Tag("unit")
class YookassaIpWhitelistFilterTest {

    private val properties = YookassaProperties(
        shopId = "test",
        secretKey = "test",
        apiUrl = "http://localhost",
        webhook = YookassaProperties.Webhook(
            allowedCidrs = "185.71.76.0/27,185.71.77.0/27,77.75.153.0/25"
        )
    )
    private val filter = YookassaIpWhitelistFilter(properties)

    private lateinit var request: MockHttpServletRequest
    private lateinit var response: MockHttpServletResponse
    private lateinit var filterChain: MockFilterChain

    @BeforeEach
    fun setup() {
        request = MockHttpServletRequest()
        response = MockHttpServletResponse()
        filterChain = MockFilterChain()
    }

    @Nested
    inner class `whitelisted IPs` {

        @Test
        fun `should allow request from whitelisted IP 185_71_76_1`() {
            request.requestURI = "/webhook/yookassa"
            request.method = "POST"
            request.remoteAddr = "185.71.76.1"

            filter.doFilter(request, response, filterChain)

            assertThat(response.status).isEqualTo(200)
            assertThat(filterChain.request).isNotNull
        }

        @Test
        fun `should allow request from whitelisted IP via X-Forwarded-For header`() {
            request.requestURI = "/webhook/yookassa"
            request.method = "POST"
            request.remoteAddr = "10.0.0.1"
            request.addHeader("X-Forwarded-For", "185.71.77.5, 10.0.0.1")

            filter.doFilter(request, response, filterChain)

            assertThat(response.status).isEqualTo(200)
            assertThat(filterChain.request).isNotNull
        }
    }

    @Nested
    inner class `non-whitelisted IPs` {

        @Test
        fun `should return 403 for request from non-whitelisted IP`() {
            request.requestURI = "/webhook/yookassa"
            request.method = "POST"
            request.remoteAddr = "192.168.1.1"

            filter.doFilter(request, response, filterChain)

            assertThat(response.status).isEqualTo(403)
            assertThat(filterChain.request).isNull()
        }
    }

    @Nested
    inner class `non-webhook paths` {

        @Test
        fun `should not filter non-webhook paths`() {
            request.requestURI = "/api/subscription/status"
            request.method = "GET"
            request.remoteAddr = "192.168.1.1"

            filter.doFilter(request, response, filterChain)

            // Non-webhook paths should pass through without IP check
            assertThat(response.status).isEqualTo(200)
            assertThat(filterChain.request).isNotNull
        }

        @Test
        fun `should not filter health endpoint`() {
            request.requestURI = "/health"
            request.method = "GET"
            request.remoteAddr = "192.168.1.1"

            filter.doFilter(request, response, filterChain)

            assertThat(response.status).isEqualTo(200)
            assertThat(filterChain.request).isNotNull
        }
    }
}
