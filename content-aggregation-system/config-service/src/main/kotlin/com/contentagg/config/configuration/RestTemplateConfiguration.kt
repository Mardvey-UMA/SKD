package com.contentagg.config.configuration

import com.contentagg.config.configuration.properties.IntegrationProperties
import org.slf4j.LoggerFactory
import org.springframework.boot.web.client.RestTemplateBuilder
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.web.client.RestTemplate
import java.time.Duration

/**
 * Centralized RestTemplate beans for outbound integrations (Phase 2).
 */
@Configuration
class RestTemplateConfiguration {

    companion object {
        private val log = LoggerFactory.getLogger(RestTemplateConfiguration::class.java)
    }

    @Bean
    fun parserServiceRestTemplate(
        builder: RestTemplateBuilder,
        integrationProperties: IntegrationProperties,
    ): RestTemplate {
        log.info(
            "Building parserServiceRestTemplate connectTimeout={}ms readTimeout={}ms",
            integrationProperties.parserService.connectTimeoutMs,
            integrationProperties.parserService.readTimeoutMs,
        )
        return builder
            .connectTimeout(Duration.ofMillis(integrationProperties.parserService.connectTimeoutMs.toLong()))
            .readTimeout(Duration.ofMillis(integrationProperties.parserService.readTimeoutMs.toLong()))
            .build()
    }

    @Bean
    fun feedServiceRestTemplate(
        builder: RestTemplateBuilder,
        integrationProperties: IntegrationProperties,
    ): RestTemplate {
        log.info(
            "Building feedServiceRestTemplate connectTimeout={}ms readTimeout={}ms",
            integrationProperties.feedService.connectTimeoutMs,
            integrationProperties.feedService.readTimeoutMs,
        )
        return builder
            .connectTimeout(Duration.ofMillis(integrationProperties.feedService.connectTimeoutMs.toLong()))
            .readTimeout(Duration.ofMillis(integrationProperties.feedService.readTimeoutMs.toLong()))
            .build()
    }
}
