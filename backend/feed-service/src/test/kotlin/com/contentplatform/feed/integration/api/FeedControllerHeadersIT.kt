package com.contentplatform.feed.integration.api

import com.contentplatform.feed.integration.IntegrationTestBase
import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.post
import com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo
import com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo
import com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.test.web.client.TestRestTemplate
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.awaitility.Awaitility.await
import java.time.Duration
import java.util.Base64
import java.util.UUID
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * Integration tests verifying X-Request-Id and X-Feed-Source response headers from FeedController.
 */
@Tag("integration")
class FeedControllerHeadersIT : IntegrationTestBase() {

    @Autowired
    lateinit var restTemplate: TestRestTemplate

    @Autowired
    lateinit var jdbcTemplate: JdbcTemplate

    @Autowired
    lateinit var redisTemplate: StringRedisTemplate

    @Autowired
    lateinit var feedCacheService: com.contentplatform.feed.infrastructure.cache.FeedCacheService

    @Value("\${gateway.hmac-secret}")
    lateinit var hmacSecret: String

    companion object {
        @JvmStatic
        val recSystemWireMock = WireMockServer(wireMockConfig().dynamicPort())

        @JvmStatic
        val contentAggWireMock = WireMockServer(wireMockConfig().dynamicPort())

        @BeforeAll
        @JvmStatic
        fun startWireMock() {
            recSystemWireMock.start()
            contentAggWireMock.start()
        }

        @AfterAll
        @JvmStatic
        fun stopWireMock() {
            recSystemWireMock.stop()
            contentAggWireMock.stop()
        }

        @DynamicPropertySource
        @JvmStatic
        fun wireMockProperties(registry: DynamicPropertyRegistry) {
            registry.add("services.rec-system.url") { "http://localhost:${recSystemWireMock.port()}" }
            registry.add("services.content-aggregation.url") { "http://localhost:${contentAggWireMock.port()}" }
        }
    }

    @BeforeEach
    fun cleanUp() {
        recSystemWireMock.resetAll()
        contentAggWireMock.resetAll()
        redisTemplate.connectionFactory?.connection?.serverCommands()?.flushAll()
        jdbcTemplate.execute("DELETE FROM feed.user_bookmarks")
        jdbcTemplate.execute("DELETE FROM feed.user_likes")
        jdbcTemplate.execute("DELETE FROM feed.user_dislikes")
        jdbcTemplate.execute("DELETE FROM feed.feed_items")
        jdbcTemplate.execute("DELETE FROM feed.feed_requests")
    }

    private fun computeHmac(payload: String): String {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(hmacSecret.toByteArray(), "HmacSHA256"))
        return Base64.getEncoder().encodeToString(mac.doFinal(payload.toByteArray()))
    }

    private fun buildAuthenticatedHeaders(
        userId: String = UUID.randomUUID().toString(),
        roles: String = "USER",
        subscriptionTier: String = "free",
        requestId: String = UUID.randomUUID().toString()
    ): HttpHeaders {
        val payload = "$userId|$roles|$subscriptionTier|$requestId"
        val signature = computeHmac(payload)
        return HttpHeaders().apply {
            contentType = MediaType.APPLICATION_JSON
            set("X-User-Id", userId)
            set("X-User-Roles", roles)
            set("X-Subscription-Tier", subscriptionTier)
            set("X-Request-Id", requestId)
            set("X-Gateway-Signature", signature)
        }
    }

    @Test
    fun `GET api feed should include X-Request-Id response header`() {
        val userId = UUID.randomUUID().toString()
        val contentId = UUID.randomUUID().toString()
        val requestId = UUID.randomUUID().toString()

        recSystemWireMock.stubFor(
            post(urlPathEqualTo("/recommendations"))
                .willReturn(
                    aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""{"user_id":"$userId","items":["$contentId"],"count":1,"generated_at":"2026-04-19T00:00:00Z"}""")
                )
        )

        contentAggWireMock.stubFor(
            post(urlEqualTo("/api/v1/content/batch"))
                .willReturn(
                    aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""{"items":{"$contentId":{"id":"$contentId","title":"Test Article"}},"not_found":[]}""")
                )
        )

        val headers = buildAuthenticatedHeaders(userId = userId, requestId = requestId)
        val response = restTemplate.exchange(
            "/api/feed",
            HttpMethod.GET,
            HttpEntity<Void>(headers),
            String::class.java
        )

        assertThat(response.statusCode).isEqualTo(HttpStatus.OK)
        assertThat(response.headers.getFirst("X-Request-Id")).isNotNull
    }

    @Test
    fun `GET api feed should include X-Feed-Source response header with valid value`() {
        val userId = UUID.randomUUID().toString()
        val contentId = UUID.randomUUID().toString()

        recSystemWireMock.stubFor(
            post(urlPathEqualTo("/recommendations"))
                .willReturn(
                    aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""{"user_id":"$userId","items":["$contentId"],"count":1,"generated_at":"2026-04-19T00:00:00Z"}""")
                )
        )

        contentAggWireMock.stubFor(
            post(urlEqualTo("/api/v1/content/batch"))
                .willReturn(
                    aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""{"items":{"$contentId":{"id":"$contentId","title":"Test Article"}},"not_found":[]}""")
                )
        )

        val headers = buildAuthenticatedHeaders(userId = userId)
        val response = restTemplate.exchange(
            "/api/feed",
            HttpMethod.GET,
            HttpEntity<Void>(headers),
            String::class.java
        )

        assertThat(response.statusCode).isEqualTo(HttpStatus.OK)
        val feedSource = response.headers.getFirst("X-Feed-Source")
        assertThat(feedSource).isNotNull
        assertThat(feedSource).isIn("personalized", "cold_start", "cached", "fallback")
    }

    @Test
    fun `GET api feed with cached feed should return X-Feed-Source as cached`() {
        val userId = UUID.randomUUID().toString()
        val contentId = UUID.randomUUID().toString()

        // Populate feed cache using feedCacheService (JSON format)
        feedCacheService.cacheFeed(UUID.fromString(userId), listOf(contentId))

        contentAggWireMock.stubFor(
            post(urlEqualTo("/api/v1/content/batch"))
                .willReturn(
                    aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""{"items":{"$contentId":{"id":"$contentId","title":"Test Article"}},"not_found":[]}""")
                )
        )

        val headers = buildAuthenticatedHeaders(userId = userId)
        val response = restTemplate.exchange(
            "/api/feed",
            HttpMethod.GET,
            HttpEntity<Void>(headers),
            String::class.java
        )

        assertThat(response.statusCode).isEqualTo(HttpStatus.OK)
        assertThat(response.headers.getFirst("X-Feed-Source")).isEqualTo("cached")
    }

    @Test
    fun `GET api feed when rec-system down should return X-Feed-Source as fallback`() {
        val userId = UUID.randomUUID().toString()

        recSystemWireMock.stubFor(
            post(urlPathEqualTo("/recommendations"))
                .willReturn(aResponse().withStatus(500))
        )

        recSystemWireMock.stubFor(
            com.github.tomakehurst.wiremock.client.WireMock.get(urlEqualTo("/recommendations/cold-start?count=120"))
                .willReturn(aResponse().withStatus(500))
        )

        val headers = buildAuthenticatedHeaders(userId = userId)
        val response = restTemplate.exchange(
            "/api/feed",
            HttpMethod.GET,
            HttpEntity<Void>(headers),
            String::class.java
        )

        assertThat(response.statusCode).isEqualTo(HttpStatus.OK)
        assertThat(response.headers.getFirst("X-Feed-Source")).isEqualTo("fallback")
    }

    @Test
    fun `GET api feed with breakdown response should persist feature_flags in feed_requests`() {
        val userId = UUID.randomUUID().toString()
        val contentId = UUID.randomUUID().toString()
        val requestId = UUID.randomUUID().toString()

        recSystemWireMock.stubFor(
            post(urlPathEqualTo("/recommendations"))
                .willReturn(
                    aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""{"user_id":"$userId","items":["$contentId"],"count":1,"generated_at":"2026-04-19T00:00:00Z","feature_flags":{"live_profile":false,"rerank":false,"hot_arrival":false}}""")
                )
        )

        contentAggWireMock.stubFor(
            post(urlEqualTo("/api/v1/content/batch"))
                .willReturn(
                    aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""{"items":{"$contentId":{"id":"$contentId","title":"Test Article"}},"not_found":[]}""")
                )
        )

        val headers = buildAuthenticatedHeaders(userId = userId, requestId = requestId)
        val response = restTemplate.exchange(
            "/api/feed",
            HttpMethod.GET,
            HttpEntity<Void>(headers),
            String::class.java
        )

        assertThat(response.statusCode).isEqualTo(HttpStatus.OK)

        val featureFlags = await().atMost(Duration.ofSeconds(5))
            .until({
                runCatching {
                    jdbcTemplate.queryForObject(
                        "SELECT feature_flags FROM feed.feed_requests WHERE request_id = ?::uuid",
                        String::class.java,
                        requestId
                    )
                }.getOrNull()
            }) { it != null }

        assertThat(featureFlags).contains("live_profile")
        assertThat(featureFlags).contains("rerank")
        assertThat(featureFlags).contains("hot_arrival")
    }
}
