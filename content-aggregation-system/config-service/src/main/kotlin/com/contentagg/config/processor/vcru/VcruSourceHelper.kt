package com.contentagg.config.processor.vcru

import com.contentagg.config.enums.SourceType
import com.contentagg.config.exception.InvalidSourceException
import org.springframework.stereotype.Component

/**
 * Shared helper for VC.RU source processing logic.
 * Centralizes URL building and parameter map construction
 * used by both CreateVcruSourceProcessor and UpdateVcruSourceProcessor.
 */
@Component
class VcruSourceHelper {

    /**
     * Validate that the source type is VCRU.
     */
    fun validateVcruSourceType(sourceType: SourceType) {
        if (sourceType != SourceType.VCRU) {
            throw InvalidSourceException(
                "Invalid VC.RU source type: $sourceType. Must be: VCRU"
            )
        }
    }

    /**
     * Build the VC.RU URL from an alias.
     * Always uses the pattern: https://vc.ru/{alias}
     */
    fun buildVcruUrl(alias: String): String {
        return "https://vc.ru/$alias"
    }

    /**
     * Build the parameters map for a VC.RU source.
     * Supported sorting values: new, hotness, day, week, month.
     */
    fun buildParametersMap(
        alias: String,
        parseImages: Boolean?,
        maxArticles: Int?,
        sorting: String?
    ): Map<String, String> {
        return buildMap {
            put("vcruAlias", alias)
            put("parseImages", parseImages.toString())
            put("maxArticles", maxArticles.toString())
            put("sorting", sorting ?: "new")
        }
    }
}
