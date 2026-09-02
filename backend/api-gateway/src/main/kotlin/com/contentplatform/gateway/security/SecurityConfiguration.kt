package com.contentplatform.gateway.security

import com.contentplatform.gateway.config.GatewayProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity
import org.springframework.security.config.web.server.ServerHttpSecurity
import org.springframework.security.web.server.SecurityWebFilterChain
import org.springframework.web.cors.reactive.CorsConfigurationSource

@Configuration
@EnableWebFluxSecurity
class SecurityConfiguration(
    private val gatewayProperties: GatewayProperties,
    private val corsConfigurationSource: CorsConfigurationSource
) {

    @Bean
    fun springSecurityFilterChain(http: ServerHttpSecurity): SecurityWebFilterChain {
        val publicPrefixes = gatewayProperties.routes
            .filter { it.public }
            .map { it.pathPrefix + "/**" }
            .plus("/health")
            .plus("/actuator/**")
            .toTypedArray()

        http
            .csrf { it.disable() }
            .cors { it.configurationSource(corsConfigurationSource) }
            .authorizeExchange { exchanges ->
                exchanges
                    .pathMatchers(*publicPrefixes).permitAll()
                    .anyExchange().authenticated()
            }
            .oauth2ResourceServer { oauth2 ->
                oauth2.jwt { }
            }

        return http.build()
    }
}
