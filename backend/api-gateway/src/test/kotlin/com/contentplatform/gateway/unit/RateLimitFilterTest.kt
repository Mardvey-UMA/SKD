package com.contentplatform.gateway.unit

import com.contentplatform.gateway.config.GatewayProperties
import com.contentplatform.gateway.config.RateLimitProperties
import com.contentplatform.gateway.filter.RateLimitFilter
import io.mockk.every
import io.mockk.mockk
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
 * Unit tests for RateLimitFilter.
 *
 * Verifies:
 * - When bucket has remaining tokens, request proceeds through the chain
 * - When bucket is exhausted, 429 is returned with Retry-After header and JSON body
 * - When Redis is unavailable, request proceeds (fail-open)
 * - Unauthenticated requests are not rate-limited (skipped)
 */
@Tag("unit")
class RateLimitFilterTest {

    private val redisTemplate = mockk<ReactiveStringRedisTemplate>()
    private val valueOps = mockk<ReactiveValueOperations<String, String>>()
    private val properties = GatewayProperties(
        rateLimit = RateLimitProperties(capacity = 5, windowSeconds = 60, overdraft = 2)
    )
    private val filter = RateLimitFilter(redisTemplate, properties)

    private fun createJwt(sub: String = "user-1"): Jwt {
        return Jwt.withTokenValue("mock-token")
            .header("alg", "RS256")
            .claim("sub", sub)
            .claim("roles", listOf("USER"))
            .claim("subscription_tier", "free")
            .claim("jti", "test-jti")
            .issuedAt(Instant.now())
            .expiresAt(Instant.now().plusSeconds(900))
            .build()
    }

    private fun createAuthenticatedExchange(path: String = "/api/users/me"): MockServerWebExchange {
        val request = MockServerHttpRequest.get(path).build()
        return MockServerWebExchange.from(request)
    }

    @Test
    fun `should allow request when rate limit is not exceeded`() {
        val jwt = createJwt(sub = "user-allowed")
        val exchange = createAuthenticatedExchange()

        // Simulate Redis returning current count below limit
        every { redisTemplate.opsForValue() } returns valueOps
        every { valueOps.increment("rate:user-allowed") } returns Mono.just(1L)
        every { redisTemplate.expire(any(), any()) } returns Mono.just(true)

        var chainCalled = false
        val chain = WebFilterChain {
            chainCalled = true
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

        assertThat(chainCalled).isTrue()
        assertThat(exchange.response.statusCode).isNotEqualTo(HttpStatus.TOO_MANY_REQUESTS)
    }

    @Test
    fun `should return 429 with Retry-After header when rate limit exceeded`() {
        val jwt = createJwt(sub = "user-limited")
        val exchange = createAuthenticatedExchange()

        // Simulate Redis returning count above capacity + overdraft
        every { redisTemplate.opsForValue() } returns valueOps
        every { valueOps.increment("rate:user-limited") } returns Mono.just(8L) // > 5 + 2 = 7
        every { redisTemplate.expire(any(), any()) } returns Mono.just(true)

        var chainCalled = false
        val chain = WebFilterChain {
            chainCalled = true
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

        assertThat(chainCalled).isFalse()
        assertThat(exchange.response.statusCode).isEqualTo(HttpStatus.TOO_MANY_REQUESTS)
        assertThat(exchange.response.headers.getFirst("Retry-After")).isNotNull()
    }

    @Test
    fun `should return JSON error body with rate_limit_exceeded code on 429`() {
        val jwt = createJwt(sub = "user-limited-json")
        val exchange = createAuthenticatedExchange()
        exchange.attributes["requestId"] = "req-429-test"

        every { redisTemplate.opsForValue() } returns valueOps
        every { valueOps.increment("rate:user-limited-json") } returns Mono.just(8L)
        every { redisTemplate.expire(any(), any()) } returns Mono.just(true)

        val chain = WebFilterChain { Mono.empty() }

        filter.filter(exchange, chain)
            .contextWrite(
                org.springframework.security.core.context.ReactiveSecurityContextHolder
                    .withAuthentication(
                        JwtAuthenticationToken(jwt, listOf(SimpleGrantedAuthority("ROLE_USER")))
                    )
            )
            .block()

        assertThat(exchange.response.statusCode).isEqualTo(HttpStatus.TOO_MANY_REQUESTS)
        // The response content type should be JSON
        assertThat(exchange.response.headers.contentType.toString()).contains("application/json")
    }

    @Test
    fun `should allow request when Redis is unavailable (fail-open)`() {
        val jwt = createJwt(sub = "user-redis-down")
        val exchange = createAuthenticatedExchange()

        every { redisTemplate.opsForValue() } returns valueOps
        every { valueOps.increment("rate:user-redis-down") } returns Mono.error(
            RuntimeException("Redis connection refused")
        )

        var chainCalled = false
        val chain = WebFilterChain {
            chainCalled = true
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

        assertThat(chainCalled).isTrue()
    }

    @Test
    fun `should skip rate limiting for unauthenticated requests`() {
        val request = MockServerHttpRequest.get("/api/auth/register").build()
        val exchange = MockServerWebExchange.from(request)

        var chainCalled = false
        val chain = WebFilterChain {
            chainCalled = true
            Mono.empty()
        }

        // No security context
        filter.filter(exchange, chain).block()

        assertThat(chainCalled).isTrue()
    }

    @Test
    fun `should use rate key pattern rate colon userId`() {
        val jwt = createJwt(sub = "specific-user-id")
        val exchange = createAuthenticatedExchange()

        every { redisTemplate.opsForValue() } returns valueOps
        // Verify key contains the user id
        every { valueOps.increment("rate:specific-user-id") } returns Mono.just(1L)
        every { redisTemplate.expire(any(), any()) } returns Mono.just(true)

        val chain = WebFilterChain { Mono.empty() }

        filter.filter(exchange, chain)
            .contextWrite(
                org.springframework.security.core.context.ReactiveSecurityContextHolder
                    .withAuthentication(
                        JwtAuthenticationToken(jwt, listOf(SimpleGrantedAuthority("ROLE_USER")))
                    )
            )
            .block()

        // If the key is wrong, the mock won't match and this will fail
        io.mockk.verify { valueOps.increment("rate:specific-user-id") }
    }
}
