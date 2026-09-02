package com.contentagg.parser.processor.vcru.strategy

import com.contentagg.parser.db.service.util.VcruSourceSubtype
import com.contentagg.parser.integration.rest.configservice.model.SourceConfigResponse
import com.contentagg.parser.processor.vcru.VcruParseProcessor
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

/**
 * Strategy for parsing VC.RU user articles.
 * Derives alias from parameters.vcruAlias field.
 */
@Component
class VcruUserParseStrategy(
    private val parseProcessor: VcruParseProcessor,
) : VcruParseStrategy {

    companion object {
        private val log = LoggerFactory.getLogger(VcruUserParseStrategy::class.java)
        private const val ALIAS_KEY = "vcruAlias"
    }

    override fun getSubtype(): VcruSourceSubtype = VcruSourceSubtype.USER

    override fun parse(sourceConfig: SourceConfigResponse, maxArticles: Int): Int {
        val alias = sourceConfig.getParameter(ALIAS_KEY, null)

        if (alias.isNullOrBlank()) {
            log.warn("VC.RU alias not found in parameters for source: {}", sourceConfig.id)
            return 0
        }

        log.info("Parsing VC.RU user articles: alias={}, sourceId={}", alias, sourceConfig.id)

        return parseProcessor.parseArticles(
            sourceConfig,
            VcruSourceSubtype.USER,
            alias,
            maxArticles,
        )
    }

    override fun getAlias(parameters: Map<String, String>?): String? =
        parameters?.get(ALIAS_KEY)
}
