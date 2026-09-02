package com.contentplatform.gateway.unit

import com.contentplatform.gateway.config.GatewayProperties
import com.contentplatform.gateway.filter.HeaderEnrichmentFilter
import com.contentplatform.gateway.filter.RequestIdFilter
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.springframework.mock.http.server.reactive.MockServerHttpRequest
import org.springframework.mock.web.server.MockServerWebExchange
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken
import org.springframework.web.server.ServerWebExchange
import org.springframework.web.server.WebFilterChain
import reactor.core.publisher.Mono
import java.time.Instant
import java.util.Base64
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * Unit tests for HeaderEnrichmentFilter.
 *
 * Verifies:
 * - X-User-Id is set from JWT sub claim
 * - X-User-Roles is set from JWT roles claim (comma-joined)
 * - X-Subscription-Tier is set from JWT subscription_tier claim
 * - X-Gateway-Signature is a valid HMAC-SHA256 of "$userId|$roles|$tier|$requestId"
 * - Filter is skipped for unauthenticated requests
 */
@Tag("unit")
class HeaderEnrichmentFilterTest {

    private val hmacSecret = "test-hmac-secret-for-unit-tests"
    private val properties = GatewayProperties(hmacSecret = hmacSecret)
    private val filter = HeaderEnrichmentFilter(properties)

    private fun createJwt(
        sub: String = "550e8400-e29b-41d4-a716-446655440000",
        roles: List<String> = listOf("USER", "SUBSCRIBER"),
        subscriptionTier: String = "premium"
    ): Jwt {
        return Jwt.withTokenValue("mock-token")
            .header("alg", "RS256")
            .claim("sub", sub)
            .claim("roles", roles)
            .claim("subscription_tier", subscriptionTier)
            .claim("jti", "test-jti")
            .issuedAt(Instant.now())
            .expiresAt(Instant.now().plusSeconds(900))
            .build()
    }

    private fun computeExpectedHmac(payload: String): String {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(hmacSecret.toByteArray(), "HmacSHA256"))
        return Base64.getEncoder().encodeToString(mac.doFinal(payload.toByteArray()))
    }

    @Test
    fun `should set X-User-Id header from JWT sub claim`() {
        val jwt = createJwt(sub = "user-uuid-123")
        val request = MockServerHttpRequest.get("/api/users/me").build()
        val exchange = MockServerWebExchange.from(request)
        exchange.attributes[RequestIdFilter.REQUEST_ID_ATTR] = "req-id-1"

        var capturedExchange: ServerWebExchange? = null
        val chain = WebFilterChain { ex ->
            capturedExchange = ex
            Mono.empty()
        }

        filter.filter(exchange, chain)
            .contextWrite(
                org.springframework.security.core.context.ReactiveSecurityContextHolder
                    .withAuthentication(
                        JwtAuthenticationToken(jwt, listOf(SimpleGrantedAuthority("ROLE_USER")))
                    )
            )
            .block()

        val userId = capturedExchange?.request?.headers?.getFirst("X-User-Id")
        assertThat(userId).isEqualTo("user-uuid-123")
    }

    @Test
    fun `should set X-User-Roles header from JWT roles claim comma-joined`() {
        val jwt = createJwt(roles = listOf("USER", "SUBSCRIBER"))
        val request = MockServerHttpRequest.get("/api/users/me").build()
        val exchange = MockServerWebExchange.from(request)
        exchange.attributes[RequestIdFilter.REQUEST_ID_ATTR] = "req-id-1"

        var capturedExchange: ServerWebExchange? = null
        val chain = WebFilterChain { ex ->
            capturedExchange = ex
            Mono.empty()
        }

        filter.filter(exchange, chain)
            .contextWrite(
                org.springframework.security.core.context.ReactiveSecurityContextHolder
                    .withAuthentication(
                        JwtAuthenticationToken(jwt, listOf(SimpleGrantedAuthority("ROLE_USER")))
                    )
            )
            .block()

        val roles = capturedExchange?.request?.headers?.getFirst("X-User-Roles")
        assertThat(roles).isEqualTo("USER,SUBSCRIBER")
    }

    @Test
    fun `should set X-Subscription-Tier header from JWT claim`() {
        val jwt = createJwt(subscriptionTier = "premium")
        val request = MockServerHttpRequest.get("/api/users/me").build()
        val exchange = MockServerWebExchange.from(request)
        exchange.attributes[RequestIdFilter.REQUEST_ID_ATTR] = "req-id-1"

        var capturedExchange: ServerWebExchange? = null
        val chain = WebFilterChain { ex ->
            capturedExchange = ex
            Mono.empty()
        }

        filter.filter(exchange, chain)
            .contextWrite(
                org.springframework.security.core.context.ReactiveSecurityContextHolder
                    .withAuthentication(
                        JwtAuthenticationToken(jwt, listOf(SimpleGrantedAuthority("ROLE_USER")))
                    )
            )
            .block()

        val tier = capturedExchange?.request?.headers?.getFirst("X-Subscription-Tier")
        assertThat(tier).isEqualTo("premium")
    }

    @Test
    fun `should compute X-Gateway-Signature as HMAC-SHA256 of userId roles tier requestId`() {
        val jwt = createJwt(
            sub = "user-1",
            roles = listOf("USER"),
            subscriptionTier = "free"
        )
        val requestId = "req-id-abc"
        val request = MockServerHttpRequest.get("/api/users/me").build()
        val exchange = MockServerWebExchange.from(request)
        exchange.attributes[RequestIdFilter.REQUEST_ID_ATTR] = requestId

        var capturedExchange: ServerWebExchange? = null
        val chain = WebFilterChain { ex ->
            capturedExchange = ex
            Mono.empty()
        }

        filter.filter(exchange, chain)
            .contextWrite(
                org.springframework.security.core.context.ReactiveSecurityContextHolder
                    .withAuthentication(
                        JwtAuthenticationToken(jwt, listOf(SimpleGrantedAuthority("ROLE_USER")))
                    )
            )
            .block()

        val signature = capturedExchange?.request?.headers?.getFirst("X-Gateway-Signature")
        assertThat(signature).isNotNull()

        val expectedPayload = "user-1|USER|free|req-id-abc"
        val expectedSignature = computeExpectedHmac(expectedPayload)
        assertThat(signature).isEqualTo(expectedSignature)
    }

    @Test
    fun `should skip enrichment for unauthenticated requests`() {
        val request = MockServerHttpRequest.get("/api/auth/register").build()
        val exchange = MockServerWebExchange.from(request)

        var capturedExchange: ServerWebExchange? = null
        val chain = WebFilterChain { ex ->
            capturedExchange = ex
            Mono.empty()
        }

        // No security context — unauthenticated
        filter.filter(exchange, chain).block()

        // Chain should be called without enrichment headers
        assertThat(capturedExchange).isNotNull()
        assertThat(capturedExchange?.request?.headers?.getFirst("X-User-Id")).isNull()
        assertThat(capturedExchange?.request?.headers?.getFirst("X-Gateway-Signature")).isNull()
    }

    @Test
    fun `should use X-Request-Id from exchange attribute for HMAC payload`() {
        val jwt = createJwt(sub = "user-2", roles = listOf("ADMIN"), subscriptionTier = "premium")
        val requestId = "unique-request-id-xyz"
        val request = MockServerHttpRequest.get("/api/users/me").build()
        val exchange = MockServerWebExchange.from(request)
        exchange.attributes[RequestIdFilter.REQUEST_ID_ATTR] = requestId

        var capturedExchange: ServerWebExchange? = null
        val chain = WebFilterChain { ex ->
            capturedExchange = ex
            Mono.empty()
        }

        filter.filter(exchange, chain)
            .contextWrite(
                org.springframework.security.core.context.ReactiveSecurityContextHolder
                    .withAuthentication(
                        JwtAuthenticationToken(jwt, listOf(SimpleGrantedAuthority("ROLE_ADMIN")))
                    )
            )
            .block()

        val signature = capturedExchange?.request?.headers?.getFirst("X-Gateway-Signature")
        val expectedPayload = "user-2|ADMIN|premium|unique-request-id-xyz"
        val expectedSignature = computeExpectedHmac(expectedPayload)
        assertThat(signature).isEqualTo(expectedSignature)

        // Also verify X-Request-Id is forwarded in the request
        val forwardedRequestId = capturedExchange?.request?.headers?.getFirst("X-Request-Id")
        assertThat(forwardedRequestId).isEqualTo(requestId)
    }
}
