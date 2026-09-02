package com.contentplatform.feed.integration.api

import com.contentplatform.feed.integration.IntegrationTestBase
import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.post
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
import java.util.Base64
import java.util.UUID
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

@Tag("integration")
class FeedServiceCacheBreakdownIT : IntegrationTestBase() {

    @Autowired
    lateinit var restTemplate: TestRestTemplate

    @Autowired
    lateinit var redisTemplate: StringRedisTemplate

    @Autowired
    lateinit var jdbcTemplate: JdbcTemplate

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
        jdbcTemplate.execute("DELETE FROM feed.feed_items")
        jdbcTemplate.execute("DELETE FROM feed.feed_requests")
    }

    private fun computeHmac(payload: String): String {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(hmacSecret.toByteArray(), "HmacSHA256"))
        return Base64.getEncoder().encodeToString(mac.doFinal(payload.toByteArray()))
    }

    private fun buildAuthHeaders(
        userId: String = UUID.randomUUID().toString(),
        requestId: String = UUID.randomUUID().toString()
    ): Pair<HttpHeaders, String> {
        val payload = "$userId|USER|free|$requestId"
        val signature = computeHmac(payload)
        val headers = HttpHeaders().apply {
            contentType = MediaType.APPLICATION_JSON
            set("X-User-Id", userId)
            set("X-User-Roles", "USER")
            set("X-Subscription-Tier", "free")
            set("X-Request-Id", requestId)
            set("X-Gateway-Signature", signature)
        }
        return headers to userId
    }

    @Test
    fun `second request cache hit should have same X-Feed-Source cached header`() {
        val contentId = UUID.randomUUID().toString()
        val userId = UUID.randomUUID().toString()

        val recResponse = """
            {
              "user_id": "$userId",
              "items": ["$contentId"],
              "count": 1,
              "generated_at": "2026-04-20T00:00:00Z",
              "items_detailed": [
                {
                  "content_id": "$contentId",
                  "final_score": 0.85,
                  "scoring_components": {"recency": 0.4, "popularity": 0.3, "topic_relevance": 0.15},
                  "rerank_score": 0.9
                }
              ]
            }
        """.trimIndent()

        val contentResponse = """
            {"items":{"$contentId":{"id":"$contentId","title":"Test Article"}},"not_found":[]}
        """.trimIndent()

        recSystemWireMock.stubFor(
            post(urlPathEqualTo("/recommendations"))
                .willReturn(
                    aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(recResponse)
                )
        )

        contentAggWireMock.stubFor(
            post(urlPathEqualTo("/api/v1/content/batch"))
                .willReturn(
                    aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(contentResponse)
                )
        )

        val (headers1) = buildAuthHeaders(userId = userId)
        val response1 = restTemplate.exchange(
            "/api/feed",
            HttpMethod.GET,
            HttpEntity<Void>(headers1),
            String::class.java
        )

        assertThat(response1.statusCode).isEqualTo(HttpStatus.OK)
        val source1 = response1.headers["X-Feed-Source"]?.firstOrNull()
        assertThat(source1).isIn("personalized", "cold_start")

        val (headers2) = buildAuthHeaders(userId = userId)
        val response2 = restTemplate.exchange(
            "/api/feed",
            HttpMethod.GET,
            HttpEntity<Void>(headers2),
            String::class.java
        )

        assertThat(response2.statusCode).isEqualTo(HttpStatus.OK)
        val source2 = response2.headers["X-Feed-Source"]?.firstOrNull()
        assertThat(source2).isEqualTo("cached")
    }

    @Test
    fun `cache hit should persist feed_items with non-null scoring_components`() {
        val contentId = UUID.randomUUID().toString()
        val userId = UUID.randomUUID().toString()
        val requestId1 = UUID.randomUUID().toString()
        val requestId2 = UUID.randomUUID().toString()

        val recResponse = """
            {
              "user_id": "$userId",
              "items": ["$contentId"],
              "count": 1,
              "generated_at": "2026-04-20T00:00:00Z",
              "items_detailed": [
                {
                  "content_id": "$contentId",
                  "final_score": 0.85,
                  "scoring_components": {"recency": 0.4, "popularity": 0.3, "topic_relevance": 0.15},
                  "rerank_score": 0.9
                }
              ]
            }
        """.trimIndent()

        val contentResponse = """
            {"items":{"$contentId":{"id":"$contentId","title":"Article"}},"not_found":[]}
        """.trimIndent()

        recSystemWireMock.stubFor(
            post(urlPathEqualTo("/recommendations"))
                .willReturn(
                    aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(recResponse)
                )
        )
        contentAggWireMock.stubFor(
            post(urlPathEqualTo("/api/v1/content/batch"))
                .willReturn(
                    aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(contentResponse)
                )
        )

        val (headers1) = buildAuthHeaders(userId = userId, requestId = requestId1)
        restTemplate.exchange("/api/feed", HttpMethod.GET, HttpEntity<Void>(headers1), String::class.java)

        val (headers2) = buildAuthHeaders(userId = userId, requestId = requestId2)
        val response2 = restTemplate.exchange("/api/feed", HttpMethod.GET, HttpEntity<Void>(headers2), String::class.java)

        assertThat(response2.headers["X-Feed-Source"]?.firstOrNull()).isEqualTo("cached")

        Thread.sleep(200)

        val scoringComponents = jdbcTemplate.queryForList(
            "SELECT scoring_components::text FROM feed.feed_items WHERE request_id = ?::uuid",
            requestId2
        )
        assertThat(scoringComponents).isNotEmpty
        val sc = scoringComponents.first().values.firstOrNull()?.toString()
        assertThat(sc).isNotNull
        assertThat(sc).contains("recency")
    }

    @Test
    fun `cache key should contain JSON with items and itemsDetailed after first request`() {
        val contentId = UUID.randomUUID().toString()
        val userId = UUID.randomUUID().toString()

        val recResponse = """
            {
              "user_id": "$userId",
              "items": ["$contentId"],
              "count": 1,
              "generated_at": "2026-04-20T00:00:00Z",
              "items_detailed": [
                {
                  "content_id": "$contentId",
                  "final_score": 0.85,
                  "scoring_components": {"recency": 0.4},
                  "rerank_score": null
                }
              ]
            }
        """.trimIndent()

        recSystemWireMock.stubFor(
            post(urlPathEqualTo("/recommendations"))
                .willReturn(
                    aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(recResponse)
                )
        )
        contentAggWireMock.stubFor(
            post(urlPathEqualTo("/api/v1/content/batch"))
                .willReturn(
                    aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""{"items":{"$contentId":{"id":"$contentId","title":"T"}},"not_found":[]}""")
                )
        )

        val (headers) = buildAuthHeaders(userId = userId)
        restTemplate.exchange("/api/feed", HttpMethod.GET, HttpEntity<Void>(headers), String::class.java)

        val cacheKey = "feed:user:$userId"
        val cachedValue = redisTemplate.opsForValue().get(cacheKey)

        assertThat(cachedValue).isNotNull
        assertThat(cachedValue).contains("items")
        assertThat(cachedValue).contains("itemsDetailed")
        assertThat(cachedValue).contains(contentId)
        assertThat(cachedValue).contains("recency")
    }
}
