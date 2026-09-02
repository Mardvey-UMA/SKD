package com.contentplatform.gateway.config

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "gateway")
data class GatewayProperties(
    val hmacSecret: String = "",
    val defaultTimeoutMs: Long = 5000,
    val cors: CorsProperties = CorsProperties(),
    val routes: List<RouteDefinition> = emptyList(),
    val rateLimit: RateLimitProperties = RateLimitProperties()
)

data class RouteDefinition(
    val pathPrefix: String,
    val target: String,
    val public: Boolean = false,
    val stripPrefix: Boolean = false,
    val requiredRoles: List<String> = emptyList()
)

data class CorsProperties(
    val allowedOrigins: List<String> = emptyList(),
    val allowedMethods: String = "GET,POST,PUT,DELETE,OPTIONS",
    val allowedHeaders: String = "Authorization,Content-Type,X-Request-Id",
    val exposedHeaders: String = "X-Request-Id,Retry-After",
    val allowCredentials: Boolean = true,
    val maxAge: Long = 3600,
    val allowLocalhostWildcard: Boolean = false
)

data class RateLimitProperties(
    val capacity: Long = 100,
    val windowSeconds: Long = 60,
    val overdraft: Long = 20
)
