package com.contentagg.config.configuration

import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.data.redis.connection.RedisConnectionFactory
import org.springframework.data.redis.core.StringRedisTemplate

/**
 * Valkey/Redis connection for the public source catalog cache (Phase 2).
 * Only the string-serializing template is needed — catalog page responses are stored
 * as JSON strings (serialized via Jackson) so a plain StringRedisTemplate is sufficient.
 */
@Configuration
class RedisConfiguration {

    companion object {
        private val log = LoggerFactory.getLogger(RedisConfiguration::class.java)
    }

    /**
     * Created only when a RedisConnectionFactory exists (RedisAutoConfiguration is active).
     * In tests that exclude Redis auto-configuration (or when Redis is absent) this bean simply
     * isn't registered, and SourceCatalogCacheService falls back to DB-only mode via ObjectProvider.
     */
    @Bean
    @ConditionalOnBean(RedisConnectionFactory::class)
    fun catalogStringRedisTemplate(connectionFactory: RedisConnectionFactory): StringRedisTemplate {
        log.info("Initializing StringRedisTemplate for catalog cache")
        return StringRedisTemplate(connectionFactory)
    }
}
