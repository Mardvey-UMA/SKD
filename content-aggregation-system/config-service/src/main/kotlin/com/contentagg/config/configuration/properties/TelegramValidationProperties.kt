package com.contentagg.config.configuration.properties

import org.springframework.boot.context.properties.ConfigurationProperties

/**
 * Telegram-channel validation feature flag (Phase 2).
 * When disabled, CreateTelegramSourceProcessor treats every channel as accessible.
 */
@ConfigurationProperties(prefix = "telegram.channel-validation")
data class TelegramValidationProperties(
    var enabled: Boolean = true,
    var maxAttempts: Int = 3,
    var retryBackoffMs: Long = 200L,
)
