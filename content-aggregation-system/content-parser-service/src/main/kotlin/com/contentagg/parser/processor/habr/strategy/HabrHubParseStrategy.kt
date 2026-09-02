package com.contentagg.parser.processor.habr.strategy

import com.contentagg.parser.db.service.util.HabrSourceSubtype
import com.contentagg.parser.integration.rest.configservice.model.SourceConfigResponse
import com.contentagg.parser.processor.habr.HabrParseProcessor
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

/**
 * Strategy for parsing Habr hub articles.
 * Derives hub alias from parameters.hubAlias field.
 */
@Component
class HabrHubParseStrategy(
    private val parseProcessor: HabrParseProcessor,
) : HabrParseStrategy {

    companion object {
        private val log = LoggerFactory.getLogger(HabrHubParseStrategy::class.java)
        private const val HUB_ALIAS_KEY = "hubAlias"
    }

    override fun getSubtype(): HabrSourceSubtype = HabrSourceSubtype.HUB

    override fun parse(sourceConfig: SourceConfigResponse, maxArticles: Int): Int {
        val hubAlias = sourceConfig.getParameter(HUB_ALIAS_KEY, null)

        if (hubAlias.isNullOrBlank()) {
            log.warn("Hub alias not found in parameters for source: {}", sourceConfig.id)
            return 0
        }

        log.info("Parsing Habr hub articles: hubAlias={}, sourceId={}", hubAlias, sourceConfig.id)

        return parseProcessor.parseArticles(
            sourceConfig,
            HabrSourceSubtype.HUB,
            hubAlias,
            maxArticles,
        )
    }

    override fun getAlias(parameters: Map<String, String>?): String? =
        parameters?.get(HUB_ALIAS_KEY)
}
