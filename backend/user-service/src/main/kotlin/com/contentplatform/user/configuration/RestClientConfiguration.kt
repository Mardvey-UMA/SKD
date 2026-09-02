package com.contentplatform.user.configuration

import com.contentplatform.user.infrastructure.client.RecSystemClient
import com.contentplatform.user.infrastructure.client.RecSystemClientPort
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Lazy
import org.springframework.http.client.SimpleClientHttpRequestFactory
import org.springframework.web.client.RestClient

@Configuration
class RestClientConfiguration {

    @Bean
    @Lazy
    fun recSystemClient(
        @Value("\${services.rec-system.url:http://localhost:8000}") url: String,
        @Value("\${services.rec-system.timeout-ms:5000}") timeoutMs: Long,
        @Value("\${services.rec-system.retry-max-attempts:6}") retryMaxAttempts: Int,
        @Value("\${services.rec-system.retry-delay-ms:2000}") retryDelayMs: Long
    ): RecSystemClientPort {
        val factory = SimpleClientHttpRequestFactory().apply {
            setConnectTimeout(timeoutMs.toInt())
            setReadTimeout(timeoutMs.toInt())
        }

        val restClient = RestClient.builder()
            .baseUrl(url)
            .requestFactory(factory)
            .build()

        val properties = RecSystemProperties(
            url = url,
            timeoutMs = timeoutMs,
            retryMaxAttempts = retryMaxAttempts,
            retryDelayMs = retryDelayMs
        )

        return RecSystemClient(restClient, properties)
    }
}
