package com.contentplatform.feed.integration.cache

import com.contentplatform.feed.infrastructure.cache.FeedCacheService
import com.contentplatform.feed.infrastructure.client.model.FeedItemDetail
import com.contentplatform.feed.integration.IntegrationTestBase
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.data.redis.core.StringRedisTemplate
import java.util.UUID
import java.util.concurrent.TimeUnit

@Tag("integration")
class FeedCacheServiceIntegrationTest : IntegrationTestBase() {

    @Autowired
    lateinit var feedCacheService: FeedCacheService

    @Autowired
    lateinit var redisTemplate: StringRedisTemplate

    @BeforeEach
    fun cleanRedis() {
        redisTemplate.connectionFactory?.connection?.serverCommands()?.flushAll()
    }

    // ── JSON-STRING (UUID-keyed, personalized feed) ─────────────────────

    @Nested
    inner class `put and getCachedFeed` {

        @Test
        fun `should store feed as JSON string in Redis`() {
            val userId = UUID.randomUUID()
            val ids = (1..10).map { UUID.randomUUID() }

            feedCacheService.put(userId, ids, null)

            val key = "feed:user:$userId"
            val raw = redisTemplate.opsForValue().get(key)
            assertThat(raw).isNotNull
            assertThat(raw).contains("items")
            assertThat(raw).contains(ids.first().toString())
        }

        @Test
        fun `should round-trip with itemsDetailed preserved`() {
            val userId = UUID.randomUUID()
            val ids = listOf(UUID.randomUUID(), UUID.randomUUID())
            val detailed = listOf(
                FeedItemDetail(
                    contentId = ids[0],
                    finalScore = 0.85,
                    scoringComponents = mapOf("recency" to 0.4, "popularity" to 0.3),
                    rerankScore = 0.9
                ),
                FeedItemDetail(contentId = ids[1], finalScore = 0.6)
            )

            feedCacheService.put(userId, ids, detailed)
            val result = feedCacheService.getCachedFeed(userId)

            assertThat(result).isNotNull
            assertThat(result!!.items).containsExactlyElementsOf(ids)
            assertThat(result.itemsDetailed).hasSize(2)
            assertThat(result.itemsDetailed!![0].finalScore).isEqualTo(0.85)
            assertThat(result.itemsDetailed[0].scoringComponents).containsEntry("recency", 0.4)
            assertThat(result.itemsDetailed[1].rerankScore).isNull()
        }

        @Test
        fun `should round-trip with null itemsDetailed`() {
            val userId = UUID.randomUUID()
            val ids = (1..5).map { UUID.randomUUID() }

            feedCacheService.put(userId, ids, null)
            val result = feedCacheService.getCachedFeed(userId)

            assertThat(result).isNotNull
            assertThat(result!!.items).hasSize(5)
            assertThat(result.itemsDetailed).isNull()
        }

        @Test
        fun `should return null when key does not exist`() {
            val result = feedCacheService.getCachedFeed(UUID.randomUUID())
            assertThat(result).isNull()
        }

        @Test
        fun `should set TTL between 25 and 35 minutes on put`() {
            val userId = UUID.randomUUID()
            feedCacheService.put(userId, listOf(UUID.randomUUID()), null)

            val key = "feed:user:$userId"
            val ttl = redisTemplate.getExpire(key, TimeUnit.SECONDS)
            assertThat(ttl).isBetween(25 * 60L - 5, 35 * 60L + 5)
        }
    }

    @Nested
    inner class `getFeedSize UUID-keyed` {

        @Test
        fun `should return 0 when feed does not exist`() {
            val size = feedCacheService.getFeedSize(UUID.randomUUID())
            assertThat(size).isEqualTo(0L)
        }

        @Test
        fun `should return correct size after caching feed`() {
            val userId = UUID.randomUUID()
            val ids = (1..25).map { UUID.randomUUID() }

            feedCacheService.put(userId, ids, null)

            val size = feedCacheService.getFeedSize(userId)
            assertThat(size).isEqualTo(25L)
        }
    }

    @Nested
    inner class `getFeedPage UUID-keyed` {

        @Test
        fun `should return empty list when feed does not exist`() {
            val page = feedCacheService.getFeedPage(UUID.randomUUID(), 0, 20)
            assertThat(page).isEmpty()
        }

        @Test
        fun `should return correct page from cached feed`() {
            val userId = UUID.randomUUID()
            val ids = (1..50).map { UUID.randomUUID() }
            feedCacheService.put(userId, ids, null)

            val page = feedCacheService.getFeedPage(userId, 0, 20)

            assertThat(page).hasSize(20)
            assertThat(page.first()).isEqualTo(ids[0].toString())
            assertThat(page.last()).isEqualTo(ids[19].toString())
        }

        @Test
        fun `should return second page with offset`() {
            val userId = UUID.randomUUID()
            val ids = (1..50).map { UUID.randomUUID() }
            feedCacheService.put(userId, ids, null)

            val page = feedCacheService.getFeedPage(userId, 20, 20)

            assertThat(page).hasSize(20)
            assertThat(page.first()).isEqualTo(ids[20].toString())
        }

        @Test
        fun `should return empty list when offset exceeds feed size`() {
            val userId = UUID.randomUUID()
            feedCacheService.put(userId, (1..10).map { UUID.randomUUID() }, null)

            val page = feedCacheService.getFeedPage(userId, 100, 20)

            assertThat(page).isEmpty()
        }
    }

    @Nested
    inner class `cacheFeed UUID-keyed` {

        @Test
        fun `should store content IDs in JSON cache`() {
            val userId = UUID.randomUUID()
            val ids = (1..10).map { UUID.randomUUID() }
            val stringIds = ids.map { it.toString() }

            feedCacheService.cacheFeed(userId, stringIds)

            val result = feedCacheService.getCachedFeed(userId)
            assertThat(result).isNotNull
            assertThat(result!!.items.map { it.toString() }).containsExactlyElementsOf(stringIds)
            assertThat(result.itemsDetailed).isNull()
        }

        @Test
        fun `should atomically replace existing feed on re-cache`() {
            val userId = UUID.randomUUID()
            val oldIds = (1..5).map { UUID.randomUUID() }
            val newIds = (1..3).map { UUID.randomUUID() }
            feedCacheService.put(userId, oldIds, null)
            feedCacheService.put(userId, newIds, null)

            val result = feedCacheService.getCachedFeed(userId)
            assertThat(result!!.items).hasSize(3)
            assertThat(result.items).containsExactlyElementsOf(newIds)
        }

        @Test
        fun `should handle empty list without error`() {
            feedCacheService.cacheFeed(UUID.randomUUID(), emptyList())
        }

        @Test
        fun `should set TTL between 25 and 35 minutes`() {
            val userId = UUID.randomUUID()
            feedCacheService.cacheFeed(userId, listOf(UUID.randomUUID().toString()))

            val key = "feed:user:$userId"
            val ttl = redisTemplate.getExpire(key, TimeUnit.SECONDS)
            assertThat(ttl).isBetween(25 * 60L - 5, 35 * 60L + 5)
        }
    }

    @Nested
    inner class `deleteFeed UUID-keyed` {

        @Test
        fun `should delete existing feed from Redis`() {
            val userId = UUID.randomUUID()
            feedCacheService.put(userId, listOf(UUID.randomUUID()), null)

            feedCacheService.deleteFeed(userId)

            assertThat(feedCacheService.getCachedFeed(userId)).isNull()
        }

        @Test
        fun `should not throw when deleting non-existent feed`() {
            feedCacheService.deleteFeed(UUID.randomUUID())
        }
    }

    // ── LIST (String-keyed, space feeds) ───────────────────────────────────

    @Nested
    inner class `getFeedSize String-keyed` {

        @Test
        fun `should return 0 when key does not exist`() {
            val size = feedCacheService.getFeedSize("feed:space:unknown")
            assertThat(size).isEqualTo(0L)
        }

        @Test
        fun `should return correct size after caching space feed`() {
            val key = "feed:space:test:user:${UUID.randomUUID()}"
            feedCacheService.cacheFeed(key, (1..25).map { "content-$it" })

            val size = feedCacheService.getFeedSize(key)
            assertThat(size).isEqualTo(25L)
        }
    }

    @Nested
    inner class `getFeedPage String-keyed` {

        @Test
        fun `should return correct page from space feed cache`() {
            val key = "feed:space:test:user:${UUID.randomUUID()}"
            val ids = (1..50).map { "content-$it" }
            feedCacheService.cacheFeed(key, ids)

            val page = feedCacheService.getFeedPage(key, 0, 20)

            assertThat(page).hasSize(20)
            assertThat(page.first()).isEqualTo("content-1")
            assertThat(page.last()).isEqualTo("content-20")
        }

        @Test
        fun `should return second page with offset for space feed`() {
            val key = "feed:space:test:user:${UUID.randomUUID()}"
            feedCacheService.cacheFeed(key, (1..50).map { "content-$it" })

            val page = feedCacheService.getFeedPage(key, 20, 20)

            assertThat(page).hasSize(20)
            assertThat(page.first()).isEqualTo("content-21")
        }
    }

    @Nested
    inner class `cacheFeed String-keyed` {

        @Test
        fun `should store all content IDs in Redis list for space feed`() {
            val key = "feed:space:${UUID.randomUUID()}:user:${UUID.randomUUID()}"
            val ids = (1..10).map { "content-$it" }

            feedCacheService.cacheFeed(key, ids)

            val stored = redisTemplate.opsForList().range(key, 0, -1)
            assertThat(stored).hasSize(10)
            assertThat(stored?.first()).isEqualTo("content-1")
        }

        @Test
        fun `should atomically replace existing space feed`() {
            val key = "feed:space:${UUID.randomUUID()}:user:${UUID.randomUUID()}"
            feedCacheService.cacheFeed(key, listOf("old-1", "old-2"))
            feedCacheService.cacheFeed(key, listOf("new-1"))

            val stored = redisTemplate.opsForList().range(key, 0, -1)
            assertThat(stored).hasSize(1)
            assertThat(stored).containsExactly("new-1")
        }

        @Test
        fun `should not leave tmp key after space feed caching`() {
            val key = "feed:space:${UUID.randomUUID()}:user:${UUID.randomUUID()}"
            feedCacheService.cacheFeed(key, listOf("content-1"))

            val tmpExists = redisTemplate.hasKey("$key:tmp")
            assertThat(tmpExists).isFalse()
        }
    }

    @Nested
    inner class `tryAcquirePrefetchLock` {

        @Test
        fun `should return true on first acquisition`() {
            val userId = UUID.randomUUID()
            val acquired = feedCacheService.tryAcquirePrefetchLock(userId)
            assertThat(acquired).isTrue()
        }

        @Test
        fun `should return false on second acquisition for same user`() {
            val userId = UUID.randomUUID()
            feedCacheService.tryAcquirePrefetchLock(userId)

            val secondAttempt = feedCacheService.tryAcquirePrefetchLock(userId)
            assertThat(secondAttempt).isFalse()
        }

        @Test
        fun `should allow acquisition for different users`() {
            val userId1 = UUID.randomUUID()
            val userId2 = UUID.randomUUID()

            assertThat(feedCacheService.tryAcquirePrefetchLock(userId1)).isTrue()
            assertThat(feedCacheService.tryAcquirePrefetchLock(userId2)).isTrue()
        }

        @Test
        fun `should set TTL of 60 seconds on lock key`() {
            val userId = UUID.randomUUID()
            feedCacheService.tryAcquirePrefetchLock(userId)

            val key = "feed:user:$userId:prefetch"
            val ttl = redisTemplate.getExpire(key, TimeUnit.SECONDS)
            assertThat(ttl).isBetween(55L, 61L)
        }
    }
}
