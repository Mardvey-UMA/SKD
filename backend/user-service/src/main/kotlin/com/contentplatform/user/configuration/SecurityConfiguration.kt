package com.contentplatform.user.configuration

import com.contentplatform.user.infrastructure.filter.GatewaySignatureFilter
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity
import org.springframework.security.config.http.SessionCreationPolicy
import org.springframework.security.web.SecurityFilterChain
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer

@Configuration
@EnableWebSecurity
@EnableConfigurationProperties(GatewayProperties::class)
class SecurityConfiguration(
    private val gatewayProperties: GatewayProperties
) : WebMvcConfigurer {

    @Bean
    fun gatewaySignatureFilter(): GatewaySignatureFilter {
        return GatewaySignatureFilter(gatewayProperties.hmacSecret)
    }

    @Bean
    fun securityFilterChain(http: HttpSecurity): SecurityFilterChain {
        http
            .csrf { it.disable() }
            .sessionManagement { it.sessionCreationPolicy(SessionCreationPolicy.STATELESS) }
            .authorizeHttpRequests { auth ->
                auth
                    .requestMatchers("/health", "/error").permitAll()
                    .anyRequest().permitAll()
            }
            .addFilterBefore(gatewaySignatureFilter(), UsernamePasswordAuthenticationFilter::class.java)
        return http.build()
    }
}
