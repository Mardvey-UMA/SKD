package com.contentplatform.feed.security

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.security.config.annotation.web.WebSecurityConfigurer
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.builders.WebSecurity
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity
import org.springframework.security.web.SecurityFilterChain
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter
import org.springframework.security.web.context.RequestAttributeSecurityContextRepository

@Configuration
@EnableWebSecurity
class SecurityConfiguration(
    private val gatewaySignatureFilter: GatewaySignatureFilter,
    private val internalSignatureFilter: InternalSignatureFilter
) : WebSecurityConfigurer<WebSecurity> {

    override fun init(builder: WebSecurity) {
        // No web-level ignoring needed — all path access control is in the filter chain
    }

    override fun configure(builder: WebSecurity) {
        // No additional web security configuration needed
    }

    @Bean
    fun securityFilterChain(http: HttpSecurity): SecurityFilterChain {
        http
            .csrf { it.disable() }
            .securityContext { it.securityContextRepository(RequestAttributeSecurityContextRepository()) }
            .authorizeHttpRequests { auth ->
                auth.requestMatchers("/health", "/actuator/**").permitAll()
                auth.anyRequest().authenticated()
            }
            .addFilterBefore(internalSignatureFilter, UsernamePasswordAuthenticationFilter::class.java)
            .addFilterBefore(gatewaySignatureFilter, UsernamePasswordAuthenticationFilter::class.java)
        return http.build()
    }
}
