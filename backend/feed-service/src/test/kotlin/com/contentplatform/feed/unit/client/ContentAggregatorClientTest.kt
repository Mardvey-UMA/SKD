package com.contentplatform.feed.unit.client

import com.contentplatform.feed.infrastructure.client.model.ContentBatchItem
import com.contentplatform.feed.infrastructure.client.model.ContentBatchResponse
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.PropertyNamingStrategies
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test

@Tag("unit")
class ContentAggregatorClientTest {

    private val objectMapper = jacksonObjectMapper().apply {
        propertyNamingStrategy = PropertyNamingStrategies.SNAKE_CASE
        configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
    }

    @Test
    fun `should deserialize ContentBatchResponse with items map and not_found list`() {
        val json = """
            {
                "items": {
                    "content-123": {
                        "id": "content-123",
                        "title": "Test Article",
                        "description": "A test description",
                        "content": "Full content text here",
                        "content_format": "html",
                        "source_type": "telegram",
                        "source_subtype": "channel",
                        "url": "https://example.com/article",
                        "published_at": "2026-04-05T10:00:00Z",
                        "author_name": "Test Author",
                        "media": [{"type": "image", "url": "https://example.com/img.jpg"}],
                        "metadata": {"views": 100},
                        "related_ids": ["content-456", "content-789"]
                    }
                },
                "not_found": ["content-999", "content-888"]
            }
        """.trimIndent()

        val response: ContentBatchResponse = objectMapper.readValue(json)

        assertThat(response.items).hasSize(1)
        assertThat(response.items).containsKey("content-123")
        assertThat(response.notFound).containsExactly("content-999", "content-888")

        val item = response.items["content-123"]!!
        assertThat(item.id).isEqualTo("content-123")
        assertThat(item.title).isEqualTo("Test Article")
        assertThat(item.description).isEqualTo("A test description")
        assertThat(item.content).isEqualTo("Full content text here")
        assertThat(item.contentFormat).isEqualTo("html")
        assertThat(item.sourceType).isEqualTo("telegram")
        assertThat(item.sourceSubtype).isEqualTo("channel")
        assertThat(item.url).isEqualTo("https://example.com/article")
        assertThat(item.publishedAt).isNotNull()
        assertThat(item.authorName).isEqualTo("Test Author")
        assertThat(item.media).hasSize(1)
        assertThat(item.relatedIds).containsExactly("content-456", "content-789")
    }

    @Test
    fun `should deserialize ContentBatchResponse with empty items and empty not_found`() {
        val json = """
            {
                "items": {},
                "not_found": []
            }
        """.trimIndent()

        val response: ContentBatchResponse = objectMapper.readValue(json)

        assertThat(response.items).isEmpty()
        assertThat(response.notFound).isEmpty()
    }

    @Test
    fun `should deserialize ContentBatchItem with null optional fields`() {
        val json = """
            {
                "items": {
                    "content-123": {
                        "id": "content-123",
                        "title": "Minimal Article",
                        "description": null,
                        "content": null,
                        "content_format": "text",
                        "source_type": "rss",
                        "source_subtype": null,
                        "url": "https://example.com/minimal",
                        "published_at": "2026-04-05T10:00:00Z",
                        "author_name": null,
                        "media": [],
                        "metadata": null,
                        "related_ids": []
                    }
                },
                "not_found": []
            }
        """.trimIndent()

        val response: ContentBatchResponse = objectMapper.readValue(json)

        val item = response.items["content-123"]!!
        assertThat(item.id).isEqualTo("content-123")
        assertThat(item.title).isEqualTo("Minimal Article")
        assertThat(item.description).isNull()
        assertThat(item.content).isNull()
        assertThat(item.sourceSubtype).isNull()
        assertThat(item.authorName).isNull()
        assertThat(item.media).isEmpty()
        assertThat(item.metadata).isNull()
        assertThat(item.relatedIds).isEmpty()
    }

    @Test
    fun `should deserialize multiple items in the map`() {
        val json = """
            {
                "items": {
                    "content-1": {
                        "id": "content-1",
                        "title": "First",
                        "content_format": "html",
                        "source_type": "telegram",
                        "url": "https://example.com/1",
                        "published_at": "2026-04-05T10:00:00Z",
                        "media": [],
                        "related_ids": []
                    },
                    "content-2": {
                        "id": "content-2",
                        "title": "Second",
                        "content_format": "text",
                        "source_type": "rss",
                        "url": "https://example.com/2",
                        "published_at": "2026-04-05T11:00:00Z",
                        "media": [],
                        "related_ids": ["content-1"]
                    }
                },
                "not_found": ["content-3"]
            }
        """.trimIndent()

        val response: ContentBatchResponse = objectMapper.readValue(json)

        assertThat(response.items).hasSize(2)
        assertThat(response.items.keys).containsExactlyInAnyOrder("content-1", "content-2")
        assertThat(response.notFound).containsExactly("content-3")
        assertThat(response.items["content-2"]!!.relatedIds).containsExactly("content-1")
    }

    @Test
    fun `should ignore unknown fields in ContentBatchItem`() {
        val json = """
            {
                "items": {
                    "content-123": {
                        "id": "content-123",
                        "title": "Article",
                        "content_format": "html",
                        "source_type": "telegram",
                        "url": "https://example.com/article",
                        "published_at": "2026-04-05T10:00:00Z",
                        "media": [],
                        "related_ids": [],
                        "unknown_extra_field": "should be ignored"
                    }
                },
                "not_found": []
            }
        """.trimIndent()

        val response: ContentBatchResponse = objectMapper.readValue(json)

        assertThat(response.items["content-123"]!!.title).isEqualTo("Article")
    }
}
