package com.contentplatform.feed.unit.client

import com.contentplatform.feed.infrastructure.client.model.ColdStartResponse
import com.contentplatform.feed.infrastructure.client.model.RecommendationsResponse
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.PropertyNamingStrategies
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import java.util.UUID

@Tag("unit")
class RecSystemClientTest {

    private val objectMapper = jacksonObjectMapper().apply {
        propertyNamingStrategy = PropertyNamingStrategies.SNAKE_CASE
        configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
    }

    @Test
    fun `should deserialize RecommendationsResponse with snake_case fields`() {
        val userId = UUID.randomUUID()
        val itemId1 = UUID.randomUUID()
        val itemId2 = UUID.randomUUID()
        val json = """
            {
                "user_id": "$userId",
                "items": ["$itemId1", "$itemId2"],
                "count": 2,
                "generated_at": "2026-04-05T10:00:00Z"
            }
        """.trimIndent()

        val response: RecommendationsResponse = objectMapper.readValue(json)

        assertThat(response.userId).isEqualTo(userId)
        assertThat(response.items).containsExactly(itemId1, itemId2)
        assertThat(response.count).isEqualTo(2)
        assertThat(response.generatedAt).isNotNull()
    }

    @Test
    fun `should deserialize RecommendationsResponse with empty items`() {
        val userId = UUID.randomUUID()
        val json = """
            {
                "user_id": "$userId",
                "items": [],
                "count": 0,
                "generated_at": "2026-04-05T10:00:00Z"
            }
        """.trimIndent()

        val response: RecommendationsResponse = objectMapper.readValue(json)

        assertThat(response.userId).isEqualTo(userId)
        assertThat(response.items).isEmpty()
        assertThat(response.count).isEqualTo(0)
    }

    @Test
    fun `should deserialize ColdStartResponse`() {
        val itemId1 = UUID.randomUUID()
        val itemId2 = UUID.randomUUID()
        val json = """
            {
                "items": ["$itemId1", "$itemId2"],
                "count": 2,
                "generated_at": "2026-04-05T10:00:00Z"
            }
        """.trimIndent()

        val response: ColdStartResponse = objectMapper.readValue(json)

        assertThat(response.items).containsExactly(itemId1, itemId2)
        assertThat(response.count).isEqualTo(2)
        assertThat(response.generatedAt).isNotNull()
    }

    @Test
    fun `should ignore unknown fields in RecommendationsResponse`() {
        val userId = UUID.randomUUID()
        val json = """
            {
                "user_id": "$userId",
                "items": [],
                "count": 0,
                "generated_at": "2026-04-05T10:00:00Z",
                "extra_field": "should be ignored"
            }
        """.trimIndent()

        val response: RecommendationsResponse = objectMapper.readValue(json)

        assertThat(response.userId).isEqualTo(userId)
    }
}
