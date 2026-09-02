package com.contentagg.config.db.service.source

import com.contentagg.config.api.model.catalog.CatalogPageResponse
import com.contentagg.config.configuration.properties.CatalogProperties
import com.contentagg.config.enums.SourceType
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.ObjectProvider
import org.springframework.data.redis.RedisConnectionFailureException
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.data.redis.core.ValueOperations
import java.time.Duration

class SourceCatalogCacheServiceTest {

    private val properties = CatalogProperties(
        cacheTtlSeconds = 300L,
        versionKey = "sources:catalog:version",
        keyPrefix = "sources:list",
        defaultPageSize = 20,
        maxPageSize = 50,
    )

    private fun serviceWithoutRedis(): SourceCatalogCacheService {
        val provider = mockk<ObjectProvider<StringRedisTemplate>>()
        every { provider.ifAvailable } returns null
        return SourceCatalogCacheService(provider, jacksonObjectMapper(), properties)
    }

    @Test
    fun `buildKey embeds version type q-hash cursor-hash and pageSize`() {
        val svc = serviceWithoutRedis()
        val key = svc.buildKey(SourceType.TELEGRAM, "habr", "cursor-xyz", 20, 42L)
        assertThat(key).startsWith("sources:list:v42:TELEGRAM:")
        assertThat(key).contains(":ps20")
    }

    @Test
    fun `buildKey uses 'all' placeholder when type is null`() {
        val svc = serviceWithoutRedis()
        val key = svc.buildKey(null, null, null, 20, 0L)
        assertThat(key).isEqualTo("sources:list:v0:all:none:none:ps20")
    }

    @Test
    fun `currentVersion returns 0 when Redis unavailable`() {
        val svc = serviceWithoutRedis()
        assertThat(svc.currentVersion()).isEqualTo(0L)
    }

    @Test
    fun `currentVersion returns 0 on Redis connection failure (fail-open)`() {
        val template = mockk<StringRedisTemplate>()
        val ops = mockk<ValueOperations<String, String>>()
        every { template.opsForValue() } returns ops
        every { ops.get(any<String>()) } throws RedisConnectionFailureException("down")
        val provider = mockk<ObjectProvider<StringRedisTemplate>>()
        every { provider.ifAvailable } returns template

        val svc = SourceCatalogCacheService(provider, jacksonObjectMapper(), properties)

        assertThat(svc.currentVersion()).isEqualTo(0L)
    }

    @Test
    fun `get returns null on Redis connection failure (fail-open)`() {
        val template = mockk<StringRedisTemplate>()
        val ops = mockk<ValueOperations<String, String>>()
        every { template.opsForValue() } returns ops
        every { ops.get(any<String>()) } throws RedisConnectionFailureException("down")
        val provider = mockk<ObjectProvider<StringRedisTemplate>>()
        every { provider.ifAvailable } returns template

        val svc = SourceCatalogCacheService(provider, jacksonObjectMapper(), properties)

        assertThat(svc.get("any-key")).isNull()
    }

    @Test
    fun `put stores JSON with configured TTL`() {
        val template = mockk<StringRedisTemplate>()
        val ops = mockk<ValueOperations<String, String>>(relaxed = true)
        every { template.opsForValue() } returns ops
        val provider = mockk<ObjectProvider<StringRedisTemplate>>()
        every { provider.ifAvailable } returns template

        val svc = SourceCatalogCacheService(provider, jacksonObjectMapper(), properties)
        val page = CatalogPageResponse(items = emptyList(), nextCursor = null, hasMore = false)

        svc.put("k", page)

        verify { ops.set("k", any<String>(), Duration.ofSeconds(300L)) }
    }

    @Test
    fun `invalidate calls INCR on version key`() {
        val template = mockk<StringRedisTemplate>()
        val ops = mockk<ValueOperations<String, String>>(relaxed = true)
        every { template.opsForValue() } returns ops
        every { ops.increment(any<String>()) } returns 5L
        val provider = mockk<ObjectProvider<StringRedisTemplate>>()
        every { provider.ifAvailable } returns template

        val svc = SourceCatalogCacheService(provider, jacksonObjectMapper(), properties)

        svc.invalidate()

        verify { ops.increment("sources:catalog:version") }
    }
}
