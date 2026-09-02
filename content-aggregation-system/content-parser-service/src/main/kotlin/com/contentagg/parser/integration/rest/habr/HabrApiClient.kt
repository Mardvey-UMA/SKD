package com.contentagg.parser.integration.rest.habr

import com.contentagg.parser.integration.rest.habr.model.HabrArticleDto
import com.contentagg.parser.integration.rest.habr.model.HabrArticleListDto
import com.contentagg.parser.integration.rest.habr.model.HabrCommentsDto
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
import org.springframework.web.client.RestClientException
import org.springframework.web.client.RestTemplate

/**
 * HTTP client for Habr API (kek/v2).
 * Implements rate limiting, retries, and circuit breaker patterns.
 *
 * API Documentation: See /mydocs/HABR_API_ANALYSIS.md
 *
 * Rate Limiting:
 * - 60 requests per minute (1 per second)
 * - Delays between requests: 0.3-1 second
 *
 * Endpoints:
 * - GET /kek/v2/articles - List articles (filtered by user/company/hub)
 * - GET /kek/v2/articles/{id} - Get single article with full HTML
 * - GET /kek/v2/articles/{id}/comments - Get article comments
 */
@Component
class HabrApiClient(
    @Qualifier("habrRestTemplate") private val restTemplate: RestTemplate,
    @Value("\${parser.habr.base-url:https://habr.com}") private val baseUrl: String,
) {
    companion object {
        private val log = LoggerFactory.getLogger(HabrApiClient::class.java)

        // Standard query parameters for Habr API
        private const val FL_PARAM = "ru"
        private const val HL_PARAM = "ru"
        private const val DEFAULT_PER_PAGE = 20
    }

    /**
     * Get list of article IDs for a user
     *
     * @param userLogin User login/alias
     * @param page Page number (default: 1)
     * @param perPage Items per page (max: 20)
     * @return Article list response
     */
    @RateLimiter(name = "habr", fallbackMethod = "rateLimitFallback")
    @Retry(name = "habr")
    @CircuitBreaker(name = "habr")
    fun getArticlesByUser(userLogin: String, page: Int, perPage: Int): HabrArticleListDto {
        val url = buildArticlesUrl("user", userLogin, null, page, perPage)
        return fetchArticleList(url)
    }

    /**
     * Get list of article IDs for a company
     *
     * @param companyAlias Company alias
     * @param page Page number (default: 1)
     * @param perPage Items per page (max: 20)
     * @return Article list response
     */
    @RateLimiter(name = "habr", fallbackMethod = "rateLimitFallback")
    @Retry(name = "habr")
    @CircuitBreaker(name = "habr")
    fun getArticlesByCompany(companyAlias: String, page: Int, perPage: Int): HabrArticleListDto {
        val url = buildArticlesUrl("company", companyAlias, null, page, perPage)
        return fetchArticleList(url)
    }

    /**
     * Get list of article IDs for a hub
     *
     * @param hubAlias Hub alias
     * @param page Page number (default: 1)
     * @param perPage Items per page (max: 20)
     * @return Article list response
     */
    @RateLimiter(name = "habr", fallbackMethod = "rateLimitFallback")
    @Retry(name = "habr")
    @CircuitBreaker(name = "habr")
    fun getArticlesByHub(hubAlias: String, page: Int, perPage: Int): HabrArticleListDto {
        // Hub articles require sort=all parameter
        val url = buildArticlesUrl("hub", hubAlias, "all", page, perPage)
        return fetchArticleList(url)
    }

    /**
     * Get full article by ID (includes textHtml)
     *
     * @param articleId Article ID
     * @return Full article DTO with HTML content, or null if not found
     */
    @RateLimiter(name = "habr", fallbackMethod = "rateLimitFallback")
    @Retry(name = "habr")
    @CircuitBreaker(name = "habr")
    fun getArticle(articleId: String): HabrArticleDto? {
        val url = "$baseUrl/kek/v2/articles/$articleId?fl=$FL_PARAM&hl=$HL_PARAM"

        log.debug("Fetching article from Habr API: {}", url)

        return try {
            val response = restTemplate.exchange(url, HttpMethod.GET, createRequestEntity(), HabrArticleDto::class.java)

            if (response.statusCode == HttpStatus.OK && response.body != null) {
                log.debug("Successfully fetched article: {}", articleId)
                response.body
            } else {
                log.warn("Failed to fetch article: {}, status: {}", articleId, response.statusCode)
                null
            }
        } catch (e: RestClientException) {
            log.error("Error fetching article {}: {}", articleId, e.message)
            throw e
        }
    }

    /**
     * Get article comments
     *
     * @param articleId Article ID
     * @return Comments response with threading, or null if not found
     */
    @RateLimiter(name = "habr", fallbackMethod = "rateLimitFallback")
    @Retry(name = "habr")
    @CircuitBreaker(name = "habr")
    fun getComments(articleId: String): HabrCommentsDto? {
        val url = "$baseUrl/kek/v2/articles/$articleId/comments?fl=$FL_PARAM&hl=$HL_PARAM"

        log.debug("Fetching comments from Habr API: {}", url)

        return try {
            val response = restTemplate.exchange(url, HttpMethod.GET, createRequestEntity(), HabrCommentsDto::class.java)

            if (response.statusCode == HttpStatus.OK && response.body != null) {
                log.debug("Successfully fetched comments for article: {}", articleId)
                response.body
            } else {
                log.warn("Failed to fetch comments for article: {}, status: {}", articleId, response.statusCode)
                null
            }
        } catch (e: RestClientException) {
            log.error("Error fetching comments for article {}: {}", articleId, e.message)
            throw e
        }
    }

    // ========== Private helper methods ==========

    /**
     * Build URL for article list endpoint with filters
     */
    private fun buildArticlesUrl(filterType: String, alias: String, sort: String?, page: Int, perPage: Int): String {
        val url = StringBuilder(baseUrl)
        url.append("/kek/v2/articles?")
        url.append("fl=").append(FL_PARAM)
        url.append("&hl=").append(HL_PARAM)

        // Add filter parameter
        url.append("&").append(filterType).append("=").append(alias)

        // Add sort parameter (required for hubs)
        if (sort != null) {
            url.append("&sort=").append(sort)
        }

        // Add pagination
        if (page > 0) {
            url.append("&page=").append(page)
        }
        if (perPage > 0 && perPage <= DEFAULT_PER_PAGE) {
            url.append("&perPage=").append(perPage)
        } else {
            url.append("&perPage=").append(DEFAULT_PER_PAGE)
        }

        return url.toString()
    }

    /**
     * Fetch article list from API
     */
    private fun fetchArticleList(url: String): HabrArticleListDto {
        log.debug("Fetching article list from Habr API: {}", url)

        return try {
            val response = restTemplate.exchange(url, HttpMethod.GET, createRequestEntity(), HabrArticleListDto::class.java)

            if (response.statusCode == HttpStatus.OK && response.body != null) {
                val result = response.body!!
                log.debug(
                    "Successfully fetched article list: {} articles, {} pages",
                    result.publicationIds?.size ?: 0,
                    result.pagesCount,
                )
                result
            } else {
                log.warn("Failed to fetch article list, status: {}", response.statusCode)
                HabrArticleListDto(0, emptyList(), emptyMap())
            }
        } catch (e: RestClientException) {
            log.error("Error fetching article list: {}", e.message)
            throw e
        }
    }

    /**
     * Create HTTP request entity with required headers
     */
    private fun createRequestEntity(): HttpEntity<String> {
        val headers = HttpHeaders()
        headers.set("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
        headers.set("Accept", "application/json")
        headers.set("Accept-Language", "ru-RU,ru;q=0.9,en-US;q=0.8,en;q=0.7")
        return HttpEntity(headers)
    }

    // ========== Fallback methods ==========

    @Suppress("UNUSED_PARAMETER")
    private fun rateLimitFallback(filterType: String, alias: String, t: Throwable): HabrArticleListDto {
        log.warn("Rate limit fallback triggered for Habr API (articles list): {}={}", filterType, alias)
        return HabrArticleListDto(0, emptyList(), emptyMap())
    }

    @Suppress("UNUSED_PARAMETER")
    private fun rateLimitFallback(articleId: String, t: Throwable): HabrArticleDto? {
        log.warn("Rate limit fallback triggered for Habr API (article): {}", articleId)
        return null
    }

    @Suppress("UNUSED_PARAMETER")
    private fun rateLimitFallbackComments(articleId: String, t: Throwable): HabrCommentsDto? {
        log.warn("Rate limit fallback triggered for Habr API (comments): {}", articleId)
        return null
    }
}
