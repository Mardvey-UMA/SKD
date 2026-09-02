package com.contentplatform.feed.integration.api

import com.contentplatform.feed.integration.IntegrationTestBase
import com.fasterxml.jackson.databind.ObjectMapper
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.test.web.client.TestRestTemplate
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.jdbc.core.JdbcTemplate
import java.sql.Timestamp
import java.time.Instant
import java.util.Base64
import java.util.UUID
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

@Tag("integration")
class BlockedSourcesControllerIntegrationTest : IntegrationTestBase() {

    @Autowired
    lateinit var restTemplate: TestRestTemplate

    @Autowired
    lateinit var jdbcTemplate: JdbcTemplate

    @Autowired
    lateinit var objectMapper: ObjectMapper

    @Value("\${gateway.hmac-secret}")
    lateinit var hmacSecret: String

    @BeforeEach
    fun cleanUp() {
        jdbcTemplate.execute("DELETE FROM feed.blocked_sources")
        jdbcTemplate.execute("DELETE FROM feed.source_additions")
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

    private fun seedBlocked(userId: UUID, sourceId: UUID, blockedAt: Instant) {
        jdbcTemplate.update(
            "INSERT INTO feed.blocked_sources (user_id, source_id, blocked_at) VALUES (?, ?, ?)",
            userId, sourceId, Timestamp.from(blockedAt)
        )
    }

    private fun seedSourceAddition(
        userId: UUID,
        sourceId: UUID,
        sourceType: String,
        sourceName: String
    ) {
        jdbcTemplate.update(
            """
            INSERT INTO feed.source_additions (user_id, source_id, source_type, source_name, added_at)
            VALUES (?, ?, ?, ?, now())
            """.trimIndent(),
            userId, sourceId, sourceType, sourceName
        )
    }

    @Test
    fun `GET api feed blocked-sources returns enriched items with metadata and nulls for LEFT JOIN miss ordered DESC`() {
        val userId = UUID.randomUUID()
        val enrichedSource = UUID.randomUUID()
        val orphanSource = UUID.randomUUID()

        // Enriched: has source_additions row — most recently blocked
        seedSourceAddition(userId, enrichedSource, "rss", "Enriched Source")
        seedBlocked(userId, enrichedSource, Instant.parse("2026-04-21T12:00:00Z"))

        // Orphan: no source_additions row — blocked earlier
        seedBlocked(userId, orphanSource, Instant.parse("2026-04-20T09:00:00Z"))

        val headers = buildAuthenticatedHeaders(userId = userId.toString())
        val response = restTemplate.exchange(
            "/api/feed/blocked-sources",
            HttpMethod.GET,
            HttpEntity<Void>(headers),
            String::class.java
        )

        assertThat(response.statusCode).isEqualTo(HttpStatus.OK)
        val body = objectMapper.readTree(response.body)
        assertThat(body.get("count").asInt()).isEqualTo(2)

        val items = body.get("items")
        assertThat(items.size()).isEqualTo(2)

        // Ordering: most recently blocked first (enrichedSource, then orphanSource)
        val first = items.get(0)
        assertThat(first.get("source_id").asText()).isEqualTo(enrichedSource.toString())
        assertThat(first.get("source_type").asText()).isEqualTo("rss")
        assertThat(first.get("source_name").asText()).isEqualTo("Enriched Source")

        val second = items.get(1)
        assertThat(second.get("source_id").asText()).isEqualTo(orphanSource.toString())
        assertThat(second.get("source_type").isNull).isTrue()
        assertThat(second.get("source_name").isNull).isTrue()
    }
}
