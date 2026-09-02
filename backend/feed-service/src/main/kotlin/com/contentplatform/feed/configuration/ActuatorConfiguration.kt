package com.contentplatform.feed.configuration

import com.contentplatform.feed.infrastructure.health.FeedSchemaHealthIndicator
import com.contentplatform.feed.infrastructure.health.RecSystemHealthIndicator
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.client.SimpleClientHttpRequestFactory
import org.springframework.web.client.RestClient
import java.time.Duration
import javax.sql.DataSource

@Configuration
class ActuatorConfiguration {

    @Bean
    fun recSystemHealthIndicator(
        @Value("\${services.rec-system.url:http://localhost:8000}") recSystemUrl: String
    ): RecSystemHealthIndicator {
        val factory = SimpleClientHttpRequestFactory().apply {
            setConnectTimeout(Duration.ofSeconds(2))
            setReadTimeout(Duration.ofSeconds(2))
        }
        val restClient = RestClient.builder()
            .baseUrl(recSystemUrl)
            .requestFactory(factory)
            .build()
        return RecSystemHealthIndicator(restClient)
    }

    @Bean
    fun feedSchemaHealthIndicator(dataSource: DataSource): FeedSchemaHealthIndicator =
        FeedSchemaHealthIndicator(dataSource)
}
