package com.contentagg.config.configuration.properties

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.NestedConfigurationProperty

/**
 * External-service integration endpoints used by config-service (Phase 2).
 */
@ConfigurationProperties(prefix = "integration")
data class IntegrationProperties(
    @NestedConfigurationProperty
    var parserService: ParserService = ParserService(),

    @NestedConfigurationProperty
    var feedService: FeedService = FeedService(),

    var internalApiToken: String = "",
) {
    data class ParserService(
        var baseUrl: String = "http://content-parser-service:8082",
        var connectTimeoutMs: Int = 2000,
        var readTimeoutMs: Int = 5000,
    )

    data class FeedService(
        var baseUrl: String = "http://feed-service:8080",
        var connectTimeoutMs: Int = 2000,
        var readTimeoutMs: Int = 3000,
        var maxAttempts: Int = 3,
        var retryBackoffMs: Long = 200L,
    )
}
