package com.contentagg.config.integration.rest.feed

import com.contentagg.config.configuration.properties.IntegrationProperties
import com.fasterxml.jackson.annotation.JsonProperty
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClientException
import org.springframework.web.client.RestTemplate
import org.springframework.web.util.UriComponentsBuilder

/**
 * Client for `GET /internal/source-additions/count?user_id=...` (feed-service, Phase 3).
 *
 * Fail-mode per design: after `maxAttempts` with backoff — returns null (fail-open);
 * caller treats null as "limit check not available — allow creation, hard cap handled by feed-service consumer".
 */
@Component
class SourceAdditionsCountClient(
    @Qualifier("feedServiceRestTemplate") private val restTemplate: RestTemplate,
    private val integrationProperties: IntegrationProperties,
) {

    companion object {
        private val log = LoggerFactory.getLogger(SourceAdditionsCountClient::class.java)
        private const val INTERNAL_TOKEN_HEADER = "X-Internal-Token"
        private const val COUNT_PATH = "/internal/source-additions/count"
    }

    data class CountResponse(
        @JsonProperty("user_id") val userId: String? = null,
        val count: Int = 0,
        val limit: Int = 20,
        val remaining: Int = 20,
    )

    /**
     * @return CountResponse on success; null when feed-service is unavailable after retries.
     */
    fun tryGetCount(userId: String): CountResponse? {
        val url = UriComponentsBuilder
            .fromUriString(integrationProperties.feedService.baseUrl + COUNT_PATH)
            .queryParam("user_id", userId)
            .toUriString()
        val headers = HttpHeaders().apply {
            if (integrationProperties.internalApiToken.isNotBlank()) {
                set(INTERNAL_TOKEN_HEADER, integrationProperties.internalApiToken)
            }
        }
        val entity = HttpEntity<Any>(headers)

        var attempt = 0
        val maxAttempts = integrationProperties.feedService.maxAttempts.coerceAtLeast(1)
        while (attempt < maxAttempts) {
            attempt++
            try {
                log.debug("GET {} attempt={}", url, attempt)
                val response = restTemplate.exchange(url, HttpMethod.GET, entity, CountResponse::class.java)
                return response.body
            } catch (ex: RestClientException) {
                log.warn("feed-service count attempt {}/{} failed: {}", attempt, maxAttempts, ex.message)
                if (attempt < maxAttempts) {
                    try {
                        Thread.sleep(integrationProperties.feedService.retryBackoffMs)
                    } catch (ie: InterruptedException) {
                        Thread.currentThread().interrupt()
                        return null
                    }
                }
            }
        }
        log.warn("feed-service count check failed after {} attempts — failing open", maxAttempts)
        return null
    }
}
