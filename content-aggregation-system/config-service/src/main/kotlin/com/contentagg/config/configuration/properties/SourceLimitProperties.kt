package com.contentagg.config.configuration.properties

import org.springframework.boot.context.properties.ConfigurationProperties

/**
 * Source addition limit configuration (Phase 2).
 */
@ConfigurationProperties(prefix = "source-limit")
data class SourceLimitProperties(
    var enabled: Boolean = true,
    var perUser: Int = 20,
)
