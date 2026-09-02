package com.contentplatform.feed.infrastructure.health

import org.slf4j.LoggerFactory
import org.springframework.boot.actuate.health.Health
import org.springframework.boot.actuate.health.HealthIndicator
import org.springframework.web.client.RestClient
import org.springframework.web.client.RestClientException

class RecSystemHealthIndicator(
    private val restClient: RestClient
) : HealthIndicator {

    companion object {
        private val log = LoggerFactory.getLogger(RecSystemHealthIndicator::class.java)
    }

    override fun health(): Health {
        return try {
            val response = restClient.get()
                .uri("/health")
                .retrieve()
                .onStatus({ it.isError }) { _, _ -> }
                .toBodilessEntity()
            if (response.statusCode.is2xxSuccessful) {
                Health.up().build()
            } else {
                Health.down()
                    .withDetail("httpStatus", response.statusCode.value())
                    .build()
            }
        } catch (e: RestClientException) {
            log.debug("rec-system health check failed: {}", e.message)
            Health.down(e).build()
        } catch (e: Exception) {
            log.warn("rec-system health check failed unexpectedly: {}", e.message)
            Health.down(e).build()
        }
    }
}
