package com.contentplatform.auth.integration.security

import com.contentplatform.auth.integration.IntegrationTestBase
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.test.web.client.TestRestTemplate
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import java.util.Base64
import java.util.UUID
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

@Tag("integration")
class SecurityConfigIntegrationTest : IntegrationTestBase() {

    @Autowired
    lateinit var restTemplate: TestRestTemplate

    @Value("\${gateway.hmac-secret}")
    lateinit var hmacSecret: String

    private fun computeHmac(payload: String): String {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(hmacSecret.toByteArray(), "HmacSHA256"))
        return Base64.getEncoder().encodeToString(mac.doFinal(payload.toByteArray()))
    }

    private fun buildAuthenticatedHeaders(
        userId: String = UUID.randomUUID().toString(),
        roles: String = "USER",
        subscriptionTier: String = "free",
        requestId: String = UUID.randomUUID().toString()
    ): HttpHeaders {
        val payload = "$userId|$roles|$subscriptionTier|$requestId"
        val signature = computeHmac(payload)

        return HttpHeaders().apply {
            set("X-User-Id", userId)
            set("X-User-Roles", roles)
            set("X-Subscription-Tier", subscriptionTier)
            set("X-Request-Id", requestId)
            set("X-Gateway-Signature", signature)
        }
    }

    @Nested
    inner class `public endpoints` {

        @Test
        fun `health endpoint should be accessible without authentication`() {
            val response = restTemplate.getForEntity("/health", String::class.java)

            // Should not be blocked by HMAC security filter (403)
            assertThat(response.statusCode).isNotEqualTo(HttpStatus.FORBIDDEN)
        }

        @Test
        fun `register endpoint should be accessible without authentication`() {
            val response = restTemplate.postForEntity(
                "/api/auth/register",
                HttpEntity("""{"email":"test@test.com","password":"Pass123!"}""", HttpHeaders().apply {
                    set("Content-Type", "application/json")
                }),
                String::class.java
            )

            // Should not be blocked by HMAC security filter (403)
            // Business logic may return 401 for invalid credentials -- that is expected
            assertThat(response.statusCode).isNotEqualTo(HttpStatus.FORBIDDEN)
        }

        @Test
        fun `login endpoint should be accessible without authentication`() {
            val response = restTemplate.postForEntity(
                "/api/auth/login",
                HttpEntity("""{"email":"test@test.com","password":"Pass123!"}""", HttpHeaders().apply {
                    set("Content-Type", "application/json")
                }),
                String::class.java
            )

            // Should not be blocked by HMAC security filter (403)
            // Business logic may return 401 for invalid credentials -- that is expected
            assertThat(response.statusCode).isNotEqualTo(HttpStatus.FORBIDDEN)
        }

        @Test
        fun `verify endpoint should be accessible without authentication`() {
            val response = restTemplate.getForEntity("/api/auth/verify?token=abc", String::class.java)

            // Should not be blocked by HMAC security filter (403)
            assertThat(response.statusCode).isNotEqualTo(HttpStatus.FORBIDDEN)
        }

        @Test
        fun `refresh endpoint should be accessible without authentication`() {
            val response = restTemplate.postForEntity(
                "/api/auth/refresh",
                HttpEntity("""{"refreshToken":"abc"}""", HttpHeaders().apply {
                    set("Content-Type", "application/json")
                }),
                String::class.java
            )

            // Should not be blocked by HMAC security filter (403)
            assertThat(response.statusCode).isNotEqualTo(HttpStatus.FORBIDDEN)
        }

        @Test
        fun `password reset request endpoint should be accessible without authentication`() {
            val response = restTemplate.postForEntity(
                "/api/auth/password/reset-request",
                HttpEntity("""{"email":"test@test.com"}""", HttpHeaders().apply {
                    set("Content-Type", "application/json")
                }),
                String::class.java
            )

            // Should not be blocked by HMAC security filter (403)
            assertThat(response.statusCode).isNotEqualTo(HttpStatus.FORBIDDEN)
        }

        @Test
        fun `password reset endpoint should be accessible without authentication`() {
            val response = restTemplate.postForEntity(
                "/api/auth/password/reset",
                HttpEntity("""{"token":"abc","newPassword":"Pass123!"}""", HttpHeaders().apply {
                    set("Content-Type", "application/json")
                }),
                String::class.java
            )

            // Should not be blocked by HMAC security filter (403)
            assertThat(response.statusCode).isNotEqualTo(HttpStatus.FORBIDDEN)
        }

        @Test
        fun `JWKS endpoint should be accessible without authentication`() {
            val response = restTemplate.getForEntity("/.well-known/jwks.json", String::class.java)

            // Should not be blocked by HMAC security filter (403)
            assertThat(response.statusCode).isNotEqualTo(HttpStatus.FORBIDDEN)
        }
    }

    @Nested
    inner class `authenticated endpoints without valid HMAC` {

        @Test
        fun `logout endpoint should return 403 without gateway headers`() {
            val response = restTemplate.postForEntity(
                "/api/auth/logout",
                HttpEntity(null, HttpHeaders().apply {
                    set("Content-Type", "application/json")
                }),
                String::class.java
            )

            assertThat(response.statusCode).isEqualTo(HttpStatus.FORBIDDEN)
        }

        @Test
        fun `password change endpoint should return 403 without gateway headers`() {
            val response = restTemplate.postForEntity(
                "/api/auth/password/change",
                HttpEntity("""{"oldPassword":"old","newPassword":"new"}""", HttpHeaders().apply {
                    set("Content-Type", "application/json")
                }),
                String::class.java
            )

            assertThat(response.statusCode).isEqualTo(HttpStatus.FORBIDDEN)
        }

        @Test
        fun `logout endpoint should return 403 with invalid HMAC signature`() {
            val headers = HttpHeaders().apply {
                set("X-User-Id", UUID.randomUUID().toString())
                set("X-User-Roles", "USER")
                set("X-Subscription-Tier", "free")
                set("X-Request-Id", UUID.randomUUID().toString())
                set("X-Gateway-Signature", "invalid-signature")
                set("Content-Type", "application/json")
            }

            val response = restTemplate.postForEntity(
                "/api/auth/logout",
                HttpEntity(null, headers),
                String::class.java
            )

            assertThat(response.statusCode).isEqualTo(HttpStatus.FORBIDDEN)
        }
    }

    @Nested
    inner class `authenticated endpoints with valid HMAC` {

        @Test
        fun `logout endpoint should not return 403 with valid gateway signature`() {
            val headers = buildAuthenticatedHeaders()
            headers.set("Content-Type", "application/json")

            val response = restTemplate.postForEntity(
                "/api/auth/logout",
                HttpEntity(null, headers),
                String::class.java
            )

            // Controller may not exist (404), but must NOT be 403
            assertThat(response.statusCode).isNotIn(HttpStatus.UNAUTHORIZED, HttpStatus.FORBIDDEN)
        }

        @Test
        fun `password change endpoint should not return 403 with valid gateway signature`() {
            val headers = buildAuthenticatedHeaders()
            headers.set("Content-Type", "application/json")

            val response = restTemplate.postForEntity(
                "/api/auth/password/change",
                HttpEntity("""{"oldPassword":"old","newPassword":"new"}""", headers),
                String::class.java
            )

            // Controller may not exist (404), but must NOT be 403
            assertThat(response.statusCode).isNotIn(HttpStatus.UNAUTHORIZED, HttpStatus.FORBIDDEN)
        }
    }

    @Nested
    inner class `security configuration` {

        @Test
        fun `should return responses without CSRF token requirement`() {
            // POST to a public endpoint without CSRF token should work
            val response = restTemplate.postForEntity(
                "/api/auth/register",
                HttpEntity("""{"email":"csrf@test.com","password":"Pass123!"}""", HttpHeaders().apply {
                    set("Content-Type", "application/json")
                }),
                String::class.java
            )

            // Should not be 403 due to CSRF (CSRF must be disabled)
            assertThat(response.statusCode).isNotEqualTo(HttpStatus.FORBIDDEN)
        }
    }
}
