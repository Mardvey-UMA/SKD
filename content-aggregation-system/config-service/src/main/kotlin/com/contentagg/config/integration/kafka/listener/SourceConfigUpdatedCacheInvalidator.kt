package com.contentagg.config.integration.kafka.listener

import com.contentagg.config.db.service.source.SourceCatalogCacheService
import org.slf4j.LoggerFactory
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.stereotype.Component

/**
 * Listens to `source.config.updated` (CREATED|UPDATED|DELETED) and invalidates the
 * Valkey catalog cache by INCR'ing the version counter. Cross-instance invalidation.
 *
 * The payload is Protobuf (bytes); we don't deserialize it — the invalidation is the same
 * regardless of event type. Any arriving event means something in sources changed.
 */
@Component
class SourceConfigUpdatedCacheInvalidator(
    private val cacheService: SourceCatalogCacheService,
) {

    companion object {
        private val log = LoggerFactory.getLogger(SourceConfigUpdatedCacheInvalidator::class.java)
    }

    @KafkaListener(
        topics = ["\${spring.kafka.topics.source-config-updated}"],
        groupId = "\${spring.kafka.consumer.group-id:config-catalog-cache-group}",
    )
    fun onEvent(key: String?, payload: ByteArray) {
        log.info("Received source.config.updated key={} payload_bytes={}", key, payload.size)
        try {
            cacheService.invalidate()
        } catch (ex: Exception) {
            log.warn("Cache invalidation failed — will retry on next event: {}", ex.message)
        }
    }
}
