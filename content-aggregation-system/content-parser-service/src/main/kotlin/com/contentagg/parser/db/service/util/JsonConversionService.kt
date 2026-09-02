package com.contentagg.parser.db.service.util

import com.contentagg.parser.exception.JsonConversionException
import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.ObjectMapper
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

/**
 * JSON conversion utility for handling JSONB columns.
 * Spring Data JDBC does not have native JSONB support,
 * so we store as String and convert in the service layer.
 *
 * Moved from util/ package to db/service/util/ to comply with architecture rules.
 */
@Component
class JsonConversionService(
    private val objectMapper: ObjectMapper,
) {
    companion object {
        private val log = LoggerFactory.getLogger(JsonConversionService::class.java)
    }

    /**
     * Convert Map to JSON String for database storage.
     * Returns null for null or empty maps to match database NULL.
     */
    fun toJson(map: Map<String, String>?): String? {
        if (map == null || map.isEmpty()) {
            return null
        }
        return try {
            objectMapper.writeValueAsString(map)
        } catch (e: Exception) {
            log.error("Failed to serialize map to JSON: {}", e.message)
            throw JsonConversionException("Failed to serialize map to JSON", e)
        }
    }

    /**
     * Convert JSON String from database to Map.
     * Returns empty map for null or blank JSON (defensive).
     */
    fun fromJson(json: String?): Map<String, String> {
        if (json == null || json.isBlank()) {
            return emptyMap()
        }
        return try {
            objectMapper.readValue(json, object : TypeReference<Map<String, String>>() {})
        } catch (e: Exception) {
            log.error("Failed to deserialize JSON to map: {}", e.message)
            throw JsonConversionException("Failed to deserialize JSON to map", e)
        }
    }
}
