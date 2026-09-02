package com.contentagg.parser.integration.rest.vcru

import com.contentagg.parser.integration.rest.vcru.model.VcruSubsiteDto
import com.contentagg.parser.integration.rest.vcru.model.VcruSubsiteResponseDto
import com.contentagg.parser.integration.rest.vcru.model.VcruTimelineDto
import com.contentagg.parser.integration.rest.vcru.model.VcruTimelineResponseDto
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker
import io.github.resilience4j.ratelimiter.annotation.RateLimiter
import io.github.resilience4j.retry.annotation.Retry
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Component
import org.springframework.web.client.HttpClientErrorException
import org.springframework.web.client.RestClientException
import org.springframework.web.client.RestTemplate
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

/**
 * HTTP client for VC.RU API.
 * Implements rate limiting, retries, and circuit breaker patterns.
 *
 * API Documentation: See /mydocs/VC_API_ANALYSIS.md
 *
 * Endpoints:
 * - GET /v2.7/subsite - Get user/company info by URI
 * - GET /v2.8/timeline - Get articles with full content (cursor-based pagination)
 */
@Component
class VcruApiClient(
    @Qualifier("vcruRestTemplate") private val restTemplate: RestTemplate,
    @Value("\${parser.vcru.base-url:https://api.vc.ru}") private val baseUrl: String
) {
    companion object {
        private val log = LoggerFactory.getLogger(VcruApiClient::class.java)
    }

    /**
     * Get subsite info by alias (username or company URI).
     * GET /v2.7/subsite?uri={alias}&markdown=false
     *
     * @param alias User/company URI alias
     * @return Subsite DTO, or null if not found
     */
    @RateLimiter(name = "vcru", fallbackMethod = "subsiteRateLimitFallback")
    @Retry(name = "vcru")
    @CircuitBreaker(name = "vcru")
    fun getSubsiteInfo(alias: String): VcruSubsiteDto? {
        val encodedAlias = URLEncoder.encode(alias, StandardCharsets.UTF_8)
        val url = "$baseUrl/v2.7/subsite?uri=$encodedAlias&markdown=false"

        log.debug("Fetching subsite info from VC.RU API: alias={}", alias)

        return try {
            val response = restTemplate.exchange(
                url,
                HttpMethod.GET,
                createRequestEntity(),
                VcruSubsiteResponseDto::class.java
            )

            if (response.statusCode == HttpStatus.OK && response.body != null) {
                val result = response.body!!.result
                log.debug("Successfully fetched subsite info: alias={}, id={}", alias, result?.id)
                result
            } else {
                log.warn("Failed to fetch subsite info: alias={}, status={}", alias, response.statusCode)
                null
            }
        } catch (e: HttpClientErrorException.NotFound) {
            log.warn("VC.RU subsite not found (removed or renamed): alias={}", alias)
            null
        } catch (e: RestClientException) {
            log.error("Error fetching subsite info for alias={}: {}", alias, e.message)
            throw e
        }
    }

    /**
     * Get timeline articles for a subsite.
     * GET /v2.8/timeline?subsitesIds={id}&sorting={sort}&markdown=false[&lastId={lastId}]
     *
     * @param subsiteId Subsite ID from getSubsiteInfo
     * @param sorting Sort order: new, hotness, day, week, month
     * @param lastId Last article ID for cursor-based pagination (optional)
     * @return Timeline DTO with items and hasMore flag, or null on error
     */
    @RateLimiter(name = "vcru", fallbackMethod = "timelineRateLimitFallback")
    @Retry(name = "vcru")
    @CircuitBreaker(name = "vcru")
    fun getTimeline(subsiteId: Long, sorting: String, lastId: Long? = null): VcruTimelineDto? {
        val url = buildTimelineUrl(subsiteId, sorting, lastId)

        log.debug("Fetching timeline from VC.RU API: subsiteId={}, sorting={}, lastId={}", subsiteId, sorting, lastId)

        return try {
            val response = restTemplate.exchange(
                url,
                HttpMethod.GET,
                createRequestEntity(),
                VcruTimelineResponseDto::class.java
            )

            if (response.statusCode == HttpStatus.OK && response.body != null) {
                val result = response.body!!.result
                log.debug(
                    "Successfully fetched timeline: subsiteId={}, items={}, hasMore={}",
                    subsiteId,
                    result?.items?.size ?: 0,
                    result?.hasMore
                )
                result
            } else {
                log.warn("Failed to fetch timeline: subsiteId={}, status={}", subsiteId, response.statusCode)
                null
            }
        } catch (e: RestClientException) {
            log.error("Error fetching timeline for subsiteId={}: {}", subsiteId, e.message)
            throw e
        }
    }

    // ========== Private helper methods ==========

    /**
     * Build URL for timeline endpoint with optional pagination cursor
     */
    private fun buildTimelineUrl(subsiteId: Long, sorting: String, lastId: Long?): String {
        val url = StringBuilder(baseUrl)
        url.append("/v2.8/timeline")
        url.append("?subsitesIds=").append(subsiteId)
        url.append("&sorting=").append(sorting)
        url.append("&markdown=false")
        if (lastId != null) {
            url.append("&lastId=").append(lastId)
        }
        return url.toString()
    }

    /**
     * Create HTTP request entity with required headers
     */
    private fun createRequestEntity(): HttpEntity<String> {
        val headers = HttpHeaders()
        headers.set("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
        headers.set("Accept", "application/json")
        return HttpEntity(headers)
    }

    // ========== Fallback methods ==========

    @Suppress("UNUSED_PARAMETER")
    private fun subsiteRateLimitFallback(alias: String, t: Throwable): VcruSubsiteDto? {
        log.warn("Rate limit fallback triggered for VC.RU API (subsite): alias={}", alias)
        return null
    }

    @Suppress("UNUSED_PARAMETER")
    private fun timelineRateLimitFallback(subsiteId: Long, sorting: String, lastId: Long?, t: Throwable): VcruTimelineDto? {
        log.warn("Rate limit fallback triggered for VC.RU API (timeline): subsiteId={}", subsiteId)
        return null
    }
}
