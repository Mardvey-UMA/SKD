package com.contentplatform.gateway.unit

import com.contentplatform.gateway.config.GatewayProperties
import com.contentplatform.gateway.config.RouteDefinition
import com.contentplatform.gateway.routing.RouteResolver
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test

@Tag("unit")
class RouteResolverTest {

    private val routes = listOf(
        RouteDefinition(
            pathPrefix = "/api/auth",
            target = "http://auth-service:8080",
            public = true,
            stripPrefix = false
        ),
        RouteDefinition(
            pathPrefix = "/api/users",
            target = "http://user-service:8080",
            public = false,
            stripPrefix = false
        ),
        RouteDefinition(
            pathPrefix = "/api/config",
            target = "http://content-aggregation-system:8086",
            public = false,
            stripPrefix = false,
            requiredRoles = listOf("SUBSCRIBER")
        ),
        RouteDefinition(
            pathPrefix = "/webhook/yookassa",
            target = "http://subscription-service:8080",
            public = true,
            stripPrefix = false
        ),
        RouteDefinition(
            pathPrefix = "/api",
            target = "http://fallback-service:8080",
            public = false,
            stripPrefix = false
        )
    )

    private val properties = GatewayProperties(routes = routes)
    private val resolver = RouteResolver(properties)

    @Test
    fun `should resolve exact prefix match`() {
        val result = resolver.resolve("/api/auth/login")

        assertThat(result).isNotNull
        assertThat(result!!.target).isEqualTo("http://auth-service:8080")
    }

    @Test
    fun `should resolve longest prefix when multiple routes match`() {
        // Both /api and /api/users match "/api/users/me" -- longest wins
        val result = resolver.resolve("/api/users/me")

        assertThat(result).isNotNull
        assertThat(result!!.target).isEqualTo("http://user-service:8080")
    }

    @Test
    fun `should return null when no route matches`() {
        val result = resolver.resolve("/unknown/path")

        assertThat(result).isNull()
    }

    @Test
    fun `should preserve public flag from route definition`() {
        val result = resolver.resolve("/api/auth/register")

        assertThat(result).isNotNull
        assertThat(result!!.public).isTrue()
    }

    @Test
    fun `should preserve required roles from route definition`() {
        val result = resolver.resolve("/api/config/sources")

        assertThat(result).isNotNull
        assertThat(result!!.requiredRoles).containsExactly("SUBSCRIBER")
    }

    @Test
    fun `should detect public route`() {
        assertThat(resolver.isPublicRoute("/api/auth/login")).isTrue()
        assertThat(resolver.isPublicRoute("/webhook/yookassa")).isTrue()
    }

    @Test
    fun `should detect non-public route`() {
        assertThat(resolver.isPublicRoute("/api/users/me")).isFalse()
        assertThat(resolver.isPublicRoute("/api/config/sources")).isFalse()
    }

    @Test
    fun `should return false for unknown route in isPublicRoute`() {
        assertThat(resolver.isPublicRoute("/totally/unknown")).isFalse()
    }
}
