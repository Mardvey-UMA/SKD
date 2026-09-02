package com.contentagg.config.db.service.source

import com.contentagg.config.api.model.catalog.CatalogPageResponse
import com.contentagg.config.configuration.properties.CatalogProperties
import com.contentagg.config.enums.SourceType
import com.fasterxml.jackson.databind.ObjectMapper
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.ObjectProvider
import org.springframework.data.redis.RedisConnectionFailureException
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.stereotype.Service
import java.security.MessageDigest
import java.time.Duration

/**
 * Valkey-backed cache for catalog page responses (Phase 2).
 *
 * Design:
 * - Single `{versionKey}` INCR counter invalidates all entries in O(1) (bypasses old keys).
 * - Entry keys embed `v{N}`. Old versions orphan and expire via TTL.
 * - Completely fail-open: any Redis exception degrades to "DB only".
 */
@Service
class SourceCatalogCacheService(
    redisTemplateProvider: ObjectProvider<StringRedisTemplate>,
    private val objectMapper: ObjectMapper,
    private val catalogProperties: CatalogProperties,
) {

    companion object {
        private val log = LoggerFactory.getLogger(SourceCatalogCacheService::class.java)
    }

    private val redisTemplate: StringRedisTemplate? = redisTemplateProvider.ifAvailable

    init {
        if (redisTemplate == null) {
            log.warn("StringRedisTemplate not available — catalog cache disabled (DB-only mode)")
        } else {
            log.info(
                "SourceCatalogCacheService initialised: ttl={}s versionKey={} prefix={}",
                catalogProperties.cacheTtlSeconds, catalogProperties.versionKey, catalogProperties.keyPrefix,
            )
        }
    }

    fun buildKey(type: SourceType?, q: String?, cursor: String?, pageSize: Int, version: Long): String {
        val qHash = q?.let { sha1Hex(it) } ?: "none"
        val cursorHash = cursor?.let { sha1Hex(it) } ?: "none"
        val typePart = type?.name ?: "all"
        return "${catalogProperties.keyPrefix}:v$version:$typePart:$qHash:$cursorHash:ps$pageSize"
    }

    fun currentVersion(): Long {
        val template = redisTemplate ?: return 0L
        return try {
            template.opsForValue().get(catalogProperties.versionKey)?.toLongOrNull() ?: 0L
        } catch (ex: RedisConnectionFailureException) {
            log.warn("Valkey GET version failed — fail-open: {}", ex.message)
            0L
        } catch (ex: Exception) {
            log.warn("Valkey GET version unexpected error — fail-open: {}", ex.message)
            0L
        }
    }

    fun get(key: String): CatalogPageResponse? {
        val template = redisTemplate ?: return null
        return try {
            val raw = template.opsForValue().get(key) ?: return null
            objectMapper.readValue(raw, CatalogPageResponse::class.java)
        } catch (ex: RedisConnectionFailureException) {
            log.warn("Valkey GET {} failed — fail-open: {}", key, ex.message)
            null
        } catch (ex: Exception) {
            log.warn("Valkey GET {} decode error — fail-open: {}", key, ex.message)
            null
        }
    }

    fun put(key: String, value: CatalogPageResponse) {
        val template = redisTemplate ?: return
        try {
            val json = objectMapper.writeValueAsString(value)
            template.opsForValue().set(key, json, Duration.ofSeconds(catalogProperties.cacheTtlSeconds))
        } catch (ex: RedisConnectionFailureException) {
            log.warn("Valkey SET {} failed — fail-open: {}", key, ex.message)
        } catch (ex: Exception) {
            log.warn("Valkey SET {} unexpected error — fail-open: {}", key, ex.message)
        }
    }

    fun invalidate() {
        val template = redisTemplate ?: return
        try {
            val newVersion = template.opsForValue().increment(catalogProperties.versionKey)
            log.info("Catalog cache invalidated — new version={}", newVersion)
        } catch (ex: RedisConnectionFailureException) {
            log.warn("Valkey INCR {} failed — invalidation skipped: {}", catalogProperties.versionKey, ex.message)
        } catch (ex: Exception) {
            log.warn("Valkey INCR unexpected error — invalidation skipped: {}", ex.message)
        }
    }

    private fun sha1Hex(s: String): String {
        val md = MessageDigest.getInstance("SHA-1")
        return md.digest(s.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
    }
}
