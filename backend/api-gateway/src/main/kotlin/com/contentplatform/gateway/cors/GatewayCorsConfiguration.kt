package com.contentplatform.gateway.cors

import com.contentplatform.gateway.config.GatewayProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.web.cors.CorsConfiguration
import org.springframework.web.cors.reactive.CorsConfigurationSource
import org.springframework.web.cors.reactive.UrlBasedCorsConfigurationSource

/**
 * Configures CORS for the gateway from GatewayProperties.cors.
 * Exposes a CorsConfigurationSource bean consumed by SecurityConfiguration via the
 * Spring Security .cors {} DSL, which ensures preflight OPTIONS requests are handled
 * inside Spring Security (order -100) before any downstream filters.
 */
@Configuration
class GatewayCorsConfiguration(
    private val properties: GatewayProperties
) {

    fun corsConfiguration(): CorsConfiguration {
        val cors = properties.cors
        return CorsConfiguration().apply {
            if (cors.allowLocalhostWildcard) {
                // Use origin patterns so wildcard works alongside allowCredentials=true.
                // Explicit origins are added as exact patterns; the wildcard covers all localhost ports.
                allowedOriginPatterns = cors.allowedOrigins + listOf("http://localhost:*")
            } else {
                allowedOrigins = cors.allowedOrigins
            }
            allowedMethods = cors.allowedMethods.split(",").map { it.trim() }
            allowedHeaders = cors.allowedHeaders.split(",").map { it.trim() }
            exposedHeaders = cors.exposedHeaders.split(",").map { it.trim() }
            allowCredentials = cors.allowCredentials
            maxAge = cors.maxAge
        }
    }

    @Bean
    fun corsConfigurationSource(): CorsConfigurationSource {
        val source = UrlBasedCorsConfigurationSource()
        source.registerCorsConfiguration("/**", corsConfiguration())
        return source
    }
}
