package com.contentplatform.feed.unit.cache

import com.contentplatform.feed.infrastructure.cache.CachedFeed
import com.contentplatform.feed.infrastructure.cache.FeedCacheService
import com.contentplatform.feed.infrastructure.client.model.FeedItemDetail
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import io.mockk.every
import io.mockk.mockk
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.data.redis.core.ValueOperations
import java.util.UUID

@Tag("unit")
class FeedCacheServiceUnitTest {

    private val redisTemplate = mockk<StringRedisTemplate>(relaxed = true)
    private val objectMapper = ObjectMapper()
        .registerModule(JavaTimeModule())
        .apply { registerKotlinModule() }
        .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
    private val service = FeedCacheService(redisTemplate, objectMapper)

    private val valueOps = mockk<ValueOperations<String, String>>(relaxed = true)
    private val stored = mutableMapOf<String, String>()

    @BeforeEach
    fun setup() {
        stored.clear()
        every { redisTemplate.opsForValue() } returns valueOps
        every { valueOps.set(any<String>(), any<String>(), any<Long>(), any()) } answers {
            stored[firstArg()] = secondArg()
        }
        every { valueOps.get(any<String>()) } answers { stored[firstArg<String>()] }
    }

    @Nested
    inner class `put and getCachedFeed round-trip` {

        @Test
        fun `should round-trip CachedFeed with itemsDetailed preserving all fields`() {
            val userId = UUID.randomUUID()
            val id1 = UUID.randomUUID()
            val id2 = UUID.randomUUID()
            val detailed = listOf(
                FeedItemDetail(
                    contentId = id1,
                    finalScore = 0.9,
                    scoringComponents = mapOf("recency" to 0.5, "popularity" to 0.4),
                    rerankScore = 0.85
                ),
                FeedItemDetail(
                    contentId = id2,
                    finalScore = 0.7,
                    scoringComponents = mapOf("recency" to 0.3),
                    rerankScore = null
                )
            )

            service.put(userId, listOf(id1, id2), detailed)
            val result = service.getCachedFeed(userId)

            assertThat(result).isNotNull
            assertThat(result!!.items).containsExactly(id1, id2)
            assertThat(result.itemsDetailed).hasSize(2)
            assertThat(result.itemsDetailed!![0].contentId).isEqualTo(id1)
            assertThat(result.itemsDetailed[0].finalScore).isEqualTo(0.9)
            assertThat(result.itemsDetailed[0].scoringComponents).containsEntry("recency", 0.5)
            assertThat(result.itemsDetailed[0].rerankScore).isEqualTo(0.85)
            assertThat(result.itemsDetailed[1].rerankScore).isNull()
        }

        @Test
        fun `should round-trip CachedFeed with null itemsDetailed`() {
            val userId = UUID.randomUUID()
            val items = listOf(UUID.randomUUID(), UUID.randomUUID())

            service.put(userId, items, null)
            val result = service.getCachedFeed(userId)

            assertThat(result).isNotNull
            assertThat(result!!.items).isEqualTo(items)
            assertThat(result.itemsDetailed).isNull()
        }

        @Test
        fun `should return null when cache key does not exist`() {
            val userId = UUID.randomUUID()
            val result = service.getCachedFeed(userId)
            assertThat(result).isNull()
        }
    }
}
