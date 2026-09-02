package com.contentagg.parser.integration.rest.configservice.cache

import com.contentagg.parser.db.service.util.SourceType
import com.contentagg.parser.integration.rest.configservice.ConfigServiceClient
import com.contentagg.parser.integration.rest.configservice.model.HabrConfigDto
import com.contentagg.parser.integration.rest.configservice.model.SourceConfigResponse
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.util.UUID

@Service
class ParserConfigService(
    private val configCacheService: ConfigCacheService,
    private val configServiceClient: ConfigServiceClient,
) {
    companion object {
        private val log = LoggerFactory.getLogger(ParserConfigService::class.java)
    }

    fun getHabrConfig(): HabrConfigDto? =
        configCacheService.getParserConfig(SourceType.HABR)?.habrConfig

    fun isSourceEnabled(sourceType: SourceType): Boolean =
        configCacheService.getParserConfig(sourceType)?.enabled ?: false

    fun getActiveSources(): List<SourceConfigResponse> =
        configServiceClient.getActiveSources()

    fun getSourceById(sourceId: UUID): SourceConfigResponse? =
        configServiceClient.getSourceById(sourceId)

    fun reloadConfig(sourceType: SourceType) {
        configCacheService.invalidateParserConfig(sourceType)
        configServiceClient.evictActiveSourcesCache()
        log.info("Configuration reloaded for source: {}", sourceType)
    }

    fun reloadAllConfigs() {
        configCacheService.clearAllConfigs()
        configServiceClient.evictActiveSourcesCache()
        log.info("All configurations reloaded")
    }
}
