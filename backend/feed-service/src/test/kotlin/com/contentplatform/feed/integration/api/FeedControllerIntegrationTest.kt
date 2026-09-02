package com.contentplatform.feed.integration.api

import com.contentplatform.feed.integration.IntegrationTestBase
import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.post
import com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo
import com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo
import com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.SoftAssertions
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import com.contentplatform.feed.infrastructure.cache.FeedCacheService
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
class FeedControllerIntegrationTest : IntegrationTestBase() {

    @Autowired
    lateinit var restTemplate: TestRestTemplate

    @Autowired
    lateinit var jdbcTemplate: JdbcTemplate

    @Autowired
    lateinit var redisTemplate: StringRedisTemplate

    @Autowired
    lateinit var feedCacheService: FeedCacheService

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

    @Nested
    inner class `GET api feed` {

        @Test
        fun `should return 200 with feed items when rec-system and content-aggregator respond`() {
            val userId = UUID.randomUUID().toString()
            val contentId = UUID.randomUUID().toString()

            recSystemWireMock.stubFor(
                post(urlPathEqualTo("/recommendations"))
                    .willReturn(
                        aResponse()
                            .withStatus(200)
                            .withHeader("Content-Type", "application/json")
                            .withBody("""{"user_id":"$userId","items":["$contentId"],"count":1,"generated_at":"2026-04-05T00:00:00Z"}""")
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
            assertThat(response.body).contains(contentId)
            assertThat(response.body).contains("Test Article")
        }

        @Test
        fun `should return empty feed with message when rec-system is down`() {
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
            assertThat(response.body).contains("items")
        }
    }

    @Nested
    inner class `GET api feed content by id` {

        @Test
        fun `should return 200 with content item`() {
            val userId = UUID.randomUUID().toString()
            val contentId = "content-42"

            contentAggWireMock.stubFor(
                post(urlEqualTo("/api/v1/content/batch"))
                    .willReturn(
                        aResponse()
                            .withStatus(200)
                            .withHeader("Content-Type", "application/json")
                            .withBody("""{"items":{"$contentId":{"id":"$contentId","title":"Found Article"}},"not_found":[]}""")
                    )
            )

            val headers = buildAuthenticatedHeaders(userId = userId)
            val response = restTemplate.exchange(
                "/api/feed/content/$contentId",
                HttpMethod.GET,
                HttpEntity<Void>(headers),
                String::class.java
            )

            assertThat(response.statusCode).isEqualTo(HttpStatus.OK)
            assertThat(response.body).contains(contentId)
            assertThat(response.body).contains("Found Article")
        }

        @Test
        fun `should return 404 when content not found`() {
            val userId = UUID.randomUUID().toString()
            val contentId = "nonexistent"

            contentAggWireMock.stubFor(
                post(urlEqualTo("/api/v1/content/batch"))
                    .willReturn(
                        aResponse()
                            .withStatus(200)
                            .withHeader("Content-Type", "application/json")
                            .withBody("""{"items":{},"not_found":["$contentId"]}""")
                    )
            )

            val headers = buildAuthenticatedHeaders(userId = userId)
            val response = restTemplate.exchange(
                "/api/feed/content/$contentId",
                HttpMethod.GET,
                HttpEntity<Void>(headers),
                String::class.java
            )

            assertThat(response.statusCode).isEqualTo(HttpStatus.NOT_FOUND)
        }
    }

    @Nested
    inner class `HMAC verification` {

        @Test
        fun `should return 403 when no gateway headers provided`() {
            val response = restTemplate.exchange(
                "/api/feed",
                HttpMethod.GET,
                HttpEntity<Void>(HttpHeaders()),
                String::class.java
            )

            assertThat(response.statusCode).isEqualTo(HttpStatus.FORBIDDEN)
        }

        @Test
        fun `should return 403 when signature is invalid`() {
            val headers = HttpHeaders().apply {
                set("X-User-Id", UUID.randomUUID().toString())
                set("X-User-Roles", "USER")
                set("X-Subscription-Tier", "free")
                set("X-Request-Id", UUID.randomUUID().toString())
                set("X-Gateway-Signature", "invalid-signature")
            }

            val response = restTemplate.exchange(
                "/api/feed",
                HttpMethod.GET,
                HttpEntity<Void>(headers),
                String::class.java
            )

            assertThat(response.statusCode).isEqualTo(HttpStatus.FORBIDDEN)
        }
    }

    @Nested
    inner class `health endpoint` {

        @Test
        fun `should return 200 without HMAC headers`() {
            val response = restTemplate.getForEntity("/health", String::class.java)

            assertThat(response.statusCode).isEqualTo(HttpStatus.OK)
            SoftAssertions.assertSoftly { softly ->
                softly.assertThat(response.body).contains("status")
                softly.assertThat(response.body).contains("feed-service")
            }
        }
    }

    @Nested
    inner class `collection endpoints` {

        @Test
        fun `should add and retrieve bookmark`() {
            val userId = UUID.randomUUID().toString()
            val contentId = UUID.randomUUID()
            val headers = buildAuthenticatedHeaders(userId = userId)

            // Add bookmark
            val addResponse = restTemplate.exchange(
                "/api/feed/bookmarks/$contentId",
                HttpMethod.POST,
                HttpEntity<Void>(headers),
                String::class.java
            )
            assertThat(addResponse.statusCode).isEqualTo(HttpStatus.CREATED)

            // Check status
            val statusResponse = restTemplate.exchange(
                "/api/feed/content/$contentId/status",
                HttpMethod.GET,
                HttpEntity<Void>(headers),
                String::class.java
            )
            assertThat(statusResponse.statusCode).isEqualTo(HttpStatus.OK)
            assertThat(statusResponse.body).contains("\"bookmarked\":true")
        }

        @Test
        fun `should return 409 on duplicate bookmark`() {
            val userId = UUID.randomUUID().toString()
            val contentId = UUID.randomUUID()
            val headers = buildAuthenticatedHeaders(userId = userId)

            // First add succeeds
            restTemplate.exchange(
                "/api/feed/bookmarks/$contentId",
                HttpMethod.POST,
                HttpEntity<Void>(headers),
                String::class.java
            )

            // Second add returns 409
            val response = restTemplate.exchange(
                "/api/feed/bookmarks/$contentId",
                HttpMethod.POST,
                HttpEntity<Void>(headers),
                String::class.java
            )
            assertThat(response.statusCode).isEqualTo(HttpStatus.CONFLICT)
        }

        @Test
        fun `should enforce like-dislike mutual exclusion`() {
            val userId = UUID.randomUUID().toString()
            val contentId = UUID.randomUUID()
            val headers = buildAuthenticatedHeaders(userId = userId)

            // Like first
            restTemplate.exchange(
                "/api/feed/likes/$contentId",
                HttpMethod.POST,
                HttpEntity<Void>(headers),
                String::class.java
            )

            // Dislike replaces like
            restTemplate.exchange(
                "/api/feed/dislikes/$contentId",
                HttpMethod.POST,
                HttpEntity<Void>(headers),
                String::class.java
            )

            // Check status -- should be disliked, not liked
            val statusResponse = restTemplate.exchange(
                "/api/feed/content/$contentId/status",
                HttpMethod.GET,
                HttpEntity<Void>(headers),
                String::class.java
            )
            assertThat(statusResponse.statusCode).isEqualTo(HttpStatus.OK)
            assertThat(statusResponse.body).contains("\"liked\":false")
            assertThat(statusResponse.body).contains("\"disliked\":true")
        }
    }

    @Nested
    inner class `GET api feed dislike filtering` {

        @Test
        fun `should return full page without disliked items after over-fetch loop`() {
            val userId = UUID.randomUUID().toString()
            // 25 content IDs: first 5 will be disliked
            val allContentIds = (1..25).map { UUID.randomUUID() }
            val dislikedContentIds = allContentIds.take(5)
            val nonDislikedIds = allContentIds.drop(5)

            // Populate feed cache using feedCacheService (JSON format)
            feedCacheService.cacheFeed(UUID.fromString(userId), allContentIds.map { it.toString() })

            // Insert 5 dislikes into DB
            dislikedContentIds.forEach { contentId ->
                jdbcTemplate.update(
                    "INSERT INTO feed.user_dislikes (user_id, content_id) VALUES (?::uuid, ?::uuid)",
                    userId,
                    contentId.toString()
                )
            }

            // Stub content-aggregator to return items for the 20 non-disliked IDs
            val itemsJson = nonDislikedIds.take(20).joinToString(",") { id ->
                """"$id":{"id":"$id","title":"Article $id"}"""
            }
            contentAggWireMock.stubFor(
                post(urlEqualTo("/api/v1/content/batch"))
                    .willReturn(
                        aResponse()
                            .withStatus(200)
                            .withHeader("Content-Type", "application/json")
                            .withBody("""{"items":{$itemsJson},"not_found":[]}""")
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
            // None of the 5 disliked IDs should appear in the response body
            dislikedContentIds.forEach { id ->
                assertThat(response.body).doesNotContain(id.toString())
            }
            // At least some non-disliked content should appear
            assertThat(response.body).contains("Article")
        }
    }
}
