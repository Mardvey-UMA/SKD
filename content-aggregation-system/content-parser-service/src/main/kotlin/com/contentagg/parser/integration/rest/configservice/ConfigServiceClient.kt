package com.contentagg.parser.integration.rest.configservice

import com.contentagg.parser.db.service.util.SourceType
import com.contentagg.parser.integration.rest.configservice.model.ApiResponseDto
import com.contentagg.parser.integration.rest.configservice.model.ParserConfigDto
import com.contentagg.parser.integration.rest.configservice.model.SourceConfigResponse
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.beans.factory.annotation.Value
import org.springframework.cache.annotation.Cacheable
import org.springframework.core.ParameterizedTypeReference
import org.springframework.http.HttpMethod
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClientException
import org.springframework.web.client.RestTemplate
import java.util.UUID

@Component
class ConfigServiceClient(
    @Qualifier("configServiceRestTemplate") private val restTemplate: RestTemplate,
    @Value("\${config-service.url}") private val configServiceUrl: String,
) {
    companion object {
        private val log = LoggerFactory.getLogger(ConfigServiceClient::class.java)
    }

    // ==================== Parser Config API (NEW) ====================

    fun getParserConfig(sourceType: SourceType): ParserConfigDto? {
        return try {
            val url = "$configServiceUrl/api/v1/parser-configs/${sourceType.name.lowercase()}"
            log.info("Fetching parser config from: {}", url)

            val response = restTemplate.exchange(
                url,
                HttpMethod.GET,
                null,
                object : ParameterizedTypeReference<ApiResponseDto<ParserConfigDto>>() {},
            )

            if (response.statusCode.is2xxSuccessful && response.body != null) {
                val body = response.body!!
                if (body.success == true) {
                    log.info("Successfully fetched parser config for source: {}", sourceType)
                    body.data
                } else {
                    log.warn("Config Service returned error: {}", body.error)
                    null
                }
            } else {
                log.error("Config Service returned non-2xx status: {}", response.statusCode)
                null
            }
        } catch (e: RestClientException) {
            log.error("Failed to fetch parser config for source: {}", sourceType, e)
            null
        }
    }

    fun getAllParserConfigs(): Array<ParserConfigDto>? {
        return try {
            val url = "$configServiceUrl/api/v1/parser-configs"
            log.info("Fetching all parser configs from: {}", url)

            val response = restTemplate.exchange(
                url,
                HttpMethod.GET,
                null,
                object : ParameterizedTypeReference<ApiResponseDto<Array<ParserConfigDto>>>() {},
            )

            if (response.statusCode.is2xxSuccessful && response.body != null) {
                val body = response.body!!
                if (body.success == true) {
                    log.info("Successfully fetched all parser configs")
                    body.data
                } else {
                    log.warn("Config Service returned error: {}", body.error)
                    null
                }
            } else {
                log.error("Config Service returned non-2xx status: {}", response.statusCode)
                null
            }
        } catch (e: RestClientException) {
            log.error("Failed to fetch all parser configs", e)
            null
        }
    }

    fun isHealthy(): Boolean {
        return try {
            val url = "$configServiceUrl/health"
            val response = restTemplate.getForEntity(url, String::class.java)
            response.statusCode.is2xxSuccessful
        } catch (e: RestClientException) {
            log.error("Config Service is not healthy", e)
            false
        }
    }

    // ==================== Source Config API (OLD - for backward compatibility) ====================

    @org.springframework.cache.annotation.CacheEvict(value = ["sourceConfigs"], cacheManager = "sourceConfigsCacheManager", allEntries = true)
    fun evictActiveSourcesCache() {
        log.info("Active sources cache evicted")
    }

    @Cacheable(value = ["sourceConfigs"], cacheManager = "sourceConfigsCacheManager", unless = "#result == null")
    fun getActiveSources(): List<SourceConfigResponse> {
        return try {
            log.debug("Fetching active sources from config-service: {}", configServiceUrl)

            val url = "$configServiceUrl/api/config/v1/sources?activeOnly=true"
            val response = restTemplate.getForObject(url, Array<SourceConfigResponse>::class.java)

            if (response != null) {
                log.info("Fetched {} active sources from config-service", response.size)
                response.toList()
            } else {
                emptyList()
            }
        } catch (e: Exception) {
            log.error("Failed to fetch sources from config-service: {}", e.message, e)
            emptyList()
        }
    }

    fun getActiveSourcesByType(sourceType: String): List<SourceConfigResponse> {
        return try {
            log.debug("Fetching active sources by type: {}", sourceType)

            val url = "$configServiceUrl/api/config/v1/sources/by-type/$sourceType?activeOnly=true"
            val response = restTemplate.getForObject(url, Array<SourceConfigResponse>::class.java)

            response?.toList() ?: emptyList()
        } catch (e: Exception) {
            log.error("Failed to fetch sources by type {}: {}", sourceType, e.message, e)
            emptyList()
        }
    }

    @Cacheable(value = ["sourceConfig"], cacheManager = "sourceConfigCacheManager", key = "#sourceId")
    fun getSourceById(sourceId: UUID): SourceConfigResponse? {
        return try {
            log.debug("Fetching source by ID: {}", sourceId)

            val url = "$configServiceUrl/api/config/v1/sources/$sourceId"
            restTemplate.getForObject(url, SourceConfigResponse::class.java)
        } catch (e: Exception) {
            log.error("Failed to fetch source {}: {}", sourceId, e.message, e)
            null
        }
    }
}
