package com.contentplatform.feed.configuration

import com.contentplatform.feed.infrastructure.client.ContentAggregatorClient
import com.contentplatform.feed.infrastructure.client.RecSystemClient
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.web.client.RestClient
import java.time.Duration

@Configuration
class HttpClientConfiguration {

    @Bean
    fun httpClientObjectMapper(): ObjectMapper = jacksonObjectMapper().apply {
        registerModule(JavaTimeModule())
        configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
        configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false)
    }

    @Bean
    fun recSystemClient(
        @Value("\${services.rec-system.url:http://localhost:8000}") recSystemUrl: String,
        httpClientObjectMapper: ObjectMapper
    ): RecSystemClient {
        val restClient = RestClient.builder()
            .baseUrl(recSystemUrl)
            .build()
        return RecSystemClient(restClient, httpClientObjectMapper)
    }

    @Bean
    fun contentAggregatorClient(
        @Value("\${services.content-aggregation.url:http://localhost:8086}") contentAggUrl: String,
        httpClientObjectMapper: ObjectMapper
    ): ContentAggregatorClient {
        val restClient = RestClient.builder()
            .baseUrl(contentAggUrl)
            .build()
        return ContentAggregatorClient(restClient, httpClientObjectMapper)
    }
}
