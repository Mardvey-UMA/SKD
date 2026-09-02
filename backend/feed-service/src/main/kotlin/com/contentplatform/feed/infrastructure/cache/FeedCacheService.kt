package com.contentplatform.feed.infrastructure.cache

import com.contentplatform.feed.infrastructure.client.model.FeedItemDetail
import com.fasterxml.jackson.databind.ObjectMapper
import org.slf4j.LoggerFactory
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.stereotype.Service
import java.time.Instant
import java.util.UUID
import java.util.concurrent.TimeUnit

@Service
class FeedCacheService(
    private val redisTemplate: StringRedisTemplate,
    private val objectMapper: ObjectMapper
) {

    companion object {
        private val log = LoggerFactory.getLogger(FeedCacheService::class.java)
        private const val PAYLOAD_WARN_BYTES = 200 * 1024
    }

    // ── JSON-STRING cache (UUID-keyed, personalized feed) ──────────────────

    fun put(userId: UUID, items: List<UUID>, itemsDetailed: List<FeedItemDetail>?) {
        val payload = objectMapper.writeValueAsString(CachedFeed(items, itemsDetailed, Instant.now()))
        if (payload.length > PAYLOAD_WARN_BYTES) {
            log.warn("Cached feed payload size={} bytes exceeds soft limit for user={}", payload.length, userId)
        }
        val ttlMinutes = (25..35).random().toLong()
        redisTemplate.opsForValue().set(userKey(userId), payload, ttlMinutes, TimeUnit.MINUTES)
    }

    fun getCachedFeed(userId: UUID): CachedFeed? {
        val raw = redisTemplate.opsForValue().get(userKey(userId)) ?: return null
        return runCatching { objectMapper.readValue(raw, CachedFeed::class.java) }.getOrNull()
    }

    fun getFeedSize(userId: UUID): Long = getCachedFeed(userId)?.items?.size?.toLong() ?: 0L

    fun getFeedPage(userId: UUID, offset: Long, limit: Long): List<String> {
        val cached = getCachedFeed(userId) ?: return emptyList()
        val items = cached.items
        val start = offset.toInt().coerceIn(0, items.size)
        val end = minOf(start + limit.toInt(), items.size)
        return if (start >= items.size) emptyList() else items.subList(start, end).map { it.toString() }
    }

    fun cacheFeed(userId: UUID, contentIds: List<String>) {
        if (contentIds.isEmpty()) return
        val uuids = contentIds.mapNotNull { runCatching { UUID.fromString(it) }.getOrNull() }
        put(userId, uuids, null)
    }

    fun deleteFeed(userId: UUID) {
        redisTemplate.delete(userKey(userId))
    }

    fun tryAcquirePrefetchLock(userId: UUID): Boolean =
        tryAcquirePrefetchLock("${userKey(userId)}:prefetch")

    // ── LIST cache (String-keyed, space feeds) ─────────────────────────────

    fun getFeedSize(key: String): Long =
        redisTemplate.opsForList().size(key) ?: 0L

    fun getFeedPage(key: String, offset: Long, limit: Long): List<String> {
        val end = offset + limit - 1
        return redisTemplate.opsForList().range(key, offset, end) ?: emptyList()
    }

    fun cacheFeed(key: String, contentIds: List<String>) {
        if (contentIds.isEmpty()) return
        val tmpKey = "$key:tmp"
        val ops = redisTemplate.opsForList()
        redisTemplate.delete(tmpKey)
        contentIds.forEach { ops.rightPush(tmpKey, it) }
        redisTemplate.rename(tmpKey, key)
        val ttlMinutes = (25..35).random().toLong()
        redisTemplate.expire(key, ttlMinutes, TimeUnit.MINUTES)
    }

    fun deleteFeed(key: String) {
        redisTemplate.delete(key)
    }

    fun tryAcquirePrefetchLock(key: String): Boolean {
        return redisTemplate.opsForValue().setIfAbsent(key, "1", 60, TimeUnit.SECONDS) ?: false
    }

    private fun userKey(userId: UUID) = "feed:user:$userId"
}
