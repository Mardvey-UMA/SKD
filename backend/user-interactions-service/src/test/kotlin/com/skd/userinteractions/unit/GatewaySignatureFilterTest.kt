package com.skd.userinteractions.unit

import com.skd.userinteractions.configuration.GatewaySignatureFilter
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.springframework.mock.web.MockFilterChain
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.mock.web.MockHttpServletResponse
import java.util.Base64
import java.util.UUID
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

@Tag("unit")
class GatewaySignatureFilterTest {

    private val hmacSecret = "test-hmac-secret"
    private lateinit var filter: GatewaySignatureFilter

    @BeforeEach
    fun setUp() {
        filter = GatewaySignatureFilter(hmacSecret)
    }

    private fun computeHmac(payload: String): String {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(hmacSecret.toByteArray(), "HmacSHA256"))
        return Base64.getEncoder().encodeToString(mac.doFinal(payload.toByteArray()))
    }

    private fun authenticatedRequest(
        userId: String = UUID.randomUUID().toString(),
        roles: String = "USER",
        subscriptionTier: String = "FREE",
        requestId: String = UUID.randomUUID().toString()
    ): MockHttpServletRequest {
        val request = MockHttpServletRequest()
        request.addHeader("X-User-Id", userId)
        request.addHeader("X-User-Roles", roles)
        request.addHeader("X-Subscription-Tier", subscriptionTier)
        request.addHeader("X-Request-Id", requestId)

        val payload = "$userId|$roles|$subscriptionTier|$requestId"
        request.addHeader("X-Gateway-Signature", computeHmac(payload))
        request.requestURI = "/api/interactions/batch"
        return request
    }

    @Nested
    inner class `valid signature` {

        @Test
        fun `request with valid HMAC signature passes through filter`() {
            val request = authenticatedRequest()
            val response = MockHttpServletResponse()
            val filterChain = MockFilterChain()

            filter.doFilter(request, response, filterChain)

            assertThat(response.status).isEqualTo(200)
            assertThat(filterChain.request).isNotNull
        }
    }

    @Nested
    inner class `missing headers` {

        @Test
        fun `request without X-Gateway-Signature returns 401`() {
            val request = MockHttpServletRequest()
            request.addHeader("X-User-Id", UUID.randomUUID().toString())
            request.requestURI = "/api/interactions/batch"
            val response = MockHttpServletResponse()
            val filterChain = MockFilterChain()

            filter.doFilter(request, response, filterChain)

            assertThat(response.status).isEqualTo(401)
            assertThat(filterChain.request).isNull()
        }

        @Test
        fun `request without X-User-Id returns 401`() {
            val request = MockHttpServletRequest()
            request.addHeader("X-Gateway-Signature", "some-signature")
            request.requestURI = "/api/interactions/batch"
            val response = MockHttpServletResponse()
            val filterChain = MockFilterChain()

            filter.doFilter(request, response, filterChain)

            assertThat(response.status).isEqualTo(401)
            assertThat(filterChain.request).isNull()
        }
    }

    @Nested
    inner class `invalid signature` {

        @Test
        fun `request with wrong signature returns 401`() {
            val request = MockHttpServletRequest()
            val userId = UUID.randomUUID().toString()
            request.addHeader("X-User-Id", userId)
            request.addHeader("X-User-Roles", "USER")
            request.addHeader("X-Subscription-Tier", "FREE")
            request.addHeader("X-Request-Id", UUID.randomUUID().toString())
            request.addHeader("X-Gateway-Signature", "invalid-base64-signature")
            request.requestURI = "/api/interactions/batch"
            val response = MockHttpServletResponse()
            val filterChain = MockFilterChain()

            filter.doFilter(request, response, filterChain)

            assertThat(response.status).isEqualTo(401)
            assertThat(filterChain.request).isNull()
        }

        @Test
        fun `request with signature computed from different payload returns 401`() {
            val request = MockHttpServletRequest()
            val userId = UUID.randomUUID().toString()
            request.addHeader("X-User-Id", userId)
            request.addHeader("X-User-Roles", "USER")
            request.addHeader("X-Subscription-Tier", "FREE")
            request.addHeader("X-Request-Id", UUID.randomUUID().toString())
            // Sign a different payload
            request.addHeader("X-Gateway-Signature", computeHmac("tampered|payload"))
            request.requestURI = "/api/interactions/batch"
            val response = MockHttpServletResponse()
            val filterChain = MockFilterChain()

            filter.doFilter(request, response, filterChain)

            assertThat(response.status).isEqualTo(401)
            assertThat(filterChain.request).isNull()
        }
    }

    @Nested
    inner class `public endpoints` {

        @Test
        fun `health endpoint bypasses HMAC filter`() {
            val request = MockHttpServletRequest()
            request.requestURI = "/health"
            val response = MockHttpServletResponse()
            val filterChain = MockFilterChain()

            filter.doFilter(request, response, filterChain)

            assertThat(response.status).isEqualTo(200)
            assertThat(filterChain.request).isNotNull
        }
    }
}
