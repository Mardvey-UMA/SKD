package com.contentagg.parser.processor.habr

import com.contentagg.parser.db.service.util.HabrSourceSubtype
import com.contentagg.parser.integration.rest.configservice.model.SourceConfigResponse
import java.util.UUID

data class HabrParseContext(
    val sourceId: UUID,
    val sourceName: String?,
    val subtype: HabrSourceSubtype,
    val alias: String,
    val maxArticles: Int,
    val parseImages: Boolean,
    val parseComments: Boolean,
    val parameters: Map<String, String>,
) {
    companion object {
        fun from(
            config: SourceConfigResponse,
            subtype: HabrSourceSubtype,
            alias: String,
            maxArticles: Int,
        ): HabrParseContext {
            val params = config.parameters
                ?.mapNotNull { (k, v) -> if (v != null) k to v.toString() else null }
                ?.toMap()
                ?: emptyMap()

            val parseImages = params.getOrDefault("parseImages", "true").toBoolean()
            val parseComments = params.getOrDefault("parseComments", "false").toBoolean()

            return HabrParseContext(
                sourceId = UUID.fromString(config.id),
                sourceName = config.name,
                subtype = subtype,
                alias = alias,
                maxArticles = maxArticles,
                parseImages = parseImages,
                parseComments = parseComments,
                parameters = params,
            )
        }
    }
}
