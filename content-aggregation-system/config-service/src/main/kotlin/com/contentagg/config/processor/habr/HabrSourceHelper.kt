package com.contentagg.config.processor.habr

import com.contentagg.config.enums.SourceType
import com.contentagg.config.exception.InvalidSourceException
import org.springframework.stereotype.Component

/**
 * Shared helper for Habr source processing logic.
 * Centralizes URL building, alias key resolution, and parameter map construction
 * used by both CreateHabrSourceProcessor and UpdateHabrSourceProcessor.
 */
@Component
class HabrSourceHelper {

    /**
     * Validate that the source type is HABR.
     */
    fun validateHabrSourceType(sourceType: SourceType) {
        if (sourceType != SourceType.HABR) {
            throw InvalidSourceException(
                "Invalid Habr source type: $sourceType. Must be: HABR"
            )
        }
    }

    /**
     * Build the Habr URL from an alias.
     * Handles full URLs (containing '/') by normalizing them,
     * or plain aliases by detecting type from URL path segments.
     */
    fun buildHabrUrl(alias: String): String {
        val url = extractUrl(alias)
        return if (url.endsWith("/")) url else "$url/"
    }

    private fun extractUrl(alias: String): String = when {
        alias.contains("/") -> {
            val normalized = if (!alias.startsWith("http")) "https://$alias" else alias
            normalized
        }
        else -> "https://habr.com/ru/hubs/$alias/"
    }

    /**
     * Determine the parameter key and extracted alias value from the input.
     * Detects subtype from URL path: /companies/ → companyAlias, /users/ → userLogin, /hubs/ → hubAlias.
     * Returns Pair(parameterKey, extractedAlias).
     */
    fun resolveAliasKeyAndValue(alias: String): Pair<String, String> {
        val url = buildHabrUrl(alias)
        return when {
            url.contains("/companies/") -> {
                val extracted = extractPathSegment(url, "companies")
                "companyAlias" to extracted
            }
            url.contains("/users/") -> {
                val extracted = extractPathSegment(url, "users")
                "userLogin" to extracted
            }
            url.contains("/hubs/") -> {
                val extracted = extractPathSegment(url, "hubs")
                "hubAlias" to extracted
            }
            else -> "hubAlias" to alias
        }
    }

    private fun extractPathSegment(url: String, segment: String): String {
        val pattern = Regex("/$segment/([^/]+)")
        return pattern.find(url)?.groupValues?.get(1) ?: ""
    }

    /**
     * Build the parameters map for a Habr source.
     */
    fun buildParametersMap(
        alias: String,
        parseComments: Boolean?,
        parseImages: Boolean?,
        maxArticles: Int?,
        minRating: Int?,
        hubsFilter: List<String>?,
        tagsFilter: List<String>?
    ): Map<String, String> {
        val (paramKey, paramValue) = resolveAliasKeyAndValue(alias)
        return buildMap {
            put(paramKey, paramValue)
            put("parseComments", parseComments.toString())
            put("parseImages", parseImages.toString())
            put("maxArticles", maxArticles.toString())
            if (minRating != null) {
                put("minRating", minRating.toString())
            }
            if (!hubsFilter.isNullOrEmpty()) {
                put("hubsFilter", hubsFilter.joinToString(","))
            }
            if (!tagsFilter.isNullOrEmpty()) {
                put("tagsFilter", tagsFilter.joinToString(","))
            }
        }
    }
}
