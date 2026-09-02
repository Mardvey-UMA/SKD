package com.contentplatform.gateway.unit

import com.contentplatform.gateway.config.CorsProperties
import com.contentplatform.gateway.config.GatewayProperties
import com.contentplatform.gateway.cors.GatewayCorsConfiguration
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.springframework.web.cors.CorsConfiguration

/**
 * Unit tests for CORS configuration.
 *
 * Verifies:
 * - Allowed origins include configured values (localhost:3000, localhost:5173)
 * - Allow credentials is true
 * - Allowed methods include GET, POST, PUT, DELETE, OPTIONS
 * - Exposed headers include X-Request-Id, Retry-After
 * - Max age is configured
 * - No wildcard origin when credentials are allowed
 */
@Tag("unit")
class CorsConfigurationTest {

    private val corsProperties = CorsProperties(
        allowedOrigins = listOf("http://localhost:3000", "http://localhost:5173"),
        allowedMethods = "GET,POST,PUT,DELETE,OPTIONS",
        allowedHeaders = "Authorization,Content-Type,X-Request-Id",
        exposedHeaders = "X-Request-Id,Retry-After",
        allowCredentials = true,
        maxAge = 3600
    )
    private val properties = GatewayProperties(cors = corsProperties)

    @Test
    fun `should include all configured allowed origins`() {
        val corsConfig = GatewayCorsConfiguration(properties)
        val config = corsConfig.corsConfiguration()

        assertThat(config.allowedOrigins).containsExactlyInAnyOrder(
            "http://localhost:3000",
            "http://localhost:5173"
        )
    }

    @Test
    fun `should allow credentials`() {
        val corsConfig = GatewayCorsConfiguration(properties)
        val config = corsConfig.corsConfiguration()

        assertThat(config.allowCredentials).isTrue()
    }

    @Test
    fun `should include all required HTTP methods`() {
        val corsConfig = GatewayCorsConfiguration(properties)
        val config = corsConfig.corsConfiguration()

        assertThat(config.allowedMethods).containsAll(
            listOf("GET", "POST", "PUT", "DELETE", "OPTIONS")
        )
    }

    @Test
    fun `should expose X-Request-Id and Retry-After headers`() {
        val corsConfig = GatewayCorsConfiguration(properties)
        val config = corsConfig.corsConfiguration()

        assertThat(config.exposedHeaders).containsAll(
            listOf("X-Request-Id", "Retry-After")
        )
    }

    @Test
    fun `should set max age from configuration`() {
        val corsConfig = GatewayCorsConfiguration(properties)
        val config = corsConfig.corsConfiguration()

        assertThat(config.maxAge).isEqualTo(3600)
    }

    @Test
    fun `should not use wildcard origin when credentials are allowed`() {
        val corsConfig = GatewayCorsConfiguration(properties)
        val config = corsConfig.corsConfiguration()

        // When allowCredentials=true, origins must not contain "*"
        assertThat(config.allowedOrigins).doesNotContain("*")
    }

    @Test
    fun `should include Authorization in allowed headers`() {
        val corsConfig = GatewayCorsConfiguration(properties)
        val config = corsConfig.corsConfiguration()

        assertThat(config.allowedHeaders).contains("Authorization")
    }

    // ===== Wildcard localhost tests (BUG-007) =====

    @Test
    fun `should not add wildcard pattern when allow-localhost-wildcard is false`() {
        val corsConfig = GatewayCorsConfiguration(properties)
        val config = corsConfig.corsConfiguration()

        assertThat(config.allowedOriginPatterns).isNullOrEmpty()
    }

    @Test
    fun `should add localhost wildcard pattern when allow-localhost-wildcard is true`() {
        val wildcardProps = GatewayProperties(cors = CorsProperties(
            allowedOrigins = listOf("http://localhost:3000", "http://localhost:5173"),
            allowLocalhostWildcard = true,
            allowedMethods = "GET,POST,PUT,DELETE,OPTIONS",
            allowedHeaders = "Authorization,Content-Type,X-Request-Id",
            exposedHeaders = "X-Request-Id,Retry-After",
            allowCredentials = true,
            maxAge = 3600
        ))
        val corsConfig = GatewayCorsConfiguration(wildcardProps)
        val config = corsConfig.corsConfiguration()

        assertThat(config.allowedOriginPatterns).contains("http://localhost:*")
    }

    @Test
    fun `should allow localhost 8100 via checkOrigin when wildcard flag is true`() {
        val wildcardProps = GatewayProperties(cors = CorsProperties(
            allowedOrigins = listOf("http://localhost:3000"),
            allowLocalhostWildcard = true,
            allowedMethods = "GET,POST,PUT,DELETE,OPTIONS",
            allowedHeaders = "Authorization,Content-Type,X-Request-Id",
            exposedHeaders = "X-Request-Id,Retry-After",
            allowCredentials = true,
            maxAge = 3600
        ))
        val corsConfig = GatewayCorsConfiguration(wildcardProps)
        val config = corsConfig.corsConfiguration()

        assertThat(config.checkOrigin("http://localhost:8100")).isEqualTo("http://localhost:8100")
    }

    @Test
    fun `should not allow localhost 8100 when wildcard flag is false`() {
        val corsConfig = GatewayCorsConfiguration(properties)
        val config = corsConfig.corsConfiguration()

        assertThat(config.checkOrigin("http://localhost:8100")).isNull()
    }

    @Test
    fun `should still allow explicit origins when wildcard flag is true`() {
        val wildcardProps = GatewayProperties(cors = CorsProperties(
            allowedOrigins = listOf("http://localhost:3000", "http://localhost:5173"),
            allowLocalhostWildcard = true,
            allowedMethods = "GET,POST,PUT,DELETE,OPTIONS",
            allowedHeaders = "Authorization,Content-Type,X-Request-Id",
            exposedHeaders = "X-Request-Id,Retry-After",
            allowCredentials = true,
            maxAge = 3600
        ))
        val corsConfig = GatewayCorsConfiguration(wildcardProps)
        val config = corsConfig.corsConfiguration()

        assertThat(config.checkOrigin("http://localhost:3000")).isEqualTo("http://localhost:3000")
        assertThat(config.checkOrigin("http://localhost:5173")).isEqualTo("http://localhost:5173")
    }
}
