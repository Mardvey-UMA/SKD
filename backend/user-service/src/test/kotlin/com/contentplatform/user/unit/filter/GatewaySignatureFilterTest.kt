package com.contentplatform.user.unit.filter

import com.contentplatform.user.infrastructure.filter.GatewaySignatureFilter
import io.mockk.mockk
import io.mockk.verify
import jakarta.servlet.FilterChain
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.mock.web.MockHttpServletResponse
import java.util.UUID
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

@Tag("unit")
class GatewaySignatureFilterTest {

    private val secret = "unit-test-hmac-secret"
    private val filter = GatewaySignatureFilter(secret)

    private fun computeHmac(data: String): String {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(secret.toByteArray(), "HmacSHA256"))
        return java.util.Base64.getEncoder().encodeToString(mac.doFinal(data.toByteArray()))
    }

    @Nested
    inner class `valid request` {

        @Test
        fun `should pass through filter when signature and user id are valid`() {
            val userId = UUID.randomUUID().toString()
            val roles = "USER"
            val tier = "free"
            val requestId = UUID.randomUUID().toString()
            val signature = computeHmac("$userId|$roles|$tier|$requestId")

            val request = MockHttpServletRequest("GET", "/api/users/me")
            request.addHeader("X-Gateway-Signature", signature)
            request.addHeader("X-User-Id", userId)
            request.addHeader("X-User-Roles", roles)
            request.addHeader("X-Subscription-Tier", tier)
            request.addHeader("X-Request-Id", requestId)
            val response = MockHttpServletResponse()
            val chain = mockk<FilterChain>(relaxed = true)

            filter.doFilter(request, response, chain)

            verify(exactly = 1) { chain.doFilter(any(), any()) }
            assertThat(response.status).isNotEqualTo(401)
        }
    }

    @Nested
    inner class `missing signature` {

        @Test
        fun `should return 401 when X-Gateway-Signature header is missing`() {
            val request = MockHttpServletRequest("GET", "/api/users/me")
            request.addHeader("X-User-Id", UUID.randomUUID().toString())
            val response = MockHttpServletResponse()
            val chain = mockk<FilterChain>(relaxed = true)

            filter.doFilter(request, response, chain)

            verify(exactly = 0) { chain.doFilter(any(), any()) }
            assertThat(response.status).isEqualTo(401)
            assertThat(response.contentType).isEqualTo("application/json")
        }
    }

    @Nested
    inner class `invalid signature` {

        @Test
        fun `should return 401 when signature does not match`() {
            val request = MockHttpServletRequest("GET", "/api/users/me")
            request.addHeader("X-Gateway-Signature", "wrong-signature")
            request.addHeader("X-User-Id", UUID.randomUUID().toString())
            val response = MockHttpServletResponse()
            val chain = mockk<FilterChain>(relaxed = true)

            filter.doFilter(request, response, chain)

            verify(exactly = 0) { chain.doFilter(any(), any()) }
            assertThat(response.status).isEqualTo(401)
            assertThat(response.contentType).isEqualTo("application/json")
        }
    }

    @Nested
    inner class `missing user id` {

        @Test
        fun `should return 401 when X-User-Id header is missing`() {
            val request = MockHttpServletRequest("GET", "/api/users/me")
            request.addHeader("X-Gateway-Signature", "some-signature")
            val response = MockHttpServletResponse()
            val chain = mockk<FilterChain>(relaxed = true)

            filter.doFilter(request, response, chain)

            verify(exactly = 0) { chain.doFilter(any(), any()) }
            assertThat(response.status).isEqualTo(401)
        }
    }

    @Nested
    inner class `excluded paths` {

        @Test
        fun `should not filter health endpoint`() {
            val request = MockHttpServletRequest("GET", "/health")

            val result = filter.shouldNotFilterRequest(request)

            assertThat(result).isTrue
        }

        @Test
        fun `should not filter error endpoint`() {
            val request = MockHttpServletRequest("GET", "/error")

            val result = filter.shouldNotFilterRequest(request)

            assertThat(result).isTrue
        }

        @Test
        fun `should filter api endpoints`() {
            val request = MockHttpServletRequest("GET", "/api/users/me")

            val result = filter.shouldNotFilterRequest(request)

            assertThat(result).isFalse
        }
    }

    @Nested
    inner class `error response format` {

        @Test
        fun `should write JSON error body with error and message fields`() {
            val request = MockHttpServletRequest("GET", "/api/users/me")
            val response = MockHttpServletResponse()
            val chain = mockk<FilterChain>(relaxed = true)

            filter.doFilter(request, response, chain)

            val body = response.contentAsString
            assertThat(body).contains("\"error\"")
            assertThat(body).contains("UNAUTHORIZED")
            assertThat(body).contains("\"message\"")
        }
    }
}
