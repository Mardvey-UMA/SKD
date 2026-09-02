package com.skd.subscription.unit

import com.skd.subscription.presentation.filter.GatewaySignatureFilter
import io.mockk.mockk
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

    private val hmacSecret = "test-hmac-secret-for-unit-tests"
    private val filter = GatewaySignatureFilter(hmacSecret)

    private lateinit var request: MockHttpServletRequest
    private lateinit var response: MockHttpServletResponse
    private lateinit var filterChain: MockFilterChain

    @BeforeEach
    fun setup() {
        request = MockHttpServletRequest()
        response = MockHttpServletResponse()
        filterChain = MockFilterChain()
    }

    private fun computeHmac(data: String, secret: String): String {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(secret.toByteArray(), "HmacSHA256"))
        return Base64.getEncoder().encodeToString(mac.doFinal(data.toByteArray()))
    }

    @Nested
    inner class `valid signature` {

        @Test
        fun `should pass through when X-Gateway-Signature is valid HMAC of payload`() {
            val userId = UUID.randomUUID().toString()
            val roles = "ROLE_USER"
            val tier = "premium"
            val requestId = UUID.randomUUID().toString()
            val payload = "$userId|$roles|$tier|$requestId"
            val signature = computeHmac(payload, hmacSecret)

            request.requestURI = "/api/subscription/status"
            request.method = "GET"
            request.addHeader("X-Gateway-Signature", signature)
            request.addHeader("X-User-Id", userId)
            request.addHeader("X-User-Roles", roles)
            request.addHeader("X-Subscription-Tier", tier)
            request.addHeader("X-Request-Id", requestId)

            filter.doFilter(request, response, filterChain)

            assertThat(response.status).isEqualTo(200)
            assertThat(filterChain.request).isNotNull
        }
    }

    @Nested
    inner class `missing signature` {

        @Test
        fun `should return 401 when X-Gateway-Signature header is missing`() {
            val userId = UUID.randomUUID().toString()
            request.requestURI = "/api/subscription/status"
            request.method = "GET"
            request.addHeader("X-User-Id", userId)

            filter.doFilter(request, response, filterChain)

            assertThat(response.status).isEqualTo(401)
            assertThat(filterChain.request).isNull()
        }

        @Test
        fun `should return 401 when X-User-Id header is missing`() {
            request.requestURI = "/api/subscription/status"
            request.method = "GET"
            request.addHeader("X-Gateway-Signature", "some-signature")

            filter.doFilter(request, response, filterChain)

            assertThat(response.status).isEqualTo(401)
            assertThat(filterChain.request).isNull()
        }
    }

    @Nested
    inner class `invalid signature` {

        @Test
        fun `should return 401 when signature does not match computed HMAC`() {
            val userId = UUID.randomUUID().toString()
            val roles = "ROLE_USER"
            val tier = "free"
            val requestId = UUID.randomUUID().toString()

            request.requestURI = "/api/subscription/status"
            request.method = "GET"
            request.addHeader("X-Gateway-Signature", "invalid-signature-value")
            request.addHeader("X-User-Id", userId)
            request.addHeader("X-User-Roles", roles)
            request.addHeader("X-Subscription-Tier", tier)
            request.addHeader("X-Request-Id", requestId)

            filter.doFilter(request, response, filterChain)

            assertThat(response.status).isEqualTo(401)
            assertThat(filterChain.request).isNull()
        }
    }

    @Nested
    inner class `excluded paths` {

        @Test
        fun `should not filter webhook yookassa path`() {
            request.requestURI = "/webhook/yookassa"
            request.method = "POST"

            filter.doFilter(request, response, filterChain)

            assertThat(response.status).isEqualTo(200)
            assertThat(filterChain.request).isNotNull
        }

        @Test
        fun `should not filter health endpoint`() {
            request.requestURI = "/health"
            request.method = "GET"

            filter.doFilter(request, response, filterChain)

            assertThat(response.status).isEqualTo(200)
            assertThat(filterChain.request).isNotNull
        }
    }
}
