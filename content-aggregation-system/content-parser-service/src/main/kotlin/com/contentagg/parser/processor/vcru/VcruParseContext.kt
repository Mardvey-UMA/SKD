package com.contentagg.parser.processor.vcru

import com.contentagg.parser.integration.rest.configservice.model.SourceConfigResponse
import java.util.UUID

data class VcruParseContext(
    val sourceId: UUID,
    val sourceName: String?,
    val alias: String,
    val maxArticles: Int,
    val parseImages: Boolean,
    val sorting: String,
    val parameters: Map<String, String>,
) {
    companion object {
        fun from(
            config: SourceConfigResponse,
            alias: String,
            maxArticles: Int,
        ): VcruParseContext {
            val params = config.parameters
                ?.mapNotNull { (k, v) -> if (v != null) k to v.toString() else null }
                ?.toMap()
                ?: emptyMap()

            val parseImages = params.getOrDefault("parseImages", "true").toBoolean()
            val sorting = params.getOrDefault("sorting", "new")

            return VcruParseContext(
                sourceId = UUID.fromString(config.id),
                sourceName = config.name,
                alias = alias,
                maxArticles = maxArticles,
                parseImages = parseImages,
                sorting = sorting,
                parameters = params,
            )
        }
    }
}
