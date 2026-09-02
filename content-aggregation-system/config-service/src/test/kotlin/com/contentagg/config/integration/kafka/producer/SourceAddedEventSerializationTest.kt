package com.contentagg.config.integration.kafka.producer

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

/**
 * Ensures the JSON wire-format for `source.added` stays stable across refactors.
 * Backend consumers (feed-service, Phase 3) rely on these exact field names.
 */
class SourceAddedEventSerializationTest {

    private val mapper: ObjectMapper = jacksonObjectMapper()

    @Test
    fun `serialises all fields using camelCase JSON keys`() {
        val event = SourceAddedEvent(
            sourceId = "src-1",
            userId = "u-1",
            sourceType = "TELEGRAM",
            name = "Habr Daily",
            addedAt = "2026-04-16T10:00:00Z",
            requestId = "r-9",
        )

        val json = mapper.writeValueAsString(event)
        val tree = mapper.readTree(json)

        assertThat(tree.get("sourceId").asText()).isEqualTo("src-1")
        assertThat(tree.get("userId").asText()).isEqualTo("u-1")
        assertThat(tree.get("sourceType").asText()).isEqualTo("TELEGRAM")
        assertThat(tree.get("name").asText()).isEqualTo("Habr Daily")
        assertThat(tree.get("addedAt").asText()).isEqualTo("2026-04-16T10:00:00Z")
        assertThat(tree.get("requestId").asText()).isEqualTo("r-9")
    }

    @Test
    fun `roundtrips via JSON preserving values`() {
        val event = SourceAddedEvent(
            sourceId = "s",
            userId = "u",
            sourceType = "HABR",
            name = "n",
            addedAt = "2026-04-16T10:00:00Z",
            requestId = null,
        )

        val back: SourceAddedEvent = mapper.readValue(mapper.writeValueAsString(event))

        assertThat(back).isEqualTo(event)
    }
}
