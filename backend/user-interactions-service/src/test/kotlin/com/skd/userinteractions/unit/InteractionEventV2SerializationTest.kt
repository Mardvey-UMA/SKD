package com.skd.userinteractions.unit

import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.skd.userinteractions.api.model.interactions.InteractionEvent
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.UUID

@Tag("unit")
class InteractionEventV2SerializationTest {

    private val objectMapper = jacksonObjectMapper()
        .registerModule(JavaTimeModule())
        .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)

    @Test
    fun `deserializes v1 json without new optional fields - new fields are null`() {
        val contentId = UUID.randomUUID().toString()
        val json = """{"content_id":"$contentId","action_type":"view","duration_sec":45,"timestamp":"2026-04-19T12:00:00Z"}"""

        val event = objectMapper.readValue(json, InteractionEvent::class.java)

        assertThat(event.contentId).isEqualTo(contentId)
        assertThat(event.actionType).isEqualTo("view")
        assertThat(event.durationSec).isEqualTo(45)
        assertThat(event.feedRequestId).isNull()
        assertThat(event.positionInFeed).isNull()
        assertThat(event.deviceType).isNull()
        assertThat(event.appVersion).isNull()
        assertThat(event.abBucket).isNull()
        assertThat(event.scrollDepth).isNull()
        assertThat(event.metadata).isNull()
    }

    @Test
    fun `deserializes v2 json with all new optional fields`() {
        val contentId = UUID.randomUUID().toString()
        val feedReqId = UUID.randomUUID().toString()
        val json = """
            {
              "content_id": "$contentId",
              "action_type": "click",
              "duration_sec": 45,
              "timestamp": "2026-04-19T12:00:00Z",
              "feed_request_id": "$feedReqId",
              "position_in_feed": 2,
              "device_type": "android",
              "app_version": "1.2.3",
              "ab_bucket": 1,
              "scroll_depth": 0.75,
              "metadata": {"key": "value"}
            }
        """.trimIndent()

        val event = objectMapper.readValue(json, InteractionEvent::class.java)

        assertThat(event.feedRequestId).isEqualTo(feedReqId)
        assertThat(event.positionInFeed).isEqualTo(2)
        assertThat(event.deviceType).isEqualTo("android")
        assertThat(event.appVersion).isEqualTo("1.2.3")
        assertThat(event.abBucket).isEqualTo(1)
        assertThat(event.scrollDepth).isEqualTo(0.75)
        assertThat(event.metadata).containsEntry("key", "value")
    }

    @Test
    fun `serializes null optional fields as absent in json - not as null values`() {
        val event = InteractionEvent(
            contentId = UUID.randomUUID().toString(),
            actionType = "view",
            durationSec = null,
            timestamp = Instant.parse("2026-04-19T12:00:00Z")
        )

        val json = objectMapper.writeValueAsString(event)
        val node = objectMapper.readTree(json)

        assertThat(node.has("feed_request_id")).isFalse()
        assertThat(node.has("position_in_feed")).isFalse()
        assertThat(node.has("device_type")).isFalse()
        assertThat(node.has("app_version")).isFalse()
        assertThat(node.has("ab_bucket")).isFalse()
        assertThat(node.has("scroll_depth")).isFalse()
        assertThat(node.has("metadata")).isFalse()
    }

    @Test
    fun `serializes v2 event with all fields using snake_case json names`() {
        val feedReqId = UUID.randomUUID().toString()
        val event = InteractionEvent(
            contentId = UUID.randomUUID().toString(),
            actionType = "click",
            durationSec = 45,
            timestamp = Instant.parse("2026-04-19T12:00:00Z"),
            feedRequestId = feedReqId,
            positionInFeed = 2,
            deviceType = "android",
            appVersion = "1.2.3",
            abBucket = 1,
            scrollDepth = 0.75,
            metadata = mapOf("key" to "value")
        )

        val json = objectMapper.writeValueAsString(event)
        val node = objectMapper.readTree(json)

        assertThat(node.get("content_id")).isNotNull()
        assertThat(node.get("action_type").asText()).isEqualTo("click")
        assertThat(node.get("feed_request_id").asText()).isEqualTo(feedReqId)
        assertThat(node.get("position_in_feed").asInt()).isEqualTo(2)
        assertThat(node.get("device_type").asText()).isEqualTo("android")
        assertThat(node.get("app_version").asText()).isEqualTo("1.2.3")
        assertThat(node.get("ab_bucket").asInt()).isEqualTo(1)
        assertThat(node.get("scroll_depth").asDouble()).isEqualTo(0.75)
        assertThat(node.get("metadata").get("key").asText()).isEqualTo("value")
    }

    @Test
    fun `existing required fields use correct snake_case json names`() {
        val contentId = UUID.randomUUID().toString()
        val event = InteractionEvent(
            contentId = contentId,
            actionType = "save",
            durationSec = 10,
            timestamp = Instant.parse("2026-04-19T12:00:00Z")
        )

        val json = objectMapper.writeValueAsString(event)
        val node = objectMapper.readTree(json)

        assertThat(node.has("content_id")).isTrue()
        assertThat(node.has("action_type")).isTrue()
        assertThat(node.has("duration_sec")).isTrue()
        assertThat(node.has("timestamp")).isTrue()
        assertThat(node.get("content_id").asText()).isEqualTo(contentId)
        assertThat(node.get("action_type").asText()).isEqualTo("save")
        assertThat(node.get("duration_sec").asInt()).isEqualTo(10)
    }
}
