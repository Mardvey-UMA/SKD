package com.contentagg.parser.integration.rest.configservice.cache

import com.contentagg.parser.db.service.util.SourceType
import com.contentagg.parser.integration.rest.configservice.ConfigServiceClient
import com.contentagg.parser.integration.rest.configservice.model.ParserConfigDto
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

@Service
class ConfigCacheService(
    private val configServiceClient: ConfigServiceClient,
) {
    companion object {
        private val log = LoggerFactory.getLogger(ConfigCacheService::class.java)
    }

    fun getParserConfig(sourceType: SourceType): ParserConfigDto? =
        configServiceClient.getParserConfig(sourceType)

    fun invalidateParserConfig(sourceType: SourceType) {
        // Caffeine eviction is handled via ConfigServiceClient.evictActiveSourcesCache()
        log.info("Config cache invalidation requested for source: {}", sourceType)
    }

    fun getConfigsCacheSize(): Int = 0

    fun clearAllConfigs() {
        configServiceClient.evictActiveSourcesCache()
        log.info("All config caches cleared")
    }
}
