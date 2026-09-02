package com.contentplatform.auth.unit.security

import com.contentplatform.auth.configuration.GatewayProperties
import com.contentplatform.auth.security.GatewaySignatureFilter
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus
import org.springframework.mock.web.MockFilterChain
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.mock.web.MockHttpServletResponse
import org.springframework.security.core.context.SecurityContextHolder
import java.util.Base64
import java.util.UUID
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

@Tag("unit")
class GatewaySignatureFilterTest {

    private val hmacSecret = "test-secret-key-for-unit-tests"
    private val gatewayProperties = GatewayProperties().apply { hmacSecret = this@GatewaySignatureFilterTest.hmacSecret }
    private val filter = GatewaySignatureFilter(gatewayProperties)

    private lateinit var request: MockHttpServletRequest
    private lateinit var response: MockHttpServletResponse
    private lateinit var filterChain: MockFilterChain

    @BeforeEach
    fun setUp() {
        request = MockHttpServletRequest()
        response = MockHttpServletResponse()
        filterChain = MockFilterChain()
        SecurityContextHolder.clearContext()
    }

    private fun computeHmac(payload: String): String {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(hmacSecret.toByteArray(), "HmacSHA256"))
        return Base64.getEncoder().encodeToString(mac.doFinal(payload.toByteArray()))
    }

    private fun setValidGatewayHeaders(
        userId: String = UUID.randomUUID().toString(),
        roles: String = "USER",
        subscriptionTier: String = "free",
        requestId: String = UUID.randomUUID().toString()
    ) {
        val payload = "$userId|$roles|$subscriptionTier|$requestId"
        val signature = computeHmac(payload)

        request.addHeader("X-User-Id", userId)
        request.addHeader("X-User-Roles", roles)
        request.addHeader("X-Subscription-Tier", subscriptionTier)
        request.addHeader("X-Request-Id", requestId)
        request.addHeader("X-Gateway-Signature", signature)
    }

    @Nested
    inner class `when HMAC signature is valid` {

        @Test
        fun `should pass request through filter chain`() {
            request.requestURI = "/api/auth/logout"
            request.method = "POST"
            setValidGatewayHeaders()

            filter.doFilter(request, response, filterChain)

            assertThat(response.status).isEqualTo(HttpStatus.OK.value())
            assertThat(filterChain.request).isNotNull
        }

        @Test
        fun `should set authentication in security context with user id`() {
            val userId = UUID.randomUUID().toString()
            request.requestURI = "/api/auth/password/change"
            request.method = "POST"
            setValidGatewayHeaders(userId = userId)

            filter.doFilter(request, response, filterChain)

            val authentication = SecurityContextHolder.getContext().authentication
            assertThat(authentication).isNotNull
            assertThat(authentication.name).isEqualTo(userId)
        }

        @Test
        fun `should set authentication with roles from header`() {
            request.requestURI = "/api/auth/logout"
            request.method = "POST"
            setValidGatewayHeaders(roles = "USER")

            filter.doFilter(request, response, filterChain)

            val authentication = SecurityContextHolder.getContext().authentication
            assertThat(authentication).isNotNull
            assertThat(authentication.authorities.map { it.authority }).contains("ROLE_USER")
        }

        @Test
        fun `should set authentication with subscription tier`() {
            request.requestURI = "/api/auth/logout"
            request.method = "POST"
            setValidGatewayHeaders(subscriptionTier = "premium")

            filter.doFilter(request, response, filterChain)

            val authentication = SecurityContextHolder.getContext().authentication
            assertThat(authentication).isNotNull
            assertThat(authentication.isAuthenticated).isTrue()
        }
    }

    @Nested
    inner class `when HMAC signature is invalid` {

        @Test
        fun `should return 403 Forbidden`() {
            request.requestURI = "/api/auth/logout"
            request.method = "POST"

            val userId = UUID.randomUUID().toString()
            request.addHeader("X-User-Id", userId)
            request.addHeader("X-User-Roles", "USER")
            request.addHeader("X-Subscription-Tier", "free")
            request.addHeader("X-Request-Id", UUID.randomUUID().toString())
            request.addHeader("X-Gateway-Signature", "invalid-signature-value")

            filter.doFilter(request, response, filterChain)

            assertThat(response.status).isEqualTo(HttpStatus.FORBIDDEN.value())
        }

        @Test
        fun `should not pass request through filter chain`() {
            request.requestURI = "/api/auth/logout"
            request.method = "POST"

            request.addHeader("X-User-Id", UUID.randomUUID().toString())
            request.addHeader("X-User-Roles", "USER")
            request.addHeader("X-Subscription-Tier", "free")
            request.addHeader("X-Request-Id", UUID.randomUUID().toString())
            request.addHeader("X-Gateway-Signature", "tampered-signature")

            filter.doFilter(request, response, filterChain)

            assertThat(filterChain.request).isNull()
        }

        @Test
        fun `should return JSON error body on invalid signature`() {
            request.requestURI = "/api/auth/logout"
            request.method = "POST"

            request.addHeader("X-User-Id", UUID.randomUUID().toString())
            request.addHeader("X-User-Roles", "USER")
            request.addHeader("X-Subscription-Tier", "free")
            request.addHeader("X-Request-Id", UUID.randomUUID().toString())
            request.addHeader("X-Gateway-Signature", "wrong")

            filter.doFilter(request, response, filterChain)

            assertThat(response.contentType).contains("application/json")
            assertThat(response.contentAsString).contains("error")
        }

        @Test
        fun `should not set authentication in security context`() {
            request.requestURI = "/api/auth/logout"
            request.method = "POST"

            request.addHeader("X-User-Id", UUID.randomUUID().toString())
            request.addHeader("X-User-Roles", "USER")
            request.addHeader("X-Subscription-Tier", "free")
            request.addHeader("X-Request-Id", UUID.randomUUID().toString())
            request.addHeader("X-Gateway-Signature", "bad")

            filter.doFilter(request, response, filterChain)

            assertThat(SecurityContextHolder.getContext().authentication).isNull()
        }
    }

    @Nested
    inner class `when required headers are missing` {

        @Test
        fun `should return 403 when X-User-Id header is missing`() {
            request.requestURI = "/api/auth/logout"
            request.method = "POST"

            request.addHeader("X-User-Roles", "USER")
            request.addHeader("X-Subscription-Tier", "free")
            request.addHeader("X-Request-Id", UUID.randomUUID().toString())
            request.addHeader("X-Gateway-Signature", "some-sig")

            filter.doFilter(request, response, filterChain)

            assertThat(response.status).isEqualTo(HttpStatus.FORBIDDEN.value())
        }

        @Test
        fun `should return 403 when X-Gateway-Signature header is missing`() {
            request.requestURI = "/api/auth/logout"
            request.method = "POST"

            request.addHeader("X-User-Id", UUID.randomUUID().toString())
            request.addHeader("X-User-Roles", "USER")
            request.addHeader("X-Subscription-Tier", "free")
            request.addHeader("X-Request-Id", UUID.randomUUID().toString())

            filter.doFilter(request, response, filterChain)

            assertThat(response.status).isEqualTo(HttpStatus.FORBIDDEN.value())
        }
    }

    @Nested
    inner class `when request targets public endpoints` {

        @Test
        fun `should pass through health endpoint without any headers`() {
            request.requestURI = "/health"
            request.method = "GET"

            filter.doFilter(request, response, filterChain)

            // Filter should skip -- request passes through, no 403
            assertThat(filterChain.request).isNotNull
            assertThat(response.status).isNotEqualTo(HttpStatus.FORBIDDEN.value())
        }

        @Test
        fun `should pass through register endpoint without any headers`() {
            request.requestURI = "/api/auth/register"
            request.method = "POST"

            filter.doFilter(request, response, filterChain)

            assertThat(filterChain.request).isNotNull
            assertThat(response.status).isNotEqualTo(HttpStatus.FORBIDDEN.value())
        }

        @Test
        fun `should pass through login endpoint without any headers`() {
            request.requestURI = "/api/auth/login"
            request.method = "POST"

            filter.doFilter(request, response, filterChain)

            assertThat(filterChain.request).isNotNull
            assertThat(response.status).isNotEqualTo(HttpStatus.FORBIDDEN.value())
        }

        @Test
        fun `should pass through email verification endpoint without any headers`() {
            request.requestURI = "/api/auth/verify"
            request.method = "GET"

            filter.doFilter(request, response, filterChain)

            assertThat(filterChain.request).isNotNull
            assertThat(response.status).isNotEqualTo(HttpStatus.FORBIDDEN.value())
        }

        @Test
        fun `should pass through token refresh endpoint without any headers`() {
            request.requestURI = "/api/auth/refresh"
            request.method = "POST"

            filter.doFilter(request, response, filterChain)

            assertThat(filterChain.request).isNotNull
            assertThat(response.status).isNotEqualTo(HttpStatus.FORBIDDEN.value())
        }

        @Test
        fun `should pass through password reset request endpoint without any headers`() {
            request.requestURI = "/api/auth/password/reset-request"
            request.method = "POST"

            filter.doFilter(request, response, filterChain)

            assertThat(filterChain.request).isNotNull
            assertThat(response.status).isNotEqualTo(HttpStatus.FORBIDDEN.value())
        }

        @Test
        fun `should pass through password reset endpoint without any headers`() {
            request.requestURI = "/api/auth/password/reset"
            request.method = "POST"

            filter.doFilter(request, response, filterChain)

            assertThat(filterChain.request).isNotNull
            assertThat(response.status).isNotEqualTo(HttpStatus.FORBIDDEN.value())
        }

        @Test
        fun `should pass through JWKS endpoint without any headers`() {
            request.requestURI = "/.well-known/jwks.json"
            request.method = "GET"

            filter.doFilter(request, response, filterChain)

            assertThat(filterChain.request).isNotNull
            assertThat(response.status).isNotEqualTo(HttpStatus.FORBIDDEN.value())
        }

        @Test
        fun `should NOT pass through logout endpoint without headers`() {
            request.requestURI = "/api/auth/logout"
            request.method = "POST"

            filter.doFilter(request, response, filterChain)

            // Logout requires authentication -- should be blocked
            assertThat(response.status).isEqualTo(HttpStatus.FORBIDDEN.value())
        }

        @Test
        fun `should NOT pass through password change endpoint without headers`() {
            request.requestURI = "/api/auth/password/change"
            request.method = "POST"

            filter.doFilter(request, response, filterChain)

            // Password change requires authentication -- should be blocked
            assertThat(response.status).isEqualTo(HttpStatus.FORBIDDEN.value())
        }
    }
}
