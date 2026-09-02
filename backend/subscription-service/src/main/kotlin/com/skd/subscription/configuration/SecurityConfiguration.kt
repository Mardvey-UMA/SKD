package com.skd.subscription.configuration

import com.skd.subscription.presentation.filter.GatewaySignatureFilter
import com.skd.subscription.presentation.filter.YookassaIpWhitelistFilter
import jakarta.servlet.http.HttpServletResponse
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.core.annotation.Order
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity
import org.springframework.security.config.http.SessionCreationPolicy
import org.springframework.security.web.SecurityFilterChain
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter

@Configuration
@EnableWebSecurity
class SecurityConfiguration(
    private val gatewaySignatureFilter: GatewaySignatureFilter,
    private val yookassaIpWhitelistFilter: YookassaIpWhitelistFilter
) {

    @Bean
    @Order(1)
    fun actuatorSecurityFilterChain(http: HttpSecurity): SecurityFilterChain {
        http
            .securityMatcher("/actuator", "/actuator/**")
            .csrf { it.disable() }
            .authorizeHttpRequests { auth ->
                auth.anyRequest().permitAll()
            }
        return http.build()
    }

    @Bean
    @Order(2)
    fun securityFilterChain(http: HttpSecurity): SecurityFilterChain {
        http
            .csrf { it.disable() }
            .sessionManagement { it.sessionCreationPolicy(SessionCreationPolicy.STATELESS) }
            .authorizeHttpRequests { auth ->
                auth
                    .requestMatchers("/health", "/actuator", "/actuator/**", "/error").permitAll()
                    .requestMatchers("/webhook/yookassa").permitAll()
                    .anyRequest().authenticated()
            }
            .exceptionHandling { ex ->
                ex.authenticationEntryPoint { _, response, _ ->
                    response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Unauthorized")
                }
            }
            .addFilterBefore(yookassaIpWhitelistFilter, UsernamePasswordAuthenticationFilter::class.java)
            .addFilterBefore(gatewaySignatureFilter, UsernamePasswordAuthenticationFilter::class.java)
        return http.build()
    }
}
