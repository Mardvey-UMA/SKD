package com.contentplatform.feed.infrastructure.cache

import com.contentplatform.feed.infrastructure.client.model.ContentBatchItem
import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.stereotype.Service
import java.util.concurrent.TimeUnit

@Service
class ContentCacheService(
    private val redisTemplate: StringRedisTemplate,
    private val objectMapper: ObjectMapper
) {

    fun getContent(contentId: String): ContentBatchItem? {
        val json = redisTemplate.opsForValue().get("content:$contentId") ?: return null
        return objectMapper.readValue(json, ContentBatchItem::class.java)
    }

    fun cacheContent(contentId: String, item: ContentBatchItem) {
        redisTemplate.opsForValue().set(
            "content:$contentId",
            objectMapper.writeValueAsString(item),
            24,
            TimeUnit.HOURS
        )
    }

    fun isNegativeCached(contentId: String): Boolean =
        redisTemplate.hasKey("content:miss:$contentId") ?: false

    fun setNegativeCache(contentId: String) {
        redisTemplate.opsForValue().set("content:miss:$contentId", "1", 5, TimeUnit.MINUTES)
    }

    fun getColdStartTrending(): List<String>? {
        val json = redisTemplate.opsForValue().get("rec:cold-start:trending") ?: return null
        return objectMapper.readValue(json, object : TypeReference<List<String>>() {})
    }
}
