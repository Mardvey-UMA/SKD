package com.contentplatform.gateway.unit

import com.contentplatform.gateway.filter.TokenRevocationFilter
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.springframework.data.redis.core.ReactiveStringRedisTemplate
import org.springframework.data.redis.core.ReactiveValueOperations
import org.springframework.http.HttpStatus
import org.springframework.mock.http.server.reactive.MockServerHttpRequest
import org.springframework.mock.web.server.MockServerWebExchange
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken
import org.springframework.web.server.ServerWebExchange
import org.springframework.web.server.WebFilterChain
import reactor.core.publisher.Mono
import java.time.Instant

/**
 * Unit tests for TokenRevocationFilter.
 *
 * Verifies:
 * - When Redis has "revoked:{jti}" key, request is rejected with 401
 * - When Redis throws exception, request continues (fail-open)
 * - When JWT has no jti claim, request is rejected with 401
 * - When request is unauthenticated, filter is skipped
 */
@Tag("unit")
class TokenRevocationFilterTest {

    private val redisTemplate = mockk<ReactiveStringRedisTemplate>()
    private val valueOps = mockk<ReactiveValueOperations<String, String>>()
    private val filter = TokenRevocationFilter(redisTemplate)

    private fun createJwt(
        jti: String? = "test-jti-123",
        sub: String = "user-1",
        roles: List<String> = listOf("USER"),
        subscriptionTier: String = "free"
    ): Jwt {
        val builder = Jwt.withTokenValue("mock-token")
            .header("alg", "RS256")
            .claim("sub", sub)
            .claim("roles", roles)
            .claim("subscription_tier", subscriptionTier)
            .issuedAt(Instant.now())
            .expiresAt(Instant.now().plusSeconds(900))

        if (jti != null) {
            builder.claim("jti", jti)
        }

        return builder.build()
    }

    private fun createAuthenticatedExchange(jwt: Jwt): MockServerWebExchange {
        val request = MockServerHttpRequest.get("/api/users/me").build()
        val exchange = MockServerWebExchange.from(request)
        val auth = JwtAuthenticationToken(jwt, listOf(SimpleGrantedAuthority("ROLE_USER")))
        // Store authentication in exchange attributes for the filter to find
        exchange.attributes["org.springframework.security.context"] = auth
        return exchange
    }

    @Test
    fun `should return 401 when token jti is found in Redis revocation set`() {
        val jwt = createJwt(jti = "revoked-jti")
        val exchange = createAuthenticatedExchange(jwt)

        every { redisTemplate.opsForValue() } returns valueOps
        every { valueOps.get("revoked:revoked-jti") } returns Mono.just("1")

        var chainCalled = false
        val chain = WebFilterChain {
            chainCalled = true
            Mono.empty()
        }

        // The filter should use ReactiveSecurityContextHolder, so we need to provide
        // security context via Reactor context
        val result = filter.filter(exchange, chain)
            .contextWrite(
                org.springframework.security.core.context.ReactiveSecurityContextHolder
                    .withAuthentication(
                        JwtAuthenticationToken(jwt, listOf(SimpleGrantedAuthority("ROLE_USER")))
                    )
            )

        result.block()

        assertThat(chainCalled).isFalse()
        assertThat(exchange.response.statusCode).isEqualTo(HttpStatus.UNAUTHORIZED)
    }

    @Test
    fun `should continue filter chain when Redis throws exception (fail-open)`() {
        val jwt = createJwt(jti = "some-jti")
        val exchange = createAuthenticatedExchange(jwt)

        every { redisTemplate.opsForValue() } returns valueOps
        every { valueOps.get("revoked:some-jti") } returns Mono.error(RuntimeException("Redis connection refused"))

        var chainCalled = false
        val chain = WebFilterChain {
            chainCalled = true
            Mono.empty()
        }

        val result = filter.filter(exchange, chain)
            .contextWrite(
                org.springframework.security.core.context.ReactiveSecurityContextHolder
                    .withAuthentication(
                        JwtAuthenticationToken(jwt, listOf(SimpleGrantedAuthority("ROLE_USER")))
                    )
            )

        result.block()

        assertThat(chainCalled).isTrue()
    }

    @Test
    fun `should return 401 when JWT has no jti claim`() {
        val jwt = createJwt(jti = null)
        val exchange = createAuthenticatedExchange(jwt)

        var chainCalled = false
        val chain = WebFilterChain {
            chainCalled = true
            Mono.empty()
        }

        val result = filter.filter(exchange, chain)
            .contextWrite(
                org.springframework.security.core.context.ReactiveSecurityContextHolder
                    .withAuthentication(
                        JwtAuthenticationToken(jwt, listOf(SimpleGrantedAuthority("ROLE_USER")))
                    )
            )

        result.block()

        assertThat(chainCalled).isFalse()
        assertThat(exchange.response.statusCode).isEqualTo(HttpStatus.UNAUTHORIZED)
    }

    @Test
    fun `should continue filter chain when token is not revoked`() {
        val jwt = createJwt(jti = "valid-jti")
        val exchange = createAuthenticatedExchange(jwt)

        every { redisTemplate.opsForValue() } returns valueOps
        every { valueOps.get("revoked:valid-jti") } returns Mono.empty()

        var chainCalled = false
        val chain = WebFilterChain {
            chainCalled = true
            Mono.empty()
        }

        val result = filter.filter(exchange, chain)
            .contextWrite(
                org.springframework.security.core.context.ReactiveSecurityContextHolder
                    .withAuthentication(
                        JwtAuthenticationToken(jwt, listOf(SimpleGrantedAuthority("ROLE_USER")))
                    )
            )

        result.block()

        assertThat(chainCalled).isTrue()
    }

    @Test
    fun `should skip revocation check for unauthenticated requests`() {
        val request = MockServerHttpRequest.get("/api/auth/register").build()
        val exchange = MockServerWebExchange.from(request)

        var chainCalled = false
        val chain = WebFilterChain {
            chainCalled = true
            Mono.empty()
        }

        // No security context — unauthenticated
        filter.filter(exchange, chain).block()

        assertThat(chainCalled).isTrue()
        // Redis should not be called at all
        verify(exactly = 0) { redisTemplate.opsForValue() }
    }
}
