package com.contentplatform.feed.integration.cache

import com.contentplatform.feed.infrastructure.cache.ContentCacheService
import com.contentplatform.feed.infrastructure.client.model.ContentBatchItem
import com.contentplatform.feed.integration.IntegrationTestBase
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.data.redis.core.StringRedisTemplate
import java.util.concurrent.TimeUnit

@Tag("integration")
class ContentCacheServiceIntegrationTest : IntegrationTestBase() {

    @Autowired
    lateinit var contentCacheService: ContentCacheService

    @Autowired
    lateinit var redisTemplate: StringRedisTemplate

    @BeforeEach
    fun cleanRedis() {
        redisTemplate.connectionFactory?.connection?.serverCommands()?.flushAll()
    }

    companion object {
        fun createTestItem(
            id: String = "test-content-1",
            title: String = "Test Article",
            description: String? = "A test article description",
            url: String? = "https://example.com/article"
        ) = ContentBatchItem(
            id = id,
            title = title,
            description = description,
            url = url
        )
    }

    @Nested
    inner class `getContent` {

        @Test
        fun `should return null when content is not cached`() {
            val result = contentCacheService.getContent("nonexistent-id")
            assertThat(result).isNull()
        }

        @Test
        fun `should return cached content after caching`() {
            val item = createTestItem()
            contentCacheService.cacheContent("test-content-1", item)

            val result = contentCacheService.getContent("test-content-1")

            assertThat(result).isNotNull
            assertThat(result!!.id).isEqualTo("test-content-1")
            assertThat(result.title).isEqualTo("Test Article")
            assertThat(result.description).isEqualTo("A test article description")
            assertThat(result.url).isEqualTo("https://example.com/article")
        }

        @Test
        fun `should preserve all fields including nullable ones`() {
            val item = ContentBatchItem(
                id = "full-item",
                title = "Full Article",
                description = "Full description",
                content = "Article body text",
                contentFormat = "html",
                sourceType = "telegram",
                sourceSubtype = "channel",
                url = "https://example.com",
                publishedAt = "2026-04-05T10:00:00Z",
                authorName = "Author Name",
                media = listOf(mapOf("type" to "image", "url" to "https://img.example.com/1.jpg")),
                metadata = mapOf("tags" to listOf("tech", "news")),
                relatedIds = listOf("related-1", "related-2")
            )
            contentCacheService.cacheContent("full-item", item)

            val result = contentCacheService.getContent("full-item")

            assertThat(result).isNotNull
            assertThat(result!!.contentFormat).isEqualTo("html")
            assertThat(result.sourceType).isEqualTo("telegram")
            assertThat(result.sourceSubtype).isEqualTo("channel")
            assertThat(result.publishedAt).isEqualTo("2026-04-05T10:00:00Z")
            assertThat(result.authorName).isEqualTo("Author Name")
            assertThat(result.media).hasSize(1)
            assertThat(result.relatedIds).containsExactly("related-1", "related-2")
        }

        @Test
        fun `should handle content with only required fields`() {
            val item = ContentBatchItem(id = "minimal", title = "Minimal")
            contentCacheService.cacheContent("minimal", item)

            val result = contentCacheService.getContent("minimal")

            assertThat(result).isNotNull
            assertThat(result!!.id).isEqualTo("minimal")
            assertThat(result.title).isEqualTo("Minimal")
            assertThat(result.description).isNull()
            assertThat(result.content).isNull()
            assertThat(result.media).isNull()
        }
    }

    @Nested
    inner class `cacheContent` {

        @Test
        fun `should set TTL of 24 hours`() {
            val item = createTestItem()
            contentCacheService.cacheContent("test-content-1", item)

            val key = "content:test-content-1"
            val ttl = redisTemplate.getExpire(key, TimeUnit.HOURS)
            assertThat(ttl).isBetween(23L, 25L)
        }

        @Test
        fun `should overwrite existing cached content`() {
            val original = createTestItem(title = "Original Title")
            contentCacheService.cacheContent("test-content-1", original)

            val updated = createTestItem(title = "Updated Title")
            contentCacheService.cacheContent("test-content-1", updated)

            val result = contentCacheService.getContent("test-content-1")
            assertThat(result!!.title).isEqualTo("Updated Title")
        }
    }

    @Nested
    inner class `negative cache` {

        @Test
        fun `should return false when content is not negative cached`() {
            val result = contentCacheService.isNegativeCached("unknown-id")
            assertThat(result).isFalse()
        }

        @Test
        fun `should return true after setting negative cache`() {
            contentCacheService.setNegativeCache("missing-id")

            val result = contentCacheService.isNegativeCached("missing-id")
            assertThat(result).isTrue()
        }

        @Test
        fun `should set TTL of 5 minutes on negative cache`() {
            contentCacheService.setNegativeCache("missing-id")

            val key = "content:miss:missing-id"
            val ttl = redisTemplate.getExpire(key, TimeUnit.SECONDS)
            assertThat(ttl).isBetween(295L, 305L)
        }

        @Test
        fun `should use correct key pattern for negative cache`() {
            contentCacheService.setNegativeCache("my-content-id")

            val exists = redisTemplate.hasKey("content:miss:my-content-id")
            assertThat(exists).isTrue()
        }
    }

    @Nested
    inner class `getColdStartTrending` {

        @Test
        fun `should return null when no trending data exists`() {
            val result = contentCacheService.getColdStartTrending()
            assertThat(result).isNull()
        }

        @Test
        fun `should return list of content IDs when trending data exists`() {
            // Simulate rec-system writing trending data to Redis
            val trendingJson = """["trending-1","trending-2","trending-3"]"""
            redisTemplate.opsForValue().set("rec:cold-start:trending", trendingJson)

            val result = contentCacheService.getColdStartTrending()

            assertThat(result).isNotNull
            assertThat(result).containsExactly("trending-1", "trending-2", "trending-3")
        }

        @Test
        fun `should return empty list when trending data is empty array`() {
            redisTemplate.opsForValue().set("rec:cold-start:trending", "[]")

            val result = contentCacheService.getColdStartTrending()

            assertThat(result).isNotNull
            assertThat(result).isEmpty()
        }
    }
}
