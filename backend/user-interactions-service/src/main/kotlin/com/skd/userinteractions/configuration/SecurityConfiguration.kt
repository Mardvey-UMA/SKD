package com.skd.userinteractions.configuration

import org.springframework.boot.web.servlet.FilterRegistrationBean
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity
import org.springframework.security.config.http.SessionCreationPolicy
import org.springframework.security.web.SecurityFilterChain
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter

@Configuration
@EnableWebSecurity
class SecurityConfiguration(
    private val gatewaySignatureFilter: GatewaySignatureFilter
) {

    @Bean
    fun gatewaySignatureFilterRegistration(): FilterRegistrationBean<GatewaySignatureFilter> =
        FilterRegistrationBean(gatewaySignatureFilter).apply { isEnabled = false }

    @Bean
    fun securityFilterChain(http: HttpSecurity): SecurityFilterChain {
        http
            .csrf { it.disable() }
            .sessionManagement { it.sessionCreationPolicy(SessionCreationPolicy.STATELESS) }
            .authorizeHttpRequests { auth ->
                auth
                    .requestMatchers("/health", "/error", "/actuator", "/actuator/**").permitAll()
                    .anyRequest().authenticated()
            }
            .addFilterBefore(gatewaySignatureFilter, UsernamePasswordAuthenticationFilter::class.java)
        return http.build()
    }
}
