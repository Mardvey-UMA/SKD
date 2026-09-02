package com.contentplatform.feed.unit.security

import com.contentplatform.feed.configuration.GatewayProperties
import com.contentplatform.feed.security.GatewaySignatureFilter
import jakarta.servlet.FilterChain
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.mock.web.MockHttpServletResponse
import java.util.Base64
import java.util.UUID
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

@Tag("unit")
class GatewaySignatureFilterTest {

    private val hmacSecret = "test-secret-key"
    private val gatewayProperties = GatewayProperties().apply { hmacSecret = this@GatewaySignatureFilterTest.hmacSecret }
    private val filter = GatewaySignatureFilter(gatewayProperties)

    private fun computeHmac(payload: String): String {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(hmacSecret.toByteArray(), "HmacSHA256"))
        return Base64.getEncoder().encodeToString(mac.doFinal(payload.toByteArray()))
    }

    @Nested
    inner class `valid signature` {

        @Test
        fun `should pass through filter when signature is valid`() {
            val userId = UUID.randomUUID().toString()
            val roles = "USER"
            val tier = "free"
            val reqId = UUID.randomUUID().toString()
            val payload = "$userId|$roles|$tier|$reqId"
            val signature = computeHmac(payload)

            val request = MockHttpServletRequest("GET", "/api/feed")
            request.requestURI = "/api/feed"
            request.addHeader("X-User-Id", userId)
            request.addHeader("X-User-Roles", roles)
            request.addHeader("X-Subscription-Tier", tier)
            request.addHeader("X-Request-Id", reqId)
            request.addHeader("X-Gateway-Signature", signature)

            val response = MockHttpServletResponse()
            var chainCalled = false
            val chain = FilterChain { _, _ -> chainCalled = true }

            filter.doFilter(request, response, chain)

            assertThat(chainCalled).isTrue()
            assertThat(response.status).isEqualTo(200)
        }

        @Test
        fun `should allow filter chain to proceed with userId accessible from request`() {
            val userId = UUID.randomUUID().toString()
            val roles = "USER,ADMIN"
            val tier = "premium"
            val reqId = UUID.randomUUID().toString()
            val payload = "$userId|$roles|$tier|$reqId"
            val signature = computeHmac(payload)

            val request = MockHttpServletRequest("GET", "/api/feed")
            request.requestURI = "/api/feed"
            request.addHeader("X-User-Id", userId)
            request.addHeader("X-User-Roles", roles)
            request.addHeader("X-Subscription-Tier", tier)
            request.addHeader("X-Request-Id", reqId)
            request.addHeader("X-Gateway-Signature", signature)

            val response = MockHttpServletResponse()
            var receivedUserId: String? = null
            val chain = FilterChain { req, _ ->
                receivedUserId = (req as? jakarta.servlet.http.HttpServletRequest)?.getHeader("X-User-Id")
            }

            filter.doFilter(request, response, chain)

            assertThat(receivedUserId).isEqualTo(userId)
        }
    }

    @Nested
    inner class `missing headers` {

        @Test
        fun `should return 403 when X-Gateway-Signature is missing`() {
            val request = MockHttpServletRequest("GET", "/api/feed")
            request.requestURI = "/api/feed"
            request.addHeader("X-User-Id", UUID.randomUUID().toString())

            val response = MockHttpServletResponse()
            var chainCalled = false
            val chain = FilterChain { _, _ -> chainCalled = true }

            filter.doFilter(request, response, chain)

            assertThat(chainCalled).isFalse()
            assertThat(response.status).isEqualTo(403)
        }

        @Test
        fun `should return 403 when X-User-Id is missing`() {
            val request = MockHttpServletRequest("GET", "/api/feed")
            request.requestURI = "/api/feed"
            request.addHeader("X-Gateway-Signature", "some-signature")

            val response = MockHttpServletResponse()
            var chainCalled = false
            val chain = FilterChain { _, _ -> chainCalled = true }

            filter.doFilter(request, response, chain)

            assertThat(chainCalled).isFalse()
            assertThat(response.status).isEqualTo(403)
        }
    }

    @Nested
    inner class `invalid signature` {

        @Test
        fun `should return 403 when signature is wrong`() {
            val request = MockHttpServletRequest("GET", "/api/feed")
            request.requestURI = "/api/feed"
            request.addHeader("X-User-Id", UUID.randomUUID().toString())
            request.addHeader("X-User-Roles", "USER")
            request.addHeader("X-Subscription-Tier", "free")
            request.addHeader("X-Request-Id", UUID.randomUUID().toString())
            request.addHeader("X-Gateway-Signature", "definitely-wrong-signature")

            val response = MockHttpServletResponse()
            var chainCalled = false
            val chain = FilterChain { _, _ -> chainCalled = true }

            filter.doFilter(request, response, chain)

            assertThat(chainCalled).isFalse()
            assertThat(response.status).isEqualTo(403)
            assertThat(response.contentAsString).contains("FORBIDDEN")
        }
    }

    @Nested
    inner class `public paths` {

        @Test
        fun `should not filter health endpoint`() {
            val request = MockHttpServletRequest("GET", "/health")
            request.requestURI = "/health"

            assertThat(filter.shouldNotFilter(request)).isTrue()
        }

        @Test
        fun `should filter protected endpoint`() {
            val request = MockHttpServletRequest("GET", "/api/feed")
            request.requestURI = "/api/feed"

            assertThat(filter.shouldNotFilter(request)).isFalse()
        }
    }
}
