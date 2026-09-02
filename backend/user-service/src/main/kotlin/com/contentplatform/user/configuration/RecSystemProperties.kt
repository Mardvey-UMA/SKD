package com.contentplatform.user.configuration

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "services.rec-system")
data class RecSystemProperties(
    val url: String = "http://localhost:8000",
    val timeoutMs: Long = 5000,
    val retryMaxAttempts: Int = 6,
    val retryDelayMs: Long = 2000,
)
