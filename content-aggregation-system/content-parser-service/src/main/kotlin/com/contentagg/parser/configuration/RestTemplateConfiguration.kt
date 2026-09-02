package com.contentagg.parser.configuration

import com.contentagg.parser.configuration.properties.ConfigServiceProperties
import org.apache.hc.client5.http.config.RequestConfig
import org.apache.hc.client5.http.impl.classic.HttpClients
import org.apache.hc.core5.util.Timeout
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.client.BufferingClientHttpRequestFactory
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory
import org.springframework.web.client.RestTemplate
import java.util.concurrent.TimeUnit

/**
 * Centralized HTTP client configuration.
 * Each external system gets its own RestTemplate bean with specific timeouts.
 */
@Configuration
class RestTemplateConfiguration(
    private val configServiceProperties: ConfigServiceProperties,
) {

    /**
     * Generic RestTemplate for Habr API client.
     * No custom timeouts — Resilience4j handles rate limiting and circuit breaking.
     */
    @Bean("habrRestTemplate")
    fun habrRestTemplate(): RestTemplate = RestTemplate()

    /**
     * Generic RestTemplate for VC.RU API client.
     * No custom timeouts -- Resilience4j handles rate limiting and circuit breaking.
     */
    @Bean("vcruRestTemplate")
    fun vcruRestTemplate(): RestTemplate = RestTemplate()

    /**
     * RestTemplate for config-service with explicit connection and read timeouts.
     * Wrapped in BufferingClientHttpRequestFactory for proper response logging.
     */
    @Bean("configServiceRestTemplate")
    fun configServiceRestTemplate(): RestTemplate {
        val requestConfig = RequestConfig.custom()
            .setConnectionRequestTimeout(Timeout.of(configServiceProperties.connectionTimeout, TimeUnit.MILLISECONDS))
            .setResponseTimeout(Timeout.of(configServiceProperties.readTimeout, TimeUnit.MILLISECONDS))
            .build()

        val httpClient = HttpClients.custom()
            .setDefaultRequestConfig(requestConfig)
            .build()

        val factory = HttpComponentsClientHttpRequestFactory(httpClient)
        val bufferingFactory = BufferingClientHttpRequestFactory(factory)

        return RestTemplate(bufferingFactory)
    }
}
