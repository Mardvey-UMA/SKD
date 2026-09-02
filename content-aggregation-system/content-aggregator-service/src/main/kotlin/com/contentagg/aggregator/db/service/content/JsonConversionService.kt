package com.contentagg.aggregator.db.service.content

import com.contentagg.aggregator.exception.JsonConversionException
import com.fasterxml.jackson.core.JsonProcessingException
import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.stereotype.Component

@Component
class JsonConversionService(
    private val objectMapper: ObjectMapper
) {

    fun toJson(map: Map<String, String>?): String? {
        if (map.isNullOrEmpty()) return null
        return try {
            objectMapper.writeValueAsString(map)
        } catch (e: JsonProcessingException) {
            throw JsonConversionException("Failed to serialize map to JSON", e)
        }
    }

    fun fromJson(json: String?): Map<String, String> {
        if (json.isNullOrBlank()) return emptyMap()
        return try {
            objectMapper.readValue(json, object : TypeReference<Map<String, String>>() {})
        } catch (e: JsonProcessingException) {
            throw JsonConversionException("Failed to deserialize JSON to map", e)
        }
    }

    fun toJsonList(list: List<Map<String, Any>>?): String? {
        if (list.isNullOrEmpty()) return null
        return try {
            objectMapper.writeValueAsString(list)
        } catch (e: JsonProcessingException) {
            throw JsonConversionException("Failed to serialize list to JSON", e)
        }
    }

    fun fromJsonList(json: String?): List<Map<String, Any>> {
        if (json.isNullOrBlank()) return emptyList()
        return try {
            val trimmed = json.trim()
            if (trimmed.startsWith("[")) {
                objectMapper.readValue(trimmed, object : TypeReference<List<Map<String, Any>>>() {})
            } else if (trimmed.startsWith("{")) {
                // Handle object format {"media_0": "{...}", "media_1": "{...}"}
                val map = objectMapper.readValue(trimmed, object : TypeReference<Map<String, Any>>() {})
                map.values.map { value ->
                    when (value) {
                        is String -> try {
                            objectMapper.readValue(value, object : TypeReference<Map<String, Any>>() {})
                        } catch (_: Exception) {
                            mapOf("data" to value)
                        }
                        is Map<*, *> -> @Suppress("UNCHECKED_CAST") (value as Map<String, Any>)
                        else -> mapOf("data" to value.toString())
                    }
                }
            } else {
                emptyList()
            }
        } catch (e: JsonProcessingException) {
            throw JsonConversionException("Failed to deserialize JSON to list", e)
        }
    }
}
