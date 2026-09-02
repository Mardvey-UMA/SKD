package com.contentplatform.gateway.unit

import com.contentplatform.gateway.config.GatewayProperties
import com.contentplatform.gateway.config.RouteDefinition
import com.contentplatform.gateway.filter.SubscriptionGateFilter
import com.contentplatform.gateway.routing.RouteResolver
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus
import org.springframework.mock.http.server.reactive.MockServerHttpRequest
import org.springframework.mock.web.server.MockServerWebExchange
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken
import org.springframework.web.server.WebFilterChain
import reactor.core.publisher.Mono
import java.time.Instant

/**
 * Unit tests for SubscriptionGateFilter.
 *
 * Verifies:
 * - Free user accessing route with requiredRoles=SUBSCRIBER gets 403 with subscription_required error
 * - Premium user accessing route with requiredRoles=SUBSCRIBER passes through
 * - Route without requiredRoles passes through regardless of tier
 * - Unauthenticated requests pass through (security handles auth separately)
 */
@Tag("unit")
class SubscriptionGateFilterTest {

    private val properties = GatewayProperties(
        routes = listOf(
            RouteDefinition(
                pathPrefix = "/api/config",
                target = "http://content-aggregation-system:8086",
                public = false,
                stripPrefix = false,
                requiredRoles = listOf("SUBSCRIBER")
            ),
            RouteDefinition(
                pathPrefix = "/api/users",
                target = "http://user-service:8080",
                public = false,
                stripPrefix = false,
                requiredRoles = emptyList()
            )
        )
    )
    private val routeResolver = RouteResolver(properties)
    private val filter = SubscriptionGateFilter(routeResolver)

    private fun createJwt(
        sub: String = "user-1",
        roles: List<String> = listOf("USER"),
        subscriptionTier: String = "free"
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

    @Test
    fun `should return 403 when free user accesses route requiring SUBSCRIBER role`() {
        val jwt = createJwt(subscriptionTier = "free")
        val request = MockServerHttpRequest.get("/api/config/sources").build()
        val exchange = MockServerWebExchange.from(request)
        exchange.attributes["requestId"] = "req-403-test"

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
        assertThat(exchange.response.statusCode).isEqualTo(HttpStatus.FORBIDDEN)
    }

    @Test
    fun `should return JSON error body with subscription_required code on 403`() {
        val jwt = createJwt(subscriptionTier = "free")
        val request = MockServerHttpRequest.get("/api/config/sources").build()
        val exchange = MockServerWebExchange.from(request)
        exchange.attributes["requestId"] = "req-sub-json"

        val chain = WebFilterChain { Mono.empty() }

        filter.filter(exchange, chain)
            .contextWrite(
                org.springframework.security.core.context.ReactiveSecurityContextHolder
                    .withAuthentication(
                        JwtAuthenticationToken(jwt, listOf(SimpleGrantedAuthority("ROLE_USER")))
                    )
            )
            .block()

        assertThat(exchange.response.statusCode).isEqualTo(HttpStatus.FORBIDDEN)
        assertThat(exchange.response.headers.contentType.toString()).contains("application/json")
    }

    @Test
    fun `should allow premium user to access route requiring SUBSCRIBER role`() {
        val jwt = createJwt(subscriptionTier = "premium")
        val request = MockServerHttpRequest.get("/api/config/sources").build()
        val exchange = MockServerWebExchange.from(request)

        var chainCalled = false
        val chain = WebFilterChain {
            chainCalled = true
            Mono.empty()
        }

        filter.filter(exchange, chain)
            .contextWrite(
                org.springframework.security.core.context.ReactiveSecurityContextHolder
                    .withAuthentication(
                        JwtAuthenticationToken(jwt, listOf(SimpleGrantedAuthority("ROLE_USER"), SimpleGrantedAuthority("ROLE_SUBSCRIBER")))
                    )
            )
            .block()

        assertThat(chainCalled).isTrue()
    }

    @Test
    fun `should pass through when route has no required roles`() {
        val jwt = createJwt(subscriptionTier = "free")
        val request = MockServerHttpRequest.get("/api/users/me").build()
        val exchange = MockServerWebExchange.from(request)

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
    fun `should pass through for unauthenticated requests`() {
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
    fun `should pass through when route is not found in route resolver`() {
        val jwt = createJwt(subscriptionTier = "free")
        val request = MockServerHttpRequest.get("/unknown/path").build()
        val exchange = MockServerWebExchange.from(request)

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
}
