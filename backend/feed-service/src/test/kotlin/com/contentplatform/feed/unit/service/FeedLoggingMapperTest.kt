package com.contentplatform.feed.unit.service

import com.contentplatform.feed.application.FeedItemLog
import com.contentplatform.feed.application.FeedLoggingMapper
import com.contentplatform.feed.application.FeedRequestLog
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.UUID

@Tag("unit")
class FeedLoggingMapperTest {

    private val objectMapper: ObjectMapper = jacksonObjectMapper()
    private val mapper = FeedLoggingMapper(objectMapper)

    @Test
    fun `should map FeedRequestLog to FeedRequestEntity preserving all scalar fields`() {
        val requestId = UUID.randomUUID()
        val userId = UUID.randomUUID()
        val requestedAt = Instant.parse("2026-04-19T10:30:00Z")

        val log = FeedRequestLog(
            requestId = requestId,
            userId = userId,
            requestedAt = requestedAt,
            pageNumber = 3,
            source = "personalized",
            countRequested = 30,
            countReturned = 25,
            latencyMs = 210,
            latencyBreakdown = mapOf("rec_system" to 100, "content_agg" to 60, "db" to 50),
            featureFlags = mapOf("new_ranker" to true, "show_ads" to false),
            abBucket = 5,
            appVersion = "2.0.1",
            deviceType = "ios"
        )

        val entity = mapper.toEntity(log)

        assertThat(entity.requestId).isEqualTo(requestId)
        assertThat(entity.userId).isEqualTo(userId)
        assertThat(entity.requestedAt).isEqualTo(requestedAt)
        assertThat(entity.pageNumber).isEqualTo(3)
        assertThat(entity.source).isEqualTo("personalized")
        assertThat(entity.countRequested).isEqualTo(30)
        assertThat(entity.countReturned).isEqualTo(25)
        assertThat(entity.latencyMs).isEqualTo(210)
        assertThat(entity.abBucket).isEqualTo(5)
        assertThat(entity.appVersion).isEqualTo("2.0.1")
        assertThat(entity.deviceType).isEqualTo("ios")
    }

    @Test
    fun `should serialize latency_breakdown map to JSON string`() {
        val log = baseRequestLog().copy(
            latencyBreakdown = mapOf("rec_system" to 100, "content_agg" to 60)
        )

        val entity = mapper.toEntity(log)

        assertThat(entity.latencyBreakdown).isNotNull
        val parsed = objectMapper.readValue(entity.latencyBreakdown, Map::class.java)
        assertThat(parsed["rec_system"]).isEqualTo(100)
        assertThat(parsed["content_agg"]).isEqualTo(60)
    }

    @Test
    fun `should serialize feature_flags map to JSON string`() {
        val log = baseRequestLog().copy(
            featureFlags = mapOf("new_ranker" to true, "variant" to "b")
        )

        val entity = mapper.toEntity(log)

        assertThat(entity.featureFlags).isNotNull
        val parsed = objectMapper.readValue(entity.featureFlags, Map::class.java)
        assertThat(parsed["new_ranker"]).isEqualTo(true)
        assertThat(parsed["variant"]).isEqualTo("b")
    }

    @Test
    fun `should map FeedItemLog to FeedItemEntity preserving all scalar fields`() {
        val requestId = UUID.randomUUID()
        val contentId = UUID.randomUUID()
        val rawContentId = UUID.randomUUID()

        val log = FeedItemLog(
            requestId = requestId,
            position = 4,
            contentId = contentId,
            rawContentId = rawContentId,
            finalScore = 0.87f,
            scoringComponents = mapOf("semantic" to 0.8, "freshness" to 0.9),
            rerankScore = 0.82f,
            filteredOutBy = null
        )

        val entity = mapper.toEntity(log)

        assertThat(entity.requestId).isEqualTo(requestId)
        assertThat(entity.position).isEqualTo(4.toShort())
        assertThat(entity.contentId).isEqualTo(contentId)
        assertThat(entity.rawContentId).isEqualTo(rawContentId)
        assertThat(entity.finalScore).isEqualTo(0.87f)
        assertThat(entity.rerankScore).isEqualTo(0.82f)
        assertThat(entity.filteredOutBy).isNull()
    }

    @Test
    fun `should serialize scoring_components map to JSON string`() {
        val log = baseItemLog().copy(
            scoringComponents = mapOf("semantic" to 0.8, "freshness" to 0.9, "popularity" to 0.3)
        )

        val entity = mapper.toEntity(log)

        assertThat(entity.scoringComponents).isNotNull
        val parsed = objectMapper.readValue(entity.scoringComponents, Map::class.java)
        assertThat((parsed["semantic"] as Number).toDouble()).isEqualTo(0.8)
        assertThat((parsed["freshness"] as Number).toDouble()).isEqualTo(0.9)
        assertThat((parsed["popularity"] as Number).toDouble()).isEqualTo(0.3)
    }

    @Test
    fun `should map null JSONB fields to null on FeedRequestEntity`() {
        val log = baseRequestLog().copy(
            latencyBreakdown = null,
            featureFlags = null
        )

        val entity = mapper.toEntity(log)

        assertThat(entity.latencyBreakdown).isNull()
        assertThat(entity.featureFlags).isNull()
    }

    @Test
    fun `should map null JSONB fields to null on FeedItemEntity`() {
        val log = baseItemLog().copy(scoringComponents = null)

        val entity = mapper.toEntity(log)

        assertThat(entity.scoringComponents).isNull()
    }

    @Test
    fun `should map nullable scalar fields to null on FeedItemEntity`() {
        val log = baseItemLog().copy(
            rawContentId = null,
            finalScore = null,
            rerankScore = null,
            filteredOutBy = "dislike"
        )

        val entity = mapper.toEntity(log)

        assertThat(entity.rawContentId).isNull()
        assertThat(entity.finalScore).isNull()
        assertThat(entity.rerankScore).isNull()
        assertThat(entity.filteredOutBy).isEqualTo("dislike")
    }

    @Test
    fun `should map nullable scalar fields to null on FeedRequestEntity`() {
        val log = baseRequestLog().copy(
            latencyMs = null,
            appVersion = null,
            deviceType = null
        )

        val entity = mapper.toEntity(log)

        assertThat(entity.latencyMs).isNull()
        assertThat(entity.appVersion).isNull()
        assertThat(entity.deviceType).isNull()
    }

    private fun baseRequestLog() = FeedRequestLog(
        requestId = UUID.randomUUID(),
        userId = UUID.randomUUID(),
        requestedAt = Instant.parse("2026-04-19T10:00:00Z"),
        pageNumber = 0,
        source = "personalized",
        countRequested = 30,
        countReturned = 0,
        latencyMs = 100,
        latencyBreakdown = null,
        featureFlags = null,
        abBucket = 0,
        appVersion = null,
        deviceType = null
    )

    private fun baseItemLog() = FeedItemLog(
        requestId = UUID.randomUUID(),
        position = 0,
        contentId = UUID.randomUUID(),
        rawContentId = null,
        finalScore = 0.5f,
        scoringComponents = null,
        rerankScore = null,
        filteredOutBy = null
    )
}
